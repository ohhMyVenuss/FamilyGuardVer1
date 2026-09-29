#include <arpa/inet.h>
#include <cerrno>
#include <chrono>
#include <cctype>
#include <csignal>
#include <cstdint>
#include <cstring>
#include <ctime>
#include <iostream>
#include <mutex>
#include <netinet/in.h>
#include <poll.h>
#include <shared_mutex>
#include <set>
#include <string>
#include <sys/socket.h>
#include <thread>
#include <unordered_set>
#include <unistd.h>
#include <vector>

#include <nlohmann/json.hpp>

using json = nlohmann::json;

// ============================================================================
// CẤU HÌNH HỆ THỐNG FAMILYGUARD VPS CORE
// ============================================================================
// Lắng nghe trên 0.0.0.0 để chấp nhận cả kết nối từ WireGuard VPN (10.0.0.1)
// lẫn kết nối trực tiếp từ Internet qua IP Public của VPS (103.74.101.176).
constexpr const char* LISTEN_IP = "0.0.0.0";
constexpr const char* UPSTREAM_DNS_IP = "8.8.8.8";
constexpr const char* AUTH_PASS = "AZvpsd6eb!5l@66";

constexpr int DNS_PORT = 53;
constexpr int CONTROL_TCP_PORT = 9000;
constexpr int BUFFER_SIZE = 4096;
constexpr int TIMEOUT_MS = 5000;
constexpr int SYNC_INTERVAL_SECONDS = 60;
constexpr std::size_t MAX_NDJSON_LINE = 8192;

// Cấu trúc lưu nhật ký truy cập DNS
struct AccessLog {
    std::string domain;
    std::string status;
    std::string client_ip;
    std::string timestamp;
};

// ============================================================================
// BỘ NHỚ TRẠNG THÁI TOÀN CỤC (THREAD-SAFE STATE)
// ============================================================================
std::unordered_set<std::string> blacklist;
std::unordered_set<std::string> whitelist;
std::unordered_set<std::string> blocked_client_ips; // Danh sách IP máy con bị ngắt mạng (VD: 10.0.0.2)
std::shared_mutex state_mutex;

bool filter_enabled = true;
bool emergency_pause = false; // Chế độ khóa mạng khẩn cấp toàn bộ máy con

// Cấu hình giờ giới nghiêm (Bedtime Curfew)
bool curfew_enabled = false;
int curfew_start_hour = 21, curfew_start_min = 30;
int curfew_end_hour = 6, curfew_end_min = 0;

// ============================================================================
// ĐỊNH NGHĨA 5 ỨNG DỤNG DI ĐỘNG & BỘ THEO DÕI THỜI LƯỢNG TRUY CẬP (SCREEN TIME)
// ============================================================================
struct AppDefinition {
    std::string id;
    std::string name;
    std::vector<std::string> domains;
};

const std::vector<AppDefinition> MANAGED_APPS = {
    {"YOUTUBE", "YouTube", {"youtube.com", "googlevideo.com", "ytimg.com", "youtu.be", "youtubei.googleapis.com", "yt.be", "ggpht.com", "youtube-nocookie.com"}},
    {"FACEBOOK", "Facebook & Messenger", {"facebook.com", "fbcdn.net", "fbsbx.com", "fb.com", "facebook.net", "messenger.com", "m.me", "fbinfra.net"}},
    {"TIKTOK", "TikTok", {"tiktok.com", "tiktokv.com", "tiktokcdn.com", "byteoversea.com", "byteoversea.net", "ibytedtos.com", "musical.ly", "tiktokcdn-us.com", "byteicdn.com", "bytedance.com", "ibyteimg.com", "byteorge.com", "tiktokv.eu", "tiktokw.us", "ttwstatic.com"}},
    {"INSTAGRAM", "Instagram", {"instagram.com", "cdninstagram.com", "ig.me", "threads.net"}},
    {"MLBB", "Mobile Legends: Bang Bang", {"mobilelegends.com", "moonton.com", "youngjoygame.com", "mlbb.com", "intlgame.com"}}
};

struct ClientAppUsage {
    int used_seconds = 0;           // Số giây đã dùng hôm nay
    int time_limit_minutes = 60;    // Hạn mức ngày (-1: không giới hạn, >0: phút)
    bool parent_blocked = false;    // Phụ huynh bật cấm hẳn
    std::time_t last_active_ts = 0; // Timestamp của DNS query gần nhất
};

// client_ip -> app_id -> ClientAppUsage
std::unordered_map<std::string, std::unordered_map<std::string, ClientAppUsage>> client_app_usage;

std::vector<AccessLog> pending_logs;
std::mutex pending_logs_mutex;

// ============================================================================
// CÁC HÀM TIỆN ÍCH XỬ LÝ TÊN MIỀN VÀ THỜI GIAN
// ============================================================================
std::string normalize_domain(std::string domain) {
    while (!domain.empty() && std::isspace(static_cast<unsigned char>(domain.front()))) {
        domain.erase(domain.begin());
    }
    while (!domain.empty() && std::isspace(static_cast<unsigned char>(domain.back()))) {
        domain.pop_back();
    }
    while (!domain.empty() && domain.back() == '.') {
        domain.pop_back();
    }
    for (char& ch : domain) {
        ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
    }
    return domain;
}

bool is_valid_domain(const std::string& domain) {
    if (domain.empty() || domain.size() > 253 || domain.front() == '.' || domain.back() == '.') {
        return false;
    }
    std::size_t label_length = 0;
    for (char ch : domain) {
        if (ch == '.') {
            if (label_length == 0 || label_length > 63) return false;
            label_length = 0;
            continue;
        }
        if (!(std::isalnum(static_cast<unsigned char>(ch)) || ch == '-')) {
            return false;
        }
        ++label_length;
    }
    return label_length > 0 && label_length <= 63;
}

std::string current_timestamp() {
    const std::time_t now = std::time(nullptr);
    std::tm local_time {};
    localtime_r(&now, &local_time);
    char buffer[20] {};
    std::strftime(buffer, sizeof(buffer), "%Y-%m-%d %H:%M:%S", &local_time);
    return buffer;
}

std::string sockaddr_ip(const sockaddr_in& address) {
    char ip[INET_ADDRSTRLEN] {};
    inet_ntop(AF_INET, &address.sin_addr, ip, sizeof(ip));
    return ip;
}

bool is_curfew_active() {
    if (!curfew_enabled) return false;
    const std::time_t now = std::time(nullptr);
    std::tm local_time {};
    localtime_r(&now, &local_time);
    int current_minutes = local_time.tm_hour * 60 + local_time.tm_min;
    int start_minutes = curfew_start_hour * 60 + curfew_start_min;
    int end_minutes = curfew_end_hour * 60 + curfew_end_min;

    if (start_minutes < end_minutes) {
        return current_minutes >= start_minutes && current_minutes < end_minutes;
    } else {
        return current_minutes >= start_minutes || current_minutes < end_minutes;
    }
}

// ============================================================================
// TƯỜNG LỬA LINUX IPTABLES (LAYER 3/4 FIREWALL - NGẮT MẠNG TRIỆT ĐỂ CHO APP & WEB)
// ============================================================================

bool is_valid_ipv4_address(const std::string& ip) {
    if (ip.empty() || ip.size() > 15) return false;
    int dots = 0;
    for (char c : ip) {
        if (c == '.') dots++;
        else if (!isdigit(static_cast<unsigned char>(c))) return false;
    }
    return dots == 3;
}

void iptables_block_device(const std::string& ip) {
    if (!is_valid_ipv4_address(ip)) return;
    // 1. Thêm luật DROP vào chuỗi FORWARD (nếu chưa có) để chặn toàn bộ gói tin IP ra Internet
    std::string check_cmd = "iptables -C FORWARD -s " + ip + " -j DROP 2>/dev/null";
    if (system(check_cmd.c_str()) != 0) {
        std::string add_cmd = "iptables -I FORWARD -s " + ip + " -j DROP";
        system(add_cmd.c_str());
    }
    // 2. Xóa các phiên kết nối đang mở sẵn trong bảng conntrack của Kernel để cắt đứt tức thì app YouTube, Facebook, TikTok
    std::string kill_conn = "conntrack -D -s " + ip + " 2>/dev/null";
    system(kill_conn.c_str());
    std::cout << "[FIREWALL] Đã kích hoạt iptables DROP & hủy conntrack cho IP: " << ip << std::endl;
}

void iptables_unblock_device(const std::string& ip) {
    if (!is_valid_ipv4_address(ip)) return;
    // Xóa sạch các luật DROP cho IP này trong FORWARD chain
    std::string del_cmd = "while iptables -D FORWARD -s " + ip + " -j DROP 2>/dev/null; do :; done";
    system(del_cmd.c_str());
    std::cout << "[FIREWALL] Đã gỡ bỏ iptables DROP cho IP: " << ip << std::endl;
}

void iptables_set_emergency_pause(bool pause) {
    if (pause) {
        std::string check_cmd = "iptables -C FORWARD -i wg0 -j DROP 2>/dev/null";
        if (system(check_cmd.c_str()) != 0) {
            system("iptables -I FORWARD -i wg0 -j DROP");
        }
        system("conntrack -F 2>/dev/null");
        std::cout << "[FIREWALL] Đã kích hoạt KHÓA MẠNG KHẨN CẤP (DROP toàn bộ wg0)" << std::endl;
    } else {
        system("while iptables -D FORWARD -i wg0 -j DROP 2>/dev/null; do :; done");
        std::cout << "[FIREWALL] Đã HỦY khóa mạng khẩn cấp toàn hệ thống (mở lại wg0)" << std::endl;
    }
}

void sync_curfew_firewall() {
    bool active = false;
    {
        std::shared_lock<std::shared_mutex> lock(state_mutex);
        active = is_curfew_active();
    }
    static bool s_curfew_applied = false;
    if (active && !s_curfew_applied) {
        std::cout << "[CURFEW] 🌙 Kích hoạt ngắt Internet ra bên ngoài qua wg0 (Giờ giới nghiêm)" << std::endl;
        std::string check_cmd = "iptables -C FORWARD -i wg0 -j DROP 2>/dev/null";
        if (system(check_cmd.c_str()) != 0) {
            system("iptables -I FORWARD -i wg0 -j DROP");
        }
        system("conntrack -F 2>/dev/null");
        s_curfew_applied = true;
    } else if (!active && s_curfew_applied) {
        std::cout << "[CURFEW] ☀️ Mở lại kết nối Internet cho wg0 (Hết giờ giới nghiêm)" << std::endl;
        system("while iptables -D FORWARD -i wg0 -j DROP 2>/dev/null; do :; done");
        // Khôi phục lại luật DROP của các thiết bị bị khóa cụ thể
        std::shared_lock<std::shared_mutex> lock(state_mutex);
        for (const auto& ip : blocked_client_ips) {
            iptables_block_device(ip);
        }
        s_curfew_applied = false;
    }
}

void add_access_log(const std::string& domain,
                    const std::string& status,
                    const sockaddr_in& client) {
    AccessLog entry {
        domain.empty() ? "UNKNOWN" : domain,
        status,
        sockaddr_ip(client),
        current_timestamp()
    };

    {
        std::lock_guard<std::mutex> lock(pending_logs_mutex);
        pending_logs.push_back(entry);
    }

    std::cout << json({
        {"type", "DNS_ACCESS"},
        {"domain", entry.domain},
        {"status", entry.status},
        {"client_ip", entry.client_ip},
        {"timestamp", entry.timestamp}
    }).dump() << std::endl;
}

bool is_domain_in_set(const std::string& queried_domain, const std::unordered_set<std::string>& domain_set) {
    for (const std::string& target_domain : domain_set) {
        if (queried_domain == target_domain) {
            return true;
        }
        if (queried_domain.size() > target_domain.size() &&
            queried_domain.compare(
                queried_domain.size() - target_domain.size(),
                target_domain.size(),
                target_domain
            ) == 0 &&
            queried_domain[queried_domain.size() - target_domain.size() - 1] == '.') {
            return true;
        }
    }
    return false;
}

std::string detect_app_for_domain(const std::string& queried_domain) {
    if (queried_domain.empty()) return "";
    std::string lower = queried_domain;
    for (char& c : lower) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));

    // 1. Nhận diện TikTok theo từ khóa và CDN đặc trưng
    if (lower.find("tiktok") != std::string::npos ||
        lower.find("byteoversea") != std::string::npos ||
        lower.find("ibytedtos") != std::string::npos ||
        lower.find("byteicdn") != std::string::npos ||
        lower.find("bytedance") != std::string::npos ||
        lower.find("ibyteimg") != std::string::npos ||
        lower.find("musical.ly") != std::string::npos) {
        return "TIKTOK";
    }

    // 2. Nhận diện YouTube
    if (lower.find("youtube") != std::string::npos ||
        lower.find("googlevideo.com") != std::string::npos ||
        lower.find("ytimg.com") != std::string::npos ||
        lower.find("youtu.be") != std::string::npos ||
        lower.find("ggpht.com") != std::string::npos) {
        return "YOUTUBE";
    }

    // 3. Nhận diện Facebook & Messenger
    if (lower.find("facebook.com") != std::string::npos ||
        lower.find("fbcdn.net") != std::string::npos ||
        lower.find("fbsbx.com") != std::string::npos ||
        lower.find("messenger.com") != std::string::npos ||
        lower == "fb.com" || (lower.size() > 7 && lower.substr(lower.size() - 7) == ".fb.com") ||
        lower == "m.me" || (lower.size() > 5 && lower.substr(lower.size() - 5) == ".m.me")) {
        return "FACEBOOK";
    }

    // 4. Nhận diện Instagram
    if (lower.find("instagram.com") != std::string::npos ||
        lower.find("cdninstagram.com") != std::string::npos ||
        lower == "ig.me" || (lower.size() > 6 && lower.substr(lower.size() - 6) == ".ig.me") ||
        lower.find("threads.net") != std::string::npos) {
        return "INSTAGRAM";
    }

    // 5. Nhận diện Mobile Legends: Bang Bang
    if (lower.find("mobilelegends") != std::string::npos ||
        lower.find("moonton") != std::string::npos ||
        lower.find("youngjoygame") != std::string::npos ||
        lower.find("mlbb.com") != std::string::npos) {
        return "MLBB";
    }

    // 6. Đối chiếu chính xác theo danh sách domain chuẩn
    for (const auto& app : MANAGED_APPS) {
        for (const auto& target_domain : app.domains) {
            if (lower == target_domain) {
                return app.id;
            }
            if (lower.size() > target_domain.size() &&
                lower.compare(lower.size() - target_domain.size(), target_domain.size(), target_domain) == 0 &&
                lower[lower.size() - target_domain.size() - 1] == '.') {
                return app.id;
            }
        }
    }
    return "";
}

// ============================================================================
// PHÂN TÍCH & ĐÓNG GÓI GÓI TIN DNS (WIRE FORMAT RFC 1035)
// ============================================================================
bool extract_dns_qname(const unsigned char* packet,
                       std::size_t packet_length,
                       std::string& domain) {
    if (packet_length < 13) return false;
    const std::uint16_t question_count =
        static_cast<std::uint16_t>((packet[4] << 8) | packet[5]);
    if (question_count == 0) return false;

    std::size_t position = 12;
    std::string result;

    while (position < packet_length) {
        const unsigned char label_length = packet[position++];
        if (label_length == 0) break;
        if ((label_length & 0xC0) != 0 || label_length > 63 || position + label_length > packet_length) {
            return false;
        }
        if (!result.empty()) {
            result.push_back('.');
        }
        for (unsigned char index = 0; index < label_length; ++index) {
            result.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(packet[position++]))));
        }
    }

    if (result.empty() || position + 4 > packet_length) return false;
    domain = normalize_domain(result);
    return is_valid_domain(domain);
}

std::vector<unsigned char> make_refused_response(const unsigned char* query, std::size_t query_length) {
    if (query_length < 12) return {};
    std::size_t position = 12;
    while (position < query_length) {
        const unsigned char label_length = query[position++];
        if (label_length == 0) break;
        if ((label_length & 0xC0) != 0 || label_length > 63 || position + label_length > query_length) {
            return {};
        }
        position += label_length;
    }
    if (position + 4 > query_length) return {};

    const std::size_t question_end = position + 4;
    std::vector<unsigned char> response(query, query + question_end);

    // QR = 1 (Response), RCODE = 0x05 (REFUSED)
    response[2] = static_cast<unsigned char>(response[2] | 0x80);
    response[2] = static_cast<unsigned char>(response[2] & 0xFB);
    response[3] = static_cast<unsigned char>((response[3] & 0xF0) | 0x05);

    response[6] = 0; response[7] = 0;
    response[8] = 0; response[9] = 0;
    response[10] = 0; response[11] = 0;

    return response;
}

std::string exec_command(const std::string& cmd) {
    std::array<char, 512> buffer;
    std::string result;
    std::unique_ptr<FILE, decltype(&pclose)> pipe(popen(cmd.c_str(), "r"), pclose);
    if (!pipe) return "{\"success\":false,\"error\":\"popen failed\"}";
    while (fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr) {
        result += buffer.data();
    }
    return result;
}

// ============================================================================
// XỬ LÝ LỆNH JSON TỪ PHẦN MỀM PHỤ HUYNH JAVAFX (TCP IPC CỔNG 9000)
// ============================================================================
json handle_command(const json& command) {
    if (!command.is_object() || !command.contains("action") || !command["action"].is_string()) {
        return {{"success", false}, {"error", "INVALID_ARGUMENT"}};
    }

    const std::string action = command["action"].get<std::string>();

    // 1. Lệnh xác thực (AUTH) từ JavaFX
    if (action == "AUTH") {
        std::string pass = "";
        if (command.contains("password") && command["password"].is_string()) {
            pass = command["password"].get<std::string>();
        } else if (command.contains("auth_key") && command["auth_key"].is_string()) {
            pass = command["auth_key"].get<std::string>();
        }

        bool match = (pass == AUTH_PASS);
        std::cout << "[CONTROL] Client AUTH: " << (match ? "SUCCESS" : "FAILED") << std::endl;
        return {
            {"success", match},
            {"action", "AUTH"},
            {"status", match ? "AUTH_SUCCESS" : "AUTH_FAILED"},
            {"message", match ? "Xac thuc thanh cong voi FamilyGuard VPS" : "Sai mat khau!"},
            {"server_time", current_timestamp()}
        };
    }

    // 2. Thao tác thêm hoặc xóa Blacklist (ADD_BLACKLIST / REMOVE_BLACKLIST)
    if (action == "ADD_BLACKLIST" || action == "REMOVE_BLACKLIST") {
        if (!command.contains("domain") || !command["domain"].is_string()) {
            return {{"success", false}, {"action", action}, {"error", "MISSING_DOMAIN"}};
        }

        const std::string domain = normalize_domain(command["domain"].get<std::string>());
        if (!is_valid_domain(domain)) {
            return {{"success", false}, {"action", action}, {"error", "INVALID_DOMAIN"}};
        }

        std::unique_lock<std::shared_mutex> lock(state_mutex);
        if (action == "ADD_BLACKLIST") {
            bool added = blacklist.insert(domain).second;
            std::cout << "[BLACKLIST] Thêm domain chặn: " << domain << std::endl;
            return {{"success", true}, {"action", action}, {"domain", domain}, {"added", added}};
        } else {
            bool removed = blacklist.erase(domain) > 0;
            std::cout << "[BLACKLIST] Gỡ domain chặn: " << domain << std::endl;
            return {{"success", true}, {"action", action}, {"domain", domain}, {"removed", removed}};
        }
    }

    // 3. Thao tác Whitelist (Học tập ưu tiên - Luôn cho phép)
    if (action == "ADD_WHITELIST" || action == "REMOVE_WHITELIST") {
        if (!command.contains("domain") || !command["domain"].is_string()) {
            return {{"success", false}, {"action", action}, {"error", "MISSING_DOMAIN"}};
        }

        const std::string domain = normalize_domain(command["domain"].get<std::string>());
        if (!is_valid_domain(domain)) {
            return {{"success", false}, {"action", action}, {"error", "INVALID_DOMAIN"}};
        }

        std::unique_lock<std::shared_mutex> lock(state_mutex);
        if (action == "ADD_WHITELIST") {
            bool added = whitelist.insert(domain).second;
            std::cout << "[WHITELIST] Thêm trang học tập: " << domain << std::endl;
            return {{"success", true}, {"action", action}, {"domain", domain}, {"added", added}};
        } else {
            bool removed = whitelist.erase(domain) > 0;
            std::cout << "[WHITELIST] Gỡ trang học tập: " << domain << std::endl;
            return {{"success", true}, {"action", action}, {"domain", domain}, {"removed", removed}};
        }
    }

    // 4. Bật / Tắt bảo vệ (SET_STATUS)
    if (action == "SET_STATUS") {
        if (!command.contains("enabled") || !command["enabled"].is_boolean()) {
            return {{"success", false}, {"action", action}, {"error", "INVALID_ARGUMENT"}};
        }

        const bool enabled = command["enabled"].get<bool>();
        {
            std::unique_lock<std::shared_mutex> lock(state_mutex);
            filter_enabled = enabled;
        }

        std::cout << "[FILTER] Trạng thái bảo vệ: " << (enabled ? "ĐÃ BẬT" : "ĐÃ TẮT") << std::endl;
        return {{"success", true}, {"action", action}, {"enabled", enabled}};
    }

    // 5. Khóa mạng khẩn cấp toàn hệ thống (EMERGENCY_PAUSE)
    if (action == "EMERGENCY_PAUSE") {
        bool pause = true;
        if (command.contains("paused") && command["paused"].is_boolean()) {
            pause = command["paused"].get<bool>();
        } else if (command.contains("enabled") && command["enabled"].is_boolean()) {
            pause = command["enabled"].get<bool>();
        }

        {
            std::unique_lock<std::shared_mutex> lock(state_mutex);
            emergency_pause = pause;
            iptables_set_emergency_pause(pause);
        }

        std::cout << "[EMERGENCY] Chế độ khóa khẩn cấp: " << (pause ? "KÍCH HOẠT" : "HỦY") << std::endl;
        return {{"success", true}, {"action", action}, {"paused", pause}};
    }

    // 6. Cắt mạng / Mở mạng từng thiết bị con (DEVICE_BLOCK)
    if (action == "DEVICE_BLOCK") {
        std::string ip = "";
        if (command.contains("ip") && command["ip"].is_string()) {
            ip = command["ip"].get<std::string>();
        }

        bool blocked = true;
        if (command.contains("blocked") && command["blocked"].is_boolean()) {
            blocked = command["blocked"].get<bool>();
        }

        if (!ip.empty()) {
            std::unique_lock<std::shared_mutex> lock(state_mutex);
            if (blocked) {
                blocked_client_ips.insert(ip);
                iptables_block_device(ip);
                std::cout << "[DEVICE_BLOCK] Đã ngắt mạng triệt để IP: " << ip << std::endl;
            } else {
                blocked_client_ips.erase(ip);
                iptables_unblock_device(ip);
                std::cout << "[DEVICE_BLOCK] Đã khôi phục mạng cho thiết bị IP: " << ip << std::endl;
            }
        }

        return {{"success", true}, {"action", action}, {"ip", ip}, {"blocked", blocked}};
    }

    // 7. Cập nhật giờ giới nghiêm (CURFEW_UPDATE)
    if (action == "CURFEW_UPDATE") {
        bool enabled = true;
        if (command.contains("enabled") && command["enabled"].is_boolean()) {
            enabled = command["enabled"].get<bool>();
        }

        if (command.contains("startTime") && command["startTime"].is_string() &&
            command.contains("endTime") && command["endTime"].is_string()) {
            std::string start = command["startTime"].get<std::string>();
            std::string end = command["endTime"].get<std::string>();

            int sh = 21, sm = 30, eh = 6, em = 0;
            if (sscanf(start.c_str(), "%d:%d", &sh, &sm) == 2 &&
                sscanf(end.c_str(), "%d:%d", &eh, &em) == 2) {
                std::unique_lock<std::shared_mutex> lock(state_mutex);
                curfew_enabled = enabled;
                curfew_start_hour = sh; curfew_start_min = sm;
                curfew_end_hour = eh; curfew_end_min = em;
            }
        }

        std::cout << "[CURFEW] Cập nhật giờ giới nghiêm: " << (enabled ? "BẬT" : "TẮT") << std::endl;
        sync_curfew_firewall();
        return {{"success", true}, {"action", action}, {"enabled", enabled}};
    }

    // 7b. Cập nhật hạn mức thời gian ngày (TIME_LIMIT_UPDATE)
    if (action == "TIME_LIMIT_UPDATE") {
        double weekday = command.value("weekdayHours", 2.0);
        double weekend = command.value("weekendHours", 4.0);
        std::cout << "[TIME_LIMIT] Nhận hạn mức sử dụng ngày: T2-T6: " << weekday << "h, T7-CN: " << weekend << "h" << std::endl;
        return {
            {"success", true},
            {"action", action},
            {"weekdayHours", weekday},
            {"weekendHours", weekend}
        };
    }

    // 8. Ping / Health check
    if (action == "PING") {
        return {{"success", true}, {"action", "PONG"}, {"timestamp", current_timestamp()}};
    }

    // 9. Lấy trạng thái hiện tại (GET_STATUS)
    if (action == "GET_STATUS") {
        std::shared_lock<std::shared_mutex> lock(state_mutex);
        return {
            {"success", true},
            {"action", "GET_STATUS"},
            {"filter_enabled", filter_enabled},
            {"emergency_pause", emergency_pause},
            {"curfew_active", is_curfew_active()},
            {"blacklist_count", blacklist.size()},
            {"whitelist_count", whitelist.size()},
            {"blocked_devices_count", blocked_client_ips.size()},
            {"server_time", current_timestamp()}
        };
    }

    // 10. Đồng bộ toàn diện danh sách và trạng thái từ JavaFX (SYNC_RULES)
    if (action == "SYNC_RULES") {
        std::unique_lock<std::shared_mutex> lock(state_mutex);
        if (command.contains("filter_enabled") && command["filter_enabled"].is_boolean()) {
            filter_enabled = command["filter_enabled"].get<bool>();
        }
        if (command.contains("emergency_pause") && command["emergency_pause"].is_boolean()) {
            emergency_pause = command["emergency_pause"].get<bool>();
        }
        if (command.contains("blacklist") && command["blacklist"].is_array()) {
            blacklist.clear();
            for (const auto& item : command["blacklist"]) {
                if (item.is_string()) {
                    std::string d = normalize_domain(item.get<std::string>());
                    if (is_valid_domain(d)) blacklist.insert(d);
                }
            }
        }
        if (command.contains("whitelist") && command["whitelist"].is_array()) {
            whitelist.clear();
            for (const auto& item : command["whitelist"]) {
                if (item.is_string()) {
                    std::string d = normalize_domain(item.get<std::string>());
                    if (is_valid_domain(d)) whitelist.insert(d);
                }
            }
        }
        if (command.contains("blocked_ips") && command["blocked_ips"].is_array()) {
            std::unordered_set<std::string> new_blocked;
            for (const auto& item : command["blocked_ips"]) {
                if (item.is_string()) {
                    new_blocked.insert(item.get<std::string>());
                }
            }
            // Gỡ bỏ luật tường lửa cho các IP không còn nằm trong danh sách chặn
            for (const auto& old_ip : blocked_client_ips) {
                if (new_blocked.find(old_ip) == new_blocked.end()) {
                    iptables_unblock_device(old_ip);
                }
            }
            // Kích hoạt tường lửa iptables cho các IP bị chặn
            for (const auto& ip : new_blocked) {
                iptables_block_device(ip);
            }
            blocked_client_ips = new_blocked;
        }
        if (command.contains("emergency_pause") && command["emergency_pause"].is_boolean()) {
            emergency_pause = command["emergency_pause"].get<bool>();
            iptables_set_emergency_pause(emergency_pause);
        }
        std::cout << "[SYNC_RULES] Đã đồng bộ từ JavaFX: " 
                  << blacklist.size() << " tên miền cấm, " 
                  << whitelist.size() << " tên miền học tập, " 
                  << blocked_client_ips.size() << " IP bị chặn tường lửa." << std::endl;
        return {
            {"success", true},
            {"action", "SYNC_RULES"},
            {"blacklist_count", blacklist.size()},
            {"whitelist_count", whitelist.size()},
            {"blocked_devices_count", blocked_client_ips.size()},
            {"filter_enabled", filter_enabled},
            {"emergency_pause", emergency_pause}
        };
    }

    // 11. Xóa toàn bộ Blacklist
    if (action == "CLEAR_BLACKLIST") {
        std::unique_lock<std::shared_mutex> lock(state_mutex);
        blacklist.clear();
        std::cout << "[BLACKLIST] Đã làm trống danh sách cấm." << std::endl;
        return {{"success", true}, {"action", action}};
    }

    // 12. Tương thích ngược WHITELIST_UPDATE
    if (action == "WHITELIST_UPDATE") {
        std::string op = command.value("operation", "ADD");
        std::string domain = normalize_domain(command.value("domain", ""));
        if (!is_valid_domain(domain)) {
            return {{"success", false}, {"action", action}, {"error", "INVALID_DOMAIN"}};
        }
        std::unique_lock<std::shared_mutex> lock(state_mutex);
        if (op == "REMOVE") {
            whitelist.erase(domain);
        } else {
            whitelist.insert(domain);
        }
        return {{"success", true}, {"action", action}, {"domain", domain}, {"operation", op}};
    }

    // 13. Tự động hóa cấp cấu hình WireGuard (CREATE_WIREGUARD_PEER)
    if (action == "CREATE_WIREGUARD_PEER") {
        std::string device_name = "ThietBiCon";
        if (command.contains("deviceName") && command["deviceName"].is_string()) {
            device_name = command["deviceName"].get<std::string>();
        } else if (command.contains("device_name") && command["device_name"].is_string()) {
            device_name = command["device_name"].get<std::string>();
        }

        std::string device_type = "Điện thoại";
        if (command.contains("deviceType") && command["deviceType"].is_string()) {
            device_type = command["deviceType"].get<std::string>();
        }

        // Loại bỏ ký tự đặc biệt nguy hiểm trước khi đưa vào command
        std::string safe_name;
        for (char c : device_name) {
            if (c == '"' || c == '\'' || c == ';' || c == '`' || c == '$' || c == '\\') continue;
            safe_name.push_back(c);
        }
        if (safe_name.empty()) safe_name = "ThietBiCon";

        std::string cmd = "python3 /opt/familyguard/wg_manager.py create \"" + safe_name + "\" 2>&1";
        std::string output = exec_command(cmd);

        json py_res = json::parse(output, nullptr, false);
        if (py_res.is_discarded() || !py_res.value("success", false)) {
            std::string err = py_res.is_discarded() ? ("Loi thuc thi: " + output) : py_res.value("error", "Loi tao WireGuard peer");
            std::cerr << "[WIREGUARD] Lỗi tạo peer: " << err << std::endl;
            return {
                {"success", false},
                {"action", "CREATE_WIREGUARD_PEER"},
                {"error", err}
            };
        }

        std::string assigned_ip = py_res.value("assigned_ip", "");
        std::string config_text = py_res.value("config_text", "");
        std::string qr_base64 = py_res.value("qr_base64", "");

        std::cout << "[WIREGUARD] Đã tạo thành công cấu hình cho thiết bị: " << safe_name 
                  << " (IP: " << assigned_ip << ")" << std::endl;

        return {
            {"success", true},
            {"action", "CREATE_WIREGUARD_PEER"},
            {"deviceName", safe_name},
            {"deviceType", device_type},
            {"assignedIp", assigned_ip},
            {"configText", config_text},
            {"qrBase64", qr_base64},
            {"message", "Tạo cấu hình WireGuard thành công"}
        };
    }

    // 14. Ghi nhận thiết bị mới từ JavaFX (ADD_DEVICE)
    if (action == "ADD_DEVICE") {
        std::string dev_name = command.value("name", "Unknown");
        std::string dev_ip = command.value("ip", "");
        std::cout << "[DEVICE] Ghi nhận thiết bị mới từ JavaFX: " << dev_name << " (" << dev_ip << ")" << std::endl;
        return {
            {"success", true},
            {"action", "ADD_DEVICE"},
            {"name", dev_name},
            {"ip", dev_ip}
        };
    }

    // 15. Cập nhật chính sách và hạn mức thời gian 5 ứng dụng (SET_APP_POLICY)
    if (action == "SET_APP_POLICY") {
        std::string ip = command.value("client_ip", "10.0.0.2");
        std::string app_id = command.value("app_id", "");
        int limit_min = command.value("time_limit_minutes", 60);
        bool blocked = command.value("blocked", false);

        if (!app_id.empty()) {
            std::unique_lock<std::shared_mutex> lock(state_mutex);
            auto& usage = client_app_usage[ip][app_id];
            usage.time_limit_minutes = limit_min;
            usage.parent_blocked = blocked;
            std::cout << "[APP_POLICY] IP " << ip << " - App " << app_id 
                      << ": Chặn=" << (blocked ? "BẬT" : "TẮT") 
                      << ", Hạn mức=" << limit_min << " phút" << std::endl;
        }

        return {
            {"success", true},
            {"action", "SET_APP_POLICY"},
            {"client_ip", ip},
            {"app_id", app_id},
            {"time_limit_minutes", limit_min},
            {"blocked", blocked}
        };
    }

    // 16. Đặt lại thời gian sử dụng hôm nay (RESET_APP_USAGE)
    if (action == "RESET_APP_USAGE") {
        std::string ip = command.value("client_ip", "10.0.0.2");
        std::string app_id = command.value("app_id", "");

        std::unique_lock<std::shared_mutex> lock(state_mutex);
        if (app_id.empty()) {
            for (auto& pair : client_app_usage[ip]) {
                pair.second.used_seconds = 0;
                pair.second.last_active_ts = 0;
            }
            std::cout << "[APP_POLICY] Đã đặt lại thời gian tất cả App cho IP " << ip << std::endl;
        } else {
            client_app_usage[ip][app_id].used_seconds = 0;
            client_app_usage[ip][app_id].last_active_ts = 0;
            std::cout << "[APP_POLICY] Đã đặt lại thời gian App " << app_id << " cho IP " << ip << std::endl;
        }

        return {
            {"success", true},
            {"action", "RESET_APP_USAGE"},
            {"client_ip", ip},
            {"app_id", app_id}
        };
    }

    // 17. Lấy danh sách thống kê sử dụng 5 app (GET_APP_USAGE)
    if (action == "GET_APP_USAGE") {
        std::shared_lock<std::shared_mutex> lock(state_mutex);
        json apps_arr = json::array();
        std::set<std::string> ips = {"10.0.0.2"};
        for (const auto& [ip, _] : client_app_usage) ips.insert(ip);

        for (const auto& ip : ips) {
            json dev_entry;
            dev_entry["client_ip"] = ip;
            dev_entry["apps"] = json::array();
            auto it_ip = client_app_usage.find(ip);
            for (const auto& app_def : MANAGED_APPS) {
                int used = 0, limit = 60;
                bool blk = false, exc = false;
                if (it_ip != client_app_usage.end()) {
                    auto it_app = it_ip->second.find(app_def.id);
                    if (it_app != it_ip->second.end()) {
                        used = it_app->second.used_seconds;
                        limit = it_app->second.time_limit_minutes;
                        blk = it_app->second.parent_blocked;
                        exc = (limit > 0 && used >= limit * 60);
                    }
                }
                dev_entry["apps"].push_back({
                    {"app_id", app_def.id},
                    {"app_name", app_def.name},
                    {"used_seconds", used},
                    {"time_limit_minutes", limit},
                    {"blocked", blk},
                    {"time_exceeded", exc}
                });
            }
            apps_arr.push_back(dev_entry);
        }
        return {
            {"success", true},
            {"action", "GET_APP_USAGE"},
            {"devices", apps_arr}
        };
    }

    std::cerr << "[CONTROL] UNKNOWN_ACTION: " << action << std::endl;
    return {{"success", false}, {"action", action}, {"error", "UNKNOWN_ACTION"}};
}

// ============================================================================
// TRUYỀN NHẬN TCP VÀ ĐỒNG BỘ LOGS (NDJSON)
// ============================================================================
bool send_all(int fd, const std::string& message) {
    std::size_t total_sent = 0;
    while (total_sent < message.size()) {
        const ssize_t sent = send(fd, message.data() + total_sent, message.size() - total_sent, MSG_NOSIGNAL);
        if (sent > 0) {
            total_sent += static_cast<std::size_t>(sent);
            continue;
        }
        if (sent < 0 && errno == EINTR) continue;
        return false;
    }
    return true;
}

bool send_json_line(int fd, const json& message) {
    return send_all(fd, message.dump() + "\n");
}

json make_sync_batch(const std::vector<AccessLog>& logs) {
    json entries = json::array();
    for (const AccessLog& entry : logs) {
        entries.push_back({
            {"domain", entry.domain},
            {"status", entry.status},
            {"client_ip", entry.client_ip},
            {"timestamp", entry.timestamp}
        });
    }
    return {
        {"action", "SYNC_LOGS"},
        {"logs", entries}
    };
}

bool sync_app_usage_to_parent(int parent_fd) {
    std::shared_lock<std::shared_mutex> lock(state_mutex);
    json apps_arr = json::array();
    std::set<std::string> ips = {"10.0.0.2"};
    for (const auto& [ip, _] : client_app_usage) ips.insert(ip);

    for (const auto& ip : ips) {
        json dev_entry;
        dev_entry["client_ip"] = ip;
        dev_entry["apps"] = json::array();
        auto it_ip = client_app_usage.find(ip);
        for (const auto& app_def : MANAGED_APPS) {
            int used = 0, limit = 60;
            bool blk = false, exc = false;
            if (it_ip != client_app_usage.end()) {
                auto it_app = it_ip->second.find(app_def.id);
                if (it_app != it_ip->second.end()) {
                    used = it_app->second.used_seconds;
                    limit = it_app->second.time_limit_minutes;
                    blk = it_app->second.parent_blocked;
                    exc = (limit > 0 && used >= limit * 60);
                }
            }
            dev_entry["apps"].push_back({
                {"app_id", app_def.id},
                {"app_name", app_def.name},
                {"used_seconds", used},
                {"time_limit_minutes", limit},
                {"blocked", blk},
                {"time_exceeded", exc}
            });
        }
        apps_arr.push_back(dev_entry);
    }
    json msg = {{"action", "SYNC_APP_USAGE"}, {"devices", apps_arr}};
    return send_json_line(parent_fd, msg);
}

bool sync_pending_logs(int parent_fd) {
    // 1. Đồng bộ thống kê sử dụng 5 app về JavaFX
    sync_app_usage_to_parent(parent_fd);

    // 2. Đồng bộ các log DNS vi phạm/cho phép
    std::vector<AccessLog> snapshot;
    {
        std::lock_guard<std::mutex> lock(pending_logs_mutex);
        if (pending_logs.empty()) return true;
        snapshot = pending_logs;
    }

    if (!send_json_line(parent_fd, make_sync_batch(snapshot))) {
        return false;
    }

    {
        std::lock_guard<std::mutex> lock(pending_logs_mutex);
        if (pending_logs.size() >= snapshot.size()) {
            pending_logs.erase(
                pending_logs.begin(),
                pending_logs.begin() + static_cast<std::vector<AccessLog>::difference_type>(snapshot.size())
            );
        }
    }

    std::cout << "[SYNC] Đã đồng bộ " << snapshot.size() << " bản ghi nhật ký DNS về ứng dụng cha mẹ." << std::endl;
    return true;
}

int create_tcp_listener() {
    const int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        std::perror("[CONTROL] socket");
        return -1;
    }

    int reuse = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse));

    sockaddr_in address {};
    address.sin_family = AF_INET;
    address.sin_port = htons(CONTROL_TCP_PORT);

    if (inet_pton(AF_INET, LISTEN_IP, &address.sin_addr) != 1) {
        std::cerr << "[CONTROL] Invalid bind IP" << std::endl;
        close(fd);
        return -1;
    }

    if (bind(fd, reinterpret_cast<sockaddr*>(&address), sizeof(address)) < 0) {
        std::perror("[CONTROL] bind 0.0.0.0:9000");
        close(fd);
        return -1;
    }

    if (listen(fd, 8) < 0) {
        std::perror("[CONTROL] listen");
        close(fd);
        return -1;
    }

    return fd;
}

void serve_parent_connection(int parent_fd, const sockaddr_in& parent_addr) {
    std::cout << "[CONTROL] Máy Phụ Huynh kết nối thành công: "
              << sockaddr_ip(parent_addr) << ":" << ntohs(parent_addr.sin_port) << std::endl;

    std::string receive_buffer;
    auto last_sync = std::chrono::steady_clock::now();

    while (true) {
        const auto now = std::chrono::steady_clock::now();
        const auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(now - last_sync).count();
        const int wait_ms = elapsed >= SYNC_INTERVAL_SECONDS ? 0 : static_cast<int>((SYNC_INTERVAL_SECONDS - elapsed) * 1000);

        pollfd pfd {};
        pfd.fd = parent_fd;
        pfd.events = POLLIN | POLLERR | POLLHUP;

        const int ready = poll(&pfd, 1, wait_ms);
        if (ready < 0) {
            if (errno == EINTR) continue;
            std::perror("[CONTROL] poll parent");
            break;
        }

        if (ready == 0) {
            if (!sync_pending_logs(parent_fd)) {
                std::cerr << "[SYNC] Lỗi khi gửi log; dữ liệu được giữ lại để thử lại" << std::endl;
                break;
            }
            last_sync = std::chrono::steady_clock::now();
            continue;
        }

        if (pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) {
            break;
        }

        if (pfd.revents & POLLIN) {
            char buffer[BUFFER_SIZE];
            const ssize_t received = recv(parent_fd, buffer, sizeof(buffer), 0);
            if (received <= 0) {
                if (received < 0 && errno == EINTR) continue;
                break;
            }

            receive_buffer.append(buffer, static_cast<std::size_t>(received));
            if (receive_buffer.size() > MAX_NDJSON_LINE) {
                std::cerr << "[CONTROL] Gói tin quá lớn, đóng phiên kết nối" << std::endl;
                break;
            }

            std::size_t newline_position = 0;
            while ((newline_position = receive_buffer.find('\n')) != std::string::npos) {
                std::string line = receive_buffer.substr(0, newline_position);
                receive_buffer.erase(0, newline_position + 1);

                if (!line.empty() && line.back() == '\r') line.pop_back();
                if (line.empty()) continue;

                const json command = json::parse(line, nullptr, false);
                json response;

                if (command.is_discarded()) {
                    std::cerr << "[CONTROL] Gói tin không phải JSON hợp lệ" << std::endl;
                    response = {{"success", false}, {"error", "INVALID_JSON"}};
                } else {
                    response = handle_command(command);
                }

                if (!send_json_line(parent_fd, response)) {
                    std::cerr << "[CONTROL] Không thể gửi phản hồi về cho phụ huynh" << std::endl;
                    close(parent_fd);
                    return;
                }
            }
        }

        const auto after_io = std::chrono::steady_clock::now();
        if (std::chrono::duration_cast<std::chrono::seconds>(after_io - last_sync).count() >= SYNC_INTERVAL_SECONDS) {
            if (!sync_pending_logs(parent_fd)) {
                std::cerr << "[SYNC] Lỗi đồng bộ; dữ liệu được bảo lưu" << std::endl;
                break;
            }
            last_sync = std::chrono::steady_clock::now();
        }
    }

    std::cout << "[CONTROL] Máy Phụ Huynh đã ngắt kết nối." << std::endl;
    close(parent_fd);
}

void tcp_control_worker() {
    const int listener_fd = create_tcp_listener();
    if (listener_fd < 0) return;

    std::cout << "[CONTROL] TCP IPC Server đang lắng nghe trên: " << LISTEN_IP
              << ":" << CONTROL_TCP_PORT << " (NDJSON)" << std::endl;

    while (true) {
        sockaddr_in parent_addr {};
        socklen_t parent_length = sizeof(parent_addr);
        const int parent_fd = accept(listener_fd, reinterpret_cast<sockaddr*>(&parent_addr), &parent_length);

        if (parent_fd < 0) {
            if (errno == EINTR) continue;
            std::perror("[CONTROL] accept");
            continue;
        }

        std::thread([parent_fd, parent_addr]() {
            serve_parent_connection(parent_fd, parent_addr);
        }).detach();
    }
}

// ============================================================================
// DNS FORWARDER ENGINE (UDP CỔNG 53)
// ============================================================================
int create_dns_socket() {
    const int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) {
        std::perror("[DNS] socket");
        return -1;
    }

    int reuse = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse));

    sockaddr_in address {};
    address.sin_family = AF_INET;
    address.sin_port = htons(DNS_PORT);

    if (inet_pton(AF_INET, LISTEN_IP, &address.sin_addr) != 1) {
        std::cerr << "[DNS] Invalid bind IP" << std::endl;
        close(fd);
        return -1;
    }

    if (bind(fd, reinterpret_cast<sockaddr*>(&address), sizeof(address)) < 0) {
        std::perror("[DNS] bind 0.0.0.0:53");
        close(fd);
        return -1;
    }

    return fd;
}

void handle_dns_packet(int dns_fd, const sockaddr_in& upstream_addr) {
    unsigned char query[BUFFER_SIZE];
    sockaddr_in client_addr {};
    socklen_t client_len = sizeof(client_addr);

    const ssize_t query_len = recvfrom(
        dns_fd, query, sizeof(query), 0,
        reinterpret_cast<sockaddr*>(&client_addr), &client_len
    );

    if (query_len < 0) {
        if (errno != EINTR) std::perror("[DNS] recvfrom client");
        return;
    }

    std::string queried_domain;
    const bool has_domain = extract_dns_qname(query, static_cast<std::size_t>(query_len), queried_domain);
    const std::string client_ip = sockaddr_ip(client_addr);

    std::cout << json({
        {"type", "DNS_QUERY"},
        {"client_ip", client_ip},
        {"client_port", ntohs(client_addr.sin_port)},
        {"domain", queried_domain.empty() ? "UNKNOWN" : queried_domain},
        {"bytes", query_len}
    }).dump() << std::endl;

    // 1. Kiểm tra nếu chế độ khóa mạng khẩn cấp toàn hệ thống (EMERGENCY_PAUSE) đang bật
    bool should_block = false;
    std::string block_reason = "";

    {
        std::unique_lock<std::shared_mutex> lock(state_mutex);

        if (emergency_pause) {
            should_block = true;
            block_reason = "EMERGENCY_LOCKDOWN";
        } else if (blocked_client_ips.count(client_ip) > 0) {
            // Thiết bị con cụ thể bị cắt mạng
            should_block = true;
            block_reason = "DEVICE_SPECIFIC_BLOCK";
        } else {
            // Kiểm tra danh sách trắng (Whitelist - Học tập)
            bool is_whitelisted = is_domain_in_set(queried_domain, whitelist);
            if (is_whitelisted) {
                should_block = false; // Luôn cho phép Whitelist
            } else if (is_curfew_active()) {
                // Đang trong giờ giới nghiêm ban đêm (Bedtime Curfew)
                should_block = true;
                block_reason = "BEDTIME_CURFEW";
            } else if (filter_enabled && has_domain && is_domain_in_set(queried_domain, blacklist)) {
                // Tên miền nằm trong danh sách cấm (Blacklist)
                should_block = true;
                block_reason = "BLACKLIST_REFUSED";
            } else {
                // Kiểm tra 5 ứng dụng di động: YouTube, Facebook, TikTok, Instagram, Mobile Legends
                std::string detected_app = detect_app_for_domain(queried_domain);
                if (!detected_app.empty()) {
                    auto& usage = client_app_usage[client_ip][detected_app];
                    if (usage.parent_blocked) {
                        should_block = true;
                        block_reason = "APP_PARENT_BLOCKED_" + detected_app;
                    } else if (usage.time_limit_minutes > 0 && usage.used_seconds >= (usage.time_limit_minutes * 60)) {
                        should_block = true;
                        block_reason = "APP_TIME_EXCEEDED_" + detected_app;
                    } else {
                        // Cho phép và tính toán thời gian sử dụng theo phiên thực tế
                        std::time_t now = std::time(nullptr);
                        if (usage.last_active_ts > 0) {
                            long diff = static_cast<long>(now - usage.last_active_ts);
                            if (diff > 0 && diff <= 120) {
                                usage.used_seconds += static_cast<int>(diff > 60 ? 60 : diff);
                            } else if (diff > 120) {
                                usage.used_seconds += 5; // Mở phiên mới
                            }
                        } else {
                            usage.used_seconds += 5;
                        }
                        usage.last_active_ts = now;

                        if (usage.time_limit_minutes > 0 && usage.used_seconds >= (usage.time_limit_minutes * 60)) {
                            std::cout << "[APP_LIMIT] IP " << client_ip << " đã hết hạn mức thời gian App " 
                                      << detected_app << " (" << usage.used_seconds << "s / " 
                                      << (usage.time_limit_minutes * 60) << "s)" << std::endl;
                        }
                    }
                }
            }
        }
    }

    // Xử lý khi bị chặn: Trả về mã REFUSED (0x05)
    if (should_block) {
        add_access_log(queried_domain, "BLOCKED", client_addr);

        std::cout << json({
            {"type", "DNS_BLOCKED"},
            {"domain", queried_domain},
            {"client_ip", client_ip},
            {"reason", block_reason}
        }).dump() << std::endl;

        const std::vector<unsigned char> refused = make_refused_response(query, static_cast<std::size_t>(query_len));
        if (!refused.empty()) {
            sendto(dns_fd, refused.data(), refused.size(), 0,
                   reinterpret_cast<sockaddr*>(&client_addr), client_len);
        }
        return;
    }

    // Trường hợp hợp lệ: Chuyển tiếp ra Upstream DNS (Google 8.8.8.8)
    add_access_log(queried_domain.empty() ? "UNKNOWN" : queried_domain, "ALLOWED", client_addr);

    int upstream_fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (upstream_fd < 0) {
        std::perror("[DNS] upstream socket");
        return;
    }

    const ssize_t sent = sendto(
        upstream_fd, query, query_len, 0,
        reinterpret_cast<const sockaddr*>(&upstream_addr), sizeof(upstream_addr)
    );

    if (sent != query_len) {
        std::perror("[DNS] sendto upstream");
        close(upstream_fd);
        return;
    }

    pollfd pfd {};
    pfd.fd = upstream_fd;
    pfd.events = POLLIN;

    const int ready = poll(&pfd, 1, TIMEOUT_MS);
    if (ready <= 0) {
        if (ready == 0) std::cerr << "[DNS] Timeout từ " << UPSTREAM_DNS_IP << std::endl;
        else std::perror("[DNS] poll upstream");
        close(upstream_fd);
        return;
    }

    unsigned char response[BUFFER_SIZE];
    sockaddr_in upstream_response {};
    socklen_t upstream_len = sizeof(upstream_response);

    const ssize_t response_len = recvfrom(
        upstream_fd, response, sizeof(response), 0,
        reinterpret_cast<sockaddr*>(&upstream_response), &upstream_len
    );

    if (response_len > 0) {
        sendto(dns_fd, response, response_len, 0,
               reinterpret_cast<sockaddr*>(&client_addr), client_len);
    }

    close(upstream_fd);
}

// ============================================================================
// HÀM MAIN KHỞI CHẠY HỆ THỐNG
// ============================================================================
int main() {
    std::signal(SIGPIPE, SIG_IGN);

    const int dns_fd = create_dns_socket();
    if (dns_fd < 0) {
        return 1;
    }

    sockaddr_in upstream_addr {};
    upstream_addr.sin_family = AF_INET;
    upstream_addr.sin_port = htons(DNS_PORT);
    inet_pton(AF_INET, UPSTREAM_DNS_IP, &upstream_addr.sin_addr);

    // Khởi chạy luồng TCP Control Server (Cổng 9000)
    std::thread control_thread(tcp_control_worker);
    control_thread.detach();

    // Khởi chạy luồng giám sát giờ giới nghiêm & ngắt Internet định kỳ
    std::thread curfew_watchdog([]() {
        while (true) {
            std::this_thread::sleep_for(std::chrono::seconds(10));
            sync_curfew_firewall();
        }
    });
    curfew_watchdog.detach();

    std::cout << "===========================================================" << std::endl;
    std::cout << "🛡️  FAMILYGUARD VPS CORE - DNS FORWARDER & TCP IPC SERVER" << std::endl;
    std::cout << "🌐 Giao thức DNS: UDP " << LISTEN_IP << ":" << DNS_PORT << " -> Upstream " << UPSTREAM_DNS_IP << std::endl;
    std::cout << "⚡ Giao thức Điều khiển: TCP " << LISTEN_IP << ":" << CONTROL_TCP_PORT << " (NDJSON)" << std::endl;
    std::cout << "🔑 Mật khẩu xác thực: " << AUTH_PASS << std::endl;
    std::cout << "===========================================================" << std::endl;

    while (true) {
        pollfd pfd {};
        pfd.fd = dns_fd;
        pfd.events = POLLIN;

        const int ready = poll(&pfd, 1, -1);
        if (ready < 0) {
            if (errno == EINTR) continue;
            std::perror("[DNS] poll");
            break;
        }

        if (pfd.revents & POLLIN) {
            handle_dns_packet(dns_fd, upstream_addr);
        }
    }

    close(dns_fd);
    return 0;
}
