package org.example.desktopver1.controller;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.example.desktopver1.network.VpsClientService;
import org.example.desktopver1.service.DataService;
import org.example.desktopver1.view.AccessLogsView;
import org.example.desktopver1.view.ContentFilterView;
import org.example.desktopver1.view.DashboardView;
import org.example.desktopver1.view.DeviceManagerView;
import org.example.desktopver1.view.ToastManager;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignV;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;

import java.util.function.Consumer;

/**
 * Controller chính điều phối giao diện hiện đại của FamilyGuard:
 * - Root StackPane bọc ngoài phục vụ Non-blocking Toast Notification (ToastManager)
 * - Main BorderPane với Left Sidebar cố định (~220px) & Center Content Area chuyển đổi mượt mà
 * - Lắng nghe luồng TCP Socket từ VPS an toàn luồng (Platform.runLater)
 */
public class MainController {

    private final StackPane rootStackPane;
    private final BorderPane mainLayout;
    private final DataService dataService;
    private final Gson gson = new Gson();

    private DashboardView dashboardView;
    private DeviceManagerView deviceManagerView;
    private ContentFilterView contentFilterView;
    private AccessLogsView accessLogsView;

    private ToggleGroup navGroup;
    private ToggleButton btnDashboard;
    private ToggleButton btnDevices;
    private ToggleButton btnFilter;
    private ToggleButton btnLogs;

    private Label vpsStatusBadge;
    private FontIcon vpsStatusIcon;
    private boolean darkTheme = false;

    public MainController() {
        this.dataService = DataService.getInstance();
        this.rootStackPane = new StackPane();
        this.rootStackPane.getStyleClass().add("root-shell");
        this.mainLayout = new BorderPane();

        initViews();
        buildLayout();
        bindVpsSocketEvents();
    }

    public StackPane getRoot() {
        return rootStackPane;
    }

    private void initViews() {
        Consumer<String> infoToast = msg -> showToast("Thông báo", msg, ToastManager.ToastType.SUCCESS);

        dashboardView = new DashboardView(dataService, this::showToast, () -> navigateTo(accessLogsView, btnLogs));
        deviceManagerView = new DeviceManagerView(dataService, infoToast, this::showToast);
        contentFilterView = new ContentFilterView(dataService, infoToast, this::showToast);
        accessLogsView = new AccessLogsView(dataService, infoToast, this::showToast);
    }

    private void buildLayout() {
        mainLayout.setLeft(createSidebar());
        rootStackPane.getChildren().add(mainLayout);

        // Mặc định mở tab Tổng quan (Dashboard)
        btnDashboard.setSelected(true);
        navigateTo(dashboardView, btnDashboard);
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("sidebar");

        // 1. Logo + Tên ứng dụng FamilyGuard
        VBox brandBox = new VBox(10);
        brandBox.getStyleClass().add("brand-box");

        HBox brandHeader = new HBox(10);
        brandHeader.setAlignment(Pos.CENTER_LEFT);

        StackPane logoBadge = new StackPane();
        logoBadge.getStyleClass().add("brand-logo-badge");
        FontIcon logoIcon = new FontIcon(MaterialDesignS.SHIELD_CHECK);
        logoIcon.setIconSize(20);
        logoIcon.getStyleClass().add("brand-logo-icon");
        logoBadge.getChildren().add(logoIcon);

        VBox titleGroup = new VBox(1);
        Label lblBrand = new Label("FamilyGuard");
        lblBrand.getStyleClass().add("brand-title");
        Label lblSub = new Label("Parental Control");
        lblSub.getStyleClass().add("brand-subtitle");
        titleGroup.getChildren().addAll(lblBrand, lblSub);

        brandHeader.getChildren().addAll(logoBadge, titleGroup);

        // Badge trạng thái kết nối VPS (Connected / Disconnected real-time)
        vpsStatusIcon = new FontIcon(MaterialDesignC.CIRCLE_MEDIUM);
        vpsStatusIcon.setIconSize(14);
        vpsStatusBadge = new Label("Disconnected");
        vpsStatusBadge.setGraphic(vpsStatusIcon);
        vpsStatusBadge.setGraphicTextGap(6);
        vpsStatusBadge.setMaxWidth(Double.MAX_VALUE);
        vpsStatusBadge.getStyleClass().add("vps-badge");

        brandBox.getChildren().addAll(brandHeader, vpsStatusBadge);

        // 2. Menu điều hướng dạng ToggleButton full-width kèm icon
        Label navLabel = new Label("ĐIỀU HƯỚNG");
        navLabel.getStyleClass().add("nav-section-label");

        navGroup = new ToggleGroup();

        btnDashboard = createNavToggle("Tổng quan (Dashboard)", MaterialDesignV.VIEW_DASHBOARD);
        btnDevices = createNavToggle("Quản lý Thiết bị", MaterialDesignD.DEVICES);
        btnFilter = createNavToggle("Bộ lọc Web & App", MaterialDesignS.SHIELD_LOCK);
        btnLogs = createNavToggle("Nhật ký truy cập", MaterialDesignF.FORMAT_LIST_BULLETED);

        btnDashboard.setOnAction(e -> navigateTo(dashboardView, btnDashboard));
        btnDevices.setOnAction(e -> navigateTo(deviceManagerView, btnDevices));
        btnFilter.setOnAction(e -> navigateTo(contentFilterView, btnFilter));
        btnLogs.setOnAction(e -> navigateTo(accessLogsView, btnLogs));

        // Ngăn bỏ chọn toàn bộ nút trong ToggleGroup
        navGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });

        VBox navList = new VBox(4, btnDashboard, btnDevices, btnFilter, btnLogs);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // 3. Footer: Nút chuyển nhanh PrimerLight <-> PrimerDark
        VBox footer = new VBox(8);
        footer.getStyleClass().add("sidebar-footer");

        FontIcon themeIcon = new FontIcon(MaterialDesignW.WEATHER_NIGHT);
        themeIcon.setIconSize(16);
        Button btnThemeToggle = new Button("Giao diện Tối");
        btnThemeToggle.setGraphic(themeIcon);
        btnThemeToggle.setMaxWidth(Double.MAX_VALUE);
        btnThemeToggle.getStyleClass().add("theme-toggle-btn");
        btnThemeToggle.setOnAction(e -> toggleTheme(btnThemeToggle, themeIcon));

        Label lblVer = new Label("TCP Socket :9000 • v2.0");
        lblVer.getStyleClass().add("brand-subtitle");

        footer.getChildren().addAll(btnThemeToggle, lblVer);

        sidebar.getChildren().addAll(brandBox, navLabel, navList, spacer, footer);
        return sidebar;
    }

    private ToggleButton createNavToggle(String text, Ikon iconCode) {
        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(18);
        ToggleButton btn = new ToggleButton(text, icon);
        btn.setToggleGroup(navGroup);
        btn.getStyleClass().add("nav-toggle");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private void navigateTo(Node view, ToggleButton targetBtn) {
        if (targetBtn != null) {
            targetBtn.setSelected(true);
        }
        if (mainLayout.getCenter() != view) {
            view.setOpacity(0.0);
            mainLayout.setCenter(view);
            FadeTransition fade = new FadeTransition(Duration.millis(150), view);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.play();
        }
    }

    private void toggleTheme(Button btnThemeToggle, FontIcon themeIcon) {
        darkTheme = !darkTheme;
        if (darkTheme) {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            if (!rootStackPane.getStyleClass().contains("dark-theme")) {
                rootStackPane.getStyleClass().add("dark-theme");
            }
            themeIcon.setIconCode(MaterialDesignW.WEATHER_SUNNY);
            btnThemeToggle.setText("Giao diện Sáng");
        } else {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            rootStackPane.getStyleClass().remove("dark-theme");
            themeIcon.setIconCode(MaterialDesignW.WEATHER_NIGHT);
            btnThemeToggle.setText("Giao diện Tối");
        }
    }

    private void bindVpsSocketEvents() {
        VpsClientService vps = dataService.getVpsClient();
        if (vps == null) {
            updateVpsBadge(VpsClientService.ConnectionState.DISCONNECTED);
            return;
        }

        updateVpsBadge(vps.getConnectionState());
        vps.connectionStateProperty().addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> updateVpsBadge(newVal)));

        vps.addListener(new VpsClientService.VpsMessageListener() {
            @Override
            public void onStateChanged(VpsClientService.ConnectionState newState, String message) {
                Platform.runLater(() -> {
                    updateVpsBadge(newState);
                    if (newState == VpsClientService.ConnectionState.AUTHENTICATED) {
                        showToast("Kết nối VPS thành công", "Đã xác thực kênh giám sát TCP :9000.", ToastManager.ToastType.SUCCESS);
                    }
                });
            }

            @Override
            public void onMessageReceived(String rawJson) {
                handleIncomingSocketMessage(rawJson);
            }

            @Override
            public void onMessageSent(String rawJson, boolean success) {
            }
        });
    }

    private void updateVpsBadge(VpsClientService.ConnectionState state) {
        if (vpsStatusBadge == null || state == null) {
            return;
        }
        vpsStatusBadge.getStyleClass().removeAll("vps-badge-connected", "vps-badge-connecting", "vps-badge-disconnected");
        switch (state) {
            case AUTHENTICATED, CONNECTED -> {
                vpsStatusBadge.setText("VPS: Connected");
                vpsStatusBadge.getStyleClass().add("vps-badge-connected");
            }
            case CONNECTING -> {
                vpsStatusBadge.setText("VPS: Connecting...");
                vpsStatusBadge.getStyleClass().add("vps-badge-connecting");
            }
            default -> {
                vpsStatusBadge.setText("VPS: Disconnected");
                vpsStatusBadge.getStyleClass().add("vps-badge-disconnected");
            }
        }
    }

    /**
     * Xử lý thông báo từ luồng TCP Socket nền bằng Non-blocking Toast (không tạo Alert/Stage mới).
     */
    private void handleIncomingSocketMessage(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return;
        }
        try {
            JsonObject obj = gson.fromJson(rawJson, JsonObject.class);
            if (obj == null) {
                return;
            }
            String action = obj.has("action") ? obj.get("action").getAsString() : "";

            // Nếu VPS gửi SYNC_LOGS có tên miền bị chặn -> bắn Toast DANGER
            if ("SYNC_LOGS".equalsIgnoreCase(action) && obj.has("logs") && obj.get("logs").isJsonArray()) {
                JsonArray logs = obj.getAsJsonArray("logs");
                String firstBlockedDomain = null;
                String firstBlockedDevice = null;
                int blockedCount = 0;

                for (JsonElement elem : logs) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject entry = elem.getAsJsonObject();
                    String status = entry.has("status") ? entry.get("status").getAsString() : "";
                    if ("BLOCKED".equalsIgnoreCase(status)) {
                        blockedCount++;
                        if (firstBlockedDomain == null) {
                            firstBlockedDomain = entry.has("domain") ? entry.get("domain").getAsString() : "Tên miền lạ";
                            String ip = entry.has("client_ip") ? entry.get("client_ip").getAsString() : "";
                            firstBlockedDevice = dataService.findDeviceNameByIp(ip);
                        }
                    }
                }

                if (blockedCount > 0) {
                    final String domain = firstBlockedDomain;
                    final String device = firstBlockedDevice;
                    final int extra = blockedCount - 1;
                    Platform.runLater(() -> {
                        String msg = device + " vừa bị chặn truy cập: " + domain
                                + (extra > 0 ? " (+" + extra + " truy vấn khác)" : "");
                        showToast("Phát hiện truy cập bị chặn", msg, ToastManager.ToastType.DANGER);
                    });
                }
                return;
            }

            // Nếu VPS gửi gói cảnh báo riêng (ALERT / BLOCK_ALERT)
            if (action.toUpperCase().contains("ALERT") || action.toUpperCase().contains("BLOCKED")) {
                String domain = obj.has("domain") ? obj.get("domain").getAsString() : "";
                String msg = obj.has("message") ? obj.get("message").getAsString()
                        : (!domain.isEmpty() ? "Đã chặn truy cập tên miền nguy hại: " + domain : "Phát hiện cảnh báo an toàn mạng từ VPS.");
                Platform.runLater(() -> showToast("Cảnh báo bảo mật VPS", msg, ToastManager.ToastType.DANGER));
            }
        } catch (Exception ignored) {
            // Bỏ qua các gói tin không phải JSON cảnh báo
        }
    }

    public void showToast(String title, String message, ToastManager.ToastType type) {
        ToastManager.showToast(rootStackPane, title, message, type);
    }
}
