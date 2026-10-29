package com.distributed_ide.lsp_bridge.config;

import com.distributed_ide.lsp_bridge.bridge.DocumentToSessionRegistry;
import com.distributed_ide.lsp_bridge.bridge.LspSessionRegistry;
import com.distributed_ide.lsp_bridge.sync.DocumentContentForwarder;
import com.distributed_ide.lsp_bridge.sync.DocumentReplicaStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration de l'intégration sync-server → réplique CRDT → LSP (didChange) → diagnostics → client.
 */
@Configuration
public class SyncIntegrationConfig {

    @Bean
    public DocumentToSessionRegistry documentToSessionRegistry() {
        return new DocumentToSessionRegistry();
    }

    @Bean
    public DocumentReplicaStore documentReplicaStore() {
        return new DocumentReplicaStore();
    }

    @Bean
    public DocumentContentForwarder documentContentForwarder(
            DocumentToSessionRegistry documentToSessionRegistry,
            LspSessionRegistry sessionRegistry) {
        return new DocumentContentForwarder(documentToSessionRegistry, sessionRegistry);
    }
}
