package org.example.desktopver1.view;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.example.desktopver1.model.Device;
import org.example.desktopver1.network.VpsClientService;
import org.example.desktopver1.service.DataService;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * Giao diện Quản lý thiết bị (Device Management):
 * - Theo dõi các thiết bị của con đang kết nối trong gia đình
 * - Tự động hóa cấp cấu hình WireGuard VPN & Sinh mã QR trực tiếp từ VPS
 * - Kiểm tra IP, MAC, trạng thái Online/Offline
 * - Cắt mạng internet hoặc khôi phục kết nối cho từng máy
 * - Tích hợp mô phỏng quét mạng nội bộ (LAN Scan)
 */
public class DeviceManagerView extends ScrollPane {

    private final DataService dataService;
    private final Consumer<String> toastNotifier;
    private VBox devicesContainer;
    private Dialog<?> waitingDialog;

    public DeviceManagerView(DataService dataService, Consumer<String> toastNotifier) {
        this.dataService = dataService;
        this.toastNotifier = toastNotifier;

        // Lắng nghe sự kiện cấp cấu hình WireGuard từ VPS
        VpsClientService vps = dataService.getVpsClient() != null ? dataService.getVpsClient() : VpsClientService.getInstance();
        if (vps != null) {
            vps.addListener(new VpsClientService.VpsMessageListener() {
                @Override
                public void onStateChanged(VpsClientService.ConnectionState newState, String message) {}

                @Override
                public void onMessageReceived(String rawJson) {}

                @Override
                public void onMessageSent(String rawJson, boolean success) {}

                @Override
                public void onWireGuardPeerCreated(VpsClientService.WireGuardPeerResult result) {
                    handleWireGuardPeerResult(result);
                }
            });
        }

        // Tự động làm mới danh sách thiết bị và cập nhật thời gian sử dụng khi có sự kiện mạng
        dataService.getAccessLogs().addListener((javafx.collections.ListChangeListener<org.example.desktopver1.model.AccessLog>) c -> {
            javafx.application.Platform.runLater(this::refreshDevicesList);
        });
        dataService.getDevices().addListener((javafx.collections.ListChangeListener<Device>) c -> {
            javafx.application.Platform.runLater(this::refreshDevicesList);
        });

        setFitToWidth(true);
        setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        VBox contentBox = new VBox(24);
        contentBox.getStyleClass().add("view-container");

        // Tiêu đề & thanh công cụ
        contentBox.getChildren().add(createTopBar());

        // Danh sách thẻ thiết bị
        devicesContainer = new VBox(14);
        refreshDevicesList();
        contentBox.getChildren().add(devicesContainer);

        // Khung thông tin kỹ thuật mạng (Hữu ích cho môn Lập Trình Mạng)
        contentBox.getChildren().add(createNetworkTechCard());

        setContent(contentBox);
    }

    private HBox createTopBar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label title = new Label("Quản Lý Thiết Bị Trẻ Em");
        title.getStyleClass().add("header-welcome");
        Label sub = new Label("Giám sát và kiểm soát quyền truy cập Internet theo từng thiết bị trong gia đình.");
        sub.getStyleClass().add("header-subtitle");
        titleBox.getChildren().addAll(title, sub);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnScan = new Button("🔍 Quét mạng LAN");
        btnScan.getStyleClass().add("btn-outline");
        btnScan.setOnAction(e -> {
            dataService.recalculateAllDevicesUsageTime();
            refreshDevicesList();
            toastNotifier.accept("Đang quét dải mạng 192.168.1.0/24 & cập nhật thời gian sử dụng các thiết bị.");
        });

        Button btnAddManual = new Button("+ Thêm thủ công");
        btnAddManual.getStyleClass().add("btn-outline");
        btnAddManual.setOnAction(e -> showAddDeviceDialog());

        Button btnAddWireGuard = new Button("📱 + Cấp WireGuard tự động");
        btnAddWireGuard.getStyleClass().add("btn-primary");
        btnAddWireGuard.setOnAction(e -> showAutoWireGuardDialog());

        HBox btnGroup = new HBox(10, btnScan, btnAddManual, btnAddWireGuard);
        btnGroup.setAlignment(Pos.CENTER_RIGHT);

        bar.getChildren().addAll(titleBox, sp, btnGroup);
        return bar;
    }

    private void refreshDevicesList() {
        devicesContainer.getChildren().clear();
        for (Device device : dataService.getDevices()) {
            devicesContainer.getChildren().add(createDeviceCard(device));
        }
    }

    private HBox createDeviceCard(Device device) {
        HBox card = new HBox(18);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER_LEFT);

        // Icon thiết bị
        Label lblIcon = new Label(getDeviceIcon(device.getType()));
        lblIcon.setStyle("-fx-font-size: 32px; -fx-padding: 6 12; -fx-background-color: #F1F5F9; -fx-background-radius: 10px;");

        // Chi tiết thiết bị
        VBox infoBox = new VBox(4);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        HBox nameRow = new HBox(10);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        Label lblName = new Label(device.getName());
        lblName.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        Label lblStatus = new Label(device.getStatus());
        lblStatus.getStyleClass().add("badge");
        if ("Trực tuyến".equalsIgnoreCase(device.getStatus())) {
            lblStatus.getStyleClass().add("badge-success");
        } else {
            lblStatus.getStyleClass().add("badge-neutral");
        }
        nameRow.getChildren().addAll(lblName, lblStatus);

        HBox netMeta = new HBox(16);
        Label lblIp = new Label("IP: " + device.getIpAddress());
        lblIp.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        Label lblMac = new Label("MAC: " + device.getMacAddress());
        lblMac.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        
        // Thời gian sử dụng liên kết tự động với Property để cập nhật giao diện thời gian thực
        Label lblTime = new Label();
        lblTime.textProperty().bind(javafx.beans.binding.Bindings.concat("Hôm nay: ", device.timeSpentTodayProperty()));
        lblTime.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #2563EB;");
        netMeta.getChildren().addAll(lblIp, lblMac, lblTime);

        // Hàng thống kê nhanh số trang web đã truy cập và trạng thái danh sách đen
        DataService.DeviceStats stats = dataService.getDeviceStats(device);
        HBox summaryRow = new HBox(8);
        summaryRow.setAlignment(Pos.CENTER_LEFT);
        summaryRow.setStyle("-fx-padding: 3 0 0 0;");

        Label lblVisits = new Label("🌐 " + stats.getTotalVisits() + " web đã xem");
        lblVisits.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        Label lblSafeBadge = new Label("🟢 " + stats.getSafeVisits() + " an toàn");
        lblSafeBadge.setStyle("-fx-font-size: 10px; -fx-background-color: #ECFDF5; -fx-text-fill: #059669; -fx-padding: 2 6; -fx-background-radius: 4px; -fx-border-color: #A7F3D0; -fx-border-radius: 4px; -fx-font-weight: 600;");

        Label lblBlacklistBadge = new Label("🛑 " + stats.getBlockedVisits() + " danh sách đen");
        lblBlacklistBadge.setStyle("-fx-font-size: 10px; -fx-background-color: #FEE2E2; -fx-text-fill: #DC2626; -fx-padding: 2 6; -fx-background-radius: 4px; -fx-border-color: #FECACA; -fx-border-radius: 4px; -fx-font-weight: 600;");

        summaryRow.getChildren().addAll(lblVisits, lblSafeBadge, lblBlacklistBadge);

        infoBox.getChildren().addAll(nameRow, netMeta, summaryRow);

        // Nút Theo dõi chi tiết các trang web truy cập
        Button btnTrack = new Button("👁️ Theo dõi");
        btnTrack.getStyleClass().add("btn-outline");
        btnTrack.setStyle("-fx-font-size: 12px; -fx-padding: 8 14; -fx-text-fill: #2563EB; -fx-border-color: #93C5FD; -fx-cursor: hand; -fx-font-weight: 600;");
        btnTrack.setTooltip(new Tooltip("Theo dõi lịch sử truy cập web và trạng thái an toàn của " + device.getName()));
        btnTrack.setOnAction(e -> {
            DeviceTrackingDialog dialog = new DeviceTrackingDialog(device, dataService, toastNotifier);
            dialog.showAndWait();
            refreshDevicesList();
        });

        // Nút thao tác Khóa / Mở mạng cho thiết bị
        Button btnToggleBlock = new Button();
        updateBlockButton(btnToggleBlock, device.isBlocked());

        btnToggleBlock.setOnAction(e -> {
            dataService.toggleDeviceBlock(device);
            updateBlockButton(btnToggleBlock, device.isBlocked());
            toastNotifier.accept((device.isBlocked() ? "Đã ngắt mạng internet của " : "Đã mở lại mạng cho ") + device.getName());
        });

        // Nút xóa thiết bị
        Button btnDelete = new Button("🗑️");
        btnDelete.getStyleClass().add("btn-outline");
        btnDelete.setStyle("-fx-font-size: 13px; -fx-padding: 8 12; -fx-text-fill: #EF4444; -fx-border-color: #EF4444; -fx-cursor: hand;");
        btnDelete.setTooltip(new Tooltip("Xóa thiết bị khỏi hệ thống"));
        btnDelete.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Bạn có chắc muốn xóa thiết bị \"" + device.getName() + "\" không?", ButtonType.YES, ButtonType.NO);
            alert.setTitle("Xác nhận xóa thiết bị");
            alert.setHeaderText(null);
            alert.showAndWait().ifPresent(type -> {
                if (type == ButtonType.YES) {
                    dataService.removeDevice(device);
                    refreshDevicesList();
                    toastNotifier.accept("Đã xóa thiết bị: " + device.getName());
                }
            });
        });

        HBox actionsBox = new HBox(8, btnTrack, btnToggleBlock, btnDelete);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(lblIcon, infoBox, actionsBox);
        return card;
    }

    private void updateBlockButton(Button btn, boolean isBlocked) {
        btn.getStyleClass().removeAll("btn-danger", "btn-sm-success", "btn-sm-danger", "btn-warning");
        if (isBlocked) {
            btn.setText("🔓 Mở mạng Internet");
            btn.getStyleClass().add("btn-sm-success");
            btn.setStyle("-fx-font-size: 12px; -fx-padding: 8 16;");
        } else {
            btn.setText("🚫 Ngắt kết nối Internet");
            btn.getStyleClass().add("btn-sm-danger");
            btn.setStyle("-fx-font-size: 12px; -fx-padding: 8 16;");
        }
    }

    private String getDeviceIcon(String type) {
        if (type == null) return "💻";
        switch (type.toLowerCase()) {
            case "điện thoại":
                return "📱";
            case "máy tính bảng":
                return "📟";
            case "máy tính để bàn":
                return "🖥️";
            case "laptop":
                return "💻";
            default:
                return "🌐";
        }
    }

    private void showAddDeviceDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Thêm thiết bị con cái vào giám sát");
        dialog.setHeaderText("Nhập thông tin thiết bị cần quản lý kết nối Internet");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField tfName = new TextField();
        tfName.setPromptText("VD: Laptop Bé An");
        ComboBox<String> cbType = new ComboBox<>();
        cbType.getItems().addAll("Máy tính để bàn", "Laptop", "Máy tính bảng", "Điện thoại");
        cbType.setValue("Laptop");
        TextField tfIp = new TextField("192.168.1.");
        TextField tfMac = new TextField("AA:BB:CC:DD:EE:FF");

        grid.add(new Label("Tên thiết bị:"), 0, 0);
        grid.add(tfName, 1, 0);
        grid.add(new Label("Loại thiết bị:"), 0, 1);
        grid.add(cbType, 1, 1);
        grid.add(new Label("Địa chỉ IP:"), 0, 2);
        grid.add(tfIp, 1, 2);
        grid.add(new Label("Địa chỉ MAC:"), 0, 3);
        grid.add(tfMac, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK && !tfName.getText().trim().isEmpty()) {
                Device newDev = new Device(
                        "DEV-" + (dataService.getDevices().size() + 1),
                        tfName.getText().trim(),
                        cbType.getValue(),
                        tfIp.getText().trim(),
                        tfMac.getText().trim(),
                        "Trực tuyến",
                        false,
                        "0h 05m"
                );
                dataService.addDevice(newDev);
                refreshDevicesList();
                toastNotifier.accept("Đã thêm thiết bị mới: " + newDev.getName());
            }
        });
    }

    private VBox createNetworkTechCard() {
        VBox card = new VBox(8);
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 10px; -fx-border-color: #E2E8F0; -fx-border-radius: 10px;");

        Label lblTech = new Label("📡 Kiến trúc kiểm soát mạng (Network Architecture Reference)");
        lblTech.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #475569;");

        Label lblDesc = new Label("Hệ thống hoạt động ở tầng Network/Transport bằng cách ánh xạ bảng ARP Table & DNS Sinkhole. Khi phụ huynh ngắt mạng, thiết bị sẽ bị điều hướng các gói tin TCP/UDP đến máy chủ nội bộ thông báo đã quá hạn mức giờ sử dụng.");
        lblDesc.setWrapText(true);
        lblDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        card.getChildren().addAll(lblTech, lblDesc);
        return card;
    }

    private void showAutoWireGuardDialog() {
        VpsClientService vps = dataService.getVpsClient() != null ? dataService.getVpsClient() : VpsClientService.getInstance();
        if (vps == null || vps.getConnectionState() != VpsClientService.ConnectionState.AUTHENTICATED) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Ứng dụng chưa kết nối tới máy chủ VPS (103.74.101.176:9000).\n" +
                    "Vui lòng đảm bảo dịch vụ FamilyGuard trên VPS đang hoạt động trước khi yêu cầu cấp cấu hình WireGuard.",
                    ButtonType.OK);
            alert.setTitle("Chưa kết nối VPS");
            alert.setHeaderText("Cần kết nối VPS để tự động sinh khóa & cấp IP");
            alert.showAndWait();
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("📱 Cấp kết nối WireGuard cho điện thoại");
        dialog.setHeaderText("Tự động sinh cặp khóa mật mã bảo mật & Cấp địa chỉ IP ảo từ VPS Cloud");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(20, 20, 10, 20));

        TextField tfName = new TextField();
        tfName.setPromptText("VD: iPhone Bé Minh hoặc Samsung A54");
        tfName.setPrefWidth(260);

        ComboBox<String> cbType = new ComboBox<>();
        cbType.getItems().addAll("Điện thoại", "Máy tính bảng", "Laptop");
        cbType.setValue("Điện thoại");

        Label lblNote = new Label("💡 Thiết bị sẽ được giám sát 24/7 kể cả khi ra khỏi nhà (dùng 4G/5G hoặc WiFi trường học).\nToàn bộ truy vấn DNS sẽ được lọc tự động tại máy chủ VPS 103.74.101.176.");
        lblNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B; -fx-padding: 8; -fx-background-color: #F1F5F9; -fx-background-radius: 6;");
        lblNote.setWrapText(true);

        grid.add(new Label("Tên thiết bị của con:"), 0, 0);
        grid.add(tfName, 1, 0);
        grid.add(new Label("Loại thiết bị:"), 0, 1);
        grid.add(cbType, 1, 1);
        grid.add(lblNote, 0, 2, 2, 1);

        dialog.getDialogPane().setContent(grid);
        ButtonType btnCreateType = new ButtonType("⚡ Tạo & Sinh mã QR", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnCreateType, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(res -> {
            if (res == btnCreateType && !tfName.getText().trim().isEmpty()) {
                String devName = tfName.getText().trim();
                String devType = cbType.getValue();

                // Hiển thị dialog chờ phản hồi từ VPS
                showWaitingDialog(devName);

                // Gửi lệnh lên VPS
                vps.sendCreateWireGuardPeer(devName, devType);
            }
        });
    }

    private void showWaitingDialog(String deviceName) {
        waitingDialog = new Dialog<>();
        waitingDialog.setTitle("Đang liên hệ VPS...");
        waitingDialog.setHeaderText("Đang tạo cấu hình WireGuard cho: " + deviceName);

        VBox box = new VBox(14);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        ProgressIndicator pi = new ProgressIndicator();
        Label lblMsg = new Label("VPS đang sinh cặp khóa Curve25519, cấp IP 10.0.0.X và tạo mã QR...");
        lblMsg.setStyle("-fx-text-fill: #475569; -fx-font-size: 12px;");
        box.getChildren().addAll(pi, lblMsg);

        waitingDialog.getDialogPane().setContent(box);
        waitingDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        waitingDialog.show();
    }

    private void handleWireGuardPeerResult(VpsClientService.WireGuardPeerResult result) {
        if (waitingDialog != null) {
            waitingDialog.close();
            waitingDialog = null;
        }

        if (result == null) return;

        if (result.success) {
            boolean exists = dataService.getDevices().stream().anyMatch(d -> result.assignedIp != null && result.assignedIp.equalsIgnoreCase(d.getIpAddress()));
            if (!exists) {
                Device newDev = new Device(
                        "DEV-" + (dataService.getDevices().size() + 1),
                        result.deviceName != null ? result.deviceName : "Điện thoại con",
                        result.deviceType != null ? result.deviceType : "Điện thoại",
                        result.assignedIp,
                        "WG-VIRTUAL",
                        "Trực tuyến",
                        false,
                        "0h 00m"
                );
                dataService.addDevice(newDev);
                refreshDevicesList();
            }
            toastNotifier.accept("Đã cấp cấu hình WireGuard thành công cho " + result.deviceName);
            showWireGuardSuccessDialog(result);
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Lỗi từ VPS: " + (result.error != null ? result.error : "Không thể tạo cấu hình WireGuard"), ButtonType.OK);
            alert.setTitle("Tạo cấu hình thất bại");
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }

    private void showWireGuardSuccessDialog(VpsClientService.WireGuardPeerResult result) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Cấu hình WireGuard - " + result.deviceName);
        dialog.setHeaderText("🎉 ĐÃ CẤP CẤU HÌNH WIREGUARD THÀNH CÔNG");

        VBox mainBox = new VBox(16);
        mainBox.setPadding(new Insets(10));
        mainBox.setPrefWidth(580);

        HBox topInfo = new HBox(16);
        topInfo.setAlignment(Pos.CENTER_LEFT);
        topInfo.setPadding(new Insets(10, 14, 10, 14));
        topInfo.setStyle("-fx-background-color: #ECFDF5; -fx-border-color: #10B981; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        Label lblBadge = new Label("🔒 IP: " + result.assignedIp);
        lblBadge.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #065F46;");
        Label lblDns = new Label("🛡️ DNS Sinkhole: 10.0.0.1 (VPS Cloud)");
        lblDns.setStyle("-fx-font-size: 12px; -fx-text-fill: #047857;");
        topInfo.getChildren().addAll(lblBadge, new Separator(Orientation.VERTICAL), lblDns);

        HBox contentRow = new HBox(20);
        contentRow.setAlignment(Pos.CENTER_LEFT);

        // Cột trái: Hiển thị mã QR
        VBox qrBox = new VBox(8);
        qrBox.setAlignment(Pos.CENTER);
        qrBox.setPadding(new Insets(10));
        qrBox.setStyle("-fx-background-color: white; -fx-border-color: #E2E8F0; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2);");

        if (result.qrBase64 != null && !result.qrBase64.trim().isEmpty()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(result.qrBase64.trim());
                Image qrImg = new Image(new ByteArrayInputStream(decoded));
                ImageView iv = new ImageView(qrImg);
                iv.setFitWidth(230);
                iv.setFitHeight(230);
                iv.setPreserveRatio(true);
                qrBox.getChildren().add(iv);
            } catch (Exception e) {
                Label lblNoQr = new Label("Không thể tải ảnh QR: " + e.getMessage());
                qrBox.getChildren().add(lblNoQr);
            }
        } else {
            Label lblNoQr = new Label("Chưa có mã QR. Vui lòng sao chép file cấu hình bên dưới.");
            lblNoQr.setWrapText(true);
            qrBox.getChildren().add(lblNoQr);
        }

        Label lblQrGuide = new Label("📸 Quét bằng app WireGuard");
        lblQrGuide.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #2563EB;");
        qrBox.getChildren().add(lblQrGuide);

        // Cột phải: Hướng dẫn & Tùy chọn sao chép
        VBox guideBox = new VBox(10);
        guideBox.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(guideBox, Priority.ALWAYS);

        Label lblStepsTitle = new Label("📌 Các bước kích hoạt trên điện thoại con:");
        lblStepsTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1E293B;");

        Label step1 = new Label("1. Cài app WireGuard từ App Store hoặc CH Play.");
        step1.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");
        Label step2 = new Label("2. Mở app -> Bấm (+) -> Chọn 'Quét mã QR'.");
        step2.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");
        Label step3 = new Label("3. Hướng camera quét hình bên cạnh và kích hoạt!");
        step3.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        Label lblConfTitle = new Label("📄 File cấu hình WireGuard (.conf):");
        lblConfTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #475569;");

        TextArea taConf = new TextArea(result.configText != null ? result.configText : "");
        taConf.setEditable(false);
        taConf.setPrefRowCount(5);
        taConf.setStyle("-fx-font-family: monospace; -fx-font-size: 10px;");

        Button btnCopy = new Button("📋 Sao chép cấu hình");
        btnCopy.getStyleClass().add("btn-outline");
        btnCopy.setOnAction(e -> {
            ClipboardContent clip = new ClipboardContent();
            clip.putString(result.configText);
            Clipboard.getSystemClipboard().setContent(clip);
            toastNotifier.accept("Đã sao chép cấu hình WireGuard vào bộ nhớ tạm!");
        });

        Button btnSave = new Button("💾 Lưu file .conf");
        btnSave.getStyleClass().add("btn-outline");
        btnSave.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Lưu cấu hình WireGuard");
            String safeFile = result.deviceName != null ? result.deviceName.replaceAll("\\s+", "_") : "child";
            fc.setInitialFileName(safeFile + "_wireguard.conf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("WireGuard Config (*.conf)", "*.conf"));
            File file = fc.showSaveDialog(getScene().getWindow());
            if (file != null) {
                try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                    writer.write(result.configText);
                    toastNotifier.accept("Đã lưu file: " + file.getName());
                } catch (Exception ex) {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Lỗi lưu file: " + ex.getMessage(), ButtonType.OK);
                    err.showAndWait();
                }
            }
        });

        HBox btnRow = new HBox(8, btnCopy, btnSave);

        guideBox.getChildren().addAll(lblStepsTitle, step1, step2, step3, new Separator(), lblConfTitle, taConf, btnRow);
        contentRow.getChildren().addAll(qrBox, guideBox);

        mainBox.getChildren().addAll(topInfo, contentRow);

        dialog.getDialogPane().setContent(mainBox);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.showAndWait();
    }
}
