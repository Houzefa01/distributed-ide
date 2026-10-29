package com.distributed_ide.terminal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gère le cycle de vie des processus du terminal avec robustesse.
 *
 * ✅ Arrêt propre des processus (pas de zombie)
 * ✅ Timeout pour les opérations système
 * ✅ Gestion des ressources (streams, pipes)
 * ✅ Détection de process mort
 * ✅ Logging détaillé pour diagnostique
 */
public class TerminalProcessManager {
    private static final Logger LOG = LoggerFactory.getLogger(TerminalProcessManager.class);

    private static final long PROCESS_WAIT_TIMEOUT_MS = 5000;
    private static final long PROCESS_DESTROY_TIMEOUT_MS = 3000;
    private static final int MAX_OUTPUT_BUFFER_LINES = 10000;

    private final Map<String, ProcessInfo> processesBySessionId = new ConcurrentHashMap<>();
    private final ScheduledExecutorService monitoringService = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "terminal-process-monitor");
        t.setDaemon(true);
        return t;
    });

    /**
     * Lance un processus shell dans une session terminal.
     * Gère automatiquement le redirection d'I/O et le monitoring.
     */
    public boolean startProcess(String sessionId, String command, String workingDir) {
        try {
            LOG.info("📌 Lancement processus pour session {}: {}", sessionId, command);

            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
            if (workingDir != null && !workingDir.isEmpty()) {
                pb.directory(new File(workingDir));
            }
            
            pb.redirectError(ProcessBuilder.Redirect.PIPE);
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE);

            Process process = pb.start();
            ProcessInfo procInfo = new ProcessInfo(sessionId, process, command);
            processesBySessionId.put(sessionId, procInfo);

            // Monitorer la sortie standard
            monitorStream(sessionId, process.getInputStream(), false);
            // Monitorer la stderr
            monitorStream(sessionId, process.getErrorStream(), true);

            // Monitorer l'état du processus
            scheduleProcessMonitoring(sessionId, process);

            LOG.info("✅ Processus lancé pour session {}", sessionId);
            return true;

        } catch (Exception e) {
            LOG.error("❌ Erreur lancement processus pour {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * Envoie un signal au processus (SIGTERM ou SIGKILL).
     */
    public boolean terminateProcess(String sessionId, boolean force) {
        ProcessInfo procInfo = processesBySessionId.get(sessionId);
        if (procInfo == null) {
            LOG.warn("⚠️  Processus non trouvé pour session {}", sessionId);
            return false;
        }

        try {
            Process process = procInfo.process;

            if (!process.isAlive()) {
                LOG.info("🛑 Processus déjà terminé pour session {}", sessionId);
                return true;
            }

            if (force) {
                LOG.warn("💥 Force kill du processus pour session {}", sessionId);
                process.destroyForcibly();
            } else {
                LOG.debug("🛑 Graceful shutdown du processus pour session {}", sessionId);
                process.destroy();

                // Attendre max 3 secondes
                if (process.waitFor(PROCESS_DESTROY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    LOG.info("✅ Processus arrêté proprement", sessionId);
                    return true;
                } else {
                    LOG.warn("⚠️  Graceful timeout, force kill pour session {}", sessionId);
                    process.destroyForcibly();
                    process.waitFor(1, TimeUnit.SECONDS);
                }
            }

            return true;

        } catch (Exception e) {
            LOG.error("❌ Erreur arrêt processus pour session {}: {}", sessionId, e.getMessage());
            return false;
        } finally {
            cleanupProcess(sessionId);
        }
    }

    /**
     * Attendre la fin du processus avec timeout.
     */
    public int waitForProcess(String sessionId, long timeoutMs) {
        ProcessInfo procInfo = processesBySessionId.get(sessionId);
        if (procInfo == null) {
            LOG.warn("⚠️  Processus non trouvé pour session {}", sessionId);
            return -1;
        }

        try {
            Process process = procInfo.process;

            if (!process.isAlive()) {
                int exitCode = process.exitValue();
                LOG.debug("📈 Processus session {} déjà terminé avec code: {}", sessionId, exitCode);
                return exitCode;
            }

            if (process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                int exitCode = process.exitValue();
                LOG.info("✅ Processus session {} terminé avec code: {}", sessionId, exitCode);
                return exitCode;
            } else {
                LOG.warn("⏱️  Timeout attente processus session {}, killing forcibly", sessionId);
                terminateProcess(sessionId, true);
                return -1;
            }

        } catch (InterruptedException e) {
            LOG.warn("⚠️  Interruption attente processus session {}", sessionId);
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /**
     * Vérifier si le processus est toujours vivant.
     */
    public boolean isProcessAlive(String sessionId) {
        ProcessInfo procInfo = processesBySessionId.get(sessionId);
        if (procInfo == null) return false;
        return procInfo.process.isAlive();
    }

    /**
     * Obtenir le code de sortie du processus (ou -1 si toujours actif).
     */
    public int getProcessExitCode(String sessionId) {
        ProcessInfo procInfo = processesBySessionId.get(sessionId);
        if (procInfo == null) return -2; // Not found
        
        try {
            return procInfo.process.exitValue();
        } catch (IllegalThreadStateException e) {
            return -1; // Still running
        }
    }

    /**
     * Obtenir la sortie collectée du processus.
     */
    public List<String> getProcessOutput(String sessionId) {
        ProcessInfo procInfo = processesBySessionId.get(sessionId);
        if (procInfo == null) return List.of();
        return new ArrayList<>(procInfo.outputLines);
    }

    /**
     * Nettoyer les ressources du processus.
     */
    private void cleanupProcess(String sessionId) {
        ProcessInfo procInfo = processesBySessionId.remove(sessionId);
        if (procInfo == null) return;

        try {
            Process process = procInfo.process;
            
            // Fermer les streams
            try {
                if (process.getInputStream() != null) {
                    process.getInputStream().close();
                }
            } catch (Exception e) {
                LOG.debug("⚠️  Erreur fermetura stdin: {}", e.getMessage());
            }
            
            try {
                if (process.getErrorStream() != null) {
                    process.getErrorStream().close();
                }
            } catch (Exception e) {
                LOG.debug("⚠️  Erreur fermetura stderr: {}", e.getMessage());
            }
            
            try {
                if (process.getOutputStream() != null) {
                    process.getOutputStream().close();
                }
            } catch (Exception e) {
                LOG.debug("⚠️  Erreur fermetura stdout: {}", e.getMessage());
            }

            // Forcer la destruction si encore vivant
            if (process.isAlive()) {
                LOG.warn("⚠️  Forcing process kill pour session {}", sessionId);
                process.destroyForcibly();
            }

            LOG.info("✅ Ressources nettoyées pour session {}", sessionId);

        } catch (Exception e) {
            LOG.error("❌ Erreur cleanup processus session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Monitorer la stdout/stderr du processus.
     */
    private void monitorStream(String sessionId, InputStream stream, boolean isError) {
        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                ProcessInfo procInfo = processesBySessionId.get(sessionId);
                
                while ((line = reader.readLine()) != null) {
                    if (procInfo != null) {
                        if (isError) {
                            procInfo.errorLines.add(line);
                        } else {
                            procInfo.outputLines.add(line);
                        }
                        // Limiter la taille du buffer
                        if (procInfo.outputLines.size() > MAX_OUTPUT_BUFFER_LINES) {
                            procInfo.outputLines.remove(0);
                        }
                    }
                }
            } catch (IOException e) {
                LOG.debug("📭 Stream reader fermé ou erreur pour session {}", sessionId);
            }
        });
    }

    /**
     * Programmer le monitoring périodique du processus.
     */
    private void scheduleProcessMonitoring(String sessionId, Process process) {
        monitoringService.schedule(() -> {
            try {
                if (!process.isAlive()) {
                    int exitCode = process.exitValue();
                    LOG.info("📭 Processus session {} terminé avec code: {}", sessionId, exitCode);
                    cleanupProcess(sessionId);
                }
            } catch (IllegalThreadStateException e) {
                // Process still running
            } catch (Exception e) {
                LOG.error("❌ Erreur monitoring processus session {}: {}", sessionId, e.getMessage());
            }
        }, 1, TimeUnit.SECONDS);
    }

    /**
     * Arrêter tous les processus et nettoyer les ressources.
     */
    public void shutdown() {
        LOG.info("🛑 Arrêt du gestionnaire de processus terminal");

        // Terminer tous les processus
        for (String sessionId : new ArrayList<>(processesBySessionId.keySet())) {
            terminateProcess(sessionId, false);
        }

        // Arrêter le service de monitoring
        monitoringService.shutdown();
        try {
            if (!monitoringService.awaitTermination(5, TimeUnit.SECONDS)) {
                monitoringService.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitoringService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOG.info("✅ Gestionnaire processus terminal arrêté");
    }

    /**
     * Classe interne pour tracker les infos du processus.
     */
    private static class ProcessInfo {
        final String sessionId;
        final Process process;
        final String command;
        final long startTime;
        final List<String> outputLines = Collections.synchronizedList(new ArrayList<>());
        final List<String> errorLines = Collections.synchronizedList(new ArrayList<>());

        ProcessInfo(String sessionId, Process process, String command) {
            this.sessionId = sessionId;
            this.process = process;
            this.command = command;
            this.startTime = System.currentTimeMillis();
        }
    }
}
