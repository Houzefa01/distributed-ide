package com.distributed_ide.javafx_client.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Request data for project creation from wizard.
 */
public class ProjectCreationRequest {
    private final ProjectType projectType;
    private final String projectName;
    private final Path parentDirectory;
    private final String packageName;          // For Java
    private final String mainClassName;       // For Java
    private final String jdkVersion;          // For Java
    private final String pythonVersion;       // For Python
    private final boolean createVenv;         // For Python
    private final boolean createMainFile;     // Generate main file
    private final String groupId;             // For Maven
    private final String version;             // For Maven

    private ProjectCreationRequest(Builder builder) {
        this.projectType = Objects.requireNonNull(builder.projectType, "projectType required");
        this.projectName = Objects.requireNonNull(builder.projectName, "projectName required");
        this.parentDirectory = Objects.requireNonNull(builder.parentDirectory, "parentDirectory required");
        this.packageName = builder.packageName;
        this.mainClassName = builder.mainClassName;
        this.jdkVersion = builder.jdkVersion != null ? builder.jdkVersion : "21";
        this.pythonVersion = builder.pythonVersion != null ? builder.pythonVersion : "3.8";
        this.createVenv = builder.createVenv;
        this.createMainFile = builder.createMainFile;
        this.groupId = builder.groupId;
        this.version = builder.version != null ? builder.version : "1.0-SNAPSHOT";
    }

    // Getters
    public ProjectType getProjectType() { return projectType; }
    public String getProjectName() { return projectName; }
    public Path getParentDirectory() { return parentDirectory; }
    public String getPackageName() { return packageName; }
    public String getMainClassName() { return mainClassName; }
    public String getJdkVersion() { return jdkVersion; }
    public String getPythonVersion() { return pythonVersion; }
    public boolean isCreateVenv() { return createVenv; }
    public boolean isCreateMainFile() { return createMainFile; }
    public String getGroupId() { return groupId; }
    public String getVersion() { return version; }
    public String getArtifactId() { return projectName.toLowerCase().replace(" ", "-"); }

    /**
     * Builder for ProjectCreationRequest
     */
    public static class Builder {
        private ProjectType projectType;
        private String projectName;
        private Path parentDirectory;
        private String packageName;
        private String mainClassName;
        private String jdkVersion;
        private String pythonVersion;
        private boolean createVenv;
        private boolean createMainFile = true;
        private String groupId;
        private String version;

        public Builder projectType(ProjectType projectType) {
            this.projectType = projectType;
            return this;
        }

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder parentDirectory(Path parentDirectory) {
            this.parentDirectory = parentDirectory;
            return this;
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder mainClassName(String mainClassName) {
            this.mainClassName = mainClassName;
            return this;
        }

        public Builder jdkVersion(String jdkVersion) {
            this.jdkVersion = jdkVersion;
            return this;
        }

        public Builder pythonVersion(String pythonVersion) {
            this.pythonVersion = pythonVersion;
            return this;
        }

        public Builder createVenv(boolean createVenv) {
            this.createVenv = createVenv;
            return this;
        }

        public Builder createMainFile(boolean createMainFile) {
            this.createMainFile = createMainFile;
            return this;
        }

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public ProjectCreationRequest build() {
            return new ProjectCreationRequest(this);
        }
    }

    @Override
    public String toString() {
        return "ProjectCreationRequest{" +
                "projectType=" + projectType +
                ", projectName='" + projectName + '\'' +
                ", parentDirectory=" + parentDirectory +
                ", packageName='" + packageName + '\'' +
                '}';
    }
}
