# Distributed IDE — Architecture Technique

## 1. Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────────┐
│                    DISTRIBUTED IDE                               │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │  JavaFX       │  │  Sync Server │  │  LSP Bridge  │          │
│  │  Client       │  │  (WebSocket) │  │  (WebSocket) │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                  │                  │                   │
│  ┌──────┴───────┐  ┌──────┴───────┐  ┌──────┴───────┐          │
│  │  CRDT Core   │  │  Terminal    │  │  Signaling   │          │
│  │  (shared)    │  │  Service     │  │  Server      │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐                             │
│  │  Plugin      │  │  Project     │                             │
│  │  System      │  │  Manager     │                             │
│  └──────────────┘  └──────────────┘                             │
└─────────────────────────────────────────────────────────────────┘
```

## 2. Modules Maven

| Module | Rôle | Port par défaut |
|--------|------|-----------------|
| `crdt-core` | Moteur CRDT (LSeq) partagé client/serveur | — |
| `sync-server` | Serveur WebSocket de synchronisation temps réel | 8765 |
| `javafx-client` | Client IDE JavaFX (UI, éditeur, services) | — |
| `lsp-bridge` | Pont WebSocket vers serveurs LSP natifs | 8081 |
| `terminal-service` | Service terminal distant via WebSocket | 8766 |
| `signaling-server` | Signaling WebRTC pour appels audio/vidéo | 8082 |
| `plugin-system` | Framework de plugins (chargement, API) | — |
| `project-manager` | Gestion de projets et templates | — |

## 3. Architecture du Client JavaFX

### 3.1 Diagramme logique

```
DistributedIDEApp (entry point)
  │
  ├── IDEConfig (singleton)         ← Configuration centralisée
  ├── IDEErrorHandler (singleton)   ← Gestion erreurs globale
  │
  └── MainLayoutController          ← Contrôleur principal (logique pure)
        │
        ├── Services Backend
        │   ├── SyncClientService       (WebSocket → sync-server)
        │   │   ├── Auto-reconnect (exponential backoff)
        │   │   ├── CRDT engine (local replica)
        │   │   └── Document subscriptions
        │   ├── LspClientService        (WebSocket → lsp-bridge)
        │   ├── TerminalClientService   (WebSocket → terminal-service)
        │   ├── CollaborationService    (gestion sessions collab)
        │   ├── WebRtcCallService       (appels audio/vidéo)
        │   ├── FileService             (opérations fichiers)
        │   ├── FileWatcherService      (WatchService, auto-refresh)
        │   ├── GitService              (intégration Git)
        │   ├── LocalTerminalService    (terminal local ProcessBuilder)
        │   ├── SettingsManager         (JSON persistant)
        │   └── WorkspaceManager        (sauvegarde/restauration état)
        │
        ├── Composants UI
        │   ├── MainLayout              (construction nœuds JavaFX)
        │   ├── EditorManager           (tabs, split, dirty tracking)
        │   ├── ProjectTreeView         (arborescence fichiers)
        │   ├── GlobalSearchView        (recherche projet Ctrl+Shift+F)
        │   ├── QuickOpenDialog         (Ctrl+P fuzzy search)
        │   ├── CommandPaletteDialog    (Ctrl+Shift+P)
        │   ├── NavigationHistory       (Alt+Left/Right)
        │   ├── DiagnosticsView         (erreurs/warnings LSP)
        │   ├── OutputView              (logs sortie)
        │   ├── TerminalView            (terminal intégré)
        │   ├── ChatView                (chat collaboratif)
        │   ├── CollaborationPanel      (gestion sessions)
        │   ├── StatusBarView           (barre d'état)
        │   └── GitPanelView            (panneau Git)
        │
        └── Plugin Extension
            └── IDEPluginAPI (interface)  ← Contrat stable pour plugins
```

### 3.2 Flux de données

```
Utilisateur
  │
  ▼
CodeEditor (keystroke)
  │
  ├──► EditorManager.markDirty()     → Tab affiche "●"
  ├──► NavigationHistory.record()    → Historique navigation
  ├──► SyncClientService.insert()    → CRDT local + envoi serveur
  │     │
  │     ▼
  │   SyncWebSocketServer
  │     ├── Validation entrée (taille, docId, rate limit)
  │     ├── CRDTEngine.apply()       → État convergent
  │     ├── OperationBatcher         → Batch + broadcast
  │     └── Broadcast → autres clients
  │
  └──► LspClientService.didChange()  → Debounced → LSP Bridge
        │
        ▼
      LSP Server → diagnostics → DiagnosticsView
```

## 4. Architecture du Sync Server

```
SyncWebSocketServer
  │
  ├── Input Validation
  │   ├── Message size (max 5 MB)
  │   ├── Document ID sanitization (anti-traversal)
  │   ├── Operation type validation (INSERT/DELETE)
  │   └── Rate limiting (200 msg/s per client)
  │
  ├── CRDTEngine (serveur)
  │   └── Documents CRDT partagés
  │
  ├── OperationBatcher
  │   ├── Batch par document
  │   └── Flush timeout + size threshold
  │
  ├── ClientSession
  │   ├── Authentication (token-based)
  │   ├── Role (ADMIN/EDITOR/VIEWER)
  │   ├── Heartbeat tracking
  │   ├── Rate limit tracking
  │   └── Document subscriptions
  │
  └── Network Stats
      ├── totalMessagesReceived
      ├── totalMessagesSent
      └── totalOperationsProcessed
```

## 5. Packages (Clean Architecture)

```
com.distributed_ide.javafx_client
  ├── config/          ← IDEConfig, IDEErrorHandler
  ├── editor/          ← CodeEditor, EditorManager, NavigationHistory, SyntaxHighlighter
  ├── git/             ← GitService, GitPanelView
  ├── lsp/             ← LSPManager, LspProtocol, LspProcessManager, JsonRpcTransport
  ├── model/           ← DiagnosticItem, RemoteCursor, ProjectNode, UndoRedoStack
  ├── plugin/          ← IDEPluginAPI (interface)
  ├── service/         ← Tous les services backend (aucune dépendance UI)
  ├── theme/           ← ThemeManager
  ├── ui/              ← Tous les composants UI JavaFX
  ├── util/            ← Utilitaires (SyncOperationConverter, etc.)
  └── webrtc/          ← WebRtcCallService, SignalingClientService

com.distributed_ide.sync
  ├── model/           ← SyncMessage, ClientSession
  └── server/          ← SyncWebSocketServer, OperationBatcher

com.distributed_ide.crdt
  ├── engine/          ← CRDTEngine
  └── model/           ← CRDTDocument, CRDTElement, CRDTIdentifier, CRDTOperation
```

## 6. Configuration centralisée

**`IDEConfig`** (singleton) — source unique de vérité :
- URIs WebSocket (sync, lsp, terminal, signaling)
- Timeouts (connect, heartbeat, debounce)
- Limites (message size, rate limit, search results)
- Feature flags (autoSave, autoReconnect, fileWatcher)
- Priorité : defaults → `settings.json` → system properties (`-Dide.sync.uri=...`)

## 7. Gestion d'erreurs globale

**`IDEErrorHandler`** (singleton) :
- `handle(category, message, cause)` → log + output panel
- `handleUI(category, message, cause)` → log + dialog utilisateur
- `warn()` / `info()` → niveaux légers
- Thread.setDefaultUncaughtExceptionHandler → capture exceptions non gérées
- Listeners extensibles pour télémétrie future

## 8. Codes morts identifiés

| Fichier | Raison | Remplacement |
|---------|--------|--------------|
| `MainView.java` | Non référencé | `MainLayout` + `MainLayoutController` |
| `EnhancedEditorTabPaneView.java` | Non référencé | `EditorManager` |
| `SimplePluginIntegration.java` | Non référencé | `IDEPluginAPI` |
| `VideoCallView.java` | Non référencé | `WebRtcCallService` direct |
| `EditorTabModel.java` | Uniquement par code mort | `EditorManager` tabs |

## 9. Préparation Extension Plugin

**`IDEPluginAPI`** (interface) — contrat stable :
- **Editor** : open/close/read files, go to line
- **Commands** : register/unregister command palette entries
- **UI** : notifications, output, dialogs
- **Project** : get root, refresh tree
- **Settings** : read/write persistent settings
- **Events** : subscribe FILE_OPENED, FILE_SAVED, PROJECT_OPENED, etc.

## 10. Tests

| Suite | Couverture |
|-------|-----------|
| `SyncServerIntegrationTest` | Connection, multi-client, broadcast, invalid input, rate limiting, network stats, latency |
| CRDT unit tests | Insert, delete, concurrent operations, convergence |
| Sync model tests | SyncMessage serialization |
