package com.distributed_ide.plugin;

import com.distributed_ide.plugin.api.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides access to IDE services and functionality for plugins.
 * This is the main API surface that plugins use to interact with the IDE.
 */
public interface IDEContext {
    
    // Editor Management
    
    EditorManager getEditorManager();
    
    // Project Management
    
    ProjectManager getProjectManager();
    
    // Theme Management
    
    ThemeManager getThemeManager();
    
    // Collaboration Management
    
    CollaborationManager getCollaborationManager();
    
    // Git Management
    
    GitManager getGitManager();
    
    // Terminal Management
    
    TerminalManager getTerminalManager();
    
    // UI Integration
    
    UIManager getUIManager();
    
    // Event System
    
    EventManager getEventManager();
    
    // Configuration
    
    ConfigurationManager getConfigurationManager();
    
    // Logging
    
    Logger getLogger( String name);
    
    /**
     * Get the IDE's working directory.
     * 
     * @return IDE working directory
     */
    
    Path getWorkingDirectory();
    
    /**
     * Get the IDE's version.
     * 
     * @return IDE version
     */
    
    String getIDEVersion();
    
    /**
     * Execute a task on the JavaFX Application Thread.
     * 
     * @param task Task to execute
     */
    void runOnUIThread( Runnable task);
    
    /**
     * Execute a task on a background thread.
     * 
     * @param task Task to execute
     * @return CompletableFuture for the task
     */
    
    CompletableFuture<Void> runInBackground( Runnable task);
    
    /**
     * Show a notification to the user.
     * 
     * @param title Notification title
     * @param message Notification message
     * @param type Notification type
     */
    void showNotification( String title,  String message,  NotificationType type);
    
    /**
     * Show an input dialog to the user.
     * 
     * @param title Dialog title
     * @param message Dialog message
     * @param defaultValue Default value
     * @return CompletableFuture with user input
     */
    
    CompletableFuture<String> showInputDialog( String title,  String message,  String defaultValue);
    
    /**
     * Show a confirmation dialog to the user.
     * 
     * @param title Dialog title
     * @param message Dialog message
     * @return CompletableFuture with user choice
     */
    
    CompletableFuture<Boolean> showConfirmDialog( String title,  String message);
    
    /**
     * Register a menu item.
     * 
     * @param menuItem Menu item to register
     * @return Registration handle for later removal
     */
    
    MenuRegistration registerMenuItem( MenuItemDescriptor menuItem);
    
    /**
     * Register a toolbar item.
     * 
     * @param toolbarItem Toolbar item to register
     * @return Registration handle for later removal
     */
    
    ToolbarRegistration registerToolbarItem( ToolbarItemDescriptor toolbarItem);
    
    /**
     * Register a command.
     * 
     * @param command Command to register
     * @return Registration handle for later removal
     */
    
    CommandRegistration registerCommand( CommandDescriptor command);
    
    /**
     * Get the current user's ID.
     * 
     * @return User ID
     */
    
    String getCurrentUserId();
    
    /**
     * Get the current session ID.
     * 
     * @return Session ID
     */
    
    String getCurrentSessionId();
    
    /**
     * Check if the IDE is in development mode.
     * 
     * @return true if in development mode
     */
    boolean isDevelopmentMode();
    
    /**
     * Get plugin-specific storage directory.
     * 
     * @param pluginId Plugin ID
     * @return Storage directory for the plugin
     */
    
    Path getPluginStorageDirectory( String pluginId);
    
    /**
     * Get plugin-specific configuration.
     * 
     * @param pluginId Plugin ID
     * @return Plugin configuration
     */
    
    PluginConfiguration getPluginConfiguration( String pluginId);
}
