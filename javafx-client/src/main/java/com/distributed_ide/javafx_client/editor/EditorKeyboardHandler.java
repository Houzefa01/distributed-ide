package com.distributed_ide.javafx_client.editor;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.CodeArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advanced keyboard shortcuts for code editor.
 * Implements VSCode-like keybindings
 */
public class EditorKeyboardHandler {
    private static final Logger LOG = LoggerFactory.getLogger(EditorKeyboardHandler.class);
    
    private final CodeArea codeArea;
    private EditorActionListener listener;

    public EditorKeyboardHandler(CodeArea codeArea) {
        this.codeArea = codeArea;
    }

    public void setListener(EditorActionListener listener) {
        this.listener = listener;
    }

    /**
     * Handle keyboard events
     */
    public void handleKeyPress(KeyEvent event) {
        if (event.isControlDown()) {
            handleCtrlKey(event);
        } else if (event.isAltDown()) {
            handleAltKey(event);
        } else if (event.isShiftDown() && event.isControlDown()) {
            handleCtrlShiftKey(event);
        }
    }

    private void handleCtrlKey(KeyEvent event) {
        switch (event.getCode()) {
            case SLASH:
                // Ctrl+/ : Toggle line comment
                toggleLineComment();
                event.consume();
                break;
            case L:
                // Ctrl+L : Select entire line
                selectLine();
                event.consume();
                break;
            case D:
                // Ctrl+D : Duplicate line
                duplicateLine();
                event.consume();
                break;
            case K:
                // Ctrl+K : Cut line (with Shift)
                if (event.isShiftDown()) {
                    cutLine();
                }
                event.consume();
                break;
            case UP:
                // Ctrl+Up : Move line up
                moveLineUp();
                event.consume();
                break;
            case DOWN:
                // Ctrl+Down : Move line down
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
                // Alt+Up : Move line up
                moveLineUp();
                event.consume();
                break;
            case DOWN:
                // Alt+Down : Move line down
                moveLineDown();
                event.consume();
                break;
            default:
                break;
        }
    }

    private void handleCtrlShiftKey(KeyEvent event) {
        switch (event.getCode()) {
            case K:
                // Ctrl+Shift+K : Delete line
                deleteLine();
                event.consume();
                break;
            case SLASH:
                // Ctrl+Shift+/ : Toggle block comment
                toggleBlockComment();
                event.consume();
                break;
            default:
                break;
        }
    }

    private void toggleLineComment() {
        if (listener != null) {
            listener.onToggleLineComment();
        }
        LOG.debug("Toggle line comment");
    }

    private void toggleBlockComment() {
        if (listener != null) {
            listener.onToggleBlockComment();
        }
        LOG.debug("Toggle block comment");
    }

    private void selectLine() {
        if (listener != null) {
            listener.onSelectLine();
        }
        LOG.debug("Select line");
    }

    private void duplicateLine() {
        if (listener != null) {
            listener.onDuplicateLine();
        }
        LOG.debug("Duplicate line");
    }

    private void cutLine() {
        if (listener != null) {
            listener.onCutLine();
        }
        LOG.debug("Cut line");
    }

    private void deleteLine() {
        if (listener != null) {
            listener.onDeleteLine();
        }
        LOG.debug("Delete line");
    }

    private void moveLineUp() {
        if (listener != null) {
            listener.onMoveLineUp();
        }
        LOG.debug("Move line up");
    }

    private void moveLineDown() {
        if (listener != null) {
            listener.onMoveLineDown();
        }
        LOG.debug("Move line down");
    }

    /**
     * Interface for editor action callbacks
     */
    public interface EditorActionListener {
        void onToggleLineComment();
        void onToggleBlockComment();
        void onSelectLine();
        void onDuplicateLine();
        void onCutLine();
        void onDeleteLine();
        void onMoveLineUp();
        void onMoveLineDown();
    }
}
