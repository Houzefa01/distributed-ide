package com.distributed_ide.sync.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Représente un client connecté au serveur de synchronisation.
 * Suivi des opérations en attente et de l'état.
 */
public class ClientSession {
    private final String clientId;
    private final String nodeId;
    private final long connectedAt;
    
    private final Set<String> subscribedDocuments;
    private final Queue<SyncMessage.OperationData> pendingOperations;
    private volatile long lastAcknowledgedTimestamp;
    private volatile boolean isConnected;
    private volatile long lastHeartbeat;

    private volatile String username;
    private volatile String role; // "ADMIN" or "CLIENT"
    private volatile boolean authenticated;

    // Rate limiting
    private volatile long rateLimitWindowStart;
    private volatile int rateLimitCount;

    public ClientSession(String clientId, String nodeId) {
        this.clientId = clientId;
        this.nodeId = nodeId;
        this.connectedAt = System.currentTimeMillis();
        this.subscribedDocuments = Collections.synchronizedSet(new HashSet<>());
        this.pendingOperations = new LinkedList<>();
        this.lastAcknowledgedTimestamp = System.currentTimeMillis();
        this.isConnected = true;
        this.lastHeartbeat = System.currentTimeMillis();
        this.username = clientId;
        this.role = "CLIENT";
        this.authenticated = false;
    }

    public String getClientId() {
        return clientId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public Set<String> getSubscribedDocuments() {
        return subscribedDocuments;
    }

    public void subscribeToDocument(String documentId) {
        subscribedDocuments.add(documentId);
    }

    public void unsubscribeFromDocument(String documentId) {
        subscribedDocuments.remove(documentId);
    }

    public Queue<SyncMessage.OperationData> getPendingOperations() {
        return pendingOperations;
    }

    public void addPendingOperation(SyncMessage.OperationData op) {
        pendingOperations.offer(op);
    }

    public SyncMessage.OperationData pollPendingOperation() {
        return pendingOperations.poll();
    }

    public int getPendingOperationCount() {
        return pendingOperations.size();
    }

    public long getLastAcknowledgedTimestamp() {
        return lastAcknowledgedTimestamp;
    }

    public void setLastAcknowledgedTimestamp(long timestamp) {
        this.lastAcknowledgedTimestamp = timestamp;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public long getConnectionDuration() {
        return System.currentTimeMillis() - connectedAt;
    }

    public boolean isAlive(long heartbeatTimeout) {
        return System.currentTimeMillis() - lastHeartbeat < heartbeatTimeout;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isAdmin() { return "ADMIN".equals(role); }

    public boolean isAuthenticated() { return authenticated; }
    public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }

    /**
     * Checks if this client is within the rate limit.
     * Returns true if allowed, false if rate exceeded.
     */
    public boolean checkRateLimit(int maxPerSecond) {
        long now = System.currentTimeMillis();
        if (now - rateLimitWindowStart > 1000) {
            rateLimitWindowStart = now;
            rateLimitCount = 1;
            return true;
        }
        rateLimitCount++;
        return rateLimitCount <= maxPerSecond;
    }

    @Override
    public String toString() {
        return String.format("ClientSession(%s, user=%s, role=%s, docs=%d, pending=%d)",
                clientId, username, role, subscribedDocuments.size(), pendingOperations.size());
    }
}
