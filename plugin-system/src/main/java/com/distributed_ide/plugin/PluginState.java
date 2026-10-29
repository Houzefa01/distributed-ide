package com.distributed_ide.plugin;

/**
 * Represents the lifecycle state of a plugin.
 *
 * <p>Plugin State Transitions:
 * <pre>
 * LOADED → ENABLED → DISABLED → LOADED
 *    ↓        ↓         ↓
 *  ERROR ← ERROR ← ← ERROR
 * </pre>
 *
 * <p>State descriptions:
 * <ul>
 *   <li>LOADED: Plugin loaded into memory, classes available, onEnable() not yet called</li>
 *   <li>ENABLED: Plugin active and running, onEnable() successfully called</li>
 *   <li>DISABLED: Plugin disabled but remains loaded, onDisable() called</li>
 *   <li>ERROR: Plugin encountered an error during load, enable, or runtime</li>
 * </ul>
 */
public enum PluginState {
    /**
     * Plugin is loaded but not enabled.
     * The plugin's classes are available but onEnable() has not been called.
     */
    LOADED,

    /**
     * Plugin is currently active and running.
     * The onEnable() method has been successfully called.
     */
    ENABLED,

    /**
     * Plugin has been disabled but remains loaded in memory.
     * The onDisable() method has been called.
     */
    DISABLED,

    /**
     * Plugin encountered an error during load, enable, or runtime.
     * The plugin is in an invalid state and may need to be unloaded.
     */
    ERROR;

    /**
     * Checks if the plugin is in a state where it can be enabled.
     *
     * @return true if the plugin can transition to ENABLED state
     */
    public boolean canEnable() {
        return this == LOADED || this == DISABLED;
    }

    /**
     * Checks if the plugin is in a state where it can be disabled.
     *
     * @return true if the plugin can transition to DISABLED state
     */
    public boolean canDisable() {
        return this == ENABLED;
    }

    /**
     * Checks if the plugin is currently active.
     *
     * @return true if the plugin is in ENABLED state
     */
    public boolean isActive() {
        return this == ENABLED;
    }

    /**
     * Checks if the plugin is in an error state.
     *
     * @return true if the plugin is in ERROR state
     */
    public boolean isError() {
        return this == ERROR;
    }
}
