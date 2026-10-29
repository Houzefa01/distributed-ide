package com.distributed_ide.lsp_bridge.sync;

import com.distributed_ide.sync.model.SyncMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Intégration avec le sync-server : envoie DOCUMENT_JOIN / DOCUMENT_LEAVE et reçoit
 * BATCH_OPERATIONS / OPERATION pour mettre à jour la réplique CRDT et notifier le LSP (didChange).
 */
public class SyncServerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(SyncServerAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String syncServerUrl;
    private final String nodeId = "lsp-" + UUID.randomUUID().toString().substring(0, 8);
    private final String clientId = "lsp-" + UUID.randomUUID().toString().substring(0, 8);
    private final AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>();
    private final ExecutorService asyncExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sync-adapter-async");
        t.setDaemon(true);
        return t;
    });

    private DocumentReplicaStore replicaStore;
    private DocumentContentForwarder contentForwarder;

    public SyncServerAdapter(String syncServerUrl) {
        this.syncServerUrl = syncServerUrl != null ? syncServerUrl.trim() : "";
    }

    /**
     * Configure la réplique CRDT et le forwarder pour envoyer les mises à jour au LSP.
     * À appeler avant connect() pour activer la réception des opérations.
     */
    public void setSyncToLspIntegration(DocumentReplicaStore replicaStore, DocumentContentForwarder contentForwarder) {
        this.replicaStore = replicaStore;
        this.contentForwarder = contentForwarder;
    }

    public boolean isEnabled() {
        return !syncServerUrl.isEmpty();
    }

    public void connect() {
        if (!isEnabled() || sessionRef.get() != null) return;
        try {
            SyncServerMessageHandler handler = new SyncServerMessageHandler(this::onSyncMessage);
            StandardWebSocketClient client = new StandardWebSocketClient();
            WebSocketSession session = client.doHandshake(handler, syncServerUrl).get();
            sessionRef.set(session);
            SyncMessage init = new SyncMessage(SyncMessage.MessageType.INIT, clientId, null, nodeId);
            session.sendMessage(new TextMessage(MAPPER.writeValueAsString(init)));
            LOG.info("Connected to sync-server");
        } catch (Exception e) {
            LOG.warn("Sync-server connect failed: {}", e.getMessage());
        }
    }

    /**
     * Reçoit les messages du sync-server. Traitement asynchrone pour ne pas bloquer le thread WebSocket.
     */
    private void onSyncMessage(SyncMessage msg) {
        if (msg.getType() != SyncMessage.MessageType.BATCH_OPERATIONS && msg.getType() != SyncMessage.MessageType.OPERATION) {
            return;
        }
        String documentId = msg.getDocumentId();
        List<SyncMessage.OperationData> ops = msg.getOperations();
        if (documentId == null || ops == null || ops.isEmpty()) return;
        if (replicaStore == null || contentForwarder == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                String fullText = replicaStore.applyOperationsAndGetText(documentId, ops);
                contentForwarder.onDocumentContentChanged(documentId, fullText);
            } catch (Exception e) {
                LOG.warn("Apply ops / forward failed for doc {}", documentId, e);
            }
        }, asyncExecutor);
    }

    public void documentOpened(String documentId) {
        if (!isEnabled() || documentId == null) return;
        WebSocketSession session = sessionRef.get();
        if (session == null || !session.isOpen()) {
            connect();
            session = sessionRef.get();
        }
        if (session != null && session.isOpen()) {
            try {
                SyncMessage msg = new SyncMessage(SyncMessage.MessageType.DOCUMENT_JOIN, clientId, documentId, nodeId);
                session.sendMessage(new TextMessage(MAPPER.writeValueAsString(msg)));
            } catch (IOException e) {
                LOG.warn("DOCUMENT_JOIN failed", e);
            }
        }
    }

    public void documentClosed(String documentId) {
        if (!isEnabled() || documentId == null) return;
        WebSocketSession session = sessionRef.get();
        if (session != null && session.isOpen()) {
            try {
                SyncMessage msg = new SyncMessage(SyncMessage.MessageType.DOCUMENT_LEAVE, clientId, documentId, nodeId);
                session.sendMessage(new TextMessage(MAPPER.writeValueAsString(msg)));
            } catch (IOException e) {
                LOG.warn("DOCUMENT_LEAVE failed", e);
            }
        }
    }

    public void disconnect() {
        WebSocketSession s = sessionRef.getAndSet(null);
        if (s != null && s.isOpen()) {
            try { s.close(); } catch (IOException e) { /* ignore */ }
        }
        asyncExecutor.shutdown();
    }
}
