package com.distributed_ide.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolated ClassLoader for plugin JAR files.
 * 
 * <p>This ClassLoader provides:
 * <ul>
 *   <li>Isolation between plugins and the parent application</li>
 *   <li>Ability to load and unload plugins dynamically</li>
 *   <li>Resource tracking for cleanup</li>
 *   <li>Plugin-specific class resolution</li>
 * </ul>
 * 
 * <p>Class Loading Strategy:
 * <ol>
 *   <li>Check if class was already loaded by this loader</li>
 *   <li>Try to load from plugin JAR</li>
 *   <li>Delegate to parent for system/IDE classes</li>
 * </ol>
 * 
 * <p>This design ensures that:
 * <ul>
 *   <li>Plugins can have their own versions of dependencies</li>
 *   <li>System classes (java.*, javax.*, etc.) are always from parent</li>
 *   <li>IDE API classes are shared from parent</li>
 *   <li>Plugin-specific classes are isolated</li>
 * </ul>
 * 
 * <p>Thread Safety: This class is thread-safe.
 */
public class PluginClassLoader extends URLClassLoader {
    
    private static final Logger LOG = LoggerFactory.getLogger(PluginClassLoader.class);
    
    private final String pluginId;
    private final Path jarPath;
    private final Set<String> loadedClasses = ConcurrentHashMap.newKeySet();
    private volatile boolean closed = false;
    
    /**
     * Creates a new PluginClassLoader.
     * 
     * @param pluginId Unique identifier for the plugin
     * @param jarPath Path to the plugin JAR file
     * @param parent Parent ClassLoader (typically the system ClassLoader)
     * @throws IOException if the JAR file cannot be read
     */
    public PluginClassLoader(String pluginId, Path jarPath, ClassLoader parent) throws IOException {
        super(new URL[]{jarPath.toUri().toURL()}, parent);
        this.pluginId = pluginId;
        this.jarPath = jarPath;
        
        LOG.debug("Created PluginClassLoader for plugin '{}' from: {}", pluginId, jarPath);
    }
    
    /**
     * Gets the plugin ID associated with this ClassLoader.
     * 
     * @return The plugin ID
     */
    public String getPluginId() {
        return pluginId;
    }
    
    /**
     * Gets the JAR path for this plugin.
     * 
     * @return The JAR file path
     */
    public Path getJarPath() {
        return jarPath;
    }
    
    /**
     * Gets all classes loaded by this ClassLoader.
     * 
     * @return An unmodifiable set of loaded class names
     */
    public Set<String> getLoadedClasses() {
        return Collections.unmodifiableSet(loadedClasses);
    }
    
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (closed) {
            throw new IllegalStateException("PluginClassLoader for " + pluginId + " has been closed");
        }
        
        synchronized (getClassLoadingLock(name)) {
            // First, check if the class has already been loaded
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass != null) {
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
            
            // System classes are always loaded by parent
            if (isSystemClass(name)) {
                return super.loadClass(name, resolve);
            }
            
            // IDE API classes should come from parent
            if (isIDEApiClass(name)) {
                return super.loadClass(name, resolve);
            }
            
            // Try to load from plugin JAR first
            try {
                loadedClass = findClass(name);
                loadedClasses.add(name);
                
                if (resolve) {
                    resolveClass(loadedClass);
                }
                
                LOG.trace("Loaded class '{}' from plugin '{}'", name, pluginId);
                return loadedClass;
                
            } catch (ClassNotFoundException e) {
                // If not found in plugin, try parent
                return super.loadClass(name, resolve);
            }
        }
    }
    
    /**
     * Checks if a class is a system class that should always be loaded by parent.
     * 
     * @param className The class name to check
     * @return true if this is a system class
     */
    private boolean isSystemClass(String className) {
        return className.startsWith("java.") ||
               className.startsWith("javax.") ||
               className.startsWith("sun.") ||
               className.startsWith("com.sun.") ||
               className.startsWith("jdk.");
    }
    
    /**
     * Checks if a class is an IDE API class that should be shared.
     * 
     * @param className The class name to check
     * @return true if this is an IDE API class
     */
    private boolean isIDEApiClass(String className) {
        return className.startsWith("com.distributed_ide.plugin.api.") ||
               className.startsWith("org.slf4j.") ||
               className.startsWith("javafx.");
    }
    
    @Override
    public URL getResource(String name) {
        if (closed) {
            return null;
        }
        
        // First try to find in plugin JAR
        URL url = findResource(name);
        if (url != null) {
            return url;
        }
        
        // Fall back to parent
        return super.getResource(name);
    }
    
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            
            LOG.debug("Closing PluginClassLoader for plugin '{}'. Loaded {} classes.",
                pluginId, loadedClasses.size());
            
            // Clear loaded classes tracking
            loadedClasses.clear();
            
            // Close the URLClassLoader (releases JAR file locks)
            super.close();
            
            LOG.info("PluginClassLoader closed for plugin '{}'", pluginId);
        }
    }
    
    /**
     * Checks if this ClassLoader has been closed.
     * 
     * @return true if closed
     */
    public boolean isClosed() {
        return closed;
    }
    
    @Override
    public String toString() {
        return String.format("PluginClassLoader[plugin=%s, jar=%s, closed=%s, loadedClasses=%d]",
            pluginId, jarPath.getFileName(), closed, loadedClasses.size());
    }
}
