package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.sync.model.SyncMessage;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;
import java.util.function.Consumer;

/**
 * Collaboration side panel: status, connected client list,
 * admin buttons (kick, shutdown), audio/video call button, token display.
 */
public class CollaborationPanel {

    private final VBox root;
    private final Label statusLabel;
    private final Label roleLabel;
    private final Label tokenLabel;
    private final ListView<SyncMessage.ClientInfo> clientListView;
    private final ObservableList<SyncMessage.ClientInfo> clientItems = FXCollections.observableArrayList();
    private final Button kickButton;
    private final Button shutdownButton;
    private final Button callButton;
    private final Button disconnectButton;

    private final Button shareProjectButton;

    private Consumer<String> onKickClient;
    private Runnable onShutdownServer;
    private Runnable onCallToggle;
    private Runnable onDisconnect;
    private Runnable onShareProject;
    private volatile boolean isAdmin = false;
    private volatile String myClientId;

    public CollaborationPanel() {
        root = new VBox(8);
        root.setPadding(new Insets(10));
        root.setPrefWidth(220);
        root.getStyleClass().add("collab-panel");

        Label title = new Label("Collaboration");
        title.getStyleClass().add("collab-title");

        statusLabel = new Label("Disconnected");
        statusLabel.getStyleClass().add("collab-status-disconnected");

        roleLabel = new Label("");
        roleLabel.getStyleClass().add("collab-role");

        tokenLabel = new Label("");
        tokenLabel.getStyleClass().add("collab-token");
        tokenLabel.setWrapText(true);

        clientListView = new ListView<>(clientItems);
        clientListView.setPrefHeight(200);
        clientListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SyncMessage.ClientInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(6);
                    row.setAlignment(Pos.CENTER_LEFT);
                    Circle dot = new Circle(4);
                    dot.setFill("ADMIN".equals(item.getRole()) ? Color.GOLD : Color.LIMEGREEN);
                    Label name = new Label(item.getUsername() != null ? item.getUsername() : item.getClientId());
                    name.getStyleClass().add("collab-client-name");
                    Label roleLbl = new Label("ADMIN".equals(item.getRole()) ? " ★" : "");
                    roleLbl.getStyleClass().add("collab-client-role");
                    row.getChildren().addAll(dot, name, roleLbl);
                    setGraphic(row);
                    setText(null);
                }
            }
        });

        kickButton = new Button("Kick");
        kickButton.setMaxWidth(Double.MAX_VALUE);
        kickButton.setVisible(false);
        kickButton.getStyleClass().add("button-danger");
        kickButton.setOnAction(e -> {
            SyncMessage.ClientInfo selected = clientListView.getSelectionModel().getSelectedItem();
            if (selected != null && onKickClient != null && !selected.getClientId().equals(myClientId)) {
                onKickClient.accept(selected.getClientId());
            }
        });

        shutdownButton = new Button("Stop Server");
        shutdownButton.setMaxWidth(Double.MAX_VALUE);
        shutdownButton.setVisible(false);
        shutdownButton.getStyleClass().add("button-danger");
        shutdownButton.setOnAction(e -> {
            if (onShutdownServer != null) onShutdownServer.run();
        });

        callButton = new Button("Audio/Video Call");
        callButton.setMaxWidth(Double.MAX_VALUE);
        callButton.getStyleClass().add("button-success");
        callButton.setOnAction(e -> {
            if (onCallToggle != null) onCallToggle.run();
        });

        disconnectButton = new Button("Disconnect");
        disconnectButton.setMaxWidth(Double.MAX_VALUE);
        disconnectButton.setOnAction(e -> {
            if (onDisconnect != null) onDisconnect.run();
        });

        shareProjectButton = new Button("Share Project");
        shareProjectButton.setMaxWidth(Double.MAX_VALUE);
        shareProjectButton.getStyleClass().add("button-primary");
        shareProjectButton.setOnAction(e -> {
            if (onShareProject != null) onShareProject.run();
        });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        root.getChildren().addAll(
                title, statusLabel, roleLabel, tokenLabel,
                new Separator(),
                new Label("Connected Clients:") {{ getStyleClass().add("collab-section-label"); }},
                clientListView,
                kickButton,
                new Separator(),
                shareProjectButton,
                callButton,
                spacer,
                shutdownButton,
                disconnectButton
        );
    }

    public VBox getView() {
        return root;
    }

    public void setConnected(String clientId, String role, String token) {
        Platform.runLater(() -> {
            this.myClientId = clientId;
            this.isAdmin = "ADMIN".equals(role);
            statusLabel.setText("Connected: " + clientId);
            statusLabel.getStyleClass().removeAll("collab-status-connected", "collab-status-disconnected");
            statusLabel.getStyleClass().add("collab-status-connected");
            roleLabel.setText("Role: " + (isAdmin ? "Administrator ★" : "Client"));
            if (token != null && isAdmin) {
                tokenLabel.setText("Token: " + token);
            } else {
                tokenLabel.setText("");
            }
            kickButton.setVisible(isAdmin);
            shutdownButton.setVisible(isAdmin);
        });
    }

    public void setDisconnected() {
        Platform.runLater(() -> {
            statusLabel.setText("Disconnected");
            statusLabel.getStyleClass().removeAll("collab-status-connected", "collab-status-disconnected");
            statusLabel.getStyleClass().add("collab-status-disconnected");
            roleLabel.setText("");
            tokenLabel.setText("");
            clientItems.clear();
            kickButton.setVisible(false);
            shutdownButton.setVisible(false);
            isAdmin = false;
            myClientId = null;
        });
    }

    public void updateClientList(List<SyncMessage.ClientInfo> clients) {
        Platform.runLater(() -> {
            clientItems.setAll(clients);
        });
    }

    // Callbacks
    public void setOnKickClient(Consumer<String> handler) { this.onKickClient = handler; }
    public void setOnShutdownServer(Runnable handler) { this.onShutdownServer = handler; }
    public void setOnCallToggle(Runnable handler) { this.onCallToggle = handler; }
    public void setOnDisconnect(Runnable handler) { this.onDisconnect = handler; }
    public void setOnShareProject(Runnable handler) { this.onShareProject = handler; }
}
