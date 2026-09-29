package org.example.desktopver1.database;

import org.example.desktopver1.model.AccessLog;
import org.example.desktopver1.model.CategoryRule;
import org.example.desktopver1.model.Device;
import org.example.desktopver1.service.DataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử Tích hợp DataService với SQLite (DataServiceDatabaseTest)")
class DataServiceDatabaseTest {

    private static final String TEST_DB_FILE = "target/test_dataservice.db";
    private static final String TEST_DB_URL = "jdbc:sqlite:" + TEST_DB_FILE;

    private DatabaseManager dbManager;
    private DataService dataService;

    @BeforeEach
    void setUp() {
        File file = new File(TEST_DB_FILE);
        if (file.exists()) {
            file.delete();
        }

        dbManager = new DatabaseManager(TEST_DB_URL);
        dataService = new DataService(dbManager);
    }

    @AfterEach
    void tearDown() {
        File file = new File(TEST_DB_FILE);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    @DisplayName("DataService tải thành công toàn bộ dữ liệu giả lập từ SQLite vào bộ nhớ")
    void testDataLoadedFromDatabase() {
        assertTrue(dataService.isProtectionActive());
        assertFalse(dataService.isEmergencyPause());
        assertEquals(4, dataService.getDevices().size());
        assertEquals(6, dataService.getCategoryRules().size());
        assertTrue(dataService.getBlacklistDomains().size() >= 5);
        assertTrue(dataService.getWhitelistDomains().size() >= 5);
        assertTrue(dataService.getAccessLogs().size() >= 8);
    }

    @Test
    @DisplayName("Thao tác Bật/Tắt bảo vệ hệ thống được lưu vào SQLite")
    void testToggleProtectionPersisted() {
        dataService.toggleProtection(false);
        assertFalse(dataService.isProtectionActive());
        assertEquals("false", dbManager.getSetting("protection_active", ""));

        dataService.toggleProtection(true);
        assertTrue(dataService.isProtectionActive());
        assertEquals("true", dbManager.getSetting("protection_active", ""));
    }

    @Test
    @DisplayName("Khóa mạng khẩn cấp (Emergency Pause) cập nhật trạng thái mọi thiết bị vào SQLite")
    void testEmergencyPausePersisted() {
        dataService.toggleEmergencyPause(true);
        assertTrue(dataService.isEmergencyPause());
        assertEquals("true", dbManager.getSetting("emergency_pause", ""));

        // Tất cả thiết bị phải bị chặn
        for (Device d : dataService.getDevices()) {
            assertTrue(d.isBlocked());
        }

        // Kiểm tra trong SQLite
        for (Device d : dbManager.getAllDevices()) {
            assertTrue(d.isBlocked(), "Thiết bị " + d.getName() + " trong SQLite phải có is_blocked = 1");
        }

        // Hủy khóa mạng
        dataService.toggleEmergencyPause(false);
        assertFalse(dataService.isEmergencyPause());
        for (Device d : dbManager.getAllDevices()) {
            assertFalse(d.isBlocked(), "Thiết bị " + d.getName() + " trong SQLite phải có is_blocked = 0");
        }
    }

    @Test
    @DisplayName("Thêm/Xóa Blacklist tên miền đồng bộ tức thì vào SQLite")
    void testBlacklistSync() {
        String domain = "web-xau.org";
        dataService.addBlacklistDomain(domain);
        assertTrue(dataService.getBlacklistDomains().contains(domain));
        assertTrue(dbManager.getAllBlacklistDomains().contains(domain));

        dataService.removeBlacklistDomain(domain);
        assertFalse(dataService.getBlacklistDomains().contains(domain));
        assertFalse(dbManager.getAllBlacklistDomains().contains(domain));
    }

    @Test
    @DisplayName("Cắt mạng thiết bị đơn lẻ đồng bộ vào SQLite")
    void testToggleDeviceBlockSync() {
        Device dev = dataService.getDevices().get(0);
        assertFalse(dev.isBlocked());

        dataService.toggleDeviceBlock(dev);
        assertTrue(dev.isBlocked());

        Device inDb = dbManager.getAllDevices().stream().filter(d -> d.getId().equals(dev.getId())).findFirst().orElse(null);
        assertNotNull(inDb);
        assertTrue(inDb.isBlocked());
    }

    @Test
    @DisplayName("Cập nhật quy tắc danh mục nội dung lưu vào SQLite")
    void testToggleCategoryRuleSync() {
        CategoryRule rule = dataService.getCategoryRules().get(0);
        boolean initial = rule.isBlocked();

        dataService.toggleCategoryRule(rule);
        assertEquals(!initial, rule.isBlocked());

        CategoryRule inDb = dbManager.getAllCategoryRules().stream().filter(r -> r.getId().equals(rule.getId())).findFirst().orElse(null);
        assertNotNull(inDb);
        assertEquals(!initial, inDb.isBlocked());
    }

    @Test
    @DisplayName("Thêm nhật ký truy cập lưu vào SQLite và cập nhật số sự kiện bị chặn")
    void testAddLogSync() {
        int initialTotal = dataService.getAccessLogs().size();
        AccessLog log = new AccessLog("LOG-TEST", "17:00:00 - 24/09", "PC Test", "gambling-site.com", "Cờ bạc", "ĐÃ CHẶN", "Quy tắc chặn tự động");
        dataService.addAccessLog(log);

        assertEquals(initialTotal + 1, dataService.getAccessLogs().size());
        assertEquals("LOG-TEST", dataService.getAccessLogs().get(0).getId());

        AccessLog inDb = dbManager.getAllAccessLogs().stream().filter(l -> l.getId().equals("LOG-TEST")).findFirst().orElse(null);
        assertNotNull(inDb);
        assertEquals("ĐÃ CHẶN", inDb.getAction());
    }

    @Test
    @DisplayName("Kiểm tra chức năng theo dõi từng thiết bị và đánh giá danh sách đen / an toàn")
    void testDeviceTrackingAndSafetyAssessment() {
        Device dev = dataService.getDevices().get(0);

        AccessLog safeLog = new AccessLog("LOG-SAFE", "2026-09-28 10:00:00", dev.getName(), "hocmai.vn", "Học tập", "CHO PHÉP", "Website giáo dục");
        AccessLog blockedLog = new AccessLog("LOG-BLOCKED", "2026-09-28 10:05:00", dev.getName(), "tiktok.com", "Mạng xã hội", "ĐÃ CHẶN", "Danh sách đen");
        dataService.addAccessLog(safeLog);
        dataService.addAccessLog(blockedLog);

        assertFalse(dataService.isLogBlacklistedOrBlocked(safeLog));
        assertTrue(dataService.isLogBlacklistedOrBlocked(blockedLog));

        DataService.DeviceStats stats = dataService.getDeviceStats(dev);
        assertTrue(stats.getTotalVisits() >= 2);
        assertTrue(stats.getSafeVisits() >= 1);
        assertTrue(stats.getBlockedVisits() >= 1);
    }

    @Test
    @DisplayName("Kiểm tra tính toán và cập nhật lại thời gian sử dụng thiết bị hôm nay")
    void testDeviceUsageTimeRecalculation() {
        Device dev = new Device("DEV-TEST-TIME", "Máy Test Thời Gian", "Laptop", "10.0.0.99", "AA:BB:CC:11:22:33", "Trực tuyến", false, "0h 00m");
        dataService.addDevice(dev);

        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        AccessLog log1 = new AccessLog("L-1", today + " 08:00:00", dev.getName(), "google.com", "Tìm kiếm", "CHO PHÉP", "Hợp lệ");
        AccessLog log2 = new AccessLog("L-2", today + " 08:10:00", dev.getName(), "youtube.com", "Giải trí", "CHO PHÉP", "Hợp lệ");
        dataService.addAccessLog(log1);
        dataService.addAccessLog(log2);

        dataService.recalculateDeviceUsageTime(dev);
        assertNotEquals("0h 00m", dev.getTimeSpentToday());

        Device inDb = dbManager.getAllDevices().stream().filter(d -> d.getId().equals(dev.getId())).findFirst().orElse(null);
        assertNotNull(inDb);
        assertEquals(dev.getTimeSpentToday(), inDb.getTimeSpentToday());
    }
}
