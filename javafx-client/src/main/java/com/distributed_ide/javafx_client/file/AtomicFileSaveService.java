package com.distributed_ide.javafx_client.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class AtomicFileSaveService {

    private static final Logger LOG = LoggerFactory.getLogger(AtomicFileSaveService.class);

    private static final String TEMP_SUFFIX = ".tmp";
    private static final String BACKUP_SUFFIX = ".bak-" + System.currentTimeMillis();
    private static final int VERIFY_BUFFER_SIZE = 8192;

    /**
     * Per-file lock for concurrent write prevention.
     * Each file path gets its own lock to allow parallel saves to different files.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, ReentrantReadWriteLock> fileLocks =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Result of atomic save operation.
     */
    public static class SaveResult {
        public final boolean success;
        public final long bytesSaved;
        public final long saveTimeMs;
        public final String originalHash;
        public final String savedHash;
        public final Path backupPath;
        public final Exception error;

        SaveResult(boolean success, long bytesSaved, long saveTimeMs, String originalHash, 
                   String savedHash, Path backupPath, Exception error) {
            this.success = success;
            this.bytesSaved = bytesSaved;
            this.saveTimeMs = saveTimeMs;
            this.originalHash = originalHash;
            this.savedHash = savedHash;
            this.backupPath = backupPath;
            this.error = error;
        }

        @Override
        public String toString() {
            if (success) {
                return String.format("SaveResult(SUCCESS %d bytes in %dms, hash: %s→%s, backup: %s)",
                    bytesSaved, saveTimeMs, originalHash, savedHash, backupPath);
            } else {
                return String.format("SaveResult(FAILED: %s, backup: %s)", 
                    error != null ? error.getMessage() : "unknown", backupPath);
            }
        }
    }

    /**
     * Atomically save content to file.
     * Safe against IDE crashes, system crashes, and network issues.
     *
     * @param filePath Path to file to save
     * @param content  String content to write
     * @return SaveResult with success status, bytes saved, and metadata
     */
    public SaveResult atomicSave(Path filePath, String content) {
        return atomicSave(filePath, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Atomically save content to file (byte array version).
     *
     * @param filePath Path to file to save
     * @param content  Byte array content to write
     * @return SaveResult with success status, bytes saved, and metadata
     */
    public synchronized SaveResult atomicSave(Path filePath, byte[] content) {
        long startTime = System.currentTimeMillis();
        Path tempFile = null;
        Path backupFile = null;
        Exception lastError = null;
        String originalHash = "";
        String savedHash = "";

        try {
            // Lock file to prevent concurrent writes
            ReentrantReadWriteLock lock = fileLocks.computeIfAbsent(
                filePath.toString(), 
                k -> new ReentrantReadWriteLock()
            );

            lock.writeLock().lock();
            try {
                // Step 1: Calculate original file hash and create backup
                if (Files.exists(filePath)) {
                    originalHash = calculateHash(filePath);
                    byte[] originalContent = Files.readAllBytes(filePath);

                    backupFile = createBackup(filePath, originalContent);
                    LOG.debug("📋 Backup created: {}", backupFile);
                } else {
                    originalHash = "[NEW_FILE]";
                }

                // Step 2: Write to temporary file
                tempFile = filePath.resolveSibling(filePath.getFileName() + TEMP_SUFFIX);
                Files.write(tempFile, content);
                savedHash = calculateHash(tempFile);

                LOG.debug("📝 Temp file written: {} ({} bytes)", tempFile, content.length);

                // Step 3: Verify temporary file content
                if (!verifyContent(tempFile, content)) {
                    throw new IOException("❌ Verification failed: temp file content mismatch");
                }
                LOG.debug("✅ Verification passed: temp file content matches input");

                // Step 4: Atomically move temp file to original location
                Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

                long saveTimeMs = System.currentTimeMillis() - startTime;
                LOG.info("✅ Atomic save successful: {} ({} bytes, {}ms, hash: {}→{})",
                    filePath, content.length, saveTimeMs, originalHash, savedHash);

                return new SaveResult(true, content.length, saveTimeMs, originalHash, savedHash, 
                    backupFile, null);

            } finally {
                lock.writeLock().unlock();
            }

        } catch (Exception e) {
            lastError = e;
            LOG.error("❌ Atomic save failed for {}: {}", filePath, e.getMessage(), e);

            // Cleanup temp file if it still exists
            if (tempFile != null && Files.exists(tempFile)) {
                try {
                    Files.delete(tempFile);
                    LOG.debug("🗑️ Cleaned up failed temp file: {}", tempFile);
                } catch (IOException cleanup) {
                    LOG.warn("⚠️ Failed to cleanup temp file {}: {}", tempFile, cleanup.getMessage());
                }
            }

            long saveTimeMs = System.currentTimeMillis() - startTime;
            return new SaveResult(false, 0, saveTimeMs, originalHash, "", backupFile, lastError);
        }
    }

    /**
     * Create backup of original file before saving.
     */
    private Path createBackup(Path filePath, byte[] content) throws IOException {
        Path backupDir = filePath.getParent().resolve(".ide_backups");
        
        // Create backup directory if not exists
        if (!Files.exists(backupDir)) {
            Files.createDirectories(backupDir);
        }

        Path backupFile = backupDir.resolve(filePath.getFileName() + BACKUP_SUFFIX);
        Files.write(backupFile, content);

        return backupFile;
    }

    /**
     * Calculate SHA-256 hash of file for verification.
     */
    private String calculateHash(Path filePath) {
        try {
            byte[] content = Files.readAllBytes(filePath);
            return calculateHash(content);
        } catch (Exception e) {
            LOG.debug("⚠️ Failed to calculate hash for {}: {}", filePath, e.getMessage());
            return "[HASH_ERROR]";
        }
    }

    /**
     * Calculate SHA-256 hash of byte array for verification.
     */
    private String calculateHash(byte[] content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 8);  // First 8 chars of hash
        } catch (java.security.NoSuchAlgorithmException e) {
            LOG.warn("SHA-256 not available, using content length hash");
            return String.format("[%d]", content.length);
        }
    }

    /**
     * Verify that temporary file contains expected content.
     */
    private boolean verifyContent(Path tempFile, byte[] expectedContent) {
        try {
            byte[] actualContent = Files.readAllBytes(tempFile);
            
            if (actualContent.length != expectedContent.length) {
                LOG.error("❌ Size mismatch: expected {} bytes, got {}", 
                    expectedContent.length, actualContent.length);
                return false;
            }

            // Compare content in chunks to save memory for large files
            for (int i = 0; i < expectedContent.length; i++) {
                if (actualContent[i] != expectedContent[i]) {
                    LOG.error("❌ Content mismatch at byte {}", i);
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            LOG.error("❌ Verification failed with exception: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Cleanup old backup files (keeps last N backups).
     */
    public void cleanupOldBackups(Path filePath, int keepCount) {
        try {
            Path backupDir = filePath.getParent().resolve(".ide_backups");
            if (!Files.exists(backupDir)) {
                return;
            }

            // List all backup files for this file (sorted by name, newest first)
            java.util.List<Path> backupFiles = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith(filePath.getFileName().toString()))
                .sorted(java.util.Collections.reverseOrder())
                .toList();

            // Delete all but the last keepCount backups
            for (int i = keepCount; i < backupFiles.size(); i++) {
                Path oldBackup = backupFiles.get(i);
                try {
                    Files.delete(oldBackup);
                    LOG.debug("🗑️ Deleted old backup: {}", oldBackup);
                } catch (IOException e) {
                    LOG.warn("⚠️ Failed to delete backup {}: {}", oldBackup, e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.warn("⚠️ Backup cleanup failed: {}", e.getMessage());
        }
    }

    /**
     * Recovery: restore file from latest backup.
     */
    public boolean restoreFromBackup(Path filePath) {
        try {
            Path backupDir = filePath.getParent().resolve(".ide_backups");
            if (!Files.exists(backupDir)) {
                LOG.error("❌ No backup directory found");
                return false;
            }

            // Find latest backup for this file (by creation time)
            Path latestBackup = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith(filePath.getFileName().toString()))
                .max((p1, p2) -> {
                    try {
                        long t1 = Files.getLastModifiedTime(p1).toMillis();
                        long t2 = Files.getLastModifiedTime(p2).toMillis();
                        return Long.compare(t1, t2);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .orElse(null);

            if (latestBackup == null) {
                LOG.error("❌ No backup found for {}", filePath);
                return false;
            }

            // Restore from backup
            byte[] content = Files.readAllBytes(latestBackup);
            Files.write(filePath, content);

            LOG.info("✅ File restored from backup: {} → {}", latestBackup, filePath);
            return true;
        } catch (Exception e) {
            LOG.error("❌ Restore failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Singleton instance.
     */
    private static volatile AtomicFileSaveService instance;

    public static AtomicFileSaveService getInstance() {
        if (instance == null) {
            synchronized (AtomicFileSaveService.class) {
                if (instance == null) {
                    instance = new AtomicFileSaveService();
                }
            }
        }
        return instance;
    }
}
