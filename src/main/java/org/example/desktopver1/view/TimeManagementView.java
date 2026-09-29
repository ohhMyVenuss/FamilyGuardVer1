package org.example.desktopver1.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.desktopver1.model.Device;
import org.example.desktopver1.model.TimeSchedule;
import org.example.desktopver1.service.DataService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Giao diện Quản lý thời gian (Time Management):
 * - Đặt hạn mức thời lượng online mỗi ngày (Ngày thường / Cuối tuần)
 * - Khung giờ giới nghiêm linh hoạt (Bedtime Lock) ngắt kết nối internet ra bên ngoài
 * - Tự động ngắt internet thiết bị khi truy cập quá hạn mức 1 ngày
 * - Thưởng/cộng thêm hạn mức thời gian cho từng thiết bị hoặc toàn bộ máy
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

        // 1. Tiêu đề module
        contentBox.getChildren().add(createHeader());

        // 2. Hàng 2 cột: Cài đặt hạn mức & Khung giờ giới nghiêm linh hoạt
        HBox settingsRow = new HBox(20);
        VBox col1 = createDailyLimitCard();
        VBox col2 = createCurfewCard();
        HBox.setHgrow(col1, Priority.ALWAYS);
        HBox.setHgrow(col2, Priority.ALWAYS);
        settingsRow.getChildren().addAll(col1, col2);
        contentBox.getChildren().add(settingsRow);

        // 3. Theo dõi tiến độ thực tế & Cộng thêm giờ từng máy
        contentBox.getChildren().add(createDeviceTimeTrackerCard());

        setContent(contentBox);
    }

    private VBox createHeader() {
        VBox box = new VBox(4);
        Label title = new Label("Quản Lý & Giới Hạn Thời Gian Truy Cập");
        title.getStyleClass().add("header-welcome");
        Label sub = new Label("Thiết lập thời gian biểu khoa học, tự động ngắt Internet khi quá hạn hoặc vào giờ giới nghiêm.");
        sub.getStyleClass().add("header-subtitle");
        box.getChildren().addAll(title, sub);
        return box;
    }

    private VBox createDailyLimitCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");

        Label cardTitle = new Label("⏱️ Hạn mức sử dụng hàng ngày (Daily Limits)");
        cardTitle.getStyleClass().add("card-title");
        Label cardSub = new Label("Hệ thống sẽ tự động ngắt kết nối internet của thiết bị khi hết giờ trong ngày");
        cardSub.getStyleClass().add("card-subtitle");

        // Đọc giá trị đã lưu từ database
        double initWeekday = 2.0;
        double initWeekend = 4.0;
        if (dataService.getTimeSchedules() != null && !dataService.getTimeSchedules().isEmpty()) {
            for (TimeSchedule ts : dataService.getTimeSchedules()) {
                String dg = ts.getDayGroup().toLowerCase();
                if (dg.contains("cuối") || dg.contains("t7") || dg.contains("weekend")) {
                    initWeekend = ts.getDailyLimitHours();
                } else {
                    initWeekday = ts.getDailyLimitHours();
                }
            }
        }

        // 1. Ngày thường
        VBox weekdayBox = new VBox(6);
        Label lblWeekday = new Label(String.format("Ngày trong tuần (Thứ 2 - Thứ 6): %.1f giờ / ngày", initWeekday));
        lblWeekday.setStyle("-fx-font-weight: 600; -fx-text-fill: #1E293B;");
        Slider sliderWeekday = new Slider(0.5, 8.0, initWeekday);
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
        Label lblWeekend = new Label(String.format("Cuối tuần (Thứ 7 & Chủ Nhật): %.1f giờ / ngày", initWeekend));
        lblWeekend.setStyle("-fx-font-weight: 600; -fx-text-fill: #1E293B;");
        Slider sliderWeekend = new Slider(1.0, 10.0, initWeekend);
        sliderWeekend.setShowTickMarks(true);
        sliderWeekend.setShowTickLabels(true);
        sliderWeekend.setMajorTickUnit(1.0);
        sliderWeekend.setBlockIncrement(0.5);
        sliderWeekend.valueProperty().addListener((obs, oldVal, newVal) -> {
            lblWeekend.setText(String.format("Cuối tuần (Thứ 7 & Chủ Nhật): %.1f giờ / ngày", newVal.doubleValue()));
        });
        weekendBox.getChildren().addAll(lblWeekend, sliderWeekend);

        Label note = new Label("💡 Khi thiết bị vượt quá số giờ trên, Internet sẽ lập tức bị ngắt. Phụ huynh có thể thưởng thêm giờ để mở lại mạng bất cứ lúc nào.");
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B; -fx-background-color: #F8FAFC; -fx-padding: 8; -fx-background-radius: 6;");

        Button btnSaveLimits = new Button("Lưu hạn mức");
        btnSaveLimits.getStyleClass().add("btn-primary");
        btnSaveLimits.setOnAction(e -> {
            dataService.updateDailyLimits(sliderWeekday.getValue(), sliderWeekend.getValue());
            toastNotifier.accept(String.format("Đã cập nhật hạn mức: Ngày thường %.1fh - Cuối tuần %.1fh", sliderWeekday.getValue(), sliderWeekend.getValue()));
        });

        card.getChildren().addAll(cardTitle, cardSub, new Separator(), weekdayBox, weekendBox, note, btnSaveLimits);
        return card;
    }

    private VBox createCurfewCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        Label cardTitle = new Label("🌙 Giờ giới nghiêm & Khóa ban đêm (Bedtime Lock)");
        cardTitle.getStyleClass().add("card-title");
        Label cardSub = new Label("Khóa hoàn toàn kết nối Internet ra bên ngoài của các máy con từ đêm đến sáng");
        cardSub.getStyleClass().add("card-subtitle");

        // Đọc cấu hình giờ giới nghiêm hiện tại từ database
        boolean initCurfewActive = true;
        String initStart = "21:30";
        String initEnd = "06:00";
        if (dataService.getTimeSchedules() != null && !dataService.getTimeSchedules().isEmpty()) {
            TimeSchedule ts = dataService.getTimeSchedules().get(0);
            initCurfewActive = ts.isActive();
            if (ts.getCurfewStart() != null && !ts.getCurfewStart().isEmpty()) initStart = ts.getCurfewStart();
            if (ts.getCurfewEnd() != null && !ts.getCurfewEnd().isEmpty()) initEnd = ts.getCurfewEnd();
        }

        CheckBox cbEnableCurfew = new CheckBox("Kích hoạt chế độ khóa mạng ban đêm tự động");
        cbEnableCurfew.setSelected(initCurfewActive);
        cbEnableCurfew.setStyle("-fx-font-weight: bold; -fx-text-fill: #0F172A; -fx-font-size: 13px;");

        // --- BỘ CHỌN GIỜ LINH HOẠT ---
        HBox timeRow = new HBox(20);
        timeRow.setAlignment(Pos.CENTER_LEFT);

        // Giờ bắt đầu (Start Time)
        VBox startBox = new VBox(6);
        Label lblStart = new Label("Giờ bắt đầu khóa mạng:");
        lblStart.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");

        HBox startPicker = new HBox(6);
        startPicker.setAlignment(Pos.CENTER_LEFT);
        ComboBox<String> cbStartHour = createHourComboBox(initStart.split(":")[0]);
        ComboBox<String> cbStartMin = createMinuteComboBox(initStart.contains(":") ? initStart.split(":")[1] : "00");
        Label colon1 = new Label(":");
        colon1.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        startPicker.getChildren().addAll(cbStartHour, colon1, cbStartMin);
        startBox.getChildren().addAll(lblStart, startPicker);

        // Giờ kết thúc (End Time)
        VBox endBox = new VBox(6);
        Label lblEnd = new Label("Giờ mở lại mạng:");
        lblEnd.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");

        HBox endPicker = new HBox(6);
        endPicker.setAlignment(Pos.CENTER_LEFT);
        ComboBox<String> cbEndHour = createHourComboBox(initEnd.split(":")[0]);
        ComboBox<String> cbEndMin = createMinuteComboBox(initEnd.contains(":") ? initEnd.split(":")[1] : "00");
        Label colon2 = new Label(":");
        colon2.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        endPicker.getChildren().addAll(cbEndHour, colon2, cbEndMin);
        endBox.getChildren().addAll(lblEnd, endPicker);

        timeRow.getChildren().addAll(startBox, endBox);

        // Nhãn tính toán động tổng thời gian khóa và trạng thái thực tế
        Label lblCurfewSummary = new Label();
        lblCurfewSummary.setStyle("-fx-font-size: 12px; -fx-text-fill: #2563EB; -fx-font-weight: bold;");

        Runnable updateSummary = () -> {
            String sh = cbStartHour.getValue();
            String sm = cbStartMin.getValue();
            String eh = cbEndHour.getValue();
            String em = cbEndMin.getValue();
            if (sh == null || sm == null || eh == null || em == null) return;
            try {
                int sTotal = Integer.parseInt(sh) * 60 + Integer.parseInt(sm);
                int eTotal = Integer.parseInt(eh) * 60 + Integer.parseInt(em);
                int durMin = (eTotal >= sTotal) ? (eTotal - sTotal) : (1440 - sTotal + eTotal);
                int durH = durMin / 60;
                int durM = durMin % 60;

                LocalTime now = LocalTime.now();
                int nowMin = now.getHour() * 60 + now.getMinute();
                boolean isCurfewNow = cbEnableCurfew.isSelected() && (sTotal < eTotal
                        ? (nowMin >= sTotal && nowMin < eTotal)
                        : (nowMin >= sTotal || nowMin < eTotal));

                String durationText = String.format("⏰ Khóa Internet trong: %d giờ %02d phút (từ %s:%s đến %s:%s)", durH, durM, sh, sm, eh, em);
                if (isCurfewNow) {
                    lblCurfewSummary.setText(durationText + "\n🔴 HIỆN ĐANG TRONG GIỜ GIỚI NGHIÊM (INTERNET ĐANG BỊ NGẮT)");
                    lblCurfewSummary.setStyle("-fx-font-size: 11px; -fx-text-fill: #DC2626; -fx-font-weight: bold;");
                } else {
                    lblCurfewSummary.setText(durationText);
                    lblCurfewSummary.setStyle("-fx-font-size: 11px; -fx-text-fill: #2563EB; -fx-font-weight: bold;");
                }
            } catch (Exception ignored) {}
        };

        cbStartHour.setOnAction(e -> updateSummary.run());
        cbStartMin.setOnAction(e -> updateSummary.run());
        cbEndHour.setOnAction(e -> updateSummary.run());
        cbEndMin.setOnAction(e -> updateSummary.run());
        cbEnableCurfew.setOnAction(e -> updateSummary.run());
        updateSummary.run();

        // Mẫu giờ gợi ý chọn nhanh
        HBox presetChips = new HBox(8);
        presetChips.setAlignment(Pos.CENTER_LEFT);
        Label lblPresets = new Label("Gợi ý nhanh:");
        lblPresets.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        Button chip1 = createPresetChip("🌙 21:30 - 06:00 (Chuẩn)", "21", "30", "06", "00", cbStartHour, cbStartMin, cbEndHour, cbEndMin, updateSummary);
        Button chip2 = createPresetChip("📚 20:30 - 06:00 (Thi cử)", "20", "30", "06", "00", cbStartHour, cbStartMin, cbEndHour, cbEndMin, updateSummary);
        Button chip3 = createPresetChip("🎮 22:30 - 07:00 (Cuối tuần)", "22", "30", "07", "00", cbStartHour, cbStartMin, cbEndHour, cbEndMin, updateSummary);
        Button chip4 = createPresetChip("☀️ 12:00 - 13:30 (Trưa)", "12", "00", "13", "30", cbStartHour, cbStartMin, cbEndHour, cbEndMin, updateSummary);

        presetChips.getChildren().addAll(lblPresets, chip1, chip2, chip3, chip4);

        Label note = new Label("ℹ️ Trong khung giờ giới nghiêm, toàn bộ Internet ra bên ngoài bị ngắt triệt để (ngoại trừ các website học tập trong Danh sách trắng).");
        note.setWrapText(true);
        note.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B; -fx-background-color: #F8FAFC; -fx-padding: 8; -fx-background-radius: 6;");

        Button btnSaveCurfew = new Button("Lưu giờ giới nghiêm");
        btnSaveCurfew.getStyleClass().add("btn-primary");
        btnSaveCurfew.setOnAction(e -> {
            boolean enabled = cbEnableCurfew.isSelected();
            String start = cbStartHour.getValue() + ":" + cbStartMin.getValue();
            String end = cbEndHour.getValue() + ":" + cbEndMin.getValue();
            dataService.updateCurfew(enabled, start, end);
            if (enabled) {
                toastNotifier.accept("Đã bật giới nghiêm ban đêm từ " + start + " đến " + end + ". Internet sẽ tự động ngắt!");
            } else {
                toastNotifier.accept("Đã tắt chế độ giới nghiêm ban đêm.");
            }
            updateSummary.run();
        });

        card.getChildren().addAll(cardTitle, cardSub, new Separator(), cbEnableCurfew, timeRow, presetChips, lblCurfewSummary, note, btnSaveCurfew);
        return card;
    }

    private ComboBox<String> createHourComboBox(String initialValue) {
        ComboBox<String> cb = new ComboBox<>();
        for (int i = 0; i < 24; i++) {
            cb.getItems().add(String.format("%02d", i));
        }
        cb.setValue(initialValue != null ? initialValue : "21");
        cb.setPrefWidth(75);
        cb.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        return cb;
    }

    private ComboBox<String> createMinuteComboBox(String initialValue) {
        ComboBox<String> cb = new ComboBox<>();
        for (int i = 0; i < 60; i += 5) {
            cb.getItems().add(String.format("%02d", i));
        }
        cb.setValue(initialValue != null ? initialValue : "00");
        cb.setPrefWidth(75);
        cb.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        return cb;
    }

    private Button createPresetChip(String text, String sh, String sm, String eh, String em,
                                    ComboBox<String> cbSh, ComboBox<String> cbSm,
                                    ComboBox<String> cbEh, ComboBox<String> cbEm, Runnable callback) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #334155; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 6; -fx-cursor: hand;");
        btn.setOnAction(e -> {
            cbSh.setValue(sh);
            cbSm.setValue(sm);
            cbEh.setValue(eh);
            cbEm.setValue(em);
            callback.run();
        });
        return btn;
    }

    private VBox createDeviceTimeTrackerCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");

        HBox head = new HBox(12);
        head.setAlignment(Pos.CENTER_LEFT);

        DayOfWeek dow = LocalDate.now().getDayOfWeek();
        boolean isWeekend = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
        String dayDesc = isWeekend ? "Cuối tuần (T7 & CN)" : "Ngày trong tuần (T2 - T6)";

        VBox titleBox = new VBox(2);
        Label cardTitle = new Label("📊 Tiến độ sử dụng thời gian hôm nay & Thưởng giờ theo từng máy");
        cardTitle.getStyleClass().add("card-title");
        Label cardSub = new Label("Hôm nay: " + dayDesc + " • Tự động ngắt kết nối Internet khi vượt quá hạn mức trong ngày.");
        cardSub.getStyleClass().add("card-subtitle");
        titleBox.getChildren().addAll(cardTitle, cardSub);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        // Nhóm nút thưởng thời gian cho tất cả thiết bị
        HBox globalBonusBox = new HBox(8);
        globalBonusBox.setAlignment(Pos.CENTER_RIGHT);

        Label lblAll = new Label("🎁 Thưởng tất cả:");
        lblAll.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B; -fx-font-weight: bold;");

        Button btnBonusAll15 = new Button("+15p");
        btnBonusAll15.getStyleClass().add("btn-outline");
        btnBonusAll15.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");
        btnBonusAll15.setOnAction(e -> {
            dataService.addBonusMinutesToAllDevices(15);
            toastNotifier.accept("Đã thưởng thêm 15 phút cho tất cả các thiết bị!");
        });

        Button btnBonusAll30 = new Button("+30p");
        btnBonusAll30.getStyleClass().add("btn-outline");
        btnBonusAll30.setStyle("-fx-font-size: 11px; -fx-padding: 4 8; -fx-font-weight: bold; -fx-text-fill: #2563EB;");
        btnBonusAll30.setOnAction(e -> {
            dataService.addBonusMinutesToAllDevices(30);
            toastNotifier.accept("Đã thưởng thêm 30 phút cho tất cả các thiết bị!");
        });

        Button btnBonusAll60 = new Button("+1 giờ");
        btnBonusAll60.getStyleClass().add("btn-outline");
        btnBonusAll60.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");
        btnBonusAll60.setOnAction(e -> {
            dataService.addBonusMinutesToAllDevices(60);
            toastNotifier.accept("Đã thưởng thêm 1 giờ cho tất cả các thiết bị!");
        });

        Button btnResetBonusAll = new Button("🔄 Đặt lại thưởng");
        btnResetBonusAll.getStyleClass().add("btn-outline");
        btnResetBonusAll.setStyle("-fx-font-size: 11px; -fx-padding: 4 8; -fx-text-fill: #64748B;");
        btnResetBonusAll.setOnAction(e -> {
            dataService.resetAllBonusMinutes();
            toastNotifier.accept("Đã đặt lại giờ thưởng hôm nay về mặc định!");
        });

        globalBonusBox.getChildren().addAll(lblAll, btnBonusAll15, btnBonusAll30, btnBonusAll60, btnResetBonusAll);
        head.getChildren().addAll(titleBox, sp, globalBonusBox);

        VBox trackerList = new VBox(14);
        refreshTrackerList(trackerList);

        // Lắng nghe để cập nhật tiến độ sử dụng thời gian của các thiết bị theo thời gian thực
        dataService.getAccessLogs().addListener((javafx.collections.ListChangeListener<org.example.desktopver1.model.AccessLog>) c -> {
            javafx.application.Platform.runLater(() -> refreshTrackerList(trackerList));
        });
        dataService.getDevices().addListener((javafx.collections.ListChangeListener<org.example.desktopver1.model.Device>) c -> {
            javafx.application.Platform.runLater(() -> refreshTrackerList(trackerList));
        });

        card.getChildren().addAll(head, new Separator(), trackerList);
        return card;
    }

    private void refreshTrackerList(VBox trackerList) {
        trackerList.getChildren().clear();

        if (dataService.getDevices().isEmpty()) {
            Label empty = new Label("Chưa có thiết bị nào được kết nối trong hệ thống.");
            empty.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12px;");
            trackerList.getChildren().add(empty);
            return;
        }

        for (Device dev : dataService.getDevices()) {
            trackerList.getChildren().add(createDeviceTrackerRow(dev));
        }
    }

    private VBox createDeviceTrackerRow(Device dev) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12, 14, 12, 14));
        box.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 10px; -fx-border-color: #E2E8F0; -fx-border-radius: 10px;");

        double spentHours = dataService.parseSpentHours(dev.getTimeSpentToday());
        double deviceLimitHours = dataService.getTodayDailyLimitHours(dev);
        double progress = Math.min(1.0, spentHours / Math.max(0.1, deviceLimitHours));
        boolean isExceeded = spentHours >= deviceLimitHours && deviceLimitHours > 0;
        boolean isWarning = !isExceeded && progress >= 0.85;
        int bonusMin = dev.getBonusMinutes();

        // 1. Hàng thông tin thiết bị, badge trạng thái & thời gian
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        String icon = "Điện thoại".equalsIgnoreCase(dev.getType()) ? "📱" : ("Máy tính bảng".equalsIgnoreCase(dev.getType()) ? "📱" : "💻");
        Label lblIcon = new Label(icon);
        lblIcon.setStyle("-fx-font-size: 18px;");

        Label lblName = new Label(dev.getName());
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1E293B;");

        Label lblIp = new Label("[" + dev.getIpAddress() + "]");
        lblIp.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        topRow.getChildren().addAll(lblIcon, lblName, lblIp);

        if (bonusMin > 0) {
            Label lblBonusBadge = new Label("🎁 +" + bonusMin + "m thưởng");
            lblBonusBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #16A34A; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 6;");
            topRow.getChildren().add(lblBonusBadge);
        }

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        topRow.getChildren().add(sp);

        // Badge trạng thái mạng
        Label lblStatusBadge = new Label();
        if (isExceeded || dev.isBlocked()) {
            lblStatusBadge.setText("⛔ ĐÃ NGẮT INTERNET (Hết giờ)");
            lblStatusBadge.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 6;");
        } else if (isWarning) {
            lblStatusBadge.setText("⚠️ SẮP HẾT HẠN MỨC");
            lblStatusBadge.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #D97706; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 6;");
        } else {
            lblStatusBadge.setText("🟢 ĐANG HOẠT ĐỘNG");
            lblStatusBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #16A34A; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 6;");
        }

        String timeStr = dev.getTimeSpentToday() + " / " + String.format("%.1fh", deviceLimitHours);
        Label lblTime = new Label(timeStr);
        lblTime.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; " + (isExceeded ? "-fx-text-fill: #DC2626;" : (isWarning ? "-fx-text-fill: #D97706;" : "-fx-text-fill: #059669;")));

        topRow.getChildren().addAll(lblStatusBadge, lblTime);

        // 2. Thanh tiến độ
        ProgressBar bar = new ProgressBar(progress);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(9);
        if (isExceeded || dev.isBlocked()) {
            bar.setStyle("-fx-accent: #EF4444;");
        } else if (isWarning) {
            bar.setStyle("-fx-accent: #F59E0B;");
        } else {
            bar.setStyle("-fx-accent: #2563EB;");
        }

        // 3. Hàng điều khiển thưởng thêm hạn mức cho riêng thiết bị này
        HBox actionRow = new HBox(8);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Label lblActionTitle = new Label("🎁 Thưởng thêm giờ hôm nay:");
        lblActionTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        Button btnPlus15 = createBonusButton("+15 phút", () -> applyBonusToDevice(dev, 15));
        Button btnPlus30 = createBonusButton("+30 phút", () -> applyBonusToDevice(dev, 30));
        Button btnPlus60 = createBonusButton("+1 giờ", () -> applyBonusToDevice(dev, 60));

        Button btnCustomBonus = new Button("Tùy chọn...");
        btnCustomBonus.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #334155; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
        btnCustomBonus.setOnAction(e -> showCustomBonusDialog(dev));

        actionRow.getChildren().addAll(lblActionTitle, btnPlus15, btnPlus30, btnPlus60, btnCustomBonus);

        if (bonusMin > 0) {
            Button btnResetDevBonus = new Button("↩ Hủy thưởng");
            btnResetDevBonus.setStyle("-fx-background-color: transparent; -fx-text-fill: #DC2626; -fx-font-size: 11px; -fx-underline: true; -fx-cursor: hand; -fx-padding: 2 4;");
            btnResetDevBonus.setOnAction(e -> {
                dataService.addBonusMinutesToDevice(dev, -bonusMin);
                toastNotifier.accept("Đã hủy giờ thưởng của " + dev.getName());
                refreshCurrentTracker();
            });
            actionRow.getChildren().add(btnResetDevBonus);
        }

        box.getChildren().addAll(topRow, bar, actionRow);
        return box;
    }

    private Button createBonusButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #CBD5E1; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 11px; -fx-text-fill: #334155; -fx-padding: 3 8; -fx-cursor: hand;");
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private void applyBonusToDevice(Device dev, int minutes) {
        dataService.addBonusMinutesToDevice(dev, minutes);
        double newLimit = dataService.getTodayDailyLimitHours(dev);
        toastNotifier.accept("Đã thưởng thêm +" + minutes + " phút cho " + dev.getName() + "! Hạn mức mới: " + String.format("%.1fh", newLimit));
        refreshCurrentTracker();
    }

    private void showCustomBonusDialog(Device dev) {
        TextInputDialog dialog = new TextInputDialog("45");
        dialog.setTitle("Thưởng thêm thời gian");
        dialog.setHeaderText("Cộng thêm hạn mức sử dụng hôm nay cho:\n" + dev.getName() + " (" + dev.getIpAddress() + ")");
        dialog.setContentText("Nhập số phút muốn cộng thêm:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(val -> {
            try {
                int min = Integer.parseInt(val.trim());
                if (min > 0 && min <= 720) {
                    applyBonusToDevice(dev, min);
                } else {
                    toastNotifier.accept("Vui lòng nhập số phút hợp lệ (từ 1 đến 720 phút)!");
                }
            } catch (Exception ex) {
                toastNotifier.accept("Số phút nhập vào không hợp lệ!");
            }
        });
    }

    private void refreshCurrentTracker() {
        Node content = getContent();
        if (content instanceof VBox) {
            VBox box = (VBox) content;
            if (box.getChildren().size() >= 3 && box.getChildren().get(2) instanceof VBox) {
                VBox trackerCard = (VBox) box.getChildren().get(2);
                if (trackerCard.getChildren().size() >= 3 && trackerCard.getChildren().get(2) instanceof VBox) {
                    refreshTrackerList((VBox) trackerCard.getChildren().get(2));
                }
            }
        }
    }
}
