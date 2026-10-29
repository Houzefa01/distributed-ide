package com.distributed_ide.javafx_client.lsp;

import com.distributed_ide.javafx_client.model.DiagnosticItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Façade principale pour la gestion LSP multi-langage.
 *
 * Thread-safe, non-bloquante. Toutes les requêtes retournent CompletableFuture.
 *
 * Fonctionnalités :
 * - Démarrage automatique du serveur LSP selon le langage du fichier
 * - initialize/shutdown lifecycle
 * - textDocument sync (didOpen, didChange, didSave, didClose)
 * - Completion, Hover, Go to Definition
 * - Diagnostics (via notification callback)
 * - Gestion propre des processus externes
 *
 * Usage :
 * <pre>
 *   LSPManager lsp = new LSPManager();
 *   lsp.setDiagnosticsListener((uri, items) -> updateUI(items));
 *   lsp.openFile("/path/to/File.java", "java", content, projectRoot);
 *   lsp.completion("/path/to/File.java", 10, 5).thenAccept(result -> ...);
 *   lsp.shutdown();
 * </pre>
 */
public class LSPManager {

    private static final Logger LOG = LoggerFactory.getLogger(LSPManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LspProcessManager processManager;
    private final ExecutorService executor;
    
    // ✅ FIX: Add cache manager for performance optimization
    private final LspCacheManager cacheManager;

    // Document versions per URI (for didChange versioning)
    private final ConcurrentHashMap<String, AtomicInteger> documentVersions = new ConcurrentHashMap<>();

    // Language mapping: filePath → languageId
    private final ConcurrentHashMap<String, String> fileLanguageMap = new ConcurrentHashMap<>();

    // Initialized servers (languageId → initialized flag)
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> initFutures = new ConcurrentHashMap<>();

    // Listeners
    private volatile BiConsumer<String, List<DiagnosticItem>> diagnosticsListener;
    private volatile BiConsumer<String, String> logListener;

    public LSPManager() {
        this.processManager = new LspProcessManager();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "lsp-manager-worker");
            t.setDaemon(true);
            return t;
        });
        this.cacheManager = new LspCacheManager();
    }

    public LSPManager(LspProcessManager processManager) {
        this.processManager = processManager;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "lsp-manager-worker");
            t.setDaemon(true);
            return t;
        });
        this.cacheManager = new LspCacheManager();
    }

    // ═══════════════════════════════════════════════════════════
    //  LISTENERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Callback pour les diagnostics publiés par le serveur.
     * BiConsumer(uri, diagnosticItems).
     */
    public void setDiagnosticsListener(BiConsumer<String, List<DiagnosticItem>> listener) {
        this.diagnosticsListener = listener;
    }

    /**
     * Callback pour les messages de log LSP.
     */
    public void setLogListener(BiConsumer<String, String> listener) {
        this.logListener = listener;
    }

    // ═══════════════════════════════════════════════════════════
    //  SERVER CONFIG
    // ═══════════════════════════════════════════════════════════

    /**
     * Enregistre une configuration de serveur LSP personnalisée.
     */
    public void registerServer(LspProcessManager.ServerConfig config) {
        processManager.register(config);
    }

    public boolean isLanguageSupported(String languageId) {
        return processManager.isLanguageSupported(languageId);
    }

    public Set<String> getSupportedLanguages() {
        return processManager.getSupportedLanguages();
    }

    // ═══════════════════════════════════════════════════════════
    //  FILE LIFECYCLE
    // ═══════════════════════════════════════════════════════════

    /**
     * Ouvre un fichier dans le LSP : démarre le serveur si nécessaire,
     * initialise, puis envoie textDocument/didOpen.
     *
     * @param filePath    chemin absolu du fichier
     * @param languageId  ex: "java", "python", "typescript"
     * @param content     contenu du fichier
     * @param projectRoot racine du projet (pour rootUri)
     * @return Future complété quand le fichier est ouvert côté LSP
     */
    public CompletableFuture<Void> openFile(String filePath, String languageId, String content, Path projectRoot) {
        String uri = LspProtocol.fileToUri(filePath);
        fileLanguageMap.put(filePath, languageId);
        documentVersions.put(uri, new AtomicInteger(1));

        return ensureServerReady(languageId, projectRoot)
                .thenAcceptAsync(serverCaps -> {
                    JsonRpcTransport transport = processManager.getTransport(languageId);
                    if (transport != null) {
                        LspProtocol.didOpen(transport, uri, languageId,
                                documentVersions.get(uri).get(), content);
                        LOG.debug("didOpen: {} ({})", filePath, languageId);
                    }
                }, executor);
    }

    /**
     * Notifie le LSP d'un changement de contenu (full text sync).
     * ✅ Invalidates completion cache for this file.
     */
    public void didChange(String filePath, String content) {
        String languageId = fileLanguageMap.get(filePath);
        if (languageId == null) return;

        String uri = LspProtocol.fileToUri(filePath);
        AtomicInteger version = documentVersions.get(uri);
        if (version == null) return;

        // ✅ FIX: Invalidate cache when file changes
        cacheManager.invalidateFile(uri);
        LOG.debug("📝 File changed, cache invalidated: {}", filePath);

        JsonRpcTransport transport = processManager.getTransport(languageId);
        if (transport != null) {
            LspProtocol.didChange(transport, uri, version.incrementAndGet(), content);
        }
    }

    /**
     * Notifie le LSP d'une sauvegarde.
     */
    public void didSave(String filePath, String content) {
        String languageId = fileLanguageMap.get(filePath);
        if (languageId == null) return;

        String uri = LspProtocol.fileToUri(filePath);
        JsonRpcTransport transport = processManager.getTransport(languageId);
        if (transport != null) {
            LspProtocol.didSave(transport, uri, content);
        }
    }

    /**
     * Ferme un fichier côté LSP.
     */
    public void closeFile(String filePath) {
        String languageId = fileLanguageMap.remove(filePath);
        if (languageId == null) return;

        String uri = LspProtocol.fileToUri(filePath);
        documentVersions.remove(uri);

        JsonRpcTransport transport = processManager.getTransport(languageId);
        if (transport != null) {
            LspProtocol.didClose(transport, uri);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  COMPLETION
    // ═══════════════════════════════════════════════════════════

    /**
     * Demande la complétion à la position donnée (0-based line & column).
     *
     * @return Future avec le résultat JSON (CompletionList ou CompletionItem[])
     */
    /**
     * Request code completions at given position.
     * ✅ Uses cache to avoid LSP server roundtrip for recently completed positions.
     * 
     * @return Future with completion results (or null if not available)
     */
    public CompletableFuture<JsonNode> completion(String filePath, int line, int character) {
        String languageId = fileLanguageMap.get(filePath);
        if (languageId == null) {
            return CompletableFuture.completedFuture(null);
        }
        JsonRpcTransport transport = processManager.getTransport(languageId);
        if (transport == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        String uri = LspProtocol.fileToUri(filePath);
        String prefix = "";  // Could extract text prefix for better cache key
        
        // ✅ FIX: Check cache first (avoids LSP server roundtrip)
        JsonNode cached = cacheManager.get(uri, line, character, prefix);
        if (cached != null) {
            LOG.debug("⚡ Completion cache hit for {}:{}:{}", filePath, line, character);
            return CompletableFuture.completedFuture(cached);
        }
        
        // Cache miss - request from LSP server
        LOG.debug("🔍 Completion cache miss, querying LSP for {}:{}:{}", filePath, line, character);
        return LspProtocol.completion(transport, uri, line, character)
                .thenApply(result -> {
                    // Store in cache for future requests
                    if (result != null) {
                        cacheManager.put(uri, line, character, prefix, result);
                    }
                    return result;
                });
    }

    // ═══════════════════════════════════════════════════════════
    //  HOVER
    // ═══════════════════════════════════════════════════════════

    /**
     * Demande les informations de hover à la position donnée.
     *
     * @return Future avec le résultat Hover JSON
     */
    public CompletableFuture<JsonNode> hover(String filePath, int line, int character) {
        String languageId = fileLanguageMap.get(filePath);
        if (languageId == null) {
            return CompletableFuture.completedFuture(null);
        }
        JsonRpcTransport transport = processManager.getTransport(languageId);
        if (transport == null) {
            return CompletableFuture.completedFuture(null);
        }
        String uri = LspProtocol.fileToUri(filePath);
        return LspProtocol.hover(transport, uri, line, character);
    }

    // ═══════════════════════════════════════════════════════════
    //  GO TO DEFINITION
    // ═══════════════════════════════════════════════════════════

    /**
     * Demande la définition du symbole à la position donnée.
     *
     * @return Future avec Location ou Location[]
     */
    public CompletableFuture<JsonNode> definition(String filePath, int line, int character) {
        String languageId = fileLanguageMap.get(filePath);
        if (languageId == null) {
            return CompletableFuture.completedFuture(null);
        }
        JsonRpcTransport transport = processManager.getTransport(languageId);
        if (transport == null) {
            return CompletableFuture.completedFuture(null);
        }
        String uri = LspProtocol.fileToUri(filePath);
        return LspProtocol.definition(transport, uri, line, character);
    }

    // ═══════════════════════════════════════════════════════════
    //  SERVER LIFECYCLE (INTERNAL)
    // ═══════════════════════════════════════════════════════════

    /**
     * Assure que le serveur LSP pour le langage donné est démarré et initialisé.
     * Thread-safe : un seul initialize par langage, les appels suivants
     * réutilisent le même Future.
     */
    private CompletableFuture<JsonNode> ensureServerReady(String languageId, Path projectRoot) {
        return initFutures.computeIfAbsent(languageId, lang -> {
            CompletableFuture<JsonNode> future = new CompletableFuture<>();

            executor.submit(() -> {
                try {
                    LspProcessManager.LspProcess lspProcess = processManager.start(lang, projectRoot);
                    if (lspProcess == null) {
                        future.completeExceptionally(
                                new RuntimeException("Failed to start LSP for " + lang));
                        return;
                    }

                    JsonRpcTransport transport = lspProcess.getTransport();

                    // Set notification handler for this transport
                    transport.setNotificationHandler(this::handleServerNotification);

                    String rootUri = projectRoot != null
                            ? projectRoot.toUri().toString()
                            : null;

                    int pid = (int) ProcessHandle.current().pid();

                    LspProtocol.initialize(transport, rootUri, pid)
                            .whenComplete((result, error) -> {
                                if (error != null) {
                                    LOG.error("LSP initialize failed for {}: {}", lang, error.getMessage());
                                    future.completeExceptionally(error);
                                    initFutures.remove(lang);
                                } else {
                                    LOG.info("LSP initialized for {}", lang);
                                    logMessage(lang, "Server initialized");
                                    future.complete(result);
                                }
                            });

                } catch (Exception e) {
                    LOG.error("LSP start failed for {}", lang, e);
                    future.completeExceptionally(e);
                    initFutures.remove(lang);
                }
            });

            return future;
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  NOTIFICATION HANDLING
    // ═══════════════════════════════════════════════════════════

    private void handleServerNotification(JsonNode message) {
        String method = message.has("method") ? message.get("method").asText() : "";
        JsonNode params = message.get("params");

        switch (method) {
            case "textDocument/publishDiagnostics":
                handleDiagnostics(params);
                break;
            case "window/logMessage":
            case "window/showMessage":
                handleLogMessage(params);
                break;
            default:
                LOG.trace("Unhandled LSP notification: {}", method);
                break;
        }
    }

    private void handleDiagnostics(JsonNode params) {
        if (params == null) return;

        String uri = params.has("uri") ? params.get("uri").asText() : "";
        List<DiagnosticItem> items = new ArrayList<>();

        if (params.has("diagnostics")) {
            for (JsonNode d : params.get("diagnostics")) {
                int line = 0;
                int col = 0;
                if (d.has("range") && d.get("range").has("start")) {
                    JsonNode start = d.get("range").get("start");
                    line = start.has("line") ? start.get("line").asInt(0) : 0;
                    col = start.has("character") ? start.get("character").asInt(0) : 0;
                }
                String msg = d.has("message") ? d.get("message").asText() : "";
                int sev = d.has("severity") ? d.get("severity").asInt(1) : 1;
                DiagnosticItem.Severity severity = switch (sev) {
                    case 1 -> DiagnosticItem.Severity.ERROR;
                    case 2 -> DiagnosticItem.Severity.WARNING;
                    case 3 -> DiagnosticItem.Severity.INFORMATION;
                    case 4 -> DiagnosticItem.Severity.HINT;
                    default -> DiagnosticItem.Severity.ERROR;
                };
                items.add(new DiagnosticItem(uri, line, col, msg, severity));
            }
        }

        LOG.info("[LSPManager] Received diagnostics: {} diagnostics for URI={}", items.size(), uri);
        for (DiagnosticItem item : items) {
            LOG.info("[LSPManager]   - Line {}: {} - {}", item.getLine(), item.getSeverity(), item.getMessage());
        }

        BiConsumer<String, List<DiagnosticItem>> listener = diagnosticsListener;
        if (listener != null) {
            LOG.info("[LSPManager] Calling diagnosticsListener...");
            listener.accept(uri, items);
        } else {
            LOG.warn("[LSPManager] No diagnosticsListener registered!");
        }
    }

    private void handleLogMessage(JsonNode params) {
        if (params == null) return;
        String msg = params.has("message") ? params.get("message").asText() : "";
        int type = params.has("type") ? params.get("type").asInt(3) : 3;
        String level = switch (type) {
            case 1 -> "ERROR";
            case 2 -> "WARN";
            case 3 -> "INFO";
            case 4 -> "LOG";
            default -> "INFO";
        };
        LOG.debug("[LSP-{}] {}", level, msg);
        logMessage("LSP", level + ": " + msg);
    }

    private void logMessage(String source, String message) {
        BiConsumer<String, String> listener = logListener;
        if (listener != null) {
            listener.accept(source, message);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SHUTDOWN
    // ═══════════════════════════════════════════════════════════

    /**
     * Arrête proprement tous les serveurs LSP.
     * Envoie shutdown+exit, puis détruit les processus.
     */
    public void shutdown() {
        LOG.info("LSPManager shutting down...");

        // Send shutdown to each initialized server
        List<CompletableFuture<Void>> shutdownFutures = new ArrayList<>();
        for (Map.Entry<String, CompletableFuture<JsonNode>> entry : initFutures.entrySet()) {
            String lang = entry.getKey();
            if (entry.getValue().isDone() && !entry.getValue().isCompletedExceptionally()) {
                JsonRpcTransport transport = processManager.getTransport(lang);
                if (transport != null) {
                    CompletableFuture<Void> sf = LspProtocol.shutdown(transport)
                            .orTimeout(3, TimeUnit.SECONDS)
                            .handle((r, e) -> null)
                            .thenAccept(v -> {});
                    shutdownFutures.add(sf);
                }
            }
        }

        // Wait up to 5s for all shutdowns
        try {
            CompletableFuture.allOf(shutdownFutures.toArray(new CompletableFuture[0]))
                    .orTimeout(5, TimeUnit.SECONDS)
                    .join();
        } catch (Exception e) {
            LOG.debug("Some LSP shutdowns timed out");
        }

        // Force-stop all processes
        processManager.stopAll();
        initFutures.clear();
        documentVersions.clear();
        fileLanguageMap.clear();

        // ✅ FIX: Shut down cache manager
        cacheManager.shutdown();

        executor.shutdown();
        LOG.info("LSPManager shut down");
    }

    /**
     * Arrête le serveur LSP pour un langage spécifique.
     */
    public void shutdownLanguage(String languageId) {
        CompletableFuture<JsonNode> initFuture = initFutures.remove(languageId);
        if (initFuture != null && initFuture.isDone() && !initFuture.isCompletedExceptionally()) {
            JsonRpcTransport transport = processManager.getTransport(languageId);
            if (transport != null) {
                try {
                    LspProtocol.shutdown(transport)
                            .orTimeout(3, TimeUnit.SECONDS)
                            .join();
                } catch (Exception e) {
                    LOG.debug("Shutdown timeout for {}", languageId);
                }
            }
        }
        processManager.stop(languageId);
    }

    // ═══════════════════════════════════════════════════════════
    //  ACCESSORS
    // ═══════════════════════════════════════════════════════════

    public LspProcessManager getProcessManager() {
        return processManager;
    }

    public boolean isServerRunning(String languageId) {
        return processManager.isRunning(languageId);
    }
}
