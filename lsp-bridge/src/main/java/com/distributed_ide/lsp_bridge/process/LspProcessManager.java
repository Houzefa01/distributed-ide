package com.distributed_ide.lsp_bridge.process;

import com.distributed_ide.lsp_bridge.config.LspServerConfig;
import com.distributed_ide.lsp_bridge.config.LspServerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LspProcessManager {

    private static final Logger LOG = LoggerFactory.getLogger(LspProcessManager.class);
    private final Map<String, LspProcessHandle> processes = new ConcurrentHashMap<>();

    public static String processKey(LspServerType type, Path workspaceRoot) {
        return type.name() + ":" + (workspaceRoot != null ? workspaceRoot.toAbsolutePath().toString() : "");
    }

    public LspProcessHandle start(LspServerConfig config) throws IOException {
        String key = processKey(config.getType(), config.getWorkspaceRoot());
        LspProcessHandle existing = processes.get(key);
        if (existing != null && existing.isAlive()) return existing;
        if (existing != null) processes.remove(key);

        ProcessBuilder pb = new ProcessBuilder(config.command());
        pb.redirectErrorStream(false);
        if (config.getWorkspaceRoot() != null) pb.directory(config.getWorkspaceRoot().toFile());
        if (!config.getEnv().isEmpty()) pb.environment().putAll(config.getEnv());

        Process p = pb.start();
        LspProcessHandle h = new LspProcessHandle(config.getType(), p, key);
        processes.put(key, h);
        LOG.info("Started LSP: {}", key);
        return h;
    }

    public void stop(String key) {
        LspProcessHandle h = processes.remove(key);
        if (h != null) { h.destroy(); LOG.info("Stopped: {}", key); }
    }

    public LspProcessHandle get(String key) { return processes.get(key); }
    public void stopAll() { processes.keySet().forEach(this::stop); }

    public static final class LspProcessHandle {
        private final LspServerType type;
        private final Process process;
        private final String key;

        LspProcessHandle(LspServerType type, Process process, String key) {
            this.type = type; this.process = process; this.key = key;
        }
        public InputStream getInputStream() { return process.getInputStream(); }
        public OutputStream getOutputStream() { return process.getOutputStream(); }
        public boolean isAlive() { return process.isAlive(); }
        public void destroy() { process.destroyForcibly(); }
        public String getKey() { return key; }
        public LspServerType getType() { return type; }
    }
}
