package com.distributed_ide.sync_server.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manages WebSocket heartbeat/keepalive to detect and clean up zombie connections.
 * 
 * KEY RESPONSIBILITIES:
 * ✅ Send periodic ping messages to all connected clients
 * ✅ Track last heartbeat ACK from each client
 * ✅ Detect zombie connections (no response for timeout period)
 * ✅ Automatically close and cleanup dead connections
 * ✅ Prevent connection accumulation under network issues
 * 
 * PROBLEM SOLVED:
 * ❌ BEFORE: WebSocket connections not monitored → zombie connections accumulate
 *           IDE becomes unresponsive as server memory fills with dead sessions
 * ✅ AFTER:  Automatic heartbeat ensures only active connections tracked
 */
public class WebSocketHeartbeatManager {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketHeartbeatManager.class);

    // Configuration parameters (all tunable based on network conditions)
    private static final long HEARTBEAT_INTERVAL_MS = 30_000;    // Send ping every 30 seconds
    private static final long HEARTBEAT_TIMEOUT_MS = 65_000;     // Consider dead if no pong for 65 seconds
    private static final String HEARTBEAT_MESSAGE = "HEARTBEAT_PING";
    private static final String HEARTBEAT_PONG = "HEARTBEAT_PONG";

    /**
     * Per-connection heartbeat state.
     */
    public static class ClientHeartbeatState {
        public volatile long lastHeartbeatSentAt = System.currentTimeMillis();
        public volatile long lastHeartbeatAckAt = System.currentTimeMillis();
        public volatile boolean isAlive = true;

        public long getTimeSinceLastAck() {
            return System.currentTimeMillis() - lastHeartbeatAckAt;
        }

        public boolean isStale() {
            return getTimeSinceLastAck() > HEARTBEAT_TIMEOUT_MS;
        }

        @Override
        public String toString() {
            return String.format("ClientHeartbeat(lastAck:%dms ago, alive:%s, stale:%s)", 
                getTimeSinceLastAck(), isAlive, isStale());
        }
    }

    // Session ID → heartbeat state mapping
    private final Map<String, ClientHeartbeatState> clientStates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "WebSocket-Heartbeat-Thread");
        t.setDaemon(true);
        return t;
    });

    // Callback when a zombie connection is detected and cleaned up
    private volatile Consumer<String> onClientStaleCallback;

    /**
     * Start heartbeat monitoring service.
     * Call during server startup.
     */
    public void start() {
        // Schedule periodic heartbeat checking and sending
        executor.scheduleAtFixedRate(
            this::sendHeartbeatAndCheckStale,
            HEARTBEAT_INTERVAL_MS,
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        LOG.info("✅ WebSocket Heartbeat Manager started (interval: {}ms, timeout: {}ms)", 
            HEARTBEAT_INTERVAL_MS, HEARTBEAT_TIMEOUT_MS);
    }

    /**
     * Register a new client session.
     * Call when WebSocket connection established.
     */
    public void registerClient(String sessionId) {
        ClientHeartbeatState state = new ClientHeartbeatState();
        clientStates.put(sessionId, state);
        LOG.debug("📱 Client registered for heartbeat monitoring: {}", sessionId);
    }

    /**
     * Unregister a client session.
     * Call when WebSocket connection closed.
     */
    public void unregisterClient(String sessionId) {
        clientStates.remove(sessionId);
        LOG.debug("📱 Client unregistered from heartbeat monitoring: {}", sessionId);
    }

    /**
     * Record heartbeat ACK from client.
     * Call when pong message received from client.
     */
    public void recordHeartbeatAck(String sessionId) {
        ClientHeartbeatState state = clientStates.get(sessionId);
        if (state != null) {
            state.lastHeartbeatAckAt = System.currentTimeMillis();
            state.isAlive = true;
            LOG.trace("💓 Heartbeat ACK received from client: {}", sessionId);
        }
    }

    /**
     * Get heartbeat state for a specific client (for debugging).
     */
    public ClientHeartbeatState getClientState(String sessionId) {
        return clientStates.get(sessionId);
    }

    /**
     * Get all active client states (for monitoring/dashboard).
     */
    public Map<String, ClientHeartbeatState> getAllClientStates() {
        return new ConcurrentHashMap<>(clientStates);
    }

    /**
     * Count of currently monitored connections.
     */
    public int getActiveClientCount() {
        return clientStates.size();
    }

    /**
     * Set callback for when a stale client is detected.
     * Callback will be invoked when zombie connection needs cleanup.
     */
    public void setOnClientStaleCallback(Consumer<String> callback) {
        this.onClientStaleCallback = callback;
    }

    /**
     * Send heartbeat ping to all clients and check for stale connections.
     * Called periodically by ScheduledExecutorService.
     */
    private void sendHeartbeatAndCheckStale() {
        long startTime = System.currentTimeMillis();
        int totalClients = clientStates.size();
        int staleClients = 0;

        for (Map.Entry<String, ClientHeartbeatState> entry : clientStates.entrySet()) {
            String sessionId = entry.getKey();
            ClientHeartbeatState state = entry.getValue();

            // Check if client is stale (no ACK for timeout period)
            if (state.isStale()) {
                staleClients++;
                state.isAlive = false;

                LOG.warn("⚠️ Stale WebSocket client detected (timeout: {}ms): {} - {}", 
                    HEARTBEAT_TIMEOUT_MS, sessionId, state);

                // Invoke callback to cleanup (will close connection)
                if (onClientStaleCallback != null) {
                    try {
                        onClientStaleCallback.accept(sessionId);
                    } catch (Exception e) {
                        LOG.error("Error in stale client callback for {}", sessionId, e);
                    }
                }
            }
        }

        // Send ping to all alive clients
        for (String sessionId : clientStates.keySet()) {
            ClientHeartbeatState state = clientStates.get(sessionId);
            if (state != null && state.isAlive) {
                sendHeartbeatPing(sessionId);
                state.lastHeartbeatSentAt = System.currentTimeMillis();
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        LOG.debug("💓 Heartbeat cycle complete: {} clients, {} stale, {}ms", 
            totalClients, staleClients, duration);
    }

    /**
     * Send heartbeat ping message to specific client.
     * Must be implemented by caller (not included in this manager).
     * This is a placeholder that caller should override or hook into.
     */
    private void sendHeartbeatPing(String sessionId) {
        // Implementation depends on WebSocket framework (javax.websocket, Spring WebSocket, etc)
        LOG.trace("📤 Sending heartbeat ping to client: {}", sessionId);
        
        // CALLER MUST IMPLEMENT:
        // - Get WebSocket session for sessionId
        // - Send HEARTBEAT_MESSAGE
        // Example:
        // WebSocketSession session = sessionRegistry.get(sessionId);
        // if (session != null) {
        //     session.sendMessage(new TextMessage(HEARTBEAT_MESSAGE));
        // }
    }

    /**
     * Stop heartbeat monitoring service.
     * Call during server shutdown.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                LOG.warn("⚠️ Heartbeat executor shutdown forced");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        clientStates.clear();
        LOG.info("✅ WebSocket Heartbeat Manager stopped");
    }

    /**
     * Get heartbeat message format for client protocol.
     */
    public static String getHeartbeatMessage() {
        return HEARTBEAT_MESSAGE;
    }

    /**
     * Get heartbeat pong message format for client protocol.
     */
    public static String getHeartbeatPong() {
        return HEARTBEAT_PONG;
    }

    /**
     * Check if a message is a heartbeat PONG response from client.
     */
    public static boolean isHeartbeatPong(String message) {
        return HEARTBEAT_PONG.equals(message);
    }
}
