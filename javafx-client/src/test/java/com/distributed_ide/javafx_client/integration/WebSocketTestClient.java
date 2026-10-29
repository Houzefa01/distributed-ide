package com.distributed_ide.javafx_client.integration;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class WebSocketTestClient {

    private final WebSocketClient client;
    private final CountDownLatch openLatch = new CountDownLatch(1);
    private final List<String> messages = new CopyOnWriteArrayList<>();

    WebSocketTestClient(String uri) {
        try {
            this.client = new WebSocketClient(new URI(uri)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    openLatch.countDown();
                }

                @Override
                public void onMessage(String message) {
                    messages.add(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                }

                @Override
                public void onError(Exception ex) {
                }
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void connectBlocking(long timeoutMs) {
        client.connect();
        try {
            if (!openLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("WebSocket connect timeout: " + client.getURI());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    void send(String msg) {
        client.send(msg);
    }

    void close() {
        try {
            client.close();
        } catch (Exception ignored) {
        }
    }

    List<String> messages() {
        return messages;
    }
}
