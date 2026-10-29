package com.distributed_ide.project_manager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a file within a project with CRDT association.
 * Each file is associated with a CRDT document for collaborative editing.
 */
public class ProjectFile {
    
    public enum FileType {
        JAVA(".java"),
        PYTHON(".py"),
        HTML(".html"),
        CSS(".css"),
        JAVASCRIPT(".js"),
        XML(".xml"),
        JSON(".json"),
        MARKDOWN(".md"),
        YAML(".yaml"),
        OTHER("");
        
        private final String extension;
        
        FileType(String extension) {
            this.extension = extension;
        }
        
        public String getExtension() {
            return extension;
        }
        
        public static FileType fromExtension(String extension) {
            for (FileType type : values()) {
                if (type.extension.equalsIgnoreCase(extension)) {
                    return type;
                }
            }
            return OTHER;
        }
    }
    
    public enum FileStatus {
        CREATED, MODIFIED, DELETED, SYNCED
    }
    
    private final String fileId;
    private final String projectId;
    private final String relativePath;
    private final FileType fileType;
    private String crdtDocumentId;
    private FileStatus status;
    private long sizeInBytes;
    private String contentHash;
    private Instant createdAt;
    private Instant modifiedAt;
    private String lastModifiedBy;
    private final Map<String, Object> metadata;
    
    public ProjectFile(String fileId, String projectId, String relativePath, FileType fileType) {
        this.fileId = Objects.requireNonNull(fileId, "fileId cannot be null");
        this.projectId = Objects.requireNonNull(projectId, "projectId cannot be null");
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath cannot be null");
        this.fileType = Objects.requireNonNull(fileType, "fileType cannot be null");
        this.status = FileStatus.CREATED;
        this.createdAt = Instant.now();
        this.metadata = new HashMap<>();
    }
    
    // Getters
    public String getFileId() { return fileId; }
    public String getProjectId() { return projectId; }
    public String getRelativePath() { return relativePath; }
    public FileType getFileType() { return fileType; }
    public String getCrdtDocumentId() { return crdtDocumentId; }
    public FileStatus getStatus() { return status; }
    public long getSizeInBytes() { return sizeInBytes; }
    public String getContentHash() { return contentHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getModifiedAt() { return modifiedAt; }
    public String getLastModifiedBy() { return lastModifiedBy; }
    
    // Setters
    public void setCrdtDocumentId(String crdtDocumentId) {
        this.crdtDocumentId = crdtDocumentId;
    }
    
    public void setStatus(FileStatus status) {
        this.status = Objects.requireNonNull(status);
    }
    
    public void setSizeInBytes(long sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
    }
    
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
    
    public void updateModification(String modifiedBy) {
        this.modifiedAt = Instant.now();
        this.lastModifiedBy = Objects.requireNonNull(modifiedBy);
        this.status = FileStatus.MODIFIED;
    }
    
    // Metadata operations
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    public Map<String, Object> getAllMetadata() {
        return new HashMap<>(metadata);
    }
    
    public String getFileName() {
        int lastSlash = relativePath.lastIndexOf('/');
        return lastSlash == -1 ? relativePath : relativePath.substring(lastSlash + 1);
    }
    
    public String getDirectory() {
        int lastSlash = relativePath.lastIndexOf('/');
        return lastSlash == -1 ? "" : relativePath.substring(0, lastSlash);
    }
    
    public boolean isInDirectory(String directory) {
        return relativePath.startsWith(directory);
    }
    
    @Override
    public String toString() {
        return "ProjectFile{" +
                "fileId='" + fileId + '\'' +
                ", relativePath='" + relativePath + '\'' +
                ", fileType=" + fileType +
                ", status=" + status +
                ", sizeInBytes=" + sizeInBytes +
                ", crdtDocumentId='" + crdtDocumentId + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectFile that = (ProjectFile) o;
        return Objects.equals(fileId, that.fileId) &&
               Objects.equals(projectId, that.projectId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fileId, projectId);
    }
}
