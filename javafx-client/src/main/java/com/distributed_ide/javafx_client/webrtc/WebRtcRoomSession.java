package com.distributed_ide.javafx_client.webrtc;

import com.distributed_ide.signaling.model.SignalingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Session d'appel multi-utilisateurs : connexion au signaling-server,
 * gestion SDP (offer/answer) et ICE candidates pour chaque pair distant.
 * Flux média P2P ; le backend ne fait que la signalisation.
 */
public class WebRtcRoomSession {

    private static final Logger LOG = LoggerFactory.getLogger(WebRtcRoomSession.class);

    private final SignalingClientService signaling;
    private final WebRtcPeerConnectionFactory peerFactory;
    private final Map<String, WebRtcPeerConnection> peersByRemoteUserId = new ConcurrentHashMap<>();
    private final WebRtcConnectionManager connectionManager = new WebRtcConnectionManager();
    private final IceCandidateBuffer candidateBuffer = new IceCandidateBuffer();
    private final Map<String, Long> peerConnectStartTimes = new ConcurrentHashMap<>();
    private Listener listener;

    public interface Listener {
        void onPeerJoined(String remoteUserId);
        void onPeerLeft(String remoteUserId);
        void onPeerConnected(String remoteUserId);
        void onError(String message);
    }

    public WebRtcRoomSession(SignalingClientService signaling, WebRtcPeerConnectionFactory peerFactory) {
        this.signaling = signaling;
        this.peerFactory = peerFactory != null ? peerFactory : WebRtcPeerConnectionFactory.defaultFactory();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Démarre l'écoute des événements de signalisation et rejoint la salle.
     * À appeler après connect() sur le signaling client.
     */
    public void joinRoom(String roomId, String userId) {
        signaling.setListener(new SignalingClientService.Listener() {
            @Override
            public void onConnected() { }

            @Override
            public void onDisconnected(String reason) {
                closeAllPeers();
            }

            @Override
            public void onJoinedRoom(String rId, List<String> existingUserIds) {
                LOG.info("Joined room {}; existing users: {}", rId, existingUserIds);
                for (String remoteId : existingUserIds) {
                    if (!remoteId.equals(userId)) createPeerAndSendOffer(remoteId);
                }
            }

            @Override
            public void onUserJoined(String remoteUserId) {
                LOG.info("User joined: {}", remoteUserId);
                createPeerAndSendOffer(remoteUserId);
                notifyPeerJoined(remoteUserId);
            }

            @Override
            public void onUserLeft(String remoteUserId) {
                LOG.info("User left: {}", remoteUserId);
                closePeer(remoteUserId);
                notifyPeerLeft(remoteUserId);
            }

            @Override
            public void onOffer(String fromUserId, String sdp) {
                try {
                    WebRtcPeerConnection peer = getOrCreatePeer(fromUserId);
                    peer.setRemoteDescription(sdp);
                    // ✅ FIX: Flusher les ICE candidates en attente
                    int flushed = candidateBuffer.flushCandidates(fromUserId, peer);
                    LOG.info("📞 Offre reçue de {}, {} candidats appliqués", fromUserId, flushed);
                    peer.createAnswer();
                } catch (Exception e) {
                    LOG.error("❌ Erreur lors de la gestion d'offre depuis {}: {}", fromUserId, e.getMessage());
                    notifyError("Erreur de traitement d'offre: " + e.getMessage());
                }
            }

            @Override
            public void onAnswer(String fromUserId, String sdp) {
                try {
                    WebRtcPeerConnection peer = peersByRemoteUserId.get(fromUserId);
                    if (peer != null) {
                        peer.setRemoteDescription(sdp);
                        // ✅ FIX: Flusher les ICE candidates en attente
                        int flushed = candidateBuffer.flushCandidates(fromUserId, peer);
                        LOG.info("📟 Réponse reçue de {}, {} candidats appliqués", fromUserId, flushed);
                    }
                } catch (Exception e) {
                    LOG.error("❌ Erreur lors de la gestion de réponse depuis {}: {}", fromUserId, e.getMessage());
                    notifyError("Erreur de traitement de réponse: " + e.getMessage());
                }
            }

            @Override
            public void onIceCandidate(String fromUserId, SignalingMessage.IceCandidate candidate) {
                WebRtcPeerConnection peer = peersByRemoteUserId.get(fromUserId);
                if (peer != null) {
                    // ✅ FIX: Buffer candidate si remote description n'est pas encore reçue
                    if (!candidateBuffer.addCandidate(fromUserId, candidate, peer)) {
                        LOG.debug("✅ ICE candidate appliqué directement");
                    }
                }
            }

            @Override
            public void onRoomFull(String roomId) {
                notifyError("Salle pleine: " + roomId);
            }

            @Override
            public void onError(String message) {
                notifyError(message);
            }
        });
        signaling.joinRoom(roomId, userId);
    }

    /**
     * Quitte la salle et ferme toutes les connexions P2P.
     */
    public void leaveRoom() {
        closeAllPeers();
        candidateBuffer.clearAll();
        peerConnectStartTimes.clear();
        signaling.leaveRoom();
        LOG.info("✅ Salle quittée proprement");
    }

    private void createPeerAndSendOffer(String remoteUserId) {
        if (peersByRemoteUserId.containsKey(remoteUserId)) return;
        try {
            WebRtcPeerConnection peer = peerFactory.createPeerConnection(remoteUserId);
            peer.setListener(new WebRtcPeerConnection.Listener() {
                @Override
                public void onLocalDescription(String sdp) {
                    try {
                        signaling.sendOffer(remoteUserId, sdp);
                    } catch (Exception e) {
                        LOG.error("❌ Erreur envoi offre pour {}: {}", remoteUserId, e.getMessage());
                    }
                }
                @Override
                public void onIceCandidate(SignalingMessage.IceCandidate candidate) {
                    try {
                        signaling.sendIceCandidate(remoteUserId, candidate);
                    } catch (Exception e) {
                        LOG.error("❌ Erreur envoi ICE pour {}: {}", remoteUserId, e.getMessage());
                    }
                }
                @Override
                public void onStateChange(WebRtcPeerConnection.State state) {
                    if (state == WebRtcPeerConnection.State.CONNECTED) {
                        peerConnectStartTimes.remove(remoteUserId);
                        notifyPeerConnected(remoteUserId);
                        LOG.info("✅ Peer {} connecté", remoteUserId);
                    } else if (state == WebRtcPeerConnection.State.FAILED) {
                        LOG.warn("❌ Peer {} échoué", remoteUserId);
                        notifyError("Échec de la connexion peer: " + remoteUserId);
                    }
                }
            });
            peersByRemoteUserId.put(remoteUserId, peer);
            peerConnectStartTimes.put(remoteUserId, System.currentTimeMillis());
            
            // ✅ FIX: Programmer un timeout pour la connexion peer
            connectionManager.scheduleTimeout(
                "peer-connect-" + remoteUserId,
                connectionManager.getPeerConnectTimeoutMs(),
                () -> handlePeerTimeout(remoteUserId)
            );
            
            peer.createOffer();
        } catch (Exception e) {
            LOG.error("❌ Erreur création peer pour {}: {}", remoteUserId, e.getMessage());
            notifyError("Erreur création peer: " + e.getMessage());
        }
    }
    
    private void handlePeerTimeout(String remoteUserId) {
        Long startTime = peerConnectStartTimes.get(remoteUserId);
        if (startTime != null) {
            long elapsed = System.currentTimeMillis() - startTime;
            WebRtcPeerConnection peer = peersByRemoteUserId.get(remoteUserId);
            if (peer != null) {
                LOG.warn("⏱️  Timeout connexion peer {} après {}ms, fermeture", remoteUserId, elapsed);
                closePeer(remoteUserId);
                notifyError("Timeout connexion peer: " + remoteUserId);
            }
        }
    }

    private WebRtcPeerConnection getOrCreatePeer(String remoteUserId) {
        return peersByRemoteUserId.computeIfAbsent(remoteUserId, id -> {
            WebRtcPeerConnection p = peerFactory.createPeerConnection(id);
            p.setListener(new WebRtcPeerConnection.Listener() {
                @Override
                public void onLocalDescription(String sdp) {
                    signaling.sendAnswer(remoteUserId, sdp);
                }
                @Override
                public void onIceCandidate(SignalingMessage.IceCandidate candidate) {
                    signaling.sendIceCandidate(remoteUserId, candidate);
                }
                @Override
                public void onStateChange(WebRtcPeerConnection.State state) {
                    if (state == WebRtcPeerConnection.State.CONNECTED) notifyPeerConnected(remoteUserId);
                }
            });
            return p;
        });
    }

    private void closePeer(String remoteUserId) {
        WebRtcPeerConnection peer = peersByRemoteUserId.remove(remoteUserId);
        if (peer != null) peer.close();
    }

    private void closeAllPeers() {
        for (Map.Entry<String, WebRtcPeerConnection> e : peersByRemoteUserId.entrySet()) {
            try {
                e.getValue().close();
            } catch (Exception ex) {
                LOG.warn("⚠️  Erreur fermeture peer {}: {}", e.getKey(), ex.getMessage());
            }
        }
        peersByRemoteUserId.clear();
        peerConnectStartTimes.clear();
        candidateBuffer.clearAll();
    }

    private void notifyPeerJoined(String remoteUserId) {
        if (listener != null) listener.onPeerJoined(remoteUserId);
    }

    private void notifyPeerLeft(String remoteUserId) {
        if (listener != null) listener.onPeerLeft(remoteUserId);
    }

    private void notifyPeerConnected(String remoteUserId) {
        if (listener != null) listener.onPeerConnected(remoteUserId);
    }

    private void notifyError(String message) {
        if (listener != null) listener.onError(message);
    }

    public SignalingClientService getSignaling() { return signaling; }
    public Map<String, WebRtcPeerConnection> getPeers() { return Map.copyOf(peersByRemoteUserId); }
    
    /**
     * Arrête proprement la session et libère toutes les ressources.
     */
    public void shutdown() {
        leaveRoom();
        connectionManager.shutdown();
        LOG.info("🛑 WebRtcRoomSession arrêtée");
    }
}
