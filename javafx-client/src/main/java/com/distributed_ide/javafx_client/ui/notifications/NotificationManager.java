package com.distributed_ide.javafx_client.ui.notifications;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Toast notification system for IDE feedback.
 * Shows temporary notifications for user actions: save success, errors, warnings
 */
public class NotificationManager {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationManager.class);
    
    private final VBox notificationContainer;
    private static final double NOTIFICATION_WIDTH = 400;
    private static final double ANIMATION_DURATION = 300;

    public NotificationManager(VBox container) {
        this.notificationContainer = container;
        this.notificationContainer.setStyle("-fx-spacing: 8; -fx-padding: 8;");
    }

    /**
     * Show success notification
     */
    public void showSuccess(String message) {
        showNotification(message, NotificationType.SUCCESS);
    }

    /**
     * Show error notification
     */
    public void showError(String message) {
        showNotification(message, NotificationType.ERROR);
    }

    /**
     * Show warning notification
     */
    public void showWarning(String message) {
        showNotification(message, NotificationType.WARNING);
    }

    /**
     * Show info notification
     */
    public void showInfo(String message) {
        showNotification(message, NotificationType.INFO);
    }

    private void showNotification(String message, NotificationType type) {
        HBox notif = createNotificationBox(message, type);
        notificationContainer.getChildren().add(notif);

        // Fade in
        FadeTransition fadeIn = new FadeTransition(Duration.millis(ANIMATION_DURATION), notif);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        // Auto-fade out after 4 seconds
        PauseTransition pause = new PauseTransition(Duration.seconds(4));
        pause.setOnFinished(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(ANIMATION_DURATION), notif);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(ae -> notificationContainer.getChildren().remove(notif));
            fadeOut.play();
        });
        pause.play();

        LOG.debug("Notification: {} - {}", type, message);
    }

    private HBox createNotificationBox(String message, NotificationType type) {
        HBox box = new HBox();
        box.setStyle(String.format(
            "-fx-background-color: %s; " +
            "-fx-border-color: %s; " +
            "-fx-border-width: 1; " +
            "-fx-border-radius: 4; " +
            "-fx-padding: 10 14; " +
            "-fx-spacing: 10; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0, 0, 2);",
            type.backgroundColor,
            type.borderColor
        ));
        box.setPrefWidth(NOTIFICATION_WIDTH);
        box.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label(type.icon);
        icon.setStyle("-fx-font-size: 16;");

        Label content = new Label(message);
        content.setStyle(String.format(
            "-fx-text-fill: %s; " +
            "-fx-font-size: 12; " +
            "-fx-wrap-text: true;",
            type.textColor
        ));

        box.getChildren().addAll(icon, content);
        HBox.setHgrow(content, javafx.scene.layout.Priority.ALWAYS);

        return box;
    }

    public enum NotificationType {
        SUCCESS("✓", "#4caf50", "#388e3c", "#ffffff"),
        ERROR("✕", "#f44336", "#c62828", "#ffffff"),
        WARNING("⚠", "#ff9800", "#e65100", "#ffffff"),
        INFO("ℹ", "#2196f3", "#1565c0", "#ffffff");

        public final String icon;
        public final String backgroundColor;
        public final String borderColor;
        public final String textColor;

        NotificationType(String icon, String backgroundColor, String borderColor, String textColor) {
            this.icon = icon;
            this.backgroundColor = backgroundColor;
            this.borderColor = borderColor;
            this.textColor = textColor;
        }
    }
}
