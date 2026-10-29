package com.distributed_ide.javafx_client.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Gestionnaire de paramètres persistants (JSON).
 * Stocke les préférences dans ~/.distributed-ide/settings.json
 *
 * Clés supportées :
 *   editor.fontSize, editor.tabSize, editor.wordWrap,
 *   editor.minimap, editor.autoSave, editor.autoSaveDelaySeconds,
 *   theme.current,
 *   window.width, window.height, window.maximized,
 *   terminal.shell,
 *   sync.serverUri, sync.autoReconnect,
 *   search.excludePatterns
 */
public class SettingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Path settingsFile;
    private ObjectNode root;
    private final List<Consumer<SettingsManager>> listeners = new CopyOnWriteArrayList<>();

    public SettingsManager() {
        this(Paths.get(System.getProperty("user.home"), ".distributed-ide", "settings.json"));
    }

    public SettingsManager(Path settingsFile) {
        this.settingsFile = settingsFile;
        load();
    }

    // ═══════════════════════════════════════════════════════════
    //  LOAD / SAVE
    // ═══════════════════════════════════════════════════════════

    public void load() {
        if (Files.exists(settingsFile)) {
            try {
                JsonNode node = MAPPER.readTree(settingsFile.toFile());
                root = node.isObject() ? (ObjectNode) node : MAPPER.createObjectNode();
                LOG.info("Settings loaded from {}", settingsFile);
            } catch (IOException e) {
                LOG.warn("Failed to load settings, using defaults", e);
                root = MAPPER.createObjectNode();
                applyDefaults();
            }
        } else {
            root = MAPPER.createObjectNode();
            applyDefaults();
            save();
        }
    }

    public void save() {
        try {
            Files.createDirectories(settingsFile.getParent());
            MAPPER.writeValue(settingsFile.toFile(), root);
            LOG.debug("Settings saved to {}", settingsFile);
        } catch (IOException e) {
            LOG.error("Failed to save settings", e);
        }
    }

    private void applyDefaults() {
        putIfAbsent("editor.fontSize", 14);
        putIfAbsent("editor.fontFamily", "Consolas");
        putIfAbsent("editor.tabSize", 4);
        putIfAbsent("editor.wordWrap", false);
        putIfAbsent("editor.minimap", true);
        putIfAbsent("editor.autoSave", false);
        putIfAbsent("editor.autoSaveDelaySeconds", 30);
        putIfAbsent("theme.current", "dark");
        putIfAbsent("window.width", 1400);
        putIfAbsent("window.height", 900);
        putIfAbsent("window.maximized", false);
        putIfAbsent("sync.serverUri", "ws://localhost:8765");
        putIfAbsent("sync.autoReconnect", true);
        putIfAbsent("search.excludePatterns", "node_modules,target,.git,.idea,build,dist");
    }

    // ═══════════════════════════════════════════════════════════
    //  GETTERS
    // ═══════════════════════════════════════════════════════════

    public String getString(String key, String defaultValue) {
        JsonNode node = resolve(key);
        return node != null && node.isTextual() ? node.asText() : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        JsonNode node = resolve(key);
        return node != null && node.isNumber() ? node.asInt() : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        JsonNode node = resolve(key);
        return node != null && node.isBoolean() ? node.asBoolean() : defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        JsonNode node = resolve(key);
        return node != null && node.isNumber() ? node.asLong() : defaultValue;
    }

    public double getDouble(String key, double defaultValue) {
        JsonNode node = resolve(key);
        return node != null && node.isNumber() ? node.asDouble() : defaultValue;
    }

    // ═══════════════════════════════════════════════════════════
    //  SETTERS (auto-save + notify)
    // ═══════════════════════════════════════════════════════════

    public void set(String key, String value) {
        put(key, value);
        save();
        notifyListeners();
    }

    public void set(String key, int value) {
        put(key, value);
        save();
        notifyListeners();
    }

    public void set(String key, boolean value) {
        put(key, value);
        save();
        notifyListeners();
    }

    public void set(String key, double value) {
        put(key, value);
        save();
        notifyListeners();
    }

    // ═══════════════════════════════════════════════════════════
    //  LISTENERS
    // ═══════════════════════════════════════════════════════════

    public void addListener(Consumer<SettingsManager> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<SettingsManager> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Consumer<SettingsManager> l : listeners) {
            try {
                l.accept(this);
            } catch (Exception e) {
                LOG.warn("Settings listener error", e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  INTERNAL — dotted key navigation
    // ═══════════════════════════════════════════════════════════

    private JsonNode resolve(String dottedKey) {
        String[] parts = dottedKey.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || !current.has(part)) return null;
            current = current.get(part);
        }
        return current;
    }

    private ObjectNode ensureParent(String dottedKey) {
        String[] parts = dottedKey.split("\\.");
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.has(parts[i]) || !current.get(parts[i]).isObject()) {
                current.set(parts[i], MAPPER.createObjectNode());
            }
            current = (ObjectNode) current.get(parts[i]);
        }
        return current;
    }

    private String leafKey(String dottedKey) {
        int idx = dottedKey.lastIndexOf('.');
        return idx >= 0 ? dottedKey.substring(idx + 1) : dottedKey;
    }

    private void put(String key, Object value) {
        ObjectNode parent = ensureParent(key);
        String leaf = leafKey(key);
        if (value instanceof String s) parent.put(leaf, s);
        else if (value instanceof Integer i) parent.put(leaf, i);
        else if (value instanceof Boolean b) parent.put(leaf, b);
        else if (value instanceof Double d) parent.put(leaf, d);
        else if (value instanceof Long l) parent.put(leaf, l);
    }

    private void putIfAbsent(String key, Object value) {
        if (resolve(key) == null) put(key, value);
    }

    public Path getSettingsFile() {
        return settingsFile;
    }
}
