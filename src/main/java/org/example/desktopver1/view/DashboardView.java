package org.example.desktopver1.view;

import atlantafx.base.controls.ToggleSwitch;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.desktopver1.model.AccessLog;
import org.example.desktopver1.model.Device;
import org.example.desktopver1.service.DataService;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignB;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;

/**
 * Màn hình Tổng quan (Dashboard):
 * - Hàng trên: 3 thẻ KPI Cards (Thiết bị đang online, Số truy vấn bị chặn hôm nay, Trạng thái AI Engine ONNX)
 * - Khối điều khiển nhanh Panic Lock & danh sách thiết bị con với ToggleSwitch khóa mạng tức thì gửi lệnh TCP xuống VPS
 * - Hoạt động truy cập gần đây
 */
public class DashboardView extends ScrollPane {

    private final DataService dataService;
    private final ToastManager.ToastHandler toastHandler;
    private final Runnable navigateToLogs;

    private Label lblOnlineDevicesValue;
    private Label lblOnlineDevicesHint;
    private Label lblBlockedTodayValue;
    private Label lblBlockedTodayHint;
    private Label lblAiEngineValue;
    private Label lblAiEngineHint;

    private ToggleSwitch switchGlobalPanic;
    private ToggleSwitch switchAiProtection;
    private HBox panicBanner;
    private VBox quickDevicesList;
    private VBox recentEventsList;

    private boolean updatingGlobalSwitches = false;

    public DashboardView(DataService dataService, ToastManager.ToastHandler toastHandler, Runnable navigateToLogs) {
        this.dataService = dataService;
        this.toastHandler = toastHandler;
        this.navigateToLogs = navigateToLogs;

        setFitToWidth(true);
        getStyleClass().add("view-scroll");

        VBox contentBox = new VBox(20);
        contentBox.getStyleClass().add("view-container");

        // 1. Header
        contentBox.getChildren().add(createHeader());

        // 2. Hàng 3 KPI Cards
        contentBox.getChildren().add(createKpiRow());

        // 3. Thanh Panic Lock toàn hệ thống & Công tắc AI Engine
        contentBox.getChildren().add(createPanicControlBanner());

        // 4. Danh sách nhanh thiết bị con (ToggleSwitch Khóa mạng tức thì) + Sự kiện gần đây
        contentBox.getChildren().add(createMainGrid());

        setContent(contentBox);

        // Lắng nghe thay đổi dữ liệu an toàn luồng
        dataService.protectionActiveProperty().addListener((obs, o, n) -> Platform.runLater(this::refreshStats));
        dataService.emergencyPauseProperty().addListener((obs, o, n) -> Platform.runLater(this::refreshPanicBannerState));
        dataService.getAccessLogs().addListener((ListChangeListener<AccessLog>) c -> Platform.runLater(this::refreshStats));
        dataService.getDevices().addListener((ListChangeListener<Device>) c -> Platform.runLater(() -> {
            refreshStats();
            refreshQuickDevices();
        }));

        refreshStats();
        refreshPanicBannerState();
        refreshQuickDevices();
    }

    private VBox createHeader() {
        VBox box = new VBox(4);
        Label title = new Label("Tổng quan Hệ thống (Dashboard)");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Giám sát trạng thái thiết bị, bộ lọc AI Engine (ONNX) và khóa mạng tức thì qua TCP Socket.");
        sub.getStyleClass().add("page-subtitle");
        box.getChildren().addAll(title, sub);
        return box;
    }

    private GridPane createKpiRow() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        for (int i = 0; i < 3; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(33.333);
            grid.getColumnConstraints().add(col);
        }

        lblOnlineDevicesValue = new Label("0 / 0");
        lblOnlineDevicesValue.getStyleClass().add("kpi-value");
        lblOnlineDevicesHint = new Label("Đang kết nối qua WireGuard / LAN");
        lblOnlineDevicesHint.getStyleClass().add("kpi-hint-success");

        lblBlockedTodayValue = new Label("0");
        lblBlockedTodayValue.getStyleClass().add("kpi-value");
        lblBlockedTodayHint = new Label("Truy vấn độc hại / vi phạm đã chặn");
        lblBlockedTodayHint.getStyleClass().add("kpi-hint-danger");

        lblAiEngineValue = new Label("Đang hoạt động");
        lblAiEngineValue.getStyleClass().add("kpi-value");
        lblAiEngineHint = new Label("ONNX Runtime • Phân tích tên miền real-time");
        lblAiEngineHint.getStyleClass().add("kpi-hint-success");

        grid.add(createKpiCard("Thiết bị đang online", lblOnlineDevicesValue, lblOnlineDevicesHint, MaterialDesignD.DEVICES, "kpi-icon-blue"), 0, 0);
        grid.add(createKpiCard("Số truy vấn bị chặn hôm nay", lblBlockedTodayValue, lblBlockedTodayHint, MaterialDesignS.SHIELD_ALERT, "kpi-icon-red"), 1, 0);
        grid.add(createKpiCard("Trạng thái AI Engine (ONNX)", lblAiEngineValue, lblAiEngineHint, MaterialDesignB.BRAIN, "kpi-icon-green"), 2, 0);

        return grid;
    }

    private VBox createKpiCard(String title, Label valueLabel, Label hintLabel, Ikon iconCode, String iconColorClass) {
        VBox card = new VBox(8);
        card.getStyleClass().add("kpi-card");

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        StackPane iconBox = new StackPane();
        iconBox.getStyleClass().addAll("kpi-icon-box", iconColorClass);
        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(20);
        iconBox.getChildren().add(icon);

        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("kpi-title");

        topRow.getChildren().addAll(iconBox, lblTitle);
        card.getChildren().addAll(topRow, valueLabel, hintLabel);
        return card;
    }

    private HBox createPanicControlBanner() {
        panicBanner = new HBox(20);
        panicBanner.setAlignment(Pos.CENTER_LEFT);
        panicBanner.getStyleClass().add("panic-banner");

        FontIcon shieldIcon = new FontIcon(MaterialDesignS.SHIELD_LOCK);
        shieldIcon.setIconSize(26);
        shieldIcon.setStyle("-fx-icon-color: #DC2626;");

        VBox textGroup = new VBox(2);
        Label lblTitle = new Label("Khóa mạng tức thì (Panic Lock)");
        lblTitle.getStyleClass().add("card-title");
        Label lblDesc = new Label("Ngắt kết nối Internet khẩn cấp toàn bộ thiết bị con hoặc bật/tắt AI Engine bảo vệ.");
        lblDesc.getStyleClass().add("card-subtitle");
        textGroup.getChildren().addAll(lblTitle, lblDesc);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        switchAiProtection = new ToggleSwitch("AI Engine (ONNX)");
        switchAiProtection.setSelected(dataService.isProtectionActive());
        switchAiProtection.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingGlobalSwitches) return;
            dataService.toggleProtection(newVal);
            toastHandler.show(
                    "AI Engine (ONNX)",
                    newVal ? "Đã kích hoạt bảo vệ và lọc DNS real-time." : "Đã tạm dừng hệ thống bảo vệ.",
                    newVal ? ToastManager.ToastType.SUCCESS : ToastManager.ToastType.WARNING
            );
        });

        switchGlobalPanic = new ToggleSwitch("Panic Lock (Khóa tất cả)");
        switchGlobalPanic.setSelected(dataService.isEmergencyPause());
        switchGlobalPanic.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (updatingGlobalSwitches) return;
            dataService.toggleEmergencyPause(newVal);
            refreshQuickDevices();
            toastHandler.show(
                    newVal ? "Đã kích hoạt Panic Lock" : "Đã mở khóa mạng",
                    newVal ? "Đã gửi lệnh khóa Internet tất cả thiết bị xuống VPS." : "Đã khôi phục kết nối Internet cho các thiết bị.",
                    newVal ? ToastManager.ToastType.DANGER : ToastManager.ToastType.SUCCESS
            );
        });

        panicBanner.getChildren().addAll(shieldIcon, textGroup, spacer, switchAiProtection, switchGlobalPanic);
        return panicBanner;
    }

    private HBox createMainGrid() {
        HBox row = new HBox(20);

        // Cột trái: Danh sách nhanh các thiết bị con kèm ToggleSwitch Khóa mạng tức thì
        VBox devicesCard = new VBox(14);
        devicesCard.getStyleClass().add("fg-card");
        HBox.setHgrow(devicesCard, Priority.ALWAYS);

        HBox devHeader = new HBox(10);
        devHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon devIcon = new FontIcon(MaterialDesignL.LAPTOP);
        devIcon.setIconSize(18);
        VBox devTitleBox = new VBox(2);
        Label devTitle = new Label("Thiết bị con & Khóa mạng tức thì");
        devTitle.getStyleClass().add("card-title");
        Label devSub = new Label("Gạt công tắc để gửi lệnh DEVICE_BLOCK qua TCP Socket xuống VPS");
        devSub.getStyleClass().add("card-subtitle");
        devTitleBox.getChildren().addAll(devTitle, devSub);
        devHeader.getChildren().addAll(devIcon, devTitleBox);

        quickDevicesList = new VBox(10);
        devicesCard.getChildren().addAll(devHeader, quickDevicesList);

        // Cột phải: Hoạt động truy cập gần đây
        VBox eventsCard = new VBox(14);
        eventsCard.getStyleClass().add("fg-card");
        eventsCard.setPrefWidth(390);
        eventsCard.setMinWidth(340);

        HBox eventHeader = new HBox(10);
        eventHeader.setAlignment(Pos.CENTER_LEFT);
        VBox eventTitleBox = new VBox(2);
        Label eventsTitle = new Label("Truy cập gần đây");
        eventsTitle.getStyleClass().add("card-title");
        Label eventsSub = new Label("Đồng bộ real-time từ DNS Firewall");
        eventsSub.getStyleClass().add("card-subtitle");
        eventTitleBox.getChildren().addAll(eventsTitle, eventsSub);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnViewAll = new Button("Xem tất cả");
        btnViewAll.setGraphic(new FontIcon(MaterialDesignA.ARROW_RIGHT));
        btnViewAll.setOnAction(e -> {
            if (navigateToLogs != null) {
                navigateToLogs.run();
            }
        });

        eventHeader.getChildren().addAll(eventTitleBox, sp, btnViewAll);

        recentEventsList = new VBox(10);
        eventsCard.getChildren().addAll(eventHeader, recentEventsList);

        row.getChildren().addAll(devicesCard, eventsCard);
        return row;
    }

    private void refreshPanicBannerState() {
        updatingGlobalSwitches = true;
        try {
            boolean paused = dataService.isEmergencyPause();
            if (switchGlobalPanic != null) {
                switchGlobalPanic.setSelected(paused);
            }
            if (panicBanner != null) {
                panicBanner.getStyleClass().remove("panic-banner-locked");
                if (paused) {
                    panicBanner.getStyleClass().add("panic-banner-locked");
                }
            }
            if (switchAiProtection != null) {
                switchAiProtection.setSelected(dataService.isProtectionActive());
            }
        } finally {
            updatingGlobalSwitches = false;
        }
    }

    private void refreshQuickDevices() {
        if (quickDevicesList == null) return;
        quickDevicesList.getChildren().clear();

        if (dataService.getDevices().isEmpty()) {
            Label empty = new Label("Chưa có thiết bị nào được thêm vào hệ thống.");
            empty.getStyleClass().add("card-subtitle");
            quickDevicesList.getChildren().add(empty);
            return;
        }

        for (Device device : dataService.getDevices()) {
            quickDevicesList.getChildren().add(createQuickDeviceRow(device));
        }
    }

    private HBox createQuickDeviceRow(Device device) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("sub-card");

        FontIcon devIcon = new FontIcon(resolveDeviceIcon(device.getType()));
        devIcon.setIconSize(22);

        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox nameRow = new HBox(8);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        Label lblName = new Label(device.getName());
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label lblStatus = new Label(device.getStatus());
        lblStatus.getStyleClass().add("status-badge");
        lblStatus.getStyleClass().add("Trực tuyến".equalsIgnoreCase(device.getStatus()) ? "badge-success" : "badge-neutral");
        nameRow.getChildren().addAll(lblName, lblStatus);

        Label lblMeta = new Label("IP: " + device.getIpAddress() + " • Hôm nay: " + device.getTimeSpentToday());
        lblMeta.getStyleClass().add("card-subtitle");
        info.getChildren().addAll(nameRow, lblMeta);

        ToggleSwitch panicSwitch = new ToggleSwitch("Khóa mạng");
        panicSwitch.setSelected(device.isBlocked());
        panicSwitch.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (device.isBlocked() != newVal) {
                dataService.toggleDeviceBlock(device);
                toastHandler.show(
                        newVal ? "Đã khóa mạng thiết bị" : "Đã mở khóa mạng",
                        (newVal ? "Đã gửi lệnh ngắt Internet: " : "Đã khôi phục Internet: ") + device.getName(),
                        newVal ? ToastManager.ToastType.WARNING : ToastManager.ToastType.SUCCESS
                );
            }
        });

        row.getChildren().addAll(devIcon, info, panicSwitch);
        return row;
    }

    private void refreshStats() {
        int totalDevices = dataService.getDevices().size();
        int onlineDevices = dataService.getOnlineDeviceCount();
        int blockedToday = dataService.getTotalBlockedEventsCount();
        boolean aiActive = dataService.isProtectionActive();

        if (lblOnlineDevicesValue != null) {
            lblOnlineDevicesValue.setText(onlineDevices + " / " + totalDevices);
        }
        if (lblBlockedTodayValue != null) {
            lblBlockedTodayValue.setText(String.valueOf(blockedToday));
        }
        if (lblAiEngineValue != null) {
            lblAiEngineValue.setText(aiActive ? "Đang hoạt động" : "Tạm dừng");
        }
        if (lblAiEngineHint != null) {
            lblAiEngineHint.getStyleClass().removeAll("kpi-hint-success", "kpi-hint-danger");
            lblAiEngineHint.getStyleClass().add(aiActive ? "kpi-hint-success" : "kpi-hint-danger");
            lblAiEngineHint.setText(aiActive ? "ONNX Runtime • Đang phân tích lưu lượng" : "ONNX Runtime • Bộ lọc tạm ngưng");
        }

        refreshPanicBannerState();

        if (recentEventsList != null) {
            recentEventsList.getChildren().clear();
            int max = Math.min(5, dataService.getAccessLogs().size());
            if (max == 0) {
                Label empty = new Label("Chưa có sự kiện truy cập nào.");
                empty.getStyleClass().add("card-subtitle");
                recentEventsList.getChildren().add(empty);
            } else {
                for (int i = 0; i < max; i++) {
                    recentEventsList.getChildren().add(createEventRow(dataService.getAccessLogs().get(i)));
                }
            }
        }
    }

    private HBox createEventRow(AccessLog log) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 12, 10, 12));
        row.getStyleClass().add("sub-card");

        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label lblDomain = new Label(log.getDomain());
        lblDomain.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label lblMeta = new Label(log.getDeviceName() + " • " + log.getTimestamp());
        lblMeta.getStyleClass().add("card-subtitle");
        info.getChildren().addAll(lblDomain, lblMeta);

        Label lblBadge = new Label(log.getAction());
        lblBadge.getStyleClass().add("status-badge");
        lblBadge.getStyleClass().add("ĐÃ CHẶN".equalsIgnoreCase(log.getAction()) ? "badge-danger" : "badge-success");

        row.getChildren().addAll(info, lblBadge);
        return row;
    }

    private Ikon resolveDeviceIcon(String type) {
        if (type == null) return MaterialDesignL.LAPTOP;
        return switch (type.toLowerCase()) {
            case "điện thoại" -> MaterialDesignC.CELLPHONE;
            case "máy tính bảng" -> MaterialDesignT.TABLET;
            case "máy tính để bàn" -> MaterialDesignD.DESKTOP_CLASSIC;
            default -> MaterialDesignL.LAPTOP;
        };
    }
}
