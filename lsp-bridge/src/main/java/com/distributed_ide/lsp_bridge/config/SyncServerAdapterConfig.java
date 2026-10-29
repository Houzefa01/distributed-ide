package com.distributed_ide.lsp_bridge.config;

import com.distributed_ide.lsp_bridge.sync.DocumentContentForwarder;
import com.distributed_ide.lsp_bridge.sync.DocumentReplicaStore;
import com.distributed_ide.lsp_bridge.sync.SyncServerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SyncServerAdapterConfig {

    @Bean
    public SyncServerAdapter syncServerAdapter(LspBridgeProperties properties,
                                               DocumentReplicaStore documentReplicaStore,
                                               DocumentContentForwarder documentContentForwarder) {
        SyncServerAdapter adapter = new SyncServerAdapter(properties.getSyncServerUrl());
        if (adapter.isEnabled()) {
            adapter.setSyncToLspIntegration(documentReplicaStore, documentContentForwarder);
        }
        return adapter;
    }
}
