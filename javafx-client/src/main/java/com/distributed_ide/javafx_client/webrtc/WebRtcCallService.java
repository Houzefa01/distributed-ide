package com.distributed_ide.javafx_client.webrtc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Facade pour les appels WebRTC multi-utilisateurs : connexion au signaling-server,
 * gestion SDP et ICE, appels P2P. Flux média P2P ; le backend ne gère que la signalisation.
 */
public class WebRtcCallService {

    private static final Logger LOG = LoggerFactory.getLogger(WebRtcCallService.class);

    private final SignalingClientService signaling = new SignalingClientService();
    private final WebRtcRoomSession roomSession;
    private volatile String defaultUserId = "javafx-" + UUID.randomUUID().toString().substring(0, 8);

    public WebRtcCallService() {
        this(WebRtcPeerConnectionFactory.defaultFactory());
    }

    public WebRtcCallService(WebRtcPeerConnectionFactory peerFactory) {
        this.roomSession = new WebRtcRoomSession(signaling, peerFactory);
    }

    /**
     * Connexion non bloquante au signaling-server.
     */
    public void connect(String signalingWsUri) {
        if (signalingWsUri == null || signalingWsUri.isEmpty()) return;
        signaling.connect(signalingWsUri);
    }

    /**
     * Rejoint une salle (à appeler après connexion au signaling-server).
     * Démarre la session multi-pairs : SDP (offer/answer) et ICE candidates.
     */
    public void joinRoom(String roomId, String userId) {
        if (userId != null && !userId.isEmpty()) defaultUserId = userId;
        roomSession.joinRoom(roomId, defaultUserId);
    }

    public void setRoomSessionListener(WebRtcRoomSession.Listener listener) {
        roomSession.setListener(listener);
    }

    /**
     * Quitte la salle et ferme les connexions P2P.
     */
    public void leaveRoom() {
        roomSession.leaveRoom();
    }

    /**
     * Déconnexion du signaling-server et fermeture de toutes les connexions P2P.
     * ✅ FIX: Appelle shutdown() sur la room session et nettoie les ressources
     */
    public void disconnect() {
        LOG.info("🛑 Déconnexion WebRTC");
        try {
            roomSession.shutdown();
        } catch (Exception e) {
            LOG.warn("⚠️  Erreur shutdown room session: {}", e.getMessage());
        }
        try {
            signaling.disconnect();
        } catch (Exception e) {
            LOG.warn("⚠️  Erreur disconnect signaling: {}", e.getMessage());
        }
        try {
            WebRtcPeerConnectionStub.shutdownExecutor();
        } catch (Exception e) {
            LOG.warn("⚠️  Erreur shutdown executor: {}", e.getMessage());
        }
    }

    public SignalingClientService getSignaling() {
        return signaling;
    }

    public WebRtcRoomSession getRoomSession() {
        return roomSession;
    }

    public String getDefaultUserId() {
        return defaultUserId;
    }

    public void setDefaultUserId(String userId) {
        if (userId != null && !userId.isEmpty()) defaultUserId = userId;
    }
}
