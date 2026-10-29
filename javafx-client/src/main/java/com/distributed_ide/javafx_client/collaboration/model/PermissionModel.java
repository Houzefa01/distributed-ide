package com.distributed_ide.javafx_client.collaboration.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Modèle de permissions pour un client collaborateur.
 * Define rôles, droits d'accès et limitations.
 */
public class PermissionModel {
    private final String clientId;
    private final String role;  // admin | editor | viewer
    private final Set<String> allowedDocumentIds;  // null = tous les documents
    private final boolean canKickClients;
    private final boolean canShareProjects;
    private final boolean canManagePermissions;
    private final boolean canStartCalls;
    private final boolean canLockDocuments;
    private final long grantedAt;
    private final long expiresAt;  // 0 = jamais

    public enum DocumentPermission {
        VIEW,       // Lecture seule
        EDIT,       // Éditer
        OWN,        // Modifier permissions + supprimer
        LOCK        // Verrouillé par admin
    }

    private PermissionModel(Builder builder) {
        this.clientId = builder.clientId;
        this.role = builder.role;
        this.allowedDocumentIds = new HashSet<>(builder.allowedDocumentIds);
        this.canKickClients = builder.canKickClients;
        this.canShareProjects = builder.canShareProjects;
        this.canManagePermissions = builder.canManagePermissions;
        this.canStartCalls = builder.canStartCalls;
        this.canLockDocuments = builder.canLockDocuments;
        this.grantedAt = builder.grantedAt;
        this.expiresAt = builder.expiresAt;
    }

    public static class Builder {
        private String clientId;
        private String role = "viewer";
        private Set<String> allowedDocumentIds = new HashSet<>();
        private boolean canKickClients = false;
        private boolean canShareProjects = false;
        private boolean canManagePermissions = false;
        private boolean canStartCalls = true;
        private boolean canLockDocuments = false;
        private long grantedAt = System.currentTimeMillis();
        private long expiresAt = 0;  // 0 = jamais

        public Builder clientId(String clientId) { this.clientId = clientId; return this; }
        public Builder role(String role) { this.role = role; return this; }
        public Builder allowedDocumentIds(Set<String> ids) { this.allowedDocumentIds = new HashSet<>(ids); return this; }
        public Builder addAllowedDocument(String docId) { this.allowedDocumentIds.add(docId); return this; }
        public Builder canKickClients(boolean b) { this.canKickClients = b; return this; }
        public Builder canShareProjects(boolean b) { this.canShareProjects = b; return this; }
        public Builder canManagePermissions(boolean b) { this.canManagePermissions = b; return this; }
        public Builder canStartCalls(boolean b) { this.canStartCalls = b; return this; }
        public Builder canLockDocuments(boolean b) { this.canLockDocuments = b; return this; }
        public Builder expiresAt(long timestamp) { this.expiresAt = timestamp; return this; }

        public PermissionModel build() {
            Objects.requireNonNull(clientId, "clientId must not be null");
            // Set default permissions based on role
            switch (role.toLowerCase()) {
                case "admin":
                    this.canKickClients = true;
                    this.canShareProjects = true;
                    this.canManagePermissions = true;
                    this.canStartCalls = true;
                    this.canLockDocuments = true;
                    break;
                case "editor":
                    this.canShareProjects = true;
                    this.canStartCalls = true;
                    this.canLockDocuments = false;
                    break;
                case "viewer":
                    this.canShareProjects = false;
                    this.canStartCalls = true;
                    this.canLockDocuments = false;
                    break;
            }
            return new PermissionModel(this);
        }
    }

    // Getters
    public String getClientId() { return clientId; }
    public String getRole() { return role; }
    public Set<String> getAllowedDocumentIds() { return new HashSet<>(allowedDocumentIds); }
    public boolean isCanKickClients() { return canKickClients; }
    public boolean isCanShareProjects() { return canShareProjects; }
    public boolean isCanManagePermissions() { return canManagePermissions; }
    public boolean isCanStartCalls() { return canStartCalls; }
    public boolean isCanLockDocuments() { return canLockDocuments; }
    public long getGrantedAt() { return grantedAt; }
    public long getExpiresAt() { return expiresAt; }

    public boolean hasAccessToDocument(String documentId) {
        if (allowedDocumentIds.isEmpty()) return true;  // null = tous
        return allowedDocumentIds.contains(documentId);
    }

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    public boolean isValid() {
        return !isExpired();
    }

    @Override
    public String toString() {
        return "PermissionModel{" +
                "clientId='" + clientId + '\'' +
                ", role='" + role + '\'' +
                ", canKickClients=" + canKickClients +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PermissionModel that = (PermissionModel) o;
        return Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId);
    }
}
