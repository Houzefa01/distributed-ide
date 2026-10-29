package com.distributed_ide.javafx_client.editor;

import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.CodeArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CodeEditor Enhancement - Integrates keyboard actions and advanced editing features
 * This module is designed to be integrated into the existing CodeEditor class
 */
public class CodeEditorEnhancer {
    private static final Logger LOG = LoggerFactory.getLogger(CodeEditorEnhancer.class);

    private final CodeArea codeArea;
    private String currentLanguage = "java";
    private Runnable onContentModified;

    public CodeEditorEnhancer(CodeArea codeArea) {
        this.codeArea = codeArea;
        setupKeyboardShortcuts();
    }

    public void setCurrentLanguage(String language) {
        this.currentLanguage = language;
    }

    public void setOnContentModified(Runnable callback) {
        this.onContentModified = callback;
    }

    /**
     * Setup advanced keyboard shortcuts for the editor
     */
    private void setupKeyboardShortcuts() {
        codeArea.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                handleControlKey(event);
            } else if (event.isAltDown()) {
                handleAltKey(event);
            } else if (event.isControlDown() && event.isShiftDown()) {
                handleControlShiftKey(event);
            }
        });
    }

    private void handleControlKey(KeyEvent event) {
        switch (event.getCode()) {
            case SLASH:
                toggleLineComment();
                event.consume();
                break;
            case L:
                selectLine();
                event.consume();
                break;
            case D:
                duplicateLine();
                event.consume();
                break;
            case UP:
                moveLineUp();
                event.consume();
                break;
            case DOWN:
                moveLineDown();
                event.consume();
                break;
            default:
                break;
        }
    }

    private void handleAltKey(KeyEvent event) {
        switch (event.getCode()) {
            case UP:
                moveLineUp();
                event.consume();
                break;
            case DOWN:
                moveLineDown();
                event.consume();
                break;
            default:
                break;
        }
    }

    private void handleControlShiftKey(KeyEvent event) {
        switch (event.getCode()) {
            case K:
                deleteLine();
                event.consume();
                break;
            case SLASH:
                toggleBlockComment();
                event.consume();
                break;
            default:
                break;
        }
    }

    // ──────────────────────────────────────────────────────
    // Editing Actions
    // ──────────────────────────────────────────────────────

    /**
     * Toggle line comment
     */
    public void toggleLineComment() {
        try {
            EditorActions.toggleLineComment(codeArea, currentLanguage);
            notifyContentModified();
            LOG.debug("Toggled line comment in {}", currentLanguage);
        } catch (Exception e) {
            LOG.error("Failed to toggle comment", e);
        }
    }

    /**
     * Toggle block comment
     */
    public void toggleBlockComment() {
        // This would require more complex parsing
        // For now, show a message
        LOG.info("Block comment toggle not yet implemented");
    }

    /**
     * Join lines (opposite of split)
     */
    public void joinLines() {
        try {
            int caretLine = codeArea.getCurrentParagraph();
            if (caretLine < codeArea.getParagraphs().size() - 1) {
                String currentLine = codeArea.getParagraph(caretLine).getText();
                String nextLine = codeArea.getParagraph(caretLine + 1).getText();
                
                int currentStart = codeArea.getAbsolutePosition(caretLine, 0);
                int nextEnd = codeArea.getAbsolutePosition(caretLine + 2, 0);
                
                if (nextEnd > codeArea.getLength()) {
                    nextEnd = codeArea.getLength();
                }
                
                String joined = currentLine + " " + nextLine.trim();
                codeArea.replaceText(currentStart, nextEnd, joined);
                notifyContentModified();
            }
        } catch (Exception e) {
            LOG.error("Failed to join lines", e);
        }
    }

    /**
     * Delete entire line
     */
    public void deleteLine() {
        try {
            EditorActions.deleteLine(codeArea);
            notifyContentModified();
            LOG.debug("Deleted line");
        } catch (Exception e) {
            LOG.error("Failed to delete line", e);
        }
    }

    /**
     * Duplicate line
     */
    public void duplicateLine() {
        try {
            EditorActions.duplicateLine(codeArea);
            notifyContentModified();
            LOG.debug("Duplicated line");
        } catch (Exception e) {
            LOG.error("Failed to duplicate line", e);
        }
    }

    /**
     * Move line up
     */
    public void moveLineUp() {
        try {
            EditorActions.moveLineUp(codeArea);
            notifyContentModified();
            LOG.debug("Moved line up");
        } catch (Exception e) {
            LOG.error("Failed to move line up", e);
        }
    }

    /**
     * Move line down
     */
    public void moveLineDown() {
        try {
            EditorActions.moveLineDown(codeArea);
            notifyContentModified();
            LOG.debug("Moved line down");
        } catch (Exception e) {
            LOG.error("Failed to move line down", e);
        }
    }

    /**
     * Select entire line
     */
    public void selectLine() {
        try {
            EditorActions.selectLine(codeArea);
            LOG.debug("Selected line");
        } catch (Exception e) {
            LOG.error("Failed to select line", e);
        }
    }

    /**
     * Indent selected text
     */
    public void indent() {
        String selectedText = codeArea.getSelectedText();
        if (!selectedText.isEmpty()) {
            String indented = "    " + selectedText.replace("\n", "\n    ");
            codeArea.replaceSelection(indented);
            notifyContentModified();
        }
    }

    /**
     * Outdent selected text
     */
    public void outdent() {
        String selectedText = codeArea.getSelectedText();
        if (!selectedText.isEmpty()) {
            String outdented = selectedText.replace("\n    ", "\n").replace("^    ", "");
            codeArea.replaceSelection(outdented);
            notifyContentModified();
        }
    }

    /**
     * Convert to uppercase
     */
    public void toUpperCase() {
        String selectedText = codeArea.getSelectedText();
        if (!selectedText.isEmpty()) {
            codeArea.replaceSelection(selectedText.toUpperCase());
            notifyContentModified();
        }
    }

    /**
     * Convert to lowercase
     */
    public void toLowerCase() {
        String selectedText = codeArea.getSelectedText();
        if (!selectedText.isEmpty()) {
            codeArea.replaceSelection(selectedText.toLowerCase());
            notifyContentModified();
        }
    }

    /**
     * Sort selected lines
     */
    public void sortLines() {
        String selectedText = codeArea.getSelectedText();
        if (!selectedText.isEmpty()) {
            String sorted = selectedText.lines()
                    .sorted()
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            codeArea.replaceSelection(sorted);
            notifyContentModified();
        }
    }

    /**
     * Reverse selected lines
     */
    public void reverseLines() {
        String selectedText = codeArea.getSelectedText();
        if (!selectedText.isEmpty()) {
            String reversed = selectedText.lines()
                    .reduce("", (s, line) -> line + "\n" + s, String::concat);
            codeArea.replaceSelection(reversed);
            notifyContentModified();
        }
    }

    // ──────────────────────────────────────────────────────
    // Utility Methods
    // ──────────────────────────────────────────────────────

    private void notifyContentModified() {
        if (onContentModified != null) {
            onContentModified.run();
        }
    }

    /**
     * Get current caret position
     */
    public int[] getCaretPosition() {
        return new int[]{
                codeArea.getCurrentParagraph() + 1,
                codeArea.getCaretColumn() + 1
        };
    }

    /**
     * Set caret position
     */
    public void setCaretPosition(int line, int column) {
        try {
            int position = codeArea.getAbsolutePosition(line - 1, column - 1);
            codeArea.moveTo(position);
        } catch (Exception e) {
            LOG.warn("Invalid caret position: {}, {}", line, column);
        }
    }

    /**
     * Get word at caret
     */
    public String getWordAtCaret() {
        try {
            int caretPosition = codeArea.getCaretPosition();
            String text = codeArea.getText();
            
            int start = caretPosition;
            int end = caretPosition;
            
            while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
                start--;
            }
            
            while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
                end++;
            }
            
            return text.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Highlight word occurrences
     */
    public void highlightWord(String word) {
        // This would require integration with StyleSpans
        LOG.debug("Highlighting occurrences of: {}", word);
    }
}
