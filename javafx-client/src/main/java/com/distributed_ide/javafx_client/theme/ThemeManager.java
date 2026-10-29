package com.distributed_ide.javafx_client.theme;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Gestionnaire de thèmes dynamique.
 * <ul>
 *   <li>Enregistre les thèmes disponibles (CSS resource path)</li>
 *   <li>Applique un thème à une Scene en swappant uniquement la stylesheet du thème</li>
 *   <li>Persiste la préférence utilisateur via java.util.prefs.Preferences</li>
 *   <li>Optimisé : ajoute la feuille de style du thème à la fin des stylesheets (priorité la plus élevée)</li>
 * </ul>
 */
public class ThemeManager {

    private static final Logger LOG = LoggerFactory.getLogger(ThemeManager.class);

    private static final String PREF_KEY = "ide.theme";
    private static final String DEFAULT_THEME_ID = "modern";

    private final Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);
    private final Map<String, ThemeDescriptor> themes = new LinkedHashMap<>();
    private final ObservableList<ThemeDescriptor> themeList = FXCollections.observableArrayList();

    private volatile Scene scene;
    private volatile String activeThemeId;
    private volatile String activeThemeUrl;

    private Listener listener;

    public ThemeManager() {
        registerBuiltinThemes();
        activeThemeId = prefs.get(PREF_KEY, DEFAULT_THEME_ID);
    }

    // ═══════════════════════════════════════════════════════════
    //  THEME DESCRIPTOR
    // ═══════════════════════════════════════════════════════════
    public static class ThemeDescriptor {
        private final String id;
        private final String displayName;
        private final String cssResourcePath;

        public ThemeDescriptor(String id, String displayName, String cssResourcePath) {
            this.id = id;
            this.displayName = displayName;
            this.cssResourcePath = cssResourcePath;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getCssResourcePath() { return cssResourcePath; }

        @Override
        public String toString() { return displayName; }
    }

    // ═══════════════════════════════════════════════════════════
    //  LISTENER
    // ═══════════════════════════════════════════════════════════
    public interface Listener {
        void onThemeChanged(String themeId, String displayName);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // ═══════════════════════════════════════════════════════════
    //  REGISTRATION
    // ═══════════════════════════════════════════════════════════
    private void registerBuiltinThemes() {
        register(new ThemeDescriptor("modern", "Modern Dark", "/themes/modern.css"));
        register(new ThemeDescriptor("dark", "Dark", "/themes/ide-dark.css"));
        register(new ThemeDescriptor("light", "Light", "/themes/ide-light.css"));
        register(new ThemeDescriptor("dracula", "Dracula", "/themes/ide-dracula.css"));
        register(new ThemeDescriptor("nord", "Nord", "/themes/ide-nord.css"));
    }

    public void register(ThemeDescriptor descriptor) {
        themes.put(descriptor.getId(), descriptor);
        if (!themeList.contains(descriptor)) {
            themeList.add(descriptor);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SCENE BINDING
    // ═══════════════════════════════════════════════════════════

    /**
     * Attache le ThemeManager à une Scene et applique le thème sauvegardé.
     * Doit être appelé une seule fois au démarrage, sur le FX thread.
     */
    public void attach(Scene scene) {
        this.scene = scene;
        applyTheme(activeThemeId);
    }

    // ═══════════════════════════════════════════════════════════
    //  THEME APPLICATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Applique le thème identifié par {@code themeId}.
     * Optimisé : retire uniquement l'ancienne stylesheet du thème et ajoute la nouvelle.
     * Ne touche pas aux autres stylesheets (styles.css, etc.).
     */
    public void applyTheme(String themeId) {
        if (scene == null) {
            LOG.warn("ThemeManager: no scene attached, deferring theme apply");
            activeThemeId = themeId;
            return;
        }

        ThemeDescriptor descriptor = themes.get(themeId);
        if (descriptor == null) {
            LOG.warn("ThemeManager: unknown theme '{}', falling back to '{}'", themeId, DEFAULT_THEME_ID);
            descriptor = themes.get(DEFAULT_THEME_ID);
            if (descriptor == null) return;
            themeId = DEFAULT_THEME_ID;
        }

        URL resource = getClass().getResource(descriptor.getCssResourcePath());
        if (resource == null) {
            LOG.error("ThemeManager: CSS resource not found: {}", descriptor.getCssResourcePath());
            return;
        }

        String newUrl = resource.toExternalForm();

        // Remove only the current theme stylesheet (not other stylesheets)
        if (activeThemeUrl != null) {
            scene.getStylesheets().remove(activeThemeUrl);
        }

        // Insert theme stylesheet at the end (highest priority, overrides base styles.css)
        scene.getStylesheets().add(newUrl);

        activeThemeUrl = newUrl;
        activeThemeId = themeId;

        // Persist preference
        prefs.put(PREF_KEY, themeId);
        LOG.info("Theme applied: {} ({})", descriptor.getDisplayName(), themeId);

        if (listener != null) {
            listener.onThemeChanged(themeId, descriptor.getDisplayName());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  GETTERS
    // ═══════════════════════════════════════════════════════════
    public String getActiveThemeId() {
        return activeThemeId;
    }

    public ThemeDescriptor getActiveTheme() {
        return themes.get(activeThemeId);
    }

    public ObservableList<ThemeDescriptor> getAvailableThemes() {
        return themeList;
    }

    public ThemeDescriptor getTheme(String id) {
        return themes.get(id);
    }

    /**
     * Switches to the next registered theme in round‑robin order.
     * Useful for a simple "toggle" shortcut (e.g. Ctrl+T).
     */
    public void toggleTheme() {
        if (themes.isEmpty()) {
            return;
        }
        java.util.List<String> ids = new java.util.ArrayList<>(themes.keySet());
        int idx = ids.indexOf(activeThemeId);
        if (idx < 0) idx = 0;
        int next = (idx + 1) % ids.size();
        applyTheme(ids.get(next));
    }

    // ═══════════════════════════════════════════════════════════
    //  EXTERNAL THEME LOADING
    // ═══════════════════════════════════════════════════════════

    /**
     * Charge un thème externe depuis un fichier CSS sur le disque.
     * Utile pour les thèmes personnalisés ou plugins.
     */
    public void registerExternalTheme(String id, String displayName, Path cssFilePath) {
        if (!Files.exists(cssFilePath)) {
            LOG.warn("External theme file not found: {}", cssFilePath);
            return;
        }
        String fileUrl = cssFilePath.toUri().toString();
        ThemeDescriptor ext = new ThemeDescriptor(id, displayName, null) {
            @Override
            public String getCssResourcePath() {
                return null;
            }

            @Override
            public String toString() {
                return displayName;
            }
        };
        themes.put(id, ext);
        themeList.add(ext);

        // Store the file URL for direct application
        externalThemeUrls.put(id, fileUrl);
    }

    private final Map<String, String> externalThemeUrls = new LinkedHashMap<>();

    /**
     * Override applyTheme pour supporter les thèmes externes.
     * Si le thème est externe, utilise directement l'URL fichier.
     */
    public void applyThemeById(String themeId) {
        if (externalThemeUrls.containsKey(themeId) && scene != null) {
            String newUrl = externalThemeUrls.get(themeId);
            if (activeThemeUrl != null) {
                scene.getStylesheets().remove(activeThemeUrl);
            }
            scene.getStylesheets().add(newUrl);
            activeThemeUrl = newUrl;
            activeThemeId = themeId;
            prefs.put(PREF_KEY, themeId);
            ThemeDescriptor desc = themes.get(themeId);
            LOG.info("External theme applied: {}", themeId);
            if (listener != null && desc != null) {
                listener.onThemeChanged(themeId, desc.getDisplayName());
            }
        } else {
            applyTheme(themeId);
        }
    }

    /**
     * Applique un thème personnalisé basé sur des couleurs.
     * Génère dynamiquement un CSS à partir des couleurs fournies.
     */
    public void applyCustomTheme(String themeName, java.util.Map<String, String> colorMap) {
        if (scene == null) {
            LOG.warn("ThemeManager: no scene attached, cannot apply custom theme");
            return;
        }

        try {
            // Generate CSS from color map
            String css = generateCustomThemeCSS(colorMap);

            // Create data URI for the CSS
            String dataUri = "data:text/css;base64," +
                    java.util.Base64.getEncoder().encodeToString(css.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Remove old theme stylesheet
            if (activeThemeUrl != null) {
                scene.getStylesheets().remove(activeThemeUrl);
            }

            // Add new custom theme
            scene.getStylesheets().add(dataUri);
            activeThemeUrl = dataUri;
            activeThemeId = themeName;
            prefs.put(PREF_KEY, themeName);

            LOG.info("Custom theme applied: {}", themeName);
            if (listener != null) {
                listener.onThemeChanged(themeName, themeName);
            }
        } catch (Exception e) {
            LOG.error("Failed to apply custom theme: {}", themeName, e);
        }
    }

    /**
     * Génère un CSS à partir d'une map de couleurs personnalisées.
     */
    private String generateCustomThemeCSS(java.util.Map<String, String> colorMap) {
        StringBuilder css = new StringBuilder();

        // Define CSS variables for the custom theme
        css.append(":root {\n");
        css.append("  --editor-bg: ").append(colorMap.getOrDefault("editorBackground", "#1e1e1e")).append(";\n");
        css.append("  --editor-fg: ").append(colorMap.getOrDefault("editorForeground", "#e0e0e0")).append(";\n");
        css.append("  --accent-color: ").append(colorMap.getOrDefault("accentColor", "#007acc")).append(";\n");
        css.append("}\n\n");

        // Apply colors to IDE components
        css.append(".editor-area { -fx-control-inner-background: ").append(colorMap.getOrDefault("editorBackground", "#1e1e1e")).append("; }\n");
        css.append(".editor-area .text { -fx-fill: ").append(colorMap.getOrDefault("editorForeground", "#e0e0e0")).append("; }\n");
        css.append(".button { -fx-background-color: ").append(colorMap.getOrDefault("buttonBackground", "#3e3e42")).append("; }\n");
        css.append(".button { -fx-text-fill: ").append(colorMap.getOrDefault("buttonForeground", "#cccccc")).append("; }\n");
        css.append(".button:hover { -fx-background-color: ").append(colorMap.getOrDefault("buttonHover", "#52ad9e")).append("; }\n");

        return css.toString();
    }

    /**
     * Réinitialise au thème par défaut et supprime la préférence.
     */
    public void resetToDefault() {
        prefs.remove(PREF_KEY);
        applyTheme(DEFAULT_THEME_ID);
    }
}
