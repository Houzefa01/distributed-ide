package com.distributed_ide.sync.server;

import com.distributed_ide.sync.model.SyncMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the SyncWebSocketServer.
 * Tests: connection, multi-client, latency, reconnect resilience, input validation.
 */
class SyncServerIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final java.util.concurrent.atomic.AtomicInteger PORT_COUNTER =
            new java.util.concurrent.atomic.AtomicInteger(19876);
    private int testPort;
    private SyncWebSocketServer server;

    @BeforeEach
    void setUp() throws Exception {
        testPort = PORT_COUNTER.getAndIncrement();
        server = new SyncWebSocketServer(testPort, "test-node");
        server.start();
        // Active poll until the server is accepting connections
        boolean ready = false;
        for (int i = 0; i < 50; i++) {
            try {
                java.net.Socket probe = new java.net.Socket();
                probe.connect(new java.net.InetSocketAddress("localhost", testPort), 200);
                probe.close();
                ready = true;
                break;
            } catch (Exception ignored) {
                Thread.sleep(100);
            }
        }
        if (!ready) throw new RuntimeException("Server did not start on port " + testPort);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop(2000);
            Thread.sleep(200);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    private TestClient createClient() throws Exception {
        return createClient(testPort);
    }

    private TestClient createClient(int port) throws Exception {
        TestClient client = new TestClient(new URI("ws://localhost:" + port));
        boolean connected = client.connectBlocking(5, TimeUnit.SECONDS);
        assertTrue(connected, "Client should be connected");
        // Wait for INIT message
        SyncMessage init = client.awaitMessage(SyncMessage.MessageType.INIT, 5);
        assertNotNull(init, "Should receive INIT message");
        return client;
    }

    // ═══════════════════════════════════════════════════════════
    //  TEST: Single client connection
    // ═══════════════════════════════════════════════════════════

    @Test
    void testSingleClientConnection() throws Exception {
        TestClient client = createClient();
        assertNotNull(client.getClientId(), "Should receive clientId on INIT");
        assertNotNull(client.getNodeId(), "Should receive nodeId on INIT");
        client.closeBlocking();
    }

    // ═══════════════════════════════════════════════════════════
    //  TEST: Multiple concurrent clients
    // ═══════════════════════════════════════════════════════════

    @Test
    void testMultipleConcurrentClients() throws Exception {
        int clientCount = 5;
        List<TestClient> clients = new ArrayList<>();
        for (int i = 0; i < clientCount; i++) {
            clients.add(createClient());
        }
        assertEquals(clientCount, clients.size());
        // All should have distinct client IDs
        long distinctIds = clients.stream().map(TestClient::getClientId).distinct().count();
        assertEquals(clientCount, distinctIds, "All clients should have unique IDs");

        for (TestClient c : clients) {
            c.closeBlocking();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  TEST: Document join and operation broadcast
    // ═══════════════════════════════════════════════════════════

    @Test
    void testDocumentJoinAndBroadcast() throws Exception {
        TestClient client1 = createClient();
        TestClient client2 = createClient();

        // Both join same document
        String docId = "test-doc-1";
        client1.sendJoinDocument(docId);
        client2.sendJoinDocument(docId);

        // Wait for ACK
        client1.awaitMessage(SyncMessage.MessageType.ACK, 3);
        client2.awaitMessage(SyncMessage.MessageType.ACK, 3);

        // Client1 sends an operation
        SyncMessage opMsg = new SyncMessage(SyncMessage.MessageType.OPERATION,
                client1.getClientId(), docId, client1.getNodeId());
        SyncMessage.OperationData opData = new SyncMessage.OperationData();
        opData.setOperationType("INSERT");
        opData.setContent('A');
        opData.setTimestamp(System.currentTimeMillis());
        opData.setIdTimestamp("1");
        opData.setIdNodeId(client1.getNodeId());
        opData.setIdDisambiguator(0);
        opMsg.setOperations(List.of(opData));
        client1.sendMsg(opMsg);

        // Client2 should receive the broadcast
        SyncMessage received = client2.awaitMessage(SyncMessage.MessageType.BATCH_OPERATIONS, 5);
        assertNotNull(received, "Client2 should receive broadcast operations");

        client1.closeBlocking();
        client2.closeBlocking();
    }

    // ═══════════════════════════════════════════════════════════
    //  TEST: Input validation - invalid document ID
    // ═══════════════════════════════════════════════════════════

    @Test
    void testInvalidDocumentIdRejected() throws Exception {
        TestClient client = createClient();

        // Try to join with path traversal document ID
        SyncMessage joinMsg = new SyncMessage(SyncMessage.MessageType.DOCUMENT_JOIN,
                client.getClientId(), "../../etc/passwd", client.getNodeId());
        client.sendMsg(joinMsg);

        SyncMessage error = client.awaitMessage(SyncMessage.MessageType.ERROR, 3);
        assertNotNull(error, "Should receive error for invalid doc ID");

        client.closeBlocking();
    }

    // ═══════════════════════════════════════════════════════════
    //  TEST: Rate limiting
    // ═══════════════════════════════════════════════════════════

    @Test
    void testRateLimiting() throws Exception {
        TestClient client = createClient();
        String docId = "rate-test";
        client.sendJoinDocument(docId);
        client.awaitMessage(SyncMessage.MessageType.ACK, 3);

        // Flood with messages - should eventually get rate limited
        boolean rateLimited = false;
        for (int i = 0; i < 300; i++) {
            SyncMessage msg = new SyncMessage(SyncMessage.MessageType.HEARTBEAT,
                    client.getClientId(), docId, client.getNodeId());
            client.sendMsg(msg);
        }
        // Check if we got an error about rate limiting
        Thread.sleep(500);
        for (SyncMessage msg : client.getAllMessages()) {
            if (msg.getType() == SyncMessage.MessageType.ERROR
                    && msg.getErrorMessage() != null
                    && msg.getErrorMessage().contains("Rate limit")) {
                rateLimited = true;
                break;
            }
        }
        assertTrue(rateLimited, "Should have been rate limited after flooding");

        client.closeBlocking();
    }

    // ═══════════════════════════════════════════════════════════
    //  TEST: Network stats
    // ═══════════════════════════════════════════════════════════

    @Test
    void testNetworkStats() throws Exception {
        TestClient client = createClient();
        String stats = server.getNetworkStats();
        assertNotNull(stats);
        assertTrue(stats.contains("clients=1"), "Stats should show 1 client");
        assertTrue(stats.contains("msgs_in="), "Stats should track messages");
        client.closeBlocking();
    }

    // ═══════════════════════════════════════════════════════════
    //  TEST: Connection latency
    // ═══════════════════════════════════════════════════════════

    @Test
    void testConnectionLatency() throws Exception {
        // Server is already started in setUp, measure only connect+handshake
        TestClient client = new TestClient(new URI("ws://localhost:" + testPort));
        long start = System.currentTimeMillis();
        boolean connected = client.connectBlocking(5, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(connected, "Client should connect");
        client.awaitMessage(SyncMessage.MessageType.INIT, 5);
        assertTrue(elapsed < 3000, "Connection should complete within 3s, took " + elapsed + "ms");
        client.closeBlocking();
    }

    // ═══════════════════════════════════════════════════════════
    //  TEST CLIENT IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════

    static class TestClient extends WebSocketClient {
        private final List<SyncMessage> receivedMessages = Collections.synchronizedList(new ArrayList<>());
        private volatile String clientId;
        private volatile String nodeId;

        TestClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {}

        @Override
        public void onMessage(String message) {
            try {
                SyncMessage msg = MAPPER.readValue(message, SyncMessage.class);
                if (msg.getType() == SyncMessage.MessageType.INIT) {
                    this.clientId = msg.getClientId();
                    this.nodeId = msg.getNodeId();
                }
                receivedMessages.add(msg);
            } catch (Exception e) {
                // ignore parse errors in tests
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {}

        @Override
        public void onError(Exception ex) {}

        String getClientId() { return clientId; }
        String getNodeId() { return nodeId; }

        void sendMsg(SyncMessage msg) throws Exception {
            send(MAPPER.writeValueAsString(msg));
        }

        void sendJoinDocument(String docId) throws Exception {
            SyncMessage join = new SyncMessage(SyncMessage.MessageType.DOCUMENT_JOIN,
                    clientId, docId, nodeId);
            sendMsg(join);
        }

        SyncMessage awaitMessage(SyncMessage.MessageType type, int timeoutSeconds) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
            while (System.currentTimeMillis() < deadline) {
                synchronized (receivedMessages) {
                    for (SyncMessage msg : receivedMessages) {
                        if (msg.getType() == type) return msg;
                    }
                }
                Thread.sleep(50);
            }
            return null;
        }

        List<SyncMessage> getAllMessages() {
            synchronized (receivedMessages) {
                return new ArrayList<>(receivedMessages);
            }
        }
    }
}
