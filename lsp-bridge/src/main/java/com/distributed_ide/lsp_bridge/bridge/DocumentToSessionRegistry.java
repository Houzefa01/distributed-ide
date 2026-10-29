package com.distributed_ide.lsp_bridge.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre document (uri) → sessions LSP. Permet de router les mises à jour
 * de contenu (sync → LSP) vers les bonnes sessions.
 */
public class DocumentToSessionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentToSessionRegistry.class);
    private final ConcurrentHashMap<String, Set<String>> uriToSessionKeys = new ConcurrentHashMap<>();

    public void documentOpened(String documentUri, String sessionKey) {
        if (documentUri == null || sessionKey == null) return;
        uriToSessionKeys.computeIfAbsent(documentUri, k -> ConcurrentHashMap.newKeySet()).add(sessionKey);
        LOG.debug("Document opened: {} -> {}", documentUri, sessionKey);
    }

    public void documentClosed(String documentUri, String sessionKey) {
        if (documentUri == null || sessionKey == null) return;
        uriToSessionKeys.computeIfPresent(documentUri, (k, set) -> {
            set.remove(sessionKey);
            return set.isEmpty() ? null : set;
        });
    }

    public Set<String> getSessionKeysForDocument(String documentUri) {
        if (documentUri == null) return Set.of();
        Set<String> set = uriToSessionKeys.get(documentUri);
        return set != null ? Set.copyOf(set) : Set.of();
    }

    public void removeSession(String sessionKey) {
        if (sessionKey == null) return;
        uriToSessionKeys.entrySet().removeIf(e -> e.getValue().remove(sessionKey) && e.getValue().isEmpty());
    }
}
