package org.example.desktopver1.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Đại diện cho cấu hình thời gian sử dụng theo các ngày trong tuần.
 */
public class TimeSchedule {
    private final StringProperty dayGroup; // "Ngày thường (Thứ 2 - Thứ 6)", "Cuối tuần (Thứ 7 - CN)"
    private final DoubleProperty dailyLimitHours; // Số giờ tối đa
    private final StringProperty curfewStart; // Bắt đầu giờ giới nghiêm (VD: 22:00)
    private final StringProperty curfewEnd; // Kết thúc giờ giới nghiêm (VD: 06:00)
    private final BooleanProperty active;

    public TimeSchedule(String dayGroup, double dailyLimitHours, String curfewStart, String curfewEnd, boolean active) {
        this.dayGroup = new SimpleStringProperty(dayGroup);
        this.dailyLimitHours = new SimpleDoubleProperty(dailyLimitHours);
        this.curfewStart = new SimpleStringProperty(curfewStart);
        this.curfewEnd = new SimpleStringProperty(curfewEnd);
        this.active = new SimpleBooleanProperty(active);
    }

    public String getDayGroup() {
        return dayGroup.get();
    }

    public StringProperty dayGroupProperty() {
        return dayGroup;
    }

    public double getDailyLimitHours() {
        return dailyLimitHours.get();
    }

    public void setDailyLimitHours(double dailyLimitHours) {
        this.dailyLimitHours.set(dailyLimitHours);
    }

    public DoubleProperty dailyLimitHoursProperty() {
        return dailyLimitHours;
    }

    public String getCurfewStart() {
        return curfewStart.get();
    }

    public void setCurfewStart(String curfewStart) {
        this.curfewStart.set(curfewStart);
    }

    public StringProperty curfewStartProperty() {
        return curfewStart;
    }

    public String getCurfewEnd() {
        return curfewEnd.get();
    }

    public void setCurfewEnd(String curfewEnd) {
        this.curfewEnd.set(curfewEnd);
    }

    public StringProperty curfewEndProperty() {
        return curfewEnd;
    }

    public boolean isActive() {
        return active.get();
    }

    public void setActive(boolean active) {
        this.active.set(active);
    }

    public BooleanProperty activeProperty() {
        return active;
    }
}
