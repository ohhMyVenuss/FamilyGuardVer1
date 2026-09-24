package org.example.desktopver1.database;

import org.example.desktopver1.model.AccessLog;
import org.example.desktopver1.model.CategoryRule;
import org.example.desktopver1.model.Device;
import org.example.desktopver1.model.TimeSchedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử Cơ sở dữ liệu SQLite & Dữ liệu giả lập (DatabaseManager)")
class DatabaseManagerTest {

    private static final String TEST_DB_FILE = "target/test_familyguard.db";
    private static final String TEST_DB_URL = "jdbc:sqlite:" + TEST_DB_FILE;
    private DatabaseManager dbManager;

    @BeforeEach
    void setUp() {
        // Xóa file test cũ nếu có
        File file = new File(TEST_DB_FILE);
        if (file.exists()) {
            file.delete();
        }

        // Khởi tạo db test mới
        dbManager = new DatabaseManager(TEST_DB_URL);
    }

    @AfterEach
    void tearDown() {
        File file = new File(TEST_DB_FILE);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    @DisplayName("Kiểm tra kết nối và tạo đầy đủ các bảng dữ liệu trong SQLite")
    void testTablesCreated() throws Exception {
        try (Connection conn = dbManager.getConnection(); Statement stmt = conn.createStatement()) {
            String[] expectedTables = {
                    "system_settings",
                    "devices",
                    "category_rules",
                    "blacklist_domains",
                    "whitelist_domains",
                    "access_logs",
                    "time_schedules"
            };

            for (String table : expectedTables) {
                try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
                    assertTrue(rs.next(), "Bảng " + table + " phải tồn tại trong SQLite.");
                }
            }
        }
    }

    @Test
    @DisplayName("Kiểm tra việc nạp dữ liệu giả lập (Seed Mock Data) vào SQLite")
    void testMockDataSeeding() {
        // 1. Kiểm tra thiết bị giả lập
        List<Device> devices = dbManager.getAllDevices();
        assertNotNull(devices);
        assertEquals(4, devices.size(), "Phải có đúng 4 thiết bị mẫu ban đầu.");
        assertTrue(devices.stream().anyMatch(d -> d.getName().contains("Bé Minh")));
        assertTrue(devices.stream().anyMatch(d -> d.getName().contains("Bé Lan")));

        // 2. Kiểm tra quy tắc danh mục giả lập
        List<CategoryRule> rules = dbManager.getAllCategoryRules();
        assertEquals(6, rules.size(), "Phải có đúng 6 quy tắc lọc theo danh mục.");
        assertTrue(rules.stream().anyMatch(r -> r.getName().contains("Nội dung người lớn")));
        assertTrue(rules.stream().anyMatch(r -> r.getName().contains("Cờ bạc")));

        // 3. Kiểm tra Blacklist & Whitelist giả lập
        List<String> blacklist = dbManager.getAllBlacklistDomains();
        assertTrue(blacklist.size() >= 5);
        assertTrue(blacklist.contains("tiktok.com"));
        assertTrue(blacklist.contains("gamevui.vn"));

        List<String> whitelist = dbManager.getAllWhitelistDomains();
        assertTrue(whitelist.size() >= 5);
        assertTrue(whitelist.contains("khanacademy.org"));
        assertTrue(whitelist.contains("olm.vn"));

        // 4. Kiểm tra Logs giả lập
        List<AccessLog> logs = dbManager.getAllAccessLogs();
        assertEquals(8, logs.size(), "Phải có 8 bản ghi nhật ký mẫu.");

        // 5. Kiểm tra Settings giả lập
        assertEquals("true", dbManager.getSetting("protection_active", ""));
        assertEquals("false", dbManager.getSetting("emergency_pause", ""));

        // 6. Kiểm tra TimeSchedules giả lập
        List<TimeSchedule> schedules = dbManager.getAllTimeSchedules();
        assertEquals(2, schedules.size(), "Phải có 2 cấu hình lịch dùng máy.");
    }

    @Test
    @DisplayName("Kiểm tra các thao tác Thêm, Sửa, Khóa mạng thiết bị (Device CRUD)")
    void testDeviceCrud() {
        Device newDev = new Device("DEV-99", "Laptop Thử Nghiệm", "Laptop", "192.168.1.199", "11:22:33:44:55:66", "Trực tuyến", false, "0h 10m");
        dbManager.saveDevice(newDev);

        List<Device> list = dbManager.getAllDevices();
        assertEquals(5, list.size());

        // Kiểm tra ngắt kết nối mạng của thiết bị
        dbManager.updateDeviceBlock("DEV-99", true);
        List<Device> updatedList = dbManager.getAllDevices();
        Device fetched = updatedList.stream().filter(d -> d.getId().equals("DEV-99")).findFirst().orElse(null);
        assertNotNull(fetched);
        assertTrue(fetched.isBlocked(), "Thiết bị DEV-99 phải ở trạng thái bị ngắt mạng (blocked).");

        // Khôi phục mạng
        dbManager.updateDeviceBlock("DEV-99", false);
        updatedList = dbManager.getAllDevices();
        Device restored = updatedList.stream().filter(d -> d.getId().equals("DEV-99")).findFirst().orElse(null);
        assertNotNull(restored);
        assertFalse(restored.isBlocked(), "Thiết bị DEV-99 phải được mở lại mạng.");
    }

    @Test
    @DisplayName("Kiểm tra Thêm và Xóa Blacklist & Whitelist tên miền")
    void testDomainFilterCrud() {
        String testDomain = "trang-web-doc-hai.net";
        dbManager.insertBlacklistDomain(testDomain);
        assertTrue(dbManager.getAllBlacklistDomains().contains(testDomain));

        dbManager.deleteBlacklistDomain(testDomain);
        assertFalse(dbManager.getAllBlacklistDomains().contains(testDomain));

        String eduDomain = "tienganh123.com";
        dbManager.insertWhitelistDomain(eduDomain);
        assertTrue(dbManager.getAllWhitelistDomains().contains(eduDomain));

        dbManager.deleteWhitelistDomain(eduDomain);
        assertFalse(dbManager.getAllWhitelistDomains().contains(eduDomain));
    }

    @Test
    @DisplayName("Kiểm tra thêm bản ghi nhật ký và dọn sạch nhật ký (Logs CRUD)")
    void testLogsCrud() {
        AccessLog log = new AccessLog("LOG-999", "16:00:00 - 24/09", "PC Thử Nghiệm", "xem-phim-lau.com", "Giải trí", "ĐÃ CHẶN", "Vi phạm danh sách đen");
        dbManager.insertAccessLog(log);

        List<AccessLog> logs = dbManager.getAllAccessLogs();
        assertEquals(9, logs.size());
        assertEquals("LOG-999", logs.get(0).getId());

        dbManager.clearAllLogs();
        assertEquals(0, dbManager.getAllAccessLogs().size(), "Sau khi dọn dẹp, số lượng logs phải là 0.");
    }

    @Test
    @DisplayName("Kiểm tra cập nhật trạng thái quy tắc danh mục (CategoryRule)")
    void testCategoryRuleUpdate() {
        // CAT-01 ban đầu là blocked = true
        dbManager.updateCategoryRule("CAT-01", false);
        CategoryRule rule = dbManager.getAllCategoryRules().stream().filter(r -> r.getId().equals("CAT-01")).findFirst().orElse(null);
        assertNotNull(rule);
        assertFalse(rule.isBlocked());

        dbManager.updateCategoryRule("CAT-01", true);
        rule = dbManager.getAllCategoryRules().stream().filter(r -> r.getId().equals("CAT-01")).findFirst().orElse(null);
        assertNotNull(rule);
        assertTrue(rule.isBlocked());
    }
}
