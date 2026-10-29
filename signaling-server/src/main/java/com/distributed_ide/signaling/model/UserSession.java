package com.distributed_ide.signaling.model;

import org.java_websocket.WebSocket;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Représente une session utilisateur dans une salle de signalisation.
 */
public class UserSession {
    private final String userId;
    private final String roomId;
    private final WebSocket webSocket;
    private volatile long connectedAt;
    private volatile long lastHeartbeat;
    private volatile boolean active;
    private final Map<String, Object> metadata;
    
    public UserSession(String userId, String roomId, WebSocket webSocket) {
        this.userId = userId;
        this.roomId = roomId;
        this.webSocket = webSocket;
        this.connectedAt = System.currentTimeMillis();
        this.lastHeartbeat = System.currentTimeMillis();
        this.active = true;
        this.metadata = Collections.synchronizedMap(new HashMap<>());
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public WebSocket getWebSocket() {
        return webSocket;
    }
    
    public long getConnectedAt() {
        return connectedAt;
    }
    
    public long getLastHeartbeat() {
        return lastHeartbeat;
    }
    
    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public long getConnectionDuration() {
        return System.currentTimeMillis() - connectedAt;
    }
    
    public boolean isConnected() {
        return webSocket != null && webSocket.isOpen();
    }
    
    public void sendMessage(String jsonMessage) {
        if (isConnected()) {
            webSocket.send(jsonMessage);
        }
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadataValue(String key, Object value) {
        metadata.put(key, value);
    }
    
    public Object getMetadataValue(String key) {
        return metadata.get(key);
    }
}
