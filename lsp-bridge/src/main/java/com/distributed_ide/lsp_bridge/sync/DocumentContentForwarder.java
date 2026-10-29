package com.distributed_ide.lsp_bridge.sync;

import com.distributed_ide.lsp_bridge.bridge.DocumentToSessionRegistry;
import com.distributed_ide.lsp_bridge.bridge.LspBridgeSession;
import com.distributed_ide.lsp_bridge.bridge.LspSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reçoit les mises à jour de contenu document (après application CRDT) et envoie
 * textDocument/didChange au LSP de manière asynchrone. Les diagnostics renvoyés
 * par le LSP sont déjà transmis aux clients WebSocket par {@link LspBridgeSession}.
 */
public class DocumentContentForwarder {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentContentForwarder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DocumentToSessionRegistry documentToSessionRegistry;
    private final LspSessionRegistry sessionRegistry;
    private final ExecutorService asyncExecutor;
    private final ConcurrentHashMap<String, AtomicLong> documentVersions = new ConcurrentHashMap<>();

    public DocumentContentForwarder(DocumentToSessionRegistry documentToSessionRegistry,
                                    LspSessionRegistry sessionRegistry) {
        this.documentToSessionRegistry = documentToSessionRegistry;
        this.sessionRegistry = sessionRegistry;
        this.asyncExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "lsp-didChange-sender");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Appelé lorsque le contenu d'un document a été mis à jour (réplique CRDT).
     * Envoie textDocument/didChange au LSP sans bloquer le thread appelant.
     *
     * @param documentUri identifiant du document (uri)
     * @param fullText    contenu courant après application des opérations
     */
    public void onDocumentContentChanged(String documentUri, String fullText) {
        if (documentUri == null) return;
        Set<String> sessionKeys = documentToSessionRegistry.getSessionKeysForDocument(documentUri);
        if (sessionKeys.isEmpty()) {
            LOG.trace("No session for document {}, skip didChange", documentUri);
            return;
        }
        long version = documentVersions.computeIfAbsent(documentUri, k -> new AtomicLong(0)).incrementAndGet();

        for (String sessionKey : sessionKeys) {
            CompletableFuture.runAsync(() -> sendDidChangeToSession(sessionKey, documentUri, version, fullText), asyncExecutor)
                    .exceptionally(ex -> {
                        LOG.warn("Failed to send didChange for {} to session {}", documentUri, sessionKey, ex);
                        return null;
                    });
        }
    }

    /**
     * Envoi effectif de textDocument/didChange (full sync) à la session LSP.
     */
    private void sendDidChangeToSession(String sessionKey, String documentUri, long version, String fullText) {
        LspBridgeSession session = sessionRegistry.getSessionByKey(sessionKey);
        if (session == null) {
            LOG.debug("Session not found for key: {}", sessionKey);
            return;
        }
        String json = buildDidChangeNotification(documentUri, version, fullText);
        session.pushToLspAsync(json);
    }

    /**
     * Construit la notification JSON-RPC textDocument/didChange (full sync).
     */
    static String buildDidChangeNotification(String documentUri, long version, String fullText) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("uri", documentUri);
        params.put("version", version);
        ArrayNode contentChanges = MAPPER.createArrayNode();
        ObjectNode change = MAPPER.createObjectNode();
        change.put("text", fullText != null ? fullText : "");
        contentChanges.add(change);
        params.set("contentChanges", contentChanges);

        ObjectNode root = MAPPER.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.put("method", "textDocument/didChange");
        root.set("params", params);
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Build didChange JSON", e);
        }
    }

    /**
     * Arrêt propre du pool async.
     */
    public void shutdown() {
        asyncExecutor.shutdown();
    }
}
