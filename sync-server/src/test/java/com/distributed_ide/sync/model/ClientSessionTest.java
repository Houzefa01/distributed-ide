package com.distributed_ide.sync.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour la classe ClientSession.
 */
class ClientSessionTest {

    private ClientSession session;

    @BeforeEach
    void setUp() {
        session = new ClientSession("client1", "node1");
    }

    @Test
    void testSessionCreation() {
        assertEquals("client1", session.getClientId());
        assertEquals("node1", session.getNodeId());
        assertTrue(session.isConnected());
    }

    @Test
    void testDocumentSubscription() {
        session.subscribeToDocument("doc1");
        session.subscribeToDocument("doc2");

        assertTrue(session.getSubscribedDocuments().contains("doc1"));
        assertTrue(session.getSubscribedDocuments().contains("doc2"));
        assertEquals(2, session.getSubscribedDocuments().size());
    }

    @Test
    void testDocumentUnsubscription() {
        session.subscribeToDocument("doc1");
        session.unsubscribeFromDocument("doc1");

        assertFalse(session.getSubscribedDocuments().contains("doc1"));
    }

    @Test
    void testPendingOperations() {
        SyncMessage.OperationData op1 = new SyncMessage.OperationData();
        op1.setOperationType("INSERT");
        op1.setContent('A');

        SyncMessage.OperationData op2 = new SyncMessage.OperationData();
        op2.setOperationType("INSERT");
        op2.setContent('B');

        session.addPendingOperation(op1);
        session.addPendingOperation(op2);

        assertEquals(2, session.getPendingOperationCount());
        assertEquals('A', session.pollPendingOperation().getContent());
        assertEquals(1, session.getPendingOperationCount());
    }

    @Test
    void testHeartbeat() {
        long initialHeartbeat = session.getLastHeartbeat();
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        session.updateHeartbeat();
        long newHeartbeat = session.getLastHeartbeat();

        assertTrue(newHeartbeat > initialHeartbeat);
    }

    @Test
    void testIsAlive() {
        assertTrue(session.isAlive(1000)); // 1 sec timeout

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertFalse(session.isAlive(50)); // 50ms timeout should be false after 100ms sleep
    }

    @Test
    void testConnectionDuration() {
        long duration = session.getConnectionDuration();
        assertTrue(duration >= 0);
        assertTrue(duration < 100); // Moins de 100ms depuis la création
    }

    @Test
    void testConnectedFlag() {
        assertTrue(session.isConnected());
        session.setConnected(false);
        assertFalse(session.isConnected());
    }
}
