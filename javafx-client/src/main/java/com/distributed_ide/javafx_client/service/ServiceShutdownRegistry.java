package com.distributed_ide.javafx_client.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central registry for all services and resources requiring coordinated cleanup.
 * Ensures proper shutdown ordering and prevents resource leaks.
 *
 * KEY RESPONSIBILITIES:
 * ✅ Register all services/resources requiring cleanup
 * ✅ Track cleanup status (prevent double cleanup)
 * ✅ Execute shutdown in reverse registration order (LIFO)
 * ✅ Log cleanup operations for debugging
 * ✅ Handle exceptions without cascade failures
 *
 * PROBLEM SOLVED:
 * ❌ BEFORE: Services shutdown in random order → some may still need resources
 *           Listeners never removed → memory leaks from file watchers
 *           No central cleanup → dangling threads, DB connections, WebSocket sessions
 * ✅ AFTER:  Coordinated LIFO shutdown ensures clean termination
 *           All services properly closed before IDE shutdown
 */
public class ServiceShutdownRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceShutdownRegistry.class);

    /**
     * Represents a single service/resource requiring cleanup.
     */
    private static class ServiceEntry {
        final String name;
        final AutoCloseable resource;
        volatile boolean isShutdown = false;
        long shutdownTimeMs = 0;

        ServiceEntry(String name, AutoCloseable resource) {
            this.name = name;
            this.resource = resource;
        }

        @Override
        public String toString() {
            return String.format("Service(%s, shutdown:%s, time:%dms)", name, isShutdown, shutdownTimeMs);
        }
    }

    private final List<ServiceEntry> services = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean isShuttingDown = false;
    private volatile boolean isFullyShutdown = false;

    /**
     * Register a service/resource for coordinated cleanup.
     * Services are shutdown in REVERSE order (LIFO - Last In, First Out).
     * 
     * @param name Descriptive name for logging (e.g., "FileWatcherService")
     * @param resource AutoCloseable resource to cleanup (or null to skip)
     */
    public void registerService(String name, AutoCloseable resource) {
        if (isShuttingDown) {
            LOG.warn("⚠️ Attempted to register service {} during shutdown, ignoring", name);
            return;
        }

        services.add(new ServiceEntry(name, resource));
        LOG.debug("📌 Service registered: {} (total: {})", name, services.size());
    }

    /**
     * Register a service with custom shutdown task (alternative to AutoCloseable).
     * Useful for services with custom shutdown methods.
     * 
     * @param name Descriptive name for logging
     * @param shutdownTask Custom runnable to execute during shutdown
     */
    public void registerServiceTask(String name, Runnable shutdownTask) {
        registerService(name, () -> {
            if (shutdownTask != null) {
                shutdownTask.run();
            }
        });
    }

    /**
     * Get count of registered services.
     */
    public int getServiceCount() {
        return services.size();
    }

    /**
     * Check if a service is known to registry.
     */
    public boolean hasService(String name) {
        return services.stream().anyMatch(s -> s.name.equals(name));
    }

    /**
     * Shutdown all registered services in reverse order (LIFO).
     * Should be called exactly once during application shutdown.
     * Safe to call multiple times (subsequent calls are ignored).
     */
    public synchronized void shutdownAll() {
        if (isFullyShutdown) {
            LOG.debug("ℹ️ Shutdown already completed, skipping");
            return;
        }

        if (isShuttingDown) {
            LOG.warn("⚠️ Shutdown already in progress");
            return;
        }

        isShuttingDown = true;
        LOG.info("🛑 Starting coordinated shutdown of {} services...", services.size());

        // Create reverse iterator for LIFO shutdown
        List<ServiceEntry> reverseServices = new ArrayList<>(services);
        Collections.reverse(reverseServices);

        int shutdownCount = 0;
        int errorCount = 0;

        for (ServiceEntry entry : reverseServices) {
            if (entry.resource == null) {
                LOG.trace("⏭️ Skipping {} (no resource)", entry.name);
                continue;
            }

            if (entry.isShutdown) {
                LOG.trace("⏭️ Skipping {} (already shutdown)", entry.name);
                continue;
            }

            try {
                long startTime = System.currentTimeMillis();
                entry.resource.close();
                entry.shutdownTimeMs = System.currentTimeMillis() - startTime;
                entry.isShutdown = true;
                shutdownCount++;

                LOG.info("✅ Shutdown complete: {} ({}ms)", entry.name, entry.shutdownTimeMs);
            } catch (Exception e) {
                errorCount++;
                LOG.error("❌ Error shutting down {}: {}", entry.name, e.getMessage(), e);
                // Continue shutdown of remaining services despite errors
            }
        }

        isFullyShutdown = true;
        long totalServices = services.size();
        LOG.info("✅ Coordinated shutdown complete: {}/{} services, {} errors",
            shutdownCount, totalServices, errorCount);

        if (errorCount > 0) {
            LOG.warn("⚠️ {} errors occurred during shutdown (see logs above)", errorCount);
        }
    }

    /**
     * Check if shutdown is in progress or complete.
     */
    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    /**
     * Check if shutdown is fully complete.
     */
    public boolean isFullyShutdown() {
        return isFullyShutdown;
    }

    /**
     * Get detailed shutdown status for each service (for debugging).
     */
    public String getShutdownStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Service Shutdown Status ===\n");
        sb.append("Total services: ").append(services.size()).append("\n");
        sb.append("Shutting down: ").append(isShuttingDown).append("\n");
        sb.append("Fully shutdown: ").append(isFullyShutdown).append("\n\n");

        for (int i = 0; i < services.size(); i++) {
            ServiceEntry entry = services.get(i);
            String status = entry.isShutdown ? "✅ SHUTDOWN" : "⏸️ RUNNING";
            sb.append(String.format("%d. %s (%s) - %dms\n", i + 1, entry.name, status, entry.shutdownTimeMs));
        }

        return sb.toString();
    }

    /**
     * Singleton instance - should be created once in application main class.
     * Managed as static to ensure single instance across entire IDE.
     */
    private static volatile ServiceShutdownRegistry instance;

    public static ServiceShutdownRegistry getInstance() {
        if (instance == null) {
            synchronized (ServiceShutdownRegistry.class) {
                if (instance == null) {
                    instance = new ServiceShutdownRegistry();
                    LOG.info("✅ ServiceShutdownRegistry singleton created");
                }
            }
        }
        return instance;
    }

    /**
     * For testing purposes - reset singleton.
     */
    public static void resetInstance() {
        instance = null;
    }
}
