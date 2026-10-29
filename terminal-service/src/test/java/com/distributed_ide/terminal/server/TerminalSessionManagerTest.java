package com.distributed_ide.terminal.server;

import com.distributed_ide.terminal.model.TerminalSession;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TerminalSessionManagerTest {
    private TerminalSessionManager manager;

    private static boolean dockerAvailable() {
        try {
            Process p = new ProcessBuilder(List.of("docker", "info"))
                .redirectErrorStream(true)
                .start();
            boolean finished = p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished || p.exitValue() != 0) return false;

            Process p2 = new ProcessBuilder(List.of(
                "docker", "run", "--rm", "alpine:3.19", "sh", "-lc", "true"
            ))
                .redirectErrorStream(true)
                .start();
            boolean finished2 = p2.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
            return finished2 && p2.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    public void setup() {
        Assumptions.assumeTrue(dockerAvailable(), "Docker not available");
        manager = new TerminalSessionManager(5, 300, 2, 512, 300);
    }

    @Test
    public void testManagerCreation() {
        assertNotNull(manager);
        assertEquals(0, manager.getSessionCount());
    }

    @Test
    public void testCreateSession() {
        TerminalSession session = manager.createSession(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON,
            2, 512, 300
        );
        
        assertNotNull(session);
        assertEquals("session-1", session.getSessionId());
        assertEquals("user-1", session.getUserId());
        assertEquals(1, manager.getSessionCount());
    }

    @Test
    public void testGetSession() {
        manager.createSession(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON,
            2, 512, 300
        );
        
        TerminalSession session = manager.getSession("session-1");
        assertNotNull(session);
        assertEquals("session-1", session.getSessionId());
    }

    @Test
    public void testGetSessionNotFound() {
        TerminalSession session = manager.getSession("nonexistent");
        assertNull(session);
    }

    @Test
    public void testGetUserSessions() {
        manager.createSession("session-1", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        manager.createSession("session-2", "user-1", TerminalSession.ExecutionType.JAVA, 4, 1024, 600);
        
        Collection<TerminalSession> sessions = manager.getUserSessions("user-1");
        assertEquals(2, sessions.size());
    }

    @Test
    public void testGetUserSessionsEmpty() {
        Collection<TerminalSession> sessions = manager.getUserSessions("user-99");
        assertEquals(0, sessions.size());
    }

    @Test
    public void testUpdateSessionStatus() {
        manager.createSession("session-1", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        
        manager.updateSessionStatus("session-1", TerminalSession.SessionStatus.COMPLETED);
        
        TerminalSession session = manager.getSession("session-1");
        assertEquals(TerminalSession.SessionStatus.COMPLETED, session.getStatus());
    }

    @Test
    public void testDeleteSession() {
        manager.createSession("session-1", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        assertEquals(1, manager.getSessionCount());
        
        boolean deleted = manager.deleteSession("session-1");
        assertTrue(deleted);
        assertEquals(0, manager.getSessionCount());
    }

    @Test
    public void testDeleteNonexistentSession() {
        boolean deleted = manager.deleteSession("nonexistent");
        assertFalse(deleted);
    }

    @Test
    public void testMaxSessionsPerUser() {
        // Create max sessions
        for (int i = 0; i < 5; i++) {
            manager.createSession("session-" + i, "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        }
        
        // Try to create one more - should fail
        assertThrows(RuntimeException.class, () -> {
            manager.createSession("session-6", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        });
    }

    @Test
    public void testMultipleUsers() {
        manager.createSession("session-1", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        manager.createSession("session-2", "user-2", TerminalSession.ExecutionType.JAVA, 4, 1024, 600);
        
        assertEquals(1, manager.getUserSessions("user-1").size());
        assertEquals(1, manager.getUserSessions("user-2").size());
        assertEquals(2, manager.getSessionCount());
    }

    @Test
    public void testCleanupInactiveSessions() throws InterruptedException {
        TerminalSessionManager shortTimeoutManager = new TerminalSessionManager(5, 1, 2, 512, 300);
        TerminalSession session = shortTimeoutManager.createSession(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300
        );
        
        assertEquals(1, shortTimeoutManager.getSessionCount());
        
        // Wait to exceed inactivity timeout (1 second)
        Thread.sleep(1500);
        
        int cleaned = shortTimeoutManager.cleanupInactiveSessions();
        assertEquals(1, cleaned);
        assertEquals(0, shortTimeoutManager.getSessionCount());
    }

    @Test
    public void testGetAllSessions() {
        manager.createSession("session-1", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        manager.createSession("session-2", "user-2", TerminalSession.ExecutionType.JAVA, 4, 1024, 600);
        manager.createSession("session-3", "user-1", TerminalSession.ExecutionType.WEB, 1, 256, 180);
        
        Collection<TerminalSession> sessions = manager.getAllSessions();
        assertEquals(3, sessions.size());
    }

    @Test
    public void testGetActiveContainerCount() {
        manager.createSession("session-1", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        manager.createSession("session-2", "user-2", TerminalSession.ExecutionType.JAVA, 4, 1024, 600);
        
        assertEquals(2, manager.getActiveContainerCount());
    }

    @Test
    public void testShutdown() {
        manager.createSession("session-1", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        manager.createSession("session-2", "user-2", TerminalSession.ExecutionType.JAVA, 4, 1024, 600);
        
        assertEquals(2, manager.getSessionCount());
        
        manager.shutdown();
        
        assertEquals(0, manager.getSessionCount());
        assertEquals(0, manager.getAllSessions().size());
    }

    @Test
    public void testDifferentExecutionTypes() {
        manager.createSession("java-session", "user-1", TerminalSession.ExecutionType.JAVA, 4, 1024, 600);
        manager.createSession("python-session", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        manager.createSession("web-session", "user-1", TerminalSession.ExecutionType.WEB, 1, 256, 180);
        
        assertEquals(3, manager.getSessionCount());
    }

    @Test
    public void testDefaultResourceLimits() {
        TerminalSession session = manager.createSession(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON,
            0, 0, 0  // Defaults should be used
        );
        
        assertEquals(2, session.getCpuLimitInCores());
        assertEquals(512, session.getMemoryLimitInMB());
        assertEquals(300, session.getTimeoutInSeconds());
    }
}
