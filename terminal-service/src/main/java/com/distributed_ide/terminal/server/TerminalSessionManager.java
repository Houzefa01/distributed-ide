package com.distributed_ide.terminal.server;

import com.distributed_ide.terminal.model.TerminalSession;
import com.distributed_ide.terminal.service.TerminalProcessManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized manager for terminal sessions.
 * Handles session lifecycle, cleanup, and resource tracking.
 */
public class TerminalSessionManager {
    private static final Logger logger = LoggerFactory.getLogger(TerminalSessionManager.class);

    // Session storage: sessionId -> TerminalSession
    private final Map<String, TerminalSession> sessions;
    
    // User session mapping: userId -> List of sessionIds
    private final Map<String, Map<String, TerminalSession>> userSessions;
    
    // Docker sandbox manager
    private final DockerSandboxManager dockerManager;
    
    // Process manager for terminal processes
    private final TerminalProcessManager processManager = new TerminalProcessManager();
    
    // Track deletion attempts for error recovery
    private final Map<String, AtomicInteger> deletionRetries = new ConcurrentHashMap<>();
    private static final int MAX_DELETION_RETRIES = 3;
    
    // Configuration
    private final long maxSessionsPerUser;
    private final long inactivityTimeoutSeconds;
    private final long defaultCpuCores;
    private final long defaultMemoryMB;
    private final long defaultTimeoutSeconds;

    public TerminalSessionManager(long maxSessions, long inactivityTimeout, 
                                 long cpuCores, long memoryMB, long timeoutSeconds) {
        this.sessions = new ConcurrentHashMap<>();
        this.userSessions = new ConcurrentHashMap<>();
        this.dockerManager = new DockerSandboxManager(cpuCores, memoryMB, timeoutSeconds, "distributed-ide");
        this.maxSessionsPerUser = maxSessions;
        this.inactivityTimeoutSeconds = inactivityTimeout;
        this.defaultCpuCores = cpuCores;
        this.defaultMemoryMB = memoryMB;
        this.defaultTimeoutSeconds = timeoutSeconds;
    }

    /**
     * Create a new terminal session.
     */
    public TerminalSession createSession(String sessionId, String userId, 
                                        TerminalSession.ExecutionType execType,
                                        long cpuLimit, long memoryLimit, long timeoutSeconds) {
        try {
            // Check user session limits
            Map<String, TerminalSession> userSessionMap = userSessions.get(userId);
            if (userSessionMap != null && userSessionMap.size() >= maxSessionsPerUser) {
                logger.warn("User {} reached max sessions limit: {}", userId, maxSessionsPerUser);
                throw new RuntimeException("Maximum sessions limit reached: " + maxSessionsPerUser);
            }

            // Use defaults if not specified
            long cpu = cpuLimit > 0 ? cpuLimit : defaultCpuCores;
            long mem = memoryLimit > 0 ? memoryLimit : defaultMemoryMB;
            long timeout = timeoutSeconds > 0 ? timeoutSeconds : defaultTimeoutSeconds;

            // Create Docker container
            String containerId = dockerManager.createContainer(sessionId, userId, execType, cpu, mem, timeout);

            // Create session object
            TerminalSession session = new TerminalSession(sessionId, userId, containerId, 
                                                         execType, cpu, mem, timeout);
            session.setStatus(TerminalSession.SessionStatus.RUNNING);

            // Store session
            sessions.put(sessionId, session);
            userSessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                       .put(sessionId, session);

            logger.info("Session created: {} for user: {} with containerId: {}", sessionId, userId, containerId);
            return session;
        } catch (Exception e) {
            logger.error("Failed to create session: {} for user: {}", sessionId, userId, e);
            throw new RuntimeException("Session creation failed: " + e.getMessage());
        }
    }

    public TerminalSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public Collection<TerminalSession> getUserSessions(String userId) {
        Map<String, TerminalSession> userMap = userSessions.get(userId);
        return userMap != null ? userMap.values() : java.util.Collections.emptyList();
    }

    /**
     * Update session status and activity.
     */
    public void updateSessionStatus(String sessionId, TerminalSession.SessionStatus status) {
        TerminalSession session = sessions.get(sessionId);
        if (session != null) {
            session.setStatus(status);
            logger.debug("Session {} status updated to: {}", sessionId, status);
        }
    }

    /**
     * Delete a session with retry logic.
     * ✅ FIX: Retry on failure to ensure container cleanup
     */
    public boolean deleteSession(String sessionId) {
        TerminalSession session = sessions.get(sessionId);
        if (session == null) {
            logger.warn("⚠️  Session not found: {}", sessionId);
            return false;
        }

        int retries = 0;
        Exception lastError = null;
        
        while (retries < MAX_DELETION_RETRIES) {
            try {
                logger.info("🛑 Deleting session {} (attempt {}/{})", sessionId, retries + 1, MAX_DELETION_RETRIES);
                
                // Terminate associated process
                try {
                    if (processManager.isProcessAlive(sessionId)) {
                        logger.debug("📌 Terminating process for session {}", sessionId);
                        processManager.terminateProcess(sessionId, retries >= MAX_DELETION_RETRIES - 1);
                    }
                } catch (Exception e) {
                    logger.warn("⚠️  Error terminating process: {}", e.getMessage());
                }
                
                // Stop Docker container
                try {
                    dockerManager.stopContainer(sessionId);
                } catch (Exception e) {
                    logger.warn("⚠️  Error stopping container: {}", e.getMessage());
                    lastError = e;
                    retries++;
                    if (retries < MAX_DELETION_RETRIES) {
                        logger.info("🔄 Retrying container shutdown...");
                        try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        continue;
                    }
                }

                // Remove from storage
                sessions.remove(sessionId);
                
                String userId = session.getUserId();
                Map<String, TerminalSession> userMap = userSessions.get(userId);
                if (userMap != null) {
                    userMap.remove(sessionId);
                    if (userMap.isEmpty()) {
                        userSessions.remove(userId);
                    }
                }
                
                deletionRetries.remove(sessionId);
                logger.info("✅ Session deleted: {}", sessionId);
                return true;
                
            } catch (Exception e) {
                lastError = e;
                retries++;
                logger.error("❌ Error deleting session (attempt {}): {}", retries, e.getMessage());
                
                if (retries < MAX_DELETION_RETRIES) {
                    try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        
        logger.error("❌ Failed to delete session after {} attempts", MAX_DELETION_RETRIES, lastError);
        return false;
    }

    /**
     * Clean up inactive sessions with proper error handling.
     * ✅ FIX: Thread-safe iteration and error recovery
     */
    public int cleanupInactiveSessions() {
        int count = 0;
        
        // Create a snapshot to avoid concurrent modification
        List<TerminalSession> sessionsToCheck = new ArrayList<>(sessions.values());
        
        for (TerminalSession session : sessionsToCheck) {
            try {
                // Check if session no longer exists (might be deleted elsewhere)
                if (!sessions.containsKey(session.getSessionId())) {
                    continue;
                }
                
                if (!session.isAlive(inactivityTimeoutSeconds)) {
                    logger.info("⏰ Cleaning up inactive session: {} (idle for {} seconds)", 
                               session.getSessionId(), session.getInactivityDurationInSeconds());
                    if (deleteSession(session.getSessionId())) {
                        count++;
                    }
                }
                
                if (session.isTimedOut()) {
                    logger.info("⏱️  Cleaning up timed-out session: {} (elapsed: {}s)", 
                               session.getSessionId(), 
                               (System.currentTimeMillis() - session.getCreatedAt().toEpochMilli()) / 1000);
                    if (deleteSession(session.getSessionId())) {
                        count++;
                    }
                }
            } catch (Exception e) {
                logger.error("❌ Error cleaning up session {}: {}", session.getSessionId(), e.getMessage());
            }
        }
        
        if (count > 0) {
            logger.info("✅ Cleaned up {} inactive/timed-out sessions", count);
        }
        return count;
    }

    /**
     * Get all active sessions.
     */
    public Collection<TerminalSession> getAllSessions() {
        return sessions.values();
    }

    /**
     * Get session count.
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * Get active container count.
     */
    public int getActiveContainerCount() {
        return dockerManager.getActiveContainerCount();
    }

    /**
     * Shutdown manager - cleanup all sessions.
     * ✅ FIX: Proper cleanup of process manager and graceful shutdown
     */
    public void shutdown() {
        logger.info("🛑 Shutting down terminal session manager");
        
        // Terminate all processes first
        try {
            processManager.shutdown();
        } catch (Exception e) {
            logger.warn("⚠️  Error shutting down process manager: {}", e.getMessage());
        }
        
        // Then cleanup all containers
        try {
            dockerManager.cleanupAllContainers();
        } catch (Exception e) {
            logger.warn("⚠️  Error cleaning up containers: {}", e.getMessage());
        }
        
        // Clear data structures
        sessions.clear();
        userSessions.clear();
        deletionRetries.clear();
        
        logger.info("✅ Terminal session manager shutdown complete");
    }

    public DockerSandboxManager getDockerManager() {
        return dockerManager;
    }
    
    public TerminalProcessManager getProcessManager() {
        return processManager;
    }
}
