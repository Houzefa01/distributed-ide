package com.distributed_ide.javafx_client.execution;

import com.distributed_ide.javafx_client.util.OSDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exécute un projet Maven.
 * Essaie d'abord mvn compile suivi de mvn exec:java
 * Fallback à mvn package et jar execution
 */
public class MavenExecutor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenExecutor.class);

    /**
     * Exécute un projet Maven.
     * @param projectRoot Le répertoire du projet (contenant pom.xml)
     * @return Le processus Maven qui exécute la classe principale
     * @throws IOException Si une erreur d'exécution se produit
     */
    public Process execute(Path projectRoot) throws IOException {
        LOGGER.info("Executing Maven project: {}", projectRoot);
        
        List<String> command = new ArrayList<>();
        command.add(OSDetector.getMavenCommand());
        command.add("exec:java");
        command.add("-DskipTests=true");
        
        // Detect main class from pom.xml
        String mainClass = detectMainClass(projectRoot);
        if (mainClass != null && !mainClass.isEmpty()) {
            command.add("-Dexec.mainClass=" + mainClass);
            LOGGER.info("Using main class: {}", mainClass);
        } else {
            LOGGER.warn("No main class configured. Using fallback JAR execution.");
            return executeJar(projectRoot);
        }
        
        LOGGER.debug("Maven execute command: {}", command);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        
        return pb.start();
    }

    /**
     * Détecte la classe principale à partir de la configuration pom.xml
     * Cherche dans: 1) properties/mainClass, 2) exec-maven-plugin configuration
     */
    private String detectMainClass(Path projectRoot) {
        try {
            Path pomPath = projectRoot.resolve("pom.xml");
            if (!Files.exists(pomPath)) {
                return null;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomPath.toFile());

            // Vérifier properties/mainClass
            NodeList properties = doc.getElementsByTagName("properties");
            for (int i = 0; i < properties.getLength(); i++) {
                Element propsElement = (Element) properties.item(i);
                NodeList mainClassNodes = propsElement.getElementsByTagName("mainClass");
                if (mainClassNodes.getLength() > 0) {
                    String value = mainClassNodes.item(0).getTextContent().trim();
                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            }

            // Vérifier exec-maven-plugin configuration
            NodeList plugins = doc.getElementsByTagName("plugin");
            for (int i = 0; i < plugins.getLength(); i++) {
                Element plugin = (Element) plugins.item(i);
                NodeList artifactIds = plugin.getElementsByTagName("artifactId");
                if (artifactIds.getLength() > 0 && 
                    "exec-maven-plugin".equals(artifactIds.item(0).getTextContent().trim())) {
                    
                    NodeList configs = plugin.getElementsByTagName("configuration");
                    if (configs.getLength() > 0) {
                        Element config = (Element) configs.item(0);
                        NodeList mainClassNodes = config.getElementsByTagName("mainClass");
                        if (mainClassNodes.getLength() > 0) {
                            String value = mainClassNodes.item(0).getTextContent().trim();
                            if (!value.isEmpty()) {
                                return value;
                            }
                        }
                    }
                }
            }
        } catch (IOException | ParserConfigurationException | SAXException e) {
            LOGGER.error("Error detecting main class from pom.xml", e);
        }
        return null;
    }
    
    /**
     * Alternative : compile le projet et exécute le JAR généré
     * (utilisée en fallback si exec:java échoue)
     */
    public Process executeJar(Path projectRoot) throws IOException {
        LOGGER.info("Executing Maven project with JAR: {}", projectRoot);
        
        // Compiler et packager
        List<String> buildCommand = new ArrayList<>();
        buildCommand.add(OSDetector.getMavenCommand());
        buildCommand.add("clean");
        buildCommand.add("package");
        buildCommand.add("-DskipTests=true");
        
        LOGGER.debug("Maven build command: {}", buildCommand);
        
        ProcessBuilder buildPb = new ProcessBuilder(buildCommand);
        buildPb.directory(projectRoot.toFile());
        
        Process buildProcess = buildPb.start();
        try {
            int buildExitCode = buildProcess.waitFor();
            
            if (buildExitCode != 0) {
                throw new IOException("Maven build failed with exit code: " + buildExitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Maven build interrupted", e);
        }
        
        // Chercher le JAR executable
        Path targetDir = projectRoot.resolve("target");
        File[] files = targetDir.toFile().listFiles((dir, name) -> 
            name.endsWith(".jar") && !name.endsWith("-sources.jar"));
        
        if (files == null || files.length == 0) {
            throw new IOException("No JAR file found in target directory");
        }
        
        // Exécuter le premier JAR trouvé
        String jarPath = files[0].getAbsolutePath();
        
        List<String> runCommand = new ArrayList<>();
        runCommand.add(OSDetector.getJavaPath());
        runCommand.add("-jar");
        runCommand.add(jarPath);
        
        LOGGER.debug("Maven execute JAR command: {}", runCommand);
        
        ProcessBuilder runPb = new ProcessBuilder(runCommand);
        runPb.directory(projectRoot.toFile());
        
        return runPb.start();
    }
}

