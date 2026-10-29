package com.distributed_ide.javafx_client.lsp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère le cycle de vie des processus LSP par langage.
 *
 * Responsabilités :
 * - Démarrer un processus language server selon le langage
 * - Fournir le JsonRpcTransport associé
 * - Arrêter proprement les processus (shutdown + destroy)
 * - Configuration des commandes par langage
 *
 * Thread-safe : accès concurrent aux processus gérés via ConcurrentHashMap.
 */
public class LspProcessManager {

    private static final Logger LOG = LoggerFactory.getLogger(LspProcessManager.class);

    /**
     * Configuration d'un language server.
     */
    public static class ServerConfig {
        private final String languageId;
        private final List<String> command;
        private final Map<String, String> env;

        public ServerConfig(String languageId, List<String> command, Map<String, String> env) {
            this.languageId = languageId;
            this.command = command;
            this.env = env != null ? env : Collections.emptyMap();
        }

        public ServerConfig(String languageId, List<String> command) {
            this(languageId, command, null);
        }

        public String getLanguageId() { return languageId; }
        public List<String> getCommand() { return command; }
        public Map<String, String> getEnv() { return env; }
    }

    /**
     * Handle vers un processus LSP actif.
     */
    public static class LspProcess {
        private final String languageId;
        private final Process process;
        private final JsonRpcTransport transport;

        LspProcess(String languageId, Process process, JsonRpcTransport transport) {
            this.languageId = languageId;
            this.process = process;
            this.transport = transport;
        }

        public String getLanguageId() { return languageId; }
        public Process getProcess() { return process; }
        public JsonRpcTransport getTransport() { return transport; }

        public boolean isAlive() {
            return process.isAlive() && transport.isRunning();
        }
    }

    // Registered server configs
    private final Map<String, ServerConfig> configs = new ConcurrentHashMap<>();

    // Active processes
    private final Map<String, LspProcess> activeProcesses = new ConcurrentHashMap<>();

    public LspProcessManager() {
        registerDefaults();
    }

    // ═══════════════════════════════════════════════════════════
    //  DEFAULT CONFIGS
    // ═══════════════════════════════════════════════════════════

    private void registerDefaults() {
        // Java — Eclipse JDT Language Server
        register(new ServerConfig("java", List.of(
                "jdtls"
        )));

        // Python — pylsp (pip install python-lsp-server)
        register(new ServerConfig("python", List.of(
                "pylsp"
        )));

        // TypeScript/JavaScript — typescript-language-server
        register(new ServerConfig("typescript", List.of(
                "typescript-language-server", "--stdio"
        )));

        // Also register JS as alias for TS server
        register(new ServerConfig("javascript", List.of(
                "typescript-language-server", "--stdio"
        )));
    }

    // ═══════════════════════════════════════════════════════════
    //  REGISTRATION
    // ═══════════════════════════════════════════════════════════

    public void register(ServerConfig config) {
        configs.put(config.getLanguageId(), config);
    }

    public boolean isLanguageSupported(String languageId) {
        return configs.containsKey(languageId);
    }

    public Set<String> getSupportedLanguages() {
        return Collections.unmodifiableSet(configs.keySet());
    }

    // ═══════════════════════════════════════════════════════════
    //  START / STOP
    // ═══════════════════════════════════════════════════════════

    /**
     * Démarre un processus LSP pour le langage donné.
     * Si un processus est déjà actif pour ce langage, le retourne directement.
     *
     * @param languageId  identifiant du langage (ex: "java", "python", "typescript")
     * @param workingDir  répertoire de travail du projet (peut être null)
     * @return le LspProcess démarré, ou null si la config est manquante ou le démarrage échoue
     */
    public LspProcess start(String languageId, Path workingDir) {
        // Déjà actif ?
        LspProcess existing = activeProcesses.get(languageId);
        if (existing != null && existing.isAlive()) {
            return existing;
        }

        ServerConfig config = configs.get(languageId);
        if (config == null) {
            LOG.warn("No LSP config for language: {}", languageId);
            return null;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(config.getCommand());
            pb.redirectErrorStream(false);
            if (workingDir != null) {
                pb.directory(workingDir.toFile());
            }
            if (!config.getEnv().isEmpty()) {
                pb.environment().putAll(config.getEnv());
            }

            LOG.info("Starting LSP process for {}: {}", languageId, config.getCommand());
            Process process = pb.start();

            JsonRpcTransport transport = new JsonRpcTransport(
                    process.getOutputStream(),
                    process.getInputStream()
            );
            transport.start();

            // Monitor stderr in background
            Thread stderrThread = new Thread(() -> readStderr(languageId, process),
                    "lsp-stderr-" + languageId);
            stderrThread.setDaemon(true);
            stderrThread.start();

            // Monitor process exit
            process.onExit().thenAccept(p -> {
                LOG.info("LSP process {} exited with code {}", languageId, p.exitValue());
                activeProcesses.remove(languageId);
                transport.stop();
            });

            LspProcess lspProcess = new LspProcess(languageId, process, transport);
            activeProcesses.put(languageId, lspProcess);
            return lspProcess;

        } catch (IOException e) {
            LOG.error("Failed to start LSP process for {}: {}", languageId, e.getMessage());
            return null;
        }
    }

    /**
     * Arrête le processus LSP pour un langage.
     * Tente d'abord un arrêt gracieux (3s), puis force.
     */
    public void stop(String languageId) {
        LspProcess lsp = activeProcesses.remove(languageId);
        if (lsp == null) return;

        lsp.transport.stop();

        Process p = lsp.process;
        if (p.isAlive()) {
            p.destroy();
            try {
                if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    LOG.warn("Force-killed LSP process for {}", languageId);
                }
            } catch (InterruptedException e) {
                p.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        LOG.info("Stopped LSP process for {}", languageId);
    }

    /**
     * Arrête tous les processus LSP.
     */
    public void stopAll() {
        List<String> languages = new ArrayList<>(activeProcesses.keySet());
        for (String lang : languages) {
            stop(lang);
        }
    }

    /**
     * Retourne le processus actif pour un langage, ou null.
     */
    public LspProcess getProcess(String languageId) {
        LspProcess lsp = activeProcesses.get(languageId);
        if (lsp != null && !lsp.isAlive()) {
            activeProcesses.remove(languageId);
            return null;
        }
        return lsp;
    }

    /**
     * Retourne le transport actif pour un langage, ou null.
     */
    public JsonRpcTransport getTransport(String languageId) {
        LspProcess lsp = getProcess(languageId);
        return lsp != null ? lsp.getTransport() : null;
    }

    public boolean isRunning(String languageId) {
        LspProcess lsp = activeProcesses.get(languageId);
        return lsp != null && lsp.isAlive();
    }

    // ═══════════════════════════════════════════════════════════
    //  STDERR READER
    // ═══════════════════════════════════════════════════════════

    private void readStderr(String languageId, Process process) {
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOG.debug("[LSP-{}] {}", languageId, line);
            }
        } catch (IOException e) {
            // Process ended
        }
    }
}
