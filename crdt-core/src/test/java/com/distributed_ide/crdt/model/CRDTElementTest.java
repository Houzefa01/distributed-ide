package com.distributed_ide.crdt.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour la classe CRDTElement.
 */
class CRDTElementTest {

    private CRDTIdentifier id;
    private CRDTElement element;

    @BeforeEach
    void setUp() {
        id = new CRDTIdentifier(100, "node1", 0);
        element = new CRDTElement(id, 'a');
    }

    @Test
    void testElementCreation() {
        assertEquals(id, element.getId());
        assertEquals('a', element.getValue());
        assertFalse(element.isDeleted());
    }

    @Test
    void testMarkDeleted() {
        assertFalse(element.isDeleted());
        element.markDeleted();
        assertTrue(element.isDeleted());
    }

    @Test
    void testEquals_SameId() {
        CRDTElement other = new CRDTElement(id, 'b'); // contenu différent mais même ID
        assertEquals(element, other, "Éléments avec même ID doivent être égaux");
    }

    @Test
    void testEquals_DifferentId() {
        CRDTIdentifier id2 = new CRDTIdentifier(101, "node1", 0);
        CRDTElement other = new CRDTElement(id2, 'a');
        assertNotEquals(element, other);
    }

    @Test
    void testCompareTo() {
        CRDTIdentifier id1 = new CRDTIdentifier(100, "node1", 0);
        CRDTIdentifier id2 = new CRDTIdentifier(101, "node1", 0);
        
        CRDTElement elem1 = new CRDTElement(id1, 'a');
        CRDTElement elem2 = new CRDTElement(id2, 'b');
        
        assertTrue(elem1.compareTo(elem2) < 0);
    }

    @Test
    void testGetCreatedAt() {
        long before = System.currentTimeMillis();
        CRDTElement newElem = new CRDTElement(id, 'x');
        long after = System.currentTimeMillis();
        
        assertTrue(newElem.getCreatedAt() >= before && newElem.getCreatedAt() <= after);
    }

    @Test
    void testNullIdThrows() {
        assertThrows(NullPointerException.class, () ->
            new CRDTElement(null, 'a'));
    }
}
