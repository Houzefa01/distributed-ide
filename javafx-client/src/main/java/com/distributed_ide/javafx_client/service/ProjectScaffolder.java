package com.distributed_ide.javafx_client.service;

import com.distributed_ide.javafx_client.model.ProjectCreationRequest;
import com.distributed_ide.javafx_client.model.ProjectCreationResult;
import com.distributed_ide.javafx_client.model.ProjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scaffolds project structures from templates.
 * Orchestrates the creation of project files and directories.
 */
public class ProjectScaffolder {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectScaffolder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Creates a project based on the request.
     */
    public ProjectCreationResult createProject(ProjectCreationRequest request) {
        LOG.info("Creating project: {} of type {}", request.getProjectName(), request.getProjectType());

        try {
            Path projectPath = switch (request.getProjectType()) {
                case MAVEN -> createMavenProject(request);
                case JAVA_SIMPLE -> createJavaSimpleProject(request);
                case PYTHON -> createPythonProject(request);
                default -> throw new IllegalArgumentException("Unsupported project type: " + request.getProjectType());
            };

            return new ProjectCreationResult.Builder()
                    .projectPath(projectPath)
                    .projectType(request.getProjectType())
                    .projectName(request.getProjectName())
                    .createdAt(LocalDateTime.now())
                    .success(true)
                    .message("Project created successfully at: " + projectPath)
                    .build();

        } catch (Exception e) {
            LOG.error("Project creation failed", e);
            return new ProjectCreationResult.Builder()
                    .projectType(request.getProjectType())
                    .projectName(request.getProjectName())
                    .success(false)
                    .message("Project creation failed: " + e.getMessage())
                    .error(e)
                    .build();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MAVEN PROJECT CREATION
    // ═══════════════════════════════════════════════════════════

    private Path createMavenProject(ProjectCreationRequest request) throws IOException {
        Path projectRoot = request.getParentDirectory().resolve(request.getProjectName());

        if (Files.exists(projectRoot)) {
            throw new IOException("Project already exists: " + projectRoot);
        }

        LOG.debug("Creating Maven project structure at: {}", projectRoot);

        // Create directory structure
        Files.createDirectories(projectRoot);
        String packagePath = request.getPackageName().replace('.', '/');

        Path srcMain = projectRoot.resolve("src/main/java")
                .resolve(packagePath);
        Path srcTest = projectRoot.resolve("src/test/java")
                .resolve(packagePath);
        Path resources = projectRoot.resolve("src/main/resources");
        Path testResources = projectRoot.resolve("src/test/resources");

        Files.createDirectories(srcMain);
        Files.createDirectories(srcTest);
        Files.createDirectories(resources);
        Files.createDirectories(testResources);
        Files.createDirectories(projectRoot.resolve("lib"));
        Files.createDirectories(projectRoot.resolve("tmp"));

        // Create pom.xml
        String pomContent = generateMavenPom(request);
        Files.writeString(projectRoot.resolve("pom.xml"), pomContent, StandardOpenOption.CREATE);

        // Create Main.java
        if (request.isCreateMainFile()) {
            String mainContent = generateJavaMain(request.getPackageName(), request.getMainClassName());
            Files.writeString(srcMain.resolve(request.getMainClassName() + ".java"), mainContent, StandardOpenOption.CREATE);
        }

        // Create .gitignore
        Files.writeString(projectRoot.resolve(".gitignore"), generateJavaGitignore(), StandardOpenOption.CREATE);

        // Create README.md
        Files.writeString(projectRoot.resolve("README.md"), generateReadme(request.getProjectName(), "Maven Java Project"), StandardOpenOption.CREATE);

        LOG.info("Maven project created: {}", projectRoot);
        return projectRoot;
    }

    private String generateMavenPom(ProjectCreationRequest request) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                         http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>%s</version>
                    <packaging>jar</packaging>

                    <name>%s</name>
                    <description>Project created with Distributed IDE</description>

                    <properties>
                        <maven.compiler.source>%s</maven.compiler.source>
                        <maven.compiler.target>%s</maven.compiler.target>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>

                    <dependencies>
                        <!-- JUnit 5 for testing -->
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.1</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <version>3.11.0</version>
                                <configuration>
                                    <source>%s</source>
                                    <target>%s</target>
                                </configuration>
                            </plugin>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <version>3.2.2</version>
                            </plugin>
                            <plugin>
                                <groupId>org.codehaus.mojo</groupId>
                                <artifactId>exec-maven-plugin</artifactId>
                                <version>3.1.0</version>
                                <configuration>
                                    <mainClass>%s.%s</mainClass>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(
                request.getGroupId(),
                request.getArtifactId(),
                request.getVersion(),
                request.getProjectName(),
                request.getJdkVersion(),
                request.getJdkVersion(),
                request.getJdkVersion(),
                request.getJdkVersion(),
                request.getPackageName(),
                request.getMainClassName()
        );
    }

    // ═══════════════════════════════════════════════════════════
    //  JAVA SIMPLE PROJECT CREATION
    // ═══════════════════════════════════════════════════════════

    private Path createJavaSimpleProject(ProjectCreationRequest request) throws IOException {
        Path projectRoot = request.getParentDirectory().resolve(request.getProjectName());

        if (Files.exists(projectRoot)) {
            throw new IOException("Project already exists: " + projectRoot);
        }

        LOG.debug("Creating Java Simple project structure at: {}", projectRoot);

        // Create directory structure
        Files.createDirectories(projectRoot);
        Path src = projectRoot.resolve("src");
        Path lib = projectRoot.resolve("lib");
        Path bin = projectRoot.resolve("bin");
        Path exJavaConfig = projectRoot.resolve("ExJavaConfig");

        Files.createDirectories(src);
        Files.createDirectories(lib);
        Files.createDirectories(bin);
        Files.createDirectories(exJavaConfig);

        // Create Main.java
        if (request.isCreateMainFile()) {
            String packagePath = request.getPackageName().replace('.', '/');
            Path packageDir = src.resolve(packagePath);
            Files.createDirectories(packageDir);

            String mainContent = generateJavaMain(request.getPackageName(), request.getMainClassName());
            Files.writeString(packageDir.resolve(request.getMainClassName() + ".java"), mainContent, StandardOpenOption.CREATE);
        }

        // Create launch.json
        Map<String, Object> launchConfig = new LinkedHashMap<>();
        launchConfig.put("mainClass", request.getPackageName() + "." + request.getMainClassName());
        launchConfig.put("vmArgs", "");
        launchConfig.put("programArgs", "");
        String launchJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(launchConfig);
        Files.writeString(exJavaConfig.resolve("launch.json"), launchJson, StandardOpenOption.CREATE);

        // Create .gitignore
        Files.writeString(projectRoot.resolve(".gitignore"), generateJavaGitignore(), StandardOpenOption.CREATE);

        // Create README.md
        Files.writeString(projectRoot.resolve("README.md"), generateReadme(request.getProjectName(), "Java Simple Project"), StandardOpenOption.CREATE);

        LOG.info("Java Simple project created: {}", projectRoot);
        return projectRoot;
    }

    // ═══════════════════════════════════════════════════════════
    //  PYTHON PROJECT CREATION
    // ═══════════════════════════════════════════════════════════

    private Path createPythonProject(ProjectCreationRequest request) throws IOException {
        Path projectRoot = request.getParentDirectory().resolve(request.getProjectName());

        if (Files.exists(projectRoot)) {
            throw new IOException("Project already exists: " + projectRoot);
        }

        LOG.debug("Creating Python project structure at: {}", projectRoot);

        // Create directory structure
        Files.createDirectories(projectRoot);
        Path exPython = projectRoot.resolve("ExPython");
        Files.createDirectories(exPython);

        // Create main.py
        if (request.isCreateMainFile()) {
            String mainContent = generatePythonMain();
            Files.writeString(projectRoot.resolve("main.py"), mainContent, StandardOpenOption.CREATE);
        }

        // Create launch.json
        Map<String, Object> launchConfig = new LinkedHashMap<>();
        launchConfig.put("mainFile", "main.py");
        launchConfig.put("args", "");
        launchConfig.put("pythonPath", "python3");
        String launchJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(launchConfig);
        Files.writeString(exPython.resolve("launch.json"), launchJson, StandardOpenOption.CREATE);

        // Create requirements.txt
        Files.writeString(projectRoot.resolve("requirements.txt"), 
                "# Add your dependencies here\n", StandardOpenOption.CREATE);

        // Create .gitignore
        Files.writeString(projectRoot.resolve(".gitignore"), generatePythonGitignore(), StandardOpenOption.CREATE);

        // Create README.md
        Files.writeString(projectRoot.resolve("README.md"), generateReadme(request.getProjectName(), "Python Project"), StandardOpenOption.CREATE);

        LOG.info("Python project created: {}", projectRoot);
        return projectRoot;
    }

    // ═══════════════════════════════════════════════════════════
    //  TEMPLATE GENERATORS
    // ═══════════════════════════════════════════════════════════

    private String generateJavaMain(String packageName, String className) {
        return """
                package %s;

                /**
                 * Main class generated by Distributed IDE.
                 * Entry point for the application.
                 */
                public class %s {
                    public static void main(String[] args) {
                        System.out.println("Hello from " + %s.class.getName());
                        System.out.println("Java version: " + System.getProperty("java.version"));
                    }
                }
                """.formatted(packageName, className, className);
    }

    private String generatePythonMain() {
        return """
                \"\"\"
                Main module for this project.
                
                Created with Distributed IDE.
                \"\"\"

                def main():
                    print("Hello from Python!")
                    print("Welcome to Distributed IDE")


                if __name__ == "__main__":
                    main()
                """;
    }

    private String generateJavaGitignore() {
        return """
                # Compiled class files
                *.class

                # Maven
                target/
                pom.xml.tag
                pom.xml.releaseBackup
                pom.xml.versionsBackup
                pom.xml.next
                release.properties
                dependency-reduced-pom.xml
                buildNumber.properties
                .mvn/timing.properties

                # IntelliJ IDEA
                .idea/
                *.iml
                *.ipr
                *.iws
                out/

                # Visual Studio Code
                .vscode/
                *.code-workspace

                # Eclipse
                .classpath
                .project
                .settings/
                bin/

                # Mac OS
                .DS_Store

                # Windows
                Thumbs.db

                # Logs
                *.log
                logs/

                # Temporary files
                tmp/
                temp/
                *.tmp
                """;
    }

    private String generatePythonGitignore() {
        return """
                # Python
                __pycache__/
                *.py[cod]
                *$py.class
                *.so
                .Python
                build/
                develop-eggs/
                dist/
                downloads/
                eggs/
                .eggs/
                lib/
                lib64/
                parts/
                sdist/
                var/
                wheels/
                *.egg-info/
                .installed.cfg
                *.egg
                MANIFEST

                # Virtual environments
                venv/
                venv3/
                ENV/
                env/
                env.bak/
                venv.bak/

                # IntelliJ IDEA
                .idea/
                *.iml
                *.ipr
                *.iws
                out/

                # Visual Studio Code
                .vscode/
                *.code-workspace

                # PyCharm
                .mypy_cache/
                .dmypy.json
                dmypy.json

                # Testing
                .pytest_cache/
                .coverage
                htmlcov/
                .tox/

                # Mac OS
                .DS_Store

                # Windows
                Thumbs.db

                # Logs
                *.log
                logs/

                # Temporary files
                tmp/
                temp/
                *.tmp
                """;
    }

    private String generateReadme(String projectName, String projectType) {
        return """
                # %s

                %s created with **Distributed IDE**.

                ## Getting Started

                ### Prerequisites

                %s

                ### Installation

                ```bash
                cd %s
                # (Add your build/setup commands here)
                ```

                ### Running the Project

                ```bash
                # (Add your run commands here)
                ```

                ## Project Structure

                ```
                %s/
                ├── src/                     # Source code
                ├── lib/                     # External libraries
                ├── bin/                     # Compiled output
                ├── README.md                # This file
                └── .gitignore               # Git ignore rules
                ```

                ## License

                MIT License - Change as needed

                ---

                Created: %s
                """.formatted(
                projectName,
                projectType,
                projectType.contains("Maven") ? "- Java 21 or higher\n- Maven 3.8 or higher" :
                        projectType.contains("Python") ? "- Python 3.8 or higher" :
                        "- Java 21 or higher",
                projectName,
                projectName,
                LocalDateTime.now()
        );
    }
}
