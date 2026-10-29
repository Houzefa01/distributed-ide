package com.distributed_ide.lsp_bridge.websocket;

import com.distributed_ide.lsp_bridge.bridge.DocumentToSessionRegistry;
import com.distributed_ide.lsp_bridge.bridge.LspBridgeSession;
import com.distributed_ide.lsp_bridge.bridge.LspSessionRegistry;
import com.distributed_ide.lsp_bridge.config.LspServerType;
import com.distributed_ide.lsp_bridge.sync.SyncServerAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class LspWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(LspWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    // FIX: Add message size limit to prevent memory exhaustion DoS attacks
    private static final int MAX_MESSAGE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final int MAX_JSON_DEPTH = 50; // Prevent deeply nested JSON DoS

    private final LspSessionRegistry sessionRegistry;
    private final DocumentToSessionRegistry documentToSessionRegistry;
    private final Optional<SyncServerAdapter> syncAdapter;
    private final Map<String, SessionInfo> sessionInfos = new ConcurrentHashMap<>();

    public LspWebSocketHandler(LspSessionRegistry sessionRegistry) {
        this(sessionRegistry, null, null);
    }

    public LspWebSocketHandler(LspSessionRegistry sessionRegistry,
                               DocumentToSessionRegistry documentToSessionRegistry,
                               SyncServerAdapter syncAdapter) {
        this.sessionRegistry = sessionRegistry;
        this.documentToSessionRegistry = documentToSessionRegistry != null ? documentToSessionRegistry : new DocumentToSessionRegistry();
        this.syncAdapter = Optional.ofNullable(syncAdapter);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionInfos.put(session.getId(), new SessionInfo(null, null, null));
        LOG.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String sessionId = session.getId();
        SessionInfo info = sessionInfos.get(sessionId);
        if (info == null) return;

        // FIX: Validate message size to prevent DoS attacks
        if (payload.length() > MAX_MESSAGE_SIZE) {
            LOG.warn("Message size limit exceeded: {} bytes > {} bytes for session {}", 
                payload.length(), MAX_MESSAGE_SIZE, sessionId);
            sendError(session, "Message size exceeds limit (" + MAX_MESSAGE_SIZE + " bytes)");
            return;
        }

        try {
            // FIX: Validate JSON depth to prevent deeply nested JSON DoS
            JsonNode root = parseJsonSafely(payload);
            if (root == null || !root.isObject()) {
                sendError(session, "Invalid JSON or JSON too deeply nested");
                return;
            }
            
            JsonNode method = root.get("method");
            JsonNode params = root.get("params");

            if (method != null && "init".equals(method.asText()) && params != null) {
                String language = params.has("language") ? params.get("language").asText() : "java";
                String rootUri = params.has("rootUri") ? params.get("rootUri").asText() : null;
                LspServerType type = parseLanguage(language);
                LspBridgeSession bridge = sessionRegistry.getOrCreate(type, rootUri);
                String sessionKey = LspSessionRegistry.sessionKeyFor(type, rootUri);
                bridge.addClient(sessionId, new BridgeSessionClient(session));
                sessionInfos.put(sessionId, new SessionInfo(sessionKey, type, rootUri));
                LOG.info("Client {} init: language={}", sessionId, language);
                return;
            }

            if (info.sessionKey == null) {
                sendError(session, "Send init first: {\"method\":\"init\",\"params\":{\"language\":\"java|python|typescript\",\"rootUri\":\"file:///...\"}}");
                return;
            }

            registerDocumentOpenClose(root, info);

            notifySyncDocumentIfDidOpen(root, info);

            LspBridgeSession bridge = sessionRegistry.getOrCreate(info.type, info.rootUri);
            bridge.forwardFromClient(sessionId, payload);
        } catch (Exception e) {
            LOG.error("Error handling LSP message for session {}", sessionId, e);
            sendError(session, "Error processing message: " + e.getMessage());
        }
    }

    /**
     * Parse JSON safely with depth limit to prevent DoS attacks.
     * SECURITY: Prevents deeply nested JSON attack vectors
     */
    private JsonNode parseJsonSafely(String payload) {
        try {
            JsonNode root = MAPPER.readTree(payload);
            if (exceedsJsonDepth(root, 0, MAX_JSON_DEPTH)) {
                LOG.warn("JSON depth exceeded limit: {}", MAX_JSON_DEPTH);
                return null;
            }
            return root;
        } catch (Exception e) {
            LOG.debug("Failed to parse JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if JSON depth exceeds maximum allowed depth.
     * Prevents deeply nested JSON DoS attacks
     */
    private boolean exceedsJsonDepth(JsonNode node, int currentDepth, int maxDepth) {
        if (currentDepth > maxDepth) {
            return true;
        }
        
        if (node.isObject()) {
            for (JsonNode child : node) {
                if (exceedsJsonDepth(child, currentDepth + 1, maxDepth)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (exceedsJsonDepth(child, currentDepth + 1, maxDepth)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Enregistre didOpen/didClose pour le routage sync → LSP (document → session).
     */
    private void registerDocumentOpenClose(JsonNode root, SessionInfo info) {
        if (info.sessionKey == null) return;
        JsonNode method = root.get("method");
        if (method == null) return;
        JsonNode params = root.get("params");
        JsonNode textDoc = params != null && params.has("textDocument") ? params.get("textDocument") : null;
        String uri = textDoc != null && textDoc.has("uri") ? textDoc.get("uri").asText(null) : null;
        if (uri == null) return;
        if ("textDocument/didOpen".equals(method.asText())) {
            documentToSessionRegistry.documentOpened(uri, info.sessionKey);
        } else if ("textDocument/didClose".equals(method.asText())) {
            documentToSessionRegistry.documentClosed(uri, info.sessionKey);
        }
    }

    private void notifySyncDocumentIfDidOpen(JsonNode root, SessionInfo info) {
        JsonNode method = root.get("method");
        if (method == null || !"textDocument/didOpen".equals(method.asText())) return;
        syncAdapter.filter(SyncServerAdapter::isEnabled).ifPresent(adapter -> {
            JsonNode params = root.get("params");
            if (params != null && params.has("textDocument") && params.get("textDocument").has("uri")) {
                String uri = params.get("textDocument").get("uri").asText();
                adapter.documentOpened(uri);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        SessionInfo info = sessionInfos.remove(sessionId);
        if (info != null && info.sessionKey != null) {
            documentToSessionRegistry.removeSession(info.sessionKey);
            sessionRegistry.removeClient(info.sessionKey, sessionId);
        }
        LOG.info("WebSocket closed: {}", sessionId);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        LOG.error("Transport error in session {}: {}", session.getId(), exception.getMessage());
        
        // FIX: Implement graceful error recovery
        // Check if this is a payload too large error
        String errorMsg = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        if (errorMsg.contains("payload") || errorMsg.contains("size") || errorMsg.contains("too large")) {
            try {
                sendError(session, "Message payload too large. Maximum size: " + MAX_MESSAGE_SIZE + " bytes");
                LOG.warn("Client {} sent oversized message, notified and continuing", session.getId());
            } catch (Exception e) {
                LOG.error("Failed to notify client of size limit", e);
            }
        }
    }

    private static LspServerType parseLanguage(String lang) {
        if (lang == null) return LspServerType.JAVA;
        switch (lang.toLowerCase()) {
            case "python": case "py": return LspServerType.PYTHON;
            case "typescript": case "ts": return LspServerType.TYPESCRIPT;
            default: return LspServerType.JAVA;
        }
    }

    private static void sendError(WebSocketSession session, String msg) {
        try {
            session.sendMessage(new TextMessage("{\"error\":\"" + msg.replace("\"", "\\\"") + "\"}"));
        } catch (Exception e) {
            LOG.warn("Send error failed", e);
        }
    }

    private static final class SessionInfo {
        final String sessionKey;
        final LspServerType type;
        final String rootUri;
        SessionInfo(String sessionKey, LspServerType type, String rootUri) {
            this.sessionKey = sessionKey; this.type = type; this.rootUri = rootUri;
        }
    }

    private static final class BridgeSessionClient implements LspBridgeSession.SessionClient {
        private final WebSocketSession session;
        BridgeSessionClient(WebSocketSession session) { this.session = session; }
        @Override
        public void send(String json) {
            try {
                if (session.isOpen()) session.sendMessage(new TextMessage(json));
            } catch (Exception e) {
                LOG.warn("Send failed", e);
            }
        }
        @Override
        public void sendError(String message) {
            send("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
        }
    }
}
