package com.distributed_ide.javafx_client.webrtc;

import com.distributed_ide.signaling.model.SignalingMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du client de signalisation (construction des messages, pas de WebSocket réel).
 */
class SignalingClientServiceTest {

    @Test
    void signalingMessage_offer_hasSdpAndTarget() {
        SignalingMessage msg = new SignalingMessage(SignalingMessage.MessageType.OFFER, "user1", "room1");
        msg.setTargetUserId("user2");
        msg.setSdp("v=0\r\no=test\r\n");
        assertEquals(SignalingMessage.MessageType.OFFER, msg.getType());
        assertEquals("user1", msg.getUserId());
        assertEquals("room1", msg.getRoomId());
        assertEquals("user2", msg.getTargetUserId());
        assertEquals("v=0\r\no=test\r\n", msg.getSdp());
    }

    @Test
    void signalingMessage_iceCandidate_hasCandidate() {
        SignalingMessage.IceCandidate c = new SignalingMessage.IceCandidate(
            "candidate:1 1 UDP 1 127.0.0.1 9 typ host", 0, "0");
        assertEquals(0, c.getSdpMLineIndex());
        assertEquals("0", c.getSdpMid());
        assertNotNull(c.getCandidate());
    }

    @Test
    void webRtcCallService_hasDefaultUserId() {
        WebRtcCallService service = new WebRtcCallService();
        assertNotNull(service.getDefaultUserId());
        assertTrue(service.getDefaultUserId().startsWith("javafx-"));
    }

    @Test
    void webRtcPeerConnectionFactory_defaultCreatesStub() {
        WebRtcPeerConnectionFactory factory = WebRtcPeerConnectionFactory.defaultFactory();
        WebRtcPeerConnection peer = factory.createPeerConnection("remote1");
        assertNotNull(peer);
        assertTrue(peer instanceof WebRtcPeerConnectionStub);
    }
}
