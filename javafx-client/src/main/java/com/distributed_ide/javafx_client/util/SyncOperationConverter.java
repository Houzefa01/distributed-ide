package com.distributed_ide.javafx_client.util;

import com.distributed_ide.crdt.model.CRDTIdentifier;
import com.distributed_ide.crdt.model.CRDTOperation;
import com.distributed_ide.sync.model.SyncMessage;

import java.util.ArrayList;
import java.util.List;

/** Converts SyncMessage.OperationData to CRDTOperation for the local replica. */
public final class SyncOperationConverter {

    private SyncOperationConverter() {}

    public static List<CRDTOperation> toCrdtOperations(List<SyncMessage.OperationData> operations) {
        if (operations == null || operations.isEmpty()) return List.of();
        List<CRDTOperation> result = new ArrayList<>();
        for (SyncMessage.OperationData op : operations) {
            CRDTOperation crdt = toCrdtOperation(op);
            if (crdt != null) result.add(crdt);
        }
        return result;
    }

    public static CRDTOperation toCrdtOperation(SyncMessage.OperationData op) {
        if (op == null || op.getOperationType() == null) return null;
        CRDTIdentifier id = toCrdtIdentifier(op);
        if (id == null) return null;
        long ts = op.getTimestamp();
        if ("INSERT".equals(op.getOperationType())) {
            return CRDTOperation.insert(id, op.getContent(), null, null, ts);
        }
        if ("DELETE".equals(op.getOperationType())) {
            return CRDTOperation.delete(id, ts);
        }
        return null;
    }

    private static CRDTIdentifier toCrdtIdentifier(SyncMessage.OperationData op) {
        try {
            long ts = Long.parseLong(op.getIdTimestamp());
            String nodeId = op.getIdNodeId() != null ? op.getIdNodeId() : "";
            int dis = op.getIdDisambiguator();
            return new CRDTIdentifier(ts, nodeId, dis);
        } catch (Exception e) {
            return null;
        }
    }
}
