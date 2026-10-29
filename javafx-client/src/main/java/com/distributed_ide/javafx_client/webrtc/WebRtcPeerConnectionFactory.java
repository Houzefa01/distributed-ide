package com.distributed_ide.javafx_client.webrtc;

/**
 * Fabrique de connexions WebRTC P2P. Permet de brancher une implémentation stub
 * (signalisation seule) ou une implémentation réelle (ex. webrtc-java) pour le flux média.
 */
@FunctionalInterface
public interface WebRtcPeerConnectionFactory {

    WebRtcPeerConnection createPeerConnection(String remoteUserId);

    /** Fabrique par défaut : stub (SDP/ICE uniquement, pas de média). */
    static WebRtcPeerConnectionFactory defaultFactory() {
        return remoteUserId -> new WebRtcPeerConnectionStub();
    }
}
