# Architecture - Signaling Server

## Vue d'ensemble détaillée

```
┌──────────────────────────────────────────────────────────────┐
│                 Signaling Server Module                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ SignalingWebSocketServer (WebSocket principal)         │ │
│  │ - Gère connexions de clients                           │ │
│  │ - Reçoit messages JSON (SDP, ICE, control)            │ │
│  │ - Route vers destinataires                            │ │
│  │ - Notifie participants                                │ │
│  └────────────────────────────────────────────────────────┘ │
│                       │                                      │
│                       ├──────────────────┬──────────────────┤
│                       │                  │                  │
│  ┌────────────────────▼──┐  ┌───────────▼─────┐  ┌────────▼──┐
│  │ RoomManager           │  │ Room Registry    │  │ User Mgmt │
│  │                       │  │                  │  │           │
│  │ - Create room         │  │ - Room storage   │  │ - Session │
│  │ - Delete room         │  │ - User map       │  │   tracking│
│  │ - Cleanup inactive    │  │ - Participant    │  │ - Activity│
│  │ - User tracking       │  │   queries        │  │   monitor │
│  │ - Capacity check      │  │                  │  │ - Metadata│
│  └──────────────────────┘  └──────────────────┘  └───────────┘
│           │                        │                   │
│           └────────────────────────┼───────────────────┘
│                                    │
│           ┌───────────────────────▼────────────────────┐
│           │ Room + UserSession Objects                 │
│           │ - Concurrent collections                   │
│           │ - Thread-safe operations                   │
│           │ - Metadata storage                         │
│           └────────────────────────────────────────────┘

● SignalingMessage Protocol
  └─ MessageType enum (11 types)
  └─ IceCandidate nested class
```

## Flux des données

### 1. Réception d'un JOIN_ROOM

```java
SignalingWebSocketServer.onMessage(WebSocket, String)
  ├─ Parse JSON → SignalingMessage
  ├─ Extract userId, roomId
  ├─ RoomManager.getOrCreateRoom()
  │   └─ Create Room if doesn't exist
  ├─ Room.addParticipant(UserSession)
  │   ├─ Check capacity
  │   ├─ Store in ConcurrentHashMap
  │   └─ Update activity time
  ├─ RoomManager.addUserToRoom()
  │   └─ Track userId → roomId mapping
  ├─ Send JOIN_ROOM confirmation with room users list
  └─ notifyUserJoined(room, userId) 
      └─ Send USER_JOINED to all other participants
```

### 2. Forwarding OFFER SDP

```
Client A:
  {
    "type": "OFFER",
    "userId": "alice",
    "roomId": "room1",
    "targetUserId": "bob",
    "sdp": "v=0\r\n..."
  }
       │
       ├─ WebSocket→SignalingWebSocketServer.onMessage()
       │
       ├─ handleSdpMessage(msg)
       │  ├─ Room room = roomManager.getRoom("room1")
       │  ├─ UserSession bob = room.getParticipant("bob")
       │  └─ bob.sendMessage(serializeMessage(msg))
       │
       └─ Client B receives OFFER
          └─ Prepares ANSWER
          
Client B:
  {
    "type": "ANSWER",
    "userId": "bob",
    "targetUserId": "alice",
    "sdp": "v=0\r\n..."
  }
       │
       └─ Sent back to Alice via same mechanism
```

### 3. ICE Candidate Forwarding (continu)

```
Client A sends:
  {
    "type": "ICE_CANDIDATE",
    "userId": "alice",
    "targetUserId": "bob",
    "candidate": {
      "candidate": "candidate:844740 1 UDP ...",
      "sdpMLineIndex": 0,
      "sdpMid": "audio"
    }
  }
       │
       └─ handleIceCandidate(msg)
           └─ Forwarded to bob immediately
```

## Architecture de concurrence

### Thread Safety
```java
// Collections thread-safe
Map<String, Room> rooms = new ConcurrentHashMap<>()
Map<String, String> userRoomMap = new ConcurrentHashMap<>()
Map<String, UserSession> participants = new ConcurrentHashMap<>()

// Volatile pour visibilité
volatile boolean active
volatile long lastActivityAt
```

### Opérations atomiques
```
1. Ajout d'utilisateur:
   - Lock-free (ConcurrentHashMap)
   - Remove + Add pattern safe
   
2. Room full check:
   - getParticipantCount() returns current snapshot
   - Race condition acceptable (worst: one extra participant)
   
3. Activity update:
   - Volatile write: thread-safe visibility
```

## Gestion des salles

```
Creation:
  notFound → createRoom() → stored in map

Discovery:
  userId → userRoomMap.get(userId) → Room

Cleanup:
  onClose: no participants → deleteRoom()
           (auto-cleanup on last user leave)
           
Inactivity:
  Optional scheduled: cleanupInactiveRooms()
  Removes rooms inactive > timeout
```

## Types de messages et patterns

```
Control Messages:
  JOIN_ROOM      Server decides: create room or reject if full
  LEAVE_ROOM     Remove from room, clean room if empty
  
Signaling Messages:
  OFFER          Server forwards to targetUserId
  ANSWER         Server forwards to targetUserId
  ICE_CANDIDATE  Server forwards to targetUserId
  
Notification Messages:
  USER_JOINED    Sent by server to all room participants
  USER_LEFT      Sent by server to all room participants
  
Status Messages:
  PING/PONG      Heartbeat (optional)
  ROOM_FULL      Sent by server if join rejected
  ERROR          Connection errors, validation failures
```

## Lifecycle d'une session utilisateur

```
1. CONNECTED (WebSocket open)
   │
   ├─ onOpen()
   │  └─ User acknowledged, wait for JOIN_ROOM
   │
   ├─ JOIN_ROOM
   │  └─ Create UserSession, add to Room
   │
   ├─ SIGNALING PHASE
   │  ├─ Exchange OFFER/ANSWER
   │  └─ Exchange ICE candidates
   │
   ├─ P2P ESTABLISHED
   │  └─ Direct communication (no server needed for media)
   │
   ├─ LEAVE_ROOM or disconnect
   │  └─ onClose()
   │
   └─ REMOVED
      └─ Session cleaned up, room deleted if empty
```

## Performance Considerations

### Scalability
```
Per room:
  - 4 participants (configurable)
  - ~10 messages per setup (OFFER, ANSWER, ICE ×N)
  - ~100 bytes per message
  
Total server load (1 room):
  - Memory: ~5KB per session
  - Messages: ~1KB total bandwidth
  - CPU: Minimal JSON parsing + forwarding

Horizontal scaling:
  - Multiple server instances
  - Sticky sessions (user → same server)
  - Central room registry (Redis)
```

### Memory patterns
```
- UserSession: ~500 bytes (WebSocket ref, metadata)
- Room: ~2KB (participants map)
- Message: ~1KB (JSON buffer)

Example: 100 rooms × 4 users = 400 sessions = ~200KB
```

## Error Handling

### Protocol Errors
```
1. Invalid JSON → send ERROR + disconnect
2. Missing required fields → send ERROR response
3. Unknown message type → log warning, ignore
4. Target user not found → no error (just silently skip)
```

### Connection Errors
```
1. onError() → log exception
2. onClose() → cleanup session/room
3. Send to disconnected user → silently fail (isConnected check)
```

## Monitoring Points

### Metrics to track
```
- Rooms created/deleted
- Users connected/disconnected
- Messages forwarded (by type)
- Errors encountered
- Room utilization (avg participants)
- Message latency (optional)
```

### Logs
```
INFO:  User X joined room Y
INFO:  Room Y created with Z max participants
INFO:  User X left room Y
INFO:  Forwarded OFFER from A to B
DEBUG: Forwarded ICE candidate from A to B
WARN:  Room not found for SDP message
ERROR: JSON parsing failed
```

## Sequence Diagram: Multi-party Conference

```
Alice           Server          Bob            Carol
  │                │                │            │
  │  JOIN_ROOM      │                │            │
  ├───────────────>│                │            │
  │  confirm       │                │            │
  │<───────────────┤                │            │
  │                │                │            │
  │  JOIN_ROOM                      │            │
  │<─────────────────────────────────┤            │
  │                │ USER_JOINED(bob)│            │
  │<──────────────────────────────────│            │
  │                │  confirm        │            │
  │                │<─────────────────┤            │
  │                │                 │            │
  │                │                 │  JOIN_ROOM │
  │                │                 ├──────────>│
  │                │ USER_JOINED(carol)          │
  │<──────────────────────────────────────────────┤
  │                │ USER_JOINED(carol)          │
  │                │<──────────────────────────────┤
  │                │ USER_JOINED(alice,bob)       │
  │  confirm       │                 │<──────────┤
  │<──────────────────────────────────────────────┤
  │                │                 │            │
  │  OFFER to bob  │                 │            │
  ├───────────────>│    OFFER to bob │            │
  │                ├──────────────────────────────>│
  │                │                 │            │
  │  ANSWER        │                 ANSWER      │
  │<──────────────────────────────────┤            │
  │                │                 │            │
  │  OFFER to carol│                 │            │
  ├─────────────────────────────────────────────>│
  │                │                 │  OFFER     │
  │                │                 │<──────────┤
  │                │                 │            │
  │  ANSWER        │    ANSWER to carol          │
  │<─────────────────────────────────────────────┤
  │                │                 │            │
  │═══ P2P WebRTC Connections Established ════════│
  │  Audio/Video data flows directly (no server)  │
  │                │                 │            │
```

## Security considerations

```
Current:
  ✓ WebSocket transport (use wss:// in production)
  ✓ JSON parsing with Jackson
  ✓ Null checks on required fields
  
To add:
  – Authentication (JWT tokens)
  – Authorization (room access control)
  – Rate limiting (messages per user/room)
  – Input validation (message size limits)
  – Encryption (optional E2E for SDP)
  – CORS headers
  – Token rotation
  – Room passwords/keys
```

## Testing strategy

```
Unit Tests (current):
  ✓ SignalingMessage serialization
  ✓ UserSession lifecycle
  ✓ Room capacity & participants
  ✓ RoomManager operations
  ✓ Integration scenarios

To add:
  – WebSocket mock integration
  – Concurrent access tests
  – Memory leak tests
  – Performance benchmarks
  – Edge cases (duplicate joins, etc.)
```
