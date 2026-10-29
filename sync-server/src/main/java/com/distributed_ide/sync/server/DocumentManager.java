package com.distributed_ide.sync.server;

import com.distributed_ide.crdt.engine.CRDTEngine;
import com.distributed_ide.crdt.model.CRDTDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire de documents CRDT côté serveur.
 * Maintient l'état des documents distribués et les distribue aux clients.
 */
public class DocumentManager {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentManager.class);

    private final CRDTEngine engine;
    private final Map<String, DocumentMetadata> documentIndex;

    public DocumentManager(CRDTEngine engine) {
        this.engine = engine;
        this.documentIndex = new ConcurrentHashMap<>();
    }

    /**
     * Crée un nouveau document.
     */
    public CRDTDocument createDocument(String documentId, String ownerId) {
        CRDTDocument doc = engine.createDocument(documentId);
        DocumentMetadata metadata = new DocumentMetadata(documentId, ownerId);
        documentIndex.put(documentId, metadata);
        LOG.info("Document created: {} by {}", documentId, ownerId);
        return doc;
    }

    /**
     * Récupère un document par son ID.
     */
    public CRDTDocument getDocument(String documentId) {
        return engine.getDocument(documentId);
    }

    /**
     * Supprime un document.
     */
    public boolean deleteDocument(String documentId) {
        boolean removed = engine.deleteDocument(documentId);
        documentIndex.remove(documentId);
        if (removed) {
            LOG.info("Document deleted: {}", documentId);
        }
        return removed;
    }

    /**
     * Récupère les métadonnées d'un document.
     */
    public DocumentMetadata getDocumentMetadata(String documentId) {
        return documentIndex.get(documentId);
    }

    /**
     * Liste tous les documents disponibles.
     */
    public Collection<DocumentMetadata> listDocuments() {
        return documentIndex.values();
    }

    /**
     * Classe interne pour les métadonnées de document.
     */
    public static class DocumentMetadata {
        private final String documentId;
        private final String ownerId;
        private final long createdAt;
        // atomics make updates safe without external synchronization
        private final java.util.concurrent.atomic.AtomicLong lastModifiedAt;
        private final java.util.concurrent.atomic.AtomicInteger activeSubscribers;

        public DocumentMetadata(String documentId, String ownerId) {
            this.documentId = documentId;
            this.ownerId = ownerId;
            this.createdAt = System.currentTimeMillis();
            this.lastModifiedAt = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
            this.activeSubscribers = new java.util.concurrent.atomic.AtomicInteger(0);
        }

        public String getDocumentId() {
            return documentId;
        }

        public String getOwnerId() {
            return ownerId;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public long getLastModifiedAt() {
            return lastModifiedAt.get();
        }

        public void updateModificationTime() {
            lastModifiedAt.set(System.currentTimeMillis());
        }

        public int getActiveSubscribers() {
            return activeSubscribers.get();
        }

        public void incrementSubscribers() {
            activeSubscribers.incrementAndGet();
        }

        public void decrementSubscribers() {
            activeSubscribers.updateAndGet(v -> Math.max(0, v - 1));
        }
    }
}
