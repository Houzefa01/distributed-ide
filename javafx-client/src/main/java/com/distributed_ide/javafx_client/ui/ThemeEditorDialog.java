package com.distributed_ide.javafx_client.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Advanced Theme Editor dialog with multiple color pickers.
 *
 * Features:
 * - Create/edit custom themes
 * - 10+ color properties for comprehensive theming
 * - Live preview in separate area
 * - ScrollPane for many color controls
 * - Save/Cancel/Reset buttons
 * - Professional color organization
 *
 * Color Categories:
 * 1. Background & Text
 *    - editorBackground, editorForeground
 * 2. Syntax Highlighting
 *    - keywordColor, stringColor, numberColor, commentColor
 * 3. UI Elements
 *    - buttonBackground, buttonForeground, buttonHover
 * 4. Accents
 *    - accentColor, lineNumberColor
 */
public class ThemeEditorDialog extends Dialog<Map<String, String>> {

    private static final Logger LOG = LoggerFactory.getLogger(ThemeEditorDialog.class);

    private final String themeName;
    private final Map<String, Color> colors;
    private final Map<String, ColorPanel> colorPanels = new LinkedHashMap<>();

    public ThemeEditorDialog(String themeName, Map<String, String> initialColors) {
        this.themeName = themeName;
        this.colors = parseColors(initialColors);
        initDialog();
    }

    private void initDialog() {
        setTitle("Theme Editor");
        setHeaderText("Customize theme: " + themeName);
        setResizable(true);

        // ── Theme name input ──────────────────────────────────────────
        HBox nameBox = new HBox(10);
        nameBox.setPadding(new Insets(10));
        Label nameLabel = new Label("Theme Name:");
        nameLabel.setPrefWidth(120);
        TextField nameField = new TextField(themeName);
        nameField.setPrefWidth(200);
        nameBox.getChildren().addAll(nameLabel, nameField);

        // ── Color panels in scrollable area ───────────────────────────
        VBox colorContainer = createColorPanels();

        ScrollPane scrollPane = new ScrollPane(colorContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(500);
        scrollPane.setStyle("-fx-control-inner-background: #f5f5f5");

        // ── Preview area ──────────────────────────────────────────────
        TextArea preview = createPreviewArea();

        // ── Split for large screens ───────────────────────────────────
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        splitPane.getItems().addAll(scrollPane, preview);
        splitPane.setDividerPositions(0.6);

        // ── Main layout ───────────────────────────────────────────────
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(nameBox, new Separator(), splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        DialogPane dialogPane = getDialogPane();
        dialogPane.setContent(content);
        dialogPane.setPrefWidth(900);
        dialogPane.setPrefHeight(700);

        // ── Buttons ───────────────────────────────────────────────────
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType resetType = new ButtonType("Reset to Default", ButtonBar.ButtonData.OTHER);

        dialogPane.getButtonTypes().addAll(saveType, cancelType, resetType);

        setResultConverter(buttonType -> {
            if (buttonType == saveType) {
                return exportColors();
            }
            return null;
        });

        // ── Reset button handler ──────────────────────────────────────
        Button resetButton = (Button) dialogPane.lookupButton(resetType);
        resetButton.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Reset Theme");
            confirm.setHeaderText("Reset all colors to default?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                resetToDefaults();
            }
        });

        // ── Wire preview updates ──────────────────────────────────────
        for (ColorPanel panel : colorPanels.values()) {
            panel.setOnColorChanged(color -> updatePreview(preview));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  COLOR PANEL CREATION
    // ═══════════════════════════════════════════════════════════════════

    private VBox createColorPanels() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(10));

        // ── Background & Text ─────────────────────────────────────────
        Label bgTextLabel = new Label("Background & Text");
        bgTextLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13");
        container.getChildren().add(bgTextLabel);

        addColorPanel("editorBackground", "Editor Background", Color.web("#1e1e1e"));
        addColorPanel("editorForeground", "Editor Text Color", Color.web("#e0e0e0"));
        addColorPanel("lineNumberBackground", "Line Number Background", Color.web("#252526"));
        addColorPanel("lineNumberColor", "Line Number Color", Color.web("#858585"));

        // ── Syntax Highlighting ───────────────────────────────────────
        container.getChildren().add(new Separator());
        Label syntaxLabel = new Label("Syntax Highlighting");
        syntaxLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13");
        container.getChildren().add(syntaxLabel);

        addColorPanel("keywordColor", "Keywords (if, for, class, etc.)", Color.web("#569cd6"));
        addColorPanel("stringColor", "Strings", Color.web("#ce9178"));
        addColorPanel("commentColor", "Comments", Color.web("#6a9955"));
        addColorPanel("numberColor", "Numbers", Color.web("#b5cea8"));
        addColorPanel("operatorColor", "Operators", Color.web("#d4d4d4"));

        // ── UI Elements ───────────────────────────────────────────────
        container.getChildren().add(new Separator());
        Label uiLabel = new Label("UI Elements");
        uiLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13");
        container.getChildren().add(uiLabel);

        addColorPanel("buttonBackground", "Button Background", Color.web("#3e3e42"));
        addColorPanel("buttonForeground", "Button Text", Color.web("#cccccc"));
        addColorPanel("buttonHover", "Button Hover", Color.web("#52ad9e"));
        addColorPanel("accentColor", "Accent Color", Color.web("#007acc"));

        // ── Add all panels to container ───────────────────────────────
        for (ColorPanel panel : colorPanels.values()) {
            container.getChildren().add(panel);
        }

        return container;
    }

    private void addColorPanel(String colorKey, String displayName, Color defaultColor) {
        Color color = colors.getOrDefault(colorKey, defaultColor);
        ColorPanel panel = new ColorPanel(displayName, color);
        colorPanels.put(colorKey, panel);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PREVIEW AREA
    // ═══════════════════════════════════════════════════════════════════

    private TextArea createPreviewArea() {
        TextArea preview = new TextArea();
        preview.setWrapText(false);
        preview.setPrefWidth(300);
        preview.setEditable(false);
        preview.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #e0e0e0");

        String sampleCode = "// Sample code preview\n" +
                "public class Example {\n" +
                "    private String name = \"theme\";\n" +
                "    \n" +
                "    public void highlight() {\n" +
                "        int count = 42; // comment\n" +
                "        System.out.println(\"colors\");\n" +
                "    }\n" +
                "}";

        preview.setText(sampleCode);
        updatePreview(preview);

        return preview;
    }

    private void updatePreview(TextArea preview) {
        // Build CSS for the preview area based on current color selections
        String bgColor = colorPanels.get("editorBackground").getColorHex();
        String fgColor = colorPanels.get("editorForeground").getColorHex();

        String css = String.format(
                "-fx-control-inner-background: %s; -fx-text-fill: %s",
                bgColor, fgColor
        );
        preview.setStyle(css);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  COLOR MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    private Map<String, Color> parseColors(Map<String, String> hexColors) {
        Map<String, Color> result = new LinkedHashMap<>();
        if (hexColors != null) {
            for (Map.Entry<String, String> entry : hexColors.entrySet()) {
                try {
                    result.put(entry.getKey(), Color.web(entry.getValue()));
                } catch (IllegalArgumentException e) {
                    LOG.warn("Invalid color hex: {}", entry.getValue());
                }
            }
        }
        return result;
    }

    private Map<String, String> exportColors() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, ColorPanel> entry : colorPanels.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getColorHex());
        }
        LOG.info("Theme exported with {} colors", result.size());
        return result;
    }

    private void resetToDefaults() {
        colorPanels.get("editorBackground").setColor(Color.web("#1e1e1e"));
        colorPanels.get("editorForeground").setColor(Color.web("#e0e0e0"));
        colorPanels.get("lineNumberBackground").setColor(Color.web("#252526"));
        colorPanels.get("lineNumberColor").setColor(Color.web("#858585"));
        colorPanels.get("keywordColor").setColor(Color.web("#569cd6"));
        colorPanels.get("stringColor").setColor(Color.web("#ce9178"));
        colorPanels.get("commentColor").setColor(Color.web("#6a9955"));
        colorPanels.get("numberColor").setColor(Color.web("#b5cea8"));
        colorPanels.get("operatorColor").setColor(Color.web("#d4d4d4"));
        colorPanels.get("buttonBackground").setColor(Color.web("#3e3e42"));
        colorPanels.get("buttonForeground").setColor(Color.web("#cccccc"));
        colorPanels.get("buttonHover").setColor(Color.web("#52ad9e"));
        colorPanels.get("accentColor").setColor(Color.web("#007acc"));

        LOG.info("Theme reset to defaults");
    }

    public String getThemeName() {
        return themeName;
    }
}
