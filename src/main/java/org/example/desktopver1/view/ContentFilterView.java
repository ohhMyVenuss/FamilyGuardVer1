package org.example.desktopver1.view;

import atlantafx.base.controls.ToggleSwitch;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.desktopver1.model.AppPolicy;
import org.example.desktopver1.model.CategoryRule;
import org.example.desktopver1.model.Device;
import org.example.desktopver1.service.DataService;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignG;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignY;

import java.util.function.Consumer;

/**
 * Màn hình Bộ lọc Web & App (Rules & AI):
 * - Phần 1: Các thẻ công tắc bật/tắt nhanh theo nhóm ứng dụng phổ biến (TikTok, YouTube, Facebook, Game Online)
 * - Phần 2: Form thêm tên miền chặn thủ công + danh sách tên miền đã chặn có thanh tìm kiếm (Search Bar) lọc trực tiếp
 * - Phần 3: Quy tắc lọc theo danh mục & Danh sách trắng (Whitelist)
 */
public class ContentFilterView extends ScrollPane {

    private final DataService dataService;
    private final ToastManager.ToastHandler toastHandler;

    private GridPane quickAppGrid;
    private ComboBox<Device> cbDeviceSelector;

    public ContentFilterView(DataService dataService, Consumer<String> legacyToast, ToastManager.ToastHandler toastHandler) {
        this.dataService = dataService;
        this.toastHandler = toastHandler;

        setFitToWidth(true);
        getStyleClass().add("view-scroll");
        SmoothScrollHelper.applySmoothScrolling(this);

        VBox contentBox = new VBox(20);
        contentBox.getStyleClass().add("view-container");

        contentBox.getChildren().add(createHeader());
        contentBox.getChildren().add(createQuickAppTogglesCard());
        contentBox.getChildren().add(createDomainFilterSection());
        contentBox.getChildren().add(createCategorySection());

        setContent(contentBox);

        dataService.getAppPolicies().addListener((ListChangeListener<AppPolicy>) c -> Platform.runLater(this::rebuildQuickAppGrid));
    }

    private VBox createHeader() {
        VBox box = new VBox(4);
        Label title = new Label("Bộ lọc Web & App (Rules & AI)");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Bật/tắt nhanh các nhóm ứng dụng phổ biến và quản lý danh sách tên miền chặn thủ công.");
        sub.getStyleClass().add("page-subtitle");
        box.getChildren().addAll(title, sub);
        return box;
    }

    private VBox createQuickAppTogglesCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("fg-card");

        HBox headerRow = new HBox(12);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = new FontIcon(MaterialDesignA_APPS());
        icon.setIconSize(20);

        VBox titleBox = new VBox(2);
        Label cardTitle = new Label("Chặn nhanh theo nhóm ứng dụng phổ biến");
        cardTitle.getStyleClass().add("card-title");
        Label cardSub = new Label("Gạt công tắc để chặn/mở truy cập tức thì và đồng bộ qua TCP Socket xuống VPS.");
        cardSub.getStyleClass().add("card-subtitle");
        titleBox.getChildren().addAll(cardTitle, cardSub);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        // Bộ chọn thiết bị áp dụng
        cbDeviceSelector = new ComboBox<>(dataService.getDevices());
        cbDeviceSelector.setPrefWidth(260);
        cbDeviceSelector.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Device d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? null : d.getName() + " (" + d.getIpAddress() + ")");
            }
        });
        cbDeviceSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Device d, boolean empty) {
                super.updateItem(d, empty);
                setText(empty || d == null ? "Chọn thiết bị..." : d.getName() + " (" + d.getIpAddress() + ")");
            }
        });
        if (dataService.getSelectedFilterDevice() != null) {
            cbDeviceSelector.setValue(dataService.getSelectedFilterDevice());
        } else if (!dataService.getDevices().isEmpty()) {
            cbDeviceSelector.setValue(dataService.getDevices().get(0));
        }
        cbDeviceSelector.setOnAction(e -> {
            Device selected = cbDeviceSelector.getValue();
            if (selected != null) {
                dataService.setSelectedFilterDevice(selected);
                rebuildQuickAppGrid();
            }
        });

        headerRow.getChildren().addAll(icon, titleBox, cbDeviceSelector);

        quickAppGrid = new GridPane();
        quickAppGrid.setHgap(14);
        quickAppGrid.setVgap(14);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(50);
            quickAppGrid.getColumnConstraints().add(col);
        }

        rebuildQuickAppGrid();

        card.getChildren().addAll(headerRow, new Separator(), quickAppGrid);
        return card;
    }

    private Ikon MaterialDesignA_APPS() {
        return MaterialDesignS.SHIELD_LOCK;
    }

    private void rebuildQuickAppGrid() {
        if (quickAppGrid == null) return;
        quickAppGrid.getChildren().clear();

        quickAppGrid.add(createQuickAppCard("TikTok", "Video ngắn & Giải trí", "TIKTOK", MaterialDesignM.MUSIC_NOTE), 0, 0);
        quickAppGrid.add(createQuickAppCard("YouTube", "Video trực tuyến", "YOUTUBE", MaterialDesignY.YOUTUBE), 1, 0);
        quickAppGrid.add(createQuickAppCard("Facebook", "Mạng xã hội", "FACEBOOK", MaterialDesignF.FACEBOOK), 0, 1);
        quickAppGrid.add(createQuickAppCard("Game Online", "Trò chơi trực tuyến (MLBB / Web Game)", "MLBB", MaterialDesignG.GAMEPAD_VARIANT), 1, 1);
    }

    private HBox createQuickAppCard(String title, String subtitle, String appId, Ikon iconCode) {
        AppPolicy matchedPolicy = findAppPolicy(appId);
        boolean initialBlocked = matchedPolicy != null && matchedPolicy.isBlocked();

        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("app-toggle-card");
        if (initialBlocked) {
            card.getStyleClass().add("app-toggle-card-blocked");
        }

        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(24);

        VBox textGroup = new VBox(2);
        HBox.setHgrow(textGroup, Priority.ALWAYS);
        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label lblSub = new Label(subtitle);
        lblSub.getStyleClass().add("card-subtitle");
        textGroup.getChildren().addAll(lblTitle, lblSub);

        ToggleSwitch toggle = new ToggleSwitch("Chặn");
        toggle.setSelected(initialBlocked);
        toggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (matchedPolicy != null && matchedPolicy.isBlocked() != newVal) {
                dataService.toggleAppBlock(matchedPolicy);
            }
            if ("MLBB".equalsIgnoreCase(appId)) {
                // Đồng bộ thêm danh mục Game Online nếu có
                for (CategoryRule rule : dataService.getCategoryRules()) {
                    if (rule.getName() != null && rule.getName().toLowerCase().contains("game") && rule.isBlocked() != newVal) {
                        dataService.toggleCategoryRule(rule);
                    }
                }
            }
            card.getStyleClass().remove("app-toggle-card-blocked");
            if (newVal) {
                card.getStyleClass().add("app-toggle-card-blocked");
            }
            toastHandler.show(
                    newVal ? "Đã bật chặn " + title : "Đã mở khóa " + title,
                    newVal ? "Đã gửi lệnh chặn " + title + " xuống VPS." : "Đã cho phép truy cập " + title + ".",
                    newVal ? ToastManager.ToastType.WARNING : ToastManager.ToastType.SUCCESS
            );
        });

        card.getChildren().addAll(icon, textGroup, toggle);
        return card;
    }

    private AppPolicy findAppPolicy(String appId) {
        for (AppPolicy p : dataService.getAppPolicies()) {
            if (p.getAppId() != null && p.getAppId().equalsIgnoreCase(appId)) {
                return p;
            }
        }
        return null;
    }

    private HBox createDomainFilterSection() {
        HBox row = new HBox(20);
        VBox blacklistCard = createBlacklistCard();
        VBox whitelistCard = createWhitelistCard();
        HBox.setHgrow(blacklistCard, Priority.ALWAYS);
        HBox.setHgrow(whitelistCard, Priority.ALWAYS);
        row.getChildren().addAll(blacklistCard, whitelistCard);
        return row;
    }

    private VBox createBlacklistCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("fg-card");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = new FontIcon(MaterialDesignS.SHIELD_ALERT);
        icon.setIconSize(18);
        Label cardTitle = new Label("Tên miền bị chặn (Blacklist)");
        cardTitle.getStyleClass().add("card-title");
        titleRow.getChildren().addAll(icon, cardTitle);

        Label cardSub = new Label("Thêm tên miền chặn thủ công và tìm kiếm trực tiếp trong danh sách.");
        cardSub.getStyleClass().add("card-subtitle");

        // Form thêm tên miền chặn thủ công
        HBox addRow = new HBox(10);
        TextField tfAdd = new TextField();
        tfAdd.setPromptText("Nhập tên miền cần chặn (VD: tiktok.com, 88bet.vn)...");
        HBox.setHgrow(tfAdd, Priority.ALWAYS);

        Button btnAdd = new Button("Thêm chặn");
        btnAdd.setGraphic(new FontIcon(MaterialDesignP.PLUS));
        btnAdd.getStyleClass().add("accent");
        btnAdd.setOnAction(e -> {
            String val = tfAdd.getText();
            if (val != null && !val.trim().isEmpty()) {
                dataService.addBlacklistDomain(val.trim());
                toastHandler.show("Đã thêm tên miền chặn", "Đã gửi lệnh ADD_BLACKLIST: " + val.trim(), ToastManager.ToastType.SUCCESS);
                tfAdd.clear();
            } else {
                toastHandler.show("Thiếu thông tin", "Vui lòng nhập tên miền cần chặn.", ToastManager.ToastType.WARNING);
            }
        });
        addRow.getChildren().addAll(tfAdd, btnAdd);

        // Thanh tìm kiếm (Search Bar) lọc trực tiếp
        TextField tfSearch = new TextField();
        tfSearch.setPromptText("Tìm kiếm tên miền đã chặn...");
        FilteredList<String> filteredBlacklist = new FilteredList<>(dataService.getBlacklistDomains(), s -> true);
        tfSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = newVal == null ? "" : newVal.trim().toLowerCase();
            filteredBlacklist.setPredicate(domain -> query.isEmpty() || domain.toLowerCase().contains(query));
        });

        ListView<String> listView = new ListView<>(filteredBlacklist);
        listView.setPrefHeight(220);
        listView.setCellFactory(lv -> new ListCell<>() {
            private final Label lblDomain = new Label();
            private final Region spacer = new Region();
            private final Button btnRemove = new Button("Gỡ", new FontIcon(MaterialDesignD.DELETE_OUTLINE));
            private final HBox cellRow = new HBox(10, new FontIcon(MaterialDesignC.CLOSE_CIRCLE_OUTLINE), lblDomain, spacer, btnRemove);

            {
                cellRow.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                btnRemove.getStyleClass().add("danger");
                btnRemove.setOnAction(evt -> {
                    String item = getItem();
                    if (item != null) {
                        dataService.removeBlacklistDomain(item);
                        toastHandler.show("Đã gỡ tên miền", "Đã xóa khỏi danh sách chặn: " + item, ToastManager.ToastType.SUCCESS);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    lblDomain.setText(item);
                    setGraphic(cellRow);
                }
            }
        });

        card.getChildren().addAll(titleRow, cardSub, addRow, tfSearch, listView);
        return card;
    }

    private VBox createWhitelistCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("fg-card");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = new FontIcon(MaterialDesignC.CHECK_DECAGRAM);
        icon.setIconSize(18);
        Label cardTitle = new Label("Tên miền ưu tiên (Whitelist)");
        cardTitle.getStyleClass().add("card-title");
        titleRow.getChildren().addAll(icon, cardTitle);

        Label cardSub = new Label("Các trang học tập, giáo dục luôn được phép truy cập.");
        cardSub.getStyleClass().add("card-subtitle");

        HBox addRow = new HBox(10);
        TextField tfAdd = new TextField();
        tfAdd.setPromptText("VD: olm.vn, khanacademy.org...");
        HBox.setHgrow(tfAdd, Priority.ALWAYS);

        Button btnAdd = new Button("Thêm");
        btnAdd.setGraphic(new FontIcon(MaterialDesignP.PLUS));
        btnAdd.setOnAction(e -> {
            String val = tfAdd.getText();
            if (val != null && !val.trim().isEmpty()) {
                dataService.addWhitelistDomain(val.trim());
                toastHandler.show("Đã thêm Whitelist", "Luôn cho phép: " + val.trim(), ToastManager.ToastType.SUCCESS);
                tfAdd.clear();
            }
        });
        addRow.getChildren().addAll(tfAdd, btnAdd);

        ListView<String> listView = new ListView<>(dataService.getWhitelistDomains());
        listView.setPrefHeight(264);
        listView.setCellFactory(lv -> new ListCell<>() {
            private final Label lblDomain = new Label();
            private final Region spacer = new Region();
            private final Button btnRemove = new Button("Xóa", new FontIcon(MaterialDesignD.DELETE_OUTLINE));
            private final HBox cellRow = new HBox(10, new FontIcon(MaterialDesignC.CHECK_CIRCLE_OUTLINE), lblDomain, spacer, btnRemove);

            {
                cellRow.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                btnRemove.setOnAction(evt -> {
                    String item = getItem();
                    if (item != null) {
                        dataService.removeWhitelistDomain(item);
                        toastHandler.show("Đã gỡ Whitelist", "Đã xóa: " + item, ToastManager.ToastType.WARNING);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    lblDomain.setText(item);
                    setGraphic(cellRow);
                }
            }
        });

        card.getChildren().addAll(titleRow, cardSub, addRow, listView);
        return card;
    }

    private VBox createCategorySection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("fg-card");

        Label cardTitle = new Label("Quy tắc lọc theo danh mục (AI & DNS Categories)");
        cardTitle.getStyleClass().add("card-title");
        Label cardSub = new Label("Tự động chặn các nhóm nội dung nhạy cảm trên toàn hệ thống.");
        cardSub.getStyleClass().add("card-subtitle");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(50);
            grid.getColumnConstraints().add(col);
        }

        int idx = 0;
        for (CategoryRule rule : dataService.getCategoryRules()) {
            HBox item = new HBox(12);
            item.setAlignment(Pos.CENTER_LEFT);
            item.getStyleClass().add("sub-card");

            VBox text = new VBox(2);
            HBox.setHgrow(text, Priority.ALWAYS);
            Label lblName = new Label(rule.getName());
            lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
            Label lblDesc = new Label(rule.getDescription());
            lblDesc.getStyleClass().add("card-subtitle");
            lblDesc.setWrapText(true);
            text.getChildren().addAll(lblName, lblDesc);

            ToggleSwitch toggle = new ToggleSwitch();
            toggle.setSelected(rule.isBlocked());
            toggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (rule.isBlocked() != newVal) {
                    dataService.toggleCategoryRule(rule);
                    toastHandler.show(
                            "Cập nhật danh mục",
                            (newVal ? "Đã bật chặn: " : "Đã tắt chặn: ") + rule.getName(),
                            newVal ? ToastManager.ToastType.WARNING : ToastManager.ToastType.SUCCESS
                    );
                }
            });

            item.getChildren().addAll(text, toggle);
            grid.add(item, idx % 2, idx / 2);
            idx++;
        }

        card.getChildren().addAll(cardTitle, cardSub, new Separator(), grid);
        return card;
    }
}
