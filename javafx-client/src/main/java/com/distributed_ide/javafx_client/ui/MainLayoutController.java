package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.javafx_client.config.UIConfiguration;
import com.distributed_ide.javafx_client.editor.CodeExecutionService;
import com.distributed_ide.javafx_client.editor.NavigationHistory;
import com.distributed_ide.javafx_client.editor.SyntaxErrorChecker;
import com.distributed_ide.javafx_client.execution.ProjectExecutionManager;
import com.distributed_ide.javafx_client.git.GitPanelView;
import com.distributed_ide.javafx_client.git.GitService;
import com.distributed_ide.javafx_client.lsp.LSPManager;
import com.distributed_ide.javafx_client.model.DiagnosticItem;
import com.distributed_ide.javafx_client.model.ProjectCreationResult;
import com.distributed_ide.javafx_client.model.RemoteCursor;
import com.distributed_ide.javafx_client.performance.PerformanceOptimizer;
import com.distributed_ide.javafx_client.service.*;
import com.distributed_ide.javafx_client.theme.ThemeManager;
import com.distributed_ide.javafx_client.ui.animations.AnimationUtils;
import com.distributed_ide.javafx_client.ui.notifications.NotificationManager;
import com.distributed_ide.javafx_client.ui.accessibility.AccessibilityManager;
import com.distributed_ide.javafx_client.webrtc.WebRtcCallService;
import com.distributed_ide.sync.model.SyncMessage;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/**
 * Main IDE controller.
 * Connects backend services to layout views.
 * No JavaFX node construction here — pure logic.
 */
public class MainLayoutController {

    private static final Logger LOG = LoggerFactory.getLogger(MainLayoutController.class);

    // --- Services ---
    private final FileService fileService = new FileService();
    private final SyncClientService syncClient = new SyncClientService();
    private final LspClientService lspClient = new LspClientService();
    private final TerminalClientService terminalClient = new TerminalClientService();
    private final WebRtcCallService webrtcCallService = new WebRtcCallService();
    private final CollaborationService collaborationService = new CollaborationService();
    private final LSPManager lspManager = new LSPManager();
    private final GitService gitService = new GitService();
    private final LocalTerminalService localTerminal = new LocalTerminalService();
    private final CodeExecutionService codeExecutionService = new CodeExecutionService();
    private final ProjectTemplateService projectTemplateService = new ProjectTemplateService();
    private final FileWatcherService fileWatcher = new FileWatcherService();
    private final SettingsManager settingsManager = new SettingsManager();
    private final com.distributed_ide.javafx_client.service.CustomThemeManager customThemeManager = new com.distributed_ide.javafx_client.service.CustomThemeManager();
    private final WorkspaceManager workspaceManager = new WorkspaceManager();
    private final ProjectExecutionManager projectExecutionManager = new ProjectExecutionManager();

    // --- UX/UI Enhancement Services ---
    private final SessionManager sessionManager = new SessionManager();
    private final UIConfiguration uiConfiguration = new UIConfiguration();
    private NotificationManager notificationManager;

    // --- New UI components ---
    private final QuickOpenDialog quickOpenDialog = new QuickOpenDialog();
    private final CommandPaletteDialog commandPalette = new CommandPaletteDialog();
    private final NavigationHistory navigationHistory = new NavigationHistory();

    // --- Project root ---
    private volatile Path projectRoot;

    // --- Recent files/folders ---
    private final LinkedList<String> recentFiles = new LinkedList<>();
    private final LinkedList<String> recentFolders = new LinkedList<>();
    private static final int MAX_RECENT = 10;
    private final Preferences prefs = Preferences.userNodeForPackage(MainLayoutController.class);

    // --- Auto-save ---
    private volatile boolean autoSaveEnabled = false;
    private javafx.animation.Timeline autoSaveTimeline;

    // --- Zoom ---
    private double currentZoom = 1.0;

    // --- Debounce for LSP didChange & find/replace ---
    private static final long LSP_DEBOUNCE_MS = 300;
    private static final long FIND_REPLACE_DEBOUNCE_MS = 250;
    private static final long MEMORY_CHECK_INTERVAL_MS = 5000;  // Check every 5 seconds
    
    private final ScheduledExecutorService debounceScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "lsp-debounce");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, ScheduledFuture<?>> pendingDidChange = new ConcurrentHashMap<>();
    
    // Debounce for find/replace operations to reduce performance impact of large search spaces
    private final Map<String, ScheduledFuture<?>> pendingFindOperation = new ConcurrentHashMap<>();
    
    // Memory monitoring task
    private ScheduledFuture<?> memoryMonitoringTask;

    // --- Config ---
    private volatile String syncServerWsUri = "ws://localhost:8765";
    private volatile String lspBridgeWsUri = "ws://localhost:8081/lsp";
    private volatile String terminalWsUri = "ws://localhost:8766";
    private volatile String signalingWsUri = "ws://localhost:8082";
    private volatile String collabWsUri;

    // --- State ---
    private final List<RemoteCursor> currentCollaborators = Collections.synchronizedList(new ArrayList<>());

    // --- Theme ---
    private ThemeManager themeManager;

    // --- Layout reference ---
    private final MainLayout layout;

    public MainLayoutController(MainLayout layout) {
        this.layout = layout;
        initialize();
    }

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
        themeManager.setListener((themeId, displayName) -> {
            Platform.runLater(() -> layout.getStatusBarView().setNotification("Theme: " + displayName));
        });
    }

    public ThemeManager getThemeManager() {
        return themeManager;
    }

    private void initialize() {
        loadRecentItems();
        wireMenuActions();
        wireKeyboardShortcuts();
        wireSyncClient();
        wireLspClient();
        wireTerminalClient();
        wireLocalTerminal();
        wireCollaboration();
        wireEditorCallbacks();
        wireGitPanel();
        wireNewFeatures();
        wireUIEnhancements();  // ✅ Initialize UI/UX enhancements
        connectServices();
        startMemoryMonitoring();  // Performance optimization: monitor memory usage
        registerServices();  // ✅ Register services with shutdown registry
    }

    // ═══════════════════════════════════════════════════════════
    //  UI/UX ENHANCEMENTS
    // ═══════════════════════════════════════════════════════════
    private void wireUIEnhancements() {
        try {
            // Create notification container and add to main layout
            VBox notificationContainer = new VBox();
            notificationContainer.setStyle("-fx-spacing: 5;");
            notificationContainer.setPrefWidth(400);
            
            // Initialize NotificationManager with container
            notificationManager = new NotificationManager(notificationContainer);
            
            // Load UI configuration
            UIConfiguration.UISettings settings = uiConfiguration.getSettings();
            LOG.info("UI Configuration loaded: fontSize={}, theme={}, autoSave={}",
                    settings.fontSize, settings.theme, settings.autoSave);
            
            // Restore session if enabled
            if (settings.sessionRestore) {
                try {
                    sessionManager.loadSession();
                    LOG.info("Session restored from previous IDE session");
                } catch (Exception e) {
                    LOG.debug("No previous session to restore", e);
                }
            }
            
            LOG.debug("UI/UX enhancements initialized successfully");
        } catch (Exception e) {
            LOG.error("Failed to initialize UI/UX enhancements", e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MENU ACTIONS
    // ═══════════════════════════════════════════════════════════
    private void wireMenuActions() {
        // ── File menu ─────────────────────────────────────────────
        findMenuItem(layout.getFileMenu(), "menu-new-file").setOnAction(e -> handleNewFile());
        findMenuItem(layout.getFileMenu(), "menu-new-folder").setOnAction(e -> handleNewFolder());
        findMenuItem(layout.getFileMenu(), "menu-open-file").setOnAction(e -> handleOpenFile());
        findMenuItem(layout.getFileMenu(), "menu-open-folder").setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Open Folder");
            File dir = chooser.showDialog(null);
            if (dir != null) {
                loadProjectRoot(dir.toPath());
                addRecentFolder(dir.getAbsolutePath());
            }
        });
        findMenuItem(layout.getFileMenu(), "menu-save").setOnAction(e -> saveCurrentFile());
        findMenuItem(layout.getFileMenu(), "menu-save-as").setOnAction(e -> handleSaveAs());
        findMenuItem(layout.getFileMenu(), "menu-save-all").setOnAction(e -> saveAllOpenFiles());

        MenuItem autoSaveItem = findMenuItem(layout.getFileMenu(), "menu-auto-save");
        if (autoSaveItem instanceof CheckMenuItem cmi) {
            cmi.setSelected(autoSaveEnabled);
            cmi.setOnAction(e -> toggleAutoSave(cmi.isSelected()));
        }

        findMenuItem(layout.getFileMenu(), "menu-close-tab").setOnAction(e -> {
            String path = layout.getEditorManager().getCurrentPath();
            if (path != null) layout.getEditorManager().closeTab(path);
        });
        findMenuItem(layout.getFileMenu(), "menu-exit").setOnAction(e -> {
            shutdown();
            Platform.exit();
        });

        // ── Edit menu ─────────────────────────────────────────────
        findMenuItem(layout.getEditMenu(), "menu-undo").setOnAction(e -> layout.getEditorManager().undo());
        findMenuItem(layout.getEditMenu(), "menu-redo").setOnAction(e -> layout.getEditorManager().redo());
        findMenuItem(layout.getEditMenu(), "menu-find").setOnAction(e -> handleFind());
        findMenuItem(layout.getEditMenu(), "menu-replace").setOnAction(e -> handleReplace());

        // ── View menu ─────────────────────────────────────────────
        findMenuItem(layout.getViewMenu(), "menu-view-sidebar").setOnAction(e -> toggleSidebar());
        findMenuItem(layout.getViewMenu(), "menu-view-terminal").setOnAction(e -> toggleBottomPanel());
        findMenuItem(layout.getViewMenu(), "menu-view-explorer").setOnAction(e -> toggleSidebar());
        findMenuItem(layout.getViewMenu(), "menu-view-problems").setOnAction(e -> layout.showBottomTab(1));
        findMenuItem(layout.getViewMenu(), "menu-view-output").setOnAction(e -> layout.showBottomTab(2));
        findMenuItem(layout.getViewMenu(), "menu-view-git").setOnAction(e -> layout.showBottomTab(3));
        findMenuItem(layout.getViewMenu(), "menu-view-zoom-in").setOnAction(e -> zoomIn());
        findMenuItem(layout.getViewMenu(), "menu-view-zoom-out").setOnAction(e -> zoomOut());
        findMenuItem(layout.getViewMenu(), "menu-view-collab-panel").setOnAction(e -> {
            layout.toggleCollaborationPanel();
            layout.getStatusBarView().setNotification(
                    layout.isCollabPanelVisible() ? "Collaboration panel shown" : "Collaboration panel hidden");
        });

        // ── Project menu ──────────────────────────────────────────
        findMenuItem(layout.getProjectMenu(), "menu-project-new").setOnAction(e -> handleNewProject());
        findMenuItem(layout.getProjectMenu(), "menu-project-open").setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Open Project");
            File dir = chooser.showDialog(null);
            if (dir != null) {
                loadProjectRoot(dir.toPath());
                addRecentFolder(dir.getAbsolutePath());
            }
        });
        findMenuItem(layout.getProjectMenu(), "menu-project-close").setOnAction(e -> {
            layout.getProjectTreeView().loadRoot(null);
            layout.getEditorManager().disposeAll();
            layout.getStatusBarView().setNotification("Project closed");
        });
        findMenuItem(layout.getProjectMenu(), "menu-project-build").setOnAction(e -> handleProjectBuild());
        findMenuItem(layout.getProjectMenu(), "menu-project-run").setOnAction(e -> handleProjectRun());
        findMenuItem(layout.getProjectMenu(), "menu-project-clean").setOnAction(e -> handleProjectClean());

        // ── Run menu ──────────────────────────────────────────────
        findMenuItem(layout.getRunMenu(), "menu-run-code").setOnAction(e -> handleRunCode());
        findMenuItem(layout.getRunMenu(), "menu-stop").setOnAction(e -> handleStopCode());

        // ── Git menu ──────────────────────────────────────────────
        findMenuItem(layout.getGitMenu(), "menu-git-init").setOnAction(e -> handleGitInit());
        findMenuItem(layout.getGitMenu(), "menu-git-commit").setOnAction(e -> handleGitCommit());
        findMenuItem(layout.getGitMenu(), "menu-git-push").setOnAction(e -> handleGitPush());
        findMenuItem(layout.getGitMenu(), "menu-git-pull").setOnAction(e -> handleGitPull());
        findMenuItem(layout.getGitMenu(), "menu-git-clone").setOnAction(e -> handleGitClone());
        // ── Settings > Preferences menu ───────────────────────────
        findMenuItem(layout.getSettingsMenu(), "menu-settings-preferences").setOnAction(e -> showSettingsDialog());
        findMenuItem(layout.getSettingsMenu(), "menu-settings-customize-theme").setOnAction(e -> showThemeCustomizationDialog());

        // ── Settings > Theme menu ─────────────────────────────────
        wireThemeMenuItem("menu-theme-modern", "modern");
        wireThemeMenuItem("menu-theme-dark", "dark");
        wireThemeMenuItem("menu-theme-light", "light");
        wireThemeMenuItem("menu-theme-dracula", "dracula");
        wireThemeMenuItem("menu-theme-nord", "nord");

        // ── Collaboration menu ────────────────────────────────────
        findMenuItem(layout.getCollabMenu(), "menu-collab-host-join").setOnAction(e -> showHostJoinDialog());
        findMenuItem(layout.getCollabMenu(), "menu-collab-disconnect").setOnAction(e -> disconnectCollaboration());
        findMenuItem(layout.getCollabMenu(), "menu-collab-call").setOnAction(e -> {
            TextInputDialog roomDialog = new TextInputDialog("room1");
            roomDialog.setTitle("Audio/Video Call");
            roomDialog.setHeaderText("Room ID");
            roomDialog.showAndWait().ifPresent(roomId -> {
                TextInputDialog userDialog = new TextInputDialog(webrtcCallService.getDefaultUserId());
                userDialog.setTitle("Audio/Video Call");
                userDialog.setHeaderText("Your User ID");
                userDialog.showAndWait().ifPresent(userId -> {
                    webrtcCallService.connect(signalingWsUri);
                    webrtcCallService.joinRoom(roomId, userId);
                    LOG.info("WebRTC: joined room {} as {}", roomId, userId);
                });
            });
        });
        findMenuItem(layout.getCollabMenu(), "menu-collab-leave-call").setOnAction(e -> webrtcCallService.leaveRoom());

        // ── Help menu ─────────────────────────────────────────────
        findMenuItem(layout.getHelpMenu(), "menu-about").setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("About");
            alert.setHeaderText("Distributed IDE v2.0.0");
            alert.setContentText("Modern collaborative IDE with CRDT sync, LSP, Git, Terminal.\n"
                    + "Built with JavaFX 21.\n\n"
                    + "Features: Multi-tab editor, syntax highlighting, plugin system,\n"
                    + "project templates, integrated terminal, Git integration.");
            alert.showAndWait();
        });
        findMenuItem(layout.getHelpMenu(), "menu-shortcuts").setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Keyboard Shortcuts");
            alert.setHeaderText("Shortcuts");
            alert.setContentText(
                    "FILE & EDIT\n"
                    + "Ctrl+N — New File\n"
                    + "Ctrl+O — Open File\n"
                    + "Ctrl+S — Save\n"
                    + "Ctrl+Shift+S — Save All\n"
                    + "Ctrl+W — Close Tab\n"
                    + "Ctrl+Z — Undo\n"
                    + "Ctrl+Y — Redo\n"
                    + "Ctrl+/ — Toggle Comment\n"
                    + "\n"
                    + "NAVIGATION & VIEW\n"
                    + "Ctrl+P — Quick Open\n"
                    + "Ctrl+Shift+P — Command Palette\n"
                    + "Ctrl+Tab — Next Tab\n"
                    + "Ctrl+Shift+Tab — Previous Tab\n"
                    + "Ctrl+` — Toggle Terminal\n"
                    + "\n"
                    + "SEARCH & FIND\n"
                    + "Ctrl+F — Find\n"
                    + "Ctrl+H — Replace\n"
                    + "Ctrl+Shift+F — Find in Files\n"
                    + "\n"
                    + "OTHER\n"
                    + "Ctrl+= — Zoom In\n"
                    + "Ctrl+- — Zoom Out\n"
                    + "Alt+Left — Navigate Back\n"
                    + "Alt+Right — Navigate Forward");
            alert.showAndWait();
        });

        // Update recent menus
        refreshRecentMenus();
    }

    // ═══════════════════════════════════════════════════════════
    //  SYNC CLIENT
    // ═══════════════════════════════════════════════════════════
    // Colors assigned to remote collaborators
    private static final String[] COLLAB_COLORS = {
            "#00bcd4", "#ff9800", "#e91e63", "#4caf50", "#9c27b0", "#ff5722", "#3f51b5", "#009688"
    };
    private int nextColorIndex = 0;

    private String assignColor(String clientId) {
        // Deterministic by clientId hash
        int idx = Math.abs(clientId.hashCode()) % COLLAB_COLORS.length;
        return COLLAB_COLORS[idx];
    }

    private void wireSyncClient() {
        syncClient.setListener(new SyncClientService.Listener() {
            @Override
            public void onConnected(String cid, String nid) {
                LOG.info("Sync connected: {} / {}", cid, nid);
                layout.getOutputView().appendOutput("Sync", "Connected: " + cid);
                Platform.runLater(() -> {
                    layout.getStatusBarView().setNotification("Sync: connected");
                    layout.getStatusBarView().setConnectionState(StatusBarView.ConnectionState.CONNECTED);
                    layout.getStatusBarView().setCollabStatus("Connected");
                });
            }
            @Override
            public void onDocumentUpdated(String documentId, String fullText) {
                Platform.runLater(() -> layout.getEditorManager().setContent(documentId, fullText));
            }
            @Override
            public void onCollaboratorJoined(String documentId, String cid, String nid) {
                Platform.runLater(() -> {
                    RemoteCursor rc = new RemoteCursor(cid, nid);
                    rc.setColor(assignColor(cid));
                    rc.setDocumentId(documentId);
                    synchronized (currentCollaborators) {
                        currentCollaborators.add(rc);
                        layout.getCollaboratorsView().setCollaboratorsForDocument(new ArrayList<>(currentCollaborators));
                    }
                    layout.getChatView().addSystemMessage(cid + " joined");
                });
            }
            @Override
            public void onRemoteCursorUpdate(String documentId, String clientId, String username,
                                             int line, int column, String color) {
                Platform.runLater(() -> {
                    RemoteCursor rc = findOrCreateCursor(clientId);
                    rc.setUsername(username);
                    rc.setLine(line);
                    rc.setColumn(column);
                    rc.setDocumentId(documentId);
                    if (color != null && !color.isEmpty()) rc.setColor(color);
                    rc.clearSelection();
                    // Update editor if this cursor is on the active document
                    layout.getEditorManager().getActiveEditor().ifPresent(editor -> {
                        if (documentId != null && documentId.equals(editor.getFilePath())) {
                            editor.updateRemoteCursor(rc);
                        }
                    });
                    layout.getCollaboratorsView().setCollaboratorsForDocument(new ArrayList<>(currentCollaborators));
                });
            }
            @Override
            public void onRemoteSelectionUpdate(String documentId, String clientId, String username,
                                                int startLine, int startCol, int endLine, int endCol) {
                Platform.runLater(() -> {
                    RemoteCursor rc = findOrCreateCursor(clientId);
                    rc.setUsername(username);
                    rc.setDocumentId(documentId);
                    rc.setSelStartLine(startLine);
                    rc.setSelStartCol(startCol);
                    rc.setSelEndLine(endLine);
                    rc.setSelEndCol(endCol);
                    layout.getEditorManager().getActiveEditor().ifPresent(editor -> {
                        if (documentId != null && documentId.equals(editor.getFilePath())) {
                            editor.updateRemoteCursor(rc);
                        }
                    });
                });
            }
            @Override
            public void onChatMessage(String clientId, String username, String message, long timestamp) {
                boolean isOwn = clientId != null && clientId.equals(syncClient.getClientId());
                String name = (username != null && !username.isEmpty()) ? username : clientId;
                Platform.runLater(() -> layout.getChatView().addMessage(name, message, timestamp, isOwn));
            }
            @Override
            public void onError(String message) {
                LOG.warn("Sync: {}", message);
                layout.getOutputView().appendOutput("Sync", "ERROR: " + message);
                Platform.runLater(() ->
                    layout.getStatusBarView().setConnectionState(StatusBarView.ConnectionState.ERROR));
            }
        });

        // Wire chat send
        layout.getChatView().setOnSendMessage(msg -> syncClient.sendChatMessage(msg));
    }

    private RemoteCursor findOrCreateCursor(String clientId) {
        synchronized (currentCollaborators) {
            for (RemoteCursor rc : currentCollaborators) {
                if (clientId.equals(rc.getClientId())) return rc;
            }
            RemoteCursor rc = new RemoteCursor(clientId, clientId, assignColor(clientId));
            currentCollaborators.add(rc);
            return rc;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LSP CLIENT (WebSocket bridge — legacy)
    // ═══════════════════════════════════════════════════════════
    private void wireLspClient() {
        LOG.info("[MainLayoutController] Wiring LSP clients...");
        lspClient.setListener(new LspClientService.Listener() {
            @Override
            public void onDiagnostics(String uri, List<DiagnosticItem> diagnostics) {
                LOG.info("[MainLayoutController LspClient] onDiagnostics: uri={}, count={}", uri, diagnostics.size());
                Platform.runLater(() -> {
                    LOG.info("[MainLayoutController LspClient] Calling DiagnosticsView.setDiagnostics...");
                    layout.getDiagnosticsView().setDiagnostics(uri, diagnostics);
                });
            }
            @Override
            public void onMessage(String msg) {
                layout.getOutputView().appendOutput("LSP", msg);
            }
            @Override
            public void onError(String message) {
                LOG.warn("LSP: {}", message);
                layout.getOutputView().appendOutput("LSP", "ERROR: " + message);
            }
        });

        // LSPManager (local process) — diagnostics + log
        lspManager.setDiagnosticsListener((uri, diagnostics) -> {
            LOG.info("[MainLayoutController LSPManager] onDiagnostics: uri={}, count={}", uri, diagnostics.size());
            Platform.runLater(() -> {
                LOG.info("[MainLayoutController LSPManager] Calling DiagnosticsView.setDiagnostics...");
                layout.getDiagnosticsView().setDiagnostics(uri, diagnostics);
            });
        });
        lspManager.setLogListener((source, msg) ->
                Platform.runLater(() -> layout.getOutputView().appendOutput(source, msg))
        );
        LOG.info("[MainLayoutController] LSP clients wired successfully");
    }

    // ═══════════════════════════════════════════════════════════
    //  TERMINAL CLIENT
    // ═══════════════════════════════════════════════════════════
    private void wireTerminalClient() {
        TerminalView tv = layout.getTerminalView();
        tv.setOnCommand(cmd -> terminalClient.sendInput(cmd));
        tv.setOnReconnect(() -> terminalClient.connect(terminalWsUri));

        terminalClient.setListener(new TerminalClientService.Listener() {
            @Override
            public void onConnected() {
                Platform.runLater(() -> tv.setConnectionStatus(TerminalView.ConnectionStatus.CONNECTED, null));
            }
            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> tv.setConnectionStatus(TerminalView.ConnectionStatus.DISCONNECTED, reason));
            }
            @Override
            public void onReconnecting(int attempt) {
                Platform.runLater(() -> tv.setConnectionStatus(TerminalView.ConnectionStatus.RECONNECTING, String.valueOf(attempt)));
            }
            @Override
            public void onSessionCreated(String sessionId) {
                LOG.info("Terminal session created: {}", sessionId);
            }
            @Override
            public void onOutput(String line, boolean isError) {
                Platform.runLater(() -> tv.appendOutput(line, isError));
            }
            @Override
            public void onError(String message) {
                Platform.runLater(() -> tv.setConnectionStatus(TerminalView.ConnectionStatus.ERROR, message));
            }
            @Override
            public void onHistoryReceived(java.util.List<String> history) {
                Platform.runLater(() -> tv.setCommandHistory(history));
            }
            @Override
            public void onTabCreated(String tabId, String tabName) {
                LOG.info("Terminal tab created: {} ({})", tabId, tabName);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  COLLABORATION
    // ═══════════════════════════════════════════════════════════
    private void wireCollaboration() {
        CollaborationPanel cp = layout.getCollaborationPanel();
        StatusBarView sb = layout.getStatusBarView();

        collaborationService.setListener(new CollaborationService.Listener() {
            @Override
            public void onConnected(String clientId, String role) {
                cp.setConnected(clientId, role, collaborationService.getServerToken());
                Platform.runLater(() -> sb.setCollabStatus("● " + role + " — " + clientId));
                LOG.info("Collaboration connected: {} (role={})", clientId, role);
                layout.getOutputView().appendOutput("Sync", "Collaboration connected as " + role);
                reconnectSyncToCollaboration();
            }
            @Override
            public void onDisconnected(String reason) {
                cp.setDisconnected();
                Platform.runLater(() -> sb.setCollabStatus("Disconnected"));
                LOG.info("Collaboration disconnected: {}", reason);
            }
            @Override
            public void onClientListUpdated(List<SyncMessage.ClientInfo> clients) {
                cp.updateClientList(clients);
            }
            @Override
            public void onKicked(String reason) {
                cp.setDisconnected();
                Platform.runLater(() -> {
                    sb.setCollabStatus("Kicked");
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Kicked: " + reason, ButtonType.OK);
                    alert.setHeaderText("Disconnected from server");
                    alert.showAndWait();
                });
            }
            @Override
            public void onServerStopping(String reason) {
                cp.setDisconnected();
                Platform.runLater(() -> {
                    sb.setCollabStatus("Server stopped");
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, reason, ButtonType.OK);
                    alert.setHeaderText("Server stopped");
                    alert.showAndWait();
                });
            }
            @Override
            public void onError(String message) {
                LOG.warn("Collaboration error: {}", message);
                layout.getOutputView().appendOutput("Sync", "Collab ERROR: " + message);
            }
        });

        cp.setOnKickClient(targetId -> collaborationService.kickClient(targetId, null));
        cp.setOnShutdownServer(() -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Stop the server?", ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> collaborationService.shutdownServer());
        });
        cp.setOnCallToggle(() -> {
            webrtcCallService.connect(signalingWsUri);
            String roomId = "collab-" + (collaborationService.getServerToken() != null ? collaborationService.getServerToken() : "default");
            webrtcCallService.joinRoom(roomId, collaborationService.getMyUsername());
            LOG.info("WebRTC call joined room {}", roomId);
        });
        cp.setOnDisconnect(this::disconnectCollaboration);
        cp.setOnShareProject(() -> {
            Path root = layout.getProjectTreeView().getRootPath();
            if (root == null) {
                showError("Share", "Open a project first");
                return;
            }
            if (!collaborationService.isConnected()) {
                showHostJoinDialog();
            }
            // Share all open files via sync
            layout.getEditorManager().getOpenPaths().forEach(pathStr -> {
                String content = layout.getEditorManager().getContent(pathStr);
                if (content != null) {
                    syncClient.joinDocument(pathStr);
                    syncClient.setDocumentText(pathStr, content);
                }
            });
            layout.getStatusBarView().setNotification("Project shared with collaborators");
            LOG.info("Project shared: {}", root);
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  EDITOR CALLBACKS
    // ═══════════════════════════════════════════════════════════
    private void wireEditorCallbacks() {
        // Load saved editor settings at startup (font size, font family, tab size, etc.)
        layout.getEditorManager().loadStoredSettings(settingsManager);
        
        layout.getProjectTreeView().setOnFileOpen(this::openFile);
        layout.getEditorManager().setOnContentChanged(this::onEditorContentChanged);
        layout.getEditorManager().setOnCaretChanged(pos -> {
            Platform.runLater(() -> layout.getStatusBarView().setCursorPosition(pos[0], pos[1]));
            // Broadcast cursor position to collaborators
            String docId = layout.getEditorManager().getCurrentPath();
            if (docId != null && syncClient.isConnected()) {
                syncClient.sendCursorUpdate(docId, pos[0], pos[1], null);
            }
        });
        layout.getEditorManager().setOnActiveFileChanged(path -> {
            String lang = com.distributed_ide.javafx_client.editor.EditorManager.detectLanguage(path);
            Platform.runLater(() -> layout.getStatusBarView().setLanguage(lang));
        });
        layout.getDiagnosticsView().setOnGoToLine(this::goToLine);
    }

    // ═══════════════════════════════════════════════════════════
    //  GIT PANEL
    // ═══════════════════════════════════════════════════════════
    private void wireGitPanel() {
        GitPanelView gp = layout.getGitPanelView();

        gp.setOnRefresh(this::refreshGitPanel);

        gp.setOnStageAll(() -> {
            gitService.addAll().thenRun(this::refreshGitPanel)
                    .exceptionally(ex -> {
                        Platform.runLater(() -> gp.setStatus("Stage failed: " + ex.getMessage()));
                        return null;
                    });
        });

        gp.setOnCommit((message, unused) -> {
            gitService.commit(message, null, null).thenAccept(info -> {
                Platform.runLater(() -> gp.setStatus("Committed: " + info.shortHash() + " — " + info.message()));
                refreshGitPanel();
            }).exceptionally(ex -> {
                Platform.runLater(() -> gp.setStatus("Commit failed: " + ex.getMessage()));
                return null;
            });
        });

        gp.setOnPull(() -> {
            Platform.runLater(() -> gp.setStatus("Pulling..."));
            gitService.pull().thenAccept(msg -> {
                Platform.runLater(() -> gp.setStatus(msg));
                refreshGitPanel();
            }).exceptionally(ex -> {
                Platform.runLater(() -> gp.setStatus("Pull failed: " + ex.getMessage()));
                return null;
            });
        });

        gp.setOnPush(() -> {
            Platform.runLater(() -> gp.setStatus("Pushing..."));
            gitService.push().thenAccept(msg -> {
                Platform.runLater(() -> gp.setStatus(msg));
                refreshGitPanel();
            }).exceptionally(ex -> {
                Platform.runLater(() -> gp.setStatus("Push failed: " + ex.getMessage()));
                return null;
            });
        });

        gp.setOnCheckoutBranch(branch -> {
            gitService.checkout(branch).thenRun(this::refreshGitPanel)
                    .exceptionally(ex -> {
                        Platform.runLater(() -> gp.setStatus("Checkout failed: " + ex.getMessage()));
                        return null;
                    });
        });

        gp.setOnFileSelected(fileStatus -> {
            gitService.diffFile(fileStatus.path()).thenAccept(diff ->
                    Platform.runLater(() -> gp.setDiffContent(diff))
            );
        });

        gp.setNoRepo();
    }

    private void refreshGitPanel() {
        if (!gitService.isOpen()) return;
        GitPanelView gp = layout.getGitPanelView();

        gitService.status().thenAccept(changes ->
                Platform.runLater(() -> gp.setChanges(changes))
        );

        gitService.currentBranch().thenAccept(branch ->
                gitService.branches().thenAccept(branches ->
                        Platform.runLater(() -> gp.setBranches(branches, branch))
                )
        );

        gitService.log(50).thenAccept(commits ->
                Platform.runLater(() -> gp.setHistory(commits))
        );
    }

    // ═══════════════════════════════════════════════════════════
    //  SERVICE LIFECYCLE
    // ═══════════════════════════════════════════════════════════
    public void connectServices() {
        layout.getStatusBarView().setConnectionState(StatusBarView.ConnectionState.CONNECTING);
        CompletableFuture.runAsync(() -> syncClient.connect(syncServerWsUri));
        CompletableFuture.runAsync(() -> lspClient.connect(lspBridgeWsUri, "java", null));
        Platform.runLater(() -> layout.getTerminalView().setConnectionStatus(TerminalView.ConnectionStatus.CONNECTING, null));
        CompletableFuture.runAsync(() -> terminalClient.connect(terminalWsUri));
    }

    public void shutdown() {
        LOG.info("Shutting down IDE");

        // Save workspace state
        try {
            saveWorkspaceState();
        } catch (Exception e) {
            LOG.warn("Failed to save workspace state", e);
        }

        // Dispose all open editors (unsubscribe highlight listeners, free resources)
        try {
            layout.getEditorManager().disposeAll();
        } catch (Exception e) {
            LOG.warn("Error disposing editors", e);
        }

        // Stop file watcher
        try {
            fileWatcher.stop();
        } catch (Exception e) {
            LOG.warn("Error stopping file watcher", e);
        }
        
        // Close all services
        layout.getStatusBarView().setConnectionState(StatusBarView.ConnectionState.DISCONNECTED);
        try {
            if (syncClient != null) {
                syncClient.shutdown();
            }
            if (lspClient != null) {
                lspClient.disconnect();
            }
            if (lspManager != null) {
                lspManager.shutdown();
            }
            if (terminalClient != null) {
                terminalClient.shutdown();
            }
            if (webrtcCallService != null) {
                webrtcCallService.disconnect();
            }
            if (collaborationService != null) {
                collaborationService.disconnect();
            }
            if (localTerminal != null) {
                localTerminal.shutdown();
            }
            projectExecutionManager.shutdown();
            codeExecutionService.shutdown();
            SyntaxErrorChecker.shutdown();
            if (gitService != null) {
                gitService.shutdown();
            }
            if (autoSaveTimeline != null) {
                autoSaveTimeline.stop();
            }
            
            // Stop memory monitoring
            if (memoryMonitoringTask != null) {
                memoryMonitoringTask.cancel(false);
            }
            
            debounceScheduler.shutdownNow();
            pendingDidChange.clear();
            pendingFindOperation.clear();
        } catch (Exception e) {
            LOG.error("Error during shutdown", e);
        }
        
        // Clear collaborators
        synchronized (currentCollaborators) {
            currentCollaborators.clear();
        }
        
        // Clear icon cache to release Image references
        com.distributed_ide.javafx_client.util.IconManager.clearCache();
        
        // Print performance report
        PerformanceOptimizer.printPerformanceReport();
        
        LOG.info("Shutdown complete");
    }

    // ═══════════════════════════════════════════════════════════
    //  SERVICE REGISTRATION FOR GRACEFUL SHUTDOWN
    // ═══════════════════════════════════════════════════════════
    
    /**
     * Register all services with ServiceShutdownRegistry for LIFO shutdown.
     * Ensures all resources are properly cleaned up in the correct order.
     */
    private void registerServices() {
        ServiceShutdownRegistry shutdownRegistry = ServiceShutdownRegistry.getInstance();
        
        try {
            // Register core services (LIFO order means these shutdown first)
            shutdownRegistry.registerService("SyncClientService", syncClient::shutdown);
            shutdownRegistry.registerService("LspClientService", lspClient::disconnect);
            shutdownRegistry.registerService("LSPManager", lspManager::shutdown);
            shutdownRegistry.registerService("TerminalClientService", terminalClient::shutdown);
            shutdownRegistry.registerService("WebRtcCallService", webrtcCallService::disconnect);
            shutdownRegistry.registerService("CollaborationService", collaborationService::disconnect);
            shutdownRegistry.registerService("LocalTerminalService", localTerminal::shutdown);
            shutdownRegistry.registerService("CodeExecutionService", codeExecutionService::shutdown);
            shutdownRegistry.registerService("ProjectExecutionManager", projectExecutionManager::shutdown);
            shutdownRegistry.registerService("GitService", gitService::shutdown);
            shutdownRegistry.registerService("FileWatcherService", () -> {
                try {
                    fileWatcher.stop();
                } catch (Exception e) {
                    LOG.warn("Error stopping file watcher", e);
                }
            });
            
            LOG.info("✅ All services registered with shutdown registry");
        } catch (Exception e) {
            LOG.error("❌ Error registering services", e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PERFORMANCE MONITORING
    // ═══════════════════════════════════════════════════════════
    
    /**
     * Start periodic memory monitoring to track IDE performance.
     * Logs warnings if memory falls below thresholds.
     */
    private void startMemoryMonitoring() {
        memoryMonitoringTask = debounceScheduler.scheduleAtFixedRate(() -> {
            try {
                long availableMemory = PerformanceOptimizer.getAvailableMemory();
                if (availableMemory < PerformanceOptimizer.MEMORY_CRITICAL_THRESHOLD) {
                    LOG.warn("CRITICAL: Memory available: {} MB", availableMemory / (1024 * 1024));
                    Platform.runLater(() -> layout.getStatusBarView().setNotification(
                            "⚠ Critical memory low: " + (availableMemory / (1024 * 1024)) + " MB available"));
                } else if (availableMemory < PerformanceOptimizer.MEMORY_LOW_THRESHOLD) {
                    LOG.info("Memory low: {} MB available", availableMemory / (1024 * 1024));
                }
            } catch (Exception e) {
                LOG.debug("Error monitoring memory", e);
            }
        }, MEMORY_CHECK_INTERVAL_MS, MEMORY_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    // ═══════════════════════════════════════════════════════════
    //  WORKSPACE SAVE / RESTORE
    // ═══════════════════════════════════════════════════════════
    private void saveWorkspaceState() {
        WorkspaceManager.WorkspaceState state = new WorkspaceManager.WorkspaceState();
        state.projectRoot = projectRoot != null ? projectRoot.toString() : null;
        state.openFiles = new java.util.ArrayList<>(layout.getEditorManager().getOpenPaths());
        state.activeFile = layout.getEditorManager().getCurrentPath();
        state.selectedBottomTab = layout.getBottomTabPane().getSelectionModel().getSelectedIndex();
        
        // Save caret and scroll positions for each open file
        for (String filePath : state.openFiles) {
            var editor = layout.getEditorManager().getEditor(filePath);
            if (editor != null) {
                // Save caret position
                state.caretPositions.put(filePath, 
                    new WorkspaceManager.CaretPosition(editor.getCaretLine(), editor.getCaretColumn()));
                
                // Save scroll position (try-catch for safety)
                try {
                    var codeArea = editor.getCodeArea();
                    var scrollPane = codeArea.getParent();
                    // Note: CodeArea doesn't directly expose scroll position, using 0,0 for now
                    // This is a limitation we can improve later with virtual scroll tracking
                    state.scrollPositions.put(filePath, new WorkspaceManager.ScrollPosition(0.0, 0.0));
                } catch (Exception ignored) {}
            }
        }
        
        try {
            state.horizontalDividers = layout.getMainHorizontalSplit().getDividerPositions();
            state.verticalDividers = layout.getCenterVerticalSplit().getDividerPositions();
        } catch (Exception ignored) {}
        if (layout.getView().getScene() != null) {
            state.windowWidth = layout.getView().getScene().getWidth();
            state.windowHeight = layout.getView().getScene().getHeight();
        }
        workspaceManager.save(state);
    }

    public void restoreWorkspaceState() {
        WorkspaceManager.WorkspaceState state = workspaceManager.load();
        if (state == null) return;
        if (state.projectRoot != null) {
            Path root = Paths.get(state.projectRoot);
            if (java.nio.file.Files.isDirectory(root)) {
                loadProjectRoot(root);
                addRecentFolder(state.projectRoot);
            }
        }
        // Restore open files after a brief delay to let the project load
        Platform.runLater(() -> {
            if (state.openFiles != null) {
                for (String filePath : state.openFiles) {
                    Path p = Paths.get(filePath);
                    if (java.nio.file.Files.isRegularFile(p)) {
                        openFile(p);
                        
                        // Restore caret position if saved
                        Platform.runLater(() -> {
                            var caretPos = state.caretPositions.get(filePath);
                            if (caretPos != null && caretPos.line > 0) {
                                layout.getEditorManager().goToLine(filePath, caretPos.line);
                            }
                        });
                    }
                }
            }
            if (state.activeFile != null) {
                layout.getEditorManager().goToLine(state.activeFile, 1);
            }
            layout.showBottomTab(Math.max(0, state.selectedBottomTab));
            
            // Restore divider positions
            try {
                if (state.horizontalDividers != null && state.horizontalDividers.length > 0) {
                    layout.getMainHorizontalSplit().setDividerPositions(state.horizontalDividers);
                }
                if (state.verticalDividers != null && state.verticalDividers.length > 0) {
                    layout.getCenterVerticalSplit().setDividerPositions(state.verticalDividers);
                }
            } catch (Exception ignored) {}
            
            LOG.info("Workspace restored: {} files, project: {}", 
                state.openFiles != null ? state.openFiles.size() : 0,
                state.projectRoot);
        });
    }

    public SettingsManager getSettingsManager() { return settingsManager; }
    public WorkspaceManager getWorkspaceManager() { return workspaceManager; }

    // ═══════════════════════════════════════════════════════════
    //  CONFIG SETTERS
    // ═══════════════════════════════════════════════════════════
    public void setSyncServerWsUri(String uri) { this.syncServerWsUri = uri != null ? uri : ""; }
    public void setLspBridgeWsUri(String uri) { this.lspBridgeWsUri = uri != null ? uri : ""; }
    public void setTerminalWsUri(String uri) { this.terminalWsUri = uri != null ? uri : ""; }
    public void setSignalingWsUri(String uri) { this.signalingWsUri = uri != null ? uri : ""; }

    // ═══════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════
    public void loadProjectRoot(Path path) {
        this.projectRoot = path;
        CompletableFuture.runAsync(() -> {
            try {
                // Update the UI with project root
                Platform.runLater(() -> {
                    layout.getStatusBarView().setNotification("Project: " + path);
                    // Load project tree regardless of Git presence
                    layout.getProjectTreeView().loadRoot(path);
                    layout.getGlobalSearchView().setProjectRoot(path);
                });

                // Start file watcher
                fileWatcher.watch(path);

                // Set local terminal working directory
                localTerminal.setWorkingDirectory(path);

                // Try to initialize git service for this project (optional)
                gitService.open(path).thenAccept(hasGit -> {
                    if (hasGit) {
                        refreshGitPanel();
                        LOG.info("Git repository detected and loaded");
                    } else {
                        Platform.runLater(() -> layout.getGitPanelView().setNoRepo());
                        LOG.info("No Git repository - Git features disabled");
                    }
                }).exceptionally(ex -> {
                    Platform.runLater(() -> layout.getGitPanelView().setNoRepo());
                    LOG.debug("Git not available: {}", ex.getMessage());
                    return null;
                });
            } catch (Exception e) {
                LOG.error("Failed to load project root", e);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("Failed to load project");
                    alert.setContentText("Could not load project from: " + path + "\nError: " + e.getMessage());
                    alert.showAndWait();
                });
            }
        });
    }

    private void openFile(Path path) {
        // Verify file exists before attempting to open
        if (!java.nio.file.Files.exists(path)) {
            LOG.warn("File does not exist: {}", path);
            Platform.runLater(() ->
                layout.getOutputView().appendOutput("Build", "File not found: " + path));
            return;
        }
        
        if (!java.nio.file.Files.isRegularFile(path)) {
            LOG.warn("Not a file: {}", path);
            Platform.runLater(() ->
                layout.getOutputView().appendOutput("Build", "Not a regular file: " + path));
            return;
        }
        
        String pathStr = path.toString();
        // Read file off FX thread for non-blocking IO
        CompletableFuture.supplyAsync(() -> {
            try {
                return fileService.readFile(path);
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }).thenAcceptAsync(content -> {
            layout.getEditorManager().openFile(path, content);
            navigationHistory.record(pathStr, 1);
            syncClient.joinDocument(pathStr);
            currentCollaborators.clear();
            layout.getCollaboratorsView().clearCollaborators();

            // Update status bar language
            String lang = com.distributed_ide.javafx_client.editor.EditorManager.detectLanguage(pathStr);
            layout.getStatusBarView().setLanguage(lang);
            layout.getStatusBarView().setCursorPosition(1, 1);

            // Notify LSPManager
            String lspLang = mapToLspLanguageId(lang);
            if (lspLang != null && lspManager.isLanguageSupported(lspLang)) {
                Path projRoot = layout.getProjectTreeView().getRootPath();
                lspManager.openFile(pathStr, lspLang, content, projRoot);
            }

            // Track recent file
            addRecentFile(pathStr);
        }, Platform::runLater).exceptionally(ex -> {
            LOG.warn("Open file failed: {}", path, ex);
            Platform.runLater(() ->
                layout.getOutputView().appendOutput("Build", "Failed to open: " + path + " — " + ex.getMessage()));
            return null;
        });
    }

    private void saveCurrentFile() {
        String pathStr = layout.getEditorManager().getCurrentPath();
        if (pathStr == null) return;
        String content = layout.getEditorManager().getContent(pathStr);
        if (content == null) return;
        
        // Show save in progress
        layout.getStatusBarView().showSaveInProgress(pathStr);
        
        // Write off FX thread to avoid UI freeze on slow disks
        CompletableFuture.runAsync(() -> {
            try {
                fileService.writeFile(java.nio.file.Paths.get(pathStr), content);
                lspManager.didSave(pathStr, content);
                Platform.runLater(() -> {
                    layout.getEditorManager().markClean(pathStr);
                    layout.getStatusBarView().showSaveConfirmation(pathStr);
                    layout.getOutputView().appendOutput("Build", "Saved: " + pathStr);
                });
            } catch (Exception e) {
                LOG.warn("Save failed: {}", pathStr, e);
                Platform.runLater(() -> {
                    layout.getStatusBarView().showSaveError("Save failed: " + e.getMessage());
                    layout.getOutputView().appendOutput("Build", "Save failed: " + e.getMessage());
                });
            }
        });
    }

    private void saveAllOpenFiles() {
        // Snapshot paths and contents on FX thread, then write on background thread
        java.util.Map<String, String> toSave = new java.util.LinkedHashMap<>();
        try {
            for (String pathStr : layout.getEditorManager().getOpenPaths()) {
                String content = layout.getEditorManager().getContent(pathStr);
                if (content != null) {
                    toSave.put(pathStr, content);
                }
            }
        } catch (Exception e) {
            LOG.error("Error collecting open files for save", e);
            return;
        }
        if (toSave.isEmpty()) return;
        
        // Show save in progress
        layout.getStatusBarView().showSaveInProgress("Multiple files");
        
        CompletableFuture.runAsync(() -> {
            int saved = 0;
            for (var entry : toSave.entrySet()) {
                try {
                    fileService.writeFile(java.nio.file.Paths.get(entry.getKey()), entry.getValue());
                    lspManager.didSave(entry.getKey(), entry.getValue());
                    saved++;
                    String key = entry.getKey();
                    Platform.runLater(() -> layout.getEditorManager().markClean(key));
                } catch (Exception e) {
                    LOG.warn("Failed to save: {}", entry.getKey(), e);
                }
            }
            int finalSaved = saved;
            Platform.runLater(() -> {
                layout.getOutputView().appendOutput("Build", finalSaved + " file(s) saved");
                layout.getStatusBarView().showSaveConfirmation("(" + finalSaved + " files)");
            });
        });
    }

    private void onEditorContentChanged(String pathStr) {
        // Debounce LSP didChange: cancel previous pending notification for this file
        ScheduledFuture<?> prev = pendingDidChange.remove(pathStr);
        if (prev != null) prev.cancel(false);
        ScheduledFuture<?> future = debounceScheduler.schedule(() -> {
            pendingDidChange.remove(pathStr);
            String content = layout.getEditorManager().getContent(pathStr);
            if (content != null) {
                lspManager.didChange(pathStr, content);
                // Trigger async syntax error checking
                triggerSyntaxCheck(pathStr, content);
            }
        }, LSP_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        pendingDidChange.put(pathStr, future);
    }

    private void goToLine(String uri, int line) {
        layout.getEditorManager().goToLine(uri, line);
        LOG.info("Go to {}:{}", uri, line);
    }

    private void showSettingsDialog() {
        LOG.info("Opening Settings dialog");
        SettingsDialog dialog = new SettingsDialog(settingsManager);
        // Set callback so Apply button (and OK) trigger real-time settings changes
        dialog.setOnSettingsApplied(() -> {
            LOG.info("Settings applied via callback");
            applySettings();  // Apply settings to main UI in real-time
        });
        dialog.showAndWait().ifPresent(result -> {
            LOG.info("Settings dialog returned: {}", result);
            if (!result) {
                LOG.info("Settings dialog cancelled");
            }
        });
        LOG.info("Settings dialog closed");
    }

    private void applySettings() {
        LOG.info("=== Applying Settings ===");
        try {
            // Apply font settings
            int fontSize = settingsManager.getInt("editor.fontSize", 14);
            String fontFamily = settingsManager.getString("editor.fontFamily", "Consolas");
            LOG.info("Reading from manager - Font: {}pt {}", fontSize, fontFamily);
            
            if (layout.getEditorManager() != null) {
                layout.getEditorManager().setFontSize(fontSize);
                layout.getEditorManager().setFontFamily(fontFamily);
                LOG.info("✓ Font settings applied to all editors");
            } else {
                LOG.warn("EditorManager is null, cannot apply font");
            }

            // Apply editor settings
            int tabSize = settingsManager.getInt("editor.tabSize", 4);
            boolean wordWrap = settingsManager.getBoolean("editor.wordWrap", false);
            boolean minimap = settingsManager.getBoolean("editor.minimap", true);
            LOG.info("Reading from manager - Tab size: {}, Word wrap: {}, Minimap: {}", tabSize, wordWrap, minimap);
            
            if (layout.getEditorManager() != null) {
                layout.getEditorManager().setTabSize(tabSize);
                layout.getEditorManager().setWordWrap(wordWrap);
                layout.getEditorManager().setMinimapVisible(minimap);
                LOG.info("✓ Editor settings applied to all editors");
            } else {
                LOG.warn("EditorManager is null, cannot apply editor settings");
            }

            // Apply theme
            String theme = settingsManager.getString("theme.current", "dark");
            LOG.info("Reading from manager - Theme: {}", theme);
            if (themeManager != null) {
                themeManager.applyTheme(theme);
                layout.getStatusBarView().setNotification("Theme applied: " + theme);
                LOG.info("✓ Theme applied: {}", theme);
            } else {
                LOG.warn("ThemeManager is null, cannot apply theme");
            }

            // Apply window settings
            int windowWidth = settingsManager.getInt("window.width", 1400);
            int windowHeight = settingsManager.getInt("window.height", 900);
            boolean windowMaximized = settingsManager.getBoolean("window.maximized", false);
            LOG.info("Reading from manager - Window: {}x{}, Maximized: {}", windowWidth, windowHeight, windowMaximized);
            
            // Note: Window size changes would need to be applied to the Stage
            // For now, just log it
            if (windowMaximized) {
                LOG.info("Window maximized setting: {}", windowMaximized);
            }

            // Apply auto-save settings
            boolean autoSave = settingsManager.getBoolean("editor.autoSave", false);
            int autoSaveDelay = settingsManager.getInt("editor.autoSaveDelaySeconds", 30);
            LOG.info("Reading from manager - Auto-save: enabled={}, delay={}s", autoSave, autoSaveDelay);
            setAutoSaveSettings(autoSave, autoSaveDelay);
            LOG.info("✓ Auto-save settings applied");

            LOG.info("=== All settings applied successfully ===");
        } catch (Exception e) {
            LOG.error("Error applying settings", e);
        }
    }

    private void setAutoSaveSettings(boolean enabled, int delaySecs) {
        autoSaveEnabled = enabled;
        if (autoSaveEnabled) {
            LOG.info("Auto-save enabled with delay: {}s", delaySecs);
        } else {
            LOG.info("Auto-save disabled");
        }
    }

    private void showThemeCustomizationDialog() {
        ThemeManagementDialog dialog = new ThemeManagementDialog(customThemeManager, themeManager);
        dialog.showAndWait();
        LOG.info("Theme customization dialog closed");
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
        layout.getCollaborationPanel().setDisconnected();
        syncClient.setAuth(null, null);
        syncClient.disconnect();
        layout.getStatusBarView().setConnectionState(StatusBarView.ConnectionState.DISCONNECTED);
        layout.getStatusBarView().setCollabStatus("Disconnected");
        layout.getStatusBarView().setConnectionState(StatusBarView.ConnectionState.CONNECTING);
        CompletableFuture.runAsync(() -> syncClient.connect(syncServerWsUri));
    }

    private void reconnectSyncToCollaboration() {
        if (collabWsUri != null) {
            syncClient.setAuth(collaborationService.getServerToken(), collaborationService.getMyUsername());
            syncClient.disconnect();
            layout.getStatusBarView().setConnectionState(StatusBarView.ConnectionState.CONNECTING);
            CompletableFuture.runAsync(() -> syncClient.connect(collabWsUri));
        }
    }

    private String detectLanguage(String fileName) {
        String lang = com.distributed_ide.javafx_client.editor.EditorManager.detectLanguage(fileName);
        if (lang == null || lang.isEmpty()) return "Text";
        return lang.substring(0, 1).toUpperCase() + lang.substring(1);
    }

    /**
     * Maps a display language name to an LSP languageId.
     * Returns null if the language has no configured LSP server.
     */
    private String mapToLspLanguageId(String displayLang) {
        if (displayLang == null) return null;
        return switch (displayLang) {
            case "Java", "java" -> "java";
            case "Python", "python" -> "python";
            case "TypeScript", "typescript" -> "typescript";
            case "JavaScript", "javascript" -> "javascript";
            default -> null;
        };
    }

    private void wireThemeMenuItem(String menuItemId, String themeId) {
        MenuItem item = findMenuItemDeep(layout.getSettingsMenu(), menuItemId);
        if (item != null) {
            item.setOnAction(e -> {
                if (themeManager != null) {
                    themeManager.applyTheme(themeId);
                }
            });
        }
    }

    private MenuItem findMenuItem(Menu menu, String id) {
        for (MenuItem item : menu.getItems()) {
            if (id.equals(item.getId())) return item;
        }
        // Return a no-op placeholder so we never NPE
        MenuItem placeholder = new MenuItem("?");
        placeholder.setId(id);
        return placeholder;
    }

    private MenuItem findMenuItemDeep(Menu menu, String id) {
        for (MenuItem item : menu.getItems()) {
            if (id.equals(item.getId())) return item;
            if (item instanceof Menu) {
                MenuItem found = findMenuItemDeep((Menu) item, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  NEW FEATURES WIRING
    // ═══════════════════════════════════════════════════════════
    private void wireNewFeatures() {
        // --- Quick Open ---
        quickOpenDialog.setOnFileSelected(path -> openFile(path));
        findMenuItem(layout.getViewMenu(), "menu-view-quick-open").setOnAction(e -> {
            Window w = layout.getView().getScene() != null ? layout.getView().getScene().getWindow() : null;
            if (w != null && projectRoot != null) quickOpenDialog.show(w, projectRoot);
        });

        // --- Command Palette ---
        findMenuItem(layout.getViewMenu(), "menu-view-command-palette").setOnAction(e -> {
            Window w = layout.getView().getScene() != null ? layout.getView().getScene().getWindow() : null;
            if (w != null) commandPalette.show(w);
        });
        registerCommandPaletteCommands();

        // --- Inline Find Bar ---
        InlineFindBar findBar = layout.getInlineFindBar();
        findBar.setOnFind(query -> {
            var editor = layout.getEditorManager().getCurrentEditor();
            if (editor != null && query != null && !query.isEmpty()) {
                editor.findAndHighlight(query);
            }
        });
        findBar.setOnFindNext(query -> {
            var editor = layout.getEditorManager().getCurrentEditor();
            if (editor != null && query != null && !query.isEmpty()) {
                editor.findAndHighlight(query);
            }
        });
        findBar.setOnFindPrev(query -> {
            var editor = layout.getEditorManager().getCurrentEditor();
            if (editor != null && query != null && !query.isEmpty()) {
                editor.findAndHighlight(query);
            }
        });
        findBar.setOnReplace((find, replace) -> {
            var editor = layout.getEditorManager().getCurrentEditor();
            if (editor != null && find != null && !find.isEmpty()) {
                editor.replaceAll(find, replace != null ? replace : "");
            }
        });
        findBar.setOnReplaceAll((find, replace) -> {
            var editor = layout.getEditorManager().getCurrentEditor();
            if (editor != null && find != null && !find.isEmpty()) {
                editor.replaceAll(find, replace != null ? replace : "");
            }
        });

        // --- Global Search ---
        GlobalSearchView gsv = layout.getGlobalSearchView();
        gsv.setOnResultSelected((absPath, line) -> {
            Path p = Paths.get(absPath);
            openFile(p);
            layout.getEditorManager().goToLine(absPath, line);
            navigationHistory.record(absPath, line);
        });
        findMenuItem(layout.getEditMenu(), "menu-find-in-files").setOnAction(e -> {
            if (projectRoot != null) gsv.setProjectRoot(projectRoot);
            layout.showBottomTab(5); // SEARCH tab
            gsv.focusSearch();
        });

        // --- Navigation History ---
        findMenuItem(layout.getViewMenu(), "menu-view-nav-back").setOnAction(e -> navigateBack());
        findMenuItem(layout.getViewMenu(), "menu-view-nav-forward").setOnAction(e -> navigateForward());

        // --- Split Editor ---
        findMenuItem(layout.getViewMenu(), "menu-view-split-vertical").setOnAction(e -> layout.getEditorManager().splitVertical());
        findMenuItem(layout.getViewMenu(), "menu-view-split-horizontal").setOnAction(e -> layout.getEditorManager().splitHorizontal());

        // --- Activity Bar ---
        layout.getActExplorer().setOnAction(e -> {
            if (!layout.isSidebarVisible()) toggleSidebar();
        });
        layout.getActSearch().setOnAction(e -> {
            layout.showBottomTab(5); // SEARCH tab
            layout.getGlobalSearchView().focusSearch();
        });
        layout.getActGit().setOnAction(e -> layout.showBottomTab(3)); // GIT tab
        layout.getActCollab().setOnAction(e -> {
            layout.toggleCollaborationPanel();
            layout.getStatusBarView().setNotification(
                    layout.isCollabPanelVisible() ? "Collaboration panel shown" : "Collaboration panel hidden");
        });

        // --- File Watcher (smart tree refresh + async file reload) ---
        fileWatcher.addListener(change -> {
            switch (change.type()) {
                case CREATED, DELETED -> {
                    // Smart refresh: only update the parent directory, preserve expansion state
                    Path changedPath = change.path();
                    Path parentPath = changedPath.getParent();
                    if (parentPath != null) {
                        layout.getProjectTreeView().refreshPath(parentPath);
                    } else {
                        layout.getProjectTreeView().refresh();
                    }
                }
                case MODIFIED -> {
                    String changedPath = change.path().toString();
                    if (layout.getEditorManager().getOpenPaths().contains(changedPath)
                            && !layout.getEditorManager().isDirty(changedPath)) {
                        // Read changed file off FX thread
                        CompletableFuture.supplyAsync(() -> {
                            try { return fileService.readFile(change.path()); }
                            catch (Exception e) { return null; }
                        }).thenAcceptAsync(newContent -> {
                            if (newContent != null) {
                                layout.getEditorManager().setContent(changedPath, newContent);
                                layout.getStatusBarView().setNotification("Reloaded: " + change.path().getFileName());
                            }
                        }, Platform::runLater);
                    }
                }
            }
        });
    }

    private void navigateBack() {
        NavigationHistory.NavigationEntry entry = navigationHistory.goBack();
        if (entry != null) {
            Path p = Paths.get(entry.filePath);
            if (java.nio.file.Files.isRegularFile(p)) {
                CompletableFuture.supplyAsync(() -> {
                    try { return fileService.readFile(p); }
                    catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
                }).thenAcceptAsync(content -> {
                    layout.getEditorManager().openFile(p, content);
                    layout.getEditorManager().goToLine(entry.filePath, entry.line);
                }, Platform::runLater).exceptionally(ex -> {
                    LOG.warn("Navigate back failed: {}", entry.filePath, ex);
                    return null;
                });
            }
        }
    }

    private void navigateForward() {
        NavigationHistory.NavigationEntry entry = navigationHistory.goForward();
        if (entry != null) {
            Path p = Paths.get(entry.filePath);
            if (java.nio.file.Files.isRegularFile(p)) {
                CompletableFuture.supplyAsync(() -> {
                    try { return fileService.readFile(p); }
                    catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
                }).thenAcceptAsync(content -> {
                    layout.getEditorManager().openFile(p, content);
                    layout.getEditorManager().goToLine(entry.filePath, entry.line);
                }, Platform::runLater).exceptionally(ex -> {
                    LOG.warn("Navigate forward failed: {}", entry.filePath, ex);
                    return null;
                });
            }
        }
    }

    private void registerCommandPaletteCommands() {
        commandPalette.registerCommand("Save", "File", "Ctrl+S", this::saveCurrentFile);
        commandPalette.registerCommand("Save All", "File", "Ctrl+Shift+S", this::saveAllOpenFiles);
        commandPalette.registerCommand("New File", "File", "Ctrl+N", this::handleNewFile);
        commandPalette.registerCommand("Open File", "File", "Ctrl+O", this::handleOpenFile);
        commandPalette.registerCommand("Close Tab", "File", "Ctrl+W", () -> {
            String path = layout.getEditorManager().getCurrentPath();
            if (path != null) layout.getEditorManager().closeTab(path);
        });
        commandPalette.registerCommand("Undo", "Edit", "Ctrl+Z", () -> layout.getEditorManager().undo());
        commandPalette.registerCommand("Redo", "Edit", "Ctrl+Y", () -> layout.getEditorManager().redo());
        commandPalette.registerCommand("Toggle Comment", "Edit", "Ctrl+/", () -> layout.getEditorManager().toggleLineComment());
        commandPalette.registerCommand("Find in Files", "Search", "Ctrl+Shift+F", () -> {
            if (projectRoot != null) layout.getGlobalSearchView().setProjectRoot(projectRoot);
            layout.showBottomTab(5);
            layout.getGlobalSearchView().focusSearch();
        });
        commandPalette.registerCommand("Quick Open", "View", "Ctrl+P", () -> {
            Window w = layout.getView().getScene() != null ? layout.getView().getScene().getWindow() : null;
            if (w != null && projectRoot != null) quickOpenDialog.show(w, projectRoot);
        });
        commandPalette.registerCommand("Command Palette", "View", "Ctrl+Shift+P", () -> {
            Window w = layout.getView().getScene() != null ? layout.getView().getScene().getWindow() : null;
            if (w != null) commandPalette.show(w);
        });
        commandPalette.registerCommand("Navigate Back", "Navigation", "Alt+Left", this::navigateBack);
        commandPalette.registerCommand("Navigate Forward", "Navigation", "Alt+Right", this::navigateForward);
        commandPalette.registerCommand("Split Editor Right", "View", "Ctrl+\\", () -> layout.getEditorManager().splitVertical());
        commandPalette.registerCommand("Split Editor Down", "View", null, () -> layout.getEditorManager().splitHorizontal());
        commandPalette.registerCommand("Toggle Terminal", "View", "Ctrl+`", () -> layout.showBottomTab(0));
        commandPalette.registerCommand("Show Problems", "View", null, () -> layout.showBottomTab(1));
        commandPalette.registerCommand("Show Output", "View", null, () -> layout.showBottomTab(2));
        commandPalette.registerCommand("Show Git", "View", null, () -> layout.showBottomTab(3));
        commandPalette.registerCommand("Zoom In", "View", "Ctrl+=", () ->
                findMenuItem(layout.getViewMenu(), "menu-view-zoom-in").fire());
        commandPalette.registerCommand("Zoom Out", "View", "Ctrl+-", () ->
                findMenuItem(layout.getViewMenu(), "menu-view-zoom-out").fire());
        commandPalette.registerCommand("Git: Commit", "Git", null, this::handleGitCommit);
        commandPalette.registerCommand("Git: Push", "Git", null, this::handleGitPush);
        commandPalette.registerCommand("Git: Pull", "Git", null, this::handleGitPull);
    }

    // ═══════════════════════════════════════════════════════════
    //  KEYBOARD SHORTCUTS
    // ═══════════════════════════════════════════════════════════
    private void wireKeyboardShortcuts() {
        // ── Global focus management keys ─────────────────────
        layout.getView().setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ESCAPE -> {
                    // Escape: close find bar if open, otherwise focus editor
                    if (layout.getInlineFindBar().isShowing()) {
                        layout.getInlineFindBar().hide();
                    } else {
                        var editor = layout.getEditorManager().getCurrentEditor();
                        if (editor != null) editor.getView().requestFocus();
                    }
                    event.consume();
                }
                case F1 -> {
                    // F1: open command palette
                    Window w = layout.getView().getScene() != null ? layout.getView().getScene().getWindow() : null;
                    if (w != null) commandPalette.show(w);
                    event.consume();
                }
                case DIGIT1 -> {
                    if (event.isControlDown()) {
                        // Ctrl+1: focus explorer
                        layout.getProjectTreeView().getView().requestFocus();
                        event.consume();
                    }
                }
                case DIGIT2 -> {
                    if (event.isControlDown()) {
                        // Ctrl+2: focus editor
                        var editor = layout.getEditorManager().getCurrentEditor();
                        if (editor != null) editor.getView().requestFocus();
                        event.consume();
                    }
                }
                case DIGIT3 -> {
                    if (event.isControlDown()) {
                        // Ctrl+3: focus terminal
                        if (!layout.isBottomPanelVisible()) layout.toggleBottomPanel();
                        layout.showBottomTab(0);
                        layout.getTerminalView().getView().requestFocus();
                        event.consume();
                    }
                }
                case TAB -> {
                    if (event.isControlDown() && !event.isAltDown()) {
                        // Ctrl+Tab: next editor tab, Ctrl+Shift+Tab: previous
                        TabPane tp = layout.getEditorManager().getTabPane();
                        if (tp != null && tp.getTabs().size() > 1) {
                            int idx = tp.getSelectionModel().getSelectedIndex();
                            int count = tp.getTabs().size();
                            if (event.isShiftDown()) {
                                tp.getSelectionModel().select((idx - 1 + count) % count);
                            } else {
                                tp.getSelectionModel().select((idx + 1) % count);
                            }
                        }
                        event.consume();
                    }
                }
                case P -> {
                    if (event.isControlDown() && !event.isAltDown()) {
                        if (event.isShiftDown()) {
                            // Ctrl+Shift+P: Command palette
                            Window w = layout.getView().getScene() != null ? layout.getView().getScene().getWindow() : null;
                            if (w != null) commandPalette.show(w);
                        } else {
                            // Ctrl+P: Quick open
                            Window w = layout.getView().getScene() != null ? layout.getView().getScene().getWindow() : null;
                            if (w != null && projectRoot != null) quickOpenDialog.show(w, projectRoot);
                        }
                        event.consume();
                    }
                }
                case SLASH -> {
                    if (event.isControlDown() && !event.isAltDown() && !event.isShiftDown()) {
                        // Ctrl+/: Toggle line comment
                        layout.getEditorManager().toggleLineComment();
                        event.consume();
                    }
                }
                case T -> {
                    if (event.isControlDown() && !event.isAltDown()) {
                        // Ctrl+T: cycle through themes (quick user feedback)
                        if (themeManager != null) {
                            themeManager.toggleTheme();
                        }
                        event.consume();
                    }
                }
                default -> {}
            }
        });

        // ── Menu accelerators ────────────────────────────────
        findMenuItem(layout.getFileMenu(), "menu-save")
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getFileMenu(), "menu-save-all")
                .setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        findMenuItem(layout.getFileMenu(), "menu-new-file")
                .setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getFileMenu(), "menu-open-file")
                .setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getFileMenu(), "menu-open-folder")
                .setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        findMenuItem(layout.getFileMenu(), "menu-close-tab")
                .setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getEditMenu(), "menu-undo")
                .setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getEditMenu(), "menu-redo")
                .setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getEditMenu(), "menu-find")
                .setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getEditMenu(), "menu-replace")
                .setAccelerator(new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getEditMenu(), "menu-find-in-files")
                .setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        findMenuItem(layout.getViewMenu(), "menu-view-zoom-in")
                .setAccelerator(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getViewMenu(), "menu-view-zoom-out")
                .setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getViewMenu(), "menu-view-quick-open")
                .setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getViewMenu(), "menu-view-command-palette")
                .setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        findMenuItem(layout.getViewMenu(), "menu-view-nav-back")
                .setAccelerator(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN));
        findMenuItem(layout.getViewMenu(), "menu-view-nav-forward")
                .setAccelerator(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN));
        findMenuItem(layout.getViewMenu(), "menu-view-split-vertical")
                .setAccelerator(new KeyCodeCombination(KeyCode.BACK_SLASH, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getViewMenu(), "menu-view-sidebar")
                .setAccelerator(new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getViewMenu(), "menu-view-terminal")
                .setAccelerator(new KeyCodeCombination(KeyCode.BACK_QUOTE, KeyCombination.CONTROL_DOWN));
        findMenuItem(layout.getViewMenu(), "menu-view-collab-panel")
                .setAccelerator(new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
    }

    // ═══════════════════════════════════════════════════════════
    //  LOCAL TERMINAL
    // ═══════════════════════════════════════════════════════════
    private void wireLocalTerminal() {
        TerminalView tv = layout.getTerminalView();
        // Wire local terminal as fallback when remote terminal is not connected
        localTerminal.setOutputListener((line, isError) ->
                Platform.runLater(() -> tv.appendOutput(line, isError))
        );
        // Start interactive shell
        Path rootPath = layout.getProjectTreeView().getRootPath();
        if (rootPath != null) {
            localTerminal.setWorkingDirectory(rootPath);
        }
        localTerminal.startInteractiveShell();

        // Wire terminal input to local terminal
        tv.setOnCommand(cmd -> {
            if (localTerminal.isProcessActive()) {
                localTerminal.sendInput(cmd);
            } else {
                // Restart shell if dead
                localTerminal.startInteractiveShell();
                localTerminal.sendInput(cmd);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  FILE HANDLERS
    // ═══════════════════════════════════════════════════════════
    private void handleNewFile() {
        Path root = layout.getProjectTreeView().getRootPath();
        if (root == null) {
            layout.getOutputView().appendOutput("IDE", "Open a folder first");
            return;
        }
        TextInputDialog dialog = new TextInputDialog("untitled.txt");
        dialog.setTitle("New File");
        dialog.setHeaderText("Create a new file");
        dialog.setContentText("File name:");
        dialog.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) return;
            try {
                Path newFile = root.resolve(name);
                java.nio.file.Files.createDirectories(newFile.getParent());
                java.nio.file.Files.writeString(newFile, "");
                layout.getProjectTreeView().refresh();
                openFile(newFile);
                layout.getStatusBarView().setNotification("Created: " + name);
            } catch (Exception ex) {
                LOG.error("Failed to create file", ex);
                showError("Create File Failed", ex.getMessage());
            }
        });
    }

    private void handleNewFolder() {
        Path root = layout.getProjectTreeView().getRootPath();
        if (root == null) {
            layout.getOutputView().appendOutput("IDE", "Open a folder first");
            return;
        }
        TextInputDialog dialog = new TextInputDialog("new-folder");
        dialog.setTitle("New Folder");
        dialog.setHeaderText("Create a new folder");
        dialog.setContentText("Folder name:");
        dialog.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) return;
            try {
                Path newDir = root.resolve(name);
                java.nio.file.Files.createDirectories(newDir);
                layout.getProjectTreeView().refresh();
                layout.getStatusBarView().setNotification("Created folder: " + name);
            } catch (Exception ex) {
                LOG.error("Failed to create folder", ex);
                showError("Create Folder Failed", ex.getMessage());
            }
        });
    }

    private void handleOpenFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open File");
        // Get window from root BorderPane if available
        Window window = null;
        try {
            if (layout.getRoot() != null && layout.getRoot().getScene() != null) {
                window = layout.getRoot().getScene().getWindow();
            }
        } catch (Exception e) {
            // Window not available, proceed with null
        }
        File file = chooser.showOpenDialog(window);
        if (file != null) {
            openFile(file.toPath());
            addRecentFile(file.getAbsolutePath());
        }
    }

    private void handleSaveAs() {
        String currentPath = layout.getEditorManager().getCurrentPath();
        String content = currentPath != null ? layout.getEditorManager().getContent(currentPath) : null;
        if (content == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save As");
        if (currentPath != null) {
            chooser.setInitialFileName(Paths.get(currentPath).getFileName().toString());
        }
        // Get window from root BorderPane if available
        Window window = null;
        try {
            if (layout.getRoot() != null && layout.getRoot().getScene() != null) {
                window = layout.getRoot().getScene().getWindow();
            }
        } catch (Exception e) {
            // Window not available, proceed with null
        }
        File file = chooser.showSaveDialog(window);
        if (file != null) {
            try {
                java.nio.file.Files.writeString(file.toPath(), content);
                layout.getStatusBarView().setNotification("Saved as: " + file.getName());
                addRecentFile(file.getAbsolutePath());
            } catch (Exception ex) {
                LOG.error("Save as failed", ex);
                showError("Save As Failed", ex.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FIND / REPLACE
    // ═══════════════════════════════════════════════════════════
    private void handleFind() {
        InlineFindBar bar = layout.getInlineFindBar();
        if (bar.isShowing()) {
            bar.getFindField().requestFocus();
            bar.getFindField().selectAll();
        } else {
            // Pre-fill with selected text if available
            var editor = layout.getEditorManager().getCurrentEditor();
            String selection = (editor != null) ? editor.getSelectedText() : null;
            bar.show(selection);
        }
    }

    private void handleReplace() {
        InlineFindBar bar = layout.getInlineFindBar();
        // Pre-fill with selected text if available
        var editor = layout.getEditorManager().getCurrentEditor();
        String selection = (editor != null) ? editor.getSelectedText() : null;
        bar.showWithReplace(selection);
    }

    // ═══════════════════════════════════════════════════════════
    //  VIEW HANDLERS
    // ═══════════════════════════════════════════════════════════
    private void toggleSidebar() {
        layout.toggleSidebar();
        layout.getStatusBarView().setNotification(layout.isSidebarVisible() ? "Sidebar visible" : "Sidebar hidden");
    }

    private void toggleBottomPanel() {
        layout.toggleBottomPanel();
        layout.getStatusBarView().setNotification(layout.isBottomPanelVisible() ? "Panel visible" : "Panel hidden");
    }

    private void zoomIn() {
        currentZoom = Math.min(currentZoom + 0.1, 2.0);
        applyZoom();
    }

    private void zoomOut() {
        currentZoom = Math.max(currentZoom - 0.1, 0.5);
        applyZoom();
    }

    private void applyZoom() {
        int fontSize = (int) (13 * currentZoom);
        // Use the root's style property for dynamic zoom — CSS classes can't parametrize values
        layout.getView().setStyle("-fx-font-size: " + fontSize + "px;");
        layout.getStatusBarView().setNotification("Zoom: " + (int) (currentZoom * 100) + "%");
    }

    // ═══════════════════════════════════════════════════════════
    //  PROJECT HANDLERS
    // ═══════════════════════════════════════════════════════════
    private void handleNewProject() {
        ProjectCreationWizard wizard = new ProjectCreationWizard();
        wizard.setOnProjectCreated(result -> {
            if (result.isSuccess()) {
                Platform.runLater(() -> {
                    loadProjectRoot(result.getProjectPath());
                    layout.getStatusBarView().setNotification("Project created: " + result.getProjectName());
                    
                    // Show success dialog
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Project Created");
                    alert.setHeaderText("Project Successfully Created");
                    alert.setContentText("Project: " + result.getProjectName() +
                            "\nType: " + result.getProjectType() +
                            "\nLocation: " + result.getProjectPath());
                    alert.showAndWait();
                });
            }
        });
        wizard.show();
    }

    private void handleNewJavaProject() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("New Java Project");
        dialog.setHeaderText("Create a new Java Maven project");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        TextField nameField = new TextField("my-java-project");
        nameField.setPromptText("Project name");
        TextField groupField = new TextField("com.example");
        groupField.setPromptText("Group ID");
        grid.add(new Label("Project Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Group ID:"), 0, 1);
        grid.add(groupField, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                return new String[]{nameField.getText(), groupField.getText()};
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select parent directory");
            File parentDir = chooser.showDialog(null);
            if (parentDir != null) {
                String projectName = result[0].trim().isEmpty() ? "my-java-project" : result[0].trim();
                String groupId = result[1].trim().isEmpty() ? "com.example" : result[1].trim();
                projectTemplateService.createJavaProject(parentDir.toPath(), projectName, groupId, projectName)
                        .thenAccept(projectPath -> Platform.runLater(() -> {
                            loadProjectRoot(projectPath);
                            layout.getStatusBarView().setNotification("Java project created: " + projectName);
                        }))
                        .exceptionally(ex -> {
                            Platform.runLater(() -> showError("Project Creation Failed", ex.getMessage()));
                            return null;
                        });
            }
        });
    }

    private void handleNewPythonProject() {
        TextInputDialog dialog = new TextInputDialog("my-python-project");
        dialog.setTitle("New Python Project");
        dialog.setHeaderText("Create a new Python project");
        dialog.setContentText("Project name:");

        dialog.showAndWait().ifPresent(projectName -> {
            if (projectName.trim().isEmpty()) return;
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select parent directory");
            File parentDir = chooser.showDialog(null);
            if (parentDir != null) {
                projectTemplateService.createPythonProject(parentDir.toPath(), projectName.trim(), true)
                        .thenAccept(projectPath -> Platform.runLater(() -> {
                            loadProjectRoot(projectPath);
                            layout.getStatusBarView().setNotification("Python project created: " + projectName);
                        }))
                        .exceptionally(ex -> {
                            Platform.runLater(() -> showError("Project Creation Failed", ex.getMessage()));
                            return null;
                        });
            }
        });
    }

    private void handleProjectBuild() {
        Path root = layout.getProjectTreeView().getRootPath();
        if (root == null) {
            showError("Build", "No project open");
            return;
        }
        layout.getOutputView().appendOutput("Build", "Building project...");
        layout.showBottomTab(2); // Show output tab

        // Detect project type and build
        String cmd;
        if (java.nio.file.Files.exists(root.resolve("pom.xml"))) {
            cmd = "mvn clean compile";
        } else if (java.nio.file.Files.exists(root.resolve("build.gradle"))) {
            cmd = "gradle build";
        } else if (java.nio.file.Files.exists(root.resolve("setup.py")) || java.nio.file.Files.exists(root.resolve("requirements.txt"))) {
            cmd = "python -m py_compile " + root.resolve("*.py");
        } else {
            layout.getOutputView().appendOutput("Build", "No recognized build system found");
            return;
        }

        localTerminal.setWorkingDirectory(root);
        localTerminal.executeCommand(cmd).thenAccept(exitCode ->
                Platform.runLater(() -> layout.getOutputView().appendOutput("Build",
                        "Build finished with exit code: " + exitCode))
        );
    }

    private void handleProjectRun() {
        Path root = layout.getProjectTreeView().getRootPath();
        if (root == null) {
            showError("Run", "No project open");
            return;
        }
        
        layout.showBottomTab(0); // Show output tab
        layout.getOutputView().appendOutput("Run", "═".repeat(50));
        
        // Utiliser le gestionnaire d'exécution intelligent
        projectExecutionManager.executeAsync(
            root,
            // Output consumer: affiche les lignes stdout
            line -> Platform.runLater(() -> {
                layout.getOutputView().appendOutput("Run", line);
            }),
            // Error consumer: affiche les lignes stderr avec un préfixe
            line -> Platform.runLater(() -> {
                layout.getOutputView().appendOutput("Run", line);
            }),
            // Completion callback: affiche le statut final
            exitCode -> Platform.runLater(() -> {
                String status = exitCode == 0 ? "✓ SUCCESS" : "✗ FAILED (exit: " + exitCode + ")";
                layout.getOutputView().appendOutput("Run", "═".repeat(50));
                layout.getStatusBarView().setNotification("Run " + status);
            })
        );
    }

    private void handleProjectClean() {
        Path root = layout.getProjectTreeView().getRootPath();
        if (root == null) {
            showError("Clean", "No project open");
            return;
        }
        layout.getOutputView().appendOutput("Build", "Cleaning project...");

        String cmd;
        if (java.nio.file.Files.exists(root.resolve("pom.xml"))) {
            cmd = "mvn clean";
        } else if (java.nio.file.Files.exists(root.resolve("build.gradle"))) {
            cmd = "gradle clean";
        } else {
            cmd = "rm -rf __pycache__ *.pyc build dist *.egg-info";
        }

        localTerminal.setWorkingDirectory(root);
        localTerminal.executeCommand(cmd).thenAccept(exitCode ->
                Platform.runLater(() -> {
                    layout.getOutputView().appendOutput("Build", "Clean finished (exit: " + exitCode + ")");
                    layout.getProjectTreeView().refresh();
                })
        );
    }

    // ═══════════════════════════════════════════════════════════
    //  RUN CODE HANDLERS
    // ═══════════════════════════════════════════════════════════

    private void handleRunCode() {
        String currentPath = layout.getEditorManager().getCurrentPath();
        if (currentPath == null) {
            layout.getOutputView().appendOutput("Run", "No file open");
            return;
        }

        // Save before running
        String content = layout.getEditorManager().getContent(currentPath);
        if (content != null && layout.getEditorManager().isDirty(currentPath)) {
            try {
                fileService.writeFile(java.nio.file.Paths.get(currentPath), content);
                layout.getEditorManager().markClean(currentPath);
            } catch (Exception e) {
                LOG.warn("Auto-save before run failed: {}", currentPath, e);
            }
        }

        String language = com.distributed_ide.javafx_client.editor.EditorManager.detectLanguage(currentPath);
        layout.showBottomTab(2); // Show output tab
        layout.getOutputView().appendOutput("Run", "─".repeat(40));

        // Wire output listener
        codeExecutionService.setOutputListener((line, isError) ->
                layout.getOutputView().appendOutput("Run", (isError ? "⚠ " : "") + line));

        codeExecutionService.runFile(currentPath, language).thenAccept(exitCode ->
                Platform.runLater(() -> {
                    layout.getOutputView().appendOutput("Run", "─".repeat(40));
                    layout.getStatusBarView().setNotification("Run finished (exit: " + exitCode + ")");
                })
        );
    }

    private void handleStopCode() {
        // Arrêter le processus d'exécution simple (CodeExecutionService)
        if (codeExecutionService.isRunning()) {
            codeExecutionService.stop();
            layout.getOutputView().appendOutput("Run", "✓ File execution stopped by user");
            layout.getStatusBarView().setNotification("Stopped");
            return;
        }
        
        // Arrêter le processus du projet (ProjectExecutionManager)
        projectExecutionManager.stop();
        layout.getOutputView().appendOutput("Run", "✓ Project execution stopped by user");
        layout.getStatusBarView().setNotification("Stopped");
    }

    private void triggerSyntaxCheck(String filePath, String content) {
        if (filePath == null || content == null) return;
        String language = com.distributed_ide.javafx_client.editor.EditorManager.detectLanguage(filePath);
        SyntaxErrorChecker.checkAsync(content, language, filePath).thenAccept(errors -> {
            Platform.runLater(() -> {
                // Pass full error list to editor for character-level underlines + tooltips
                layout.getEditorManager().setSyntaxErrors(filePath, errors);
                
                // Also add syntax errors to DiagnosticsView (Problems tab)
                String uri = new java.io.File(filePath).toURI().toString();
                if (!errors.isEmpty()) {
                    LOG.info("[MainLayoutController] Adding {} syntax errors to DiagnosticsView", errors.size());
                    java.util.List<com.distributed_ide.javafx_client.model.DiagnosticItem> diagnosticItems = new java.util.ArrayList<>();
                    for (SyntaxErrorChecker.SyntaxError err : errors) {
                        com.distributed_ide.javafx_client.model.DiagnosticItem.Severity severity = 
                            err.getSeverity() == SyntaxErrorChecker.SyntaxError.Severity.ERROR
                                ? com.distributed_ide.javafx_client.model.DiagnosticItem.Severity.ERROR
                                : com.distributed_ide.javafx_client.model.DiagnosticItem.Severity.WARNING;
                        diagnosticItems.add(new com.distributed_ide.javafx_client.model.DiagnosticItem(
                            uri, err.getLine(), err.getColumn(), err.getMessage(), severity
                        ));
                    }
                    layout.getDiagnosticsView().setDiagnostics(uri, diagnosticItems);
                } else {
                    // Clear diagnostics for this file if no errors
                    LOG.info("[MainLayoutController] No syntax errors for {}, clearing DiagnosticsView", uri);
                    layout.getDiagnosticsView().setDiagnostics(uri, java.util.Collections.emptyList());
                }
                
                if (!errors.isEmpty()) {
                    layout.getStatusBarView().setNotification(
                            errors.size() + " syntax issue(s) in " + java.nio.file.Path.of(filePath).getFileName());
                }
            });
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  GIT MENU HANDLERS
    // ═══════════════════════════════════════════════════════════
    private void handleGitInit() {
        Path root = layout.getProjectTreeView().getRootPath();
        if (root == null) {
            showError("Git Init", "No project open");
            return;
        }
        gitService.init(root).thenAccept(success -> Platform.runLater(() -> {
            if (success) {
                layout.getStatusBarView().setNotification("Git repository initialized");
                refreshGitPanel();
            } else {
                showError("Git Init", "Failed to initialize repository");
            }
        }));
    }

    private void handleGitCommit() {
        if (!gitService.isOpen()) {
            showError("Git Commit", "No Git repository open");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Git Commit");
        dialog.setHeaderText("Commit staged changes");
        dialog.setContentText("Commit message:");
        dialog.showAndWait().ifPresent(message -> {
            if (message.trim().isEmpty()) return;
            gitService.addAll().thenCompose(v ->
                    gitService.commit(message.trim(), null, null)
            ).thenAccept(info -> Platform.runLater(() -> {
                layout.getStatusBarView().setNotification("Committed: " + info.shortHash());
                refreshGitPanel();
            })).exceptionally(ex -> {
                Platform.runLater(() -> showError("Git Commit", ex.getMessage()));
                return null;
            });
        });
    }

    private void handleGitPush() {
        if (!gitService.isOpen()) {
            showError("Git Push", "No Git repository open");
            return;
        }
        layout.getStatusBarView().setNotification("Pushing...");
        gitService.push().thenAccept(msg -> Platform.runLater(() -> {
            layout.getStatusBarView().setNotification(msg);
            refreshGitPanel();
        })).exceptionally(ex -> {
            Platform.runLater(() -> showError("Git Push", ex.getMessage()));
            return null;
        });
    }

    private void handleGitPull() {
        if (!gitService.isOpen()) {
            showError("Git Pull", "No Git repository open");
            return;
        }
        layout.getStatusBarView().setNotification("Pulling...");
        gitService.pull().thenAccept(msg -> Platform.runLater(() -> {
            fileService.clearCache();
            layout.getStatusBarView().setNotification(msg);
            refreshGitPanel();
            layout.getProjectTreeView().refresh();
        })).exceptionally(ex -> {
            Platform.runLater(() -> showError("Git Pull", ex.getMessage()));
            return null;
        });
    }

    private void handleGitClone() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Git Clone");
        dialog.setHeaderText("Clone a remote repository");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        TextField urlField = new TextField();
        urlField.setPromptText("https://github.com/user/repo.git");
        urlField.setPrefWidth(400);
        grid.add(new Label("Repository URL:"), 0, 0);
        grid.add(urlField, 1, 0);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> btn == ButtonType.OK ? new String[]{urlField.getText()} : null);

        dialog.showAndWait().ifPresent(result -> {
            if (result[0] == null || result[0].trim().isEmpty()) return;
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select target directory");
            File targetDir = chooser.showDialog(null);
            if (targetDir != null) {
                layout.getStatusBarView().setNotification("Cloning...");
                gitService.cloneRepo(result[0].trim(), targetDir.toPath()).thenAccept(success -> Platform.runLater(() -> {
                    if (success) {
                        loadProjectRoot(targetDir.toPath());
                        layout.getStatusBarView().setNotification("Clone successful");
                    } else {
                        showError("Git Clone", "Clone failed");
                    }
                }));
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  AUTO-SAVE
    // ═══════════════════════════════════════════════════════════
    private void toggleAutoSave(boolean enabled) {
        autoSaveEnabled = enabled;
        if (enabled) {
            autoSaveTimeline = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.seconds(30), e -> saveAllOpenFiles())
            );
            autoSaveTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
            autoSaveTimeline.play();
            layout.getStatusBarView().setNotification("Auto-save enabled (30s)");
        } else {
            if (autoSaveTimeline != null) {
                autoSaveTimeline.stop();
                autoSaveTimeline = null;
            }
            layout.getStatusBarView().setNotification("Auto-save disabled");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  RECENT FILES / FOLDERS
    // ═══════════════════════════════════════════════════════════
    private void loadRecentItems() {
        String files = prefs.get("recentFiles", "");
        String folders = prefs.get("recentFolders", "");
        if (!files.isEmpty()) {
            for (String f : files.split("\\|")) {
                if (!f.isEmpty()) recentFiles.add(f);
            }
        }
        if (!folders.isEmpty()) {
            for (String f : folders.split("\\|")) {
                if (!f.isEmpty()) recentFolders.add(f);
            }
        }
    }

    private void saveRecentItems() {
        prefs.put("recentFiles", String.join("|", recentFiles));
        prefs.put("recentFolders", String.join("|", recentFolders));
    }

    private void addRecentFile(String path) {
        recentFiles.remove(path);
        recentFiles.addFirst(path);
        while (recentFiles.size() > MAX_RECENT) recentFiles.removeLast();
        saveRecentItems();
        refreshRecentMenus();
    }

    private void addRecentFolder(String path) {
        recentFolders.remove(path);
        recentFolders.addFirst(path);
        while (recentFolders.size() > MAX_RECENT) recentFolders.removeLast();
        saveRecentItems();
        refreshRecentMenus();
    }

    private void refreshRecentMenus() {
        // Recent Files
        MenuItem recentFilesMenu = findMenuItemDeep(layout.getFileMenu(), "menu-recent-files");
        if (recentFilesMenu instanceof Menu rfm) {
            rfm.getItems().clear();
            if (recentFiles.isEmpty()) {
                rfm.getItems().add(new MenuItem("(empty)"));
            } else {
                for (String path : recentFiles) {
                    MenuItem item = new MenuItem(Paths.get(path).getFileName().toString());
                    item.setOnAction(e -> openFile(Paths.get(path)));
                    rfm.getItems().add(item);
                }
            }
        }

        // Recent Folders
        MenuItem recentFoldersMenu = findMenuItemDeep(layout.getFileMenu(), "menu-recent-folders");
        if (recentFoldersMenu instanceof Menu rfm) {
            rfm.getItems().clear();
            if (recentFolders.isEmpty()) {
                rfm.getItems().add(new MenuItem("(empty)"));
            } else {
                for (String path : recentFolders) {
                    MenuItem item = new MenuItem(path);
                    item.setOnAction(e -> {
                        loadProjectRoot(Paths.get(path));
                        addRecentFolder(path);
                    });
                    rfm.getItems().add(item);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ERROR DIALOG HELPER
    // ═══════════════════════════════════════════════════════════
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
