package com.distributed_ide.javafx_client.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Vue chat intégrée pour la collaboration temps réel.
 *
 * Structure :
 * ┌─────────────────────────────┐
 * │  Messages scrollables       │
 * │  [12:34] Alice: Hello       │
 * │  [12:35] Bob: Hi!           │
 * ├─────────────────────────────┤
 * │ [message input     ] [Send] │
 * └─────────────────────────────┘
 */
public class ChatView {

    private final VBox root;
    private final VBox messagesBox;
    private final ScrollPane scrollPane;
    private final TextField inputField;
    private final Button sendButton;

    private Consumer<String> onSendMessage;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    public ChatView() {
        root = new VBox();
        root.getStyleClass().add("chat-panel");

        // Messages area
        messagesBox = new VBox(4);
        messagesBox.setPadding(new Insets(8));
        messagesBox.getStyleClass().add("chat-messages");

        scrollPane = new ScrollPane(messagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("chat-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Auto-scroll to bottom
        messagesBox.heightProperty().addListener((obs, old, val) ->
                scrollPane.setVvalue(1.0)
        );

        // Input area
        inputField = new TextField();
        inputField.setPromptText("Message...");
        inputField.getStyleClass().add("chat-input");
        inputField.setOnAction(e -> sendMessage());
        HBox.setHgrow(inputField, Priority.ALWAYS);

        sendButton = new Button("Envoyer");
        sendButton.getStyleClass().add("chat-send-btn");
        sendButton.setOnAction(e -> sendMessage());

        HBox inputBar = new HBox(6, inputField, sendButton);
        inputBar.setAlignment(Pos.CENTER_LEFT);
        inputBar.setPadding(new Insets(6, 8, 6, 8));
        inputBar.getStyleClass().add("chat-input-bar");

        root.getChildren().addAll(scrollPane, inputBar);
    }

    public Node getView() {
        return root;
    }

    // ═══════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════

    /**
     * Ajoute un message au chat.
     *
     * @param username nom de l'utilisateur
     * @param message  texte du message
     * @param timestamp epoch millis
     * @param isOwn    true si c'est notre propre message
     */
    public void addMessage(String username, String message, long timestamp, boolean isOwn) {
        Platform.runLater(() -> {
            String time = TIME_FMT.format(Instant.ofEpochMilli(timestamp));

            Text timeText = new Text("[" + time + "] ");
            timeText.getStyleClass().add("chat-time");

            Text nameText = new Text(username + ": ");
            nameText.getStyleClass().add(isOwn ? "chat-name-own" : "chat-name-other");

            Text msgText = new Text(message);
            msgText.getStyleClass().add("chat-message-text");

            TextFlow flow = new TextFlow(timeText, nameText, msgText);
            flow.getStyleClass().add(isOwn ? "chat-bubble-own" : "chat-bubble-other");
            flow.setPadding(new Insets(3, 6, 3, 6));

            messagesBox.getChildren().add(flow);

            // Limit to last 500 messages
            if (messagesBox.getChildren().size() > 500) {
                messagesBox.getChildren().remove(0);
            }
        });
    }

    /**
     * Ajoute un message système (join, leave, etc.).
     */
    public void addSystemMessage(String message) {
        Platform.runLater(() -> {
            Text text = new Text("— " + message + " —");
            text.getStyleClass().add("chat-system");
            TextFlow flow = new TextFlow(text);
            flow.getStyleClass().add("chat-bubble-system");
            flow.setPadding(new Insets(2, 6, 2, 6));
            messagesBox.getChildren().add(flow);
        });
    }

    public void clear() {
        Platform.runLater(() -> messagesBox.getChildren().clear());
    }

    // ═══════════════════════════════════════════════════════════
    //  CALLBACKS
    // ═══════════════════════════════════════════════════════════

    public void setOnSendMessage(Consumer<String> handler) {
        this.onSendMessage = handler;
    }

    private void sendMessage() {
        String text = inputField.getText();
        if (text != null && !text.isBlank() && onSendMessage != null) {
            onSendMessage.accept(text.trim());
            inputField.clear();
        }
        inputField.requestFocus();
    }
}
