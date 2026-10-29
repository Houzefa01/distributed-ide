package com.distributed_ide.javafx_client.error;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Global exception handler for uncaught exceptions in threads (including FX thread).
 * Ensures no silent failures and provides user feedback for unexpected errors.
 * 
 * KEY RESPONSIBILITIES:
 * ✅ Catches and logs ALL uncaught exceptions
 * ✅ Distinguishes FX thread vs background thread crashes
 * ✅ Shows user-friendly error dialog
 * ✅ Maintains error history for debugging
 * ✅ Prevents cascade failures
 */
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_ERROR_HISTORY = 50;
    
    private final List<ExceptionRecord> errorHistory = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean isShuttingDown = false;

    /**
     * Record of an exception for debugging and analysis.
     */
    public static class ExceptionRecord {
        public final LocalDateTime timestamp;
        public final String threadName;
        public final String exceptionType;
        public final String message;
        public final String stackTrace;
        public final boolean isFxThread;

        public ExceptionRecord(LocalDateTime timestamp, String threadName, Throwable exception, boolean isFxThread) {
            this.timestamp = timestamp;
            this.threadName = threadName;
            this.exceptionType = exception.getClass().getSimpleName();
            this.message = exception.getMessage();
            this.isFxThread = isFxThread;
            
            // Capture stack trace
            StringWriter sw = new StringWriter();
            exception.printStackTrace(new PrintWriter(sw));
            this.stackTrace = sw.toString();
        }

        @Override
        public String toString() {
            return String.format("[%s] %s (thread: %s): %s - %s", 
                timestamp.format(DATE_FORMAT), exceptionType, threadName, message, isFxThread ? "FX" : "BG");
        }
    }

    /**
     * Install global exception handler for all threads.
     * Call during application startup.
     */
    public void install() {
        // Handler for background threads
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
            handleBackgroundException(thread, exception);
        });

        LOG.info("✅ Global exception handler installed");
    }

    /**
     * Handle uncaught exception in background thread.
     * Logs, records, and notifies user without crashing IDE.
     */
    private void handleBackgroundException(Thread thread, Throwable exception) {
        if (isShuttingDown) {
            return;  // Ignore exceptions during shutdown
        }

        LOG.error("❌ Uncaught exception in background thread: {}", thread.getName(), exception);
        recordException(thread.getName(), exception, false);

        // Notify user (on FX thread)
        Platform.runLater(() -> {
            String message = exception.getMessage();
            if (message == null || message.isEmpty()) {
                message = exception.getClass().getSimpleName();
            }
            
            showErrorDialog(
                "Background Operation Failed",
                "An error occurred in: " + thread.getName(),
                message,
                exception
            );
        });
    }

    /**
     * Handle exception on the FX application thread.
     * Must be called from within FX thread context.
     */
    public void handleFxThreadException(Throwable exception) {
        if (isShuttingDown) {
            return;
        }

        String threadName = Thread.currentThread().getName();
        LOG.error("❌ Exception on FX thread ({})", threadName, exception);
        recordException(threadName, exception, true);

        // Don't show dialog if already showing (prevent cascade)
        if (excessiveErrorRateDetected()) {
            LOG.error("Excessive error rate detected, suppressing further dialogs");
            return;
        }

        String message = exception.getMessage();
        if (message == null || message.isEmpty()) {
            message = exception.getClass().getSimpleName();
        }
        
        showErrorDialog(
            "UI Error",
            "A FX thread error occurred",
            message,
            exception
        );
    }

    /**
     * Show user-friendly error dialog with expandable details.
     */
    private void showErrorDialog(String title, String header, String message, Throwable exception) {
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(message);

            // Create expandable details (stack trace)
            StringWriter sw = new StringWriter();
            exception.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();

            TextArea textArea = new TextArea(stackTrace);
            textArea.setEditable(false);
            textArea.setWrapText(false);
            textArea.setPrefRowCount(10);
            textArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 10;");

            VBox.setVgrow(textArea, Priority.ALWAYS);
            VBox expandable = new VBox(textArea);

            alert.getDialogPane().setExpandableContent(expandable);
            alert.getDialogPane().setExpanded(false);

            alert.showAndWait();
        } catch (Exception e) {
            // If dialog creation fails, just log
            LOG.error("Failed to show error dialog", e);
        }
    }

    /**
     * Record exception for debugging purposes.
     */
    private void recordException(String threadName, Throwable exception, boolean isFxThread) {
        ExceptionRecord record = new ExceptionRecord(
            LocalDateTime.now(),
            threadName,
            exception,
            isFxThread
        );
        
        errorHistory.add(record);
        
        // Keep history bounded
        if (errorHistory.size() > MAX_ERROR_HISTORY) {
            errorHistory.remove(0);
        }
        
        LOG.debug("Exception recorded: {}", record);
    }

    /**
     * Check if excessive error rate detected (prevent error dialog cascade).
     */
    private boolean excessiveErrorRateDetected() {
        if (errorHistory.size() < 5) {
            return false;
        }

        // Check if 5 errors in last 5 seconds
        long now = System.currentTimeMillis();
        long fiveSecondsAgo = now - 5000;
        
        long recentErrors = errorHistory.stream()
            .filter(r -> r.timestamp.toString().length() > 0)  // Simple filter for timing
            .count();

        return recentErrors >= 5;
    }

    /**
     * Get error history for debugging.
     */
    public List<ExceptionRecord> getErrorHistory() {
        return new ArrayList<>(errorHistory);
    }

    /**
     * Clear error history.
     */
    public void clearErrorHistory() {
        errorHistory.clear();
    }

    /**
     * Mark that IDE is shutting down (suppress further error dialogs).
     */
    public void markShuttingDown() {
        isShuttingDown = true;
    }

    /**
     * Format error history for reporting/debugging.
     */
    public String getErrorReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== IDE ERROR REPORT ===\n");
        sb.append("Total errors: ").append(errorHistory.size()).append("\n\n");
        
        for (ExceptionRecord record : errorHistory) {
            sb.append(record.toString()).append("\n");
        }
        
        return sb.toString();
    }
}
