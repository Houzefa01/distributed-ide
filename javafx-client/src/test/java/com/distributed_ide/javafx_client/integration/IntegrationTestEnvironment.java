package com.distributed_ide.javafx_client.integration;

import com.distributed_ide.signaling.server.RoomManager;
import com.distributed_ide.signaling.server.SignalingWebSocketServer;
import com.distributed_ide.sync.server.SyncWebSocketServer;
import com.distributed_ide.terminal.server.TerminalSessionManager;
import com.distributed_ide.terminal.server.TerminalWebSocketServer;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetSocketAddress;
import java.nio.file.Path;

final class IntegrationTestEnvironment {

    private final int syncPort;
    private final int terminalPort;
    private final int signalingPort;
    private final int lspPort;

    private SyncWebSocketServer syncServer;
    private TerminalWebSocketServer terminalServer;
    private SignalingWebSocketServer signalingServer;
    private ConfigurableApplicationContext lspContext;
    private boolean lspAvailable;

    private final IntegrationTestReport report;

    IntegrationTestEnvironment(Path reportFile) {
        this.syncPort = TestPorts.freePort();
        this.terminalPort = TestPorts.freePort();
        this.signalingPort = TestPorts.freePort();
        this.lspPort = TestPorts.freePort();
        this.report = new IntegrationTestReport(reportFile);
    }

    void startAll() throws Exception {
        report.section("Environment");
        report.kv("syncPort", syncPort);
        report.kv("terminalPort", terminalPort);
        report.kv("signalingPort", signalingPort);
        report.kv("lspPort", lspPort);
        report.blank();

        syncServer = new SyncWebSocketServer(syncPort, "sync-it");
        syncServer.start();

        TerminalSessionManager terminalManager = new TerminalSessionManager(50, 300, 2, 512, 60);
        terminalServer = new TerminalWebSocketServer(new InetSocketAddress(terminalPort), terminalManager);
        terminalServer.start();

        RoomManager roomManager = new RoomManager(4, 300_000);
        signalingServer = new SignalingWebSocketServer(new InetSocketAddress(signalingPort), roomManager);
        signalingServer.start();

        try {
            Class<?> lspAppClass = Class.forName("com.distributed_ide.lsp_bridge.LspBridgeApplication");
            SpringApplication app = new SpringApplication(lspAppClass);
            lspContext = app.run("--server.port=" + lspPort);
            lspAvailable = true;
        } catch (ClassNotFoundException e) {
            lspContext = null;
            lspAvailable = false;
            report.kv("lspBridge", "not on test classpath (skipped)");
            report.blank();
        }
    }

    void stopAll() {
        try {
            if (lspContext != null) lspContext.close();
        } catch (Exception ignored) {
        }

        try {
            if (syncServer != null) syncServer.stop();
        } catch (Exception ignored) {
        }

        try {
            if (terminalServer != null) terminalServer.stop();
        } catch (Exception ignored) {
        }

        try {
            if (signalingServer != null) signalingServer.stop();
        } catch (Exception ignored) {
        }

        report.write();
    }

    String syncWsUri() {
        return "ws://localhost:" + syncPort;
    }

    String terminalWsUri() {
        return "ws://localhost:" + terminalPort;
    }

    String signalingWsUri() {
        return "ws://localhost:" + signalingPort;
    }

    String lspWsUri() {
        return "ws://localhost:" + lspPort + "/lsp";
    }

    boolean isLspAvailable() {
        return lspAvailable;
    }

    IntegrationTestReport report() {
        return report;
    }
}
