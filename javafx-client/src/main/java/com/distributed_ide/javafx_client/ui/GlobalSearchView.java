package com.distributed_ide.javafx_client.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Global project search panel (Ctrl+Shift+F).
 * Searches file contents across the entire project with results grouped by file.
 */
public class GlobalSearchView {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalSearchView.class);
    private static final int MAX_RESULTS = 500;
    private static final int MAX_FILE_SIZE = 1024 * 1024; // 1 MB
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "node_modules", "target", ".git", ".idea", "build", "dist", ".gradle", "__pycache__", ".mvn"
    );
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "jar", "class", "exe", "dll", "so", "png", "jpg", "jpeg", "gif", "bmp",
            "ico", "pdf", "zip", "gz", "tar", "war", "ear"
    );

    private final VBox root;
    private final TextField searchField;
    private final CheckBox caseSensitive;
    private final CheckBox wholeWord;
    private final CheckBox regexMode;
    private final ListView<SearchResult> resultList;
    private final ObservableList<SearchResult> results = FXCollections.observableArrayList();
    private final Label statusLabel;

    private Path projectRoot;
    private final AtomicBoolean searching = new AtomicBoolean(false);
    private BiConsumer<String, Integer> onResultSelected;

    public GlobalSearchView() {
        searchField = new TextField();
        searchField.setPromptText("Search in files...");
        searchField.getStyleClass().add("global-search-input");

        caseSensitive = new CheckBox("Aa");
        caseSensitive.setTooltip(new Tooltip("Case Sensitive"));
        wholeWord = new CheckBox("W");
        wholeWord.setTooltip(new Tooltip("Whole Word"));
        regexMode = new CheckBox(".*");
        regexMode.setTooltip(new Tooltip("Regex"));

        HBox options = new HBox(4, caseSensitive, wholeWord, regexMode);
        options.setPadding(new Insets(2, 0, 2, 0));

        statusLabel = new Label("Prêt");
        statusLabel.getStyleClass().add("global-search-status");

        resultList = new ListView<>(results);
        resultList.getStyleClass().add("global-search-results");
        resultList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SearchResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if (item.isHeader) {
                    setText(null);
                    Label fileLabel = new Label(item.relativePath + "  (" + item.matchCount + " correspondances)");
                    fileLabel.getStyleClass().add("section-title");
                    setGraphic(fileLabel);
                } else {
                    setText(null);
                    Label lineNum = new Label(String.valueOf(item.lineNumber));
                    lineNum.setMinWidth(40);
                    lineNum.getStyleClass().add("label-muted");
                    Label lineText = new Label(item.lineText.trim());
                    lineText.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(lineText, Priority.ALWAYS);
                    HBox row = new HBox(4, lineNum, lineText);
                    row.setPadding(new Insets(0, 0, 0, 16));
                    setGraphic(row);
                }
            }
        });

        resultList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) navigateToResult();
        });
        resultList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) navigateToResult();
        });

        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) executeSearch();
        });

        VBox.setVgrow(resultList, Priority.ALWAYS);

        root = new VBox(4, searchField, options, statusLabel, resultList);
        root.setPadding(new Insets(6));
        root.getStyleClass().add("global-search-panel");
    }

    public VBox getView() {
        return root;
    }

    public void setProjectRoot(Path root) {
        this.projectRoot = root;
    }

    public void setOnResultSelected(BiConsumer<String, Integer> handler) {
        this.onResultSelected = handler;
    }

    public void focusSearch() {
        searchField.requestFocus();
        searchField.selectAll();
    }

    // ═══════════════════════════════════════════════════════════
    //  SEARCH EXECUTION
    // ═══════════════════════════════════════════════════════════

    public void executeSearch() {
        String query = searchField.getText();
        if (query == null || query.isEmpty() || projectRoot == null) return;
        if (searching.getAndSet(true)) return;

        results.clear();
        statusLabel.setText("Recherche en cours...");

        CompletableFuture.supplyAsync(() -> search(query))
                .thenAccept(searchResults -> Platform.runLater(() -> {
                    results.setAll(searchResults);
                    int matchCount = (int) searchResults.stream().filter(r -> !r.isHeader).count();
                    int fileCount = (int) searchResults.stream().filter(r -> r.isHeader).count();
                    statusLabel.setText(matchCount + " résultats dans " + fileCount + " fichiers");
                    searching.set(false);
                }));
    }

    private List<SearchResult> search(String query) {
        List<SearchResult> allResults = new ArrayList<>();
        boolean caseSens = caseSensitive.isSelected();
        boolean whole = wholeWord.isSelected();
        boolean regex = regexMode.isSelected();

        String searchQuery = caseSens ? query : query.toLowerCase();

        try {
            Files.walkFileTree(projectRoot, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 20, new SimpleFileVisitor<>() {
                int totalMatches = 0;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (EXCLUDED_DIRS.contains(name)) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (totalMatches >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                    if (!attrs.isRegularFile() || attrs.size() > MAX_FILE_SIZE) return FileVisitResult.CONTINUE;
                    String name = file.getFileName() != null ? file.getFileName().toString() : "";
                    int dot = name.lastIndexOf('.');
                    if (dot > 0 && BINARY_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase())) {
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        String relative = projectRoot.relativize(file).toString().replace('\\', '/');
                        List<SearchResult> fileResults = new ArrayList<>();

                        String[] lines = content.split("\n", -1);
                        for (int i = 0; i < lines.length && totalMatches < MAX_RESULTS; i++) {
                            String line = lines[i];
                            String compareLine = caseSens ? line : line.toLowerCase();
                            boolean match;
                            if (regex) {
                                try {
                                    match = java.util.regex.Pattern.compile(
                                            caseSens ? query : "(?i)" + query).matcher(line).find();
                                } catch (Exception e) {
                                    match = false;
                                }
                            } else if (whole) {
                                match = compareLine.matches(".*\\b" + java.util.regex.Pattern.quote(searchQuery) + "\\b.*");
                            } else {
                                match = compareLine.contains(searchQuery);
                            }
                            if (match) {
                                fileResults.add(new SearchResult(
                                        file.toString(), relative, i + 1, line, false, 0));
                                totalMatches++;
                            }
                        }

                        if (!fileResults.isEmpty()) {
                            allResults.add(new SearchResult(
                                    file.toString(), relative, 0, "", true, fileResults.size()));
                            allResults.addAll(fileResults);
                        }
                    } catch (IOException ignored) {
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.warn("Search failed", e);
        }

        return allResults;
    }

    private void navigateToResult() {
        SearchResult sel = resultList.getSelectionModel().getSelectedItem();
        if (sel != null && !sel.isHeader && onResultSelected != null) {
            onResultSelected.accept(sel.absolutePath, sel.lineNumber);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MODEL
    // ═══════════════════════════════════════════════════════════

    public static class SearchResult {
        public final String absolutePath;
        public final String relativePath;
        public final int lineNumber;
        public final String lineText;
        public final boolean isHeader;
        public final int matchCount;

        SearchResult(String absolutePath, String relativePath, int lineNumber,
                     String lineText, boolean isHeader, int matchCount) {
            this.absolutePath = absolutePath;
            this.relativePath = relativePath;
            this.lineNumber = lineNumber;
            this.lineText = lineText;
            this.isHeader = isHeader;
            this.matchCount = matchCount;
        }
    }
}
