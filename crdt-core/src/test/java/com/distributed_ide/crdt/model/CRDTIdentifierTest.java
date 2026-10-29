package com.distributed_ide.crdt.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour la classe CRDTIdentifier.
 */
class CRDTIdentifierTest {

    private CRDTIdentifier id1;
    private CRDTIdentifier id2;
    private CRDTIdentifier id3;

    @BeforeEach
    void setUp() {
        id1 = new CRDTIdentifier(100, "node1", 0);
        id2 = new CRDTIdentifier(100, "node1", 1);
        id3 = new CRDTIdentifier(101, "node1", 0);
    }

    @Test
    void testCompareTo_SameTimestampDifferentDisambiguator() {
        assertTrue(id1.compareTo(id2) < 0, "id1 devrait être < à id2");
        assertTrue(id2.compareTo(id1) > 0, "id2 devrait être > à id1");
    }

    @Test
    void testCompareTo_DifferentTimestamp() {
        assertTrue(id1.compareTo(id3) < 0, "id1 devrait être < à id3");
        assertTrue(id3.compareTo(id1) > 0, "id3 devrait être > à id1");
    }

    @Test
    void testCompareTo_SameIdentifier() {
        CRDTIdentifier same = new CRDTIdentifier(100, "node1", 0);
        assertEquals(0, id1.compareTo(same), "Identifiants identiques doivent avoir compareTo=0");
    }

    @Test
    void testCompareTo_DifferentNodeId() {
        CRDTIdentifier id4 = new CRDTIdentifier(100, "node2", 0);
        assertTrue(id1.compareTo(id4) < 0, "node1 < node2 lexicographiquement");
    }

    @Test
    void testEquals_SameValues() {
        CRDTIdentifier same = new CRDTIdentifier(100, "node1", 0);
        assertEquals(id1, same);
    }

    @Test
    void testEquals_DifferentDisambiguator() {
        assertNotEquals(id1, id2);
    }

    @Test
    void testHashCode_Consistency() {
        CRDTIdentifier same = new CRDTIdentifier(100, "node1", 0);
        assertEquals(id1.hashCode(), same.hashCode());
    }

    @Test
    void testGetters() {
        assertEquals(100, id1.getTimestamp());
        assertEquals("node1", id1.getNodeId());
        assertEquals(0, id1.getDisambiguator());
    }

    @Test
    void testNullNodeIdThrows() {
        assertThrows(NullPointerException.class, () -> 
            new CRDTIdentifier(100, null, 0));
    }
}
