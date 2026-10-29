package com.distributed_ide.terminal.server;

import com.distributed_ide.terminal.model.TerminalSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Docker container sandbox manager for secure code execution.
 * Creates isolated containers with resource limits per session.
 */
public class DockerSandboxManager {
    private static final Logger logger = LoggerFactory.getLogger(DockerSandboxManager.class);

    // Container tracking: sessionId -> containerId
    private final Map<String, String> sessionContainers;
    
    // Resource configuration
    private final long defaultCpuCores;
    private final long defaultMemoryMB;
    private final long defaultTimeoutSeconds;
    private final String dockerImageBase;

    private static final long DOCKER_RUN_TIMEOUT_MS = 180_000;
    private static final long DOCKER_STOP_TIMEOUT_MS = 60_000;
    private static final long DOCKER_LOGS_TIMEOUT_MS = 20_000;
    private static final long DOCKER_IMAGE_PULL_TIMEOUT_MS = 300_000;

    public DockerSandboxManager(long cpuCores, long memoryMB, long timeoutSeconds, String dockerImage) {
        this.sessionContainers = new ConcurrentHashMap<>();
        this.defaultCpuCores = cpuCores;
        this.defaultMemoryMB = memoryMB;
        this.defaultTimeoutSeconds = timeoutSeconds;
        this.dockerImageBase = dockerImage;
    }

    /**
     * Create a new Docker container sandbox for a session.
     */
    public String createContainer(String sessionId, String userId, TerminalSession.ExecutionType execType,
                                  long cpuLimit, long memoryLimit, long timeoutSeconds) {
        try {
            // Generate container ID
            String containerId = "term-" + sessionId + "-" + System.nanoTime();
            
            // Select Docker image based on execution type
            String image = selectImage(execType);

            // Ensure image is present before docker run (prevents docker run timeout during pulls)
            ensureImageAvailable(image);
            
            // Build Docker run command with resource limits
            String dockerCommand = buildDockerCommand(containerId, image, cpuLimit, memoryLimit, timeoutSeconds);
            
            logger.info("Creating container: {} for session: {} with limits: CPU={}, Mem={}", 
                       containerId, sessionId, cpuLimit, memoryLimit);
            
            ExecResult res = exec(List.of("sh", "-lc", dockerCommand), DOCKER_RUN_TIMEOUT_MS);
            if (res.exitCode != 0) {
                throw new RuntimeException("docker run failed: " + res.stderr);
            }

            sessionContainers.put(sessionId, containerId);
            
            return containerId;
        } catch (Exception e) {
            logger.error("Failed to create container for session: {}", sessionId, e);
            throw new RuntimeException("Container creation failed: " + e.getMessage());
        }
    }

    /**
     * Stop and remove a container.
     */
    public boolean stopContainer(String sessionId) {
        String containerId = sessionContainers.get(sessionId);
        if (containerId == null) {
            logger.warn("No container found for session: {}", sessionId);
            return false;
        }

        try {
            logger.info("Stopping container: {} for session: {}", containerId, sessionId);

            // Container started with --rm, so stop is enough.
            ExecResult stop = exec(List.of("docker", "stop", containerId), DOCKER_STOP_TIMEOUT_MS);
            if (stop.exitCode != 0) {
                logger.warn("docker stop failed for {}: {}", containerId, stop.stderr);
            }
            
            sessionContainers.remove(sessionId);
            logger.info("Container stopped and removed: {}", containerId);
            return true;
        } catch (Exception e) {
            logger.error("Failed to stop container: {}", containerId, e);
            return false;
        }
    }

    /**
     * Execute code inside a container.
     */
    public ExecutionResult executeCode(String sessionId, String code, String language) {
        String containerId = sessionContainers.get(sessionId);
        if (containerId == null) {
            return new ExecutionResult("", "Container not found for session: " + sessionId, 1);
        }

        try {
            logger.info("Executing code in container: {} - language: {}", containerId, language);

            String lang = language != null ? language.trim().toLowerCase() : "shell";
            String script = buildScript(code != null ? code : "", lang);
            ExecResult res = exec(List.of(
                "docker", "exec", "-i", containerId,
                "sh", "-lc", script
            ), Math.max(5_000, defaultTimeoutSeconds * 1000));

            return new ExecutionResult(res.stdout, res.stderr, res.exitCode);
        } catch (Exception e) {
            logger.error("Code execution failed in container: {}", containerId, e);
            return new ExecutionResult("", "Execution error: " + e.getMessage(), 1);
        }
    }

    /**
     * Get logs from a running container.
     */
    public String getContainerLogs(String sessionId) {
        String containerId = sessionContainers.get(sessionId);
        if (containerId == null) {
            return "";
        }

        try {
            logger.debug("Fetching logs from container: {}", containerId);

            ExecResult res = exec(List.of("docker", "logs", "--tail", "200", containerId), DOCKER_LOGS_TIMEOUT_MS);
            return res.stdout != null ? res.stdout : "";
        } catch (Exception e) {
            logger.error("Failed to fetch logs from container: {}", containerId, e);
            return "";
        }
    }

    /**
     * Get memory usage of a container.
     */
    public long getMemoryUsage(String sessionId) {
        String containerId = sessionContainers.get(sessionId);
        if (containerId == null) {
            return 0;
        }

        try {
            // TODO: Execute "docker stats" command (in real implementation)
            return 0;
        } catch (Exception e) {
            logger.error("Failed to get memory usage for container: {}", containerId, e);
            return 0;
        }
    }

    /**
     * Remove a container by session ID.
     */
    public boolean removeContainer(String sessionId) {
        return stopContainer(sessionId);
    }

    /**
     * Clean up all containers (for shutdown).
     */
    public void cleanupAllContainers() {
        logger.info("Cleaning up all containers, total: {}", sessionContainers.size());

        List<String> sessions = new ArrayList<>(sessionContainers.keySet());
        for (String sessionId : sessions) {
            try {
                stopContainer(sessionId);
            } catch (Exception e) {
                logger.error("Error during cleanup for session: {}", sessionId, e);
            }
        }

        logger.info("All containers cleaned up");
    }

    /**
     * Shutdown the Docker manager and clean up all resources.
     */
    public void shutdown() {
        logger.info("Shutting down Docker sandbox manager");
        cleanupAllContainers();
        logger.info("Docker sandbox manager shutdown complete");
    }

    // Helper methods

    private String selectImage(TerminalSession.ExecutionType execType) {
        String base = dockerImageBase != null ? dockerImageBase.trim() : "";
        String candidate = switch (execType) {
            case JAVA -> base + ":java-latest";
            case PYTHON -> base + ":python-latest";
            case WEB -> base + ":web-latest";
            case SHELL -> base + ":shell-latest";
        };

        if (!base.isEmpty() && imageExistsLocally(candidate)) {
            return candidate;
        }

        // Safe public fallbacks
        return switch (execType) {
            case JAVA -> "eclipse-temurin:21-jdk";
            case PYTHON -> "python:3.12-alpine";
            case WEB -> "node:20-alpine";
            case SHELL -> "alpine:3.19";
        };
    }

    private String buildDockerCommand(String containerId, String image, long cpuLimit, 
                                      long memoryLimit, long timeoutSeconds) {
        // Note: Docker has no --time-limit flag. Timeout is enforced by our exec wrapper.
        // Run an idle shell so the container is ready for docker exec.
        return String.format(java.util.Locale.US,
            "docker run -d --rm --name %s --cpus %.1f --memory %dm %s sh -lc 'while true; do sleep 1; done'",
            containerId, (double) cpuLimit, memoryLimit, image
        );
    }

    private boolean imageExistsLocallyOrPullable(String image) {
        try {
            if (imageExistsLocally(image)) return true;
            ExecResult pull = exec(List.of("docker", "pull", image), DOCKER_IMAGE_PULL_TIMEOUT_MS);
            return pull.exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean imageExistsLocally(String image) {
        try {
            ExecResult inspect = exec(List.of("docker", "image", "inspect", image), 10_000);
            return inspect.exitCode == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureImageAvailable(String image) {
        if (!imageExistsLocallyOrPullable(image)) {
            throw new RuntimeException("Docker image not available: " + image);
        }
    }

    private String buildScript(String code, String lang) {
        String safeCode = code;
        return switch (lang) {
            case "python", "py" -> "python - <<'PY'\n" + safeCode + "\nPY\n";
            case "node", "js", "javascript" -> "node - <<'JS'\n" + safeCode + "\nJS\n";
            case "java" -> buildJavaScript(safeCode);
            default -> safeCode;
        };
    }

    private String buildJavaScript(String code) {
        String src;
        if (code.contains("class ") && code.contains("public static void main")) {
            src = code;
        } else {
            src = "public class Main { public static void main(String[] args) throws Exception { " + code + " } }";
        }
        return "cat > Main.java <<'JAVA'\n" + src + "\nJAVA\n" +
            "javac Main.java && java Main\n";
    }

    private ExecResult exec(List<String> command, long timeoutMs) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        Process p = pb.start();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        Thread t1 = new Thread(() -> drain(p.getInputStream(), out), "docker-out");
        Thread t2 = new Thread(() -> drain(p.getErrorStream(), err), "docker-err");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();

        boolean finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            p.destroyForcibly();
            joinQuietly(t1, 3_000);
            joinQuietly(t2, 3_000);
            return new ExecResult(out.toString(StandardCharsets.UTF_8),
                    "Timeout running: " + String.join(" ", command) + "\n" + err.toString(StandardCharsets.UTF_8), 124);
        }

        joinQuietly(t1, 5_000);
        joinQuietly(t2, 5_000);

        return new ExecResult(out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8), p.exitValue());
    }

    private static void joinQuietly(Thread t, long timeoutMs) {
        try {
            t.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void drain(InputStream in, ByteArrayOutputStream out) {
        if (in == null) return;
        try (in) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
        } catch (IOException ignored) {
        }
    }

    private static final class ExecResult {
        final String stdout;
        final String stderr;
        final int exitCode;
        ExecResult(String stdout, String stderr, int exitCode) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
        }
    }

    // Getters
    public String getContainerId(String sessionId) {
        return sessionContainers.get(sessionId);
    }

    public int getActiveContainerCount() {
        return sessionContainers.size();
    }

    /**
     * Execution result wrapper.
     */
    public static class ExecutionResult {
        private final String output;
        private final String error;
        private final int exitCode;

        public ExecutionResult(String output, String error, int exitCode) {
            this.output = output;
            this.error = error;
            this.exitCode = exitCode;
        }

        public String getOutput() {
            return output;
        }

        public String getError() {
            return error;
        }

        public int getExitCode() {
            return exitCode;
        }
    }
}
