package com.distributed_ide.javafx_client.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Performance optimization utilities and metrics.
 * 
 * Features:
 * - File size analysis for operation skipping
 * - Debounce timing recommendations
 * - Memory monitoring and alerts
 * - Operation timing and profiling
 * - Automatic performance tuning
 * 
 * Architecture: Singleton utility class for IDE-wide performance management.
 */
public class PerformanceOptimizer {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceOptimizer.class);

    // File size thresholds
    public static final long LARGE_FILE_THRESHOLD = 1_000_000;  // 1MB
    public static final long VERY_LARGE_FILE_THRESHOLD = 10_000_000;  // 10MB
    public static final long HUGE_FILE_THRESHOLD = 100_000_000;  // 100MB

    // Memory thresholds (in bytes)
    public static final long MEMORY_LOW_THRESHOLD = 100_000_000;  // 100MB free
    public static final long MEMORY_CRITICAL_THRESHOLD = 50_000_000;  // 50MB free

    // Debounce timing (configurable by operation)
    public static final long DEFAULT_DEBOUNCE_MS = 300;
    public static final long FAST_DEBOUNCE_MS = 150;  // For quick operations
    public static final long SLOW_DEBOUNCE_MS = 500;  // For expensive operations

    // Operation timing tracking
    private static final ConcurrentHashMap<String, OperationStats> operationStats = new ConcurrentHashMap<>();

    /**
     * Classify file by size for performance decisions.
     */
    public static FileSizeCategory classifyFileSize(long fileSizeBytes) {
        if (fileSizeBytes <= 0) return FileSizeCategory.EMPTY;
        if (fileSizeBytes < LARGE_FILE_THRESHOLD) return FileSizeCategory.SMALL;
        if (fileSizeBytes < VERY_LARGE_FILE_THRESHOLD) return FileSizeCategory.LARGE;
        if (fileSizeBytes < HUGE_FILE_THRESHOLD) return FileSizeCategory.VERY_LARGE;
        return FileSizeCategory.HUGE;
    }

    /**
     * Determine if syntax highlighting should be skipped for this file size.
     * Returns true if file is too large for efficient highlighting.
     */
    public static boolean shouldSkipHighlighting(long fileSizeBytes) {
        return fileSizeBytes > VERY_LARGE_FILE_THRESHOLD;
    }

    /**
     * Determine if minimap should be rendered for this file size.
     * Returns false if file is too large.
     */
    public static boolean shouldRenderMinimap(long fileSizeBytes) {
        return fileSizeBytes < VERY_LARGE_FILE_THRESHOLD;
    }

    /**
     * Determine optimal debounce timing based on file size and memory.
     * Larger files and lower memory = longer debounce.
     */
    public static long getOptimalDebounceMs(long fileSizeBytes) {
        FileSizeCategory category = classifyFileSize(fileSizeBytes);
        
        // Increase debounce for larger files
        long debounce = switch (category) {
            case EMPTY, SMALL -> FAST_DEBOUNCE_MS;        // 150ms
            case LARGE -> DEFAULT_DEBOUNCE_MS;             // 300ms
            case VERY_LARGE -> SLOW_DEBOUNCE_MS;           // 500ms
            case HUGE -> 1000;                             // 1 second
        };

        // Further increase if memory is low
        if (getAvailableMemoryMB() < MEMORY_LOW_THRESHOLD) {
            debounce = Math.min(debounce * 2, 2000);  // Max 2 seconds
        }

        return debounce;
    }

    /**
     * Get available memory in bytes.
     */
    public static long getAvailableMemory() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryMXBean.getHeapMemoryUsage().getMax();
        return heapMax - heapUsed;
    }

    /**
     * Get available memory in MB.
     */
    public static long getAvailableMemoryMB() {
        return getAvailableMemory() / (1024 * 1024);
    }

    /**
     * Check if memory is low and warn if needed.
     */
    public static boolean isMemoryLow() {
        long available = getAvailableMemory();
        if (available < MEMORY_CRITICAL_THRESHOLD) {
            LOG.warn("Critical memory low: {} MB available", available / (1024 * 1024));
            return true;
        }
        if (available < MEMORY_LOW_THRESHOLD) {
            LOG.info("Memory low: {} MB available", available / (1024 * 1024));
            return true;
        }
        return false;
    }

    /**
     * Record timing for an operation.
     * Logs if operation exceeds expected duration.
     */
    public static void recordOperationTiming(String operationName, long durationMs) {
        OperationStats stats = operationStats.computeIfAbsent(operationName, k -> new OperationStats());
        stats.recordTiming(durationMs);

        // Log slow operations
        if (durationMs > getSlowThresholdMs(operationName)) {
            LOG.warn("Slow operation: {} took {}ms", operationName, durationMs);
        }
    }

    /**
     * Get slow operation threshold based on operation type.
     */
    private static long getSlowThresholdMs(String operationName) {
        return switch (operationName) {
            case "syntax-highlight" -> 500;
            case "find-replace" -> 200;
            case "file-save" -> 1000;
            case "tree-refresh" -> 2000;
            default -> 1000;
        };
    }

    /**
     * Get statistics for an operation.
     */
    public static OperationStats getOperationStats(String operationName) {
        return operationStats.getOrDefault(operationName, new OperationStats());
    }

    /**
     * Clear all operation statistics.
     */
    public static void clearOperationStats() {
        operationStats.clear();
    }

    /**
     * Print performance report.
     */
    public static void printPerformanceReport() {
        LOG.info("╔════════════════════════════════════════╗");
        LOG.info("║       PERFORMANCE REPORT              ║");
        LOG.info("║════════════════════════════════════════║");
        LOG.info("║ Available Memory: {} MB", getAvailableMemoryMB());
        LOG.info("║ Memory Status: {}", isMemoryLow() ? "LOW" : "NORMAL");
        LOG.info("║────────────────────────────────────────║");

        operationStats.forEach((operation, stats) -> {
            LOG.info("║ {}: {} calls, avg {}ms, max {}ms",
                    String.format("%-20s", operation),
                    String.format("%4d", stats.getCallCount()),
                    String.format("%4d", stats.getAvgDurationMs()),
                    String.format("%5d", stats.getMaxDurationMs()));
        });

        LOG.info("╚════════════════════════════════════════╝");
    }

    /**
     * File size category for performance decisions.
     */
    public enum FileSizeCategory {
        EMPTY("Empty", 0),
        SMALL("Small", 100),
        LARGE("Large", 500),
        VERY_LARGE("Very Large", 1000),
        HUGE("Huge", 5000);

        public final String displayName;
        public final int warningThresholdMs;

        FileSizeCategory(String displayName, int warningThresholdMs) {
            this.displayName = displayName;
            this.warningThresholdMs = warningThresholdMs;
        }
    }

    /**
     * Operation statistics tracker.
     */
    public static class OperationStats {
        private volatile long callCount = 0;
        private volatile long totalDurationMs = 0;
        private volatile long minDurationMs = Long.MAX_VALUE;
        private volatile long maxDurationMs = 0;

        public synchronized void recordTiming(long durationMs) {
            callCount++;
            totalDurationMs += durationMs;
            minDurationMs = Math.min(minDurationMs, durationMs);
            maxDurationMs = Math.max(maxDurationMs, durationMs);
        }

        public long getCallCount() { return callCount; }
        public long getTotalDurationMs() { return totalDurationMs; }
        public long getAvgDurationMs() { return callCount > 0 ? totalDurationMs / callCount : 0; }
        public long getMinDurationMs() { return minDurationMs == Long.MAX_VALUE ? 0 : minDurationMs; }
        public long getMaxDurationMs() { return maxDurationMs; }
    }
}
