package org.example.desktopver1.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Đại diện cho quy tắc kiểm soát quyền truy cập và hạn mức thời gian
 * sử dụng của một ứng dụng di động cụ thể (YouTube, Facebook, TikTok, Instagram, MLBB).
 */
public class AppPolicy {
    private final StringProperty appId;
    private final StringProperty appName;
    private final StringProperty category;
    private final StringProperty icon;
    private final StringProperty targetIp;
    private final IntegerProperty usedSeconds;
    private final IntegerProperty timeLimitMinutes;
    private final BooleanProperty blocked;
    private final BooleanProperty timeExceeded;

    public AppPolicy(String appId, String appName, String category, String icon, String targetIp,
                     int usedSeconds, int timeLimitMinutes, boolean blocked) {
        this.appId = new SimpleStringProperty(appId);
        this.appName = new SimpleStringProperty(appName);
        this.category = new SimpleStringProperty(category);
        this.icon = new SimpleStringProperty(icon);
        this.targetIp = new SimpleStringProperty(targetIp);
        this.usedSeconds = new SimpleIntegerProperty(usedSeconds);
        this.timeLimitMinutes = new SimpleIntegerProperty(timeLimitMinutes);
        this.blocked = new SimpleBooleanProperty(blocked);
        boolean exceeded = timeLimitMinutes > 0 && usedSeconds >= (timeLimitMinutes * 60);
        this.timeExceeded = new SimpleBooleanProperty(exceeded);

        // Tự động tính toán lại timeExceeded khi usedSeconds hoặc timeLimitMinutes thay đổi
        this.usedSeconds.addListener((obs, oldVal, newVal) -> recheckExceeded());
        this.timeLimitMinutes.addListener((obs, oldVal, newVal) -> recheckExceeded());
    }

    private void recheckExceeded() {
        int limit = getTimeLimitMinutes();
        int used = getUsedSeconds();
        boolean exceeded = limit > 0 && used >= (limit * 60);
        this.timeExceeded.set(exceeded);
    }

    public String getAppId() {
        return appId.get();
    }

    public StringProperty appIdProperty() {
        return appId;
    }

    public String getAppName() {
        return appName.get();
    }

    public void setAppName(String appName) {
        this.appName.set(appName);
    }

    public StringProperty appNameProperty() {
        return appName;
    }

    public String getCategory() {
        return category.get();
    }

    public StringProperty categoryProperty() {
        return category;
    }

    public String getIcon() {
        return icon.get();
    }

    public StringProperty iconProperty() {
        return icon;
    }

    public String getTargetIp() {
        return targetIp.get();
    }

    public void setTargetIp(String targetIp) {
        this.targetIp.set(targetIp);
    }

    public StringProperty targetIpProperty() {
        return targetIp;
    }

    public int getUsedSeconds() {
        return usedSeconds.get();
    }

    public void setUsedSeconds(int usedSeconds) {
        this.usedSeconds.set(usedSeconds);
    }

    public IntegerProperty usedSecondsProperty() {
        return usedSeconds;
    }

    public int getTimeLimitMinutes() {
        return timeLimitMinutes.get();
    }

    public void setTimeLimitMinutes(int timeLimitMinutes) {
        this.timeLimitMinutes.set(timeLimitMinutes);
    }

    public IntegerProperty timeLimitMinutesProperty() {
        return timeLimitMinutes;
    }

    public boolean isBlocked() {
        return blocked.get();
    }

    public void setBlocked(boolean blocked) {
        this.blocked.set(blocked);
    }

    public BooleanProperty blockedProperty() {
        return blocked;
    }

    public boolean isTimeExceeded() {
        return timeExceeded.get();
    }

    public void setTimeExceeded(boolean timeExceeded) {
        this.timeExceeded.set(timeExceeded);
    }

    public BooleanProperty timeExceededProperty() {
        return timeExceeded;
    }

    /**
     * Tỉ lệ thời gian sử dụng từ 0.0 đến 1.0 (dùng cho ProgressBar)
     */
    public double getUsageProgress() {
        int limit = getTimeLimitMinutes();
        if (limit <= 0) return 0.0;
        double ratio = (double) getUsedSeconds() / (limit * 60.0);
        return Math.min(1.0, Math.max(0.0, ratio));
    }

    /**
     * Định dạng chuỗi thời gian đã dùng (VD: "25 phút", "1h 15m")
     */
    public String getFormattedUsedTime() {
        int totalSec = getUsedSeconds();
        int minutes = totalSec / 60;
        int hours = minutes / 60;
        int remainMin = minutes % 60;
        if (hours > 0) {
            return hours + "h " + remainMin + "m";
        }
        return minutes + " phút";
    }

    /**
     * Định dạng chuỗi hạn mức (VD: "60 phút", "Không giới hạn")
     */
    public String getFormattedLimit() {
        int limit = getTimeLimitMinutes();
        if (limit <= 0) return "Không giới hạn";
        if (limit >= 60) {
            int h = limit / 60;
            int m = limit % 60;
            return m > 0 ? (h + "h " + m + "m") : (h + " giờ");
        }
        return limit + " phút";
    }

    /**
     * Định dạng trạng thái trực quan
     */
    public String getStatusDescription() {
        if (isBlocked()) {
            return "Đã chặn bởi phụ huynh";
        }
        if (isTimeExceeded()) {
            return "Đã dùng hết thời gian hôm nay";
        }
        int limit = getTimeLimitMinutes();
        if (limit <= 0) {
            return "Đang cho phép (Không giới hạn)";
        }
        int remainSec = Math.max(0, (limit * 60) - getUsedSeconds());
        int remainMin = remainSec / 60;
        return "Còn " + remainMin + " phút khả dụng";
    }
}
