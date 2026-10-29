package com.distributed_ide.javafx_client.service;

import com.distributed_ide.javafx_client.file.AtomicFileSaveService;
import com.distributed_ide.javafx_client.model.ProjectNode;
import com.distributed_ide.javafx_client.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * File access service: read, write, list.
 */
public class FileService {

    private static final Logger LOG = LoggerFactory.getLogger(FileService.class);
    private static final int MAX_CACHE_ENTRIES = 50;
    private static final long MAX_CACHEABLE_SIZE = 512 * 1024; // 512 KB

    private final Map<Path, String> fileCache = new LinkedHashMap<>(MAX_CACHE_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, String> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    /**
     * Reads file content as UTF-8 (with LRU cache).
     * Validates file exists and is readable before reading.
     */
    public String readFile(Path path) throws IOException {
        if (path == null) {
            throw new IOException("Path is null");
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("Not a regular file: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new IOException("File is not readable: " + path);
        }
        
        String cached = fileCache.get(path);
        if (cached != null) {
            LOG.debug("Cache hit for {}", path);
            return cached;
        }
        
        String content = Files.readString(path, StandardCharsets.UTF_8);
        if (content.length() <= MAX_CACHEABLE_SIZE) {
            fileCache.put(path, content);
        }
        return content;
    }

    /**
     * Writes content as UTF-8 (creates or overwrites) using atomic save.
     * Prevents data loss if IDE crashes during write.
     * Invalidates cache entry.
     */
    public void writeFile(Path path, String content) throws IOException {
        if (path == null) throw new IOException("Path is null");
        Files.createDirectories(path.getParent());
        String toWrite = content != null ? content : "";
        
        // Use atomic file save to prevent data loss/corruption
        AtomicFileSaveService atomicSave = AtomicFileSaveService.getInstance();
        AtomicFileSaveService.SaveResult result = atomicSave.atomicSave(path, toWrite);
        
        if (!result.success) {
            LOG.error("❌ Atomic save failed for {}: {}", path, result.error);
            throw new IOException("Failed to save file atomically: " + result.error.getMessage(), result.error);
        }
        
        LOG.debug("✅ File saved atomically: {} ({} bytes, hash: {})", 
            path, result.bytesSaved, result.savedHash);
        
        // Update cache if file is cacheable
        if (toWrite.length() <= MAX_CACHEABLE_SIZE) {
            fileCache.put(path, toWrite);
        } else {
            fileCache.remove(path);
        }
    }

    /**
     * Clears the file content cache (e.g., after external changes like git pull).
     */
    public void clearCache() {
        fileCache.clear();
    }

    /**
     * Lists direct children of a directory (sorted: directories first, then files, by name).
     */
    public List<Path> listChildren(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            StreamSupport.stream(stream.spliterator(), false)
                    .sorted(FileUtil.pathComparator())
                    .forEach(result::add);
        }
        return result;
    }

    /**
     * Builds a ProjectNode tree from a directory (one level by default).
     */
    public ProjectNode loadTree(Path root, int maxDepth) {
        if (root == null || !Files.exists(root)) return ProjectNode.directory(root);
        ProjectNode node = ProjectNode.directory(root);
        if (maxDepth <= 0) return node;
        try {
            for (Path child : listChildren(root)) {
                if (Files.isDirectory(child)) {
                    node.getChildren().add(loadTree(child, maxDepth - 1));
                } else {
                    node.getChildren().add(ProjectNode.file(child));
                }
            }
        } catch (IOException e) {
            LOG.warn("List children failed: {}", root, e);
        }
        return node;
    }
}
