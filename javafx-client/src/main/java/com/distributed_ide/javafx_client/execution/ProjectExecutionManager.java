package com.distributed_ide.javafx_client.execution;

import com.distributed_ide.javafx_client.config.LaunchConfigLoader;
import com.distributed_ide.javafx_client.model.JavaLaunchConfig;
import com.distributed_ide.javafx_client.model.ProjectType;
import com.distributed_ide.javafx_client.model.PythonLaunchConfig;
import com.distributed_ide.javafx_client.service.ProjectTypeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * Gestionnaire centralisé pour l'exécution de projets.
 * 
 * Responsabilités:
 * - Détecter le type de projet (Maven, Java Simple, Python)
 * - Charger les configurations launch.json
 * - Compiler si nécessaire
 * - Exécuter le projet
 * - Gérer les flux stdout/stderr de façon asynchrone
 * - Gérer les processus proprement (cleanup)
 */
public class ProjectExecutionManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectExecutionManager.class);
    
    // Services
    private final ProjectTypeDetector projectTypeDetector = new ProjectTypeDetector();
    private final LaunchConfigLoader launchConfigLoader = new LaunchConfigLoader();
    private final JavaSimpleExecutor javaSimpleExecutor = new JavaSimpleExecutor();
    private final MavenExecutor mavenExecutor = new MavenExecutor();
    private final PythonExecutor pythonExecutor = new PythonExecutor();
    
    // ThreadPools
    private final ExecutorService executionThreadPool;
    private final ExecutorService streamReaderThreadPool;
    
    // Process courant
    private volatile Process currentProcess;
    
    public ProjectExecutionManager() {
        ThreadFactory daemonFactory = r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        };
        
        this.executionThreadPool = Executors.newFixedThreadPool(2, daemonFactory);
        this.streamReaderThreadPool = Executors.newFixedThreadPool(4, daemonFactory);
    }
    
    /**
     * Lance l'exécution d'un projet de façon asynchrone.
     */
    public void executeAsync(
        Path projectRoot,
        Consumer<String> outputConsumer,
        Consumer<String> errorConsumer,
        Consumer<Integer> completionCallback) {
        
        executionThreadPool.submit(() -> {
            try {
                ProjectType projectType = projectTypeDetector.detectProjectType(projectRoot);
                LOG.info("Detected project type: {} for {}", projectType, projectRoot);
                
                if (projectType == ProjectType.UNKNOWN) {
                    errorConsumer.accept("✗ Unable to detect project type.");
                    if (completionCallback != null) completionCallback.accept(1);
                    return;
                }
                
                outputConsumer.accept("→ Starting " + projectType + " project...");
                
                currentProcess = prepareAndExecute(projectRoot, projectType, outputConsumer, errorConsumer);
                
                if (currentProcess == null) {
                    errorConsumer.accept("✗ Failed to start process");
                    if (completionCallback != null) completionCallback.accept(1);
                    return;
                }
                
                // Lire stdout et stderr en parallèle
                Future<?> stdoutFuture = streamReaderThreadPool.submit(() ->
                    readStream(currentProcess.getInputStream(), outputConsumer, false)
                );
                Future<?> stderrFuture = streamReaderThreadPool.submit(() ->
                    readStream(currentProcess.getErrorStream(), errorConsumer, true)
                );
                
                int exitCode = currentProcess.waitFor();
                
                stdoutFuture.get();
                stderrFuture.get();
                
                LOG.info("Process finished with exit code: {}", exitCode);
                outputConsumer.accept("→ Process finished (exit code: " + exitCode + ")");
                
                if (completionCallback != null) {
                    completionCallback.accept(exitCode);
                }
                
            } catch (InterruptedException e) {
                LOG.info("Execution interrupted");
                errorConsumer.accept("⚠ Execution interrupted");
                Thread.currentThread().interrupt();
                if (completionCallback != null) completionCallback.accept(-1);
            } catch (Exception e) {
                LOG.error("Execution error", e);
                errorConsumer.accept("✗ Execution error: " + e.getMessage());
                if (completionCallback != null) completionCallback.accept(1);
            } finally {
                if (currentProcess != null) {
                    try {
                        currentProcess.destroy();
                    } catch (Exception e) {
                        LOG.warn("Error destroying process", e);
                    }
                    currentProcess = null;
                }
            }
        });
    }
    
    private Process prepareAndExecute(
        Path projectRoot,
        ProjectType projectType,
        Consumer<String> outputConsumer,
        Consumer<String> errorConsumer) throws IOException {
        
        switch (projectType) {
            case MAVEN:
                return mavenExecutor.execute(projectRoot);
                
            case JAVA_SIMPLE:
                JavaLaunchConfig javaConfig = launchConfigLoader.loadJavaLaunchConfig(projectRoot);
                outputConsumer.accept("→ Main class: " + javaConfig.getMainClass());
                return javaSimpleExecutor.execute(projectRoot, javaConfig);
                
            case PYTHON:
                PythonLaunchConfig pythonConfig = launchConfigLoader.loadPythonLaunchConfig(projectRoot);
                outputConsumer.accept("→ Main file: " + pythonConfig.getMainFile());
                return pythonExecutor.execute(projectRoot, pythonConfig);
                
            default:
                throw new IOException("Unknown project type: " + projectType);
        }
    }
    
    private void readStream(java.io.InputStream stream, Consumer<String> lineConsumer, boolean isError) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                if (isError && !line.isEmpty()) {
                    lineConsumer.accept("⚠ " + line);
                } else {
                    lineConsumer.accept(line);
                }
            }
        } catch (IOException e) {
            if (!Thread.currentThread().isInterrupted()) {
                LOG.warn("Stream reading error", e);
            }
        }
    }
    
    public void stop() {
        if (currentProcess != null && currentProcess.isAlive()) {
            LOG.info("Stopping current process");
            currentProcess.destroyForcibly();
            currentProcess = null;
        }
    }
    
    public void invalidateCache(Path projectRoot) {
        projectTypeDetector.invalidateCache(projectRoot);
    }
    
    public void shutdown() {
        stop();
        executionThreadPool.shutdownNow();
        streamReaderThreadPool.shutdownNow();
        LOG.info("ProjectExecutionManager shut down");
    }
}
