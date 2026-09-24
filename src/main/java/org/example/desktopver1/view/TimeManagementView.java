package org.example.desktopver1.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.desktopver1.service.DataService;

import java.util.function.Consumer;

/**
 * Giao diện Quản lý thời gian (Time Management):
 * - Đặt hạn mức thời lượng online mỗi ngày (Ngày thường / Cuối tuần)
 * - Khung giờ giới nghiêm tự động (Bedtime Lock)
 * - Theo dõi tiến độ dùng mạng của từng thiết bị trong ngày
 * - Thưởng thêm thời gian hoặc khóa mạng nhanh
 */
public class TimeManagementView extends ScrollPane {

    private final DataService dataService;
    private final Consumer<String> toastNotifier;

    public TimeManagementView(DataService dataService, Consumer<String> toastNotifier) {
        this.dataService = dataService;
        this.toastNotifier = toastNotifier;

        setFitToWidth(true);
        setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        VBox contentBox = new VBox(24);
        contentBox.getStyleClass().add("view-container");

        // Tiêu đề module
        contentBox.getChildren().add(createHeader());

        // Hàng 2 cột: Cài đặt hạn mức & Khung giờ giới nghiêm
        HBox settingsRow = new HBox(20);
        VBox col1 = createDailyLimitCard();
        VBox col2 = createCurfewCard();
        HBox.setHgrow(col1, Priority.ALWAYS);
        HBox.setHgrow(col2, Priority.ALWAYS);
        settingsRow.getChildren().addAll(col1, col2);
        contentBox.getChildren().add(settingsRow);

        // Theo dõi tiến độ thực tế các thiết bị hôm nay
        contentBox.getChildren().add(createDeviceTimeTrackerCard());

        setContent(contentBox);
    }

    private VBox createHeader() {
        VBox box = new VBox(4);
        Label title = new Label("Quản Lý & Giới Hạn Thời Gian Truy Cập");
        title.getStyleClass().add("header-welcome");
        Label sub = new Label("Thiết lập thời gian biểu khoa học giúp trẻ cân bằng giữa việc học tập, giải trí và nghỉ ngơi.");
        sub.getStyleClass().add("header-subtitle");
        box.getChildren().addAll(title, sub);
        return box;
    }

    private VBox createDailyLimitCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");

        Label cardTitle = new Label("⏱️ Hạn mức sử dụng hàng ngày (Daily Limits)");
        cardTitle.getStyleClass().add("card-title");
        Label cardSub = new Label("Hệ thống sẽ tự động ngắt kết nối internet khi hết giờ");
        cardSub.getStyleClass().add("card-subtitle");

        // 1. Ngày thường
        VBox weekdayBox = new VBox(6);
        Label lblWeekday = new Label("Ngày trong tuần (Thứ 2 - Thứ 6): 2.0 giờ / ngày");
        lblWeekday.setStyle("-fx-font-weight: 600; -fx-text-fill: #1E293B;");
        Slider sliderWeekday = new Slider(0.5, 6.0, 2.0);
        sliderWeekday.setShowTickMarks(true);
        sliderWeekday.setShowTickLabels(true);
        sliderWeekday.setMajorTickUnit(1.0);
        sliderWeekday.setBlockIncrement(0.5);
        sliderWeekday.valueProperty().addListener((obs, oldVal, newVal) -> {
            lblWeekday.setText(String.format("Ngày trong tuần (Thứ 2 - Thứ 6): %.1f giờ / ngày", newVal.doubleValue()));
        });
        weekdayBox.getChildren().addAll(lblWeekday, sliderWeekday);

        // 2. Cuối tuần
        VBox weekendBox = new VBox(6);
        Label lblWeekend = new Label("Cuối tuần (Thứ 7 & Chủ Nhật): 4.0 giờ / ngày");
        lblWeekend.setStyle("-fx-font-weight: 600; -fx-text-fill: #1E293B;");
        Slider sliderWeekend = new Slider(1.0, 8.0, 4.0);
        sliderWeekend.setShowTickMarks(true);
        sliderWeekend.setShowTickLabels(true);
        sliderWeekend.setMajorTickUnit(1.0);
        sliderWeekend.setBlockIncrement(0.5);
        sliderWeekend.valueProperty().addListener((obs, oldVal, newVal) -> {
            lblWeekend.setText(String.format("Cuối tuần (Thứ 7 & Chủ Nhật): %.1f giờ / ngày", newVal.doubleValue()));
        });
        weekendBox.getChildren().addAll(lblWeekend, sliderWeekend);

        Button btnSaveLimits = new Button("Lưu hạn mức");
        btnSaveLimits.getStyleClass().add("btn-primary");
        btnSaveLimits.setOnAction(e -> {
            toastNotifier.accept(String.format("Đã cập nhật hạn mức: Ngày thường %.1fh - Cuối tuần %.1fh", sliderWeekday.getValue(), sliderWeekend.getValue()));
        });

        card.getChildren().addAll(cardTitle, cardSub, new Separator(), weekdayBox, weekendBox, btnSaveLimits);
        return card;
    }

    private VBox createCurfewCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");

        Label cardTitle = new Label("🌙 Giờ giới nghiêm & Khóa ban đêm (Bedtime Lock)");
        cardTitle.getStyleClass().add("card-title");
        Label cardSub = new Label("Khóa hoàn toàn mạng internet từ đêm đến sáng hôm sau");
        cardSub.getStyleClass().add("card-subtitle");

        CheckBox cbEnableCurfew = new CheckBox("Kích hoạt chế độ khóa mạng ban đêm tự động");
        cbEnableCurfew.setSelected(true);
        cbEnableCurfew.setStyle("-fx-font-weight: bold; -fx-text-fill: #0F172A;");

        HBox timeRow = new HBox(16);
        timeRow.setAlignment(Pos.CENTER_LEFT);

        VBox startBox = new VBox(6);
        Label lblStart = new Label("Giờ bắt đầu khóa:");
        lblStart.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");
        ComboBox<String> cbStart = new ComboBox<>();
        cbStart.getItems().addAll("21:00", "21:30", "22:00", "22:30", "23:00");
        cbStart.setValue("21:30");
        startBox.getChildren().addAll(lblStart, cbStart);

        VBox endBox = new VBox(6);
        Label lblEnd = new Label("Giờ mở lại mạng:");
        lblEnd.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");
        ComboBox<String> cbEnd = new ComboBox<>();
        cbEnd.getItems().addAll("05:30", "06:00", "06:30", "07:00");
        cbEnd.setValue("06:00");
        endBox.getChildren().addAll(lblEnd, cbEnd);

        timeRow.getChildren().addAll(startBox, endBox);

        Label note = new Label("ℹ️ Trong khung giờ giới nghiêm, chỉ có website danh sách trắng (học tập) mới có thể xem.");
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B; -fx-background-color: #F8FAFC; -fx-padding: 8; -fx-background-radius: 6;");

        Button btnSaveCurfew = new Button("Lưu giờ giới nghiêm");
        btnSaveCurfew.getStyleClass().add("btn-primary");
        btnSaveCurfew.setOnAction(e -> {
            if (cbEnableCurfew.isSelected()) {
                toastNotifier.accept("Đã bật khóa ban đêm từ " + cbStart.getValue() + " đến " + cbEnd.getValue());
            } else {
                toastNotifier.accept("Đã tắt chế độ giới nghiêm ban đêm.");
            }
        });

        card.getChildren().addAll(cardTitle, cardSub, new Separator(), cbEnableCurfew, timeRow, note, btnSaveCurfew);
        return card;
    }

    private VBox createDeviceTimeTrackerCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");

        HBox head = new HBox();
        head.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label cardTitle = new Label("📊 Tiến độ sử dụng thời gian hôm nay theo từng máy");
        cardTitle.getStyleClass().add("card-title");
        Label cardSub = new Label("Giám sát thời gian thực tế đã sử dụng so với hạn mức trong ngày (3.0 giờ)");
        cardSub.getStyleClass().add("card-subtitle");
        titleBox.getChildren().addAll(cardTitle, cardSub);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnBonusTime = new Button("🎁 Thưởng +30 phút hôm nay");
        btnBonusTime.getStyleClass().add("btn-outline");
        btnBonusTime.setOnAction(e -> toastNotifier.accept("Đã cộng thêm 30 phút sử dụng cho các thiết bị hoàn thành bài tập!"));

        head.getChildren().addAll(titleBox, sp, btnBonusTime);

        VBox trackerList = new VBox(14);
        trackerList.getChildren().addAll(
                createDeviceProgressRow("PC Phòng Học - Bé Minh", "2h 45m / 3h 00m", 0.91, true),
                createDeviceProgressRow("iPad Pro - Bé Lan", "1h 15m / 3h 00m", 0.41, false),
                createDeviceProgressRow("Laptop Asus - Học Tập", "1h 50m / 3h 00m", 0.61, false)
        );

        card.getChildren().addAll(head, new Separator(), trackerList);
        return card;
    }

    private VBox createDeviceProgressRow(String deviceName, String timeStr, double progress, boolean isWarning) {
        VBox row = new VBox(6);

        HBox labelRow = new HBox();
        labelRow.setAlignment(Pos.CENTER_LEFT);

        Label lblName = new Label(deviceName);
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1E293B;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label lblTime = new Label(timeStr);
        lblTime.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; " + (isWarning ? "-fx-text-fill: #DC2626;" : "-fx-text-fill: #059669;"));

        labelRow.getChildren().addAll(lblName, sp, lblTime);

        ProgressBar bar = new ProgressBar(progress);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(10);
        if (isWarning) {
            bar.setStyle("-fx-accent: #EF4444;");
        } else {
            bar.setStyle("-fx-accent: #2563EB;");
        }

        row.getChildren().addAll(labelRow, bar);
        return row;
    }
}
