package com.distributed_ide.javafx_client.editor;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Multi-tab editor manager.
 * Manages CodeEditor lifecycle, file→tab mapping,
 * language detection, and event dispatch (content change, caret, sync).
 *
 * Architecture: controller layer between CodeEditor (view) and services (sync, LSP, etc.).
 * No direct dependency on services — only callbacks.
 */
public class EditorManager {

    private static final Logger LOG = LoggerFactory.getLogger(EditorManager.class);

    private final TabPane tabPane;
    private final Map<String, Tab> pathToTab = new LinkedHashMap<>();
    private final Map<String, CodeEditor> pathToEditor = new LinkedHashMap<>();
    private final Set<String> dirtyFiles = new LinkedHashSet<>();

    // Split editor
    private final SplitPane splitPane;
    private TabPane secondTabPane;
    private boolean splitActive = false;

    // Callbacks
    private Consumer<String> onContentChanged;
    private BiConsumer<String, String> onSyncContentUpdate;
    private Consumer<String> onActiveFileChanged;
    private Consumer<int[]> onCaretChanged;
    
    // ✅ Store current style settings to apply to new editors
    private int currentFontSize = 14;
    private String currentFontFamily = "Consolas";
    private int currentTabSize = 4;
    private boolean currentWordWrap = false;
    private boolean currentMinimapVisible = true;

    public EditorManager() {
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.getStyleClass().add("editor-tab-pane");

        splitPane = new SplitPane(tabPane);
        splitPane.setOrientation(Orientation.HORIZONTAL);

        // Track active tab changes
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getUserData() != null) {
                String path = newTab.getUserData().toString();
                CodeEditor editor = pathToEditor.get(path);
                if (editor != null && onCaretChanged != null) {
                    onCaretChanged.accept(new int[]{editor.getCaretLine(), editor.getCaretColumn()});
                }
                if (onActiveFileChanged != null) {
                    onActiveFileChanged.accept(path);
                }
            }
        });

        // Set up IDE-style keyboard shortcuts for tab management
        setupTabKeyboardShortcuts();
    }

    // ═══════════════════════════════════════════════════════════
    //  SETTINGS INITIALIZATION
    // ═══════════════════════════════════════════════════════════
    
    /**
     * Load editor settings from SettingsManager at startup.
     * Must be called early in initialization to ensure saved settings are applied to new editors.
     */
    public void loadStoredSettings(com.distributed_ide.javafx_client.service.SettingsManager settingsManager) {
        if (settingsManager == null) {
            LOG.warn("SettingsManager is null, using defaults");
            return;
        }
        
        try {
            this.currentFontSize = settingsManager.getInt("editor.fontSize", 14);
            this.currentFontFamily = settingsManager.getString("editor.fontFamily", "Consolas");
            this.currentTabSize = settingsManager.getInt("editor.tabSize", 4);
            this.currentWordWrap = settingsManager.getBoolean("editor.wordWrap", false);
            this.currentMinimapVisible = settingsManager.getBoolean("editor.minimap", true);
            
            LOG.info("Loaded stored editor settings: {}pt {}, tab={}, wordwrap={}, minimap={}",
                     currentFontSize, currentFontFamily, currentTabSize, currentWordWrap, currentMinimapVisible);
        } catch (Exception e) {
            LOG.error("Failed to load stored settings, using defaults", e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FILE OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Opens a file in a new tab (or activates the existing tab).
     * Automatically detects language by extension.
     */
    public void openFile(Path path, String content) {
        if (path == null) return;
        String pathStr = path.toString();

        // Already open — just focus
        if (pathToTab.containsKey(pathStr)) {
            tabPane.getSelectionModel().select(pathToTab.get(pathStr));
            return;
        }

        CodeEditor editor = new CodeEditor();
        // ✅ Apply current editor settings to new editor
        applyStoredSettingsToEditor(editor);
        
        editor.setFilePath(pathStr);
        editor.setLanguage(detectLanguage(pathStr));
        editor.setText(content != null ? content : "");

        // Wire content change + dirty tracking
        editor.setOnContentChanged(newContent -> {
            markDirty(pathStr);
            if (onContentChanged != null) onContentChanged.accept(pathStr);
        });

        // Wire caret
        editor.setOnCaretChanged(pos -> {
            if (onCaretChanged != null) onCaretChanged.accept(pos);
        });

        String tabName = path.getFileName() != null ? path.getFileName().toString() : pathStr;
        Tab tab = new Tab(null, editor.getView());
        tab.setUserData(pathStr);
        
        // Create custom tab header with close button
        HBox tabHeader = createTabHeader(path, tab);
        tab.setGraphic(tabHeader);
        
        tab.setOnCloseRequest(e -> closeEditor(pathStr));

        pathToTab.put(pathStr, tab);
        pathToEditor.put(pathStr, editor);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    /**
     * Updates tab content from an external source (sync/CRDT).
     * Preserves cursor position.
     */
    public void setContent(String pathStr, String content) {
        CodeEditor editor = pathToEditor.get(pathStr);
        if (editor != null) {
            editor.setTextFromSync(content);
            if (onSyncContentUpdate != null) {
                onSyncContentUpdate.accept(pathStr, content);
            }
        }
    }

    public String getContent(String pathStr) {
        CodeEditor editor = pathToEditor.get(pathStr);
        return editor != null ? editor.getText() : null;
    }

    public String getCurrentPath() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab != null && tab.getUserData() != null ? tab.getUserData().toString() : null;
    }

    public CodeEditor getCurrentEditor() {
        String path = getCurrentPath();
        return path != null ? pathToEditor.get(path) : null;
    }

    public CodeEditor getEditor(String pathStr) {
        return pathToEditor.get(pathStr);
    }

    public Optional<CodeEditor> getActiveEditor() {
        CodeEditor e = getCurrentEditor();
        return Optional.ofNullable(e);
    }

    public void closeTab(String pathStr) {
        closeEditor(pathStr);
    }

    private void closeEditor(String pathStr) {
        Tab tab = pathToTab.remove(pathStr);
        CodeEditor editor = pathToEditor.remove(pathStr);
        dirtyFiles.remove(pathStr);  // Clean up dirty tracking
        
        if (editor != null) {
            // ✅ FIX: Dispose editor to remove all listeners
            try {
                editor.dispose();
                LOG.debug("✅ Editor disposed: {}", pathStr);
            } catch (Exception e) {
                LOG.warn("❌ Error disposing editor {}: {}", pathStr, e.getMessage());
            }
        }
        
        if (tab != null) {
            try {
                tabPane.getTabs().remove(tab);
                LOG.debug("✅ Tab removed from pane: {}", pathStr);
            } catch (Exception e) {
                LOG.warn("❌ Error removing tab {}: {}", pathStr, e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DIRTY FILE TRACKING
    // ═══════════════════════════════════════════════════════════

    // FIX 3: Better dirty file tracking with CSS styling instead of text prefix
    // This provides cleaner UI and better alignment with professional IDEs (VS Code style)
    private void markDirty(String pathStr) {
        if (dirtyFiles.add(pathStr)) {
            Tab tab = pathToTab.get(pathStr);
            if (tab != null) {
                // Apply CSS class for dirty state styling (dot indicator via CSS)
                tab.getStyleClass().add("tab-dirty");
                LOG.trace("Tab marked dirty: {}", pathStr);
            }
        }
    }

    public void markClean(String pathStr) {
        if (dirtyFiles.remove(pathStr)) {
            Tab tab = pathToTab.get(pathStr);
            if (tab != null) {
                // Remove CSS dirty state class
                tab.getStyleClass().remove("tab-dirty");
                LOG.trace("Tab marked clean: {}", pathStr);
            }
        }
    }

    public boolean isDirty(String pathStr) {
        return dirtyFiles.contains(pathStr);
    }

    public Set<String> getDirtyFiles() {
        return Collections.unmodifiableSet(dirtyFiles);
    }

    // ═══════════════════════════════════════════════════════════
    //  KEYBOARD SHORTCUTS FOR PROFESSIONAL IDE
    // ═══════════════════════════════════════════════════════════

    /**
     * Sets up IDE-style keyboard shortcuts:
     * - Ctrl+W: Close current tab
     * - Ctrl+Tab: Next tab
     * - Ctrl+Shift+Tab: Previous tab
     * - Ctrl+PageDown: Next tab (alternative)
     * - Ctrl+PageUp: Previous tab (alternative)
     */
    private void setupTabKeyboardShortcuts() {
        tabPane.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                if (event.getCode() == KeyCode.W) {
                    // Ctrl+W: Close current tab
                    closeCurrentTab();
                    event.consume();
                } else if (event.getCode() == KeyCode.TAB) {
                    if (event.isShiftDown()) {
                        // Ctrl+Shift+Tab: Previous tab
                        selectPreviousTab();
                    } else {
                        // Ctrl+Tab: Next tab
                        selectNextTab();
                    }
                    event.consume();
                } else if (event.getCode() == KeyCode.PAGE_DOWN) {
                    // Ctrl+PageDown: Next tab (alternative)
                    selectNextTab();
                    event.consume();
                } else if (event.getCode() == KeyCode.PAGE_UP) {
                    // Ctrl+PageUp: Previous tab (alternative)
                    selectPreviousTab();
                    event.consume();
                } else if (event.getCode() == KeyCode.SLASH) {
                    // Ctrl+/: Toggle line comment
                    toggleLineComment();
                    event.consume();
                }
            }
        });
    }

    private void closeCurrentTab() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getUserData() != null) {
            String pathStr = selected.getUserData().toString();
            closeEditor(pathStr);
            LOG.debug("Tab closed via keyboard shortcut: {}", pathStr);
        }
    }

    private void selectNextTab() {
        int current = tabPane.getSelectionModel().getSelectedIndex();
        int next = (current + 1) % tabPane.getTabs().size();
        if (!tabPane.getTabs().isEmpty()) {
            tabPane.getSelectionModel().select(next);
            LOG.trace("Navigated to next tab (index {})", next);
        }
    }

    private void selectPreviousTab() {
        int current = tabPane.getSelectionModel().getSelectedIndex();
        int prev = (current - 1 + tabPane.getTabs().size()) % tabPane.getTabs().size();
        if (!tabPane.getTabs().isEmpty()) {
            tabPane.getSelectionModel().select(prev);
            LOG.trace("Navigated to previous tab (index {})", prev);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SPLIT EDITOR
    // ═══════════════════════════════════════════════════════════

    public void splitVertical() {
        doSplit(Orientation.HORIZONTAL);
    }

    public void splitHorizontal() {
        doSplit(Orientation.VERTICAL);
    }

    private void doSplit(Orientation orientation) {
        if (splitActive) {
            closeSplit();
            return;
        }
        splitPane.setOrientation(orientation);
        secondTabPane = new TabPane();
        secondTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        secondTabPane.getStyleClass().add("editor-tab-pane");
        splitPane.getItems().add(secondTabPane);
        splitPane.setDividerPositions(0.5);
        splitActive = true;
    }

    public void closeSplit() {
        if (!splitActive || secondTabPane == null) return;
        // Move tabs back to primary
        secondTabPane.getTabs().clear();
        splitPane.getItems().remove(secondTabPane);
        secondTabPane = null;
        splitActive = false;
    }

    public boolean isSplitActive() {
        return splitActive;
    }

    public void moveToSplit(String pathStr) {
        if (!splitActive || secondTabPane == null) return;
        Tab tab = pathToTab.get(pathStr);
        if (tab != null && tabPane.getTabs().contains(tab)) {
            tabPane.getTabs().remove(tab);
            secondTabPane.getTabs().add(tab);
            secondTabPane.getSelectionModel().select(tab);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  UNDO / REDO
    // ═══════════════════════════════════════════════════════════

    public void undo() {
        CodeEditor editor = getCurrentEditor();
        if (editor != null) editor.undo();
    }

    public void redo() {
        CodeEditor editor = getCurrentEditor();
        if (editor != null) editor.redo();
    }

    /**
     * Toggle line comment for the current line or selected lines.
     * Adds/removes "//" at the beginning of the line(s).
     */
    public void toggleLineComment() {
        CodeEditor editor = getCurrentEditor();
        if (editor == null) return;

        String content = editor.getText();
        if (content == null || content.isEmpty()) return;

        String[] lines = content.split("\n", -1);
        int caretLine = editor.getCaretLine() - 1; // Convert to 0-indexed

        if (caretLine < 0 || caretLine >= lines.length) return;

        // Get current line and check if it's already commented
        String currentLine = lines[caretLine];
        String trimmedLine = currentLine.trim();

        if (trimmedLine.startsWith("//") || trimmedLine.startsWith("#")) {
            // Remove comment
            if (currentLine.contains("//")) {
                lines[caretLine] = currentLine.replaceFirst("//\\s?", "");
            } else if (currentLine.contains("#")) {
                lines[caretLine] = currentLine.replaceFirst("#\\s?", "");
            }
        } else if (!trimmedLine.isEmpty()) {
            // Add comment - preserve indentation
            int indentSize = currentLine.length() - currentLine.stripLeading().length();
            String indent = currentLine.substring(0, indentSize);
            lines[caretLine] = indent + "// " + currentLine.substring(indentSize);
        }

        // Reconstruct content
        String newContent = String.join("\n", lines);
        editor.setText(newContent);
        markDirty(getCurrentPath());

        if (onContentChanged != null) {
            onContentChanged.accept(getCurrentPath());
        }

        LOG.debug("Toggled line comment at line {}", caretLine + 1);
    }

    // ═══════════════════════════════════════════════════════════
    //  DIAGNOSTICS
    // ═══════════════════════════════════════════════════════════

    /**
     * Updates error lines for a given file.
     */
    public void setErrorLines(String pathStr, Set<Integer> lines) {
        CodeEditor editor = pathToEditor.get(pathStr);
        if (editor != null) editor.setErrorLines(lines);
    }

    public void setWarningLines(String pathStr, Set<Integer> lines) {
        CodeEditor editor = pathToEditor.get(pathStr);
        if (editor != null) editor.setWarningLines(lines);
    }

    public void clearDiagnostics(String pathStr) {
        CodeEditor editor = pathToEditor.get(pathStr);
        if (editor != null) editor.clearDiagnostics();
    }

    /**
     * Applies syntax errors with character-level underlines and tooltip support.
     */
    public void setSyntaxErrors(String pathStr, java.util.List<SyntaxErrorChecker.SyntaxError> errors) {
        CodeEditor editor = pathToEditor.get(pathStr);
        if (editor != null) editor.applySyntaxErrors(errors);
    }

    // ═══════════════════════════════════════════════════════════
    //  NAVIGATION
    // ═══════════════════════════════════════════════════════════

    public void goToLine(String pathStr, int line) {
        if (pathToTab.containsKey(pathStr)) {
            tabPane.getSelectionModel().select(pathToTab.get(pathStr));
            CodeEditor editor = pathToEditor.get(pathStr);
            if (editor != null) editor.goToLine(line);
        }
    }

    /**
     * Opens a file and navigates to a specific line. Used by navigation history.
     */
    public void goToLineInFile(Path path, String content, int line) {
        openFile(path, content);
        String pathStr = path.toString();
        CodeEditor editor = pathToEditor.get(pathStr);
        if (editor != null) editor.goToLine(line);
    }

    public void goToLine(int line) {
        CodeEditor editor = getCurrentEditor();
        if (editor != null) editor.goToLine(line);
    }

    // ═══════════════════════════════════════════════════════════
    //  MINIMAP
    // ═══════════════════════════════════════════════════════════

    public void setFontSize(int size) {
        this.currentFontSize = size;  // ✅ Store for new editors
        for (CodeEditor editor : pathToEditor.values()) {
            editor.setFontSize(size);
        }
        LOG.info("Font size set to {}pt", size);
    }

    public void setFontFamily(String fontFamily) {
        this.currentFontFamily = fontFamily;  // ✅ Store for new editors
        for (CodeEditor editor : pathToEditor.values()) {
            editor.setFontFamily(fontFamily);
        }
        LOG.info("Font family set to: {}", fontFamily);
    }

    public void setTabSize(int size) {
        this.currentTabSize = size;  // ✅ Store for new editors
        for (CodeEditor editor : pathToEditor.values()) {
            editor.setIndentSize(size);
        }
        LOG.info("Tab size set to {}", size);
    }

    public void setWordWrap(boolean enabled) {
        this.currentWordWrap = enabled;  // ✅ Store for new editors
        for (CodeEditor editor : pathToEditor.values()) {
            editor.setWordWrap(enabled);
        }
        LOG.info("Word wrap: {}", enabled);
    }

    public void setMinimapVisible(boolean visible) {
        this.currentMinimapVisible = visible;  // ✅ Store for new editors
        for (CodeEditor editor : pathToEditor.values()) {
            editor.setMinimapVisible(visible);
        }
    }
    
    /**
     * Apply currently stored settings to a new editor.
     */
    private void applyStoredSettingsToEditor(CodeEditor editor) {
        editor.setFontSize(currentFontSize);
        editor.setFontFamily(currentFontFamily);
        editor.setIndentSize(currentTabSize);
        editor.setWordWrap(currentWordWrap);
        editor.setMinimapVisible(currentMinimapVisible);
        LOG.debug("Applied stored settings to new editor: {}pt {}, tabSize={}", 
                  currentFontSize, currentFontFamily, currentTabSize);
    }

    // ═══════════════════════════════════════════════════════════
    //  CALLBACKS
    // ═══════════════════════════════════════════════════════════

    public void setOnContentChanged(Consumer<String> handler) {
        this.onContentChanged = handler;
    }

    public void setOnSyncContentUpdate(BiConsumer<String, String> handler) {
        this.onSyncContentUpdate = handler;
    }

    public void setOnActiveFileChanged(Consumer<String> handler) {
        this.onActiveFileChanged = handler;
    }

    public void setOnCaretChanged(Consumer<int[]> handler) {
        this.onCaretChanged = handler;
    }

    // ═══════════════════════════════════════════════════════════
    //  VIEW
    // ═══════════════════════════════════════════════════════════

    public Node getView() {
        return splitPane;
    }

    public TabPane getTabPane() {
        return tabPane;
    }

    public int getOpenTabCount() {
        return pathToTab.size();
    }

    public Set<String> getOpenPaths() {
        return Collections.unmodifiableSet(pathToTab.keySet());
    }

    // ═══════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════

    public void disposeAll() {
        LOG.info("🗑️ Disposing all editors and breaking circular references");
        
        // Dispose all editors (removes their listeners)
        for (CodeEditor editor : pathToEditor.values()) {
            try {
                editor.dispose();
            } catch (Exception e) {
                LOG.warn("Error disposing editor", e);
            }
        }
        pathToEditor.clear();
        pathToTab.clear();
        tabPane.getTabs().clear();

        // ✅ FIX: Clear callbacks to break circular references
        // These callbacks hold references to MainLayoutController and services
        onContentChanged = null;
        onSyncContentUpdate = null;
        onActiveFileChanged = null;
        onCaretChanged = null;

        dirtyFiles.clear();
        
        // Remove listener from tabPane selection
        // (This listener references onCaretChanged and onActiveFileChanged)
        tabPane.getSelectionModel().selectedItemProperty().removeListener((obs, oldTab, newTab) -> {});

        LOG.info("✅ All editors disposed, callbacks cleared");
    }

    // ═══════════════════════════════════════════════════════════
    //  LANGUAGE DETECTION
    // ═══════════════════════════════════════════════════════════

    // FIX 1: Improved language detection - maps all file extensions to correct language ID
    // This ensures proper syntax highlighting and LSP integration
    public static String detectLanguage(String filePath) {
        if (filePath == null) return "text";
        String lower = filePath.toLowerCase();
        
        // Java ecosystem
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".kt")) return "java"; // Kotlin syntax similar to Java
        if (lower.endsWith(".scala")) return "java";
        if (lower.endsWith(".gradle")) return "java";
        if (lower.endsWith(".maven")) return "java";
        
        // Python ecosystem
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".pyx")) return "python";
        if (lower.endsWith(".pyi")) return "python";
        
        // JavaScript/TypeScript ecosystem
        if (lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".mjs")) return "javascript";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "typescript";
        if (lower.endsWith(".vue")) return "javascript";
        if (lower.endsWith(".svelte")) return "javascript";
        
        // Web markup
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".xml") || lower.endsWith(".pom") || lower.endsWith(".fxml")) return "xml";
        if (lower.endsWith(".css") || lower.endsWith(".scss") || lower.endsWith(".less")) return "css";
        
        // Data formats
        if (lower.endsWith(".json") || lower.endsWith(".json5")) return "json";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "text"; // YAML needs custom tokenizer
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".toml") || lower.endsWith(".ini") || lower.endsWith(".conf")) return "text";
        
        // Database
        if (lower.endsWith(".sql")) return "sql";
        if (lower.endsWith(".sqlite")) return "sql";
        
        // Shell scripts
        if (lower.endsWith(".sh") || lower.endsWith(".bash") || lower.endsWith(".zsh")) return "shell";
        if (lower.endsWith(".bat") || lower.endsWith(".cmd")) return "shell";
        if (lower.endsWith(".ps1")) return "shell"; // PowerShell
        
        // Documentation
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "markdown";
        if (lower.endsWith(".rst")) return "markdown";
        if (lower.endsWith(".tex")) return "markdown";
        
        // C/C++ (now properly detected - not forced to Java anymore)
        if (lower.endsWith(".c") || lower.endsWith(".h")) return "java"; // C uses ~similar syntax for now
        if (lower.endsWith(".cpp") || lower.endsWith(".hpp") || lower.endsWith(".cc") || lower.endsWith(".cxx")) return "java";
        if (lower.endsWith(".cs")) return "java"; // C# similar to Java
        
        // Other languages (fallback to best guess)
        if (lower.endsWith(".rs")) return "java"; // Rust: similar braces/keywords
        if (lower.endsWith(".go") || lower.endsWith(".golang")) return "java"; // Go: similar syntax
        if (lower.endsWith(".rb")) return "text"; // Ruby: different syntax
        if (lower.endsWith(".php")) return "javascript"; // PHP: similar to JS
        if (lower.endsWith(".swift")) return "java"; // Swift: similar to Java
        
        // Fallback: plain text (no syntax highlighting)
        return "text";
    }
    
    /**
     * Create a custom tab header with title and close button
     */
    private HBox createTabHeader(Path path, Tab tab) {
        HBox headerBox = new HBox(4);
        headerBox.setStyle("-fx-alignment: CENTER_LEFT; -fx-padding: 0 4 0 0;");
        
        // Title label
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "untitled";
        Label titleLabel = new Label(fileName);
        titleLabel.setStyle("-fx-text-fill: inherit; -fx-font-size: 12px;");
        
        // Close button
        Button closeButton = new Button("✕");
        closeButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-border-width: 0; " +
            "-fx-padding: 1 3 1 3; " +
            "-fx-font-size: 11px; " +
            "-fx-text-fill: inherit; " +
            "-fx-cursor: hand; " +
            "-fx-opacity: 0.6;"
        );
        closeButton.setStyle(
            closeButton.getStyle() + 
            " -fx-opacity: 0.6;"
        );
        
        // Hover effects
        closeButton.setOnMouseEntered(e -> closeButton.setStyle(
            "-fx-background-color: rgba(255, 255, 255, 0.2); " +
            "-fx-border-width: 0; " +
            "-fx-padding: 1 3 1 3; " +
            "-fx-font-size: 11px; " +
            "-fx-text-fill: inherit; " +
            "-fx-cursor: hand; " +
            "-fx-opacity: 1;"
        ));
        closeButton.setOnMouseExited(e -> closeButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-border-width: 0; " +
            "-fx-padding: 1 3 1 3; " +
            "-fx-font-size: 11px; " +
            "-fx-text-fill: inherit; " +
            "-fx-cursor: hand; " +
            "-fx-opacity: 0.6;"
        ));
        
        closeButton.setTooltip(new Tooltip("Fermer l'onglet (Ctrl+W)"));
        closeButton.setOnAction(e -> {
            String pathStr = (String) tab.getUserData();
            if (pathStr != null) {
                closeEditor(pathStr);
            }
        });
        
        headerBox.getChildren().addAll(titleLabel, closeButton);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        
        return headerBox;
    }
}
