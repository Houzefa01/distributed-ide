package com.distributed_ide.lsp_bridge.bridge;

import com.distributed_ide.lsp_bridge.config.LspServerConfig;
import com.distributed_ide.lsp_bridge.config.LspServerType;
import com.distributed_ide.lsp_bridge.process.LspProcessManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LspSessionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(LspSessionRegistry.class);
    private final LspProcessManager processManager = new LspProcessManager();
    private final Map<String, LspBridgeSession> sessions = new ConcurrentHashMap<>();

    public static String sessionKeyFor(LspServerType type, String rootUri) {
        return LspProcessManager.processKey(type, pathFromUri(rootUri));
    }

    public LspBridgeSession getOrCreate(LspServerType type, String workspaceRootUriOrPath) {
        Path root = workspaceRootUriOrPath != null && !workspaceRootUriOrPath.isEmpty()
            ? pathFromUri(workspaceRootUriOrPath)
            : Paths.get(".").toAbsolutePath();
        String key = LspProcessManager.processKey(type, root);
        return sessions.computeIfAbsent(key, k -> {
            try {
                LspServerConfig config = LspServerConfig.defaults(type, root);
                LspProcessManager.LspProcessHandle h = processManager.start(config);
                LspBridgeSession s = new LspBridgeSession(h);
                s.startReading();
                return s;
            } catch (Exception e) {
                String friendly = friendlyStartError(type, root, e);
                LOG.warn("{}", friendly);
                throw new RuntimeException(friendly);
            }
        });
    }

    private static String friendlyStartError(LspServerType type, Path root, Exception e) {
        String base = "Start LSP failed: " + type + ":" + (root != null ? root.toAbsolutePath() : "") + ".";
        String msg = e.getMessage() != null ? e.getMessage() : e.toString();
        if (msg.contains("Cannot run program \"jdtls\"") || msg.contains("Cannot run program 'jdtls'") || msg.contains("Cannot run program \"jdtls")) {
            return base + " jdtls not found. Install jdtls or set JDTLS_HOME to a valid JDTLS directory (expects $JDTLS_HOME/bin/jdtls).";
        }
        if (msg.contains("Cannot run program \"npx\"") || msg.contains("Cannot run program 'npx'")) {
            return base + " npx not found. Install Node.js (npx) or adjust LSP config.";
        }
        return base + " " + msg;
    }

    /**
     * Retourne la session pour la clé donnée, ou null.
     */
    public LspBridgeSession getSessionByKey(String sessionKey) {
        return sessions.get(sessionKey);
    }

    public void removeClient(String sessionKey, String clientId) {
        LspBridgeSession s = sessions.get(sessionKey);
        if (s == null) return;
        s.removeClient(clientId);
        if (!s.hasClients()) {
            s.shutdown();
            sessions.remove(sessionKey);
            processManager.stop(sessionKey);
        }
    }

    public void shutdown() {
        sessions.values().forEach(LspBridgeSession::shutdown);
        sessions.clear();
        processManager.stopAll();
    }

    private static Path pathFromUri(String uriOrPath) {
        if (uriOrPath == null || uriOrPath.isEmpty()) return Paths.get(".").toAbsolutePath();
        if (uriOrPath.startsWith("file://")) {
            String path = uriOrPath.substring(7);
            if (path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
                return Paths.get(path.substring(1));
            }
            return Paths.get(path);
        }
        return Paths.get(uriOrPath);
    }
}
