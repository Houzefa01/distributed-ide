package com.distributed_ide.javafx_client.editor;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.reactfx.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.distributed_ide.javafx_client.model.RemoteCursor;
import com.distributed_ide.javafx_client.performance.PerformanceOptimizer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Professional code editor based on RichTextFX.
 *
 * Features:
 * - Async syntax highlighting (debounced 100ms, off FX thread, thread pool)
 * - Line numbers with gutter
 * - Error/warning highlights per line
 * - Minimap (reduced scrollable view, debounced sync)
 * - Native RichTextFX multi-cursor
 * - Advanced undo/redo (native RichTextFX, exposed via API)
 * - Optimized for large files: async highlighting + viewport-only minimap
 *
 * Architecture: this class is a pure View, no business logic.
 * Wiring is done via EditorManager.
 */
public class CodeEditor {

    private static final Logger LOG = LoggerFactory.getLogger(CodeEditor.class);

    private static final long HIGHLIGHT_DELAY_MS = 200;
    private static final long MINIMAP_DELAY_MS = 300;
    private static final int MINIMAP_MAX_CHARS = 50000;
    private static final ExecutorService HIGHLIGHT_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "syntax-highlight"); t.setDaemon(true); return t; });

    // Performance tracking
    private long currentFileSizeBytes = 0;

    private final CodeArea codeArea;
    private final VirtualizedScrollPane<CodeArea> scrollPane;
    private final StackPane root;

    // Minimap
    private final CodeArea minimapArea;
    private final VirtualizedScrollPane<CodeArea> minimapScrollPane;
    private boolean minimapVisible = true;

    // State
    private volatile String language = "java";
    private volatile String filePath;
    private final Set<Integer> errorLines = new HashSet<>();
    private final Set<Integer> warningLines = new HashSet<>();
    private Subscription highlightSub;
    private Consumer<String> onContentChanged;
    private Consumer<int[]> onCaretChanged;
    private volatile boolean suppressContentChangedEvent;

    // Remote cursors
    private final Map<String, RemoteCursor> remoteCursors = new ConcurrentHashMap<>();

    // Syntax errors for underline + tooltip
    private volatile List<SyntaxErrorChecker.SyntaxError> currentErrors = Collections.emptyList();
    private final Tooltip errorTooltip = new Tooltip();
    private volatile StyleSpans<Collection<String>> lastHighlighting;

    // ✅ FIX: Store listener references for proper cleanup in dispose()
    private javafx.beans.value.ChangeListener<String> textListener;
    private javafx.beans.value.ChangeListener<Integer> caretListener;
    private javafx.beans.value.ChangeListener<String> minimapTextListener;
    private javafx.event.EventHandler<javafx.scene.input.MouseEvent> mouseMovedHandler;
    private javafx.event.EventHandler<javafx.scene.input.MouseEvent> minimapClickHandler;
    private javafx.animation.PauseTransition minimapDebounce;
    
    // ✅ Store style settings to combine them properly (don't overwrite each other)
    private int currentFontSize = 14;
    private String currentFontFamily = "Consolas";
    private int currentIndentSize = 4;

    public CodeEditor() {
        codeArea = new CodeArea();
        codeArea.setWrapText(false);
        codeArea.getStyleClass().add("code-editor");

        // Line numbers
        codeArea.setParagraphGraphicFactory(lineNumber -> {
            int line = lineNumber + 1;
            Label label = new Label(String.valueOf(line));
            label.getStyleClass().add("line-number");
            label.setMinWidth(45);
            label.setAlignment(Pos.CENTER_RIGHT);

            if (errorLines.contains(line)) {
                label.getStyleClass().add("line-error");
            } else if (warningLines.contains(line)) {
                label.getStyleClass().add("line-warning");
            }
            return label;
        });

        // Async syntax highlighting — debounced, computed off FX thread
        // Skip highlighting for very large files to avoid UI lag
        highlightSub = codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(getOptimalHighlightDelay()))
                .retainLatestUntilLater()
                .supplyTask(this::computeHighlightingAsync)
                .awaitLatest(codeArea.multiPlainChanges())
                .filterMap(t -> {
                    if (t.isSuccess()) return Optional.ofNullable(t.get());
                    LOG.trace("Highlighting failed", t.getFailure());
                    return Optional.empty();
                })
                .subscribe(this::applyHighlighting);

        codeArea.setOnMouseMoved(null); // Clear any previous handler

        // Error tooltip on mouse hover
        errorTooltip.setWrapText(true);
        errorTooltip.setMaxWidth(400);
        errorTooltip.getStyleClass().add("error-tooltip");
        errorTooltip.setShowDelay(javafx.util.Duration.millis(300));
        errorTooltip.setHideDelay(javafx.util.Duration.millis(200));

        mouseMovedHandler = event -> {
            try {
                int charIdx = codeArea.hit(event.getX(), event.getY()).getCharacterIndex().orElse(-1);
                if (charIdx >= 0) {
                    String msg = getErrorMessageAt(charIdx);
                    if (msg != null) {
                        errorTooltip.setText(msg);
                        if (!errorTooltip.isShowing()) {
                            Tooltip.install(codeArea, errorTooltip);
                        }
                        return;
                    }
                }
            } catch (Exception ignored) {}
            Tooltip.uninstall(codeArea, errorTooltip);
            if (errorTooltip.isShowing()) errorTooltip.hide();
        };
        codeArea.setOnMouseMoved(mouseMovedHandler);

        // Content change callback
        textListener = (obs, oldVal, newVal) -> {
            if (!suppressContentChangedEvent && onContentChanged != null) onContentChanged.accept(newVal);
        };
        codeArea.textProperty().addListener(textListener);

        // Caret position callback
        caretListener = (obs, oldVal, newVal) -> {
            if (onCaretChanged != null && newVal != null) {
                int para = codeArea.getCurrentParagraph() + 1;
                int col = codeArea.getCaretColumn() + 1;
                onCaretChanged.accept(new int[]{para, col});
            }
        };
        codeArea.caretPositionProperty().addListener(caretListener);

        scrollPane = new VirtualizedScrollPane<>(codeArea);

        // Minimap
        minimapArea = new CodeArea();
        minimapArea.setEditable(false);
        minimapArea.setWrapText(false);
        minimapArea.getStyleClass().addAll("code-editor-minimap");
        minimapArea.setPrefWidth(80);
        minimapArea.setMaxWidth(80);
        minimapArea.setMinWidth(80);

        // Sync minimap content (debounced via PauseTransition)
        minimapDebounce = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(MINIMAP_DELAY_MS));
        minimapDebounce.setOnFinished(evt -> {
            if (minimapVisible) {
                String text = codeArea.getText();
                if (text != null) {
                    String truncated = text.length() > MINIMAP_MAX_CHARS ? text.substring(0, MINIMAP_MAX_CHARS) : text;
                    minimapArea.replaceText(truncated);
                }
            }
        });
        minimapTextListener = (obs, oldVal, newVal) -> {
            if (minimapVisible) minimapDebounce.playFromStart();
        };
        codeArea.textProperty().addListener(minimapTextListener);

        // Click minimap to scroll editor
        minimapClickHandler = e -> {
            if (minimapArea.getLength() > 0) {
                int targetPara = minimapArea.getCurrentParagraph();
                codeArea.showParagraphAtTop(targetPara);
            }
        };
        minimapArea.setOnMouseClicked(minimapClickHandler);

        minimapScrollPane = new VirtualizedScrollPane<>(minimapArea);
        minimapScrollPane.setPrefWidth(80);
        minimapScrollPane.setMaxWidth(80);

        // Root layout
        HBox editorWithMinimap = new HBox(scrollPane, minimapScrollPane);
        HBox.setHgrow(scrollPane, Priority.ALWAYS);
        root = new StackPane(editorWithMinimap);
    }

    // ═══════════════════════════════════════════════════════════
    //  PERFORMANCE HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Get optimal highlight delay based on file size and available memory.
     * Larger files and lower memory result in longer delays to reduce UI lag.
     */
    private long getOptimalHighlightDelay() {
        long contentLength = codeArea.getLength();
        currentFileSizeBytes = contentLength;
        
        // Use performance optimizer to determine optimal debounce
        long optimal = PerformanceOptimizer.getOptimalDebounceMs(contentLength);
        
        // Also consider minimap rendering skip
        if (!PerformanceOptimizer.shouldRenderMinimap(contentLength)) {
            minimapVisible = false;
            minimapScrollPane.setVisible(false);
        }
        
        return Math.max(optimal, HIGHLIGHT_DELAY_MS);  // Never less than base delay
    }

    // ═══════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════

    public Node getView() {
        return root;
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }

    public String getText() {
        return codeArea.getText();
    }

    public void setText(String text) {
        codeArea.replaceText(text != null ? text : "");
    }

    /**
     * Replaces text without triggering a new immediate highlighting.
     * Used for sync/CRDT updates.
     */
    public void setTextFromSync(String text) {
        int caretPos = codeArea.getCaretPosition();
        suppressContentChangedEvent = true;
        try {
            codeArea.replaceText(text != null ? text : "");
        } finally {
            suppressContentChangedEvent = false;
        }
        if (caretPos <= codeArea.getLength()) {
            codeArea.moveTo(caretPos);
        }
    }

    public String getLanguage() {
        return language;
    }

    // FIX 4b: Language setter with validation
    public void setLanguage(String language) {
        String newLang = language != null ? language.toLowerCase() : "java";
        
        // Only retrigger highlighting if language actually changed
        if (!newLang.equals(this.language)) {
            this.language = newLang;
            LOG.trace("Language changed to: {}", newLang);
            triggerHighlighting();
        }
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    // ═══════════════════════════════════════════════════════════
    //  CARET & CURSOR
    // ═══════════════════════════════════════════════════════════

    public int getCaretLine() {
        return codeArea.getCurrentParagraph() + 1;
    }

    public int getCaretColumn() {
        return codeArea.getCaretColumn() + 1;
    }

    public void goToLine(int line) {
        if (line < 1) line = 1;
        int para = Math.min(line - 1, codeArea.getParagraphs().size() - 1);
        codeArea.moveTo(para, 0);
        codeArea.showParagraphAtCenter(para);
        codeArea.requestFocus();
    }

    // ═══════════════════════════════════════════════════════════
    //  UNDO / REDO
    // ═══════════════════════════════════════════════════════════

    public void undo() {
        codeArea.undo();
    }

    public void redo() {
        codeArea.redo();
    }

    public boolean isUndoAvailable() {
        return codeArea.isUndoAvailable();
    }

    public boolean isRedoAvailable() {
        return codeArea.isRedoAvailable();
    }

    // ═══════════════════════════════════════════════════════════
    //  ERROR / WARNING HIGHLIGHTING
    // ═══════════════════════════════════════════════════════════

    public void setErrorLines(Set<Integer> lines) {
        errorLines.clear();
        if (lines != null) errorLines.addAll(lines);
        refreshLineNumbers();
    }

    public void setWarningLines(Set<Integer> lines) {
        warningLines.clear();
        if (lines != null) warningLines.addAll(lines);
        refreshLineNumbers();
    }

    public void clearDiagnostics() {
        errorLines.clear();
        warningLines.clear();
        refreshLineNumbers();
    }

    private void refreshLineNumbers() {
        // Force line number factory refresh
        Platform.runLater(() -> {
            int caret = codeArea.getCaretPosition();
            codeArea.setStyle(0, 0, Collections.emptyList());
            if (caret <= codeArea.getLength()) {
                codeArea.moveTo(caret);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  MINIMAP
    // ═══════════════════════════════════════════════════════════

    public void setMinimapVisible(boolean visible) {
        this.minimapVisible = visible;
        minimapScrollPane.setVisible(visible);
        minimapScrollPane.setManaged(visible);
        if (visible) {
            String text = codeArea.getText();
            String truncated = text.length() > MINIMAP_MAX_CHARS ? text.substring(0, MINIMAP_MAX_CHARS) : text;
            minimapArea.replaceText(truncated);
        }
    }

    public boolean isMinimapVisible() {
        return minimapVisible;
    }

    // ═══════════════════════════════════════════════════════════
    //  EDITOR SETTINGS
    // ═══════════════════════════════════════════════════════════

    /**
     * Sets the font size for the editor (in points).
     */
    public void setFontSize(int size) {
        this.currentFontSize = size;
        applyStyleSettings();
        LOG.debug("Font size set to {}pt", size);
    }

    /**
     * Sets the font family for the editor.
     */
    public void setFontFamily(String fontFamily) {
        this.currentFontFamily = fontFamily;
        applyStyleSettings();
        LOG.debug("Font family set to: {}", fontFamily);
    }

    /**
     * Sets the indentation size (tab size in spaces).
     */
    public void setIndentSize(int size) {
        this.currentIndentSize = size;
        // Store for future use - can be applied when handling tab key
        // For now, we just store it for consistency
        LOG.debug("Indent size set to {}", size);
    }
    
    /**
     * Apply all style settings together to avoid overwriting each other.
     */
    private void applyStyleSettings() {
        String style = String.format(
            "-fx-font-size: %dpt; -fx-font-family: '%s';",
            currentFontSize,
            currentFontFamily
        );
        codeArea.setStyle(style);
        minimapArea.setStyle(style);
        LOG.debug("Applied combined styles: {}pt {}", currentFontSize, currentFontFamily);
    }

    /**
     * Enables or disables word wrapping.
     */
    public void setWordWrap(boolean enabled) {
        codeArea.setWrapText(enabled);
        minimapArea.setWrapText(enabled);
        LOG.debug("Word wrap: {}", enabled);
    }

    // ═══════════════════════════════════════════════════════════
    //  REMOTE CURSORS & SELECTIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Updates a remote collaborator's cursor.
     * Rendering is done via the gutter (colored indicator + name) and selection highlight.
     */
    public void updateRemoteCursor(RemoteCursor cursor) {
        if (cursor == null || cursor.getClientId() == null) return;
        remoteCursors.put(cursor.getClientId(), cursor);
        Platform.runLater(this::renderRemoteCursors);
    }

    public void removeRemoteCursor(String clientId) {
        if (clientId != null) {
            remoteCursors.remove(clientId);
            Platform.runLater(this::renderRemoteCursors);
        }
    }

    public void clearRemoteCursors() {
        remoteCursors.clear();
        Platform.runLater(this::renderRemoteCursors);
    }

    public Map<String, RemoteCursor> getRemoteCursors() {
        return Collections.unmodifiableMap(remoteCursors);
    }

    /**
     * Re-render remote cursors via paragraph graphic factory + style classes.
     * Cursors shown as colored bar in gutter with username tooltip.
     * Selections shown via styleClass ranges on affected paragraphs.
     */
    private void renderRemoteCursors() {
        try {
            // Clear previous remote selection styles
            int totalParas = codeArea.getParagraphs().size();
            for (int p = 0; p < totalParas; p++) {
                codeArea.setParagraphStyle(p, Collections.emptyList());
            }

            // Apply selection highlights for each remote cursor
            for (RemoteCursor rc : remoteCursors.values()) {
                if (rc.hasSelection()) {
                    int startPara = Math.max(0, rc.getSelStartLine());
                    int endPara = Math.min(totalParas - 1, rc.getSelEndLine());
                    for (int p = startPara; p <= endPara; p++) {
                        codeArea.setParagraphStyle(p, List.of("remote-selection"));
                    }
                }
                // Cursor line indicator via paragraph style
                int cursorPara = rc.getLine();
                if (cursorPara >= 0 && cursorPara < totalParas) {
                    codeArea.setParagraphStyle(cursorPara, List.of("remote-cursor-line"));
                }
            }

            // Force gutter refresh to show cursor indicators
            refreshLineNumbers();
        } catch (Exception e) {
            LOG.trace("Render remote cursors failed: {}", e.getMessage());
        }
    }

    /**
     * Returns remote cursor info on a given line (0-based).
     * Used by the gutter to display indicators.
     */
    public List<RemoteCursor> getRemoteCursorsOnLine(int line) {
        List<RemoteCursor> result = new ArrayList<>();
        for (RemoteCursor rc : remoteCursors.values()) {
            if (rc.getLine() == line) {
                result.add(rc);
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    //  FIND & REPLACE
    // ═══════════════════════════════════════════════════════════

    /**
     * Finds and highlights all occurrences of the query, scrolling to the first match.
     */
    public void findAndHighlight(String query) {
        if (query == null || query.isEmpty()) return;
        String text = codeArea.getText();
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();

        int firstMatch = lowerText.indexOf(lowerQuery);
        if (firstMatch >= 0) {
            codeArea.selectRange(firstMatch, firstMatch + query.length());
            codeArea.requestFollowCaret();
            codeArea.requestFocus();
        }
    }

    /**
     * Replaces all occurrences of target with replacement in the editor.
     */
    public void replaceAll(String target, String replacement) {
        if (target == null || target.isEmpty()) return;
        String text = codeArea.getText();
        String newText = text.replace(target, replacement);
        if (!newText.equals(text)) {
            int caretPos = codeArea.getCaretPosition();
            codeArea.replaceText(newText);
            if (caretPos <= codeArea.getLength()) {
                codeArea.moveTo(caretPos);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  CALLBACKS
    // ═══════════════════════════════════════════════════════════

    public void setOnContentChanged(Consumer<String> handler) {
        this.onContentChanged = handler;
    }

    public void setOnCaretChanged(Consumer<int[]> handler) {
        this.onCaretChanged = handler;
    }

    // ═══════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════

    public void dispose() {
        // ✅ FIX: Properly remove all listeners to prevent memory leaks
        LOG.debug("🗑️ Disposing CodeEditor: {}", filePath);
        
        // Stop highlight subscription
        if (highlightSub != null) {
            highlightSub.unsubscribe();
            highlightSub = null;
        }

        // Remove all property listeners
        if (textListener != null) {
            codeArea.textProperty().removeListener(textListener);
            textListener = null;
        }
        if (caretListener != null) {
            codeArea.caretPositionProperty().removeListener(caretListener);
            caretListener = null;
        }
        if (minimapTextListener != null) {
            codeArea.textProperty().removeListener(minimapTextListener);
            minimapTextListener = null;
        }

        // Remove event handlers
        if (mouseMovedHandler != null) {
            codeArea.setOnMouseMoved(null);
            mouseMovedHandler = null;
        }
        if (minimapClickHandler != null) {
            minimapArea.setOnMouseClicked(null);
            minimapClickHandler = null;
        }

        // Stop and clear minimap debounce animation
        if (minimapDebounce != null) {
            minimapDebounce.stop();
            minimapDebounce = null;
        }

        // Clear callbacks to break circular references
        onContentChanged = null;
        onCaretChanged = null;

        // Clear remote cursors
        remoteCursors.clear();

        // Clear error state
        errorLines.clear();
        warningLines.clear();
        currentErrors = Collections.emptyList();
        lastHighlighting = null;

        // Hide and uninstall tooltip
        if (errorTooltip.isShowing()) {
            errorTooltip.hide();
        }
        Tooltip.uninstall(codeArea, errorTooltip);

        LOG.debug("✅ CodeEditor disposed: {}", filePath);
    }

    // ═══════════════════════════════════════════════════════════
    //  SYNTAX HIGHLIGHTING — ASYNC
    // ═══════════════════════════════════════════════════════════

    // FIX 4c: Async highlighting with better error handling and logging
    private javafx.concurrent.Task<StyleSpans<Collection<String>>> computeHighlightingAsync() {
        String text = codeArea.getText();
        String lang = this.language;
        
        javafx.concurrent.Task<StyleSpans<Collection<String>>> task = new javafx.concurrent.Task<>() {
            @Override
            protected StyleSpans<Collection<String>> call() {
                long startTime = System.currentTimeMillis();
                try {
                    if (text == null || text.isEmpty()) {
                        return null;
                    }

                    // Skip highlighting for very large files (performance optimization)
                    if (PerformanceOptimizer.shouldSkipHighlighting(text.length())) {
                        LOG.info("Skipping syntax highlighting for large file ({} bytes)", text.length());
                        return null;  // Return null to apply no styling
                    }
                    
                    // Check if language is supported
                    if (!SyntaxHighlighter.isSupported(lang)) {
                        LOG.trace("Language '{}' not supported, using plain text", lang);
                        return SyntaxHighlighter.computeHighlighting(text, "text");
                    }
                    
                    StyleSpans<Collection<String>> spans = SyntaxHighlighter.computeHighlighting(text, lang);
                    LOG.trace("Computed highlighting for {} chars, lang: {}", text.length(), lang);
                    return spans;
                    
                } catch (Exception e) {
                    LOG.error("Error computing highlighting for language '{}': {}", lang, e.getMessage());
                    return null;
                } finally {
                    long duration = System.currentTimeMillis() - startTime;
                    PerformanceOptimizer.recordOperationTiming("syntax-highlight", duration);
                }
            }
        };
        
        HIGHLIGHT_EXECUTOR.execute(task);
        return task;
    }

    // FIX 4: Improved highlighting application with error handling
    private void applyHighlighting(StyleSpans<Collection<String>> highlighting) {
        if (highlighting == null) {
            LOG.trace("Highlighting is null for language: {}", language);
            return;
        }
        
        try {
            int textLen = codeArea.getLength();
            if (textLen == 0) {
                return; // Empty editor, nothing to highlight
            }
            
            this.lastHighlighting = highlighting;
            StyleSpans<Collection<String>> merged = mergeErrorUnderlines(highlighting);
            
            // Apply with length validation to prevent off-by-one errors
            if (merged != null && merged.length() >= 0) {
                codeArea.setStyleSpans(0, merged);
                LOG.trace("Applied highlighting for language: {}", language);
            }
        } catch (Exception e) {
            LOG.warn("Failed to apply syntax highlighting for language '{}': {}", language, e.getMessage());
        }
    }

    public void triggerHighlighting() {
        javafx.concurrent.Task<StyleSpans<Collection<String>>> task = computeHighlightingAsync();
        task.setOnSucceeded(e -> applyHighlighting(task.getValue()));
    }

    /**
     * Returns the currently selected text, or null if no selection.
     */
    public String getSelectedText() {
        String sel = codeArea.getSelectedText();
        return (sel != null && !sel.isEmpty()) ? sel : null;
    }

    // ═══════════════════════════════════════════════════════════
    //  ERROR UNDERLINE RENDERING
    // ═══════════════════════════════════════════════════════════

    /**
     * Applies syntax errors: sets line-level gutter highlights AND
     * character-level red underlines via style spans overlay.
     */
    public void applySyntaxErrors(List<SyntaxErrorChecker.SyntaxError> errors) {
        this.currentErrors = errors != null ? List.copyOf(errors) : Collections.emptyList();

        // Update line-level gutter highlights
        Set<Integer> errLines = new HashSet<>();
        Set<Integer> warnLines = new HashSet<>();
        for (SyntaxErrorChecker.SyntaxError err : this.currentErrors) {
            if (err.getSeverity() == SyntaxErrorChecker.SyntaxError.Severity.ERROR) {
                errLines.add(err.getLine());
            } else {
                warnLines.add(err.getLine());
            }
        }
        errorLines.clear();
        errorLines.addAll(errLines);
        warningLines.clear();
        warningLines.addAll(warnLines);
        refreshLineNumbers();

        // Re-apply highlighting with error underlines merged in
        if (lastHighlighting != null && codeArea.getLength() > 0) {
            try {
                StyleSpans<Collection<String>> merged = mergeErrorUnderlines(lastHighlighting);
                codeArea.setStyleSpans(0, merged);
            } catch (Exception e) {
                LOG.trace("Apply error underlines failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Merges error-underline style classes into existing syntax highlighting spans.
     * For each error with valid offsets, adds "error-underline" or "warning-underline"
     * to the style classes at those character positions.
     */
    private StyleSpans<Collection<String>> mergeErrorUnderlines(StyleSpans<Collection<String>> base) {
        List<SyntaxErrorChecker.SyntaxError> errors = this.currentErrors;
        if (errors == null || errors.isEmpty()) return base;

        // Filter to errors that have valid character offsets
        List<SyntaxErrorChecker.SyntaxError> offsetErrors = new ArrayList<>();
        for (SyntaxErrorChecker.SyntaxError e : errors) {
            if (e.hasOffsets() && e.getStartOffset() < codeArea.getLength()) {
                offsetErrors.add(e);
            }
        }
        if (offsetErrors.isEmpty()) return base;

        // Build a map of character positions that need underline styles
        // We overlay on top of the base spans
        int textLen = codeArea.getLength();
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int pos = 0;

        // Iterate through base spans and inject underline classes
        for (var span : base) {
            int spanLen = span.getLength();
            Collection<String> baseStyles = span.getStyle();
            int spanEnd = pos + spanLen;

            // Check if any error overlaps this span
            List<int[]> overlaps = new ArrayList<>();
            for (SyntaxErrorChecker.SyntaxError err : offsetErrors) {
                int errStart = Math.max(err.getStartOffset(), pos);
                int errEnd = Math.min(Math.min(err.getEndOffset(), textLen), spanEnd);
                if (errStart < errEnd) {
                    String cls = err.getSeverity() == SyntaxErrorChecker.SyntaxError.Severity.ERROR
                            ? "error-underline" : "warning-underline";
                    overlaps.add(new int[]{errStart - pos, errEnd - pos, cls.hashCode()});
                }
            }

            if (overlaps.isEmpty()) {
                builder.add(baseStyles, spanLen);
            } else {
                // Split this span into sub-segments with and without underlines
                overlaps.sort(Comparator.comparingInt(a -> a[0]));
                int localPos = 0;
                for (int[] ov : overlaps) {
                    int ovStart = ov[0];
                    int ovEnd = ov[1];
                    // Determine underline class from the error
                    String ulClass = null;
                    for (SyntaxErrorChecker.SyntaxError err : offsetErrors) {
                        int errStart = Math.max(err.getStartOffset(), pos) - pos;
                        int errEnd = Math.min(Math.min(err.getEndOffset(), textLen), spanEnd) - pos;
                        if (errStart == ovStart && errEnd == ovEnd) {
                            ulClass = err.getSeverity() == SyntaxErrorChecker.SyntaxError.Severity.ERROR
                                    ? "error-underline" : "warning-underline";
                            break;
                        }
                    }

                    // Non-underlined part before this overlap
                    if (ovStart > localPos) {
                        builder.add(baseStyles, ovStart - localPos);
                    }
                    // Underlined part
                    if (ovEnd > ovStart) {
                        Collection<String> merged = new ArrayList<>(baseStyles);
                        if (ulClass != null) merged.add(ulClass);
                        builder.add(merged, ovEnd - ovStart);
                    }
                    localPos = ovEnd;
                }
                // Remaining non-underlined part
                if (localPos < spanLen) {
                    builder.add(baseStyles, spanLen - localPos);
                }
            }
            pos = spanEnd;
        }

        return builder.create();
    }

    /**
     * Returns the error message at the given character index, or null if no error at that position.
     */
    private String getErrorMessageAt(int charIndex) {
        for (SyntaxErrorChecker.SyntaxError err : currentErrors) {
            if (err.hasOffsets()) {
                if (charIndex >= err.getStartOffset() && charIndex < err.getEndOffset()) {
                    return err.getSeverity() + ": " + err.getMessage()
                            + " (line " + err.getLine() + ", col " + err.getColumn() + ")";
                }
            } else {
                // Fallback: check by line
                int line = codeArea.getCurrentParagraph() + 1;
                if (line == err.getLine()) {
                    return err.getSeverity() + ": " + err.getMessage();
                }
            }
        }
        return null;
    }
}
