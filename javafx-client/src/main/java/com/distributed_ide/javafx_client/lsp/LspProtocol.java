package com.distributed_ide.javafx_client.lsp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Constructeur de messages LSP (Language Server Protocol).
 *
 * Encapsule la construction JSON des requêtes/notifications LSP standard :
 * - initialize / initialized / shutdown / exit
 * - textDocument/didOpen, didChange, didSave, didClose
 * - textDocument/completion, hover, definition
 *
 * Toutes les méthodes sont stateless et thread-safe.
 */
public class LspProtocol {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LspProtocol() {}

    // ═══════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════

    /**
     * Envoie initialize + initialized sur le transport donné.
     * Retourne le Future de la réponse initialize.
     */
    public static CompletableFuture<JsonNode> initialize(JsonRpcTransport transport, String rootUri, int processId) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("processId", processId);

        ObjectNode capabilities = MAPPER.createObjectNode();

        // Text document capabilities
        ObjectNode textDoc = MAPPER.createObjectNode();

        ObjectNode completion = MAPPER.createObjectNode();
        ObjectNode completionItem = MAPPER.createObjectNode();
        completionItem.put("snippetSupport", true);
        completion.set("completionItem", completionItem);
        textDoc.set("completion", completion);

        ObjectNode hover = MAPPER.createObjectNode();
        ArrayNode hoverFormats = MAPPER.createArrayNode();
        hoverFormats.add("markdown");
        hoverFormats.add("plaintext");
        hover.set("contentFormat", hoverFormats);
        textDoc.set("hover", hover);

        ObjectNode definition = MAPPER.createObjectNode();
        definition.put("linkSupport", true);
        textDoc.set("definition", definition);

        ObjectNode publishDiag = MAPPER.createObjectNode();
        publishDiag.put("relatedInformation", true);
        textDoc.set("publishDiagnostics", publishDiag);

        ObjectNode sync = MAPPER.createObjectNode();
        sync.put("dynamicRegistration", true);
        sync.put("didSave", true);
        textDoc.set("synchronization", sync);

        capabilities.set("textDocument", textDoc);

        // Workspace capabilities
        ObjectNode workspace = MAPPER.createObjectNode();
        workspace.put("applyEdit", true);
        capabilities.set("workspace", workspace);

        params.set("capabilities", capabilities);

        if (rootUri != null) {
            params.put("rootUri", rootUri);
        }
        params.putNull("rootPath");

        ObjectNode clientInfo = MAPPER.createObjectNode();
        clientInfo.put("name", "DistributedIDE");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);

        return transport.sendRequest("initialize", params)
                .thenApply(result -> {
                    // Send initialized notification
                    transport.sendNotification("initialized", MAPPER.createObjectNode());
                    return result;
                });
    }

    /**
     * Envoie shutdown + exit.
     */
    public static CompletableFuture<JsonNode> shutdown(JsonRpcTransport transport) {
        return transport.sendRequest("shutdown", null)
                .thenApply(result -> {
                    transport.sendNotification("exit", null);
                    return result;
                });
    }

    // ═══════════════════════════════════════════════════════════
    //  TEXT DOCUMENT SYNC
    // ═══════════════════════════════════════════════════════════

    /**
     * textDocument/didOpen
     */
    public static void didOpen(JsonRpcTransport transport, String uri, String languageId, int version, String text) {
        ObjectNode params = MAPPER.createObjectNode();
        ObjectNode textDocument = MAPPER.createObjectNode();
        textDocument.put("uri", uri);
        textDocument.put("languageId", languageId);
        textDocument.put("version", version);
        textDocument.put("text", text);
        params.set("textDocument", textDocument);
        transport.sendNotification("textDocument/didOpen", params);
    }

    /**
     * textDocument/didChange (full sync — envoie tout le contenu)
     */
    public static void didChange(JsonRpcTransport transport, String uri, int version, String fullText) {
        ObjectNode params = MAPPER.createObjectNode();

        ObjectNode textDocument = MAPPER.createObjectNode();
        textDocument.put("uri", uri);
        textDocument.put("version", version);
        params.set("textDocument", textDocument);

        ArrayNode changes = MAPPER.createArrayNode();
        ObjectNode change = MAPPER.createObjectNode();
        change.put("text", fullText);
        changes.add(change);
        params.set("contentChanges", changes);

        transport.sendNotification("textDocument/didChange", params);
    }

    /**
     * textDocument/didSave
     */
    public static void didSave(JsonRpcTransport transport, String uri, String text) {
        ObjectNode params = MAPPER.createObjectNode();
        ObjectNode textDocument = MAPPER.createObjectNode();
        textDocument.put("uri", uri);
        params.set("textDocument", textDocument);
        if (text != null) {
            params.put("text", text);
        }
        transport.sendNotification("textDocument/didSave", params);
    }

    /**
     * textDocument/didClose
     */
    public static void didClose(JsonRpcTransport transport, String uri) {
        ObjectNode params = MAPPER.createObjectNode();
        ObjectNode textDocument = MAPPER.createObjectNode();
        textDocument.put("uri", uri);
        params.set("textDocument", textDocument);
        transport.sendNotification("textDocument/didClose", params);
    }

    // ═══════════════════════════════════════════════════════════
    //  COMPLETION
    // ═══════════════════════════════════════════════════════════

    /**
     * textDocument/completion
     * @param line      0-based
     * @param character 0-based
     */
    public static CompletableFuture<JsonNode> completion(JsonRpcTransport transport, String uri, int line, int character) {
        ObjectNode params = textDocumentPosition(uri, line, character);
        return transport.sendRequest("textDocument/completion", params);
    }

    // ═══════════════════════════════════════════════════════════
    //  HOVER
    // ═══════════════════════════════════════════════════════════

    /**
     * textDocument/hover
     */
    public static CompletableFuture<JsonNode> hover(JsonRpcTransport transport, String uri, int line, int character) {
        ObjectNode params = textDocumentPosition(uri, line, character);
        return transport.sendRequest("textDocument/hover", params);
    }

    // ═══════════════════════════════════════════════════════════
    //  GO TO DEFINITION
    // ═══════════════════════════════════════════════════════════

    /**
     * textDocument/definition
     */
    public static CompletableFuture<JsonNode> definition(JsonRpcTransport transport, String uri, int line, int character) {
        ObjectNode params = textDocumentPosition(uri, line, character);
        return transport.sendRequest("textDocument/definition", params);
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    private static ObjectNode textDocumentPosition(String uri, int line, int character) {
        ObjectNode params = MAPPER.createObjectNode();

        ObjectNode textDocument = MAPPER.createObjectNode();
        textDocument.put("uri", uri);
        params.set("textDocument", textDocument);

        ObjectNode position = MAPPER.createObjectNode();
        position.put("line", line);
        position.put("character", character);
        params.set("position", position);

        return params;
    }

    /**
     * Convertit un chemin fichier en URI LSP (file:///).
     */
    public static String fileToUri(String filePath) {
        try {
            return Path.of(filePath).toUri().toString();
        } catch (Exception e) {
            return "file://" + filePath;
        }
    }

    /**
     * Convertit un URI LSP en chemin fichier.
     */
    public static String uriToFile(String uri) {
        try {
            return Path.of(new URI(uri)).toString();
        } catch (Exception e) {
            if (uri.startsWith("file://")) {
                return uri.substring(7);
            }
            return uri;
        }
    }
}
