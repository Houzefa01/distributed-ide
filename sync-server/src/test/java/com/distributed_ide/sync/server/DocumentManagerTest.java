package com.distributed_ide.sync.server;

import com.distributed_ide.crdt.engine.CRDTEngine;
import com.distributed_ide.crdt.model.CRDTDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour la classe DocumentManager.
 */
class DocumentManagerTest {

    private DocumentManager documentManager;
    private CRDTEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CRDTEngine("server-node");
        documentManager = new DocumentManager(engine);
    }

    @Test
    void testCreateDocument() {
        CRDTDocument doc = documentManager.createDocument("doc1", "user1");
        assertNotNull(doc);
        assertEquals("doc1", doc.getDocumentId());
    }

    @Test
    void testGetDocument() {
        documentManager.createDocument("doc1", "user1");
        CRDTDocument doc = documentManager.getDocument("doc1");
        assertNotNull(doc);
        assertEquals("doc1", doc.getDocumentId());
    }

    @Test
    void testGetNonExistentDocument() {
        CRDTDocument doc = documentManager.getDocument("nonexistent");
        assertNull(doc);
    }

    @Test
    void testDeleteDocument() {
        documentManager.createDocument("doc1", "user1");
        assertTrue(documentManager.deleteDocument("doc1"));
        assertNull(documentManager.getDocument("doc1"));
    }

    @Test
    void testDocumentMetadata() {
        documentManager.createDocument("doc1", "user1");
        DocumentManager.DocumentMetadata metadata = documentManager.getDocumentMetadata("doc1");

        assertNotNull(metadata);
        assertEquals("doc1", metadata.getDocumentId());
        assertEquals("user1", metadata.getOwnerId());
    }

    @Test
    void testListDocuments() {
        documentManager.createDocument("doc1", "user1");
        documentManager.createDocument("doc2", "user1");
        documentManager.createDocument("doc3", "user2");

        assertEquals(3, documentManager.listDocuments().size());
    }

    @Test
    void testSubscriberTracking() {
        documentManager.createDocument("doc1", "user1");
        DocumentManager.DocumentMetadata metadata = documentManager.getDocumentMetadata("doc1");

        metadata.incrementSubscribers();
        metadata.incrementSubscribers();
        assertEquals(2, metadata.getActiveSubscribers());

        metadata.decrementSubscribers();
        assertEquals(1, metadata.getActiveSubscribers());
    }

    @Test
    void testMetadataModificationTime() {
        documentManager.createDocument("doc1", "user1");
        DocumentManager.DocumentMetadata metadata = documentManager.getDocumentMetadata("doc1");

        long initialTime = metadata.getLastModifiedAt();
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        metadata.updateModificationTime();
        assertTrue(metadata.getLastModifiedAt() > initialTime);
    }
}
