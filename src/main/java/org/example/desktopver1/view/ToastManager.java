package org.example.desktopver1.view;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;

/**
 * Tiện ích hiển thị thông báo nổi (Non-blocking Toast Notification)
 * đặt tại góc dưới bên phải của Root StackPane, không cướp focus và không chặn thao tác chuột.
 */
public final class ToastManager {

    public enum ToastType {
        SUCCESS,
        WARNING,
        DANGER
    }

    @FunctionalInterface
    public interface ToastHandler {
        void show(String title, String message, ToastType type);
    }

    private static final String TOAST_CONTAINER_ID = "familyguard-toast-container";
    private static final int MAX_VISIBLE_TOASTS = 4;

    private ToastManager() {
    }

    public static void showToast(StackPane root, String title, String message, ToastType type) {
        if (root == null) {
            return;
        }
        Runnable task = () -> renderToast(root, title, message, type != null ? type : ToastType.SUCCESS);
        try {
            if (Platform.isFxApplicationThread()) {
                task.run();
            } else {
                Platform.runLater(task);
            }
        } catch (IllegalStateException ignored) {
            // Bỏ qua nếu JavaFX Runtime chưa khởi tạo (vd trong Unit Test)
        }
    }

    private static void renderToast(StackPane root, String title, String message, ToastType type) {
        VBox container = getOrCreateContainer(root);

        // Giới hạn số lượng toast hiển thị cùng lúc để tránh tràn màn hình
        while (container.getChildren().size() >= MAX_VISIBLE_TOASTS) {
            container.getChildren().remove(0);
        }

        HBox toast = new HBox(12);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setMouseTransparent(true);
        toast.setMaxWidth(380);
        toast.setMinWidth(280);
        toast.getStyleClass().addAll("toast-card", getTypeStyleClass(type));

        FontIcon icon = createIcon(type);
        icon.getStyleClass().add("toast-icon");

        VBox textGroup = new VBox(2);
        Label lblTitle = new Label(title != null && !title.isBlank() ? title : getDefaultTitle(type));
        lblTitle.getStyleClass().add("toast-title");

        Label lblMessage = new Label(message != null ? message : "");
        lblMessage.getStyleClass().add("toast-message");
        lblMessage.setWrapText(true);
        lblMessage.setMaxWidth(310);

        textGroup.getChildren().addAll(lblTitle, lblMessage);
        toast.getChildren().addAll(icon, textGroup);

        container.getChildren().add(toast);

        // Tự động mờ dần và biến mất sau 3.5 giây
        PauseTransition pause = new PauseTransition(Duration.seconds(3.5));
        pause.setOnFinished(e -> {
            FadeTransition fade = new FadeTransition(Duration.seconds(0.4), toast);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(ev -> container.getChildren().remove(toast));
            fade.play();
        });
        pause.play();
    }

    private static VBox getOrCreateContainer(StackPane root) {
        for (Node child : root.getChildren()) {
            if (TOAST_CONTAINER_ID.equals(child.getId()) && child instanceof VBox box) {
                box.toFront();
                return box;
            }
        }
        VBox container = new VBox(10);
        container.setId(TOAST_CONTAINER_ID);
        container.setAlignment(Pos.BOTTOM_RIGHT);
        container.setMouseTransparent(true);
        container.setPickOnBounds(false);
        container.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(container, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(container, new Insets(0, 20, 20, 0));
        root.getChildren().add(container);
        return container;
    }

    private static String getTypeStyleClass(ToastType type) {
        return switch (type) {
            case SUCCESS -> "toast-success";
            case WARNING -> "toast-warning";
            case DANGER -> "toast-danger";
        };
    }

    private static String getDefaultTitle(ToastType type) {
        return switch (type) {
            case SUCCESS -> "Thành công";
            case WARNING -> "Cảnh báo";
            case DANGER -> "Cảnh báo bảo mật";
        };
    }

    private static FontIcon createIcon(ToastType type) {
        FontIcon icon = switch (type) {
            case SUCCESS -> new FontIcon(MaterialDesignC.CHECK_CIRCLE);
            case WARNING -> new FontIcon(MaterialDesignA.ALERT);
            case DANGER -> new FontIcon(MaterialDesignS.SHIELD_ALERT);
        };
        icon.setIconSize(22);
        return icon;
    }
}
