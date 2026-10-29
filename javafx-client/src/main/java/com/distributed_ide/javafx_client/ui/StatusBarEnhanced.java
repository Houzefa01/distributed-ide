package com.distributed_ide.javafx_client.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced Status Bar View with auto-save indicators and improved feedback
 */
public class StatusBarEnhanced {
    private static final Logger LOG = LoggerFactory.getLogger(StatusBarEnhanced.class);
    
    private final HBox bar;
    
    // Status indicators
    private final Label autosaveIndicator;
    private final Label savedIndicator;
    private final Label unsavedIndicator;
    private final Label lineColLabel;
    private final Label wordCountLabel;
    private final Label languageLabel;
    private final Label encodingLabel;
    
    private PauseTransition autoSaveTimer;
    private RotateTransition autoSaveSpinner;

    public StatusBarEnhanced() {
        bar = new HBox(12);
        bar.getStyleClass().add("status-bar-enhanced");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new javafx.geometry.Insets(0, 12, 0, 12));
        bar.setStyle("-fx-background-color: -ide-statusbar-bg; -fx-min-height: 28;");

        // Auto-save indicator (spinner)
        autosaveIndicator = createIndicator("🔄");
        autosaveIndicator.setStyle("-fx-font-size: 12;");
        autosaveIndicator.setVisible(false);

        // Saved indicator (checkmark)
        savedIndicator = createIndicator("✓");
        savedIndicator.setStyle("-fx-text-fill: #4caf50; -fx-font-size: 12;");
        savedIndicator.setVisible(false);

        // Unsaved indicator (dot)
        unsavedIndicator = createIndicator("●");
        unsavedIndicator.setStyle("-fx-text-fill: #ff9800; -fx-font-size: 8;");
        unsavedIndicator.setVisible(false);

        // Line:Column info
        lineColLabel = createStatusLabel("Ln 1, Col 1");

        // Word count
        wordCountLabel = createStatusLabel("Words: 0");

        // Language selector
        languageLabel = createClickableLabel("Java");
        languageLabel.getStyleClass().add("status-label-clickable");

        // Encoding
        encodingLabel = createStatusLabel("UTF-8");

        // Add items to bar
        bar.getChildren().addAll(
            autosaveIndicator,
            savedIndicator,
            unsavedIndicator,
            createSeparator(),
            lineColLabel,
            createSeparator(),
            wordCountLabel,
            createSeparator(),
            languageLabel,
            createSeparator(),
            encodingLabel,
            createRegion(),  // Spacer
            createStatusLabel("Ready")  // Ready indicator
        );

        HBox.setHgrow(createRegion(), Priority.ALWAYS);
    }

    public HBox getView() {
        return bar;
    }

    public void showAutoSaving() {
        Platform.runLater(() -> {
            autosaveIndicator.setVisible(true);
            unsavedIndicator.setVisible(false);
            savedIndicator.setVisible(false);
            
            // Start spinner animation
            if (autoSaveSpinner != null) {
                autoSaveSpinner.stop();
            }
            autoSaveSpinner = new RotateTransition(Duration.seconds(1), autosaveIndicator);
            autoSaveSpinner.setFromAngle(0);
            autoSaveSpinner.setToAngle(360);
            autoSaveSpinner.setCycleCount(-1);
            autoSaveSpinner.play();
        });
    }

    public void showSaved() {
        Platform.runLater(() -> {
            if (autoSaveSpinner != null) {
                autoSaveSpinner.stop();
            }
            autosaveIndicator.setVisible(false);
            savedIndicator.setVisible(true);
            unsavedIndicator.setVisible(false);

            // Fade out after 2 seconds
            if (autoSaveTimer != null) {
                autoSaveTimer.stop();
            }
            autoSaveTimer = new PauseTransition(Duration.seconds(2));
            autoSaveTimer.setOnFinished(e -> {
                FadeTransition fade = new FadeTransition(Duration.millis(500), savedIndicator);
                fade.setFromValue(1);
                fade.setToValue(0);
                fade.setOnFinished(ae -> savedIndicator.setVisible(false));
                fade.play();
            });
            autoSaveTimer.play();

            LOG.debug("File saved");
        });
    }

    public void showUnsaved() {
        Platform.runLater(() -> {
            if (autoSaveSpinner != null) {
                autoSaveSpinner.stop();
            }
            autosaveIndicator.setVisible(false);
            savedIndicator.setVisible(false);
            unsavedIndicator.setVisible(true);
        });
    }

    public void setSaveError(String errorMessage) {
        Platform.runLater(() -> {
            if (autoSaveSpinner != null) {
                autoSaveSpinner.stop();
            }
            autosaveIndicator.setVisible(false);
            Label errorLabel = createStatusLabel("✕ Save failed: " + errorMessage);
            errorLabel.setStyle("-fx-text-fill: #f44336;");
        });
    }

    public void setPosition(int line, int column) {
        Platform.runLater(() -> {
            lineColLabel.setText(String.format("Ln %d, Col %d", line, column));
        });
    }

    public void setLanguage(String language) {
        Platform.runLater(() -> {
            languageLabel.setText(language != null ? language : "Plain text");
        });
    }

    public void setEncoding(String encoding) {
        Platform.runLater(() -> {
            encodingLabel.setText(encoding != null ? encoding : "UTF-8");
        });
    }

    public void setWordCount(int words) {
        Platform.runLater(() -> {
            wordCountLabel.setText(String.format("Words: %d", words));
        });
    }

    // --- Utility Methods ---

    private Label createIndicator(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("status-indicator");
        label.setStyle("-fx-text-fill: -ide-statusbar-fg;");
        return label;
    }

    private Label createStatusLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("status-label");
        label.setStyle("-fx-text-fill: -ide-statusbar-fg; -fx-font-size: 11;");
        return label;
    }

    private Label createClickableLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("status-label-clickable");
        label.setStyle("-fx-text-fill: -ide-statusbar-fg; -fx-font-size: 11; -fx-cursor: hand;");
        label.setOnMouseEntered(e -> label.setStyle("-fx-text-fill: -ide-accent; -fx-cursor: hand;"));
        label.setOnMouseExited(e -> label.setStyle("-fx-text-fill: -ide-statusbar-fg;"));
        return label;
    }

    private Region createSeparator() {
        Region sep = new Region();
        sep.setPrefWidth(1);
        sep.setPrefHeight(16);
        sep.setStyle("-fx-background-color: rgba(255, 255, 255, 0.15);");
        return sep;
    }

    private Region createRegion() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}
