package org.example.desktopver1.view;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.example.desktopver1.model.AccessLog;
import org.example.desktopver1.model.Device;
import org.example.desktopver1.service.DataService;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Hộp thoại Giám sát & Theo dõi chi tiết cho từng thiết bị:
 * - Hiển thị danh sách toàn bộ các trang web/tên miền thiết bị đã truy cập
 * - Đánh giá rõ ràng: Nằm trong danh sách đen hay An toàn (cho phép)
 * - Hiển thị và cập nhật thời gian sử dụng hôm nay của thiết bị
 * - Cho phép phụ huynh thao tác nhanh: Chặn ngay tên miền hoặc Bỏ chặn
 * - Hỗ trợ lọc theo trạng thái, tìm kiếm từ khóa và xuất báo cáo CSV
 */
public class DeviceTrackingDialog extends Dialog<ButtonType> {

    private final Device device;
    private final DataService dataService;
    private final Consumer<String> toastNotifier;

    private final ObservableList<AccessLog> deviceLogs = FXCollections.observableArrayList();
    private FilteredList<AccessLog> filteredLogs;

    private TableView<AccessLog> tableView;
    private TextField searchField;
    private ComboBox<String> filterCombo;

    private Label lblTimeStat;
    private Label lblTotalVisitsStat;
    private Label lblSafeVisitsStat;
    private Label lblBlockedVisitsStat;
    private Label lblFooterStatus;

    public DeviceTrackingDialog(Device device, DataService dataService, Consumer<String> toastNotifier) {
        this.device = device;
        this.dataService = dataService;
        this.toastNotifier = toastNotifier;

        setTitle("Giám sát hoạt động mạng - " + device.getName());
        setHeaderText(null);

        // Kích thước chuẩn chuyên nghiệp
        getDialogPane().setPrefWidth(940);
        getDialogPane().setPrefHeight(650);

        VBox rootBox = new VBox(16);
        rootBox.setPadding(new Insets(16, 20, 16, 20));

        // 1. Tiêu đề hồ sơ thiết bị
        rootBox.getChildren().add(createDeviceProfileHeader());

        // 2. Thẻ số liệu thống kê nhanh (KPI Mini Cards)
        rootBox.getChildren().add(createStatsRow());

        // 3. Thanh tìm kiếm và bộ lọc trạng thái
        rootBox.getChildren().add(createToolbar());

        // 4. Bảng nhật ký truy cập web của thiết bị
        tableView = createTableView();
        VBox.setVgrow(tableView, Priority.ALWAYS);
        rootBox.getChildren().add(tableView);

        // 5. Thanh trạng thái chân trang
        rootBox.getChildren().add(createFooter());

        getDialogPane().setContent(rootBox);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        setupData();

        // Lắng nghe thay đổi dữ liệu thời gian thực
        dataService.getAccessLogs().addListener((javafx.collections.ListChangeListener<AccessLog>) c -> {
            javafx.application.Platform.runLater(this::refreshData);
        });
        dataService.getBlacklistDomains().addListener((javafx.collections.ListChangeListener<String>) c -> {
            javafx.application.Platform.runLater(this::refreshData);
        });
    }

    private HBox createDeviceProfileHeader() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 16, 12, 16));
        header.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-radius: 10px; -fx-background-radius: 10px;");

        Label lblIcon = new Label(getDeviceIcon(device.getType()));
        lblIcon.setStyle("-fx-font-size: 30px; -fx-padding: 4 10; -fx-background-color: #FFFFFF; -fx-background-radius: 8px; -fx-border-color: #E2E8F0; -fx-border-radius: 8px;");

        VBox titleBox = new VBox(4);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label lblName = new Label(device.getName());
        lblName.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        Label lblStatus = new Label(device.getStatus());
        lblStatus.getStyleClass().add("badge");
        if ("Trực tuyến".equalsIgnoreCase(device.getStatus())) {
            lblStatus.getStyleClass().add("badge-success");
        } else {
            lblStatus.getStyleClass().add("badge-neutral");
        }
        titleRow.getChildren().addAll(lblName, lblStatus);

        HBox metaRow = new HBox(16);
        Label lblIp = new Label("🌐 IP: " + device.getIpAddress());
        lblIp.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        Label lblMac = new Label("🏷️ MAC: " + device.getMacAddress());
        lblMac.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        Label lblType = new Label("💻 Loại: " + device.getType());
        lblType.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        metaRow.getChildren().addAll(lblIp, lblMac, lblType);

        titleBox.getChildren().addAll(titleRow, metaRow);

        // Nút xuất báo cáo và làm mới
        Button btnRefresh = new Button("🔄 Làm mới");
        btnRefresh.getStyleClass().add("btn-outline");
        btnRefresh.setOnAction(e -> {
            dataService.recalculateDeviceUsageTime(device);
            refreshData();
            toastNotifier.accept("Đã làm mới dữ liệu theo dõi của " + device.getName());
        });

        Button btnExport = new Button("📥 Xuất CSV");
        btnExport.getStyleClass().add("btn-outline");
        btnExport.setOnAction(e -> exportToCsv());

        HBox btnBox = new HBox(8, btnRefresh, btnExport);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        header.getChildren().addAll(lblIcon, titleBox, btnBox);
        return header;
    }

    private HBox createStatsRow() {
        HBox row = new HBox(12);

        lblTimeStat = new Label(device.getTimeSpentToday());
        lblTotalVisitsStat = new Label("0");
        lblSafeVisitsStat = new Label("0");
        lblBlockedVisitsStat = new Label("0");

        VBox cardTime = createMiniCard("⏱️", "Thời gian hôm nay", lblTimeStat, "#2563EB");
        VBox cardTotal = createMiniCard("🌐", "Tổng trang đã truy cập", lblTotalVisitsStat, "#0F172A");
        VBox cardSafe = createMiniCard("🟢", "Trang web An toàn", lblSafeVisitsStat, "#059669");
        VBox cardBlocked = createMiniCard("🛑", "Danh sách đen / Chặn", lblBlockedVisitsStat, "#DC2626");

        HBox.setHgrow(cardTime, Priority.ALWAYS);
        HBox.setHgrow(cardTotal, Priority.ALWAYS);
        HBox.setHgrow(cardSafe, Priority.ALWAYS);
        HBox.setHgrow(cardBlocked, Priority.ALWAYS);

        row.getChildren().addAll(cardTime, cardTotal, cardSafe, cardBlocked);
        return row;
    }

    private VBox createMiniCard(String icon, String title, Label lblVal, String colorHex) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(10, 14, 10, 14));
        card.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2E8F0; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-effect: dropshadow(three-pass-box, rgba(15, 23, 42, 0.03), 6, 0, 0, 2);");

        HBox top = new HBox(6);
        top.setAlignment(Pos.CENTER_LEFT);
        Label ic = new Label(icon);
        ic.setStyle("-fx-font-size: 14px;");
        Label tit = new Label(title);
        tit.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B; -fx-font-weight: 600;");
        top.getChildren().addAll(ic, tit);

        lblVal.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + colorHex + ";");

        card.getChildren().addAll(top, lblVal);
        return card;
    }

    private HBox createToolbar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("🔍 Tìm theo tên miền, URL, lý do...");
        searchField.setPrefWidth(320);

        filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll("Tất cả trang web", "🛑 Chỉ xem: Danh sách đen (Đã chặn)", "🟢 Chỉ xem: An toàn (Cho phép)");
        filterCombo.setValue("Tất cả trang web");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label lblTip = new Label("💡 Bấm \"Chặn web\" để đưa ngay tên miền vào danh sách đen.");
        lblTip.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B; -fx-font-style: italic;");

        bar.getChildren().addAll(searchField, filterCombo, sp, lblTip);
        return bar;
    }

    @SuppressWarnings("unchecked")
    private TableView<AccessLog> createTableView() {
        TableView<AccessLog> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<AccessLog, String> colTime = new TableColumn<>("Thời gian");
        colTime.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        colTime.setPrefWidth(140);
        colTime.setMaxWidth(160);

        TableColumn<AccessLog, String> colDomain = new TableColumn<>("Địa chỉ Web / Tên miền đã truy cập");
        colDomain.setCellValueFactory(new PropertyValueFactory<>("domain"));
        colDomain.setPrefWidth(260);
        colDomain.setCellFactory(col -> new TableCell<AccessLog, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setStyle("-fx-font-family: monospace; -fx-font-weight: bold; -fx-text-fill: #1E293B;");
                }
            }
        });

        TableColumn<AccessLog, AccessLog> colSafety = new TableColumn<>("Đánh giá An toàn / Danh sách đen");
        colSafety.setPrefWidth(210);
        colSafety.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue()));
        colSafety.setCellFactory(col -> new TableCell<AccessLog, AccessLog>() {
            private final Label badge = new Label();
            {
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(AccessLog log, boolean empty) {
                super.updateItem(log, empty);
                if (empty || log == null) {
                    setGraphic(null);
                } else {
                    boolean isBlacklisted = dataService.isLogBlacklistedOrBlocked(log);
                    badge.getStyleClass().clear();
                    badge.getStyleClass().add("badge");
                    if (isBlacklisted) {
                        badge.setText("🛑 DANH SÁCH ĐEN (Đã chặn)");
                        badge.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626; -fx-border-color: #FECACA; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 3 8; -fx-font-weight: bold; -fx-font-size: 10.5px;");
                    } else {
                        badge.setText("🟢 AN TOÀN (Cho phép)");
                        badge.setStyle("-fx-background-color: #ECFDF5; -fx-text-fill: #059669; -fx-border-color: #A7F3D0; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 3 8; -fx-font-weight: bold; -fx-font-size: 10.5px;");
                    }
                    setGraphic(badge);
                }
            }
        });

        TableColumn<AccessLog, String> colCategory = new TableColumn<>("Phân loại");
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colCategory.setPrefWidth(120);

        TableColumn<AccessLog, AccessLog> colAction = new TableColumn<>("Thao tác");
        colAction.setPrefWidth(120);
        colAction.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue()));
        colAction.setCellFactory(col -> new TableCell<AccessLog, AccessLog>() {
            private final Button btnAction = new Button();
            {
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(AccessLog log, boolean empty) {
                super.updateItem(log, empty);
                if (empty || log == null || log.getDomain() == null) {
                    setGraphic(null);
                } else {
                    String domain = log.getDomain().trim();
                    boolean inBlacklist = dataService.isDomainInBlacklist(domain) || "ĐÃ CHẶN".equalsIgnoreCase(log.getAction());

                    if (inBlacklist) {
                        btnAction.setText("🔓 Bỏ chặn");
                        btnAction.getStyleClass().clear();
                        btnAction.getStyleClass().add("btn-outline");
                        btnAction.setStyle("-fx-font-size: 10.5px; -fx-padding: 3 8; -fx-text-fill: #059669; -fx-border-color: #A7F3D0; -fx-cursor: hand;");
                        btnAction.setOnAction(e -> {
                            dataService.removeBlacklistDomain(domain);
                            toastNotifier.accept("Đã gỡ tên miền khỏi danh sách chặn: " + domain);
                            refreshData();
                        });
                    } else {
                        btnAction.setText("🚫 Chặn web");
                        btnAction.getStyleClass().clear();
                        btnAction.getStyleClass().add("btn-sm-danger");
                        btnAction.setStyle("-fx-font-size: 10.5px; -fx-padding: 3 8; -fx-cursor: hand;");
                        btnAction.setOnAction(e -> {
                            dataService.addBlacklistDomain(domain);
                            toastNotifier.accept("Đã thêm tên miền vào danh sách đen: " + domain);
                            refreshData();
                        });
                    }
                    setGraphic(btnAction);
                }
            }
        });

        table.getColumns().addAll(colTime, colDomain, colSafety, colCategory, colAction);

        // Hiển thị thông báo khi không có dữ liệu
        Label emptyLabel = new Label("Thiết bị này chưa có lịch sử truy cập web nào được ghi nhận hôm nay.");
        emptyLabel.setStyle("-fx-text-fill: #64748B; -fx-font-size: 13px;");
        table.setPlaceholder(emptyLabel);

        return table;
    }

    private HBox createFooter() {
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 12, 8, 12));
        footer.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 8px; -fx-border-color: #E2E8F0; -fx-border-radius: 8px;");

        lblFooterStatus = new Label();
        lblFooterStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        footer.getChildren().addAll(lblFooterStatus, sp);
        return footer;
    }

    private void setupData() {
        filteredLogs = new FilteredList<>(deviceLogs, p -> true);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        filterCombo.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter());

        tableView.setItems(filteredLogs);
        refreshData();
    }

    private void refreshData() {
        dataService.recalculateDeviceUsageTime(device);

        deviceLogs.setAll(dataService.getLogsForDevice(device));

        DataService.DeviceStats stats = dataService.getDeviceStats(device);
        lblTimeStat.setText(device.getTimeSpentToday());
        lblTotalVisitsStat.setText(String.format("%,d", stats.getTotalVisits()));
        lblSafeVisitsStat.setText(String.format("%,d", stats.getSafeVisits()));
        lblBlockedVisitsStat.setText(String.format("%,d", stats.getBlockedVisits()));

        applyFilter();
    }

    private void applyFilter() {
        String search = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        String filterMode = filterCombo.getValue();

        filteredLogs.setPredicate(log -> {
            boolean isBlacklisted = dataService.isLogBlacklistedOrBlocked(log);

            if ("🛑 Chỉ xem: Danh sách đen (Đã chặn)".equals(filterMode) && !isBlacklisted) {
                return false;
            }
            if ("🟢 Chỉ xem: An toàn (Cho phép)".equals(filterMode) && isBlacklisted) {
                return false;
            }

            if (search.isEmpty()) {
                return true;
            }

            return (log.getDomain() != null && log.getDomain().toLowerCase().contains(search))
                    || (log.getCategory() != null && log.getCategory().toLowerCase().contains(search))
                    || (log.getReason() != null && log.getReason().toLowerCase().contains(search));
        });

        int total = deviceLogs.size();
        int showing = filteredLogs.size();
        int blocked = 0;
        for (AccessLog log : filteredLogs) {
            if (dataService.isLogBlacklistedOrBlocked(log)) {
                blocked++;
            }
        }

        lblFooterStatus.setText(String.format("Đang hiển thị %d / %d bản ghi | Danh sách đen: %d | Kiểm soát thời gian thực bởi VPS Cloud", showing, total, blocked));
        tableView.refresh();
    }

    private void exportToCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Xuất lịch sử truy cập thiết bị");
        String safeName = device.getName().replaceAll("\\s+", "_");
        fc.setInitialFileName(safeName + "_access_history.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        File file = fc.showSaveDialog(getDialogPane().getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                // Ghi BOM UTF-8 để Excel đọc tiếng Việt không bị lỗi font
                writer.write('\ufeff');
                writer.write("Thời gian,Tên thiết bị,Địa chỉ IP,Trang web,Đánh giá an toàn,Phân loại,Hành động,Lý do\n");
                for (AccessLog log : deviceLogs) {
                    boolean isBlacklisted = dataService.isLogBlacklistedOrBlocked(log);
                    String safeAssessment = isBlacklisted ? "Nằm trong danh sách đen (Đã chặn)" : "An toàn (Cho phép)";
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            escapeCsv(log.getTimestamp()),
                            escapeCsv(device.getName()),
                            escapeCsv(device.getIpAddress()),
                            escapeCsv(log.getDomain()),
                            escapeCsv(safeAssessment),
                            escapeCsv(log.getCategory()),
                            escapeCsv(log.getAction()),
                            escapeCsv(log.getReason())
                    ));
                }
                toastNotifier.accept("Đã xuất thành công " + deviceLogs.size() + " bản ghi ra: " + file.getName());
            } catch (Exception ex) {
                Alert err = new Alert(Alert.AlertType.ERROR, "Lỗi khi lưu file: " + ex.getMessage(), ButtonType.OK);
                err.showAndWait();
            }
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"");
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
}
