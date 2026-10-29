package com.distributed_ide.signaling.server;

import com.distributed_ide.signaling.model.Room;
import com.distributed_ide.signaling.model.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour RoomManager.
 */
class RoomManagerTest {
    
    private RoomManager manager;
    
    @BeforeEach
    void setUp() {
        manager = new RoomManager(4, 300000); // max 4, timeout 5min
    }
    
    @Test
    void testCreateRoom() {
        Room room = manager.createRoom("room1", "user1");
        assertNotNull(room);
        assertEquals("room1", room.getRoomId());
        assertEquals("user1", room.getCreatorId());
    }
    
    @Test
    void testGetRoom() {
        manager.createRoom("room1", "user1");
        Room room = manager.getRoom("room1");
        assertNotNull(room);
    }
    
    @Test
    void testGetNonExistentRoom() {
        assertNull(manager.getRoom("nonexistent"));
    }
    
    @Test
    void testGetOrCreateRoom() {
        Room room1 = manager.getOrCreateRoom("room1", "user1");
        assertNotNull(room1);
        
        Room room2 = manager.getOrCreateRoom("room1", "other");
        assertSame(room1, room2); // same instance
        assertEquals("user1", room2.getCreatorId()); // creator unchanged
    }
    
    @Test
    void testDeleteRoom() {
        manager.createRoom("room1", "user1");
        manager.deleteRoom("room1");
        assertNull(manager.getRoom("room1"));
    }
    
    @Test
    void testAddUserToRoom() {
        manager.createRoom("room1", "user1");
        UserSession session = new UserSession("user1", "room1", null);
        
        assertTrue(manager.addUserToRoom("room1", session));
        
        Room room = manager.getRoom("room1");
        assertEquals(1, room.getParticipantCount());
    }
    
    @Test
    void testRemoveUserFromRoom() {
        manager.createRoom("room1", "user1");
        UserSession session = new UserSession("user1", "room1", null);
        manager.addUserToRoom("room1", session);
        
        manager.removeUserFromRoom("room1", "user1");
        assertNull(manager.getRoom("room1")); // room deleted when empty
    }
    
    @Test
    void testGetUserRoom() {
        manager.createRoom("room1", "user1");
        UserSession session = new UserSession("user1", "room1", null);
        manager.addUserToRoom("room1", session);
        
        Room room = manager.getUserRoom("user1");
        assertEquals("room1", room.getRoomId());
    }
    
    @Test
    void testAddMultipleUsers() {
        manager.createRoom("room1", "user1");
        
        for (int i = 1; i <= 3; i++) {
            UserSession session = new UserSession("user" + i, "room1", null);
            manager.addUserToRoom("room1", session);
        }
        
        Room room = manager.getRoom("room1");
        assertEquals(3, room.getParticipantCount());
    }
    
    @Test
    void testRoomCountFull() {
        manager.createRoom("room1", "user1");
        assertEquals(1, manager.getRoomCount());
        
        manager.createRoom("room2", "user2");
        assertEquals(2, manager.getRoomCount());
    }
    
    @Test
    void testGetAllRooms() {
        manager.createRoom("room1", "user1");
        manager.createRoom("room2", "user2");
        
        assertEquals(2, manager.getAllRooms().size());
    }
    
    @Test
    void testGetAllRoomsInfo() {
        manager.createRoom("room1", "user1");
        manager.createRoom("room2", "user2");
        
        java.util.List<java.util.Map<String, Object>> infos = manager.getAllRoomsInfo();
        assertEquals(2, infos.size());
    }
}
