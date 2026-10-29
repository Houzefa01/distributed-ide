package com.distributed_ide.sync.model;

import com.distributed_ide.crdt.model.CRDTOperation;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Message de synchronisation CRDT transmis via WebSocket.
 * Format: JSON sérialisable pour transport réseau.
 */
public class SyncMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MessageType {
        INIT,                   // Initialisation client
        DOCUMENT_JOIN,          // Client rejoint un document
        DOCUMENT_LEAVE,         // Client quitte un document
        OPERATION,              // Opération CRDT
        BATCH_OPERATIONS,       // Batch d'opérations
        ACK,                    // Accusé de réception
        SYNC_REQUEST,           // Demande de synchronisation
        SYNC_RESPONSE,          // Réponse avec état du serveur
        ERROR,                  // Erreur
        HEARTBEAT,              // Ping pour maintenir la connexion
        AUTH,                   // Authentification token + username
        CLIENT_LIST,            // Liste des clients connectés
        ADMIN_KICK,             // Admin expulse un client
        SERVER_SHUTDOWN,        // Admin arrête le serveur
        KICKED,                 // Notification d'expulsion
        SERVER_STOPPING,        // Notification arrêt serveur
        CURSOR_UPDATE,          // Position curseur d'un collaborateur
        SELECTION_UPDATE,       // Sélection d'un collaborateur
        CHAT                    // Message chat
    }

    private MessageType type;
    private String clientId;
    private String documentId;
    private long timestamp;
    private String nodeId;
    
    // Pour OPERATION et BATCH_OPERATIONS
    private List<OperationData> operations;
    
    // Pour ACK
    private long acknowledgedTimestamp;
    
    // Pour ERROR
    private String errorMessage;
    
    // Pour SYNC_RESPONSE
    private List<OperationData> fullHistory;
    private long serverTimestamp;

    // Pour AUTH
    private String username;
    private String token;
    private String role;

    // Pour CLIENT_LIST
    private List<ClientInfo> clients;

    // Pour ADMIN_KICK
    private String targetClientId;

    // Pour KICKED / SERVER_STOPPING
    private String reason;

    // Pour CURSOR_UPDATE
    private int cursorLine;
    private int cursorColumn;
    private String cursorColor;

    // Pour SELECTION_UPDATE
    private int selStartLine;
    private int selStartColumn;
    private int selEndLine;
    private int selEndColumn;

    // Pour CHAT
    private String chatMessage;

    // Constructeur par défaut pour Jackson
    public SyncMessage() {
        this.timestamp = System.currentTimeMillis();
        this.operations = new ArrayList<>();
    }

    public SyncMessage(MessageType type, String clientId, String documentId, String nodeId) {
        this();
        this.type = type;
        this.clientId = clientId;
        this.documentId = documentId;
        this.nodeId = nodeId;
    }

    // Getters et setters
    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public List<OperationData> getOperations() {
        return operations;
    }

    public void setOperations(List<OperationData> operations) {
        this.operations = operations;
    }

    public void addOperation(OperationData op) {
        this.operations.add(op);
    }

    public long getAcknowledgedTimestamp() {
        return acknowledgedTimestamp;
    }

    public void setAcknowledgedTimestamp(long acknowledgedTimestamp) {
        this.acknowledgedTimestamp = acknowledgedTimestamp;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<OperationData> getFullHistory() {
        return fullHistory;
    }

    public void setFullHistory(List<OperationData> fullHistory) {
        this.fullHistory = fullHistory;
    }

    public long getServerTimestamp() {
        return serverTimestamp;
    }

    public void setServerTimestamp(long serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public List<ClientInfo> getClients() { return clients; }
    public void setClients(List<ClientInfo> clients) { this.clients = clients; }

    public String getTargetClientId() { return targetClientId; }
    public void setTargetClientId(String targetClientId) { this.targetClientId = targetClientId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public int getCursorLine() { return cursorLine; }
    public void setCursorLine(int cursorLine) { this.cursorLine = cursorLine; }
    public int getCursorColumn() { return cursorColumn; }
    public void setCursorColumn(int cursorColumn) { this.cursorColumn = cursorColumn; }
    public String getCursorColor() { return cursorColor; }
    public void setCursorColor(String cursorColor) { this.cursorColor = cursorColor; }

    public int getSelStartLine() { return selStartLine; }
    public void setSelStartLine(int v) { this.selStartLine = v; }
    public int getSelStartColumn() { return selStartColumn; }
    public void setSelStartColumn(int v) { this.selStartColumn = v; }
    public int getSelEndLine() { return selEndLine; }
    public void setSelEndLine(int v) { this.selEndLine = v; }
    public int getSelEndColumn() { return selEndColumn; }
    public void setSelEndColumn(int v) { this.selEndColumn = v; }

    public String getChatMessage() { return chatMessage; }
    public void setChatMessage(String chatMessage) { this.chatMessage = chatMessage; }

    @Override
    public String toString() {
        return String.format("SyncMessage(%s, client=%s, doc=%s, ops=%d)",
                type, clientId, documentId, operations.size());
    }

    /**
     * Info d'un client connecté, pour CLIENT_LIST.
     */
    public static class ClientInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String clientId;
        private String username;
        private String role;
        private long connectedAt;

        public ClientInfo() {}
        public ClientInfo(String clientId, String username, String role, long connectedAt) {
            this.clientId = clientId;
            this.username = username;
            this.role = role;
            this.connectedAt = connectedAt;
        }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public long getConnectedAt() { return connectedAt; }
        public void setConnectedAt(long connectedAt) { this.connectedAt = connectedAt; }
    }

    /**
     * Classe interne représentant une opération sérialisable.
     */
    public static class OperationData implements Serializable {
        private static final long serialVersionUID = 1L;

        private String operationType;  // INSERT ou DELETE
        private long timestamp;
        private String idTimestamp;
        private String idNodeId;
        private int idDisambiguator;
        private char content;

        public OperationData() {}

        public OperationData(CRDTOperation op) {
            this.operationType = op.getType().toString();
            this.timestamp = op.getTimestamp();
            this.idTimestamp = String.valueOf(op.getId().getTimestamp());
            this.idNodeId = op.getId().getNodeId();
            this.idDisambiguator = op.getId().getDisambiguator();
            this.content = op.getContent();
        }

        // Getters et setters
        public String getOperationType() {
            return operationType;
        }

        public void setOperationType(String operationType) {
            this.operationType = operationType;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public String getIdTimestamp() {
            return idTimestamp;
        }

        public void setIdTimestamp(String idTimestamp) {
            this.idTimestamp = idTimestamp;
        }

        public String getIdNodeId() {
            return idNodeId;
        }

        public void setIdNodeId(String idNodeId) {
            this.idNodeId = idNodeId;
        }

        public int getIdDisambiguator() {
            return idDisambiguator;
        }

        public void setIdDisambiguator(int idDisambiguator) {
            this.idDisambiguator = idDisambiguator;
        }

        public char getContent() {
            return content;
        }

        public void setContent(char content) {
            this.content = content;
        }

        @Override
        public String toString() {
            return String.format("OperationData(%s, %s)", operationType, content);
        }
    }
}
