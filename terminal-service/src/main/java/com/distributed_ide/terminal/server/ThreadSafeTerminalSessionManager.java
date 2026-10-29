package com.distributed_ide.terminal.server;

import com.distributed_ide.terminal.model.TerminalSession;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe terminal session manager with proper resource management and error handling.
 * 
 * Key improvements:
 * - Thread-safe session operations
 * - Proper resource cleanup
 * - Timeout protection
 * - Exception isolation
 * - Session monitoring
 */
public class ThreadSafeTerminalSessionManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(ThreadSafeTerminalSessionManager.class);
    
    // Thread-safe session storage
    private final Map<String, TerminalSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, TerminalSession>> userSessions = new ConcurrentHashMap<>();
    private final ReadWriteLock sessionsLock = new ReentrantReadWriteLock();
    
    // Docker sandbox manager
    private final DockerSandboxManager dockerManager;
    
    // Configuration
    private final long maxSessionsPerUser;
    private final long inactivityTimeoutSeconds;
    private final long defaultCpuCores;
    private final long defaultMemoryMB;
    private final long defaultTimeoutSeconds;
    
    // Monitoring
    private final ScheduledExecutorService monitoringExecutor;
    private final AtomicLong sessionCounter = new AtomicLong(0);
    private final Map<String, Long> sessionLastActivity = new ConcurrentHashMap<>();
    
    // Shutdown flag
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    public ThreadSafeTerminalSessionManager(long maxSessions, long inactivityTimeout, 
                                          long cpuCores, long memoryMB, long timeoutSeconds) {
        this.maxSessionsPerUser = maxSessions;
        this.inactivityTimeoutSeconds = inactivityTimeout;
        this.defaultCpuCores = cpuCores;
        this.defaultMemoryMB = memoryMB;
        this.defaultTimeoutSeconds = timeoutSeconds;
        this.dockerManager = new DockerSandboxManager(cpuCores, memoryMB, timeoutSeconds, "distributed-ide");
        this.monitoringExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "terminal-monitor");
            t.setDaemon(true);
            return t;
        });
        
        startMonitoring();
    }
    
    /**
     * Create a new terminal session with proper error handling and resource management.
     */
    @Nonnull
    public CompletableFuture<TerminalSession> createSession(@Nonnull String sessionId, @Nonnull String userId, 
                                                               @Nonnull TerminalSession.ExecutionType execType,
                                                               long cpuLimit, long memoryLimit, long timeoutSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            if (isShuttingDown.get()) {
                throw new TerminalSessionException("Terminal session manager is shutting down");
            }
            
            try {
                // Check user session limits
                sessionsLock.readLock().lock();
                try {
                    Map<String, TerminalSession> userSessionMap = userSessions.get(userId);
                    if (userSessionMap != null && userSessionMap.size() >= maxSessionsPerUser) {
                        throw new TerminalSessionException("Maximum sessions limit reached: " + maxSessionsPerUser);
                    }
                } finally {
                    sessionsLock.readLock().unlock();
                }
                
                // Use defaults if not specified
                long cpu = cpuLimit > 0 ? cpuLimit : defaultCpuCores;
                long mem = memoryLimit > 0 ? memoryLimit : defaultMemoryMB;
                long timeout = timeoutSeconds > 0 ? timeoutSeconds : defaultTimeoutSeconds;
                
                // Create Docker container with timeout protection
                CompletableFuture<String> containerFuture = CompletableFuture.supplyAsync(() -> {
                    return dockerManager.createContainer(sessionId, userId, execType, cpu, mem, timeout);
                });
                
                String containerId = containerFuture.get(timeoutSeconds, TimeUnit.SECONDS);
                
                // Create session object
                TerminalSession session = new TerminalSession(sessionId, userId, containerId, 
                    execType, cpu, mem, timeout);
                session.setStatus(TerminalSession.SessionStatus.RUNNING);
                
                // Store session
                sessionsLock.writeLock().lock();
                try {
                    sessions.put(sessionId, session);
                    userSessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                        .put(sessionId, session);
                    sessionLastActivity.put(sessionId, System.currentTimeMillis());
                } finally {
                    sessionsLock.writeLock().unlock();
                }
                
                sessionCounter.incrementAndGet();
                LOG.info("Session created: {} for user: {} with containerId: {}", sessionId, userId, containerId);
                return session;
                
            } catch (TimeoutException e) {
                LOG.error("Session creation timed out: {} for user: {}", sessionId, userId, e);
                throw new TerminalSessionException("Session creation timed out: " + e.getMessage());
            } catch (Exception e) {
                LOG.error("Failed to create session: {} for user: {}", sessionId, userId, e);
                
                // Clean up if container was created
                try {
                    dockerManager.removeContainer(sessionId);
                } catch (Exception cleanupError) {
                    LOG.error("Failed to cleanup container after session creation failure: {}", sessionId, cleanupError);
                }
                
                throw new TerminalSessionException("Session creation failed: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Get session by ID (thread-safe).
     */
    @Nullable
    public TerminalSession getSession(@Nonnull String sessionId) {
        return sessions.get(sessionId);
    }
    
    /**
     * Get all sessions for a user (thread-safe).
     */
    @Nonnull
    public Collection<TerminalSession> getUserSessions(@Nonnull String userId) {
        sessionsLock.readLock().lock();
        try {
            Map<String, TerminalSession> userMap = userSessions.get(userId);
            return userMap != null ? new ArrayList<>(userMap.values()) : Collections.emptyList();
        } finally {
            sessionsLock.readLock().unlock();
        }
    }
    
    /**
     * Get all active sessions (thread-safe).
     */
    @Nonnull
    public Collection<TerminalSession> getAllSessions() {
        sessionsLock.readLock().lock();
        try {
            return new ArrayList<>(sessions.values());
        } finally {
            sessionsLock.readLock().unlock();
        }
    }
    
    /**
     * Remove a session and clean up resources.
     */
    @Nonnull
    public CompletableFuture<Boolean> removeSession(@Nonnull String sessionId) {
        TerminalSession session = sessions.remove(sessionId);
        if (session != null) {
            return cleanupSession(session);
        }
        return CompletableFuture.completedFuture(false);
    }
    
    /**
     * Remove all sessions for a user.
     */
    @Nonnull
    public CompletableFuture<Integer> removeUserSessions(@Nonnull String userId) {
        return CompletableFuture.supplyAsync(() -> {
            sessionsLock.writeLock().lock();
            try {
                Map<String, TerminalSession> userSessionMap = userSessions.remove(userId);
                if (userSessionMap != null) {
                    List<CompletableFuture<Boolean>> cleanupTasks = new ArrayList<>();
                    
                    for (TerminalSession session : userSessionMap.values()) {
                        cleanupTasks.add(cleanupSession(session));
                    }
                    
                    // Wait for all cleanup tasks to complete
                    CompletableFuture.allOf(cleanupTasks.toArray(new CompletableFuture[0]));
                    
                    return userSessionMap.size();
                }
                return 0;
            } finally {
                sessionsLock.writeLock().unlock();
            }
        });
    }
    
    /**
     * Update session activity timestamp.
     */
    public void updateSessionActivity(@Nonnull String sessionId) {
        sessionLastActivity.put(sessionId, System.currentTimeMillis());
    }
    
    /**
     * Get session statistics.
     */
    @Nonnull
    public SessionStats getStats() {
        sessionsLock.readLock().lock();
        try {
            int totalSessions = sessions.size();
            int activeSessions = 0;
            int errorSessions = 0;
            
            for (TerminalSession session : sessions.values()) {
                if (session.getStatus() == TerminalSession.SessionStatus.RUNNING) {
                    activeSessions++;
                } else if (session.getStatus() == TerminalSession.SessionStatus.ERROR) {
                    errorSessions++;
                }
            }
            
            long totalUsers = userSessions.size();
            
            return new SessionStats(totalSessions, activeSessions, errorSessions, totalUsers, sessionCounter.get());
        } finally {
            sessionsLock.readLock().unlock();
        }
    }
    
    /**
     * Cleanup inactive sessions.
     */
    @Nonnull
    public CompletableFuture<Integer> cleanupInactiveSessions() {
        return CompletableFuture.supplyAsync(() -> {
            long currentTime = System.currentTimeMillis();
            List<String> inactiveSessions = new ArrayList<>();
            
            sessionsLock.readLock().lock();
            try {
                for (Map.Entry<String, Long> entry : sessionLastActivity.entrySet()) {
                    String sessionId = entry.getKey();
                    long lastActivity = entry.getValue();
                    
                    if ((currentTime - lastActivity) > (inactivityTimeoutSeconds * 1000)) {
                        inactiveSessions.add(sessionId);
                    }
                }
            } finally {
                sessionsLock.readLock().unlock();
            }
            
            List<CompletableFuture<Boolean>> cleanupTasks = new ArrayList<>();
            for (String sessionId : inactiveSessions) {
                cleanupTasks.add(removeSession(sessionId));
            }
            
            // Wait for all cleanup tasks to complete
            if (!cleanupTasks.isEmpty()) {
                CompletableFuture.allOf(cleanupTasks.toArray(new CompletableFuture[0]));
            }
            
            return inactiveSessions.size();
        });
    }
    
    /**
     * Shutdown the session manager and clean up all resources.
     */
    public void shutdown() {
        LOG.info("Shutting down terminal session manager");
        
        isShuttingDown.set(true);
        
        try {
            // Stop monitoring
            monitoringExecutor.shutdown();
            
            // Remove all sessions
            List<String> allSessionIds = new ArrayList<>(sessions.keySet());
            List<CompletableFuture<Boolean>> cleanupTasks = new ArrayList<>();
            
            for (String sessionId : allSessionIds) {
                cleanupTasks.add(removeSession(sessionId));
            }
            
            // Wait for all cleanup tasks to complete
            if (!cleanupTasks.isEmpty()) {
                CompletableFuture.allOf(cleanupTasks.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
            }
            
            // Shutdown Docker manager
            dockerManager.shutdown();
            
            // Clear all maps
            sessionsLock.writeLock().lock();
            try {
                sessions.clear();
                userSessions.clear();
                sessionLastActivity.clear();
            } finally {
                sessionsLock.writeLock().unlock();
            }
            
            LOG.info("Terminal session manager shutdown complete");
        } catch (Exception e) {
            LOG.error("Error during terminal session manager shutdown", e);
        }
    }
    
    // Private helper methods
    
    @Nonnull
    private CompletableFuture<Boolean> cleanupSession(@Nonnull TerminalSession session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Update session status
                session.setStatus(TerminalSession.SessionStatus.TERMINATED);
                
                // Remove from user sessions
                sessionsLock.writeLock().lock();
                try {
                    Map<String, TerminalSession> userSessionMap = userSessions.get(session.getUserId());
                    if (userSessionMap != null) {
                        userSessionMap.remove(session.getSessionId());
                    }
                } finally {
                    sessionsLock.writeLock().unlock();
                }
                
                // Remove from activity tracking
                sessionLastActivity.remove(session.getSessionId());
                
                // Remove Docker container
                if (session.getContainerId() != null) {
                    dockerManager.removeContainer(session.getContainerId());
                }
                
                LOG.info("Session cleaned up: {}", session.getSessionId());
                return true;
                
            } catch (Exception e) {
                LOG.error("Failed to cleanup session: {}", session.getSessionId(), e);
                return false;
            }
        });
    }
    
    private void startMonitoring() {
        // Cleanup inactive sessions every minute
        monitoringExecutor.scheduleAtFixedRate(this::cleanupInactiveSessions, 60, 60, TimeUnit.SECONDS);
        
        // Log statistics every 5 minutes
        monitoringExecutor.scheduleAtFixedRate(() -> {
            SessionStats stats = getStats();
            LOG.info("Session stats: {}", stats);
        }, 300, 300, TimeUnit.SECONDS);
    }
    
    /**
     * Session statistics for monitoring.
     */
    public static class SessionStats {
        public final int totalSessions;
        public final int activeSessions;
        public final int errorSessions;
        public final long totalUsers;
        public final long totalSessionsCreated;
        
        public SessionStats(int totalSessions, int activeSessions, int errorSessions, long totalUsers, long totalSessionsCreated) {
            this.totalSessions = totalSessions;
            this.activeSessions = activeSessions;
            this.errorSessions = errorSessions;
            this.totalUsers = totalUsers;
            this.totalSessionsCreated = totalSessionsCreated;
        }
        
        @Override
        public String toString() {
            return String.format("SessionStats{total=%d, active=%d, error=%d, users=%d, created=%d}", 
                totalSessions, activeSessions, errorSessions, totalUsers, totalSessionsCreated);
        }
    }
    
    /**
     * Terminal session exception for proper error handling.
     */
    public static class TerminalSessionException extends RuntimeException {
        public TerminalSessionException(@Nonnull String message) {
            super(message);
        }
        
        public TerminalSessionException(@Nonnull String message, @Nonnull Throwable cause) {
            super(message, cause);
        }
    }
}
