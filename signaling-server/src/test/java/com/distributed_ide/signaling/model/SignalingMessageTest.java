package com.distributed_ide.signaling.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour SignalingMessage.
 */
class SignalingMessageTest {
    
    private SignalingMessage message;
    
    @BeforeEach
    void setUp() {
        message = new SignalingMessage(SignalingMessage.MessageType.OFFER, "user1", "room1");
    }
    
    @Test
    void testMessageCreation() {
        assertEquals(SignalingMessage.MessageType.OFFER, message.getType());
        assertEquals("user1", message.getUserId());
        assertEquals("room1", message.getRoomId());
        assertTrue(message.getTimestamp() > 0);
    }
    
    @Test
    void testSetSdp() {
        String sdp = "v=0\r\ns=test\r\n";
        message.setSdp(sdp);
        assertEquals(sdp, message.getSdp());
    }
    
    @Test
    void testIceCandidate() {
        SignalingMessage.IceCandidate candidate = new SignalingMessage.IceCandidate(
            "candidate:844740 1 UDP 1234567890 127.0.0.1 54321 typ host",
            0,
            "audio"
        );
        
        message.setCandidate(candidate);
        assertNotNull(message.getCandidate());
        assertEquals("audio", message.getCandidate().getSdpMid());
        assertEquals(0, message.getCandidate().getSdpMLineIndex());
    }
    
    @Test
    void testMessageTypes() {
        SignalingMessage join = new SignalingMessage(SignalingMessage.MessageType.JOIN_ROOM, "u1", "r1");
        SignalingMessage leave = new SignalingMessage(SignalingMessage.MessageType.LEAVE_ROOM, "u1", "r1");
        SignalingMessage answer = new SignalingMessage(SignalingMessage.MessageType.ANSWER, "u1", "r1");
        
        assertEquals(SignalingMessage.MessageType.JOIN_ROOM, join.getType());
        assertEquals(SignalingMessage.MessageType.LEAVE_ROOM, leave.getType());
        assertEquals(SignalingMessage.MessageType.ANSWER, answer.getType());
    }
    
    @Test
    void testTargetUserId() {
        message.setTargetUserId("user2");
        assertEquals("user2", message.getTargetUserId());
    }
    
    @Test
    void testErrorMessage() {
        message.setError("Connection failed");
        assertEquals("Connection failed", message.getError());
    }
    
    @Test
    void testMetadata() {
        message.setMetadata(java.util.Map.of("codec", "H264", "bitrate", 3000));
        assertNotNull(message.getMetadata());
        assertEquals(2, message.getMetadata().size());
    }
}
