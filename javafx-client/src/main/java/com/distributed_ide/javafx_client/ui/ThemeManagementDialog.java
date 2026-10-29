package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.javafx_client.service.CustomThemeManager;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

/**
 * Theme Management Dialog for creating, editing, and managing custom themes.
 *
 * Features:
 * - List all custom themes
 * - Create new theme from scratch
 * - Create new theme from existing theme (clone)
 * - Edit selected theme
 * - Delete theme with confirmation
 * - Import theme from file
 * - Export selected theme to file
 * - Export all themes to file
 * - Real-time theme list updates
 *
 * Architecture:
 * - Left panel: Theme list with context menu
 * - Right panel: Buttons for actions
 * - Uses CustomThemeManager for persistence
 * - Delegates to ThemeEditorDialog for color editing
 */
public class ThemeManagementDialog extends Dialog<Boolean> {

    private static final Logger LOG = LoggerFactory.getLogger(ThemeManagementDialog.class);

    private final CustomThemeManager themeManager;
    private final com.distributed_ide.javafx_client.theme.ThemeManager uiThemeManager;
    private ListView<String> themeListView;
    private Label themeCountLabel;

    public ThemeManagementDialog(CustomThemeManager themeManager) {
        this(themeManager, null);
    }

    public ThemeManagementDialog(CustomThemeManager themeManager, com.distributed_ide.javafx_client.theme.ThemeManager uiThemeManager) {
        this.themeManager = themeManager;
        this.uiThemeManager = uiThemeManager;
        initDialog();
    }

    private void initDialog() {
        setTitle("Theme Management");
        setHeaderText("Create and manage custom IDE themes");
        setResizable(true);

        // ── Theme list ────────────────────────────────────────────────
        themeListView = new ListView<>();
        themeListView.setPrefWidth(250);

        // ── List label ────────────────────────────────────────────────
        HBox listHeaderBox = new HBox(10);
        Label listLabel = new Label("Thèmes personnalisés :");
        listLabel.setStyle("-fx-font-weight: bold");
        themeCountLabel = new Label("(0 thèmes)");
        themeCountLabel.setStyle("-fx-text-fill: #666666");
        listHeaderBox.getChildren().addAll(listLabel, themeCountLabel);

        // ── Refresh theme list after label created ────────────────────
        refreshThemeList();

        // ── Context menu for list ─────────────────────────────────────
        setupContextMenu();

        // ── Action buttons ────────────────────────────────────────────
        VBox actionBox = new VBox(8);
        actionBox.setPadding(new Insets(10));

        Button newThemeButton = new Button("New Theme");
        newThemeButton.setPrefWidth(150);
        newThemeButton.setOnAction(e -> createNewTheme());

        Button cloneThemeButton = new Button("Clone Selected");
        cloneThemeButton.setPrefWidth(150);
        cloneThemeButton.setOnAction(e -> {
            String selected = themeListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                cloneTheme(selected);
            } else {
                showInfo("Select a theme to clone");
            }
        });

        Button editThemeButton = new Button("Edit Selected");
        editThemeButton.setPrefWidth(150);
        editThemeButton.setOnAction(e -> {
            String selected = themeListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                editTheme(selected);
            } else {
                showInfo("Select a theme to edit");
            }
        });

        Button applyThemeButton = new Button("Apply Selected");
        applyThemeButton.setPrefWidth(150);
        applyThemeButton.setStyle("-fx-text-fill: #27ae60");
        applyThemeButton.setOnAction(e -> {
            String selected = themeListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                applyTheme(selected);
            } else {
                showInfo("Select a theme to apply");
            }
        });

        Button deleteThemeButton = new Button("Delete Selected");
        deleteThemeButton.setPrefWidth(150);
        deleteThemeButton.setStyle("-fx-text-fill: #d32f2f");
        deleteThemeButton.setOnAction(e -> {
            String selected = themeListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                deleteTheme(selected);
            } else {
                showInfo("Select a theme to delete");
            }
        });

        Separator sep1 = new Separator();

        Button importThemeButton = new Button("Import Theme...");
        importThemeButton.setPrefWidth(150);
        importThemeButton.setOnAction(e -> importTheme());

        Button importAllButton = new Button("Import All...");
        importAllButton.setPrefWidth(150);
        importAllButton.setOnAction(e -> importAllThemes());

        Button exportThemeButton = new Button("Export Selected...");
        exportThemeButton.setPrefWidth(150);
        exportThemeButton.setOnAction(e -> {
            String selected = themeListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                exportTheme(selected);
            } else {
                showInfo("Select a theme to export");
            }
        });

        Button exportAllButton = new Button("Export All...");
        exportAllButton.setPrefWidth(150);
        exportAllButton.setOnAction(e -> exportAllThemes());

        actionBox.getChildren().addAll(
                newThemeButton,
                cloneThemeButton,
                editThemeButton,
                applyThemeButton,
                deleteThemeButton,
                sep1,
                importThemeButton,
                importAllButton,
                exportThemeButton,
                exportAllButton
        );

        // ── Main layout ───────────────────────────────────────────────
        HBox mainContent = new HBox(15);
        mainContent.setPadding(new Insets(10));

        VBox listPanel = new VBox(8);
        listPanel.getChildren().addAll(listHeaderBox, themeListView);
        VBox.setVgrow(themeListView, Priority.ALWAYS);

        mainContent.getChildren().addAll(listPanel, actionBox);
        HBox.setHgrow(listPanel, Priority.ALWAYS);

        DialogPane dialogPane = getDialogPane();
        dialogPane.setContent(mainContent);
        dialogPane.setPrefWidth(450);
        dialogPane.setPrefHeight(500);

        // ── Dialog buttons ────────────────────────────────────────────
        ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().add(closeButton);

        setResultConverter(buttonType -> true);

        // ── Listen to theme changes ───────────────────────────────────
        themeManager.addListener(manager -> refreshThemeList());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CONTEXT MENU
    // ═══════════════════════════════════════════════════════════════════

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem editItem = new MenuItem("Éditer...");
        editItem.setOnAction(e -> {
            String selected = themeListView.getSelectionModel().getSelectedItem();
            if (selected != null) editTheme(selected);
        });

        MenuItem cloneItem = new MenuItem("Cloner...");
        cloneItem.setOnAction(e -> {
            String selected = themeListView.getSelectionModel().getSelectedItem();
            if (selected != null) cloneTheme(selected);
        });

        MenuItem deleteItem = new MenuItem("Supprimer");
        deleteItem.setOnAction(e -> {
            String selected = themeListView.getSelectionModel().getSelectedItem();
            if (selected != null) deleteTheme(selected);
        });

        MenuItem exportItem = new MenuItem("Exporter...");
        exportItem.setOnAction(e -> {
            String selected = themeListView.getSelectionModel().getSelectedItem();
            if (selected != null) exportTheme(selected);
        });

        contextMenu.getItems().addAll(editItem, cloneItem, deleteItem, new SeparatorMenuItem(), exportItem);
        themeListView.setContextMenu(contextMenu);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  THEME OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    private void createNewTheme() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Theme");
        dialog.setHeaderText("Enter theme name:");
        dialog.setContentText("Theme name:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String themeName = result.get().trim();
            if (themeName.isEmpty()) {
                showError("Theme name cannot be empty");
                return;
            }

            if (themeManager.themeExists(themeName)) {
                showError("Theme already exists: " + themeName);
                return;
            }

            // Create with default colors
            Map<String, String> defaultColors = Map.ofEntries(
                    Map.entry("editorBackground", "#1e1e1e"),
                    Map.entry("editorForeground", "#e0e0e0"),
                    Map.entry("lineNumberBackground", "#252526"),
                    Map.entry("lineNumberColor", "#858585"),
                    Map.entry("keywordColor", "#569cd6"),
                    Map.entry("stringColor", "#ce9178"),
                    Map.entry("commentColor", "#6a9955"),
                    Map.entry("numberColor", "#b5cea8"),
                    Map.entry("operatorColor", "#d4d4d4"),
                    Map.entry("buttonBackground", "#3e3e42"),
                    Map.entry("buttonForeground", "#cccccc"),
                    Map.entry("buttonHover", "#52ad9e"),
                    Map.entry("accentColor", "#007acc")
            );

            themeManager.saveTheme(themeName, defaultColors);
            editTheme(themeName);
        }
    }

    private void editTheme(String themeName) {
        Map<String, String> colors = themeManager.getTheme(themeName);
        if (colors == null) {
            showError("Theme not found");
            return;
        }

        ThemeEditorDialog editor = new ThemeEditorDialog(themeName, colors);
        Optional<Map<String, String>> result = editor.showAndWait();

        if (result.isPresent()) {
            themeManager.saveTheme(themeName, result.get());
            LOG.info("Theme edited: {}", themeName);
        }
    }

    private void cloneTheme(String themeName) {
        Map<String, String> sourceColors = themeManager.getTheme(themeName);
        if (sourceColors == null) {
            showError("Theme not found");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(themeName + "-copy");
        dialog.setTitle("Clone Theme");
        dialog.setHeaderText("Enter new theme name:");
        dialog.setContentText("Theme name:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String newName = result.get().trim();
            if (newName.isEmpty()) {
                showError("Theme name cannot be empty");
                return;
            }

            if (themeManager.themeExists(newName)) {
                showError("Theme already exists: " + newName);
                return;
            }

            themeManager.saveTheme(newName, new java.util.HashMap<>(sourceColors));
            LOG.info("Theme cloned: {} -> {}", themeName, newName);
        }
    }

    private void applyTheme(String themeName) {
        if (uiThemeManager == null) {
            showError("Theme application not available");
            return;
        }

        Map<String, String> colors = themeManager.getTheme(themeName);
        if (colors == null) {
            showError("Theme not found");
            return;
        }

        // Apply custom theme via UIThemeManager
        uiThemeManager.applyCustomTheme(themeName, colors);
        LOG.info("Custom theme applied: {}", themeName);
        showInfo("Theme applied: " + themeName);
    }

    private void deleteTheme(String themeName) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Theme");
        confirm.setHeaderText("Delete theme: " + themeName);
        confirm.setContentText("This action cannot be undone.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            themeManager.deleteTheme(themeName);
            LOG.info("Theme deleted: {}", themeName);
        }
    }

    private void importTheme() {
        FileChooserDialog chooser = new FileChooserDialog();
        Optional<Path> result = chooser.showOpenDialog("Import Theme", "*.json");

        if (result.isPresent()) {
            TextInputDialog nameDialog = new TextInputDialog();
            nameDialog.setTitle("Import Theme");
            nameDialog.setHeaderText("Enter theme name:");
            nameDialog.setContentText("Theme name:");

            Optional<String> nameResult = nameDialog.showAndWait();
            if (nameResult.isPresent()) {
                String themeName = nameResult.get().trim();
                if (themeManager.importTheme(result.get(), themeName)) {
                    showInfo("Theme imported successfully");
                } else {
                    showError("Failed to import theme (invalid format or structure)");
                }
            }
        }
    }

    private void importAllThemes() {
        FileChooserDialog chooser = new FileChooserDialog();
        Optional<Path> result = chooser.showOpenDialog("Import Themes", "*.json");

        if (result.isPresent()) {
            int count = themeManager.importAllThemes(result.get());
            showInfo("Imported " + count + " themes");
        }
    }

    private void exportTheme(String themeName) {
        FileChooserDialog chooser = new FileChooserDialog();
        Optional<Path> result = chooser.showSaveDialog("Export Theme", themeName + ".json");

        if (result.isPresent()) {
            if (themeManager.exportTheme(themeName, result.get())) {
                showInfo("Theme exported successfully");
            } else {
                showError("Failed to export theme");
            }
        }
    }

    private void exportAllThemes() {
        FileChooserDialog chooser = new FileChooserDialog();
        Optional<Path> result = chooser.showDirectoryChooser("Export All Themes");

        if (result.isPresent()) {
            if (themeManager.exportAllThemes(result.get())) {
                showInfo("All themes exported successfully");
            } else {
                showError("Failed to export themes");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UI UPDATES
    // ═══════════════════════════════════════════════════════════════════

    private void refreshThemeList() {
        java.util.List<String> themes = themeManager.listThemes();
        themeListView.getItems().setAll(themes);
        themeCountLabel.setText("(" + themes.size() + " thème" + (themes.size() == 1 ? "" : "s") + ")");
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FILE CHOOSER HELPER
    // ═══════════════════════════════════════════════════════════════════

    private static class FileChooserDialog {
        private final FileChooser fileChooser = new FileChooser();

        Optional<Path> showOpenDialog(String title, String extension) {
            fileChooser.setTitle(title);
            fileChooser.getExtensionFilters().clear();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", extension));
            fileChooser.setInitialDirectory(Paths.get(System.getProperty("user.home")).toFile());

            java.io.File file = fileChooser.showOpenDialog(null);
            return file != null ? Optional.of(file.toPath()) : Optional.empty();
        }

        Optional<Path> showSaveDialog(String title, String filename) {
            fileChooser.setTitle(title);
            fileChooser.setInitialFileName(filename);
            fileChooser.getExtensionFilters().clear();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
            fileChooser.setInitialDirectory(Paths.get(System.getProperty("user.home")).toFile());

            java.io.File file = fileChooser.showSaveDialog(null);
            return file != null ? Optional.of(file.toPath()) : Optional.empty();
        }

        Optional<Path> showDirectoryChooser(String title) {
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle(title);
            dirChooser.setInitialDirectory(Paths.get(System.getProperty("user.home")).toFile());

            java.io.File dir = dirChooser.showDialog(null);
            return dir != null ? Optional.of(dir.toPath()) : Optional.empty();
        }
    }
}
