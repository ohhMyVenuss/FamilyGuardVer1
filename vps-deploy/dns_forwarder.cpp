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
                std::cout << "[DEVICE_BLOCK] Đã ngắt mạng thiết bị IP: " << ip << std::endl;
            } else {
                blocked_client_ips.erase(ip);
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
        return {{"success", true}, {"action", action}, {"enabled", enabled}};
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
            blocked_client_ips.clear();
            for (const auto& item : command["blocked_ips"]) {
                if (item.is_string()) {
                    blocked_client_ips.insert(item.get<std::string>());
                }
            }
        }
        std::cout << "[SYNC_RULES] Đã đồng bộ từ JavaFX: " 
                  << blacklist.size() << " tên miền cấm, " 
                  << whitelist.size() << " tên miền học tập, " 
                  << blocked_client_ips.size() << " IP bị chặn." << std::endl;
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

bool sync_pending_logs(int parent_fd) {
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
        std::shared_lock<std::shared_mutex> lock(state_mutex);

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
