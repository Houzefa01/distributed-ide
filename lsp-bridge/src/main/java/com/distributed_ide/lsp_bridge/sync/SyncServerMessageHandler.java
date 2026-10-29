package com.distributed_ide.lsp_bridge.sync;

import com.distributed_ide.sync.model.SyncMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.function.Consumer;

/**
 * Handler WebSocket client pour recevoir les messages du sync-server.
 * Délègue les {@link SyncMessage} reçues à un callback (sans bloquer le thread réseau).
 */
public class SyncServerMessageHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SyncServerMessageHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Consumer<SyncMessage> onMessage;

    public SyncServerMessageHandler(Consumer<SyncMessage> onMessage) {
        this.onMessage = onMessage != null ? onMessage : m -> {};
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        try {
            SyncMessage msg = MAPPER.readValue(payload, SyncMessage.class);
            if (msg != null && msg.getType() != null) {
                onMessage.accept(msg);
            }
        } catch (Exception e) {
            LOG.trace("Parse sync message failed: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LOG.debug("Sync-server WebSocket connected");
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        LOG.warn("Sync-server transport error: {}", exception.getMessage());
    }
}
