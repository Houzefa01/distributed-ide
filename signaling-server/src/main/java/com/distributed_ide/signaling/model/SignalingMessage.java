package com.distributed_ide.signaling.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Message de signalisation WebRTC contenant SDP et ICE candidates.
 */
public class SignalingMessage {
    
    public enum MessageType {
        JOIN_ROOM,
        LEAVE_ROOM,
        OFFER,
        ANSWER,
        SDP_OFFER,
        SDP_ANSWER,
        ICE_CANDIDATE,
        USER_JOINED,
        USER_LEFT,
        ROOM_FULL,
        ERROR,
        PING,
        PONG,
        CALL_REQUEST,
        CALL_ACCEPT,
        CALL_REJECT
    }
    
    @JsonProperty("type")
    private MessageType type;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("roomId")
    private String roomId;
    
    @JsonProperty("targetUserId")
    private String targetUserId;
    
    @JsonProperty("sdp")
    private String sdp;
    
    @JsonProperty("candidate")
    private IceCandidate candidate;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("error")
    private String error;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    // Constructors
    public SignalingMessage() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public SignalingMessage(MessageType type, String userId, String roomId) {
        this();
        this.type = type;
        this.userId = userId;
        this.roomId = roomId;
    }
    
    // Getters and Setters
    public MessageType getType() {
        return type;
    }
    
    public void setType(MessageType type) {
        this.type = type;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
    public String getTargetUserId() {
        return targetUserId;
    }
    
    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }
    
    public String getSdp() {
        return sdp;
    }
    
    public void setSdp(String sdp) {
        this.sdp = sdp;
    }
    
    public IceCandidate getCandidate() {
        return candidate;
    }
    
    public void setCandidate(IceCandidate candidate) {
        this.candidate = candidate;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    /**
     * Classe pour les ICE candidates.
     */
    public static class IceCandidate {
        @JsonProperty("candidate")
        private String candidate;
        
        @JsonProperty("sdpMLineIndex")
        private Integer sdpMLineIndex;
        
        @JsonProperty("sdpMid")
        private String sdpMid;
        
        public IceCandidate() {}
        
        public IceCandidate(String candidate, Integer sdpMLineIndex, String sdpMid) {
            this.candidate = candidate;
            this.sdpMLineIndex = sdpMLineIndex;
            this.sdpMid = sdpMid;
        }
        
        public String getCandidate() {
            return candidate;
        }
        
        public void setCandidate(String candidate) {
            this.candidate = candidate;
        }
        
        public Integer getSdpMLineIndex() {
            return sdpMLineIndex;
        }
        
        public void setSdpMLineIndex(Integer sdpMLineIndex) {
            this.sdpMLineIndex = sdpMLineIndex;
        }
        
        public String getSdpMid() {
            return sdpMid;
        }
        
        public void setSdpMid(String sdpMid) {
            this.sdpMid = sdpMid;
        }
    }
}
