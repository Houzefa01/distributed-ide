package com.distributed_ide.javafx_client.editor;

import org.fxmisc.richtext.CodeArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advanced editor actions: toggle comment, duplicate line, move lines, etc.
 */
public class EditorActions {
    private static final Logger LOG = LoggerFactory.getLogger(EditorActions.class);

    /**
     * Toggle line comment (// for Java, Python, JavaScript)
     */
    public static void toggleLineComment(CodeArea codeArea, String language) {
        int caretLine = codeArea.getCurrentParagraph();
        String lineText = codeArea.getParagraph(caretLine).getText();
        
        String commentPrefix = getCommentPrefix(language);
        if (commentPrefix == null) return;

        int lineStart = codeArea.getAbsolutePosition(caretLine, 0);
        int lineEnd = codeArea.getAbsolutePosition(caretLine, lineText.length());

        String trimmed = lineText.trim();
        boolean isCommented = trimmed.startsWith(commentPrefix);

        if (isCommented) {
            // Remove comment
            String uncommented = lineText.replaceFirst("^\\s*" + escapeRegex(commentPrefix), "");
            codeArea.replaceText(lineStart, lineEnd, uncommented);
        } else {
            // Add comment
            String commented = lineText.replaceFirst("^(\\s*)", "$1" + commentPrefix + " ");
            codeArea.replaceText(lineStart, lineEnd, commented);
        }

        LOG.debug("Toggled line comment: {}", language);
    }

    /**
     * Delete entire line
     */
    public static void deleteLine(CodeArea codeArea) {
        int caretLine = codeArea.getCurrentParagraph();
        int lineStart = codeArea.getAbsolutePosition(caretLine, 0);
        int lineEnd = codeArea.getAbsolutePosition(caretLine + 1, 0);

        if (lineEnd > codeArea.getLength()) {
            lineEnd = codeArea.getLength();
        }

        codeArea.deleteText(lineStart, lineEnd);
        LOG.debug("Deleted line: {}", caretLine);
    }

    /**
     * Duplicate line
     */
    public static void duplicateLine(CodeArea codeArea) {
        int caretLine = codeArea.getCurrentParagraph();
        String lineText = codeArea.getParagraph(caretLine).getText();
        
        int lineStart = codeArea.getAbsolutePosition(caretLine, 0);
        int lineEnd = codeArea.getAbsolutePosition(caretLine + 1, 0);

        if (lineEnd > codeArea.getLength()) {
            // Last line without newline
            codeArea.insertText(lineEnd, "\n" + lineText);
        } else {
            codeArea.insertText(lineStart, lineText + "\n");
        }

        LOG.debug("Duplicated line: {}", caretLine);
    }

    /**
     * Move line up
     */
    public static void moveLineUp(CodeArea codeArea) {
        int caretLine = codeArea.getCurrentParagraph();
        if (caretLine == 0) return;

        String currentLine = codeArea.getParagraph(caretLine).getText();
        String previousLine = codeArea.getParagraph(caretLine - 1).getText();

        int currentStart = codeArea.getAbsolutePosition(caretLine, 0);
        int currentEnd = codeArea.getAbsolutePosition(caretLine + 1, 0);
        int previousStart = codeArea.getAbsolutePosition(caretLine - 1, 0);
        int previousEnd = codeArea.getAbsolutePosition(caretLine, 0);

        codeArea.replaceText(previousStart, currentEnd, currentLine + "\n" + previousLine);
        codeArea.moveTo(previousStart);

        LOG.debug("Moved line up: {} -> {}", caretLine, caretLine - 1);
    }

    /**
     * Move line down
     */
    public static void moveLineDown(CodeArea codeArea) {
        int caretLine = codeArea.getCurrentParagraph();
        if (caretLine == codeArea.getParagraphs().size() - 1) return;

        String currentLine = codeArea.getParagraph(caretLine).getText();
        String nextLine = codeArea.getParagraph(caretLine + 1).getText();

        int currentStart = codeArea.getAbsolutePosition(caretLine, 0);
        int nextEnd = codeArea.getAbsolutePosition(caretLine + 2, 0);

        if (nextEnd > codeArea.getLength()) {
            nextEnd = codeArea.getLength();
        }

        codeArea.replaceText(currentStart, nextEnd, nextLine + "\n" + currentLine);
        codeArea.moveTo(codeArea.getAbsolutePosition(caretLine + 1, 0));

        LOG.debug("Moved line down: {} -> {}", caretLine, caretLine + 1);
    }

    /**
     * Select entire line
     */
    public static void selectLine(CodeArea codeArea) {
        int caretLine = codeArea.getCurrentParagraph();
        int lineStart = codeArea.getAbsolutePosition(caretLine, 0);
        int lineEnd = codeArea.getAbsolutePosition(caretLine + 1, 0);

        if (lineEnd > codeArea.getLength()) {
            lineEnd = codeArea.getLength();
        }

        codeArea.selectRange(lineStart, lineEnd);
        LOG.debug("Selected line: {}", caretLine);
    }

    /**
     * Get the comment prefix for a language
     */
    public static String getCommentPrefix(String language) {
        if (language == null) return "//";

        return switch (language.toLowerCase()) {
            case "java", "javascript", "typescript", "c", "cpp", "go", "rust" -> "//";
            case "python", "bash", "shell", "yml", "yaml", "properties" -> "#";
            case "html", "xml", "svg" -> "<!--";
            case "sql" -> "--";
            case "css", "scss" -> "/*";
            default -> "//";
        };
    }

    /**
     * Escape regex special characters
     */
    private static String escapeRegex(String s) {
        return s.replace("\\", "\\\\").replace(".", "\\.");
    }
}
