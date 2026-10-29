package com.distributed_ide.lsp_bridge.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class LspServerConfigTest {

    @Test
    void defaults_java_hasCommand() {
        LspServerConfig config = LspServerConfig.defaults(LspServerType.JAVA, Paths.get("/tmp"));
        assertEquals(LspServerType.JAVA, config.getType());
        assertFalse(config.command().isEmpty());
        assertEquals(Paths.get("/tmp"), config.getWorkspaceRoot());
    }

    @Test
    void defaults_python_hasNpxOrPyright() {
        LspServerConfig config = LspServerConfig.defaults(LspServerType.PYTHON, Paths.get("."));
        assertEquals(LspServerType.PYTHON, config.getType());
        assertFalse(config.command().isEmpty());
    }

    @Test
    void defaults_typescript_hasTypescriptLanguageServer() {
        LspServerConfig config = LspServerConfig.defaults(LspServerType.TYPESCRIPT, Paths.get("."));
        assertEquals(LspServerType.TYPESCRIPT, config.getType());
        assertTrue(config.command().stream().anyMatch(c -> c.contains("typescript")));
    }

}
