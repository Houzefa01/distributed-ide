package com.distributed_ide.javafx_client.webrtc;

import com.distributed_ide.signaling.model.SignalingMessage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WebRtcPeerConnectionStubTest {

    @Test
    void createOffer_invokesOnLocalDescription() throws Exception {
        WebRtcPeerConnectionStub peer = new WebRtcPeerConnectionStub();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> sdpRef = new AtomicReference<>();
        peer.setListener(new WebRtcPeerConnection.Listener() {
            @Override
            public void onLocalDescription(String sdp) {
                sdpRef.set(sdp);
                latch.countDown();
            }
            @Override
            public void onIceCandidate(SignalingMessage.IceCandidate candidate) { }
            @Override
            public void onStateChange(WebRtcPeerConnection.State state) { }
        });
        peer.createOffer();
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(sdpRef.get());
        assertTrue(sdpRef.get().contains("stub-offer"));
    }

    @Test
    void createAnswer_invokesOnLocalDescription() throws Exception {
        WebRtcPeerConnectionStub peer = new WebRtcPeerConnectionStub();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> sdpRef = new AtomicReference<>();
        peer.setListener(new WebRtcPeerConnection.Listener() {
            @Override
            public void onLocalDescription(String sdp) {
                sdpRef.set(sdp);
                latch.countDown();
            }
            @Override
            public void onIceCandidate(SignalingMessage.IceCandidate candidate) { }
            @Override
            public void onStateChange(WebRtcPeerConnection.State state) { }
        });
        peer.setRemoteDescription("v=0\r\no=remote\r\n");
        peer.createAnswer();
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(sdpRef.get());
        assertTrue(sdpRef.get().contains("stub-answer"));
    }

    @Test
    void close_setsStateClosed() throws Exception {
        WebRtcPeerConnectionStub peer = new WebRtcPeerConnectionStub();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<WebRtcPeerConnection.State> stateRef = new AtomicReference<>();
        peer.setListener(new WebRtcPeerConnection.Listener() {
            @Override
            public void onLocalDescription(String sdp) { }
            @Override
            public void onIceCandidate(SignalingMessage.IceCandidate candidate) { }
            @Override
            public void onStateChange(WebRtcPeerConnection.State state) {
                stateRef.set(state);
                if (state == WebRtcPeerConnection.State.CLOSED) latch.countDown();
            }
        });
        peer.close();
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(WebRtcPeerConnection.State.CLOSED, stateRef.get());
    }
}
