package com.distributed_ide.javafx_client.config;

import com.distributed_ide.javafx_client.service.SettingsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Centralized configuration for the Distributed IDE.
 * Single source of truth for all connection URIs, timeouts, limits, and feature flags.
 * Loads defaults, then overrides from SettingsManager (persistent JSON) and system properties.
 */
public final class IDEConfig {

    private static final Logger LOG = LoggerFactory.getLogger(IDEConfig.class);

    // ── Singleton ────────────────────────────────────────────
    private static final IDEConfig INSTANCE = new IDEConfig();
    public static IDEConfig get() { return INSTANCE; }

    // ── Paths ────────────────────────────────────────────────
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".distributed-ide");

    // ── WebSocket URIs ───────────────────────────────────────
    private String syncServerUri       = "ws://localhost:8765";
    private String lspBridgeUri        = "ws://localhost:8081/lsp";
    private String terminalUri         = "ws://localhost:8766";
    private String signalingUri        = "ws://localhost:8082";

    // ── Timeouts (ms) ────────────────────────────────────────
    private long wsConnectTimeoutMs    = 5_000;
    private long wsHeartbeatIntervalMs = 30_000;
    private long wsHeartbeatTimeoutMs  = 60_000;
    private long lspDebounceMs         = 300;
    private long autoSaveIntervalMs    = 30_000;

    // ── Reconnect ────────────────────────────────────────────
    private int  reconnectMaxAttempts  = 20;
    private long reconnectInitialMs    = 1_000;
    private long reconnectMaxMs        = 30_000;

    // ── Limits ───────────────────────────────────────────────
    private int  maxRecentItems        = 10;
    private int  maxSearchResults      = 500;
    private long maxSearchFileSizeBytes = 1_048_576; // 1 MB
    private int  maxMessageSize        = 5 * 1024 * 1024; // 5 MB
    private int  rateLimitPerSecond    = 200;

    // ── Feature flags ────────────────────────────────────────
    private boolean autoSaveEnabled    = false;
    private boolean autoReconnect      = true;
    private boolean fileWatcherEnabled = true;

    // ── UI ───────────────────────────────────────────────────
    private double defaultZoom         = 1.0;
    private double windowWidth         = 1400;
    private double windowHeight        = 900;

    private IDEConfig() {}

    /**
     * Loads overrides from SettingsManager (JSON) and system properties.
     * System properties take highest priority (e.g., -Dide.sync.uri=ws://...).
     */
    public void loadFrom(SettingsManager settings) {
        if (settings == null) return;
        try {
            syncServerUri       = settings.getString("sync.uri", syncServerUri);
            lspBridgeUri        = settings.getString("lsp.uri", lspBridgeUri);
            terminalUri         = settings.getString("terminal.uri", terminalUri);
            signalingUri        = settings.getString("signaling.uri", signalingUri);
            autoSaveEnabled     = settings.getBoolean("editor.autoSave", autoSaveEnabled);
            autoReconnect       = settings.getBoolean("sync.autoReconnect", autoReconnect);
            fileWatcherEnabled  = settings.getBoolean("fileWatcher.enabled", fileWatcherEnabled);
            lspDebounceMs       = settings.getLong("lsp.debounceMs", lspDebounceMs);
            autoSaveIntervalMs  = settings.getLong("editor.autoSaveIntervalMs", autoSaveIntervalMs);
            defaultZoom         = settings.getDouble("editor.zoom", defaultZoom);
            LOG.info("Configuration loaded from settings");
        } catch (Exception e) {
            LOG.warn("Error loading config from settings, using defaults", e);
        }

        // System property overrides (highest priority)
        syncServerUri  = System.getProperty("ide.sync.uri", syncServerUri);
        lspBridgeUri   = System.getProperty("ide.lsp.uri", lspBridgeUri);
        terminalUri    = System.getProperty("ide.terminal.uri", terminalUri);
        signalingUri   = System.getProperty("ide.signaling.uri", signalingUri);
    }

    // ── Getters ──────────────────────────────────────────────
    public Path   getConfigDir()              { return CONFIG_DIR; }

    public String getSyncServerUri()          { return syncServerUri; }
    public String getLspBridgeUri()            { return lspBridgeUri; }
    public String getTerminalUri()             { return terminalUri; }
    public String getSignalingUri()            { return signalingUri; }

    public long   getWsConnectTimeoutMs()     { return wsConnectTimeoutMs; }
    public long   getWsHeartbeatIntervalMs()  { return wsHeartbeatIntervalMs; }
    public long   getWsHeartbeatTimeoutMs()   { return wsHeartbeatTimeoutMs; }
    public long   getLspDebounceMs()           { return lspDebounceMs; }
    public long   getAutoSaveIntervalMs()     { return autoSaveIntervalMs; }

    public int    getReconnectMaxAttempts()    { return reconnectMaxAttempts; }
    public long   getReconnectInitialMs()     { return reconnectInitialMs; }
    public long   getReconnectMaxMs()         { return reconnectMaxMs; }

    public int    getMaxRecentItems()         { return maxRecentItems; }
    public int    getMaxSearchResults()       { return maxSearchResults; }
    public long   getMaxSearchFileSizeBytes() { return maxSearchFileSizeBytes; }
    public int    getMaxMessageSize()         { return maxMessageSize; }
    public int    getRateLimitPerSecond()     { return rateLimitPerSecond; }

    public boolean isAutoSaveEnabled()        { return autoSaveEnabled; }
    public boolean isAutoReconnect()          { return autoReconnect; }
    public boolean isFileWatcherEnabled()     { return fileWatcherEnabled; }

    public double  getDefaultZoom()           { return defaultZoom; }
    public double  getWindowWidth()           { return windowWidth; }
    public double  getWindowHeight()          { return windowHeight; }

    // ── Setters (for runtime changes) ────────────────────────
    public void setSyncServerUri(String uri)  { this.syncServerUri = uri; }
    public void setLspBridgeUri(String uri)   { this.lspBridgeUri = uri; }
    public void setTerminalUri(String uri)    { this.terminalUri = uri; }
    public void setSignalingUri(String uri)   { this.signalingUri = uri; }
    public void setAutoSaveEnabled(boolean b) { this.autoSaveEnabled = b; }

    @Override
    public String toString() {
        return String.format("IDEConfig[sync=%s, lsp=%s, terminal=%s, signaling=%s, autoSave=%s, autoReconnect=%s]",
                syncServerUri, lspBridgeUri, terminalUri, signalingUri, autoSaveEnabled, autoReconnect);
    }
}
