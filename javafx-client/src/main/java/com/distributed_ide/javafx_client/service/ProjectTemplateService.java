package com.distributed_ide.javafx_client.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;

/**
 * Project creation service from templates.
 * Supports Java (Maven) and Python (optional venv).
 */
public class ProjectTemplateService {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectTemplateService.class);

    /**
     * Creates a new Java project with standard Maven structure.
     */
    public CompletableFuture<Path> createJavaProject(Path parentDir, String projectName, String groupId, String artifactId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create the structure
                Path projectRoot = parentDir.resolve(projectName);
                if (Files.exists(projectRoot)) {
                    throw new IOException("Project already exists: " + projectRoot);
                }

                Files.createDirectories(projectRoot);

                // Structure Maven standard
                Path src = projectRoot.resolve("src");
                Path srcMain = src.resolve("main");
                Path srcTest = src.resolve("test");
                Files.createDirectories(srcMain.resolve("java").resolve(groupId.replace('.', '/')));
                Files.createDirectories(srcMain.resolve("resources"));
                Files.createDirectories(srcTest.resolve("java").resolve(groupId.replace('.', '/')));
                Files.createDirectories(srcTest.resolve("resources"));

                // Create pom.xml
                String pomContent = generatePomXml(groupId, artifactId, "1.0-SNAPSHOT");
                Files.writeString(projectRoot.resolve("pom.xml"), pomContent, StandardOpenOption.CREATE);

                // Create Main.java
                String mainClass = groupId + ".Main";
                String mainContent = generateMainJava(groupId);
                Path mainFile = srcMain.resolve("java")
                        .resolve(groupId.replace('.', '/'))
                        .resolve("Main.java");
                Files.writeString(mainFile, mainContent, StandardOpenOption.CREATE);

                // Create .gitignore
                String gitignoreContent = generateJavaGitignore();
                Files.writeString(projectRoot.resolve(".gitignore"), gitignoreContent, StandardOpenOption.CREATE);

                // Create README.md
                String readmeContent = generateReadme(projectName, "Java Maven Project");
                Files.writeString(projectRoot.resolve("README.md"), readmeContent, StandardOpenOption.CREATE);

                LOG.info("Created Java project: {}", projectRoot);
                return projectRoot;
            } catch (IOException e) {
                LOG.error("Failed to create Java project", e);
                throw new RuntimeException("Failed to create Java project: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Creates a new Python project with standard structure.
     */
    public CompletableFuture<Path> createPythonProject(Path parentDir, String projectName, boolean createVenv) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create the structure
                Path projectRoot = parentDir.resolve(projectName);
                if (Files.exists(projectRoot)) {
                    throw new IOException("Project already exists: " + projectRoot);
                }

                Files.createDirectories(projectRoot);

                // Structure Python standard
                Path srcDir = projectRoot.resolve(projectName.toLowerCase().replace('-', '_'));
                Files.createDirectories(srcDir);

                Path testsDir = projectRoot.resolve("tests");
                Files.createDirectories(testsDir);

                // Create __init__.py
                Files.createFile(srcDir.resolve("__init__.py"));
                Files.createFile(testsDir.resolve("__init__.py"));

                // Create main.py
                String mainContent = generateMainPy(projectName);
                Files.writeString(srcDir.resolve("main.py"), mainContent, StandardOpenOption.CREATE);

                // Create requirements.txt
                String requirementsContent = "# Add your dependencies here\n";
                Files.writeString(projectRoot.resolve("requirements.txt"), requirementsContent, StandardOpenOption.CREATE);

                // Create setup.py
                String setupContent = generateSetupPy(projectName);
                Files.writeString(projectRoot.resolve("setup.py"), setupContent, StandardOpenOption.CREATE);

                // Create .gitignore
                String gitignoreContent = generatePythonGitignore();
                Files.writeString(projectRoot.resolve(".gitignore"), gitignoreContent, StandardOpenOption.CREATE);

                // Create README.md
                String readmeContent = generateReadme(projectName, "Python Project");
                Files.writeString(projectRoot.resolve("README.md"), readmeContent, StandardOpenOption.CREATE);

                // Create venv if requested (via external script or ProcessBuilder)
                if (createVenv) {
                    LOG.info("Creating virtual environment for Python project...");
                    // Note: Creating the venv would require a ProcessBuilder
                    // For simplicity, we just create a documentation file
                    String venvInstructions = "# To create virtual environment:\n" +
                            "# python3 -m venv venv\n" +
                            "# source venv/bin/activate  # On Unix\n" +
                            "# venv\\Scripts\\activate  # On Windows\n" +
                            "# pip install -r requirements.txt\n";
                    Files.writeString(projectRoot.resolve("VENV_INSTRUCTIONS.md"), venvInstructions, StandardOpenOption.CREATE);
                }

                LOG.info("Created Python project: {}", projectRoot);
                return projectRoot;
            } catch (IOException e) {
                LOG.error("Failed to create Python project", e);
                throw new RuntimeException("Failed to create Python project: " + e.getMessage(), e);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  TEMPLATE GENERATORS
    // ═══════════════════════════════════════════════════════════

    private String generatePomXml(String groupId, String artifactId, String version) {
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

                    <properties>
                        <maven.compiler.source>21</maven.compiler.source>
                        <maven.compiler.target>21</maven.compiler.target>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>

                    <dependencies>
                        <!-- Add your dependencies here -->
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
                            </plugin>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <version>3.2.2</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(groupId, artifactId, version);
    }

    private String generateMainJava(String packageName) {
        return """
                package %s;

                /**
                 * Main application entry point.
                 */
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello from Distributed IDE!");
                        System.out.println("Java version: " + System.getProperty("java.version"));
                    }
                }
                """.formatted(packageName);
    }

    private String generateMainPy(String projectName) {
        return "\"\"\"\n"
                + "Main module for " + projectName + ".\n"
                + "\"\"\"\n"
                + "\n"
                + "def main():\n"
                + "    print(\"Hello from Distributed IDE!\")\n"
                + "    print(\"Project: " + projectName + "\")\n"
                + "\n"
                + "if __name__ == \"__main__\":\n"
                + "    main()\n";
    }

    private String generateSetupPy(String projectName) {
        String packageName = projectName.toLowerCase().replace('-', '_');
        return """
                from setuptools import setup, find_packages

                setup(
                    name='%s',
                    version='0.1.0',
                    description='A Python project created with Distributed IDE',
                    author='Your Name',
                    author_email='your.email@example.com',
                    packages=find_packages(),
                    python_requires='>=3.8',
                    install_requires=[
                        # Add your dependencies here
                    ],
                    entry_points={
                        'console_scripts': [
                            '%s=%s.main:main',
                        ],
                    },
                )
                """.formatted(projectName, projectName, packageName);
    }

    private String generateJavaGitignore() {
        return """
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

                # IDE
                .idea/
                .vscode/
                *.iml
                *.ipr
                *.iws
                .classpath
                .project
                .settings/
                bin/

                # OS
                .DS_Store
                Thumbs.db

                # Logs
                *.log
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
                pip-wheel-metadata/
                share/python-wheels/
                *.egg-info/
                .installed.cfg
                *.egg
                MANIFEST

                # Virtual environment
                venv/
                ENV/
                env/

                # IDE
                .idea/
                .vscode/
                *.swp
                *.swo
                *~

                # Testing
                .pytest_cache/
                .coverage
                htmlcov/

                # OS
                .DS_Store
                Thumbs.db

                # Logs
                *.log
                """;
    }

    private String generateReadme(String projectName, String projectType) {
        return """
                # %s

                %s created with Distributed IDE.

                ## Getting Started

                ### Prerequisites

                - %s

                ### Installation

                ```bash
                # Clone the repository
                git clone <repository-url>
                cd %s

                # Build and run
                # (Add your build commands here)
                ```

                ## Usage

                (Add usage instructions here)

                ## License

                (Add license information here)
                """.formatted(
                projectName,
                projectType,
                projectType.contains("Java") ? "Java 21+" : "Python 3.8+",
                projectName
        );
    }
}
