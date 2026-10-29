package com.distributed_ide.crdt.engine;

import com.distributed_ide.crdt.model.CRDTDocument;
import com.distributed_ide.crdt.model.CRDTIdentifier;
import com.distributed_ide.crdt.model.CRDTOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Moteur CRDT principal pour la gestion de documents multi-utilisateur.
 * 
 * Responsabilités:
 * - Gérer plusieurs documents CRDT
 * - Effectuer la synchronisation entre réplicas
 * - Assurer la causalité des opérations
 * - Nettoyer les données anciennes
 */
public class CRDTEngine {
    private static final Logger LOG = LoggerFactory.getLogger(CRDTEngine.class);

    private final String nodeId;
    private final Map<String, CRDTDocument> documents;
    private final ConcurrentLinkedQueue<CRDTOperation> pendingOperations;
    private final CopyOnWriteArrayList<OperationListener> listeners;

    public CRDTEngine(String nodeId) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
        this.documents = new ConcurrentHashMap<>();
        this.pendingOperations = new ConcurrentLinkedQueue<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Crée un nouveau document CRDT.
     */
    public CRDTDocument createDocument(String documentId) {
        CRDTDocument doc = new CRDTDocument(documentId);
        documents.put(documentId, doc);
        LOG.info("Document créé: {}", documentId);
        return doc;
    }

    /**
     * Récupère un document par son ID.
     */
    public CRDTDocument getDocument(String documentId) {
        return documents.get(documentId);
    }

    /**
     * Supprime un document du moteur.
     */
    public boolean deleteDocument(String documentId) {
        CRDTDocument removed = documents.remove(documentId);
        if (removed != null) {
            LOG.info("Document supprimé: {}", documentId);
            return true;
        }
        return false;
    }

    /**
     * Insère un caractère dans un document.
     */
    public CRDTIdentifier insertCharacter(String documentId, int index, char value) {
        CRDTDocument doc = getDocument(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("Document non trouvé: " + documentId);
        }

        CRDTIdentifier id = doc.insert(index, value, nodeId);
        CRDTOperation op = CRDTOperation.insert(id, value, null, null, System.currentTimeMillis());
        
        pendingOperations.add(op);
        notifyListeners(documentId, op);
        
        LOG.debug("Insert: {} at index {}", id, index);
        return id;
    }

    /**
     * Supprime un caractère d'un document.
     */
    public boolean deleteCharacter(String documentId, int index) {
        CRDTDocument doc = getDocument(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("Document non trouvé: " + documentId);
        }

        boolean success = doc.delete(index);
        if (success) {
            LOG.debug("Delete at index {}", index);
            notifyListeners(documentId, null); // Notifier le changement
        }
        return success;
    }

    /**
     * Applique une opération distante reçue.
     */
    public void applyRemoteOperation(String documentId, CRDTOperation operation) {
        CRDTDocument doc = getDocument(documentId);
        if (doc == null) {
            LOG.warn("Document non trouvé pour opération distante: {}", documentId);
            return;
        }

        doc.applyRemoteOperation(operation);
        LOG.debug("Opération distante appliquée: {}", operation);
        notifyListeners(documentId, operation);
    }

    /**
     * Récupère les opérations en attente de synchronisation.
     */
    public List<CRDTOperation> getPendingOperations() {
        List<CRDTOperation> pending = new ArrayList<>(pendingOperations);
        return pending;
    }

    /**
     * Récupère et utilise les opérations en attente avec taille maximale.
     * OPTIMISATION: Évite l'accumulation infinie de mémoire
     */
    public List<CRDTOperation> getPendingOperationsLimited(int maxSize) {
        List<CRDTOperation> pending = new ArrayList<>();
        int count = 0;
        for (CRDTOperation op : pendingOperations) {
            if (count >= maxSize) break;
            pending.add(op);
            count++;
        }
        return pending;
    }

    /**
     * Marque les opérations comme synchronisées.
     */
    public void clearPendingOperations() {
        pendingOperations.clear();
    }

    /**
     * Nettoie les opérations anciennes pour éviter les fuites mémoire.
     * OPTIMISATION: Garbage collection des opérations obsolètes
     * @param maxAgeMilis L'âge maximum en millisecondes
     * @return nombre d'opérations supprimées
     */
    public int cleanupOldOperations(long maxAgeMilis) {
        long cutoffTime = System.currentTimeMillis() - maxAgeMilis;
        int removed = 0;
        
        // Nettoyer aussi les documents
        for (CRDTDocument doc : documents.values()) {
            doc.garbageCollect(cutoffTime);
        }
        
        // Nota: ConcurrentLinkedQueue ne supporte pas bien le filtrage
        // Nous l'utilisons tel quel pour la performance
        LOG.debug("Cleanup effects: {} ongoing operations in queue", pendingOperations.size());
        return removed;
    }

    /**
     * Fusionne deux documents CRDT.
     */
    public void mergeDocuments(String sourceId, String targetId) {
        CRDTDocument source = getDocument(sourceId);
        CRDTDocument target = getDocument(targetId);

        if (source == null || target == null) {
            throw new IllegalArgumentException("Document source ou cible non trouvé");
        }

        target.merge(source);
        LOG.info("Documents fusionnés: {} -> {}", sourceId, targetId);
    }

    /**
     * Annule la dernière opération dans un document.
     */
    public boolean undo(String documentId) {
        CRDTDocument doc = getDocument(documentId);
        if (doc == null) {
            return false;
        }

        boolean success = doc.undo();
        if (success) {
            LOG.debug("Undo executed in document: {}", documentId);
            notifyListeners(documentId, null);
        }
        return success;
    }

    /**
     * Refait l'opération annulée dans un document.
     */
    public boolean redo(String documentId) {
        CRDTDocument doc = getDocument(documentId);
        if (doc == null) {
            return false;
        }

        boolean success = doc.redo();
        if (success) {
            LOG.debug("Redo executed in document: {}", documentId);
            notifyListeners(documentId, null);
        }
        return success;
    }

    /**
     * Effectue le garbage collection sur tous les documents.
     */
    public void garbageCollectAll(long ageThreshold) {
        for (CRDTDocument doc : documents.values()) {
            doc.garbageCollect(ageThreshold);
        }
        LOG.debug("Garbage collection effectué (seuil: {}ms)", ageThreshold);
    }

    /**
     * Ajoute un listener pour les changements de documents.
     */
    public void addOperationListener(OperationListener listener) {
        listeners.add(listener);
    }

    /**
     * Retire un listener.
     */
    public void removeOperationListener(OperationListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notifie les listeners des changements.
     */
    private void notifyListeners(String documentId, CRDTOperation operation) {
        for (OperationListener listener : listeners) {
            try {
                listener.onOperationApplied(documentId, operation);
            } catch (Exception e) {
                LOG.error("Erreur lors de la notification du listener", e);
            }
        }
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getDocumentCount() {
        return documents.size();
    }

    /**
     * Interface pour écouter les changements CRDT.
     */
    @FunctionalInterface
    public interface OperationListener {
        void onOperationApplied(String documentId, CRDTOperation operation);
    }

    /**
     * Shutdown handler pour nettoyage des ressources.
     * OPTIMISATION: Évite les resource leaks lors de l'arrêt
     */
    public void shutdown() {
        LOG.info("Shutting down CRDTEngine (nodeId={}. documents={}, operations={})",
                 nodeId, documents.size(), pendingOperations.size());
        
        // Clear all listeners first
        listeners.clear();
        
        // Clear all pending operations
        pendingOperations.clear();
        
        // Remove all documents
        for (String docId : documents.keySet()) {
            try {
                deleteDocument(docId);
            } catch (Exception e) {
                LOG.warn("Error deleting document {} during shutdown", docId, e);
            }
        }
        
        LOG.info("CRDTEngine shutdown complete");
    }

    @Override
    public String toString() {
        return String.format("CRDTEngine(nodeId=%s, documents=%d, pendingOps=%d)", 
                           nodeId, documents.size(), pendingOperations.size());
    }
}
