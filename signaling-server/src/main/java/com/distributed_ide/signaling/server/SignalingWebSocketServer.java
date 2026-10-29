package com.distributed_ide.signaling.server;

import com.distributed_ide.signaling.model.Room;
import com.distributed_ide.signaling.model.SignalingMessage;
import com.distributed_ide.signaling.model.UserSession;
import com.distributed_ide.common.ObjectMapperFactory;
import com.distributed_ide.common.PerformanceMetrics;
import com.distributed_ide.common.SSLContextFactory;
import com.distributed_ide.common.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serveur WebSocket pour la signalisation WebRTC.
 * 
 * FIX: Added validation and rate limiting for better security and reliability:
 * - Input validation (roomId, userId, SDP size)
 * - Rate limiting on ICE/SDP messages per user
 * - Orphaned session cleanup
 */
public class SignalingWebSocketServer extends WebSocketServer {
    private static final Logger LOG = LoggerFactory.getLogger(SignalingWebSocketServer.class);
    
    // FIX: Added validation constants
    private static final int MAX_ROOM_ID_LENGTH = 256;
    private static final int MAX_USER_ID_LENGTH = 256;
    private static final int MAX_SDP_SIZE = 64 * 1024; // 64KB max SDP
    private static final int MAX_ICE_MESSAGES_PER_SECOND = 100;
    
    private final ObjectMapper objectMapper;
    private final RoomManager roomManager;
    private final Map<WebSocket, UserSession> sessionMap;
    private final SignalingCleanupManager cleanupManager;  // OPTIMIZATION: Cleanup orphaned sessions
    // FIX: Rate limiting per user for ICE/SDP messages
    private final Map<String, RateLimiter> userRateLimiters = new ConcurrentHashMap<>();
    
    public SignalingWebSocketServer(InetSocketAddress address, RoomManager roomManager) {
        super(address);
        
        // Initialize TLS 1.3 encryption context for secure WebSocket connections
        try {
            String keystorePath = System.getProperty("keystore.path", "./keystore.p12");
            String keystorePassword = System.getProperty("keystore.password", "distributed-ide-default");
            SSLContext sslContext = SSLContextFactory.createSSLContext(keystorePath, keystorePassword);
            this.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
            LOG.info("✅ TLS 1.3 initialized for SignalingWebSocketServer");
        } catch (Exception e) {
            LOG.warn("⚠️ TLS initialization failed: {}. Server will run unencrypted.", e.getMessage());
            // Continue without TLS rather than failing startup
        }
        
        this.roomManager = roomManager;
        this.objectMapper = ObjectMapperFactory.getMapper();  // OPTIMIZATION: Use pooled ObjectMapper
        this.sessionMap = new ConcurrentHashMap<>();
        this.cleanupManager = new SignalingCleanupManager(roomManager);
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        LOG.info("Client connected: {}", conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        LOG.info("Client disconnected: {}", conn.getRemoteSocketAddress());
        
        UserSession session = sessionMap.remove(conn);
        if (session != null) {
            Room room = roomManager.getUserRoom(session.getUserId());
            if (room != null) {
                // Notifier les autres utilisateurs
                notifyUserLeft(room, session.getUserId());
                // Retirer de la salle
                roomManager.removeUserFromRoom(room.getRoomId(), session.getUserId());
            }
        }
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            PerformanceMetrics.recordCounter("signaling_messages_received");
            
            SignalingMessage msg = objectMapper.readValue(message, SignalingMessage.class);
            PerformanceMetrics.recordCounter("signaling_message_type_" + msg.getType());
            
            handleMessage(conn, msg);
        } catch (Exception e) {
            PerformanceMetrics.recordCounter("signaling_messages_parsing_errors");
            LOG.error("Error parsing message: {}", e.getMessage(), e);
            sendError(conn, "Invalid message format");
        }
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        LOG.error("WebSocket error: {}", ex.getMessage(), ex);
    }
    
    @Override
    public void onStart() {
        LOG.info("Signaling server started on {}", getAddress());
        cleanupManager.start();  // OPTIMIZATION: Start cleanup of inactive sessions
    }
    
    private void handleMessage(WebSocket conn, SignalingMessage msg) {
        // Note: SignalingMessage does not include token field by design
        // WebRTC signaling is typically handled through separate authentication
        // If needed in future, add token: String field to SignalingMessage
        
        switch (msg.getType()) {
            case JOIN_ROOM:
                handleJoinRoom(conn, msg);
                break;
            case LEAVE_ROOM:
                handleLeaveRoom(conn, msg);
                break;
            case OFFER:
            case ANSWER:
            case SDP_OFFER:
            case SDP_ANSWER:
                handleSdpMessage(msg);
                break;
            case ICE_CANDIDATE:
                handleIceCandidate(msg);
                break;
            case PING:
                handlePing(conn);
                break;
            case CALL_REQUEST:
            case CALL_ACCEPT:
            case CALL_REJECT:
                handleCallMessage(conn, msg);
                break;
            default:
                LOG.warn("Unknown message type: {}", msg.getType());
        }
    }
    
    private void handleJoinRoom(WebSocket conn, SignalingMessage msg) {
        String userId = msg.getUserId();
        String roomId = msg.getRoomId();
        
        // FIX: Validate input format and length
        if (userId == null || userId.isEmpty() || userId.length() > MAX_USER_ID_LENGTH) {
            sendError(conn, "Invalid userId");
            return;
        }
        if (roomId == null || roomId.isEmpty() || roomId.length() > MAX_ROOM_ID_LENGTH) {
            sendError(conn, "Invalid roomId");
            return;
        }
        
        // FIX: Prevent path traversal and injection
        if (!isValidIdentifier(userId) || !isValidIdentifier(roomId)) {
            LOG.warn("Invalid identifier format: userId={}, roomId={}", userId, roomId);
            sendError(conn, "Invalid identifier format");
            return;
        }
        
        // Créer ou récupérer la salle
        Room room = roomManager.getOrCreateRoom(roomId, userId);
        
        // Vérifier si la salle est pleine
        if (room.isFull() && room.getParticipant(userId) == null) {
            SignalingMessage response = new SignalingMessage(SignalingMessage.MessageType.ROOM_FULL, null, roomId);
            conn.send(serializeMessage(response));
            return;
        }
        
        // Créer une session utilisateur
        UserSession session = new UserSession(userId, roomId, conn);
        
        // Ajouter à la salle
        if (roomManager.addUserToRoom(roomId, session)) {
            sessionMap.put(conn, session);
            
            // Envoyer la confirmation
            SignalingMessage confirmMsg = new SignalingMessage(SignalingMessage.MessageType.JOIN_ROOM, userId, roomId);
            Map<String, Object> meta = (Map<String, Object>) confirmMsg.getMetadata();
            if (meta != null) {
                meta.put("roomUsers", room.getOtherParticipantIds(userId));
            }
            conn.send(serializeMessage(confirmMsg));
            
            // Notifier les autres utilisateurs
            notifyUserJoined(room, userId);
            LOG.info("User {} joined room {}", userId, roomId);
        } else {
            sendError(conn, "Failed to join room");
        }
    }
    
    private void handleLeaveRoom(WebSocket conn, SignalingMessage msg) {
        UserSession session = sessionMap.remove(conn);
        if (session != null) {
            Room room = roomManager.getUserRoom(session.getUserId());
            if (room != null) {
                notifyUserLeft(room, session.getUserId());
                roomManager.removeUserFromRoom(room.getRoomId(), session.getUserId());
            }
        }
    }
    
    private void handleSdpMessage(SignalingMessage msg) {
        Room room = roomManager.getRoom(msg.getRoomId());
        if (room == null) {
            LOG.warn("Room not found for SDP: {}", msg.getRoomId());
            return;
        }
        
        String userId = msg.getUserId();
        String targetUserId = msg.getTargetUserId();
        if (targetUserId == null) {
            LOG.warn("No target user for SDP message");
            return;
        }
        
        // FIX: Add rate limiting for SDP messages to prevent flood
        RateLimiter limiter = userRateLimiters.computeIfAbsent(userId, k -> new RateLimiter(MAX_ICE_MESSAGES_PER_SECOND));
        if (!limiter.allowRequest()) {
            LOG.warn("SDP message rate limit exceeded for user: {}", userId);
            return;
        }
        
        // FIX: Validate SDP size
        String sdp = msg.getSdp();
        if (sdp != null && sdp.length() > MAX_SDP_SIZE) {
            LOG.warn("SDP message too large: {}b (max: {}b)", sdp.length(), MAX_SDP_SIZE);
            return;
        }
        
        UserSession targetSession = room.getParticipant(targetUserId);
        if (targetSession != null && targetSession.isConnected()) {
            targetSession.sendMessage(serializeMessage(msg));
            LOG.info("Forwarding {} from {} to {}", msg.getType(), userId, targetUserId);
        }
    }
    
    private void handleIceCandidate(SignalingMessage msg) {
        Room room = roomManager.getRoom(msg.getRoomId());
        if (room == null) {
            LOG.warn("Room not found for ICE candidate: {}", msg.getRoomId());
            return;
        }
        
        String userId = msg.getUserId();
        String targetUserId = msg.getTargetUserId();
        if (targetUserId == null) {
            LOG.warn("No target user for ICE message");
            return;
        }
        
        // FIX: Add rate limiting for ICE messages to prevent candidate flood
        RateLimiter limiter = userRateLimiters.computeIfAbsent(userId, k -> new RateLimiter(MAX_ICE_MESSAGES_PER_SECOND));
        if (!limiter.allowRequest()) {
            LOG.warn("ICE message rate limit exceeded for user: {}", userId);
            return;
        }
        
        // Note: ICE candidate strings are typically <500 bytes, so size validation less critical
        
        UserSession targetSession = room.getParticipant(targetUserId);
        if (targetSession != null && targetSession.isConnected()) {
            targetSession.sendMessage(serializeMessage(msg));
            LOG.debug("Forwarding ICE candidate from {} to {}", userId, targetUserId);
        }
    }
    
    private void handleCallMessage(WebSocket conn, SignalingMessage msg) {
        Room room = roomManager.getRoom(msg.getRoomId());
        if (room == null) {
            LOG.warn("Room not found for call message: {}", msg.getRoomId());
            return;
        }
        String targetUserId = msg.getTargetUserId();
        if (targetUserId == null) {
            // Broadcast to all other participants in room
            String serialized = serializeMessage(msg);
            for (UserSession session : room.getAllParticipants()) {
                if (!session.getUserId().equals(msg.getUserId()) && session.isConnected()) {
                    session.sendMessage(serialized);
                }
            }
            LOG.info("Broadcasting {} from {} to room {}", msg.getType(), msg.getUserId(), msg.getRoomId());
        } else {
            UserSession targetSession = room.getParticipant(targetUserId);
            if (targetSession != null && targetSession.isConnected()) {
                targetSession.sendMessage(serializeMessage(msg));
                LOG.info("Forwarding {} from {} to {}", msg.getType(), msg.getUserId(), targetUserId);
            }
        }
    }

    private void handlePing(WebSocket conn) {
        SignalingMessage pong = new SignalingMessage();
        pong.setType(SignalingMessage.MessageType.PONG);
        conn.send(serializeMessage(pong));
    }
    
    private void notifyUserJoined(Room room, String userId) {
        SignalingMessage notification = new SignalingMessage(
            SignalingMessage.MessageType.USER_JOINED,
            null,
            room.getRoomId()
        );
        notification.setMetadata(Map.of("userId", userId));
        
        String message = serializeMessage(notification);
        for (UserSession session : room.getAllParticipants()) {
            if (!session.getUserId().equals(userId) && session.isConnected()) {
                session.sendMessage(message);
            }
        }
    }
    
    private void notifyUserLeft(Room room, String userId) {
        SignalingMessage notification = new SignalingMessage(
            SignalingMessage.MessageType.USER_LEFT,
            null,
            room.getRoomId()
        );
        notification.setMetadata(Map.of("userId", userId));
        
        String message = serializeMessage(notification);
        for (UserSession session : room.getAllParticipants()) {
            if (!session.getUserId().equals(userId) && session.isConnected()) {
                session.sendMessage(message);
            }
        }
    }
    
    private void sendError(WebSocket conn, String errorMsg) {
        SignalingMessage error = new SignalingMessage(SignalingMessage.MessageType.ERROR, null, null);
        error.setError(errorMsg);
        conn.send(serializeMessage(error));
    }
    
    private String serializeMessage(SignalingMessage msg) {
        try {
            return objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            LOG.error("Error serializing message: {}", e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * FIX: Validates identifier format to prevent injection/path traversal attacks.
     * Only allows alphanumeric characters, underscore, and hyphen.
     * 
     * @param id the identifier to validate
     * @return true if valid format, false otherwise
     */
    private boolean isValidIdentifier(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        // Pattern: alphanumeric (a-z, A-Z, 0-9), underscore, and hyphen only
        // Prevents: special characters, path traversal (..), injection attacks
        return id.matches("^[a-zA-Z0-9_-]+$");
    }
    
    /**
     * FIX: Simple rate limiter using sliding window counter.
     * Allows burst up to MAX_ICE_MESSAGES_PER_SECOND per client.
     * Resets counter every second to allow next burst.
     */
    private static class RateLimiter {
        private final int maxRequestsPerSecond;
        private long windowStart = System.currentTimeMillis();
        private int requestCount = 0;
        
        public RateLimiter(int maxRequestsPerSecond) {
            this.maxRequestsPerSecond = maxRequestsPerSecond;
        }
        
        /**
         * Check if request is allowed under rate limit.
         * 
         * @return true if request allowed, false if rate limit exceeded
         */
        public synchronized boolean allowRequest() {
            long now = System.currentTimeMillis();
            long timeSinceWindowStart = now - windowStart;
            
            // Reset window if 1 second has passed
            if (timeSinceWindowStart > 1000) {
                windowStart = now;
                requestCount = 0;
            }
            
            // Check if under limit
            if (requestCount < maxRequestsPerSecond) {
                requestCount++;
                return true;
            }
            return false;
        }
    }
}
