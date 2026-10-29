package com.distributed_ide.javafx_client.collaboration.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Représente un curseur distant d'un collaborateur.
 * Position ligne/colonne, sélection étendue, couleur unique, et nom utilisateur.
 */
public class RemoteCursor {
    private final String clientId;
    private final String username;
    private final String color;
    private final int line;
    private final int column;
    private final long lastUpdateTimestamp;
    private final Optional<Selection> selection;

    public static class Selection {
        public final int startLine;
        public final int startCol;
        public final int endLine;
        public final int endCol;

        public Selection(int startLine, int startCol, int endLine, int endCol) {
            this.startLine = startLine;
            this.startCol = startCol;
            this.endLine = endLine;
            this.endCol = endCol;
        }

        @Override
        public String toString() {
            return String.format("(%d:%d)-(%d:%d)", startLine, startCol, endLine, endCol);
        }
    }

    private RemoteCursor(Builder builder) {
        this.clientId = builder.clientId;
        this.username = builder.username;
        this.color = builder.color;
        this.line = builder.line;
        this.column = builder.column;
        this.lastUpdateTimestamp = builder.lastUpdateTimestamp;
        this.selection = builder.selection;
    }

    public static class Builder {
        private String clientId;
        private String username;
        private String color = "#000000";
        private int line = 0;
        private int column = 0;
        private long lastUpdateTimestamp = System.currentTimeMillis();
        private Optional<Selection> selection = Optional.empty();

        public Builder clientId(String clientId) { this.clientId = clientId; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder color(String color) { this.color = color; return this; }
        public Builder position(int line, int col) { this.line = line; this.column = col; return this; }
        public Builder lastUpdateTimestamp(long ts) { this.lastUpdateTimestamp = ts; return this; }
        public Builder selection(Selection sel) { this.selection = Optional.ofNullable(sel); return this; }

        public RemoteCursor build() {
            Objects.requireNonNull(clientId, "clientId must not be null");
            Objects.requireNonNull(username, "username must not be null");
            return new RemoteCursor(this);
        }
    }

    // Getters
    public String getClientId() { return clientId; }
    public String getUsername() { return username; }
    public String getColor() { return color; }
    public int getLine() { return line; }
    public int getColumn() { return column; }
    public long getLastUpdateTimestamp() { return lastUpdateTimestamp; }
    public Optional<Selection> getSelection() { return selection; }

    public String getDisplayFormat() {
        return String.format("%s @ %d:%d", username, line, column);
    }

    public long getAgeMs() {
        return System.currentTimeMillis() - lastUpdateTimestamp;
    }

    @Override
    public String toString() {
        return "RemoteCursor{" +
                "clientId='" + clientId + '\'' +
                ", username='" + username + '\'' +
                ", position=" + line + ":" + column +
                ", color='" + color + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemoteCursor that = (RemoteCursor) o;
        return Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId);
    }
}
