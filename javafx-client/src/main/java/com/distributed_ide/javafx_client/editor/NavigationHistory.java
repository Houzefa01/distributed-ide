package com.distributed_ide.javafx_client.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Navigation history (back/forward) for editor file positions.
 * Tracks file path + line number, supports Alt+Left / Alt+Right navigation.
 */
public class NavigationHistory {

    private static final int MAX_HISTORY = 100;

    private final List<NavigationEntry> history = new ArrayList<>();
    private int currentIndex = -1;

    /**
     * Records a navigation to a file at a given line.
     * Truncates forward history if navigating from a non-head position.
     */
    public void record(String filePath, int line) {
        if (filePath == null) return;
        NavigationEntry entry = new NavigationEntry(filePath, line);

        // Don't record duplicate consecutive entries
        if (currentIndex >= 0 && currentIndex < history.size()) {
            NavigationEntry current = history.get(currentIndex);
            if (current.filePath.equals(filePath) && Math.abs(current.line - line) <= 1) {
                // Update line in-place for small movements within same file
                history.set(currentIndex, entry);
                return;
            }
        }

        // Truncate forward history
        if (currentIndex < history.size() - 1) {
            history.subList(currentIndex + 1, history.size()).clear();
        }

        history.add(entry);
        currentIndex = history.size() - 1;

        // Cap max size
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
            currentIndex--;
        }
    }

    /**
     * Navigate back in history.
     * @return the previous entry, or null if at the beginning.
     */
    public NavigationEntry goBack() {
        if (currentIndex > 0) {
            currentIndex--;
            return history.get(currentIndex);
        }
        return null;
    }

    /**
     * Navigate forward in history.
     * @return the next entry, or null if at the end.
     */
    public NavigationEntry goForward() {
        if (currentIndex < history.size() - 1) {
            currentIndex++;
            return history.get(currentIndex);
        }
        return null;
    }

    public boolean canGoBack() {
        return currentIndex > 0;
    }

    public boolean canGoForward() {
        return currentIndex < history.size() - 1;
    }

    public NavigationEntry current() {
        if (currentIndex >= 0 && currentIndex < history.size()) {
            return history.get(currentIndex);
        }
        return null;
    }

    public void clear() {
        history.clear();
        currentIndex = -1;
    }

    // ═══════════════════════════════════════════════════════════
    //  MODEL
    // ═══════════════════════════════════════════════════════════

    public static class NavigationEntry {
        public final String filePath;
        public final int line;

        public NavigationEntry(String filePath, int line) {
            this.filePath = Objects.requireNonNull(filePath);
            this.line = line;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NavigationEntry that)) return false;
            return line == that.line && filePath.equals(that.filePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(filePath, line);
        }

        @Override
        public String toString() {
            return filePath + ":" + line;
        }
    }
}
