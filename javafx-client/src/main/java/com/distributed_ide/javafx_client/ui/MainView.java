package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.javafx_client.model.DiagnosticItem;
import com.distributed_ide.javafx_client.model.RemoteCursor;
import com.distributed_ide.javafx_client.service.*;
import com.distributed_ide.javafx_client.webrtc.WebRtcCallService;
import com.distributed_ide.sync.model.SyncMessage;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Main view: tree | editor (tabs) | diagnostics + collaborators; terminal at bottom.
 * MVC structure: services (Sync, LSP, Terminal, File) are injected or created here.
 */
public class MainView {

    private static final Logger LOG = LoggerFactory.getLogger(MainView.class);

    private final BorderPane root;
    private final FileService fileService = new FileService();
    private final SyncClientService syncClient = new SyncClientService();
    private final LspClientService lspClient = new LspClientService();
    private final TerminalClientService terminalClient = new TerminalClientService();
    private final WebRtcCallService webrtcCallService = new WebRtcCallService();

    private final ProjectTreeView projectTreeView;
    private final EditorTabPaneView editorTabPaneView;
    private final DiagnosticsView diagnosticsView;
    private final CollaboratorsView collaboratorsView;
    private final TerminalView terminalView;
    private final CollaborationPanel collaborationPanel;
    private final CollaborationService collaborationService = new CollaborationService();

    private volatile String syncServerWsUri = "ws://localhost:8765";
    private volatile String lspBridgeWsUri = "ws://localhost:8081/lsp";
    private volatile String terminalWsUri = "ws://localhost:8766";
    private volatile String signalingWsUri = "ws://localhost:8082";
    private final List<RemoteCursor> currentCollaborators = new ArrayList<>();

    public MainView() {
        projectTreeView = new ProjectTreeView(fileService);
        editorTabPaneView = new EditorTabPaneView();
        diagnosticsView = new DiagnosticsView();
        collaboratorsView = new CollaboratorsView();
        terminalView = new TerminalView();
        collaborationPanel = new CollaborationPanel();

        root = new BorderPane();
        SplitPane leftRight = new SplitPane();
        leftRight.getItems().add(projectTreeView.getView());
        projectTreeView.getView().setPrefWidth(220);

        SplitPane centerRight = new SplitPane();
        centerRight.getItems().add(editorTabPaneView.getView());
        SplitPane diagCollab = new SplitPane();
        diagCollab.getItems().add(diagnosticsView.getView());
        diagCollab.getItems().add(collaboratorsView.getView());
        diagCollab.setDividerPositions(0.7);
        centerRight.getItems().add(diagCollab);
        centerRight.setDividerPositions(0.75);

        leftRight.getItems().add(centerRight);
        leftRight.setDividerPositions(0.18);

        root.setCenter(leftRight);
        root.setRight(collaborationPanel.getView());

        MenuItem openFolder = new MenuItem("Ouvrir un dossier...");
        openFolder.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            java.io.File dir = chooser.showDialog(null);
            if (dir != null) {
                loadProjectRoot(dir.toPath());
            }
        });
        Menu fileMenu = new Menu("Fichier");
        fileMenu.getItems().add(openFolder);

        MenuItem joinCall = new MenuItem("Rejoindre la salle...");
        joinCall.setOnAction(e -> {
            TextInputDialog roomDialog = new TextInputDialog("room1");
            roomDialog.setTitle("WebRTC Call");
            roomDialog.setHeaderText("Room ID");
            roomDialog.showAndWait().ifPresent(roomId -> {
                TextInputDialog userDialog = new TextInputDialog(webrtcCallService.getDefaultUserId());
                userDialog.setTitle("WebRTC Call");
                userDialog.setHeaderText("Your User ID");
                userDialog.showAndWait().ifPresent(userId -> {
                    webrtcCallService.connect(signalingWsUri);
                    webrtcCallService.joinRoom(roomId, userId);
                    LOG.info("WebRTC: joined room {} as {}", roomId, userId);
                });
            });
        });
        MenuItem leaveCall = new MenuItem("Quitter la salle");
        leaveCall.setOnAction(e -> webrtcCallService.leaveRoom());
        Menu callMenu = new Menu("Appel");
        callMenu.getItems().addAll(joinCall, leaveCall);

        MenuItem hostJoinItem = new MenuItem("Héberger / Rejoindre...");
        hostJoinItem.setOnAction(e -> showHostJoinDialog());
        MenuItem disconnectItem = new MenuItem("Déconnecter");
        disconnectItem.setOnAction(e -> disconnectCollaboration());
        Menu collabMenu = new Menu("Collaboration");
        collabMenu.getItems().addAll(hostJoinItem, disconnectItem);

        MenuBar menuBar = new MenuBar(fileMenu, callMenu, collabMenu);
        root.setTop(menuBar);

        SplitPane bottom = new SplitPane(terminalView.getView());
        bottom.setPrefHeight(200);
        root.setBottom(bottom);

        projectTreeView.setOnFileOpen(this::openFile);
        editorTabPaneView.setOnContentChanged(this::onEditorContentChanged);
        editorTabPaneView.setOnSyncContentUpdate((path, content) -> {});
        diagnosticsView.setOnGoToLine(this::goToLine);
        terminalView.setOnCommand(cmd -> terminalClient.sendInput(cmd));
        terminalView.setOnReconnect(() -> terminalClient.connect(terminalWsUri));

        syncClient.setListener(new SyncClientService.Listener() {
            @Override
            public void onConnected(String cid, String nid) {
                LOG.info("Sync connected: {} / {}", cid, nid);
            }
            @Override
            public void onDocumentUpdated(String documentId, String fullText) {
                Platform.runLater(() -> editorTabPaneView.setContent(documentId, fullText));
            }
            @Override
            public void onCollaboratorJoined(String documentId, String cid, String nid) {
                Platform.runLater(() -> {
                    currentCollaborators.add(new RemoteCursor(cid, nid));
                    collaboratorsView.setCollaboratorsForDocument(new ArrayList<>(currentCollaborators));
                });
            }
            @Override
            public void onRemoteCursorUpdate(String documentId, String clientId, String username, int line, int column, String color) {
                // handled by MainLayoutController
            }
            @Override
            public void onRemoteSelectionUpdate(String documentId, String clientId, String username, int startLine, int startCol, int endLine, int endCol) {
                // handled by MainLayoutController
            }
            @Override
            public void onChatMessage(String clientId, String username, String message, long timestamp) {
                // handled by MainLayoutController
            }
            @Override
            public void onError(String message) {
                LOG.warn("Sync: {}", message);
            }
        });

        lspClient.setListener(new LspClientService.Listener() {
            @Override
            public void onDiagnostics(String uri, List<DiagnosticItem> diagnostics) {
                Platform.runLater(() -> diagnosticsView.setDiagnostics(uri, diagnostics));
            }
            @Override
            public void onMessage(String msg) { /* forward to LSP if needed */ }
            @Override
            public void onError(String message) {
                LOG.warn("LSP: {}", message);
            }
        });

        connectServices();
        setupCollaboration();

        terminalClient.setListener(new TerminalClientService.Listener() {
            @Override
            public void onConnected() {
                terminalView.setConnectionStatus(TerminalView.ConnectionStatus.CONNECTED, null);
            }
            @Override
            public void onDisconnected(String reason) {
                terminalView.setConnectionStatus(TerminalView.ConnectionStatus.DISCONNECTED, reason);
            }
            @Override
            public void onReconnecting(int attempt) {
                terminalView.setConnectionStatus(TerminalView.ConnectionStatus.RECONNECTING, String.valueOf(attempt));
            }
            @Override
            public void onSessionCreated(String sessionId) {
                LOG.info("Terminal session: {}", sessionId);
                terminalView.setConnectionStatus(TerminalView.ConnectionStatus.CONNECTED, "Session " + sessionId);
            }
            @Override
            public void onOutput(String line, boolean isError) {
                Platform.runLater(() -> terminalView.appendOutput(line, isError));
            }
            @Override
            public void onError(String message) {
                LOG.warn("Terminal: {}", message);
                terminalView.setConnectionStatus(TerminalView.ConnectionStatus.ERROR, message);
            }
            @Override
            public void onHistoryReceived(java.util.List<String> history) {
                Platform.runLater(() -> terminalView.setCommandHistory(history));
            }
            @Override
            public void onTabCreated(String tabId, String tabName) {
                LOG.info("Terminal tab created: {} ({})", tabId, tabName);
            }
        });
    }

    public BorderPane getView() {
        return root;
    }

    /**
     * Configures service URLs (call before connect).
     */
    public void setSyncServerWsUri(String uri) { this.syncServerWsUri = uri != null ? uri : ""; }
    public void setLspBridgeWsUri(String uri) { this.lspBridgeWsUri = uri != null ? uri : ""; }
    public void setTerminalWsUri(String uri) { this.terminalWsUri = uri != null ? uri : ""; }
    public void setSignalingWsUri(String uri) { this.signalingWsUri = uri != null ? uri : ""; }

    /**
     * Connects WebSockets to services (async).
     */
    public void connectServices() {
        CompletableFuture.runAsync(() -> syncClient.connect(syncServerWsUri));
        CompletableFuture.runAsync(() -> lspClient.connect(lspBridgeWsUri, "java", null));
        terminalView.setConnectionStatus(TerminalView.ConnectionStatus.CONNECTING, null);
        CompletableFuture.runAsync(() -> terminalClient.connect(terminalWsUri));
    }

    /**
     * Loads the project root (tree).
     */
    public void loadProjectRoot(Path rootPath) {
        projectTreeView.loadRoot(rootPath);
    }

    public void shutdown() {
        collaborationService.disconnect();
        syncClient.shutdown();
        lspClient.disconnect();
        terminalClient.disconnect();
        webrtcCallService.disconnect();
    }

    private void setupCollaboration() {
        collaborationService.setListener(new CollaborationService.Listener() {
            @Override
            public void onConnected(String clientId, String role) {
                collaborationPanel.setConnected(clientId, role, collaborationService.getServerToken());
                LOG.info("Collaboration connected: {} (role={})", clientId, role);
                // Reconnect SyncClientService to the collaboration server with auth
                reconnectSyncToCollaboration();
            }
            @Override
            public void onDisconnected(String reason) {
                collaborationPanel.setDisconnected();
                LOG.info("Collaboration disconnected: {}", reason);
            }
            @Override
            public void onClientListUpdated(List<SyncMessage.ClientInfo> clients) {
                collaborationPanel.updateClientList(clients);
            }
            @Override
            public void onKicked(String reason) {
                collaborationPanel.setDisconnected();
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Kicked: " + reason, ButtonType.OK);
                    alert.setHeaderText("Disconnected from server");
                    alert.showAndWait();
                });
            }
            @Override
            public void onServerStopping(String reason) {
                collaborationPanel.setDisconnected();
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, reason, ButtonType.OK);
                    alert.setHeaderText("Server stopped");
                    alert.showAndWait();
                });
            }
            @Override
            public void onError(String message) {
                LOG.warn("Collaboration error: {}", message);
            }
        });

        collaborationPanel.setOnKickClient(targetId -> collaborationService.kickClient(targetId, null));
        collaborationPanel.setOnShutdownServer(() -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Arrêter le serveur ?", ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> collaborationService.shutdownServer());
        });
        collaborationPanel.setOnCallToggle(() -> {
            webrtcCallService.connect(signalingWsUri);
            String roomId = "collab-" + (collaborationService.getServerToken() != null ? collaborationService.getServerToken() : "default");
            webrtcCallService.joinRoom(roomId, collaborationService.getMyUsername());
            LOG.info("WebRTC call joined room {}", roomId);
        });
        collaborationPanel.setOnDisconnect(this::disconnectCollaboration);
    }

    private void showHostJoinDialog() {
        HostJoinDialog.showAndGet().ifPresent(result -> {
            if (result.mode == HostJoinDialog.Mode.HOST) {
                collabWsUri = "ws://localhost:" + result.port;
                collaborationService.hostServer(result.port, result.username);
            } else {
                collabWsUri = "ws://" + result.host + ":" + result.port;
                collaborationService.joinServer(result.host, result.port, result.token, result.username);
            }
        });
    }

    private void disconnectCollaboration() {
        collaborationService.disconnect();
        collaborationPanel.setDisconnected();
        // Reconnect SyncClientService to default
        syncClient.setAuth(null, null);
        syncClient.disconnect();
        CompletableFuture.runAsync(() -> syncClient.connect(syncServerWsUri));
    }

    private volatile String collabWsUri;

    private void reconnectSyncToCollaboration() {
        if (collabWsUri != null) {
            syncClient.setAuth(collaborationService.getServerToken(), collaborationService.getMyUsername());
            syncClient.disconnect();
            CompletableFuture.runAsync(() -> syncClient.connect(collabWsUri));
        }
    }

    private void openFile(Path path) {
        try {
            String content = fileService.readFile(path);
            String pathStr = path.toString();
            editorTabPaneView.openFile(path, content);
            syncClient.joinDocument(pathStr);
            currentCollaborators.clear();
            collaboratorsView.clearCollaborators();
        } catch (Exception e) {
            LOG.warn("Open file failed: {}", path, e);
        }
    }

    private void onEditorContentChanged(String pathStr) {
        if (pathStr == null) return;
        String newContent = editorTabPaneView.getContent(pathStr);
        if (newContent == null) return;
        String oldContent = syncClient.getDocumentText(pathStr);
        if (oldContent == null) oldContent = "";
        int i = 0;
        while (i < oldContent.length() && i < newContent.length() && oldContent.charAt(i) == newContent.charAt(i)) i++;
        if (i < oldContent.length() && i == newContent.length()) {
            var op = syncClient.deleteAndCreateOperation(pathStr, i);
            if (op != null) syncClient.sendOperation(pathStr, op);
        } else if (i == oldContent.length() && i < newContent.length()) {
            var op = syncClient.insertAndCreateOperation(pathStr, i, newContent.charAt(i));
            if (op != null) syncClient.sendOperation(pathStr, op);
        } else if (i < oldContent.length() && i < newContent.length()) {
            var del = syncClient.deleteAndCreateOperation(pathStr, i);
            if (del != null) syncClient.sendOperation(pathStr, del);
        }
    }

    private void goToLine(String uri, int line) {
        editorTabPaneView.getView().requestFocus();
    }
}
