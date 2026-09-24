package org.example.desktopver1.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.desktopver1.model.Device;
import org.example.desktopver1.service.DataService;

import java.util.function.Consumer;

/**
 * Giao diện Quản lý thiết bị (Device Management):
 * - Theo dõi các thiết bị của con đang kết nối trong gia đình
 * - Kiểm tra IP, MAC, trạng thái Online/Offline
 * - Cắt mạng internet hoặc khôi phục kết nối cho từng máy
 * - Tích hợp mô phỏng quét mạng nội bộ (LAN Scan)
 */
public class DeviceManagerView extends ScrollPane {

    private final DataService dataService;
    private final Consumer<String> toastNotifier;
    private VBox devicesContainer;

    public DeviceManagerView(DataService dataService, Consumer<String> toastNotifier) {
        this.dataService = dataService;
        this.toastNotifier = toastNotifier;

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
            toastNotifier.accept("Đang quét dải mạng 192.168.1.0/24... Đã đồng bộ 4 thiết bị!");
            refreshDevicesList();
        });

        Button btnAdd = new Button("+ Thêm thiết bị mới");
        btnAdd.getStyleClass().add("btn-primary");
        btnAdd.setOnAction(e -> showAddDeviceDialog());

        HBox btnGroup = new HBox(10, btnScan, btnAdd);
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
        Label lblTime = new Label("Hôm nay: " + device.getTimeSpentToday());
        lblTime.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #2563EB;");
        netMeta.getChildren().addAll(lblIp, lblMac, lblTime);

        infoBox.getChildren().addAll(nameRow, netMeta);

        // Nút thao tác Khóa / Mở mạng cho thiết bị
        Button btnToggleBlock = new Button();
        updateBlockButton(btnToggleBlock, device.isBlocked());

        btnToggleBlock.setOnAction(e -> {
            dataService.toggleDeviceBlock(device);
            updateBlockButton(btnToggleBlock, device.isBlocked());
            toastNotifier.accept((device.isBlocked() ? "Đã ngắt mạng internet của " : "Đã mở lại mạng cho ") + device.getName());
        });

        card.getChildren().addAll(lblIcon, infoBox, btnToggleBlock);
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
}
