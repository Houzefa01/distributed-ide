package com.distributed_ide.lsp_bridge.transport;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LspMessageTransportTest {

    @Test
    void roundTrip_messagePreserved() throws IOException {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LspMessageTransport.writeMessage(out, json);
        byte[] written = out.toByteArray();

        ByteArrayInputStream in = new ByteArrayInputStream(written);
        String read = LspMessageTransport.readMessage(in);
        assertEquals(json, read);
    }

    @Test
    void readMessage_returnsNullOnEmptyStream() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        assertNull(LspMessageTransport.readMessage(in));
    }

    @Test
    void readMessage_throwsWhenContentLengthMissing() {
        String header = "Content-Type: application/json\r\n\r\n";
        ByteArrayInputStream in = new ByteArrayInputStream(header.getBytes(StandardCharsets.US_ASCII));
        assertThrows(IOException.class, () -> LspMessageTransport.readMessage(in));
    }

    @Test
    void writeMessage_flushesContentLengthHeader() throws IOException {
        String body = "{}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LspMessageTransport.writeMessage(out, body);
        String s = out.toString(StandardCharsets.US_ASCII);
        assertTrue(s.startsWith("Content-Length: 2\r\n\r\n"));
        assertTrue(s.contains("{}"));
    }
}
