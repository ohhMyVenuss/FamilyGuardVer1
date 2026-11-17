package org.example.desktopver1.view;

import atlantafx.base.controls.ToggleSwitch;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.desktopver1.model.AccessLog;
import org.example.desktopver1.model.Device;
import org.example.desktopver1.model.TimeSchedule;
import org.example.desktopver1.service.DataService;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignG;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Màn hình Quản lý thời gian (Time Management):
 * - Hạn mức hàng ngày: Thanh trượt điều chỉnh số giờ tối đa cho ngày trong tuần (T2 - T6) và cuối tuần (T7, CN)
 * - Giờ giới nghiêm ban đêm (Bedtime Curfew): Tự động ngắt mạng từ 21:30 đến 06:00 sáng
 * - Thanh đo tiến độ (ProgressBar): Theo dõi trực tiếp thời gian từng thiết bị đã sử dụng trong ngày
 * - Tính năng thưởng giờ: Cộng thêm 30 phút (hoặc tùy chỉnh) khi con hoàn thành tốt bài tập
 */
public class TimeManagementView extends ScrollPane {

    private final DataService dataService;
    private final ToastManager.ToastHandler toastHandler;
    private VBox trackerList;

    public TimeManagementView(DataService dataService, Consumer<String> legacyToast, ToastManager.ToastHandler toastHandler) {
        this.dataService = dataService;
        this.toastHandler = toastHandler;

        setFitToWidth(true);
        getStyleClass().add("view-scroll");

        VBox contentBox = new VBox(20);
        contentBox.getStyleClass().add("view-container");

        // 1. Tiêu đề
        contentBox.getChildren().add(createHeader());

        // 2. Hàng 2 cột: Hạn mức hàng ngày & Giờ giới nghiêm ban đêm
        HBox settingsRow = new HBox(20);
        VBox dailyLimitCard = createDailyLimitCard();
        VBox curfewCard = createCurfewCard();
        HBox.setHgrow(dailyLimitCard, Priority.ALWAYS);
        HBox.setHgrow(curfewCard, Priority.ALWAYS);
        settingsRow.getChildren().addAll(dailyLimitCard, curfewCard);
        contentBox.getChildren().add(settingsRow);

        // 3. Thanh đo tiến độ trực tiếp theo thiết bị & Thưởng giờ
        contentBox.getChildren().add(createDeviceTimeTrackerCard());

        setContent(contentBox);
    }

    private VBox createHeader() {
        VBox box = new VBox(4);
        Label title = new Label("Quản lý Thời gian (Time Management)");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Thiết lập hạn mức sử dụng hàng ngày, giờ giới nghiêm ban đêm và thưởng thêm thời gian cho con.");
        sub.getStyleClass().add("page-subtitle");
        box.getChildren().addAll(title, sub);
        return box;
    }

    private VBox createDailyLimitCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("fg-card");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = new FontIcon(MaterialDesignC.CLOCK_OUTLINE);
        icon.setIconSize(20);
        Label cardTitle = new Label("Hạn mức hàng ngày (Daily Limits)");
        cardTitle.getStyleClass().add("card-title");
        titleRow.getChildren().addAll(icon, cardTitle);

        Label cardSub = new Label("Điều chỉnh số giờ tối đa được phép truy cập Internet mỗi ngày.");
        cardSub.getStyleClass().add("card-subtitle");

        double initWeekday = 2.0;
        double initWeekend = 4.0;
        if (dataService.getTimeSchedules() != null) {
            for (TimeSchedule ts : dataService.getTimeSchedules()) {
                String dg = ts.getDayGroup() != null ? ts.getDayGroup().toLowerCase() : "";
                if (dg.contains("cuối") || dg.contains("t7") || dg.contains("weekend")) {
                    initWeekend = ts.getDailyLimitHours();
                } else {
                    initWeekday = ts.getDailyLimitHours();
                }
            }
        }

        // Thanh trượt T2 - T6
        VBox weekdayBox = new VBox(6);
        HBox weekdayHeader = new HBox();
        weekdayHeader.setAlignment(Pos.CENTER_LEFT);
        Label lblWeekdayTitle = new Label("Ngày trong tuần (T2 - T6)");
        lblWeekdayTitle.setStyle("-fx-font-weight: bold;");
        Region sp1 = new Region();
        HBox.setHgrow(sp1, Priority.ALWAYS);
        Label lblWeekdayVal = new Label(String.format("%.1f giờ / ngày", initWeekday));
        lblWeekdayVal.getStyleClass().addAll("status-badge", "badge-neutral");
        weekdayHeader.getChildren().addAll(lblWeekdayTitle, sp1, lblWeekdayVal);

        Slider sliderWeekday = new Slider(0.5, 8.0, initWeekday);
        sliderWeekday.setShowTickMarks(true);
        sliderWeekday.setShowTickLabels(true);
        sliderWeekday.setMajorTickUnit(1.0);
        sliderWeekday.setBlockIncrement(0.5);
        sliderWeekday.valueProperty().addListener((obs, oldVal, newVal) -> {
            double snapped = Math.round(newVal.doubleValue() * 2.0) / 2.0;
            lblWeekdayVal.setText(String.format("%.1f giờ / ngày", snapped));
        });
        weekdayBox.getChildren().addAll(weekdayHeader, sliderWeekday);

        // Thanh trượt T7, CN
        VBox weekendBox = new VBox(6);
        HBox weekendHeader = new HBox();
        weekendHeader.setAlignment(Pos.CENTER_LEFT);
        Label lblWeekendTitle = new Label("Cuối tuần (T7, CN)");
        lblWeekendTitle.setStyle("-fx-font-weight: bold;");
        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        Label lblWeekendVal = new Label(String.format("%.1f giờ / ngày", initWeekend));
        lblWeekendVal.getStyleClass().addAll("status-badge", "badge-neutral");
        weekendHeader.getChildren().addAll(lblWeekendTitle, sp2, lblWeekendVal);

        Slider sliderWeekend = new Slider(1.0, 10.0, initWeekend);
        sliderWeekend.setShowTickMarks(true);
        sliderWeekend.setShowTickLabels(true);
        sliderWeekend.setMajorTickUnit(1.0);
        sliderWeekend.setBlockIncrement(0.5);
        sliderWeekend.valueProperty().addListener((obs, oldVal, newVal) -> {
            double snapped = Math.round(newVal.doubleValue() * 2.0) / 2.0;
            lblWeekendVal.setText(String.format("%.1f giờ / ngày", snapped));
        });
        weekendBox.getChildren().addAll(weekendHeader, sliderWeekend);

        Label note = new Label("Khi thiết bị vượt quá hạn mức ngày, hệ thống tự động ngắt Internet và đồng bộ lên VPS.");
        note.setWrapText(true);
        note.getStyleClass().add("card-subtitle");

        Button btnSaveLimits = new Button("Lưu hạn mức", new FontIcon(MaterialDesignC.CONTENT_SAVE));
        btnSaveLimits.getStyleClass().add("accent");
        btnSaveLimits.setOnAction(e -> {
            double weekday = Math.round(sliderWeekday.getValue() * 2.0) / 2.0;
            double weekend = Math.round(sliderWeekend.getValue() * 2.0) / 2.0;
            dataService.updateDailyLimits(weekday, weekend);
            refreshTrackerList();
            toastHandler.show(
                    "Đã lưu hạn mức",
                    String.format("T2 - T6: %.1f giờ/ngày • T7 - CN: %.1f giờ/ngày", weekday, weekend),
                    ToastManager.ToastType.SUCCESS
            );
        });

        card.getChildren().addAll(titleRow, cardSub, new Separator(), weekdayBox, weekendBox, note, btnSaveLimits);
        return card;
    }

    private VBox createCurfewCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("fg-card");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = new FontIcon(MaterialDesignW.WEATHER_NIGHT);
        icon.setIconSize(20);
        Label cardTitle = new Label("Giờ giới nghiêm ban đêm (Bedtime Curfew)");
        cardTitle.getStyleClass().add("card-title");
        titleRow.getChildren().addAll(icon, cardTitle);

        Label cardSub = new Label("Tự động ngắt mạng từ 21:30 đến 06:00 sáng giúp trẻ ngủ đúng giờ.");
        cardSub.getStyleClass().add("card-subtitle");

        boolean initCurfewActive = true;
        String initStart = "21:30";
        String initEnd = "06:00";
        if (dataService.getTimeSchedules() != null && !dataService.getTimeSchedules().isEmpty()) {
            TimeSchedule ts = dataService.getTimeSchedules().get(0);
            initCurfewActive = ts.isActive();
            if (ts.getCurfewStart() != null && ts.getCurfewStart().contains(":")) initStart = ts.getCurfewStart();
            if (ts.getCurfewEnd() != null && ts.getCurfewEnd().contains(":")) initEnd = ts.getCurfewEnd();
        }

        ToggleSwitch toggleCurfew = new ToggleSwitch("Kích hoạt khóa mạng ban đêm tự động");
        toggleCurfew.setSelected(initCurfewActive);

        // Bộ chọn giờ bắt đầu & kết thúc
        HBox timeRow = new HBox(24);
        timeRow.setAlignment(Pos.CENTER_LEFT);

        String[] startParts = initStart.split(":");
        ComboBox<String> cbStartHour = createHourComboBox(startParts[0]);
        ComboBox<String> cbStartMin = createMinuteComboBox(startParts.length > 1 ? startParts[1] : "30");
        VBox startBox = new VBox(6,
                new Label("Bắt đầu khóa mạng:"),
                new HBox(6, cbStartHour, new Label(":"), cbStartMin)
        );

        String[] endParts = initEnd.split(":");
        ComboBox<String> cbEndHour = createHourComboBox(endParts[0]);
        ComboBox<String> cbEndMin = createMinuteComboBox(endParts.length > 1 ? endParts[1] : "00");
        VBox endBox = new VBox(6,
                new Label("Mở lại mạng sáng:"),
                new HBox(6, cbEndHour, new Label(":"), cbEndMin)
        );

        timeRow.getChildren().addAll(startBox, endBox);

        Label lblCurfewSummary = new Label();
        lblCurfewSummary.setWrapText(true);

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
                boolean isCurfewNow = toggleCurfew.isSelected() && (sTotal < eTotal
                        ? (nowMin >= sTotal && nowMin < eTotal)
                        : (nowMin >= sTotal || nowMin < eTotal));

                String durationText = String.format("Khung giờ khóa: %s:%s → %s:%s (%d giờ %02d phút)", sh, sm, eh, em, durH, durM);
                lblCurfewSummary.getStyleClass().removeAll("kpi-hint-danger", "kpi-hint-success");
                if (isCurfewNow) {
                    lblCurfewSummary.setText(durationText + " • ĐANG TRONG GIỜ GIỚI NGHIÊM");
                    lblCurfewSummary.getStyleClass().add("kpi-hint-danger");
                } else {
                    lblCurfewSummary.setText(durationText);
                    lblCurfewSummary.getStyleClass().add("kpi-hint-success");
                }
            } catch (Exception ignored) {
            }
        };

        cbStartHour.setOnAction(e -> updateSummary.run());
        cbStartMin.setOnAction(e -> updateSummary.run());
        cbEndHour.setOnAction(e -> updateSummary.run());
        cbEndMin.setOnAction(e -> updateSummary.run());
        toggleCurfew.selectedProperty().addListener((obs, o, n) -> updateSummary.run());
        updateSummary.run();

        // Preset nhanh
        HBox presetChips = new HBox(8);
        presetChips.setAlignment(Pos.CENTER_LEFT);
        Button chipDefault = createPresetChip("21:30 - 06:00 (Mặc định)", "21", "30", "06", "00", cbStartHour, cbStartMin, cbEndHour, cbEndMin, updateSummary);
        Button chipExam = createPresetChip("20:30 - 06:00 (Ôn thi)", "20", "30", "06", "00", cbStartHour, cbStartMin, cbEndHour, cbEndMin, updateSummary);
        Button chipWeekend = createPresetChip("22:30 - 07:00 (Cuối tuần)", "22", "30", "07", "00", cbStartHour, cbStartMin, cbEndHour, cbEndMin, updateSummary);
        presetChips.getChildren().addAll(chipDefault, chipExam, chipWeekend);

        Button btnSaveCurfew = new Button("Lưu giờ giới nghiêm", new FontIcon(MaterialDesignC.CONTENT_SAVE));
        btnSaveCurfew.getStyleClass().add("accent");
        btnSaveCurfew.setOnAction(e -> {
            boolean enabled = toggleCurfew.isSelected();
            String start = cbStartHour.getValue() + ":" + cbStartMin.getValue();
            String end = cbEndHour.getValue() + ":" + cbEndMin.getValue();
            dataService.updateCurfew(enabled, start, end);
            updateSummary.run();
            toastHandler.show(
                    enabled ? "Đã bật giờ giới nghiêm" : "Đã tắt giờ giới nghiêm",
                    enabled ? "Khóa mạng ban đêm từ " + start + " đến " + end + "." : "Chế độ giới nghiêm ban đêm đã tạm tắt.",
                    enabled ? ToastManager.ToastType.SUCCESS : ToastManager.ToastType.WARNING
            );
        });

        card.getChildren().addAll(titleRow, cardSub, new Separator(), toggleCurfew, timeRow, presetChips, lblCurfewSummary, btnSaveCurfew);
        return card;
    }

    private VBox createDeviceTimeTrackerCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("fg-card");

        HBox head = new HBox(12);
        head.setAlignment(Pos.CENTER_LEFT);

        DayOfWeek dow = LocalDate.now().getDayOfWeek();
        boolean isWeekend = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
        String dayDesc = isWeekend ? "Cuối tuần (T7 & CN)" : "Ngày trong tuần (T2 - T6)";

        VBox titleBox = new VBox(2);
        Label cardTitle = new Label("Tiến độ sử dụng hôm nay & Thưởng giờ");
        cardTitle.getStyleClass().add("card-title");
        Label cardSub = new Label("Hôm nay: " + dayDesc + " • Theo dõi trực tiếp qua ProgressBar và cộng thưởng +30 phút khi con hoàn thành bài tập.");
        cardSub.getStyleClass().add("card-subtitle");
        titleBox.getChildren().addAll(cardTitle, cardSub);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnBonusAll30 = new Button("+30 phút (Hoàn thành tốt bài tập)", new FontIcon(MaterialDesignG.GIFT_OUTLINE));
        btnBonusAll30.getStyleClass().add("success");
        btnBonusAll30.setOnAction(e -> {
            dataService.addBonusMinutesToAllDevices(30);
            refreshTrackerList();
            toastHandler.show("Đã thưởng +30 phút", "Cộng thêm 30 phút cho tất cả thiết bị của con!", ToastManager.ToastType.SUCCESS);
        });

        Button btnResetBonusAll = new Button("Đặt lại thưởng", new FontIcon(MaterialDesignR.REFRESH));
        btnResetBonusAll.setOnAction(e -> {
            dataService.resetAllBonusMinutes();
            refreshTrackerList();
            toastHandler.show("Đã đặt lại", "Giờ thưởng hôm nay của các thiết bị đã về 0.", ToastManager.ToastType.WARNING);
        });

        HBox globalActions = new HBox(8, btnBonusAll30, btnResetBonusAll);
        globalActions.setAlignment(Pos.CENTER_RIGHT);

        head.getChildren().addAll(titleBox, sp, globalActions);

        trackerList = new VBox(12);
        refreshTrackerList();

        dataService.getAccessLogs().addListener((ListChangeListener<AccessLog>) c -> Platform.runLater(this::refreshTrackerList));
        dataService.getDevices().addListener((ListChangeListener<Device>) c -> Platform.runLater(this::refreshTrackerList));

        card.getChildren().addAll(head, new Separator(), trackerList);
        return card;
    }

    private void refreshTrackerList() {
        if (trackerList == null) return;
        trackerList.getChildren().clear();

        if (dataService.getDevices().isEmpty()) {
            Label empty = new Label("Chưa có thiết bị nào được kết nối trong hệ thống.");
            empty.getStyleClass().add("card-subtitle");
            trackerList.getChildren().add(empty);
            return;
        }

        for (Device dev : dataService.getDevices()) {
            trackerList.getChildren().add(createDeviceTrackerRow(dev));
        }
    }

    private VBox createDeviceTrackerRow(Device dev) {
        VBox box = new VBox(10);
        box.getStyleClass().add("sub-card");
        box.setPadding(new Insets(14, 16, 14, 16));

        double spentHours = dataService.parseSpentHours(dev.getTimeSpentToday());
        double deviceLimitHours = dataService.getTodayDailyLimitHours(dev);
        double progress = Math.min(1.0, spentHours / Math.max(0.1, deviceLimitHours));
        boolean isExceeded = spentHours >= deviceLimitHours && deviceLimitHours > 0;
        boolean isWarning = !isExceeded && progress >= 0.85;
        int bonusMin = dev.getBonusMinutes();

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        FontIcon devIcon = new FontIcon(resolveDeviceIcon(dev.getType()));
        devIcon.setIconSize(20);

        Label lblName = new Label(dev.getName());
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label lblIp = new Label("[" + dev.getIpAddress() + "]");
        lblIp.getStyleClass().add("card-subtitle");

        topRow.getChildren().addAll(devIcon, lblName, lblIp);

        if (bonusMin > 0) {
            Label lblBonusBadge = new Label("+" + bonusMin + " phút thưởng");
            lblBonusBadge.getStyleClass().addAll("status-badge", "badge-success");
            topRow.getChildren().add(lblBonusBadge);
        }

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label lblStatusBadge = new Label();
        lblStatusBadge.getStyleClass().add("status-badge");
        if (isExceeded || dev.isBlocked()) {
            lblStatusBadge.setText("Hết giờ / Đã ngắt mạng");
            lblStatusBadge.getStyleClass().add("badge-danger");
        } else if (isWarning) {
            lblStatusBadge.setText("Sắp hết hạn mức");
            lblStatusBadge.getStyleClass().add("badge-warning");
        } else {
            lblStatusBadge.setText("Đang hoạt động");
            lblStatusBadge.getStyleClass().add("badge-success");
        }

        String timeStr = String.format("%s / %.1fh", dev.getTimeSpentToday(), deviceLimitHours);
        Label lblTime = new Label(timeStr);
        lblTime.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        topRow.getChildren().addAll(sp, lblStatusBadge, lblTime);

        // Thanh đo tiến độ ProgressBar
        ProgressBar bar = new ProgressBar(progress);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(10);
        if (isExceeded || dev.isBlocked()) {
            bar.setStyle("-fx-accent: #EF4444;");
        } else if (isWarning) {
            bar.setStyle("-fx-accent: #F59E0B;");
        } else {
            bar.setStyle("-fx-accent: #2563EB;");
        }

        // Hàng nút thưởng giờ cho thiết bị
        HBox actionRow = new HBox(8);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Label lblActionTitle = new Label("Thưởng giờ hôm nay:");
        lblActionTitle.getStyleClass().add("card-subtitle");

        Button btnPlus15 = new Button("+15 phút");
        btnPlus15.setOnAction(e -> applyBonusToDevice(dev, 15));

        Button btnPlus30 = new Button("+30 phút (Hoàn thành bài tập)", new FontIcon(MaterialDesignG.GIFT_OUTLINE));
        btnPlus30.getStyleClass().add("success");
        btnPlus30.setOnAction(e -> applyBonusToDevice(dev, 30));

        Button btnPlus60 = new Button("+1 giờ");
        btnPlus60.setOnAction(e -> applyBonusToDevice(dev, 60));

        Button btnCustomBonus = new Button("Tùy chọn...");
        btnCustomBonus.setOnAction(e -> showCustomBonusDialog(dev));

        actionRow.getChildren().addAll(lblActionTitle, btnPlus15, btnPlus30, btnPlus60, btnCustomBonus);

        if (bonusMin > 0) {
            Button btnResetDevBonus = new Button("Hủy thưởng");
            btnResetDevBonus.getStyleClass().add("danger");
            btnResetDevBonus.setOnAction(e -> {
                dataService.resetDeviceBonusMinutes(dev);
                refreshTrackerList();
                toastHandler.show("Đã hủy thưởng", "Đã đặt lại giờ thưởng của " + dev.getName(), ToastManager.ToastType.WARNING);
            });
            actionRow.getChildren().add(btnResetDevBonus);
        }

        box.getChildren().addAll(topRow, bar, actionRow);
        return box;
    }

    private void applyBonusToDevice(Device dev, int minutes) {
        dataService.addBonusMinutesToDevice(dev, minutes);
        double newLimit = dataService.getTodayDailyLimitHours(dev);
        refreshTrackerList();
        toastHandler.show(
                "Đã thưởng +" + minutes + " phút",
                String.format("Thiết bị %s có hạn mức mới hôm nay: %.1f giờ.", dev.getName(), newLimit),
                ToastManager.ToastType.SUCCESS
        );
    }

    private void showCustomBonusDialog(Device dev) {
        TextInputDialog dialog = new TextInputDialog("30");
        dialog.setTitle("Thưởng thêm thời gian");
        dialog.setHeaderText("Cộng thêm hạn mức sử dụng hôm nay cho " + dev.getName());
        dialog.setContentText("Nhập số phút muốn cộng thêm (1 - 720):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(val -> {
            try {
                int min = Integer.parseInt(val.trim());
                if (min > 0 && min <= 720) {
                    applyBonusToDevice(dev, min);
                } else {
                    toastHandler.show("Giá trị không hợp lệ", "Vui lòng nhập số phút từ 1 đến 720.", ToastManager.ToastType.WARNING);
                }
            } catch (NumberFormatException ex) {
                toastHandler.show("Giá trị không hợp lệ", "Vui lòng nhập một số nguyên hợp lệ.", ToastManager.ToastType.WARNING);
            }
        });
    }

    private ComboBox<String> createHourComboBox(String initialValue) {
        ComboBox<String> cb = new ComboBox<>();
        for (int i = 0; i < 24; i++) {
            cb.getItems().add(String.format("%02d", i));
        }
        cb.setValue(initialValue != null ? initialValue : "21");
        cb.setPrefWidth(76);
        return cb;
    }

    private ComboBox<String> createMinuteComboBox(String initialValue) {
        ComboBox<String> cb = new ComboBox<>();
        for (int i = 0; i < 60; i += 5) {
            cb.getItems().add(String.format("%02d", i));
        }
        cb.setValue(initialValue != null ? initialValue : "00");
        cb.setPrefWidth(76);
        return cb;
    }

    private Button createPresetChip(String text, String sh, String sm, String eh, String em,
                                    ComboBox<String> cbSh, ComboBox<String> cbSm,
                                    ComboBox<String> cbEh, ComboBox<String> cbEm, Runnable callback) {
        Button btn = new Button(text);
        btn.setOnAction(e -> {
            cbSh.setValue(sh);
            cbSm.setValue(sm);
            cbEh.setValue(eh);
            cbEm.setValue(em);
            callback.run();
        });
        return btn;
    }

    private Ikon resolveDeviceIcon(String type) {
        if (type == null) return MaterialDesignL.LAPTOP;
        return switch (type.toLowerCase()) {
            case "điện thoại" -> MaterialDesignC.CELLPHONE;
            case "máy tính bảng" -> MaterialDesignT.TABLET;
            case "máy tính để bàn" -> MaterialDesignD.DESKTOP_CLASSIC;
            default -> MaterialDesignL.LAPTOP;
        };
    }
}
