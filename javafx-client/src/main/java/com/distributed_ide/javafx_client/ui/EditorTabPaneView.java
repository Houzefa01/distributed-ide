package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.javafx_client.model.EditorTabModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Multi-tab editor (TabPane). Each tab = file with TextArea.
 */
public class EditorTabPaneView {

    private final TabPane tabPane;
    private final Map<String, Tab> pathToTab = new HashMap<>();
    private final Map<String, TextArea> pathToEditor = new HashMap<>();
    private Consumer<String> onContentChanged;
    private BiConsumer<String, String> onSyncContentUpdate;
    private volatile boolean suppressContentChangedEvent;

    public EditorTabPaneView() {
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
    }

    public TabPane getView() {
        return tabPane;
    }

    /**
     * Appelé quand l'utilisateur modifie le contenu (pour sync).
     */
    public void setOnContentChanged(Consumer<String> handler) {
        this.onContentChanged = handler;
    }

    /**
     * Appelé pour mettre à jour le contenu d'un onglet depuis le sync (sans déclencher onContentChanged).
     */
    public void setOnSyncContentUpdate(BiConsumer<String, String> handler) {
        this.onSyncContentUpdate = handler;
    }

    public void openFile(Path path, String content) {
        if (path == null) return;
        String pathStr = path.toString();
        if (pathToTab.containsKey(pathStr)) {
            tabPane.getSelectionModel().select(pathToTab.get(pathStr));
            return;
        }
        TextArea area = new TextArea(content != null ? content : "");
        area.setWrapText(false);
        area.getStyleClass().add("code-editor");
        area.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!suppressContentChangedEvent && onContentChanged != null) onContentChanged.accept(pathStr);
        });

        Tab tab = new Tab(null, area);
        tab.setUserData(pathStr);
        
        // Create custom tab header with close button
        HBox tabHeader = createTabHeader(path, tab);
        tab.setGraphic(tabHeader);
        
        tab.setOnCloseRequest(e -> {
            pathToTab.remove(pathStr);
            pathToEditor.remove(pathStr);
        });

        pathToTab.put(pathStr, tab);
        pathToEditor.put(pathStr, area);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    /**
     * Met à jour le contenu d'un onglet (ex. après réception sync) sans déclencher le listener de changement.
     */
    public void setContent(String pathStr, String content) {
        TextArea area = pathToEditor.get(pathStr);
        if (area != null) {
            suppressContentChangedEvent = true;
            try {
                area.setText(content != null ? content : "");
            } finally {
                suppressContentChangedEvent = false;
            }
            if (onSyncContentUpdate != null) onSyncContentUpdate.accept(pathStr, content);
        }
    }

    public String getCurrentPath() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        return tab != null && tab.getUserData() != null ? tab.getUserData().toString() : null;
    }

    public String getContent(String pathStr) {
        TextArea area = pathToEditor.get(pathStr);
        return area != null ? area.getText() : null;
    }

    public void closeTab(String pathStr) {
        Tab tab = pathToTab.remove(pathStr);
        if (tab != null) tabPane.getTabs().remove(tab);
        pathToEditor.remove(pathStr);
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
