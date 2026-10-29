package com.distributed_ide.lsp_bridge.sync;

import com.distributed_ide.crdt.model.CRDTOperation;
import com.distributed_ide.sync.model.SyncMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyncMessageOperationConverterTest {

    @Test
    void toCrdtOperations_empty_returnsEmpty() {
        assertTrue(SyncMessageOperationConverter.toCrdtOperations(List.of()).isEmpty());
        assertTrue(SyncMessageOperationConverter.toCrdtOperations(null).isEmpty());
    }

    @Test
    void toCrdtOperation_insert_validData() {
        SyncMessage.OperationData op = new SyncMessage.OperationData();
        op.setOperationType("INSERT");
        op.setTimestamp(1L);
        op.setIdTimestamp("10");
        op.setIdNodeId("node1");
        op.setIdDisambiguator(0);
        op.setContent('a');

        CRDTOperation crdt = SyncMessageOperationConverter.toCrdtOperation(op);
        assertNotNull(crdt);
        assertEquals(CRDTOperation.Type.INSERT, crdt.getType());
        assertEquals('a', crdt.getContent());
        assertEquals(10L, crdt.getId().getTimestamp());
        assertEquals("node1", crdt.getId().getNodeId());
    }

    @Test
    void toCrdtOperation_delete_validData() {
        SyncMessage.OperationData op = new SyncMessage.OperationData();
        op.setOperationType("DELETE");
        op.setTimestamp(2L);
        op.setIdTimestamp("20");
        op.setIdNodeId("n2");
        op.setIdDisambiguator(1);

        CRDTOperation crdt = SyncMessageOperationConverter.toCrdtOperation(op);
        assertNotNull(crdt);
        assertEquals(CRDTOperation.Type.DELETE, crdt.getType());
    }

    @Test
    void toCrdtOperation_invalid_returnsNull() {
        assertNull(SyncMessageOperationConverter.toCrdtOperation(null));
        SyncMessage.OperationData op = new SyncMessage.OperationData();
        op.setOperationType("UNKNOWN");
        op.setIdTimestamp("1");
        op.setIdNodeId("n");
        assertNull(SyncMessageOperationConverter.toCrdtOperation(op));
    }
}
