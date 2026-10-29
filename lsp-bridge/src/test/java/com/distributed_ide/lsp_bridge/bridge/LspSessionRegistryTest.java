package com.distributed_ide.lsp_bridge.bridge;

import com.distributed_ide.lsp_bridge.config.LspServerType;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class LspSessionRegistryTest {

    @Test
    void sessionKeyFor_sameInput_sameKey() {
        String k1 = LspSessionRegistry.sessionKeyFor(LspServerType.JAVA, "file:///tmp");
        String k2 = LspSessionRegistry.sessionKeyFor(LspServerType.JAVA, "file:///tmp");
        assertEquals(k1, k2);
    }

    @Test
    void sessionKeyFor_differentLanguage_differentKey() {
        String kJava = LspSessionRegistry.sessionKeyFor(LspServerType.JAVA, "file:///tmp");
        String kPy = LspSessionRegistry.sessionKeyFor(LspServerType.PYTHON, "file:///tmp");
        assertNotEquals(kJava, kPy);
    }

    @Test
    void sessionKeyFor_nullUri_usesCurrentDir() {
        String k = LspSessionRegistry.sessionKeyFor(LspServerType.JAVA, null);
        assertNotNull(k);
        assertTrue(k.contains("JAVA"));
    }
}
