package com.distributed_ide.javafx_client.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Result of project creation.
 */
public class ProjectCreationResult {
    private final Path projectPath;
    private final ProjectType projectType;
    private final String projectName;
    private final LocalDateTime createdAt;
    private final boolean success;
    private final String message;
    private final Throwable error;

    private ProjectCreationResult(Builder builder) {
        this.projectPath = builder.projectPath;
        this.projectType = Objects.requireNonNull(builder.projectType, "projectType required");
        this.projectName = Objects.requireNonNull(builder.projectName, "projectName required");
        this.createdAt = builder.createdAt != null ? builder.createdAt : LocalDateTime.now();
        this.success = builder.success;
        this.message = builder.message;
        this.error = builder.error;
    }

    // Getters
    public Path getProjectPath() { return projectPath; }
    public ProjectType getProjectType() { return projectType; }
    public String getProjectName() { return projectName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Throwable getError() { return error; }

    /**
     * Builder for ProjectCreationResult
     */
    public static class Builder {
        private Path projectPath;
        private ProjectType projectType;
        private String projectName;
        private LocalDateTime createdAt;
        private boolean success;
        private String message;
        private Throwable error;

        public Builder projectPath(Path projectPath) {
            this.projectPath = projectPath;
            return this;
        }

        public Builder projectType(ProjectType projectType) {
            this.projectType = projectType;
            return this;
        }

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }

        public ProjectCreationResult build() {
            return new ProjectCreationResult(this);
        }
    }

    @Override
    public String toString() {
        return "ProjectCreationResult{" +
                "projectPath=" + projectPath +
                ", projectType=" + projectType +
                ", projectName='" + projectName + '\'' +
                ", success=" + success +
                ", message='" + message + '\'' +
                '}';
    }
}
