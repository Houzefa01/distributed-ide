package com.distributed_ide.plugin.api;


import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Provides access to editor functionality for plugins.
 */
public interface EditorManager {
    
    /**
     * Open a file in the editor.
     * 
     * @param filePath Path to the file to open
     * @return CompletableFuture with the editor tab
     */
    
    java.util.concurrent.CompletableFuture<EditorTab> openFile( Path filePath);
    
    /**
     * Get the currently active editor tab.
     * 
     * @return Currently active tab, or empty if none
     */
    
    Optional<EditorTab> getActiveTab();
    
    /**
     * Get all open editor tabs.
     * 
     * @return List of all open tabs
     */
    
    List<EditorTab> getAllTabs();
    
    /**
     * Close a specific editor tab.
     * 
     * @param tab Tab to close
     * @return CompletableFuture with success status
     */
    
    java.util.concurrent.CompletableFuture<Boolean> closeTab( EditorTab tab);
    
    /**
     * Get the content of an editor tab.
     * 
     * @param tab Editor tab
     * @return Tab content
     */
    
    String getContent( EditorTab tab);
    
    /**
     * Set the content of an editor tab.
     * 
     * @param tab Editor tab
     * @param content New content
     */
    void setContent( EditorTab tab,  String content);
    
    /**
     * Register an editor listener.
     * 
     * @param listener Listener to register
     * @return Registration handle
     */
    
    EditorListenerRegistration addListener( EditorListener listener);
    
    /**
     * Interface representing an editor tab.
     */
    interface EditorTab {
        
        Path getFilePath();
        
        
        String getFileName();
        
        boolean isDirty();
        
        void setDirty(boolean dirty);
        
        
        String getLanguage();
    }
    
    /**
     * Listener for editor events.
     */
    interface EditorListener {
        /**
         * Called when a file is opened.
         * 
         * @param tab The opened tab
         */
        default void onFileOpened( EditorTab tab) {}
        
        /**
         * Called when a file is closed.
         * 
         * @param tab The closed tab
         */
        default void onFileClosed( EditorTab tab) {}
        
        /**
         * Called when content changes.
         * 
         * @param tab The changed tab
         * @param newContent New content
         */
        default void onContentChanged( EditorTab tab,  String newContent) {}
        
        /**
         * Called when the active tab changes.
         * 
         * @param newTab New active tab, or null if none
         */
        default void onActiveTabChanged( EditorTab newTab) {}
    }
    
    /**
     * Registration handle for editor listeners.
     */
    interface EditorListenerRegistration {
        void remove();
    }
}
