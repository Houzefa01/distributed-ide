package com.distributed_ide.javafx_client.config;

import com.distributed_ide.javafx_client.model.JavaLaunchConfig;
import com.distributed_ide.javafx_client.model.PythonLaunchConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Charge les configurations launch.json pour projets Java et Python.
 * Gère les valeurs par défaut et les erreurs.
 */
public class LaunchConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(LaunchConfigLoader.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Charge la configuration Java depuis ExJavaConfig/launch.json
     * Retourne une config par défaut si le fichier n'existe pas
     */
    public JavaLaunchConfig loadJavaLaunchConfig(Path projectRoot) {
        Path launchJsonPath = projectRoot.resolve("ExJavaConfig/launch.json");
        
        if (!Files.exists(launchJsonPath)) {
            LOG.warn("Java launch.json not found at {}, using defaults", launchJsonPath);
            return createDefaultJavaConfig();
        }
        
        try {
            JavaLaunchConfig config = objectMapper.readValue(launchJsonPath.toFile(), JavaLaunchConfig.class);
            LOG.debug("Loaded Java config: mainClass={}", config.getMainClass());
            
            // Vérifier les champs obligatoires
            if (config.getMainClass() == null || config.getMainClass().trim().isEmpty()) {
                LOG.warn("Java config mainClass is empty, using defaults");
                return createDefaultJavaConfig();
            }
            
            return config;
        } catch (IOException e) {
            LOG.error("Failed to load Java launch.json from {}", launchJsonPath, e);
            return createDefaultJavaConfig();
        }
    }
    
    /**
     * Charge la configuration Python depuis ExPython/launch.json
     * Retourne une config par défaut si le fichier n'existe pas
     */
    public PythonLaunchConfig loadPythonLaunchConfig(Path projectRoot) {
        Path launchJsonPath = projectRoot.resolve("ExPython/launch.json");
        
        if (!Files.exists(launchJsonPath)) {
            LOG.warn("Python launch.json not found at {}, using defaults", launchJsonPath);
            return createDefaultPythonConfig();
        }
        
        try {
            PythonLaunchConfig config = objectMapper.readValue(launchJsonPath.toFile(), PythonLaunchConfig.class);
            LOG.debug("Loaded Python config: mainFile={}", config.getMainFile());
            
            // Vérifier les champs obligatoires
            if (config.getMainFile() == null || config.getMainFile().trim().isEmpty()) {
                LOG.warn("Python config mainFile is empty, using defaults");
                return createDefaultPythonConfig();
            }
            
            return config;
        } catch (IOException e) {
            LOG.error("Failed to load Python launch.json from {}", launchJsonPath, e);
            return createDefaultPythonConfig();
        }
    }
    
    private JavaLaunchConfig createDefaultJavaConfig() {
        JavaLaunchConfig config = new JavaLaunchConfig();
        config.setMainClass("Main");
        config.setVmArgs("-Xmx512m");
        config.setProgramArgs("");
        config.setClassPathExtra("./lib/*");
        return config;
    }
    
    private PythonLaunchConfig createDefaultPythonConfig() {
        PythonLaunchConfig config = new PythonLaunchConfig();
        config.setMainFile("main.py");
        config.setArgs("");
        config.setPythonPath("python3");
        return config;
    }
}

