package com.distributed_ide.terminal.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TerminalMessageTest {
    
    @Test
    public void testMessageCreation() {
        TerminalMessage msg = new TerminalMessage(TerminalMessage.MessageType.PING);
        assertEquals(TerminalMessage.MessageType.PING, msg.getType());
        assertTrue(msg.getTimestamp() > 0);
    }

    @Test
    public void testCreateSessionMessage() {
        TerminalMessage msg = TerminalMessage.createSession(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON,
            2, 512, 300
        );
        
        assertEquals(TerminalMessage.MessageType.CREATE_SESSION, msg.getType());
        assertEquals("session-1", msg.getSessionId());
        assertEquals("user-1", msg.getUserId());
        assertEquals(TerminalSession.ExecutionType.PYTHON, msg.getExecutionType());
        assertEquals(2L, msg.getCpuLimit());
        assertEquals(512L, msg.getMemoryLimit());
        assertEquals(300L, msg.getTimeout());
    }

    @Test
    public void testExecuteMessage() {
        String code = "print('Hello, World!')";
        TerminalMessage msg = TerminalMessage.execute("session-1", "user-1", code, "python");
        
        assertEquals(TerminalMessage.MessageType.EXECUTE, msg.getType());
        assertEquals("session-1", msg.getSessionId());
        assertEquals("user-1", msg.getUserId());
        assertEquals(code, msg.getCode());
        assertEquals("python", msg.getLanguage());
    }

    @Test
    public void testLogChunkMessage() {
        String logOutput = "Output line 1\nOutput line 2";
        TerminalMessage msg = TerminalMessage.logChunk("session-1", logOutput);
        
        assertEquals(TerminalMessage.MessageType.LOG_CHUNK, msg.getType());
        assertEquals("session-1", msg.getSessionId());
        assertEquals(logOutput, msg.getOutput());
    }

    @Test
    public void testResultMessage() {
        TerminalMessage msg = TerminalMessage.result(
            "session-1", "Output text", "Error text", 0
        );
        
        assertEquals(TerminalMessage.MessageType.EXECUTE_RESULT, msg.getType());
        assertEquals("session-1", msg.getSessionId());
        assertEquals("Output text", msg.getOutput());
        assertEquals("Error text", msg.getError());
        assertEquals(0, msg.getExitCode());
    }

    @Test
    public void testSessionInfoMessage() {
        TerminalMessage msg = TerminalMessage.sessionInfo("session-1", "container-1", "RUNNING");
        
        assertEquals(TerminalMessage.MessageType.SESSION_INFO, msg.getType());
        assertEquals("session-1", msg.getSessionId());
        assertEquals("container-1", msg.getContainerId());
        assertEquals("RUNNING", msg.getMessage());
    }

    @Test
    public void testErrorMessage() {
        TerminalMessage msg = TerminalMessage.error("session-1", "An error occurred");
        
        assertEquals(TerminalMessage.MessageType.ERROR, msg.getType());
        assertEquals("session-1", msg.getSessionId());
        assertEquals("An error occurred", msg.getError());
    }

    @Test
    public void testPingPongMessages() {
        TerminalMessage ping = TerminalMessage.ping();
        assertEquals(TerminalMessage.MessageType.PING, ping.getType());
        
        TerminalMessage pong = TerminalMessage.pong();
        assertEquals(TerminalMessage.MessageType.PONG, pong.getType());
    }

    @Test
    public void testMessageSetters() {
        TerminalMessage msg = new TerminalMessage();
        
        msg.setType(TerminalMessage.MessageType.EXECUTE);
        msg.setSessionId("session-1");
        msg.setUserId("user-1");
        msg.setCode("code");
        msg.setLanguage("python");
        msg.setOutput("output");
        msg.setError("error");
        msg.setExitCode(0);
        
        assertEquals(TerminalMessage.MessageType.EXECUTE, msg.getType());
        assertEquals("session-1", msg.getSessionId());
        assertEquals("user-1", msg.getUserId());
        assertEquals("code", msg.getCode());
        assertEquals("python", msg.getLanguage());
        assertEquals("output", msg.getOutput());
        assertEquals("error", msg.getError());
        assertEquals(0, msg.getExitCode());
    }

    @Test
    public void testMultipleExecutionTypes() {
        for (TerminalSession.ExecutionType type : TerminalSession.ExecutionType.values()) {
            TerminalMessage msg = TerminalMessage.createSession(
                "session", "user", type, 1, 256, 60
            );
            assertEquals(type, msg.getExecutionType());
        }
    }
}
