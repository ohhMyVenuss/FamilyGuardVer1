package org.example.desktopver1.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.desktopver1.model.AppPolicy;
import org.example.desktopver1.model.CategoryRule;
import org.example.desktopver1.model.Device;
import org.example.desktopver1.service.DataService;

import java.util.function.Consumer;

/**
 * Giao diện Bộ lọc nội dung (Content Filtering):
 * - Bật/Tắt các danh mục nhạy cảm (18+, Bạo lực, Cờ bạc, Game, Mạng xã hội, Video ngắn)
 * - Quản lý Blacklist (Tên miền chặn riêng) & Whitelist (Tên miền học tập ưu tiên)
 * - Tùy chọn SafeSearch và Family DNS an toàn
 */
public class ContentFilterView extends ScrollPane {

    private final DataService dataService;
    private final Consumer<String> toastNotifier;

    public ContentFilterView(DataService dataService, Consumer<String> toastNotifier) {
        this.dataService = dataService;
        this.toastNotifier = toastNotifier;

        setFitToWidth(true);
        setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        VBox contentBox = new VBox(24);
        contentBox.getStyleClass().add("view-container");

        // Tiêu đề
        contentBox.getChildren().add(createHeader());

        // 1. Quản lý 5 ứng dụng di động & thời lượng sử dụng
        contentBox.getChildren().add(createAppControlSection());

        // 2. Lọc theo danh mục nội dung
        contentBox.getChildren().add(createCategorySection());

        // 2. Hai cột: Blacklist và Whitelist
        HBox listsRow = new HBox(20);
        VBox blacklistCard = createBlacklistCard();
        VBox whitelistCard = createWhitelistCard();
        HBox.setHgrow(blacklistCard, Priority.ALWAYS);
        HBox.setHgrow(whitelistCard, Priority.ALWAYS);
        listsRow.getChildren().addAll(blacklistCard, whitelistCard);
        contentBox.getChildren().add(listsRow);

        // 3. Tùy chọn an toàn tìm kiếm & DNS
        contentBox.getChildren().add(createAdvancedOptionsCard());

        setContent(contentBox);
    }

    private VBox createHeader() {
        VBox box = new VBox(4);
        Label title = new Label("Bộ Lọc Nội Dung & An Toàn Web");
        title.getStyleClass().add("header-welcome");
        Label sub = new Label("Chủ động ngăn chặn các trang web độc hại, không phù hợp lứa tuổi và tạo môi trường mạng lành mạnh.");
        sub.getStyleClass().add("header-subtitle");
        box.getChildren().addAll(title, sub);
        return box;
    }

    private VBox createCategorySection() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");

        Label cardTitle = new Label("🛡️ Quy tắc lọc theo danh mục tự động (Category Filters)");
        cardTitle.getStyleClass().add("card-title");
        Label cardSub = new Label("Hệ thống tích hợp cơ sở dữ liệu nhận diện hàng triệu trang web độc hại");
        cardSub.getStyleClass().add("card-subtitle");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col1, col2);

        int index = 0;
        for (CategoryRule rule : dataService.getCategoryRules()) {
            HBox item = createCategoryItem(rule);
            grid.add(item, index % 2, index / 2);
            index++;
        }

        card.getChildren().addAll(cardTitle, cardSub, new Separator(), grid);
        return card;
    }

    private HBox createCategoryItem(CategoryRule rule) {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(12, 14, 12, 14));
        box.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 10px; -fx-border-color: #E2E8F0; -fx-border-radius: 10px;");

        Label lblIcon = new Label(rule.getIcon());
        lblIcon.setStyle("-fx-font-size: 22px;");

        VBox textGroup = new VBox(2);
        HBox.setHgrow(textGroup, Priority.ALWAYS);

        HBox nameAndCount = new HBox(8);
        nameAndCount.setAlignment(Pos.CENTER_LEFT);
        Label lblName = new Label(rule.getName());
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1E293B;");
        Label lblCount = new Label("Đã chặn: " + rule.getBlockedCount());
        lblCount.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B; -fx-background-color: #E2E8F0; -fx-padding: 2 6; -fx-background-radius: 6;");
        nameAndCount.getChildren().addAll(lblName, lblCount);

        Label lblDesc = new Label(rule.getDescription());
        lblDesc.setWrapText(true);
        lblDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        textGroup.getChildren().addAll(nameAndCount, lblDesc);

        CheckBox switchBlock = new CheckBox("Chặn");
        switchBlock.setSelected(rule.isBlocked());
        switchBlock.setStyle("-fx-font-weight: bold; -fx-text-fill: #DC2626;");
        switchBlock.setOnAction(e -> {
            dataService.toggleCategoryRule(rule);
            toastNotifier.accept((rule.isBlocked() ? "Đã bật chặn: " : "Đã cho phép: ") + rule.getName());
        });

        box.getChildren().addAll(lblIcon, textGroup, switchBlock);
        return box;
    }

    private VBox createBlacklistCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        Label cardTitle = new Label("⛔ Danh sách đen (Blacklist - Chặn tên miền)");
        cardTitle.getStyleClass().add("card-title");
        Label cardSub = new Label("Ngăn chặn hoàn toàn các địa chỉ web này trên mọi thiết bị của trẻ");
        cardSub.getStyleClass().add("card-subtitle");

        HBox inputRow = new HBox(10);
        TextField tfInput = new TextField();
        tfInput.setPromptText("VD: facebook.com, gamevui.vn...");
        HBox.setHgrow(tfInput, Priority.ALWAYS);

        Button btnAdd = new Button("Thêm chặn");
        btnAdd.getStyleClass().add("btn-danger");
        btnAdd.setOnAction(e -> {
            String val = tfInput.getText();
            if (val != null && !val.trim().isEmpty()) {
                dataService.addBlacklistDomain(val.trim());
                toastNotifier.accept("Đã thêm vào danh sách chặn: " + val.trim());
                tfInput.clear();
            }
        });

        inputRow.getChildren().addAll(tfInput, btnAdd);

        ListView<String> listView = new ListView<>(dataService.getBlacklistDomains());
        listView.setPrefHeight(180);
        listView.setCellFactory(lv -> new ListCell<String>() {
            private final Button btnDelete = new Button("Xóa");
            private final Label lblText = new Label();
            private final Region spacer = new Region();
            private final HBox row = new HBox(8, lblText, spacer, btnDelete);

            {
                row.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                btnDelete.getStyleClass().add("btn-sm-danger");
                btnDelete.setOnAction(evt -> {
                    String item = getItem();
                    if (item != null) {
                        dataService.removeBlacklistDomain(item);
                        toastNotifier.accept("Đã xóa khỏi danh sách chặn: " + item);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    lblText.setText("🚫 " + item);
                    setGraphic(row);
                }
            }
        });

        card.getChildren().addAll(cardTitle, cardSub, inputRow, listView);
        return card;
    }

    private VBox createWhitelistCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        Label cardTitle = new Label("✅ Danh sách trắng (Whitelist - Luôn cho phép)");
        cardTitle.getStyleClass().add("card-title");
        Label cardSub = new Label("Các trang học tập, giáo dục luôn mở kể cả trong giờ giới hạn");
        cardSub.getStyleClass().add("card-subtitle");

        HBox inputRow = new HBox(10);
        TextField tfInput = new TextField();
        tfInput.setPromptText("VD: olm.vn, khanacademy.org...");
        HBox.setHgrow(tfInput, Priority.ALWAYS);

        Button btnAdd = new Button("Thêm cho phép");
        btnAdd.getStyleClass().add("btn-primary");
        btnAdd.setOnAction(e -> {
            String val = tfInput.getText();
            if (val != null && !val.trim().isEmpty()) {
                dataService.addWhitelistDomain(val.trim());
                toastNotifier.accept("Đã thêm vào danh sách cho phép: " + val.trim());
                tfInput.clear();
            }
        });

        inputRow.getChildren().addAll(tfInput, btnAdd);

        ListView<String> listView = new ListView<>(dataService.getWhitelistDomains());
        listView.setPrefHeight(180);
        listView.setCellFactory(lv -> new ListCell<String>() {
            private final Button btnDelete = new Button("Xóa");
            private final Label lblText = new Label();
            private final Region spacer = new Region();
            private final HBox row = new HBox(8, lblText, spacer, btnDelete);

            {
                row.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                btnDelete.getStyleClass().add("btn-sm-danger");
                btnDelete.setOnAction(evt -> {
                    String item = getItem();
                    if (item != null) {
                        dataService.removeWhitelistDomain(item);
                        toastNotifier.accept("Đã gỡ khỏi danh sách cho phép: " + item);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    lblText.setText("🟢 " + item);
                    setGraphic(row);
                }
            }
        });

        card.getChildren().addAll(cardTitle, cardSub, inputRow, listView);
        return card;
    }

    private VBox createAdvancedOptionsCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        Label cardTitle = new Label("⚙️ Tùy chọn an toàn nâng cao");
        cardTitle.getStyleClass().add("card-title");

        HBox opt1 = new HBox(14);
        opt1.setAlignment(Pos.CENTER_LEFT);
        CheckBox cbSafeSearch = new CheckBox("Ép buộc Chế độ Tìm kiếm An toàn (SafeSearch)");
        cbSafeSearch.setSelected(dataService.isSafeSearchEnabled());
        cbSafeSearch.setStyle("-fx-font-weight: bold;");
        cbSafeSearch.setOnAction(e -> {
            dataService.safeSearchEnabledProperty().set(cbSafeSearch.isSelected());
            toastNotifier.accept("Chế độ SafeSearch (Google/YouTube/Bing) đã được " + (cbSafeSearch.isSelected() ? "BẬT" : "TẮT"));
        });
        Label lblSafeSearchDesc = new Label("Tự động lọc kết quả tìm kiếm hình ảnh và video không lành mạnh");
        lblSafeSearchDesc.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");
        opt1.getChildren().addAll(cbSafeSearch, new Separator(), lblSafeSearchDesc);

        HBox opt2 = new HBox(14);
        opt2.setAlignment(Pos.CENTER_LEFT);
        Label lblDns = new Label("Máy chủ DNS gia đình:");
        lblDns.setStyle("-fx-font-weight: bold;");
        ComboBox<String> cbDns = new ComboBox<>();
        cbDns.getItems().addAll("Cloudflare 1.1.1.3 (Chặn mã độc & Nội dung 18+)", "AdGuard Family Protection", "NextDNS Tùy biến gia đình", "OpenDNS FamilyShield");
        cbDns.setValue("Cloudflare 1.1.1.3 (Chặn mã độc & Nội dung 18+)");
        cbDns.setOnAction(e -> toastNotifier.accept("Đã chuyển DNS bảo vệ sang: " + cbDns.getValue()));
        opt2.getChildren().addAll(lblDns, cbDns);

        card.getChildren().addAll(cardTitle, new Separator(), opt1, opt2);
        return card;
    }

    private VBox createAppControlSection() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");

        // Header của Card
        HBox headerRow = new HBox(12);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label cardTitle = new Label("📱 Quản Lý Quyền Truy Cập & Lưu Lượng Thời Gian 5 Ứng Dụng");
        cardTitle.getStyleClass().add("card-title");
        Label cardSub = new Label("Kiểm soát trực tiếp qua WireGuard VPN: Cho phép/Chặn hoặc giới hạn thời gian sử dụng mỗi ngày cho từng thiết bị.");
        cardSub.getStyleClass().add("card-subtitle");
        titleBox.getChildren().addAll(cardTitle, cardSub);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Button btnResetAll = new Button("🔄 Đặt lại giờ tất cả App");
        btnResetAll.getStyleClass().add("btn-outline");
        btnResetAll.setStyle("-fx-font-size: 12px; -fx-padding: 6 12;");
        btnResetAll.setOnAction(e -> {
            dataService.resetAppUsage(null);
            Device cur = dataService.getSelectedFilterDevice();
            String devName = cur != null ? cur.getName() : "thiết bị";
            toastNotifier.accept("Đã đặt lại thời gian sử dụng hôm nay cho 5 ứng dụng trên " + devName + "!");
        });

        headerRow.getChildren().addAll(titleBox, btnResetAll);

        // Thanh chọn thiết bị đang theo dõi
        HBox deviceBar = new HBox(14);
        deviceBar.setAlignment(Pos.CENTER_LEFT);
        deviceBar.setPadding(new Insets(10, 16, 10, 16));
        deviceBar.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 10px; -fx-border-color: #E2E8F0; -fx-border-radius: 10px;");

        Label lblTarget = new Label("🎯 Chọn thiết bị theo dõi:");
        lblTarget.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B; -fx-font-size: 13px;");

        ComboBox<Device> cbDeviceSelector = new ComboBox<>(dataService.getDevices());
        cbDeviceSelector.setPrefWidth(350);
        cbDeviceSelector.setStyle("-fx-font-size: 12px;");

        javafx.util.Callback<ListView<Device>, ListCell<Device>> cellFactory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(Device d, boolean empty) {
                super.updateItem(d, empty);
                if (empty || d == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);

                    String icon = "10.0.0.2".equals(d.getIpAddress()) ? "📱" : ("Điện thoại".equalsIgnoreCase(d.getType()) ? "📱" : ("Máy tính bảng".equalsIgnoreCase(d.getType()) ? "📱" : "💻"));
                    Label lblIcon = new Label(icon);

                    Label lblName = new Label(d.getName());
                    lblName.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B;");

                    Label lblIp = new Label("[" + d.getIpAddress() + "]");
                    lblIp.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Label lblStatus = new Label("Trực tuyến".equalsIgnoreCase(d.getStatus()) ? "🟢 Online" : "⚪ Offline");
                    lblStatus.setStyle("Trực tuyến".equalsIgnoreCase(d.getStatus())
                            ? "-fx-text-fill: #16A34A; -fx-font-size: 10px; -fx-font-weight: bold; -fx-background-color: #DCFCE7; -fx-padding: 2 6; -fx-background-radius: 4;"
                            : "-fx-text-fill: #94A3B8; -fx-font-size: 10px; -fx-padding: 2 6;");

                    row.getChildren().addAll(lblIcon, lblName, lblIp, spacer, lblStatus);
                    setGraphic(row);
                }
            }
        };

        cbDeviceSelector.setCellFactory(cellFactory);
        cbDeviceSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Device d, boolean empty) {
                super.updateItem(d, empty);
                if (empty || d == null) {
                    setText("Chọn thiết bị theo dõi...");
                } else {
                    String isWg = "10.0.0.2".equals(d.getIpAddress()) ? " • WireGuard Tunnel" : "";
                    setText("📱 " + d.getName() + " (" + d.getIpAddress() + ")" + isWg);
                }
            }
        });

        Device initialDev = dataService.getSelectedFilterDevice();
        if (initialDev != null) {
            cbDeviceSelector.setValue(initialDev);
        } else if (!dataService.getDevices().isEmpty()) {
            cbDeviceSelector.setValue(dataService.getDevices().get(0));
            dataService.setSelectedFilterDevice(dataService.getDevices().get(0));
        }

        cbDeviceSelector.setOnAction(e -> {
            Device selected = cbDeviceSelector.getValue();
            if (selected != null && (dataService.getSelectedFilterDevice() == null || !selected.getId().equals(dataService.getSelectedFilterDevice().getId()))) {
                dataService.setSelectedFilterDevice(selected);
                toastNotifier.accept("Đã chuyển cấu hình 5 ứng dụng sang: " + selected.getName() + " (" + selected.getIpAddress() + ")");
            }
        });

        dataService.selectedFilterDeviceProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(cbDeviceSelector.getValue())) {
                cbDeviceSelector.setValue(newVal);
            }
        });

        Region barSpacer = new Region();
        HBox.setHgrow(barSpacer, Priority.ALWAYS);

        Label lblHint = new Label("💡 Cấu hình hạn mức và đếm giờ áp dụng riêng biệt cho từng thiết bị.");
        lblHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569; -fx-font-style: italic;");

        deviceBar.getChildren().addAll(lblTarget, cbDeviceSelector, barSpacer, lblHint);

        VBox appsList = new VBox(12);
        for (AppPolicy policy : dataService.getAppPolicies()) {
            appsList.getChildren().add(createAppItem(policy));
        }

        dataService.getAppPolicies().addListener((javafx.collections.ListChangeListener<AppPolicy>) c -> {
            appsList.getChildren().clear();
            for (AppPolicy policy : dataService.getAppPolicies()) {
                appsList.getChildren().add(createAppItem(policy));
            }
        });

        card.getChildren().addAll(headerRow, deviceBar, new Separator(), appsList);
        return card;
    }

    private Node createAppBrandBadge(String appId, String fallbackIcon) {
        StackPane badge = new StackPane();
        badge.setPrefSize(46, 46);
        badge.setMinSize(46, 46);
        badge.setMaxSize(46, 46);

        String id = appId != null ? appId.toUpperCase().trim() : "";
        Label lblSymbol = new Label();
        lblSymbol.setAlignment(Pos.CENTER);

        switch (id) {
            case "YOUTUBE":
                badge.setStyle("-fx-background-color: #FF0000; -fx-background-radius: 12px; -fx-effect: dropshadow(three-pass-box, rgba(255,0,0,0.35), 6, 0, 0, 2);");
                lblSymbol.setText("▶");
                lblSymbol.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 0 0 0 2;");
                break;
            case "FACEBOOK":
                badge.setStyle("-fx-background-color: #1877F2; -fx-background-radius: 12px; -fx-effect: dropshadow(three-pass-box, rgba(24,119,242,0.35), 6, 0, 0, 2);");
                lblSymbol.setText("f");
                lblSymbol.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 24px; -fx-font-weight: 900; -fx-font-family: 'Arial Black', 'Arial', sans-serif;");
                break;
            case "TIKTOK":
                badge.setStyle("-fx-background-color: #010101; -fx-background-radius: 12px; -fx-border-color: #00F2FE; -fx-border-width: 1.5px; -fx-border-radius: 12px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.45), 6, 0, 0, 2);");
                lblSymbol.setText("♪");
                lblSymbol.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 22px; -fx-font-weight: bold;");
                break;
            case "INSTAGRAM":
                badge.setStyle("-fx-background-color: linear-gradient(to bottom right, #833AB4, #FD1D1D, #FCB045); -fx-background-radius: 12px; -fx-effect: dropshadow(three-pass-box, rgba(253,29,29,0.35), 6, 0, 0, 2);");
                lblSymbol.setText("📷");
                lblSymbol.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 18px; -fx-font-weight: bold;");
                break;
            case "MLBB":
                badge.setStyle("-fx-background-color: linear-gradient(to bottom right, #0F172A, #1E293B); -fx-background-radius: 12px; -fx-border-color: #F59E0B; -fx-border-width: 1.5px; -fx-border-radius: 12px; -fx-effect: dropshadow(three-pass-box, rgba(245,158,11,0.35), 6, 0, 0, 2);");
                lblSymbol.setText("⚔");
                lblSymbol.setStyle("-fx-text-fill: #FBBF24; -fx-font-size: 20px; -fx-font-weight: bold;");
                break;
            default:
                badge.setStyle("-fx-background-color: #3B82F6; -fx-background-radius: 12px;");
                lblSymbol.setText(fallbackIcon != null && !fallbackIcon.isEmpty() ? fallbackIcon : "📱");
                lblSymbol.setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 16px;");
                break;
        }

        badge.getChildren().add(lblSymbol);
        return badge;
    }

    private HBox createAppItem(AppPolicy policy) {
        HBox box = new HBox(16);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(14, 16, 14, 16));
        box.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 12px; -fx-border-color: #E2E8F0; -fx-border-radius: 12px;");

        // 1. Icon & Tên ứng dụng
        HBox appInfo = new HBox(14);
        appInfo.setAlignment(Pos.CENTER_LEFT);
        appInfo.setPrefWidth(230);

        Node brandBadge = createAppBrandBadge(policy.getAppId(), policy.getIcon());

        VBox nameBox = new VBox(3);
        Label lblName = new Label(policy.getAppName());
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1E293B;");
        Label lblCat = new Label(policy.getCategory());
        lblCat.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        nameBox.getChildren().addAll(lblName, lblCat);

        appInfo.getChildren().addAll(brandBadge, nameBox);

        // 2. Thanh tiến độ sử dụng & Thời gian
        VBox progressBox = new VBox(6);
        HBox.setHgrow(progressBox, Priority.ALWAYS);

        HBox statusRow = new HBox(8);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        Label lblBadge = new Label();
        Label lblTimeDetail = new Label();
        lblTimeDetail.setStyle("-fx-font-size: 12px; -fx-text-fill: #334155;");

        statusRow.getChildren().addAll(lblBadge, lblTimeDetail);

        ProgressBar pbar = new ProgressBar(policy.getUsageProgress());
        pbar.setMaxWidth(Double.MAX_VALUE);
        pbar.setPrefHeight(10);

        Runnable updateVisuals = () -> {
            double prog = policy.getUsageProgress();
            pbar.setProgress(prog);

            if (policy.isBlocked()) {
                lblBadge.setText("🚫 ĐÃ CHẶN");
                lblBadge.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 6;");
                pbar.setStyle("-fx-accent: #DC2626;");
                lblTimeDetail.setText("Bố mẹ đã chặn hoàn toàn quyền truy cập");
            } else if (policy.isTimeExceeded()) {
                lblBadge.setText("⏳ HẾT GIỜ HÔM NAY");
                lblBadge.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #D97706; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 6;");
                pbar.setStyle("-fx-accent: #F59E0B;");
                lblTimeDetail.setText("Đã dùng: " + policy.getFormattedUsedTime() + " / Hạn mức: " + policy.getFormattedLimit());
            } else {
                lblBadge.setText("🟢 ĐANG HOẠT ĐỘNG");
                lblBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #16A34A; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 6;");
                pbar.setStyle(prog >= 0.75 ? "-fx-accent: #F59E0B;" : "-fx-accent: #10B981;");
                lblTimeDetail.setText("Đã dùng: " + policy.getFormattedUsedTime() + " / Hạn mức: " + policy.getFormattedLimit() + " (" + policy.getStatusDescription() + ")");
            }
        };

        updateVisuals.run();
        policy.usedSecondsProperty().addListener((obs, o, n) -> updateVisuals.run());
        policy.timeLimitMinutesProperty().addListener((obs, o, n) -> updateVisuals.run());
        policy.blockedProperty().addListener((obs, o, n) -> updateVisuals.run());
        policy.timeExceededProperty().addListener((obs, o, n) -> updateVisuals.run());

        progressBox.getChildren().addAll(statusRow, pbar);

        // 3. Tùy chọn Hạn mức thời gian & Nút chặn
        HBox controlBox = new HBox(12);
        controlBox.setAlignment(Pos.CENTER_RIGHT);

        VBox limitGroup = new VBox(2);
        limitGroup.setAlignment(Pos.CENTER_LEFT);
        Label lblLimitTitle = new Label("Hạn mức/ngày:");
        lblLimitTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        ComboBox<String> cbLimit = new ComboBox<>();
        cbLimit.getItems().addAll("15 phút", "30 phút", "45 phút", "60 phút (1h)", "90 phút (1.5h)", "120 phút (2h)", "180 phút (3h)", "Không giới hạn");
        cbLimit.setStyle("-fx-font-size: 12px;");

        int lim = policy.getTimeLimitMinutes();
        if (lim == 15) cbLimit.setValue("15 phút");
        else if (lim == 30) cbLimit.setValue("30 phút");
        else if (lim == 45) cbLimit.setValue("45 phút");
        else if (lim == 60) cbLimit.setValue("60 phút (1h)");
        else if (lim == 90) cbLimit.setValue("90 phút (1.5h)");
        else if (lim == 120) cbLimit.setValue("120 phút (2h)");
        else if (lim == 180) cbLimit.setValue("180 phút (3h)");
        else if (lim <= 0) cbLimit.setValue("Không giới hạn");
        else cbLimit.setValue(lim + " phút");

        cbLimit.setOnAction(e -> {
            String sel = cbLimit.getValue();
            int newMin = 60;
            if ("15 phút".equals(sel)) newMin = 15;
            else if ("30 phút".equals(sel)) newMin = 30;
            else if ("45 phút".equals(sel)) newMin = 45;
            else if ("60 phút (1h)".equals(sel)) newMin = 60;
            else if ("90 phút (1.5h)".equals(sel)) newMin = 90;
            else if ("120 phút (2h)".equals(sel)) newMin = 120;
            else if ("180 phút (3h)".equals(sel)) newMin = 180;
            else if ("Không giới hạn".equals(sel)) newMin = -1;
            dataService.setAppTimeLimit(policy, newMin);
            toastNotifier.accept("Đã đổi hạn mức " + policy.getAppName() + " thành: " + sel);
        });
        limitGroup.getChildren().addAll(lblLimitTitle, cbLimit);

        CheckBox cbBlock = new CheckBox("Chặn");
        cbBlock.setSelected(policy.isBlocked());
        cbBlock.setStyle("-fx-font-weight: bold; -fx-text-fill: #DC2626; -fx-font-size: 13px;");
        cbBlock.setOnAction(e -> {
            dataService.toggleAppBlock(policy);
            toastNotifier.accept((policy.isBlocked() ? "Đã bật chặn: " : "Đã cho phép: ") + policy.getAppName());
        });

        Button btnReset = new Button("🔄");
        btnReset.setTooltip(new Tooltip("Đặt lại thời gian sử dụng hôm nay về 0"));
        btnReset.getStyleClass().add("btn-outline");
        btnReset.setStyle("-fx-padding: 4 8; -fx-font-size: 11px;");
        btnReset.setOnAction(e -> {
            dataService.resetAppUsage(policy);
            toastNotifier.accept("Đã đặt lại thời gian hôm nay cho " + policy.getAppName());
        });

        controlBox.getChildren().addAll(limitGroup, cbBlock, btnReset);

        box.getChildren().addAll(appInfo, progressBox, controlBox);
        return box;
    }
}
