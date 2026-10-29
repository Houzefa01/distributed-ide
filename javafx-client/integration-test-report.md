# Distributed-IDE - Integration Test Report

Generated at: 2026-02-22T17:39:53.131165970Z

## Environment

- **syncPort**: 42485
- **terminalPort**: 33273
- **signalingPort**: 44661
- **lspPort**: 34937

## 5) Signaling WebRTC (SDP/ICE forwarding)

- **roomId**: room-it-1153f6d8
- **alice**: alice-8ecac2
- **bob**: bob-129250

## 1) CRDT convergence multi-clients

- **clients**: 3
- **opsPerClient**: 4
- **totalOps**: 12
- **convergenceMs**: 849

## 4) Terminal service (Docker sandbox via WebSocket)

- **terminalWsUri**: ws://localhost:33273
- **sessionId**: term-it-20f05d78

## 2) Synchronisation WebSocket (reconnexion + resync)

- **docId**: doc-it-resync-d032a622-2457-462e-9539-d7c7f0f12985
- **ops**: 6

## 3) LSP diagnostics (smoke init)

- **lspWsUri**: ws://localhost:34937/lsp
- **firstMessage**: {"error":"Start LSP failed: JAVA:/home/hsavatra/distributed-ide/javafx-client. jdtls not found. Install jdtls or set JDTLS_HOME to a valid JDTLS directory (expects $JDTLS_HOME/bin/jdtls)."}

