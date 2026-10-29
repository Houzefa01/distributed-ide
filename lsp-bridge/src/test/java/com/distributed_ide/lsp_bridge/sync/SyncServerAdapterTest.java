package com.distributed_ide.lsp_bridge.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyncServerAdapterTest {

    @Test
    void isEnabled_emptyUrl_returnsFalse() {
        SyncServerAdapter adapter = new SyncServerAdapter("");
        assertFalse(adapter.isEnabled());
    }

    @Test
    void isEnabled_nullUrl_returnsFalse() {
        SyncServerAdapter adapter = new SyncServerAdapter(null);
        assertFalse(adapter.isEnabled());
    }

    @Test
    void isEnabled_wsUrl_returnsTrue() {
        SyncServerAdapter adapter = new SyncServerAdapter("ws://localhost:8765");
        assertTrue(adapter.isEnabled());
    }

    @Test
    void documentOpened_whenDisabled_doesNotThrow() {
        SyncServerAdapter adapter = new SyncServerAdapter("");
        assertDoesNotThrow(() -> adapter.documentOpened("file:///tmp/foo.java"));
    }

    @Test
    void documentOpened_whenNullId_doesNotThrow() {
        SyncServerAdapter adapter = new SyncServerAdapter("ws://localhost:9999");
        assertDoesNotThrow(() -> adapter.documentOpened(null));
    }
}
