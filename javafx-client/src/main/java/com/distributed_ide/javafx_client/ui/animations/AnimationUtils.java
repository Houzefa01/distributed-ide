package com.distributed_ide.javafx_client.ui.animations;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Smooth animation utilities for IDE UI
 */
public class AnimationUtils {

    /**
     * Fade in animation
     */
    public static void fadeIn(Node node, Duration duration) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(duration, node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    /**
     * Fade out animation
     */
    public static void fadeOut(Node node, Duration duration, Runnable onFinished) {
        FadeTransition ft = new FadeTransition(duration, node);
        ft.setFromValue(1);
        ft.setToValue(0);
        ft.setOnFinished(e -> {
            if (onFinished != null) onFinished.run();
        });
        ft.play();
    }

    /**
     * Scale animation (hover effect)
     */
    public static void scaleOnHover(Node node, double scale) {
        node.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), node);
            st.setByX(scale - 1);
            st.setByY(scale - 1);
            st.play();
        });
        
        node.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), node);
            st.setToX(1);
            st.setToY(1);
            st.play();
        });
    }

    /**
     * Slide in animation
     */
    public static void slideIn(Node node, double fromX, Duration duration) {
        node.setTranslateX(fromX);
        TranslateTransition tt = new TranslateTransition(duration, node);
        tt.setToX(0);
        tt.play();
    }

    /**
     * Slide out animation
     */
    public static void slideOut(Node node, double toX, Duration duration, Runnable onFinished) {
        TranslateTransition tt = new TranslateTransition(duration, node);
        tt.setToX(toX);
        tt.setOnFinished(e -> {
            if (onFinished != null) onFinished.run();
        });
        tt.play();
    }

    /**
     * Glow effect on hover
     */
    public static void glowOnHover(Node node, Color color) {
        node.setOnMouseEntered(e -> {
            DropShadow glow = new DropShadow();
            glow.setColor(color);
            glow.setRadius(10);
            glow.setSpread(0.5);
            node.setEffect(glow);
        });
        
        node.setOnMouseExited(e -> {
            node.setEffect(null);
        });
    }

    /**
     * Pulse animation
     */
    public static void pulse(Node node, Duration duration, int count) {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.millis(0), new KeyValue(node.opacityProperty(), 1)),
            new KeyFrame(duration.divide(2), new KeyValue(node.opacityProperty(), 0.6)),
            new KeyFrame(duration, new KeyValue(node.opacityProperty(), 1))
        );
        timeline.setCycleCount(count);
        timeline.play();
    }

    /**
     * Bounce animation
     */
    public static void bounce(Node node, Duration duration) {
        double originalY = node.getTranslateY();
        
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.millis(0), new KeyValue(node.translateYProperty(), originalY)),
            new KeyFrame(duration.multiply(0.25), new KeyValue(node.translateYProperty(), originalY - 10)),
            new KeyFrame(duration.multiply(0.5), new KeyValue(node.translateYProperty(), originalY)),
            new KeyFrame(duration.multiply(0.75), new KeyValue(node.translateYProperty(), originalY - 5)),
            new KeyFrame(duration, new KeyValue(node.translateYProperty(), originalY))
        );
        timeline.play();
    }

    /**
     * Rotation animation
     */
    public static void rotate(Node node, double fromAngle, double toAngle, Duration duration) {
        RotateTransition rt = new RotateTransition(duration, node);
        rt.setFromAngle(fromAngle);
        rt.setToAngle(toAngle);
        rt.play();
    }

    /**
     * Smooth color transition
     */
    public static void colorTransition(javafx.scene.control.Labeled node, 
                                       Color fromColor, Color toColor, Duration duration) {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(node.textFillProperty(), fromColor)),
            new KeyFrame(duration, new KeyValue(node.textFillProperty(), toColor))
        );
        timeline.play();
    }

    /**
     * Smooth width transition using scale
     */
    public static void widthTransition(Node node, double fromWidth, double toWidth, Duration duration) {
        // Use scaleX instead of prefWidthProperty as it's more universally available
        double scaleFrom = fromWidth / node.getBoundsInLocal().getWidth();
        double scaleTo = toWidth / node.getBoundsInLocal().getWidth();
        
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(node.scaleXProperty(), scaleFrom)),
            new KeyFrame(duration, new KeyValue(node.scaleXProperty(), scaleTo))
        );
        timeline.play();
    }

    /**
     * Spring animation effect
     */
    public static void spring(Node node, Duration duration) {
        double originalX = node.getTranslateX();
        
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.millis(0), new KeyValue(node.translateXProperty(), originalX - 5)),
            new KeyFrame(duration.multiply(0.3), new KeyValue(node.translateXProperty(), originalX + 8)),
            new KeyFrame(duration.multiply(0.6), new KeyValue(node.translateXProperty(), originalX - 4)),
            new KeyFrame(duration.multiply(0.8), new KeyValue(node.translateXProperty(), originalX + 2)),
            new KeyFrame(duration, new KeyValue(node.translateXProperty(), originalX))
        );
        timeline.play();
    }
}
