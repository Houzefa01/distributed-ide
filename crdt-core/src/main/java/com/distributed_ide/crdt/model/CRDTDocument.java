package com.distributed_ide.crdt.model;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Représente un document CRDT unique.
 * 
 * Gère la collection ordonnée d'éléments avec les opérations CRDT.
 * Structure interne: SortedMap pour O(log n) lookup et insertion.
 */
public class CRDTDocument implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String documentId;
    private final SortedMap<CRDTIdentifier, CRDTElement> elements;
    private final Deque<CRDTOperation> operationHistory;
    private final Deque<CRDTOperation> undoStack;
    private volatile long logicalClock;

    public CRDTDocument(String documentId) {
        this.documentId = Objects.requireNonNull(documentId);
        this.elements = new ConcurrentSkipListMap<>();
        this.operationHistory = new LinkedList<>();
        this.undoStack = new LinkedList<>();
        this.logicalClock = 0;
    }

    /**
     * Insère un caractère à la position spécifiée.
     * Complexité: O(log n) pour la recherche + O(1) pour l'insertion
     */
    public CRDTIdentifier insert(int index, char value, String nodeId) {
        synchronized (this) {
            logicalClock++;
            
            // Trouver les éléments gauche et droit pour le positionnement
            CRDTIdentifier leftId = null;
            CRDTIdentifier rightId = null;
            
            List<CRDTElement> visible = getVisibleElements();
            if (index > 0 && index <= visible.size()) {
                leftId = visible.get(index - 1).getId();
            }
            if (index < visible.size()) {
                rightId = visible.get(index).getId();
            }
            
            CRDTIdentifier newId = new CRDTIdentifier(logicalClock, nodeId, 0);
            CRDTElement element = new CRDTElement(newId, value);
            
            elements.put(newId, element);
            
            CRDTOperation op = CRDTOperation.insert(newId, value, leftId, rightId, logicalClock);
            operationHistory.add(op);
            undoStack.clear(); // Réinitialiser redo après une nouvelle opération
            
            return newId;
        }
    }

    /**
     * Supprime un élément par son index visible.
     * Complexité: O(log n)
     */
    public boolean delete(int index) {
        synchronized (this) {
            List<CRDTElement> visible = getVisibleElements();
            if (index < 0 || index >= visible.size()) {
                return false;
            }
            
            CRDTElement element = visible.get(index);
            element.markDeleted();
            
            logicalClock++;
            CRDTOperation op = CRDTOperation.delete(element.getId(), logicalClock);
            operationHistory.add(op);
            undoStack.clear();
            
            return true;
        }
    }

    /**
     * Supprime un élément par son identifiant CRDT.
     */
    public boolean deleteById(CRDTIdentifier id) {
        synchronized (this) {
            CRDTElement element = elements.get(id);
            if (element == null || element.isDeleted()) {
                return false;
            }
            
            element.markDeleted();
            
            logicalClock++;
            CRDTOperation op = CRDTOperation.delete(id, logicalClock);
            operationHistory.add(op);
            undoStack.clear();
            
            return true;
        }
    }

    /**
     * Applique une opération distante.
     * Idempotent et commutative.
     */
    public void applyRemoteOperation(CRDTOperation op) {
        synchronized (this) {
            if (op.getType() == CRDTOperation.Type.INSERT) {
                if (!elements.containsKey(op.getId())) {
                    CRDTElement element = new CRDTElement(op.getId(), op.getContent());
                    elements.put(op.getId(), element);
                }
            } else if (op.getType() == CRDTOperation.Type.DELETE) {
                CRDTElement element = elements.get(op.getId());
                if (element != null) {
                    element.markDeleted();
                }
            }
            
            // Mettre à jour l'horloge logique
            if (op.getTimestamp() >= logicalClock) {
                logicalClock = op.getTimestamp() + 1;
            }
            
            operationHistory.add(op);
        }
    }

    /**
     * Annule la dernière opération.
     */
    public boolean undo() {
        synchronized (this) {
            if (operationHistory.isEmpty()) {
                return false;
            }
            
            CRDTOperation lastOp = operationHistory.removeLast();
            undoStack.push(lastOp);
            
            if (lastOp.getType() == CRDTOperation.Type.INSERT) {
                CRDTElement element = elements.get(lastOp.getId());
                if (element != null) {
                    element.markDeleted();
                }
            } else if (lastOp.getType() == CRDTOperation.Type.DELETE) {
                CRDTElement element = elements.get(lastOp.getId());
                if (element != null && element.isDeleted()) {
                    // Restaurer l'élément supprimé
                    element.unmarkDeleted();
                }
            }
            
            return true;
        }
    }

    /**
     * Refait l'opération annulée.
     */
    public boolean redo() {
        synchronized (this) {
            if (undoStack.isEmpty()) {
                return false;
            }
            
            CRDTOperation op = undoStack.pop();
            operationHistory.add(op);
            
            if (op.getType() == CRDTOperation.Type.INSERT) {
                CRDTElement element = elements.get(op.getId());
                if (element != null && element.isDeleted()) {
                    // Restaurer l'élément inséré
                    element.unmarkDeleted();
                }
            } else if (op.getType() == CRDTOperation.Type.DELETE) {
                CRDTElement element = elements.get(op.getId());
                if (element != null && !element.isDeleted()) {
                    element.markDeleted();
                }
            }
            
            return true;
        }
    }

    /**
     * Retourne le texte visible du document.
     * Exclut les éléments supprimés.
     */
    public String getText() {
        StringBuilder sb = new StringBuilder();
        for (CRDTElement elem : getVisibleElements()) {
            sb.append(elem.getValue());
        }
        return sb.toString();
    }

    /**
     * Retourne les éléments visibles (non supprimés) dans l'ordre.
     */
    public List<CRDTElement> getVisibleElements() {
        return elements.values().stream()
                .filter(elem -> !elem.isDeleted())
                .toList();
    }

    /**
     * Garbage collection: supprime les éléments supprimés anciens.
     * Réduit la consommation mémoire (important pour les longs documents).
     * 
     * Paramètre: ageThreshold - âge minimum avant suppression (ms)
     */
    public synchronized void garbageCollect(long ageThreshold) {
        long now = System.currentTimeMillis();
        List<CRDTIdentifier> toRemove = new ArrayList<>();
        
        for (CRDTElement elem : elements.values()) {
            if (elem.isDeleted() && (now - elem.getCreatedAt()) > ageThreshold) {
                toRemove.add(elem.getId());
            }
        }
        
        for (CRDTIdentifier id : toRemove) {
            elements.remove(id);
        }
    }

    /**
     * Fusionne les opérations d'un autre document.
     * Résout les conflits automatiquement grâce aux identifiants CRDT.
     */
    public void merge(CRDTDocument other) {
        for (CRDTOperation op : other.operationHistory) {
            if (!operationHistory.contains(op)) {
                applyRemoteOperation(op);
            }
        }
    }

    public String getDocumentId() {
        return documentId;
    }

    public List<CRDTOperation> getOperationHistory() {
        return new ArrayList<>(operationHistory);
    }

    public int size() {
        return getVisibleElements().size();
    }

    @Override
    public synchronized String toString() {
        return String.format("CRDTDocument(%s, content=%s)", documentId, getText());
    }
}
