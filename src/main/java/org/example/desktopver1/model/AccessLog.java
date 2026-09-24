package org.example.desktopver1.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Đại diện cho nhật ký sự kiện truy cập web/internet của các thiết bị.
 */
public class AccessLog {
    private final StringProperty id;
    private final StringProperty timestamp;
    private final StringProperty deviceName;
    private final StringProperty domain;
    private final StringProperty category;
    private final StringProperty action; // "CHO PHÉP", "ĐÃ CHẶN"
    private final StringProperty reason;

    public AccessLog(String id, String timestamp, String deviceName, String domain, String category, String action, String reason) {
        this.id = new SimpleStringProperty(id);
        this.timestamp = new SimpleStringProperty(timestamp);
        this.deviceName = new SimpleStringProperty(deviceName);
        this.domain = new SimpleStringProperty(domain);
        this.category = new SimpleStringProperty(category);
        this.action = new SimpleStringProperty(action);
        this.reason = new SimpleStringProperty(reason);
    }

    public String getId() {
        return id.get();
    }

    public StringProperty idProperty() {
        return id;
    }

    public String getTimestamp() {
        return timestamp.get();
    }

    public void setTimestamp(String timestamp) {
        this.timestamp.set(timestamp);
    }

    public StringProperty timestampProperty() {
        return timestamp;
    }

    public String getDeviceName() {
        return deviceName.get();
    }

    public void setDeviceName(String deviceName) {
        this.deviceName.set(deviceName);
    }

    public StringProperty deviceNameProperty() {
        return deviceName;
    }

    public String getDomain() {
        return domain.get();
    }

    public void setDomain(String domain) {
        this.domain.set(domain);
    }

    public StringProperty domainProperty() {
        return domain;
    }

    public String getCategory() {
        return category.get();
    }

    public void setCategory(String category) {
        this.category.set(category);
    }

    public StringProperty categoryProperty() {
        return category;
    }

    public String getAction() {
        return action.get();
    }

    public void setAction(String action) {
        this.action.set(action);
    }

    public StringProperty actionProperty() {
        return action;
    }

    public String getReason() {
        return reason.get();
    }

    public void setReason(String reason) {
        this.reason.set(reason);
    }

    public StringProperty reasonProperty() {
        return reason;
    }
}
