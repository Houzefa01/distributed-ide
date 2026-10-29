package com.distributed_ide.javafx_client.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.function.Consumer;

/**
 * Professional color picker panel with RGB sliders and hex input.
 *
 * Features:
 * - RGB slider controls (0-255 each)
 * - Hex color input (#RRGGBB format)
 * - Live color preview
 * - Two-way binding (sliders ↔ hex)
 * - Optional alpha/opacity slider
 * - Callback on color change
 *
 * Usage:
 *   ColorPanel panel = new ColorPanel("Background", Color.web("#1e1e1e"));
 *   panel.setOnColorChanged(color -> applyThemeColor(color));
 *   pane.getChildren().add(panel);
 */
public class ColorPanel extends VBox {

    private final String colorName;
    private Color currentColor;
    private Consumer<Color> onColorChanged;

    // Controls
    private Slider redSlider;
    private Slider greenSlider;
    private Slider blueSlider;
    private Slider alphaSlider;

    private Spinner<Integer> redSpinner;
    private Spinner<Integer> greenSpinner;
    private Spinner<Integer> blueSpinner;
    private Spinner<Integer> alphaSpinner;

    private TextField hexField;
    private Rectangle colorPreview;

    private boolean isUpdating = false; // Prevent recursive updates

    public ColorPanel(String colorName, Color initialColor) {
        this.colorName = colorName;
        this.currentColor = initialColor;
        initUI();
    }

    private void initUI() {
        setSpacing(10);
        setPadding(new Insets(10));
        setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 4");

        // ─ Title ─────────────────────────────────────────────────────
        Label titleLabel = new Label(colorName);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12");

        // ─ Hex input ──────────────────────────────────────────────────
        HBox hexBox = new HBox(8);
        hexBox.setStyle("-fx-alignment: center-left");
        Label hexLabel = new Label("Hex:");
        hexLabel.setPrefWidth(60);
        hexField = new TextField();
        hexField.setPrefWidth(120);
        hexField.setText(colorToHex(currentColor));
        hexField.setOnAction(e -> hexToColor());
        hexBox.getChildren().addAll(hexLabel, hexField);

        // ─ Color preview ──────────────────────────────────────────────
        colorPreview = new Rectangle(40, 40);
        colorPreview.setFill(currentColor);
        colorPreview.setStroke(javafx.scene.paint.Color.BLACK);
        colorPreview.setStrokeWidth(1);

        HBox previewBox = new HBox(8);
        previewBox.setStyle("-fx-alignment: center-left");
        Label previewLabel = new Label("Preview:");
        previewLabel.setPrefWidth(60);
        previewBox.getChildren().addAll(previewLabel, colorPreview);

        // ─ RGB Sliders ────────────────────────────────────────────────
        VBox rgbBox = new VBox(8);
        rgbBox.setPadding(new Insets(10, 0, 10, 0));

        // Red slider
        HBox redBox = createSliderBox("Red:", 0, 255);
        redSlider = (Slider) ((HBox) redBox.getChildren().get(1)).getChildren().get(0);
        redSpinner = (Spinner<Integer>) ((HBox) redBox.getChildren().get(1)).getChildren().get(1);
        redSlider.setValue(currentColor.getRed() * 255);
        redSpinner.getValueFactory().setValue((int) (currentColor.getRed() * 255));

        // Green slider
        HBox greenBox = createSliderBox("Green:", 0, 255);
        greenSlider = (Slider) ((HBox) greenBox.getChildren().get(1)).getChildren().get(0);
        greenSpinner = (Spinner<Integer>) ((HBox) greenBox.getChildren().get(1)).getChildren().get(1);
        greenSlider.setValue(currentColor.getGreen() * 255);
        greenSpinner.getValueFactory().setValue((int) (currentColor.getGreen() * 255));

        // Blue slider
        HBox blueBox = createSliderBox("Blue:", 0, 255);
        blueSlider = (Slider) ((HBox) blueBox.getChildren().get(1)).getChildren().get(0);
        blueSpinner = (Spinner<Integer>) ((HBox) blueBox.getChildren().get(1)).getChildren().get(1);
        blueSlider.setValue(currentColor.getBlue() * 255);
        blueSpinner.getValueFactory().setValue((int) (currentColor.getBlue() * 255));

        // Alpha slider (opacity)
        HBox alphaBox = createSliderBox("Alpha:", 0, 255);
        alphaSlider = (Slider) ((HBox) alphaBox.getChildren().get(1)).getChildren().get(0);
        alphaSpinner = (Spinner<Integer>) ((HBox) alphaBox.getChildren().get(1)).getChildren().get(1);
        alphaSlider.setValue(currentColor.getOpacity() * 255);
        alphaSpinner.getValueFactory().setValue((int) (currentColor.getOpacity() * 255));

        // ─ Wire slider and spinner listeners ──────────────────────────
        setupSliderListener(redSlider, redSpinner);
        setupSliderListener(greenSlider, greenSpinner);
        setupSliderListener(blueSlider, blueSpinner);
        setupSliderListener(alphaSlider, alphaSpinner);
        setupSpinnerListener(redSpinner, redSlider);
        setupSpinnerListener(greenSpinner, greenSlider);
        setupSpinnerListener(blueSpinner, blueSlider);
        setupSpinnerListener(alphaSpinner, alphaSlider);

        rgbBox.getChildren().addAll(redBox, greenBox, blueBox, alphaBox);

        // ─ Assembly ───────────────────────────────────────────────────
        getChildren().addAll(
                titleLabel,
                hexBox,
                previewBox,
                new Separator(),
                rgbBox
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    //  COMPONENT CREATION
    // ═══════════════════════════════════════════════════════════════════

    private HBox createSliderBox(String label, int min, int max) {
        HBox box = new HBox(8);
        box.setStyle("-fx-alignment: center-left");

        Label labelNode = new Label(label);
        labelNode.setPrefWidth(60);

        Slider slider = new Slider(min, max, (min + max) / 2.0);
        slider.setPrefWidth(150);
        slider.setShowTickLabels(false);
        slider.setShowTickMarks(false);

        Spinner<Integer> spinner = new Spinner<>(min, max, 128);
        spinner.setPrefWidth(70);
        spinner.setEditable(true);

        HBox controlBox = new HBox(8);
        controlBox.getChildren().addAll(slider, spinner);

        box.getChildren().addAll(labelNode, controlBox);
        return box;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  EVENT LISTENERS
    // ═══════════════════════════════════════════════════════════════════

    private void setupSliderListener(Slider slider, Spinner<Integer> spinner) {
        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!isUpdating) {
                spinner.getValueFactory().setValue(newVal.intValue());
                updateColor();
            }
        });
    }

    private void setupSpinnerListener(Spinner<Integer> spinner, Slider slider) {
        spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!isUpdating && newVal != null) {
                slider.setValue(newVal);
                updateColor();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //  COLOR UPDATES
    // ═══════════════════════════════════════════════════════════════════

    private void updateColor() {
        isUpdating = true;

        double r = redSlider.getValue() / 255.0;
        double g = greenSlider.getValue() / 255.0;
        double b = blueSlider.getValue() / 255.0;
        double a = alphaSlider.getValue() / 255.0;

        currentColor = new Color(r, g, b, a);

        // Update hex field
        hexField.setText(colorToHex(currentColor));

        // Update preview
        colorPreview.setFill(currentColor);

        // Notify listener
        if (onColorChanged != null) {
            onColorChanged.accept(currentColor);
        }

        isUpdating = false;
    }

    private void hexToColor() {
        String hex = hexField.getText().trim();
        if (!hex.startsWith("#")) {
            hex = "#" + hex;
        }

        try {
            Color color = Color.web(hex);
            setColor(color);
        } catch (IllegalArgumentException e) {
            // Invalid hex, revert to current
            hexField.setText(colorToHex(currentColor));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    public void setColor(Color color) {
        isUpdating = true;

        this.currentColor = color;

        redSlider.setValue(color.getRed() * 255);
        greenSlider.setValue(color.getGreen() * 255);
        blueSlider.setValue(color.getBlue() * 255);
        alphaSlider.setValue(color.getOpacity() * 255);

        redSpinner.getValueFactory().setValue((int) (color.getRed() * 255));
        greenSpinner.getValueFactory().setValue((int) (color.getGreen() * 255));
        blueSpinner.getValueFactory().setValue((int) (color.getBlue() * 255));
        alphaSpinner.getValueFactory().setValue((int) (color.getOpacity() * 255));

        hexField.setText(colorToHex(color));
        colorPreview.setFill(color);

        isUpdating = false;
    }

    public Color getColor() {
        return currentColor;
    }

    public String getColorName() {
        return colorName;
    }

    public String getColorHex() {
        return colorToHex(currentColor);
    }

    public void setOnColorChanged(Consumer<Color> callback) {
        this.onColorChanged = callback;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════════════════════

    private String colorToHex(Color color) {
        int r = (int) (color.getRed() * 255);
        int g = (int) (color.getGreen() * 255);
        int b = (int) (color.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }
}
