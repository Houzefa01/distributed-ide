package com.distributed_ide.terminal.server;

import com.distributed_ide.terminal.model.TerminalMessage;
import com.distributed_ide.terminal.model.TerminalSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

public class TerminalWebSocketServerTest {
    private TerminalSessionManager manager;
    private TerminalWebSocketServer server;

    @BeforeEach
    public void setup() {
        manager = new TerminalSessionManager(5, 300, 2, 512, 300);
        // Note: We don't actually start the WebSocket server in unit tests
        // We just test the manager integration
    }

    @Test
    public void testManagerIntegration() {
        assertNotNull(manager);
        assertEquals(0, manager.getSessionCount());
    }

    @Test
    public void testCreateSessionFlow() {
        TerminalMessage createMsg = TerminalMessage.createSession(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON,
            2, 512, 300
        );
        
        assertNotNull(createMsg);
        assertEquals(TerminalMessage.MessageType.CREATE_SESSION, createMsg.getType());
        
        // Simulate server-side processing
        TerminalSession session = manager.createSession(
            createMsg.getSessionId(),
            createMsg.getUserId(),
            createMsg.getExecutionType(),
            createMsg.getCpuLimit(),
            createMsg.getMemoryLimit(),
            createMsg.getTimeout()
        );
        
        assertNotNull(session);
        assertEquals(1, manager.getSessionCount());
    }

    @Test
    public void testExecuteCodeFlow() {
        // First create session
        TerminalSession session = manager.createSession(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON,
            2, 512, 300
        );
        
        // Then create execute message
        TerminalMessage execMsg = TerminalMessage.execute(
            "session-1", "user-1", "print('hello')", "python"
        );
        
        assertEquals(TerminalMessage.MessageType.EXECUTE, execMsg.getType());
        
        // Simulate execution
        DockerSandboxManager.ExecutionResult result = 
            manager.getDockerManager().executeCode(
                execMsg.getSessionId(),
                execMsg.getCode(),
                execMsg.getLanguage()
            );
        
        assertNotNull(result);
        assertEquals(0, result.getExitCode());
    }

    @Test
    public void testMultipleSessionManagement() {
        // Create multiple sessions
        manager.createSession("session-1", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        manager.createSession("session-2", "user-1", TerminalSession.ExecutionType.JAVA, 4, 1024, 600);
        manager.createSession("session-3", "user-2", TerminalSession.ExecutionType.WEB, 1, 256, 180);
        
        assertEquals(3, manager.getSessionCount());
        assertEquals(2, manager.getUserSessions("user-1").size());
        assertEquals(1, manager.getUserSessions("user-2").size());
    }

    @Test
    public void testSessionCleanup() {
        manager.createSession("session-1", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        
        boolean deleted = manager.deleteSession("session-1");
        assertTrue(deleted);
        assertEquals(0, manager.getSessionCount());
    }

    @Test
    public void testHeaderbeatAndPing() {
        TerminalMessage ping = TerminalMessage.ping();
        assertEquals(TerminalMessage.MessageType.PING, ping.getType());
        
        TerminalMessage pong = TerminalMessage.pong();
        assertEquals(TerminalMessage.MessageType.PONG, pong.getType());
    }

    @Test
    public void testErrorHandling() {
        TerminalMessage errorMsg = TerminalMessage.error(
            "session-1", "Container not found"
        );
        
        assertEquals(TerminalMessage.MessageType.ERROR, errorMsg.getType());
        assertEquals("Container not found", errorMsg.getError());
    }

    @Test
    public void testLogStreaming() {
        // Create session
        manager.createSession("session-1", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        
        // Get logs
        String logs = manager.getDockerManager().getContainerLogs("session-1");
        assertNotNull(logs);
        
        // Create log chunk message
        TerminalMessage logMsg = TerminalMessage.logChunk("session-1", logs);
        assertEquals(TerminalMessage.MessageType.LOG_CHUNK, logMsg.getType());
    }
}
