package com.distributed_ide.javafx_client.service;

import com.distributed_ide.javafx_client.model.DiagnosticItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Client WebSocket vers le lsp-bridge : init, envoi de requêtes LSP, réception des diagnostics.
 */
public class LspClientService {

    private static final Logger LOG = LoggerFactory.getLogger(LspClientService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private volatile WebSocketClient client;
    private volatile Listener listener;

    public interface Listener {
        void onDiagnostics(String uri, List<DiagnosticItem> diagnostics);
        void onMessage(String jsonRpcMessage);
        void onError(String message);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void connect(String wsUri, String language, String rootUri) {
        if (wsUri == null || wsUri.isEmpty()) return;
        try {
            URI uri = new URI(wsUri);
            client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    LOG.info("LSP WebSocket open");
                    sendInit(language, rootUri);
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    LOG.info("LSP WebSocket closed: {}", reason);
                }

                @Override
                public void onError(Exception ex) {
                    LOG.warn("LSP WebSocket error", ex);
                    if (listener != null) listener.onError(ex.getMessage());
                }
            };
            client.connect();
        } catch (Exception e) {
            LOG.error("LSP connect failed", e);
            if (listener != null) listener.onError(e.getMessage());
        }
    }

    public void disconnect() {
        WebSocketClient c = client;
        client = null;
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                LOG.warn("Error closing LSP client", e);
            }
        }
    }

    public void send(String jsonRpcMessage) {
        WebSocketClient c = client;
        if (c == null || !c.isOpen()) return;
        try {
            c.send(jsonRpcMessage);
        } catch (Exception e) {
            LOG.warn("Send LSP message failed", e);
        }
    }

    private void sendInit(String language, String rootUri) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("method", "init");
            ObjectNode params = MAPPER.createObjectNode();
            params.put("language", language != null ? language : "java");
            if (rootUri != null) params.put("rootUri", rootUri);
            root.set("params", params);
            WebSocketClient c = client;
            if (c != null && c.isOpen()) c.send(MAPPER.writeValueAsString(root));
        } catch (Exception e) {
            LOG.warn("Send init failed", e);
        }
    }

    private void handleMessage(String message) {
        if (listener != null) listener.onMessage(message);
        try {
            JsonNode root = MAPPER.readTree(message);
            if (root.has("method") && "textDocument/publishDiagnostics".equals(root.get("method").asText())) {
                JsonNode params = root.get("params");
                if (params != null) {
                    String uri = params.has("uri") ? params.get("uri").asText() : "";
                    List<DiagnosticItem> items = new ArrayList<>();
                    if (params.has("diagnostics")) {
                        for (JsonNode d : params.get("diagnostics")) {
                            int line = d.has("range") && d.get("range").has("start") ? d.get("range").get("start").get("line").asInt(0) : 0;
                            int col = d.has("range") && d.get("range").has("start") ? d.get("range").get("start").get("character").asInt(0) : 0;
                            String msg = d.has("message") ? d.get("message").asText() : "";
                            int sev = d.has("severity") ? d.get("severity").asInt(1) : 1;
                            DiagnosticItem.Severity severity = sev == 1 ? DiagnosticItem.Severity.ERROR : (sev == 2 ? DiagnosticItem.Severity.WARNING : DiagnosticItem.Severity.INFORMATION);
                            items.add(new DiagnosticItem(uri, line, col, msg, severity));
                        }
                    }
                    LOG.info("[LspClientService] Received diagnostics: {} diagnostics for URI={}", items.size(), uri);
                    for (DiagnosticItem item : items) {
                        LOG.info("[LspClientService]   - Line {}: {} - {}", item.getLine(), item.getSeverity(), item.getMessage());
                    }
                    if (listener != null) {
                        LOG.info("[LspClientService] Calling listener...");
                        listener.onDiagnostics(uri, items);
                    }
                }
            }
        } catch (Exception e) {
            LOG.trace("Parse LSP message failed: {}", e.getMessage());
        }
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }
}
