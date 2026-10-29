package com.distributed_ide.crdt.engine;

import com.distributed_ide.crdt.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour la classe CRDTEngine.
 * Tests les opérations multi-documents, synchronisation et listeners.
 */
class CRDTEngineTest {

    private CRDTEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CRDTEngine("node1");
    }

    @Test
    void testCreateDocument() {
        CRDTDocument doc = engine.createDocument("doc1");
        assertNotNull(doc);
        assertEquals("doc1", doc.getDocumentId());
        assertEquals(1, engine.getDocumentCount());
    }

    @Test
    void testGetDocument() {
        CRDTDocument doc = engine.createDocument("doc1");
        CRDTDocument retrieved = engine.getDocument("doc1");
        assertEquals(doc, retrieved);
    }

    @Test
    void testDeleteDocument() {
        engine.createDocument("doc1");
        assertTrue(engine.deleteDocument("doc1"));
        assertNull(engine.getDocument("doc1"));
        assertEquals(0, engine.getDocumentCount());
    }

    @Test
    void testInsertCharacter() {
        engine.createDocument("doc1");
        CRDTIdentifier id1 = engine.insertCharacter("doc1", 0, 'H');
        CRDTIdentifier id2 = engine.insertCharacter("doc1", 1, 'i');

        CRDTDocument doc = engine.getDocument("doc1");
        assertEquals("Hi", doc.getText());
    }

    @Test
    void testDeleteCharacter() {
        engine.createDocument("doc1");
        engine.insertCharacter("doc1", 0, 'a');
        engine.insertCharacter("doc1", 1, 'b');

        assertTrue(engine.deleteCharacter("doc1", 0));
        assertEquals("b", engine.getDocument("doc1").getText());
    }

    @Test
    void testInsertInNonExistentDocument() {
        assertThrows(IllegalArgumentException.class, () ->
            engine.insertCharacter("nonexistent", 0, 'a'));
    }

    @Test
    void testApplyRemoteOperation() {
        engine.createDocument("doc1");
        CRDTIdentifier id = new CRDTIdentifier(100, "node2", 0);
        CRDTOperation op = CRDTOperation.insert(id, 'x', null, null, 100);

        engine.applyRemoteOperation("doc1", op);
        assertEquals("x", engine.getDocument("doc1").getText());
    }

    @Test
    void testGetPendingOperations() {
        engine.createDocument("doc1");
        engine.insertCharacter("doc1", 0, 'a');
        engine.insertCharacter("doc1", 1, 'b');

        List<CRDTOperation> pending = engine.getPendingOperations();
        assertEquals(2, pending.size());
    }

    @Test
    void testClearPendingOperations() {
        engine.createDocument("doc1");
        engine.insertCharacter("doc1", 0, 'a');

        engine.clearPendingOperations();
        assertEquals(0, engine.getPendingOperations().size());
    }

    @Test
    void testMergeDocuments() {
        CRDTDocument doc1 = engine.createDocument("doc1");
        CRDTDocument doc2 = engine.createDocument("doc2");

        engine.insertCharacter("doc1", 0, 'a');
        engine.insertCharacter("doc2", 0, 'x');

        int sizeBefore = engine.getDocument("doc1").getOperationHistory().size();
        engine.mergeDocuments("doc2", "doc1");
        int sizeAfter = engine.getDocument("doc1").getOperationHistory().size();

        assertTrue(sizeAfter >= sizeBefore, "Merge devrait ajouter des opérations");
    }

    @Test
    void testUndo() {
        engine.createDocument("doc1");
        engine.insertCharacter("doc1", 0, 'a');
        engine.insertCharacter("doc1", 1, 'b');

        assertTrue(engine.undo("doc1"));
        assertEquals("a", engine.getDocument("doc1").getText());
    }

    @Test
    void testRedo() {
        engine.createDocument("doc1");
        engine.insertCharacter("doc1", 0, 'a');
        engine.insertCharacter("doc1", 1, 'b');

        assertEquals("ab", engine.getDocument("doc1").getText());
        
        engine.undo("doc1");
        assertEquals("a", engine.getDocument("doc1").getText());

        engine.redo("doc1");
        assertEquals("ab", engine.getDocument("doc1").getText());
    }

    @Test
    void testGarbageCollectAll() {
        engine.createDocument("doc1");
        engine.createDocument("doc2");

        engine.insertCharacter("doc1", 0, 'a');
        engine.insertCharacter("doc2", 0, 'x');

        // GC ne doit pas lever d'exception
        engine.garbageCollectAll(1000);
    }

    @Test
    void testOperationListener() {
        engine.createDocument("doc1");

        // Counter for listener callback
        int[] callCount = {0};
        CRDTEngine.OperationListener listener = (docId, op) -> {
            callCount[0]++;
        };

        engine.addOperationListener(listener);
        engine.insertCharacter("doc1", 0, 'a');

        assertEquals(1, callCount[0]);

        engine.removeOperationListener(listener);
        engine.insertCharacter("doc1", 1, 'b');

        assertEquals(1, callCount[0]); // Pas d'augmentation après suppression du listener
    }

    @Test
    void testMultipleDocuments() {
        engine.createDocument("doc1");
        engine.createDocument("doc2");
        engine.createDocument("doc3");

        engine.insertCharacter("doc1", 0, 'a');
        engine.insertCharacter("doc2", 0, 'b');
        engine.insertCharacter("doc3", 0, 'c');

        assertEquals("a", engine.getDocument("doc1").getText());
        assertEquals("b", engine.getDocument("doc2").getText());
        assertEquals("c", engine.getDocument("doc3").getText());
    }

    @Test
    void testNodeId() {
        assertEquals("node1", engine.getNodeId());
    }

    @Test
    void testListenerErrorHandling() {
        engine.createDocument("doc1");

        // Listener qui lance une exception
        CRDTEngine.OperationListener badListener = (docId, op) -> {
            throw new RuntimeException("Listener error");
        };

        engine.addOperationListener(badListener);

        // Ne doit pas lever d'exception malgré l'erreur du listener
        assertDoesNotThrow(() -> engine.insertCharacter("doc1", 0, 'a'));
    }
}
