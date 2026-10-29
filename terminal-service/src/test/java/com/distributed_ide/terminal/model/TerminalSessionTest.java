package com.distributed_ide.terminal.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

public class TerminalSessionTest {
    private TerminalSession session;

    @BeforeEach
    public void setup() {
        session = new TerminalSession(
            "session-1", "user-1", "container-1",
            TerminalSession.ExecutionType.PYTHON,
            2, 512, 300
        );
    }

    @Test
    public void testSessionCreation() {
        assertEquals("session-1", session.getSessionId());
        assertEquals("user-1", session.getUserId());
        assertEquals("container-1", session.getContainerId());
        assertEquals(TerminalSession.ExecutionType.PYTHON, session.getExecutionType());
    }

    @Test
    public void testResourceLimits() {
        assertEquals(2, session.getCpuLimitInCores());
        assertEquals(512, session.getMemoryLimitInMB());
        assertEquals(300, session.getTimeoutInSeconds());
    }

    @Test
    public void testInitialStatus() {
        assertEquals(TerminalSession.SessionStatus.CREATED, session.getStatus());
    }

    @Test
    public void testStatusUpdate() {
        session.setStatus(TerminalSession.SessionStatus.RUNNING);
        assertEquals(TerminalSession.SessionStatus.RUNNING, session.getStatus());
        
        session.setStatus(TerminalSession.SessionStatus.COMPLETED);
        assertEquals(TerminalSession.SessionStatus.COMPLETED, session.getStatus());
    }

    @Test
    public void testActivityTracking() {
        long beforeActivity = System.currentTimeMillis();
        session.updateActivity();
        long afterActivity = System.currentTimeMillis();
        
        assertTrue(session.getLastActivityAt() >= beforeActivity);
        assertTrue(session.getLastActivityAt() <= afterActivity);
    }

    @Test
    public void testInactivityDuration() throws InterruptedException {
        long beforeInactivity = session.getInactivityDurationInSeconds();
        Thread.sleep(100);
        long afterInactivity = session.getInactivityDurationInSeconds();
        
        assertTrue(afterInactivity >= beforeInactivity);
    }

    @Test
    public void testIsAliveCheck() {
        assertTrue(session.isAlive(5)); // Alive with 5 second timeout
    }

    @Test
    public void testMetadataOperations() {
        session.setMetadataValue("environment", "production");
        assertEquals("production", session.getMetadataValue("environment"));
        
        session.setMetadataValue("version", "1.0");
        assertEquals("1.0", session.getMetadataValue("version"));
    }

    @Test
    public void testSessionInfo() {
        String info = session.getSessionInfo();
        assertTrue(info.contains("session-1"));
        assertTrue(info.contains("user-1"));
        assertTrue(info.contains("container-1"));
        assertTrue(info.contains("PYTHON"));
    }

    @Test
    public void testDifferentExecutionTypes() {
        TerminalSession javaSession = new TerminalSession(
            "session-java", "user-1", "container-java",
            TerminalSession.ExecutionType.JAVA,
            4, 1024, 600
        );
        assertEquals(TerminalSession.ExecutionType.JAVA, javaSession.getExecutionType());
        
        TerminalSession webSession = new TerminalSession(
            "session-web", "user-1", "container-web",
            TerminalSession.ExecutionType.WEB,
            1, 256, 180
        );
        assertEquals(TerminalSession.ExecutionType.WEB, webSession.getExecutionType());
    }
}
