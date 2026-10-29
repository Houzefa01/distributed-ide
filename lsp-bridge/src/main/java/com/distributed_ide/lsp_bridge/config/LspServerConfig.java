package com.distributed_ide.lsp_bridge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LspServerConfig {

    private static final Logger LOG = LoggerFactory.getLogger(LspServerConfig.class);
    private final LspServerType type;
    private final List<String> command;
    private final Map<String, String> env;
    private final Path workspaceRoot;

    public LspServerConfig(LspServerType type, List<String> command, Map<String, String> env, Path workspaceRoot) {
        this.type = type;
        this.command = command != null ? new ArrayList<>(command) : new ArrayList<>();
        this.env = env != null ? Map.copyOf(env) : Map.of();
        this.workspaceRoot = workspaceRoot;
    }

    public LspServerType getType() { return type; }
    public List<String> command() { return List.copyOf(command); }
    public Map<String, String> getEnv() { return env; }
    public Path getWorkspaceRoot() { return workspaceRoot; }

    public static LspServerConfig defaults(LspServerType type, Path workspaceRoot) {
        switch (type) {
            case JAVA: return jdtlsConfig(workspaceRoot);
            case PYTHON: return pyrightConfig(workspaceRoot);
            case TYPESCRIPT: return typescriptConfig(workspaceRoot);
            default: throw new IllegalArgumentException("Unknown: " + type);
        }
    }

    private static LspServerConfig jdtlsConfig(Path workspaceRoot) {
        String jdtls = System.getenv("JDTLS_HOME");
        List<String> cmd = new ArrayList<>();
        if (jdtls != null && !jdtls.isEmpty()) {
            cmd.add(Paths.get(jdtls).resolve("bin").resolve("jdtls").toString());
        } else {
            cmd.add("jdtls");
        }
        LOG.debug("JDT command: {}", cmd);
        return new LspServerConfig(LspServerType.JAVA, cmd, Map.of(), workspaceRoot);
    }

    private static LspServerConfig pyrightConfig(Path workspaceRoot) {
        List<String> cmd = new ArrayList<>();
        if ("false".equals(System.getProperty("lsp.bridge.pyright.npx"))) {
            cmd.add("pyright-langserver");
        } else {
            cmd.add("npx"); cmd.add("--yes"); cmd.add("pyright-langserver");
        }
        return new LspServerConfig(LspServerType.PYTHON, cmd, Map.of(), workspaceRoot);
    }

    private static LspServerConfig typescriptConfig(Path workspaceRoot) {
        List<String> cmd = List.of("npx", "--yes", "typescript-language-server", "--stdio");
        return new LspServerConfig(LspServerType.TYPESCRIPT, cmd, Map.of(), workspaceRoot);
    }
}
