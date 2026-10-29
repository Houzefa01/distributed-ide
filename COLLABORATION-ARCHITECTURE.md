# Architecture Collaborative Moderne - IDE Distribué

**Statut**: Conception architecturale pour modernisation complète  
**Objectif**: Système collaboratif comparable à Figma / VSCode Live Share / JetBrains Code With Me  
**Approche**: Intégration transparente sans breaking changes

---

## 1. Vue d'ensemble stratégique

```
┌─────────────────────────────────────────────────────────────────┐
│              COLLABORATIVE IDE ARCHITECTURE                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              PRÉSENTATION TIER (JavaFX UI)                │  │
│  │  ┌────────────────┐  ┌──────────────┐  ┌─────────────┐  │  │
│  │  │  Collaborative │  │   Presence   │  │  Remote    │  │  │
│  │  │    Panel       │  │   Indicators │  │  Cursors   │  │  │
│  │  └────────────────┘  └──────────────┘  └─────────────┘  │  │
│  │  ┌────────────────┐  ┌──────────────┐  ┌─────────────┐  │  │
│  │  │  Call Controls │  │  Permission  │  │ Share Panel │  │  │
│  │  │   (Audio/Vidéo)│  │     UI       │  │             │  │  │
│  │  └────────────────┘  └──────────────┘  └─────────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              ▲                                   │
│                              │ Events / Commands                 │
│                              ▼                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         COORDINATION TIER (Local Services)                │  │
│  │  ┌──────────────────┐  ┌──────────────────┐            │  │
│  │  │  Presence        │  │  Permission      │            │  │
│  │  │  Manager         │  │  Manager         │            │  │
│  │  └──────────────────┘  └──────────────────┘            │  │
│  │  ┌──────────────────┐  ┌──────────────────┐            │  │
│  │  │  Cursor          │  │  Call            │            │  │
│  │  │  Sync Manager    │  │  Manager         │            │  │
│  │  └──────────────────┘  └──────────────────┘            │  │
│  │  ┌──────────────────┐  ┌──────────────────┐            │  │
│  │  │  File Share      │  │  Collaboration   │            │  │
│  │  │  Manager         │  │  Engine          │            │  │
│  │  └──────────────────┘  └──────────────────┘            │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              ▲                                   │
│                              │ Protocol Messages                 │
│                              ▼                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         NETWORK TIER (Servers & Protocols)               │  │
│  │  ┌──────────────────┐  ┌──────────────────┐            │  │
│  │  │  Sync Server     │  │  Signaling       │            │  │
│  │  │  (CRDT SYNC)     │  │  Server          │            │  │
│  │  └──────────────────┘  └──────────────────┘            │  │
│  │  ┌──────────────────┐  ┌──────────────────┐            │  │
│  │  │  Presence Sync   │  │  WebRTC P2P      │            │  │
│  │  │  (Client List)   │  │  Media           │            │  │
│  │  └──────────────────┘  └──────────────────┘            │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Piliers architecturaux

### 2.1 Principes directeurs

| Principe | Description | Implémentation |
|----------|-------------|-----------------|
| **Real-Time First** | Chaque action doit mettre à jour tous les clients immédiatement | WebSocket + CRDT + debounce optimisé |
| **Multi-Tier Architecture** | UI ↔ Services ↔ Network | Couches bien séparées, dépendances unidirectionnelles |
| **Non-Blocking Operations** | Aucune UI jamais bloquée | CompletableFuture, ExecutorService, Platform.runLater |
| **Failure Resilient** | Perte réseau / serveur crash / reconnexion transparente | Auto-reconnect, buffering, replay |
| **Secure by Default** | Permissions / rôles / validation côté serveur | Token auth + role-based access control |
| **Extensible Design** | Facile d'ajouter nouveaux types de collaboration | Plugin architecture pour types de partage |

### 2.2 Modèles de données principaux

#### `ClientPresence`
```java
class ClientPresence {
    String clientId;
    String username;
    String role;                    // "admin", "editor", "viewer"
    PresenceStatus status;          // ONLINE, EDITING, IDLE, AWAY
    String currentDocument;         // Document ID en édition
    long lastActivityTimestamp;     // Pour détecter IDLE
    String color;                   // Couleur UI unique
    boolean audioEnabled;
    boolean videoEnabled;
    String sessionToken;            // Pour vérifier la session
}

enum PresenceStatus {
    ONLINE,      // Connecté mais pas actif
    EDITING,     // En train d'éditer
    IDLE,        // Pas d'activité depuis 2 min
    AWAY,        // Pas d'activité depuis 5 min
    OFFLINE
}
```

#### `PermissionModel`
```java
class PermissionModel {
    String clientId;
    String role;                    // admin | editor | viewer
    Set<String> allowedDocumentIds; // null = tous les documents
    boolean canKickClients;
    boolean canShareProjects;
    boolean canManagePermissions;
    boolean canStartCalls;
    long grantedAt;
    long expiresAt;                // null = jamais
}

enum DocumentPermission {
    VIEW,       // Lecture seule
    EDIT,       // Éditer
    OWN,        // Modifier permissions + supprimer
    LOCK        // Verrouillé par admin
}
```

#### `RemoteCursor`
```java
class RemoteCursor {
    String clientId;
    String username;
    String color;
    int line;
    int column;
    long lastUpdateTimestamp;
    String displayFormat;          // "username @ line:col"
    
    // Pour les sélections étendues
    Optional<Selection> selection; // startLine, startCol, endLine, endCol
}
```

#### `CallSession`
```java
class CallSession {
    String callId;
    String initiatorClientId;
    Set<String> participantClientIds;
    CallType type;                 // AUDIO, VIDEO, SCREEN_SHARE
    CallStatus status;             // RINGING, ACTIVE, ENDED
    long startedAt;
    long endedAt;
    Map<String, CallParticipant> participants;
}

class CallParticipant {
    String clientId;
    boolean audioMuted;
    boolean videoDisabled;
    String state;                  // "connecting" | "connected" | "failed"
}
```

---

## 3. Couche de présence (Presence Tier)

### 3.1 `ClientPresenceManager`
Gère la liste des clients et leurs statuts en temps réel.

**Responsabilités**:
- Mettre à jour la liste des clients connexion/déconnexion
- Détecter l'inactivité (IDLE après 2 min, AWAY après 5 min)
- Attribuer couleurs uniques par client
- Notifier observateurs de changements

**Interfaces clés**:
```java
class ClientPresenceManager {
    // Ajouter/nettoyer présence
    void register(ClientPresence presence);
    void unregister(String clientId);
    void updateStatus(String clientId, PresenceStatus status);
    void updateCurrentDocument(String clientId, String docId);
    
    // Queries
    List<ClientPresence> getOnlineClients();
    Optional<ClientPresence> getClient(String clientId);
    List<ClientPresence> getClientsEditingDocument(String docId);
    
    // Listeners
    void addListener(PresenceListener listener);
}

interface PresenceListener {
    void onClientJoined(ClientPresence presence);
    void onClientLeft(String clientId, String username);
    void onStatusChanged(String clientId, PresenceStatus newStatus);
    void onDocumentChanged(String clientId, String newDocId);
    void onActivityDetected(String clientId);
}
```

### 3.2 `CollaborativePanel` (UI)
Panneau dockable moderne montrant la liste des collaborateurs.

**Features**:
- Affiche liste clients avec avatars/couleurs
- Indicateurs de statut (cercle vert/jaune/gris)
- Indicateur activité en temps réel
- Boutons: appel audio/vidéo, expulser (si admin)
- Recherche/filtrage clients
- Animations fluides

---

## 4. Couche de synchronisation de curseurs

### 4.1 `RemoteCursorManager`
Gère l'affichage des curseurs distants en temps réel.

**Responsabilités**:
- Recevoir positions curseur distants via WebSocket
- Dessiner curseurs avec label "username"
- Couleur unique par client
- Animer mouvement curseur
- Nettoyer curseur à la déconnexion

**Optimisations réseau**:
```
Debounce: 50ms max (envoyer position seulement si > 50ms depuis dernier)
Compression: Envoyer delta positions (not absolue) 
Throttle: Max 20 updates/sec par client
```

**Protocole**:
```json
{
  "type": "CURSOR_UPDATE",
  "documentId": "doc-123",
  "clientId": "client-abc",
  "username": "Alice",
  "color": "#FF5733",
  "position": {
    "line": 42,
    "column": 15
  },
  "timestamp": 1708671600000,
  "selection": {
    "startLine": 40,
    "startCol": 0,
    "endLine": 42,
    "endCol": 15
  }
}
```

### 4.2 Intégration CodeEditor
```java
// Dans CodeEditor.java
@Override
public void handle(KeyEvent e) {
    handleKeyInput(e);

    // Notifier mise à jour curseur avec debounce
    cursorSyncManager.reportCursorPosition(
        documentId,
        getCurrentLine(),
        getCurrentColumn(),
        getSelection()  // Optional pour sélections étendues
    );
}

// Afficher curseurs distants
@Override
protected void onKeyTyped(KeyCode code, String character) {
    // ...
    // Animer curseurs distants (ne pas bloquer typing)
    remoteCursorManager.commitCursorPosition(documentId, clientId, position);
}
```

---

## 5. Couche d'appels WebRTC

### 5.1 `WebRTCCallManager`
Gère les appels audio/vidéo P2P et groupe via WebRTC.

**Flux d'appel**:
```
Alice clique "Call Bob"
  ↓
CallManager.initiateCall(bobClientId, CallType.AUDIO)
  ↓
Alice obtient état RINGING local
  ↓
SignalingServer reçoit CALL_INITIATE → envoie Bob : INCOMING_CALL
  ↓
Bob reçoit notification, accepte
  ↓
Alice ↔ SignalingServer: Échange SDP offers/answers
  ↓
Alice ↔ SignalingServer: Échange ICE candidates
  ↓
WebRTC P2P media stream établie
  ↓
État ACTIVE, audio bidirectionnel
  ↓
(Optional) Bob clique "Add Charlie"
  ↓
Charlie rejoint call, devient groupe AUDIO
```

**Interfaces**:
```java
class WebRTCCallManager {
    // Initier appel
    CompletableFuture<String> initiateCall(
        String targetClientId, 
        CallType type  // AUDIO | VIDEO | SCREEN_SHARE
    );
    
    // Répondre à appel entrant
    void answerCall(String callId);
    void declineCall(String callId);
    void hangupCall(String callId);
    
    // Contrôles dans appel
    void muteAudio(String callId);
    void unmuteAudio(String callId);
    void disableVideo(String callId);
    void enableVideo(String callId);
    
    // Ajouter participant (groupe)
    void addParticipant(String callId, String clientId);
    void removeParticipant(String callId, String clientId);
    
    // Listeners
    void addCallListener(CallListener listener);
}

interface CallListener {
    void onIncomingCall(CallSession call);
    void onCallActive(String callId);
    void onCallEnded(String callId, String reason);
    void onParticipantJoined(String callId, String clientId);
    void onParticipantLeft(String callId, String clientId);
    void onMediaError(String callId, String error);
}
```

### 5.2 UI des appels (CallControlPanel)
```
┌─────────────────────────────────┐
│  Active Call: Audio with Alice  │
├─────────────────────────────────┤
│  🎤 🎥 🖥️  ⏱️ 05:32            │
│  ┌─────────────────────────────┐│
│  │ [🔇]  [📹]  [🖥️]  [☎️]     ││ ← Buttons
│  └─────────────────────────────┘│
│  Participants: Alice, Bob, Charlie
│  [+] Add participant            │
│  [X] End call                   │
└─────────────────────────────────┘
```

---

## 6. Couche de partage de fichiers

### 6.1 `FileShareManager`
Gère les demandes de partage projet/fichier.

**Flux**:
```
Alice: "Je veux partager le projet 'backend'"
  ↓
Alice envoie SHARE_REQUEST {projectId, targetClientIds}
  ↓
Bob/Charlie reçoivent notification popup:
  "Alice wants to share project 'backend'"
  [Accept] [Decline]
  ↓
Bob clique [Accept]
  ↓
Server envoie full project snapshot de Alice
  ↓
Bob reçoit et initialise CRDT avec projet
  ↓
À partir de là: modifications temps réel synchronized
```

**Interfaces**:
```java
class FileShareManager {
    // Initier partage
    CompletableFuture<Void> requestShareProject(
        Set<String> targetClientIds, 
        String projectPath
    );
    
    CompletableFuture<Void> requestShareFile(
        Set<String> targetClientIds,
        String filePath
    );
    
    // Répondre à demande
    void acceptShare(String shareRequestId);
    void declineShare(String shareRequestId);
    
    // Listeners
    void addShareListener(ShareListener listener);
}

interface ShareListener {
    void onShareRequested(ShareRequest request);
    void onShareAccepted(String shareRequestId);
    void onShareRejected(String shareRequestId);
}
```

**UI**: PopupDialog avec description projet et boutons Accept/Decline

---

## 7. Couche de permissions et rôles

### 7.1 `PermissionManager`
Gère rôles, permissions et contrôle d'accès.

**Rôles**:
| Rôle | Connexion | Lecture | Édition | Admin | Appels |
|------|-----------|---------|---------|-------|--------|
| Admin | ✓ | ✓ | ✓ | ✓ | ✓ |
| Editor | ✓ | ✓ | ✓ | ✗ | ✓ |
| Viewer | ✓ | ✓ | ✗ | ✗ | ✓ |

**Interfaces**:
```java
class PermissionManager {
    // Gestion rôles
    void grantRole(String clientId, String role);
    void revokeRole(String clientId);
    String getRole(String clientId);
    
    // Gestion permissions document
    void lockDocument(String docId, String reason);
    void unlockDocument(String docId);
    boolean canEditDocument(String clientId, String docId);
    
    // Admin commands
    void kickClient(String clientId, String reason);
    void setMaxClients(int maxClients);
    
    // Listeners
    void addPermissionListener(PermissionListener listener);
}
```

---

## 8. Optimisations réseau et performance

### 8.1 Stratégies d'optimisation

```
┌────────────────────────────────────────────────┐
│            NETWORK OPTIMIZATION                │
├────────────────────────────────────────────────┤
│                                                │
│ 1. MESSAGE BATCHING                           │
│    Bundle multiple updates → single message   │
│    Batch size: 100 ops, Timeout: 50ms         │
│                                                │
│ 2. DELTA COMPRESSION                          │
│    Send only changed fields, not full state   │
│    Saves ~60% bandwidth on presence updates   │
│                                                │
│ 3. DEBOUNCE & THROTTLE                        │
│    Cursor: debounce 50ms, throttle 20Hz       │
│    Selection: debounce 100ms, throttle 10Hz   │
│    Activity: debounce 500ms                   │
│                                                │
│ 4. PROTOCOL EFFICIENCY                        │
│    Use binary format (MessagePack) for        │
│    high-frequency messages (cursor, activity) │
│    Keep JSON for low-frequency (auth, config) │
│                                                │
│ 5. LAZY LOADING                               │
│    Don't load full document history until     │
│    user scrolls to that section               │
│                                                │
│ 6. CONNECTION POOLING                         │
│    One WebSocket per document CRDT sync       │
│    Separate WebSocket for presence updates    │
│    Separate WebRTC for media                  │
│    Total: 3 connections max per client        │
│                                                │
└────────────────────────────────────────────────┘
```

### 8.2 Gestion de la mémoire

```java
// Limiter nombre de curseurs en cache
class CursorCache {
    private static final int MAX_CURSORS = 1000;
    private LinkedHashMap<String, RemoteCursor> cache;
    
    // Éviction LRU après MAX_CURSORS
}

// Auto-cleanup de sessions inactives
ScheduledExecutorService idleChecker = Executors.newScheduledThreadPool(1);
idleChecker.scheduleAtFixedRate(() -> {
    for (ClientPresence client : presenceManager.getClients()) {
        if (client.status == AWAY && now - client.lastActivityTimestamp > 30 * 60_000) {
            // Auto-disconnect after 30 min idle
            kickClient(client.clientId, "Idle timeout");
        }
    }
}, 0, 5, TimeUnit.MINUTES);
```

---

## 9. Tests et stabilité

### 9.1 Scénarios de test critiques

**Test 1: 10 clients simultanés**
- Tous modifient le même document
- Vérifier: Pas de deadlock, convergence CRDT rapide (<200ms)

**Test 2: Édition conflictuelle**
- Client A insère "int" à position 10
- Client B insère "void" à position 10 simultanément
- Vérifier: Résolution déterministe, pas de corruption

**Test 3: Reconnexion après perte réseau**
- Serveur arrêté → Clients buffering locale
- Serveur redémarré → Clients reconnectent auto
- Vérifier: Zéro perte d'opérations, state reconstruit

**Test 4: Appel vidéo + édition simultanée**
- 3 clients en appel vidéo
- Pendant ce temps, tous éditent document
- Vérifier: Vidéo fluide, pas de lag édition

**Test 5: Partage projet 100MB**
- Alice partage projet volumineux
- Bob accepte
- Vérifier: Pas de timeout, progression visible, temps < 30s

**Test 6: Kick client pendant appel**
- Admin kick client en appel actif
- Vérifier: Client déconnecté proprement, appel fermé, autres clients notifiés

### 9.2 Métriques de performance
```
Metric                          Target        Acceptable
─────────────────────────────────────────────────────────
Latency (local LAN):            <50ms         <100ms
Message delivery:               100%(retries) >99.9%
Cursor position update delay:   <100ms        <200ms
Edit propagation:               <200ms        <500ms
Connection setup:               <2s           <5s
Video call latency:             <150ms        <300ms
Memory per client:              <20MB         <50MB
CPU per client:                 <5%           <15%
```

---

## 10. Livrables par phase

### Phase 1: Foundation (Semaine 1)
- ✅ `ClientPresenceManager` + model
- ✅ `PermissionManager` + model  
- ✅ `CollaborationPanel` UI
- ✅ Tests unitaires

### Phase 2: Curseurs et Synchronisation (Semaine 2)
- ✅ `RemoteCursorManager`
- ✅ Protocol debounce/throttle
- ✅ UI curseurs distants
- ✅ Tests avec 5 clients

### Phase 3: Appels WebRTC (Semaine 3)
- ✅ `WebRTCCallManager`
- ✅ `SignalingServer` enhancements
- ✅ `CallControlPanel` UI
- ✅ Tests audio/vidéo

### Phase 4: Partage Fichiers (Semaine 3.5)
- ✅ `FileShareManager`
- ✅ Share request dialogs
- ✅ Project snapshot logic

### Phase 5: Optimisation & Stabilité (Semaine 4)
- ✅ Batching + compression
- ✅ Tests 10+ clients
- ✅ Reconnection resilience
- ✅ Memory profiling

### Phase 6: Documentation (Semaine 4.5)
- ✅ Architecture finale
- ✅ API documentation
- ✅ Integration guide
- ✅ Operations guide

---

## 11. Critères de succès

| Critère | Métrique |
|---------|----------|
| **Stabilité** | 0 crash sur 1h de test 10 clients |
| **Performance** | <50ms latency, <10% CPU per client |
| **Real-time** | Curseur visible <100ms, edit <200ms |
| **Fiabilité** | 99.9% message delivery guarantee |
| **UX** | Toutes transitions fluides, 60 FPS |
| **Code** | Clean architecture, 80%+ test coverage |

---

## Prochaines étapes

1. **Créer models de données** (ClientPresence, Permission, etc.)
2. **Implémenter ClientPresenceManager**
3. **Créer CollaborativePanel UI moderne**
4. **Intégrer avec CollaborationService existant**
5. **Progresser selon phasing**

