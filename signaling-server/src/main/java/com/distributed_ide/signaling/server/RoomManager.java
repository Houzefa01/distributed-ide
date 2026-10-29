package com.distributed_ide.signaling.server;

import com.distributed_ide.signaling.model.Room;
import com.distributed_ide.signaling.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire de salles de conférence.
 */
public class RoomManager {
    private static final Logger LOG = LoggerFactory.getLogger(RoomManager.class);
    
    private final Map<String, Room> rooms;
    private final Map<String, String> userRoomMap; // userId -> roomId
    private final int defaultMaxParticipants;
    private final long inactivityTimeout; // ms
    
    public RoomManager(int defaultMaxParticipants, long inactivityTimeout) {
        this.rooms = new ConcurrentHashMap<>();
        this.userRoomMap = new ConcurrentHashMap<>();
        this.defaultMaxParticipants = defaultMaxParticipants;
        this.inactivityTimeout = inactivityTimeout;
    }
    
    /**
     * Crée une nouvelle salle.
     */
    public Room createRoom(String roomId, String creatorId) {
        Room room = new Room(roomId, creatorId, defaultMaxParticipants);
        rooms.put(roomId, room);
        LOG.info("Room created: {} by user {}", roomId, creatorId);
        return room;
    }
    
    /**
     * Récupère une salle.
     */
    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }
    
    /**
     * Crée une salle si elle n'existe pas.
     */
    public Room getOrCreateRoom(String roomId, String creatorId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            room = createRoom(roomId, creatorId);
        }
        return room;
    }
    
    /**
     * Supprime une salle.
     */
    public void deleteRoom(String roomId) {
        Room room = rooms.remove(roomId);
        if (room != null) {
            // Supprimer tous les participants de la map
            for (String userId : new ArrayList<>(userRoomMap.keySet())) {
                if (userRoomMap.get(userId).equals(roomId)) {
                    userRoomMap.remove(userId);
                }
            }
            LOG.info("Room deleted: {}", roomId);
        }
    }
    
    /**
     * Ajoute un utilisateur à une salle.
     */
    public boolean addUserToRoom(String roomId, UserSession session) {
        Room room = getRoom(roomId);
        if (room == null) {
            LOG.warn("Room not found: {}", roomId);
            return false;
        }
        
        if (!room.addParticipant(session)) {
            LOG.warn("Failed to add user {} to room {}", session.getUserId(), roomId);
            return false;
        }
        
        userRoomMap.put(session.getUserId(), roomId);
        return true;
    }
    
    /**
     * Supprime un utilisateur d'une salle.
     */
    public void removeUserFromRoom(String roomId, String userId) {
        Room room = getRoom(roomId);
        if (room != null) {
            room.removeParticipant(userId);
            userRoomMap.remove(userId);
            
            // Supprimer la salle si elle est vide
            if (room.getParticipantCount() == 0) {
                deleteRoom(roomId);
            }
        }
    }
    
    /**
     * Récupère la salle actuelle d'un utilisateur.
     */
    public Room getUserRoom(String userId) {
        String roomId = userRoomMap.get(userId);
        return roomId != null ? getRoom(roomId) : null;
    }
    
    /**
     * Liste toutes les salles actives.
     */
    public Collection<Room> getAllRooms() {
        return rooms.values();
    }
    
    /**
     * Compte les salles actives.
     */
    public int getRoomCount() {
        return rooms.size();
    }
    
    /**
     * Nettoie les salles inactives.
     */
    public void cleanupInactiveRooms() {
        List<String> inactiveRooms = new ArrayList<>();
        
        for (Map.Entry<String, Room> entry : rooms.entrySet()) {
            Room room = entry.getValue();
            if (room.getInactivityDuration() > inactivityTimeout) {
                inactiveRooms.add(entry.getKey());
            }
        }
        
        for (String roomId : inactiveRooms) {
            deleteRoom(roomId);
            LOG.info("Cleaned up inactive room: {}", roomId);
        }
    }
    
    /**
     * Récupère les infos de toutes les salles.
     */
    public List<Map<String, Object>> getAllRoomsInfo() {
        List<Map<String, Object>> infos = new ArrayList<>();
        for (Room room : rooms.values()) {
            infos.add(room.getRoomInfo());
        }
        return infos;
    }
}
