package com.distributed_ide.javafx_client.lsp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Transport JSON-RPC sur stdin/stdout d'un processus LSP.
 *
 * Protocole LSP base :
 *   Content-Length: N\r\n
 *   \r\n
 *   {JSON payload de N octets}
 *
 * Thread-safe. Non-bloquant côté appelant (requêtes retournent CompletableFuture).
 * Un thread lecteur dédié parse les réponses/notifications en continu.
 */
public class JsonRpcTransport {

    private static final Logger LOG = LoggerFactory.getLogger(JsonRpcTransport.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OutputStream out;
    private final InputStream in;
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Pending requests: id → future
    private final ConcurrentHashMap<Integer, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();

    // Notification handler (method → handler)
    private volatile Consumer<JsonNode> notificationHandler;

    private Thread readerThread;

    public JsonRpcTransport(OutputStream out, InputStream in) {
        this.out = out;
        this.in = in;
    }

    // ═══════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════

    public void start() {
        if (running.compareAndSet(false, true)) {
            readerThread = new Thread(this::readLoop, "lsp-json-rpc-reader");
            readerThread.setDaemon(true);
            readerThread.start();
        }
    }

    public void stop() {
        running.set(false);
        if (readerThread != null) {
            readerThread.interrupt();
        }
        // Cancel all pending
        for (Map.Entry<Integer, CompletableFuture<JsonNode>> entry : pendingRequests.entrySet()) {
            entry.getValue().completeExceptionally(new IOException("Transport closed"));
        }
        pendingRequests.clear();
    }

    public boolean isRunning() {
        return running.get();
    }

    public void setNotificationHandler(Consumer<JsonNode> handler) {
        this.notificationHandler = handler;
    }

    // ═══════════════════════════════════════════════════════════
    //  SEND — REQUEST (expects response)
    // ═══════════════════════════════════════════════════════════

    /**
     * Envoie une requête JSON-RPC et retourne un Future pour la réponse.
     * Thread-safe.
     */
    public CompletableFuture<JsonNode> sendRequest(String method, JsonNode params) {
        int id = idCounter.getAndIncrement();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();

        // Timeout 30s
        future.orTimeout(30, TimeUnit.SECONDS);
        pendingRequests.put(id, future);

        try {
            var root = MAPPER.createObjectNode();
            root.put("jsonrpc", "2.0");
            root.put("id", id);
            root.put("method", method);
            if (params != null) root.set("params", params);
            writeMessage(root);
        } catch (Exception e) {
            pendingRequests.remove(id);
            future.completeExceptionally(e);
        }

        return future;
    }

    // ═══════════════════════════════════════════════════════════
    //  SEND — NOTIFICATION (no response expected)
    // ═══════════════════════════════════════════════════════════

    /**
     * Envoie une notification JSON-RPC (pas d'id, pas de réponse).
     */
    public void sendNotification(String method, JsonNode params) {
        try {
            var root = MAPPER.createObjectNode();
            root.put("jsonrpc", "2.0");
            root.put("method", method);
            if (params != null) root.set("params", params);
            writeMessage(root);
        } catch (Exception e) {
            LOG.warn("Send notification failed: {}", method, e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  WIRE FORMAT — Content-Length header + JSON body
    // ═══════════════════════════════════════════════════════════

    private synchronized void writeMessage(JsonNode message) throws IOException {
        byte[] body = MAPPER.writeValueAsBytes(message);
        String header = "Content-Length: " + body.length + "\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    // ═══════════════════════════════════════════════════════════
    //  READER LOOP — parses Content-Length framed messages
    // ═══════════════════════════════════════════════════════════

    private void readLoop() {
        try (BufferedInputStream bis = new BufferedInputStream(in, 65536)) {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                String headerBlock = readHeaders(bis);
                if (headerBlock == null) break;

                int contentLength = parseContentLength(headerBlock);
                if (contentLength <= 0) {
                    LOG.warn("Invalid Content-Length: {}", headerBlock);
                    continue;
                }

                byte[] body = readExactly(bis, contentLength);
                if (body == null) break;

                try {
                    JsonNode message = MAPPER.readTree(body);
                    dispatch(message);
                } catch (Exception e) {
                    LOG.trace("Failed to parse JSON-RPC message: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                LOG.debug("LSP reader stream ended: {}", e.getMessage());
            }
        } finally {
            running.set(false);
        }
    }

    private String readHeaders(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        int prev = -1;
        int crlfCount = 0;
        while (true) {
            int b = stream.read();
            if (b == -1) return null;
            sb.append((char) b);
            if (b == '\n' && prev == '\r') {
                crlfCount++;
            } else if (b != '\r') {
                crlfCount = 0;
            }
            if (crlfCount >= 2) break;
            prev = b;
        }
        return sb.toString();
    }

    private int parseContentLength(String headers) {
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    return Integer.parseInt(line.substring(15).trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private byte[] readExactly(InputStream stream, int count) throws IOException {
        byte[] buf = new byte[count];
        int offset = 0;
        while (offset < count) {
            int read = stream.read(buf, offset, count - offset);
            if (read == -1) return null;
            offset += read;
        }
        return buf;
    }

    // ═══════════════════════════════════════════════════════════
    //  DISPATCH — route responses vs notifications
    // ═══════════════════════════════════════════════════════════

    private void dispatch(JsonNode message) {
        // Response to a request (has "id" + "result" or "error")
        if (message.has("id") && (message.has("result") || message.has("error"))) {
            int id = message.get("id").asInt(-1);
            CompletableFuture<JsonNode> future = pendingRequests.remove(id);
            if (future != null) {
                if (message.has("error")) {
                    String errMsg = message.get("error").has("message")
                            ? message.get("error").get("message").asText()
                            : "Unknown LSP error";
                    future.completeExceptionally(new RuntimeException(errMsg));
                } else {
                    future.complete(message.get("result"));
                }
            }
            return;
        }

        // Notification from server (has "method", no "id")
        if (message.has("method") && !message.has("id")) {
            Consumer<JsonNode> handler = notificationHandler;
            if (handler != null) {
                handler.accept(message);
            }
            return;
        }

        // Server request (has "method" + "id") — we respond with empty result
        if (message.has("method") && message.has("id")) {
            try {
                var response = MAPPER.createObjectNode();
                response.put("jsonrpc", "2.0");
                response.set("id", message.get("id"));
                response.putNull("result");
                writeMessage(response);
            } catch (IOException e) {
                LOG.trace("Failed to respond to server request", e);
            }
        }
    }
}
