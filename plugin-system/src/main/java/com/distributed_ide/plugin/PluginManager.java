package com.distributed_ide.plugin;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Interface for managing IDE plugins.
 * 
 * <p>The PluginManager is responsible for the complete lifecycle management of plugins:
 * <ul>
 *   <li>Loading plugins from JAR files</li>
 *   <li>Enabling and disabling plugins</li>
 *   <li>Unloading plugins and cleaning up resources</li>
 *   <li>Tracking plugin states</li>
 *   <li>Providing access to loaded plugins</li>
 * </ul>
 * 
 * <p>Thread Safety: Implementations of this interface must be thread-safe.
 * Multiple threads may call these methods concurrently.
 * 
 * <p>Example usage:
 * <pre>{@code
 * PluginManager manager = new SimplePluginManager();
 * 
 * // Load a plugin
 * manager.loadPlugin(Paths.get("/path/to/plugin.jar"));
 * 
 * // Enable the plugin
 * manager.enablePlugin("plugin-id");
 * 
 * // Get plugin information
 * Optional<LoadedPlugin> plugin = manager.getPlugin("plugin-id");
 * plugin.ifPresent(p -> {
 *     System.out.println("Plugin: " + p.getMetadata().getName());
 *     System.out.println("State: " + p.getState());
 * });
 * 
 * // Disable and unload
 * manager.disablePlugin("plugin-id");
 * manager.unloadPlugin("plugin-id");
 * }</pre>
 * 
 * @see LoadedPlugin
 * @see PluginState
 */
public interface PluginManager {
    
    /**
     * Loads a plugin from the specified JAR file.
     * 
     * <p>This method:
     * <ol>
     *   <li>Creates an isolated ClassLoader for the plugin</li>
     *   <li>Reads plugin metadata from META-INF/plugin.json</li>
     *   <li>Validates the plugin metadata and main class</li>
     *   <li>Instantiates the plugin's main class</li>
     *   <li>Adds the plugin to the loaded plugins registry</li>
     * </ol>
     * 
     * <p>After loading, the plugin is in the {@link PluginState#LOADED} state.
     * To activate the plugin, call {@link #enablePlugin(String)}.
     * 
     * @param pluginPath Path to the plugin JAR file
     * @throws PluginException if the plugin cannot be loaded (invalid metadata,
     *         missing main class, duplicate plugin ID, etc.)
     * @throws IllegalArgumentException if pluginPath is null or doesn't exist
     */
    void loadPlugin(Path pluginPath) throws PluginException;
    
    /**
     * Enables a previously loaded plugin.
     * 
     * <p>This method calls the plugin's {@code onEnable()} method and transitions
     * the plugin to the {@link PluginState#ENABLED} state. If the plugin throws
     * an exception during enablement, it will be transitioned to
     * {@link PluginState#ERROR} state.
     * 
     * <p>Prerequisites: The plugin must be in {@link PluginState#LOADED} or
     * {@link PluginState#DISABLED} state.
     * 
     * @param pluginId The unique identifier of the plugin to enable
     * @throws PluginException if the plugin cannot be enabled (not found,
     *         wrong state, or plugin's onEnable() throws an exception)
     * @throws IllegalArgumentException if pluginId is null or empty
     */
    void enablePlugin(String pluginId) throws PluginException;
    
    /**
     * Disables an enabled plugin.
     * 
     * <p>This method calls the plugin's {@code onDisable()} method and transitions
     * the plugin to the {@link PluginState#DISABLED} state. The plugin remains
     * loaded in memory and can be re-enabled later.
     * 
     * <p>Prerequisites: The plugin must be in {@link PluginState#ENABLED} state.
     * 
     * @param pluginId The unique identifier of the plugin to disable
     * @throws PluginException if the plugin cannot be disabled (not found or wrong state)
     * @throws IllegalArgumentException if pluginId is null or empty
     */
    void disablePlugin(String pluginId) throws PluginException;
    
    /**
     * Unloads a plugin completely from memory.
     * 
     * <p>This method:
     * <ol>
     *   <li>Disables the plugin if it's currently enabled</li>
     *   <li>Removes the plugin from the loaded plugins registry</li>
     *   <li>Closes the plugin's ClassLoader to release resources</li>
     *   <li>Clears any references to the plugin</li>
     * </ol>
     * 
     * <p>After unloading, the plugin can be loaded again from its JAR file.
     * 
     * @param pluginId The unique identifier of the plugin to unload
     * @throws PluginException if the plugin cannot be unloaded (not found or cleanup fails)
     * @throws IllegalArgumentException if pluginId is null or empty
     */
    void unloadPlugin(String pluginId) throws PluginException;
    
    /**
     * Returns a list of all loaded plugins.
     * 
     * <p>The returned list includes plugins in all states: LOADED, ENABLED,
     * DISABLED, and ERROR. The list is a snapshot and may not reflect concurrent
     * modifications.
     * 
     * @return An immutable list of all loaded plugins (never null, may be empty)
     */
    List<LoadedPlugin> getLoadedPlugins();
    
    /**
     * Retrieves a specific plugin by its ID.
     * 
     * <p>This method provides access to detailed plugin information including
     * its current state, metadata, and instance.
     * 
     * @param pluginId The unique identifier of the plugin
     * @return An Optional containing the plugin if found, or empty if not found
     * @throws IllegalArgumentException if pluginId is null or empty
     */
    Optional<LoadedPlugin> getPlugin(String pluginId);
    
    /**
     * Checks if a plugin with the given ID is currently loaded.
     * 
     * @param pluginId The unique identifier of the plugin
     * @return true if the plugin is loaded (in any state), false otherwise
     * @throws IllegalArgumentException if pluginId is null or empty
     */
    default boolean isPluginLoaded(String pluginId) {
        return getPlugin(pluginId).isPresent();
    }
    
    /**
     * Retrieves the current state of a plugin.
     * 
     * @param pluginId The unique identifier of the plugin
     * @return An Optional containing the plugin's state if found, or empty if not found
     * @throws IllegalArgumentException if pluginId is null or empty
     */
    default Optional<PluginState> getPluginState(String pluginId) {
        return getPlugin(pluginId).map(LoadedPlugin::getState);
    }
}
