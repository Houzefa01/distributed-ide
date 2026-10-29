package com.distributed_ide.javafx_client.collaboration.service;

import com.distributed_ide.javafx_client.collaboration.model.CallSession;
import com.distributed_ide.javafx_client.collaboration.model.CallSession.CallType;
import com.distributed_ide.javafx_client.collaboration.model.ClientPresence;
import com.distributed_ide.javafx_client.collaboration.model.ClientPresence.PresenceStatus;
import com.distributed_ide.javafx_client.collaboration.model.PermissionModel;
import com.distributed_ide.javafx_client.collaboration.model.RemoteCursor;
import com.distributed_ide.javafx_client.collaboration.model.ShareRequest;
import com.distributed_ide.javafx_client.service.SignalingClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Moteur central de collaboration.
 * Coordonne tous les managers : présence, permissions, curseurs, appels, partage.
 * Point d'intégration unique pour la partie collaboration de l'IDE.
 */
public class CollaborationEngine {
    private static final Logger LOG = LoggerFactory.getLogger(CollaborationEngine.class);

    // Managers
    private final ClientPresenceManager presenceManager;
    private final PermissionManager permissionManager;
    private final RemoteCursorManager cursorManager;
    private final WebRTCCallManager callManager;
    private final FileShareManager shareManager;

    // Listeners unifiés
    // Note: listeners moved to field initialization

    // État
    private volatile String myClientId;
    private volatile String myUsername;
    private volatile boolean initialized = false;
    private volatile SignalingClientService signalingService;

    public interface CollaborationListener {
        void onCollaborationStateChanged(CollaborationState state);
        void onError(String error);
    }

    public enum CollaborationState {
        INITIALIZING,
        READY,
        HOSTING,
        CONNECTED,
        DISCONNECTED,
        ERROR
    }

    public CollaborationEngine() {
        this.presenceManager = new ClientPresenceManager();
        this.permissionManager = new PermissionManager();
        this.cursorManager = new RemoteCursorManager();
        this.callManager = new WebRTCCallManager();
        this.shareManager = new FileShareManager();
        
        LOG.info("CollaborationEngine created");
    }

    // ============= Initialisation =============

    /**
     * Initialise le moteur de collaboration.
     */
    public void initialize(String clientId, String username, SignalingClientService signalingService) {
        this.myClientId = clientId;
        this.myUsername = username;
        this.signalingService = signalingService;

        permissionManager.setCurrentClientId(clientId);
        callManager.initialize(clientId, username, signalingService);
        shareManager.initialize(clientId, username);

        this.initialized = true;
        LOG.info("CollaborationEngine initialized: {} ({})", username, clientId);
    }

    /**
     * Enregistre la présence du client actuel.
     */
    public void registerMyPresence(String role) {
        if (!initialized) {
            LOG.warn("Engine not initialized");
            return;
        }

        ClientPresence presence = new ClientPresence.Builder()
                .clientId(myClientId)
                .username(myUsername)
                .role(role)
                .status(PresenceStatus.ONLINE)
                .color(presenceManager.getNextColor())
                .sessionToken("token_" + System.nanoTime())
                .build();

        presenceManager.register(presence);

        // Grant permission based on role
        PermissionModel permission = new PermissionModel.Builder()
                .clientId(myClientId)
                .role(role)
                .build();

        permissionManager.grantPermission(permission);

        LOG.info("My presence registered: role={}", role);
    }

    // ============= Gestion Présence =============

    public List<ClientPresence> getOnlineClients() {
        return presenceManager.getOnlineClients();
    }

    public Optional<ClientPresence> getClient(String clientId) {
        return presenceManager.getClient(clientId);
    }

    public void recordActivity() {
        if (initialized) {
            presenceManager.recordActivity(myClientId);
        }
    }

    public void setEditorActivity(String documentId) {
        if (initialized) {
            presenceManager.updateCurrentDocument(myClientId, documentId);
            presenceManager.updateStatus(myClientId, PresenceStatus.EDITING);
        }
    }

    // ============= Gestion Permissions =============

    public boolean canEditDocument(String documentId) {
        return permissionManager.canEditDocument(myClientId, documentId);
    }

    public boolean canReadDocument(String documentId) {
        return permissionManager.canReadDocument(myClientId, documentId);
    }

    public boolean isAdmin() {
        return permissionManager.isAdmin(myClientId);
    }

    public boolean canStartCalls() {
        return permissionManager.canStartCalls(myClientId);
    }

    public boolean canShareProjects() {
        return permissionManager.canShareProjects(myClientId);
    }

    // ============= Gestion Curseurs Distants =============

    public void reportRemoteCursorUpdate(String documentId, String clientId, String username,
                                        int line, int column, Optional<RemoteCursor.Selection> selection,
                                        String color) {
        cursorManager.reportCursorUpdate(documentId, clientId, username, line, column, selection, color);
    }

    public List<RemoteCursor> getRemoteCursors(String documentId) {
        return cursorManager.getCursorsForDocument(documentId);
    }

    public void removeRemoteCursor(String documentId, String clientId) {
        cursorManager.removeCursor(documentId, clientId);
    }

    // ============= Gestion Appels WebRTC =============

    public String initiateCall(String targetClientId, CallType type) {
        if (!permissionManager.canStartCalls(myClientId)) {
            LOG.warn("Not authorized to start calls");
            return null;
        }
        return callManager.initiateCall(targetClientId, type);
    }

    public void answerCall(String callId) {
        callManager.answerCall(callId);
    }

    public void declineCall(String callId, String reason) {
        callManager.declineCall(callId, reason);
    }

    public void endCall(String callId, String reason) {
        callManager.endCall(callId, reason);
    }

    public void muteAudio(String callId) {
        callManager.muteAudio(callId);
    }

    public void unmuteAudio(String callId) {
        callManager.unmuteAudio(callId);
    }

    public void disableVideo(String callId) {
        callManager.disableVideo(callId);
    }

    public void enableVideo(String callId) {
        callManager.enableVideo(callId);
    }

    public List<CallSession> getActiveCalls() {
        return callManager.getActiveCalls();
    }

    // ============= Gestion Partage de Fichiers =============

    public String requestShareProject(Set<String> targetClientIds, String projectPath, String projectName) {
        if (!permissionManager.canShareProjects(myClientId)) {
            LOG.warn("Not authorized to share projects");
            return null;
        }
        return shareManager.requestShareProject(targetClientIds, projectPath, projectName);
    }

    public String requestShareFile(Set<String> targetClientIds, String filePath, String fileName) {
        if (!permissionManager.canShareProjects(myClientId)) {
            LOG.warn("Not authorized to share files");
            return null;
        }
        return shareManager.requestShareFile(targetClientIds, filePath, fileName);
    }

    public void acceptShare(String shareRequestId) {
        shareManager.acceptShare(shareRequestId);
    }

    public void declineShare(String shareRequestId, String reason) {
        shareManager.declineShare(shareRequestId, reason);
    }

    public List<ShareRequest> getPendingShares() {
        return shareManager.getPendingRequests();
    }

    // ============= Listeners Managers =============

    public void addPresenceListener(ClientPresenceManager.PresenceListener listener) {
        presenceManager.addListener(listener);
    }

    public void removePresenceListener(ClientPresenceManager.PresenceListener listener) {
        presenceManager.removeListener(listener);
    }

    public void addCursorListener(RemoteCursorManager.CursorListener listener) {
        cursorManager.addListener(listener);
    }

    public void removeCursorListener(RemoteCursorManager.CursorListener listener) {
        cursorManager.removeListener(listener);
    }

    public void addCallListener(WebRTCCallManager.CallListener listener) {
        callManager.addListener(listener);
    }

    public void removeCallListener(WebRTCCallManager.CallListener listener) {
        callManager.removeListener(listener);
    }

    public void addShareListener(FileShareManager.ShareListener listener) {
        shareManager.addListener(listener);
    }

    public void removeShareListener(FileShareManager.ShareListener listener) {
        shareManager.removeListener(listener);
    }

    // ============= Lifecycle =============

    public void shutdown() {
        LOG.info("Shutting down CollaborationEngine");
        presenceManager.shutdown();
        permissionManager.shutdown();
        cursorManager.shutdown();
        callManager.shutdown();
        shareManager.shutdown();
        initialized = false;
    }

    // ============= Getters =============

    public ClientPresenceManager getPresenceManager() {
        return presenceManager;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public RemoteCursorManager getCursorManager() {
        return cursorManager;
    }

    public WebRTCCallManager getCallManager() {
        return callManager;
    }

    public FileShareManager getShareManager() {
        return shareManager;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public String getMyClientId() {
        return myClientId;
    }

    public String getMyUsername() {
        return myUsername;
    }
}
