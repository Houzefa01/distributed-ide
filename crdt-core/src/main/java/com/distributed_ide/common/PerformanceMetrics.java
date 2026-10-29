package com.distributed_ide.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * Performance metrics and monitoring utility.
 * 
 * OPTIMIZATION: Low-overhead metrics collection for performance monitoring
 * and bottleneck identification.
 * 
 * KEY IMPROVEMENTS:
 * ✅ Lock-free metrics using AtomicLong
 * ✅ Minimal CPU overhead
 * ✅ Per-operation statistics
 * ✅ Memory usage tracking
 */
public class PerformanceMetrics {
    private static final Logger LOG = LoggerFactory.getLogger(PerformanceMetrics.class);
    
    private static final Map<String, MetricCounter> counters = new ConcurrentHashMap<>();
    private static final Map<String, OperationStats> timers = new ConcurrentHashMap<>();  // FIXED: Changed OperationTimer to OperationStats
    
    /**
     * Record a counter increment (messages, operations, etc).
     */
    public static void recordCounter(String name) {
        recordCounter(name, 1);
    }
    
    /**
     * Record a counter increment with custom value.
     */
    public static void recordCounter(String name, long delta) {
        counters.computeIfAbsent(name, k -> new MetricCounter(name))
                .increment(delta);
    }
    
    /**
     * Record operation timing (non-blocking).
     */
    public static OperationTimer startTimer(String operationName) {
        return new OperationTimer(operationName);
    }
    
    /**
     * Get counter value.
     */
    public static long getCounter(String name) {
        MetricCounter counter = counters.get(name);
        return counter != null ? counter.getValue() : 0;
    }
    
    /**
     * Get all counters.
     */
    public static Map<String, Long> getAllCounters() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        counters.forEach((k, v) -> result.put(k, v.getValue()));
        return result;
    }
    
    /**
     * Reset a counter.
     */
    public static void resetCounter(String name) {
        MetricCounter counter = counters.get(name);
        if (counter != null) {
            counter.reset();
        }
    }
    
    /**
     * Log current metrics snapshot.
     */
    public static void logMetrics() {
        LOG.info("╔═══════════════════════════════════════════════╗");
        LOG.info("║         PERFORMANCE METRICS SNAPSHOT           ║");
        LOG.info("╠═══════════════════════════════════════════════╣");
        
        counters.forEach((name, counter) -> {
            LOG.info("║ {}: {}", String.format("%-40s", name), counter.getValue());
        });
        
        LOG.info("║                                               ║");
        LOG.info("║ Memory:                                      ║");
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        LOG.info("║   Used: {}/{} MB ({} %)", 
            used / (1024 * 1024),
            max / (1024 * 1024),
            (used * 100) / max
        );
        LOG.info("╚═══════════════════════════════════════════════╝");
    }
    
    /**
     * Internal metric counter.
     */
    public static class MetricCounter {
        private final String name;
        private final AtomicLong value = new AtomicLong(0);
        
        public MetricCounter(String name) {
            this.name = name;
        }
        
        public void increment(long delta) {
            value.addAndGet(delta);
        }
        
        public long getValue() {
            return value.get();
        }
        
        public void reset() {
            value.set(0);
        }
    }
    
    /**
     * Operation timer for measuring execution time.
     */
    public static class OperationTimer {
        private final String name;
        private final long startTime;
        
        public OperationTimer(String name) {
            this.name = name;
            this.startTime = System.nanoTime();
        }
        
        /**
         * Stop timer and record duration.
         */
        public long stop() {
            long elapsedNanos = System.nanoTime() - startTime;
            long elapsedMs = elapsedNanos / 1_000_000;
            
            timers.computeIfAbsent(name, k -> new OperationStats(name))
                  .recordOperation(elapsedMs);
            
            return elapsedMs;
        }
        
        /**
         * Stop and log the duration.
         */
        public long stopAndLog() {
            long elapsedMs = stop();
            if (elapsedMs > 100) {  // Only log if > 100ms
                LOG.debug("⏱️  {} took {}ms", name, elapsedMs);
            }
            return elapsedMs;
        }
    }
    
    /**
     * Operation statistics tracker.
     */
    public static class OperationStats {
        private final String name;
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalTime = new AtomicLong(0);
        private volatile long minTime = Long.MAX_VALUE;
        private volatile long maxTime = 0;
        
        public OperationStats(String name) {
            this.name = name;
        }
        
        public void recordOperation(long timeMs) {
            count.incrementAndGet();
            totalTime.addAndGet(timeMs);
            if (timeMs < minTime) minTime = timeMs;
            if (timeMs > maxTime) maxTime = timeMs;
        }
        
        public long getCount() {
            return count.get();
        }
        
        public long getAverageTime() {
            long c = count.get();
            return c > 0 ? totalTime.get() / c : 0;
        }
        
        public long getMinTime() {
            return minTime == Long.MAX_VALUE ? 0 : minTime;
        }
        
        public long getMaxTime() {
            return maxTime;
        }
        
        @Override
        public String toString() {
            return String.format("%s: count=%d, avg=%dms, min=%dms, max=%dms",
                name, getCount(), getAverageTime(), getMinTime(), getMaxTime());
        }
    }
}
