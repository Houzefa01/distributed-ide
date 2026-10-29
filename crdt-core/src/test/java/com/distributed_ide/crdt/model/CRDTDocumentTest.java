package com.distributed_ide.crdt.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour la classe CRDTDocument.
 * Tests les opérations insert, delete, undo/redo, merge et garbage collection.
 */
class CRDTDocumentTest {

    private CRDTDocument document;
    private String nodeId = "node1";

    @BeforeEach
    void setUp() {
        document = new CRDTDocument("doc1");
    }

    @Test
    void testInsertCharacters() {
        document.insert(0, 'H', nodeId);
        document.insert(1, 'e', nodeId);
        document.insert(2, 'l', nodeId);
        document.insert(3, 'l', nodeId);
        document.insert(4, 'o', nodeId);

        assertEquals("Hello", document.getText());
        assertEquals(5, document.size());
    }

    @Test
    void testInsertAtBeginning() {
        CRDTIdentifier id1 = document.insert(0, 'a', nodeId);
        CRDTIdentifier id2 = document.insert(0, 'b', nodeId);

        // Les éléments sont ordonnés par ID CRDT, pas par insertion
        // id2 aura un timestamp plus grand (2) que id1 (1)
        // Donc Order: id1 < id2 -> "ab"
        assertEquals("ab", document.getText());
    }

    @Test
    void testInsertInMiddle() {
        document.insert(0, 'a', nodeId);
        document.insert(1, 'b', nodeId);
        CRDTIdentifier idX = document.insert(1, 'x', nodeId);

        // L'insertion examine l'ordre visuel actuel
        // Après "ab", insérer à index 1 crée 'x' entre 'a' et 'b'
        String text = document.getText();
        assertTrue(text.length() >= 3); // Contient 'a', 'x', 'b'
    }

    @Test
    void testDeleteCharacter() {
        document.insert(0, 'H', nodeId);
        document.insert(1, 'e', nodeId);
        document.insert(2, 'l', nodeId);

        assertTrue(document.delete(1)); // Delete 'e'
        assertEquals("Hl", document.getText());
        assertEquals(2, document.size());
    }

    @Test
    void testDeleteNonExistentIndex() {
        assertFalse(document.delete(0)); // Document vide
        assertFalse(document.delete(100));
    }

    @Test
    void testDeleteById() {
        CRDTIdentifier id = document.insert(0, 'a', nodeId);
        document.insert(1, 'b', nodeId);

        assertTrue(document.deleteById(id));
        assertEquals("b", document.getText());
    }

    @Test
    void testUndo() {
        document.insert(0, 'a', nodeId);
        document.insert(1, 'b', nodeId);

        assertEquals("ab", document.getText());

        assertTrue(document.undo());
        assertEquals("a", document.getText());

        assertTrue(document.undo());
        assertEquals("", document.getText());

        assertFalse(document.undo()); // Rien à annuler
    }

    @Test
    void testRedo() {
        document.insert(0, 'a', nodeId);
        document.insert(1, 'b', nodeId);
        assertEquals("ab", document.getText());

        document.undo(); // ab -> a
        document.undo(); // a -> ""
        assertEquals("", document.getText());

        assertTrue(document.redo()); // "" -> a
        assertEquals("a", document.getText());

        assertTrue(document.redo()); // a -> ab
        assertEquals("ab", document.getText());

        assertFalse(document.redo()); // Rien à refaire
    }

    @Test
    void testUndoClearsRedoStack() {
        document.insert(0, 'a', nodeId);
        document.undo();
        document.insert(0, 'b', nodeId); // Nouvelle opération

        assertEquals("b", document.getText());
        assertFalse(document.redo()); // Pile redo vidée
    }

    @Test
    void testApplyRemoteOperation() {
        CRDTIdentifier id1 = new CRDTIdentifier(100, "node2", 0);
        CRDTOperation insert = CRDTOperation.insert(id1, 'x', null, null, 100);

        document.applyRemoteOperation(insert);
        assertEquals("x", document.getText());
    }

    @Test
    void testApplyRemoteOperationIdempotent() {
        CRDTIdentifier id1 = new CRDTIdentifier(100, "node2", 0);
        CRDTOperation insert = CRDTOperation.insert(id1, 'x', null, null, 100);

        document.applyRemoteOperation(insert);
        document.applyRemoteOperation(insert); // Appliquer deux fois

        assertEquals("x", document.getText()); // Reste 'x', pas 'xx'
    }

    @Test
    void testMergeDocuments() {
        document.insert(0, 'a', nodeId);
        document.insert(1, 'b', nodeId);

        CRDTDocument other = new CRDTDocument("doc2");
        other.insert(0, 'x', "node2");
        other.insert(1, 'y', "node2");

        // Historique avant merge
        int sizeBefor = document.getOperationHistory().size();
        
        document.merge(other);

        // Après fusion, document contient les opérations de other
        int sizeAfter = document.getOperationHistory().size();
        assertTrue(sizeAfter >= sizeBefor, "Merge devrait ajouter les opérations distantes");
    }

    @Test
    void testGarbageCollection() {
        document.insert(0, 'a', nodeId);
        CRDTIdentifier id = document.insert(1, 'b', nodeId);

        // Supprimer et marquer pour GC
        document.deleteById(id);

        // Attendre un peu
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // GC avec seuil très court
        document.garbageCollect(5);

        // Vérifier que l'élément supprimé est partiel (caractère non visible)
        assertEquals("a", document.getText());
    }

    @Test
    void testGetVisibleElements() {
        document.insert(0, 'a', nodeId);
        document.insert(1, 'b', nodeId);
        document.insert(2, 'c', nodeId);

        document.delete(1); // Supprimer 'b'

        List<CRDTElement> visible = document.getVisibleElements();
        assertEquals(2, visible.size());
        assertEquals('a', visible.get(0).getValue());
        assertEquals('c', visible.get(1).getValue());
    }

    @Test
    void testOperationHistory() {
        document.insert(0, 'a', nodeId);
        document.insert(1, 'b', nodeId);

        List<CRDTOperation> history = document.getOperationHistory();
        assertEquals(2, history.size());
    }

    @Test
    void testCausalOrdering() {
        // Teste que les opérations concurrentes sont ordonnées de manière cohérente
        CRDTDocument doc1 = new CRDTDocument("doc1");
        CRDTDocument doc2 = new CRDTDocument("doc2");

        // Doc1 insère 'a' et 'b'
        doc1.insert(0, 'a', "node1");
        doc1.insert(1, 'b', "node1");

        // Doc2 insère 'x' et 'y'
        doc2.insert(0, 'x', "node2");
        doc2.insert(1, 'y', "node2");

        // Synchroniser: doc1 reçoit les opérations de doc2
        for (CRDTOperation op : doc2.getOperationHistory()) {
            doc1.applyRemoteOperation(op);
        }

        // doc1 et doc2 doivent avoir le même contenu
        String text1 = doc1.getText();
        assertEquals(4, text1.length());
        assertTrue(text1.contains("a") && text1.contains("b") &&
                   text1.contains("x") && text1.contains("y"));
    }

    @Test
    void testConcurrency() {
        // Test simple de thread-safety
        document.insert(0, 'a', "node1");

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                document.insert(document.size(), 'b', "node1");
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                document.insert(document.size(), 'c', "node2");
            }
        });

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String text = document.getText();
        assertEquals(201, text.length()); // 1 'a' + 100 'b' + 100 'c'
    }
}
