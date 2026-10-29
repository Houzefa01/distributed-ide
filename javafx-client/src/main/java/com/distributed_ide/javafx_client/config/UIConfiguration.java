package com.distributed_ide.javafx_client.config;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;

/**
 * IDE UI Configuration - Unified configuration for all UI/UX settings
 */
public class UIConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(UIConfiguration.class);
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.distributed-ide/config";
    private static final String UI_CONFIG_FILE = "ui-config.json";
    private static final Preferences PREFS = Preferences.userNodeForPackage(UIConfiguration.class);

    // Default values
    public static final int DEFAULT_FONT_SIZE = 14;
    public static final int DEFAULT_TAB_SIZE = 4;
    public static final String DEFAULT_THEME = "ide-dark";
    public static final boolean DEFAULT_AUTO_SAVE = true;
    public static final int DEFAULT_AUTO_SAVE_INTERVAL = 10000; // 10 seconds
    public static final boolean DEFAULT_WORD_WRAP = false;
    public static final boolean DEFAULT_MINIMAP = true;
    public static final boolean DEFAULT_LINE_NUMBERS = true;
    public static final boolean DEFAULT_SESSION_RESTORE = true;
    public static final boolean DEFAULT_BRACKET_MATCHING = true;
    public static final boolean DEFAULT_SMOOTH_SCROLL = true;

    // UI Settings
    private UISettings settings;
    private final Gson gson = new Gson();

    public UIConfiguration() {
        loadConfiguration();
    }

    private void loadConfiguration() {
        try {
            Path configFile = Paths.get(CONFIG_DIR, UI_CONFIG_FILE);
            if (Files.exists(configFile)) {
                String json = new String(Files.readAllBytes(configFile));
                settings = gson.fromJson(json, UISettings.class);
                LOG.debug("UI configuration loaded from file");
            } else {
                settings = new UISettings();
                saveConfiguration();
            }
        } catch (Exception e) {
            LOG.warn("Failed to load UI configuration, using defaults", e);
            settings = new UISettings();
        }
    }

    public void saveConfiguration() {
        try {
            Path configDir = Paths.get(CONFIG_DIR);
            Files.createDirectories(configDir);
            
            String json = gson.toJson(settings);
            Files.write(Paths.get(CONFIG_DIR, UI_CONFIG_FILE), json.getBytes());
            LOG.debug("UI configuration saved");
        } catch (IOException e) {
            LOG.error("Failed to save UI configuration", e);
        }
    }

    // ─── Getters & Setters ─────────────────────────────────

    public int getFontSize() {
        return PREFS.getInt("fontSize", settings.fontSize);
    }

    public void setFontSize(int size) {
        settings.fontSize = size;
        PREFS.putInt("fontSize", size);
        saveConfiguration();
    }

    public int getTabSize() {
        return settings.tabSize;
    }

    public void setTabSize(int size) {
        settings.tabSize = size;
        saveConfiguration();
    }

    public String getTheme() {
        return PREFS.get("theme", settings.theme);
    }

    public void setTheme(String theme) {
        settings.theme = theme;
        PREFS.put("theme", theme);
        saveConfiguration();
    }

    public boolean isAutoSaveEnabled() {
        return PREFS.getBoolean("autoSave", settings.autoSave);
    }

    public void setAutoSaveEnabled(boolean enabled) {
        settings.autoSave = enabled;
        PREFS.putBoolean("autoSave", enabled);
        saveConfiguration();
    }

    public int getAutoSaveInterval() {
        return settings.autoSaveInterval;
    }

    public void setAutoSaveInterval(int interval) {
        settings.autoSaveInterval = interval;
        saveConfiguration();
    }

    public boolean isWordWrapEnabled() {
        return settings.wordWrap;
    }

    public void setWordWrapEnabled(boolean enabled) {
        settings.wordWrap = enabled;
        saveConfiguration();
    }

    public boolean isMinimapVisible() {
        return settings.minimapVisible;
    }

    public void setMinimapVisible(boolean visible) {
        settings.minimapVisible = visible;
        saveConfiguration();
    }

    public boolean areLineNumbersVisible() {
        return settings.lineNumbers;
    }

    public void setLineNumbersVisible(boolean visible) {
        settings.lineNumbers = visible;
        saveConfiguration();
    }

    public boolean isSessionRestoreEnabled() {
        return PREFS.getBoolean("sessionRestore", settings.sessionRestore);
    }

    public void setSessionRestoreEnabled(boolean enabled) {
        settings.sessionRestore = enabled;
        PREFS.putBoolean("sessionRestore", enabled);
        saveConfiguration();
    }

    public boolean isBracketMatchingEnabled() {
        return settings.bracketMatching;
    }

    public void setBracketMatchingEnabled(boolean enabled) {
        settings.bracketMatching = enabled;
        saveConfiguration();
    }

    public boolean isSmoothScrollEnabled() {
        return settings.smoothScroll;
    }

    public void setSmoothScrollEnabled(boolean enabled) {
        settings.smoothScroll = enabled;
        saveConfiguration();
    }

    public UISettings getSettings() {
        return settings;
    }

    /**
     * UI Settings model
     */
    public static class UISettings {
        public int fontSize = DEFAULT_FONT_SIZE;
        public int tabSize = DEFAULT_TAB_SIZE;
        public String theme = DEFAULT_THEME;
        public boolean autoSave = DEFAULT_AUTO_SAVE;
        public int autoSaveInterval = DEFAULT_AUTO_SAVE_INTERVAL;
        public boolean wordWrap = DEFAULT_WORD_WRAP;
        public boolean minimapVisible = DEFAULT_MINIMAP;
        public boolean lineNumbers = DEFAULT_LINE_NUMBERS;
        public boolean sessionRestore = DEFAULT_SESSION_RESTORE;
        public boolean bracketMatching = DEFAULT_BRACKET_MATCHING;
        public boolean smoothScroll = DEFAULT_SMOOTH_SCROLL;
        
        // Additional settings
        public String fontFamily = "Consolas";
        public String encodingDefault = "UTF-8";
        public boolean showMenuBar = true;
        public boolean showActivityBar = true;
        public boolean showSidebar = true;
        public boolean showStatusBar = true;
        public int sidebarWidth = 250;
    }
}
