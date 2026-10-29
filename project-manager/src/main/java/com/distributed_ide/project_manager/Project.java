package com.distributed_ide.project_manager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a software project with multi-language support.
 */
public class Project {
    
    public enum ProjectType {
        JAVA_PROJECT("Java"),
        PYTHON_PROJECT("Python"),
        WEB_PROJECT("Web"),
        MULTI_LANGUAGE("Multi-Language");
        
        private final String displayName;
        
        ProjectType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum ProjectStatus {
        CREATED, OPEN, CLOSED, ARCHIVED
    }
    
    private final String projectId;
    private final String name;
    private final ProjectType type;
    private String description;
    private String basePath;
    private ProjectStatus status;
    private String ownerUserId;
    private Instant createdAt;
    private Instant modifiedAt;
    private Instant closedAt;
    private final Map<String, Object> metadata;
    private int totalFiles;
    
    public Project(String projectId, String name, ProjectType type) {
        this.projectId = Objects.requireNonNull(projectId, "projectId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.status = ProjectStatus.CREATED;
        this.createdAt = Instant.now();
        this.metadata = new HashMap<>();
        this.totalFiles = 0;
    }
    
    // Getters
    public String getProjectId() { return projectId; }
    public String getName() { return name; }
    public ProjectType getType() { return type; }
    public String getDescription() { return description; }
    public String getBasePath() { return basePath; }
    public ProjectStatus getStatus() { return status; }
    public String getOwnerUserId() { return ownerUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getModifiedAt() { return modifiedAt; }
    public Instant getClosedAt() { return closedAt; }
    public int getTotalFiles() { return totalFiles; }
    
    // Setters
    public void setDescription(String description) {
        this.description = description;
        updateModificationTime();
    }
    
    public void setBasePath(String basePath) {
        this.basePath = Objects.requireNonNull(basePath, "basePath cannot be null");
        updateModificationTime();
    }
    
    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId cannot be null");
    }
    
    public void setStatus(ProjectStatus status) {
        this.status = Objects.requireNonNull(status);
        updateModificationTime();
        
        if (status == ProjectStatus.CLOSED) {
            this.closedAt = Instant.now();
        }
    }
    
    public void incrementFileCount() {
        this.totalFiles++;
        updateModificationTime();
    }
    
    public void decrementFileCount() {
        if (totalFiles > 0) {
            this.totalFiles--;
            updateModificationTime();
        }
    }
    
    private void updateModificationTime() {
        this.modifiedAt = Instant.now();
    }
    
    // Metadata operations
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
        updateModificationTime();
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    public Map<String, Object> getAllMetadata() {
        return new HashMap<>(metadata);
    }
    
    public boolean isOpen() {
        return status == ProjectStatus.OPEN;
    }
    
    public boolean isClosed() {
        return status == ProjectStatus.CLOSED;
    }
    
    public boolean isArchived() {
        return status == ProjectStatus.ARCHIVED;
    }
    
    @Override
    public String toString() {
        return "Project{" +
                "projectId='" + projectId + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", status=" + status +
                ", totalFiles=" + totalFiles +
                ", owner='" + ownerUserId + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return Objects.equals(projectId, project.projectId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(projectId);
    }
}
