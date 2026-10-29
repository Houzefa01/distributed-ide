package com.distributed_ide.javafx_client;

import com.distributed_ide.plugin.SimplePluginManager;
import com.distributed_ide.plugin.api.IDEContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class SimplePluginIntegration {

    private static final Logger LOG = LoggerFactory.getLogger(SimplePluginIntegration.class);

    private SimplePluginManager pluginManager;
    private IDEContext ideContext;
    private boolean initialized = false;

    /**
     * Initialize the plugin system.
     */
    public void initialize() {
        if (initialized) {
            LOG.warn("Plugin system already initialized");
            return;
        }

        try {
            // Create IDE context
            ideContext = new IDEContext();

            // Create plugin manager with context
            pluginManager = new SimplePluginManager(ideContext);

            initialized = true;
            LOG.info("Plugin system initialized successfully");

        } catch (Exception e) {
            LOG.error("Failed to initialize plugin system", e);
        }
    }

    /**
     * Shutdown the plugin system.
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }

        try {
            if (pluginManager != null) {
                pluginManager.shutdown();
            }
            initialized = false;
            LOG.info("Plugin system shutdown successfully");

        } catch (Exception e) {
            LOG.error("Failed to shutdown plugin system", e);
        }
    }

    /**
     * Load a plugin from a JAR file.
     */
    public boolean loadPlugin(File jarFile) {
        if (!initialized) {
            LOG.warn("Plugin system not initialized");
            return false;
        }

        try {
            pluginManager.loadPlugin(jarFile.toPath());
            LOG.info("Plugin loaded successfully: {}", jarFile.getName());
            return true;
        } catch (Exception e) {
            LOG.error("Error loading plugin: " + jarFile.getName(), e);
            return false;
        }
    }
    
    /**
     * Get the plugin manager.
     */
    public SimplePluginManager getPluginManager() {
        return pluginManager;
    }
    
    /**
     * Get the IDE context.
     */
    public IDEContext getIdeContext() {
        return ideContext;
    }
    
    /**
     * Check if the plugin system is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
}
