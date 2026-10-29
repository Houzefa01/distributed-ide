package com.distributed_ide.crdt.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Représente une opération CRDT atomique.
 * Opérations supportées: INSERT, DELETE
 * 
 * Immutable et serializable pour la réplication sur le réseau.
 */
public class CRDTOperation implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        INSERT, DELETE
    }

    private final Type type;
    private final CRDTIdentifier id;
    private final char content; // pour INSERT
    private final CRDTIdentifier leftId; // identifiant gauche (pour positionnement)
    private final CRDTIdentifier rightId; // identifiant droit (pour positionnement)
    private final long timestamp; // moment de création de l'opération

    private CRDTOperation(Type type, CRDTIdentifier id, char content, 
                          CRDTIdentifier leftId, CRDTIdentifier rightId, long timestamp) {
        this.type = Objects.requireNonNull(type);
        this.id = Objects.requireNonNull(id);
        this.content = content;
        this.leftId = leftId;
        this.rightId = rightId;
        this.timestamp = timestamp;
    }

    public static CRDTOperation insert(CRDTIdentifier id, char content, 
                                       CRDTIdentifier leftId, CRDTIdentifier rightId, 
                                       long timestamp) {
        return new CRDTOperation(Type.INSERT, id, content, leftId, rightId, timestamp);
    }

    public static CRDTOperation delete(CRDTIdentifier id, long timestamp) {
        return new CRDTOperation(Type.DELETE, id, '\0', null, null, timestamp);
    }

    public Type getType() {
        return type;
    }

    public CRDTIdentifier getId() {
        return id;
    }

    public char getContent() {
        return content;
    }

    public CRDTIdentifier getLeftId() {
        return leftId;
    }

    public CRDTIdentifier getRightId() {
        return rightId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CRDTOperation)) return false;
        CRDTOperation that = (CRDTOperation) o;
        return type == that.type && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }

    @Override
    public String toString() {
        return String.format("%s(%s,%c)", type, id, type == Type.INSERT ? content : '-');
    }
}
