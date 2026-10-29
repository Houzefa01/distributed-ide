package com.distributed_ide.javafx_client.util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Icon manager for the IDE.
 * Maps file extensions to corresponding icons.
 * Loads icons from /icons/ with a thread-safe cache for performance.
 */
public class IconManager {

    private static final Logger LOG = LoggerFactory.getLogger(IconManager.class);

    private static final int ICON_SIZE = 16;
    private static final String ICON_BASE_PATH = "/icons/";
    private static final int MAX_CACHE_SIZE = 256;

    // Thread-safe icon cache to avoid repeated loading
    private static final Map<String, Image> imageCache = new ConcurrentHashMap<>();

    // Extension → icon name mapping (without .png)
    private static final Map<String, String> extensionToIcon = new ConcurrentHashMap<>();

    static {
        // Programming
        extensionToIcon.put("java", "java");
        extensionToIcon.put("py", "python");
        extensionToIcon.put("js", "javascript");
        extensionToIcon.put("jsx", "react");
        extensionToIcon.put("ts", "typescript");
        extensionToIcon.put("tsx", "typescript");
        extensionToIcon.put("c", "c");
        extensionToIcon.put("cpp", "cpp");
        extensionToIcon.put("cc", "cpp");
        extensionToIcon.put("cxx", "cpp");
        extensionToIcon.put("h", "h");
        extensionToIcon.put("hpp", "h");
        extensionToIcon.put("cs", "csharp");
        extensionToIcon.put("go", "go");
        extensionToIcon.put("rs", "rust");
        extensionToIcon.put("rb", "ruby");
        extensionToIcon.put("php", "php");
        extensionToIcon.put("swift", "swift");
        extensionToIcon.put("kt", "kotlin");
        extensionToIcon.put("scala", "scala");
        extensionToIcon.put("r", "r");
        extensionToIcon.put("lua", "lua");
        extensionToIcon.put("pl", "perl");
        extensionToIcon.put("sh", "shell");
        extensionToIcon.put("bash", "shell");
        extensionToIcon.put("zsh", "shell");
        extensionToIcon.put("fish", "shell");
        extensionToIcon.put("ps1", "powershell");
        extensionToIcon.put("bat", "console");
        extensionToIcon.put("cmd", "console");

        // Web
        extensionToIcon.put("html", "html");
        extensionToIcon.put("htm", "html");
        extensionToIcon.put("css", "css");
        extensionToIcon.put("scss", "sass");
        extensionToIcon.put("sass", "sass");
        extensionToIcon.put("less", "less");
        extensionToIcon.put("vue", "vue");
        extensionToIcon.put("svelte", "svelte");
        extensionToIcon.put("angular", "angular");

        // Data & Configuration
        extensionToIcon.put("json", "json");
        extensionToIcon.put("xml", "xml");
        extensionToIcon.put("yaml", "yaml");
        extensionToIcon.put("yml", "yaml");
        extensionToIcon.put("toml", "toml");
        extensionToIcon.put("ini", "settings");
        extensionToIcon.put("conf", "settings");
        extensionToIcon.put("config", "settings");
        extensionToIcon.put("properties", "settings");
        extensionToIcon.put("env", "tune");

        // Build & Package managers
        extensionToIcon.put("gradle", "gradle");
        extensionToIcon.put("maven", "maven");
        extensionToIcon.put("pom", "maven");
        extensionToIcon.put("package", "npm");
        extensionToIcon.put("lock", "lock");
        extensionToIcon.put("sum", "lock");

        // Documentation
        extensionToIcon.put("md", "markdown");
        extensionToIcon.put("markdown", "markdown");
        extensionToIcon.put("txt", "document");
        extensionToIcon.put("rst", "document");
        extensionToIcon.put("adoc", "asciidoc");
        extensionToIcon.put("tex", "tex");
        extensionToIcon.put("pdf", "pdf");

        // Images
        extensionToIcon.put("png", "image");
        extensionToIcon.put("jpg", "image");
        extensionToIcon.put("jpeg", "image");
        extensionToIcon.put("gif", "image");
        extensionToIcon.put("svg", "svg");
        extensionToIcon.put("ico", "image");
        extensionToIcon.put("webp", "image");
        extensionToIcon.put("bmp", "image");

        // Audio/Video
        extensionToIcon.put("mp3", "audio");
        extensionToIcon.put("wav", "audio");
        extensionToIcon.put("ogg", "audio");
        extensionToIcon.put("mp4", "video");
        extensionToIcon.put("avi", "video");
        extensionToIcon.put("mov", "video");
        extensionToIcon.put("mkv", "video");

        // Archives
        extensionToIcon.put("zip", "zip");
        extensionToIcon.put("tar", "zip");
        extensionToIcon.put("gz", "zip");
        extensionToIcon.put("7z", "zip");
        extensionToIcon.put("rar", "zip");
        extensionToIcon.put("jar", "jar");
        extensionToIcon.put("war", "jar");

        // Git & VCS
        extensionToIcon.put("gitignore", "git");
        extensionToIcon.put("gitattributes", "git");
        extensionToIcon.put("gitmodules", "git");

        // Docker & Cloud
        extensionToIcon.put("dockerfile", "docker");
        extensionToIcon.put("dockerignore", "docker");
        extensionToIcon.put("docker-compose", "docker");

        // Databases
        extensionToIcon.put("sql", "database");
        extensionToIcon.put("db", "database");
        extensionToIcon.put("sqlite", "database");

        // Special files
        extensionToIcon.put("license", "certificate");
        extensionToIcon.put("readme", "info");
        extensionToIcon.put("changelog", "changelog");
        extensionToIcon.put("makefile", "settings");
        extensionToIcon.put("cmakelists", "cmake");
    }

    /**
     * Returns an ImageView with the appropriate icon for a file.
     */
    public static ImageView getFileIcon(String fileName, boolean isDirectory) {
        if (isDirectory) {
            return getFolderIcon();
        }

        String extension = getExtension(fileName);
        String iconName = extensionToIcon.getOrDefault(extension, null);

        // Special cases based on full file name
        if (iconName == null) {
            String lowerName = fileName.toLowerCase();
            if (lowerName.contains("dockerfile")) {
                iconName = "docker";
            } else if (lowerName.equals("makefile") || lowerName.equals("makefile.am")) {
                iconName = "settings";
            } else if (lowerName.equals("cmakelists.txt")) {
                iconName = "cmake";
            } else if (lowerName.equals("package.json")) {
                iconName = "npm";
            } else if (lowerName.equals("pom.xml")) {
                iconName = "maven";
            } else if (lowerName.equals("build.gradle") || lowerName.equals("settings.gradle")) {
                iconName = "gradle";
            } else if (lowerName.startsWith(".git")) {
                iconName = "git";
            } else if (lowerName.equals("readme.md") || lowerName.equals("readme.txt")) {
                iconName = "info";
            } else if (lowerName.equals("license") || lowerName.equals("license.txt")) {
                iconName = "certificate";
            }
        }

        return getIconView(iconName != null ? iconName : "file");
    }

    /**
     * Returns an ImageView with the folder icon.
     */
    public static ImageView getFolderIcon() {
        return getIconView("folder");
    }

    /**
     * Returns an ImageView with the default file icon.
     */
    public static ImageView getDefaultFileIcon() {
        return getIconView("file");
    }

    /**
     * Loads and returns an ImageView for the specified icon.
     */
    private static ImageView getIconView(String iconName) {
        // Evict oldest entries if cache exceeds max size
        if (imageCache.size() > MAX_CACHE_SIZE) {
            var it = imageCache.keySet().iterator();
            if (it.hasNext()) { it.next(); it.remove(); }
        }
        Image image = imageCache.computeIfAbsent(iconName, IconManager::loadIcon);
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(ICON_SIZE);
            view.setFitHeight(ICON_SIZE);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            return view;
        }
        // Fallback: empty icon
        return new ImageView();
    }

    /**
     * Loads an icon from the classpath.
     */
    private static Image loadIcon(String iconName) {
        try {
            String path = ICON_BASE_PATH + iconName + ".png";
            InputStream stream = IconManager.class.getResourceAsStream(path);
            if (stream != null) {
                return new Image(stream, ICON_SIZE, ICON_SIZE, true, true);
            } else {
                LOG.debug("Icon not found: {}", path);
                // Try "file" as fallback
                if (!iconName.equals("file")) {
                    stream = IconManager.class.getResourceAsStream(ICON_BASE_PATH + "file.png");
                    if (stream != null) {
                        return new Image(stream, ICON_SIZE, ICON_SIZE, true, true);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to load icon: {}", iconName, e);
        }
        return null;
    }

    /**
     * Extracts the extension from a file name (lowercase).
     */
    private static String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }

        // Special cases without extension
        String lower = fileName.toLowerCase();
        if (lower.equals("makefile")) return "makefile";
        if (lower.equals("dockerfile")) return "dockerfile";
        if (lower.equals("readme")) return "readme";
        if (lower.equals("license")) return "license";
        if (lower.equals("changelog")) return "changelog";

        return "";
    }

    /**
     * Clears the icon cache.
     */
    public static void clearCache() {
        imageCache.clear();
    }
}
