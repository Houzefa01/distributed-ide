package com.distributed_ide.plugin.api;


import java.util.List;

/**
 * Provides access to terminal functionality for plugins.
 */
public interface TerminalManager {
    
    /**
     * Create a new terminal session.
     * 
     * @param name Terminal name
     * @return Created terminal session
     */
    
    TerminalSession createTerminal( String name);
    
    /**
     * Get all active terminal sessions.
     * 
     * @return List of active terminals
     */
    
    List<TerminalSession> getActiveTerminals();
    
    /**
     * Get a terminal session by ID.
     * 
     * @param sessionId Session ID
     * @return Terminal session, or null if not found
     */
    
    TerminalSession getTerminal( String sessionId);
    
    /**
     * Close a terminal session.
     * 
     * @param sessionId Session ID to close
     * @return true if closed successfully
     */
    boolean closeTerminal( String sessionId);
    
    /**
     * Execute a command in a terminal.
     * 
     * @param sessionId Session ID
     * @param command Command to execute
     */
    void executeCommand( String sessionId,  String command);
    
    interface TerminalSession {
        
        String getSessionId();
        
        
        String getName();
        
        boolean isActive();
        
        void sendInput( String input);
        
        void addOutputListener( OutputListener listener);
        
        void removeOutputListener( OutputListener listener);
    }
    
    interface OutputListener {
        void onOutput( String text, boolean isError);
    }
}
