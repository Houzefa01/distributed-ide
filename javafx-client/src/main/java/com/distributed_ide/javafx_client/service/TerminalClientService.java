package com.distributed_ide.javafx_client.service;

import com.distributed_ide.terminal.model.TerminalMessage;
import com.distributed_ide.terminal.model.TerminalSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket client for the terminal service: session creation, execution, log reception.
 * Automatic reconnection with exponential backoff. Session isolation (one sessionId per client).
 * All network operations are non-blocking (dedicated executor).
 */
public class TerminalClientService {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalClientService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final long INITIAL_RECONNECT_DELAY_MS = 1_000;
    private static final long MAX_RECONNECT_DELAY_MS = 30_000;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    /** -1 = unlimited reconnection */
    private static final int MAX_RECONNECT_ATTEMPTS = -1;

    private volatile WebSocketClient client;
    private volatile String sessionId;
    private final String userId = "javafx-" + UUID.randomUUID().toString().substring(0, 8);
    private volatile String wsUri;
    private volatile Listener listener;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "terminal-ws-io");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "terminal-reconnect");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicBoolean disconnectRequested = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);

    public interface Listener {
        void onConnected();
        void onDisconnected(String reason);
        void onReconnecting(int attempt);
        void onSessionCreated(String sessionId);
        void onOutput(String line, boolean isError);
        void onError(String message);
        void onHistoryReceived(java.util.List<String> history);
        void onTabCreated(String tabId, String tabName);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Non-blocking connection: launches connection on the dedicated executor.
     */
    public void connect(String uri) {
        if (uri == null || uri.isEmpty()) return;
        wsUri = uri;
        disconnectRequested.set(false);
        reconnectAttempt.set(0);
        ioExecutor.execute(this::doConnect);
    }

    private void doConnect() {
        try {
            URI u = new URI(wsUri);
            client = new WebSocketClient(u) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    LOG.info("Terminal WebSocket open");
                    reconnectAttempt.set(0);
                    notifyOnConnected();
                    createSession();
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    LOG.info("Terminal WebSocket closed: {} (code={})", reason, code);
                    sessionId = null;
                    client = null;
                    notifyOnDisconnected(reason != null ? reason : "closed");
                    if (!disconnectRequested.get()) scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    LOG.warn("Terminal WebSocket error", ex);
                    if (listener != null) listener.onError(ex.getMessage());
                }
            };
            client.connect();
        } catch (Exception e) {
            LOG.error("Terminal connect failed", e);
            if (listener != null) listener.onError(e.getMessage());
            if (!disconnectRequested.get()) scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (reconnectScheduled.getAndSet(true)) return;
        if (MAX_RECONNECT_ATTEMPTS >= 0 && reconnectAttempt.get() >= MAX_RECONNECT_ATTEMPTS) {
            reconnectScheduled.set(false);
            if (listener != null) listener.onError("Max reconnection attempts reached");
            return;
        }
        int attempt = reconnectAttempt.incrementAndGet();
        long delayMs = (long) Math.min(
            INITIAL_RECONNECT_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, attempt - 1),
            MAX_RECONNECT_DELAY_MS
        );
        notifyOnReconnecting(attempt);
        reconnectScheduler.schedule(() -> {
            reconnectScheduled.set(false);
            if (disconnectRequested.get()) return;
            ioExecutor.execute(TerminalClientService.this::doConnect);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void notifyOnConnected() {
        if (listener != null) javafx.application.Platform.runLater(() -> listener.onConnected());
    }

    private void notifyOnDisconnected(String reason) {
        if (listener != null) javafx.application.Platform.runLater(() -> listener.onDisconnected(reason));
    }

    private void notifyOnReconnecting(int attempt) {
        if (listener != null) javafx.application.Platform.runLater(() -> listener.onReconnecting(attempt));
    }

    public void disconnect() {
        disconnectRequested.set(true);
        WebSocketClient c = client;
        client = null;
        sessionId = null;
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                LOG.warn("Error closing terminal client", e);
            }
        }
    }

    public void shutdown() {
        disconnect();
        ioExecutor.shutdownNow();
        reconnectScheduler.shutdownNow();
    }

    /**
     * Non-blocking send on the executor.
     */
    public void execute(String code, String language) {
        if (client == null || !client.isOpen() || sessionId == null) return;
        String sid = sessionId;
        String lang = language != null ? language : "java";
        ioExecutor.execute(() -> {
            try {
                TerminalMessage msg = TerminalMessage.execute(sid, userId, code, lang);
                if (client != null && client.isOpen()) client.send(MAPPER.writeValueAsString(msg));
            } catch (Exception e) {
                LOG.warn("Send execute failed", e);
                if (listener != null) javafx.application.Platform.runLater(() -> listener.onError(e.getMessage()));
            }
        });
    }

    public void sendInput(String line) {
        execute(line, "shell");
    }

    /**
     * Request command history from server (non-blocking).
     */
    public void requestHistory(int count) {
        if (client == null || !client.isOpen() || sessionId == null) return;
        String sid = sessionId;
        ioExecutor.execute(() -> {
            try {
                TerminalMessage msg = TerminalMessage.historyRequest(sid, count);
                if (client != null && client.isOpen()) client.send(MAPPER.writeValueAsString(msg));
            } catch (Exception e) {
                LOG.warn("Request history failed", e);
            }
        });
    }

    /**
     * Create a new terminal tab (non-blocking).
     */
    public void createTab(String tabId, String tabName) {
        if (client == null || !client.isOpen() || sessionId == null) return;
        String sid = sessionId;
        ioExecutor.execute(() -> {
            try {
                TerminalMessage msg = TerminalMessage.createTab(sid, tabId, tabName);
                if (client != null && client.isOpen()) client.send(MAPPER.writeValueAsString(msg));
            } catch (Exception e) {
                LOG.warn("Create tab failed", e);
            }
        });
    }

    /**
     * Switch to a different tab (non-blocking).
     */
    public void switchTab(String tabId) {
        if (client == null || !client.isOpen() || sessionId == null) return;
        String sid = sessionId;
        ioExecutor.execute(() -> {
            try {
                TerminalMessage msg = new TerminalMessage(TerminalMessage.MessageType.SWITCH_TAB);
                msg.setSessionId(sid);
                msg.setTabId(tabId);
                if (client != null && client.isOpen()) client.send(MAPPER.writeValueAsString(msg));
            } catch (Exception e) {
                LOG.warn("Switch tab failed", e);
            }
        });
    }

    /**
     * Requests container logs (non-blocking).
     */
    public void requestLogs() {
        if (client == null || !client.isOpen() || sessionId == null) return;
        String sid = sessionId;
        ioExecutor.execute(() -> {
            try {
                TerminalMessage msg = new TerminalMessage(TerminalMessage.MessageType.LOGS_REQUEST);
                msg.setSessionId(sid);
                msg.setUserId(userId);
                if (client != null && client.isOpen()) client.send(MAPPER.writeValueAsString(msg));
            } catch (Exception e) {
                LOG.warn("Request logs failed", e);
            }
        });
    }

    private void createSession() {
        String newSessionId = "ide-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            TerminalMessage msg = TerminalMessage.createSession(newSessionId, userId,
                TerminalSession.ExecutionType.SHELL, 2L, 512L, 60L);
            if (client != null && client.isOpen()) {
                client.send(MAPPER.writeValueAsString(msg));
                sessionId = newSessionId;
            }
        } catch (Exception e) {
            LOG.warn("Create session failed", e);
            if (listener != null) javafx.application.Platform.runLater(() -> listener.onError(e.getMessage()));
        }
    }

    /**
     * Only processes messages for the current session (isolation).
     */
    private void handleMessage(String message) {
        try {
            TerminalMessage msg = MAPPER.readValue(message, TerminalMessage.class);
            if (msg == null || msg.getType() == null) return;
            String msgSessionId = msg.getSessionId();
            switch (msg.getType()) {
                case SESSION_INFO:
                    if (msgSessionId != null) {
                        sessionId = msgSessionId;
                        if (listener != null) javafx.application.Platform.runLater(() -> listener.onSessionCreated(msgSessionId));
                    }
                    break;
                case LOG_CHUNK:
                case EXECUTE_RESULT:
                    if (!isForCurrentSession(msgSessionId)) break;
                    if (listener != null) {
                        if (msg.getOutput() != null)
                            javafx.application.Platform.runLater(() -> listener.onOutput(msg.getOutput(), false));
                        if (msg.getError() != null)
                            javafx.application.Platform.runLater(() -> listener.onOutput(msg.getError(), true));
                    }
                    break;
                case ERROR:
                    if (listener != null && msg.getError() != null)
                        javafx.application.Platform.runLater(() -> listener.onError(msg.getError()));
                    break;
                case HISTORY_RESPONSE:
                    if (!isForCurrentSession(msgSessionId)) break;
                    if (listener != null && msg.getHistoryLines() != null)
                        javafx.application.Platform.runLater(() -> listener.onHistoryReceived(msg.getHistoryLines()));
                    break;
                case CREATE_TAB:
                    if (!isForCurrentSession(msgSessionId)) break;
                    if (listener != null)
                        javafx.application.Platform.runLater(() -> listener.onTabCreated(msg.getTabId(), msg.getTabName()));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            LOG.trace("Parse terminal message failed: {}", e.getMessage());
        }
    }

    private boolean isForCurrentSession(String msgSessionId) {
        if (sessionId == null) return msgSessionId == null;
        return sessionId.equals(msgSessionId);
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }
}
