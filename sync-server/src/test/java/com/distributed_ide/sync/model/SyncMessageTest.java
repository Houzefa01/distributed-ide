package com.distributed_ide.sync.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour la classe SyncMessage.
 */
class SyncMessageTest {

    private SyncMessage message;

    @BeforeEach
    void setUp() {
        message = new SyncMessage(SyncMessage.MessageType.OPERATION, "client1", "doc1", "node1");
    }

    @Test
    void testMessageCreation() {
        assertEquals(SyncMessage.MessageType.OPERATION, message.getType());
        assertEquals("client1", message.getClientId());
        assertEquals("doc1", message.getDocumentId());
        assertEquals("node1", message.getNodeId());
    }

    @Test
    void testAddOperation() {
        SyncMessage.OperationData op = new SyncMessage.OperationData();
        op.setOperationType("INSERT");
        op.setContent('X');

        message.addOperation(op);
        assertEquals(1, message.getOperations().size());
        assertEquals('X', message.getOperations().get(0).getContent());
    }

    @Test
    void testOperationData() {
        SyncMessage.OperationData op = new SyncMessage.OperationData();
        op.setOperationType("INSERT");
        op.setIdTimestamp("100");
        op.setIdNodeId("node1");
        op.setIdDisambiguator(0);
        op.setContent('H');
        op.setTimestamp(System.currentTimeMillis());

        assertEquals("INSERT", op.getOperationType());
        assertEquals('H', op.getContent());
        assertEquals("node1", op.getIdNodeId());
    }

    @Test
    void testDifferentMessageTypes() {
        SyncMessage init = new SyncMessage(SyncMessage.MessageType.INIT, "c1", null, "n1");
        SyncMessage join = new SyncMessage(SyncMessage.MessageType.DOCUMENT_JOIN, "c1", "d1", "n1");
        SyncMessage ack = new SyncMessage(SyncMessage.MessageType.ACK, "c1", "d1", "n1");
        SyncMessage error = new SyncMessage(SyncMessage.MessageType.ERROR, "c1", null, "n1");

        assertEquals(SyncMessage.MessageType.INIT, init.getType());
        assertEquals(SyncMessage.MessageType.DOCUMENT_JOIN, join.getType());
        assertEquals(SyncMessage.MessageType.ACK, ack.getType());
        assertEquals(SyncMessage.MessageType.ERROR, error.getType());
    }

    @Test
    void testAcknowledgement() {
        SyncMessage ack = new SyncMessage(SyncMessage.MessageType.ACK, "c1", "d1", "n1");
        ack.setAcknowledgedTimestamp(12345);

        assertEquals(12345, ack.getAcknowledgedTimestamp());
    }

    @Test
    void testErrorMessage() {
        SyncMessage error = new SyncMessage(SyncMessage.MessageType.ERROR, "c1", null, "n1");
        error.setErrorMessage("Connection failed");

        assertEquals("Connection failed", error.getErrorMessage());
    }
}
