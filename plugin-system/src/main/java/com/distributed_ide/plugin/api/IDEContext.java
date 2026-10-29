package com.distributed_ide.plugin.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context providing access to IDE services and APIs for plugins.
 *
 * <p>The IDEContext is the main entry point for plugins to interact with the IDE.
 * It provides access to various managers that control different aspects of the IDE:
 * <ul>
 *   <li>{@link EditorManager} - Editor and file operations</li>
 *   <li>{@link UIManager} - UI components, menus, toolbars</li>
 *   <li>{@link ProjectManager} - Project and workspace operations</li>
 *   <li>{@link TerminalManager} - Terminal and command execution</li>
 *   <li>{@link GitManager} - Git version control operations</li>
 *   <li>{@link EventManager} - Event subscription and publishing</li>
 *   <li>{@link ConfigurationManager} - Configuration persistence</li>
 *   <li>{@link CollaborationManager} - Real-time collaboration features</li>
 *   <li>{@link ThemeManager} - Theme and styling operations</li>
 *   <li>{@link Logger} - Logging facilities</li>
 * </ul>
 *
 * <p>Example usage in a plugin:
 * <pre>{@code
 * public class MyPlugin implements Plugin {
 *     @Override
 *     public void onEnable(IDEContext context) {
 *         // Get logger
 *         Logger logger = context.getLogger();
 *         logger.info("Plugin enabled!");
 *
 *         // Register a menu item
 *         UIManager ui = context.getUIManager();
 *         ui.registerMenu(MenuRegistration.builder()
 *             .path("Tools/My Plugin/My Action")
 *             .action(() -> logger.info("Action executed!"))
 *             .build());
 *
 *         // Access editor
 *         EditorManager editor = context.getEditorManager();
 *         editor.openFile("/path/to/file.txt");
 *     }
 * }
 * }</pre>
 *
 * <p>Thread Safety: This class is thread-safe.
 *
 * @see Plugin
 */
public class IDEContext {

    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    // Core IDE services
    private Logger logger;
    private EditorManager editorManager;
    private UIManager uiManager;
    private ProjectManager projectManager;
    private TerminalManager terminalManager;
    private GitManager gitManager;
    private EventManager eventManager;
    private ConfigurationManager configurationManager;
    private CollaborationManager collaborationManager;
    private ThemeManager themeManager;

    /**
     * Creates a new IDEContext with default configuration.
     */
    public IDEContext() {
        // Managers will be injected by the IDE
    }

    // ========== Service Getters ==========

    /**
     * Gets the logger for plugin logging.
     *
     * @return The logger instance (never null)
     * @throws IllegalStateException if logger is not initialized
     */
    @Nonnull
    public Logger getLogger() {
        if (logger == null) {
            throw new IllegalStateException("Logger not initialized in IDEContext");
        }
        return logger;
    }

    /**
     * Sets the logger instance.
     * This method should only be called by the IDE during initialization.
     *
     * @param logger The logger instance
     */
    public void setLogger(@Nonnull Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    /**
     * Gets the editor manager for file and editor operations.
     *
     * @return The editor manager instance (never null)
     * @throws IllegalStateException if not initialized
     */
    @Nonnull
    public EditorManager getEditorManager() {
        if (editorManager == null) {
            throw new IllegalStateException("EditorManager not initialized in IDEContext");
        }
        return editorManager;
    }

    /**
     * Sets the editor manager.
     *
     * @param editorManager The editor manager instance
     */
    public void setEditorManager(@Nonnull EditorManager editorManager) {
        this.editorManager = Objects.requireNonNull(editorManager, "editorManager cannot be null");
    }

    /**
     * Gets the UI manager for UI operations.
     *
     * @return The UI manager instance (never null)
     * @throws IllegalStateException if not initialized
     */
    @Nonnull
    public UIManager getUIManager() {
        if (uiManager == null) {
            throw new IllegalStateException("UIManager not initialized in IDEContext");
        }
        return uiManager;
    }

    /**
     * Sets the UI manager.
     *
     * @param uiManager The UI manager instance
     */
    public void setUIManager(@Nonnull UIManager uiManager) {
        this.uiManager = Objects.requireNonNull(uiManager, "uiManager cannot be null");
    }

    /**
     * Gets the project manager for project operations.
     *
     * @return The project manager instance (never null)
     * @throws IllegalStateException if not initialized
     */
    @Nonnull
    public ProjectManager getProjectManager() {
        if (projectManager == null) {
            throw new IllegalStateException("ProjectManager not initialized in IDEContext");
        }
        return projectManager;
    }

    /**
     * Sets the project manager.
     *
     * @param projectManager The project manager instance
     */
    public void setProjectManager(@Nonnull ProjectManager projectManager) {
        this.projectManager = Objects.requireNonNull(projectManager, "projectManager cannot be null");
    }

    /**
     * Gets the terminal manager for terminal operations.
     *
     * @return The terminal manager instance (never null)
     * @throws IllegalStateException if not initialized
     */
    @Nonnull
    public TerminalManager getTerminalManager() {
        if (terminalManager == null) {
            throw new IllegalStateException("TerminalManager not initialized in IDEContext");
        }
        return terminalManager;
    }

    /**
     * Sets the terminal manager.
     *
     * @param terminalManager The terminal manager instance
     */
    public void setTerminalManager(@Nonnull TerminalManager terminalManager) {
        this.terminalManager = Objects.requireNonNull(terminalManager, "terminalManager cannot be null");
    }

    /**
     * Gets the Git manager for version control operations.
     *
     * @return The Git manager instance (never null)
     * @throws IllegalStateException if not initialized
     */
    @Nonnull
    public GitManager getGitManager() {
        if (gitManager == null) {
            throw new IllegalStateException("GitManager not initialized in IDEContext");
        }
        return gitManager;
    }

    /**
     * Sets the Git manager.
     *
     * @param gitManager The Git manager instance
     */
    public void setGitManager(@Nonnull GitManager gitManager) {
        this.gitManager = Objects.requireNonNull(gitManager, "gitManager cannot be null");
    }

    /**
     * Gets the event manager for event handling.
     *
     * @return The event manager instance (never null)
     * @throws IllegalStateException if not initialized
     */
    @Nonnull
    public EventManager getEventManager() {
        if (eventManager == null) {
            throw new IllegalStateException("EventManager not initialized in IDEContext");
        }
        return eventManager;
    }

    /**
     * Sets the event manager.
     *
     * @param eventManager The event manager instance
     */
    public void setEventManager(@Nonnull EventManager eventManager) {
        this.eventManager = Objects.requireNonNull(eventManager, "eventManager cannot be null");
    }

    /**
     * Gets the configuration manager for plugin configuration.
     *
     * @return The configuration manager instance (never null)
     * @throws IllegalStateException if not initialized
     */
    @Nonnull
    public ConfigurationManager getConfigurationManager() {
        if (configurationManager == null) {
            throw new IllegalStateException("ConfigurationManager not initialized in IDEContext");
        }
        return configurationManager;
    }

    /**
     * Sets the configuration manager.
     *
     * @param configurationManager The configuration manager instance
     */
    public void setConfigurationManager(@Nonnull ConfigurationManager configurationManager) {
        this.configurationManager = Objects.requireNonNull(configurationManager,
            "configurationManager cannot be null");
    }

    /**
     * Gets the collaboration manager for real-time collaboration.
     *
     * @return The collaboration manager instance (never null)
     * @throws IllegalStateException if not initialized
     */
    @Nonnull
    public CollaborationManager getCollaborationManager() {
        if (collaborationManager == null) {
            throw new IllegalStateException("CollaborationManager not initialized in IDEContext");
        }
        return collaborationManager;
    }

    /**
     * Sets the collaboration manager.
     *
     * @param collaborationManager The collaboration manager instance
     */
    public void setCollaborationManager(@Nonnull CollaborationManager collaborationManager) {
        this.collaborationManager = Objects.requireNonNull(collaborationManager,
            "collaborationManager cannot be null");
    }

    /**
     * Gets the theme manager for theming operations.
     *
     * @return The theme manager instance (never null)
     * @throws IllegalStateException if not initialized
     */
    @Nonnull
    public ThemeManager getThemeManager() {
        if (themeManager == null) {
            throw new IllegalStateException("ThemeManager not initialized in IDEContext");
        }
        return themeManager;
    }

    /**
     * Sets the theme manager.
     *
     * @param themeManager The theme manager instance
     */
    public void setThemeManager(@Nonnull ThemeManager themeManager) {
        this.themeManager = Objects.requireNonNull(themeManager, "themeManager cannot be null");
    }

    // ========== Generic Service Registration ==========

    /**
     * Registers a custom service in the context.
     *
     * @param serviceClass The service interface class
     * @param service The service implementation
     * @param <T> The service type
     */
    public <T> void registerService(@Nonnull Class<T> serviceClass, @Nonnull T service) {
        Objects.requireNonNull(serviceClass, "serviceClass cannot be null");
        Objects.requireNonNull(service, "service cannot be null");
        services.put(serviceClass, service);
    }

    /**
     * Gets a registered service by its class.
     *
     * @param serviceClass The service interface class
     * @param <T> The service type
     * @return The service instance, or null if not registered
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getService(@Nonnull Class<T> serviceClass) {
        Objects.requireNonNull(serviceClass, "serviceClass cannot be null");
        return (T) services.get(serviceClass);
    }

    // ========== Property Management ==========

    /**
     * Sets a context property.
     *
     * @param key The property key
     * @param value The property value
     */
    public void setProperty(@Nonnull String key, @Nullable Object value) {
        Objects.requireNonNull(key, "key cannot be null");
        if (value != null) {
            properties.put(key, value);
        } else {
            properties.remove(key);
        }
    }

    /**
     * Gets a context property.
     *
     * @param key The property key
     * @return The property value, or null if not set
     */
    @Nullable
    public Object getProperty(@Nonnull String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return properties.get(key);
    }

    /**
     * Gets a context property with a default value.
     *
     * @param key The property key
     * @param defaultValue The default value if property is not set
     * @return The property value, or defaultValue if not set
     */
    @Nonnull
    public Object getProperty(@Nonnull String key, @Nonnull Object defaultValue) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(defaultValue, "defaultValue cannot be null");
        return properties.getOrDefault(key, defaultValue);
    }

    /**
     * Removes a context property.
     *
     * @param key The property key
     * @return The previous value, or null if not set
     */
    @Nullable
    public Object removeProperty(@Nonnull String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return properties.remove(key);
    }

    /**
     * Gets all context properties.
     *
     * @return A copy of all properties
     */
    @Nonnull
    public Map<String, Object> getProperties() {
        return new ConcurrentHashMap<>(properties);
    }

    /**
     * Clears all context properties.
     */
    public void clear() {
        properties.clear();
    }
}
