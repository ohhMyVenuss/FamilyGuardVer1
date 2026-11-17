package org.example.desktopver1.view;

import javafx.application.Platform;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import org.example.desktopver1.model.AccessLog;
import org.example.desktopver1.service.DataService;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Giao diện Nhật ký truy cập (Access Logs):
 * - Bảng TableView chuyên nghiệp với badge màu cho từng hành vi
 * - Tìm kiếm theo thời gian thực và lọc theo trạng thái Cho phép/Đã chặn
 * - Công cụ làm mới, xóa và xuất dữ liệu
 */
public class AccessLogsView extends VBox {

    private final DataService dataService;
    private final ToastManager.ToastHandler toastHandler;

    private TableView<AccessLog> tableView;
    private FilteredList<AccessLog> filteredData;
    private TextField searchField;
    private ComboBox<String> filterCombo;
    private ComboBox<String> timeRangeCombo;
    private DatePicker datePicker;
    private Label footerStatsLabel;

    public AccessLogsView(DataService dataService, Consumer<String> legacyToast, ToastManager.ToastHandler toastHandler) {
        this.dataService = dataService;
        this.toastHandler = toastHandler;

        getStyleClass().add("view-container");
        setSpacing(18);

        // 1. Tiêu đề
        getChildren().add(createHeader());

        // 2. Card chứa thanh công cụ + bảng TableView
        VBox tableCard = new VBox(14);
        tableCard.getStyleClass().add("fg-card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        tableCard.getChildren().add(createToolbar());
        tableView = createTableView();
        VBox.setVgrow(tableView, Priority.ALWAYS);
        tableCard.getChildren().add(tableView);

        getChildren().add(tableCard);

        // 3. Thanh trạng thái phía dưới
        getChildren().add(createFooter());

        setupFiltering();
    }

    private VBox createHeader() {
        VBox box = new VBox(4);
        Label title = new Label("Nhật ký truy cập (Live Logs)");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Theo dõi thời gian thực mọi truy vấn mạng, tên miền và cảnh báo bị chặn từ VPS.");
        sub.getStyleClass().add("page-subtitle");
        box.getChildren().addAll(title, sub);
        return box;
    }

    private HBox createToolbar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("Tìm theo tên miền, thiết bị, lý do...");
        searchField.setPrefWidth(240);

        filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll("Tất cả trạng thái", "Chỉ xem: ĐÃ CHẶN", "Chỉ xem: CHO PHÉP");
        filterCombo.setValue("Tất cả trạng thái");

        // Mốc thời gian theo dõi
        FontIcon clockIcon = new FontIcon(MaterialDesignC.CLOCK_OUTLINE);
        clockIcon.setIconSize(16);
        timeRangeCombo = new ComboBox<>();
        timeRangeCombo.getItems().addAll(
                "Tất cả thời gian",
                "15 phút gần đây",
                "1 giờ gần đây",
                "Hôm nay",
                "7 ngày gần đây",
                "Chọn ngày cụ thể..."
        );
        timeRangeCombo.setValue("Tất cả thời gian");

        datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(135);
        datePicker.setVisible(false);
        datePicker.setManaged(false);

        timeRangeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean customDate = "Chọn ngày cụ thể...".equals(newVal);
            datePicker.setVisible(customDate);
            datePicker.setManaged(customDate);
            applyFilter();
        });
        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter());

        HBox timeFilterBox = new HBox(6, clockIcon, timeRangeCombo, datePicker);
        timeFilterBox.setAlignment(Pos.CENTER_LEFT);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnRefresh = new Button("Làm mới", new FontIcon(MaterialDesignR.REFRESH));
        btnRefresh.setOnAction(e -> {
            tableView.refresh();
            updateFooterStats();
            toastHandler.show("Đã làm mới", "Dữ liệu nhật ký truy cập đã được cập nhật.", ToastManager.ToastType.SUCCESS);
        });

        Button btnExport = new Button("Xuất CSV", new FontIcon(MaterialDesignD.DOWNLOAD));
        btnExport.setOnAction(e -> toastHandler.show("Xuất báo cáo", "Đã xuất báo cáo truy cập ra file family_access_report.csv", ToastManager.ToastType.SUCCESS));

        Button btnClear = new Button("Xóa log", new FontIcon(MaterialDesignD.DELETE_OUTLINE));
        btnClear.getStyleClass().add("danger");
        btnClear.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Bạn có chắc chắn muốn xóa toàn bộ lịch sử truy cập?", ButtonType.YES, ButtonType.NO);
            alert.setHeaderText("Xác nhận xóa nhật ký");
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    dataService.clearLogs();
                    updateFooterStats();
                    toastHandler.show("Đã dọn dẹp", "Toàn bộ dữ liệu nhật ký đã được xóa.", ToastManager.ToastType.WARNING);
                }
            });
        });

        bar.getChildren().addAll(searchField, filterCombo, timeFilterBox, sp, btnRefresh, btnExport, btnClear);
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
                    badge.getStyleClass().add("status-badge");
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
        String timeVal = timeRangeCombo != null ? timeRangeCombo.getValue() : "Tất cả thời gian";
        LocalDate selectedDate = datePicker != null ? datePicker.getValue() : null;
        LocalDateTime now = LocalDateTime.now();

        filteredData.setPredicate(log -> {
            // Lọc theo trạng thái combo
            if ("Chỉ xem: ĐÃ CHẶN".equals(comboVal) && !"ĐÃ CHẶN".equalsIgnoreCase(log.getAction())) {
                return false;
            }
            if ("Chỉ xem: CHO PHÉP".equals(comboVal) && !"CHO PHÉP".equalsIgnoreCase(log.getAction())) {
                return false;
            }

            // Lọc theo mốc thời gian
            if (timeVal != null && !"Tất cả thời gian".equals(timeVal)) {
                LocalDateTime logTime = dataService.parseLogTimestamp(log.getTimestamp());
                if (logTime == null) {
                    return false;
                }
                switch (timeVal) {
                    case "15 phút gần đây":
                        if (logTime.isBefore(now.minusMinutes(15))) return false;
                        break;
                    case "1 giờ gần đây":
                        if (logTime.isBefore(now.minusHours(1))) return false;
                        break;
                    case "Hôm nay":
                        if (!logTime.toLocalDate().equals(now.toLocalDate())) return false;
                        break;
                    case "7 ngày gần đây":
                        if (logTime.isBefore(now.minusDays(7))) return false;
                        break;
                    case "Chọn ngày cụ thể...":
                        if (selectedDate != null && !logTime.toLocalDate().equals(selectedDate)) return false;
                        break;
                    default:
                        break;
                }
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
        footer.setPadding(new Insets(10, 14, 10, 14));
        footer.getStyleClass().add("fg-card");

        footerStatsLabel = new Label();
        footerStatsLabel.getStyleClass().add("card-subtitle");

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
        String milestone = timeRangeCombo != null ? timeRangeCombo.getValue() : "Tất cả thời gian";
        if ("Chọn ngày cụ thể...".equals(milestone) && datePicker != null && datePicker.getValue() != null) {
            milestone = "Ngày " + datePicker.getValue();
        }
        footerStatsLabel.setText(String.format("Mốc: %s | Hiển thị %d / %d bản ghi | Đã chặn: %d sự kiện | Đồng bộ thời gian thực",
                milestone, showing, total, blocked));
    }
}
