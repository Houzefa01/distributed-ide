package com.distributed_ide.javafx_client.service;

import com.distributed_ide.javafx_client.model.ProjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Détecteur automatique du type de projet avec cache.
 * Détecte : Maven, Java Simple, Python.
 */
public class ProjectTypeDetector {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectTypeDetector.class);
    
    // Cache: projectRoot path -> ProjectType
    private final ConcurrentMap<String, ProjectType> typeCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> lastCheckTime = new ConcurrentHashMap<>();
    private static final long CACHE_VALIDITY_MS = 5000; // 5 secondes

    public ProjectType detectProjectType(Path projectRoot) {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            LOG.debug("Project root not a directory: {}", projectRoot);
            return ProjectType.UNKNOWN;
        }
        
        String rootPath = projectRoot.toString();
        
        // Vérifier le cache
        Long lastCheck = lastCheckTime.get(rootPath);
        if (lastCheck != null && System.currentTimeMillis() - lastCheck < CACHE_VALIDITY_MS) {
            ProjectType cached = typeCache.get(rootPath);
            if (cached != null) {
                LOG.debug("Using cached project type {} for {}", cached, rootPath);
                return cached;
            }
        }
        
        // Détection
        ProjectType detected = performDetection(projectRoot);
        
        // Cacher le résultat
        typeCache.put(rootPath, detected);
        lastCheckTime.put(rootPath, System.currentTimeMillis());
        
        LOG.debug("Detected project type: {} for {}", detected, rootPath);
        return detected;
    }

    private ProjectType performDetection(Path projectRoot) {
        File projectDir = projectRoot.toFile();
        File[] files = projectDir.listFiles();
        if (files == null) {
            return ProjectType.UNKNOWN;
        }

        // Priority 1: Maven
        for (File file : files) {
            if (file.isFile() && file.getName().equals("pom.xml")) {
                return ProjectType.MAVEN;
            }
        }

        // Priority 2: Java Simple (avec launch.json)
        if (hasJavaFiles(projectRoot) && hasExJavaConfig(projectRoot)) {
            return ProjectType.JAVA_SIMPLE;
        }
        
        // Priority 3: Python (avec launch.json)
        if (hasPythonFiles(projectRoot) && hasExPythonConfig(projectRoot)) {
            return ProjectType.PYTHON;
        }
        
        // Fallback: détection par fichiers (sans launch.json)
        if (hasJavaFiles(projectRoot)) {
            return ProjectType.JAVA_SIMPLE;
        }
        if (hasPythonFiles(projectRoot)) {
            return ProjectType.PYTHON;
        }

        return ProjectType.UNKNOWN;
    }
    
    private boolean hasJavaFiles(Path projectRoot) {
        try {
            return Files.walk(projectRoot)
                        .filter(p -> !p.toString().contains("/target/") 
                                  && !p.toString().contains("\\.git\\"))
                        .anyMatch(p -> p.toString().endsWith(".java"));
        } catch (IOException e) {
            LOG.warn("Error checking for Java files in {}", projectRoot, e);
            return false;
        }
    }
    
    private boolean hasPythonFiles(Path projectRoot) {
        try {
            return Files.walk(projectRoot)
                        .filter(p -> !p.toString().contains("__pycache__")
                                  && !p.toString().contains(".venv")
                                  && !p.toString().contains(".git"))
                        .anyMatch(p -> p.toString().endsWith(".py"));
        } catch (IOException e) {
            LOG.warn("Error checking for Python files in {}", projectRoot, e);
            return false;
        }
    }
    
    private boolean hasExJavaConfig(Path projectRoot) {
        return Files.exists(projectRoot.resolve("ExJavaConfig/launch.json"));
    }
    
    private boolean hasExPythonConfig(Path projectRoot) {
        return Files.exists(projectRoot.resolve("ExPython/launch.json"));
    }
    
    /**
     * Invalide le cache pour un projet (utile après des changements de structure)
     */
    public void invalidateCache(Path projectRoot) {
        String rootPath = projectRoot.toString();
        typeCache.remove(rootPath);
        lastCheckTime.remove(rootPath);
        LOG.debug("Invalidated cache for {}", rootPath);
    }
}

