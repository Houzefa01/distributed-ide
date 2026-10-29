package com.distributed_ide.javafx_client.service;

import com.distributed_ide.sync.model.SyncMessage;
import com.distributed_ide.sync.server.SyncWebSocketServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service de collaboration LAN : héberger un serveur embarqué ou rejoindre un serveur distant.
 * Gère l'authentification par token, le rôle admin, la liste des clients,
 * les commandes admin (kick, shutdown) et le cycle de vie host/join.
 */
public class CollaborationService {

    private static final Logger LOG = LoggerFactory.getLogger(CollaborationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private volatile SyncWebSocketServer embeddedServer;
    private volatile WebSocketClient wsClient;
    private volatile String serverToken;
    private volatile String myClientId;
    private volatile String myRole;
    private volatile String myUsername;
    private volatile boolean hosting = false;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile Listener listener;

    public interface Listener {
        void onConnected(String clientId, String role);
        void onDisconnected(String reason);
        void onClientListUpdated(List<SyncMessage.ClientInfo> clients);
        void onKicked(String reason);
        void onServerStopping(String reason);
        void onError(String message);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Démarre un serveur embarqué et s'y connecte en tant qu'admin.
     */
    public CompletableFuture<String> hostServer(int port, String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                serverToken = UUID.randomUUID().toString().substring(0, 8);
                String nodeId = "host-" + UUID.randomUUID().toString().substring(0, 8);
                embeddedServer = new SyncWebSocketServer(port, nodeId, serverToken);
                embeddedServer.start();
                hosting = true;
                myUsername = username != null ? username : "Admin";

                // Attendre que le serveur soit prêt et vérifier le bind
                Thread.sleep(800);

                // Vérifier que le port est bien bindé en tentant une connexion test
                try (java.net.Socket testSock = new java.net.Socket("localhost", port)) {
                    // Port is open, server started OK
                } catch (java.net.ConnectException ce) {
                    throw new RuntimeException("Server failed to bind port " + port + " (already in use?)");
                }

                String localIp = InetAddress.getLocalHost().getHostAddress();
                String wsUri = "ws://" + localIp + ":" + port;
                connectToServer(wsUri, serverToken, myUsername);

                LOG.info("Hosted server on port {} (token={}, ip={})", port, serverToken, localIp);
                return serverToken;
            } catch (Exception e) {
                LOG.error("Failed to host server", e);
                if (listener != null) Platform.runLater(() -> listener.onError("Host failed: " + e.getMessage()));
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Rejoint un serveur distant avec IP, port, token et username.
     */
    public CompletableFuture<Void> joinServer(String host, int port, String token, String username) {
        return CompletableFuture.runAsync(() -> {
            try {
                serverToken = token;
                myUsername = username != null ? username : "Client";
                hosting = false;
                String wsUri = "ws://" + host + ":" + port;
                connectToServer(wsUri, token, myUsername);
            } catch (Exception e) {
                LOG.error("Failed to join server", e);
                if (listener != null) Platform.runLater(() -> listener.onError("Join failed: " + e.getMessage()));
            }
        });
    }

    private void connectToServer(String wsUri, String token, String username) {
        try {
            URI uri = new URI(wsUri);
            wsClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    LOG.info("Collaboration WebSocket open to {}", wsUri);
                    // Envoyer AUTH
                    SyncMessage auth = new SyncMessage(SyncMessage.MessageType.AUTH, "", null, "");
                    auth.setToken(token);
                    auth.setUsername(username);
                    sendJson(auth);
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connected.set(false);
                    LOG.info("Collaboration WebSocket closed: {} (code={})", reason, code);
                    if (listener != null) Platform.runLater(() -> listener.onDisconnected(reason));
                }

                @Override
                public void onError(Exception ex) {
                    LOG.warn("Collaboration WebSocket error", ex);
                    if (listener != null) Platform.runLater(() -> listener.onError(ex.getMessage()));
                }
            };
            wsClient.connectBlocking();
        } catch (Exception e) {
            LOG.error("Connect to server failed", e);
            if (listener != null) Platform.runLater(() -> listener.onError(e.getMessage()));
        }
    }

    private void handleMessage(String raw) {
        try {
            SyncMessage msg = MAPPER.readValue(raw, SyncMessage.class);
            if (msg == null || msg.getType() == null) return;

            switch (msg.getType()) {
                case INIT:
                    myClientId = msg.getClientId();
                    myRole = msg.getRole();
                    // AUTH sera envoyé dans onOpen, attendre le ACK pour confirmer
                    break;
                case ACK:
                    if (msg.getRole() != null) myRole = msg.getRole();
                    if (msg.getUsername() != null) myUsername = msg.getUsername();
                    connected.set(true);
                    final String role = myRole;
                    final String cid = myClientId;
                    if (listener != null) Platform.runLater(() -> listener.onConnected(cid, role));
                    break;
                case CLIENT_LIST:
                    List<SyncMessage.ClientInfo> clients = msg.getClients() != null
                            ? msg.getClients() : new ArrayList<>();
                    if (listener != null) Platform.runLater(() -> listener.onClientListUpdated(clients));
                    break;
                case KICKED:
                    String kickReason = msg.getReason() != null ? msg.getReason() : "Kicked";
                    if (listener != null) Platform.runLater(() -> listener.onKicked(kickReason));
                    break;
                case SERVER_STOPPING:
                    String stopReason = msg.getReason() != null ? msg.getReason() : "Server stopping";
                    if (listener != null) Platform.runLater(() -> listener.onServerStopping(stopReason));
                    break;
                case ERROR:
                    String errMsg = msg.getErrorMessage();
                    if (listener != null) Platform.runLater(() -> listener.onError(errMsg));
                    break;
                default:
                    // Ignorer les autres types (CRDT ops, etc.) – gérés par SyncClientService
                    break;
            }
        } catch (Exception e) {
            LOG.trace("Parse collaboration message failed: {}", e.getMessage());
        }
    }

    /**
     * Envoie une commande ADMIN_KICK au serveur.
     */
    public void kickClient(String targetClientId, String reason) {
        if (!"ADMIN".equals(myRole)) {
            LOG.warn("Only admin can kick");
            return;
        }
        SyncMessage msg = new SyncMessage(SyncMessage.MessageType.ADMIN_KICK, myClientId, null, "");
        msg.setTargetClientId(targetClientId);
        msg.setReason(reason != null ? reason : "Kicked by admin");
        sendJson(msg);
    }

    /**
     * Envoie une commande SERVER_SHUTDOWN au serveur.
     */
    public void shutdownServer() {
        if (!"ADMIN".equals(myRole)) {
            LOG.warn("Only admin can shutdown");
            return;
        }
        SyncMessage msg = new SyncMessage(SyncMessage.MessageType.SERVER_SHUTDOWN, myClientId, null, "");
        sendJson(msg);
    }

    /**
     * Déconnexion complète : ferme le client WS et arrête le serveur embarqué si hébergeur.
     */
    public void disconnect() {
        connected.set(false);
        WebSocketClient c = wsClient;
        wsClient = null;
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                LOG.warn("Error closing collaboration client", e);
            }
        }
        SyncWebSocketServer srv = embeddedServer;
        embeddedServer = null;
        if (hosting && srv != null) {
            try {
                srv.stop();
            } catch (Exception e) {
                LOG.warn("Error stopping embedded server", e);
            }
            hosting = false;
        }
        serverToken = null;
        myClientId = null;
        myRole = null;
    }

    private void sendJson(SyncMessage msg) {
        if (wsClient == null || !wsClient.isOpen()) return;
        try {
            wsClient.send(MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            LOG.warn("Send collaboration message failed", e);
        }
    }

    // Getters
    public boolean isHosting() { return hosting; }
    public boolean isConnected() { return connected.get(); }
    public boolean isAdmin() { return "ADMIN".equals(myRole); }
    public String getServerToken() { return serverToken; }
    public String getMyClientId() { return myClientId; }
    public String getMyRole() { return myRole; }
    public String getMyUsername() { return myUsername; }
    public SyncWebSocketServer getEmbeddedServer() { return embeddedServer; }
}
