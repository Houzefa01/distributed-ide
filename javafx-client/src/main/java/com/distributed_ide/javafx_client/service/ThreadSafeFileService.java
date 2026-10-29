package com.distributed_ide.javafx_client.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Thread-safe file service with proper async operations.
 * 
 * Key features:
 * - All I/O operations are asynchronous
 * - Non-blocking file operations
 * - Proper error handling and logging
 * - File system watching
 * - Recent files and folders tracking
 * - Auto-save functionality
 */
public class ThreadSafeFileService {
    
    private static final Logger LOG = LoggerFactory.getLogger(ThreadSafeFileService.class);
    
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "file-service-io");
        t.setDaemon(true);
        return t;
    });
    
    // File system state
    private volatile Path rootPath;
    private final Map<String, String> fileContents = new ConcurrentHashMap<>();
    private final Map<String, Long> fileTimestamps = new ConcurrentHashMap<>();
    private final Set<Path> watchedPaths = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    // File watching
    private WatchService watchService;
    private final Map<Path, WatchKey> watchKeys = new ConcurrentHashMap<>();
    
    // Recent files and folders
    private final List<String> recentFiles = Collections.synchronizedList(new ArrayList<>());
    private final List<String> recentFolders = Collections.synchronizedList(new ArrayList<>());
    private final int maxRecentItems = 20;
    
    // Auto-save configuration
    private volatile boolean autoSaveEnabled = true;
    private volatile long autoSaveInterval = 30000; // 30 seconds
    private volatile Timer autoSaveTimer;
    
    public ThreadSafeFileService() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            startFileWatcher();
        } catch (IOException e) {
            LOG.error("Failed to initialize file watcher", e);
        }
    }

    /**
     * Start the file watcher thread.
     */
    private void startFileWatcher() {
        Thread watcherThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    WatchKey key = watchService.take();
                    Path watchedPath = (Path) key.watchable();

                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = watchedPath.resolve((Path) event.context());
                        LOG.debug("File system event: {} on {}", event.kind(), changed);
                    }

                    key.reset();
                } catch (InterruptedException e) {
                    LOG.info("File watcher interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOG.error("Error in file watcher", e);
                }
            }
        }, "file-watcher");

        watcherThread.setDaemon(true);
        watcherThread.start();
    }
    
    /**
     * Set the root directory for the project.
     */
    public void setRootPath( Path path) {
        this.rootPath = path;
        LOG.info("Set root path: {}", path);
    }
    
    /**
     * Get the current root directory.
     */
    
    public Path getRootPath() {
        return rootPath;
    }
    
    /**
     * Read file content asynchronously.
     */
    
    public CompletableFuture<String> readFile( Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.readString(path);
            } catch (IOException e) {
                    throw new RuntimeException("Failed to read file: " + path, e);
            }
        }, ioExecutor);
    }
    
    /**
     * Write file content asynchronously.
     */
    
    public CompletableFuture<Void> writeFile( Path path,  String content) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.writeString(path, content);
                fileContents.put(path.toString(), content);
                fileTimestamps.put(path.toString(), System.currentTimeMillis());
                
                // Update recent files
                addToRecentFiles(path.toString());
                
                LOG.debug("Saved file: {}", path);
            } catch (IOException e) {
                    throw new RuntimeException("Failed to write file: " + path, e);
            }
        }, ioExecutor);
    }
    
    /**
     * Save file with proper error handling.
     */

    public CompletableFuture<Boolean> saveFile( Path path,  String content) {
        if (content == null) {
            return CompletableFuture.completedFuture(false);
        }

        return writeFile(path, content).thenApply(v -> {
            LOG.info("Successfully saved file: {}", path);
            return true;
        }).exceptionally(e -> {
            LOG.error("Exception while saving file: {}", path, e);
            return false;
        });
    }
    
    /**
     * Delete file asynchronously.
     */
    
    public CompletableFuture<Boolean> deleteFile( Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.delete(path);
                fileContents.remove(path.toString());
                fileTimestamps.remove(path.toString());
                
                // Remove from recent files
                removeFromRecentFiles(path.toString());
                
                LOG.info("Deleted file: {}", path);
                return true;
            } catch (IOException e) {
                LOG.error("Failed to delete file: {}", path, e);
                return false;
            }
        }, ioExecutor);
    }
    
    /**
     * Get all file contents.
     */
    
    public Map<String, String> getAllContents() {
        return new HashMap<>(fileContents);
    }
    
    /**
     * Get file content.
     */
    
    public String getContent( String pathStr) {
        return fileContents.get(pathStr);
    }
    
    /**
     * Get file timestamp.
     */
    public long getFileTimestamp( String pathStr) {
        return fileTimestamps.getOrDefault(pathStr, 0L);
    }
    
    /**
     * Start watching a directory for changes.
     */
    public void watchDirectory( Path path) {
        try {
            WatchKey key = path.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
            watchKeys.put(path, key);
            watchedPaths.add(path);
            LOG.info("Started watching directory: {}", path);
        } catch (IOException e) {
            LOG.error("Failed to watch directory: {}", path, e);
        }
    }
    
    /**
     * Stop watching a directory.
     */
    public void stopWatching( Path path) {
        WatchKey key = watchKeys.remove(path);
        if (key != null) {
            key.cancel();
            watchedPaths.remove(path);
            LOG.info("Stopped watching directory: {}", path);
        }
    }
    
    /**
     * Add a file to recent files list.
     */
    private void addToRecentFiles( String pathStr) {
        String fileName = Paths.get(pathStr).getFileName().toString();
        synchronized (recentFiles) {
            // Remove if already exists
            recentFiles.removeIf(fileName::equals);
            // Add to front
            recentFiles.add(0, fileName);
            
            // Keep only recent items
            while (recentFiles.size() > maxRecentItems) {
                recentFiles.remove(recentFiles.size() - 1);
            }
        }
    }
    
    /**
     * Remove a file from recent files list.
     */
    private void removeFromRecentFiles( String pathStr) {
        String fileName = Paths.get(pathStr).getFileName().toString();
        synchronized (recentFiles) {
            recentFiles.removeIf(fileName::equals);
        }
    }
    
    /**
     * Get recent files list.
     */
    
    public List<String> getRecentFiles() {
        synchronized (recentFiles) {
            return new ArrayList<>(recentFiles);
        }
    }
    
    /**
     * Add a folder to recent folders list.
     */
    public void addToRecentFolders( String folderPath) {
        String folderName = Paths.get(folderPath).getFileName().toString();
        synchronized (recentFolders) {
            // Remove if already exists
            recentFolders.removeIf(folderName::equals);
            // Add to front
            recentFolders.add(0, folderName);
            
            // Keep only recent items
            while (recentFolders.size() > maxRecentItems) {
                recentFolders.remove(recentFolders.size() - 1);
            }
        }
    }
    
    /**
     * Get recent folders list.
     */
    
    public List<String> getRecentFolders() {
        synchronized (recentFolders) {
            return new ArrayList<>(recentFolders);
        }
    }
    
    /**
     * Enable/disable auto-save.
     */
    public void setAutoSaveEnabled(boolean enabled) {
        this.autoSaveEnabled = enabled;
        if (enabled) {
            startAutoSave();
        } else {
            stopAutoSave();
        }
    }
    
    /**
     * Set auto-save interval in milliseconds.
     */
    public void setAutoSaveInterval(long intervalMs) {
        this.autoSaveInterval = intervalMs;
        if (autoSaveEnabled) {
            stopAutoSave();
            startAutoSave();
        }
    }
    
    /**
     * Start auto-save timer.
     */
    private void startAutoSave() {
        stopAutoSave();
        autoSaveTimer = new Timer("auto-save", true);
        autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                saveAllFiles();
            }
        }, autoSaveInterval, autoSaveInterval);
        LOG.info("Auto-save enabled with interval: {}ms", autoSaveInterval);
    }
    
    /**
     * Stop auto-save timer.
     */
    private void stopAutoSave() {
        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
            autoSaveTimer = null;
        }
    }
    
    /**
     * Save all modified files.
     */
    public void saveAllFiles() {
        Map<String, String> currentContents = getAllContents();
        List<CompletableFuture<Boolean>> saveTasks = new ArrayList<>();

        for (Map.Entry<String, String> entry : currentContents.entrySet()) {
            String pathStr = entry.getKey();
            String content = entry.getValue();

            Path path = Paths.get(pathStr);

            saveTasks.add(saveFile(path, content));
        }

        // Wait for all saves to complete
        CompletableFuture.allOf(saveTasks.toArray(new CompletableFuture[0])).thenRun(() -> {
            LOG.info("Auto-save completed: {} files saved", currentContents.size());
        }).exceptionally(e -> {
            LOG.error("Auto-save failed", e);
            return null;
        });
    }
    
    /**
     * Get file statistics.
     */
    
    public FileStats getStats() {
        return new FileStats(
            fileContents.size(),
            watchedPaths.size(),
            recentFiles.size(),
            recentFolders.size(),
            autoSaveEnabled,
            autoSaveInterval
        );
    }
    
    /**
     * Cleanup resources.
     */
    public void shutdown() {
        try {
            stopAutoSave();
            
            // Stop all watchers
            for (Path path : new ArrayList<>(watchedPaths)) {
                stopWatching(path);
            }
            
            // Shutdown file watcher
            if (watchService != null) {
                watchService.close();
            }
            
            // Shutdown executor
            ioExecutor.shutdown();
            
            // Clear state
            fileContents.clear();
            fileTimestamps.clear();
            recentFiles.clear();
            recentFolders.clear();
            
            LOG.info("File service shutdown complete");
        } catch (Exception e) {
            LOG.error("Error during file service shutdown", e);
        }
    }
    
    /**
     * File statistics for monitoring.
     */
    public static class FileStats {
        public final int fileCount;
        public final int watchedDirectories;
        public final int recentFilesCount;
        public final int recentFoldersCount;
        public final boolean autoSaveEnabled;
        public final long autoSaveInterval;
        
        public FileStats(int fileCount, int watchedDirectories, int recentFilesCount, int recentFoldersCount, 
                       boolean autoSaveEnabled, long autoSaveInterval) {
            this.fileCount = fileCount;
            this.watchedDirectories = watchedDirectories;
            this.recentFilesCount = recentFilesCount;
            this.recentFoldersCount = recentFoldersCount;
            this.autoSaveEnabled = autoSaveEnabled;
            this.autoSaveInterval = autoSaveInterval;
        }
        
        @Override
        public String toString() {
            return String.format("FileStats{files=%d, watched=%d, recentFiles=%d, recentFolders=%d, autoSave=%s, interval=%dms}", 
                fileCount, watchedDirectories, recentFilesCount, recentFoldersCount, autoSaveEnabled, autoSaveInterval);
        }
    }
}
