package com.distributed_ide.javafx_client.integration;

import com.distributed_ide.sync.model.SyncMessage;
import com.distributed_ide.sync.server.SyncWebSocketServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration pour les fonctionnalités de collaboration :
 * auth par token, CLIENT_LIST broadcast, ADMIN_KICK, SERVER_SHUTDOWN.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CollaborationIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOKEN = "test-token-" + UUID.randomUUID().toString().substring(0, 6);
    private static SyncWebSocketServer server;
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        port = TestPorts.freePort();
        server = new SyncWebSocketServer(port, "collab-test", TOKEN);
        server.start();
        Thread.sleep(500);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            try { server.stop(); } catch (Exception ignored) {}
        }
    }

    private WebSocketTestClient connect() {
        WebSocketTestClient client = new WebSocketTestClient("ws://localhost:" + port);
        client.connectBlocking(3000);
        return client;
    }

    private SyncMessage findMessage(List<String> messages, SyncMessage.MessageType type) throws Exception {
        for (String raw : messages) {
            SyncMessage msg = MAPPER.readValue(raw, SyncMessage.class);
            if (msg.getType() == type) return msg;
        }
        return null;
    }

    private SyncMessage awaitMessage(WebSocketTestClient client, SyncMessage.MessageType type, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                SyncMessage found = findMessage(client.messages(), type);
                if (found != null) return found;
                Thread.sleep(50);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private SyncMessage findClientListWithMinClients(List<String> messages, int minClients) throws Exception {
        for (int i = messages.size() - 1; i >= 0; i--) {
            SyncMessage msg = MAPPER.readValue(messages.get(i), SyncMessage.class);
            if (msg.getType() == SyncMessage.MessageType.CLIENT_LIST
                    && msg.getClients() != null && msg.getClients().size() >= minClients) {
                return msg;
            }
        }
        return null;
    }

    @Test
    @Order(1)
    void testAuthRequired() throws Exception {
        WebSocketTestClient client = connect();
        Thread.sleep(300);

        // Should receive INIT
        SyncMessage init = awaitMessage(client, SyncMessage.MessageType.INIT, 2000);
        assertNotNull(init, "Should receive INIT");

        // Send a DOCUMENT_JOIN without auth -> should get ERROR
        SyncMessage join = new SyncMessage(SyncMessage.MessageType.DOCUMENT_JOIN, init.getClientId(), "test.txt", init.getNodeId());
        client.send(MAPPER.writeValueAsString(join));
        Thread.sleep(500);

        SyncMessage error = findMessage(client.messages(), SyncMessage.MessageType.ERROR);
        assertNotNull(error, "Should receive ERROR when not authenticated");
        assertTrue(error.getErrorMessage().contains("Authentication required"));

        client.close();
    }

    @Test
    @Order(2)
    void testAuthWithInvalidToken() throws Exception {
        WebSocketTestClient client = connect();
        Thread.sleep(300);

        SyncMessage init = awaitMessage(client, SyncMessage.MessageType.INIT, 2000);
        assertNotNull(init);

        // Send AUTH with wrong token
        SyncMessage auth = new SyncMessage(SyncMessage.MessageType.AUTH, "", null, "");
        auth.setToken("wrong-token");
        auth.setUsername("hacker");
        client.send(MAPPER.writeValueAsString(auth));
        Thread.sleep(500);

        SyncMessage error = findMessage(client.messages(), SyncMessage.MessageType.ERROR);
        assertNotNull(error, "Should receive ERROR for invalid token");
        assertTrue(error.getErrorMessage().contains("Invalid token"));

        client.close();
    }

    @Test
    @Order(3)
    void testAuthSuccessAndAdminRole() throws Exception {
        WebSocketTestClient admin = connect();
        Thread.sleep(300);

        SyncMessage init = awaitMessage(admin, SyncMessage.MessageType.INIT, 2000);
        assertNotNull(init);

        // Auth with correct token
        SyncMessage auth = new SyncMessage(SyncMessage.MessageType.AUTH, "", null, "");
        auth.setToken(TOKEN);
        auth.setUsername("AdminUser");
        admin.send(MAPPER.writeValueAsString(auth));
        Thread.sleep(500);

        SyncMessage ack = awaitMessage(admin, SyncMessage.MessageType.ACK, 2000);
        assertNotNull(ack, "Should receive ACK after auth");
        assertEquals("ADMIN", ack.getRole(), "First client should be ADMIN");
        assertEquals("AdminUser", ack.getUsername());

        admin.close();
    }

    @Test
    @Order(4)
    void testClientListBroadcast() throws Exception {
        // Connect admin
        WebSocketTestClient admin = connect();
        Thread.sleep(300);
        SyncMessage adminInit = awaitMessage(admin, SyncMessage.MessageType.INIT, 2000);
        assertNotNull(adminInit);
        SyncMessage authAdmin = new SyncMessage(SyncMessage.MessageType.AUTH, "", null, "");
        authAdmin.setToken(TOKEN);
        authAdmin.setUsername("Admin");
        admin.send(MAPPER.writeValueAsString(authAdmin));
        awaitMessage(admin, SyncMessage.MessageType.ACK, 2000);

        // Connect client
        WebSocketTestClient client = connect();
        Thread.sleep(300);
        SyncMessage clientInit = awaitMessage(client, SyncMessage.MessageType.INIT, 2000);
        assertNotNull(clientInit);
        SyncMessage authClient = new SyncMessage(SyncMessage.MessageType.AUTH, "", null, "");
        authClient.setToken(TOKEN);
        authClient.setUsername("Client1");
        client.send(MAPPER.writeValueAsString(authClient));
        awaitMessage(client, SyncMessage.MessageType.ACK, 2000);

        // Wait for periodic CLIENT_LIST broadcast (interval = 5s)
        Thread.sleep(6000);

        // Find a CLIENT_LIST with >= 2 clients
        SyncMessage clientList = findClientListWithMinClients(admin.messages(), 2);
        assertNotNull(clientList, "Admin should receive CLIENT_LIST with >= 2 clients");
        assertTrue(clientList.getClients().size() >= 2, "CLIENT_LIST should have at least 2 clients");

        admin.close();
        client.close();
    }

    @Test
    @Order(5)
    void testAdminKick() throws Exception {
        // Connect admin
        WebSocketTestClient admin = connect();
        Thread.sleep(300);
        SyncMessage adminInit = awaitMessage(admin, SyncMessage.MessageType.INIT, 2000);
        assertNotNull(adminInit);
        SyncMessage authAdmin = new SyncMessage(SyncMessage.MessageType.AUTH, "", null, "");
        authAdmin.setToken(TOKEN);
        authAdmin.setUsername("Admin");
        admin.send(MAPPER.writeValueAsString(authAdmin));
        SyncMessage adminAck = awaitMessage(admin, SyncMessage.MessageType.ACK, 2000);
        assertNotNull(adminAck);

        // Connect victim
        WebSocketTestClient victim = connect();
        Thread.sleep(300);
        SyncMessage victimInit = awaitMessage(victim, SyncMessage.MessageType.INIT, 2000);
        assertNotNull(victimInit);
        SyncMessage authVictim = new SyncMessage(SyncMessage.MessageType.AUTH, "", null, "");
        authVictim.setToken(TOKEN);
        authVictim.setUsername("Victim");
        victim.send(MAPPER.writeValueAsString(authVictim));
        SyncMessage victimAck = awaitMessage(victim, SyncMessage.MessageType.ACK, 2000);
        assertNotNull(victimAck);

        Thread.sleep(1000);

        // Admin kicks victim
        SyncMessage kick = new SyncMessage(SyncMessage.MessageType.ADMIN_KICK, adminInit.getClientId(), null, "");
        kick.setTargetClientId(victimInit.getClientId());
        kick.setReason("Test kick");
        admin.send(MAPPER.writeValueAsString(kick));

        // Wait enough for KICKED to be sent before connection closes
        Thread.sleep(2000);

        // Victim should have received KICKED
        SyncMessage kicked = findMessage(victim.messages(), SyncMessage.MessageType.KICKED);
        assertNotNull(kicked, "Victim should receive KICKED message");

        admin.close();
        victim.close();
    }

    @Test
    @Order(6)
    void testNonAdminCannotKick() throws Exception {
        // Connect admin first to take admin role
        WebSocketTestClient admin = connect();
        Thread.sleep(200);
        awaitMessage(admin, SyncMessage.MessageType.INIT, 2000);
        SyncMessage authAdmin = new SyncMessage(SyncMessage.MessageType.AUTH, "", null, "");
        authAdmin.setToken(TOKEN);
        authAdmin.setUsername("Admin");
        admin.send(MAPPER.writeValueAsString(authAdmin));
        awaitMessage(admin, SyncMessage.MessageType.ACK, 2000);

        // Connect non-admin
        WebSocketTestClient nonAdmin = connect();
        Thread.sleep(200);
        SyncMessage nonAdminInit = awaitMessage(nonAdmin, SyncMessage.MessageType.INIT, 2000);
        SyncMessage authNonAdmin = new SyncMessage(SyncMessage.MessageType.AUTH, "", null, "");
        authNonAdmin.setToken(TOKEN);
        authNonAdmin.setUsername("NonAdmin");
        nonAdmin.send(MAPPER.writeValueAsString(authNonAdmin));
        SyncMessage nonAdminAck = awaitMessage(nonAdmin, SyncMessage.MessageType.ACK, 2000);
        assertNotNull(nonAdminAck);
        assertEquals("CLIENT", nonAdminAck.getRole());

        // Non-admin tries to kick
        SyncMessage kick = new SyncMessage(SyncMessage.MessageType.ADMIN_KICK, nonAdminInit.getClientId(), null, "");
        kick.setTargetClientId("some-id");
        nonAdmin.send(MAPPER.writeValueAsString(kick));
        Thread.sleep(500);

        SyncMessage error = findMessage(nonAdmin.messages(), SyncMessage.MessageType.ERROR);
        assertNotNull(error, "Non-admin should get ERROR when trying to kick");
        assertTrue(error.getErrorMessage().contains("Only admin"));

        admin.close();
        nonAdmin.close();
    }
}
