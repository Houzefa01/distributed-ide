package com.distributed_ide.javafx_client.collaboration.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Représente une demande de partage de fichier/projet.
 */
public class ShareRequest {
    private final String shareRequestId;
    private final String initiatorClientId;
    private final String initiatorUsername;
    private final Set<String> targetClientIds;
    private final ShareType type;  // PROJECT | FILE | FOLDER
    private final String resourcePath;
    private final String resourceName;
    private final long createdAt;
    private final ShareStatus status;  // PENDING | ACCEPTED | REJECTED

    public enum ShareType {
        PROJECT,
        FILE,
        FOLDER
    }

    public enum ShareStatus {
        PENDING,
        ACCEPTED,
        REJECTED
    }

    private ShareRequest(Builder builder) {
        this.shareRequestId = builder.shareRequestId;
        this.initiatorClientId = builder.initiatorClientId;
        this.initiatorUsername = builder.initiatorUsername;
        this.targetClientIds = new HashSet<>(builder.targetClientIds);
        this.type = builder.type;
        this.resourcePath = builder.resourcePath;
        this.resourceName = builder.resourceName;
        this.createdAt = builder.createdAt;
        this.status = builder.status;
    }

    public static class Builder {
        private String shareRequestId;
        private String initiatorClientId;
        private String initiatorUsername;
        private Set<String> targetClientIds = new HashSet<>();
        private ShareType type = ShareType.PROJECT;
        private String resourcePath;
        private String resourceName;
        private long createdAt = System.currentTimeMillis();
        private ShareStatus status = ShareStatus.PENDING;

        public Builder shareRequestId(String id) { this.shareRequestId = id; return this; }
        public Builder initiatorClientId(String id) { this.initiatorClientId = id; return this; }
        public Builder initiatorUsername(String name) { this.initiatorUsername = name; return this; }
        public Builder targetClientIds(Set<String> ids) { this.targetClientIds = new HashSet<>(ids); return this; }
        public Builder addTargetClient(String clientId) { this.targetClientIds.add(clientId); return this; }
        public Builder type(ShareType type) { this.type = type; return this; }
        public Builder resourcePath(String path) { this.resourcePath = path; return this; }
        public Builder resourceName(String name) { this.resourceName = name; return this; }
        public Builder status(ShareStatus status) { this.status = status; return this; }

        public ShareRequest build() {
            Objects.requireNonNull(shareRequestId, "shareRequestId must not be null");
            Objects.requireNonNull(initiatorClientId, "initiatorClientId must not be null");
            Objects.requireNonNull(resourcePath, "resourcePath must not be null");
            return new ShareRequest(this);
        }
    }

    // Getters
    public String getShareRequestId() { return shareRequestId; }
    public String getInitiatorClientId() { return initiatorClientId; }
    public String getInitiatorUsername() { return initiatorUsername; }
    public Set<String> getTargetClientIds() { return new HashSet<>(targetClientIds); }
    public ShareType getType() { return type; }
    public String getResourcePath() { return resourcePath; }
    public String getResourceName() { return resourceName; }
    public long getCreatedAt() { return createdAt; }
    public ShareStatus getStatus() { return status; }

    public String getDisplayMessage() {
        return String.format("%s wants to share %s '%s'",
                initiatorUsername, type.toString().toLowerCase(), resourceName);
    }

    @Override
    public String toString() {
        return "ShareRequest{" +
                "shareRequestId='" + shareRequestId + '\'' +
                ", initiator='" + initiatorUsername + '\'' +
                ", type=" + type +
                ", resourceName='" + resourceName + '\'' +
                ", status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShareRequest that = (ShareRequest) o;
        return Objects.equals(shareRequestId, that.shareRequestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shareRequestId);
    }
}
