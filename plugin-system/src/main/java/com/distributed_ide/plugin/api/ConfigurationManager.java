package com.distributed_ide.plugin.api;


import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Provides configuration management for plugins.
 */
public interface ConfigurationManager {
    
    /**
     * Get a configuration value.
     * 
     * @param key Configuration key
     * @param defaultValue Default value
     * @param <T> Value type
     * @return Configuration value
     */
    
    <T> T get( String key,  T defaultValue);
    
    /**
     * Set a configuration value.
     * 
     * @param key Configuration key
     * @param value Configuration value
     * @param <T> Value type
     */
    <T> void set( String key,  T value);
    
    /**
     * Get all configuration values as a map.
     * 
     * @return Configuration map
     */
    
    Map<String, Object> getAll();
    
    /**
     * Save configuration to disk.
     * 
     * @return CompletableFuture with success status
     */
    
    CompletableFuture<Boolean> save();
    
    /**
     * Load configuration from disk.
     * 
     * @return CompletableFuture with success status
     */
    
    CompletableFuture<Boolean> load();
    
    /**
     * Add a configuration change listener.
     * 
     * @param listener Listener to add
     * @return Registration handle
     */
    
    ConfigurationListenerRegistration addListener( ConfigurationListener listener);
    
    interface ConfigurationListener {
        void onConfigurationChanged( String key,  Object oldValue,  Object newValue);
    }
    
    interface ConfigurationListenerRegistration {
        void remove();
    }
}
