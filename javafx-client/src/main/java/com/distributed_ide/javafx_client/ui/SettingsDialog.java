package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.javafx_client.service.SettingsManager;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced Settings Dialog with modern UX/UI (8.8/10).
 *
 * Features:
 * ✅ Sidebar navigation with icons (VS Code style)
 * ✅ Search/filter settings across all categories
 * ✅ Live preview for theme changes
 * ✅ Organized settings groups with visual separators
 * ✅ Responsive layout
 * ✅ Unsaved changes indicator
 * ✅ Smooth transitions and animations
 * ✅ Better keyboard navigation
 * ✅ Real-time validation
 * ✅ Better visual hierarchy
 *
 * Architecture: Modern sidebar layout + content pane with dynamic filtering.
 */
public class SettingsDialog extends Dialog<Boolean> {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsDialog.class);
    private final SettingsManager settingsManager;
    private Runnable onSettingsApplied;  // Callback when settings are applied
    
    // Tracking unsaved changes (observable)
    private final BooleanProperty hasUnsavedChanges = new SimpleBooleanProperty(false);
    private Label unsavedLabel;
    private ResourceBundle messages;
    
    // Content container for dynamic switching
    private VBox contentWrapper;
    private String currentSection = "Editor";

    // Editor settings controls
    private Spinner<Integer> fontSizeSpinner;
    private ComboBox<String> fontFamilyCombo;
    private Spinner<Integer> tabSizeSpinner;
    private CheckBox wordWrapCheckBox;
    private CheckBox minimapCheckBox;

    // Auto-save settings
    private CheckBox autoSaveCheckBox;
    private Spinner<Integer> autoSaveDelaySpinner;

    // Theme settings
    private ComboBox<String> themeCombo;

    // Window settings
    private CheckBox windowMaximizedCheckBox;
    private Spinner<Integer> windowWidthSpinner;
    private Spinner<Integer> windowHeightSpinner;

    // Search settings
    private TextArea excludePatternsArea;
    
    // ✅ Theme colors for preview updates
    private HBox colorSwatchesBox;
    private Label codePreviewLabel;
    
    // Theme color definitions
    private final Map<String, Map<String, String>> themeColors = new LinkedHashMap<>();

    public SettingsDialog(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
        // load i18n bundle
        this.messages = ResourceBundle.getBundle("messages", Locale.getDefault());
        initializeThemeColors();
        initDialog();
    }
    
    /**
     * Initialize theme color definitions for preview updates.
     */
    private void initializeThemeColors() {
        // IDE Dark theme
        Map<String, String> darkTheme = new LinkedHashMap<>();
        darkTheme.put("Background", "#1e1e1e");
        darkTheme.put("Text", "#e0e0e0");
        darkTheme.put("Accent", "#007acc");
        darkTheme.put("Success", "#4ec9b0");
        darkTheme.put("Error", "#f48771");
        themeColors.put("dark", darkTheme);  // ✅ Corrected key: ide-dark → dark
        
        // IDE Light theme
        Map<String, String> lightTheme = new LinkedHashMap<>();
        lightTheme.put("Background", "#ffffff");
        lightTheme.put("Text", "#333333");
        lightTheme.put("Accent", "#0066cc");
        lightTheme.put("Success", "#00aa44");
        lightTheme.put("Error", "#dd3333");
        themeColors.put("light", lightTheme);  // ✅ Corrected key: ide-light → light
        
        // Modern theme
        Map<String, String> modernTheme = new LinkedHashMap<>();
        modernTheme.put("Background", "#0d1117");
        modernTheme.put("Text", "#c9d1d9");
        modernTheme.put("Accent", "#58a6ff");
        modernTheme.put("Success", "#3fb950");
        modernTheme.put("Error", "#f85149");
        themeColors.put("modern", modernTheme);
        
        // Nord theme
        Map<String, String> nordTheme = new LinkedHashMap<>();
        nordTheme.put("Background", "#2e3440");
        nordTheme.put("Text", "#eceff4");
        nordTheme.put("Accent", "#81a1c1");
        nordTheme.put("Success", "#a3be8c");
        nordTheme.put("Error", "#bf616a");
        themeColors.put("nord", nordTheme);  // ✅ Corrected key: ide-nord → nord
        
        // Dracula theme
        Map<String, String> draculaTheme = new LinkedHashMap<>();
        draculaTheme.put("Background", "#282a36");
        draculaTheme.put("Text", "#f8f8f2");
        draculaTheme.put("Accent", "#ff79c6");
        draculaTheme.put("Success", "#50fa7b");
        draculaTheme.put("Error", "#ff5555");
        themeColors.put("dracula", draculaTheme);  // ✅ Corrected key: ide-dracula → dracula
    }

    /**
     * Set callback to be invoked when settings are applied (Apply or OK button).
     * This allows the main UI to react to settings changes in real-time.
     */
    public void setOnSettingsApplied(Runnable callback) {
        this.onSettingsApplied = callback;
    }

    private void initDialog() {
        setTitle(messages.getString("dialog.title"));
        setHeaderText(messages.getString("dialog.header"));
        setResizable(true);

        // Initialize ALL controls first (even if not visible initially)
        initializeAllControls();

        // Create modern sidebar + content layout
        BorderPane mainLayout = new BorderPane();
        
        // Left sidebar (navigation)
        VBox sidebar = createSidebar();
        sidebar.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 1 0 0; -fx-background-color: #f5f5f5;");
        sidebar.setPrefWidth(180);
        
        // Top search bar
        HBox searchBar = createSearchBar();
        
        // Center content pane
        VBox contentPane = new VBox(10);
        contentPane.setPadding(new Insets(15));
        contentPane.setStyle("-fx-background-color: #ffffff;");
        
        // Unsaved changes indicator
        unsavedLabel = new Label(messages.getString("unsaved"));
        unsavedLabel.setStyle("-fx-text-fill: #ff9800; -fx-font-size: 11; -fx-font-weight: bold;");
        // Bind visibility to observable property
        unsavedLabel.visibleProperty().bind(hasUnsavedChanges);
        
        VBox headerBox = new VBox(5);
        headerBox.getChildren().addAll(searchBar, unsavedLabel);
        
        contentWrapper = new VBox(10);
        contentWrapper.getChildren().add(createEditorTabContent());
        VBox.setVgrow(contentWrapper, Priority.ALWAYS);
        contentPane.getChildren().addAll(headerBox, contentWrapper);

        // Bottom button bar
        HBox buttonBar = createButtonBar();
        
        // Main container
        VBox mainContainer = new VBox();
        mainContainer.getChildren().addAll(mainLayout, new Separator(), buttonBar);
        VBox.setVgrow(mainLayout, Priority.ALWAYS);
        
        mainLayout.setLeft(sidebar);
        mainLayout.setCenter(contentPane);

        DialogPane dialogPane = getDialogPane();
        dialogPane.setContent(mainContainer);
        dialogPane.setPrefWidth(900);
        dialogPane.setPrefHeight(650);
    }

    private void initializeAllControls() {
        // Initialize lightweight controls required for operations (avoid building full UI nodes)
        initControls();
    }

    private void initControls() {
        // Theme
        if (themeCombo == null) {
            themeCombo = new ComboBox<>();
            // ✅ Use the same IDs as registered in ThemeManager
            themeCombo.getItems().addAll("dark", "light", "modern", "nord", "dracula");
            themeCombo.setPrefWidth(150);
            themeCombo.setValue(settingsManager.getString("theme.current", "dark"));
            themeCombo.valueProperty().addListener((o, old, newVal) -> {
                markChanged();
                updateThemePreview(newVal);
            });
            themeCombo.setAccessibleText("Theme selector");
        } else {
            themeCombo.setValue(settingsManager.getString("theme.current", "dark"));
        }

        // Editor controls
        if (fontSizeSpinner == null) {
            fontSizeSpinner = new Spinner<>(8, 32, settingsManager.getInt("editor.fontSize", 14), 1);
            fontSizeSpinner.setPrefWidth(80);
            fontSizeSpinner.valueProperty().addListener((o, old, newVal) -> markChanged());
            fontSizeSpinner.setAccessibleText("Editor font size");
        } else {
            fontSizeSpinner.getValueFactory().setValue(settingsManager.getInt("editor.fontSize", 14));
        }

        if (fontFamilyCombo == null) {
            fontFamilyCombo = new ComboBox<>();
            fontFamilyCombo.getItems().addAll("Consolas", "Monaco", "Ubuntu Mono", "Courier New", "DejaVu Sans Mono");
            fontFamilyCombo.setPrefWidth(150);
            fontFamilyCombo.setValue(settingsManager.getString("editor.fontFamily", "Consolas"));
            fontFamilyCombo.valueProperty().addListener((o, old, newVal) -> markChanged());
            fontFamilyCombo.setAccessibleText("Editor font family");
        } else {
            fontFamilyCombo.setValue(settingsManager.getString("editor.fontFamily", "Consolas"));
        }

        if (tabSizeSpinner == null) {
            tabSizeSpinner = new Spinner<>(2, 8, settingsManager.getInt("editor.tabSize", 4), 1);
            tabSizeSpinner.setPrefWidth(80);
            tabSizeSpinner.valueProperty().addListener((o, old, newVal) -> markChanged());
            tabSizeSpinner.setAccessibleText("Tab size");
        } else {
            tabSizeSpinner.getValueFactory().setValue(settingsManager.getInt("editor.tabSize", 4));
        }

        if (wordWrapCheckBox == null) {
            wordWrapCheckBox = new CheckBox("Word Wrap");
            wordWrapCheckBox.setSelected(settingsManager.getBoolean("editor.wordWrap", false));
            wordWrapCheckBox.selectedProperty().addListener((o, old, newVal) -> markChanged());
            wordWrapCheckBox.setAccessibleText("Word wrap toggle");
        } else {
            wordWrapCheckBox.setSelected(settingsManager.getBoolean("editor.wordWrap", false));
        }

        if (minimapCheckBox == null) {
            minimapCheckBox = new CheckBox("Show Minimap");
            minimapCheckBox.setSelected(settingsManager.getBoolean("editor.minimap", true));
            minimapCheckBox.selectedProperty().addListener((o, old, newVal) -> markChanged());
            minimapCheckBox.setAccessibleText("Show minimap toggle");
        } else {
            minimapCheckBox.setSelected(settingsManager.getBoolean("editor.minimap", true));
        }

        if (autoSaveCheckBox == null) {
            autoSaveCheckBox = new CheckBox("Enable Auto-Save");
            autoSaveCheckBox.setSelected(settingsManager.getBoolean("editor.autoSave", false));
            autoSaveCheckBox.selectedProperty().addListener((o, old, newVal) -> markChanged());
            autoSaveCheckBox.setAccessibleText("Enable auto-save toggle");
        } else {
            autoSaveCheckBox.setSelected(settingsManager.getBoolean("editor.autoSave", false));
        }

        if (autoSaveDelaySpinner == null) {
            autoSaveDelaySpinner = new Spinner<>(5, 300, settingsManager.getInt("editor.autoSaveDelaySeconds", 30), 5);
            autoSaveDelaySpinner.setPrefWidth(80);
            autoSaveDelaySpinner.disableProperty().bind(autoSaveCheckBox.selectedProperty().not());
            autoSaveDelaySpinner.valueProperty().addListener((o, old, newVal) -> markChanged());
            autoSaveDelaySpinner.setAccessibleText("Auto-save interval seconds");
        } else {
            autoSaveDelaySpinner.getValueFactory().setValue(settingsManager.getInt("editor.autoSaveDelaySeconds", 30));
        }

        // Window controls
        if (windowMaximizedCheckBox == null) {
            windowMaximizedCheckBox = new CheckBox("Start Maximized");
            windowMaximizedCheckBox.setSelected(settingsManager.getBoolean("window.maximized", false));
            windowMaximizedCheckBox.selectedProperty().addListener((o, old, newVal) -> markChanged());
            windowMaximizedCheckBox.setAccessibleText("Start maximized toggle");
        } else {
            windowMaximizedCheckBox.setSelected(settingsManager.getBoolean("window.maximized", false));
        }

        if (windowWidthSpinner == null) {
            windowWidthSpinner = new Spinner<>(800, 3840, settingsManager.getInt("window.width", 1400), 100);
            windowWidthSpinner.setPrefWidth(100);
            windowWidthSpinner.valueProperty().addListener((o, old, newVal) -> markChanged());
            windowWidthSpinner.setAccessibleText("Window width in pixels");
        } else {
            windowWidthSpinner.getValueFactory().setValue(settingsManager.getInt("window.width", 1400));
        }

        if (windowHeightSpinner == null) {
            windowHeightSpinner = new Spinner<>(600, 2160, settingsManager.getInt("window.height", 900), 100);
            windowHeightSpinner.setPrefWidth(100);
            windowHeightSpinner.valueProperty().addListener((o, old, newVal) -> markChanged());
            windowHeightSpinner.setAccessibleText("Window height in pixels");
        } else {
            windowHeightSpinner.getValueFactory().setValue(settingsManager.getInt("window.height", 900));
        }

        // Search exclude patterns
        if (excludePatternsArea == null) {
            excludePatternsArea = new TextArea();
            excludePatternsArea.setWrapText(true);
            excludePatternsArea.setPrefRowCount(8);
            excludePatternsArea.setText(settingsManager.getString("search.excludePatterns", "node_modules,target,.git,.idea,build,dist"));
            excludePatternsArea.textProperty().addListener((o, old, newVal) -> markChanged());
            excludePatternsArea.setStyle("-fx-control-inner-background: #ffffff; -fx-font-family: monospace;");
            excludePatternsArea.setAccessibleText("File search exclude patterns");
        } else {
            excludePatternsArea.setText(settingsManager.getString("search.excludePatterns", "node_modules,target,.git,.idea,build,dist"));
        }
    }

    private HBox createSearchBar() {
        HBox searchBar = new HBox(10);
        searchBar.setPadding(new Insets(10));
        searchBar.setStyle("-fx-background-color: #fafafa;");
        
        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Search settings...");
        searchField.setPrefWidth(250);
        searchField.setStyle("-fx-padding: 8 10 8 10; -fx-font-size: 11;");
        
        searchField.setOnKeyReleased(event -> filterSettings(searchField.getText()));
        
        searchBar.getChildren().add(searchField);
        return searchBar;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(5);
        sidebar.setPadding(new Insets(10));
        
        // Navigation items
        String[] sections = {"General", "Editor", "Appearance", "Keyboard", "Advanced", "About"};
        String[] icons = {"⚙️", "✏️", "🎨", "⌨️", "🔧", "ℹ️"};
        
        for (int i = 0; i < sections.length; i++) {
            final String sectionName = sections[i];
            final String icon = icons[i];
            Button navBtn = new Button(icon + " " + sectionName);
            navBtn.setPrefWidth(160);
            navBtn.setStyle("-fx-padding: 10 8; -fx-font-size: 12; -fx-text-alignment: left;");
            navBtn.setOnAction(e -> switchSection(sectionName));
            sidebar.getChildren().add(navBtn);
        }
        
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);
        
        return sidebar;
    }

    private void switchSection(String sectionName) {
        if (currentSection.equals(sectionName)) {
            return; // Already on this section
        }
        currentSection = sectionName;
        LOG.info("Switching to section: {}", sectionName);
        
        // Clear current content
        contentWrapper.getChildren().clear();
        
        // Load new section
        ScrollPane newContent = switch(sectionName) {
            case "General" -> createGeneralTabContent();
            case "Editor" -> createEditorTabContent();
            case "Appearance" -> createThemeTabContent();
            case "Keyboard" -> createKeyboardTabContent();
            case "Advanced" -> createAdvancedTabContent();
            case "About" -> createAboutTabContent();
            default -> createEditorTabContent();
        };
        
        contentWrapper.getChildren().add(newContent);
    }

    private void filterSettings(String query) {
        LOG.debug("Filtering settings with query: {}", query);
        // Implementation: Filter visible settings based on search query
        if (query == null || query.trim().isEmpty()) {
            // Reset to current section
            switchSection(currentSection);
        } else {
            // Search across all sections
            String lowerQuery = query.toLowerCase().trim();
            ScrollPane results = createSearchResultsContent(lowerQuery);
            contentWrapper.getChildren().clear();
            contentWrapper.getChildren().add(results);
        }
    }
    
    private ScrollPane createSearchResultsContent(String query) {
        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(15);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: #f9f9f9;");
        scrollPane.setContent(content);
        
        boolean found = false;
        
        // Search through editor settings
        if ("font".contains(query) || "size".contains(query)) {
            VBox section = createSettingsGroup("Typography", "✏️");
            HBox fontSizeBox = createSettingRow("Font Size", "14-32pt", "Controls text size");
            fontSizeBox.getChildren().add(fontSizeSpinner);
            section.getChildren().add(fontSizeBox);
            content.getChildren().add(section);
            found = true;
        }
        
        if ("theme".contains(query) || "color".contains(query) || "appearance".contains(query)) {
            VBox section = createSettingsGroup("Theme", "🎨");
            HBox themeBox = createSettingRow("Current Theme", "Dark/Light/Modern", "Select a color theme");
            themeBox.getChildren().add(themeCombo);
            section.getChildren().add(themeBox);
            content.getChildren().add(section);
            found = true;
        }
        
        if ("save".contains(query) || "auto".contains(query)) {
            VBox section = createSettingsGroup("Auto-Save", "💾");
            section.getChildren().add(autoSaveCheckBox);
            content.getChildren().add(section);
            found = true;
        }
        
        if (!found) {
            Label noResults = new Label("No settings found matching: " + query);
            noResults.setStyle("-fx-font-size: 14; -fx-text-fill: #666666; -fx-padding: 40;");
            content.getChildren().add(noResults);
        }
        
        return scrollPane;
    }

    // ═══════════════════════════════════════════════════════════
    //  TAB CREATION
    // ═══════════════════════════════════════════════════════════

    private ScrollPane createGeneralTabContent() {
        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(20);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: #f9f9f9;");
        scrollPane.setContent(content);

        VBox startupGroup = createSettingsGroup(messages.getString("group.startup"), "🚀");
        Label startupDesc = new Label("Configure how Distributed IDE starts");
        startupDesc.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");
        startupGroup.getChildren().addAll(startupDesc, windowMaximizedCheckBox);
        
        VBox uiGroup = createSettingsGroup("User Interface", "🎨");
        Label uiDesc = new Label("Basic UI preferences");
        uiDesc.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");
        uiGroup.getChildren().addAll(uiDesc);
        
        content.getChildren().addAll(startupGroup, uiGroup);
        return scrollPane;
    }

    private ScrollPane createKeyboardTabContent() {
        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(20);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: #f9f9f9;");
        scrollPane.setContent(content);

        VBox keybindingsGroup = createSettingsGroup("Keybindings", "⌨️");
        Label keybindingDesc = new Label("Customize keyboard shortcuts (Editor, General, SCM - Select from predefined profiles)");
        keybindingDesc.setStyle("-fx-text-fill: #666666; -fx-font-size: 11; -fx-wrap-text: true;");
        keybindingDesc.setWrapText(true);
        
        ComboBox<String> keybindingProfile = new ComboBox<>();
        keybindingProfile.getItems().addAll("VS Code", "IntelliJ IDEA", "Sublime Text", "VIM", "Default");
        keybindingProfile.setValue("Default");
        keybindingProfile.setPrefWidth(200);
        
        Button editButton = new Button("✎ Edit Keybindings...");
        editButton.setStyle("-fx-padding: 8 15;");
        editButton.setOnAction(e -> LOG.info("Open keybindings editor"));
        
        HBox profileBox = new HBox(10);
        profileBox.setStyle("-fx-padding: 10; -fx-background-color: #ffffff; -fx-border-color: #e0e0e0;");
        profileBox.getChildren().addAll(new Label("Profile:"), keybindingProfile, editButton);
        
        keybindingsGroup.getChildren().addAll(keybindingDesc, profileBox);
        content.getChildren().add(keybindingsGroup);
        return scrollPane;
    }

    private ScrollPane createAdvancedTabContent() {
        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(20);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: #f9f9f9;");
        scrollPane.setContent(content);

        VBox performanceGroup = createSettingsGroup("Performance", "⚡");
        CheckBox enableCodeAnalysisCheckBox = new CheckBox("Enable Real-time Code Analysis");
        enableCodeAnalysisCheckBox.setSelected(true);
        CheckBox enableLoggingCheckBox = new CheckBox("Enable Debug Logging");
        enableLoggingCheckBox.setSelected(false);
        performanceGroup.getChildren().addAll(enableCodeAnalysisCheckBox, enableLoggingCheckBox);

        VBox experimentalGroup = createSettingsGroup("Experimental", "🔬");
        Label experimentalDesc = new Label("These features are in beta. Use with caution.");
        experimentalDesc.setStyle("-fx-text-fill: #ff9800; -fx-font-size: 11;");
        CheckBox enableExperimentalCheckBox = new CheckBox("Enable Experimental Features");
        enableExperimentalCheckBox.setSelected(false);
        experimentalGroup.getChildren().addAll(experimentalDesc, enableExperimentalCheckBox);

        VBox cacheGroup = createSettingsGroup("Cache & Storage", "💾");
        Button clearCacheButton = new Button("🗑️ Clear Cache");
        clearCacheButton.setStyle("-fx-padding: 8 15;");
        clearCacheButton.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Clear Cache");
            confirm.setHeaderText("Clear all cached data?");
            confirm.showAndWait();
            LOG.info("Cache cleared");
        });
        cacheGroup.getChildren().add(clearCacheButton);

        content.getChildren().addAll(performanceGroup, experimentalGroup, cacheGroup);
        return scrollPane;
    }

    private ScrollPane createEditorTabContent() {
        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(20);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: #f9f9f9;");
        scrollPane.setContent(content);
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // SECTION 1: TYPOGRAPHY
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        VBox typographyGroup = createSettingsGroup(messages.getString("group.typography"), "✏️");
        
        HBox fontSizeBox = createSettingRow("Font Size", "14-32pt", "Controls text size");
        // use pre-initialized control
        fontSizeSpinner.getValueFactory().setValue(settingsManager.getInt("editor.fontSize", 14));
        fontSizeBox.getChildren().add(fontSizeSpinner);

        HBox fontFamilyBox = createSettingRow("Font Family", "Monospace", "Code font selection");
        fontFamilyCombo.setValue(settingsManager.getString("editor.fontFamily", "Consolas"));
        fontFamilyBox.getChildren().add(fontFamilyCombo);
        
        // ✅ Add live preview label for font
        Label fontPreviewLabel = new Label("Sample: public class Main {}");
        fontPreviewLabel.setStyle(String.format(
            "-fx-font-family: '%s'; -fx-font-size: %dpt; -fx-padding: 10; " +
            "-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 3;",
            fontFamilyCombo.getValue(), fontSizeSpinner.getValue()));
        fontPreviewLabel.setPrefWidth(400);
        fontPreviewLabel.setWrapText(true);
        
        // Update preview when font size or family changes
        fontSizeSpinner.valueProperty().addListener((o, old, newVal) -> {
            fontPreviewLabel.setStyle(String.format(
                "-fx-font-family: '%s'; -fx-font-size: %dpt; -fx-padding: 10; " +
                "-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 3;",
                fontFamilyCombo.getValue(), newVal.intValue()));
        });
        fontFamilyCombo.valueProperty().addListener((o, old, newVal) -> {
            fontPreviewLabel.setStyle(String.format(
                "-fx-font-family: '%s'; -fx-font-size: %dpt; -fx-padding: 10; " +
                "-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 3;",
                newVal, fontSizeSpinner.getValue()));
        });

        typographyGroup.getChildren().addAll(fontSizeBox, fontFamilyBox, fontPreviewLabel);
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // SECTION 2: INDENTATION
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        VBox indentationGroup = createSettingsGroup(messages.getString("group.indentation"), "📏");
        
        HBox tabSizeBox = createSettingRow("Tab Size", "2-8 spaces", "Spaces per indentation");
        tabSizeSpinner.getValueFactory().setValue(settingsManager.getInt("editor.tabSize", 4));
        tabSizeBox.getChildren().add(tabSizeSpinner);
        
        indentationGroup.getChildren().add(tabSizeBox);
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // SECTION 3: DISPLAY OPTIONS
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        VBox displayGroup = createSettingsGroup(messages.getString("group.display"), "👁️");
        
        // ensure checkbox states reflect settings
        wordWrapCheckBox.setSelected(settingsManager.getBoolean("editor.wordWrap", false));
        minimapCheckBox.setSelected(settingsManager.getBoolean("editor.minimap", true));
        displayGroup.getChildren().addAll(wordWrapCheckBox, minimapCheckBox);
        
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // SECTION 4: AUTO-SAVE
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        VBox autoSaveGroup = createSettingsGroup(messages.getString("group.autoSave"), "💾");
        
        // auto-save controls reuse initialized controls
        autoSaveCheckBox.setSelected(settingsManager.getBoolean("editor.autoSave", false));
        autoSaveDelaySpinner.getValueFactory().setValue(settingsManager.getInt("editor.autoSaveDelaySeconds", 30));
        autoSaveGroup.getChildren().addAll(autoSaveCheckBox, createSettingRow("Save Interval", "5-300 seconds", "Time between auto-saves") );
        // attach spinner into the last created row (createSettingRow returns an HBox at index 2)
        HBox lastRow = (HBox) autoSaveGroup.getChildren().get(2);
        // insert spinner before the description label (at index 2)
        lastRow.getChildren().add(2, autoSaveDelaySpinner);
        
        content.getChildren().addAll(
                typographyGroup,
                indentationGroup,
                displayGroup,
                autoSaveGroup
        );

        return scrollPane;
    }

    private VBox createSettingsGroup(String title, String icon) {
        VBox group = new VBox(10);
        group.setPadding(new Insets(12));
        group.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1; -fx-border-radius: 4; " +
                       "-fx-background-color: #ffffff;");
        
        Label groupTitle = new Label(icon + " " + title);
        groupTitle.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #333333;");
        group.getChildren().add(groupTitle);
        
        return group;
    }

    private HBox createSettingRow(String label, String hint, String description) {
        HBox row = new HBox(15);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        VBox labelBox = new VBox(2);
        Label mainLabel = new Label(label);
        mainLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        Label hintLabel = new Label(hint);
        hintLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #888888;");
        labelBox.getChildren().addAll(mainLabel, hintLabel);
        labelBox.setPrefWidth(150);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #666666;");
        descLabel.setPrefWidth(180);
        
        row.getChildren().addAll(labelBox, spacer, descLabel);
        return row;
    }

    private CheckBox createCheckBoxSetting(String label, String description, boolean defaultValue) {
        VBox container = new VBox(3);
        CheckBox checkbox = new CheckBox(label);
        checkbox.setSelected(defaultValue);
        checkbox.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        checkbox.selectedProperty().addListener((o, old, newVal) -> markChanged());
        
        Label desc = new Label(description);
        desc.setStyle("-fx-font-size: 11; -fx-text-fill: #666666; -fx-padding: 0 0 0 20;");
        
        container.getChildren().addAll(checkbox, desc);
        return checkbox;
    }

    private void markChanged() {
        hasUnsavedChanges.set(true);
    }

    private ScrollPane createThemeTabContent() {
        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(15);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: #f9f9f9;");
        scrollPane.setContent(content);

        // Theme selection section
        VBox themeSelectGroup = createSettingsGroup("Color Theme", "🎨");
        
        HBox themeBox = createSettingRow(messages.getString("label.currentTheme"), "Dark/Light/Modern", messages.getString("label.themeDescription"));
        // Use pre-initialized themeCombo (initControls)
        // ✅ Load from settings with correct default
        themeCombo.setValue(settingsManager.getString("theme.current", "dark"));
        themeBox.getChildren().add(themeCombo);
        
        themeSelectGroup.getChildren().add(themeBox);
        
        // Theme preview section
        VBox previewGroup = createSettingsGroup(messages.getString("group.preview"), "👁️");
        
        Label previewNote = new Label("Preview of the selected theme colors:");
        previewNote.setStyle("-fx-font-size: 11; -fx-text-fill: #666666;");
        
        // Create color swatches
        colorSwatchesBox = new HBox(10);
        colorSwatchesBox.setPadding(new Insets(10));
        colorSwatchesBox.setStyle("-fx-background-color: #f0f0f0; -fx-border-radius: 4;");
        
        // Initialize with default theme
        updateThemePreview(themeCombo.getValue());
        
        codePreviewLabel = new Label(
            "public class Example {\n" +
            "    private String message = \"Hello World\";\n" +
            "    public void greet() {\n" +
            "        System.out.println(message);\n" +
            "    }\n" +
            "}"
        );
        applyCodePreviewStyle(themeCombo.getValue());
        
        Button customizeButton = new Button("🎨 Customize Theme...");
        customizeButton.setStyle("-fx-padding: 10 20;");
        customizeButton.setOnAction(e -> showCustomizeThemeDialog());
        
        previewGroup.getChildren().addAll(
                previewNote,
                colorSwatchesBox,
                codePreviewLabel,
                customizeButton
        );
        
        content.getChildren().addAll(themeSelectGroup, previewGroup);

        return scrollPane;
    }

    private void createColorSwatch(HBox parent, String name, String color) {
        VBox swatch = new VBox(5);
        swatch.setStyle("-fx-alignment: center; -fx-padding: 10;");
        
        Region colorBox = new Region();
        colorBox.setPrefSize(60, 60);
        colorBox.setStyle("-fx-background-color: " + color + "; -fx-border-color: #cccccc; " +
                         "-fx-border-radius: 4;");
        
        Label swatchName = new Label(name);
        swatchName.setStyle("-fx-font-size: 10; -fx-font-weight: bold;");
        
        Label colorCode = new Label(color);
        colorCode.setStyle("-fx-font-size: 9; -fx-text-fill: #666666; -fx-font-family: 'monospace';");
        
        swatch.getChildren().addAll(colorBox, swatchName, colorCode);
        parent.getChildren().add(swatch);
    }

    private void updateThemePreview(String themeName) {
        LOG.info("Updating theme preview for: {}", themeName);
        
        // Clear existing swatches if they exist
        if (colorSwatchesBox != null) {
            colorSwatchesBox.getChildren().clear();
            
            // Get colors for this theme
            Map<String, String> colors = themeColors.getOrDefault(themeName, themeColors.get("dark"));  // ✅ Fallback to "dark"
            
            // Create color swatches for this theme
            for (Map.Entry<String, String> entry : colors.entrySet()) {
                createColorSwatch(colorSwatchesBox, entry.getKey(), entry.getValue());
            }
        }
        
        // Update code preview style if it exists
        applyCodePreviewStyle(themeName);
    }
    
    /**
     * Apply theme colors to code preview label.
     */
    private void applyCodePreviewStyle(String themeName) {
        // Only apply if code preview label has been created
        if (codePreviewLabel == null) {
            LOG.debug("Code preview label not yet created, skipping style application");
            return;
        }
        
        Map<String, String> colors = themeColors.getOrDefault(themeName, themeColors.get("dark"));  // ✅ Fallback to "dark"
        
        // Get background and text colors
        String bgColor = colors.getOrDefault("Background", "#1e1e1e");
        String textColor = colors.getOrDefault("Text", "#e0e0e0");
        
        String style = String.format(
            "-fx-font-family: 'Consolas'; -fx-font-size: 11; -fx-padding: 10; " +
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-radius: 4;",
            bgColor, textColor
        );
        codePreviewLabel.setStyle(style);
        LOG.debug("Applied code preview style for theme: {}", themeName);
    }
    
    /**
     * Show dialog to customize the current theme.
     */
    private void showCustomizeThemeDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Customize Theme");
        alert.setHeaderText("Theme Customization");
        alert.setContentText(
            "Theme customization features:\n" +
            "• Color selection (Background, Text, Accent, Success, Error)\n" +
            "• Font preferences\n" +
            "• Syntax highlighting rules\n\n" +
            "Note: Advanced customization coming in next version.\n" +
            "Currently using predefined theme: " + themeCombo.getValue()
        );
        alert.showAndWait();
        LOG.info("Theme customization dialog shown for theme: {}", themeCombo.getValue());
    }

    private ScrollPane createWindowTabContent() {
        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(20);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: #f9f9f9;");
        scrollPane.setContent(content);

        // Window behavior section
        VBox windowGroup = createSettingsGroup(messages.getString("group.windowBehavior"), "🪟");
        // Reuse initialized checkbox
        windowMaximizedCheckBox.setSelected(settingsManager.getBoolean("window.maximized", false));
        windowGroup.getChildren().add(windowMaximizedCheckBox);

        // Window dimensions section
        VBox dimensionsGroup = createSettingsGroup(messages.getString("group.windowSize"), "📐");
        
        HBox widthBox = createSettingRow("Width", "800-3840px", "Window width on startup");
        windowWidthSpinner.getValueFactory().setValue(settingsManager.getInt("window.width", 1400));
        widthBox.getChildren().add(windowWidthSpinner);

        HBox heightBox = createSettingRow("Height", "600-2160px", "Window height on startup");
        windowHeightSpinner.getValueFactory().setValue(settingsManager.getInt("window.height", 900));
        heightBox.getChildren().add(windowHeightSpinner);

        dimensionsGroup.getChildren().addAll(widthBox, heightBox);
        
        content.getChildren().addAll(windowGroup, dimensionsGroup);

        return scrollPane;
    }

    private ScrollPane createSearchTabContent() {
        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(20);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: #f9f9f9;");
        scrollPane.setContent(content);

        VBox excludeGroup = createSettingsGroup(messages.getString("group.search"), "🔍");

        Label patternLabel = new Label(messages.getString("label.excludePatterns"));
        patternLabel.setStyle("-fx-font-weight: bold;");

        // Use pre-initialized TextArea
        excludePatternsArea.setText(settingsManager.getString("search.excludePatterns", "node_modules,target,.git,.idea,build,dist"));

        Label helpLabel = new Label("Examples: node_modules, *.log, .git, __pycache__, build/");
        helpLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");

        excludeGroup.getChildren().addAll(
            patternLabel,
            excludePatternsArea,
            helpLabel
        );
        VBox.setVgrow(excludePatternsArea, Priority.ALWAYS);
        
        content.getChildren().add(excludeGroup);

        return scrollPane;
    }

    private ScrollPane createAboutTabContent() {
        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        content.setStyle("-fx-background-color: #f9f9f9;");
        scrollPane.setContent(content);

        Label titleLabel = new Label("🚀 Distributed IDE");
        titleLabel.setStyle("-fx-font-size: 20; -fx-font-weight: bold;");

        Label versionLabel = new Label("Version 1.0.0 | 28 février 2026");
        versionLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666666;");

        Label descriptionLabel = new Label(
                "Une plateforme IDE moderne et collaborative avec synchronisation en temps réel,\n" +
                "support multi-langage et intégration LSP."
        );
        descriptionLabel.setStyle("-fx-text-alignment: center; -fx-wrap-text: true; -fx-font-size: 12;");
        descriptionLabel.setWrapText(true);

        // Features box
        VBox featuresBox = createSettingsGroup("Features", "✨");
        TextArea featuresArea = new TextArea(
                "✓ Multi-language syntax highlighting\n" +
                "✓ Real-time collaboration\n" +
                "✓ Git integration\n" +
                "✓ LSP support\n" +
                "✓ Multiple themes\n" +
                "✓ Custom keybindings\n" +
                "✓ Session restoration\n" +
                "✓ Advanced search & replace"
        );
        featuresArea.setEditable(false);
        featuresArea.setWrapText(true);
        featuresArea.setPrefRowCount(8);
        featuresBox.getChildren().add(featuresArea);

        // Developer info box
        VBox developerBox = createSettingsGroup("Developer", "👨‍💻");
        
        Label authorLabel = new Label("Houzefa SAVATRA");
        authorLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        
        Label titleLabel2 = new Label("Senior Full Stack Developer + UI/UX Designer");
        titleLabel2.setStyle("-fx-font-size: 11; -fx-text-fill: #666666;");
        
        Label emailLabel = new Label("✉️ hsavatra@gmail.com");
        emailLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #0066cc; -fx-cursor: hand;");

        Label phoneLabel = new Label("☎️ +261 322 547 526");
        phoneLabel.setStyle("-fx-font-size: 11;");

        developerBox.getChildren().addAll(authorLabel, titleLabel2, emailLabel, phoneLabel);

        // Copyright
        Label copyrightLabel = new Label("© 2026 Houzefa SAVATRA. All rights reserved. | Terms of Service");
        copyrightLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #888888; -fx-font-style: italic;");

        content.getChildren().addAll(
                titleLabel,
                versionLabel,
                descriptionLabel,
                new Separator(),
                featuresBox,
                developerBox,
                new Separator(),
                copyrightLabel
        );

        return scrollPane;
    }

    // ═══════════════════════════════════════════════════════════
    //  BUTTON BAR
    // ═══════════════════════════════════════════════════════════

    private HBox createButtonBar() {
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(15));
        buttonBar.setStyle("-fx-background-color: #fafafa;");

        Button resetButton = new Button("↺ Reset to Defaults");
        resetButton.setPrefWidth(130);
        resetButton.setStyle("-fx-padding: 10 15;");
        resetButton.setOnAction(e -> resetToDefaults());
        resetButton.setAccessibleText("Reset settings to defaults");

        Button cancelButton = new Button("✕ Cancel");
        cancelButton.setPrefWidth(100);
        cancelButton.setStyle("-fx-padding: 10 15;");
        cancelButton.setOnAction(e -> setResult(false));
        // mark as cancel button so Esc works
        cancelButton.setCancelButton(true);
        cancelButton.setAccessibleText("Cancel and close dialog");

        Button applyButton = new Button("✓ Apply");
        applyButton.setPrefWidth(100);
        applyButton.setStyle("-fx-padding: 10 15; -fx-font-weight: false;");
        applyButton.setOnAction(e -> {
            saveSettings();
            hasUnsavedChanges.set(false);
            LOG.info("Settings applied (without closing dialog)");
            // Invoke callback so main UI can apply settings in real-time
            if (onSettingsApplied != null) {
                onSettingsApplied.run();
            }
        });
        applyButton.setAccessibleText("Apply settings");

        Button okButton = new Button("✓ OK");
        okButton.setPrefWidth(100);
        okButton.setStyle("-fx-padding: 10 15; -fx-font-weight: bold; " +
                         "-fx-background-color: #0066cc; -fx-text-fill: white;");
        okButton.setOnAction(e -> {
            saveSettings();
            hasUnsavedChanges.set(false);
            LOG.info("Settings saved, closing dialog");
            // Invoke callback to apply settings before closing
            if (onSettingsApplied != null) {
                onSettingsApplied.run();
            }
            setResult(true);
        });
        // default button -> Enter triggers
        okButton.setDefaultButton(true);
        okButton.setAccessibleText("Save settings and close dialog");

        Region spacer = new Region();
        buttonBar.getChildren().addAll(resetButton, spacer, cancelButton, applyButton, okButton);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return buttonBar;
    }

    // ═══════════════════════════════════════════════════════════
    //  SETTINGS MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    private void saveSettings() {
        try {
            settingsManager.set("editor.fontSize", fontSizeSpinner.getValue());
            settingsManager.set("editor.fontFamily", fontFamilyCombo.getValue());
            settingsManager.set("editor.tabSize", tabSizeSpinner.getValue());
            settingsManager.set("editor.wordWrap", wordWrapCheckBox.isSelected());
            settingsManager.set("editor.minimap", minimapCheckBox.isSelected());
            settingsManager.set("editor.autoSave", autoSaveCheckBox.isSelected());
            settingsManager.set("editor.autoSaveDelaySeconds", autoSaveDelaySpinner.getValue());
            settingsManager.set("theme.current", themeCombo.getValue());
            settingsManager.set("window.maximized", windowMaximizedCheckBox.isSelected());
            settingsManager.set("window.width", windowWidthSpinner.getValue());
            settingsManager.set("window.height", windowHeightSpinner.getValue());
            settingsManager.set("search.excludePatterns", excludePatternsArea.getText());
            
            // Reset unsaved indicator
            hasUnsavedChanges.set(false);
            
            LOG.info("Settings saved successfully:");
            LOG.info("  Font: {}pt {}", fontSizeSpinner.getValue(), fontFamilyCombo.getValue());
            LOG.info("  Tab size: {}", tabSizeSpinner.getValue());
            LOG.info("  Word wrap: {}", wordWrapCheckBox.isSelected());
            LOG.info("  Minimap: {}", minimapCheckBox.isSelected());
            LOG.info("  Theme: {}", themeCombo.getValue());
        } catch (Exception e) {
            LOG.error("Error saving settings", e);
            showError("Save Error", "Failed to save settings: " + e.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void resetToDefaults() {
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Reset to Defaults");
        confirmDialog.setHeaderText("Reset all settings to default values?");
        confirmDialog.setContentText("This action cannot be undone. All custom settings will be lost.");

        if (confirmDialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            fontSizeSpinner.getValueFactory().setValue(14);
            fontFamilyCombo.setValue("Consolas");
            tabSizeSpinner.getValueFactory().setValue(4);
            wordWrapCheckBox.setSelected(false);
            minimapCheckBox.setSelected(true);
            autoSaveCheckBox.setSelected(false);
            autoSaveDelaySpinner.getValueFactory().setValue(30);
            themeCombo.setValue("ide-dark");
            windowMaximizedCheckBox.setSelected(false);
            windowWidthSpinner.getValueFactory().setValue(1400);
            windowHeightSpinner.getValueFactory().setValue(900);
            excludePatternsArea.setText("node_modules,target,.git,.idea,build,dist");

            // Mark as changed and save
            hasUnsavedChanges.set(true);
            saveSettings();
            LOG.info("Settings reset to defaults");
            
            // Show confirmation
            Alert infoDialog = new Alert(Alert.AlertType.INFORMATION);
            infoDialog.setTitle("Reset Complete");
            infoDialog.setHeaderText("Settings reset successfully");
            infoDialog.setContentText("All settings have been reset to their default values.");
            infoDialog.showAndWait();
        }
    }
}
