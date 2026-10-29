package com.distributed_ide.lsp_bridge.process;

import com.distributed_ide.lsp_bridge.config.LspServerConfig;
import com.distributed_ide.lsp_bridge.config.LspServerType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LspProcessManagerTest {

    private final LspProcessManager manager = new LspProcessManager();

    @AfterEach
    void tearDown() {
        manager.stopAll();
    }

    @Test
    void processKey_isDeterministic() {
        String k1 = LspProcessManager.processKey(LspServerType.JAVA, Paths.get("/foo"));
        String k2 = LspProcessManager.processKey(LspServerType.JAVA, Paths.get("/foo"));
        assertEquals(k1, k2);
        assertTrue(k1.contains("JAVA"));
        assertTrue(k1.contains("/foo"));
    }

    @Test
    void start_returnsHandle() throws Exception {
        List<String> cmd = System.getProperty("os.name").toLowerCase().contains("win")
            ? List.of("cmd", "/c", "ping", "127.0.0.1", "-n", "2")
            : List.of("sleep", "1");
        LspServerConfig config = new LspServerConfig(LspServerType.JAVA, cmd, Map.of(), Paths.get("."));
        LspProcessManager.LspProcessHandle handle = manager.start(config);
        assertNotNull(handle);
        assertEquals(LspServerType.JAVA, handle.getType());
        assertNotNull(handle.getKey());
        manager.stop(handle.getKey());
    }

    @Test
    void stop_removesProcess() throws Exception {
        List<String> cmd = System.getProperty("os.name").toLowerCase().contains("win")
            ? List.of("cmd", "/c", "ping", "127.0.0.1", "-n", "3")
            : List.of("sleep", "2");
        LspServerConfig config = new LspServerConfig(LspServerType.JAVA, cmd, Map.of(), Paths.get("."));
        LspProcessManager.LspProcessHandle h = manager.start(config);
        String key = h.getKey();
        assertNotNull(manager.get(key));
        manager.stop(key);
        assertNull(manager.get(key));
    }
}
