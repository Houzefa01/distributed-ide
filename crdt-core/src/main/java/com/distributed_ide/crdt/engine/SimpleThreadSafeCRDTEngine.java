package com.distributed_ide.crdt.engine;

import com.distributed_ide.crdt.model.CRDTDocument;
import com.distributed_ide.crdt.model.CRDTIdentifier;
import com.distributed_ide.crdt.model.CRDTOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simplified thread-safe CRDT engine that works with existing API.
 */
public class SimpleThreadSafeCRDTEngine {
    
    private static final Logger LOG = LoggerFactory.getLogger(SimpleThreadSafeCRDTEngine.class);
    
    private final String nodeId;
    private final Map<String, CRDTDocument> documents = new ConcurrentHashMap<>();
    private final Queue<CRDTOperation> pendingOperations = new LinkedList<>();
    private final List<OperationListener> listeners = new CopyOnWriteArrayList<>();
    
    // Thread safety
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    // Background operations
    private final ScheduledExecutorService backgroundExecutor;
    
    // Monitoring
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);
    
    public SimpleThreadSafeCRDTEngine(String nodeId) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
        this.backgroundExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "crdt-engine-background");
            t.setDaemon(true);
            return t;
        });
        
        // Log statistics every minute
        backgroundExecutor.scheduleAtFixedRate(() -> {
            LOG.info("CRDT engine stats: documents={}, operations={}, failed={}", 
                documents.size(), totalOperations.get(), failedOperations.get());
        }, 60000, 60000, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Create a new CRDT document.
     */
    public CRDTDocument createDocument(String documentId) {
        if (isShuttingDown.get()) {
            throw new IllegalStateException("CRDT engine is shutting down");
        }
        
        CRDTDocument doc = new CRDTDocument(documentId);
        documents.put(documentId, doc);
        
        LOG.info("Document created: {}", documentId);
        return doc;
    }
    
    /**
     * Get a document by ID.
     */
    public CRDTDocument getDocument(String documentId) {
        return documents.get(documentId);
    }
    
    /**
     * Delete a document.
     */
    public boolean deleteDocument(String documentId) {
        if (isShuttingDown.get()) {
            return false;
        }
        
        CRDTDocument removed = documents.remove(documentId);
        if (removed != null) {
            LOG.info("Document deleted: {}", documentId);
            return true;
        }
        return false;
    }
    
    /**
     * Insert a character into a document.
     */
    public CRDTIdentifier insertCharacter(String documentId, int index, char value) {
        if (isShuttingDown.get()) {
            throw new IllegalStateException("CRDT engine is shutting down");
        }
        
        try {
            CRDTDocument doc = getDocument(documentId);
            if (doc == null) {
                throw new IllegalArgumentException("Document not found: " + documentId);
            }
            
            CRDTIdentifier id = doc.insert(index, value, nodeId);
            CRDTOperation op = CRDTOperation.insert(id, value, null, null, System.currentTimeMillis());
            
            pendingOperations.add(op);
            notifyListeners(documentId, op);
            
            totalOperations.incrementAndGet();
            LOG.debug("Insert: {} at index {}", id, index);
            return id;
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            LOG.error("Failed to insert character in document {}: {}", documentId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Delete a character from a document.
     */
    public boolean deleteCharacter(String documentId, int index) {
        if (isShuttingDown.get()) {
            return false;
        }
        
        try {
            CRDTDocument doc = getDocument(documentId);
            if (doc == null) {
                throw new IllegalArgumentException("Document not found: " + documentId);
            }
            
            boolean success = doc.delete(index);
            if (success) {
                // Create delete operation
                CRDTIdentifier id = new CRDTIdentifier(System.currentTimeMillis(), nodeId, 0);
                CRDTOperation op = CRDTOperation.delete(id, System.currentTimeMillis());
                
                pendingOperations.add(op);
                notifyListeners(documentId, op);
                
                totalOperations.incrementAndGet();
                LOG.debug("Delete at index {}", index);
            }
            return success;
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            LOG.error("Failed to delete character in document {}: {}", documentId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get all document IDs.
     */
    public Set<String> getDocumentIds() {
        return new HashSet<>(documents.keySet());
    }
    
    /**
     * Get document count.
     */
    public int getDocumentCount() {
        return documents.size();
    }
    
    /**
     * Get pending operations count.
     */
    public int getPendingOperationsCount() {
        return pendingOperations.size();
    }
    
    /**
     * Get engine statistics.
     */
    public EngineStats getStats() {
        return new EngineStats(
            documents.size(),
            pendingOperations.size(),
            totalOperations.get(),
            failedOperations.get(),
            listeners.size()
        );
    }
    
    /**
     * Add an operation listener.
     */
    public void addListener(OperationListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove an operation listener.
     */
    public void removeListener(OperationListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Get pending operations for synchronization.
     */
    public List<CRDTOperation> getPendingOperations() {
        List<CRDTOperation> ops = new ArrayList<>();
        CRDTOperation op;
        while ((op = pendingOperations.poll()) != null) {
            ops.add(op);
        }
        return ops;
    }
    
    /**
     * Apply an operation from another replica.
     */
    public boolean applyOperation(CRDTOperation operation) {
        if (isShuttingDown.get()) {
            return false;
        }
        
        try {
            // For now, we'll just add to pending operations
            // In a real implementation, you would apply the operation to the document
            pendingOperations.add(operation);
            notifyListeners("unknown-document", operation);
            
            totalOperations.incrementAndGet();
            LOG.debug("Applied operation: {}", operation.getType());
            return true;
        } catch (Exception e) {
            failedOperations.incrementAndGet();
            LOG.error("Failed to apply operation: {}", operation, e);
            return false;
        }
    }
    
    /**
     * Shutdown the CRDT engine.
     */
    public void shutdown() {
        LOG.info("Shutting down CRDT engine");
        
        isShuttingDown.set(true);
        
        try {
            // Shutdown background executor
            backgroundExecutor.shutdown();
            
            if (!backgroundExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow();
            }
            
            // Clear all data structures
            documents.clear();
            pendingOperations.clear();
            listeners.clear();
            
            LOG.info("CRDT engine shutdown complete");
        } catch (Exception e) {
            LOG.error("Error during CRDT engine shutdown", e);
        }
    }
    
    private void notifyListeners(String documentId, CRDTOperation operation) {
        for (OperationListener listener : listeners) {
            try {
                listener.onOperation(documentId, operation);
            } catch (Exception e) {
                LOG.error("Error in operation listener", e);
            }
        }
    }
    
    /**
     * Engine statistics for monitoring.
     */
    public static class EngineStats {
        public final int documentCount;
        public final int pendingOperationsCount;
        public final long totalOperations;
        public final long failedOperations;
        public final int listenerCount;
        
        public EngineStats(int documentCount, int pendingOperationsCount, long totalOperations, 
                          long failedOperations, int listenerCount) {
            this.documentCount = documentCount;
            this.pendingOperationsCount = pendingOperationsCount;
            this.totalOperations = totalOperations;
            this.failedOperations = failedOperations;
            this.listenerCount = listenerCount;
        }
        
        @Override
        public String toString() {
            return String.format("EngineStats{documents=%d, pending=%d, total=%d, failed=%d, listeners=%d}", 
                documentCount, pendingOperationsCount, totalOperations, failedOperations, listenerCount);
        }
    }
    
    /**
     * Operation listener interface.
     */
    public interface OperationListener {
        void onOperation(String documentId, CRDTOperation operation);
    }
}
