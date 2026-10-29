package com.distributed_ide.terminal.server;

import com.distributed_ide.terminal.model.TerminalMessage;
import com.distributed_ide.terminal.model.TerminalSession;
import com.distributed_ide.terminal.util.CommandValidator;
import com.distributed_ide.common.ObjectMapperFactory;
import com.distributed_ide.common.SSLContextFactory;
import com.distributed_ide.common.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket server for terminal service.
 * Handles code execution requests, log streaming, and session management.
 */
public class TerminalWebSocketServer extends WebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(TerminalWebSocketServer.class);

    // WebSocket mapping: WebSocket -> userId
    private final Map<WebSocket, String> sessionMap;
    
    // Terminal session manager
    private final TerminalSessionManager terminalManager;
    
    // JSON serialization
    private final ObjectMapper objectMapper;

    public TerminalWebSocketServer(InetSocketAddress address, TerminalSessionManager terminalManager) {
        super(address);
        
        // Initialize TLS 1.3 encryption context for secure WebSocket connections
        try {
            String keystorePath = System.getProperty("keystore.path", "./keystore.p12");
            String keystorePassword = System.getProperty("keystore.password", "distributed-ide-default");
            SSLContext sslContext = SSLContextFactory.createSSLContext(keystorePath, keystorePassword);
            this.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
            logger.info("✅ TLS 1.3 initialized for TerminalWebSocketServer");
        } catch (Exception e) {
            logger.warn("⚠️ TLS initialization failed: {}. Server will run unencrypted.", e.getMessage());
            // Continue without TLS rather than failing startup
        }
        
        this.sessionMap = new ConcurrentHashMap<>();
        this.terminalManager = terminalManager;
        this.objectMapper = ObjectMapperFactory.getMapper();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("New WebSocket connection from: {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            if (message == null || message.trim().isEmpty()) {
                logger.warn("Received empty message from {}", conn.getRemoteSocketAddress());
                sendError(conn, null, "Empty message received");
                return;
            }
            
            TerminalMessage msg = objectMapper.readValue(message, TerminalMessage.class);
            
            if (msg.getType() == null) {
                logger.warn("Received message with null type from {}", conn.getRemoteSocketAddress());
                sendError(conn, msg.getSessionId(), "Message type is required");
                return;
            }
            
            logger.debug("Received message type: {} for session: {} from {}", 
                msg.getType(), msg.getSessionId(), conn.getRemoteSocketAddress());
            
            handleMessage(conn, msg);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("JSON parsing error from {}: {}", conn.getRemoteSocketAddress(), e.getMessage());
            sendError(conn, null, "Invalid JSON format: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing message from {}: {}", conn.getRemoteSocketAddress(), e.getMessage(), e);
            sendError(conn, null, "Message processing error: " + e.getMessage());
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.info("WebSocket connection closed: {}, reason: {}", conn.getRemoteSocketAddress(), reason);
        sessionMap.remove(conn);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String remoteAddr = conn != null ? conn.getRemoteSocketAddress().toString() : "unknown";
        logger.error("WebSocket error from {}: {}", remoteAddr, ex.getMessage(), ex);
        
        // Try to notify client of error
        if (conn != null && conn.isOpen()) {
            try {
                sendError(conn, null, "Server error: " + ex.getMessage());
            } catch (Exception e) {
                logger.error("Failed to send error notification", e);
            }
        }
    }

    @Override
    public void onStart() {
        logger.info("Terminal WebSocket server started on: {}", getAddress());
    }

    // Message handlers

    private void handleMessage(WebSocket conn, TerminalMessage msg) {
        try {
            // FIX: JWT validation can be added when TerminalMessage includes token field
            // For now, rely on external authentication and CommandValidator
            
            switch (msg.getType()) {
                case CREATE_SESSION:
                    handleCreateSession(conn, msg);
                    break;
                case DELETE_SESSION:
                    handleDeleteSession(conn, msg);
                    break;
                case EXECUTE:
                    handleExecute(conn, msg);
                    break;
                case LOGS_REQUEST:
                    handleLogsRequest(conn, msg);
                    break;
                case STOP:
                    handleStop(conn, msg);
                    break;
                case SESSION_INFO:
                    handleSessionInfo(conn, msg);
                    break;
                case PING:
                    sendMessage(conn, TerminalMessage.pong());
                    break;
                case LIST_SESSIONS:
                    handleListSessions(conn, msg);
                    break;
                case HISTORY_REQUEST:
                    handleHistoryRequest(conn, msg);
                    break;
                case ADD_TO_HISTORY:
                    handleAddToHistory(conn, msg);
                    break;
                case CREATE_TAB:
                    handleCreateTab(conn, msg);
                    break;
                case SWITCH_TAB:
                    handleSwitchTab(conn, msg);
                    break;
                default:
                    logger.warn("Unknown message type: {} for session: {}", msg.getType(), msg.getSessionId());
                    sendError(conn, msg.getSessionId(), "Unknown message type: " + msg.getType());
            }
        } catch (Exception e) {
            logger.error("Error handling message type: {}", msg.getType(), e);
            sendError(conn, msg.getSessionId(), "Handler error: " + e.getMessage());
        }
    }

    private void handleCreateSession(WebSocket conn, TerminalMessage msg) {
        try {
            String sessionId = msg.getSessionId();
            String userId = msg.getUserId();
            TerminalSession.ExecutionType execType = msg.getExecutionType();
            long cpuLimit = msg.getCpuLimit() != null ? msg.getCpuLimit() : 1;
            long memLimit = msg.getMemoryLimit() != null ? msg.getMemoryLimit() : 512;
            long timeout = msg.getTimeout() != null ? msg.getTimeout() : 300;

            // Create session
            TerminalSession session = terminalManager.createSession(sessionId, userId, execType, cpuLimit, memLimit, timeout);
            
            // Track WebSocket-user mapping
            sessionMap.put(conn, userId);

            // Send confirmation
            TerminalMessage response = TerminalMessage.sessionInfo(
                sessionId, session.getContainerId(), session.getStatus().toString()
            );
            sendMessage(conn, response);
            
            logger.info("Session created: {} with container: {}", sessionId, session.getContainerId());
        } catch (Exception e) {
            logger.error("Error creating session", e);
            sendError(conn, msg.getSessionId(), "Session creation failed: " + e.getMessage());
        }
    }

    private void handleExecute(WebSocket conn, TerminalMessage msg) {
        String sessionId = msg.getSessionId();
        try {
            String code = msg.getCode();
            String language = msg.getLanguage();

            if (code == null || code.trim().isEmpty()) {
                sendError(conn, sessionId, "Code cannot be empty");
                return;
            }

            TerminalSession session = terminalManager.getSession(sessionId);
            if (session == null) {
                logger.warn("Execute request for non-existent session: {}", sessionId);
                sendError(conn, sessionId, "Session not found");
                return;
            }

            // FIX: Validate command using CommandValidator whitelist
            CommandValidator validator = new CommandValidator();
            if (!validator.validate(code)) {
                logger.warn("❌ Command validation failed for session {}: {}", sessionId, code);
                sendError(conn, sessionId, "Command not allowed by security policy. Only whitelisted commands are permitted.");
                return;
            }

            // Add command to history
            session.addCommandToHistory(code);
            logger.debug("Executing code in session: {}, language: {}", sessionId, language);

            // Execute code in Docker container
            DockerSandboxManager.ExecutionResult result = 
                terminalManager.getDockerManager().executeCode(sessionId, code, language);

            // Send result
            TerminalMessage response = TerminalMessage.result(
                sessionId, result.getOutput(), result.getError(), result.getExitCode()
            );
            response.setTabId(msg.getTabId());
            sendMessage(conn, response);
            
            // Update session
            if (result.getExitCode() == 0) {
                terminalManager.updateSessionStatus(sessionId, TerminalSession.SessionStatus.COMPLETED);
            } else {
                terminalManager.updateSessionStatus(sessionId, TerminalSession.SessionStatus.ERROR);
            }
            
            logger.info("✅ Code execution completed for session: {}, exitCode: {}", sessionId, result.getExitCode());
        } catch (Exception e) {
            logger.error("❌ Error executing code for session: {}", sessionId, e);
            terminalManager.updateSessionStatus(sessionId, TerminalSession.SessionStatus.ERROR);
            sendError(conn, sessionId, "Execution error: " + e.getMessage());
        }
    }

    private void handleLogsRequest(WebSocket conn, TerminalMessage msg) {
        try {
            String sessionId = msg.getSessionId();
            
            String logs = terminalManager.getDockerManager().getContainerLogs(sessionId);
            
            TerminalMessage response = TerminalMessage.logChunk(sessionId, logs);
            sendMessage(conn, response);
            
            logger.debug("Logs sent for session: {}", sessionId);
        } catch (Exception e) {
            logger.error("Error fetching logs", e);
            sendError(conn, msg.getSessionId(), "Log fetch error: " + e.getMessage());
        }
    }

    private void handleStop(WebSocket conn, TerminalMessage msg) {
        try {
            String sessionId = msg.getSessionId();
            
            boolean deleted = terminalManager.deleteSession(sessionId);
            
            if (deleted) {
                terminalManager.updateSessionStatus(sessionId, TerminalSession.SessionStatus.TERMINATED);
                TerminalMessage response = TerminalMessage.sessionInfo(sessionId, "", "TERMINATED");
                sendMessage(conn, response);
                logger.info("Session terminated: {}", sessionId);
            } else {
                sendError(conn, sessionId, "Failed to terminate session");
            }
        } catch (Exception e) {
            logger.error("Error stopping session", e);
            sendError(conn, msg.getSessionId(), "Stop error: " + e.getMessage());
        }
    }

    private void handleDeleteSession(WebSocket conn, TerminalMessage msg) {
        handleStop(conn, msg);
    }

    private void handleSessionInfo(WebSocket conn, TerminalMessage msg) {
        try {
            String sessionId = msg.getSessionId();
            TerminalSession session = terminalManager.getSession(sessionId);
            
            if (session == null) {
                sendError(conn, sessionId, "Session not found");
                return;
            }

            TerminalMessage response = TerminalMessage.sessionInfo(
                sessionId, session.getContainerId(), session.getStatus().toString()
            );
            response.setMessage(session.getSessionInfo());
            sendMessage(conn, response);
            
            logger.debug("Session info sent for: {}", sessionId);
        } catch (Exception e) {
            logger.error("Error fetching session info", e);
            sendError(conn, msg.getSessionId(), "Info fetch error: " + e.getMessage());
        }
    }

    private void handleListSessions(WebSocket conn, TerminalMessage msg) {
        try {
            String userId = msg.getUserId();
            var userSessions = terminalManager.getUserSessions(userId);
            
            StringBuilder sessionList = new StringBuilder();
            for (TerminalSession session : userSessions) {
                sessionList.append(session.getSessionId()).append(",");
            }
            
            TerminalMessage response = new TerminalMessage(TerminalMessage.MessageType.LIST_SESSIONS);
            response.setUserId(userId);
            response.setMessage(sessionList.toString());
            sendMessage(conn, response);
            
            logger.debug("Listed {} sessions for user: {}", userSessions.size(), userId);
        } catch (Exception e) {
            logger.error("Error listing sessions", e);
            sendError(conn, null, "List error: " + e.getMessage());
        }
    }

    // Utility methods

    private void sendMessage(WebSocket conn, TerminalMessage msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            conn.send(json);
        } catch (Exception e) {
            logger.error("Error sending message: {}", e.getMessage());
        }
    }

    private void handleHistoryRequest(WebSocket conn, TerminalMessage msg) {
        String sessionId = msg.getSessionId();
        try {
            TerminalSession session = terminalManager.getSession(sessionId);
            if (session == null) {
                logger.warn("History request for non-existent session: {}", sessionId);
                sendError(conn, sessionId, "Session not found");
                return;
            }

            int count = msg.getHistoryCount() != null ? msg.getHistoryCount() : 50;
            java.util.List<String> history = session.getRecentHistory(count);
            
            TerminalMessage response = TerminalMessage.historyResponse(sessionId, history);
            response.setTabId(msg.getTabId());
            sendMessage(conn, response);
            
            logger.debug("Sent {} history lines for session: {}", history.size(), sessionId);
        } catch (Exception e) {
            logger.error("Error fetching history for session: {}", sessionId, e);
            sendError(conn, sessionId, "History fetch error: " + e.getMessage());
        }
    }

    private void handleAddToHistory(WebSocket conn, TerminalMessage msg) {
        String sessionId = msg.getSessionId();
        try {
            TerminalSession session = terminalManager.getSession(sessionId);
            if (session == null) {
                logger.warn("Add to history for non-existent session: {}", sessionId);
                sendError(conn, sessionId, "Session not found");
                return;
            }

            String command = msg.getCode();
            if (command != null && !command.trim().isEmpty()) {
                session.addCommandToHistory(command);
                logger.debug("Added command to history for session: {}", sessionId);
            }
        } catch (Exception e) {
            logger.error("Error adding to history for session: {}", sessionId, e);
            sendError(conn, sessionId, "Add to history error: " + e.getMessage());
        }
    }

    private void handleCreateTab(WebSocket conn, TerminalMessage msg) {
        String sessionId = msg.getSessionId();
        try {
            TerminalSession session = terminalManager.getSession(sessionId);
            if (session == null) {
                logger.warn("Create tab for non-existent session: {}", sessionId);
                sendError(conn, sessionId, "Session not found");
                return;
            }

            String tabId = msg.getTabId();
            String tabName = msg.getTabName();
            
            session.setTabId(tabId);
            session.setMetadataValue("tabName", tabName);
            
            TerminalMessage response = TerminalMessage.createTab(sessionId, tabId, tabName);
            sendMessage(conn, response);
            
            logger.info("Created tab {} ({}) for session: {}", tabId, tabName, sessionId);
        } catch (Exception e) {
            logger.error("Error creating tab for session: {}", sessionId, e);
            sendError(conn, sessionId, "Create tab error: " + e.getMessage());
        }
    }

    private void handleSwitchTab(WebSocket conn, TerminalMessage msg) {
        String sessionId = msg.getSessionId();
        try {
            TerminalSession session = terminalManager.getSession(sessionId);
            if (session == null) {
                logger.warn("Switch tab for non-existent session: {}", sessionId);
                sendError(conn, sessionId, "Session not found");
                return;
            }

            String tabId = msg.getTabId();
            session.setTabId(tabId);
            
            logger.debug("Switched to tab {} for session: {}", tabId, sessionId);
        } catch (Exception e) {
            logger.error("Error switching tab for session: {}", sessionId, e);
            sendError(conn, sessionId, "Switch tab error: " + e.getMessage());
        }
    }

    private void sendError(WebSocket conn, String sessionId, String errorMsg) {
        try {
            logger.warn("Sending error to client - sessionId: {}, error: {}", sessionId, errorMsg);
            TerminalMessage error = TerminalMessage.error(sessionId, errorMsg);
            sendMessage(conn, error);
        } catch (Exception e) {
            logger.error("Error sending error message: {}", e.getMessage(), e);
        }
    }

    public int getActiveConnectionCount() {
        return getConnections().size();
    }

    public TerminalSessionManager getTerminalManager() {
        return terminalManager;
    }
}
