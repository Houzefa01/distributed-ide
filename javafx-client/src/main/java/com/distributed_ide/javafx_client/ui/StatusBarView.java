package com.distributed_ide.javafx_client.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * Status bar at the bottom of the IDE (VSCode-style).
 * Shows: connection state dot, collaboration status, git branch,
 * language, encoding, cursor position, and auto-fading notifications.
 */
public class StatusBarView {

    /** Connection state for the colored status dot. */
    public enum ConnectionState {
        CONNECTED, CONNECTING, DISCONNECTED, ERROR
    }

    private static final double NOTIFICATION_FADE_SECONDS = 4.0;

    private final HBox bar;

    private final Label connectionLabel;
    private final Label collabStatusLabel;
    private final Label branchLabel;
    private final Label cursorPositionLabel;
    private final Label languageLabel;
    private final Label encodingLabel;
    private final Label notificationLabel;

    private PauseTransition notificationTimer;

    public StatusBarView() {
        bar = new HBox();
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        // Connection indicator
        connectionLabel = createItem("●");
        connectionLabel.getStyleClass().add("connection-dot");
        setConnectionState(ConnectionState.DISCONNECTED);

        collabStatusLabel = createItem("Disconnected");
        branchLabel = createItem("main");
        notificationLabel = createItem("");
        notificationLabel.getStyleClass().add("status-notification");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        cursorPositionLabel = createItem("Ln 1, Col 1");
        languageLabel = createItem("Java");
        encodingLabel = createItem("UTF-8");

        bar.getChildren().addAll(
                createItemWithIcon("plug", connectionLabel),
                collabStatusLabel,
                createItemWithIcon("git-branch", branchLabel),
                notificationLabel,
                spacer,
                cursorPositionLabel,
                createItemWithIcon("code", languageLabel),
                encodingLabel
        );
    }

    public HBox getView() {
        return bar;
    }

    // ═════════════════════════════════════════════════════════════
    //  CONNECTION STATE
    // ═════════════════════════════════════════════════════════════

    public void setConnectionState(ConnectionState state) {
        Platform.runLater(() -> {
            connectionLabel.getStyleClass().removeAll(
                    "connected", "connecting",
                    "disconnected", "error");
            switch (state) {
                case CONNECTED -> connectionLabel.getStyleClass().add("connected");
                case CONNECTING -> connectionLabel.getStyleClass().add("connecting");
                case DISCONNECTED -> connectionLabel.getStyleClass().add("disconnected");
                case ERROR -> connectionLabel.getStyleClass().add("error");
            }
        });
    }

    public void setCollabStatus(String status) {
        Platform.runLater(() -> collabStatusLabel.setText(status != null ? status : "Déconnecté"));
    }

    public void setBranch(String branch) {
        Platform.runLater(() -> branchLabel.setText(branch != null ? branch : "—"));
    }

    public void setCursorPosition(int line, int col) {
        Platform.runLater(() -> cursorPositionLabel.setText("Ligne " + line + ", Colonne " + col));
    }

    public void setLanguage(String lang) {
        Platform.runLater(() -> languageLabel.setText(lang != null ? lang : "—"));
    }

    public void setEncoding(String encoding) {
        Platform.runLater(() -> encodingLabel.setText(encoding != null ? encoding : "UTF-8"));
    }

    // ═════════════════════════════════════════════════════════════
    //  NOTIFICATIONS (auto-fade)
    // ═════════════════════════════════════════════════════════════

    /**
     * Shows a notification that auto-fades after a few seconds.
     */
    public void setNotification(String text) {
        Platform.runLater(() -> {
            // Cancel any pending fade
            if (notificationTimer != null) notificationTimer.stop();

            notificationLabel.setText(text != null ? text : "");
            notificationLabel.setOpacity(1.0);
            notificationLabel.getStyleClass().remove("status-notification-saving");
            notificationLabel.getStyleClass().remove("status-notification-error");
            notificationLabel.getStyleClass().add("status-notification");

            if (text != null && !text.isEmpty()) {
                // Auto-fade after delay
                notificationTimer = new PauseTransition(Duration.seconds(NOTIFICATION_FADE_SECONDS));
                notificationTimer.setOnFinished(e -> {
                    FadeTransition fade = new FadeTransition(Duration.millis(800), notificationLabel);
                    fade.setFromValue(1.0);
                    fade.setToValue(0.0);
                    fade.setOnFinished(ev -> notificationLabel.setText(""));
                    fade.play();
                });
                notificationTimer.play();
            }
        });
    }

    /**
     * Shows "Saving..." with spinner animation during save operation.
     */
    public void showSaveInProgress(String filename) {
        Platform.runLater(() -> {
            // Cancel any pending fade
            if (notificationTimer != null) notificationTimer.stop();

            String displayName = filename != null ? java.nio.file.Paths.get(filename).getFileName().toString() : "file";
            notificationLabel.setText("⟳ Saving " + displayName + "...");
            notificationLabel.setOpacity(1.0);
            notificationLabel.getStyleClass().remove("status-notification");
            notificationLabel.getStyleClass().remove("status-notification-error");
            notificationLabel.getStyleClass().addAll("status-notification-saving", "spinner");
        });
    }

    /**
     * Shows "Saved: filename" confirmation message with auto-fade.
     */
    public void showSaveConfirmation(String filename) {
        Platform.runLater(() -> {
            // Cancel any pending fade
            if (notificationTimer != null) notificationTimer.stop();

            String displayName = filename != null ? java.nio.file.Paths.get(filename).getFileName().toString() : "file";
            notificationLabel.setText("✓ Saved: " + displayName);
            notificationLabel.setOpacity(1.0);
            notificationLabel.getStyleClass().remove("status-notification-saving");
            notificationLabel.getStyleClass().remove("status-notification-error");
            notificationLabel.getStyleClass().add("status-notification");

            // Auto-fade after shorter delay for save confirmation
            notificationTimer = new PauseTransition(Duration.seconds(NOTIFICATION_FADE_SECONDS * 0.75));
            notificationTimer.setOnFinished(e -> {
                FadeTransition fade = new FadeTransition(Duration.millis(600), notificationLabel);
                fade.setFromValue(1.0);
                fade.setToValue(0.0);
                fade.setOnFinished(ev -> {
                    notificationLabel.setText("");
                    notificationLabel.getStyleClass().removeAll("status-notification-saving", "spinner");
                });
                fade.play();
            });
            notificationTimer.play();
        });
    }

    /**
     * Shows error message when save fails with auto-fade.
     */
    public void showSaveError(String error) {
        Platform.runLater(() -> {
            // Cancel any pending fade
            if (notificationTimer != null) notificationTimer.stop();

            String errorMsg = error != null && !error.isEmpty() ? error : "Save failed";
            notificationLabel.setText("✕ " + errorMsg);
            notificationLabel.setOpacity(1.0);
            notificationLabel.getStyleClass().remove("status-notification-saving");
            notificationLabel.getStyleClass().remove("status-notification");
            notificationLabel.getStyleClass().addAll("status-notification-error", "spinner");

            // Longer delay for errors so user can see them
            notificationTimer = new PauseTransition(Duration.seconds(NOTIFICATION_FADE_SECONDS * 1.5));
            notificationTimer.setOnFinished(e -> {
                FadeTransition fade = new FadeTransition(Duration.millis(1000), notificationLabel);
                fade.setFromValue(1.0);
                fade.setToValue(0.0);
                fade.setOnFinished(ev -> {
                    notificationLabel.setText("");
                    notificationLabel.getStyleClass().removeAll("status-notification-error", "spinner");
                });
                fade.play();
            });
            notificationTimer.play();
        });
    }

    // ═════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════

    private Label createItem(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("status-bar-item");
        return label;
    }

    private HBox createItemWithIcon(String iconName, Label label) {
        Label icon = new Label();
        icon.getStyleClass().addAll("icon", "icon-" + iconName);
        HBox box = new HBox(icon, label);
        box.getStyleClass().add("status-bar-item-box");
        box.setAlignment(Pos.CENTER);
        return box;
    }
}

