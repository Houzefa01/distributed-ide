package com.distributed_ide.javafx_client.plugin;

import java.nio.file.Path;
import java.util.List;

/**
 * Public API exposed to plugins for extending the Distributed IDE.
 * Plugins interact with the IDE exclusively through this interface,
 * ensuring a stable contract and clean decoupling.
 *
 * Extension points:
 *   - Editor: open/close files, read/write content, go to line
 *   - Commands: register command palette entries
 *   - UI: show notifications, append output, add status bar items
 *   - Project: get project root, list files
 *   - Settings: read/write persistent settings
 *   - Events: subscribe to lifecycle events
 */
public interface IDEPluginAPI {

    // ═══════════════════════════════════════════════════════════
    //  EDITOR
    // ═══════════════════════════════════════════════════════════

    /** Open a file in the editor. */
    void openFile(Path path);

    /** Close the file tab. */
    void closeFile(String absolutePath);

    /** Get the content of an open file. Returns null if not open. */
    String getFileContent(String absolutePath);

    /** Get the currently active file path, or null. */
    String getActiveFilePath();

    /** Navigate to a specific line in the active or specified file. */
    void goToLine(String absolutePath, int line);

    /** Get all currently open file paths. */
    List<String> getOpenFiles();

    // ═══════════════════════════════════════════════════════════
    //  COMMANDS
    // ═══════════════════════════════════════════════════════════

    /** Register a command in the Command Palette. */
    void registerCommand(String name, String category, String shortcut, Runnable action);

    /** Unregister a previously registered command. */
    void unregisterCommand(String name);

    // ═══════════════════════════════════════════════════════════
    //  UI NOTIFICATIONS
    // ═══════════════════════════════════════════════════════════

    /** Show a transient notification in the status bar. */
    void showNotification(String message);

    /** Append a message to the Output panel. */
    void appendOutput(String category, String message);

    /** Show an information dialog. */
    void showInfo(String title, String message);

    /** Show an error dialog. */
    void showError(String title, String message);

    // ═══════════════════════════════════════════════════════════
    //  PROJECT
    // ═══════════════════════════════════════════════════════════

    /** Get the project root path, or null if no project is open. */
    Path getProjectRoot();

    /** Refresh the project tree view. */
    void refreshProjectTree();

    // ═══════════════════════════════════════════════════════════
    //  SETTINGS
    // ═══════════════════════════════════════════════════════════

    /** Read a string setting. */
    String getSetting(String key, String defaultValue);

    /** Write a setting (auto-persisted). */
    void setSetting(String key, String value);

    // ═══════════════════════════════════════════════════════════
    //  EVENTS
    // ═══════════════════════════════════════════════════════════

    /** Event types plugins can subscribe to. */
    enum EventType {
        FILE_OPENED, FILE_CLOSED, FILE_SAVED,
        PROJECT_OPENED, PROJECT_CLOSED,
        EDITOR_CONTENT_CHANGED,
        PLUGIN_LOADED, PLUGIN_UNLOADED
    }

    /** Subscribe to an IDE event. Returns a subscription ID for unsubscribing. */
    String subscribe(EventType event, EventHandler handler);

    /** Unsubscribe from an event. */
    void unsubscribe(String subscriptionId);

    /** Event handler callback. */
    @FunctionalInterface
    interface EventHandler {
        void handle(EventType type, Object data);
    }
}
