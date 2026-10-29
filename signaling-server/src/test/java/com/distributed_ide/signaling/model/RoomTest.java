package com.distributed_ide.signaling.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour Room.
 */
class RoomTest {
    
    private Room room;
    
    @BeforeEach
    void setUp() {
        room = new Room("room1", "user1", 4);
    }
    
    @Test
    void testRoomCreation() {
        assertEquals("room1", room.getRoomId());
        assertEquals("user1", room.getCreatorId());
        assertEquals(4, room.getMaxParticipants());
        assertTrue(room.isActive());
        assertEquals(0, room.getParticipantCount());
    }
    
    @Test
    void testAddParticipant() {
        UserSession session1 = new UserSession("user1", "room1", null);
        UserSession session2 = new UserSession("user2", "room1", null);
        
        assertTrue(room.addParticipant(session1));
        assertTrue(room.addParticipant(session2));
        assertEquals(2, room.getParticipantCount());
    }
    
    @Test
    void testRemoveParticipant() {
        UserSession session = new UserSession("user1", "room1", null);
        room.addParticipant(session);
        assertEquals(1, room.getParticipantCount());
        
        room.removeParticipant("user1");
        assertEquals(0, room.getParticipantCount());
    }
    
    @Test
    void testIsFull() {
        assertEquals(4, room.getMaxParticipants());
        assertFalse(room.isFull());
        
        for (int i = 1; i <= 4; i++) {
            UserSession session = new UserSession("user" + i, "room1", null);
            room.addParticipant(session);
        }
        
        assertTrue(room.isFull());
    }
    
    @Test
    void testCannotAddWhenFull() {
        for (int i = 1; i <= 4; i++) {
            UserSession session = new UserSession("user" + i, "room1", null);
            room.addParticipant(session);
        }
        
        UserSession extra = new UserSession("user5", "room1", null);
        assertFalse(room.addParticipant(extra));
    }
    
    @Test
    void testGetParticipant() {
        UserSession session = new UserSession("user1", "room1", null);
        room.addParticipant(session);
        
        UserSession retrieved = room.getParticipant("user1");
        assertNotNull(retrieved);
        assertEquals("user1", retrieved.getUserId());
    }
    
    @Test
    void testGetOtherParticipantIds() {
        for (int i = 1; i <= 3; i++) {
            UserSession session = new UserSession("user" + i, "room1", null);
            room.addParticipant(session);
        }
        
        java.util.List<String> others = room.getOtherParticipantIds("user1");
        assertEquals(2, others.size());
        assertTrue(others.contains("user2"));
        assertTrue(others.contains("user3"));
        assertFalse(others.contains("user1"));
    }
    
    @Test
    void testMetadata() {
        room.setMetadataValue("topic", "Meeting");
        room.setMetadataValue("encrypted", true);
        
        assertEquals("Meeting", room.getMetadataValue("topic"));
        assertEquals(true, room.getMetadataValue("encrypted"));
    }
    
    @Test
    void testActivityTime() {
        long initialLastActivity = room.getLastActivityAt();
        
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        room.updateActivityTime();
        assertTrue(room.getLastActivityAt() > initialLastActivity);
    }
    
    @Test
    void testRoomInfo() {
        UserSession session = new UserSession("user1", "room1", null);
        room.addParticipant(session);
        
        java.util.Map<String, Object> info = room.getRoomInfo();
        assertEquals("room1", info.get("roomId"));
        assertEquals("user1", info.get("creatorId"));
        assertEquals(1, info.get("participantCount"));
        assertEquals(4, info.get("maxParticipants"));
        assertTrue((Boolean) info.get("active"));
    }
}
