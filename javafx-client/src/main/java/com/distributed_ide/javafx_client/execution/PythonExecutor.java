package com.distributed_ide.javafx_client.execution;

import com.distributed_ide.javafx_client.model.PythonLaunchConfig;
import com.distributed_ide.javafx_client.util.OSDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exécute un projet Python.
 * - Lit launch.json
 * - Valide le fichier principal existe
 * - Exécute avec Python
 */
public class PythonExecutor {
    
    private static final Logger LOG = LoggerFactory.getLogger(PythonExecutor.class);
    
    /**
     * Exécute un projet Python
     * @param projectRoot Le répertoire du projet
     * @param config La configuration depuis launch.json
     * @return Le processus Python en cours
     * @throws IOException Si une erreur d'exécution se produit
     */
    public Process execute(Path projectRoot, PythonLaunchConfig config) throws IOException {
        LOG.info("Executing Python project: {}", projectRoot);
        
        String mainFile = config.getMainFile();
        String pythonPath = config.getPythonPath();
        if (pythonPath == null || pythonPath.trim().isEmpty()) {
            pythonPath = OSDetector.getPythonCommand();
        }
        String args = config.getArgs();
        
        // Vérifier que le fichier existe
        Path mainFilePath = projectRoot.resolve(mainFile);
        if (!Files.exists(mainFilePath)) {
            throw new IOException("Python main file not found: " + mainFilePath);
        }
        
        LOG.debug("Using Python interpreter: {}", pythonPath);
        
        // Construire la commande
        List<String> command = new ArrayList<>();
        command.add(pythonPath);
        command.add(mainFile);
        
        // Ajouter les arguments
        if (args != null && !args.trim().isEmpty()) {
            for (String arg : args.split("\\s+")) {
                command.add(arg);
            }
        }
        
        LOG.debug("Python execute command: {}", command);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        
        return pb.start();
    }
}

