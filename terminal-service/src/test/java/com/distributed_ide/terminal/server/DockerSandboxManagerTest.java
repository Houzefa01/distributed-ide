package com.distributed_ide.terminal.server;

import com.distributed_ide.terminal.model.TerminalSession;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DockerSandboxManagerTest {
    private DockerSandboxManager manager;

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
        manager = new DockerSandboxManager(2, 512, 300, "distributed-ide");
    }

    @Test
    public void testManagerCreation() {
        assertNotNull(manager);
        assertEquals(0, manager.getActiveContainerCount());
    }

    @Test
    public void testCreateContainer() {
        Assumptions.assumeTrue(dockerAvailable(), "Docker not available");
        String containerId = manager.createContainer(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON,
            2, 512, 300
        );
        
        assertNotNull(containerId);
        assertTrue(containerId.contains("term-session-1"));
        assertEquals(1, manager.getActiveContainerCount());
    }

    @Test
    public void testGetContainerId() {
        Assumptions.assumeTrue(dockerAvailable(), "Docker not available");
        String containerId = manager.createContainer(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON,
            2, 512, 300
        );
        
        String retrieved = manager.getContainerId("session-1");
        assertEquals(containerId, retrieved);
    }

    @Test
    public void testStopContainer() {
        Assumptions.assumeTrue(dockerAvailable(), "Docker not available");
        manager.createContainer(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON,
            2, 512, 300
        );
        
        assertEquals(1, manager.getActiveContainerCount());
        
        boolean stopped = manager.stopContainer("session-1");
        assertTrue(stopped);
        assertEquals(0, manager.getActiveContainerCount());
    }

    @Test
    public void testStopNonexistentContainer() {
        boolean stopped = manager.stopContainer("nonexistent");
        assertFalse(stopped);
    }

    @Test
    public void testExecuteCode() {
        Assumptions.assumeTrue(dockerAvailable(), "Docker not available");
        manager.createContainer(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON,
            2, 512, 300
        );
        
        DockerSandboxManager.ExecutionResult result = 
            manager.executeCode("session-1", "print('test')", "python");
        
        assertNotNull(result);
        assertEquals(0, result.getExitCode());
    }

    @Test
    public void testExecuteCodeInNonexistentContainer() {
        DockerSandboxManager.ExecutionResult result = 
            manager.executeCode("nonexistent", "print('test')", "python");
        
        assertNotNull(result);
        assertEquals(1, result.getExitCode());
    }

    @Test
    public void testGetContainerLogs() {
        Assumptions.assumeTrue(dockerAvailable(), "Docker not available");
        manager.createContainer(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON,
            2, 512, 300
        );
        
        String logs = manager.getContainerLogs("session-1");
        assertNotNull(logs);
    }

    @Test
    public void testGetMemoryUsage() {
        Assumptions.assumeTrue(dockerAvailable(), "Docker not available");
        manager.createContainer(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON,
            2, 512, 300
        );
        
        long memory = manager.getMemoryUsage("session-1");
        assertEquals(0, memory); // Simulated
    }

    @Test
    public void testMultipleContainers() {
        Assumptions.assumeTrue(dockerAvailable(), "Docker not available");
        String container1 = manager.createContainer(
            "session-1", "user-1", TerminalSession.ExecutionType.PYTHON,
            2, 512, 300
        );
        
        String container2 = manager.createContainer(
            "session-2", "user-2", TerminalSession.ExecutionType.JAVA,
            4, 1024, 600
        );
        
        assertNotEquals(container1, container2);
        assertEquals(2, manager.getActiveContainerCount());
    }

    @Test
    public void testCleanupAllContainers() {
        Assumptions.assumeTrue(dockerAvailable(), "Docker not available");
        manager.createContainer("session-1", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300);
        manager.createContainer("session-2", "user-2", TerminalSession.ExecutionType.JAVA, 4, 1024, 600);
        
        assertEquals(2, manager.getActiveContainerCount());
        
        manager.cleanupAllContainers();
        assertEquals(0, manager.getActiveContainerCount());
    }

    @Test
    public void testDifferentExecutionTypes() {
        Assumptions.assumeTrue(dockerAvailable(), "Docker not available");
        for (TerminalSession.ExecutionType type : TerminalSession.ExecutionType.values()) {
            String containerId = manager.createContainer(
                "session-" + type.name(), "user-1", type, 1, 256, 60
            );
            assertNotNull(containerId);
        }
        
        assertEquals(4, manager.getActiveContainerCount());
    }
}
