package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.javafx_client.util.AnsiRenderer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.TextFlow;

import java.util.*;
import java.util.function.Consumer;

/**
 * Terminal intégré multi-onglets avec support ANSI, historique de commandes, et reconnexion.
 */
public class TerminalView {

    private static final String STYLE_STATUS = "terminal-status";
    private static final String STYLE_CONNECTED = "terminal-status-connected";
    private static final String STYLE_DISCONNECTED = "terminal-status-disconnected";
    private static final String STYLE_ERROR = "terminal-status-error";

    private final BorderPane pane;
    private final Label statusLabel;
    private final TabPane tabPane;
    private final Button clearButton;
    private final Button reconnectButton;
    private final Button newTabButton;
    private Consumer<String> onCommand;
    private Runnable onReconnect;
    private final Map<String, TerminalTab> tabs = new HashMap<>();
    private int tabCounter = 1;
    
    private static class TerminalTab {
        final Tab tab;
        final ScrollPane scrollPane;
        final TextFlow output;
        final TextField input;
        final VBox container;
        final List<String> commandHistory = new ArrayList<>();
        int historyIndex = -1;
        
        TerminalTab(Tab tab, ScrollPane scrollPane, TextFlow output, TextField input, VBox container) {
            this.tab = tab;
            this.scrollPane = scrollPane;
            this.output = output;
            this.input = input;
            this.container = container;
        }
    }

    public TerminalView() {
        statusLabel = new Label("Déconnecté");
        statusLabel.getStyleClass().add(STYLE_STATUS);
        statusLabel.getStyleClass().add(STYLE_DISCONNECTED);
        statusLabel.setPadding(new Insets(2, 8, 2, 0));

        clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clearCurrentTab());

        reconnectButton = new Button("Reconnect");
        reconnectButton.setOnAction(e -> {
            if (onReconnect != null) onReconnect.run();
        });
        reconnectButton.setDisable(false);
        
        newTabButton = new Button("+");
        newTabButton.setTooltip(new Tooltip("New tab"));
        newTabButton.setOnAction(e -> createNewTab());

        HBox toolbar = new HBox(8, statusLabel, clearButton, newTabButton, reconnectButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(4, 8, 4, 8));
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        
        // Create first tab
        createNewTab();

        VBox center = new VBox(4, toolbar, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        pane = new BorderPane(center);
    }
    
    private void createNewTab() {
        String tabId = "tab-" + tabCounter++;
        Tab tab = new Tab(null);
        
        TextFlow output = new TextFlow();
        output.getStyleClass().add("terminal-output");
        
        ScrollPane scrollPane = new ScrollPane(output);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.getStyleClass().add("terminal-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        TextField input = new TextField();
        input.setPromptText("Commande... (↑↓ historique, Entrée pour envoyer)");
        input.getStyleClass().add("terminal-input");
        
        VBox container = new VBox(4, scrollPane, input);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        container.setPadding(new Insets(4));
        
        tab.setContent(container);
        
        // Create custom tab header with close button
        HBox tabHeader = createTabHeader("Terminal " + tabCounter, tab);
        tab.setGraphic(tabHeader);
        
        TerminalTab terminalTab = new TerminalTab(tab, scrollPane, output, input, container);
        tabs.put(tabId, terminalTab);
        
        // History navigation
        input.setOnKeyPressed(e -> handleInputKeyPress(e, terminalTab));
        
        // Tab close handler
        tab.setOnClosed(e -> tabs.values().removeIf(t -> t.tab == tab));
        
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }
    
    private void handleInputKeyPress(javafx.scene.input.KeyEvent e, TerminalTab terminalTab) {
        if (e.getCode() == KeyCode.ENTER && onCommand != null) {
            String line = terminalTab.input.getText();
            if (line != null && !line.isEmpty()) {
                // Add to history
                terminalTab.commandHistory.add(line);
                terminalTab.historyIndex = terminalTab.commandHistory.size();
                
                // Send command
                onCommand.accept(line);
                
                // Display in terminal
                appendToTab(terminalTab, "$ " + line + "\n", false);
                terminalTab.input.clear();
            }
        } else if (e.getCode() == KeyCode.UP) {
            // Navigate history backwards
            if (!terminalTab.commandHistory.isEmpty() && terminalTab.historyIndex > 0) {
                terminalTab.historyIndex--;
                terminalTab.input.setText(terminalTab.commandHistory.get(terminalTab.historyIndex));
                terminalTab.input.positionCaret(terminalTab.input.getText().length());
            }
            e.consume();
        } else if (e.getCode() == KeyCode.DOWN) {
            // Navigate history forwards
            if (!terminalTab.commandHistory.isEmpty() && terminalTab.historyIndex < terminalTab.commandHistory.size() - 1) {
                terminalTab.historyIndex++;
                terminalTab.input.setText(terminalTab.commandHistory.get(terminalTab.historyIndex));
                terminalTab.input.positionCaret(terminalTab.input.getText().length());
            } else if (terminalTab.historyIndex == terminalTab.commandHistory.size() - 1) {
                terminalTab.historyIndex = terminalTab.commandHistory.size();
                terminalTab.input.clear();
            }
            e.consume();
        }
    }
    
    private void clearCurrentTab() {
        TerminalTab current = getCurrentTab();
        if (current != null) {
            current.output.getChildren().clear();
        }
    }
    
    private TerminalTab getCurrentTab() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected == null) return null;
        return tabs.values().stream()
            .filter(t -> t.tab == selected)
            .findFirst()
            .orElse(null);
    }

    public BorderPane getView() {
        return pane;
    }

    public void setOnCommand(Consumer<String> handler) {
        this.onCommand = handler;
    }

    public void setOnReconnect(Runnable handler) {
        this.onReconnect = handler;
    }

    /**
     * Updates the displayed status (connection / session / error).
     */
    public void setConnectionStatus(ConnectionStatus status, String detail) {
        statusLabel.getStyleClass().removeAll(STYLE_CONNECTED, STYLE_DISCONNECTED, STYLE_ERROR);
        switch (status) {
            case CONNECTED:
                statusLabel.getStyleClass().add(STYLE_CONNECTED);
                statusLabel.setText(detail != null && !detail.isEmpty() ? "Connecté • " + detail : "Connecté");
                reconnectButton.setDisable(true);
                break;
            case CONNECTING:
                statusLabel.setText("Connexion en cours...");
                reconnectButton.setDisable(true);
                break;
            case RECONNECTING:
                statusLabel.setText(detail != null ? "Reconnexion (tentative " + detail + ")..." : "Reconnexion...");
                reconnectButton.setDisable(true);
                break;
            case DISCONNECTED:
                statusLabel.getStyleClass().add(STYLE_DISCONNECTED);
                statusLabel.setText(detail != null && !detail.isEmpty() ? "Déconnecté : " + detail : "Déconnecté");
                reconnectButton.setDisable(false);
                break;
            case ERROR:
                statusLabel.getStyleClass().add(STYLE_ERROR);
                statusLabel.setText(detail != null && !detail.isEmpty() ? "Erreur : " + detail : "Erreur");
                reconnectButton.setDisable(false);
                break;
            default:
                statusLabel.setText(detail != null ? detail : "—");
                break;
        }
    }

    /**
     * Affiche les logs en temps réel avec support ANSI. isError = true pour stderr.
     */
    public void appendOutput(String text, boolean isError) {
        if (text == null) return;
        TerminalTab current = getCurrentTab();
        if (current == null) return;
        
        String toAppend = isError ? "[stderr] " + text : text;
        if (!toAppend.endsWith("\n")) toAppend += "\n";
        
        appendToTab(current, toAppend, isError);
    }
    
    private void appendToTab(TerminalTab terminalTab, String text, boolean isError) {
        // Render with ANSI color support
        AnsiRenderer.renderToTextFlow(text, terminalTab.output);
        
        // Auto-scroll to bottom
        javafx.application.Platform.runLater(() -> {
            terminalTab.scrollPane.setVvalue(1.0);
        });
    }
    
    public void setCommandHistory(List<String> history) {
        TerminalTab current = getCurrentTab();
        if (current != null && history != null) {
            current.commandHistory.clear();
            current.commandHistory.addAll(history);
            current.historyIndex = current.commandHistory.size();
        }
    }
    
    public List<String> getCommandHistory() {
        TerminalTab current = getCurrentTab();
        return current != null ? new ArrayList<>(current.commandHistory) : new ArrayList<>();
    }
    
    /**
     * Create a custom tab header with title and close button
     */
    private HBox createTabHeader(String title, Tab tab) {
        HBox headerBox = new HBox(4);
        headerBox.setStyle("-fx-alignment: CENTER_LEFT; -fx-padding: 0 4 0 0;");
        
        // Title label
        Label titleLabel = new Label(title);
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
        
        closeButton.setTooltip(new Tooltip("Fermer cet onglet"));
        closeButton.setOnAction(e -> {
            tabPane.getTabs().remove(tab);
        });
        
        headerBox.getChildren().addAll(titleLabel, closeButton);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        
        return headerBox;
    }

    public enum ConnectionStatus {
        CONNECTING, CONNECTED, DISCONNECTED, RECONNECTING, ERROR
    }
}
