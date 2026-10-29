package com.distributed_ide.lsp_bridge.bridge;

import com.distributed_ide.lsp_bridge.process.LspProcessManager;
import com.distributed_ide.lsp_bridge.transport.LspMessageTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class LspBridgeSession {

    private static final Logger LOG = LoggerFactory.getLogger(LspBridgeSession.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LspProcessManager.LspProcessHandle processHandle;
    private final Map<String, SessionClient> clients = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, PendingRequest> pending = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private Thread readerThread;
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lsp-bridge-write");
        t.setDaemon(true);
        return t;
    });

    public LspBridgeSession(LspProcessManager.LspProcessHandle processHandle) {
        this.processHandle = processHandle;
    }

    public void addClient(String clientId, SessionClient client) {
        clients.put(clientId, client);
    }

    public void removeClient(String clientId) {
        clients.remove(clientId);
        pending.entrySet().removeIf(e -> e.getValue().clientId.equals(clientId));
    }

    public void startReading() {
        if (readerThread != null) return;
        readerThread = new Thread(this::readLoop, "lsp-read-" + processHandle.getKey());
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void forwardFromClient(String clientId, String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isObject()) return;
            ObjectNode obj = (ObjectNode) root;
            if (obj.has("id")) {
                long origId = obj.get("id").asLong();
                long internalId = nextId.getAndIncrement();
                pending.put(internalId, new PendingRequest(clientId, origId));
                obj.put("id", internalId);
            }
            writeToProcess(MAPPER.writeValueAsString(obj));
        } catch (Exception e) {
            LOG.error("Forward error", e);
            SessionClient c = clients.get(clientId);
            if (c != null) c.sendError(e.getMessage());
        }
    }

    private synchronized void writeToProcess(String json) throws IOException {
        if (!processHandle.isAlive()) throw new IOException("Process not alive");
        LspMessageTransport.writeMessage(processHandle.getOutputStream(), json);
    }

    private void readLoop() {
        InputStream in = processHandle.getInputStream();
        while (running && processHandle.isAlive()) {
            try {
                String msg = LspMessageTransport.readMessage(in);
                if (msg == null) break;
                dispatch(msg);
            } catch (IOException e) {
                if (running) LOG.error("Read error", e);
                break;
            }
        }
    }

    private void dispatch(String message) {
        try {
            JsonNode root = MAPPER.readTree(message);
            if (!root.isObject()) return;
            ObjectNode obj = (ObjectNode) root;
            if (obj.has("id")) {
                long internalId = obj.get("id").asLong();
                PendingRequest pr = pending.remove(internalId);
                if (pr != null) {
                    obj.put("id", pr.originalId);
                    SessionClient c = clients.get(pr.clientId);
                    if (c != null) c.send(MAPPER.writeValueAsString(obj));
                }
            } else {
                for (SessionClient c : clients.values()) {
                    try { c.send(message); } catch (Exception e) { LOG.warn("Send failed", e); }
                }
            }
        } catch (Exception e) {
            LOG.error("Dispatch error", e);
        }
    }

    /**
     * Envoie une notification JSON-RPC au LSP sans bloquer (ex. textDocument/didChange).
     */
    public CompletableFuture<Void> pushToLspAsync(String jsonRpcNotification) {
        return CompletableFuture.runAsync(() -> {
            try {
                writeToProcess(jsonRpcNotification);
            } catch (IOException e) {
                LOG.warn("pushToLsp failed", e);
            }
        }, writeExecutor);
    }

    public void shutdown() {
        running = false;
        if (readerThread != null) readerThread.interrupt();
        writeExecutor.shutdown();
        processHandle.destroy();
    }

    public boolean hasClients() { return !clients.isEmpty(); }

    private static final class PendingRequest {
        final String clientId;
        final long originalId;
        PendingRequest(String clientId, long originalId) { this.clientId = clientId; this.originalId = originalId; }
    }

    public interface SessionClient {
        void send(String json);
        void sendError(String message);
    }
}
