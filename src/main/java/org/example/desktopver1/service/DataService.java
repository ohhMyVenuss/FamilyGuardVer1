package org.example.desktopver1.service;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.example.desktopver1.database.DatabaseManager;
import org.example.desktopver1.model.AccessLog;
import org.example.desktopver1.model.CategoryRule;
import org.example.desktopver1.model.Device;
import org.example.desktopver1.model.TimeSchedule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service quản trị trạng thái và dữ liệu nghiệp vụ của ứng dụng bảo vệ truy cập mạng.
 * Tích hợp lưu trữ bền vững với SQLite Database qua DatabaseManager.
 * 
 * LƯU Ý CHO MÔN HỌC LẬP TRÌNH MẠNG:
 * Khi tích hợp với các module mạng thực tế:
 * - Có thể mở Socket Client / TCP connection đến Router/Proxy Server hoặc Agent con trên máy trẻ em.
 * - Các phương thức như toggleDeviceBlock(), toggleEmergencyPause(), addBlacklistDomain() 
 *   vừa cập nhật SQLite cục bộ, vừa gửi gói tin điều khiển (Packet/JSON qua Socket) đến Server giám sát mạng.
 */
public class DataService {

    private static DataService instance;

    private final DatabaseManager dbManager;

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

    public DataService() {
        this(DatabaseManager.getInstance());
    }

    public DataService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        loadFromDatabase();
    }

    public static synchronized DataService getInstance() {
        if (instance == null) {
            instance = new DataService();
        }
        return instance;
    }

    public static synchronized void setInstance(DataService customInstance) {
        instance = customInstance;
    }

    /**
     * Nạp dữ liệu từ SQLite vào bộ nhớ và các ObservableList
     */
    public void loadFromDatabase() {
        // Cài đặt hệ thống
        protectionActive.set(Boolean.parseBoolean(dbManager.getSetting("protection_active", "true")));
        emergencyPause.set(Boolean.parseBoolean(dbManager.getSetting("emergency_pause", "false")));
        safeSearchEnabled.set(Boolean.parseBoolean(dbManager.getSetting("safesearch_enabled", "true")));

        // Dữ liệu danh sách
        devices.setAll(dbManager.getAllDevices());
        categoryRules.setAll(dbManager.getAllCategoryRules());
        blacklistDomains.setAll(dbManager.getAllBlacklistDomains());
        whitelistDomains.setAll(dbManager.getAllWhitelistDomains());
        accessLogs.setAll(dbManager.getAllAccessLogs());
        timeSchedules.setAll(dbManager.getAllTimeSchedules());
    }

    // --- Các thao tác nghiệp vụ có đồng bộ SQLite ---

    public void toggleProtection(boolean active) {
        this.protectionActive.set(active);
        dbManager.setSetting("protection_active", String.valueOf(active));
        addSystemLog("Hệ thống bảo vệ " + (active ? "ĐÃ ĐƯỢC BẬT" : "ĐÃ TẠM DỪNG"), "Hệ thống", active ? "CHO PHÉP" : "CẢNH BÁO");
    }

    public void toggleEmergencyPause(boolean pause) {
        this.emergencyPause.set(pause);
        dbManager.setSetting("emergency_pause", String.valueOf(pause));
        dbManager.updateAllDevicesBlock(pause);
        for (Device d : devices) {
            d.setBlocked(pause);
        }
        addSystemLog("Chế độ TẠM DỪNG MẠNG KHẨN CẤP: " + (pause ? "KÍCH HOẠT" : "ĐÃ HỦY"), "Tất cả thiết bị", pause ? "ĐÃ CHẶN" : "CHO PHÉP");
    }

    public void toggleDeviceBlock(Device device) {
        boolean newState = !device.isBlocked();
        device.setBlocked(newState);
        dbManager.updateDeviceBlock(device.getId(), newState);
        addSystemLog((newState ? "Ngắt mạng internet: " : "Khôi phục mạng: ") + device.getName(), device.getName(), newState ? "ĐÃ CHẶN" : "CHO PHÉP");
    }

    public void addDevice(Device device) {
        dbManager.saveDevice(device);
        devices.add(device);
        addSystemLog("Đã thêm thiết bị mới: " + device.getName(), device.getName(), "CHO PHÉP");
    }

    public void addBlacklistDomain(String domain) {
        String clean = domain.trim().toLowerCase().replaceAll("^https?://", "").replaceAll("/.*", "");
        if (!clean.isEmpty() && !blacklistDomains.contains(clean)) {
            blacklistDomains.add(0, clean);
            dbManager.insertBlacklistDomain(clean);
            addSystemLog("Thêm tên miền chặn: " + clean, "Bộ lọc", "ĐÃ CHẶN");
        }
    }

    public void removeBlacklistDomain(String domain) {
        blacklistDomains.remove(domain);
        dbManager.deleteBlacklistDomain(domain);
        addSystemLog("Đã gỡ tên miền khỏi danh sách chặn: " + domain, "Bộ lọc", "CHO PHÉP");
    }

    public void addWhitelistDomain(String domain) {
        String clean = domain.trim().toLowerCase().replaceAll("^https?://", "").replaceAll("/.*", "");
        if (!clean.isEmpty() && !whitelistDomains.contains(clean)) {
            whitelistDomains.add(0, clean);
            dbManager.insertWhitelistDomain(clean);
            addSystemLog("Thêm tên miền cho phép: " + clean, "Bộ lọc", "CHO PHÉP");
        }
    }

    public void removeWhitelistDomain(String domain) {
        whitelistDomains.remove(domain);
        dbManager.deleteWhitelistDomain(domain);
        addSystemLog("Đã xóa khỏi danh sách cho phép: " + domain, "Bộ lọc", "CẢNH BÁO");
    }

    public void toggleCategoryRule(CategoryRule rule) {
        boolean newState = !rule.isBlocked();
        rule.setBlocked(newState);
        dbManager.updateCategoryRule(rule.getId(), newState);
        addSystemLog((newState ? "Kích hoạt chặn danh mục: " : "Bỏ chặn danh mục: ") + rule.getName(), "Bộ lọc danh mục", newState ? "ĐÃ CHẶN" : "CHO PHÉP");
    }

    public void addSystemLog(String description, String source, String action) {
        String now = LocalDateTime.now().format(timeFormatter);
        AccessLog log = new AccessLog("LOG-" + System.currentTimeMillis(), now, source, description, "Quản trị", action, "Thao tác phụ huynh");
        accessLogs.add(0, log);
        dbManager.insertAccessLog(log);
    }

    public void addAccessLog(AccessLog log) {
        accessLogs.add(0, log);
        dbManager.insertAccessLog(log);
    }

    public void clearLogs() {
        accessLogs.clear();
        dbManager.clearAllLogs();
    }

    // --- Getters & Properties ---

    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }

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
