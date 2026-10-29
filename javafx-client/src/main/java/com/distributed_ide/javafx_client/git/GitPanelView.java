package com.distributed_ide.javafx_client.git;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Git panel for the IDE — displays status, branches, history, diff viewer.
 * Wrapped in a ScrollPane for scrollability.
 */
public class GitPanelView {

    private final VBox root;
    private final ScrollPane scrollableRoot;

    // Branch bar
    private final ComboBox<String> branchCombo;
    private final Button pullBtn;
    private final Button pushBtn;
    private final Button refreshBtn;
    private final Label branchLabel;

    // Changes list
    private final ListView<GitService.FileStatus> changesListView;
    private final ObservableList<GitService.FileStatus> changesItems = FXCollections.observableArrayList();

    // Commit area
    private final TextArea commitMessageArea;
    private final Button stageAllBtn;
    private final Button commitBtn;

    // Diff viewer
    private final TextFlow diffViewer;
    private final ScrollPane diffScrollPane;

    // History
    private final ListView<GitService.CommitInfo> historyListView;
    private final ObservableList<GitService.CommitInfo> historyItems = FXCollections.observableArrayList();

    // Status
    private final Label statusLabel;

    // Callbacks
    private Consumer<String> onCheckoutBranch;
    private Runnable onPull;
    private Runnable onPush;
    private Runnable onRefresh;
    private BiConsumer<String, String> onCommit; // (message, unused)
    private Runnable onStageAll;
    private Consumer<GitService.FileStatus> onFileSelected;

    public GitPanelView() {
        root = new VBox();
        root.getStyleClass().add("git-panel");
        root.setSpacing(0);

        // ── Branch bar ─────────────────────────────────────────
        branchLabel = new Label("Git");
        branchLabel.getStyleClass().add("git-section-title");

        branchCombo = new ComboBox<>();
        branchCombo.setPromptText("branch");
        branchCombo.getStyleClass().add("git-branch-combo");
        branchCombo.setPrefWidth(140);
        branchCombo.setOnAction(e -> {
            String selected = branchCombo.getValue();
            if (selected != null && onCheckoutBranch != null) {
                onCheckoutBranch.accept(selected);
            }
        });

        refreshBtn = new Button("⟳");
        refreshBtn.getStyleClass().add("git-toolbar-btn");
        refreshBtn.setTooltip(new Tooltip("Refresh"));
        refreshBtn.setOnAction(e -> { if (onRefresh != null) onRefresh.run(); });

        pullBtn = new Button("↓ Pull");
        pullBtn.getStyleClass().add("git-toolbar-btn");
        pullBtn.setOnAction(e -> { if (onPull != null) onPull.run(); });

        pushBtn = new Button("↑ Push");
        pushBtn.getStyleClass().add("git-toolbar-btn");
        pushBtn.setOnAction(e -> { if (onPush != null) onPush.run(); });

        HBox branchBar = new HBox(6, branchLabel, branchCombo, refreshBtn, pullBtn, pushBtn);
        branchBar.setAlignment(Pos.CENTER_LEFT);
        branchBar.setPadding(new Insets(6, 8, 6, 8));
        branchBar.getStyleClass().add("git-toolbar");
        HBox.setHgrow(branchCombo, Priority.SOMETIMES);

        // ── Changes section ────────────────────────────────────
        Label changesTitle = new Label("CHANGES");
        changesTitle.getStyleClass().add("git-section-title");
        changesTitle.setPadding(new Insets(6, 8, 2, 8));

        changesListView = new ListView<>(changesItems);
        changesListView.getStyleClass().add("git-changes-list");
        changesListView.setPrefHeight(160);
        changesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(GitService.FileStatus item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().removeAll("git-added", "git-modified", "git-deleted", "git-untracked", "git-conflict");
                } else {
                    String prefix = switch (item.type()) {
                        case ADDED -> "+";
                        case MODIFIED -> "M";
                        case DELETED -> "D";
                        case UNTRACKED -> "?";
                        case CONFLICTING -> "!";
                        case MISSING -> "✗";
                    };
                    setText(prefix + "  " + item.path());
                    getStyleClass().removeAll("git-added", "git-modified", "git-deleted", "git-untracked", "git-conflict");
                    getStyleClass().add(switch (item.type()) {
                        case ADDED -> "git-added";
                        case MODIFIED -> "git-modified";
                        case DELETED, MISSING -> "git-deleted";
                        case UNTRACKED -> "git-untracked";
                        case CONFLICTING -> "git-conflict";
                    });
                }
            }
        });
        changesListView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && onFileSelected != null) {
                onFileSelected.accept(selected);
            }
        });

        // ── Commit section ─────────────────────────────────────
        Label commitTitle = new Label("COMMIT");
        commitTitle.getStyleClass().add("git-section-title");
        commitTitle.setPadding(new Insets(6, 8, 2, 8));

        commitMessageArea = new TextArea();
        commitMessageArea.setPromptText("Commit message...");
        commitMessageArea.getStyleClass().add("git-commit-input");
        commitMessageArea.setPrefRowCount(2);
        commitMessageArea.setMaxHeight(60);
        commitMessageArea.setWrapText(true);

        stageAllBtn = new Button("Stage All");
        stageAllBtn.getStyleClass().add("git-action-btn");
        stageAllBtn.setOnAction(e -> { if (onStageAll != null) onStageAll.run(); });

        commitBtn = new Button("✓ Commit");
        commitBtn.getStyleClass().add("git-action-btn");
        commitBtn.getStyleClass().add("git-commit-btn");
        commitBtn.setOnAction(e -> {
            String msg = commitMessageArea.getText();
            if (msg != null && !msg.isBlank() && onCommit != null) {
                onCommit.accept(msg, null);
                commitMessageArea.clear();
            }
        });

        HBox commitButtons = new HBox(6, stageAllBtn, commitBtn);
        commitButtons.setAlignment(Pos.CENTER_RIGHT);
        commitButtons.setPadding(new Insets(4, 8, 4, 8));

        VBox commitSection = new VBox(2, commitTitle, commitMessageArea, commitButtons);
        VBox.setMargin(commitMessageArea, new Insets(0, 8, 0, 8));

        // ── Diff viewer ────────────────────────────────────────
        Label diffTitle = new Label("DIFF");
        diffTitle.getStyleClass().add("git-section-title");
        diffTitle.setPadding(new Insets(6, 8, 2, 8));

        diffViewer = new TextFlow();
        diffViewer.getStyleClass().add("git-diff-viewer");
        diffViewer.setPadding(new Insets(6));

        diffScrollPane = new ScrollPane(diffViewer);
        diffScrollPane.setFitToWidth(true);
        diffScrollPane.setPrefHeight(200);
        diffScrollPane.getStyleClass().add("git-diff-scroll");

        VBox diffSection = new VBox(diffTitle, diffScrollPane);
        VBox.setVgrow(diffScrollPane, Priority.ALWAYS);

        // ── History section ────────────────────────────────────
        Label historyTitle = new Label("HISTORY");
        historyTitle.getStyleClass().add("git-section-title");
        historyTitle.setPadding(new Insets(6, 8, 2, 8));

        historyListView = new ListView<>(historyItems);
        historyListView.getStyleClass().add("git-history-list");
        historyListView.setPrefHeight(120);
        historyListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(GitService.CommitInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.shortHash() + "  " + item.message() + "  — " + item.author());
                }
            }
        });

        // ── Status bar ─────────────────────────────────────────
        statusLabel = new Label("");
        statusLabel.getStyleClass().add("git-status-label");
        statusLabel.setPadding(new Insets(4, 8, 4, 8));

        // ── Assemble ───────────────────────────────────────────
        root.getChildren().addAll(
                branchBar,
                changesTitle, changesListView,
                commitSection,
                diffSection,
                historyTitle, historyListView,
                statusLabel
        );
        VBox.setVgrow(diffScrollPane, Priority.ALWAYS);

        scrollableRoot = new ScrollPane(root);
        scrollableRoot.setFitToWidth(true);
        scrollableRoot.getStyleClass().add("git-panel-scroll");
    }

    public Node getView() {
        return scrollableRoot;
    }

    // ═══════════════════════════════════════════════════════════
    //  UPDATE METHODS (call from Platform.runLater)
    // ═══════════════════════════════════════════════════════════

    public void setChanges(List<GitService.FileStatus> changes) {
        Platform.runLater(() -> {
            changesItems.setAll(changes);
            branchLabel.setText("Git (" + changes.size() + " changes)");
        });
    }

    public void setBranches(List<GitService.BranchInfo> branches, String currentBranch) {
        Platform.runLater(() -> {
            String prev = branchCombo.getValue();
            branchCombo.getItems().clear();
            for (GitService.BranchInfo b : branches) {
                if (!b.isRemote()) {
                    branchCombo.getItems().add(b.name());
                }
            }
            // Restore selection without triggering checkout
            branchCombo.setOnAction(null);
            branchCombo.setValue(currentBranch);
            branchCombo.setOnAction(e -> {
                String selected = branchCombo.getValue();
                if (selected != null && !selected.equals(currentBranch) && onCheckoutBranch != null) {
                    onCheckoutBranch.accept(selected);
                }
            });
        });
    }

    public void setHistory(List<GitService.CommitInfo> commits) {
        Platform.runLater(() -> historyItems.setAll(commits));
    }

    public void setDiffContent(String diffText) {
        Platform.runLater(() -> {
            diffViewer.getChildren().clear();
            if (diffText == null || diffText.isEmpty()) {
                Text empty = new Text("No diff to display");
                empty.getStyleClass().add("git-diff-empty");
                diffViewer.getChildren().add(empty);
                return;
            }
            for (String line : diffText.split("\n")) {
                Text text = new Text(line + "\n");
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    text.getStyleClass().add("git-diff-add");
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    text.getStyleClass().add("git-diff-remove");
                } else if (line.startsWith("@@")) {
                    text.getStyleClass().add("git-diff-hunk");
                } else if (line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("---") || line.startsWith("+++")) {
                    text.getStyleClass().add("git-diff-header");
                } else {
                    text.getStyleClass().add("git-diff-context");
                }
                diffViewer.getChildren().add(text);
            }
        });
    }

    public void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    public void setNoRepo() {
        Platform.runLater(() -> {
            branchLabel.setText("Git (no repo)");
            changesItems.clear();
            historyItems.clear();
            diffViewer.getChildren().clear();
            branchCombo.getItems().clear();
            statusLabel.setText("No Git repository detected");
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  CALLBACKS
    // ═══════════════════════════════════════════════════════════

    public void setOnCheckoutBranch(Consumer<String> handler) { this.onCheckoutBranch = handler; }
    public void setOnPull(Runnable handler) { this.onPull = handler; }
    public void setOnPush(Runnable handler) { this.onPush = handler; }
    public void setOnRefresh(Runnable handler) { this.onRefresh = handler; }
    public void setOnCommit(BiConsumer<String, String> handler) { this.onCommit = handler; }
    public void setOnStageAll(Runnable handler) { this.onStageAll = handler; }
    public void setOnFileSelected(Consumer<GitService.FileStatus> handler) { this.onFileSelected = handler; }
}
