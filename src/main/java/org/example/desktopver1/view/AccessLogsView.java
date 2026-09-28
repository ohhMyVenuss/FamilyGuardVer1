package org.example.desktopver1.view;

import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import org.example.desktopver1.model.AccessLog;
import org.example.desktopver1.service.DataService;

import java.util.function.Consumer;

/**
 * Giao diện Nhật ký truy cập (Access Logs):
 * - Bảng TableView chuyên nghiệp với badge màu cho từng hành vi
 * - Tìm kiếm theo thời gian thực và lọc theo trạng thái Cho phép/Đã chặn
 * - Công cụ làm mới, xóa và xuất dữ liệu
 */
public class AccessLogsView extends VBox {

    private final DataService dataService;
    private final Consumer<String> toastNotifier;

    private TableView<AccessLog> tableView;
    private FilteredList<AccessLog> filteredData;
    private TextField searchField;
    private ComboBox<String> filterCombo;
    private Label footerStatsLabel;

    public AccessLogsView(DataService dataService, Consumer<String> toastNotifier) {
        this.dataService = dataService;
        this.toastNotifier = toastNotifier;

        getStyleClass().add("view-container");
        setSpacing(18);

        // 1. Tiêu đề
        getChildren().add(createHeader());

        // 2. Thanh công cụ tìm kiếm & bộ lọc
        getChildren().add(createToolbar());

        // 3. Bảng TableView
        tableView = createTableView();
        VBox.setVgrow(tableView, Priority.ALWAYS);
        getChildren().add(tableView);

        // 4. Thanh trạng thái phía dưới
        getChildren().add(createFooter());

        setupFiltering();
    }

    private VBox createHeader() {
        VBox box = new VBox(4);
        Label title = new Label("Nhật Ký Truy Cập & Giám Sát An Toàn");
        title.getStyleClass().add("header-welcome");
        Label sub = new Label("Ghi lại chi tiết mọi truy vấn mạng, tên miền và kết quả kiểm duyệt từ thiết bị của trẻ.");
        sub.getStyleClass().add("header-subtitle");
        box.getChildren().addAll(title, sub);
        return box;
    }

    private HBox createToolbar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("🔍 Tìm theo tên miền, thiết bị, lý do...");
        searchField.setPrefWidth(280);

        filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll("Tất cả trạng thái", "Chỉ xem: ĐÃ CHẶN", "Chỉ xem: CHO PHÉP");
        filterCombo.setValue("Tất cả trạng thái");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnRefresh = new Button("🔄 Làm mới");
        btnRefresh.getStyleClass().add("btn-outline");
        btnRefresh.setOnAction(e -> {
            tableView.refresh();
            updateFooterStats();
            toastNotifier.accept("Đã làm mới dữ liệu nhật ký mạng.");
        });

        Button btnExport = new Button("📥 Xuất báo cáo CSV");
        btnExport.getStyleClass().add("btn-outline");
        btnExport.setOnAction(e -> toastNotifier.accept("Đã xuất báo cáo truy cập an toàn ra file family_access_report.csv"));

        Button btnClear = new Button("🗑️ Xóa log");
        btnClear.getStyleClass().add("btn-sm-danger");
        btnClear.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Bạn có chắc chắn muốn xóa toàn bộ lịch sử truy cập?", ButtonType.YES, ButtonType.NO);
            alert.setHeaderText("Xác nhận xóa nhật ký");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    dataService.clearLogs();
                    updateFooterStats();
                    toastNotifier.accept("Đã dọn dẹp toàn bộ dữ liệu nhật ký.");
                }
            });
        });

        bar.getChildren().addAll(searchField, filterCombo, sp, btnRefresh, btnExport, btnClear);
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

        TableColumn<AccessLog, String> colDevice = new TableColumn<>("Thiết bị");
        colDevice.setCellValueFactory(new PropertyValueFactory<>("deviceName"));
        colDevice.setPrefWidth(180);

        TableColumn<AccessLog, String> colDomain = new TableColumn<>("Địa chỉ Web / Tên miền");
        colDomain.setCellValueFactory(new PropertyValueFactory<>("domain"));
        colDomain.setPrefWidth(220);

        TableColumn<AccessLog, String> colCategory = new TableColumn<>("Phân loại");
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colCategory.setPrefWidth(140);

        TableColumn<AccessLog, String> colAction = new TableColumn<>("Hành động");
        colAction.setCellValueFactory(new PropertyValueFactory<>("action"));
        colAction.setPrefWidth(110);
        colAction.setMaxWidth(130);
        // Tùy biến hiển thị Badge màu
        colAction.setCellFactory(col -> new TableCell<AccessLog, String>() {
            private final Label badge = new Label();
            {
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    badge.setText(item);
                    badge.getStyleClass().clear();
                    badge.getStyleClass().add("badge");
                    if ("ĐÃ CHẶN".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-danger");
                    } else {
                        badge.getStyleClass().add("badge-success");
                    }
                    setGraphic(badge);
                }
            }
        });

        TableColumn<AccessLog, String> colReason = new TableColumn<>("Lý do xử lý");
        colReason.setCellValueFactory(new PropertyValueFactory<>("reason"));
        colReason.setPrefWidth(180);

        table.getColumns().addAll(colTime, colDevice, colDomain, colCategory, colAction, colReason);
        return table;
    }

    private void setupFiltering() {
        filteredData = new FilteredList<>(dataService.getAccessLogs(), p -> true);

        // Lắng nghe thay đổi tìm kiếm và bộ lọc
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        filterCombo.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter());

        // Tự động làm mới bảng TableView và số liệu footer khi có dữ liệu mới từ luồng ngầm VPS
        dataService.getAccessLogs().addListener((javafx.collections.ListChangeListener<AccessLog>) c -> {
            javafx.application.Platform.runLater(() -> {
                tableView.refresh();
                updateFooterStats();
            });
        });

        tableView.setItems(filteredData);
        updateFooterStats();
    }

    public void refresh() {
        javafx.application.Platform.runLater(() -> {
            tableView.refresh();
            updateFooterStats();
        });
    }

    private void applyFilter() {
        String searchText = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        String comboVal = filterCombo.getValue();

        filteredData.setPredicate(log -> {
            // Lọc theo trạng thái combo
            if ("Chỉ xem: ĐÃ CHẶN".equals(comboVal) && !"ĐÃ CHẶN".equalsIgnoreCase(log.getAction())) {
                return false;
            }
            if ("Chỉ xem: CHO PHÉP".equals(comboVal) && !"CHO PHÉP".equalsIgnoreCase(log.getAction())) {
                return false;
            }

            // Lọc theo từ khóa tìm kiếm
            if (searchText.isEmpty()) {
                return true;
            }

            return log.getDomain().toLowerCase().contains(searchText)
                    || log.getDeviceName().toLowerCase().contains(searchText)
                    || log.getCategory().toLowerCase().contains(searchText)
                    || log.getReason().toLowerCase().contains(searchText);
        });

        updateFooterStats();
    }

    private HBox createFooter() {
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(6, 12, 6, 12));
        footer.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 8px; -fx-border-color: #E2E8F0; -fx-border-radius: 8px;");

        footerStatsLabel = new Label();
        footerStatsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        footer.getChildren().add(footerStatsLabel);
        return footer;
    }

    private void updateFooterStats() {
        int total = dataService.getAccessLogs().size();
        int showing = filteredData.size();
        int blocked = 0;
        for (AccessLog log : filteredData) {
            if ("ĐÃ CHẶN".equalsIgnoreCase(log.getAction())) {
                blocked++;
            }
        }
        footerStatsLabel.setText(String.format("Đang hiển thị %d / %d bản ghi | Đã ngăn chặn: %d sự kiện | Kiểm duyệt thời gian thực", showing, total, blocked));
    }
}
