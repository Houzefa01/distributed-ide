package com.distributed_ide.terminal_service;

import com.distributed_ide.terminal.server.TerminalSessionManager;
import com.distributed_ide.terminal.server.TerminalWebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

public final class TerminalServiceApp {
    private static final Logger LOG = LoggerFactory.getLogger(TerminalServiceApp.class);

    public static void main(String[] args) throws Exception {
        int port = intArg(args, 0, Integer.getInteger("terminal.port", 8766));

        long maxSessions = longArg(args, 1, Long.getLong("terminal.maxSessions", 50L));
        long inactivityTimeoutSec = longArg(args, 2, Long.getLong("terminal.inactivityTimeoutSeconds", 300L));
        long defaultCpuCores = longArg(args, 3, Long.getLong("terminal.defaultCpuCores", 2L));
        long defaultMemoryMb = longArg(args, 4, Long.getLong("terminal.defaultMemoryMb", 512L));
        long defaultTimeoutSec = longArg(args, 5, Long.getLong("terminal.defaultTimeoutSeconds", 60L));

        TerminalSessionManager terminalManager = new TerminalSessionManager(
            maxSessions,
            inactivityTimeoutSec,
            defaultCpuCores,
            defaultMemoryMb,
            defaultTimeoutSec
        );

        TerminalWebSocketServer server = new TerminalWebSocketServer(new InetSocketAddress("0.0.0.0", port), terminalManager);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (Exception ignored) {
            }
            try {
                terminalManager.shutdown();
            } catch (Exception ignored) {
            }
        }, "terminal-service-shutdown"));

        server.start();
        LOG.info("Terminal service started on ws://0.0.0.0:{}", port);

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
