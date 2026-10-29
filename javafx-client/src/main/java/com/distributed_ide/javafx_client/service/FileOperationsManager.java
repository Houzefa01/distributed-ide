package com.distributed_ide.javafx_client.service;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Comprehensive file operations manager with thread-safe operations and proper error handling.
 * 
 * Key features:
 * - New, Open, Save, Save As, Close operations
 * - Recent files and folders tracking
 * - File system watching
 * - Configurable default project directory
 * - Proper error dialogs
 * - Non-blocking operations
 */
public class FileOperationsManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(FileOperationsManager.class);
    
    // Configuration
    private volatile Path defaultProjectDirectory;
    private volatile int maxRecentItems = 20;
    
    // Recent files and folders
    private final List<String> recentFiles = Collections.synchronizedList(new ArrayList<>());
    private final List<String> recentFolders = Collections.synchronizedList(new ArrayList<>());
    
    // File watching
    private WatchService watchService;
    private final Map<Path, WatchKey> watchKeys = new ConcurrentHashMap<>();
    private final Map<Path, FileWatcher> fileWatchers = new ConcurrentHashMap<>();
    
    // Callbacks
    private volatile Consumer<Path> onFileOpened;
    private volatile Consumer<Path> onFileSaved;
    private volatile Consumer<Path> onFileClosed;
    private volatile Consumer<Path> onProjectOpened;
    private volatile Consumer<String> onFileChanged;
    
    // State
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final Map<String, String> openFiles = new ConcurrentHashMap<>();
    
    public FileOperationsManager() {
        initialize();
    }
    
    /**
     * Initialize the file operations manager.
     */
    private void initialize() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            startFileWatcher();
        } catch (IOException e) {
            LOG.error("Failed to initialize file watcher", e);
        }
        
        // Set default project directory to user home
        defaultProjectDirectory = Paths.get(System.getProperty("user.home"), "Projects");
    }
    
    /**
     * Set default project directory.
     */
    public void setDefaultProjectDirectory( Path directory) {
        this.defaultProjectDirectory = directory;
        LOG.info("Default project directory set to: {}", directory);
    }
    
    /**
     * Get default project directory.
     */
    
    public Path getDefaultProjectDirectory() {
        return defaultProjectDirectory;
    }
    
    /**
     * Set callback for file opened events.
     */
    public void setOnFileOpened( Consumer<Path> callback) {
        this.onFileOpened = callback;
    }
    
    /**
     * Set callback for file saved events.
     */
    public void setOnFileSaved( Consumer<Path> callback) {
        this.onFileSaved = callback;
    }
    
    /**
     * Set callback for file closed events.
     */
    public void setOnFileClosed( Consumer<Path> callback) {
        this.onFileClosed = callback;
    }
    
    /**
     * Set callback for project opened events.
     */
    public void setOnProjectOpened( Consumer<Path> callback) {
        this.onProjectOpened = callback;
    }
    
    /**
     * Set callback for file changed events.
     */
    public void setOnFileChanged( Consumer<String> callback) {
        this.onFileChanged = callback;
    }
    
    /**
     * Create a new file.
     */
    
    public CompletableFuture<Path> newFile( Stage parentStage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Platform.runLater(() -> {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("New File");
                    fileChooser.setInitialDirectory(defaultProjectDirectory.toFile());

                    File file = fileChooser.showSaveDialog(parentStage);
                    if (file != null) {
                        Path path = file.toPath();

                        try {
                            if (!Files.exists(path)) {
                                Files.createFile(path);
                                addToRecentFiles(path.toString());

                                // Start watching the file
                                watchFile(path);

                                // Notify file opened
                                if (onFileOpened != null) {
                                    onFileOpened.accept(path);
                                }

                                LOG.info("Created new file: {}", path);
                            } else {
                                Platform.runLater(() -> {
                                    Alert alert = new Alert(Alert.AlertType.ERROR);
                                    alert.setTitle("File Exists");
                                    alert.setHeaderText("File already exists");
                                    alert.setContentText("The file '" + path.getFileName() + "' already exists.");
                                    alert.showAndWait();
                                });
                            }
                        } catch (IOException e) {
                            LOG.error("Failed to create file: {}", path, e);
                            Platform.runLater(() -> showErrorAlert("Failed to Create File",
                                "Could not create file: " + path + "\nError: " + e.getMessage()));
                        }
                    }
                });
            } catch (Exception e) {
                LOG.error("Error in new file dialog", e);
            }
            return null;
        });
    }
    
    /**
     * Open an existing file.
     */
    
    public CompletableFuture<Path> openFile( Stage parentStage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Platform.runLater(() -> {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Open File");
                    fileChooser.setInitialDirectory(defaultProjectDirectory.toFile());

                    File file = fileChooser.showOpenDialog(parentStage);
                    if (file != null) {
                        Path path = file.toPath();

                        if (Files.exists(path) && Files.isRegularFile(path)) {
                            addToRecentFiles(path.toString());

                            // Start watching the file
                            watchFile(path);

                            // Notify file opened
                            if (onFileOpened != null) {
                                onFileOpened.accept(path);
                            }

                            LOG.info("Opened file: {}", path);
                        } else {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("File Not Found");
                                alert.setHeaderText("File not found or not a regular file");
                                alert.setContentText("The file '" + path + "' does not exist or is not a regular file.");
                                alert.showAndWait();
                            });
                        }
                    }
                });
            } catch (Exception e) {
                LOG.error("Error in open file dialog", e);
            }
            return null;
        });
    }
    
    /**
     * Save a file.
     */
    
    public CompletableFuture<Boolean> saveFile( Path path,  String content) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.writeString(path, content);
                addToRecentFiles(path.toString());
                
                // Notify file saved
                if (onFileSaved != null) {
                    onFileSaved.accept(path);
                }
                
                LOG.info("Saved file: {}", path);
                return true;
            } catch (IOException e) {
                LOG.error("Failed to save file: {}", path, e);
                Platform.runLater(() -> showErrorAlert("Failed to Save File", 
                    "Could not save file: " + path + "\nError: " + e.getMessage()));
                return false;
            }
        });
    }
    
    /**
     * Save file with dialog.
     */
    
    public CompletableFuture<Path> saveFileAs( Path currentPath,  String content,  Stage parentStage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Platform.runLater(() -> {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Save As");
                    fileChooser.setInitialFileName(currentPath.getFileName().toString());
                    fileChooser.setInitialDirectory(currentPath.getParent().toFile());

                    File file = fileChooser.showSaveDialog(parentStage);
                    if (file != null) {
                        Path newPath = file.toPath();

                        try {
                            Files.writeString(newPath, content);
                            addToRecentFiles(newPath.toString());

                            // Notify file saved
                            if (onFileSaved != null) {
                                onFileSaved.accept(newPath);
                            }

                            LOG.info("Saved file as: {}", newPath);
                        } catch (IOException e) {
                            LOG.error("Failed to save file as: {}", newPath, e);
                            Platform.runLater(() -> showErrorAlert("Failed to Save File",
                                "Could not save file as: " + newPath + "\nError: " + e.getMessage()));
                        }
                    }
                });
            } catch (Exception e) {
                LOG.error("Error in save file as dialog", e);
            }
            return null;
        });
    }
    
    /**
     * Close a file.
     */
    
    public CompletableFuture<Boolean> closeFile( Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Stop watching the file
                stopWatchingFile(path);
                
                // Remove from open files
                openFiles.remove(path.toString());
                
                // Notify file closed
                if (onFileClosed != null) {
                    onFileClosed.accept(path);
                }
                
                LOG.info("Closed file: {}", path);
                return true;
            } catch (Exception e) {
                LOG.error("Failed to close file: {}", path, e);
                return false;
            }
        });
    }
    
    /**
     * Open a project folder.
     */
    
    public CompletableFuture<Path> openProject( Stage parentStage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Platform.runLater(() -> {
                    DirectoryChooser directoryChooser = new DirectoryChooser();
                    directoryChooser.setTitle("Open Project");
                    directoryChooser.setInitialDirectory(defaultProjectDirectory.toFile());

                    File directory = directoryChooser.showDialog(parentStage);
                    if (directory != null) {
                        Path projectPath = directory.toPath();

                        if (Files.exists(projectPath) && Files.isDirectory(projectPath)) {
                            addToRecentFolders(projectPath.toString());

                            // Watch the project directory
                            watchDirectory(projectPath);

                            // Notify project opened
                            if (onProjectOpened != null) {
                                onProjectOpened.accept(projectPath);
                            }

                            LOG.info("Opened project: {}", projectPath);
                        } else {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("Directory Not Found");
                                alert.setHeaderText("Directory not found");
                                alert.setContentText("The directory '" + projectPath + "' does not exist.");
                                alert.showAndWait();
                            });
                        }
                    }
                });
            } catch (Exception e) {
                LOG.error("Error in open project dialog", e);
            }
            return null;
        });
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
     * Get recent folders list.
     */
    
    public List<String> getRecentFolders() {
        synchronized (recentFolders) {
            return new ArrayList<>(recentFolders);
        }
    }
    
    /**
     * Add a file to recent files list.
     */
    private void addToRecentFiles( String pathStr) {
        synchronized (recentFiles) {
            // Remove if already exists
            recentFiles.removeIf(pathStr::equals);
            // Add to front
            recentFiles.add(0, pathStr);
            
            // Keep only recent items
            while (recentFiles.size() > maxRecentItems) {
                recentFiles.remove(recentFiles.size() - 1);
            }
        }
    }
    
    /**
     * Add a folder to recent folders list.
     */
    private void addToRecentFolders( String folderPath) {
        synchronized (recentFolders) {
            // Remove if already exists
            recentFolders.removeIf(folderPath::equals);
            // Add to front
            recentFolders.add(0, folderPath);
            
            // Keep only recent items
            while (recentFolders.size() > maxRecentItems) {
                recentFolders.remove(recentFolders.size() - 1);
            }
        }
    }
    
    /**
     * Watch a file for changes.
     */
    private void watchFile( Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null && Files.exists(parent)) {
                WatchKey key = parent.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                watchKeys.put(parent, key);
                
                FileWatcher watcher = new FileWatcher(path);
                fileWatchers.put(path, watcher);
                
                LOG.debug("Started watching file: {}", path);
            }
        } catch (IOException e) {
            LOG.error("Failed to watch file: {}", path, e);
        }
    }
    
    /**
     * Stop watching a file.
     */
    private void stopWatchingFile( Path path) {
        FileWatcher watcher = fileWatchers.remove(path);
        if (watcher != null) {
            watcher.stop();
        }
    }
    
    /**
     * Watch a directory for changes.
     */
    private void watchDirectory( Path path) {
        try {
            WatchKey key = path.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
            watchKeys.put(path, key);
            
            LOG.info("Started watching directory: {}", path);
        } catch (IOException e) {
            LOG.error("Failed to watch directory: {}", path, e);
        }
    }
    
    /**
     * Start the file watcher thread.
     */
    private void startFileWatcher() {
        Thread watcherThread = new Thread(() -> {
            while (!isShuttingDown.get()) {
                try {
                    WatchKey key = watchService.take();
                    processWatchKey(key);
                } catch (InterruptedException e) {
                    LOG.info("File watcher interrupted");
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
     * Process a watch key event.
     */
    private void processWatchKey( WatchKey key) {
        for (WatchEvent<?> event : key.pollEvents()) {
            Path context = (Path) key.watchable();
            Path fullPath = context.resolve((Path) event.context());
            
            if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                // Notify file changed
                if (onFileChanged != null) {
                    onFileChanged.accept(fullPath.toString());
                }
                
                // Update file watcher
                FileWatcher watcher = fileWatchers.get(fullPath);
                if (watcher != null) {
                    watcher.onFileChanged();
                }
            }
        }
        
        // Reset the key
        boolean valid = key.reset();
        if (!valid) {
            // Key is no longer valid, remove it
            watchKeys.values().removeIf(k -> k == key);
        }
    }
    
    /**
     * Show error dialog.
     */
    private void showErrorAlert( String title,  String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Cleanup resources.
     */
    public void shutdown() {
        isShuttingDown.set(true);
        
        try {
            // Stop all watchers
            for (FileWatcher watcher : fileWatchers.values()) {
                watcher.stop();
            }
            fileWatchers.clear();
            
            // Cancel all watch keys
            for (WatchKey key : watchKeys.values()) {
                key.cancel();
            }
            watchKeys.clear();
            
            // Close watch service
            if (watchService != null) {
                watchService.close();
            }
            
            // Clear state
            recentFiles.clear();
            recentFolders.clear();
            openFiles.clear();
            
            LOG.info("File operations manager shutdown complete");
        } catch (Exception e) {
            LOG.error("Error during file operations manager shutdown", e);
        }
    }
    
    /**
     * File watcher for individual files.
     */
    private static class FileWatcher {
        private final Path filePath;
        private volatile long lastModified;
        
        public FileWatcher( Path filePath) {
            this.filePath = filePath;
            this.lastModified = 0;
        }
        
        public void onFileChanged() {
            try {
                long currentModified = Files.getLastModifiedTime(filePath).toMillis();
                if (currentModified > lastModified) {
                    lastModified = currentModified;
                    LOG.debug("File changed: {}", filePath);
                }
            } catch (IOException e) {
                LOG.error("Failed to check file modification time: {}", filePath, e);
            }
        }
        
        public void stop() {
            // Cleanup if needed
        }
    }
}
