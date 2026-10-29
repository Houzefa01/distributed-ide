package com.distributed_ide.javafx_client.service;

import com.distributed_ide.crdt.engine.CRDTEngine;
import com.distributed_ide.crdt.model.CRDTDocument;
import com.distributed_ide.crdt.model.CRDTIdentifier;
import com.distributed_ide.crdt.model.CRDTOperation;
import com.distributed_ide.javafx_client.util.SyncOperationConverter;
import com.distributed_ide.sync.model.SyncMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket client for the sync server: connection, DOCUMENT_JOIN/LEAVE, send/receive CRDT operations.
 * Maintains a local CRDT replica per document; notifies the listener on updates (async).
 */
public class SyncClientService {

    private static final Logger LOG = LoggerFactory.getLogger(SyncClientService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sync-client");
        t.setDaemon(true);
        return t;
    });

    private volatile WebSocketClient client;
    private volatile String clientId;
    private volatile String nodeId;
    private final CRDTEngine engine = new CRDTEngine("javafx-" + UUID.randomUUID().toString().substring(0, 8));
    private volatile Listener listener;
    private volatile String authToken;
    private volatile String authUsername;

    // Auto-reconnect
    private static final long RECONNECT_INITIAL_DELAY_MS = 1000;
    private static final long RECONNECT_MAX_DELAY_MS = 30_000;
    private static final int MAX_RECONNECT_ATTEMPTS = 20;
    private volatile String lastWsUri;
    private final AtomicBoolean autoReconnectEnabled = new AtomicBoolean(true);
    private final AtomicBoolean intentionalDisconnect = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);
    private final Set<String> subscribedDocuments = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sync-reconnect");
        t.setDaemon(true);
        return t;
    });
    private volatile ScheduledFuture<?> pendingReconnect;

    public interface Listener {
        void onConnected(String clientId, String nodeId);
        void onDocumentUpdated(String documentId, String fullText);
        void onCollaboratorJoined(String documentId, String clientId, String nodeId);
        void onRemoteCursorUpdate(String documentId, String clientId, String username, int line, int column, String color);
        void onRemoteSelectionUpdate(String documentId, String clientId, String username, int startLine, int startCol, int endLine, int endCol);
        void onChatMessage(String clientId, String username, String message, long timestamp);
        void onError(String message);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setAuth(String token, String username) {
        this.authToken = token;
        this.authUsername = username;
    }

    public void connect(String wsUri) {
        if (wsUri == null || wsUri.isEmpty()) return;
        this.lastWsUri = wsUri;
        intentionalDisconnect.set(false);
        reconnectAttempt.set(0);
        cancelPendingReconnect();
        doConnect(wsUri);
    }

    private void doConnect(String wsUri) {
        try {
            URI uri = new URI(wsUri);
            LOG.info("[NET] Connecting to sync server: {}", wsUri);
            client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    LOG.info("[NET] Sync WebSocket connected to {}", wsUri);
                    reconnectAttempt.set(0);
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    LOG.info("[NET] Sync WebSocket closed: code={} reason='{}' remote={}", code, reason, remote);
                    if (!intentionalDisconnect.get() && autoReconnectEnabled.get()) {
                        scheduleReconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    LOG.warn("[NET] Sync WebSocket error: {}", ex.getMessage());
                    if (listener != null) listener.onError(ex.getMessage());
                }
            };
            client.connect();
        } catch (Exception e) {
            LOG.error("[NET] Sync connect failed: {}", e.getMessage());
            if (listener != null) listener.onError(e.getMessage());
            if (!intentionalDisconnect.get() && autoReconnectEnabled.get()) {
                scheduleReconnect();
            }
        }
    }

    private void scheduleReconnect() {
        int attempt = reconnectAttempt.incrementAndGet();
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            LOG.warn("[NET] Max reconnect attempts ({}) reached, giving up", MAX_RECONNECT_ATTEMPTS);
            if (listener != null) listener.onError("Connection lost. Max reconnect attempts reached.");
            return;
        }
        long delay = Math.min(RECONNECT_INITIAL_DELAY_MS * (1L << Math.min(attempt - 1, 14)), RECONNECT_MAX_DELAY_MS);
        LOG.info("[NET] Scheduling reconnect attempt {}/{} in {}ms", attempt, MAX_RECONNECT_ATTEMPTS, delay);
        cancelPendingReconnect();
        pendingReconnect = reconnectScheduler.schedule(() -> {
            if (!intentionalDisconnect.get() && lastWsUri != null) {
                doConnect(lastWsUri);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelPendingReconnect() {
        ScheduledFuture<?> f = pendingReconnect;
        if (f != null) {
            f.cancel(false);
            pendingReconnect = null;
        }
    }

    public void setAutoReconnect(boolean enabled) {
        autoReconnectEnabled.set(enabled);
    }

    public void disconnect() {
        intentionalDisconnect.set(true);
        cancelPendingReconnect();
        subscribedDocuments.clear();
        WebSocketClient c = client;
        client = null;
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                LOG.warn("Error closing sync client", e);
            }
        }
        LOG.info("[NET] Sync client disconnected (intentional)");
    }

    public void shutdown() {
        disconnect();
        executor.shutdownNow();
        reconnectScheduler.shutdownNow();
    }

    public void joinDocument(String documentId) {
        subscribedDocuments.add(documentId);
        send(new SyncMessage(SyncMessage.MessageType.DOCUMENT_JOIN, clientId, documentId, nodeId));
        ensureDocument(documentId);
    }

    public void leaveDocument(String documentId) {
        subscribedDocuments.remove(documentId);
        send(new SyncMessage(SyncMessage.MessageType.DOCUMENT_LEAVE, clientId, documentId, nodeId));
    }

    /**
     * Sends a CRDT operation to the server (after local application).
     */
    public void sendOperation(String documentId, SyncMessage.OperationData opData) {
        SyncMessage msg = new SyncMessage(SyncMessage.MessageType.OPERATION, clientId, documentId, nodeId);
        msg.setOperations(List.of(opData));
        send(msg);
    }

    public void sendBatch(String documentId, List<SyncMessage.OperationData> operations) {
        if (operations == null || operations.isEmpty()) return;
        SyncMessage msg = new SyncMessage(SyncMessage.MessageType.BATCH_OPERATIONS, clientId, documentId, nodeId);
        msg.setOperations(operations);
        send(msg);
    }

    /**
     * Applies received operations to the local replica and returns the new text (caller side).
     * For UI updates, the onDocumentUpdated listener is called asynchronously.
     */
    public void applyRemoteOperations(String documentId, List<SyncMessage.OperationData> operations) {
        if (documentId == null || operations == null || operations.isEmpty()) return;
        if (executor.isShutdown()) return;
        executor.execute(() -> {
            try {
                ensureDocument(documentId);
                List<CRDTOperation> crdtOps = SyncOperationConverter.toCrdtOperations(operations);
                for (CRDTOperation op : crdtOps) {
                    engine.applyRemoteOperation(documentId, op);
                }
                CRDTDocument doc = engine.getDocument(documentId);
                String text = doc != null ? doc.getText() : "";
                if (listener != null) listener.onDocumentUpdated(documentId, text);
            } catch (Exception e) {
                LOG.warn("Apply remote ops failed", e);
            }
        });
    }

    /**
     * Inserts a character into the local document and returns the OperationData to send.
     */
    public SyncMessage.OperationData insertAndCreateOperation(String documentId, int index, char c) {
        ensureDocument(documentId);
        CRDTIdentifier id = engine.insertCharacter(documentId, index, c);
        CRDTDocument doc = engine.getDocument(documentId);
        if (doc == null || id == null) return null;
        try {
            CRDTOperation op = doc.getOperationHistory().stream()
                    .filter(o -> o.getId().equals(id)).findFirst().orElse(null);
            return op != null ? new SyncMessage.OperationData(op) : null;
        } catch (Exception e) {
            LOG.warn("insertAndCreateOperation lookup failed", e);
            return null;
        }
    }

    /**
     * Deletes a character and returns the OperationData to send.
     */
    public SyncMessage.OperationData deleteAndCreateOperation(String documentId, int index) {
        CRDTDocument doc = engine.getDocument(documentId);
        if (doc == null) return null;
        List<com.distributed_ide.crdt.model.CRDTElement> visible = doc.getVisibleElements();
        if (index < 0 || index >= visible.size()) return null;
        doc.delete(index);
        List<CRDTOperation> hist = doc.getOperationHistory();
        return hist.isEmpty() ? null : new SyncMessage.OperationData(hist.get(hist.size() - 1));
    }

    public String getDocumentText(String documentId) {
        CRDTDocument doc = engine.getDocument(documentId);
        return doc != null ? doc.getText() : "";
    }

    /** Replaces document content (e.g. file loading). Expensive for large files. */
    public void setDocumentText(String documentId, String text) {
        ensureDocument(documentId);
        CRDTDocument doc = engine.getDocument(documentId);
        if (doc == null) return;
        List<com.distributed_ide.crdt.model.CRDTElement> visible = new ArrayList<>(doc.getVisibleElements());
        for (com.distributed_ide.crdt.model.CRDTElement el : visible) {
            doc.deleteById(el.getId());
        }
        String s = text != null ? text : "";
        for (int i = 0; i < s.length(); i++) {
            engine.insertCharacter(documentId, i, s.charAt(i));
        }
    }

    private void ensureDocument(String documentId) {
        if (engine.getDocument(documentId) == null) {
            engine.createDocument(documentId);
        }
    }

    private void handleMessage(String message) {
        try {
            SyncMessage msg = MAPPER.readValue(message, SyncMessage.class);
            if (msg == null || msg.getType() == null) return;
            switch (msg.getType()) {
                case INIT:
                    clientId = msg.getClientId();
                    nodeId = msg.getNodeId();
                    if (authToken != null && !authToken.isEmpty()) {
                        SyncMessage auth = new SyncMessage(SyncMessage.MessageType.AUTH, clientId, null, nodeId);
                        auth.setToken(authToken);
                        auth.setUsername(authUsername != null ? authUsername : clientId);
                        send(auth);
                    }
                    if (listener != null) listener.onConnected(clientId, nodeId);
                    // Re-join documents after reconnect
                    for (String docId : subscribedDocuments) {
                        send(new SyncMessage(SyncMessage.MessageType.DOCUMENT_JOIN, clientId, docId, nodeId));
                    }
                    break;
                case ACK:
                case CLIENT_LIST:
                case KICKED:
                case SERVER_STOPPING:
                    break;
                case BATCH_OPERATIONS:
                case OPERATION:
                    applyRemoteOperations(msg.getDocumentId(), msg.getOperations());
                    break;
                case DOCUMENT_JOIN:
                    if (listener != null && msg.getClientId() != null && !msg.getClientId().equals(clientId)) {
                        listener.onCollaboratorJoined(msg.getDocumentId(), msg.getClientId(), msg.getNodeId());
                    }
                    break;
                case CURSOR_UPDATE:
                    if (listener != null && msg.getClientId() != null && !msg.getClientId().equals(clientId)) {
                        listener.onRemoteCursorUpdate(
                                msg.getDocumentId(), msg.getClientId(), msg.getUsername(),
                                msg.getCursorLine(), msg.getCursorColumn(), msg.getCursorColor());
                    }
                    break;
                case SELECTION_UPDATE:
                    if (listener != null && msg.getClientId() != null && !msg.getClientId().equals(clientId)) {
                        listener.onRemoteSelectionUpdate(
                                msg.getDocumentId(), msg.getClientId(), msg.getUsername(),
                                msg.getSelStartLine(), msg.getSelStartColumn(),
                                msg.getSelEndLine(), msg.getSelEndColumn());
                    }
                    break;
                case CHAT:
                    if (listener != null) {
                        listener.onChatMessage(
                                msg.getClientId(), msg.getUsername(),
                                msg.getChatMessage(), msg.getTimestamp());
                    }
                    break;
                case ERROR:
                    if (listener != null) listener.onError(msg.getErrorMessage());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            LOG.trace("Parse sync message failed: {}", e.getMessage());
        }
    }

    private void send(SyncMessage msg) {
        WebSocketClient c = client;
        if (c == null || !c.isOpen()) return;
        try {
            c.send(MAPPER.writeValueAsString(msg));
        } catch (Exception e) {
            LOG.warn("Send sync message failed", e);
        }
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    public String getClientId() { return clientId; }
    public String getNodeId() { return nodeId; }

    /**
     * Sends the local cursor position to other collaborators.
     */
    public void sendCursorUpdate(String documentId, int line, int column, String color) {
        SyncMessage msg = new SyncMessage(SyncMessage.MessageType.CURSOR_UPDATE, clientId, documentId, nodeId);
        msg.setCursorLine(line);
        msg.setCursorColumn(column);
        msg.setCursorColor(color);
        send(msg);
    }

    /**
     * Sends the local selection to other collaborators.
     */
    public void sendSelectionUpdate(String documentId, int startLine, int startCol, int endLine, int endCol) {
        SyncMessage msg = new SyncMessage(SyncMessage.MessageType.SELECTION_UPDATE, clientId, documentId, nodeId);
        msg.setSelStartLine(startLine);
        msg.setSelStartColumn(startCol);
        msg.setSelEndLine(endLine);
        msg.setSelEndColumn(endCol);
        send(msg);
    }

    /**
     * Sends a chat message.
     */
    public void sendChatMessage(String message) {
        SyncMessage msg = new SyncMessage(SyncMessage.MessageType.CHAT, clientId, null, nodeId);
        msg.setChatMessage(message);
        send(msg);
    }
}
