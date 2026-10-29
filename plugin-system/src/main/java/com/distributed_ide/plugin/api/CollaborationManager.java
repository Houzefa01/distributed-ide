package com.distributed_ide.plugin.api;


import java.util.List;
import java.util.Optional;

/**
 * Provides access to collaboration functionality for plugins.
 */
public interface CollaborationManager {
    
    /**
     * Check if collaboration is enabled.
     * 
     * @return true if collaboration is enabled
     */
    boolean isCollaborationEnabled();
    
    /**
     * Get the current room ID.
     * 
     * @return Current room ID, or empty if not connected
     */
    
    Optional<String> getCurrentRoomId();
    
    /**
     * Get the current user ID.
     * 
     * @return Current user ID, or empty if not connected
     */
    
    Optional<String> getCurrentUserId();
    
    /**
     * Get all connected collaborators.
     * 
     * @return List of collaborators
     */
    
    List<Collaborator> getCollaborators();
    
    /**
     * Send a chat message.
     * 
     * @param message Message to send
     */
    void sendChatMessage( String message);
    
    /**
     * Register a collaboration listener.
     * 
     * @param listener Listener to register
     * @return Registration handle
     */
    
    CollaborationListenerRegistration addListener( CollaborationListener listener);
    
    interface Collaborator {
        
        String getUserId();
        
        
        String getUsername();
        
        
        String getColor();
        
        boolean isActive();
    }
    
    interface CollaborationListener {
        default void onCollaboratorJoined( Collaborator collaborator) {}
        default void onCollaboratorLeft( String userId) {}
        default void onChatMessage( String userId,  String username,  String message) {}
        default void onRoomJoined( String roomId) {}
        default void onRoomLeft() {}
    }
    
    interface CollaborationListenerRegistration {
        void remove();
    }
}
