package com.distributed_ide.crdt.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Élément CRDT représentant un caractère avec métadonnées.
 * 
 * Immutable pour garantir la sécurité en concurrence.
 */
public class CRDTElement implements Comparable<CRDTElement>, Serializable {
    private static final long serialVersionUID = 1L;

    private final CRDTIdentifier id;
    private final char value;
    private volatile boolean deleted; // marqué pour suppression (soft delete)
    private final long createdAt;

    public CRDTElement(CRDTIdentifier id, char value) {
        this.id = Objects.requireNonNull(id);
        this.value = value;
        this.deleted = false;
        this.createdAt = System.currentTimeMillis();
    }

    public CRDTIdentifier getId() {
        return id;
    }

    public char getValue() {
        return value;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void markDeleted() {
        this.deleted = true;
    }

    public void unmarkDeleted() {
        this.deleted = false;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    public int compareTo(CRDTElement other) {
        return this.id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CRDTElement)) return false;
        CRDTElement that = (CRDTElement) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Elem(%s,%c,%s)", id, value, deleted ? "del" : "live");
    }
}
