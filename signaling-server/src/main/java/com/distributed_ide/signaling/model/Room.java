package com.distributed_ide.signaling.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Représente une salle de conférence WebRTC.
 */
public class Room {
    private static final Logger LOG = LoggerFactory.getLogger(Room.class);
    
    private final String roomId;
    private final String creatorId;
    private final long createdAt;
    private volatile long lastActivityAt;
    private final int maxParticipants;
    private final Map<String, UserSession> participants;
    private volatile boolean active;
    private final Map<String, Object> metadata;
    
    public Room(String roomId, String creatorId, int maxParticipants) {
        this.roomId = roomId;
        this.creatorId = creatorId;
        this.maxParticipants = maxParticipants;
        this.createdAt = System.currentTimeMillis();
        this.lastActivityAt = System.currentTimeMillis();
        this.active = true;
        this.participants = new ConcurrentHashMap<>();
        this.metadata = Collections.synchronizedMap(new HashMap<>());
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public String getCreatorId() {
        return creatorId;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getLastActivityAt() {
        return lastActivityAt;
    }
    
    public int getMaxParticipants() {
        return maxParticipants;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public int getParticipantCount() {
        return participants.size();
    }
    
    public boolean isFull() {
        return participants.size() >= maxParticipants;
    }
    
    public boolean addParticipant(UserSession session) {
        if (isFull()) {
            LOG.warn("Room {} is full", roomId);
            return false;
        }
        
        participants.put(session.getUserId(), session);
        updateActivityTime();
        LOG.info("User {} joined room {}", session.getUserId(), roomId);
        return true;
    }
    
    public UserSession removeParticipant(String userId) {
        UserSession removed = participants.remove(userId);
        if (removed != null) {
            updateActivityTime();
            LOG.info("User {} left room {}", userId, roomId);
        }
        return removed;
    }
    
    public UserSession getParticipant(String userId) {
        return participants.get(userId);
    }
    
    public Collection<UserSession> getAllParticipants() {
        return participants.values();
    }
    
    public List<String> getOtherParticipantIds(String excludeUserId) {
        List<String> others = new ArrayList<>();
        for (String userId : participants.keySet()) {
            if (!userId.equals(excludeUserId)) {
                others.add(userId);
            }
        }
        return others;
    }
    
    public void updateActivityTime() {
        this.lastActivityAt = System.currentTimeMillis();
    }
    
    public long getInactivityDuration() {
        return System.currentTimeMillis() - lastActivityAt;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadataValue(String key, Object value) {
        metadata.put(key, value);
        updateActivityTime();
    }
    
    public Object getMetadataValue(String key) {
        return metadata.get(key);
    }
    
    public Map<String, Object> getRoomInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("roomId", roomId);
        info.put("creatorId", creatorId);
        info.put("createdAt", createdAt);
        info.put("participantCount", getParticipantCount());
        info.put("maxParticipants", maxParticipants);
        info.put("active", active);
        info.put("participantIds", new ArrayList<>(participants.keySet()));
        return info;
    }
}
