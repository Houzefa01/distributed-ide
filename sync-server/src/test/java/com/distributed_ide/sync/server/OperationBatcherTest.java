package com.distributed_ide.sync.server;

import com.distributed_ide.sync.model.SyncMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests pour la classe OperationBatcher.
 */
class OperationBatcherTest {

    private OperationBatcher batcher;

    @BeforeEach
    void setUp() {
        batcher = new OperationBatcher(3, 1000); // Batch de 3, timeout 1s
    }

    @Test
    void testAddOperationUnderLimit() {
        SyncMessage.OperationData op = new SyncMessage.OperationData();
        op.setOperationType("INSERT");
        op.setContent('A');

        boolean shouldFlush = batcher.addOperation("doc1", op);
        assertFalse(shouldFlush);
        assertEquals(1, batcher.getBatchSize("doc1"));
    }

    @Test
    void testAddOperationReachLimit() {
        SyncMessage.OperationData op1 = createOperation('A');
        SyncMessage.OperationData op2 = createOperation('B');
        SyncMessage.OperationData op3 = createOperation('C');

        batcher.addOperation("doc1", op1);
        batcher.addOperation("doc1", op2);
        boolean shouldFlush = batcher.addOperation("doc1", op3);

        assertTrue(shouldFlush);
        assertEquals(3, batcher.getBatchSize("doc1"));
    }

    @Test
    void testFlushBatch() {
        SyncMessage.OperationData op1 = createOperation('A');
        SyncMessage.OperationData op2 = createOperation('B');

        batcher.addOperation("doc1", op1);
        batcher.addOperation("doc1", op2);

        List<SyncMessage.OperationData> batch = batcher.flushBatch("doc1");
        assertEquals(2, batch.size());
        assertEquals('A', batch.get(0).getContent());
        assertEquals('B', batch.get(1).getContent());

        assertEquals(0, batcher.getBatchSize("doc1"));
    }

    @Test
    void testMultipleDocuments() {
        batcher.addOperation("doc1", createOperation('A'));
        batcher.addOperation("doc2", createOperation('X'));
        batcher.addOperation("doc1", createOperation('B'));

        assertEquals(2, batcher.getBatchSize("doc1"));
        assertEquals(1, batcher.getBatchSize("doc2"));
    }

    @Test
    void testFlushNonExistentBatch() {
        List<SyncMessage.OperationData> batch = batcher.flushBatch("nonexistent");
        assertTrue(batch.isEmpty());
    }

    private SyncMessage.OperationData createOperation(char content) {
        SyncMessage.OperationData op = new SyncMessage.OperationData();
        op.setOperationType("INSERT");
        op.setContent(content);
        op.setTimestamp(System.currentTimeMillis());
        return op;
    }
}
