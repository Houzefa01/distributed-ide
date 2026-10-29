package com.distributed_ide.javafx_client.collaboration.service;

import com.distributed_ide.javafx_client.collaboration.model.RemoteCursor;
import com.distributed_ide.javafx_client.collaboration.model.RemoteCursor.Selection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Gère l'affichage et la synchronisation des curseurs distants.
 * Debounce, throttle et nettoyage automatique des curseurs.
 */
public class RemoteCursorManager {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteCursorManager.class);

    // Limites de cache
    private static final int MAX_CURSORS = 1000;
    private static final long CURSOR_TIMEOUT_MS = 60_000;      // 1 min
    private static final long DEBOUNCE_DELAY_MS = 50;          // 50ms debounce
    private static final long THROTTLE_INTERVAL_MS = 50;       // 20 updates/sec max

    // Curseurs par document
    private final Map<String, Map<String, RemoteCursor>> cursorsByDocument = new ConcurrentHashMap<>();
    
    // Debounce/throttle tracking
    private final Map<String, Long> lastUpdateTimeByClient = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> debounceTasksByClient = new ConcurrentHashMap<>();

    // Listeners
    private final List<CursorListener> listeners = new CopyOnWriteArrayList<>();

    // Scheduler
    private final ScheduledExecutorService debounceScheduler;
    private final ScheduledExecutorService cleanupScheduler;
    private volatile ScheduledFuture<?> cleanupTask;

    public interface CursorListener {
        void onCursorUpdated(String documentId, RemoteCursor cursor);
        void onCursorRemoved(String documentId, String clientId);
        void onSelectionUpdated(String documentId, RemoteCursor cursor);
    }

    public RemoteCursorManager() {
        this.debounceScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "cursor-debounce");
            t.setDaemon(true);
            return t;
        });
        this.cleanupScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "cursor-cleanup");
            t.setDaemon(true);
            return t;
        });
        startCleanupScheduler();
    }

    /**
     * Rapporte une mise à jour de curseur depuis le serveur.
     * Applique debounce et throttle automatiquement.
     */
    public void reportCursorUpdate(String documentId, String clientId, String username, 
                                   int line, int column, Optional<Selection> selection, 
                                   String color) {
        if (documentId == null || clientId == null) {
            LOG.warn("Invalid cursor update: documentId={}, clientId={}", documentId, clientId);
            return;
        }

        String updateKey = documentId + ":" + clientId;
        long now = System.currentTimeMillis();
        long lastUpdate = lastUpdateTimeByClient.getOrDefault(updateKey, 0L);

        // Throttle: Skip if updated recently
        if (now - lastUpdate < THROTTLE_INTERVAL_MS) {
            LOG.trace("Throttling cursor update for {}", updateKey);
            return;
        }

        // Cancel any pending debounce
        ScheduledFuture<?> pending = debounceTasksByClient.get(clientId);
        if (pending != null) {
            pending.cancel(false);
        }

        // Schedule debounced update
        ScheduledFuture<?> debounceTask = debounceScheduler.schedule(() -> {
            applyCursorUpdate(documentId, clientId, username, line, column, selection, color);
            lastUpdateTimeByClient.put(updateKey, System.currentTimeMillis());
            debounceTasksByClient.remove(clientId);
        }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);

        debounceTasksByClient.put(clientId, debounceTask);
    }

    /**
     * Obtient tous les curseurs pour un document.
     */
    public List<RemoteCursor> getCursorsForDocument(String documentId) {
        Map<String, RemoteCursor> cursors = cursorsByDocument.get(documentId);
        if (cursors == null) return new ArrayList<>();
        return new ArrayList<>(cursors.values());
    }

    /**
     * Obtient un curseur spécifique.
     */
    public Optional<RemoteCursor> getCursor(String documentId, String clientId) {
        Map<String, RemoteCursor> cursors = cursorsByDocument.get(documentId);
        if (cursors == null) return Optional.empty();
        return Optional.ofNullable(cursors.get(clientId));
    }

    /**
     * Supprime un curseur (client déconnecté).
     */
    public void removeCursor(String documentId, String clientId) {
        Map<String, RemoteCursor> cursors = cursorsByDocument.get(documentId);
        if (cursors != null) {
            RemoteCursor removed = cursors.remove(clientId);
            if (removed != null) {
                LOG.trace("Cursor removed: {}:{}", documentId, clientId);
                notifyCursorRemoved(documentId, clientId);
            }
        }

        // Clean up debounce
        debounceTasksByClient.remove(clientId);
        lastUpdateTimeByClient.remove(documentId + ":" + clientId);
    }

    /**
     * Nettoie tous les curseurs d'un document.
     */
    public void clearDocument(String documentId) {
        Map<String, RemoteCursor> cursors = cursorsByDocument.remove(documentId);
        if (cursors != null && !cursors.isEmpty()) {
            LOG.info("Cleared {} cursors for document {}", cursors.size(), documentId);
        }
    }

    /**
     * Ajoute un observateur de changements de curseurs.
     */
    public void addListener(CursorListener listener) {
        listeners.add(listener);
    }

    /**
     * Retire un observateur.
     */
    public void removeListener(CursorListener listener) {
        listeners.remove(listener);
    }

    /**
     * Arrête le manager et nettoie les ressources.
     */
    public void shutdown() {
        LOG.info("Shutting down RemoteCursorManager");
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }
        cleanupScheduler.shutdown();
        debounceScheduler.shutdown();
        cursorsByDocument.clear();
        lastUpdateTimeByClient.clear();
        debounceTasksByClient.values().forEach(f -> f.cancel(false));
        listeners.clear();
    }

    // ============= Private Methods =============

    private void startCleanupScheduler() {
        cleanupTask = cleanupScheduler.scheduleAtFixedRate(
                this::cleanupExpiredCursors,
                10, 10, TimeUnit.SECONDS
        );
    }

    /**
     * Applique la mise à jour du curseur.
     */
    private void applyCursorUpdate(String documentId, String clientId, String username,
                                   int line, int column, Optional<Selection> selection,
                                   String color) {
        // Ensure document map exists
        Map<String, RemoteCursor> cursors = cursorsByDocument.computeIfAbsent(documentId, 
                k -> Collections.synchronizedMap(new LinkedHashMap<String, RemoteCursor>() {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry eldest) {
                        return size() > MAX_CURSORS;
                    }
                }));

        RemoteCursor newCursor = new RemoteCursor.Builder()
                .clientId(clientId)
                .username(username)
                .color(color != null ? color : "#000000")
                .position(line, column)
                .lastUpdateTimestamp(System.currentTimeMillis())
                .selection(selection.orElse(null))
                .build();

        RemoteCursor oldCursor = cursors.put(clientId, newCursor);
        
        LOG.trace("Cursor updated: {}:{} @ {}:{}", documentId, clientId, line, column);
        notifyCursorUpdated(documentId, newCursor);

        // Notify if selection changed
        if (selection.isPresent()) {
            notifySelectionUpdated(documentId, newCursor);
        }
    }

    /**
     * Nettoie les curseurs expirés.
     */
    private void cleanupExpiredCursors() {
        long now = System.currentTimeMillis();
        List<String> documentsToClean = new ArrayList<>();

        for (Map.Entry<String, Map<String, RemoteCursor>> docEntry : new HashMap<>(cursorsByDocument).entrySet()) {
            String documentId = docEntry.getKey();
            Map<String, RemoteCursor> cursors = docEntry.getValue();

            List<String> expiredClientIds = new ArrayList<>();
            for (RemoteCursor cursor : cursors.values()) {
                if (now - cursor.getLastUpdateTimestamp() > CURSOR_TIMEOUT_MS) {
                    expiredClientIds.add(cursor.getClientId());
                }
            }

            for (String clientId : expiredClientIds) {
                removeCursor(documentId, clientId);
            }

            if (cursors.isEmpty()) {
                documentsToClean.add(documentId);
            }
        }

        // Clean up empty document maps
        for (String documentId : documentsToClean) {
            cursorsByDocument.remove(documentId);
        }
    }

    private void notifyCursorUpdated(String documentId, RemoteCursor cursor) {
        for (CursorListener listener : listeners) {
            try {
                listener.onCursorUpdated(documentId, cursor);
            } catch (Exception e) {
                LOG.error("Error notifying cursor updated", e);
            }
        }
    }

    private void notifyCursorRemoved(String documentId, String clientId) {
        for (CursorListener listener : listeners) {
            try {
                listener.onCursorRemoved(documentId, clientId);
            } catch (Exception e) {
                LOG.error("Error notifying cursor removed", e);
            }
        }
    }

    private void notifySelectionUpdated(String documentId, RemoteCursor cursor) {
        for (CursorListener listener : listeners) {
            try {
                listener.onSelectionUpdated(documentId, cursor);
            } catch (Exception e) {
                LOG.error("Error notifying selection updated", e);
            }
        }
    }
}
