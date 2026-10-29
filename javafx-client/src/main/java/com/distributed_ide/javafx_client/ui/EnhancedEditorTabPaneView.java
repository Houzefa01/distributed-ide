package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.javafx_client.model.EditorTabModel;
import com.distributed_ide.javafx_client.model.UndoRedoStack;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Enhanced thread-safe multi-tab editor with undo/redo, auto-save, and proper lifecycle management.
 * 
 * Key features:
 * - Thread-safe tab operations
 * - Undo/Redo functionality per tab
 * - Auto-save integration
 * - Proper resource cleanup
 * - Non-blocking operations
 * - Error handling and recovery
 */
public class EnhancedEditorTabPaneView {
    
    private static final Logger LOG = LoggerFactory.getLogger(EnhancedEditorTabPaneView.class);
    
    private final TabPane tabPane;
    private final Map<String, Tab> pathToTab = new ConcurrentHashMap<>();
    private final Map<String, EditorTabModel> pathToModel = new ConcurrentHashMap<>();
    private final Map<String, UndoRedoStack> pathToUndoStack = new ConcurrentHashMap<>();
    
    // Thread-safe state
    private final AtomicInteger tabCounter = new AtomicInteger(0);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    // Callbacks
    private volatile Consumer<String> onContentChanged;
    private volatile BiConsumer<String, String> onSyncContentUpdate;
    private volatile Consumer<String> onTabClosed;
    private volatile Consumer<String> onTabSelected;
    
    // Auto-save integration
    private volatile boolean autoSaveEnabled = true;
    private volatile long autoSaveDelay = 5000; // 5 seconds
    private final Map<String, Timer> autoSaveTimers = new ConcurrentHashMap<>();
    
    public EnhancedEditorTabPaneView() {
        this.tabPane = new TabPane();
        this.tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        
        setupTabPaneListeners();
    }
    
    /**
     * Get the JavaFX TabPane view.
     */
    
    public TabPane getView() {
        return tabPane;
    }
    
    /**
     * Set callback for content changes.
     */
    public void setOnContentChanged( Consumer<String> handler) {
        this.onContentChanged = handler;
    }
    
    /**
     * Set callback for sync content updates.
     */
    public void setOnSyncContentUpdate( BiConsumer<String, String> handler) {
        this.onSyncContentUpdate = handler;
    }
    
    /**
     * Set callback for tab closed events.
     */
    public void setOnTabClosed( Consumer<String> handler) {
        this.onTabClosed = handler;
    }
    
    /**
     * Set callback for tab selected events.
     */
    public void setOnTabSelected( Consumer<String> handler) {
        this.onTabSelected = handler;
    }
    
    /**
     * Open a file in a new tab (thread-safe).
     */
    
    public CompletableFuture<Boolean> openFile( Path path,  String content) {
        if (isShuttingDown.get()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String pathStr = path.toString();
                
                // Check if already open
                if (pathToTab.containsKey(pathStr)) {
                    Platform.runLater(() -> {
                        Tab existingTab = pathToTab.get(pathStr);
                        if (existingTab != null) {
                            tabPane.getSelectionModel().select(existingTab);
                        }
                    });
                    return true;
                }
                
                // Create new tab model
                EditorTabModel model = new EditorTabModel(pathStr, content != null ? content : "");
                UndoRedoStack undoStack = new UndoRedoStack();
                
                // Create UI components
                TextArea textArea = createTextArea(model, undoStack);
                VBox tabContent = createTabContent(textArea, model);
                
                // Create tab
                Tab tab = new Tab(null, tabContent);
                tab.setUserData(pathStr);
                
                // Create custom tab header with close button
                HBox tabHeader = createTabHeader(path, tab);
                tab.setGraphic(tabHeader);
                
                // Setup tab close handler
                setupTabCloseHandler(tab, model, undoStack);
                
                // Add to maps
                pathToTab.put(pathStr, tab);
                pathToModel.put(pathStr, model);
                pathToUndoStack.put(pathStr, undoStack);
                
                // Add to UI (must be on JavaFX thread)
                Platform.runLater(() -> {
                    if (!isShuttingDown.get()) {
                        tabPane.getTabs().add(tab);
                        tabPane.getSelectionModel().select(tab);
                        
                        // Start auto-save timer
                        startAutoSaveTimer(pathStr, model);
                        
                        // Notify tab selected
                        if (onTabSelected != null) {
                            onTabSelected.accept(pathStr);
                        }
                    }
                });
                
                LOG.info("Opened file in tab: {}", path);
                return true;
                
            } catch (Exception e) {
                LOG.error("Failed to open file: {}", path, e);
                Platform.runLater(() -> showErrorAlert("Failed to Open File", 
                    "Could not open file: " + path + "\nError: " + e.getMessage()));
                return false;
            }
        });
    }
    
    /**
     * Close a tab (thread-safe).
     */
    
    public CompletableFuture<Boolean> closeTab( String pathStr) {
        if (isShuttingDown.get()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Tab tab = pathToTab.remove(pathStr);
                EditorTabModel model = pathToModel.remove(pathStr);
                UndoRedoStack undoStack = pathToUndoStack.remove(pathStr);
                
                if (tab != null) {
                    // Stop auto-save timer
                    stopAutoSaveTimer(pathStr);
                    
                    // Remove from UI (must be on JavaFX thread)
                    Platform.runLater(() -> {
                        if (!isShuttingDown.get()) {
                            tabPane.getTabs().remove(tab);
                            
                            // Notify tab closed
                            if (onTabClosed != null) {
                                onTabClosed.accept(pathStr);
                            }
                        }
                    });
                    
                    LOG.info("Closed tab: {}", pathStr);
                    return true;
                }
                
                return false;
                
            } catch (Exception e) {
                LOG.error("Failed to close tab: {}", pathStr, e);
                return false;
            }
        });
    }
    
    /**
     * Close all tabs.
     */
    
    public CompletableFuture<Void> closeAllTabs() {
        List<String> paths = new ArrayList<>(pathToTab.keySet());
        List<CompletableFuture<Boolean>> closeTasks = new ArrayList<>();
        
        for (String path : paths) {
            closeTasks.add(closeTab(path));
        }
        
        return CompletableFuture.allOf(closeTasks.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Get content of a tab.
     */
    
    public String getContent( String pathStr) {
        EditorTabModel model = pathToModel.get(pathStr);
        return model != null ? model.getContent() : null;
    }
    
    /**
     * Set content of a tab (thread-safe).
     */
    
    public CompletableFuture<Void> setContent( String pathStr,  String content) {
        return CompletableFuture.runAsync(() -> {
            EditorTabModel model = pathToModel.get(pathStr);
            if (model != null) {
                model.setContent(content != null ? content : "");
                
                Platform.runLater(() -> {
                    Tab tab = pathToTab.get(pathStr);
                    if (tab != null && tab.getContent() instanceof VBox) {
                        VBox contentBox = (VBox) tab.getContent();
                        if (contentBox.getChildren().size() > 0 && contentBox.getChildren().get(0) instanceof TextArea) {
                            TextArea textArea = (TextArea) contentBox.getChildren().get(0);
                            model.setUpdating(true);
                            try {
                                textArea.setText(content != null ? content : "");
                            } finally {
                                model.setUpdating(false);
                            }
                        }
                    }
                });
                
                // Notify sync update
                if (onSyncContentUpdate != null) {
                    onSyncContentUpdate.accept(pathStr, content);
                }
            }
        });
    }
    
    /**
     * Get current selected tab path.
     */
    
    public String getCurrentPath() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        return selectedTab != null && selectedTab.getUserData() != null ? 
            selectedTab.getUserData().toString() : null;
    }
    
    /**
     * Get all tab contents.
     */
    
    public Map<String, String> getAllContent() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, EditorTabModel> entry : pathToModel.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getContent());
        }
        return result;
    }
    
    /**
     * Undo operation for current tab.
     */
    public void undo() {
        String currentPath = getCurrentPath();
        if (currentPath != null) {
            UndoRedoStack undoStack = pathToUndoStack.get(currentPath);
            EditorTabModel model = pathToModel.get(currentPath);
            
            if (undoStack != null && model != null && undoStack.canUndo()) {
                String previousContent = undoStack.undo();
                model.setContent(previousContent);
                
                Platform.runLater(() -> {
                    Tab tab = pathToTab.get(currentPath);
                    if (tab != null && tab.getContent() instanceof VBox) {
                        VBox contentBox = (VBox) tab.getContent();
                        if (contentBox.getChildren().size() > 0 && contentBox.getChildren().get(0) instanceof TextArea) {
                            TextArea textArea = (TextArea) contentBox.getChildren().get(0);
                            textArea.setText(previousContent);
                        }
                    }
                });
                
                if (onContentChanged != null) {
                    onContentChanged.accept(currentPath);
                }
            }
        }
    }
    
    /**
     * Redo operation for current tab.
     */
    public void redo() {
        String currentPath = getCurrentPath();
        if (currentPath != null) {
            UndoRedoStack undoStack = pathToUndoStack.get(currentPath);
            EditorTabModel model = pathToModel.get(currentPath);
            
            if (undoStack != null && model != null && undoStack.canRedo()) {
                String nextContent = undoStack.redo();
                model.setContent(nextContent);
                
                Platform.runLater(() -> {
                    Tab tab = pathToTab.get(currentPath);
                    if (tab != null && tab.getContent() instanceof VBox) {
                        VBox contentBox = (VBox) tab.getContent();
                        if (contentBox.getChildren().size() > 0 && contentBox.getChildren().get(0) instanceof TextArea) {
                            TextArea textArea = (TextArea) contentBox.getChildren().get(0);
                            textArea.setText(nextContent);
                        }
                    }
                });
                
                if (onContentChanged != null) {
                    onContentChanged.accept(currentPath);
                }
            }
        }
    }
    
    /**
     * Check if undo is available for current tab.
     */
    public boolean canUndo() {
        String currentPath = getCurrentPath();
        if (currentPath != null) {
            UndoRedoStack undoStack = pathToUndoStack.get(currentPath);
            return undoStack != null && undoStack.canUndo();
        }
        return false;
    }
    
    /**
     * Check if redo is available for current tab.
     */
    public boolean canRedo() {
        String currentPath = getCurrentPath();
        if (currentPath != null) {
            UndoRedoStack undoStack = pathToUndoStack.get(currentPath);
            return undoStack != null && undoStack.canRedo();
        }
        return false;
    }
    
    /**
     * Get tab count.
     */
    public int getTabCount() {
        return pathToTab.size();
    }
    
    /**
     * Set auto-save enabled.
     */
    public void setAutoSaveEnabled(boolean enabled) {
        this.autoSaveEnabled = enabled;
    }
    
    /**
     * Set auto-save delay.
     */
    public void setAutoSaveDelay(long delayMs) {
        this.autoSaveDelay = delayMs;
    }
    
    /**
     * Cleanup resources.
     */
    public void shutdown() {
        isShuttingDown.set(true);
        
        // Stop all auto-save timers
        for (String path : new ArrayList<>(autoSaveTimers.keySet())) {
            stopAutoSaveTimer(path);
        }
        
        // Clear all maps
        pathToTab.clear();
        pathToModel.clear();
        pathToUndoStack.clear();
        autoSaveTimers.clear();
        
        LOG.info("Enhanced editor tab pane shutdown complete");
    }
    
    // Private helper methods
    
    private void setupTabPaneListeners() {
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getUserData() != null) {
                String path = newTab.getUserData().toString();
                if (onTabSelected != null) {
                    onTabSelected.accept(path);
                }
            }
        });
    }
    
    
    private TextArea createTextArea( EditorTabModel model,  UndoRedoStack undoStack) {
        TextArea textArea = new TextArea(model.getContent());
        textArea.setWrapText(false);
        textArea.getStyleClass().add("code-editor");
        
        // Add text change listener for undo/redo
        textArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!model.isUpdating()) {
                undoStack.push(oldVal != null ? oldVal : "");
                model.setContent(newVal != null ? newVal : "");
                
                if (onContentChanged != null) {
                    onContentChanged.accept(model.getPath());
                }
            }
        });
        
        return textArea;
    }
    
    
    private VBox createTabContent( TextArea textArea,  EditorTabModel model) {
        VBox content = new VBox();
        VBox.setVgrow(textArea, Priority.ALWAYS);
        content.getChildren().add(textArea);
        return content;
    }
    
    private void setupTabCloseHandler( Tab tab,  EditorTabModel model,  UndoRedoStack undoStack) {
        tab.setOnCloseRequest(e -> {
            String path = model.getPath();
            
            // Check if content has unsaved changes
            if (model.hasUnsavedChanges()) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Unsaved Changes");
                    alert.setHeaderText("File has unsaved changes");
                    alert.setContentText("Do you want to save changes before closing?");
                    
                    ButtonType saveButton = new ButtonType("Save");
                    ButtonType dontSaveButton = new ButtonType("Don't Save");
                    ButtonType cancelButton = new ButtonType("Cancel");
                    
                    alert.getButtonTypes().setAll(saveButton, dontSaveButton, cancelButton);
                    
                    Optional<ButtonType> result = alert.showAndWait();
                    
                    if (result.isPresent()) {
                        if (result.get() == saveButton) {
                            // Save and close
                            if (onContentChanged != null) {
                                onContentChanged.accept(path);
                            }
                            closeTab(path);
                        } else if (result.get() == dontSaveButton) {
                            // Close without saving
                            closeTab(path);
                        }
                        // Cancel - do nothing
                    }
                });
            } else {
                closeTab(path);
            }
        });
    }
    
    
    private String getTabTitle( Path path) {
        String fileName = path.getFileName().toString();
        String title = fileName;
        
        // Add unsaved indicator
        EditorTabModel model = pathToModel.get(path.toString());
        if (model != null && model.hasUnsavedChanges()) {
            title += " *";
        }
        
        return title;
    }
    
    private void startAutoSaveTimer( String pathStr,  EditorTabModel model) {
        if (!autoSaveEnabled) {
            return;
        }
        
        stopAutoSaveTimer(pathStr);
        
        Timer timer = new Timer("auto-save-" + pathStr, true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (model.hasUnsavedChanges() && onContentChanged != null) {
                    onContentChanged.accept(pathStr);
                    model.setUnsavedChanges(false);
                    LOG.debug("Auto-saved file: {}", pathStr);
                }
            }
        }, autoSaveDelay, autoSaveDelay);
        
        autoSaveTimers.put(pathStr, timer);
    }
    
    private void stopAutoSaveTimer( String pathStr) {
        Timer timer = autoSaveTimers.remove(pathStr);
        if (timer != null) {
            timer.cancel();
        }
    }
    
    private void showErrorAlert( String title,  String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
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
                closeTab(pathStr);
            }
        });
        
        headerBox.getChildren().addAll(titleLabel, closeButton);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        
        return headerBox;
    }
}
