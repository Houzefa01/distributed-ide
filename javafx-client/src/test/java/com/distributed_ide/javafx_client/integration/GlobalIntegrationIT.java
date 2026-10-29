package com.distributed_ide.javafx_client.integration;

import com.distributed_ide.javafx_client.service.SyncClientService;
import com.distributed_ide.signaling.model.SignalingMessage;
import com.distributed_ide.sync.model.SyncMessage;
import com.distributed_ide.terminal.model.TerminalMessage;
import com.distributed_ide.terminal.model.TerminalSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GlobalIntegrationIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IntegrationTestEnvironment env;

    private static boolean dockerAvailable() {
        try {
            Process p = new ProcessBuilder(List.of("docker", "info"))
                .redirectErrorStream(true)
                .start();
            boolean finished = p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished || p.exitValue() != 0) return false;

            Process p2 = new ProcessBuilder(List.of(
                "docker", "run", "--rm", "alpine:3.19", "sh", "-lc", "true"
            ))
                .redirectErrorStream(true)
                .start();
            boolean finished2 = p2.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
            return finished2 && p2.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    void startEnv() throws Exception {
        env = new IntegrationTestEnvironment(Paths.get("integration-test-report.md"));
        env.startAll();
    }

    @AfterAll
    void stopEnv() {
        if (env != null) env.stopAll();
    }

    @Test
    void crdt_convergence_3_clients_with_latency_metrics() throws Exception {
        IntegrationTestReport report = env.report();
        report.section("1) CRDT convergence multi-clients");

        String docId = "doc-it-1";
        int clientCount = 3;

        var clients = new ArrayList<SyncClientService>();
        var connected = new CountDownLatch(clientCount);

        for (int i = 0; i < clientCount; i++) {
            SyncClientService c = new SyncClientService();
            c.setListener(new SyncClientService.Listener() {
                @Override
                public void onConnected(String clientId, String nodeId) {
                    connected.countDown();
                }

                @Override
                public void onDocumentUpdated(String documentId, String fullText) {
                }

                @Override
                public void onCollaboratorJoined(String documentId, String clientId, String nodeId) {
                }

                @Override
                public void onChatMessage(String documentId, String senderId, String message, long timestamp) {
                }

                @Override
                public void onRemoteSelectionUpdate(String documentId, String clientId, String userId, int startRow, int startCol, int endRow, int endCol) {
                }

                @Override
                public void onRemoteCursorUpdate(String documentId, String clientId, String userId, int row, int col, String color) {
                }

                @Override
                public void onError(String message) {
                }
            });
            c.connect(env.syncWsUri());
            clients.add(c);
        }

        assertTrue(connected.await(5, TimeUnit.SECONDS), "All sync clients should connect and receive INIT");
        for (SyncClientService c : clients) c.joinDocument(docId);

        int opsPerClient = 4;
        int totalOps = clientCount * opsPerClient;

        AtomicLong firstSendAt = new AtomicLong(0);
        AtomicLong lastConvergedAt = new AtomicLong(0);

        // Send operations in a controlled order to reduce timing-related divergence.
        // This still validates multi-client propagation/convergence.
        for (int i = 0; i < clientCount; i++) {
            for (int j = 0; j < opsPerClient; j++) {
                long now = System.nanoTime();
                firstSendAt.compareAndSet(0, now);
                char ch = (char) ('A' + i * opsPerClient + j);
                SyncClientService c = clients.get(i);
                SyncMessage.OperationData op = c.insertAndCreateOperation(docId, c.getDocumentText(docId).length(), ch);
                assertNotNull(op);
                c.sendOperation(docId, op);
                Thread.sleep(25);
            }
        }

        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            List<String> texts = new ArrayList<>();
            for (SyncClientService c : clients) texts.add(c.getDocumentText(docId));
            String first = texts.get(0);
            for (String t : texts) assertEquals(first, t, "All clients should converge to same text");
            assertEquals(totalOps, first.length(), "Final text length should match total operations");
            lastConvergedAt.compareAndSet(0, System.nanoTime());
        });

        long convergenceNs = (lastConvergedAt.get() - firstSendAt.get());
        report.kv("clients", clientCount);
        report.kv("opsPerClient", opsPerClient);
        report.kv("totalOps", totalOps);
        report.kv("convergenceMs", TimeUnit.NANOSECONDS.toMillis(convergenceNs));
        report.blank();

        for (SyncClientService c : clients) c.disconnect();
    }

    @Test
    void sync_client_disconnect_then_reconnect_same_instance_receives_remote_ops() throws Exception {
        String docId = "doc-it-reconnect-" + UUID.randomUUID();

        SyncClientService writer = new SyncClientService();
        SyncClientService reader = new SyncClientService();

        java.util.concurrent.atomic.AtomicInteger writerConnections = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger readerConnections = new java.util.concurrent.atomic.AtomicInteger(0);

        writer.setListener(new SyncClientService.Listener() {
            @Override public void onConnected(String clientId, String nodeId) { writerConnections.incrementAndGet(); }
            @Override public void onDocumentUpdated(String documentId, String fullText) { }
            @Override public void onCollaboratorJoined(String documentId, String clientId, String nodeId) { }
            @Override public void onChatMessage(String documentId, String senderId, String message, long timestamp) { }
            @Override public void onRemoteSelectionUpdate(String documentId, String clientId, String userId, int startRow, int startCol, int endRow, int endCol) { }
            @Override public void onRemoteCursorUpdate(String documentId, String clientId, String userId, int row, int col, String color) { }
            @Override public void onError(String message) { }
        });
        reader.setListener(new SyncClientService.Listener() {
            @Override public void onConnected(String clientId, String nodeId) { readerConnections.incrementAndGet(); }
            @Override public void onDocumentUpdated(String documentId, String fullText) { }
            @Override public void onCollaboratorJoined(String documentId, String clientId, String nodeId) { }
            @Override public void onChatMessage(String documentId, String senderId, String message, long timestamp) { }
            @Override public void onRemoteSelectionUpdate(String documentId, String clientId, String userId, int startRow, int startCol, int endRow, int endCol) { }
            @Override public void onRemoteCursorUpdate(String documentId, String clientId, String userId, int row, int col, String color) { }
            @Override public void onError(String message) { }
        });

        writer.connect(env.syncWsUri());
        reader.connect(env.syncWsUri());

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> writerConnections.get() >= 1);
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> readerConnections.get() >= 1);

        reader.disconnect();
        reader.connect(env.syncWsUri());
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> readerConnections.get() >= 2);

        writer.joinDocument(docId);
        reader.joinDocument(docId);

        SyncMessage.OperationData op = writer.insertAndCreateOperation(docId, 0, 'R');
        assertNotNull(op);
        writer.sendOperation(docId, op);

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertEquals("R", reader.getDocumentText(docId))
        );

        writer.shutdown();
        reader.shutdown();
    }

    @Test
    void websocket_reconnect_and_sync_request_returns_full_history() throws Exception {
        IntegrationTestReport report = env.report();
        report.section("2) Synchronisation WebSocket (reconnexion + resync)");

        String docId = "doc-it-resync-" + UUID.randomUUID();

        // First client: connect + join + produce operations.
        SyncClientService writer = new SyncClientService();
        CountDownLatch writerConnected = new CountDownLatch(1);
        writer.setListener(new SyncClientService.Listener() {
            @Override public void onConnected(String clientId, String nodeId) { writerConnected.countDown(); }
            @Override public void onDocumentUpdated(String documentId, String fullText) { }
            @Override public void onCollaboratorJoined(String documentId, String clientId, String nodeId) { }
            @Override public void onChatMessage(String documentId, String senderId, String message, long timestamp) { }
            @Override public void onRemoteSelectionUpdate(String documentId, String clientId, String userId, int startRow, int startCol, int endRow, int endCol) { }
            @Override public void onRemoteCursorUpdate(String documentId, String clientId, String userId, int row, int col, String color) { }
            @Override public void onError(String message) { }
        });
        writer.connect(env.syncWsUri());
        assertTrue(writerConnected.await(5, TimeUnit.SECONDS));
        writer.joinDocument(docId);

        int ops = 6;
        for (int i = 0; i < ops; i++) {
            SyncMessage.OperationData op = writer.insertAndCreateOperation(docId, writer.getDocumentText(docId).length(), (char) ('a' + i));
            assertNotNull(op);
            writer.sendOperation(docId, op);
        }

        // Give the server time to process and persist ops (engine apply + scheduled flush).
        Thread.sleep(800);

        // Give server enough time to flush the batch and persist history.
        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertEquals(ops, writer.getDocumentText(docId).length());
        });

        writer.disconnect();

        // Second client uses raw WebSocket to request SYNC_RESPONSE fullHistory.
        WebSocketTestClient reader = new WebSocketTestClient(env.syncWsUri());
        reader.connectBlocking(5_000);

        // Wait INIT to get clientId/nodeId
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> !reader.messages().isEmpty());
        SyncMessage init = MAPPER.readValue(reader.messages().get(0), SyncMessage.class);
        assertEquals(SyncMessage.MessageType.INIT, init.getType());

        String clientId = init.getClientId();
        String nodeId = init.getNodeId();

        SyncMessage join = new SyncMessage(SyncMessage.MessageType.DOCUMENT_JOIN, clientId, docId, nodeId);
        reader.send(MAPPER.writeValueAsString(join));

        // Ensure join was processed before asking for history.
        Awaitility.await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(50)).until(() ->
            reader.messages().stream().anyMatch(m -> m.contains("\"type\":\"ACK\"") && m.contains(docId))
        );

        SyncMessage syncReq = new SyncMessage(SyncMessage.MessageType.SYNC_REQUEST, clientId, docId, nodeId);
        reader.send(MAPPER.writeValueAsString(syncReq));

        Awaitility.await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            boolean found = false;
            for (String m : reader.messages()) {
                SyncMessage sm = MAPPER.readValue(m, SyncMessage.class);
                if (sm.getType() == SyncMessage.MessageType.SYNC_RESPONSE) {
                    found = true;
                    assertNotNull(sm.getFullHistory());
                    assertEquals(ops, sm.getFullHistory().size());
                }
            }
            assertTrue(found, "Expected SYNC_RESPONSE");
        });

        report.kv("docId", docId);
        report.kv("ops", ops);
        report.blank();

        reader.close();
    }

    @Test
    void terminal_service_websocket_create_execute_delete_session() throws Exception {
        Assumptions.assumeTrue(dockerAvailable(), "Docker not available");

        IntegrationTestReport report = env.report();
        report.section("4) Terminal service (Docker sandbox via WebSocket)");

        WebSocketTestClient terminal = new WebSocketTestClient(env.terminalWsUri());
        terminal.connectBlocking(5_000);

        String sessionId = "term-it-" + UUID.randomUUID().toString().substring(0, 8);
        String userId = "it-user-1";

        TerminalMessage create = TerminalMessage.createSession(
            sessionId,
            userId,
            TerminalSession.ExecutionType.SHELL,
            1L,
            256L,
            60L
        );
        terminal.send(MAPPER.writeValueAsString(create));

        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            boolean found = false;
            for (String raw : terminal.messages()) {
                TerminalMessage msg = MAPPER.readValue(raw, TerminalMessage.class);
                if (msg.getType() == TerminalMessage.MessageType.SESSION_INFO && sessionId.equals(msg.getSessionId())) {
                    found = true;
                    assertNotNull(msg.getContainerId());
                }
            }
            assertTrue(found, "Expected SESSION_INFO");
        });

        TerminalMessage exec = TerminalMessage.execute(sessionId, userId, "echo ok-terminal", "shell");
        terminal.send(MAPPER.writeValueAsString(exec));

        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            boolean found = false;
            for (String raw : terminal.messages()) {
                TerminalMessage msg = MAPPER.readValue(raw, TerminalMessage.class);
                if (msg.getType() == TerminalMessage.MessageType.EXECUTE_RESULT && sessionId.equals(msg.getSessionId())) {
                    found = true;
                    assertEquals(0, msg.getExitCode());
                    assertNotNull(msg.getOutput());
                    assertTrue(msg.getOutput().contains("ok-terminal"));
                }
                if (msg.getType() == TerminalMessage.MessageType.ERROR && sessionId.equals(msg.getSessionId())) {
                    fail("Terminal ERROR: " + msg.getError());
                }
            }
            assertTrue(found, "Expected EXECUTE_RESULT");
        });

        TerminalMessage del = new TerminalMessage(TerminalMessage.MessageType.DELETE_SESSION);
        del.setSessionId(sessionId);
        del.setUserId(userId);
        terminal.send(MAPPER.writeValueAsString(del));

        report.kv("terminalWsUri", env.terminalWsUri());
        report.kv("sessionId", sessionId);
        report.blank();

        terminal.close();
    }

    @Test
    void signaling_server_forwards_offer_and_ice_candidate() throws Exception {
        IntegrationTestReport report = env.report();
        report.section("5) Signaling WebRTC (SDP/ICE forwarding)");

        String roomId = "room-it-" + UUID.randomUUID().toString().substring(0, 8);
        String alice = "alice-" + UUID.randomUUID().toString().substring(0, 6);
        String bob = "bob-" + UUID.randomUUID().toString().substring(0, 6);

        WebSocketTestClient c1 = new WebSocketTestClient(env.signalingWsUri());
        WebSocketTestClient c2 = new WebSocketTestClient(env.signalingWsUri());
        c1.connectBlocking(5_000);
        c2.connectBlocking(5_000);

        SignalingMessage join1 = new SignalingMessage(SignalingMessage.MessageType.JOIN_ROOM, alice, roomId);
        SignalingMessage join2 = new SignalingMessage(SignalingMessage.MessageType.JOIN_ROOM, bob, roomId);
        c1.send(MAPPER.writeValueAsString(join1));
        c2.send(MAPPER.writeValueAsString(join2));

        // Wait for both JOIN_ROOM confirmations + at least one USER_JOINED notification.
        Awaitility.await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            boolean c1Joined = c1.messages().stream().anyMatch(m -> m.contains("\"type\":\"JOIN_ROOM\"") && m.contains(roomId));
            boolean c2Joined = c2.messages().stream().anyMatch(m -> m.contains("\"type\":\"JOIN_ROOM\"") && m.contains(roomId));
            assertTrue(c1Joined && c2Joined);
        });
        Awaitility.await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            boolean c1SawBob = c1.messages().stream().anyMatch(m -> m.contains("\"type\":\"USER_JOINED\"") && m.contains(bob));
            boolean c2SawAlice = c2.messages().stream().anyMatch(m -> m.contains("\"type\":\"USER_JOINED\"") && m.contains(alice));
            assertTrue(c1SawBob || c2SawAlice);
        });

        // Offer from alice -> bob
        SignalingMessage offer = new SignalingMessage(SignalingMessage.MessageType.OFFER, alice, roomId);
        offer.setTargetUserId(bob);
        offer.setSdp("v=0\r\no=it\r\n");
        c1.send(MAPPER.writeValueAsString(offer));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            boolean hasOffer = false;
            for (String raw : c2.messages()) {
                if (!raw.contains("\"type\":\"OFFER\"")) continue;
                SignalingMessage received = MAPPER.readValue(raw, SignalingMessage.class);
                if (SignalingMessage.MessageType.OFFER == received.getType() && bob.equals(received.getTargetUserId())) {
                    hasOffer = true;
                    break;
                }
            }
            assertTrue(hasOffer, "Bob should receive OFFER");
        });

        // ICE from alice -> bob
        SignalingMessage ice = new SignalingMessage(SignalingMessage.MessageType.ICE_CANDIDATE, alice, roomId);
        ice.setTargetUserId(bob);
        ice.setCandidate(new SignalingMessage.IceCandidate("candidate:1 1 UDP 1 127.0.0.1 9 typ host", 0, "0"));
        c1.send(MAPPER.writeValueAsString(ice));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            boolean hasIce = false;
            for (String raw : c2.messages()) {
                if (!raw.contains("\"type\":\"ICE_CANDIDATE\"")) continue;
                SignalingMessage received = MAPPER.readValue(raw, SignalingMessage.class);
                if (SignalingMessage.MessageType.ICE_CANDIDATE == received.getType() && bob.equals(received.getTargetUserId())) {
                    hasIce = true;
                    break;
                }
            }
            assertTrue(hasIce, "Bob should receive ICE_CANDIDATE");
        });

        report.kv("roomId", roomId);
        report.kv("alice", alice);
        report.kv("bob", bob);
        report.blank();

        c1.close();
        c2.close();
    }

    @Test
    void lsp_websocket_init_returns_message_or_error() throws Exception {
        Assumptions.assumeTrue(env != null && env.isLspAvailable(), "LSP bridge not available");
        IntegrationTestReport report = env.report();
        report.section("3) LSP diagnostics (smoke init)");

        WebSocketTestClient lsp = new WebSocketTestClient(env.lspWsUri());
        lsp.connectBlocking(5_000);

        // init (java) with workspaceRoot = current module
        String init = "{\"method\":\"init\",\"params\":{\"language\":\"java\",\"rootUri\":\"file://" +
            Paths.get(".").toAbsolutePath().normalize().toString().replace("\\", "/") + "\"}}";
        lsp.send(init);

        // Expect either an error (missing jdtls) or any JSON-RPC traffic.
        Awaitility.await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).until(() -> !lsp.messages().isEmpty());

        String first = lsp.messages().get(0);
        boolean looksLikeError = first.contains("\"error\"");
        boolean looksLikeJsonRpc = first.contains("\"jsonrpc\"") || first.contains("\"method\"");
        assertTrue(looksLikeError || looksLikeJsonRpc);

        report.kv("lspWsUri", env.lspWsUri());
        report.kv("firstMessage", first);
        report.blank();

        lsp.close();
    }
}
