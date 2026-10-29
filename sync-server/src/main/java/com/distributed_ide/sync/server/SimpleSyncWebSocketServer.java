package com.distributed_ide.sync.server;

import com.distributed_ide.crdt.engine.SimpleThreadSafeCRDTEngine;
import com.distributed_ide.crdt.model.CRDTDocument;
import com.distributed_ide.crdt.model.CRDTIdentifier;
import com.distributed_ide.crdt.model.CRDTOperation;
import com.distributed_ide.sync.model.SyncMessage;
import com.distributed_ide.sync.model.ClientSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simplified WebSocket server for CRDT synchronization that works with existing API.
 */
public class SimpleSyncWebSocketServer extends org.java_websocket.server.WebSocketServer {
    
    private static final Logger LOG = LoggerFactory.getLogger(SimpleSyncWebSocketServer.class);
    
    // Thread-safe client management
    private final Map<org.java_websocket.WebSocket, ClientSession> connectedClients = new ConcurrentHashMap<>();
    private final Map<String, Set<org.java_websocket.WebSocket>> documentSubscribers = new ConcurrentHashMap<>();
    private final Map<String, ClientSession> clientSessions = new ConcurrentHashMap<>();
    
    // CRDT engine and components
    private final SimpleThreadSafeCRDTEngine engine;
    private final ObjectMapper objectMapper;
    
    // Background operations
    private final ScheduledExecutorService backgroundExecutor;
    
    // Monitoring
    private final AtomicLong messageCounter = new AtomicLong(0);
    private final AtomicLong connectionCounter = new AtomicLong(0);
    private final Map<String, Long> lastActivityTime = new ConcurrentHashMap<>();
    
    // Configuration
    private final long heartbeatIntervalMs = 30000;
    private final long heartbeatTimeoutMs = 60000;
    private final long clientListIntervalMs = 5000;
    private final String serverToken;
    private volatile String adminClientId;
    
    // Shutdown flag
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    public SimpleSyncWebSocketServer(int port, String nodeId) {
        this(port, nodeId, null);
    }
    
    public SimpleSyncWebSocketServer(int port, String nodeId, String serverToken) {
        super(new InetSocketAddress(port));
        this.engine = new SimpleThreadSafeCRDTEngine(nodeId);
        this.objectMapper = new ObjectMapper();
        this.serverToken = serverToken;
        
        this.backgroundExecutor = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "sync-server-background");
            t.setDaemon(true);
            return t;
        });
        
        startBackgroundTasks();
        
        LOG.info("SimpleSyncWebSocketServer initialized on port {} (token={})", port, serverToken != null ? "set" : "none");
    }
    
    @Override
    public void onOpen(org.java_websocket.WebSocket conn, org.java_websocket.handshake.ClientHandshake handshake) {
        if (isShuttingDown.get()) {
            conn.close(1001, "Server is shutting down");
            return;
        }
        
        String clientId = "client-" + UUID.randomUUID().toString().substring(0, 8);
        String nodeId = "node-" + UUID.randomUUID().toString().substring(0, 8);
        ClientSession session = new ClientSession(clientId, nodeId);
        
        connectedClients.put(conn, session);
        clientSessions.put(clientId, session);
        lastActivityTime.put(clientId, System.currentTimeMillis());
        
        connectionCounter.incrementAndGet();
        
        LOG.info("Client connected: {} from {}", clientId, conn.getRemoteSocketAddress());
        
        // Handle authentication
        if (serverToken == null || serverToken.isEmpty()) {
            session.setAuthenticated(true);
            if (adminClientId == null) {
                adminClientId = clientId;
                session.setRole("ADMIN");
            }
        }
        
        // Send initialization message
        SyncMessage initMsg = new SyncMessage(SyncMessage.MessageType.INIT, clientId, null, nodeId);
        initMsg.setRole(session.getRole());
        sendMessageAsync(conn, initMsg);
        
        // Start heartbeat for this client
        startClientHeartbeat(clientId, conn);
        
        // Broadcast client list
        broadcastClientListAsync();
    }
    
    @Override
    public void onClose(org.java_websocket.WebSocket conn, int code, String reason, boolean remote) {
        ClientSession session = connectedClients.remove(conn);
        if (session != null) {
            String clientId = session.getClientId();
            
            // Remove from client sessions
            clientSessions.remove(clientId);
            lastActivityTime.remove(clientId);
            
            // Remove from document subscriptions
            for (String docId : session.getSubscribedDocuments()) {
                documentSubscribers.computeIfPresent(docId, (k, v) -> {
                    v.remove(conn);
                    return v.isEmpty() ? null : v;
                });
            }
            
            LOG.info("Client disconnected: {} (code: {}, reason: {})", clientId, code, reason);
            
            // Broadcast updated client list
            broadcastClientListAsync();
        }
    }
    
    @Override
    public void onError(org.java_websocket.WebSocket conn, Exception ex) {
        String clientId = getClientId(conn);
        LOG.error("WebSocket error for client {}: {}", clientId, ex.getMessage(), ex);
        
        // Try to notify client
        if (conn != null && conn.isOpen()) {
            SyncMessage errorMsg = new SyncMessage(SyncMessage.MessageType.ERROR, clientId, null, null);
            errorMsg.setErrorMessage("Server error: " + ex.getMessage());
            sendMessageAsync(conn, errorMsg);
        }
    }
    
    @Override
    public void onStart() {
        LOG.info("SimpleSyncWebSocketServer started on: {}", getAddress());
    }
    
    @Override
    public void onMessage(org.java_websocket.WebSocket conn, String message) {
        if (isShuttingDown.get()) {
            return;
        }
        
        if (message == null || message.trim().isEmpty()) {
            LOG.warn("Received empty message from client: {}", getClientId(conn));
            return;
        }
        
        // Process message asynchronously
        backgroundExecutor.submit(() -> {
            try {
                processMessage(conn, message);
            } catch (Exception e) {
                LOG.error("Error processing message from client {}: {}", getClientId(conn), e.getMessage(), e);
                
                String clientId = getClientId(conn);
                SyncMessage errorMsg = new SyncMessage(SyncMessage.MessageType.ERROR, clientId, null, null);
                errorMsg.setErrorMessage("Message processing error: " + e.getMessage());
                sendMessageAsync(conn, errorMsg);
            }
        });
    }
    
    /**
     * Process a message with proper error handling.
     */
    private void processMessage(org.java_websocket.WebSocket conn, String message) {
        String clientId = getClientId(conn);
        if (clientId == null) {
            return;
        }
        
        // Update activity time
        lastActivityTime.put(clientId, System.currentTimeMillis());
        messageCounter.incrementAndGet();
        
        try {
            SyncMessage msg = objectMapper.readValue(message, SyncMessage.class);
            
            if (msg.getType() == null) {
                LOG.warn("Received message with null type from client: {}", clientId);
                sendErrorAsync(conn, clientId, "Message type is required");
                return;
            }
            
            LOG.debug("Processing message type: {} from client: {}", msg.getType(), clientId);
            
            switch (msg.getType()) {
                case DOCUMENT_JOIN:
                    handleDocumentJoin(conn, msg);
                    break;
                case DOCUMENT_LEAVE:
                    handleDocumentLeave(conn, msg);
                    break;
                case OPERATION:
                    handleOperation(conn, msg);
                    break;
                case HEARTBEAT:
                    handleHeartbeat(conn, msg);
                    break;
                case SYNC_REQUEST:
                    handleSyncRequest(conn, msg);
                    break;
                default:
                    LOG.warn("Unknown message type: {} from client: {}", msg.getType(), clientId);
                    sendErrorAsync(conn, clientId, "Unknown message type: " + msg.getType());
                    break;
            }
            
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            LOG.error("JSON parsing error from client {}: {}", clientId, e.getMessage());
            sendErrorAsync(conn, clientId, "Invalid JSON format: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Error processing message from client {}: {}", clientId, e.getMessage(), e);
            sendErrorAsync(conn, clientId, "Message processing error: " + e.getMessage());
        }
    }
    
    /**
     * Handle document join request.
     */
    private void handleDocumentJoin(org.java_websocket.WebSocket conn, SyncMessage msg) {
        String clientId = msg.getClientId();
        String documentId = msg.getDocumentId();
        
        if (documentId == null || documentId.trim().isEmpty()) {
            sendErrorAsync(conn, clientId, "Document ID is required for document join");
            return;
        }
        
        ClientSession session = clientSessions.get(clientId);
        if (session == null) {
            sendErrorAsync(conn, clientId, "Client session not found");
            return;
        }
        
        // Add to document subscribers
        documentSubscribers.computeIfAbsent(documentId, k -> ConcurrentHashMap.newKeySet()).add(conn);
        session.getSubscribedDocuments().add(documentId);
        
        // Send current document content
        CRDTDocument doc = engine.getDocument(documentId);
        if (doc != null) {
            SyncMessage response = new SyncMessage(SyncMessage.MessageType.SYNC_RESPONSE, clientId, documentId, null);
            
            // Convert operations to OperationData
            List<SyncMessage.OperationData> operations = new ArrayList<>();
            for (CRDTOperation op : engine.getPendingOperations()) {
                operations.add(new SyncMessage.OperationData(op));
            }
            response.setFullHistory(operations);
            
            sendMessageAsync(conn, response);
        }
        
        LOG.info("Client {} joined document: {}", clientId, documentId);
    }
    
    /**
     * Handle document leave request.
     */
    private void handleDocumentLeave(org.java_websocket.WebSocket conn, SyncMessage msg) {
        String clientId = msg.getClientId();
        String documentId = msg.getDocumentId();
        
        if (documentId == null || documentId.trim().isEmpty()) {
            sendErrorAsync(conn, clientId, "Document ID is required for document leave");
            return;
        }
        
        ClientSession session = clientSessions.get(clientId);
        if (session == null) {
            sendErrorAsync(conn, clientId, "Client session not found");
            return;
        }
        
        // Remove from document subscribers
        documentSubscribers.computeIfPresent(documentId, (k, v) -> {
            v.remove(conn);
            return v.isEmpty() ? null : v;
        });
        session.getSubscribedDocuments().remove(documentId);
        
        LOG.info("Client {} left document: {}", clientId, documentId);
    }
    
    /**
     * Handle CRDT operation.
     */
    private void handleOperation(org.java_websocket.WebSocket conn, SyncMessage msg) {
        String clientId = msg.getClientId();
        String documentId = msg.getDocumentId();
        
        if (documentId == null) {
            sendErrorAsync(conn, clientId, "Document ID is required for operation");
            return;
        }
        
        // Apply operations
        for (SyncMessage.OperationData opData : msg.getOperations()) {
            // Convert OperationData back to CRDTOperation
            CRDTIdentifier id = new CRDTIdentifier(
                Long.parseLong(opData.getIdTimestamp()),
                opData.getIdNodeId(),
                opData.getIdDisambiguator()
            );
            
            CRDTOperation operation;
            if ("INSERT".equals(opData.getOperationType())) {
                operation = CRDTOperation.insert(id, opData.getContent(), null, null, opData.getTimestamp());
            } else {
                operation = CRDTOperation.delete(id, opData.getTimestamp());
            }
            
            // Apply operation
            boolean success = engine.applyOperation(operation);
            if (success) {
                // Broadcast operation to all subscribers
                broadcastOperation(documentId, operation);
            }
        }
        
        // Send acknowledgment
        SyncMessage response = new SyncMessage(SyncMessage.MessageType.ACK, clientId, documentId, null);
        response.setAcknowledgedTimestamp(System.currentTimeMillis());
        sendMessageAsync(conn, response);
    }
    
    /**
     * Handle heartbeat message.
     */
    private void handleHeartbeat(org.java_websocket.WebSocket conn, SyncMessage msg) {
        String clientId = msg.getClientId();
        lastActivityTime.put(clientId, System.currentTimeMillis());
        
        // Send heartbeat response
        SyncMessage response = new SyncMessage(SyncMessage.MessageType.HEARTBEAT, clientId, null, null);
        sendMessageAsync(conn, response);
    }
    
    /**
     * Handle sync request.
     */
    private void handleSyncRequest(org.java_websocket.WebSocket conn, SyncMessage msg) {
        String clientId = msg.getClientId();
        String documentId = msg.getDocumentId();
        
        if (documentId == null || documentId.trim().isEmpty()) {
            sendErrorAsync(conn, clientId, "Document ID is required for sync request");
            return;
        }
        
        CRDTDocument doc = engine.getDocument(documentId);
        if (doc != null) {
            SyncMessage response = new SyncMessage(SyncMessage.MessageType.SYNC_RESPONSE, clientId, documentId, null);
            
            // Convert operations to OperationData
            List<SyncMessage.OperationData> operations = new ArrayList<>();
            for (CRDTOperation op : engine.getPendingOperations()) {
                operations.add(new SyncMessage.OperationData(op));
            }
            response.setFullHistory(operations);
            
            sendMessageAsync(conn, response);
        } else {
            sendErrorAsync(conn, clientId, "Document not found: " + documentId);
        }
    }
    
    /**
     * Get client ID from WebSocket connection.
     */
    private String getClientId(org.java_websocket.WebSocket conn) {
        ClientSession session = connectedClients.get(conn);
        return session != null ? session.getClientId() : null;
    }
    
    /**
     * Send message asynchronously.
     */
    private void sendMessageAsync(org.java_websocket.WebSocket conn, SyncMessage message) {
        if (!conn.isOpen()) {
            return;
        }
        
        backgroundExecutor.submit(() -> {
            try {
                String json = objectMapper.writeValueAsString(message);
                conn.send(json);
            } catch (Exception e) {
                LOG.error("Failed to send message to client {}: {}", getClientId(conn), e.getMessage(), e);
            }
        });
    }
    
    /**
     * Send error message asynchronously.
     */
    private void sendErrorAsync(org.java_websocket.WebSocket conn, String clientId, String error) {
        SyncMessage errorMsg = new SyncMessage(SyncMessage.MessageType.ERROR, clientId, null, null);
        errorMsg.setErrorMessage(error);
        sendMessageAsync(conn, errorMsg);
    }
    
    /**
     * Broadcast operation to all subscribers of a document.
     */
    private void broadcastOperation(String documentId, CRDTOperation operation) {
        Set<org.java_websocket.WebSocket> subscribers = documentSubscribers.get(documentId);
        if (subscribers != null) {
            for (org.java_websocket.WebSocket subscriber : subscribers) {
                String clientId = getClientId(subscriber);
                SyncMessage msg = new SyncMessage(SyncMessage.MessageType.OPERATION, clientId, documentId, null);
                msg.addOperation(new SyncMessage.OperationData(operation));
                sendMessageAsync(subscriber, msg);
            }
        }
    }
    
    /**
     * Broadcast client list to all connected clients.
     */
    private void broadcastClientListAsync() {
        List<SyncMessage.ClientInfo> clientList = new ArrayList<>();
        for (ClientSession session : clientSessions.values()) {
            clientList.add(new SyncMessage.ClientInfo(
                session.getClientId(),
                session.getClientId(), // Use clientId as username for now
                session.getRole(),
                System.currentTimeMillis()
            ));
        }
        
        for (org.java_websocket.WebSocket conn : connectedClients.keySet()) {
            String clientId = getClientId(conn);
            SyncMessage msg = new SyncMessage(SyncMessage.MessageType.CLIENT_LIST, clientId, null, null);
            msg.setClients(clientList);
            sendMessageAsync(conn, msg);
        }
    }
    
    /**
     * Start background tasks.
     */
    private void startBackgroundTasks() {
        // Client list broadcaster
        backgroundExecutor.scheduleAtFixedRate(this::broadcastClientListAsync, 
            clientListIntervalMs, clientListIntervalMs, TimeUnit.MILLISECONDS);
        
        // Connection cleanup
        backgroundExecutor.scheduleAtFixedRate(this::cleanupInactiveConnections, 
            60000, 60000, TimeUnit.MILLISECONDS);
        
        // Statistics logging
        backgroundExecutor.scheduleAtFixedRate(() -> {
            LOG.info("Sync server stats: connections={}, messages={}, documents={}", 
                connectedClients.size(), messageCounter.get(), engine.getDocumentCount());
        }, 60000, 60000, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Start heartbeat for a specific client.
     */
    private void startClientHeartbeat(String clientId, org.java_websocket.WebSocket conn) {
        backgroundExecutor.scheduleAtFixedRate(() -> {
            Long lastActivity = lastActivityTime.get(clientId);
            if (lastActivity != null && (System.currentTimeMillis() - lastActivity) > heartbeatTimeoutMs) {
                LOG.warn("Client {} heartbeat timeout, disconnecting", clientId);
                conn.close(1000, "Heartbeat timeout");
            } else {
                SyncMessage heartbeat = new SyncMessage(SyncMessage.MessageType.HEARTBEAT, clientId, null, null);
                sendMessageAsync(conn, heartbeat);
            }
        }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Cleanup inactive connections.
     */
    private void cleanupInactiveConnections() {
        long currentTime = System.currentTimeMillis();
        List<org.java_websocket.WebSocket> toRemove = new ArrayList<>();
        
        for (Map.Entry<org.java_websocket.WebSocket, ClientSession> entry : connectedClients.entrySet()) {
            org.java_websocket.WebSocket conn = entry.getKey();
            ClientSession session = entry.getValue();
            String clientId = session.getClientId();
            
            Long lastActivity = lastActivityTime.get(clientId);
            if (lastActivity != null && (currentTime - lastActivity) > heartbeatTimeoutMs) {
                toRemove.add(conn);
            }
        }
        
        for (org.java_websocket.WebSocket conn : toRemove) {
            conn.close(1000, "Inactive connection");
        }
    }
    
    /**
     * Get server statistics.
     */
    public ServerStats getStats() {
        return new ServerStats(
            connectedClients.size(),
            messageCounter.get(),
            connectionCounter.get(),
            engine.getStats()
        );
    }
    
    /**
     * Get CRDT engine.
     */
    public SimpleThreadSafeCRDTEngine getEngine() {
        return engine;
    }
    
    /**
     * Shutdown the server and clean up resources.
     */
    public void shutdown() {
        LOG.info("Shutting down SimpleSyncWebSocketServer");
        
        isShuttingDown.set(true);
        
        try {
            // Close all connections
            for (org.java_websocket.WebSocket conn : connectedClients.keySet()) {
                if (conn.isOpen()) {
                    conn.close(1001, "Server is shutting down");
                }
            }
            
            // Shutdown background executor
            backgroundExecutor.shutdown();
            
            if (!backgroundExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow();
            }
            
            // Shutdown CRDT engine
            engine.shutdown();
            
            // Clear all data structures
            connectedClients.clear();
            documentSubscribers.clear();
            clientSessions.clear();
            lastActivityTime.clear();
            
            // Stop WebSocket server
            super.stop();
            
            LOG.info("SimpleSyncWebSocketServer shutdown complete");
        } catch (Exception e) {
            LOG.error("Error during WebSocket server shutdown", e);
        }
    }
    
    /**
     * Server statistics for monitoring.
     */
    public static class ServerStats {
        public final int activeConnections;
        public final long messagesProcessed;
        public final long totalConnections;
        public final SimpleThreadSafeCRDTEngine.EngineStats engineStats;
        
        public ServerStats(int activeConnections, long messagesProcessed, long totalConnections,
                          SimpleThreadSafeCRDTEngine.EngineStats engineStats) {
            this.activeConnections = activeConnections;
            this.messagesProcessed = messagesProcessed;
            this.totalConnections = totalConnections;
            this.engineStats = engineStats;
        }
        
        @Override
        public String toString() {
            return String.format("ServerStats{connections=%d, messages=%d, total=%d, engine=%s}", 
                activeConnections, messagesProcessed, totalConnections, engineStats);
        }
    }
}
