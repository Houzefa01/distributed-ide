package com.distributed_ide.javafx_client.service;

import com.distributed_ide.javafx_client.util.OSDetector;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * Local terminal service using ProcessBuilder.
 * Executes system commands in a native process.
 * Cross-platform compatible with automatic shell detection.
 */
public class LocalTerminalService {

    private static final Logger LOG = LoggerFactory.getLogger(LocalTerminalService.class);

    private final ExecutorService executor;
    private Process currentProcess;
    private BufferedWriter processInput;
    private BiConsumer<String, Boolean> outputListener; // (line, isError)

    private Path workingDirectory;
    private OSDetector.OperatingSystem osType;

    public LocalTerminalService() {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "local-terminal-worker");
            t.setDaemon(true);
            return t;
        });
        this.osType = OSDetector.detectOS();
        this.workingDirectory = Path.of(System.getProperty("user.home"));
    }

    /**
     * Sets the working directory for commands.
     */
    public void setWorkingDirectory(Path dir) {
        if (dir != null && dir.toFile().isDirectory()) {
            this.workingDirectory = dir;
            LOG.info("Working directory set to: {}", dir);
        }
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    /**
     * Sets the listener to receive command output.
     */
    public void setOutputListener(BiConsumer<String, Boolean> listener) {
        this.outputListener = listener;
    }

    /**
     * Starts an interactive shell (cmd on Windows, bash/zsh on macOS/Linux).
     * Automatically detects available shell based on platform.
     */
    public CompletableFuture<Void> startInteractiveShell() {
        return CompletableFuture.runAsync(() -> {
            try {
                stopCurrentProcess();

                List<String> command = new ArrayList<>();
                if (osType == OSDetector.OperatingSystem.WINDOWS) {
                    command.add("cmd.exe");
                    command.add("/Q"); // Quiet mode
                } else {
                    // Detect available shell on Unix-like systems
                    try {
                        String shell = OSDetector.getShell();
                        command.add(shell);
                        command.add("-i"); // Interactive
                        LOG.info("Using shell: {}", shell);
                    } catch (RuntimeException e) {
                        LOG.error("Failed to detect shell", e);
                        notifyOutput("Error: " + e.getMessage(), true);
                        return;
                    }
                }

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(workingDirectory.toFile());
                pb.redirectErrorStream(false); // Separate stdout and stderr

                currentProcess = pb.start();
                processInput = new BufferedWriter(new OutputStreamWriter(currentProcess.getOutputStream()));

                // Thread to read stdout
                executor.submit(() -> readStream(currentProcess.getInputStream(), false));

                // Thread to read stderr
                executor.submit(() -> readStream(currentProcess.getErrorStream(), true));

                // Thread to monitor process exit
                executor.submit(() -> {
                    try {
                        int exitCode = currentProcess.waitFor();
                        notifyOutput("Process exited with code: " + exitCode, false);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

                notifyOutput("Interactive shell started in: " + workingDirectory, false);
                LOG.info("Interactive shell started");

            } catch (IOException e) {
                LOG.error("Failed to start interactive shell", e);
                notifyOutput("Failed to start shell: " + e.getMessage(), true);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * Executes a single command (non-interactive).
     * Platform-aware command execution.
     */
    public CompletableFuture<Integer> executeCommand(String command) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> cmd = new ArrayList<>();
                if (osType == OSDetector.OperatingSystem.WINDOWS) {
                    cmd.add("cmd.exe");
                    cmd.add("/c");
                    cmd.add(command);
                } else {
                    // Use detected shell for command execution
                    try {
                        String shell = OSDetector.getShell();
                        cmd.add(shell);
                        cmd.add("-c");
                        cmd.add(command);
                    } catch (RuntimeException e) {
                        LOG.error("Failed to detect shell for command execution", e);
                        return -1;
                    }
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(workingDirectory.toFile());
                pb.redirectErrorStream(false);

                Process process = pb.start();

                // Read stdout
                executor.submit(() -> readStream(process.getInputStream(), false));

                // Read stderr
                executor.submit(() -> readStream(process.getErrorStream(), true));

                int exitCode = process.waitFor();
                LOG.debug("Command '{}' exited with code {}", command, exitCode);
                return exitCode;

            } catch (IOException | InterruptedException e) {
                LOG.error("Failed to execute command: {}", command, e);
                notifyOutput("Failed to execute command: " + e.getMessage(), true);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * Sends a command to the current interactive shell.
     */
    public void sendInput(String input) {
        if (currentProcess == null || !currentProcess.isAlive() || processInput == null) {
            notifyOutput("No active shell session. Start a shell first.", true);
            return;
        }

        executor.submit(() -> {
            try {
                processInput.write(input);
                processInput.newLine();
                processInput.flush();
                LOG.debug("Sent input to shell: {}", input.trim());
            } catch (IOException e) {
                LOG.error("Failed to send input to shell", e);
                notifyOutput("Failed to send input: " + e.getMessage(), true);
            }
        });
    }

    /**
     * Stops the current process.
     */
    public void stopCurrentProcess() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
            try {
                if (processInput != null) {
                    processInput.close();
                }
            } catch (IOException e) {
                LOG.warn("Error closing process input", e);
            }
            currentProcess = null;
            processInput = null;
            LOG.info("Process stopped");
        }
    }

    /**
     * Checks if a process is active.
     */
    public boolean isProcessActive() {
        return currentProcess != null && currentProcess.isAlive();
    }

    /**
     * Shuts down the service and stops all processes.
     */
    public void shutdown() {
        stopCurrentProcess();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("LocalTerminalService shut down");
    }

    // ═══════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════

    private void readStream(InputStream stream, boolean isError) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String output = line;
                notifyOutput(output, isError);
            }
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.contains("Stream closed")) {
                LOG.warn("Error reading stream", e);
            }
        }
    }

    private void notifyOutput(String line, boolean isError) {
        if (outputListener != null) {
            Platform.runLater(() -> outputListener.accept(line, isError));
        }
    }
}
