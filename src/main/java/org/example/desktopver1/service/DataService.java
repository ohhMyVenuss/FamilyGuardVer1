package org.example.desktopver1.service;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.example.desktopver1.model.AccessLog;
import org.example.desktopver1.model.CategoryRule;
import org.example.desktopver1.model.Device;
import org.example.desktopver1.model.TimeSchedule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service quản trị trạng thái và dữ liệu nghiệp vụ của ứng dụng bảo vệ truy cập mạng.
 * 
 * LƯU Ý CHO MÔN HỌC LẬP TRÌNH MẠNG:
 * Lớp này được thiết kế theo mô hình Service Layer. Khi tích hợp với các module mạng thực tế:
 * - Có thể khởi tạo Socket Client / TCP connection đến Router/Proxy Server hoặc Agent con trên máy trẻ em.
 * - Các phương thức như toggleDeviceBlock(), toggleEmergencyPause(), addBlacklistDomain() 
 *   sẽ gửi gói tin điều khiển (Packet/JSON message qua Socket) đến Server giám sát mạng.
 */
public class DataService {

    private static DataService instance;

    // Trạng thái toàn cục của hệ thống
    private final BooleanProperty protectionActive = new SimpleBooleanProperty(true);
    private final BooleanProperty emergencyPause = new SimpleBooleanProperty(false);
    private final BooleanProperty safeSearchEnabled = new SimpleBooleanProperty(true);

    // Danh sách dữ liệu tương tác giao diện
    private final ObservableList<Device> devices = FXCollections.observableArrayList();
    private final ObservableList<CategoryRule> categoryRules = FXCollections.observableArrayList();
    private final ObservableList<String> blacklistDomains = FXCollections.observableArrayList();
    private final ObservableList<String> whitelistDomains = FXCollections.observableArrayList();
    private final ObservableList<AccessLog> accessLogs = FXCollections.observableArrayList();
    private final ObservableList<TimeSchedule> timeSchedules = FXCollections.observableArrayList();

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss - dd/MM");

    private DataService() {
        initMockData();
    }

    public static synchronized DataService getInstance() {
        if (instance == null) {
            instance = new DataService();
        }
        return instance;
    }

    /**
     * Khởi tạo bộ dữ liệu mẫu sát thực tế phục vụ hiển thị và demo đồ án
     */
    private void initMockData() {
        // 1. Dữ liệu thiết bị
        devices.add(new Device("DEV-01", "PC Phòng Học - Bé Minh", "Máy tính để bàn", "192.168.1.102", "3C:7C:3F:81:4A:22", "Trực tuyến", false, "2h 45m"));
        devices.add(new Device("DEV-02", "iPad Pro - Bé Lan", "Máy tính bảng", "192.168.1.108", "E4:5F:01:8B:3C:A2", "Trực tuyến", false, "1h 15m"));
        devices.add(new Device("DEV-03", "Điện thoại Samsung - Minh", "Điện thoại", "192.168.1.115", "8A:92:B4:71:0D:33", "Ngoại tuyến", false, "0h 30m"));
        devices.add(new Device("DEV-04", "Laptop Asus - Học Tập", "Laptop", "192.168.1.120", "22:C4:6E:9A:1F:B8", "Trực tuyến", false, "3h 10m"));

        // 2. Dữ liệu quy tắc phân loại web
        categoryRules.add(new CategoryRule("CAT-01", "Nội dung người lớn (18+)", "🔞", "Tự động chặn các trang có nội dung khiêu dâm, độc hại", true, 42));
        categoryRules.add(new CategoryRule("CAT-02", "Cờ bạc & Cá cược online", "🎲", "Chặn các trang cá độ bóng đá, tài xỉu, sòng bài ảo", true, 28));
        categoryRules.add(new CategoryRule("CAT-03", "Game online & Nạp thẻ", "🎮", "Chặn truy cập máy chủ game và cổng nạp game khi học", true, 19));
        categoryRules.add(new CategoryRule("CAT-04", "Mạng xã hội & Hẹn hò", "💬", "Giới hạn Facebook, TikTok, Tinder, Zalo Web trong giờ học", false, 15));
        categoryRules.add(new CategoryRule("CAT-05", "Bạo lực, Vũ khí & Chất cấm", "⚠️", "Ngăn chặn các diễn đàn, trang kích động tiêu cực", true, 9));
        categoryRules.add(new CategoryRule("CAT-06", "Video ngắn (Reels, TikTok, Shorts)", "▶️", "Chặn thuật toán gây nghiện video ngắn làm mất tập trung", true, 34));

        // 3. Blacklist mẫu
        blacklistDomains.addAll(
                "tiktok.com",
                "gamevui.vn",
                "roblox.com",
                "discord.com",
                "steamcommunity.com",
                "88bet-vn.com"
        );

        // 4. Whitelist mẫu (Các cổng học tập ưu tiên)
        whitelistDomains.addAll(
                "khanacademy.org",
                "olm.vn",
                "vietjack.com",
                "scratch.mit.edu",
                "hocmai.vn",
                "coursera.org"
        );

        // 5. Nhật ký truy cập mẫu
        accessLogs.add(new AccessLog("LOG-01", "15:20:12 - 24/09", "PC Phòng Học - Bé Minh", "gamevui.vn/choi-game-ban-sung", "Game online", "ĐÃ CHẶN", "Thuộc danh sách Blacklist"));
        accessLogs.add(new AccessLog("LOG-02", "15:18:45 - 24/09", "iPad Pro - Bé Lan", "olm.vn/toan-lop-6", "Học tập", "CHO PHÉP", "Thuộc danh sách Whitelist"));
        accessLogs.add(new AccessLog("LOG-03", "15:12:03 - 24/09", "Laptop Asus - Học Tập", "tiktok.com/@trendhot", "Video ngắn", "ĐÃ CHẶN", "Quy tắc danh mục đang bật"));
        accessLogs.add(new AccessLog("LOG-04", "15:05:30 - 24/09", "PC Phòng Học - Bé Minh", "vietjack.com/ly-lop-8", "Học tập", "CHO PHÉP", "Website giáo dục"));
        accessLogs.add(new AccessLog("LOG-05", "14:48:19 - 24/09", "iPad Pro - Bé Lan", "roblox.com/games", "Game online", "ĐÃ CHẶN", "Khung giờ học tập"));
        accessLogs.add(new AccessLog("LOG-06", "14:30:11 - 24/09", "PC Phòng Học - Bé Minh", "youtube.com/watch?v=bai-giang", "Giải trí / Học tập", "CHO PHÉP", "Bật chế độ YouTube SafeSearch"));
        accessLogs.add(new AccessLog("LOG-07", "13:58:04 - 24/09", "Điện thoại Samsung - Minh", "vn-bet99.com/live", "Cờ bạc", "ĐÃ CHẶN", "Bộ lọc nội dung độc hại"));
        accessLogs.add(new AccessLog("LOG-08", "13:22:51 - 24/09", "Laptop Asus - Học Tập", "scratch.mit.edu/projects", "Lập trình", "CHO PHÉP", "Website giáo dục"));

        // 6. Lịch sử dụng mẫu
        timeSchedules.add(new TimeSchedule("Ngày trong tuần (T2 - T6)", 2.0, "21:30", "06:00", true));
        timeSchedules.add(new TimeSchedule("Cuối tuần (T7 & CN)", 4.0, "22:30", "06:30", true));
    }

    // --- Các thao tác nghiệp vụ ---

    public void toggleProtection(boolean active) {
        this.protectionActive.set(active);
        addSystemLog("Hệ thống bảo vệ " + (active ? "ĐÃ ĐƯỢC BẬT" : "ĐÃ TẠM DỪNG"), "Hệ thống", active ? "CHO PHÉP" : "CẢNH BÁO");
    }

    public void toggleEmergencyPause(boolean pause) {
        this.emergencyPause.set(pause);
        for (Device d : devices) {
            d.setBlocked(pause);
        }
        addSystemLog("Chế độ TẠM DỪNG MẠNG KHẨN CẤP: " + (pause ? "KÍCH HOẠT" : "ĐÃ HỦY"), "Tất cả thiết bị", pause ? "ĐÃ CHẶN" : "CHO PHÉP");
    }

    public void toggleDeviceBlock(Device device) {
        boolean newState = !device.isBlocked();
        device.setBlocked(newState);
        addSystemLog((newState ? "Ngắt mạng internet: " : "Khôi phục mạng: ") + device.getName(), device.getName(), newState ? "ĐÃ CHẶN" : "CHO PHÉP");
    }

    public void addBlacklistDomain(String domain) {
        String clean = domain.trim().toLowerCase().replaceAll("^https?://", "").replaceAll("/.*", "");
        if (!clean.isEmpty() && !blacklistDomains.contains(clean)) {
            blacklistDomains.add(clean);
            addSystemLog("Thêm tên miền chặn: " + clean, "Bộ lọc", "ĐÃ CHẶN");
        }
    }

    public void removeBlacklistDomain(String domain) {
        blacklistDomains.remove(domain);
        addSystemLog("Đã gỡ tên miền khỏi danh sách chặn: " + domain, "Bộ lọc", "CHO PHÉP");
    }

    public void addWhitelistDomain(String domain) {
        String clean = domain.trim().toLowerCase().replaceAll("^https?://", "").replaceAll("/.*", "");
        if (!clean.isEmpty() && !whitelistDomains.contains(clean)) {
            whitelistDomains.add(clean);
            addSystemLog("Thêm tên miền cho phép: " + clean, "Bộ lọc", "CHO PHÉP");
        }
    }

    public void removeWhitelistDomain(String domain) {
        whitelistDomains.remove(domain);
        addSystemLog("Đã xóa khỏi danh sách cho phép: " + domain, "Bộ lọc", "CẢNH BÁO");
    }

    public void toggleCategoryRule(CategoryRule rule) {
        boolean newState = !rule.isBlocked();
        rule.setBlocked(newState);
        addSystemLog((newState ? "Kích hoạt chặn danh mục: " : "Bỏ chặn danh mục: ") + rule.getName(), "Bộ lọc danh mục", newState ? "ĐÃ CHẶN" : "CHO PHÉP");
    }

    public void addSystemLog(String description, String source, String action) {
        String now = LocalDateTime.now().format(timeFormatter);
        accessLogs.add(0, new AccessLog("LOG-" + (accessLogs.size() + 1), now, source, description, "Quản trị", action, "Thao tác phụ huynh"));
    }

    public void clearLogs() {
        accessLogs.clear();
    }

    // --- Getters & Properties ---

    public boolean isProtectionActive() {
        return protectionActive.get();
    }

    public BooleanProperty protectionActiveProperty() {
        return protectionActive;
    }

    public boolean isEmergencyPause() {
        return emergencyPause.get();
    }

    public BooleanProperty emergencyPauseProperty() {
        return emergencyPause;
    }

    public boolean isSafeSearchEnabled() {
        return safeSearchEnabled.get();
    }

    public BooleanProperty safeSearchEnabledProperty() {
        return safeSearchEnabled;
    }

    public ObservableList<Device> getDevices() {
        return devices;
    }

    public ObservableList<CategoryRule> getCategoryRules() {
        return categoryRules;
    }

    public ObservableList<String> getBlacklistDomains() {
        return blacklistDomains;
    }

    public ObservableList<String> getWhitelistDomains() {
        return whitelistDomains;
    }

    public ObservableList<AccessLog> getAccessLogs() {
        return accessLogs;
    }

    public ObservableList<TimeSchedule> getTimeSchedules() {
        return timeSchedules;
    }

    public int getOnlineDeviceCount() {
        int count = 0;
        for (Device d : devices) {
            if ("Trực tuyến".equalsIgnoreCase(d.getStatus())) {
                count++;
            }
        }
        return count;
    }

    public int getTotalBlockedEventsCount() {
        int count = 0;
        for (AccessLog log : accessLogs) {
            if ("ĐÃ CHẶN".equalsIgnoreCase(log.getAction())) {
                count++;
            }
        }
        return count;
    }
}
