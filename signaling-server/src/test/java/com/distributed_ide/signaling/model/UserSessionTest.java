package com.distributed_ide.signaling.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour UserSession.
 */
class UserSessionTest {
    
    private UserSession session;
    
    @BeforeEach
    void setUp() {
        session = new UserSession("user1", "room1", null);
    }
    
    @Test
    void testSessionCreation() {
        assertEquals("user1", session.getUserId());
        assertEquals("room1", session.getRoomId());
        assertTrue(session.isActive());
        assertNull(session.getWebSocket());
    }
    
    @Test
    void testConnectedAt() {
        long now = System.currentTimeMillis();
        assertNotNull(session);
        assertTrue(session.getConnectedAt() <= now);
    }
    
    @Test
    void testHeartbeat() {
        long initialHeartbeat = session.getLastHeartbeat();
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        session.updateHeartbeat();
        assertTrue(session.getLastHeartbeat() > initialHeartbeat);
    }
    
    @Test
    void testConnectionDuration() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long duration = session.getConnectionDuration();
        assertTrue(duration >= 50);
        assertTrue(duration < 200);
    }
    
    @Test
    void testActiveFlag() {
        assertTrue(session.isActive());
        session.setActive(false);
        assertFalse(session.isActive());
    }
    
    @Test
    void testMetadata() {
        session.setMetadataValue("displayName", "Alice");
        session.setMetadataValue("resolution", "1920x1080");
        
        assertEquals("Alice", session.getMetadataValue("displayName"));
        assertEquals("1920x1080", session.getMetadataValue("resolution"));
    }
    
    @Test
    void testMetadataMap() {
        session.setMetadataValue("key1", "value1");
        session.setMetadataValue("key2", "value2");
        
        assertEquals(2, session.getMetadata().size());
        assertTrue(session.getMetadata().containsKey("key1"));
    }
}
