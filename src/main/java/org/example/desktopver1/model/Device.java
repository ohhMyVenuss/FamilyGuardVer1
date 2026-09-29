package org.example.desktopver1.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Đại diện cho một thiết bị của trẻ kết nối vào mạng gia đình.
 */
public class Device {
    private final StringProperty id;
    private final StringProperty name;
    private final StringProperty type;
    private final StringProperty ipAddress;
    private final StringProperty macAddress;
    private final StringProperty status; // "Trực tuyến", "Ngoại tuyến"
    private final BooleanProperty blocked; // Có bị ngắt mạng không
    private final StringProperty timeSpentToday;
    private final javafx.beans.property.IntegerProperty bonusMinutes;

    public Device(String id, String name, String type, String ipAddress, String macAddress, String status, boolean blocked, String timeSpentToday) {
        this(id, name, type, ipAddress, macAddress, status, blocked, timeSpentToday, 0);
    }

    public Device(String id, String name, String type, String ipAddress, String macAddress, String status, boolean blocked, String timeSpentToday, int bonusMinutes) {
        this.id = new SimpleStringProperty(id);
        this.name = new SimpleStringProperty(name);
        this.type = new SimpleStringProperty(type);
        this.ipAddress = new SimpleStringProperty(ipAddress);
        this.macAddress = new SimpleStringProperty(macAddress);
        this.status = new SimpleStringProperty(status);
        this.blocked = new SimpleBooleanProperty(blocked);
        this.timeSpentToday = new SimpleStringProperty(timeSpentToday);
        this.bonusMinutes = new javafx.beans.property.SimpleIntegerProperty(bonusMinutes);
    }

    public String getId() {
        return id.get();
    }

    public StringProperty idProperty() {
        return id;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getType() {
        return type.get();
    }

    public StringProperty typeProperty() {
        return type;
    }

    public String getIpAddress() {
        return ipAddress.get();
    }

    public StringProperty ipAddressProperty() {
        return ipAddress;
    }

    public String getMacAddress() {
        return macAddress.get();
    }

    public StringProperty macAddressProperty() {
        return macAddress;
    }

    public String getStatus() {
        return status.get();
    }

    public void setStatus(String status) {
        this.status.set(status);
    }

    public StringProperty statusProperty() {
        return status;
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

    public String getTimeSpentToday() {
        return timeSpentToday.get();
    }

    public void setTimeSpentToday(String timeSpentToday) {
        this.timeSpentToday.set(timeSpentToday);
    }

    public StringProperty timeSpentTodayProperty() {
        return timeSpentToday;
    }

    public int getBonusMinutes() {
        return bonusMinutes.get();
    }

    public void setBonusMinutes(int bonusMinutes) {
        this.bonusMinutes.set(bonusMinutes);
    }

    public javafx.beans.property.IntegerProperty bonusMinutesProperty() {
        return bonusMinutes;
    }
}
