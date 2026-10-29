package com.distributed_ide.terminal.server;

import com.distributed_ide.terminal.model.TerminalMessage;
import com.distributed_ide.terminal.model.TerminalSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe WebSocket server for terminal service with proper resource management.
 * 
 * Key improvements:
 * - Non-blocking message processing
 * - Proper connection management
 * - Exception isolation
 * - Resource cleanup on shutdown
 * - Connection monitoring
 */
public class ThreadSafeTerminalWebSocketServer extends WebSocketServer {
    
    private static final Logger LOG = LoggerFactory.getLogger(ThreadSafeTerminalWebSocketServer.class);
    
    // Thread-safe connection management
    private final Map<WebSocket, String> sessionMap = new ConcurrentHashMap<>();
    private final Map<String, WebSocket> userConnections = new ConcurrentHashMap<>();
    
    // Terminal session manager
    private final ThreadSafeTerminalSessionManager terminalManager;
    
    // JSON serialization
    private final ObjectMapper objectMapper;
    
    // Message processing
    private final ExecutorService messageExecutor;
    private final AtomicLong messageCounter = new AtomicLong(0);
    
    // Shutdown flag
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    // Connection monitoring
    private final ScheduledExecutorService monitoringExecutor;
    private final Map<WebSocket, Long> connectionTimestamps = new ConcurrentHashMap<>();
    
    public ThreadSafeTerminalWebSocketServer(InetSocketAddress address, 
                                            ThreadSafeTerminalSessionManager terminalManager) {
        super(address);
        this.terminalManager = terminalManager;
        this.objectMapper = new ObjectMapper();
        this.messageExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "terminal-message-processor");
            t.setDaemon(true);
            return t;
        });
        this.monitoringExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "terminal-connection-monitor");
            t.setDaemon(true);
            return t;
        });
        
        startMonitoring();
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        if (isShuttingDown.get()) {
            conn.close(1001, "Server is shutting down");
            return;
        }
        
        connectionTimestamps.put(conn, System.currentTimeMillis());
        LOG.info("New WebSocket connection from: {}", conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        if (isShuttingDown.get()) {
            return;
        }
        
        if (message == null || message.trim().isEmpty()) {
            LOG.warn("Received empty message from {}", conn.getRemoteSocketAddress());
            sendErrorAsync(conn, null, "Empty message received");
            return;
        }
        
        // Process message asynchronously
        messageExecutor.submit(() -> {
            try {
                processMessage(conn, message);
            } catch (Exception e) {
                LOG.error("Error processing message from {}: {}", conn.getRemoteSocketAddress(), e.getMessage(), e);
                sendErrorAsync(conn, null, "Message processing error: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String userId = sessionMap.remove(conn);
        if (userId != null) {
            userConnections.remove(userId);
        }
        connectionTimestamps.remove(conn);
        
        LOG.info("WebSocket connection closed: {}, reason: {}, remote: {}", 
            conn.getRemoteSocketAddress(), reason, remote);
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        String remoteAddr = conn != null ? conn.getRemoteSocketAddress().toString() : "unknown";
        LOG.error("WebSocket error from {}: {}", remoteAddr, ex.getMessage(), ex);
        
        // Try to notify client of error
        if (conn != null && conn.isOpen()) {
            sendErrorAsync(conn, null, "Server error: " + ex.getMessage());
        }
    }
    
    @Override
    public void onStart() {
        LOG.info("Terminal WebSocket server started on: {}", getAddress());
    }
    
    /**
     * Process a message with proper error handling.
     */
    private void processMessage(@Nonnull WebSocket conn, @Nonnull String message) {
        try {
            TerminalMessage msg = objectMapper.readValue(message, TerminalMessage.class);
            
            if (msg.getType() == null) {
                LOG.warn("Received message with null type from {}", conn.getRemoteSocketAddress());
                sendErrorAsync(conn, msg.getSessionId(), "Message type is required");
                return;
            }
            
            long messageId = messageCounter.incrementAndGet();
            LOG.debug("Processing message #{} type: {} for session: {} from {}", 
                messageId, msg.getType(), msg.getSessionId(), conn.getRemoteSocketAddress());
            
            // Update connection activity
            connectionTimestamps.put(conn, System.currentTimeMillis());
            
            // Handle message based on type
            handleMessage(conn, msg, messageId);
            
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            LOG.error("JSON parsing error from {}: {}", conn.getRemoteSocketAddress(), e.getMessage());
            sendErrorAsync(conn, null, "Invalid JSON format: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Error processing message from {}: {}", conn.getRemoteSocketAddress(), e.getMessage(), e);
            sendErrorAsync(conn, null, "Message processing error: " + e.getMessage());
        }
    }
    
    /**
     * Handle specific message types.
     */
    private void handleMessage(@Nonnull WebSocket conn, @Nonnull TerminalMessage msg, long messageId) {
        switch (msg.getType()) {
            case CREATE_SESSION:
                handleCreateSession(conn, msg, messageId);
                break;
            case EXECUTE_COMMAND:
                handleExecuteCommand(conn, msg, messageId);
                break;
            case GET_SESSION:
                handleGetSession(conn, msg, messageId);
                break;
            case REMOVE_SESSION:
                handleRemoveSession(conn, msg, messageId);
                break;
            case GET_USER_SESSIONS:
                handleGetUserSessions(conn, msg, messageId);
                break;
            default:
                LOG.warn("Unknown message type: {} from {}", msg.getType(), conn.getRemoteSocketAddress());
                sendErrorAsync(conn, msg.getSessionId(), "Unknown message type: " + msg.getType());
                break;
        }
    }
    
    /**
     * Handle session creation request.
     */
    private void handleCreateSession(@Nonnull WebSocket conn, @Nonnull TerminalMessage msg, long messageId) {
        String userId = msg.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            sendErrorAsync(conn, msg.getSessionId(), "User ID is required for session creation");
            return;
        }

        String tempSessionId = msg.getSessionId();
        final String sessionId = (tempSessionId == null || tempSessionId.trim().isEmpty())
            ? "session_" + System.currentTimeMillis() + "_" + messageId
            : tempSessionId;

        TerminalSession.ExecutionType tempExecType = msg.getExecutionType();
        final TerminalSession.ExecutionType execType = (tempExecType == null)
            ? TerminalSession.ExecutionType.SHELL
            : tempExecType;

        // Create session asynchronously
        terminalManager.createSession(sessionId, userId, execType,
            msg.getCpuLimit(), msg.getMemoryLimit(), msg.getTimeoutSeconds())
            .thenAccept(session -> {
                // Associate connection with user
                sessionMap.put(conn, userId);
                userConnections.put(userId, conn);

                // Send success response
                TerminalMessage response = new TerminalMessage();
                response.setType(TerminalMessage.MessageType.SESSION_CREATED);
                response.setSessionId(sessionId);
                response.setSuccess(true);
                response.setData("Session created successfully");

                sendMessageAsync(conn, response);

                LOG.info("Session created: {} for user: {}", sessionId, userId);
            })
            .exceptionally(throwable -> {
                LOG.error("Failed to create session: {} for user: {}", sessionId, userId, throwable);
                sendErrorAsync(conn, sessionId, "Session creation failed: " + throwable.getMessage());
                return null;
            });
    }
    
    /**
     * Handle command execution request.
     */
    private void handleExecuteCommand(@Nonnull WebSocket conn, @Nonnull TerminalMessage msg, long messageId) {
        String sessionId = msg.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sendErrorAsync(conn, null, "Session ID is required for command execution");
            return;
        }
        
        String command = msg.getCommand();
        if (command == null || command.trim().isEmpty()) {
            sendErrorAsync(conn, sessionId, "Command is required for execution");
            return;
        }
        
        // Execute command asynchronously
        executeCommandAsync(sessionId, command)
            .thenAccept(output -> {
                TerminalMessage response = new TerminalMessage();
                response.setType(TerminalMessage.MessageType.COMMAND_OUTPUT);
                response.setSessionId(sessionId);
                response.setSuccess(true);
                response.setData(output);
                
                sendMessageAsync(conn, response);
                
                // Update session activity
                terminalManager.updateSessionActivity(sessionId);
            })
            .exceptionally(throwable -> {
                LOG.error("Failed to execute command in session: {}", sessionId, throwable);
                sendErrorAsync(conn, sessionId, "Command execution failed: " + throwable.getMessage());
                return null;
            });
    }
    
    /**
     * Handle get session request.
     */
    private void handleGetSession(@Nonnull WebSocket conn, @Nonnull TerminalMessage msg, long messageId) {
        String sessionId = msg.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sendErrorAsync(conn, null, "Session ID is required");
            return;
        }
        
        TerminalSession session = terminalManager.getSession(sessionId);
        if (session != null) {
            TerminalMessage response = new TerminalMessage();
            response.setType(TerminalMessage.MessageType.SESSION_INFO);
            response.setSessionId(sessionId);
            response.setSuccess(true);
            response.setData(session);
            
            sendMessageAsync(conn, response);
        } else {
            sendErrorAsync(conn, sessionId, "Session not found: " + sessionId);
        }
    }
    
    /**
     * Handle remove session request.
     */
    private void handleRemoveSession(@Nonnull WebSocket conn, @Nonnull TerminalMessage msg, long messageId) {
        String sessionId = msg.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sendErrorAsync(conn, null, "Session ID is required");
            return;
        }
        
        terminalManager.removeSession(sessionId)
            .thenAccept(success -> {
                TerminalMessage response = new TerminalMessage();
                response.setType(TerminalMessage.MessageType.SESSION_REMOVED);
                response.setSessionId(sessionId);
                response.setSuccess(success);
                response.setData(success ? "Session removed successfully" : "Failed to remove session");
                
                sendMessageAsync(conn, response);
            })
            .exceptionally(throwable -> {
                LOG.error("Failed to remove session: {}", sessionId, throwable);
                sendErrorAsync(conn, sessionId, "Session removal failed: " + throwable.getMessage());
                return null;
            });
    }
    
    /**
     * Handle get user sessions request.
     */
    private void handleGetUserSessions(@Nonnull WebSocket conn, @Nonnull TerminalMessage msg, long messageId) {
        String userId = msg.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            sendErrorAsync(conn, null, "User ID is required");
            return;
        }
        
        java.util.Collection<TerminalSession> sessions = terminalManager.getUserSessions(userId);
        
        TerminalMessage response = new TerminalMessage();
        response.setType(TerminalMessage.MessageType.USER_SESSIONS);
        response.setSuccess(true);
        response.setData(sessions);
        
        sendMessageAsync(conn, response);
    }
    
    /**
     * Execute command asynchronously.
     */
    @Nonnull
    private CompletableFuture<String> executeCommandAsync(@Nonnull String sessionId, @Nonnull String command) {
        return CompletableFuture.supplyAsync(() -> {
            // In a real implementation, this would execute the command in the Docker container
            // For now, return a mock response
            return "Command executed: " + command + "\n";
        });
    }
    
    /**
     * Send message asynchronously.
     */
    private void sendMessageAsync(@Nonnull WebSocket conn, @Nonnull TerminalMessage message) {
        if (!conn.isOpen()) {
            return;
        }
        
        messageExecutor.submit(() -> {
            try {
                String json = objectMapper.writeValueAsString(message);
                conn.send(json);
            } catch (Exception e) {
                LOG.error("Failed to send message to {}: {}", conn.getRemoteSocketAddress(), e.getMessage(), e);
            }
        });
    }
    
    /**
     * Send error message asynchronously.
     */
    private void sendErrorAsync(@Nonnull WebSocket conn, @Nullable String sessionId, @Nonnull String error) {
        TerminalMessage errorMsg = new TerminalMessage();
        errorMsg.setType(TerminalMessage.MessageType.ERROR);
        errorMsg.setSessionId(sessionId);
        errorMsg.setSuccess(false);
        errorMsg.setData(error);
        
        sendMessageAsync(conn, errorMsg);
    }
    
    /**
     * Get server statistics.
     */
    @Nonnull
    public ServerStats getStats() {
        int activeConnections = sessionMap.size();
        int connectedUsers = userConnections.size();
        long messagesProcessed = messageCounter.get();
        
        ThreadSafeTerminalSessionManager.SessionStats sessionStats = terminalManager.getStats();
        
        return new ServerStats(activeConnections, connectedUsers, messagesProcessed, sessionStats);
    }
    
    /**
     * Start connection monitoring.
     */
    private void startMonitoring() {
        // Monitor for inactive connections every 30 seconds
        monitoringExecutor.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            long inactiveThreshold = 300000; // 5 minutes
            
            sessionMap.keySet().removeIf(conn -> {
                Long timestamp = connectionTimestamps.get(conn);
                if (timestamp != null && (currentTime - timestamp) > inactiveThreshold) {
                    if (conn.isOpen()) {
                        conn.close(1000, "Connection inactive");
                    }
                    return true;
                }
                return false;
            });
        }, 30, 30, TimeUnit.SECONDS);
        
        // Log statistics every 2 minutes
        monitoringExecutor.scheduleAtFixedRate(() -> {
            ServerStats stats = getStats();
            LOG.info("Server stats: {}", stats);
        }, 120, 120, TimeUnit.SECONDS);
    }
    
    /**
     * Shutdown the server and clean up resources.
     */
    public void shutdown() {
        LOG.info("Shutting down terminal WebSocket server");
        
        isShuttingDown.set(true);
        
        try {
            // Close all connections
            for (WebSocket conn : sessionMap.keySet()) {
                if (conn.isOpen()) {
                    conn.close(1001, "Server is shutting down");
                }
            }
            
            // Shutdown executors
            messageExecutor.shutdown();
            monitoringExecutor.shutdown();
            
            // Wait for executors to terminate
            if (!messageExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                messageExecutor.shutdownNow();
            }
            
            if (!monitoringExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitoringExecutor.shutdownNow();
            }
            
            // Stop WebSocket server
            super.stop();
            
            // Clear all maps
            sessionMap.clear();
            userConnections.clear();
            connectionTimestamps.clear();
            
            LOG.info("Terminal WebSocket server shutdown complete");
        } catch (Exception e) {
            LOG.error("Error during WebSocket server shutdown", e);
        }
    }
    
    /**
     * Server statistics for monitoring.
     */
    public static class ServerStats {
        public final int activeConnections;
        public final int connectedUsers;
        public final long messagesProcessed;
        public final ThreadSafeTerminalSessionManager.SessionStats sessionStats;
        
        public ServerStats(int activeConnections, int connectedUsers, long messagesProcessed,
                          ThreadSafeTerminalSessionManager.SessionStats sessionStats) {
            this.activeConnections = activeConnections;
            this.connectedUsers = connectedUsers;
            this.messagesProcessed = messagesProcessed;
            this.sessionStats = sessionStats;
        }
        
        @Override
        public String toString() {
            return String.format("ServerStats{connections=%d, users=%d, messages=%d, sessions=%s}", 
                activeConnections, connectedUsers, messagesProcessed, sessionStats);
        }
    }
}
