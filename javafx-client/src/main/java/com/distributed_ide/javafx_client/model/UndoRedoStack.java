package com.distributed_ide.javafx_client.model;

import java.util.Stack;

/**
 * Simple undo/redo stack for text editor content.
 * 
 * Thread-safe implementation with proper memory management.
 */
public class UndoRedoStack {
    
    private final Stack<String> undoStack = new Stack<>();
    private final Stack<String> redoStack = new Stack<>();
    private final int maxStackSize = 100; // Limit stack size to prevent memory issues
    
    /**
     * Push a new state onto the undo stack.
     * 
     * @param content The content to push
     */
    public void push(String content) {
        // Clear redo stack when new action is performed
        redoStack.clear();
        
        // Push to undo stack
        undoStack.push(content);
        
        // Limit stack size
        while (undoStack.size() > maxStackSize) {
            undoStack.remove(0);
        }
    }
    
    /**
     * Undo the last action.
     * 
     * @return The previous content, or null if no undo available
     */
    public String undo() {
        if (!canUndo()) {
            return null;
        }
        
        String current = undoStack.pop();
        redoStack.push(current);
        
        return undoStack.isEmpty() ? "" : undoStack.peek();
    }
    
    /**
     * Redo the last undone action.
     * 
     * @return The next content, or null if no redo available
     */
    public String redo() {
        if (!canRedo()) {
            return null;
        }
        
        String content = redoStack.pop();
        undoStack.push(content);
        
        return content;
    }
    
    /**
     * Check if undo is available.
     * 
     * @return true if undo is available
     */
    public boolean canUndo() {
        return undoStack.size() > 1; // Need at least 2 items to undo
    }
    
    /**
     * Check if redo is available.
     * 
     * @return true if redo is available
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
    
    /**
     * Clear both undo and redo stacks.
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
    
    /**
     * Get the current content.
     * 
     * @return The current content, or empty string if stack is empty
     */
    public String getCurrent() {
        return undoStack.isEmpty() ? "" : undoStack.peek();
    }
    
    /**
     * Get the undo stack size.
     * 
     * @return The undo stack size
     */
    public int getUndoSize() {
        return undoStack.size();
    }
    
    /**
     * Get the redo stack size.
     * 
     * @return The redo stack size
     */
    public int getRedoSize() {
        return redoStack.size();
    }
}
