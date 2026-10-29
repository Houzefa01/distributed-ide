package com.distributed_ide.javafx_client.ui.accessibility;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accessibility and UX improvements for the IDE
 * - ARIA-like labels for screen readers
 * - Better keyboard navigation
 * - High contrast support
 * - Better focus indicators
 */
public class AccessibilityManager {
    private static final Logger LOG = LoggerFactory.getLogger(AccessibilityManager.class);

    /**
     * Add accessible description to a UI element
     */
    public static void setAccessibleText(Node node, String text) {
        if (node == null) return;
        node.setAccessibleText(text);
        node.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
        LOG.debug("Set accessible text: {} for {}", text, node.getClass().getSimpleName());
    }

    /**
     * Add accessible help/tooltip to a button
     */
    public static void setButtonAccessible(Button button, String label, String description) {
        button.setText(label);
        button.setAccessibleText(label);
        button.setAccessibleHelp(description);
        button.setAccessibleRole(javafx.scene.AccessibleRole.BUTTON);
        LOG.debug("Button {} is now accessible", label);
    }

    /**
     * Make a text field accessible with proper labeling
     */
    public static void setTextFieldAccessible(TextField field, String label, String placeholder) {
        field.setPromptText(placeholder);
        field.setAccessibleText(label);
        // Note: TEXT_INPUT role is not available in this JavaFX version
        field.setStyle(field.getStyle() + 
            "; -fx-border-width: 2;" +
            "-fx-focus-color: -ide-accent;" +
            "-fx-faint-focus-color: -ide-accent;");
        LOG.debug("TextField {} is now accessible", label);
    }

    /**
     * Make a text area accessible
     */
    public static void setTextAreaAccessible(TextArea area, String label) {
        area.setAccessibleText(label);
        // Note: TEXT_INPUT role is not available in this JavaFX version
        area.setStyle(area.getStyle() + 
            "; -fx-border-width: 2;" +
            "-fx-focus-color: -ide-accent;" +
            "-fx-faint-focus-color: -ide-accent;");
        LOG.debug("TextArea {} is now accessible", label);
    }

    /**
     * Set focus color for better visibility
     */
    public static void improveInputFocusVisibility(Node node) {
        String focusStyle = "-fx-focus-color: -ide-accent; " +
                           "-fx-faint-focus-color: -ide-accent; " +
                           "-fx-focus-traversable: true;";
        node.setStyle(node.getStyle() + focusStyle);
    }

    /**
     * Set up keyboard navigation
     */
    public static void setupKeyboardNavigation(Node node, Runnable onEnter) {
        node.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                if (onEnter != null) {
                    onEnter.run();
                }
                e.consume();
            }
        });
    }

    /**
     * Add visual focus indicator
     */
    public static void emphasizeOnFocus(Node node) {
        node.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                // Add glow effect when focused
                javafx.scene.effect.DropShadow glow = new javafx.scene.effect.DropShadow();
                glow.setColor(javafx.scene.paint.Color.web("#00bcd4"));
                glow.setRadius(5);
                node.setEffect(glow);
            } else {
                node.setEffect(null);
            }
        });
    }

    /**
     * Set high contrast colors
     */
    public static void applyHighContrast(Node node) {
        node.getStyleClass().remove("default");
        node.getStyleClass().add("high-contrast");
        LOG.debug("Applied high contrast to {}", node.getClass().getSimpleName());
    }

    /**
     * Improve color contrast for text
     */
    public static void ensureColorContrast(Label label, javafx.scene.paint.Color background) {
        // Calculate luminance and choose appropriate text color
        double backgroundLuminance = calculateLuminance(background);
        
        if (backgroundLuminance > 0.5) {
            label.setStyle(label.getStyle() + "; -fx-text-fill: #000000;");
        } else {
            label.setStyle(label.getStyle() + "; -fx-text-fill: #ffffff;");
        }
    }

    /**
     * Calculate relative luminance of a color for WCAG contrast calculation
     */
    private static double calculateLuminance(javafx.scene.paint.Color color) {
        double r = Math.pow(color.getRed(), 2.2);
        double g = Math.pow(color.getGreen(), 2.2);
        double b = Math.pow(color.getBlue(), 2.2);
        return (0.2126 * r + 0.7152 * g + 0.0722 * b);
    }

    /**
     * Set accessible role for containers
     */
    public static void setContainerAccessible(Node container, String name) {
        container.setAccessibleText(name);
        // Note: PANE role is not available in this JavaFX version
    }

    /**
     * Add tooltip with keyboard shortcut hint
     */
    public static void addTooltipWithShortcut(Node node, String description, String shortcut) {
        javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(
            description + (shortcut != null ? " (" + shortcut + ")" : "")
        );
        tooltip.setStyle("-fx-font-size: 11; -fx-text-fill: -ide-fg-primary;");
        javafx.scene.control.Tooltip.install(node, tooltip);
    }
}
