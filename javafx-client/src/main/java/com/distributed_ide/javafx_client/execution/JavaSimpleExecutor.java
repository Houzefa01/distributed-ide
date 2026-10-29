package com.distributed_ide.javafx_client.execution;

import com.distributed_ide.javafx_client.model.JavaLaunchConfig;
import com.distributed_ide.javafx_client.util.OSDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exécute un projet Java simple avec compilation.
 * - Lit launch.json
 * - Compile le code source
 * - Exécute la classe principale
 */
public class JavaSimpleExecutor {
    
    private static final Logger LOG = LoggerFactory.getLogger(JavaSimpleExecutor.class);
    
    /**
     * Compile et exécute un projet Java simple
     * @param projectRoot Le répertoire du projet
     * @param config La configuration depuis launch.json
     * @return Le processus en cours
     * @throws IOException Si une erreur d'exécution se produit
     */
    public Process execute(Path projectRoot, JavaLaunchConfig config) throws IOException {
        LOG.info("Executing Java simple project: {}", projectRoot);
        
        // Étapes :
        // 1. Compiler
        compileProject(projectRoot);
        
        // 2. Exécuter
        return executeCompiledProject(projectRoot, config);
    }
    
    private void compileProject(Path projectRoot) throws IOException {
        LOG.debug("Compiling Java project at {}", projectRoot);
        
        Path srcDir = projectRoot.resolve("src");
        Path binDir = projectRoot.resolve("bin");
        
        // Créer le répertoire bin s'il n'existe pas
        Files.createDirectories(binDir);
        
        // Trouver tous les fichiers .java
        List<String> javaFiles = new ArrayList<>();
        if (Files.exists(srcDir)) {
            Files.walk(srcDir)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(p -> javaFiles.add(p.toString()));
        }
        
        if (javaFiles.isEmpty()) {
            LOG.warn("No Java files found in src directory");
            return;
        }
        
        // Construire la commande javac
        List<String> command = new ArrayList<>();
        command.add(OSDetector.getJavacPath());
        command.add("-d");
        command.add(binDir.toString());
        
        // Ajouter le classpath include lib/*
        Path libDir = projectRoot.resolve("lib");
        if (Files.exists(libDir)) {
            command.add("-cp");
            command.add(libDir + File.separator + "*");
        }
        
        command.addAll(javaFiles);
        
        LOG.debug("Compile command: {}", command);
        
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectRoot.toFile());
            pb.inheritIO(); // Afficher la sortie de compilation
            
            Process compileProcess = pb.start();
            int exitCode = compileProcess.waitFor();
            
            if (exitCode != 0) {
                LOG.error("Compilation failed with exit code: {}", exitCode);
                throw new IOException("Compilation failed with exit code: " + exitCode);
            }
            
            LOG.info("Compilation successful");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Compilation interrupted", e);
        }
    }
    
    private Process executeCompiledProject(Path projectRoot, JavaLaunchConfig config) throws IOException {
        String mainClass = config.getMainClass();
        String vmArgs = config.getVmArgs();
        String programArgs = config.getProgramArgs();
        String classPathExtra = config.getClassPathExtra();
        
        // Construire le classpath
        Path binDir = projectRoot.resolve("bin");
        StringBuilder classPath = new StringBuilder();
        classPath.append(binDir.toString());
        
        if (classPathExtra != null && !classPathExtra.trim().isEmpty()) {
            classPath.append(File.pathSeparator);
            classPath.append(classPathExtra);
        }
        
        // Construire la commande java
        List<String> command = new ArrayList<>();
        command.add(OSDetector.getJavaPath());
        
        // Ajouter les VM args (ex: -Xmx512m)
        if (vmArgs != null && !vmArgs.trim().isEmpty()) {
            for (String arg : vmArgs.split("\\s+")) {
                command.add(arg);
            }
        }
        
        // Ajouter le classpath
        command.add("-cp");
        command.add(classPath.toString());
        
        // Ajouter la classe principale
        command.add(mainClass);
        
        // Ajouter les arguments du programme
        if (programArgs != null && !programArgs.trim().isEmpty()) {
            for (String arg : programArgs.split("\\s+")) {
                command.add(arg);
            }
        }
        
        LOG.debug("Execute command: {}", command);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        
        return pb.start();
    }
}

