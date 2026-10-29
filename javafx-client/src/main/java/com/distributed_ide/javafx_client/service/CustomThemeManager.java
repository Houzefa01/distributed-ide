package com.distributed_ide.javafx_client.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages custom themes with persistence and import/export functionality.
 *
 * Features:
 * - Load/save custom themes to JSON files
 * - Import themes from files
 * - Export themes to files
 * - List all available custom themes
 * - Delete themes
 * - Theme validation
 * - Listener notifications on theme changes
 *
 * Storage: ~/.distributed-ide/themes/
 * Each theme stored as: themeName.json with all color definitions
 */
public class CustomThemeManager {

    private static final Logger LOG = LoggerFactory.getLogger(CustomThemeManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path themesDirectory;
    private final Map<String, Map<String, String>> loadedThemes = new HashMap<>();
    private final List<Consumer<CustomThemeManager>> listeners = new CopyOnWriteArrayList<>();

    // Required color keys for a valid theme
    private static final Set<String> REQUIRED_COLORS = Set.of(
            "editorBackground", "editorForeground",
            "keywordColor", "stringColor", "commentColor", "numberColor",
            "buttonBackground", "buttonForeground", "accentColor"
    );

    public CustomThemeManager() {
        this(Paths.get(System.getProperty("user.home"), ".distributed-ide", "themes"));
    }

    public CustomThemeManager(Path themesDirectory) {
        this.themesDirectory = themesDirectory;
        ensureDirectoryExists();
        loadAllThemes();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  DIRECTORY MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(themesDirectory);
            LOG.info("Themes directory ready: {}", themesDirectory);
        } catch (IOException e) {
            LOG.error("Failed to create themes directory", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  THEME LOADING
    // ═══════════════════════════════════════════════════════════════════

    private void loadAllThemes() {
        loadedThemes.clear();
        try {
            if (!Files.exists(themesDirectory)) {
                return;
            }

            Files.list(themesDirectory)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String themeName = getThemeNameFromPath(path);
                            Map<String, String> colors = MAPPER.readValue(
                                    path.toFile(),
                                    new TypeReference<Map<String, String>>() {}
                            );

                            if (validateTheme(colors)) {
                                loadedThemes.put(themeName, colors);
                                LOG.debug("Loaded theme: {}", themeName);
                            } else {
                                LOG.warn("Theme validation failed: {}", themeName);
                            }
                        } catch (IOException e) {
                            LOG.error("Failed to load theme from {}", path, e);
                        }
                    });

            LOG.info("Loaded {} custom themes", loadedThemes.size());
        } catch (IOException e) {
            LOG.error("Failed to list themes directory", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  THEME OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    public void saveTheme(String themeName, Map<String, String> colors) {
        if (!validateTheme(colors)) {
            LOG.warn("Theme validation failed, not saving");
            return;
        }

        try {
            Path themeFile = themesDirectory.resolve(themeName + ".json");
            MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(themeFile.toFile(), colors);

            loadedThemes.put(themeName, new HashMap<>(colors));
            LOG.info("Theme saved: {}", themeName);
            notifyListeners();
        } catch (IOException e) {
            LOG.error("Failed to save theme: {}", themeName, e);
        }
    }

    public Map<String, String> getTheme(String themeName) {
        return loadedThemes.get(themeName) != null ?
                new HashMap<>(loadedThemes.get(themeName)) :
                null;
    }

    public List<String> listThemes() {
        return new ArrayList<>(loadedThemes.keySet());
    }

    public boolean themeExists(String themeName) {
        return loadedThemes.containsKey(themeName);
    }

    public void deleteTheme(String themeName) {
        try {
            Path themeFile = themesDirectory.resolve(themeName + ".json");
            if (Files.exists(themeFile)) {
                Files.delete(themeFile);
                loadedThemes.remove(themeName);
                LOG.info("Theme deleted: {}", themeName);
                notifyListeners();
            }
        } catch (IOException e) {
            LOG.error("Failed to delete theme: {}", themeName, e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  IMPORT / EXPORT
    // ═══════════════════════════════════════════════════════════════════

    public boolean importTheme(Path sourceFile, String themeName) {
        try {
            Map<String, String> colors = MAPPER.readValue(
                    sourceFile.toFile(),
                    new TypeReference<Map<String, String>>() {}
            );

            if (!validateTheme(colors)) {
                LOG.warn("Imported theme failed validation");
                return false;
            }

            saveTheme(themeName, colors);
            LOG.info("Theme imported: {}", themeName);
            return true;
        } catch (IOException e) {
            LOG.error("Failed to import theme from {}", sourceFile, e);
            return false;
        }
    }

    public boolean exportTheme(String themeName, Path destinationFile) {
        Map<String, String> colors = getTheme(themeName);
        if (colors == null) {
            LOG.warn("Theme not found: {}", themeName);
            return false;
        }

        try {
            Files.createDirectories(destinationFile.getParent());
            MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(destinationFile.toFile(), colors);
            LOG.info("Theme exported: {} -> {}", themeName, destinationFile);
            return true;
        } catch (IOException e) {
            LOG.error("Failed to export theme", e);
            return false;
        }
    }

    public boolean exportAllThemes(Path destinationDirectory) {
        try {
            Files.createDirectories(destinationDirectory);

            Map<String, Object> allThemes = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, String>> entry : loadedThemes.entrySet()) {
                allThemes.put(entry.getKey(), entry.getValue());
            }

            Path file = destinationDirectory.resolve("themes.json");
            MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(file.toFile(), allThemes);

            LOG.info("All themes exported to {}", file);
            return true;
        } catch (IOException e) {
            LOG.error("Failed to export all themes", e);
            return false;
        }
    }

    public int importAllThemes(Path sourceFile) {
        try {
            Map<String, Map<String, String>> allThemes = MAPPER.readValue(
                    sourceFile.toFile(),
                    new TypeReference<Map<String, Map<String, String>>>() {}
            );

            int count = 0;
            for (Map.Entry<String, Map<String, String>> entry : allThemes.entrySet()) {
                if (validateTheme(entry.getValue())) {
                    saveTheme(entry.getKey(), entry.getValue());
                    count++;
                }
            }

            LOG.info("Imported {} themes from {}", count, sourceFile);
            return count;
        } catch (IOException e) {
            LOG.error("Failed to import all themes", e);
            return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    private boolean validateTheme(Map<String, String> colors) {
        if (colors == null || colors.isEmpty()) {
            return false;
        }

        // Check for required colors
        for (String required : REQUIRED_COLORS) {
            if (!colors.containsKey(required)) {
                LOG.debug("Missing required color: {}", required);
                return false;
            }
        }

        // Validate hex colors
        for (Map.Entry<String, String> entry : colors.entrySet()) {
            String hex = entry.getValue();
            if (!isValidHexColor(hex)) {
                LOG.debug("Invalid hex color: {} = {}", entry.getKey(), hex);
                return false;
            }
        }

        return true;
    }

    private boolean isValidHexColor(String hex) {
        if (hex == null) return false;
        if (!hex.startsWith("#")) hex = "#" + hex;
        return hex.matches("^#[0-9a-fA-F]{6}$");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  LISTENERS
    // ═══════════════════════════════════════════════════════════════════

    public void addListener(Consumer<CustomThemeManager> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<CustomThemeManager> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Consumer<CustomThemeManager> listener : listeners) {
            try {
                listener.accept(this);
            } catch (Exception e) {
                LOG.warn("Theme listener error", e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UTILITIES
    // ═══════════════════════════════════════════════════════════════════

    private String getThemeNameFromPath(Path path) {
        String filename = path.getFileName().toString();
        return filename.substring(0, filename.lastIndexOf('.'));
    }

    public Path getThemesDirectory() {
        return themesDirectory;
    }

    public int getThemeCount() {
        return loadedThemes.size();
    }
}
