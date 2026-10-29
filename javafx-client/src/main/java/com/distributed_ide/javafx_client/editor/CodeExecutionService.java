package com.distributed_ide.javafx_client.editor;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code execution service for the IDE.
 *
 * Supports:
 * - Java: compile with javac, then run with java
 * - Python: run with python3/python interpreter
 * - HTML: open in system default browser
 *
 * All execution is async. Output is streamed via a BiConsumer(line, isError).
 */
public class CodeExecutionService {

    private static final Logger LOG = LoggerFactory.getLogger(CodeExecutionService.class);

    private static final ExecutorService EXEC_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "code-exec");
        t.setDaemon(true);
        return t;
    });

    private volatile Process currentProcess;

    // Output listener: (line, isStderr)
    private BiConsumer<String, Boolean> outputListener;

    public void setOutputListener(BiConsumer<String, Boolean> listener) {
        this.outputListener = listener;
    }

    /**
     * Runs the given file based on its language.
     * Returns a future with the exit code (or -1 on failure).
     */
    public CompletableFuture<Integer> runFile(String filePath, String language) {
        if (filePath == null || language == null) {
            return CompletableFuture.completedFuture(-1);
        }
        return switch (language.toLowerCase()) {
            case "java" -> runJava(Path.of(filePath));
            case "python" -> runPython(Path.of(filePath));
            case "html", "htm" -> openHtmlInBrowser(Path.of(filePath));
            default -> {
                emit("Unsupported language for execution: " + language, true);
                yield CompletableFuture.completedFuture(-1);
            }
        };
    }

    /**
     * Stops the currently running process if any.
     */
    public void stop() {
        Process p = currentProcess;
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
            emit("Process terminated.", true);
            currentProcess = null;
        }
    }

    public boolean isRunning() {
        Process p = currentProcess;
        return p != null && p.isAlive();
    }

    // ═══════════════════════════════════════════════════════════
    //  JAVA: javac compile → java run
    // ═══════════════════════════════════════════════════════════

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^\\s*package\\s+([a-zA-Z_][a-zA-Z0-9_.]*?)\\s*;", Pattern.MULTILINE);

    private CompletableFuture<Integer> runJava(Path file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path dir = file.getParent();
                String fileName = file.getFileName().toString();
                if (!fileName.endsWith(".java")) {
                    emit("Not a Java file: " + fileName, true);
                    return -1;
                }
                String simpleClassName = fileName.substring(0, fileName.length() - 5);

                // Detect package declaration to resolve source root and fully-qualified class name
                String sourceCode = Files.readString(file, StandardCharsets.UTF_8);
                String packageName = detectPackage(sourceCode);
                String fullyQualifiedClass = packageName != null
                        ? packageName + "." + simpleClassName : simpleClassName;

                // Resolve source root: walk up from file directory by package depth
                Path sourceRoot = dir;
                if (packageName != null) {
                    int depth = packageName.split("\\.").length;
                    Path candidate = dir;
                    for (int i = 0; i < depth; i++) {
                        candidate = candidate.getParent();
                        if (candidate == null) break;
                    }
                    if (candidate != null) sourceRoot = candidate;
                }

                // Build classpath: source root + any lib/*.jar files
                String classpath = buildClasspath(sourceRoot);

                emit("▶ Compiling " + fileName + "...", false);
                if (packageName != null) {
                    emit("  Package: " + packageName, false);
                }
                emit("  Source root: " + sourceRoot, false);
                if (!classpath.equals(sourceRoot.toString())) {
                    emit("  Classpath: " + classpath, false);
                }

                // Step 1: Compile with classpath
                List<String> compileCmd = new ArrayList<>();
                compileCmd.add("javac");
                compileCmd.add("-cp");
                compileCmd.add(classpath);
                compileCmd.add("-d");
                compileCmd.add(sourceRoot.toString());
                compileCmd.add(file.toString());

                int compileExit = executeProcess(sourceRoot, compileCmd.toArray(new String[0]));
                if (compileExit != 0) {
                    emit("✗ Compilation failed (exit code " + compileExit + ")", true);
                    return compileExit;
                }
                emit("✓ Compilation successful", false);

                // Step 2: Run with classpath
                emit("▶ Running " + fullyQualifiedClass + "...", false);
                emit("─".repeat(40), false);
                int runExit = executeProcess(sourceRoot,
                        "java", "-cp", classpath, fullyQualifiedClass);
                emit("─".repeat(40), false);
                emit("Process exited with code: " + runExit, runExit != 0);

                // Cleanup .class files (best effort)
                cleanupClassFiles(sourceRoot, packageName, simpleClassName);

                return runExit;

            } catch (Exception e) {
                LOG.warn("Java execution failed", e);
                emit("Execution error: " + e.getMessage(), true);
                return -1;
            }
        }, EXEC_POOL);
    }

    /**
     * Detects the package declaration from Java source code.
     * Returns null if no package declaration found (default package).
     */
    private static String detectPackage(String sourceCode) {
        Matcher m = PACKAGE_PATTERN.matcher(sourceCode);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Builds a classpath string from the source root.
     * Includes the source root itself and any JAR files in lib/ directories.
     */
    private static String buildClasspath(Path sourceRoot) {
        StringBuilder cp = new StringBuilder();
        cp.append(sourceRoot.toString());

        String separator = System.getProperty("path.separator");

        // Check for lib/ directory with JARs
        Path libDir = sourceRoot.resolve("lib");
        if (Files.isDirectory(libDir)) {
            try (var stream = Files.list(libDir)) {
                stream.filter(p -> p.toString().endsWith(".jar"))
                      .forEach(jar -> cp.append(separator).append(jar.toString()));
            } catch (IOException e) {
                // Ignore — just use source root
            }
        }

        // Also check for libs/ directory
        Path libsDir = sourceRoot.resolve("libs");
        if (Files.isDirectory(libsDir)) {
            try (var stream = Files.list(libsDir)) {
                stream.filter(p -> p.toString().endsWith(".jar"))
                      .forEach(jar -> cp.append(separator).append(jar.toString()));
            } catch (IOException e) {
                // Ignore
            }
        }

        return cp.toString();
    }

    /**
     * Cleans up generated .class files after execution (best effort).
     */
    private static void cleanupClassFiles(Path sourceRoot, String packageName, String className) {
        try {
            Path classDir = sourceRoot;
            if (packageName != null) {
                for (String part : packageName.split("\\.")) {
                    classDir = classDir.resolve(part);
                }
            }
            Path classFile = classDir.resolve(className + ".class");
            Files.deleteIfExists(classFile);
            // Also delete inner class files (ClassName$Inner.class)
            if (Files.isDirectory(classDir)) {
                try (var stream = Files.list(classDir)) {
                    stream.filter(p -> p.getFileName().toString().startsWith(className + "$")
                                    && p.getFileName().toString().endsWith(".class"))
                          .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                }
            }
        } catch (IOException e) {
            // Best effort cleanup
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PYTHON: python3 / python
    // ═══════════════════════════════════════════════════════════

    private CompletableFuture<Integer> runPython(Path file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String pythonCmd = findPython();
                if (pythonCmd == null) {
                    emit("No Python interpreter found (python3/python)", true);
                    return -1;
                }

                Path dir = file.getParent();
                String fileName = file.getFileName().toString();

                emit("▶ Running " + fileName + " with " + pythonCmd + "...", false);
                emit("─".repeat(40), false);
                int exitCode = executeProcess(dir, pythonCmd, "-u", file.toString());
                emit("─".repeat(40), false);
                emit("Process exited with code: " + exitCode, exitCode != 0);
                return exitCode;

            } catch (Exception e) {
                LOG.warn("Python execution failed", e);
                emit("Execution error: " + e.getMessage(), true);
                return -1;
            }
        }, EXEC_POOL);
    }

    private static String findPython() {
        for (String cmd : new String[]{"python3", "python"}) {
            try {
                Process p = new ProcessBuilder(cmd, "--version")
                        .redirectErrorStream(true).start();
                int exit = p.waitFor();
                if (exit == 0) return cmd;
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  HTML: open in default browser
    // ═══════════════════════════════════════════════════════════

    private CompletableFuture<Integer> openHtmlInBrowser(Path file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!Files.exists(file)) {
                    emit("File not found: " + file, true);
                    return -1;
                }

                emit("▶ Opening " + file.getFileName() + " in browser...", false);

                // Primary: use Desktop.browse() (works cross-platform)
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                    if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                        desktop.browse(file.toUri());
                        emit("✓ Opened in default browser", false);
                        return 0;
                    }
                }

                // Fallback: OS-specific process
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder pb;
                if (os.contains("win")) {
                    pb = new ProcessBuilder("cmd", "/c", "start", "", file.toUri().toString());
                } else if (os.contains("mac")) {
                    pb = new ProcessBuilder("open", file.toUri().toString());
                } else {
                    pb = new ProcessBuilder("xdg-open", file.toUri().toString());
                }
                pb.redirectErrorStream(true);
                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    emit("✓ Opened in browser", false);
                } else {
                    emit("✗ Failed to open browser (exit code " + exitCode + ")", true);
                }
                return exitCode;

            } catch (Exception e) {
                LOG.warn("HTML open failed", e);
                emit("Failed to open browser: " + e.getMessage(), true);
                return -1;
            }
        }, EXEC_POOL);
    }

    // ═══════════════════════════════════════════════════════════
    //  PROCESS EXECUTION HELPER
    // ═══════════════════════════════════════════════════════════

    private int executeProcess(Path workDir, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workDir != null) pb.directory(workDir.toFile());
        pb.redirectErrorStream(false);

        Process process = pb.start();
        currentProcess = process;

        // Stream stdout
        Thread stdoutThread = new Thread(() -> readStream(process.getInputStream(), false), "exec-stdout");
        stdoutThread.setDaemon(true);
        stdoutThread.start();

        // Stream stderr
        Thread stderrThread = new Thread(() -> readStream(process.getErrorStream(), true), "exec-stderr");
        stderrThread.setDaemon(true);
        stderrThread.start();

        // Wait with timeout (5 minutes)
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            emit("Process killed after timeout (5 min)", true);
            return -1;
        }

        // Wait for stream readers to finish
        stdoutThread.join(2000);
        stderrThread.join(2000);

        currentProcess = null;
        return process.exitValue();
    }

    private void readStream(InputStream stream, boolean isError) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                emit(line, isError);
            }
        } catch (IOException e) {
            if (!"Stream closed".equals(e.getMessage())) {
                LOG.trace("Stream read error", e);
            }
        }
    }

    private void emit(String line, boolean isError) {
        BiConsumer<String, Boolean> listener = outputListener;
        if (listener != null) {
            Platform.runLater(() -> listener.accept(line, isError));
        }
    }

    /**
     * Shuts down the execution pool.
     */
    public void shutdown() {
        stop();
        EXEC_POOL.shutdownNow();
    }
}
