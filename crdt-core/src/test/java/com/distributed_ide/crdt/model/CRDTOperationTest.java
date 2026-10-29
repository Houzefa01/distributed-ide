package com.distributed_ide.crdt.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour la classe CRDTOperation.
 */
class CRDTOperationTest {

    private CRDTIdentifier id1;
    private CRDTIdentifier id2;

    @BeforeEach
    void setUp() {
        id1 = new CRDTIdentifier(100, "node1", 0);
        id2 = new CRDTIdentifier(101, "node1", 0);
    }

    @Test
    void testInsertOperation() {
        CRDTOperation op = CRDTOperation.insert(id1, 'a', null, id2, System.currentTimeMillis());
        
        assertEquals(CRDTOperation.Type.INSERT, op.getType());
        assertEquals(id1, op.getId());
        assertEquals('a', op.getContent());
        assertEquals(null, op.getLeftId());
        assertEquals(id2, op.getRightId());
    }

    @Test
    void testDeleteOperation() {
        CRDTOperation op = CRDTOperation.delete(id1, System.currentTimeMillis());
        
        assertEquals(CRDTOperation.Type.DELETE, op.getType());
        assertEquals(id1, op.getId());
        assertEquals(null, op.getLeftId());
        assertEquals(null, op.getRightId());
    }

    @Test
    void testEquals_SameOperation() {
        CRDTOperation op1 = CRDTOperation.insert(id1, 'a', null, id2, 100);
        CRDTOperation op2 = CRDTOperation.insert(id1, 'a', null, id2, 200); // timestamp différent mais même ID
        
        assertEquals(op1, op2, "Opérations avec même ID doivent être égales");
    }

    @Test
    void testEquals_DifferentOperation() {
        CRDTOperation op1 = CRDTOperation.insert(id1, 'a', null, id2, 100);
        CRDTOperation op2 = CRDTOperation.insert(id2, 'b', null, null, 100);
        
        assertNotEquals(op1, op2);
    }

    @Test
    void testHashCode_Consistency() {
        CRDTOperation op1 = CRDTOperation.insert(id1, 'a', null, id2, 100);
        CRDTOperation op2 = CRDTOperation.insert(id1, 'a', null, id2, 100);
        
        assertEquals(op1.hashCode(), op2.hashCode());
    }

    @Test
    void testNullIdThrows() {
        assertThrows(NullPointerException.class, () ->
            CRDTOperation.insert(null, 'a', null, id2, 100));
    }

    @Test
    void testNullIdThrowsDelete() {
        assertThrows(NullPointerException.class, () ->
            CRDTOperation.delete(null, 100));
    }
}
