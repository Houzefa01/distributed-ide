package com.distributed_ide.sync_server;

import com.distributed_ide.sync.server.SyncWebSocketServer;
import com.distributed_ide.common.NetworkConfig;
import com.distributed_ide.common.PerformanceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class SyncServerApp {
    private static final Logger LOG = LoggerFactory.getLogger(SyncServerApp.class);

    public static void main(String[] args) throws Exception {
        int port = intArg(args, 0, Integer.getInteger("sync.port", 8765));
        String nodeId = stringArg(args, 1, System.getProperty("sync.nodeId", "sync-1"));
        String serverToken = stringArg(args, 2, System.getProperty("sync.serverToken", ""));

        // Log network recommendations
        NetworkConfig.logNetworkRecommendations();

        SyncWebSocketServer server = new SyncWebSocketServer(port, nodeId, 
            serverToken != null && !serverToken.isEmpty() ? serverToken : null);
        
        // Log network recommendations
        NetworkConfig.logNetworkRecommendations();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LOG.info("Sync server shutting down...");
                server.stop();
                
                // Log final metrics
                PerformanceMetrics.logMetrics();
            } catch (Exception ignored) {
            }
        }, "sync-server-shutdown"));

        server.start();
        LOG.info("✅ Sync server started on ws://0.0.0.0:{} (nodeId={}, auth={})", 
            port, nodeId, !serverToken.isEmpty());
        
        // Periodic metrics logging (every 60 seconds)
        Thread metricsThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    TimeUnit.SECONDS.sleep(60);
                    PerformanceMetrics.logMetrics();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "metrics-logger");
        metricsThread.setDaemon(true);
        metricsThread.start();

        new CountDownLatch(1).await();
    }

    private static int intArg(String[] args, int idx, int defaultValue) {
        if (args != null && args.length > idx && args[idx] != null && !args[idx].isBlank()) {
            try {
                return Integer.parseInt(args[idx]);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static String stringArg(String[] args, int idx, String defaultValue) {
        if (args != null && args.length > idx && args[idx] != null && !args[idx].isBlank()) {
            return args[idx];
        }
        return defaultValue;
    }
}
