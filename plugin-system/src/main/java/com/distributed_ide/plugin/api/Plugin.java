package com.distributed_ide.plugin.api;

import com.distributed_ide.plugin.PluginMetadata;

/**
 * Base interface that all IDE plugins must implement.
 * 
 * <p>Plugin Lifecycle:
 * <ol>
 *   <li>Plugin JAR is loaded and discovered</li>
 *   <li>Plugin metadata is read from META-INF/plugin.json</li>
 *   <li>Plugin main class is instantiated via reflection</li>
 *   <li>{@link #onEnable(IDEContext)} is called when plugin is enabled</li>
 *   <li>Plugin performs its functionality</li>
 *   <li>{@link #onDisable()} is called when plugin is disabled or IDE shuts down</li>
 * </ol>
 * 
 * <p>Example plugin implementation:
 * <pre>{@code
 * public class MyPlugin implements Plugin {
 *     private IDEContext context;
 *     
 *     @Override
 *     public void onEnable(IDEContext context) {
 *         this.context = context;
 *         context.getLogger().info("MyPlugin enabled!");
 *         // Register UI components, commands, etc.
 *     }
 *     
 *     @Override
 *     public void onDisable() {
 *         context.getLogger().info("MyPlugin disabled!");
 *         // Cleanup resources
 *     }
 *     
 *     @Override
 *     public PluginMetadata getMetadata() {
 *         return new PluginMetadata(
 *             "my-plugin",
 *             "My Plugin",
 *             "1.0.0",
 *             "A sample plugin",
 *             "Author Name",
 *             "com.example.MyPlugin",
 *             "1.0.0",
 *             null,
 *             "https://example.com",
 *             "MIT"
 *         );
 *     }
 * }
 * }</pre>
 * 
 * @see IDEContext
 * @see PluginMetadata
 */
public interface Plugin {
    
    /**
     * Called when the plugin is enabled.
     * 
     * <p>This method is invoked after the plugin has been loaded and is ready to start.
     * Plugins should register their UI components, commands, event listeners, and
     * initialize any required resources in this method.
     * 
     * <p>If this method throws an exception, the plugin will be marked as failed
     * and will not be enabled.
     * 
     * @param context The IDE context providing access to IDE services and APIs
     * @throws Exception if plugin initialization fails
     */
    void onEnable(IDEContext context) throws Exception;
    
    /**
     * Called when the plugin is disabled.
     * 
     * <p>This method is invoked when the plugin is being disabled, either manually
     * by the user or during IDE shutdown. Plugins should cleanup all resources,
     * unregister listeners, save state, and perform any necessary shutdown tasks.
     * 
     * <p>Implementations should handle this method gracefully and not throw exceptions.
     * Any exceptions thrown will be logged but otherwise ignored.
     */
    void onDisable();
    
    /**
     * Returns the plugin's metadata.
     * 
     * <p>This metadata includes essential information about the plugin such as
     * its ID, name, version, author, and compatibility requirements.
     * 
     * <p>This method may be called multiple times and should return consistent results.
     * It is recommended to cache the metadata object.
     * 
     * @return The plugin's metadata, never null
     */
    PluginMetadata getMetadata();
}
