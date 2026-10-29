package com.distributed_ide.plugin.api;


import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Provides access to project functionality for plugins.
 */
public interface ProjectManager {
    
    /**
     * Get the currently open project.
     * 
     * @return Current project, or empty if none
     */
    
    Optional<Project> getCurrentProject();
    
    /**
     * Open a project from a directory.
     * 
     * @param projectPath Path to project directory
     * @return CompletableFuture with the opened project
     */
    
    java.util.concurrent.CompletableFuture<Project> openProject( Path projectPath);
    
    /**
     * Close the current project.
     * 
     * @return CompletableFuture with success status
     */
    
    java.util.concurrent.CompletableFuture<Boolean> closeProject();
    
    /**
     * Get all files in the current project.
     * 
     * @return List of project files
     */
    
    List<Path> getProjectFiles();
    
    /**
     * Register a project listener.
     * 
     * @param listener Listener to register
     * @return Registration handle
     */
    
    ProjectListenerRegistration addListener( ProjectListener listener);
    
    interface Project {
        
        Path getRootPath();
        
        
        String getName();
        
        
        List<Path> getFiles();
        
        
        Path getBuildFile();
    }
    
    interface ProjectListener {
        default void onProjectOpened( Project project) {}
        default void onProjectClosed( Project project) {}
        default void onFileAdded( Path file) {}
        default void onFileRemoved( Path file) {}
        default void onFileModified( Path file) {}
    }
    
    interface ProjectListenerRegistration {
        void remove();
    }
}
