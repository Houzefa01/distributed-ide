package com.distributed_ide.javafx_client.ui.settings;

import com.distributed_ide.plugin.LoadedPlugin;
import com.distributed_ide.plugin.PluginManager;
import com.distributed_ide.plugin.PluginState;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Plugin settings view for managing installed plugins.
 */
public class PluginSettingsView {
    
    private static final Logger LOG = LoggerFactory.getLogger(PluginSettingsView.class);
    
    private final PluginManager pluginManager;
    private final VBox root;
    private TableView<PluginItem> pluginTable;
    private final ObservableList<PluginItem> pluginItems;
    private final Label statusLabel;
    
    public PluginSettingsView( PluginManager pluginManager) {
        this.pluginManager = pluginManager;
        this.pluginItems = FXCollections.observableArrayList();
        this.root = createView();
        this.pluginTable = createPluginTable();
        this.statusLabel = new Label("Loading plugins...");
        
        setupView();
        loadPlugins();
    }
    
    private VBox createView() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        vbox.getStyleClass().add("plugin-settings");
        
        // Header
        Label header = new Label("Plugins");
        header.getStyleClass().add("settings-header");
        
        // Status label
        statusLabel.getStyleClass().add("status-label");
        
        // Plugin table
        pluginTable = createPluginTable();
        VBox.setVgrow(pluginTable, Priority.ALWAYS);
        
        // Button bar
        HBox buttonBar = createButtonBar();
        
        vbox.getChildren().addAll(header, statusLabel, pluginTable, buttonBar);
        return vbox;
    }
    
    private TableView<PluginItem> createPluginTable() {
        TableView<PluginItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        // Name column
        TableColumn<PluginItem, String> nameCol = new TableColumn<>("Plugin");
        nameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name));
        nameCol.setPrefWidth(200);
        
        // Version column
        TableColumn<PluginItem, String> versionCol = new TableColumn<>("Version");
        versionCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().version));
        versionCol.setPrefWidth(80);
        
        // State column
        TableColumn<PluginItem, PluginState> stateCol = new TableColumn<>("State");
        stateCol.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().state));
        stateCol.setPrefWidth(100);
        stateCol.setCellFactory(col -> new TableCell<PluginItem, PluginState>() {
            @Override
            protected void updateItem(PluginState state, boolean empty) {
                super.updateItem(state, empty);
                if (empty || state == null) {
                    setText("");
                    getStyleClass().removeAll("plugin-state-enabled", "plugin-state-disabled", "plugin-state-error");
                } else {
                    setText(state.name());
                    getStyleClass().removeAll("plugin-state-enabled", "plugin-state-disabled", "plugin-state-error");
                    getStyleClass().add(getStateStyleClass(state));
                }
            }
        });
        
        // Author column
        TableColumn<PluginItem, String> authorCol = new TableColumn<>("Author");
        authorCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().author));
        authorCol.setPrefWidth(150);
        
        // Description column
        TableColumn<PluginItem, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().description));
        descCol.setPrefWidth(300);
        
        table.getColumns().addAll(nameCol, versionCol, stateCol, authorCol, descCol);
        table.setItems(pluginItems);
        
        // Double-click to toggle enable/disable
        table.setRowFactory(tv -> {
            TableRow<PluginItem> row = new TableRow<PluginItem>() {
                @Override
                protected void updateItem(PluginItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item != null) {
                        setOnMouseClicked(event -> {
                            if (event.getClickCount() == 2 && !isEmpty()) {
                                togglePluginState(item);
                            }
                        });
                    }
                };
            };
            return row;
        });
        
        return table;
    }
    
    private HBox createButtonBar() {
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_LEFT);
        
        Button enableButton = new Button("Enable");
        enableButton.setOnAction(e -> enableSelectedPlugin());
        enableButton.setDisable(true);
        
        Button disableButton = new Button("Disable");
        disableButton.setOnAction(e -> disableSelectedPlugin());
        disableButton.setDisable(true);
        
        Button reloadButton = new Button("Reload");
        reloadButton.setOnAction(e -> reloadSelectedPlugin());
        reloadButton.setDisable(true);
        
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> loadPlugins());
        
        Button openPluginsButton = new Button("Open Plugins Folder");
        openPluginsButton.setOnAction(e -> openPluginsFolder());
        
        // Enable/disable buttons based on selection
        pluginTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            boolean hasSelection = newVal != null;
            enableButton.setDisable(!hasSelection || newVal.state == PluginState.ENABLED);
            disableButton.setDisable(!hasSelection || newVal.state != PluginState.ENABLED);
            reloadButton.setDisable(!hasSelection);
        });
        
        buttonBar.getChildren().addAll(enableButton, disableButton, reloadButton, 
                                     new Separator(Orientation.VERTICAL), 
                                     refreshButton, openPluginsButton);
        
        return buttonBar;
    }
    
    private void setupView() {
        VBox content = new VBox(10);
        content.getChildren().addAll(statusLabel, pluginTable);
        VBox.setVgrow(pluginTable, Priority.ALWAYS);
        
        root.getChildren().set(1, content);
    }
    
    private void loadPlugins() {
        statusLabel.setText("Loading plugins...");
        pluginTable.setDisable(true);

        // Just update the list from already loaded plugins
        updatePluginList();
        statusLabel.setText("Plugins loaded");
        pluginTable.setDisable(false);
    }

    private void updatePluginList() {
        pluginItems.clear();

        for (LoadedPlugin plugin : pluginManager.getLoadedPlugins()) {
            PluginItem item = new PluginItem(
                plugin.getPluginId(),
                plugin.getMetadata().getName(),
                plugin.getMetadata().getVersion(),
                plugin.getState(),
                plugin.getMetadata().getAuthor(),
                plugin.getMetadata().getDescription()
            );
            pluginItems.add(item);
        }
    }
    
    private void togglePluginState(PluginItem item) {
        if (item.state == PluginState.ENABLED) {
            disablePlugin(item.id);
        } else {
            enablePlugin(item.id);
        }
    }
    
    private void enableSelectedPlugin() {
        PluginItem selected = pluginTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            enablePlugin(selected.id);
        }
    }
    
    private void disableSelectedPlugin() {
        PluginItem selected = pluginTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            disablePlugin(selected.id);
        }
    }
    
    private void reloadSelectedPlugin() {
        PluginItem selected = pluginTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            reloadPlugin(selected.id);
        }
    }
    
    private void enablePlugin(String pluginId) {
        CompletableFuture.runAsync(() -> {
            try {
                pluginManager.enablePlugin(pluginId);
                Platform.runLater(() -> {
                    updatePluginList();
                });
            } catch (Exception e) {
                LOG.error("Failed to enable plugin: " + pluginId, e);
                Platform.runLater(() -> {
                    updatePluginList();
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Plugin Error");
                    alert.setHeaderText("Failed to enable plugin");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        });
    }

    private void disablePlugin(String pluginId) {
        CompletableFuture.runAsync(() -> {
            try {
                pluginManager.disablePlugin(pluginId);
                Platform.runLater(() -> {
                    updatePluginList();
                });
            } catch (Exception e) {
                LOG.error("Failed to disable plugin: " + pluginId, e);
                Platform.runLater(() -> {
                    updatePluginList();
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Plugin Error");
                    alert.setHeaderText("Failed to disable plugin");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        });
    }

    private void reloadPlugin(String pluginId) {
        CompletableFuture.runAsync(() -> {
            try {
                pluginManager.unloadPlugin(pluginId);
                // Note: re-loading would require knowing the JAR path
                Platform.runLater(() -> {
                    updatePluginList();
                });
            } catch (Exception e) {
                LOG.error("Failed to reload plugin: " + pluginId, e);
                Platform.runLater(() -> {
                    updatePluginList();
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Plugin Error");
                    alert.setHeaderText("Failed to reload plugin");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        });
    }

    private void openPluginsFolder() {
        try {
            java.awt.Desktop.getDesktop().open(
                pluginManager.getLoadedPlugins().isEmpty() ?
                java.nio.file.Paths.get("plugins").toFile() :
                pluginManager.getLoadedPlugins().get(0).getJarPath().getParent().toFile()
            );
        } catch (Exception e) {
            LOG.error("Failed to open plugins folder", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to open plugins folder");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    private String getStateStyleClass(PluginState state) {
        switch (state) {
            case ENABLED:
                return "plugin-state-enabled";
            case DISABLED:
                return "plugin-state-disabled";
            case ERROR:
            case LOADED:
                return "plugin-state-disabled";
            default:
                return "plugin-state-disabled";
        }
    }
    
    /**
     * Get the root node of this view.
     * 
     * @return Root node
     */
    
    public VBox getView() {
        return root;
    }
    
    /**
     * Data model for plugin table items.
     */
    private static class PluginItem {
        final String id;
        final String name;
        final String version;
        final PluginState state;
        final String author;
        final String description;
        
        PluginItem(String id, String name, String version, PluginState state, 
                  String author, String description) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.state = state;
            this.author = author;
            this.description = description;
        }
    }
}
