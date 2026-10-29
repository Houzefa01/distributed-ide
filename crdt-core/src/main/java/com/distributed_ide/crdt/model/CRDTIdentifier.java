package com.distributed_ide.crdt.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Identifiant unique pour les éléments CRDT.
 * 
 * Structure: (timestamp, nodeId, disambiguator)
 * - timestamp: horodatage logique (causal ordering)
 * - nodeId: identifiant unique du nœud (replica)
 * - disambiguator: index pour les opérations simultanées du même nœud
 * 
 * Garantit un ordre total avec comparaison lexicographique.
 */
public class CRDTIdentifier implements Comparable<CRDTIdentifier>, Serializable {
    private static final long serialVersionUID = 1L;

    private final long timestamp;
    private final String nodeId;
    private final int disambiguator;

    public CRDTIdentifier(long timestamp, String nodeId, int disambiguator) {
        this.timestamp = timestamp;
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
        this.disambiguator = disambiguator;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getDisambiguator() {
        return disambiguator;
    }

    @Override
    public int compareTo(CRDTIdentifier other) {
        // Ordre lexicographique: timestamp -> nodeId -> disambiguator
        if (this.timestamp != other.timestamp) {
            return Long.compare(this.timestamp, other.timestamp);
        }
        int nodeComparison = this.nodeId.compareTo(other.nodeId);
        if (nodeComparison != 0) {
            return nodeComparison;
        }
        return Integer.compare(this.disambiguator, other.disambiguator);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CRDTIdentifier)) return false;
        CRDTIdentifier that = (CRDTIdentifier) o;
        return timestamp == that.timestamp &&
                disambiguator == that.disambiguator &&
                nodeId.equals(that.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, nodeId, disambiguator);
    }

    @Override
    public String toString() {
        return String.format("ID(%d,%s,%d)", timestamp, nodeId, disambiguator);
    }
}
