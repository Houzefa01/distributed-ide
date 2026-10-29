package com.distributed_ide.plugin.api;


import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Configuration interface specific to a plugin.
 */
public interface PluginConfiguration {
    
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
     * Get all configuration values.
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
     * Clear all configuration values.
     */
    void clear();
    
    /**
     * Check if the configuration has been modified since last save.
     * 
     * @return true if modified
     */
    boolean isModified();
}
