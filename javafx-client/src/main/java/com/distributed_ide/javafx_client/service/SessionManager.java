package com.distributed_ide.javafx_client.service;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class SessionManager {
    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);
    private static final String SESSION_DIR = System.getProperty("user.home") + "/.distributed-ide/sessions";
    private static final String CURRENT_SESSION_FILE = "current-session.json";
    private static final Preferences PREFS = Preferences.userNodeForPackage(SessionManager.class);
    private static final String PREF_LAST_PROJECT = "lastProjectRoot";
    private static final String PREF_ENABLE_SESSION_RESTORE = "enableSessionRestore";
    
    private final Gson gson = new Gson();
    private SessionState currentSession;

    public SessionManager() {
        boolean enableRestore = PREFS.getBoolean(PREF_ENABLE_SESSION_RESTORE, true);
        if (enableRestore) {
            loadSession();
        } else {
            currentSession = new SessionState();
        }
    }

    /**
     * Save the current session state
     */
    public void saveSession(String projectRoot, List<String> openFiles, String activeFile, 
                           boolean autoSaveEnabled) {
        try {
            currentSession = new SessionState();
            currentSession.projectRoot = projectRoot;
            currentSession.openFiles = new ArrayList<>(openFiles);
            currentSession.activeFile = activeFile;
            currentSession.autoSaveEnabled = autoSaveEnabled;
            currentSession.timestamp = System.currentTimeMillis();
            
            // Save to prefs
            PREFS.put(PREF_LAST_PROJECT, projectRoot);
            
            // Save to file for detailed state
            Path sessionDir = Paths.get(SESSION_DIR);
            Files.createDirectories(sessionDir);
            
            String json = gson.toJson(currentSession);
            Files.write(sessionDir.resolve(CURRENT_SESSION_FILE), json.getBytes());
            
            LOG.debug("Session saved: {} files, project: {}", openFiles.size(), projectRoot);
        } catch (Exception e) {
            LOG.warn("Failed to save session", e);
        }
    }

    /**
     * Load the last saved session
     */
    public void loadSession() {
        try {
            Path sessionFile = Paths.get(SESSION_DIR, CURRENT_SESSION_FILE);
            if (Files.exists(sessionFile)) {
                String json = new String(Files.readAllBytes(sessionFile));
                currentSession = gson.fromJson(json, SessionState.class);
                LOG.debug("Session loaded: {} files", 
                    currentSession.openFiles != null ? currentSession.openFiles.size() : 0);
            } else {
                currentSession = new SessionState();
            }
        } catch (Exception e) {
            LOG.warn("Failed to load session, starting fresh", e);
            currentSession = new SessionState();
        }
    }

    public SessionState getCurrentSession() {
        return currentSession;
    }

    public String getLastProjectRoot() {
        return PREFS.get(PREF_LAST_PROJECT, null);
    }

    public void clearSession() {
        try {
            Path sessionFile = Paths.get(SESSION_DIR, CURRENT_SESSION_FILE);
            if (Files.exists(sessionFile)) {
                Files.delete(sessionFile);
            }
            currentSession = new SessionState();
            LOG.info("Session cleared");
        } catch (IOException e) {
            LOG.warn("Failed to clear session", e);
        }
    }

    public void setSessionRestoreEnabled(boolean enabled) {
        PREFS.putBoolean(PREF_ENABLE_SESSION_RESTORE, enabled);
    }

    public boolean isSessionRestoreEnabled() {
        return PREFS.getBoolean(PREF_ENABLE_SESSION_RESTORE, true);
    }

    /**
     * Session State model
     */
    public static class SessionState {
        public String projectRoot;
        public List<String> openFiles = new ArrayList<>();
        public String activeFile;
        public boolean autoSaveEnabled;
        public long timestamp;
        public int editorZoomLevel = 14;  // Default font size
        public String theme = "ide-dark";
    }
}
