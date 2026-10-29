package com.distributed_ide.signaling_server;

import com.distributed_ide.signaling.server.RoomManager;
import com.distributed_ide.signaling.server.SignalingWebSocketServer;
import com.distributed_ide.common.PerformanceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class SignalingServerApp {
    private static final Logger LOG = LoggerFactory.getLogger(SignalingServerApp.class);

    public static void main(String[] args) throws Exception {
        int port = intArg(args, 0, Integer.getInteger("signaling.port", 8082));
        int maxParticipants = intArg(args, 1, Integer.getInteger("signaling.room.maxParticipants", 4));
        long inactivityTimeoutMs = longArg(args, 2, Long.getLong("signaling.room.inactivityTimeoutMs", 300_000L));

        RoomManager roomManager = new RoomManager(maxParticipants, inactivityTimeoutMs);
        SignalingWebSocketServer server = new SignalingWebSocketServer(new InetSocketAddress("0.0.0.0", port), roomManager);

        // Graceful shutdown hook with metric logging
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LOG.info("Signaling server shutting down...");
                server.stop();
                
                // Log final metrics
                PerformanceMetrics.logMetrics();
            } catch (Exception ignored) {
            }
        }, "signaling-server-shutdown"));

        server.start();
        LOG.info("✅ Signaling server started on ws://0.0.0.0:{}", port);
        LOG.info("   Max participants: {}, Inactivity timeout: {}ms", maxParticipants, inactivityTimeoutMs);
        
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

    private static long longArg(String[] args, int idx, long defaultValue) {
        if (args != null && args.length > idx && args[idx] != null && !args[idx].isBlank()) {
            try {
                return Long.parseLong(args[idx]);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}
