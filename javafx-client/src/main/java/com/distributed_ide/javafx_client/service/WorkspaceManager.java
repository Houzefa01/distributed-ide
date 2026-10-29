package com.distributed_ide.javafx_client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages workspace state persistence — saves and restores open tabs,
 * active file, project root, split positions, and bottom panel state.
 * Stored in ~/.distributed-ide/workspace.json
 */
public class WorkspaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Path workspaceFile;

    public WorkspaceManager() {
        this(Paths.get(System.getProperty("user.home"), ".distributed-ide", "workspace.json"));
    }

    public WorkspaceManager(Path workspaceFile) {
        this.workspaceFile = workspaceFile;
    }

    /**
     * Saves current workspace state.
     */
    public void save(WorkspaceState state) {
        try {
            Files.createDirectories(workspaceFile.getParent());
            MAPPER.writeValue(workspaceFile.toFile(), state);
            LOG.debug("Workspace saved to {}", workspaceFile);
        } catch (IOException e) {
            LOG.error("Failed to save workspace", e);
        }
    }

    /**
     * Loads workspace state, or returns null if none exists.
     */
    public WorkspaceState load() {
        if (!Files.exists(workspaceFile)) return null;
        try {
            WorkspaceState state = MAPPER.readValue(workspaceFile.toFile(), WorkspaceState.class);
            LOG.info("Workspace loaded from {}", workspaceFile);
            return state;
        } catch (IOException e) {
            LOG.warn("Failed to load workspace", e);
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  STATE MODEL
    // ═══════════════════════════════════════════════════════════

    public static class WorkspaceState {
        public String projectRoot;
        public List<String> openFiles = new ArrayList<>();
        public String activeFile;
        public double[] horizontalDividers = {0.17, 0.82};
        public double[] verticalDividers = {0.7};
        public int selectedBottomTab = 0;
        public boolean collabPanelVisible = true;
        public double windowWidth = 1400;
        public double windowHeight = 900;
        public boolean windowMaximized = false;
        
        // File-specific positions (for session restoration)
        public Map<String, CaretPosition> caretPositions = new HashMap<>();
        public Map<String, ScrollPosition> scrollPositions = new HashMap<>();

        public WorkspaceState() {}
    }

    /**
     * Stores caret position (line and column) for a file.
     */
    public static class CaretPosition {
        public int line;
        public int column;
        
        public CaretPosition() {}
        
        public CaretPosition(int line, int column) {
            this.line = line;
            this.column = column;
        }
    }

    /**
     * Stores scroll position (vertical and horizontal) for a file.
     */
    public static class ScrollPosition {
        public double vValue;  // Vertical scroll (0.0 to 1.0)
        public double hValue;  // Horizontal scroll (0.0 to 1.0)
        
        public ScrollPosition() {}
        
        public ScrollPosition(double vValue, double hValue) {
            this.vValue = vValue;
            this.hValue = hValue;
        }
    }
}
