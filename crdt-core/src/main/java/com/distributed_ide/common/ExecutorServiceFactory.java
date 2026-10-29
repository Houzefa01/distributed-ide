package com.distributed_ide.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Optimized thread pool factory for backend services.
 * 
 * OPTIMIZATION: Configures thread pools based on CPU cores and workload type.
 * Prevents thread starvation and runaway thread creation.
 * 
 * KEY IMPROVEMENTS:
 * ✅ Adaptive pool sizes based on CPU count
 * ✅ Bounded queues to prevent memory exhaustion
 * ✅ Named threads for debugging
 * ✅ Configurable rejection policies
 * ✅ Proper timeout handling
 */
public class ExecutorServiceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutorServiceFactory.class);
    
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    
    /**
     * Create optimized thread pool for I/O-bound operations (WebSocket, network, etc).
     * Formula: 2 * CPU_COUNT (good for I/O-bound work with blocking calls)
     */
    public static ExecutorService createIOThreadPool(String name, int coreSize, int maxSize, int queueSize) {
        ThreadFactory factory = createThreadFactory(name);
        
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueSize);
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            coreSize,
            maxSize,
            60L,  // Keep-alive: 60 seconds
            TimeUnit.SECONDS,
            queue,
            factory,
            new ThreadPoolExecutor.CallerRunsPolicy()  // Blocking when queue is full
        );
        
        executor.allowCoreThreadTimeOut(true);
        
        LOG.info("✅ I/O thread pool '{}' created: core={}, max={}, queue={}", 
            name, coreSize, maxSize, queueSize);
        
        return executor;
    }
    
    /**
     * Create optimized thread pool for CPU-bound operations.
     * Formula: CPU_COUNT + 1 (optimal for compute-intensive work)
     */
    public static ExecutorService createComputeThreadPool(String name) {
        int poolSize = CPU_COUNT + 1;
        ThreadFactory factory = createThreadFactory(name);
        
        return new ThreadPoolExecutor(
            poolSize,
            poolSize,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            factory,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    /**
     * Create optimized ScheduledExecutorService.
     */
    public static ScheduledExecutorService createScheduledExecutor(String name, int corePoolSize) {
        return new ScheduledThreadPoolExecutor(
            corePoolSize,
            createThreadFactory(name),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    /**
     * Create custom ThreadFactory with descriptive names.
     */
    private static ThreadFactory createThreadFactory(String poolName) {
        AtomicInteger counter = new AtomicInteger(0);
        
        return r -> {
            Thread t = new Thread(r, poolName + "-" + counter.incrementAndGet());
            t.setDaemon(false);
            t.setUncaughtExceptionHandler((thread, ex) -> {
                LOG.error("❌ Uncaught exception in thread {}", thread.getName(), ex);
            });
            return t;
        };
    }
    
    /**
     * Shutdown executor with graceful timeout.
     */
    public static void shutdownExecutor(ExecutorService executor, String name, long timeoutSeconds) {
        if (executor == null) return;
        
        try {
            executor.shutdown();
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                LOG.warn("⚠️  Executor '{}' did not terminate, forcing shutdown", name);
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOG.error("❌ Executor '{}' did not terminate after force shutdown", name);
                }
            }
            LOG.info("✅ Executor '{}' shutdown complete", name);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            LOG.error("Interrupted during executor shutdown: {}", name);
        }
    }
    
    /**
     * Get recommended pool size for I/O operations.
     */
    public static int getRecommendedIOPoolSize() {
        return 2 * CPU_COUNT;
    }
    
    /**
     * Get recommended pool size for compute operations.
     */
    public static int getRecommendedComputePoolSize() {
        return CPU_COUNT + 1;
    }
    
    /**
     * Get CPU count (useful for monitoring).
     */
    public static int getCPUCount() {
        return CPU_COUNT;
    }
}
