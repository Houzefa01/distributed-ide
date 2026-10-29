package com.distributed_ide.plugin.api;


/**
 * Logger interface for plugins.
 */
public interface Logger {
    
    /**
     * Log a debug message.
     * 
     * @param message Message to log
     */
    void debug( String message);
    
    /**
     * Log a debug message with exception.
     * 
     * @param message Message to log
     * @param throwable Exception to log
     */
    void debug( String message,  Throwable throwable);
    
    /**
     * Log an info message.
     * 
     * @param message Message to log
     */
    void info( String message);
    
    /**
     * Log an info message with exception.
     * 
     * @param message Message to log
     * @param throwable Exception to log
     */
    void info( String message,  Throwable throwable);
    
    /**
     * Log a warning message.
     * 
     * @param message Message to log
     */
    void warn( String message);
    
    /**
     * Log a warning message with exception.
     * 
     * @param message Message to log
     * @param throwable Exception to log
     */
    void warn( String message,  Throwable throwable);
    
    /**
     * Log an error message.
     * 
     * @param message Message to log
     */
    void error( String message);
    
    /**
     * Log an error message with exception.
     * 
     * @param message Message to log
     * @param throwable Exception to log
     */
    void error( String message,  Throwable throwable);
}
