package com.distributed_ide.plugin;

import com.distributed_ide.plugin.api.Plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a plugin that has been loaded into the IDE.
 * 
 * <p>This class encapsulates all information about a loaded plugin, including:
 * <ul>
 *   <li>The plugin instance itself</li>
 *   <li>Plugin metadata (ID, name, version, etc.)</li>
 *   <li>Current state (LOADED, ENABLED, DISABLED, ERROR)</li>
 *   <li>Source JAR file path</li>
 *   <li>ClassLoader for isolated class loading</li>
 *   <li>Load time and error information</li>
 * </ul>
 * 
 * <p>Thread Safety: This class is immutable except for the state field,
 * which should only be modified through synchronized methods in PluginManager.
 * 
 * @see Plugin
 * @see PluginState
 * @see PluginMetadata
 */
public final class LoadedPlugin {
    
    private final String pluginId;
    private final Plugin instance;
    private final PluginMetadata metadata;
    private final Path jarPath;
    private final ClassLoader classLoader;
    private final Instant loadTime;
    
    private volatile PluginState state;
    private volatile String errorMessage;
    private volatile Throwable errorCause;
    
    /**
     * Creates a new LoadedPlugin instance.
     * 
     * @param pluginId Unique identifier for the plugin
     * @param instance The plugin instance
     * @param metadata Plugin metadata
     * @param jarPath Path to the plugin's JAR file
     * @param classLoader ClassLoader used to load the plugin
     * @param initialState Initial state of the plugin
     */
    public LoadedPlugin(
            @Nonnull String pluginId,
            @Nonnull Plugin instance,
            @Nonnull PluginMetadata metadata,
            @Nonnull Path jarPath,
            @Nonnull ClassLoader classLoader,
            @Nonnull PluginState initialState) {
        
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId cannot be null");
        this.instance = Objects.requireNonNull(instance, "instance cannot be null");
        this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
        this.jarPath = Objects.requireNonNull(jarPath, "jarPath cannot be null");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader cannot be null");
        this.state = Objects.requireNonNull(initialState, "initialState cannot be null");
        this.loadTime = Instant.now();
    }
    
    /**
     * Gets the unique plugin identifier.
     * 
     * @return The plugin ID (never null)
     */
    @Nonnull
    public String getPluginId() {
        return pluginId;
    }
    
    /**
     * Gets the plugin instance.
     * 
     * @return The plugin instance (never null)
     */
    @Nonnull
    public Plugin getInstance() {
        return instance;
    }
    
    /**
     * Gets the plugin metadata.
     * 
     * @return The plugin metadata (never null)
     */
    @Nonnull
    public PluginMetadata getMetadata() {
        return metadata;
    }
    
    /**
     * Gets the path to the plugin's JAR file.
     * 
     * @return The JAR file path (never null)
     */
    @Nonnull
    public Path getJarPath() {
        return jarPath;
    }
    
    /**
     * Gets the ClassLoader used to load this plugin.
     * 
     * @return The plugin's ClassLoader (never null)
     */
    @Nonnull
    public ClassLoader getClassLoader() {
        return classLoader;
    }
    
    /**
     * Gets the current state of the plugin.
     * 
     * @return The current plugin state (never null)
     */
    @Nonnull
    public PluginState getState() {
        return state;
    }
    
    /**
     * Sets the plugin state.
     * This method should only be called by PluginManager.
     * 
     * @param state The new state (must not be null)
     */
    void setState(@Nonnull PluginState state) {
        this.state = Objects.requireNonNull(state, "state cannot be null");
    }
    
    /**
     * Gets the time when this plugin was loaded.
     * 
     * @return The load timestamp (never null)
     */
    @Nonnull
    public Instant getLoadTime() {
        return loadTime;
    }
    
    /**
     * Gets the error message if the plugin is in ERROR state.
     * 
     * @return The error message, or null if no error
     */
    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Sets the error message.
     * This method should only be called by PluginManager.
     * 
     * @param errorMessage The error message
     */
    void setError(@Nullable String errorMessage, @Nullable Throwable cause) {
        this.errorMessage = errorMessage;
        this.errorCause = cause;
        this.state = PluginState.ERROR;
    }
    
    /**
     * Gets the error cause if the plugin is in ERROR state.
     * 
     * @return The error cause, or null if no error
     */
    @Nullable
    public Throwable getErrorCause() {
        return errorCause;
    }
    
    /**
     * Checks if the plugin is currently enabled.
     * 
     * @return true if the plugin is in ENABLED state
     */
    public boolean isEnabled() {
        return state == PluginState.ENABLED;
    }
    
    /**
     * Checks if the plugin is in an error state.
     * 
     * @return true if the plugin is in ERROR state
     */
    public boolean hasError() {
        return state == PluginState.ERROR;
    }
    
    /**
     * Gets a display name for this plugin.
     * Returns the metadata name if available, otherwise the plugin ID.
     * 
     * @return The display name (never null)
     */
    @Nonnull
    public String getDisplayName() {
        String name = metadata.getName();
        return (name != null && !name.isEmpty()) ? name : pluginId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LoadedPlugin that = (LoadedPlugin) o;
        return pluginId.equals(that.pluginId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(pluginId);
    }
    
    @Override
    public String toString() {
        return String.format(
            "LoadedPlugin{id='%s', name='%s', version='%s', state=%s}",
            pluginId,
            metadata.getName(),
            metadata.getVersion(),
            state
        );
    }
}
