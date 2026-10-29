package com.distributed_ide.lsp_bridge.sync;

import com.distributed_ide.crdt.model.CRDTIdentifier;
import com.distributed_ide.crdt.model.CRDTOperation;
import com.distributed_ide.sync.model.SyncMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Convertit les {@link SyncMessage.OperationData} reçues du sync-server
 * en {@link CRDTOperation} pour application sur une réplique CRDT locale.
 * Ne modifie pas la logique CRDT du sync-server.
 */
public final class SyncMessageOperationConverter {

    private static final Logger LOG = LoggerFactory.getLogger(SyncMessageOperationConverter.class);

    private SyncMessageOperationConverter() {}

    /**
     * Convertit une liste d'opérations sync en opérations CRDT.
     * Les entrées invalides sont ignorées (log warning).
     */
    public static List<CRDTOperation> toCrdtOperations(List<SyncMessage.OperationData> operations) {
        if (operations == null || operations.isEmpty()) {
            return List.of();
        }
        List<CRDTOperation> result = new ArrayList<>(operations.size());
        for (SyncMessage.OperationData op : operations) {
            CRDTOperation crdt = toCrdtOperation(op);
            if (crdt != null) {
                result.add(crdt);
            }
        }
        return result;
    }

    /**
     * Convertit une opération sync en opération CRDT, ou null si invalide.
     */
    public static CRDTOperation toCrdtOperation(SyncMessage.OperationData op) {
        if (op == null || op.getOperationType() == null) {
            return null;
        }
        CRDTIdentifier id = toCrdtIdentifier(op);
        if (id == null) {
            return null;
        }
        long timestamp = op.getTimestamp();
        try {
            if ("INSERT".equals(op.getOperationType())) {
                return CRDTOperation.insert(id, op.getContent(), null, null, timestamp);
            }
            if ("DELETE".equals(op.getOperationType())) {
                return CRDTOperation.delete(id, timestamp);
            }
        } catch (Exception e) {
            LOG.warn("Invalid operation data: {}", op, e);
            return null;
        }
        LOG.warn("Unknown operation type: {}", op.getOperationType());
        return null;
    }

    private static CRDTIdentifier toCrdtIdentifier(SyncMessage.OperationData op) {
        try {
            long ts = Long.parseLong(op.getIdTimestamp());
            String nodeId = op.getIdNodeId() != null ? op.getIdNodeId() : "";
            int dis = op.getIdDisambiguator();
            return new CRDTIdentifier(ts, nodeId, dis);
        } catch (Exception e) {
            LOG.warn("Invalid OperationData for identifier: {}", op, e);
            return null;
        }
    }
}
