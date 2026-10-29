package com.distributed_ide.plugin.api;


/**
 * Provides event system functionality for plugins.
 */
public interface EventManager {
    
    /**
     * Subscribe to an event.
     * 
     * @param eventType Event type
     * @param listener Event listener
     * @param <T> Event type
     * @return Registration handle
     */
    
    <T> EventRegistration<T> subscribe( Class<T> eventType,  EventListener<T> listener);
    
    /**
     * Publish an event.
     * 
     * @param event Event to publish
     * @param <T> Event type
     */
    <T> void publish( T event);
    
    /**
     * Unsubscribe all listeners for a plugin.
     * 
     * @param pluginId Plugin ID
     */
    void unsubscribeAll( String pluginId);
    
    interface EventListener<T> {
        void onEvent( T event);
    }
    
    interface EventRegistration<T> {
        void remove();
        
        
        Class<T> getEventType();
    }
}
