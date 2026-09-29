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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

        recalculateAllDevicesUsageTime();
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
        recalculateAllDevicesUsageTime();
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
        recalculateAllDevicesUsageTime();
    }

    public void runSafelyOnFx(Runnable r) {
        try {
            if (javafx.application.Platform.isFxApplicationThread()) {
                r.run();
            } else {
                javafx.application.Platform.runLater(r);
            }
        } catch (IllegalStateException e) {
            r.run();
        }
    }

    /**
     * Phân tích chuỗi thời gian nhật ký linh hoạt từ cả VPS và Client
     */
    public LocalDateTime parseLogTimestamp(String ts) {
        if (ts == null || ts.trim().isEmpty()) return null;
        ts = ts.trim();
        try {
            if (ts.length() >= 19 && ts.charAt(4) == '-' && ts.charAt(7) == '-') {
                return LocalDateTime.parse(ts.substring(0, 19), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
        } catch (Exception ignored) {}
        try {
            if (ts.contains("-") && ts.contains("/")) {
                String[] parts = ts.split(" - ");
                if (parts.length == 2) {
                    LocalTime time = LocalTime.parse(parts[0].trim(), DateTimeFormatter.ofPattern("HH:mm:ss"));
                    String[] dateParts = parts[1].trim().split("/");
                    int day = Integer.parseInt(dateParts[0]);
                    int month = Integer.parseInt(dateParts[1]);
                    int year = LocalDate.now().getYear();
                    return LocalDateTime.of(year, month, day, time.getHour(), time.getMinute(), time.getSecond());
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Kiểm tra một bản ghi log có thuộc về thiết bị chỉ định hay không
     */
    public boolean isLogForDevice(AccessLog log, Device device) {
        if (log == null || device == null) return false;
        String devName = device.getName();
        String devIp = device.getIpAddress();
        String logDev = log.getDeviceName();
        if (logDev == null) return false;
        if (devName != null && !devName.trim().isEmpty() && logDev.equalsIgnoreCase(devName.trim())) {
            return true;
        }
        if (devIp != null && !devIp.trim().isEmpty()) {
            String cleanIp = devIp.trim();
            if (logDev.equalsIgnoreCase("Thiết bị (" + cleanIp + ")")) {
                return true;
            }
            if ("10.0.0.2".equals(cleanIp) && logDev.contains("Điện thoại con")) {
                return true;
            }
            if (logDev.contains(cleanIp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kiểm tra tên miền có nằm trong danh sách đen hay không
     */
    public boolean isDomainInBlacklist(String domain) {
        if (domain == null || domain.trim().isEmpty()) return false;
        String clean = domain.trim().toLowerCase().replaceAll("^https?://", "").replaceAll("/.*", "");
        for (String bl : blacklistDomains) {
            String blClean = bl.trim().toLowerCase();
            if (clean.equals(blClean) || clean.endsWith("." + blClean) || clean.contains(blClean)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kiểm tra một sự kiện truy cập web có bị chặn hoặc nằm trong danh sách đen hay không
     */
    public boolean isLogBlacklistedOrBlocked(AccessLog log) {
        if (log == null) return false;
        if ("ĐÃ CHẶN".equalsIgnoreCase(log.getAction())) return true;
        return isDomainInBlacklist(log.getDomain());
    }

    /**
     * Lấy toàn bộ danh sách nhật ký của thiết bị cụ thể
     */
    public ObservableList<AccessLog> getLogsForDevice(Device device) {
        ObservableList<AccessLog> list = FXCollections.observableArrayList();
        for (AccessLog log : accessLogs) {
            if (isLogForDevice(log, device)) {
                list.add(log);
            }
        }
        return list;
    }

    /**
     * Lớp DTO thống kê nhanh hoạt động của thiết bị
     */
    public static class DeviceStats {
        private final int totalVisits;
        private final int safeVisits;
        private final int blockedVisits;
        private final String lastDomain;
        private final String lastTimestamp;
        private final String timeSpentToday;

        public DeviceStats(int totalVisits, int safeVisits, int blockedVisits, String lastDomain, String lastTimestamp, String timeSpentToday) {
            this.totalVisits = totalVisits;
            this.safeVisits = safeVisits;
            this.blockedVisits = blockedVisits;
            this.lastDomain = lastDomain;
            this.lastTimestamp = lastTimestamp;
            this.timeSpentToday = timeSpentToday;
        }

        public int getTotalVisits() { return totalVisits; }
        public int getSafeVisits() { return safeVisits; }
        public int getBlockedVisits() { return blockedVisits; }
        public String getLastDomain() { return lastDomain; }
        public String getLastTimestamp() { return lastTimestamp; }
        public String getTimeSpentToday() { return timeSpentToday; }
    }

    /**
     * Trích xuất các chỉ số giám sát an toàn cho từng thiết bị
     */
    public DeviceStats getDeviceStats(Device device) {
        if (device == null) return new DeviceStats(0, 0, 0, "Chưa có", "Chưa có", "0h 00m");
        int total = 0;
        int safe = 0;
        int blocked = 0;
        String lastDom = "Chưa có";
        String lastTs = "Chưa có";

        for (AccessLog log : accessLogs) {
            if (isLogForDevice(log, device)) {
                total++;
                if (isLogBlacklistedOrBlocked(log)) {
                    blocked++;
                } else {
                    safe++;
                }
                if ("Chưa có".equals(lastDom) && log.getDomain() != null && !log.getDomain().trim().isEmpty()) {
                    lastDom = log.getDomain();
                    lastTs = log.getTimestamp();
                }
            }
        }
        return new DeviceStats(total, safe, blocked, lastDom, lastTs, device.getTimeSpentToday());
    }

    /**
     * Tính toán và cập nhật lại thời gian sử dụng hôm nay của thiết bị dựa trên nhật ký truy vấn mạng
     */
    public void recalculateDeviceUsageTime(Device device) {
        if (device == null) return;
        LocalDate today = LocalDate.now();
        List<LocalDateTime> times = new ArrayList<>();
        boolean hasAnyLogForDevice = false;

        for (AccessLog log : accessLogs) {
            if (isLogForDevice(log, device)) {
                hasAnyLogForDevice = true;
                LocalDateTime ldt = parseLogTimestamp(log.getTimestamp());
                if (ldt != null && ldt.toLocalDate().equals(today)) {
                    times.add(ldt);
                }
            }
        }

        if (times.isEmpty()) {
            if (!hasAnyLogForDevice && device.getTimeSpentToday() != null && !device.getTimeSpentToday().isEmpty() && !"0h 00m".equals(device.getTimeSpentToday())) {
                return;
            }
            if (hasAnyLogForDevice) {
                runSafelyOnFx(() -> {
                    device.setTimeSpentToday("0h 00m");
                    dbManager.updateDeviceTimeSpent(device.getId(), "0h 00m");
                });
            }
            return;
        }

        times.sort(LocalDateTime::compareTo);
        long totalSeconds = 0;
        LocalDateTime sessionStart = times.get(0);
        LocalDateTime lastInSession = times.get(0);

        for (int i = 1; i < times.size(); i++) {
            LocalDateTime current = times.get(i);
            long gapSeconds = java.time.Duration.between(lastInSession, current).getSeconds();
            if (gapSeconds <= 300) { // Trong vòng 5 phút tính là phiên liên tục
                lastInSession = current;
            } else {
                long duration = Math.max(120, java.time.Duration.between(sessionStart, lastInSession).getSeconds());
                totalSeconds += duration;
                sessionStart = current;
                lastInSession = current;
            }
        }
        long duration = Math.max(120, java.time.Duration.between(sessionStart, lastInSession).getSeconds());
        totalSeconds += duration;

        long hours = totalSeconds / 3600;
        long mins = (totalSeconds % 3600) / 60;
        String formatted = String.format("%dh %02dm", hours, mins);

        runSafelyOnFx(() -> {
            device.setTimeSpentToday(formatted);
            dbManager.updateDeviceTimeSpent(device.getId(), formatted);
        });
    }

    /**
     * Tính lại thời gian sử dụng cho tất cả các thiết bị
     */
    public void recalculateAllDevicesUsageTime() {
        for (Device dev : devices) {
            recalculateDeviceUsageTime(dev);
        }
    }

    public double parseSpentHours(String timeSpentStr) {
        if (timeSpentStr == null || timeSpentStr.trim().isEmpty()) return 0.0;
        try {
            String s = timeSpentStr.trim();
            int h = 0;
            int m = 0;
            if (s.contains("h")) {
                String[] hParts = s.split("h");
                h = Integer.parseInt(hParts[0].trim());
                if (hParts.length > 1 && hParts[1].contains("m")) {
                    m = Integer.parseInt(hParts[1].replace("m", "").trim());
                }
            } else if (s.contains("m")) {
                m = Integer.parseInt(s.replace("m", "").trim());
            }
            return h + (m / 60.0);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public String getTotalTimeSpentTodayFormatted() {
        double totalH = 0.0;
        for (Device d : devices) {
            totalH += parseSpentHours(d.getTimeSpentToday());
        }
        int h = (int) totalH;
        int m = (int) Math.round((totalH - h) * 60);
        return String.format("%dh %02dm", h, m);
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
