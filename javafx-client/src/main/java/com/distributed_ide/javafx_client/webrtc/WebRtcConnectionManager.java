package com.distributed_ide.javafx_client.webrtc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Gère les timeouts et reconnexions pour la stabilité WebRTC.
 * 
 * ✅ Reconnexion automatique avec backoff exponentiel
 * ✅ Détection de timeout de connexion peer
 * ✅ Retry logic avec limites configurable
 * ✅ Métriques de santé du système
 */
public class WebRtcConnectionManager {
    private static final Logger LOG = LoggerFactory.getLogger(WebRtcConnectionManager.class);

    private static final long INITIAL_RECONNECT_DELAY_MS = 1000;
    private static final long MAX_RECONNECT_DELAY_MS = 30000;
    private static final long PEER_CONNECT_TIMEOUT_MS = 15000;
    private static final long ICE_GATHERING_TIMEOUT_MS = 5000;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private final ScheduledExecutorService scheduler;
    private final AtomicLong reconnectAttempts = new AtomicLong(0);
    private volatile long lastFailureTime = 0;
    private volatile String lastErrorMessage = "";

    public WebRtcConnectionManager() {
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "webrtc-connection-mgr");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Enregistre une tâche de reconnexion avec backoff exponentiel.
     * La reconnexion commence à INITIAL_RECONNECT_DELAY_MS et double à chaque tentative.
     */
    public void scheduleReconnect(Runnable reconnectTask, Consumer<String> onMaxAttemptsReached) {
        long attempts = reconnectAttempts.incrementAndGet();
        
        if (attempts > MAX_RECONNECT_ATTEMPTS) {
            LOG.error("🚨 Max reconnect attempts ({}) reached", MAX_RECONNECT_ATTEMPTS);
            if (onMaxAttemptsReached != null) {
                onMaxAttemptsReached.accept("Max reconnect attempts reached");
            }
            reconnectAttempts.set(0);
            return;
        }

        // Calcul du délai avec backoff exponentiel
        long delay = Math.min(
            INITIAL_RECONNECT_DELAY_MS * (long) Math.pow(2, attempts - 1),
            MAX_RECONNECT_DELAY_MS
        );

        LOG.info("📡 Reconexion programmée dans {}ms (tentative {})", delay, attempts);
        scheduler.schedule(() -> {
            try {
                LOG.info("🔄 Tentative de reconnexion #{}", attempts);
                reconnectTask.run();
            } catch (Exception e) {
                LOG.error("❌ Reconnexion échouée: {}", e.getMessage());
                lastErrorMessage = e.getMessage();
                lastFailureTime = System.currentTimeMillis();
                scheduleReconnect(reconnectTask, onMaxAttemptsReached);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Enregistre une tâche de timeout pour un événement attendu.
     * Si le timeout expire, timeoutAction est exécutée.
     */
    public void scheduleTimeout(String eventName, long timeoutMs, Runnable timeoutAction) {
        scheduler.schedule(() -> {
            LOG.warn("⏱️  Timeout: {} après {}ms", eventName, timeoutMs);
            try {
                timeoutAction.run();
            } catch (Exception e) {
                LOG.error("❌ Erreur lors du timeout pour {}: {}", eventName, e.getMessage());
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Monitore la santé d'une connexion peer avec un heartbeat périodique.
     * Si aucun heartbeat n'est reçu, la connexion est considérée comme morte.
     */
    public void monitorPeerHealth(String peerId, int heartbeatIntervalMs, 
                                   Runnable heartbeatCheck, 
                                   Runnable onUnhealthy) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                LOG.trace("💚 Heartbeat check pour peer: {}", peerId);
                heartbeatCheck.run();
            } catch (Exception e) {
                LOG.warn("⚠️  Peer {} unhealthy: {}", peerId, e.getMessage());
                onUnhealthy.run();
            }
        }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Réinitialise le compteur de tentatives de reconnexion.
     * À appeler après une reconnexion réussie.
     */
    public void resetReconnectAttempts() {
        LOG.info("✅ Reconnexion réussie, réinitialisation du compteur");
        reconnectAttempts.set(0);
        lastErrorMessage = "";
    }

    /**
     * @return Délai de timeout recommandé pour la connexion d'un peer (en ms)
     */
    public long getPeerConnectTimeoutMs() {
        return PEER_CONNECT_TIMEOUT_MS;
    }

    /**
     * @return Délai de timeout recommandé pour la collecte des ICE candidates (en ms)
     */
    public long getIceGatheringTimeoutMs() {
        return ICE_GATHERING_TIMEOUT_MS;
    }

    /**
     * @return Nombre actuel de tentatives de reconnexion
     */
    public long getReconnectAttempts() {
        return reconnectAttempts.get();
    }

    /**
     * @return Dernier message d'erreur enregistré
     */
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    /**
     * @return Timestamp du dernier échec (ms depuis epoch)
     */
    public long getLastFailureTime() {
        return lastFailureTime;
    }

    /**
     * Arrête proprement le gestionnaire et libère les ressources.
     */
    public void shutdown() {
        LOG.info("🛑 Arrêt du gestionnaire de connexion WebRTC");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                LOG.warn("⚠️  Force shutdown du scheduler");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
