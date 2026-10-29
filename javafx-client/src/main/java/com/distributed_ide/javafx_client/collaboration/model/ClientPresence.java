package com.distributed_ide.javafx_client.collaboration.model;

import java.util.Objects;

/**
 * Représente la présence d'un client collaborateur.
 * État en temps réel, couleur unique, et métadonnées de session.
 */
public class ClientPresence {
    private final String clientId;
    private final String username;
    private final String role;  // admin | editor | viewer
    private final PresenceStatus status;
    private final String currentDocument;  // ID du document en édition
    private final long lastActivityTimestamp;
    private final String color;  // Couleur hex unique (e.g., "#FF5733")
    private final boolean audioEnabled;
    private final boolean videoEnabled;
    private final String sessionToken;
    private final long connectedSince;

    public enum PresenceStatus {
        ONLINE,      // Connecté mais pas actif
        EDITING,     // En train d'éditer
        IDLE,        // Pas d'activité depuis 2 min
        AWAY,        // Pas d'activité depuis 5 min
        OFFLINE
    }

    private ClientPresence(Builder builder) {
        this.clientId = builder.clientId;
        this.username = builder.username;
        this.role = builder.role;
        this.status = builder.status;
        this.currentDocument = builder.currentDocument;
        this.lastActivityTimestamp = builder.lastActivityTimestamp;
        this.color = builder.color;
        this.audioEnabled = builder.audioEnabled;
        this.videoEnabled = builder.videoEnabled;
        this.sessionToken = builder.sessionToken;
        this.connectedSince = builder.connectedSince;
    }

    public static class Builder {
        private String clientId;
        private String username;
        private String role = "editor";
        private PresenceStatus status = PresenceStatus.ONLINE;
        private String currentDocument = null;
        private long lastActivityTimestamp = System.currentTimeMillis();
        private String color = "#000000";
        private boolean audioEnabled = true;
        private boolean videoEnabled = false;
        private String sessionToken;
        private long connectedSince = System.currentTimeMillis();

        public Builder clientId(String clientId) { this.clientId = clientId; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder role(String role) { this.role = role; return this; }
        public Builder status(PresenceStatus status) { this.status = status; return this; }
        public Builder currentDocument(String docId) { this.currentDocument = docId; return this; }
        public Builder lastActivityTimestamp(long timestamp) { this.lastActivityTimestamp = timestamp; return this; }
        public Builder color(String color) { this.color = color; return this; }
        public Builder audioEnabled(boolean enabled) { this.audioEnabled = enabled; return this; }
        public Builder videoEnabled(boolean enabled) { this.videoEnabled = enabled; return this; }
        public Builder sessionToken(String token) { this.sessionToken = token; return this; }
        public Builder connectedSince(long timestamp) { this.connectedSince = timestamp; return this; }

        public ClientPresence build() {
            Objects.requireNonNull(clientId, "clientId must not be null");
            Objects.requireNonNull(username, "username must not be null");
            return new ClientPresence(this);
        }
    }

    // Getters
    public String getClientId() { return clientId; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public PresenceStatus getStatus() { return status; }
    public String getCurrentDocument() { return currentDocument; }
    public long getLastActivityTimestamp() { return lastActivityTimestamp; }
    public String getColor() { return color; }
    public boolean isAudioEnabled() { return audioEnabled; }
    public boolean isVideoEnabled() { return videoEnabled; }
    public String getSessionToken() { return sessionToken; }
    public long getConnectedSince() { return connectedSince; }

    public long getIdleDurationMs() {
        return System.currentTimeMillis() - lastActivityTimestamp;
    }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }

    public boolean canEdit() {
        return role != null && (role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("editor"));
    }

    @Override
    public String toString() {
        return "ClientPresence{" +
                "clientId='" + clientId + '\'' +
                ", username='" + username + '\'' +
                ", role='" + role + '\'' +
                ", status=" + status +
                ", color='" + color + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientPresence that = (ClientPresence) o;
        return Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId);
    }
}
