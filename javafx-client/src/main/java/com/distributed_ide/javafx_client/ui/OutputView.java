package com.distributed_ide.javafx_client.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Multi-channel Output panel (Build, Sync, LSP, Debug).
 * Each channel has its own text buffer, selectable via ComboBox.
 */
public class OutputView {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_BUFFER_CHARS = 500_000;

    private final BorderPane root;
    private final TextArea outputArea;
    private final ComboBox<String> channelSelector;
    private final Map<String, StringBuilder> channels = new LinkedHashMap<>();
    private volatile String activeChannel;

    public OutputView() {
        addChannel("Build");
        addChannel("Sync");
        addChannel("LSP");
        addChannel("Debug");
        activeChannel = "Build";

        channelSelector = new ComboBox<>();
        channelSelector.getItems().addAll(channels.keySet());
        channelSelector.setValue(activeChannel);
        channelSelector.setOnAction(e -> switchChannel(channelSelector.getValue()));

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("button");
        clearBtn.setOnAction(e -> clearActiveChannel());

        HBox toolbar = new HBox(8, new Label("Sortie :"), channelSelector, clearBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(4, 8, 4, 8));

        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.getStyleClass().add("output-area");
        VBox.setVgrow(outputArea, Priority.ALWAYS);

        root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(outputArea);
    }

    public BorderPane getView() {
        return root;
    }

    public void appendOutput(String channel, String text) {
        if (channel == null || text == null) return;
        StringBuilder buf = channels.get(channel);
        if (buf == null) {
            addChannel(channel);
            buf = channels.get(channel);
            Platform.runLater(() -> {
                if (!channelSelector.getItems().contains(channel)) {
                    channelSelector.getItems().add(channel);
                }
            });
        }
        String stamped = "[" + LocalTime.now().format(TIME_FMT) + "] " + text;
        if (!stamped.endsWith("\n")) stamped += "\n";
        String finalStamped = stamped;
        StringBuilder finalBuf = buf;
        finalBuf.append(finalStamped);
        // Truncate buffer if it exceeds max size — remove oldest half
        if (finalBuf.length() > MAX_BUFFER_CHARS) {
            int cutPoint = finalBuf.indexOf("\n", finalBuf.length() - MAX_BUFFER_CHARS / 2);
            if (cutPoint > 0) {
                finalBuf.delete(0, cutPoint + 1);
            } else {
                finalBuf.delete(0, finalBuf.length() - MAX_BUFFER_CHARS / 2);
            }
        }
        if (channel.equals(activeChannel)) {
            Platform.runLater(() -> {
                outputArea.appendText(finalStamped);
                // Also truncate the TextArea display
                if (outputArea.getLength() > MAX_BUFFER_CHARS) {
                    String truncated = outputArea.getText(outputArea.getLength() - MAX_BUFFER_CHARS / 2, outputArea.getLength());
                    outputArea.setText(truncated);
                    outputArea.positionCaret(outputArea.getLength());
                }
            });
        }
    }

    public void clear(String channel) {
        StringBuilder buf = channels.get(channel);
        if (buf != null) {
            buf.setLength(0);
            if (channel.equals(activeChannel)) {
                Platform.runLater(() -> outputArea.clear());
            }
        }
    }

    private void addChannel(String name) {
        channels.putIfAbsent(name, new StringBuilder());
    }

    private void switchChannel(String channel) {
        if (channel == null || !channels.containsKey(channel)) return;
        activeChannel = channel;
        Platform.runLater(() -> outputArea.setText(channels.get(channel).toString()));
    }

    private void clearActiveChannel() {
        clear(activeChannel);
    }
}
