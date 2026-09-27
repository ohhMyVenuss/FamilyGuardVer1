package org.example.desktopver1.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.example.desktopver1.network.VpsClientService;
import org.example.desktopver1.service.DataService;
import org.example.desktopver1.view.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controller chính điều phối ứng dụng FamilyGuard:
 * - Điều hướng Sidebar giữa 5 màn hình chức năng
 * - Thanh Header cố định với thông tin quản trị
 * - Hệ thống Toast Popup thông báo tương tác cho người dùng
 */
public class MainController {

    private final BorderPane root;
    private final StackPane centerContainer;
    private final DataService dataService;

    // Các màn hình (Views)
    private DashboardView dashboardView;
    private TimeManagementView timeManagementView;
    private ContentFilterView contentFilterView;
    private DeviceManagerView deviceManagerView;
    private AccessLogsView accessLogsView;

    // Các nút menu
    private final List<Button> menuButtons = new ArrayList<>();
    private Button btnActive;

    // Toast Notification Box
    private HBox toastBox;
    private Label toastLabel;
    private Timeline toastTimer;

    public MainController() {
        this.dataService = DataService.getInstance();
        this.root = new BorderPane();
        this.centerContainer = new StackPane();

        initViews();
        buildLayout();
    }

    public BorderPane getRoot() {
        return root;
    }

    private void initViews() {
        dashboardView = new DashboardView(dataService, this::showToast, () -> navigateTo(accessLogsView, menuButtons.get(4)));
        timeManagementView = new TimeManagementView(dataService, this::showToast);
        contentFilterView = new ContentFilterView(dataService, this::showToast);
        deviceManagerView = new DeviceManagerView(dataService, this::showToast);
        accessLogsView = new AccessLogsView(dataService, this::showToast);
    }

    private void buildLayout() {
        // 1. Sidebar bên trái
        root.setLeft(createSidebar());

        // 2. Header trên cùng
        root.setTop(createTopHeader());

        // 3. Khung nội dung chính ở giữa
        root.setCenter(centerContainer);

        // Khởi tạo Toast container
        setupToastBox();

        // Mặc định mở tab Dashboard
        if (!menuButtons.isEmpty()) {
            navigateTo(dashboardView, menuButtons.get(0));
        }
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

        // Brand box
        VBox brandBox = new VBox(4);
        brandBox.getStyleClass().add("brand-box");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("🛡️");
        icon.getStyleClass().add("brand-icon");
        Label title = new Label("FamilyGuard");
        title.getStyleClass().add("brand-title");
        titleRow.getChildren().addAll(icon, title);

        Label sub = new Label("Kiểm soát & Bảo vệ Internet");
        sub.getStyleClass().add("brand-subtitle");
        brandBox.getChildren().addAll(titleRow, sub);

        // Menu Section
        Label navTitle = new Label("QUẢN TRỊ TRUY CẬP");
        navTitle.getStyleClass().add("nav-section-title");

        Button btnDashboard = createNavButton("📊  Tổng quan & Cảnh báo");
        Button btnTime = createNavButton("⏱️  Quản lý thời gian");
        Button btnFilter = createNavButton("🛡️  Bộ lọc nội dung web");
        Button btnDevices = createNavButton("💻  Thiết bị của con");
        Button btnLogs = createNavButton("📋  Nhật ký truy cập");

        menuButtons.add(btnDashboard);
        menuButtons.add(btnTime);
        menuButtons.add(btnFilter);
        menuButtons.add(btnDevices);
        menuButtons.add(btnLogs);

        btnDashboard.setOnAction(e -> navigateTo(dashboardView, btnDashboard));
        btnTime.setOnAction(e -> navigateTo(timeManagementView, btnTime));
        btnFilter.setOnAction(e -> navigateTo(contentFilterView, btnFilter));
        btnDevices.setOnAction(e -> navigateTo(deviceManagerView, btnDevices));
        btnLogs.setOnAction(e -> navigateTo(accessLogsView, btnLogs));

        VBox navBox = new VBox(6);
        navBox.getChildren().addAll(btnDashboard, btnTime, btnFilter, btnDevices, btnLogs);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Footer thông tin hệ thống
        VBox footer = new VBox(4);
        footer.getStyleClass().add("sidebar-footer");
        Label lblStatus = new Label("🟢 Dịch vụ DNS: Hoạt động");
        lblStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #10B981; -fx-font-weight: bold;");
        Label lblVer = new Label("Hệ thống kiểm soát mạng v1.0");
        lblVer.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748B;");
        footer.getChildren().addAll(lblStatus, lblVer);

        sidebar.getChildren().addAll(brandBox, navTitle, navBox, spacer, footer);
        return sidebar;
    }

    private Button createNavButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private void navigateTo(Node view, Button button) {
        // Cập nhật giao diện menu active
        if (btnActive != null) {
            btnActive.getStyleClass().remove("nav-button-active");
        }
        btnActive = button;
        if (btnActive != null && !btnActive.getStyleClass().contains("nav-button-active")) {
            btnActive.getStyleClass().add("nav-button-active");
        }

        // Đặt nội dung hiển thị
        centerContainer.getChildren().clear();
        centerContainer.getChildren().addAll(view, toastBox);
    }

    private HBox createTopHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("top-header");

        VBox leftText = new VBox(2);
        Label lblWelcome = new Label("Bảng Điều Khiển Của Phụ Huynh");
        lblWelcome.getStyleClass().add("header-welcome");
        Label lblSub = new Label("Giám sát và quản lý các kết nối an toàn cho trẻ trong mạng nội bộ");
        lblSub.getStyleClass().add("header-subtitle");
        leftText.getChildren().addAll(lblWelcome, lblSub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox rightBox = new HBox(14);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        // Huy hiệu trạng thái kết nối VPS ngầm
        Label badgeVps = new Label();
        badgeVps.getStyleClass().add("header-badge");
        VpsClientService vps = dataService.getVpsClient();
        if (vps != null) {
            updateVpsBadge(badgeVps, vps.getConnectionState());
            vps.connectionStateProperty().addListener((obs, oldVal, newVal) -> updateVpsBadge(badgeVps, newVal));

            vps.addListener(new VpsClientService.VpsMessageListener() {
                @Override
                public void onStateChanged(VpsClientService.ConnectionState newState, String message) {
                    // Trạng thái đã được update qua Property
                }

                @Override
                public void onMessageReceived(String rawJson) {
                    javafx.application.Platform.runLater(() -> {
                        showToast("📡 VPS gửi dữ liệu: " + (rawJson.length() > 60 ? rawJson.substring(0, 57) + "..." : rawJson));
                    });
                }

                @Override
                public void onMessageSent(String rawJson, boolean success) {
                }
            });
        }

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi-VN")));
        Label lblDate = new Label("📅 " + today);
        lblDate.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B; -fx-font-weight: 500;");

        Label badgeAdmin = new Label("👤 Tài khoản Phụ Huynh");
        badgeAdmin.getStyleClass().add("header-badge");

        rightBox.getChildren().addAll(badgeVps, lblDate, badgeAdmin);

        header.getChildren().addAll(leftText, spacer, rightBox);
        return header;
    }

    private void updateVpsBadge(Label badge, VpsClientService.ConnectionState state) {
        if (state == null) return;
        badge.setText(state.getIcon() + " " + state.getDescription());
        String bg;
        switch (state) {
            case AUTHENTICATED -> bg = "#DEF7EC";
            case CONNECTED -> bg = "#E1EFFE";
            case CONNECTING -> bg = "#FEF08A";
            default -> bg = "#FDE8E8";
        }
        badge.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + state.getColorHex() + "; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12; -fx-font-size: 11px;");
    }

    private void setupToastBox() {
        toastBox = new HBox(8);
        toastBox.getStyleClass().add("toast-box");
        toastBox.setVisible(false);
        toastBox.setManaged(false);
        StackPane.setAlignment(toastBox, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toastBox, new Insets(0, 24, 24, 0));

        Label toastIcon = new Label("🔔");
        toastIcon.setStyle("-fx-font-size: 16px;");

        toastLabel = new Label();
        toastLabel.getStyleClass().add("toast-text");

        toastBox.getChildren().addAll(toastIcon, toastLabel);
    }

    public void showToast(String message) {
        if (toastTimer != null) {
            toastTimer.stop();
        }

        toastLabel.setText(message);
        toastBox.setVisible(true);
        toastBox.setManaged(true);

        toastTimer = new Timeline(new KeyFrame(Duration.seconds(3.5), e -> {
            toastBox.setVisible(false);
            toastBox.setManaged(false);
        }));
        toastTimer.play();
    }
}
