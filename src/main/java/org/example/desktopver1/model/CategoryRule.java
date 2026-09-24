package org.example.desktopver1.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Đại diện cho quy tắc kiểm duyệt nội dung theo từng danh mục.
 */
public class CategoryRule {
    private final StringProperty id;
    private final StringProperty name;
    private final StringProperty icon;
    private final StringProperty description;
    private final BooleanProperty blocked;
    private final IntegerProperty blockedCount;

    public CategoryRule(String id, String name, String icon, String description, boolean blocked, int blockedCount) {
        this.id = new SimpleStringProperty(id);
        this.name = new SimpleStringProperty(name);
        this.icon = new SimpleStringProperty(icon);
        this.description = new SimpleStringProperty(description);
        this.blocked = new SimpleBooleanProperty(blocked);
        this.blockedCount = new SimpleIntegerProperty(blockedCount);
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

    public StringProperty nameProperty() {
        return name;
    }

    public String getIcon() {
        return icon.get();
    }

    public StringProperty iconProperty() {
        return icon;
    }

    public String getDescription() {
        return description.get();
    }

    public StringProperty descriptionProperty() {
        return description;
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

    public int getBlockedCount() {
        return blockedCount.get();
    }

    public void setBlockedCount(int blockedCount) {
        this.blockedCount.set(blockedCount);
    }

    public IntegerProperty blockedCountProperty() {
        return blockedCount;
    }
}
