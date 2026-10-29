package com.distributed_ide.javafx_client.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Command Palette (Ctrl+Shift+P) — searchable list of all IDE commands.
 * Modeled after VSCode's command palette.
 */
public class CommandPaletteDialog {

    private static final int MAX_RESULTS = 30;

    private final Popup popup;
    private final TextField searchField;
    private final ListView<CommandEntry> resultList;
    private final ObservableList<CommandEntry> results = FXCollections.observableArrayList();
    private final List<CommandEntry> allCommands = new ArrayList<>();

    public CommandPaletteDialog() {
        searchField = new TextField();
        searchField.setPromptText("> Type a command...");
        searchField.getStyleClass().add("command-palette-search");

        resultList = new ListView<>(results);
        resultList.setPrefHeight(320);
        resultList.setPrefWidth(550);
        resultList.getStyleClass().add("command-palette-list");
        resultList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CommandEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label nameLabel = new Label(item.name);
                    nameLabel.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(nameLabel, Priority.ALWAYS);

                    HBox row = new HBox(nameLabel);
                    row.setSpacing(8);
                    if (item.shortcut != null && !item.shortcut.isEmpty()) {
                        Label shortcutLabel = new Label(item.shortcut);
                        shortcutLabel.getStyleClass().add("cmd-shortcut");
                        row.getChildren().add(shortcutLabel);
                    }
                    setGraphic(row);
                    setText(null);
                }
            }
        });

        VBox content = new VBox(searchField, resultList);
        content.setPadding(new Insets(4));
        content.setSpacing(2);
        content.getStyleClass().add("command-palette-container");

        popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(content);

        searchField.textProperty().addListener((obs, o, n) -> filterCommands(n));

        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                resultList.requestFocus();
                if (!results.isEmpty()) resultList.getSelectionModel().select(0);
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                executeCurrent();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                e.consume();
            }
        });

        resultList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                executeCurrent();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                e.consume();
            }
        });

        resultList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) executeCurrent();
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  COMMAND REGISTRATION
    // ═══════════════════════════════════════════════════════════

    public void registerCommand(String name, String category, String shortcut, Runnable action) {
        allCommands.add(new CommandEntry(name, category, shortcut, action));
    }

    public void clearCommands() {
        allCommands.clear();
    }

    // ═══════════════════════════════════════════════════════════
    //  SHOW / HIDE
    // ═══════════════════════════════════════════════════════════

    public void show(Window owner) {
        searchField.clear();
        results.setAll(allCommands.subList(0, Math.min(MAX_RESULTS, allCommands.size())));

        double x = owner.getX() + (owner.getWidth() - 560) / 2;
        double y = owner.getY() + 60;
        popup.show(owner, x, y);
        Platform.runLater(searchField::requestFocus);
    }

    public void hide() {
        popup.hide();
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    // ═══════════════════════════════════════════════════════════
    //  FILTER
    // ═══════════════════════════════════════════════════════════

    private void filterCommands(String query) {
        if (query == null || query.isBlank()) {
            results.setAll(allCommands.subList(0, Math.min(MAX_RESULTS, allCommands.size())));
            return;
        }
        String lower = query.toLowerCase();
        List<CommandEntry> filtered = allCommands.stream()
                .filter(c -> c.name.toLowerCase().contains(lower)
                        || c.category.toLowerCase().contains(lower))
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
        results.setAll(filtered);
        if (!filtered.isEmpty()) resultList.getSelectionModel().select(0);
    }

    private void executeCurrent() {
        CommandEntry sel = resultList.getSelectionModel().getSelectedItem();
        if (sel != null && sel.action != null) {
            popup.hide();
            Platform.runLater(sel.action);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MODEL
    // ═══════════════════════════════════════════════════════════

    public static class CommandEntry {
        public final String name;
        public final String category;
        public final String shortcut;
        public final Runnable action;

        public CommandEntry(String name, String category, String shortcut, Runnable action) {
            this.name = name;
            this.category = category != null ? category : "";
            this.shortcut = shortcut;
            this.action = action;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
