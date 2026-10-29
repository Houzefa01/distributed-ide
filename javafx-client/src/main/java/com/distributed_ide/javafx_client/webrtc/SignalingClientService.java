package com.distributed_ide.javafx_client.webrtc;

import com.distributed_ide.signaling.model.SignalingMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client WebSocket vers le signaling-server : join/leave room, envoi/réception SDP et ICE.
 * Toute la logique réseau est non bloquante (executor dédié). Les callbacks sont notifiés sur le thread JavaFX.
 */
public class SignalingClientService {

    private static final Logger LOG = LoggerFactory.getLogger(SignalingClientService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private volatile WebSocketClient client;
    private volatile String userId;
    private volatile String roomId;
    private volatile String wsUri;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "signaling-ws-io");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final WebRtcConnectionManager connectionManager = new WebRtcConnectionManager();
    private Listener listener;

    public interface Listener {
        void onConnected();
        void onDisconnected(String reason);
        void onJoinedRoom(String roomId, List<String> existingUserIds);
        void onUserJoined(String remoteUserId);
        void onUserLeft(String remoteUserId);
        void onOffer(String fromUserId, String sdp);
        void onAnswer(String fromUserId, String sdp);
        void onIceCandidate(String fromUserId, SignalingMessage.IceCandidate candidate);
        void onRoomFull(String roomId);
        void onError(String message);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Connexion non bloquante au signaling-server.
     * ✅ FIX: Enregistre le wsUri pour les reconnexions automatiques
     */
    public void connect(String wsUri) {
        if (wsUri == null || wsUri.isEmpty()) return;
        this.wsUri = wsUri;
        if (connecting.getAndSet(true)) return;
        ioExecutor.execute(() -> doConnect(wsUri));
    }

    private void doConnect(String wsUri) {
        try {
            URI uri = new URI(wsUri);
            client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connecting.set(false);
                    connectionManager.resetReconnectAttempts();
                    LOG.info("✅ Signaling WebSocket open");
                    if (listener != null) runOnFx(() -> listener.onConnected());
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connecting.set(false);
                    client = null;
                    LOG.warn("⚠️  Signaling WebSocket closed: {}", reason);
                    if (listener != null) runOnFx(() -> listener.onDisconnected(reason != null ? reason : "closed"));
                    
                    // ✅ FIX: Programmer une reconnexion automatique
                    if (remote) {
                        LOG.info("🔄 Reconnexion automatique programmée...");
                        connectionManager.scheduleReconnect(
                            () -> doConnect(wsUri),
                            (msg) -> {
                                LOG.error("❌ Max reconnect attempts reached: {}", msg);
                                if (listener != null) runOnFx(() -> listener.onError("Impossible de se reconnecter"));
                            }
                        );
                    }
                }

                @Override
                public void onError(Exception ex) {
                    connecting.set(false);
                    LOG.error("❌ Signaling WebSocket error", ex);
                    if (listener != null) runOnFx(() -> listener.onError(ex.getMessage()));
                }
            };
            client.connect();
        } catch (Exception e) {
            connecting.set(false);
            LOG.error("❌ Signaling connect failed", e);
            if (listener != null) runOnFx(() -> listener.onError(e.getMessage()));
        }
    }

    private static void runOnFx(Runnable r) {
        try {
            javafx.application.Platform.runLater(r);
        } catch (Exception ignored) { }
    }

    /**
     * Rejoint une salle (à appeler après connexion). Envoi non bloquant.
     */
    public void joinRoom(String roomId, String userId) {
        this.roomId = roomId;
        this.userId = userId;
        send(new SignalingMessage(SignalingMessage.MessageType.JOIN_ROOM, userId, roomId));
    }

    /**
     * Quitte la salle et notifie le serveur.
     */
    public void leaveRoom() {
        if (userId != null && roomId != null) {
            send(new SignalingMessage(SignalingMessage.MessageType.LEAVE_ROOM, userId, roomId));
        }
        roomId = null;
    }

    public void sendOffer(String targetUserId, String sdp) {
        SignalingMessage msg = new SignalingMessage(SignalingMessage.MessageType.OFFER, userId, roomId);
        msg.setTargetUserId(targetUserId);
        msg.setSdp(sdp);
        send(msg);
    }

    public void sendAnswer(String targetUserId, String sdp) {
        SignalingMessage msg = new SignalingMessage(SignalingMessage.MessageType.ANSWER, userId, roomId);
        msg.setTargetUserId(targetUserId);
        msg.setSdp(sdp);
        send(msg);
    }

    public void sendIceCandidate(String targetUserId, SignalingMessage.IceCandidate candidate) {
        SignalingMessage msg = new SignalingMessage(SignalingMessage.MessageType.ICE_CANDIDATE, userId, roomId);
        msg.setTargetUserId(targetUserId);
        msg.setCandidate(candidate);
        send(msg);
    }

    private void send(SignalingMessage msg) {
        if (client == null || !client.isOpen()) return;
        ioExecutor.execute(() -> {
            try {
                String json = MAPPER.writeValueAsString(msg);
                if (client != null && client.isOpen()) client.send(json);
            } catch (Exception e) {
                LOG.warn("Signaling send failed", e);
                if (listener != null) runOnFx(() -> listener.onError(e.getMessage()));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(String message) {
        try {
            SignalingMessage msg = MAPPER.readValue(message, SignalingMessage.class);
            if (msg == null || msg.getType() == null) return;
            switch (msg.getType()) {
                case JOIN_ROOM:
                    List<String> existing = null;
                    if (msg.getMetadata() != null && msg.getMetadata().get("roomUsers") instanceof List) {
                        existing = (List<String>) msg.getMetadata().get("roomUsers");
                    }
                    List<String> finalExisting = existing;
                    runOnFx(() -> {
                        if (listener != null) listener.onJoinedRoom(msg.getRoomId(), finalExisting != null ? finalExisting : List.of());
                    });
                    break;
                case USER_JOINED:
                    String joinedId = msg.getMetadata() != null && msg.getMetadata().get("userId") != null
                        ? msg.getMetadata().get("userId").toString() : null;
                    if (joinedId != null) runOnFx(() -> { if (listener != null) listener.onUserJoined(joinedId); });
                    break;
                case USER_LEFT:
                    String leftId = msg.getMetadata() != null && msg.getMetadata().get("userId") != null
                        ? msg.getMetadata().get("userId").toString() : null;
                    if (leftId != null) runOnFx(() -> { if (listener != null) listener.onUserLeft(leftId); });
                    break;
                case OFFER:
                    if (msg.getUserId() != null && msg.getSdp() != null)
                        runOnFx(() -> { if (listener != null) listener.onOffer(msg.getUserId(), msg.getSdp()); });
                    break;
                case ANSWER:
                    if (msg.getUserId() != null && msg.getSdp() != null)
                        runOnFx(() -> { if (listener != null) listener.onAnswer(msg.getUserId(), msg.getSdp()); });
                    break;
                case ICE_CANDIDATE:
                    if (msg.getUserId() != null && msg.getCandidate() != null)
                        runOnFx(() -> { if (listener != null) listener.onIceCandidate(msg.getUserId(), msg.getCandidate()); });
                    break;
                case ROOM_FULL:
                    runOnFx(() -> { if (listener != null) listener.onRoomFull(msg.getRoomId()); });
                    break;
                case ERROR:
                    String err = msg.getError();
                    runOnFx(() -> { if (listener != null) listener.onError(err != null ? err : "Unknown error"); });
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            LOG.trace("Parse signaling message failed: {}", e.getMessage());
        }
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    public String getUserId() { return userId; }
    public String getRoomId() { return roomId; }
    
    /**
     * Déconnexion propre du signaling-server.
     * ✅ FIX: Arrête le gestionnaire de connexion et libère les ressources
     */
    public void disconnect() {
        LOG.info("🛑 Déconnexion du signaling");
        if (client != null) {
            try {
                client.closeConnection(1000, "Normal closure");
            } catch (Exception e) {
                LOG.warn("⚠️  Erreur fermeture WebSocket: {}", e.getMessage());
                client = null;
            }
        }
        connectionManager.shutdown();
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
