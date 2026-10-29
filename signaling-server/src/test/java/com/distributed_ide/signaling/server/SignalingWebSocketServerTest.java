package com.distributed_ide.signaling.server;

import com.distributed_ide.signaling.model.Room;
import com.distributed_ide.signaling.model.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour SignalingWebSocketServer (sans WebSocket réelle).
 */
class SignalingWebSocketServerTest {
    
    private RoomManager roomManager;
    
    @BeforeEach
    void setUp() {
        roomManager = new RoomManager(4, 300000);
    }
    
    @Test
    void testRoomManagerIntegration() {
        // Créer une salle
        Room room = roomManager.createRoom("meeting1", "alice");
        assertNotNull(room);
        
        // Ajouter des utilisateurs
        UserSession session1 = new UserSession("alice", "meeting1", null);
        UserSession session2 = new UserSession("bob", "meeting1", null);
        
        assertTrue(roomManager.addUserToRoom("meeting1", session1));
        assertTrue(roomManager.addUserToRoom("meeting1", session2));
        
        // Vérifier
        Room retrievedRoom = roomManager.getRoom("meeting1");
        assertEquals(2, retrievedRoom.getParticipantCount());
        
        // Vérifier les participants
        assertEquals("alice", retrievedRoom.getParticipant("alice").getUserId());
        assertEquals("bob", retrievedRoom.getParticipant("bob").getUserId());
        
        // Vérifier les autres participants
        java.util.List<String> aliceOthers = retrievedRoom.getOtherParticipantIds("alice");
        assertEquals(1, aliceOthers.size());
        assertTrue(aliceOthers.contains("bob"));
    }
    
    @Test
    void testMultipleRooms() {
        // Créer plusieurs salles
        Room room1 = roomManager.createRoom("meeting1", "alice");
        Room room2 = roomManager.createRoom("meeting2", "bob");
        
        // Ajouter des utilisateurs dans room1
        UserSession session1 = new UserSession("alice", "meeting1", null);
        UserSession session2 = new UserSession("charlie", "meeting1", null);
        roomManager.addUserToRoom("meeting1", session1);
        roomManager.addUserToRoom("meeting1", session2);
        
        // Ajouter des utilisateurs dans room2
        UserSession session3 = new UserSession("bob", "meeting2", null);
        UserSession session4 = new UserSession("dave", "meeting2", null);
        roomManager.addUserToRoom("meeting2", session3);
        roomManager.addUserToRoom("meeting2", session4);
        
        // Vérifier
        assertEquals(2, room1.getParticipantCount());
        assertEquals(2, room2.getParticipantCount());
        assertEquals(2, roomManager.getRoomCount());
    }
    
    @Test
    void testRoomCapacity() {
        Room room = roomManager.createRoom("room1", "user1");
        
        // Remplir la salle
        for (int i = 1; i <= 4; i++) {
            UserSession session = new UserSession("user" + i, "room1", null);
            assertTrue(roomManager.addUserToRoom("room1", session));
        }
        
        // Essayer d'ajouter un 5ème utilisateur
        UserSession extraSession = new UserSession("user5", "room1", null);
        assertFalse(roomManager.addUserToRoom("room1", extraSession));
    }
    
    @Test
    void testRoomDeletion() {
        // Créer une salle avec un utilisateur
        Room room = roomManager.createRoom("room1", "user1");
        UserSession session = new UserSession("user1", "room1", null);
        roomManager.addUserToRoom("room1", session);
        
        // Vérifier qu'elle existe
        assertNotNull(roomManager.getRoom("room1"));
        
        // Retirer l'utilisateur
        roomManager.removeUserFromRoom("room1", "user1");
        
        // Vérifier que la salle est supprimée (elle s'auto-supprime quand vide)
        assertNull(roomManager.getRoom("room1"));
    }
    
    @Test
    void testMetadataFlow() {
        // Créer une salle avec métadonnées
        Room room = roomManager.createRoom("conference1", "organizer");
        room.setMetadataValue("title", "Team Meeting");
        room.setMetadataValue("recordingEnabled", true);
        
        // Ajouter un utilisateur avec métadonnées
        UserSession session = new UserSession("participant1", "conference1", null);
        session.setMetadataValue("displayName", "Alice");
        session.setMetadataValue("videoEnabled", true);
        
        roomManager.addUserToRoom("conference1", session);
        
        // Vérifier les métadonnées
        Room retrieved = roomManager.getRoom("conference1");
        assertEquals("Team Meeting", retrieved.getMetadataValue("title"));
        
        UserSession retrievedSession = retrieved.getParticipant("participant1");
        assertEquals("Alice", retrievedSession.getMetadataValue("displayName"));
        assertEquals(true, retrievedSession.getMetadataValue("videoEnabled"));
    }
    
    @Test
    void testUserRoomTracking() {
        // Créer deux salles
        roomManager.createRoom("room1", "user1");
        roomManager.createRoom("room2", "user2");
        
        // Ajouter un utilisateur à room1
        UserSession session1 = new UserSession("user1", "room1", null);
        roomManager.addUserToRoom("room1", session1);
        
        // Vérifier que l'utilisateur est dans room1
        Room userRoom = roomManager.getUserRoom("user1");
        assertEquals("room1", userRoom.getRoomId());
        
        // Retirer et vérifier
        roomManager.removeUserFromRoom("room1", "user1");
        assertNull(roomManager.getUserRoom("user1"));
    }
}
