package com.distributed_ide.sync.server;

import com.distributed_ide.sync.model.SyncMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Gestionnaire de batches d'opérations pour optimisation réseau.
 * Regroupe les petites opérations en lots avant l'envoi.
 */
public class OperationBatcher {
    private static final Logger LOG = LoggerFactory.getLogger(OperationBatcher.class);

    private final int batchSize;
    private final long batchTimeoutMs;
    private final Map<String, List<SyncMessage.OperationData>> batches;
    private final ScheduledExecutorService scheduler;

    public OperationBatcher(int batchSize, long batchTimeoutMs) {
        this.batchSize = batchSize;
        this.batchTimeoutMs = batchTimeoutMs;
        this.batches = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "OperationBatcher");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Ajoute une opération au batch pour un document.
     * Retourne true si un batch doit être envoyé.
     */
    public boolean addOperation(String documentId, SyncMessage.OperationData op) {
        List<SyncMessage.OperationData> batch =
                batches.computeIfAbsent(documentId, k -> Collections.synchronizedList(new ArrayList<>()));
        batch.add(op);
        int size = batch.size();
        if (size >= batchSize) {
            LOG.debug("Batch size reached for document {}", documentId);
            return true;
        }

        // Premier opération du batch : programmer un timeout
        if (size == 1) {
            scheduler.schedule(() -> {
                List<SyncMessage.OperationData> timedOutBatch = batches.get(documentId);
                if (timedOutBatch != null && !timedOutBatch.isEmpty()) {
                    LOG.debug("Batch timeout for document {} with {} operations", 
                            documentId, timedOutBatch.size());
                }
            }, batchTimeoutMs, TimeUnit.MILLISECONDS);
        }

        return false;
    }

    /**
     * Récupère et vide le batch pour un document.
     */
    public List<SyncMessage.OperationData> flushBatch(String documentId) {
        List<SyncMessage.OperationData> batch = batches.remove(documentId);
        return batch != null ? batch : new ArrayList<>();
    }

    /**
     * Récupère la taille du batch sans le vider.
     */
    public int getBatchSize(String documentId) {
        List<SyncMessage.OperationData> batch = batches.get(documentId);
        return batch != null ? batch.size() : 0;
    }

    /**
     * Arrête le batcher (appeler au shutdown du serveur).
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
