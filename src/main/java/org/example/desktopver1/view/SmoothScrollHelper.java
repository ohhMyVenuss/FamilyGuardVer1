package org.example.desktopver1.view;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

/**
 * Tiện ích nâng cao trải nghiệm UX mượt mà cho ScrollPane và Slider trong JavaFX:
 * - Smooth inertial scrolling cho ScrollPane thay vì cuộn giật cục mặc định
 * - Continuous drag + progress fill + eased snap-to-step cho Slider
 */
public final class SmoothScrollHelper {

    private SmoothScrollHelper() {
    }

    /**
     * Kích hoạt hiệu ứng cuộn mượt (eased smooth scroll) cho ScrollPane.
     */
    public static void applySmoothScrolling(ScrollPane scrollPane) {
        if (scrollPane == null) {
            return;
        }
        final double[] targetV = {scrollPane.getVvalue()};
        final boolean[] animating = {false};

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double current = scrollPane.getVvalue();
                double diff = targetV[0] - current;
                if (Math.abs(diff) < 0.0005) {
                    scrollPane.setVvalue(targetV[0]);
                    animating[0] = false;
                    stop();
                } else {
                    scrollPane.setVvalue(current + diff * 0.22);
                }
            }
        };

        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() == 0 || scrollPane.getContent() == null) {
                return;
            }
            double contentHeight = scrollPane.getContent().getBoundsInLocal().getHeight();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double scrollableHeight = contentHeight - viewportHeight;
            if (scrollableHeight <= 0) {
                return;
            }

            if (!animating[0]) {
                targetV[0] = scrollPane.getVvalue();
            }

            // Bước cuộn mượt tỉ lệ với chiều cao nội dung
            double step = (-event.getDeltaY() * 2.6) / scrollableHeight;
            targetV[0] = Math.max(0.0, Math.min(1.0, targetV[0] + step));

            if (!animating[0]) {
                animating[0] = true;
                timer.start();
            }
            event.consume();
        });
    }

    /**
     * Nâng cấp Slider với dải màu tiến độ theo giá trị thực và hiệu ứng trượt mượt về bước 0.5 khi thả chuột.
     */
    public static void enhanceSlider(Slider slider, Label valueLabel, String unitSuffix) {
        if (slider == null) {
            return;
        }
        slider.getStyleClass().add("fg-slider");

        Runnable updateVisuals = () -> {
            double val = slider.getValue();
            double snapped = Math.round(val * 2.0) / 2.0;
            if (valueLabel != null) {
                valueLabel.setText(String.format("%.1f %s", snapped, unitSuffix));
            }
            double pct = ((val - slider.getMin()) / Math.max(0.001, slider.getMax() - slider.getMin())) * 100.0;
            pct = Math.max(0.0, Math.min(100.0, pct));
            Node track = slider.lookup(".track");
            if (track != null) {
                track.setStyle(String.format(
                        "-fx-background-color: linear-gradient(to right, #2563EB 0%%, #2563EB %.2f%%, #E2E8F0 %.2f%%, #E2E8F0 100%%);",
                        pct, pct
                ));
            }
        };

        slider.valueProperty().addListener((obs, o, n) -> updateVisuals.run());
        slider.skinProperty().addListener((obs, o, n) -> Platform.runLater(updateVisuals));
        Platform.runLater(updateVisuals);

        // Khi thả chuột: trượt mượt về bước 0.5 gần nhất
        slider.setOnMouseReleased(e -> {
            double snapped = Math.round(slider.getValue() * 2.0) / 2.0;
            Timeline snapAnim = new Timeline(
                    new KeyFrame(Duration.millis(130),
                            new KeyValue(slider.valueProperty(), snapped, Interpolator.EASE_OUT))
            );
            snapAnim.play();
        });
    }
}
