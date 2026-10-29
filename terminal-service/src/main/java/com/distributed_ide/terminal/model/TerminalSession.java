package com.distributed_ide.terminal.model;

import java.io.Serializable;
import java.time.Instant;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Represents a terminal session for code execution in a Docker container.
 * Each session is isolated with its own resource limits.
 */
public class TerminalSession implements Serializable {
    private final String sessionId;
    private final String userId;
    private final String containerId;
    private final ExecutionType executionType;
    private final Instant createdAt;
    private volatile long lastActivityAt;
    private volatile SessionStatus status;
    private final long cpuLimitInCores;
    private final long memoryLimitInMB;
    private final long timeoutInSeconds;
    private final Map<String, Object> metadata;
    private final Deque<String> commandHistory;
    private final int maxHistorySize;
    private volatile String tabId;
    private volatile String currentWorkingDir;
    private volatile Map<String, String> environmentVars;

    public enum SessionStatus {
        CREATED, RUNNING, PAUSED, COMPLETED, ERROR, TERMINATED
    }

    public enum ExecutionType {
        JAVA, PYTHON, SHELL, WEB
    }

    public TerminalSession(String sessionId, String userId, String containerId,
                          ExecutionType executionType, long cpuLimitInCores,
                          long memoryLimitInMB, long timeoutInSeconds) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.containerId = containerId;
        this.executionType = executionType;
        this.cpuLimitInCores = cpuLimitInCores;
        this.memoryLimitInMB = memoryLimitInMB;
        this.timeoutInSeconds = timeoutInSeconds;
        this.createdAt = Instant.now();
        this.lastActivityAt = System.currentTimeMillis();
        this.status = SessionStatus.CREATED;
        this.metadata = new HashMap<>();
        this.commandHistory = new ConcurrentLinkedDeque<>();
        this.maxHistorySize = 1000;
        this.tabId = "default";
        this.currentWorkingDir = "/";
        this.environmentVars = new HashMap<>();
    }

    // Getters
    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getContainerId() {
        return containerId;
    }

    public ExecutionType getExecutionType() {
        return executionType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getLastActivityAt() {
        return lastActivityAt;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public long getCpuLimitInCores() {
        return cpuLimitInCores;
    }

    public long getMemoryLimitInMB() {
        return memoryLimitInMB;
    }

    public long getTimeoutInSeconds() {
        return timeoutInSeconds;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getTabId() {
        return tabId;
    }

    public void setTabId(String tabId) {
        this.tabId = tabId;
        updateActivity();
    }

    public String getCurrentWorkingDir() {
        return currentWorkingDir;
    }

    public void setCurrentWorkingDir(String dir) {
        this.currentWorkingDir = dir;
        updateActivity();
    }

    public Map<String, String> getEnvironmentVars() {
        return new HashMap<>(environmentVars);
    }

    public void setEnvironmentVar(String key, String value) {
        this.environmentVars.put(key, value);
        updateActivity();
    }

    // Status management
    public void setStatus(SessionStatus status) {
        this.status = status;
        updateActivity();
    }

    public void updateActivity() {
        this.lastActivityAt = System.currentTimeMillis();
    }

    public long getInactivityDurationInSeconds() {
        return (System.currentTimeMillis() - lastActivityAt) / 1000;
    }

    public boolean isAlive(long inactivityTimeoutInSeconds) {
        return getInactivityDurationInSeconds() < inactivityTimeoutInSeconds;
    }

    public boolean isTimedOut() {
        long elapsedSeconds = (System.currentTimeMillis() - createdAt.toEpochMilli()) / 1000;
        return elapsedSeconds > timeoutInSeconds;
    }

    // Metadata operations
    public void setMetadataValue(String key, Object value) {
        metadata.put(key, value);
        updateActivity();
    }

    public Object getMetadataValue(String key) {
        return metadata.get(key);
    }

    // Command history operations
    public void addCommandToHistory(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }
        commandHistory.addLast(command);
        if (commandHistory.size() > maxHistorySize) {
            commandHistory.removeFirst();
        }
        updateActivity();
    }

    public List<String> getCommandHistory() {
        return new ArrayList<>(commandHistory);
    }

    public List<String> getRecentHistory(int count) {
        List<String> history = new ArrayList<>(commandHistory);
        int size = history.size();
        if (size <= count) {
            return history;
        }
        return history.subList(size - count, size);
    }

    public void clearHistory() {
        commandHistory.clear();
        updateActivity();
    }

    public int getHistorySize() {
        return commandHistory.size();
    }

    public String getSessionInfo() {
        return String.format(
            "TerminalSession{id=%s, user=%s, container=%s, type=%s, status=%s, cpu=%dCPU, mem=%dMB, timeout=%ds}",
            sessionId, userId, containerId, executionType, status, cpuLimitInCores, memoryLimitInMB, timeoutInSeconds
        );
    }

    @Override
    public String toString() {
        return getSessionInfo();
    }
}
