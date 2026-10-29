# Architecture - Sync Server

## Vue d'ensemble détaillée

```
┌──────────────────────────────────────────────────────────────┐
│                    Sync Server Module                        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ SyncWebSocketServer (Serveur WebSocket principal)      │ │
│  │ - Gère connexions de clients                           │ │
│  │ - Reçoit messages JSON                                │ │
│  │ - Applique opérations via CRDTEngine                  │ │
│  │ - Envoie updates aux autres clients                   │ │
│  └────────────────────────────────────────────────────────┘ │
│                       │                                      │
│                       ├──────────────────┬──────────────────┤
│                       │                  │                  │
│  ┌────────────────────▼──┐  ┌───────────▼─────┐  ┌────────▼──┐
│  │ ClientSession         │  │ DocumentManager  │  │ Operation │
│  │ Registry              │  │ (Gestionnaire    │  │ Batcher   │
│  │                       │  │  documents)      │  │           │
│  │ - clientId            │  │                  │  │ - Size    │
│  │ - nodeId              │  │ - getDocument()  │  │   limit   │
│  │ - connected state     │  │ - createDoc()    │  │ - Time    │
│  │ - subscribed docs     │  │ - metadata       │  │   limit   │
│  │ - pending ops queue   │  │ - subscribers    │  │ - Batch   │
│  │ - heartbeat tracking  │  │                  │  │   flush   │
│  └──────────────────────┘  └──────────────────┘  └───────────┘
│           │                        │                   │
└───────────┼────────────────────────┼───────────────────┼─────┘
            │                        │                   │
            │    CRDTEngine (crdt-core)                 │
            │    ┌─────────────────────────────────┐   │
            │    │ Documents                       │   │
            │    │ Operations                      │   │
            │    │ Merging & Synchronization       │   │
            │    └─────────────────────────────────┘   │
            │                                           │
            └───────────────────────────────────────────┘

● SyncMessage
  └─ OperationData struct
```

## Flux des données

### 1. Réception d'opération d'un client

```java
// Client A envoie OPERATION JSON
WebSocket.onMessage(String json) {
    SyncMessage msg = deserialize(json);  // Parse JSON
    
    // Appliquer au CRDT local
    CRDTEngine.applyOperation(msg.getDocumentId(), msg.getOperation());
    
    // Ajouter au batch pour distribution
    OperationBatcher.addOperation(msg.getDocumentId(), msg.getOperation());
}
```

### 2. Batching et optimisation réseau

```java
// Accumulation en mémoire
OperationBatcher {
    Map<documentId, List<Operation>> batches;
    
    addOperation(docId, op) {
        if (batches[docId].size() >= SIZE_LIMIT) {
            flush(docId);  // Envoyer maintenant
        } else {
            batches[docId].add(op);  // Ajouter à la queue
            scheduleFlush(TIME_LIMIT);  // Flush dans 100ms max
        }
    }
}
```

### 3. Distribution aux autres clients

```java
// Flush déclenche un BATCH message
void flushBatch(String documentId) {
    List<Operation> ops = batcher.flushBatch(documentId);
    
    SyncMessage batch = new SyncMessage(
        MessageType.BATCH,
        null,  // Server, pas un client
        documentId,
        nodeId
    );
    batch.setOperations(ops);
    
    // Envoyer à tous les clients abonnés à ce document
    Set<ClientSession> subscribers = documentManager
        .getDocument(documentId)
        .getSubscribers();
        
    for (ClientSession client : subscribers) {
        if (!client.getClientId().equals(originalAuthor)) {
            client.sendMessage(serialize(batch));
        }
    }
}
```

### 4. Accusé de réception

```
Client B reçoit BATCH
│
├─ Applique opérations au CRDTEngine local
│
└─ Envoie BATCH ACK au serveur
   └─ Serveur log receipt pour durabilité
```

## Gestion des états de session

```
            ┌─────────────────────┐
            │   CLIENT_INIT       │
            │  (New connection)   │
            └──────────┬──────────┘
                       │
                       │ INIT msg + credentials
                       │
            ┌──────────▼──────────┐
            │    CONNECTED        │◄──────────┐
            │  (Ready for ops)    │           │
            └──────────┬──────────┘           │
                       │                      │
          ┌────────────┼────────────┐         │
          │            │            │         │
          │ (ops)      │ (heartbeat)│         │
          │            │ timeout    │         │
          ▼            ▼            ▼         │
    ┌─────────────────────────────────┐     │
    │    DISCONNECTED                  │     │
    │  (Socket closed, queue purged)   │     │
    └──────────┬──────────────────────┘     │
               │                             │
               │ Reconnect with sessionId   │
               │                        (Yes)
               └────────────────────────────┘
```

## Concurrence et thread-safety

### Collections thread-safe
```java
// ClientSession registry
Map<String, ClientSession> clients = new ConcurrentHashMap<>();

// Opérations en attente par client
Queue<OperationData> pendingOps = 
    Collections.synchronizedList(new LinkedList<>());

// Documents abonnés
Set<String> subscribedDocs = ConcurrentHashMap.newKeySet();

// Batches en mémoire
Map<String, List<OperationData>> batches = new ConcurrentHashMap<>();
```

### Synchronisation minimale
- Pas de locks globaux
- Events asynchrones
- ScheduledExecutorService pour timers sans blocking

## Lifecycle d'une opération CRDT

```
┌──────────────────────────────────────────────────────────┐
│ 1. CLIENT A: Crée opération INSERT('X')                │
│    - Timestamp: 1234567890                             │
│    - NodeId: "client-a-node"                          │
│    - IdDisambiguator: 0                               │
└──────────────┬───────────────────────────────────────────┘
               │
┌──────────────▼───────────────────────────────────────────┐
│ 2. CLIENT A → SERVER via WebSocket                      │
│    {                                                    │
│      "type": "OPERATION",                             │
│      "clientId": "client-a",                          │
│      "documentId": "doc1",                            │
│      "operation": {                                   │
│        "operationType": "INSERT",                     │
│        "idTimestamp": "1234567890",                   │
│        "idNodeId": "client-a-node",                   │
│        "idDisambiguator": 0,                          │
│        "content": "X"                                 │
│      }                                                │
│    }                                                  │
└──────────────┬───────────────────────────────────────────┘
               │
┌──────────────▼───────────────────────────────────────────┐
│ 3. SERVER: Reçoit, désérialise, applique à CRDT        │
│    - CRDTDocument.applyOperation()                    │
│    - Op insérée dans son contexte causal              │
└──────────────┬───────────────────────────────────────────┘
               │
┌──────────────▼───────────────────────────────────────────┐
│ 4. SERVER: Ajoute à OperationBatcher                    │
│    - Accumulée en mémoire                             │
│    - Await flush (size limit ou time limit)           │
└──────────────┬───────────────────────────────────────────┘
               │
┌──────────────▼───────────────────────────────────────────┐
│ 5. SERVER: Flush (ex: batch de 50 ops)                  │
│    - Sérialise en JSON                                │
│    - Envoie BATCH à CLIENT B, C, D                   │
└──────────────┬───────────────────────────────────────────┘
               │
       ┌───────┴────────┬─────────┬──────────┐
       │                │         │          │
┌──────▼──────┐  ┌──────▼───┐ ┌──▼──────┐ ┌─▼───────┐
│ CLIENT B    │  │ CLIENT C │ │ CLIENT D│ │ CLIENT E│
│ Reçoit BATCH│  │ Reçoit   │ │ Reçoit  │ │ (pas    │
│ Applique à  │  │ BATCH +  │ │BATCH +  │ │ abonné) │
│ CRDTDocument│  │applique  │ │applique │ │         │
│ Envoie ACK  │  │ Envoie   │ │ Envoie  │ │         │
│             │  │ ACK      │ │ ACK     │ │         │
└─────────────┘  └──────────┘ └─────────┘ └─────────┘
       │                │         │          │
       └────────┬───────┴─────────┴──────────┘
                │
       ┌────────▼────────┐
       │ SERVER: Reçoit  │
       │ tous les ACKs   │
       │ → Op committed  │
       └─────────────────┘
```

## Performance et optimisations

### Batching réseau
- **Avant** : 100 opérations = 100 messages WebSocket
- **Après** : 100 opérations = 1 message BATCH (50x moins de frais)

### Message structure
```java
// Sans batching : 100 messages × struct overhead
SyncMessage {
    type: OPERATION,
    operation: {...}  // 200 bytes
}

// Avec batching : 1 message × struct overhead
SyncMessage {
    type: BATCH,
    operations: [  // 100 ops × 200 bytes = 20KB
        {...},
        {...},
        ...
    ]
}
```

## Monitoring et diagnostics

### Points de log critiques
```
✓ Client connection
✓ Client disconnection
✓ Operation received (type, author)
✓ Batch flushed (size, docs affected)
✓ Network partition detected
✓ Reconnection successful
✓ Sync state mismatch
✗ Error conditions
```

### Métriques
```java
// Per batch
- operationCount
- bytesSaved
- flushReason (SIZE or TIME)
- latency

// Per client
- messagesReceived
- operationsSent
- heartbeatRoundTripTime
- connectionUptime

// Per document
- activeSubscribers
- operationsApplied
- lastModified
```

## Sécurité et robustesse

### Gestion des erreurs
- Try-catch sur désérialisation JSON
- Validation des IDs de document
- Vérification des droits d'accès (future)

### Cleanup des ressources
```java
onClose(WebSocket) {
    // Supprimer session client
    clients.remove(clientId);
    
    // Déabonner doc tous les documents
    for (String docId : session.getSubscribedDocuments()) {
        documentManager.unsubscribe(clientId, docId);
    }
    
    // Purger opérations en attente
    session.getPendingOperations().clear();
}
```

### Timeouts
- Heartbeat: 30s envoi, 90s timeout déconnexion
- Batch flush: 100ms max attente
- Message parsing: 5s max

