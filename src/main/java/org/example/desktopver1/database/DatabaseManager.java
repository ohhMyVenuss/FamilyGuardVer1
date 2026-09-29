package org.example.desktopver1.database;

import org.example.desktopver1.model.AccessLog;
import org.example.desktopver1.model.CategoryRule;
import org.example.desktopver1.model.Device;
import org.example.desktopver1.model.TimeSchedule;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Quản trị cơ sở dữ liệu SQLite cho ứng dụng FamilyGuard:
 * - Tự động khởi tạo cấu trúc bảng (Schema).
 * - Nạp dữ liệu giả lập (Seed Mock Data) phong phú khi cơ sở dữ liệu mới khởi tạo.
 * - Cung cấp đầy đủ các thao tác CRUD phục vụ lưu vết mạng, luật lọc và danh sách thiết bị.
 */
public class DatabaseManager {

    public static final String DEFAULT_DB_URL = "jdbc:sqlite:familyguard.db";
    private static DatabaseManager instance;

    private final String dbUrl;

    public DatabaseManager() {
        this(DEFAULT_DB_URL);
    }

    public DatabaseManager(String dbUrl) {
        this.dbUrl = dbUrl;
        initDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager(DEFAULT_DB_URL);
        }
        return instance;
    }

    public static synchronized void setInstance(DatabaseManager customInstance) {
        instance = customInstance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    public String getDbUrl() {
        return dbUrl;
    }

    /**
     * Khởi tạo cấu trúc các bảng dữ liệu nếu chưa tồn tại
     */
    public void initDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // 1. Bảng cài đặt hệ thống (Key-Value)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS system_settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
            """);

            // 2. Bảng quản lý thiết bị của trẻ
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS devices (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    ip_address TEXT NOT NULL,
                    mac_address TEXT NOT NULL,
                    status TEXT NOT NULL,
                    is_blocked INTEGER NOT NULL DEFAULT 0,
                    time_spent_today TEXT NOT NULL
                );
            """);

            // 3. Bảng quy tắc phân loại web
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS category_rules (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    icon TEXT NOT NULL,
                    description TEXT NOT NULL,
                    is_blocked INTEGER NOT NULL DEFAULT 1,
                    blocked_count INTEGER NOT NULL DEFAULT 0
                );
            """);

            // 4. Bảng danh sách đen (Blacklist domain)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS blacklist_domains (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    domain TEXT UNIQUE NOT NULL,
                    created_at TEXT NOT NULL
                );
            """);

            // 5. Bảng danh sách trắng (Whitelist domain)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS whitelist_domains (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    domain TEXT UNIQUE NOT NULL,
                    created_at TEXT NOT NULL
                );
            """);

            // 6. Bảng nhật ký truy cập mạng (Access Logs)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS access_logs (
                    id TEXT PRIMARY KEY,
                    timestamp TEXT NOT NULL,
                    device_name TEXT NOT NULL,
                    domain TEXT NOT NULL,
                    category TEXT NOT NULL,
                    action TEXT NOT NULL,
                    reason TEXT NOT NULL
                );
            """);

            // 7. Bảng thời gian biểu & giới nghiêm
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS time_schedules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    day_group TEXT NOT NULL,
                    daily_limit_hours REAL NOT NULL,
                    curfew_start TEXT NOT NULL,
                    curfew_end TEXT NOT NULL,
                    is_active INTEGER NOT NULL DEFAULT 1
                );
            """);

            // Tự động kiểm tra và thêm dữ liệu giả lập mẫu nếu bảng rỗng
            seedMockDataIfEmpty(conn);

        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Lỗi khởi tạo SQLite: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Nạp dữ liệu giả lập mẫu vào SQLite nếu cơ sở dữ liệu còn trống
     */
    public void seedMockDataIfEmpty(Connection conn) throws SQLException {
        // Kiểm tra xem đã từng seed dữ liệu hoặc người dùng đã chủ động làm sạch database chưa
        try (Statement checkStmt = conn.createStatement();
             ResultSet rs = checkStmt.executeQuery("SELECT value FROM system_settings WHERE key = 'mock_seeded'")) {
            if (rs.next() && "true".equalsIgnoreCase(rs.getString("value"))) {
                return; // Đã từng nạp mẫu hoặc người dùng đã chủ động thiết lập/xóa trắng
            }
        }

        // Kiểm tra xem có thiết bị nào chưa
        try (Statement checkStmt = conn.createStatement();
             ResultSet rs = checkStmt.executeQuery("SELECT COUNT(*) FROM devices")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // Đã có dữ liệu, không cần seed lại
            }
        }

        // 1. Cài đặt hệ thống
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO system_settings (key, value) VALUES (?, ?)")) {
            ps.setString(1, "mock_seeded");
            ps.setString(2, "true");
            ps.executeUpdate();

            ps.setString(1, "protection_active");
            ps.setString(2, "true");
            ps.executeUpdate();

            ps.setString(1, "emergency_pause");
            ps.setString(2, "false");
            ps.executeUpdate();

            ps.setString(1, "safesearch_enabled");
            ps.setString(2, "true");
            ps.executeUpdate();

            ps.setString(1, "family_dns");
            ps.setString(2, "Cloudflare 1.1.1.3 (Chặn mã độc & Nội dung 18+)");
            ps.executeUpdate();
        }

        // 2. Dữ liệu thiết bị giả lập
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO devices (id, name, type, ip_address, mac_address, status, is_blocked, time_spent_today) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            insertDeviceRow(ps, "DEV-01", "PC Phòng Học - Bé Minh", "Máy tính để bàn", "192.168.1.102", "3C:7C:3F:81:4A:22", "Trực tuyến", 0, "2h 45m");
            insertDeviceRow(ps, "DEV-02", "iPad Pro - Bé Lan", "Máy tính bảng", "192.168.1.108", "E4:5F:01:8B:3C:A2", "Trực tuyến", 0, "1h 15m");
            insertDeviceRow(ps, "DEV-03", "Điện thoại Samsung - Minh", "Điện thoại", "192.168.1.115", "8A:92:B4:71:0D:33", "Ngoại tuyến", 0, "0h 30m");
            insertDeviceRow(ps, "DEV-04", "Laptop Asus - Học Tập", "Laptop", "192.168.1.120", "22:C4:6E:9A:1F:B8", "Trực tuyến", 0, "3h 10m");
        }

        // 3. Quy tắc danh mục
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO category_rules (id, name, icon, description, is_blocked, blocked_count) VALUES (?, ?, ?, ?, ?, ?)")) {
            insertRuleRow(ps, "CAT-01", "Nội dung người lớn (18+)", "🔞", "Tự động chặn các trang có nội dung khiêu dâm, độc hại", 1, 42);
            insertRuleRow(ps, "CAT-02", "Cờ bạc & Cá cược online", "🎲", "Chặn các trang cá độ bóng đá, tài xỉu, sòng bài ảo", 1, 28);
            insertRuleRow(ps, "CAT-03", "Game online & Nạp thẻ", "🎮", "Chặn truy cập máy chủ game và cổng nạp game khi học", 1, 19);
            insertRuleRow(ps, "CAT-04", "Mạng xã hội & Hẹn hò", "💬", "Giới hạn Facebook, TikTok, Tinder, Zalo Web trong giờ học", 0, 15);
            insertRuleRow(ps, "CAT-05", "Bạo lực, Vũ khí & Chất cấm", "⚠️", "Ngăn chặn các diễn đàn, trang kích động tiêu cực", 1, 9);
            insertRuleRow(ps, "CAT-06", "Video ngắn (Reels, TikTok, Shorts)", "▶️", "Chặn thuật toán gây nghiện video ngắn làm mất tập trung", 1, 34);
        }

        // 4. Blacklist mẫu
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO blacklist_domains (domain, created_at) VALUES (?, ?)")) {
            for (String domain : List.of("tiktok.com", "gamevui.vn", "roblox.com", "discord.com", "steamcommunity.com", "88bet-vn.com")) {
                ps.setString(1, domain);
                ps.setString(2, now);
                ps.executeUpdate();
            }
        }

        // 5. Whitelist mẫu
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO whitelist_domains (domain, created_at) VALUES (?, ?)")) {
            for (String domain : List.of("khanacademy.org", "olm.vn", "vietjack.com", "scratch.mit.edu", "hocmai.vn", "coursera.org")) {
                ps.setString(1, domain);
                ps.setString(2, now);
                ps.executeUpdate();
            }
        }

        // 6. Nhật ký truy cập mẫu
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO access_logs (id, timestamp, device_name, domain, category, action, reason) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            insertLogRow(ps, "LOG-01", "15:20:12 - 24/09", "PC Phòng Học - Bé Minh", "gamevui.vn/choi-game-ban-sung", "Game online", "ĐÃ CHẶN", "Thuộc danh sách Blacklist");
            insertLogRow(ps, "LOG-02", "15:18:45 - 24/09", "iPad Pro - Bé Lan", "olm.vn/toan-lop-6", "Học tập", "CHO PHÉP", "Thuộc danh sách Whitelist");
            insertLogRow(ps, "LOG-03", "15:12:03 - 24/09", "Laptop Asus - Học Tập", "tiktok.com/@trendhot", "Video ngắn", "ĐÃ CHẶN", "Quy tắc danh mục đang bật");
            insertLogRow(ps, "LOG-04", "15:05:30 - 24/09", "PC Phòng Học - Bé Minh", "vietjack.com/ly-lop-8", "Học tập", "CHO PHÉP", "Website giáo dục");
            insertLogRow(ps, "LOG-05", "14:48:19 - 24/09", "iPad Pro - Bé Lan", "roblox.com/games", "Game online", "ĐÃ CHẶN", "Khung giờ học tập");
            insertLogRow(ps, "LOG-06", "14:30:11 - 24/09", "PC Phòng Học - Bé Minh", "youtube.com/watch?v=bai-giang", "Giải trí / Học tập", "CHO PHÉP", "Bật chế độ YouTube SafeSearch");
            insertLogRow(ps, "LOG-07", "13:58:04 - 24/09", "Điện thoại Samsung - Minh", "vn-bet99.com/live", "Cờ bạc", "ĐÃ CHẶN", "Bộ lọc nội dung độc hại");
            insertLogRow(ps, "LOG-08", "13:22:51 - 24/09", "Laptop Asus - Học Tập", "scratch.mit.edu/projects", "Lập trình", "CHO PHÉP", "Website giáo dục");
        }

        // 7. Lịch sử dụng
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO time_schedules (day_group, daily_limit_hours, curfew_start, curfew_end, is_active) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, "Ngày trong tuần (T2 - T6)");
            ps.setDouble(2, 2.0);
            ps.setString(3, "21:30");
            ps.setString(4, "06:00");
            ps.setInt(5, 1);
            ps.executeUpdate();

            ps.setString(1, "Cuối tuần (T7 & CN)");
            ps.setDouble(2, 4.0);
            ps.setString(3, "22:30");
            ps.setString(4, "06:30");
            ps.setInt(5, 1);
            ps.executeUpdate();
        }
    }

    private void insertDeviceRow(PreparedStatement ps, String id, String name, String type, String ip, String mac, String status, int blocked, String time) throws SQLException {
        ps.setString(1, id);
        ps.setString(2, name);
        ps.setString(3, type);
        ps.setString(4, ip);
        ps.setString(5, mac);
        ps.setString(6, status);
        ps.setInt(7, blocked);
        ps.setString(8, time);
        ps.executeUpdate();
    }

    private void insertRuleRow(PreparedStatement ps, String id, String name, String icon, String desc, int blocked, int count) throws SQLException {
        ps.setString(1, id);
        ps.setString(2, name);
        ps.setString(3, icon);
        ps.setString(4, desc);
        ps.setInt(5, blocked);
        ps.setInt(6, count);
        ps.executeUpdate();
    }

    private void insertLogRow(PreparedStatement ps, String id, String time, String device, String domain, String cat, String action, String reason) throws SQLException {
        ps.setString(1, id);
        ps.setString(2, time);
        ps.setString(3, device);
        ps.setString(4, domain);
        ps.setString(5, cat);
        ps.setString(6, action);
        ps.setString(7, reason);
        ps.executeUpdate();
    }

    // ==========================================
    // CÁC THAO TÁC TRUY VẤN VÀ CẬP NHẬT (CRUD)
    // ==========================================

    public String getSetting(String key, String defaultValue) {
        String sql = "SELECT value FROM system_settings WHERE key = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return defaultValue;
    }

    public void setSetting(String key, String value) {
        String sql = "INSERT OR REPLACE INTO system_settings (key, value) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Device> getAllDevices() {
        List<Device> list = new ArrayList<>();
        String sql = "SELECT * FROM devices ORDER BY id ASC";
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Device(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getString("ip_address"),
                        rs.getString("mac_address"),
                        rs.getString("status"),
                        rs.getInt("is_blocked") == 1,
                        rs.getString("time_spent_today")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void saveDevice(Device d) {
        String sql = "INSERT OR REPLACE INTO devices (id, name, type, ip_address, mac_address, status, is_blocked, time_spent_today) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, d.getId());
            ps.setString(2, d.getName());
            ps.setString(3, d.getType());
            ps.setString(4, d.getIpAddress());
            ps.setString(5, d.getMacAddress());
            ps.setString(6, d.getStatus());
            ps.setInt(7, d.isBlocked() ? 1 : 0);
            ps.setString(8, d.getTimeSpentToday());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateDeviceTimeSpent(String id, String timeSpent) {
        String sql = "UPDATE devices SET time_spent_today = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, timeSpent);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateDeviceBlock(String id, boolean blocked) {
        String sql = "UPDATE devices SET is_blocked = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, blocked ? 1 : 0);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateAllDevicesBlock(boolean blocked) {
        String sql = "UPDATE devices SET is_blocked = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, blocked ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteDevice(String id) {
        String sql = "DELETE FROM devices WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void clearAllDevices() {
        String sql = "DELETE FROM devices";
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<CategoryRule> getAllCategoryRules() {
        List<CategoryRule> list = new ArrayList<>();
        String sql = "SELECT * FROM category_rules ORDER BY id ASC";
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new CategoryRule(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("icon"),
                        rs.getString("description"),
                        rs.getInt("is_blocked") == 1,
                        rs.getInt("blocked_count")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void updateCategoryRule(String id, boolean blocked) {
        String sql = "UPDATE category_rules SET is_blocked = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, blocked ? 1 : 0);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getAllBlacklistDomains() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT domain FROM blacklist_domains ORDER BY id DESC";
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(rs.getString("domain"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void insertBlacklistDomain(String domain) {
        String sql = "INSERT OR IGNORE INTO blacklist_domains (domain, created_at) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, domain);
            ps.setString(2, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteBlacklistDomain(String domain) {
        String sql = "DELETE FROM blacklist_domains WHERE domain = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, domain);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getAllWhitelistDomains() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT domain FROM whitelist_domains ORDER BY id DESC";
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(rs.getString("domain"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void insertWhitelistDomain(String domain) {
        String sql = "INSERT OR IGNORE INTO whitelist_domains (domain, created_at) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, domain);
            ps.setString(2, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteWhitelistDomain(String domain) {
        String sql = "DELETE FROM whitelist_domains WHERE domain = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, domain);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<AccessLog> getAllAccessLogs() {
        List<AccessLog> list = new ArrayList<>();
        String sql = "SELECT * FROM access_logs ORDER BY rowid DESC";
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new AccessLog(
                        rs.getString("id"),
                        rs.getString("timestamp"),
                        rs.getString("device_name"),
                        rs.getString("domain"),
                        rs.getString("category"),
                        rs.getString("action"),
                        rs.getString("reason")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void insertAccessLog(AccessLog log) {
        String sql = "INSERT OR REPLACE INTO access_logs (id, timestamp, device_name, domain, category, action, reason) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, log.getId());
            ps.setString(2, log.getTimestamp());
            ps.setString(3, log.getDeviceName());
            ps.setString(4, log.getDomain());
            ps.setString(5, log.getCategory());
            ps.setString(6, log.getAction());
            ps.setString(7, log.getReason());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void insertAccessLogs(List<AccessLog> logs) {
        if (logs == null || logs.isEmpty()) return;
        String sql = "INSERT OR REPLACE INTO access_logs (id, timestamp, device_name, domain, category, action, reason) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (AccessLog log : logs) {
                    ps.setString(1, log.getId());
                    ps.setString(2, log.getTimestamp());
                    ps.setString(3, log.getDeviceName());
                    ps.setString(4, log.getDomain());
                    ps.setString(5, log.getCategory());
                    ps.setString(6, log.getAction());
                    ps.setString(7, log.getReason());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Lỗi lưu batch access_logs: " + e.getMessage());
        }
    }

    public void clearAllLogs() {
        String sql = "DELETE FROM access_logs";
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<TimeSchedule> getAllTimeSchedules() {
        List<TimeSchedule> list = new ArrayList<>();
        String sql = "SELECT * FROM time_schedules ORDER BY id ASC";
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new TimeSchedule(
                        rs.getString("day_group"),
                        rs.getDouble("daily_limit_hours"),
                        rs.getString("curfew_start"),
                        rs.getString("curfew_end"),
                        rs.getInt("is_active") == 1
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Dọn sạch toàn bộ database (Dùng cho unit test)
     */
    public void dropAllTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS system_settings");
            stmt.execute("DROP TABLE IF EXISTS devices");
            stmt.execute("DROP TABLE IF EXISTS category_rules");
            stmt.execute("DROP TABLE IF EXISTS blacklist_domains");
            stmt.execute("DROP TABLE IF EXISTS whitelist_domains");
            stmt.execute("DROP TABLE IF EXISTS access_logs");
            stmt.execute("DROP TABLE IF EXISTS time_schedules");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
