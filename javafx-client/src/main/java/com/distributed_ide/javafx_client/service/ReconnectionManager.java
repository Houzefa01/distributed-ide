package com.distributed_ide.javafx_client.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages automatic reconnection with exponential backoff strategy
 * If connection fails, retries with increasing delays: 1s, 2s, 4s, 8s...
 * 
 * FIX #6 IMPLEMENTATION: Auto-Reconnection with Resilience
 */
public class ReconnectionManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(ReconnectionManager.class);
    
    private int reconnectAttempts = 0;
    private static final int MAX_ATTEMPTS = 10;
    private static final long INITIAL_BACKOFF_MS = 1000;      // 1 second
    private static final long MAX_BACKOFF_MS = 60_000;        // 60 seconds
    
    private final ScheduledExecutorService scheduler = 
        Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "ReconnectionManager");
            t.setDaemon(true);
            return t;
        });
    
    /**
     * Functional interface for reconnection action
     */
    @FunctionalInterface
    public interface ReconnectAction {
        void execute() throws Exception;
    }
    
    /**
     * Calculate next backoff delay with exponential strategy
     * 1s, 2s, 4s, 8s, 16s, 32s, 60s, 60s...
     */
    public long getNextBackoffMs() {
        // shifts: 1 << 0 = 1, 1 << 1 = 2, 1 << 2 = 4, etc.
        long backoff = INITIAL_BACKOFF_MS * (1L << Math.min(reconnectAttempts, 5));
        return Math.min(backoff, MAX_BACKOFF_MS);
    }
    
    /**
     * Schedule reconnection attempt after backoff delay
     * 
     * @param onReconnect   Action to execute when retrying
     */
    public void scheduleReconnect(ReconnectAction onReconnect) {
        if (reconnectAttempts >= MAX_ATTEMPTS) {
            LOG.error("❌ Max reconnection attempts ({}) reached, giving up", MAX_ATTEMPTS);
            return;
        }
        
        long delayMs = getNextBackoffMs();
        reconnectAttempts++;
        
        LOG.info("📍 Scheduling reconnect in {}ms (attempt {}/{})",
            delayMs, reconnectAttempts, MAX_ATTEMPTS);
        
        scheduler.schedule(() -> {
            try {
                onReconnect.execute();
                reset();  // Success! Reset attempts
                LOG.info("✅ Reconnected successfully!");
            } catch (Exception e) {
                LOG.warn("⚠️  Reconnect attempt {} failed: {}", reconnectAttempts, e.getMessage());
                // Schedule next retry
                scheduleReconnect(onReconnect);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Reset backoff counter after successful connection
     */
    public void reset() {
        reconnectAttempts = 0;
        LOG.debug("Reconnection attempts reset");
    }
    
    /**
     * Get current attempt number
     */
    public int getAttempts() {
        return reconnectAttempts;
    }
    
    /**
     * Check if max attempts reached
     */
    public boolean isMaxAttemptsReached() {
        return reconnectAttempts >= MAX_ATTEMPTS;
    }
    
    /**
     * Shutdown scheduler gracefully
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("ReconnectionManager shutdown");
    }
}
