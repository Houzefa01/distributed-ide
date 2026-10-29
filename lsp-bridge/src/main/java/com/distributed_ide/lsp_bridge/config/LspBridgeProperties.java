package com.distributed_ide.lsp_bridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the LSP bridge (sync-server URL, etc.).
 */
@Component
@ConfigurationProperties(prefix = "lsp.bridge")
public class LspBridgeProperties {

    private String syncServerUrl = "";

    public String getSyncServerUrl() {
        return syncServerUrl;
    }

    public void setSyncServerUrl(String syncServerUrl) {
        this.syncServerUrl = syncServerUrl != null ? syncServerUrl.trim() : "";
    }
}
