package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.javafx_client.editor.EditorManager;
import com.distributed_ide.javafx_client.git.GitPanelView;
import com.distributed_ide.javafx_client.service.FileService;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Main IDE layout — VSCode-style structure.
 *
 * ┌──────────────────────────────────────────────────────┐
 * │ MenuBar                                               │
 * ├───┬───────┬─────────────────────────┬────────────────┤
 * │ A │ Side  │  Editor TabPane         │ Collab Panel   │
 * │ c │ bar   │                         │ (toggleable)   │
 * │ t │       ├─────────────────────────┤                │
 * │   │ Tree  │  Bottom Panel           │                │
 * │   │       │  (toggleable)           │                │
 * ├───┴───────┴─────────────────────────┴────────────────┤
 * │ StatusBar                                             │
 * └──────────────────────────────────────────────────────┘
 *
 * All views accessible via getters for the controller.
 * No business logic in this class — pure structure.
 */
public class MainLayout {

    private final BorderPane root;

    // --- MenuBar ---
    private final MenuBar menuBar;
    private final Menu fileMenu;
    private final Menu editMenu;
    private final Menu viewMenu;
    private final Menu projectMenu;
    private final Menu gitMenu;
    private final Menu runMenu;
    private final Menu collabMenu;
    private final Menu settingsMenu;
    private final Menu helpMenu;

    // --- Views ---
    private final ProjectTreeView projectTreeView;
    private final EditorTabPaneView editorTabPaneView;
    private final EditorManager editorManager;
    private final DiagnosticsView diagnosticsView;
    private final CollaboratorsView collaboratorsView;
    private final TerminalView terminalView;
    private final OutputView outputView;
    private final CollaborationPanel collaborationPanel;
    private final StatusBarView statusBarView;
    private final GitPanelView gitPanelView;
    private final ChatView chatView;
    private final GlobalSearchView globalSearchView;
    private final InlineFindBar inlineFindBar;

    // --- Activity Bar ---
    private final VBox activityBar;
    private final ToggleButton actExplorer;
    private final ToggleButton actSearch;
    private final ToggleButton actGit;
    private final ToggleButton actCollab;

    // --- Sidebar ---
    private final VBox leftSidebar;

    // --- Splitters ---
    private final SplitPane mainHorizontalSplit;
    private final SplitPane centerVerticalSplit;

    // --- Bottom panel ---
    private final TabPane bottomTabPane;

    // --- Toggle state ---
    private boolean sidebarVisible = true;
    private boolean bottomPanelVisible = true;
    private boolean collabPanelVisible = false;
    private double lastSidebarDivider = 0.17;
    private double lastBottomDivider = 0.7;

    public MainLayout(FileService fileService) {
        root = new BorderPane();

        // ── MenuBar ──────────────────────────────────────────────
        fileMenu = createFileMenu();
        editMenu = createEditMenu();
        viewMenu = createViewMenu();
        projectMenu = createProjectMenu();
        gitMenu = createGitMenu();
        runMenu = createRunMenu();
        collabMenu = createCollabMenu();
        settingsMenu = createSettingsMenu();
        helpMenu = createHelpMenu();
        menuBar = new MenuBar(fileMenu, editMenu, viewMenu, projectMenu, gitMenu, runMenu, collabMenu, settingsMenu, helpMenu);

        // ── Views ────────────────────────────────────────────────
        projectTreeView = new ProjectTreeView(fileService);
        editorTabPaneView = new EditorTabPaneView();
        editorManager = new EditorManager();
        diagnosticsView = new DiagnosticsView();
        collaboratorsView = new CollaboratorsView();
        terminalView = new TerminalView();
        outputView = new OutputView();
        collaborationPanel = new CollaborationPanel();
        statusBarView = new StatusBarView();
        gitPanelView = new GitPanelView();
        chatView = new ChatView();
        globalSearchView = new GlobalSearchView();
        inlineFindBar = new InlineFindBar();

        // ── Activity Bar (far left icon strip) ─────────────────
        actExplorer = createActivityButton("☰", "Explorer");
        actSearch   = createActivityButton("⌕", "Search");
        actGit      = createActivityButton("⑂", "Git");
        actCollab   = createActivityButton("☺", "Collaboration");
        actExplorer.setSelected(true);

        activityBar = new VBox(4, actExplorer, actSearch, actGit, actCollab);
        activityBar.getStyleClass().add("activity-bar");
        activityBar.setAlignment(Pos.TOP_CENTER);
        activityBar.setPrefWidth(40);
        activityBar.setMinWidth(40);
        activityBar.setMaxWidth(40);

        // ── Left sidebar: Explorer ──────────────────────────────
        Label explorerTitle = new Label("EXPLORER");
        explorerTitle.setMaxWidth(Double.MAX_VALUE);
        HBox explorerHeader = new HBox(explorerTitle);
        explorerHeader.getStyleClass().add("sidebar-header");
        leftSidebar = new VBox(explorerHeader, projectTreeView.getView());
        leftSidebar.getStyleClass().add("sidebar-panel");
        VBox.setVgrow(projectTreeView.getView(), Priority.ALWAYS);
        leftSidebar.setPrefWidth(240);
        leftSidebar.setMinWidth(160);

        // ── Bottom tab pane: Terminal | Problems | Output ───────
        bottomTabPane = new TabPane();
        bottomTabPane.getStyleClass().add("bottom-tab-pane");
        bottomTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab terminalTab = new Tab("TERMINAL", terminalView.getView());
        Tab problemsTab = new Tab("PROBLÈMES", diagnosticsView.getView());
        Tab outputTab = new Tab("SORTIE", outputView.getView());
        Tab gitTab = new Tab("GIT", gitPanelView.getView());
        Tab chatTab = new Tab("CHAT", chatView.getView());
        Tab searchTab = new Tab("RECHERCHE", globalSearchView.getView());
        bottomTabPane.getTabs().addAll(terminalTab, problemsTab, outputTab, gitTab, chatTab, searchTab);
        bottomTabPane.setPrefHeight(220);

        // ── Center: find bar + editor + bottom panel ─────────
        VBox editorWithFindBar = new VBox(inlineFindBar, editorManager.getView());
        VBox.setVgrow(editorManager.getView(), Priority.ALWAYS);

        centerVerticalSplit = new SplitPane();
        centerVerticalSplit.setOrientation(Orientation.VERTICAL);
        centerVerticalSplit.getItems().addAll(editorWithFindBar, bottomTabPane);
        centerVerticalSplit.setDividerPositions(0.7);

        // ── Main horizontal split: left sidebar | center ───────
        mainHorizontalSplit = new SplitPane();
        mainHorizontalSplit.setOrientation(Orientation.HORIZONTAL);
        mainHorizontalSplit.getItems().addAll(leftSidebar, centerVerticalSplit);
        mainHorizontalSplit.setDividerPositions(0.20);

        // ── Content area: activity bar + split pane ─────────────
        HBox contentArea = new HBox(activityBar, mainHorizontalSplit);
        HBox.setHgrow(mainHorizontalSplit, Priority.ALWAYS);

        // ── Assemble root ───────────────────────────────────────
        root.setTop(menuBar);
        root.setCenter(contentArea);
        root.setBottom(statusBarView.getView());
        root.getStyleClass().add("root");
    }

    public BorderPane getView() {
        return root;
    }

    // ═══════════════════════════════════════════════════════════
    //  GETTERS — used by MainLayoutController
    // ═══════════════════════════════════════════════════════════
    public MenuBar getMenuBar() { return menuBar; }
    public Menu getFileMenu() { return fileMenu; }
    public Menu getEditMenu() { return editMenu; }
    public Menu getViewMenu() { return viewMenu; }
    public Menu getProjectMenu() { return projectMenu; }
    public Menu getGitMenu() { return gitMenu; }
    public Menu getRunMenu() { return runMenu; }
    public Menu getCollabMenu() { return collabMenu; }
    public Menu getSettingsMenu() { return settingsMenu; }
    public Menu getHelpMenu() { return helpMenu; }

    public ProjectTreeView getProjectTreeView() { return projectTreeView; }
    public EditorTabPaneView getEditorTabPaneView() { return editorTabPaneView; }
    public EditorManager getEditorManager() { return editorManager; }
    public DiagnosticsView getDiagnosticsView() { return diagnosticsView; }
    public CollaboratorsView getCollaboratorsView() { return collaboratorsView; }
    public TerminalView getTerminalView() { return terminalView; }
    public OutputView getOutputView() { return outputView; }
    public CollaborationPanel getCollaborationPanel() { return collaborationPanel; }
    public StatusBarView getStatusBarView() { return statusBarView; }
    public GitPanelView getGitPanelView() { return gitPanelView; }
    public ChatView getChatView() { return chatView; }
    public GlobalSearchView getGlobalSearchView() { return globalSearchView; }
    public InlineFindBar getInlineFindBar() { return inlineFindBar; }
    public TabPane getBottomTabPane() { return bottomTabPane; }
    public SplitPane getMainHorizontalSplit() { return mainHorizontalSplit; }
    public SplitPane getCenterVerticalSplit() { return centerVerticalSplit; }
    public VBox getActivityBar() { return activityBar; }
    public ToggleButton getActExplorer() { return actExplorer; }
    public ToggleButton getActSearch() { return actSearch; }
    public ToggleButton getActGit() { return actGit; }
    public ToggleButton getActCollab() { return actCollab; }
    public VBox getLeftSidebar() { return leftSidebar; }
    public boolean isSidebarVisible() { return sidebarVisible; }
    public boolean isBottomPanelVisible() { return bottomPanelVisible; }
    public boolean isCollabPanelVisible() { return collabPanelVisible; }

    public void showBottomTab(int index) {
        if (!bottomPanelVisible) toggleBottomPanel();
        if (index >= 0 && index < bottomTabPane.getTabs().size()) {
            bottomTabPane.getSelectionModel().select(index);
        }
    }

    public void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        if (!sidebarVisible) {
            if (mainHorizontalSplit.getItems().contains(leftSidebar)) {
                lastSidebarDivider = mainHorizontalSplit.getDividerPositions()[0];
                animateDivider(mainHorizontalSplit, 0, lastSidebarDivider, 0.0, () ->
                    mainHorizontalSplit.getItems().remove(leftSidebar));
            }
        } else {
            if (!mainHorizontalSplit.getItems().contains(leftSidebar)) {
                mainHorizontalSplit.getItems().add(0, leftSidebar);
                mainHorizontalSplit.setDividerPositions(0.0);
                animateDivider(mainHorizontalSplit, 0, 0.0, lastSidebarDivider, null);
            }
        }
    }

    public void toggleBottomPanel() {
        bottomPanelVisible = !bottomPanelVisible;
        if (!bottomPanelVisible) {
            if (centerVerticalSplit.getItems().contains(bottomTabPane)) {
                lastBottomDivider = centerVerticalSplit.getDividerPositions()[0];
                animateDivider(centerVerticalSplit, 0, lastBottomDivider, 1.0, () ->
                    centerVerticalSplit.getItems().remove(bottomTabPane));
            }
        } else {
            if (!centerVerticalSplit.getItems().contains(bottomTabPane)) {
                centerVerticalSplit.getItems().add(bottomTabPane);
                centerVerticalSplit.setDividerPositions(1.0);
                animateDivider(centerVerticalSplit, 0, 1.0, lastBottomDivider, null);
            }
        }
    }

    private void animateDivider(SplitPane split, int dividerIndex,
                                double from, double to, Runnable onFinished) {
        DoubleProperty divPos = new SimpleDoubleProperty(from);
        divPos.addListener((obs, oldVal, newVal) ->
            split.setDividerPositions(newVal.doubleValue()));
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(divPos, from)),
            new KeyFrame(Duration.millis(200), new KeyValue(divPos, to))
        );
        if (onFinished != null) {
            timeline.setOnFinished(e -> onFinished.run());
        }
        timeline.play();
    }

    public void toggleCollaborationPanel() {
        collabPanelVisible = !collabPanelVisible;
        if (collabPanelVisible) {
            if (!mainHorizontalSplit.getItems().contains(collaborationPanel.getView())) {
                mainHorizontalSplit.getItems().add(collaborationPanel.getView());
            }
        } else {
            mainHorizontalSplit.getItems().remove(collaborationPanel.getView());
        }
    }

    public void toggleCollaborationPanel(boolean visible) {
        if (visible != collabPanelVisible) toggleCollaborationPanel();
    }

    private ToggleButton createActivityButton(String icon, String tooltip) {
        ToggleButton btn = new ToggleButton(icon);
        btn.getStyleClass().add("activity-btn");
        btn.setTooltip(new Tooltip(tooltip));
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    // ═══════════════════════════════════════════════════════════
    //  MENU BUILDERS — create skeleton menus, items wired by controller
    // ═══════════════════════════════════════════════════════════
    private Menu createFileMenu() {
        MenuItem newFile = new MenuItem("Nouveau fichier");
        newFile.setId("menu-new-file");
        MenuItem newFolder = new MenuItem("Nouveau dossier");
        newFolder.setId("menu-new-folder");
        MenuItem openFile = new MenuItem("Ouvrir un fichier...");
        openFile.setId("menu-open-file");
        MenuItem openFolder = new MenuItem("Ouvrir un dossier...");
        openFolder.setId("menu-open-folder");

        MenuItem saveFile = new MenuItem("Enregistrer");
        saveFile.setId("menu-save");
        MenuItem saveAs = new MenuItem("Enregistrer sous...");
        saveAs.setId("menu-save-as");
        MenuItem saveAll = new MenuItem("Enregistrer tout");
        saveAll.setId("menu-save-all");

        CheckMenuItem autoSave = new CheckMenuItem("Enregistrement automatique");
        autoSave.setId("menu-auto-save");

        Menu recentFiles = new Menu("Fichiers récents");
        recentFiles.setId("menu-recent-files");
        Menu recentFolders = new Menu("Dossiers récents");
        recentFolders.setId("menu-recent-folders");

        MenuItem closeTab = new MenuItem("Fermer l'onglet");
        closeTab.setId("menu-close-tab");
        MenuItem exit = new MenuItem("Quitter");
        exit.setId("menu-exit");

        Menu menu = new Menu("Fichier");
        menu.getItems().addAll(
                newFile, newFolder, new SeparatorMenuItem(),
                openFile, openFolder, new SeparatorMenuItem(),
                saveFile, saveAs, saveAll, new SeparatorMenuItem(),
                autoSave, new SeparatorMenuItem(),
                recentFiles, recentFolders, new SeparatorMenuItem(),
                closeTab, new SeparatorMenuItem(),
                exit
        );
        return menu;
    }

    private Menu createEditMenu() {
        MenuItem undo = new MenuItem("Annuler");
        undo.setId("menu-undo");
        MenuItem redo = new MenuItem("Rétablir");
        redo.setId("menu-redo");
        MenuItem cut = new MenuItem("Couper");
        cut.setId("menu-cut");
        MenuItem copy = new MenuItem("Copier");
        copy.setId("menu-copy");
        MenuItem paste = new MenuItem("Coller");
        paste.setId("menu-paste");
        MenuItem find = new MenuItem("Chercher...");
        find.setId("menu-find");
        MenuItem replace = new MenuItem("Remplacer...");
        replace.setId("menu-replace");
        MenuItem findInFiles = new MenuItem("Chercher dans les fichiers...");
        findInFiles.setId("menu-find-in-files");
        Menu menu = new Menu("Édition");
        menu.getItems().addAll(undo, redo, new SeparatorMenuItem(),
                cut, copy, paste, new SeparatorMenuItem(),
                find, replace, findInFiles);
        return menu;
    }

    private Menu createViewMenu() {
        MenuItem toggleSidebar = new MenuItem("Afficher/Masquer la barre latérale");
        toggleSidebar.setId("menu-view-sidebar");
        MenuItem toggleTerminal = new MenuItem("Afficher/Masquer le terminal");
        toggleTerminal.setId("menu-view-terminal");
        MenuItem toggleExplorer = new MenuItem("Afficher/Masquer l'explorateur");
        toggleExplorer.setId("menu-view-explorer");
        MenuItem toggleProblems = new MenuItem("Problèmes");
        toggleProblems.setId("menu-view-problems");
        MenuItem toggleOutput = new MenuItem("Sortie");
        toggleOutput.setId("menu-view-output");
        MenuItem toggleGit = new MenuItem("Git");
        toggleGit.setId("menu-view-git");
        MenuItem zoomIn = new MenuItem("Zoom avant");
        zoomIn.setId("menu-view-zoom-in");
        MenuItem zoomOut = new MenuItem("Zoom arrière");
        zoomOut.setId("menu-view-zoom-out");
        MenuItem toggleCollabPanel = new MenuItem("Panneau de collaboration");
        toggleCollabPanel.setId("menu-view-collab-panel");
        MenuItem quickOpen = new MenuItem("Ouverture rapide...");
        quickOpen.setId("menu-view-quick-open");
        MenuItem commandPalette = new MenuItem("Palette de commandes...");
        commandPalette.setId("menu-view-command-palette");
        MenuItem splitVertical = new MenuItem("Fractionner l'éditeur à droite");
        splitVertical.setId("menu-view-split-vertical");
        MenuItem splitHorizontal = new MenuItem("Fractionner l'éditeur en bas");
        splitHorizontal.setId("menu-view-split-horizontal");
        MenuItem navBack = new MenuItem("Naviguer en arrière");
        navBack.setId("menu-view-nav-back");
        MenuItem navForward = new MenuItem("Naviguer en avant");
        navForward.setId("menu-view-nav-forward");
        Menu menu = new Menu("Affichage");
        menu.getItems().addAll(
                quickOpen, commandPalette, new SeparatorMenuItem(),
                toggleSidebar, toggleTerminal, toggleExplorer, new SeparatorMenuItem(),
                toggleProblems, toggleOutput, toggleGit, new SeparatorMenuItem(),
                splitVertical, splitHorizontal, new SeparatorMenuItem(),
                navBack, navForward, new SeparatorMenuItem(),
                zoomIn, zoomOut, new SeparatorMenuItem(),
                toggleCollabPanel
        );
        return menu;
    }

    private Menu createProjectMenu() {
        MenuItem newProject = new MenuItem("New Project...");
        newProject.setId("menu-project-new");
        MenuItem openProject = new MenuItem("Open Project...");
        openProject.setId("menu-project-open");
        MenuItem closeProject = new MenuItem("Close Project");
        closeProject.setId("menu-project-close");
        MenuItem build = new MenuItem("Build");
        build.setId("menu-project-build");
        MenuItem run = new MenuItem("Run");
        run.setId("menu-project-run");
        MenuItem clean = new MenuItem("Clean");
        clean.setId("menu-project-clean");
        Menu menu = new Menu("Projet");
        menu.getItems().addAll(
                newProject, new SeparatorMenuItem(),
                openProject, closeProject, new SeparatorMenuItem(),
                build, run, clean
        );
        return menu;
    }

    private Menu createGitMenu() {
        MenuItem initRepo = new MenuItem("Init Repository");
        initRepo.setId("menu-git-init");
        MenuItem commit = new MenuItem("Commit...");
        commit.setId("menu-git-commit");
        MenuItem push = new MenuItem("Push");
        push.setId("menu-git-push");
        MenuItem pull = new MenuItem("Pull");
        pull.setId("menu-git-pull");
        MenuItem cloneRepo = new MenuItem("Clone...");
        cloneRepo.setId("menu-git-clone");
        Menu menu = new Menu("Git");
        menu.getItems().addAll(initRepo, new SeparatorMenuItem(),
                commit, push, pull, new SeparatorMenuItem(),
                cloneRepo);
        return menu;
    }

    private Menu createRunMenu() {
        MenuItem runCode = new MenuItem("Run Code");
        runCode.setId("menu-run-code");
        MenuItem stopRun = new MenuItem("Stop");
        stopRun.setId("menu-stop");
        Menu menu = new Menu("Run");
        menu.getItems().addAll(runCode, stopRun);
        return menu;
    }

    private Menu createCollabMenu() {
        MenuItem hostJoin = new MenuItem("Héberger / Rejoindre...");
        hostJoin.setId("menu-collab-host-join");
        MenuItem disconnect = new MenuItem("Déconnecter");
        disconnect.setId("menu-collab-disconnect");
        SeparatorMenuItem sep = new SeparatorMenuItem();
        MenuItem joinCall = new MenuItem("Rejoindre l'appel audio/vidéo...");
        joinCall.setId("menu-collab-call");
        MenuItem leaveCall = new MenuItem("Quitter l'appel");
        leaveCall.setId("menu-collab-leave-call");
        Menu menu = new Menu("Collaboration");
        menu.getItems().addAll(hostJoin, disconnect, sep, joinCall, leaveCall);
        return menu;
    }

    private Menu createSettingsMenu() {
        MenuItem preferences = new MenuItem("Préférences...");
        preferences.setId("menu-settings-preferences");
        MenuItem customizeTheme = new MenuItem("Personnaliser le thème...");
        customizeTheme.setId("menu-settings-customize-theme");
        Menu themeSubMenu = new Menu("Thème");
        themeSubMenu.setId("menu-settings-theme");
        MenuItem themeModern = new MenuItem("Modern Dark");
        themeModern.setId("menu-theme-modern");
        MenuItem themeDark = new MenuItem("Sombre");
        themeDark.setId("menu-theme-dark");
        MenuItem themeLight = new MenuItem("Clair");
        themeLight.setId("menu-theme-light");
        MenuItem themeDracula = new MenuItem("Dracula");
        themeDracula.setId("menu-theme-dracula");
        MenuItem themeNord = new MenuItem("Nord");
        themeNord.setId("menu-theme-nord");
        themeSubMenu.getItems().addAll(themeModern, themeDark, themeLight, themeDracula, themeNord);
        Menu menu = new Menu("Paramètres");
        menu.getItems().addAll(preferences, customizeTheme, new SeparatorMenuItem(), themeSubMenu);
        return menu;
    }

    private Menu createHelpMenu() {
        MenuItem about = new MenuItem("À propos de Distributed IDE");
        about.setId("menu-about");
        MenuItem shortcuts = new MenuItem("Raccourcis clavier");
        shortcuts.setId("menu-shortcuts");
        Menu menu = new Menu("Aide");
        menu.getItems().addAll(about, shortcuts);
        return menu;
    }
    
    public BorderPane getRoot() {
        return root;
    }
}
