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

import org.example.desktopver1.network.VpsClientService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service quản trị trạng thái và dữ liệu nghiệp vụ của ứng dụng bảo vệ truy cập mạng.
 * Tích hợp lưu trữ bền vững với SQLite Database qua DatabaseManager.
 * 
 * LƯU Ý CHO MÔN HỌC LẬP TRÌNH MẠNG:
 * Khi tích hợp với các module mạng thực tế:
 * - Mở luồng ngầm Socket Client / TCP connection đến VPS (103.74.101.176:9000).
 * - Các phương thức như toggleDeviceBlock(), toggleEmergencyPause(), addBlacklistDomain() 
 *   vừa cập nhật SQLite cục bộ, vừa gửi gói tin JSON qua Socket đến VPS quản trị mạng.
 */
public class DataService {

    private static DataService instance;

    private final DatabaseManager dbManager;
    private final VpsClientService vpsClient;

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
        this(DatabaseManager.getInstance(), VpsClientService.getInstance());
    }

    public DataService(DatabaseManager dbManager) {
        this(dbManager, VpsClientService.getInstance());
    }

    public DataService(DatabaseManager dbManager, VpsClientService vpsClient) {
        this.dbManager = dbManager;
        this.vpsClient = vpsClient;
        loadFromDatabase();

        if (this.vpsClient != null) {
            this.vpsClient.addListener(new VpsClientService.VpsMessageListener() {
                @Override
                public void onStateChanged(VpsClientService.ConnectionState newState, String message) {
                    if (newState == VpsClientService.ConnectionState.AUTHENTICATED) {
                        syncFullStateToVps();
                    }
                }

                @Override
                public void onMessageReceived(String rawJson) {}

                @Override
                public void onMessageSent(String rawJson, boolean success) {}
            });
            if (this.vpsClient.getConnectionState() == VpsClientService.ConnectionState.AUTHENTICATED) {
                syncFullStateToVps();
            }
        }
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
        if (vpsClient != null) {
            vpsClient.sendProtectionToggle(active);
        }
    }

    public void toggleEmergencyPause(boolean pause) {
        this.emergencyPause.set(pause);
        dbManager.setSetting("emergency_pause", String.valueOf(pause));
        dbManager.updateAllDevicesBlock(pause);
        for (Device d : devices) {
            d.setBlocked(pause);
        }
        addSystemLog("Chế độ TẠM DỪNG MẠNG KHẨN CẤP: " + (pause ? "KÍCH HOẠT" : "ĐÃ HỦY"), "Tất cả thiết bị", pause ? "ĐÃ CHẶN" : "CHO PHÉP");
        if (vpsClient != null) {
            vpsClient.sendEmergencyPause(pause);
        }
    }

    public void toggleDeviceBlock(Device device) {
        boolean newState = !device.isBlocked();
        device.setBlocked(newState);
        dbManager.updateDeviceBlock(device.getId(), newState);
        addSystemLog((newState ? "Ngắt mạng internet: " : "Khôi phục mạng: ") + device.getName(), device.getName(), newState ? "ĐÃ CHẶN" : "CHO PHÉP");
        if (vpsClient != null) {
            vpsClient.sendDeviceBlock(device.getId(), device.getName(), device.getIpAddress(), device.getMacAddress(), newState);
        }
    }

    public void addDevice(Device device) {
        dbManager.saveDevice(device);
        devices.add(device);
        addSystemLog("Đã thêm thiết bị mới: " + device.getName(), device.getName(), "CHO PHÉP");
        if (vpsClient != null) {
            vpsClient.sendAddDevice(device);
        }
    }

    public void removeDevice(Device device) {
        if (device == null) return;
        devices.remove(device);
        dbManager.deleteDevice(device.getId());
        addSystemLog("Đã xóa thiết bị: " + device.getName(), device.getName(), "CẢNH BÁO");
        if (vpsClient != null) {
            syncFullStateToVps();
        }
    }

    public void clearAllDevices() {
        devices.clear();
        dbManager.clearAllDevices();
        addSystemLog("Đã dọn sạch toàn bộ thiết bị", "Hệ thống", "CẢNH BÁO");
        if (vpsClient != null) {
            syncFullStateToVps();
        }
    }

    public void addBlacklistDomain(String domain) {
        String clean = domain.trim().toLowerCase().replaceAll("^https?://", "").replaceAll("/.*", "");
        if (!clean.isEmpty() && !blacklistDomains.contains(clean)) {
            blacklistDomains.add(0, clean);
            dbManager.insertBlacklistDomain(clean);
            addSystemLog("Thêm tên miền chặn: " + clean, "Bộ lọc", "ĐÃ CHẶN");
            if (vpsClient != null) {
                vpsClient.sendBlacklistDomain(clean, "ADD");
            }
        }
    }

    public void removeBlacklistDomain(String domain) {
        blacklistDomains.remove(domain);
        dbManager.deleteBlacklistDomain(domain);
        addSystemLog("Đã gỡ tên miền khỏi danh sách chặn: " + domain, "Bộ lọc", "CHO PHÉP");
        if (vpsClient != null) {
            vpsClient.sendBlacklistDomain(domain, "REMOVE");
            if (domain != null && domain.toLowerCase().contains("tiktok")) {
                vpsClient.sendBlacklistDomain("tiktok.com", "REMOVE");
                vpsClient.sendBlacklistDomain("tiktokv.com", "REMOVE");
                vpsClient.sendBlacklistDomain("tiktokcdn.com", "REMOVE");
                vpsClient.sendBlacklistDomain("byteoversea.com", "REMOVE");
                vpsClient.sendBlacklistDomain("ibytedtos.com", "REMOVE");
            }
        }
    }

    public void addWhitelistDomain(String domain) {
        String clean = domain.trim().toLowerCase().replaceAll("^https?://", "").replaceAll("/.*", "");
        if (!clean.isEmpty() && !whitelistDomains.contains(clean)) {
            whitelistDomains.add(0, clean);
            dbManager.insertWhitelistDomain(clean);
            addSystemLog("Thêm tên miền cho phép: " + clean, "Bộ lọc", "CHO PHÉP");
            if (vpsClient != null) {
                vpsClient.sendWhitelistDomain(clean, "ADD");
            }
        }
    }

    public void removeWhitelistDomain(String domain) {
        whitelistDomains.remove(domain);
        dbManager.deleteWhitelistDomain(domain);
        addSystemLog("Đã xóa khỏi danh sách cho phép: " + domain, "Bộ lọc", "CẢNH BÁO");
        if (vpsClient != null) {
            vpsClient.sendWhitelistDomain(domain, "REMOVE");
        }
    }

    public void toggleCategoryRule(CategoryRule rule) {
        boolean newState = !rule.isBlocked();
        rule.setBlocked(newState);
        dbManager.updateCategoryRule(rule.getId(), newState);
        addSystemLog((newState ? "Kích hoạt chặn danh mục: " : "Bỏ chặn danh mục: ") + rule.getName(), "Bộ lọc danh mục", newState ? "ĐÃ CHẶN" : "CHO PHÉP");
        if (vpsClient != null) {
            vpsClient.sendCategoryRuleUpdate(rule.getId(), rule.getName(), newState);
        }
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

    /**
     * Nạp danh sách nhật ký phân tích từ VPS vào SQLite và danh sách ObservableList trên UI
     */
    public void addAccessLogs(List<AccessLog> logs) {
        if (logs == null || logs.isEmpty()) return;
        dbManager.insertAccessLogs(logs);

        try {
            if (javafx.application.Platform.isFxApplicationThread()) {
                accessLogs.addAll(0, logs);
            } else {
                javafx.application.Platform.runLater(() -> accessLogs.addAll(0, logs));
            }
        } catch (IllegalStateException e) {
            accessLogs.addAll(0, logs);
        }
    }

    public String findDeviceNameByIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) return "Thiết bị không xác định";
        String cleanIp = ip.trim();
        for (Device d : devices) {
            if (cleanIp.equalsIgnoreCase(d.getIpAddress())) {
                return d.getName();
            }
        }
        if ("10.0.0.2".equals(cleanIp)) {
            return "Điện thoại con (WireGuard)";
        }
        return "Thiết bị (" + cleanIp + ")";
    }

    public void clearLogs() {
        accessLogs.clear();
        dbManager.clearAllLogs();
    }

    // --- Getters & Properties ---

    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }

    public void requestCreateWireGuardPeer(String deviceName, String deviceType) {
        if (vpsClient != null) {
            vpsClient.sendCreateWireGuardPeer(deviceName, deviceType);
        }
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

    public void updateDailyLimits(double weekdayHours, double weekendHours) {
        addSystemLog(String.format("Cập nhật hạn mức: T2-T6: %.1fh, T7-CN: %.1fh", weekdayHours, weekendHours), "Quản trị", "CẬP NHẬT");
        if (vpsClient != null) {
            vpsClient.sendTimeLimits(weekdayHours, weekendHours);
        }
    }

    public void updateCurfew(boolean enabled, String startTime, String endTime) {
        addSystemLog(String.format("Cập nhật giờ giới nghiêm (%s): %s -> %s", enabled ? "BẬT" : "TẮT", startTime, endTime), "Quản trị", "CẬP NHẬT");
        if (vpsClient != null) {
            vpsClient.sendCurfew(enabled, startTime, endTime);
        }
    }

    /**
     * Hàm gửi chuỗi JSON tùy ý lên VPS khi phụ huynh thao tác trên giao diện.
     */
    public void sendJsonToVps(String jsonString) {
        if (vpsClient != null) {
            vpsClient.sendJson(jsonString);
        }
    }

    public VpsClientService getVpsClient() {
        return vpsClient;
    }

    /**
     * Đồng bộ toàn bộ cấu hình SQLite lên VPS (SYNC_RULES)
     */
    public void syncFullStateToVps() {
        if (vpsClient == null) return;
        List<String> bl = new ArrayList<>(blacklistDomains);
        List<String> wl = new ArrayList<>(whitelistDomains);
        List<String> blockedIps = new ArrayList<>();
        for (Device d : devices) {
            if (d.isBlocked() && d.getIpAddress() != null && !d.getIpAddress().trim().isEmpty()) {
                blockedIps.add(d.getIpAddress().trim());
            }
        }
        vpsClient.sendSyncRules(
                protectionActive.get(),
                emergencyPause.get(),
                bl,
                wl,
                blockedIps
        );
        System.out.println("[DataService] Đã gửi bản tin SYNC_RULES lên VPS: "
                + bl.size() + " cấm, " + wl.size() + " cho phép, " + blockedIps.size() + " thiết bị bị khóa mạng.");
    }
}
