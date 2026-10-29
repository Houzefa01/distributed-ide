package com.distributed_ide.lsp_bridge.sync;

import com.distributed_ide.crdt.engine.CRDTEngine;
import com.distributed_ide.crdt.model.CRDTDocument;
import com.distributed_ide.crdt.model.CRDTOperation;
import com.distributed_ide.sync.model.SyncMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintient une réplique CRDT par document (documentId = uri) pour le bridge.
 * Applique les opérations reçues du sync-server et expose le texte courant.
 * N'intervient pas dans la synchronisation CRDT côté serveur.
 */
public class DocumentReplicaStore {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentReplicaStore.class);
    private static final String NODE_ID = "lsp-bridge-replica";

    private final CRDTEngine engine = new CRDTEngine(NODE_ID);
    private final ConcurrentHashMap<String, Boolean> knownDocuments = new ConcurrentHashMap<>();

    /**
     * Applique les opérations reçues du sync-server et retourne le texte courant du document.
     * Crée le document si nécessaire. Exécution thread-safe.
     *
     * @param documentId identifiant du document (souvent l'uri)
     * @param operations liste d'opérations (BATCH_OPERATIONS ou OPERATION)
     * @return texte courant après application, ou null si aucune opération valide
     */
    public String applyOperationsAndGetText(String documentId, List<SyncMessage.OperationData> operations) {
        if (documentId == null || operations == null || operations.isEmpty()) {
            return getText(documentId);
        }
        List<CRDTOperation> crdtOps = SyncMessageOperationConverter.toCrdtOperations(operations);
        if (crdtOps.isEmpty()) {
            return getText(documentId);
        }
        ensureDocument(documentId);
        for (CRDTOperation op : crdtOps) {
            engine.applyRemoteOperation(documentId, op);
        }
        return getText(documentId);
    }

    /**
     * Retourne le texte courant du document, ou une chaîne vide s'il n'existe pas.
     */
    public String getText(String documentId) {
        if (documentId == null) return "";
        CRDTDocument doc = engine.getDocument(documentId);
        return doc != null ? doc.getText() : "";
    }

    /**
     * Indique si le document est suivi (au moins une opération appliquée).
     */
    public boolean isTracked(String documentId) {
        return documentId != null && knownDocuments.containsKey(documentId);
    }

    private void ensureDocument(String documentId) {
        knownDocuments.computeIfAbsent(documentId, k -> {
            if (engine.getDocument(documentId) == null) {
                engine.createDocument(documentId);
                LOG.debug("Created replica for document: {}", documentId);
            }
            return Boolean.TRUE;
        });
    }
}
