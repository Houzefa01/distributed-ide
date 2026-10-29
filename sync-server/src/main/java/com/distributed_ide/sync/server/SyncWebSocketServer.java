package com.distributed_ide.sync.server;

import com.distributed_ide.crdt.engine.CRDTEngine;
import com.distributed_ide.crdt.model.CRDTDocument;
import com.distributed_ide.crdt.model.CRDTIdentifier;
import com.distributed_ide.crdt.model.CRDTOperation;
import com.distributed_ide.sync.model.SyncMessage;
import com.distributed_ide.sync.model.ClientSession;
import com.distributed_ide.sync_server.websocket.WebSocketHeartbeatManager;
import com.distributed_ide.common.ObjectMapperFactory;
import com.distributed_ide.common.SSLContextFactory;
import com.distributed_ide.common.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Serveur WebSocket pour la synchronisation temps réel des opérations CRDT.
 * Gère les connexions clients, les opérations, et la distribution des changements.
 */
public class SyncWebSocketServer extends WebSocketServer {
    private static final Logger LOG = LoggerFactory.getLogger(SyncWebSocketServer.class);

    private static final int MAX_MESSAGE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final int MAX_DOC_ID_LENGTH = 1024;
    private static final int MAX_OP_CONTENT_LENGTH = 100_000;
    private static final int RATE_LIMIT_PER_SECOND = 200;

    private final CRDTEngine engine;
    private final Map<WebSocket, ClientSession> connectedClients;
    private final Map<String, Set<WebSocket>> documentSubscribers;
    private final OperationBatcher batcher;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService flushScheduler;
    private final Map<String, ScheduledFuture<?>> flushTasks;
    private final Map<String, ClientSession> lastWriterByDocument;
    private final long HEARTBEAT_INTERVAL_MS = 30_000;
    private final long HEARTBEAT_TIMEOUT_MS = 60_000;
    private final WebSocketHeartbeatManager heartbeatManager;

    // Network stats
    private final java.util.concurrent.atomic.AtomicLong totalMessagesReceived = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong totalMessagesSent = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong totalOperationsProcessed = new java.util.concurrent.atomic.AtomicLong();
    private final ScheduledExecutorService heartbeatScheduler;
    private final ScheduledExecutorService clientListScheduler;

    private final String serverToken;
    private volatile String adminClientId;
    private final long CLIENT_LIST_INTERVAL_MS = 5_000;
    private volatile boolean shuttingDown = false;
    // FIX: Synchronize access to lastWriterByDocument for thread safety
    private final Object lastWriterLock = new Object();

    public SyncWebSocketServer(int port, String nodeId) {
        this(port, nodeId, null);
    }

    public SyncWebSocketServer(int port, String nodeId, String serverToken) {
        super(new InetSocketAddress(port));
        
        // Initialize TLS 1.3 encryption context for secure WebSocket connections
        try {
            String keystorePath = System.getProperty("keystore.path", "./keystore.p12");
            String keystorePassword = System.getProperty("keystore.password", "distributed-ide-default");
            SSLContext sslContext = SSLContextFactory.createSSLContext(keystorePath, keystorePassword);
            this.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
            LOG.info("✅ TLS 1.3 initialized for SyncWebSocketServer");
        } catch (Exception e) {
            LOG.warn("⚠️ TLS initialization failed: {}. Server will run unencrypted.", e.getMessage());
            // Continue without TLS rather than failing startup
        }
        
        this.engine = new CRDTEngine(nodeId);
        this.connectedClients = new ConcurrentHashMap<>();
        this.documentSubscribers = new ConcurrentHashMap<>();
        this.batcher = new OperationBatcher(10, 500);
        this.objectMapper = ObjectMapperFactory.getMapper();  // OPTIMIZATION: Use pooled ObjectMapper
        this.flushScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "SyncFlushScheduler");
            t.setDaemon(true);
            return t;
        });
        this.flushTasks = new ConcurrentHashMap<>();
        this.lastWriterByDocument = new ConcurrentHashMap<>();
        this.serverToken = serverToken;
        
        // Initialize WebSocket heartbeat manager
        this.heartbeatManager = new WebSocketHeartbeatManager();
        
        // Set callback for stale client cleanup
        heartbeatManager.setOnClientStaleCallback(staleClientId -> {
            LOG.warn("⚠️ Stale client detected: {}", staleClientId);
            // Find and close WebSocket connection for this stale client
            for (Map.Entry<WebSocket, ClientSession> entry : connectedClients.entrySet()) {
                if (entry.getValue().getClientId().equals(staleClientId)) {
                    WebSocket staleConn = entry.getKey();
                    LOG.info("🔌 Closing stale connection: {}", staleClientId);
                    staleConn.close(4000, "Heartbeat timeout - connection stale");
                    break;
                }
            }
        });
        
        // Start heartbeat monitoring
        heartbeatManager.start();

        setConnectionLostTimeout(0);
        this.heartbeatScheduler = startHeartbeatMonitor();
        this.clientListScheduler = startClientListBroadcaster();
        LOG.info("SyncWebSocketServer initialized on port {} (token={})", port, serverToken != null ? "set" : "none");
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String clientId = "client-" + UUID.randomUUID().toString().substring(0, 8);
        String nodeId = "node-" + UUID.randomUUID().toString().substring(0, 8);
        ClientSession session = new ClientSession(clientId, nodeId);
        connectedClients.put(conn, session);
        
        // Register client with heartbeat manager
        heartbeatManager.registerClient(clientId);

        LOG.info("Client connected: {} from {}", clientId, conn.getRemoteSocketAddress());

        // FIX: Only set authenticated=true if NO token is required (backward compat)
        //      If token is required, require explicit AUTH message first
        if (serverToken == null || serverToken.isEmpty()) {
            session.setAuthenticated(true);
            if (adminClientId == null) {
                adminClientId = clientId;
                session.setRole("ADMIN");
            }
        }

        SyncMessage initMsg = new SyncMessage(SyncMessage.MessageType.INIT, clientId, null, nodeId);
        initMsg.setRole(session.getRole());
        // FIX: Indicate if authentication is required
        if (serverToken != null && !serverToken.isEmpty()) {
            initMsg.setErrorMessage("Authentication required: send AUTH message");
        }
        sendMessage(conn, initMsg);

        LOG.info("[NET] Client connected: id={} addr={} clients={} (auth_required={})",
                clientId, conn.getRemoteSocketAddress(), connectedClients.size(), 
                serverToken != null && !serverToken.isEmpty());
        broadcastClientList();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        ClientSession session = connectedClients.remove(conn);
        if (session != null) {
            // Unregister from heartbeat manager
            heartbeatManager.unregisterClient(session.getClientId());
            
            for (String docId : session.getSubscribedDocuments()) {
                documentSubscribers.computeIfPresent(docId, (k, v) -> {
                    v.remove(conn);
                    return v.isEmpty() ? null : v;
                });
            }
            LOG.info("[NET] Client disconnected: id={} reason='{}' code={} remote={} clients={}",
                    session.getClientId(), reason, code, remote, connectedClients.size());
            if (session.getClientId().equals(adminClientId)) {
                adminClientId = null;
            }
            broadcastClientList();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        long startTime = System.currentTimeMillis();
        
        try {
            totalMessagesReceived.incrementAndGet();

            // --- Input validation: message size ---
            if (message == null || message.length() > MAX_MESSAGE_SIZE) {
                LOG.warn("Message rejected: size={} from {}",
                        message != null ? message.length() : 0, conn.getRemoteSocketAddress());
                sendError(conn, "Message too large");
                return;
            }

            SyncMessage msg = objectMapper.readValue(message, SyncMessage.class);
            ClientSession session = connectedClients.get(conn);
            
            if (session == null) {
                LOG.warn("Message from unknown client: {}", conn.getRemoteSocketAddress());
                return;
            }

            // --- Rate limiting ---
            if (!session.checkRateLimit(RATE_LIMIT_PER_SECOND)) {
                LOG.warn("Rate limit exceeded for client {}", session.getClientId());
                sendError(conn, "Rate limit exceeded");
                return;
            }

            session.updateHeartbeat();

            // --- Validate document ID ---
            if (msg.getDocumentId() != null && !isValidDocumentId(msg.getDocumentId())) {
                sendError(conn, "Invalid document ID");
                return;
            }

            // --- Validate operations ---
            if (msg.getOperations() != null) {
                if (msg.getOperations().size() > MAX_OP_CONTENT_LENGTH) {
                    sendError(conn, "Too many operations in single message");
                    return;
                }
                for (SyncMessage.OperationData op : msg.getOperations()) {
                    String opType = op.getOperationType();
                    if (opType != null && !opType.equals("INSERT") && !opType.equals("DELETE")) {
                        sendError(conn, "Invalid operation type: " + opType);
                        return;
                    }
                }
            }

            if (msg.getType() == SyncMessage.MessageType.AUTH) {
                handleAuth(conn, session, msg);
                return;
            }

            if (serverToken != null && !serverToken.isEmpty() && !session.isAuthenticated()) {
                sendError(conn, "Authentication required. Send AUTH with token first.");
                return;
            }

            switch (msg.getType()) {
                case DOCUMENT_JOIN:
                    handleDocumentJoin(conn, session, msg);
                    break;
                case DOCUMENT_LEAVE:
                    handleDocumentLeave(session, msg);
                    break;
                case OPERATION:
                    handleOperation(conn, session, msg);
                    break;
                case BATCH_OPERATIONS:
                    handleBatchOperations(conn, session, msg);
                    break;
                case SYNC_REQUEST:
                    handleSyncRequest(conn, session, msg);
                    break;
                case HEARTBEAT:
                    handleHeartbeat(conn);
                    break;
                case CURSOR_UPDATE:
                case SELECTION_UPDATE:
                    broadcastToDocumentExcept(msg.getDocumentId(), conn, session, msg);
                    break;
                case CHAT:
                    broadcastChatToAll(conn, session, msg);
                    break;
                case ADMIN_KICK:
                    handleAdminKick(conn, session, msg);
                    break;
                case SERVER_SHUTDOWN:
                    handleServerShutdown(conn, session);
                    break;
                default:
                    LOG.warn("Unknown message type: {}", msg.getType());
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            if (processingTime > 100) {
                LOG.debug("Slow message processing: {}ms", processingTime);
            }
        } catch (Exception e) {
            LOG.error("Error processing message", e);
            sendError(conn, "Failed to process message: " + e.getMessage());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String addr = conn != null && conn.getRemoteSocketAddress() != null
                ? conn.getRemoteSocketAddress().toString() : "unknown";
        LOG.error("WebSocket error from {}: {}", addr, ex.getMessage());
    }

    @Override
    public void onStart() {
        LOG.info("WebSocket server started on port {}", getPort());
    }

    // ═══════════════════════════════════════════════════════════
    //  INPUT VALIDATION
    // ═══════════════════════════════════════════════════════════

    private static boolean isValidDocumentId(String docId) {
        if (docId.length() > MAX_DOC_ID_LENGTH) return false;
        // Prevent path traversal
        if (docId.contains("..") || docId.contains("\0")) return false;
        // Only allow safe characters
        return docId.matches("^[a-zA-Z0-9_.\\-/: ]+$");
    }

    /**
     * Gère la souscription à un document.
     */
    private void handleDocumentJoin(WebSocket conn, ClientSession session, SyncMessage msg) {
        String docId = msg.getDocumentId();

        // Ensure the document exists as soon as a client joins.
        // This avoids SYNC_REQUEST responses with null history for empty docs.
        if (docId != null && engine.getDocument(docId) == null) {
            engine.createDocument(docId);
        }

        session.subscribeToDocument(docId);
        documentSubscribers.computeIfAbsent(docId, k -> ConcurrentHashMap.newKeySet()).add(conn);

        LOG.debug("Client {} joined document {}", session.getClientId(), docId);

        // Envoyer ACK
        SyncMessage ack = new SyncMessage(SyncMessage.MessageType.ACK, 
                session.getClientId(), docId, session.getNodeId());
        sendMessage(conn, ack);
    }

    /**
     * Gère l'arrêt de la souscription.
     */
    private void handleDocumentLeave(ClientSession session, SyncMessage msg) {
        String docId = msg.getDocumentId();
        session.unsubscribeFromDocument(docId);
        LOG.debug("Client {} left document {}", session.getClientId(), docId);
    }

    /**
     * Gère une opération unique.
     */
    private void handleOperation(WebSocket conn, ClientSession session, SyncMessage msg) {
        if (msg.getOperations() == null || msg.getOperations().isEmpty()) {
            return;
        }
        totalOperationsProcessed.addAndGet(msg.getOperations().size());

        for (SyncMessage.OperationData opData : msg.getOperations()) {
            applyToEngine(msg.getDocumentId(), List.of(opData));
            boolean shouldFlush = batcher.addOperation(msg.getDocumentId(), opData);
            
            // FIX: Synchronize access to prevent race conditions on concurrent writes
            synchronized (lastWriterLock) {
                lastWriterByDocument.put(msg.getDocumentId(), session);
            }
            
            scheduleFlush(msg.getDocumentId());
            if (shouldFlush) {
                cancelScheduledFlush(msg.getDocumentId());
                flushAndBroadcast(session, msg.getDocumentId());
            }
        }
    }

    private void scheduleFlush(String docId) {
        if (docId == null || docId.isEmpty()) return;

        // Reset timer on each operation so we flush after inactivity.
        // FIX: Properly cancel the previous task to prevent memory leak
        ScheduledFuture<?> previousTask = flushTasks.remove(docId);
        if (previousTask != null) {
            previousTask.cancel(false);
        }
        
        flushTasks.put(docId, flushScheduler.schedule(() -> {
            try {
                // FIX: Synchronize to read lastWriter safely
                ClientSession last;
                synchronized (lastWriterLock) {
                    last = lastWriterByDocument.get(docId);
                }
                if (last != null) {
                    flushAndBroadcast(last, docId);
                }
            } catch (Exception e) {
                LOG.warn("Scheduled flush failed for {}", docId, e);
            }
        }, 500, TimeUnit.MILLISECONDS));
    }

    private void cancelScheduledFlush(String docId) {
        ScheduledFuture<?> f = flushTasks.remove(docId);
        if (f != null) f.cancel(false);
    }

    /**
     * Gère un batch d'opérations.
     */
    private void handleBatchOperations(WebSocket conn, ClientSession session, SyncMessage msg) {
        String docId = msg.getDocumentId();
        
        applyToEngine(docId, msg.getOperations());

        // Diffuser aux autres clients
        broadcastToSubscribers(conn, session, msg);

        // Envoyer ACK
        SyncMessage ack = new SyncMessage(SyncMessage.MessageType.ACK,
                session.getClientId(), docId, session.getNodeId());
        ack.setAcknowledgedTimestamp(msg.getTimestamp());
        sendMessage(conn, ack);
    }

    /**
     * Vide le batch et diffuse.
     */
    private void flushAndBroadcast(ClientSession session, String docId) {
        List<SyncMessage.OperationData> batch = batcher.flushBatch(docId);
        if (!batch.isEmpty()) {
            SyncMessage msg = new SyncMessage(SyncMessage.MessageType.BATCH_OPERATIONS,
                    session.getClientId(), docId, session.getNodeId());
            msg.setOperations(batch);
            
            broadcastToDocument(docId, msg);
        }
    }

    private void applyToEngine(String docId, List<SyncMessage.OperationData> operations) {
        if (docId == null || operations == null || operations.isEmpty()) return;
        if (engine.getDocument(docId) == null) {
            engine.createDocument(docId);
        }
        for (SyncMessage.OperationData op : operations) {
            CRDTOperation crdt = toCrdtOperation(op);
            if (crdt != null) {
                engine.applyRemoteOperation(docId, crdt);
            }
        }
    }

    private static CRDTOperation toCrdtOperation(SyncMessage.OperationData op) {
        if (op == null || op.getOperationType() == null) return null;
        CRDTIdentifier id;
        try {
            long ts = Long.parseLong(op.getIdTimestamp());
            String nodeId = op.getIdNodeId() != null ? op.getIdNodeId() : "";
            int dis = op.getIdDisambiguator();
            id = new CRDTIdentifier(ts, nodeId, dis);
        } catch (Exception e) {
            return null;
        }
        long ts = op.getTimestamp();
        if ("INSERT".equals(op.getOperationType())) {
            return CRDTOperation.insert(id, op.getContent(), null, null, ts);
        }
        if ("DELETE".equals(op.getOperationType())) {
            return CRDTOperation.delete(id, ts);
        }
        return null;
    }

    /**
     * Gère la demande de synchronisation.
     */
    private void handleSyncRequest(WebSocket conn, ClientSession session, SyncMessage msg) {
        // Récupérer l'historique complet depuis le serveur local
        SyncMessage response = new SyncMessage(SyncMessage.MessageType.SYNC_RESPONSE,
                session.getClientId(), msg.getDocumentId(), session.getNodeId());
        response.setServerTimestamp(System.currentTimeMillis());
        CRDTDocument doc = engine.getDocument(msg.getDocumentId());
        if (doc != null) {
            List<SyncMessage.OperationData> history = new ArrayList<>();
            for (CRDTOperation op : doc.getOperationHistory()) {
                history.add(new SyncMessage.OperationData(op));
            }
            response.setFullHistory(history);
        }
        
        sendMessage(conn, response);
    }

    /**
     * Gère le heartbeat (ping) et enregistre l'ACK du client.
     * Appelé quand le client reçoit un ping et envoie un pong.
     */
    private void handleHeartbeat(WebSocket conn) {
        ClientSession session = connectedClients.get(conn);
        if (session != null) {
            // Record heartbeat ACK from client
            heartbeatManager.recordHeartbeatAck(session.getClientId());
            LOG.trace("💓 Heartbeat ACK recorded from client: {}", session.getClientId());
        }
        
        // Send pong response
        SyncMessage pong = new SyncMessage(SyncMessage.MessageType.HEARTBEAT, "", "", "");
        sendMessage(conn, pong);
    }

    /**
     * Diffuse un message à tous les abonnés d'un document sauf l'émetteur.
     * FIX: Add null check to prevent NPE if subscribers set is empty/null
     */
    private void broadcastToSubscribers(WebSocket sender, ClientSession session, SyncMessage msg) {
        Set<WebSocket> subscribers = documentSubscribers.get(msg.getDocumentId());
        if (subscribers == null || subscribers.isEmpty()) {
            return; // FIX: Return early to prevent NPE
        }
        for (WebSocket ws : subscribers) {
            if (ws != sender && ws.isOpen()) {
                sendMessage(ws, msg);
            }
        }
    }

    /**
     * Diffuse un message à tous les abonnés d'un document.
     * FIX: Add null check to prevent NPE
     */
    private void broadcastToDocument(String docId, SyncMessage msg) {
        Set<WebSocket> subscribers = documentSubscribers.get(docId);
        if (subscribers == null || subscribers.isEmpty()) {
            return; // FIX: Return early to prevent NPE
        }
        for (WebSocket ws : subscribers) {
            if (ws.isOpen()) {
                sendMessage(ws, msg);
            }
        }
    }

    /**
     * Diffuse un message à tous les abonnés d'un document sauf l'émetteur (par docId explicite).
     * FIX: Add null check to prevent NPE if subscribers set is empty/null
     */
    private void broadcastToDocumentExcept(String docId, WebSocket sender, ClientSession session, SyncMessage msg) {
        if (docId == null) return;
        msg.setClientId(session.getClientId());
        msg.setUsername(session.getUsername());
        Set<WebSocket> subscribers = documentSubscribers.get(docId);
        if (subscribers == null || subscribers.isEmpty()) {
            return; // FIX: Return early to prevent NPE
        }
        for (WebSocket ws : subscribers) {
            if (ws != sender && ws.isOpen()) {
                sendMessage(ws, msg);
            }
        }
    }

    /**
     * Diffuse un message chat à tous les clients connectés, en injectant le username de l'émetteur.
     */
    private void broadcastChatToAll(WebSocket sender, ClientSession session, SyncMessage msg) {
        msg.setClientId(session.getClientId());
        msg.setUsername(session.getUsername());
        for (Map.Entry<WebSocket, ClientSession> entry : connectedClients.entrySet()) {
            if (entry.getKey().isOpen()) {
                sendMessage(entry.getKey(), msg);
            }
        }
    }

    /**
     * Envoie un message JSON au client après sérialisation.
     */
    private void sendMessage(WebSocket conn, SyncMessage msg) {
        if (conn == null || !conn.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(msg);
            conn.send(json);
            totalMessagesSent.incrementAndGet();
        } catch (Exception e) {
            LOG.error("Error sending message", e);
        }
    }

    public String getNetworkStats() {
        return String.format("clients=%d msgs_in=%d msgs_out=%d ops=%d docs=%d",
                connectedClients.size(),
                totalMessagesReceived.get(),
                totalMessagesSent.get(),
                totalOperationsProcessed.get(),
                documentSubscribers.size());
    }

    /**
     * Envoie une erreur au client.
     */
    private void sendError(WebSocket conn, String message) {
        try {
            SyncMessage error = new SyncMessage(SyncMessage.MessageType.ERROR, "", "", "");
            error.setErrorMessage(message);
            sendMessage(conn, error);
        } catch (Exception e) {
            LOG.error("Error sending error message", e);
        }
    }

    /**
     * Démarre le monitoring du heartbeat pour détecter les clients déconnectés.
     */
    private ScheduledExecutorService startHeartbeatMonitor() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "HeartbeatMonitor");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            for (Map.Entry<WebSocket, ClientSession> entry : connectedClients.entrySet()) {
                WebSocket ws = entry.getKey();
                ClientSession session = entry.getValue();

                if (!session.isAlive(HEARTBEAT_TIMEOUT_MS)) {
                    LOG.warn("Client {} timeout, closing connection", session.getClientId());
                    ws.close();
                } else {
                    // Envoyer heartbeat
                    SyncMessage ping = new SyncMessage(SyncMessage.MessageType.HEARTBEAT, "", "", "");
                    sendMessage(ws, ping);
                }
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        return scheduler;
    }

    /**
     * Arrête le serveur et libère les ressources.
     * OPTIMISATION: Cleanup complet des ressources pour éviter les fuites
     */
    @Override
    public void stop() {
        LOG.info("🛑 Stopping SyncWebSocketServer (clients={}, docs={})", 
                connectedClients.size(), documentSubscribers.size());
        
        // Stop heartbeat manager first
        heartbeatManager.shutdown();
        
        // Shutdown CRDT engine (cleanup all documents and operations)
        engine.shutdown();
        
        // Stop batchers and schedulers
        try {
            batcher.shutdown();
        } catch (Exception e) {
            LOG.warn("Error shutting down batcher", e);
        }
        
        // Cancel all pending flush tasks
        for (ScheduledFuture<?> task : flushTasks.values()) {
            try {
                task.cancel(true);
            } catch (Exception e) {
                LOG.debug("Error cancelling flush task", e);
            }
        }
        flushTasks.clear();
        
        // Shutdown schedulers
        try {
            flushScheduler.shutdownNow();
            if (!flushScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("Flush scheduler did not terminate in time");
            }
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while shutting down flush scheduler", e);
            Thread.currentThread().interrupt();
        }
        
        try {
            heartbeatScheduler.shutdownNow();
            if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("Heartbeat scheduler did not terminate in time");
            }
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while shutting down heartbeat scheduler", e);
            Thread.currentThread().interrupt();
        }
        
        try {
            clientListScheduler.shutdownNow();
            if (!clientListScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("Client list scheduler did not terminate in time");
            }
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while shutting down client list scheduler", e);
            Thread.currentThread().interrupt();
        }
        
        // Clear connections
        connectedClients.clear();
        documentSubscribers.clear();
        lastWriterByDocument.clear();
        
        // Stop WebSocket server
        try {
            super.stop();
            LOG.info("✅ WebSocket server stopped successfully");
        } catch (InterruptedException e) {
            LOG.error("Error stopping WebSocket server", e);
            Thread.currentThread().interrupt();
        }
    }

    public int getConnectedClientCount() {
        return connectedClients.size();
    }

    public CRDTEngine getEngine() {
        return engine;
    }

    public String getServerToken() {
        return serverToken;
    }

    public String getAdminClientId() {
        return adminClientId;
    }

    private void handleAuth(WebSocket conn, ClientSession session, SyncMessage msg) {
        String token = msg.getToken();
        String username = msg.getUsername();

        // Use JWT token provider for authentication if serverToken is configured
        if (serverToken != null && !serverToken.isEmpty()) {
            try {
                JwtTokenProvider jwtProvider = new JwtTokenProvider(serverToken, "distributed-ide");
                
                if (token == null || token.isEmpty()) {
                    sendError(conn, "Token required for authentication");
                    conn.close(4001, "Token required");
                    return;
                }
                
                // Validate JWT token with HS256 signature
                Claims claims = jwtProvider.validateAndGetClaims(token);
                if (claims == null) {
                    sendError(conn, "Invalid or expired token");
                    conn.close(4001, "Invalid token");
                    return;
                }
                
                // Extract userId and role from JWT claims
                String userId = jwtProvider.getUserId(token);
                String role = jwtProvider.getRole(token);
                
                session.setAuthenticated(true);
                if (username != null && !username.isEmpty()) {
                    session.setUsername(username);
                }
                if (role != null) {
                    session.setRole(role);
                }

                if (adminClientId == null && "ADMIN".equals(role)) {
                    adminClientId = session.getClientId();
                    session.setRole("ADMIN");
                }

                SyncMessage ack = new SyncMessage(SyncMessage.MessageType.ACK, session.getClientId(), null, session.getNodeId());
                ack.setRole(session.getRole());
                ack.setUsername(session.getUsername());
                sendMessage(conn, ack);

                LOG.info("✅ Client authenticated via JWT: {} (user={}, role={})", 
                        session.getClientId(), userId, role);
                broadcastClientList();
            } catch (Exception e) {
                LOG.error("❌ JWT validation failed: {}", e.getMessage());
                sendError(conn, "Authentication failed: " + e.getMessage());
                conn.close(4001, "Auth failed");
            }
        } else {
            // Legacy mode: simple token validation (backward compatibility)
            session.setAuthenticated(true);
            if (username != null && !username.isEmpty()) {
                session.setUsername(username);
            }

            if (adminClientId == null) {
                adminClientId = session.getClientId();
                session.setRole("ADMIN");
            }

            SyncMessage ack = new SyncMessage(SyncMessage.MessageType.ACK, session.getClientId(), null, session.getNodeId());
            ack.setRole(session.getRole());
            ack.setUsername(session.getUsername());
            sendMessage(conn, ack);

            LOG.info("Client authenticated (legacy mode): {} (user={}, role={})", 
                    session.getClientId(), session.getUsername(), session.getRole());
            broadcastClientList();
        }
    }

    private void handleAdminKick(WebSocket conn, ClientSession session, SyncMessage msg) {
        if (!session.isAdmin()) {
            sendError(conn, "Only admin can kick clients");
            return;
        }
        String targetId = msg.getTargetClientId();
        if (targetId == null || targetId.equals(session.getClientId())) {
            sendError(conn, "Invalid kick target");
            return;
        }
        for (Map.Entry<WebSocket, ClientSession> entry : connectedClients.entrySet()) {
            if (entry.getValue().getClientId().equals(targetId)) {
                SyncMessage kicked = new SyncMessage(SyncMessage.MessageType.KICKED, targetId, null, "");
                kicked.setReason(msg.getReason() != null ? msg.getReason() : "Kicked by admin");
                sendMessage(entry.getKey(), kicked);
                entry.getKey().close(4002, "Kicked by admin");
                LOG.info("Admin {} kicked client {}", session.getClientId(), targetId);
                return;
            }
        }
        sendError(conn, "Client not found: " + targetId);
    }

    private void handleServerShutdown(WebSocket conn, ClientSession session) {
        if (!session.isAdmin()) {
            sendError(conn, "Only admin can shutdown server");
            return;
        }
        LOG.info("Admin {} initiated server shutdown", session.getClientId());
        shuttingDown = true;
        SyncMessage stopping = new SyncMessage(SyncMessage.MessageType.SERVER_STOPPING, "", null, "");
        stopping.setReason("Server shutdown by admin");
        for (Map.Entry<WebSocket, ClientSession> entry : connectedClients.entrySet()) {
            if (!entry.getValue().getClientId().equals(session.getClientId())) {
                sendMessage(entry.getKey(), stopping);
                entry.getKey().close(4003, "Server shutdown");
            }
        }
        sendMessage(conn, stopping);
        try {
            stop();
        } catch (Exception e) {
            LOG.error("Error during admin shutdown", e);
        }
    }

    private void broadcastClientList() {
        List<SyncMessage.ClientInfo> clientInfos = new ArrayList<>();
        for (ClientSession cs : connectedClients.values()) {
            if (!cs.isAuthenticated() && serverToken != null && !serverToken.isEmpty()) continue;
            clientInfos.add(new SyncMessage.ClientInfo(
                    cs.getClientId(), cs.getUsername(), cs.getRole(), cs.getConnectedAt()));
        }
        SyncMessage msg = new SyncMessage(SyncMessage.MessageType.CLIENT_LIST, "", null, "");
        msg.setClients(clientInfos);
        for (Map.Entry<WebSocket, ClientSession> entry : connectedClients.entrySet()) {
            if (entry.getKey().isOpen()) {
                sendMessage(entry.getKey(), msg);
            }
        }
    }

    private ScheduledExecutorService startClientListBroadcaster() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "ClientListBroadcaster");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!shuttingDown && !connectedClients.isEmpty()) {
                    broadcastClientList();
                }
            } catch (Exception e) {
                LOG.warn("Client list broadcast failed", e);
            }
        }, CLIENT_LIST_INTERVAL_MS, CLIENT_LIST_INTERVAL_MS, TimeUnit.MILLISECONDS);
        return scheduler;
    }
}
