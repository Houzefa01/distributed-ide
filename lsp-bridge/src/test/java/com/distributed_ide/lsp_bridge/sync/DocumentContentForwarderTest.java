package com.distributed_ide.lsp_bridge.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentContentForwarderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildDidChangeNotification_containsMethodAndParams() throws Exception {
        String json = DocumentContentForwarder.buildDidChangeNotification("file:///tmp/foo.java", 1L, "hello");
        assertNotNull(json);
        JsonNode root = MAPPER.readTree(json);
        assertEquals("2.0", root.get("jsonrpc").asText());
        assertEquals("textDocument/didChange", root.get("method").asText());
        JsonNode params = root.get("params");
        assertNotNull(params);
        assertEquals("file:///tmp/foo.java", params.get("uri").asText());
        assertEquals(1, params.get("version").asLong());
        assertEquals("hello", params.get("contentChanges").get(0).get("text").asText());
    }
}
