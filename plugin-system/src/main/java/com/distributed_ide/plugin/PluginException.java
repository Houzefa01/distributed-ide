package com.distributed_ide.plugin;

import javax.annotation.Nullable;

/**
 * Exception thrown during plugin lifecycle operations.
 *
 * <p>This exception can carry additional context about the plugin that failed,
 * including its ID and the target state it was trying to reach.
 */
public class PluginException extends Exception {

    private final String pluginId;
    private final PluginState targetState;

    /**
     * Creates a new PluginException with just a message.
     *
     * @param message The error message
     */
    public PluginException(String message) {
        super(message);
        this.pluginId = null;
        this.targetState = null;
    }

    /**
     * Creates a new PluginException with a message and cause.
     *
     * @param message The error message
     * @param cause The underlying cause
     */
    public PluginException(String message, Throwable cause) {
        super(message, cause);
        this.pluginId = null;
        this.targetState = null;
    }

    /**
     * Creates a new PluginException with plugin ID and message.
     *
     * @param pluginId The ID of the plugin that failed
     * @param message The error message
     */
    public PluginException(String pluginId, String message) {
        super(message);
        this.pluginId = pluginId;
        this.targetState = null;
    }

    /**
     * Creates a new PluginException with plugin ID, message, and cause.
     *
     * @param pluginId The ID of the plugin that failed
     * @param message The error message
     * @param cause The underlying cause
     */
    public PluginException(String pluginId, String message, Throwable cause) {
        super(message, cause);
        this.pluginId = pluginId;
        this.targetState = null;
    }

    /**
     * Creates a new PluginException with plugin ID, target state, and message.
     *
     * @param pluginId The ID of the plugin that failed
     * @param targetState The state the plugin was trying to reach
     * @param message The error message
     */
    public PluginException(String pluginId, PluginState targetState, String message) {
        super(message);
        this.pluginId = pluginId;
        this.targetState = targetState;
    }

    /**
     * Creates a new PluginException with full context.
     *
     * @param pluginId The ID of the plugin that failed
     * @param targetState The state the plugin was trying to reach
     * @param message The error message
     * @param cause The underlying cause
     */
    public PluginException(String pluginId, PluginState targetState, String message, Throwable cause) {
        super(message, cause);
        this.pluginId = pluginId;
        this.targetState = targetState;
    }

    /**
     * Gets the ID of the plugin that threw this exception.
     *
     * @return Plugin ID, or null if not applicable
     */
    @Nullable
    public String getPluginId() {
        return pluginId;
    }

    /**
     * Gets the target state the plugin was trying to reach when the exception occurred.
     *
     * @return Target state, or null if not applicable
     */
    @Nullable
    public PluginState getTargetState() {
        return targetState;
    }
}
