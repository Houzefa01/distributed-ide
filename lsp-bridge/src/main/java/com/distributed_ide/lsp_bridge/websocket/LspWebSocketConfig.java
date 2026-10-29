package com.distributed_ide.lsp_bridge.websocket;

import com.distributed_ide.lsp_bridge.bridge.DocumentToSessionRegistry;
import com.distributed_ide.lsp_bridge.bridge.LspSessionRegistry;
import com.distributed_ide.lsp_bridge.sync.SyncServerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class LspWebSocketConfig {

    public static final String LSP_WS_PATH = "/lsp";

    @Bean
    public LspSessionRegistry lspSessionRegistry() {
        return new LspSessionRegistry();
    }

    @Bean
    public LspWebSocketHandler lspWebSocketHandler(LspSessionRegistry sessionRegistry,
                                                   DocumentToSessionRegistry documentToSessionRegistry,
                                                   java.util.Optional<SyncServerAdapter> syncAdapter) {
        return new LspWebSocketHandler(sessionRegistry, documentToSessionRegistry, syncAdapter.orElse(null));
    }

    @Bean
    public WebSocketConfigurer webSocketConfigurer(LspWebSocketHandler handler) {
        return new WebSocketConfigurer() {
            @Override
            public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
                registry.addHandler(handler, LSP_WS_PATH).setAllowedOrigins("*");
            }
        };
    }
}
