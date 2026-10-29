package com.distributed_ide.terminal.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * Terminal execution message for WebSocket communication.
 * Supports various operations: execute code, fetch logs, stop execution, etc.
 */
public class TerminalMessage implements Serializable {
    public enum MessageType {
        // Session management
        CREATE_SESSION, DELETE_SESSION, LIST_SESSIONS, SESSION_CREATED, SESSION_REMOVED,
        GET_SESSION, REMOVE_SESSION, GET_USER_SESSIONS, USER_SESSIONS,
        // Code execution
        EXECUTE, EXECUTE_RESULT, EXECUTE_COMMAND, COMMAND_OUTPUT,
        // Log streaming
        LOG_CHUNK, LOGS_REQUEST, LOGS_RESPONSE,
        // Session info
        SESSION_INFO, SESSION_STATUS,
        // Control
        STOP, PAUSE, RESUME,
        // Health
        PING, PONG, HEARTBEAT,
        // Multi-tab support
        CREATE_TAB, CLOSE_TAB, SWITCH_TAB, LIST_TABS,
        // Command history
        HISTORY_REQUEST, HISTORY_RESPONSE, ADD_TO_HISTORY,
        // Error
        ERROR
    }

    @JsonProperty("type")
    private MessageType type;

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("executionType")
    private TerminalSession.ExecutionType executionType;

    @JsonProperty("code")
    private String code;

    @JsonProperty("language")
    private String language;

    @JsonProperty("cpuLimit")
    private Long cpuLimit;

    @JsonProperty("memoryLimit")
    private Long memoryLimit;

    @JsonProperty("timeout")
    private Long timeout;

    @JsonProperty("output")
    private String output;

    @JsonProperty("error")
    private String error;

    @JsonProperty("exitCode")
    private Integer exitCode;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("message")
    private String message;

    @JsonProperty("containerId")
    private String containerId;

    @JsonProperty("tabId")
    private String tabId;

    @JsonProperty("tabName")
    private String tabName;

    @JsonProperty("historyLines")
    private java.util.List<String> historyLines;

    @JsonProperty("historyCount")
    private Integer historyCount;

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("data")
    private Object data;

    @JsonProperty("command")
    private String command;

    @JsonProperty("timeoutSeconds")
    private Long timeoutSeconds;

    public TerminalMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    // Constructors
    public TerminalMessage(MessageType type) {
        this();
        this.type = type;
    }

    public TerminalMessage(MessageType type, String sessionId, String userId) {
        this(type);
        this.sessionId = sessionId;
        this.userId = userId;
    }

    // Factory methods
    public static TerminalMessage createSession(String sessionId, String userId, 
                                                TerminalSession.ExecutionType execType,
                                                long cpuLimit, long memLimit, long timeout) {
        TerminalMessage msg = new TerminalMessage(MessageType.CREATE_SESSION, sessionId, userId);
        msg.executionType = execType;
        msg.cpuLimit = cpuLimit;
        msg.memoryLimit = memLimit;
        msg.timeout = timeout;
        return msg;
    }

    public static TerminalMessage execute(String sessionId, String userId, String code, String language) {
        TerminalMessage msg = new TerminalMessage(MessageType.EXECUTE, sessionId, userId);
        msg.code = code;
        msg.language = language;
        return msg;
    }

    public static TerminalMessage logChunk(String sessionId, String output) {
        TerminalMessage msg = new TerminalMessage(MessageType.LOG_CHUNK, sessionId, null);
        msg.output = output;
        return msg;
    }

    public static TerminalMessage result(String sessionId, String output, String error, int exitCode) {
        TerminalMessage msg = new TerminalMessage(MessageType.EXECUTE_RESULT, sessionId, null);
        msg.output = output;
        msg.error = error;
        msg.exitCode = exitCode;
        return msg;
    }

    public static TerminalMessage sessionInfo(String sessionId, String containerId, String status) {
        TerminalMessage msg = new TerminalMessage(MessageType.SESSION_INFO, sessionId, null);
        msg.containerId = containerId;
        msg.message = status;
        return msg;
    }

    public static TerminalMessage error(String sessionId, String errorMsg) {
        TerminalMessage msg = new TerminalMessage(MessageType.ERROR, sessionId, null);
        msg.error = errorMsg;
        return msg;
    }

    public static TerminalMessage ping() {
        return new TerminalMessage(MessageType.PING);
    }

    public static TerminalMessage pong() {
        return new TerminalMessage(MessageType.PONG);
    }

    // Getters and setters
    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public TerminalSession.ExecutionType getExecutionType() {
        return executionType;
    }

    public void setExecutionType(TerminalSession.ExecutionType executionType) {
        this.executionType = executionType;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Long getCpuLimit() {
        return cpuLimit;
    }

    public void setCpuLimit(Long cpuLimit) {
        this.cpuLimit = cpuLimit;
    }

    public Long getMemoryLimit() {
        return memoryLimit;
    }

    public void setMemoryLimit(Long memoryLimit) {
        this.memoryLimit = memoryLimit;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public String getTabId() {
        return tabId;
    }

    public void setTabId(String tabId) {
        this.tabId = tabId;
    }

    public String getTabName() {
        return tabName;
    }

    public void setTabName(String tabName) {
        this.tabName = tabName;
    }

    public java.util.List<String> getHistoryLines() {
        return historyLines;
    }

    public void setHistoryLines(java.util.List<String> historyLines) {
        this.historyLines = historyLines;
    }

    public Integer getHistoryCount() {
        return historyCount;
    }

    public void setHistoryCount(Integer historyCount) {
        this.historyCount = historyCount;
    }

    // Factory methods for new message types
    public static TerminalMessage historyRequest(String sessionId, int count) {
        TerminalMessage msg = new TerminalMessage(MessageType.HISTORY_REQUEST, sessionId, null);
        msg.historyCount = count;
        return msg;
    }

    public static TerminalMessage historyResponse(String sessionId, java.util.List<String> history) {
        TerminalMessage msg = new TerminalMessage(MessageType.HISTORY_RESPONSE, sessionId, null);
        msg.historyLines = history;
        return msg;
    }

    public static TerminalMessage createTab(String sessionId, String tabId, String tabName) {
        TerminalMessage msg = new TerminalMessage(MessageType.CREATE_TAB, sessionId, null);
        msg.tabId = tabId;
        msg.tabName = tabName;
        return msg;
    }

    // Additional getters and setters
    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
