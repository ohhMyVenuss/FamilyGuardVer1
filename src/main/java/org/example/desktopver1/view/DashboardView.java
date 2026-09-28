package org.example.desktopver1.view;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.example.desktopver1.model.AccessLog;
import org.example.desktopver1.service.DataService;

import java.util.function.Consumer;

/**
 * Giao diện Dashboard (Tổng quan):
 * - Banner trạng thái bảo vệ & nút khóa mạng khẩn cấp
 * - Thẻ KPI số liệu thống kê trực quan
 * - Biểu đồ PieChart phân bổ thời lượng sử dụng
 * - Danh sách sự kiện truy cập đáng chú ý gần nhất
 */
public class DashboardView extends ScrollPane {

    private final DataService dataService;
    private final Consumer<String> toastNotifier;
    private final Runnable navigateToLogs;

    private HBox statusBanner;
    private Label bannerTitle;
    private Label bannerDesc;
    private Button btnToggleProtection;
    private Button btnEmergency;

    // Dữ liệu động cho Biểu đồ PieChart và Bảng thống kê KPI
    private final ObservableList<PieChart.Data> chartData = FXCollections.observableArrayList();
    private Label lblBlockedKpi;
    private Label lblOnlineDevicesKpi;
    private Label lblViolationKpi;
    private VBox eventsList;

    public DashboardView(DataService dataService, Consumer<String> toastNotifier, Runnable navigateToLogs) {
        this.dataService = dataService;
        this.toastNotifier = toastNotifier;
        this.navigateToLogs = navigateToLogs;

        setFitToWidth(true);
        setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        VBox contentBox = new VBox(24);
        contentBox.getStyleClass().add("view-container");

        // 1. Banner trạng thái bảo vệ
        contentBox.getChildren().add(createStatusBanner());

        // 2. Hàng 4 thẻ thống kê KPI
        contentBox.getChildren().add(createKpiGrid());

        // 3. Khu vực biểu đồ và hoạt động gần đây
        contentBox.getChildren().add(createChartsAndActivitySection());

        // 4. Mẹo an toàn internet
        contentBox.getChildren().add(createSafetyTipCard());

        setContent(contentBox);

        // Lắng nghe thay đổi trạng thái từ DataService
        dataService.protectionActiveProperty().addListener((obs, oldVal, newVal) -> updateBannerStyle(newVal));
        dataService.emergencyPauseProperty().addListener((obs, oldVal, newVal) -> updateEmergencyButton(newVal));

        // Tự động làm mới PieChart và Thống kê khi có bản ghi SYNC_LOGS mới từ VPS
        dataService.getAccessLogs().addListener((javafx.collections.ListChangeListener<AccessLog>) c -> {
            javafx.application.Platform.runLater(this::refreshChartsAndStats);
        });
        dataService.getDevices().addListener((javafx.collections.ListChangeListener<org.example.desktopver1.model.Device>) c -> {
            javafx.application.Platform.runLater(this::refreshChartsAndStats);
        });

        refreshChartsAndStats();
    }

    private HBox createStatusBanner() {
        statusBanner = new HBox(20);
        statusBanner.setAlignment(Pos.CENTER_LEFT);

        VBox textGroup = new VBox(4);
        bannerTitle = new Label();
        bannerTitle.getStyleClass().add("status-banner-title");
        bannerDesc = new Label();
        bannerDesc.getStyleClass().add("status-banner-desc");
        textGroup.getChildren().addAll(bannerTitle, bannerDesc);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnToggleProtection = new Button();
        btnToggleProtection.getStyleClass().add("btn-outline");

        btnToggleProtection.setOnAction(e -> {
            boolean nextState = !dataService.isProtectionActive();
            dataService.toggleProtection(nextState);
            toastNotifier.accept(nextState ? "Hệ thống bảo vệ đã được BẬT." : "Hệ thống bảo vệ đã TẠM DỪNG!");
        });

        btnEmergency = new Button("⚡ KHÓA MẠNG KHẨN CẤP");
        btnEmergency.getStyleClass().add("btn-danger");
        btnEmergency.setOnAction(e -> {
            boolean nextEmergency = !dataService.isEmergencyPause();
            dataService.toggleEmergencyPause(nextEmergency);
            toastNotifier.accept(nextEmergency ? "ĐÃ KHÓA MẠNG TẤT CẢ THIẾT BỊ CỦA CON!" : "Đã hủy chế độ khóa mạng khẩn cấp.");
        });

        statusBanner.getChildren().addAll(textGroup, spacer, btnToggleProtection, btnEmergency);

        updateBannerStyle(dataService.isProtectionActive());
        updateEmergencyButton(dataService.isEmergencyPause());

        return statusBanner;
    }

    private void updateBannerStyle(boolean active) {
        statusBanner.getStyleClass().removeAll("status-banner-active", "status-banner-paused");
        if (active) {
            statusBanner.getStyleClass().add("status-banner-active");
            bannerTitle.setText("🛡️ Hệ thống bảo vệ mạng gia đình: ĐANG HOẠT ĐỘNG");
            bannerDesc.setText("Bộ lọc nội dung độc hại & cơ chế giám sát thời gian thực đang bảo vệ tất cả thiết bị của trẻ.");
            btnToggleProtection.setText("Tạm dừng bảo vệ");
        } else {
            statusBanner.getStyleClass().add("status-banner-paused");
            bannerTitle.setText("⚠️ CẢNH BÁO: Hệ thống bảo vệ đang TẠM DỪNG!");
            bannerDesc.setText("Các thiết bị của trẻ hiện không bị áp dụng bộ lọc web và giới hạn thời gian.");
            btnToggleProtection.setText("Kích hoạt bảo vệ");
        }
    }

    private void updateEmergencyButton(boolean isPaused) {
        if (isPaused) {
            btnEmergency.setText("🔓 MỞ KHÓA MẠNG LẠI");
            btnEmergency.getStyleClass().removeAll("btn-danger");
            btnEmergency.getStyleClass().add("btn-warning");
        } else {
            btnEmergency.setText("⚡ KHÓA MẠNG KHẨN CẤP");
            btnEmergency.getStyleClass().removeAll("btn-warning");
            btnEmergency.getStyleClass().add("btn-danger");
        }
    }

    private GridPane createKpiGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(25);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(25);
        ColumnConstraints col3 = new ColumnConstraints();
        col3.setPercentWidth(25);
        ColumnConstraints col4 = new ColumnConstraints();
        col4.setPercentWidth(25);
        grid.getColumnConstraints().addAll(col1, col2, col3, col4);

        grid.add(createKpiCard("⏱️", "Thời gian online hôm nay", "3h 45m", "Hạn mức cho phép: 4h00m", false), 0, 0);

        lblBlockedKpi = new Label(dataService.getTotalBlockedEventsCount() + " lần");
        lblBlockedKpi.getStyleClass().add("kpi-value");
        grid.add(createKpiCardWithLabel("🛡️", "Số lượt trang đã chặn", lblBlockedKpi, "+8 lần so với hôm qua", false), 1, 0);

        lblOnlineDevicesKpi = new Label(dataService.getOnlineDeviceCount() + " / " + dataService.getDevices().size());
        lblOnlineDevicesKpi.getStyleClass().add("kpi-value");
        grid.add(createKpiCardWithLabel("💻", "Thiết bị trẻ đang online", lblOnlineDevicesKpi, "Đang kết nối qua WireGuard/Wi-Fi", false), 2, 0);

        lblViolationKpi = new Label("0 lần");
        lblViolationKpi.getStyleClass().add("kpi-value");
        grid.add(createKpiCardWithLabel("⚠️", "Cảnh báo vi phạm", lblViolationKpi, "Cố truy cập web cấm", true), 3, 0);

        return grid;
    }

    private VBox createKpiCard(String icon, String title, String value, String hint, boolean isDangerHint) {
        Label lblValue = new Label(value);
        lblValue.getStyleClass().add("kpi-value");
        return createKpiCardWithLabel(icon, title, lblValue, hint, isDangerHint);
    }

    private VBox createKpiCardWithLabel(String icon, String title, Label lblValue, String hint, boolean isDangerHint) {
        VBox card = new VBox(6);
        card.getStyleClass().add("kpi-card");

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label lblIcon = new Label(icon);
        lblIcon.setStyle("-fx-font-size: 20px;");

        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("kpi-title");

        topRow.getChildren().addAll(lblIcon, lblTitle);

        Label lblHint = new Label(hint);
        lblHint.getStyleClass().add(isDangerHint ? "kpi-hint-danger" : "kpi-hint");

        card.getChildren().addAll(topRow, lblValue, lblHint);
        return card;
    }

    private HBox createChartsAndActivitySection() {
        HBox box = new HBox(20);

        // Cột trái: Biểu đồ tròn
        VBox chartCard = new VBox(14);
        chartCard.getStyleClass().add("card");
        HBox.setHgrow(chartCard, Priority.ALWAYS);

        Label chartTitle = new Label("Phân bổ thời lượng sử dụng Internet");
        chartTitle.getStyleClass().add("card-title");
        Label chartSub = new Label("Thống kê theo lưu lượng và danh mục truy cập hôm nay");
        chartSub.getStyleClass().add("card-subtitle");

        PieChart pieChart = new PieChart(chartData);
        pieChart.setPrefHeight(280);
        pieChart.setLegendVisible(true);

        chartCard.getChildren().addAll(chartTitle, chartSub, pieChart);

        // Cột phải: Danh sách sự kiện gần nhất
        VBox eventsCard = new VBox(14);
        eventsCard.getStyleClass().add("card");
        eventsCard.setPrefWidth(420);

        HBox eventHeader = new HBox();
        eventHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label eventsTitle = new Label("Hoạt động truy cập gần đây");
        eventsTitle.getStyleClass().add("card-title");
        Label eventsSub = new Label("Các bản ghi từ thiết bị của trẻ");
        eventsSub.getStyleClass().add("card-subtitle");
        titleBox.getChildren().addAll(eventsTitle, eventsSub);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnViewAll = new Button("Xem tất cả →");
        btnViewAll.getStyleClass().add("btn-outline");
        btnViewAll.setStyle("-fx-font-size: 11px; -fx-padding: 5 10;");
        btnViewAll.setOnAction(e -> {
            if (navigateToLogs != null) {
                navigateToLogs.run();
            }
        });

        eventHeader.getChildren().addAll(titleBox, sp, btnViewAll);

        eventsList = new VBox(10);
        eventsCard.getChildren().addAll(eventHeader, eventsList);

        box.getChildren().addAll(chartCard, eventsCard);
        return box;
    }

    /**
     * Tự động làm mới dữ liệu biểu đồ tròn PieChart, thẻ KPI và bảng sự kiện gần đây
     * Được gọi qua Platform.runLater() khi luồng Socket nhận mảng SYNC_LOGS.
     */
    public void refreshChartsAndStats() {
        int blockedCount = 0;
        int allowedCount = 0;
        int eduCount = 0;
        int entertainmentCount = 0;

        for (AccessLog log : dataService.getAccessLogs()) {
            if ("ĐÃ CHẶN".equalsIgnoreCase(log.getAction())) {
                blockedCount++;
            } else {
                allowedCount++;
                String d = log.getDomain() != null ? log.getDomain().toLowerCase() : "";
                if (d.contains("khan") || d.contains("olm") || d.contains("vietjack") || d.contains("hocmai") || d.contains("coursera") || d.contains("scratch")) {
                    eduCount++;
                } else if (d.contains("youtube") || d.contains("googlevideo") || d.contains("ytimg") || d.contains("facebook") || d.contains("fbcdn") || d.contains("tiktok")) {
                    entertainmentCount++;
                }
            }
        }

        int generalCount = allowedCount - eduCount - entertainmentCount;
        if (generalCount < 0) generalCount = 0;

        chartData.clear();
        if (blockedCount > 0) {
            chartData.add(new PieChart.Data("Đã chặn vi phạm (" + blockedCount + ")", blockedCount));
        }
        if (eduCount > 0) {
            chartData.add(new PieChart.Data("Học tập trực tuyến (" + eduCount + ")", eduCount));
        }
        if (entertainmentCount > 0) {
            chartData.add(new PieChart.Data("Video & MXH (" + entertainmentCount + ")", entertainmentCount));
        }
        if (generalCount > 0 || chartData.isEmpty()) {
            int displayGen = generalCount > 0 ? generalCount : 1;
            chartData.add(new PieChart.Data("Duyệt web an toàn (" + displayGen + ")", displayGen));
        }

        if (lblBlockedKpi != null) {
            lblBlockedKpi.setText(dataService.getTotalBlockedEventsCount() + " lần");
        }
        if (lblOnlineDevicesKpi != null) {
            lblOnlineDevicesKpi.setText(dataService.getOnlineDeviceCount() + " / " + dataService.getDevices().size());
        }
        if (lblViolationKpi != null) {
            lblViolationKpi.setText(blockedCount + " lần");
        }

        if (eventsList != null) {
            eventsList.getChildren().clear();
            int max = Math.min(5, dataService.getAccessLogs().size());
            for (int i = 0; i < max; i++) {
                AccessLog log = dataService.getAccessLogs().get(i);
                eventsList.getChildren().add(createEventRow(log));
            }
        }
    }

    private HBox createEventRow(AccessLog log) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 12, 10, 12));
        row.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 8px; -fx-border-color: #E2E8F0; -fx-border-radius: 8px;");

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label lblDomain = new Label(log.getDomain());
        lblDomain.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1E293B;");

        Label lblMeta = new Label(log.getDeviceName() + " • " + log.getTimestamp());
        lblMeta.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        info.getChildren().addAll(lblDomain, lblMeta);

        Label lblBadge = new Label(log.getAction());
        lblBadge.getStyleClass().add("badge");
        if ("ĐÃ CHẶN".equalsIgnoreCase(log.getAction())) {
            lblBadge.getStyleClass().add("badge-danger");
        } else {
            lblBadge.getStyleClass().add("badge-success");
        }

        row.getChildren().addAll(info, lblBadge);
        return row;
    }

    private HBox createSafetyTipCard() {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setStyle("-fx-background-color: #EFF6FF; -fx-background-radius: 10px; -fx-border-color: #BFDBFE; -fx-border-radius: 10px;");

        Label lblTipIcon = new Label("💡");
        lblTipIcon.setStyle("-fx-font-size: 24px;");

        VBox tipText = new VBox(2);
        Label tipTitle = new Label("Mẹo bảo vệ sức khỏe và an toàn số cho con");
        tipTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1E40AF;");
        Label tipContent = new Label("Các chuyên gia khuyến cáo trẻ em nên nghỉ ngơi sau mỗi 45 phút học tập online và tắt màn hình ít nhất 60 phút trước giờ đi ngủ.");
        tipContent.setStyle("-fx-font-size: 12px; -fx-text-fill: #1E3A8A;");
        tipText.getChildren().addAll(tipTitle, tipContent);

        card.getChildren().addAll(lblTipIcon, tipText);
        return card;
    }
}
