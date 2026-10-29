package com.distributed_ide.plugin;

import com.distributed_ide.plugin.api.IDEContext;
import com.distributed_ide.plugin.api.Plugin;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Thread-safe implementation of PluginManager for the Distributed IDE.
 *
 * <p>This implementation provides:
 * <ul>
 *   <li>Isolated ClassLoader for each plugin</li>
 *   <li>Complete plugin lifecycle management</li>
 *   <li>Thread-safe operations with ReadWriteLock</li>
 *   <li>Comprehensive error handling and logging</li>
 *   <li>Metadata validation and version checking</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * SimplePluginManager manager = new SimplePluginManager(ideContext);
 *
 * // Load and enable a plugin
 * Path pluginJar = Paths.get("/path/to/plugin.jar");
 * manager.loadPlugin(pluginJar);
 * manager.enablePlugin("my-plugin-id");
 *
 * // List all plugins
 * for (LoadedPlugin plugin : manager.getLoadedPlugins()) {
 *     System.out.println(plugin.getDisplayName() + " - " + plugin.getState());
 * }
 * }</pre>
 *
 * @see PluginManager
 * @see LoadedPlugin
 */
public class SimplePluginManager implements PluginManager {

    private static final Logger LOG = LoggerFactory.getLogger(SimplePluginManager.class);
    private static final String PLUGIN_METADATA_PATH = "META-INF/plugin.json";

    private final Map<String, LoadedPlugin> loadedPlugins = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final IDEContext ideContext;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * Creates a new SimplePluginManager with the given IDE context.
     *
     * @param ideContext The IDE context to provide to plugins
     */
    public SimplePluginManager(@Nonnull IDEContext ideContext) {
        this.ideContext = Objects.requireNonNull(ideContext, "ideContext cannot be null");
        LOG.info("SimplePluginManager initialized");
    }

    @Override
    public void loadPlugin(Path pluginPath) throws PluginException {
        if (pluginPath == null) {
            throw new IllegalArgumentException("pluginPath cannot be null");
        }

        if (!Files.exists(pluginPath)) {
            throw new IllegalArgumentException("Plugin file does not exist: " + pluginPath);
        }

        if (!Files.isRegularFile(pluginPath)) {
            throw new IllegalArgumentException("Plugin path is not a file: " + pluginPath);
        }

        lock.writeLock().lock();
        try {
            LOG.info("Loading plugin from: {}", pluginPath);

            // Read plugin metadata
            PluginMetadata metadata = loadMetadata(pluginPath);
            String pluginId = metadata.getId();

            // Check for duplicate plugin
            if (loadedPlugins.containsKey(pluginId)) {
                throw new PluginException("Plugin already loaded: " + pluginId);
            }

            // Create isolated ClassLoader
            PluginClassLoader classLoader = new PluginClassLoader(
                pluginId,
                pluginPath,
                getClass().getClassLoader()
            );

            // Load and instantiate plugin class
            Plugin pluginInstance = instantiatePlugin(metadata, classLoader);

            // Create LoadedPlugin wrapper
            LoadedPlugin loadedPlugin = new LoadedPlugin(
                pluginId,
                pluginInstance,
                metadata,
                pluginPath,
                classLoader,
                PluginState.LOADED
            );

            loadedPlugins.put(pluginId, loadedPlugin);

            LOG.info("Successfully loaded plugin: {} v{} by {}",
                metadata.getName(), metadata.getVersion(), metadata.getAuthor());

        } catch (PluginException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to load plugin from: {}", pluginPath, e);
            throw new PluginException("Failed to load plugin: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void enablePlugin(String pluginId) throws PluginException {
        validatePluginId(pluginId);

        lock.writeLock().lock();
        try {
            LoadedPlugin plugin = getPluginOrThrow(pluginId);

            if (!plugin.getState().canEnable()) {
                throw new PluginException(
                    String.format("Cannot enable plugin '%s' in state %s", pluginId, plugin.getState())
                );
            }

            LOG.info("Enabling plugin: {}", pluginId);

            try {
                plugin.getInstance().onEnable(ideContext);
                plugin.setState(PluginState.ENABLED);

                LOG.info("Successfully enabled plugin: {}", plugin.getDisplayName());

            } catch (Exception e) {
                LOG.error("Failed to enable plugin: {}", pluginId, e);
                plugin.setError("Failed to enable: " + e.getMessage(), e);
                throw new PluginException("Failed to enable plugin: " + e.getMessage(), e);
            }

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void disablePlugin(String pluginId) throws PluginException {
        validatePluginId(pluginId);

        lock.writeLock().lock();
        try {
            LoadedPlugin plugin = getPluginOrThrow(pluginId);

            if (!plugin.getState().canDisable()) {
                throw new PluginException(
                    String.format("Cannot disable plugin '%s' in state %s", pluginId, plugin.getState())
                );
            }

            LOG.info("Disabling plugin: {}", pluginId);

            try {
                plugin.getInstance().onDisable();
                plugin.setState(PluginState.DISABLED);

                LOG.info("Successfully disabled plugin: {}", plugin.getDisplayName());

            } catch (Exception e) {
                LOG.error("Error disabling plugin: {}", pluginId, e);
                plugin.setError("Failed to disable: " + e.getMessage(), e);
                throw new PluginException("Failed to disable plugin: " + e.getMessage(), e);
            }

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void unloadPlugin(String pluginId) throws PluginException {
        validatePluginId(pluginId);

        lock.writeLock().lock();
        try {
            LoadedPlugin plugin = getPluginOrThrow(pluginId);

            // Disable first if enabled
            if (plugin.getState() == PluginState.ENABLED) {
                try {
                    disablePlugin(pluginId);
                } catch (Exception e) {
                    LOG.warn("Error disabling plugin during unload: {}", pluginId, e);
                }
            }

            LOG.info("Unloading plugin: {}", pluginId);

            // Remove from registry
            loadedPlugins.remove(pluginId);

            // Close ClassLoader to release resources
            try {
                if (plugin.getClassLoader() instanceof PluginClassLoader pcl) {
                    pcl.close();
                }
            } catch (IOException e) {
                LOG.warn("Error closing ClassLoader for plugin: {}", pluginId, e);
            }

            LOG.info("Successfully unloaded plugin: {}", plugin.getDisplayName());

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<LoadedPlugin> getLoadedPlugins() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(loadedPlugins.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<LoadedPlugin> getPlugin(String pluginId) {
        validatePluginId(pluginId);

        lock.readLock().lock();
        try {
            return Optional.ofNullable(loadedPlugins.get(pluginId));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Shuts down the plugin manager and unloads all plugins.
     */
    public void shutdown() {
        lock.writeLock().lock();
        try {
            LOG.info("Shutting down plugin manager. Unloading {} plugins.", loadedPlugins.size());

            // Unload all plugins
            List<String> pluginIds = new ArrayList<>(loadedPlugins.keySet());
            for (String pluginId : pluginIds) {
                try {
                    unloadPlugin(pluginId);
                } catch (Exception e) {
                    LOG.error("Error unloading plugin during shutdown: {}", pluginId, e);
                }
            }

            LOG.info("Plugin manager shutdown complete");

        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========== Private Helper Methods ==========

    /**
     * Loads plugin metadata from JAR file.
     */
    private PluginMetadata loadMetadata(Path jarPath) throws PluginException {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            ZipEntry metadataEntry = jarFile.getEntry(PLUGIN_METADATA_PATH);

            if (metadataEntry == null) {
                throw new PluginException(
                    "Plugin metadata not found. Expected: " + PLUGIN_METADATA_PATH
                );
            }

            try (InputStream is = jarFile.getInputStream(metadataEntry)) {
                PluginMetadata metadata = jsonMapper.readValue(is, PluginMetadata.class);

                // Validate required fields
                validateMetadata(metadata);

                return metadata;
            }

        } catch (IOException e) {
            throw new PluginException("Failed to read plugin metadata: " + e.getMessage(), e);
        }
    }

    /**
     * Validates plugin metadata.
     */
    private void validateMetadata(PluginMetadata metadata) throws PluginException {
        if (metadata.getId() == null || metadata.getId().isEmpty()) {
            throw new PluginException("Plugin metadata missing required field: id");
        }
        if (metadata.getName() == null || metadata.getName().isEmpty()) {
            throw new PluginException("Plugin metadata missing required field: name");
        }
        if (metadata.getVersion() == null || metadata.getVersion().isEmpty()) {
            throw new PluginException("Plugin metadata missing required field: version");
        }
        if (metadata.getMainClass() == null || metadata.getMainClass().isEmpty()) {
            throw new PluginException("Plugin metadata missing required field: mainClass");
        }
    }

    /**
     * Instantiates the plugin main class.
     */
    private Plugin instantiatePlugin(PluginMetadata metadata, ClassLoader classLoader)
            throws PluginException {

        String mainClassName = metadata.getMainClass();

        try {
            Class<?> pluginClass = classLoader.loadClass(mainClassName);

            if (!Plugin.class.isAssignableFrom(pluginClass)) {
                throw new PluginException(
                    "Main class does not implement Plugin interface: " + mainClassName
                );
            }

            Object instance = pluginClass.getDeclaredConstructor().newInstance();
            return (Plugin) instance;

        } catch (ClassNotFoundException e) {
            throw new PluginException("Plugin main class not found: " + mainClassName, e);
        } catch (Exception e) {
            throw new PluginException(
                "Failed to instantiate plugin main class: " + mainClassName + " - " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Gets a plugin or throws exception if not found.
     */
    private LoadedPlugin getPluginOrThrow(String pluginId) throws PluginException {
        LoadedPlugin plugin = loadedPlugins.get(pluginId);
        if (plugin == null) {
            throw new PluginException("Plugin not found: " + pluginId);
        }
        return plugin;
    }

    /**
     * Validates plugin ID parameter.
     */
    private void validatePluginId(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) {
            throw new IllegalArgumentException("pluginId cannot be null or empty");
        }
    }
}
