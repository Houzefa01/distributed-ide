package com.distributed_ide.javafx_client.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Quick Open dialog (Ctrl+P) — fuzzy file search across the project.
 * Shows a filterable list of all project files, ranked by fuzzy match score.
 */
public class QuickOpenDialog {

    private static final int MAX_RESULTS = 50;
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "node_modules", "target", ".git", ".idea", "build", "dist", ".gradle", "__pycache__", ".mvn"
    );

    private final Popup popup;
    private final TextField searchField;
    private final ListView<FileEntry> resultList;
    private final ObservableList<FileEntry> results = FXCollections.observableArrayList();
    private List<FileEntry> allFiles = Collections.emptyList();
    private java.util.function.Consumer<Path> onFileSelected;

    public QuickOpenDialog() {
        searchField = new TextField();
        searchField.setPromptText("Type file name to search...");
        searchField.getStyleClass().add("quick-open-search");

        resultList = new ListView<>(results);
        resultList.setPrefHeight(300);
        resultList.setPrefWidth(500);
        resultList.getStyleClass().add("quick-open-list");
        resultList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(FileEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.relativePath);
                }
            }
        });

        VBox content = new VBox(searchField, resultList);
        content.setPadding(new Insets(4));
        content.setSpacing(2);
        content.getStyleClass().add("quick-open-container");

        popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(content);

        // Filter on key typed
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterFiles(newVal));

        // Navigate list with arrow keys
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                resultList.requestFocus();
                if (!results.isEmpty()) resultList.getSelectionModel().select(0);
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                selectCurrent();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                e.consume();
            }
        });

        resultList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                selectCurrent();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                e.consume();
            }
        });

        resultList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) selectCurrent();
        });
    }

    public void setOnFileSelected(java.util.function.Consumer<Path> handler) {
        this.onFileSelected = handler;
    }

    /**
     * Shows the Quick Open dialog centered on the given window.
     * Scans the project root asynchronously if not already cached.
     */
    public void show(Window owner, Path projectRoot) {
        if (projectRoot == null) return;

        searchField.clear();
        results.clear();

        // Position popup centered-top
        double x = owner.getX() + (owner.getWidth() - 510) / 2;
        double y = owner.getY() + 80;
        popup.show(owner, x, y);
        Platform.runLater(searchField::requestFocus);

        // Scan files async
        CompletableFuture.supplyAsync(() -> scanFiles(projectRoot))
                .thenAccept(files -> Platform.runLater(() -> {
                    allFiles = files;
                    results.setAll(files.subList(0, Math.min(MAX_RESULTS, files.size())));
                }));
    }

    public void hide() {
        popup.hide();
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    // ═══════════════════════════════════════════════════════════
    //  FUZZY SEARCH
    // ═══════════════════════════════════════════════════════════

    private void filterFiles(String query) {
        if (query == null || query.isBlank()) {
            results.setAll(allFiles.subList(0, Math.min(MAX_RESULTS, allFiles.size())));
            return;
        }
        String lowerQuery = query.toLowerCase();
        List<FileEntry> scored = allFiles.stream()
                .map(f -> new ScoredEntry(f, fuzzyScore(f.relativePath.toLowerCase(), lowerQuery)))
                .filter(se -> se.score > 0)
                .sorted(Comparator.comparingInt(se -> -se.score))
                .limit(MAX_RESULTS)
                .map(se -> se.entry)
                .collect(Collectors.toList());
        results.setAll(scored);
        if (!scored.isEmpty()) resultList.getSelectionModel().select(0);
    }

    /**
     * Simple fuzzy matching: characters of the query must appear in order in the target.
     * Score increases for consecutive matches, filename matches, and exact substring matches.
     */
    static int fuzzyScore(String target, String query) {
        int qi = 0, score = 0, consecutive = 0;
        boolean prevMatch = false;
        String fileName = target.substring(target.lastIndexOf('/') + 1);

        // Bonus for exact substring match in filename
        if (fileName.contains(query)) score += 50;
        else if (target.contains(query)) score += 20;

        for (int ti = 0; ti < target.length() && qi < query.length(); ti++) {
            if (target.charAt(ti) == query.charAt(qi)) {
                qi++;
                score += 1;
                if (prevMatch) {
                    consecutive++;
                    score += consecutive * 2;
                } else {
                    consecutive = 0;
                }
                // Bonus for matching at word boundary
                if (ti == 0 || target.charAt(ti - 1) == '/' || target.charAt(ti - 1) == '.'
                        || target.charAt(ti - 1) == '_' || target.charAt(ti - 1) == '-') {
                    score += 5;
                }
                prevMatch = true;
            } else {
                prevMatch = false;
                consecutive = 0;
            }
        }
        return qi == query.length() ? score : 0;
    }

    // ═══════════════════════════════════════════════════════════
    //  FILE SCANNING
    // ═══════════════════════════════════════════════════════════

    private List<FileEntry> scanFiles(Path root) {
        List<FileEntry> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 20, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (EXCLUDED_DIRS.contains(dirName)) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        String relative = root.relativize(file).toString().replace('\\', '/');
                        files.add(new FileEntry(relative, file));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // ignore
        }
        files.sort(Comparator.comparing(f -> f.relativePath));
        return files;
    }

    private void selectCurrent() {
        FileEntry sel = resultList.getSelectionModel().getSelectedItem();
        if (sel != null && onFileSelected != null) {
            popup.hide();
            onFileSelected.accept(sel.absolutePath);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MODEL
    // ═══════════════════════════════════════════════════════════

    public static class FileEntry {
        public final String relativePath;
        public final Path absolutePath;

        FileEntry(String relativePath, Path absolutePath) {
            this.relativePath = relativePath;
            this.absolutePath = absolutePath;
        }

        @Override
        public String toString() {
            return relativePath;
        }
    }

    private static class ScoredEntry {
        final FileEntry entry;
        final int score;

        ScoredEntry(FileEntry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }
}
