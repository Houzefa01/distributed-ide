package com.distributed_ide.javafx_client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Client WebSocket pour la signalisation WebRTC.
 */
public class SignalingClientService {
    private static final Logger LOG = LoggerFactory.getLogger(SignalingClientService.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketClient client;
    private Listener listener;
    private volatile String userId;
    private volatile String currentRoomId;
    
    public interface Listener {
        void onConnected();
        void onRoomJoined(String roomId);
        void onUserJoined(String userId);
        void onUserLeft(String userId);
        void onCallRequest(String fromUserId, Map<String, Object> metadata);
        void onCallAccept(String fromUserId);
        void onCallReject(String fromUserId);
        void onSdpOffer(String fromUserId, String sdp);
        void onSdpAnswer(String fromUserId, String sdp);
        void onIceCandidate(String fromUserId, String candidate, Integer sdpMLineIndex, String sdpMid);
        void onError(String error);
    }
    
    public void setListener(Listener listener) {
        this.listener = listener;
    }
    
    public void connect(String wsUri, String userId) {
        this.userId = userId;
        try {
            client = new WebSocketClient(new URI(wsUri)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    LOG.info("Signaling connected");
                    if (listener != null) {
                        listener.onConnected();
                    }
                }
                
                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    LOG.info("Signaling disconnected: {} - {}", code, reason);
                }
                
                @Override
                public void onError(Exception ex) {
                    LOG.error("Signaling error: {}", ex.getMessage(), ex);
                    if (listener != null) {
                        listener.onError(ex.getMessage());
                    }
                }
            };
            client.connect();
        } catch (Exception e) {
            LOG.error("Failed to connect to signaling server: {}", e.getMessage(), e);
            if (listener != null) {
                listener.onError(e.getMessage());
            }
        }
    }
    
    public void disconnect() {
        if (client != null && client.isOpen()) {
            client.close();
        }
    }
    
    public void joinRoom(String roomId) {
        this.currentRoomId = roomId;
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "JOIN_ROOM");
        msg.put("userId", userId);
        msg.put("roomId", roomId);
        sendMessage(msg);
    }
    
    public void leaveRoom() {
        if (currentRoomId != null) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "LEAVE_ROOM");
            msg.put("userId", userId);
            msg.put("roomId", currentRoomId);
            sendMessage(msg);
            currentRoomId = null;
        }
    }
    
    public void sendCallRequest(String targetUserId, Map<String, Object> metadata) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "CALL_REQUEST");
        msg.put("userId", userId);
        msg.put("roomId", currentRoomId);
        msg.put("targetUserId", targetUserId);
        if (metadata != null) {
            msg.put("metadata", metadata);
        }
        sendMessage(msg);
    }
    
    public void sendCallAccept(String targetUserId) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "CALL_ACCEPT");
        msg.put("userId", userId);
        msg.put("roomId", currentRoomId);
        msg.put("targetUserId", targetUserId);
        sendMessage(msg);
    }
    
    public void sendCallReject(String targetUserId) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "CALL_REJECT");
        msg.put("userId", userId);
        msg.put("roomId", currentRoomId);
        msg.put("targetUserId", targetUserId);
        sendMessage(msg);
    }
    
    public void sendSdpOffer(String targetUserId, String sdp) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "SDP_OFFER");
        msg.put("userId", userId);
        msg.put("roomId", currentRoomId);
        msg.put("targetUserId", targetUserId);
        msg.put("sdp", sdp);
        sendMessage(msg);
    }
    
    public void sendSdpAnswer(String targetUserId, String sdp) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "SDP_ANSWER");
        msg.put("userId", userId);
        msg.put("roomId", currentRoomId);
        msg.put("targetUserId", targetUserId);
        msg.put("sdp", sdp);
        sendMessage(msg);
    }
    
    public void sendIceCandidate(String targetUserId, String candidate, Integer sdpMLineIndex, String sdpMid) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "ICE_CANDIDATE");
        msg.put("userId", userId);
        msg.put("roomId", currentRoomId);
        msg.put("targetUserId", targetUserId);
        
        Map<String, Object> candidateData = new HashMap<>();
        candidateData.put("candidate", candidate);
        candidateData.put("sdpMLineIndex", sdpMLineIndex);
        candidateData.put("sdpMid", sdpMid);
        msg.put("candidate", candidateData);
        
        sendMessage(msg);
    }
    
    private void sendMessage(Map<String, Object> msg) {
        if (client != null && client.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(msg);
                client.send(json);
            } catch (Exception e) {
                LOG.error("Failed to send message: {}", e.getMessage(), e);
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void handleMessage(String message) {
        try {
            Map<String, Object> msg = objectMapper.readValue(message, Map.class);
            String type = (String) msg.get("type");
            
            if (type == null) return;
            
            switch (type) {
                case "JOIN_ROOM":
                    if (listener != null) {
                        listener.onRoomJoined((String) msg.get("roomId"));
                    }
                    break;
                    
                case "USER_JOINED":
                    if (listener != null) {
                        Map<String, Object> metadata = (Map<String, Object>) msg.get("metadata");
                        if (metadata != null) {
                            String joinedUserId = (String) metadata.get("userId");
                            listener.onUserJoined(joinedUserId);
                        }
                    }
                    break;
                    
                case "USER_LEFT":
                    if (listener != null) {
                        Map<String, Object> metadata = (Map<String, Object>) msg.get("metadata");
                        if (metadata != null) {
                            String leftUserId = (String) metadata.get("userId");
                            listener.onUserLeft(leftUserId);
                        }
                    }
                    break;
                    
                case "CALL_REQUEST":
                    if (listener != null) {
                        String fromUserId = (String) msg.get("userId");
                        Map<String, Object> metadata = (Map<String, Object>) msg.get("metadata");
                        listener.onCallRequest(fromUserId, metadata);
                    }
                    break;
                    
                case "CALL_ACCEPT":
                    if (listener != null) {
                        listener.onCallAccept((String) msg.get("userId"));
                    }
                    break;
                    
                case "CALL_REJECT":
                    if (listener != null) {
                        listener.onCallReject((String) msg.get("userId"));
                    }
                    break;
                    
                case "SDP_OFFER":
                case "OFFER":
                    if (listener != null) {
                        String fromUserId = (String) msg.get("userId");
                        String sdp = (String) msg.get("sdp");
                        listener.onSdpOffer(fromUserId, sdp);
                    }
                    break;
                    
                case "SDP_ANSWER":
                case "ANSWER":
                    if (listener != null) {
                        String fromUserId = (String) msg.get("userId");
                        String sdp = (String) msg.get("sdp");
                        listener.onSdpAnswer(fromUserId, sdp);
                    }
                    break;
                    
                case "ICE_CANDIDATE":
                    if (listener != null) {
                        String fromUserId = (String) msg.get("userId");
                        Map<String, Object> candidateData = (Map<String, Object>) msg.get("candidate");
                        if (candidateData != null) {
                            String candidate = (String) candidateData.get("candidate");
                            Integer sdpMLineIndex = (Integer) candidateData.get("sdpMLineIndex");
                            String sdpMid = (String) candidateData.get("sdpMid");
                            listener.onIceCandidate(fromUserId, candidate, sdpMLineIndex, sdpMid);
                        }
                    }
                    break;
                    
                case "ERROR":
                    if (listener != null) {
                        listener.onError((String) msg.get("error"));
                    }
                    break;
                    
                default:
                    LOG.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            LOG.error("Error handling message: {}", e.getMessage(), e);
        }
    }
    
    public boolean isConnected() {
        return client != null && client.isOpen();
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getCurrentRoomId() {
        return currentRoomId;
    }
}
