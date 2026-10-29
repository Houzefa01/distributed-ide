package com.distributed_ide.javafx_client.service;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Watches the project directory for external file changes (create, modify, delete).
 * Notifies listeners on the JavaFX Application Thread.
 */
public class FileWatcherService {

    private static final Logger LOG = LoggerFactory.getLogger(FileWatcherService.class);

    public enum ChangeType { CREATED, MODIFIED, DELETED }

    public record FileChange(Path path, ChangeType type) {}

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread watchThread;
    private volatile WatchService watchService;
    private volatile Path watchedRoot;
    private final Set<Consumer<FileChange>> listeners = new CopyOnWriteArraySet<>();

    public void addListener(Consumer<FileChange> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<FileChange> listener) {
        listeners.remove(listener);
    }

    /**
     * Starts watching the given directory (non-recursive top-level watch).
     * For deeper watching, subdirectories are registered as they are discovered.
     */
    public void watch(Path directory) {
        stop();
        if (directory == null || !Files.isDirectory(directory)) return;

        watchedRoot = directory;
        running.set(true);

        watchThread = new Thread(() -> {
            try {
                watchService = FileSystems.getDefault().newWatchService();
                registerRecursive(directory, watchService);
                LOG.info("FileWatcher started on {}", directory);

                while (running.get()) {
                    WatchKey key;
                    try {
                        // Check if watchService is still valid before polling
                        if (watchService == null) {
                            break;
                        }
                        key = watchService.poll(2, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (ClosedWatchServiceException e) {
                        // WatchService was closed, exit gracefully
                        LOG.debug("WatchService closed, closing FileWatcher");
                        break;
                    }
                    if (key == null) continue;

                    Path dir = (Path) key.watchable();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path changed = dir.resolve(ev.context());

                        ChangeType type;
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            type = ChangeType.CREATED;
                            // Register new subdirectories
                            if (Files.isDirectory(changed)) {
                                try {
                                    registerRecursive(changed, watchService);
                                } catch (IOException ignored) {}
                            }
                        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            type = ChangeType.MODIFIED;
                        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            type = ChangeType.DELETED;
                        } else {
                            continue;
                        }

                        FileChange change = new FileChange(changed, type);
                        Platform.runLater(() -> notifyListeners(change));
                    }

                    boolean valid = key.reset();
                    if (!valid) {
                        LOG.debug("Watch key no longer valid for {}", key.watchable());
                    }
                }
            } catch (IOException e) {
                LOG.error("FileWatcher error", e);
            } finally {
                closeWatchService();
            }
        }, "FileWatcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    public void stop() {
        running.set(false);
        closeWatchService();
        Thread t = watchThread;
        watchThread = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public Path getWatchedRoot() {
        return watchedRoot;
    }

    private void closeWatchService() {
        WatchService ws = watchService;
        watchService = null;
        if (ws != null) {
            try {
                ws.close();
            } catch (IOException e) {
                LOG.debug("Error closing WatchService", e);
            }
        }
    }

    private void registerRecursive(Path dir, WatchService ws) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) return;
        String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
        if (name.startsWith(".") || name.equals("node_modules") || name.equals("target")
                || name.equals("build") || name.equals("dist")) {
            return;
        }
        dir.register(ws,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    registerRecursive(child, ws);
                }
            }
        }
    }

    private void notifyListeners(FileChange change) {
        for (Consumer<FileChange> listener : listeners) {
            try {
                listener.accept(change);
            } catch (Exception e) {
                LOG.warn("FileWatcher listener error", e);
            }
        }
    }
}
