package org.example.desktopver1.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.desktopver1.model.CategoryRule;
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

        // 1. Lọc theo danh mục nội dung
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
}
