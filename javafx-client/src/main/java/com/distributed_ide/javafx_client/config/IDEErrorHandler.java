package com.distributed_ide.javafx_client.config;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Global error handler for the Distributed IDE.
 * Centralizes error reporting, logging, and user notification.
 * 
 * Usage:
 *   IDEErrorHandler.get().handle("Sync", "Connection failed", exception);
 *   IDEErrorHandler.get().handleUI("File", "Cannot save file");
 */
public final class IDEErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(IDEErrorHandler.class);
    private static final IDEErrorHandler INSTANCE = new IDEErrorHandler();

    public static IDEErrorHandler get() { return INSTANCE; }

    /** Severity levels for error categorization. */
    public enum Severity { INFO, WARNING, ERROR, FATAL }

    /** Listener for error events (e.g., output panel, telemetry). */
    public interface ErrorListener {
        void onError(Severity severity, String category, String message, Throwable cause);
    }

    private final List<ErrorListener> listeners = new CopyOnWriteArrayList<>();
    private volatile BiConsumer<String, String> outputAppender; // (category, message) -> append to output view

    private IDEErrorHandler() {
        // Set default uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LOG.error("[FATAL] Uncaught exception in thread {}", thread.getName(), throwable);
            handle(Severity.FATAL, "System", "Uncaught exception: " + throwable.getMessage(), throwable);
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  CONFIGURATION
    // ═══════════════════════════════════════════════════════════

    public void addListener(ErrorListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ErrorListener listener) {
        listeners.remove(listener);
    }

    /** Set the output appender (typically OutputView::appendOutput). */
    public void setOutputAppender(BiConsumer<String, String> appender) {
        this.outputAppender = appender;
    }

    // ═══════════════════════════════════════════════════════════
    //  HANDLE ERRORS
    // ═══════════════════════════════════════════════════════════

    /** Log + notify listeners. No UI alert. */
    public void handle(String category, String message, Throwable cause) {
        handle(Severity.ERROR, category, message, cause);
    }

    /** Log + notify listeners + append to output view. No UI dialog. */
    public void handle(Severity severity, String category, String message, Throwable cause) {
        // Log based on severity
        switch (severity) {
            case INFO    -> LOG.info("[{}] {}", category, message);
            case WARNING -> LOG.warn("[{}] {}", category, message, cause);
            case ERROR   -> LOG.error("[{}] {}", category, message, cause);
            case FATAL   -> LOG.error("[FATAL][{}] {}", category, message, cause);
        }

        // Append to output view
        BiConsumer<String, String> appender = outputAppender;
        if (appender != null) {
            String outputMsg = cause != null ? message + " — " + cause.getMessage() : message;
            Platform.runLater(() -> appender.accept(category, outputMsg));
        }

        // Notify listeners
        for (ErrorListener listener : listeners) {
            try {
                listener.onError(severity, category, message, cause);
            } catch (Exception e) {
                LOG.warn("Error listener threw exception", e);
            }
        }
    }

    /** Log + show user-visible error dialog on FX thread. */
    public void handleUI(String category, String message) {
        handleUI(category, message, null);
    }

    /** Log + show user-visible error dialog with stack trace. */
    public void handleUI(String category, String message, Throwable cause) {
        handle(Severity.ERROR, category, message, cause);
        Platform.runLater(() -> showErrorDialog(category, message, cause));
    }

    /** Log a warning (no dialog). */
    public void warn(String category, String message) {
        handle(Severity.WARNING, category, message, null);
    }

    /** Log an info message. */
    public void info(String category, String message) {
        handle(Severity.INFO, category, message, null);
    }

    // ═══════════════════════════════════════════════════════════
    //  UI DIALOG
    // ═══════════════════════════════════════════════════════════

    private void showErrorDialog(String title, String message, Throwable cause) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error — " + title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        if (cause != null) {
            StringWriter sw = new StringWriter();
            cause.printStackTrace(new PrintWriter(sw));
            TextArea textArea = new TextArea(sw.toString());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);
            GridPane content = new GridPane();
            content.setMaxWidth(Double.MAX_VALUE);
            content.add(textArea, 0, 0);
            alert.getDialogPane().setExpandableContent(content);
        }

        alert.showAndWait();
    }
}
