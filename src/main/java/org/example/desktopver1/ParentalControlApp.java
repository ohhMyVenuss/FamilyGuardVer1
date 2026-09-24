package org.example.desktopver1;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.desktopver1.controller.MainController;

import java.net.URL;

/**
 * FamilyGuard - Ứng dụng Quản lý & Giám sát Truy cập Internet dành cho Cha Mẹ
 * Điểm khởi chạy chính của ứng dụng Desktop JavaFX.
 */
public class ParentalControlApp extends Application {

    private static final int DEFAULT_WIDTH = 1180;
    private static final int DEFAULT_HEIGHT = 740;
    private static final int MIN_WIDTH = 980;
    private static final int MIN_HEIGHT = 620;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Khởi tạo Controller điều phối giao diện
        MainController mainController = new MainController();

        // Tạo Scene chính
        Scene scene = new Scene(mainController.getRoot(), DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // Nạp stylesheet giao diện hiện đại
        URL cssResource = getClass().getResource("/org/example/desktopver1/css/style.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }

        // Cấu hình cửa sổ Stage
        primaryStage.setTitle("FamilyGuard - Hệ Thống Quản Lý Truy Cập Internet Trẻ Em Dành Cho Cha Mẹ");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        primaryStage.centerOnScreen();

        primaryStage.show();
    }
}