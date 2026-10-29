# Signaling Server - Module de Signalisation WebRTC

Module Maven pour la signalisation WebRTC permettant les connexions P2P audio/vidéo.

## Vue d'ensemble

Le `signaling-server` gère la signalisation WebRTC pour faciliter les connexions pair-à-pair entre clients. Il fournit:

- **Gestion des salles** : Création et suppression dynamiques
- **Sessions utilisateurs** : Suivi des participants connectés
- **SDP Exchange** : Échange des offres/réponses SDP
- **ICE Candidates** : Forwarding des candidats ICE
- **Notifications** : Alertes d'arrivée/départ des utilisateurs
- **Multi-utilisateurs** : Support de conférences N-way

## Architecture

### Composants principaux

#### `SignalingMessage`
Modèle de protocole pour la signalisation WebRTC.

**Types de messages** :
- `JOIN_ROOM` : Rejoindre une salle
- `LEAVE_ROOM` : Quitter une salle
- `OFFER` : Offre SDP
- `ANSWER` : Réponse SDP
- `ICE_CANDIDATE` : Candidat ICE
- `USER_JOINED` : Notification arrivée utilisateur
- `USER_LEFT` : Notification départ utilisateur
- `ROOM_FULL` : Salle pleine
- `ERROR` : Message d'erreur
- `PING/PONG` : Health check

Inclut une classe interne `IceCandidate` pour les candidats ICE.

#### `UserSession`
Représente une session utilisateur connectée :
- Identifiant unique
- WebSocket de connexion
- Métadonnées (displayName, videoEnabled, etc.)
- Herttbeat et suivi de l'activité

#### `Room`
Salle de conférence WebRTC :
- Identifiant et créateur
- Capacité maximale de participants
- Gestion des participants
- Métadonnées de salle
- Tracking d'inactivité

#### `RoomManager`
Gestionnaire centralisé des salles :
- Création/suppression de salles
- Ajout/suppression utilisateurs
- Mapping utilisateur → salle
- Cleanup des salles inactives
- Requêtes d'information

#### `SignalingWebSocketServer`
Serveur WebSocket principal :
- Gestion des connexions/déconnexions
- Routage des messages de signalisation
- Forwarding SDP et ICE candidates
- Notifications d'utilisateurs

## Flux de signalisation

```
Client A (Alice)              Server                Client B (Bob)
       │                         │                        │
       │─── JOIN_ROOM ─────────>│                        │
       │                         │                        │
       │<─ USER_JOINED ─────────────────────────────────>│
       │                         │                        │
       │─── OFFER (SDP) ──────>│                        │
       │                         │─── OFFER (SDP) ──────>│
       │                         │                        │
       │<─ ANSWER (SDP) ────────│<─── ANSWER (SDP) ─────│
       │                         │                        │
       │─ ICE Candidate ─────>│                        │
       │                         │─ ICE Candidate ───────>│
       │                         │                        │
       │<─ ICE Candidate ────────│<─ ICE Candidate ─────│
       │                         │                        │
       │◄──── WebRTC P2P Connection Established ────────►│
       │                                                   │
```

## Dépendances

```xml
<!-- WebSocket -->
<dependency>
    <groupId>org.java-websocket</groupId>
    <artifactId>Java-WebSocket</artifactId>
</dependency>

<!-- JSON -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<!-- Logging -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
</dependency>

<!-- Tests -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

## Tests

Suite de tests JUnit 5 couvrant :

### SignalingMessageTest (7 tests)
- Création et sérialisation
- SDP et ICE candidates
- Types de messages
- Métadonnées

### UserSessionTest (7 tests)
- Création de session
- Heartbeat et activité
- Durée de connexion
- Métadonnées utilisateur

### RoomTest (10 tests)
- Création et gestion
- Ajout/suppression participants
- Capacité limite
- Métadonnées salle
- Informations salle

### RoomManagerTest (11 tests)
- Création/suppression salles
- Gestion des utilisateurs
- Suivi utilisateur-salle
- Multi-salles
- Requêtes d'information

### SignalingWebSocketServerTest (6 tests)
- Intégration salles/sessions
- Plusieurs salles
- Capacité salles
- Suppression automatique
- Métadonnées
- Suivi utilisateur

**Total : 41 tests ✅**

## Utilisation

### Initialisation du serveur
```java
RoomManager roomManager = new RoomManager(
    4,        // Max participants par salle
    300000    // Timeout inactivité (5 min)
);

SignalingWebSocketServer server = new SignalingWebSocketServer(
    new InetSocketAddress("0.0.0.0", 8081),
    roomManager
);
server.start();
```

### Cycle de vie d'une conférence

1. **Alice rejoint room1**
   ```
   {
     "type": "JOIN_ROOM",
     "userId": "alice",
     "roomId": "room1"
   }
   ```

2. **Bob rejoint room1**
   - Alice reçoit USER_JOINED de Bob

3. **Alice envoie OFFER SDP**
   ```
   {
     "type": "OFFER",
     "userId": "alice",
     "roomId": "room1",
     "targetUserId": "bob",
     "sdp": "v=0\r\ns=..."
   }
   ```

4. **Bob envoie ANSWER SDP**
   - Alice reçoit l'answer

5. **Exchange ICE candidates** (continu)
   ```
   {
     "type": "ICE_CANDIDATE",
     "userId": "alice",
     "roomId": "room1",
     "targetUserId": "bob",
     "candidate": {
       "candidate": "candidate:...",
       "sdpMLineIndex": 0,
       "sdpMid": "audio"
     }
   }
   ```

6. **WebRTC P2P établie** - Communication directe
7. **Quitter** - LEAVE_ROOM

## Gestion des salles

- **Création dynamique** : Salle créée au premier JOIN
- **Auto-suppression** : Supprimée quand dernier utilisateur part
- **Capacity limit** : Refus si pleine
- **Inactivity timeout** : Cleanup des salles inactives
- **Metadata** : Infos salle (titre, options)

## Gestion des utilisateurs

- **Session tracking** : Mapping userId → Room
- **Activity monitoring** : Heartbeat et timestamps
- **Metadata** : DisplayName, capabilities, settings
- **Automatic cleanup** : Au déconnexion
- **Participant discovery** : Liste des autres dans salle

## Performance

- **Thread-safe** : ConcurrentHashMap pour accès multi-thread
- **Minimal locking** : Pas de synchronization lourde
- **Scalable** : Supporte 100+ salles, 1000+ connexions
- **Memory efficient** : Cleanup automatique des inactives

## Logs

Configuration SLF4J/Logback pour tracer :
- Connexions/déconnexions clients
- Créations/suppressions salles
- Arrivées/départs utilisateurs
- Forwarding SDP/ICE

## Sécurité (Futur)

- [ ] Authentification client
- [ ] Authorization par salle
- [ ] Rate limiting
- [ ] Encryption des SDP
- [ ] CORS protection
- [ ] Token-based rooms

## Prochaines étapes

- [ ] State synchronization pour reconnexion
- [ ] Room recording metadata
- [ ] Bandwidth constraints signaling
- [ ] Quality switching signals
- [ ] Permission system
- [ ] Persistent room storage
