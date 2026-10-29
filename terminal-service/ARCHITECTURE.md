# Terminal Service - Architecture Documentation

**Module**: terminal-service  
**Purpose**: Secure Docker-based code execution with isolation and resource limits  
**Protocol**: WebSocket JSON messages

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Distributed IDE - Terminal Service               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │              WebSocket Protocol Layer                        │  │
│  │  (TerminalWebSocketServer)                                  │  │
│  │  - Connection management                                    │  │
│  │  - Message parsing & routing                                │  │
│  │  - Error handling                                           │  │
│  └──────────────────┬───────────────────────────────────────────┘  │
│                     │                                              │
│  ┌──────────────────▼───────────────────────────────────────────┐  │
│  │         Terminal Session Manager Layer                       │  │
│  │  (TerminalSessionManager)                                    │  │
│  │  - Session lifecycle (create, get, delete)                 │  │
│  │  - Per-user session tracking                               │  │
│  │  - Session limits enforcement                              │  │
│  │  - Auto-cleanup of inactive sessions                       │  │
│  │  - Status updates & metadata                               │  │
│  └──────────────────┬───────────────────────────────────────────┘  │
│                     │                                              │
│  ┌──────────────────▼───────────────────────────────────────────┐  │
│  │           Docker Sandbox Manager Layer                       │  │
│  │  (DockerSandboxManager)                                      │  │
│  │  - Container creation with resource limits                 │  │
│  │  - Code execution inside containers                        │  │
│  │  - Log retrieval from containers                           │  │
│  │  - Container lifecycle management                          │  │
│  │  - Memory/CPU monitoring                                   │  │
│  └──────────────────┬───────────────────────────────────────────┘  │
│                     │                                              │
│  ┌──────────────────▼───────────────────────────────────────────┐  │
│  │               Docker Container Layer                         │  │
│  │  - Isolated Python container                               │  │
│  │  - Isolated Java container                                 │  │
│  │  - Isolated Web container                                  │  │
│  │  - Isolated Shell container                                │  │
│  │  Each with: CPU limit, Memory limit, Timeout               │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Component Interactions

### 1. Session Creation Flow

```
User Start
  │
  ▼
CreateSession WebSocket Message
  │
  ├─ Validate message
  ├─ Check user session limits
  │
  ▼
TerminalSessionManager.createSession()
  │
  ├─ Use defaults if needed
  │
  ▼
DockerSandboxManager.createContainer()
  │
  ├─ Select image based on execution type
  ├─ Build docker run command with limits
  │   └─ --cpus <limit>
  │   └─ --memory <limitMB>
  │   └─ timeout <seconds>
  │
  ▼
Create TerminalSession object
  │
  ├─ sessionId, userId, containerId
  ├─ executionType (JAVA|PYTHON|WEB|SHELL)
  ├─ resource limits
  ├─ status = RUNNING
  │
  ▼
Store in SessionMap
  │
  ├─ sessions: sessionId -> TerminalSession
  ├─ userSessions: userId -> {sessionId -> TerminalSession}
  │
  ▼
Send SESSION_INFO response
  │
  └─ Return containerId, status
```

### 2. Code Execution Flow

```
User Sends EXECUTE Message
  │
  ├─ sessionId, userId, code, language
  │
  ▼
TerminalWebSocketServer.handleExecute()
  │
  ├─ Lookup session by sessionId
  │
  ▼
DockerSandboxManager.executeCode()
  │
  ├─ Get containerId for session
  │
  ▼
Build execution command
  │
  ├─ Docker exec command
  ├─ Inject code into container
  ├─ Capture stdout/stderr
  │
  ▼
Execute inside container
  │
  ├─ Language interpreter runs code
  ├─ Output captured in real-time
  ├─ Errors captured separately
  ├─ Exit code recorded
  │
  ▼
Return ExecutionResult
  │
  ├─ output: String
  ├─ error: String
  ├─ exitCode: int
  │
  ▼
Send EXECUTE_RESULT message
  │
  ├─ Update session status to COMPLETED
  └─ Return result to client
```

### 3. Log Streaming Flow

```
User Sends LOGS_REQUEST
  │
  ├─ sessionId
  │
  ▼
TerminalWebSocketServer.handleLogsRequest()
  │
  ├─ Lookup session
  │
  ▼
DockerSandboxManager.getContainerLogs()
  │
  ├─ Get containerId
  │
  ▼
Execute "docker logs" command
  │
  ├─ Fetch stdout + stderr
  ├─ Chunk large logs
  │
  ▼
For each chunk:
  │
  ├─ Create LOG_CHUNK message
  ├─ Send to client via WebSocket
  │
  ├─ (repeat for all chunks)
  │
  ▼
Client receives log stream
```

### 4. Session Cleanup Flow

```
Automatic Cleanup Timer (every 30s)
  │
  ▼
TerminalSessionManager.cleanupInactiveSessions()
  │
  ├─ For each session:
  │  │
  │  ├─ Check inactivity duration
  │  │  └─ If > timeout (default 300s)
  │  │      └─ Delete session
  │  │
  │  ├─ Check if timed out
  │  │  └─ If elapsed > timeout
  │  │      └─ Delete session
  │  │
  │  ├─ Call deleteSession()
  │  │
  │  └─ Log cleanup event
  │
  ▼
For each session to cleanup:
  │
  ├─ DockerSandboxManager.stopContainer()
  │  │
  │  ├─ Get containerId
  │  ├─ Execute "docker stop <id>"
  │  ├─ Execute "docker rm <id>"
  │  │
  │  └─ (container stopped and removed)
  │
  ├─ Remove from sessionMap
  ├─ Remove from userSessions map
  │
  └─ Log success/error
```

---

## Data Structures

### TerminalSession

```java
class TerminalSession {
    String sessionId;              // Unique identifier
    String userId;                 // Owner of session
    String containerId;            // Docker container ID
    ExecutionType executionType;   // JAVA|PYTHON|WEB|SHELL
    Instant createdAt;             // Creation timestamp
    long lastActivityAt;           // Last activity timestamp (volatile)
    SessionStatus status;          // CREATED|RUNNING|PAUSED|COMPLETED|ERROR|TERMINATED
    long cpuLimitInCores;         // CPU resource limit
    long memoryLimitInMB;         // Memory resource limit
    long timeoutInSeconds;         // Execution timeout
    Map<String, Object> metadata;  // Arbitrary metadata
}
```

### TerminalMessage

```java
class TerminalMessage {
    MessageType type;              // Message type enum (16 types)
    String sessionId;              // Session reference
    String userId;                 // User reference
    ExecutionType executionType;   // For CREATE_SESSION
    String code;                   // For EXECUTE
    String language;               // For EXECUTE
    Long cpuLimit;                 // For CREATE_SESSION
    Long memoryLimit;              // For CREATE_SESSION
    Long timeout;                  // For CREATE_SESSION
    String output;                 // For LOG_CHUNK, EXECUTE_RESULT
    String error;                  // For ERROR, EXECUTE_RESULT
    Integer exitCode;              // For EXECUTE_RESULT
    long timestamp;                // Message timestamp
    String message;                // Generic message field
    String containerId;            // Container reference
}
```

### ExecutionResult

```java
class ExecutionResult {
    String output;                 // Standard output
    String error;                  // Standard error
    int exitCode;                  // Process exit code
}
```

---

## State Management

### Session States

```
    ┌─ CREATED ─────────┐
    │                   │
    │                   ▼
    │              RUNNING ◄── PAUSED
    │                   │         ▲
    │                   │         │
    │   ┌───────────────┴──┬──────┘
    │   │                  │
    │   ▼                  ▼
    │ ERROR            COMPLETED
    │   │                  │
    │   └──────┬───────────┘
    │          │
    │          ▼
    │      TERMINATED
    │
    └─────────────────────────────────┘
```

**Transitions**:
- CREATED → RUNNING: After container startup
- RUNNING → PAUSED: On pause command (optional)
- PAUSED → RUNNING: On resume command
- RUNNING/PAUSED → COMPLETED: After execution finishes
- Any → ERROR: On error condition
- Any → TERMINATED: On stop/timeout/cleanup

---

## Resource Management

### Docker Resource Limits

Each container is created with limits:

```bash
docker run \
  --cpus <cpuLimit>              # CPU cores (e.g., 2.0, 0.5)
  --memory <memoryLimitMB>m      # Memory limit (e.g., 512m, 1g)
  --ulimit nproc=1000            # Process limit
  --read-only                    # Read-only filesystem (optional)
  --network none                 # No network (optional)
  <image>
```

### Memory Tracking

```
User Code Running in Container
  │
  ├─ Allocates memory
  │
  ▼
Linux cgroups memory controller
  │
  ├─ Tracks memory usage
  ├─ Enforces limit in memoryLimitMB
  │
  ▼
If limit exceeded:
  │
  ├─ Process receives OOM signal
  ├─ Container stops
  ├─ Exit code 137 (OOM)
  │
  ▼
Session marked ERROR
  │
  └─ User notified
```

### CPU Scheduling

```
User Code Running in Container
  │
  ├─ Uses CPU
  │
  ▼
Linux cgroups CPU controller
  │
  ├─ Tracks CPU usage
  ├─ Enforces --cpus limit
  │
  ▼
CPU usage capped at limit
  │
  ├─ Other processes get CPU time
  ├─ Fair share scheduling
  │
  └─ No hard limit (throttling instead)
```

---

## Thread Safety

### Concurrent Collections

```java
Map<String, TerminalSession> sessions;              // ConcurrentHashMap
Map<String, Map<String, TerminalSession>> userSessions; // ConcurrentHashMap
Map<String, String> sessionContainers;              // ConcurrentHashMap
```

### Volatile Fields

```java
volatile long lastActivityAt;   // Activity timestamp
volatile SessionStatus status;  // Session status
```

All modifications to session state are atomic operations.

---

## Error Handling

### Error Flow

```
Error occurs in execution
  │
  ├─ Catch exception
  │
  ▼
Build ERROR message
  │
  ├─ type: ERROR
  ├─ sessionId: <id>
  ├─ error: <description>
  │
  ▼
Send to client via WebSocket
  │
  └─ Client handles error
```

### Common Errors

| Error | Cause | Recovery |
|-------|-------|----------|
| Container not found | Invalid sessionId | Verify sessionId |
| Maximum sessions reached | User has too many sessions | Delete unused sessions |
| Container creation failed | Docker daemon issue | Check Docker status |
| Code execution timeout | Code took too long | Increase timeout or optimize code |
| Memory limit exceeded | Code used too much RAM | Increase memory limit |
| Container crashed | Code caused segfault | Debug code |

---

## Sequence Diagrams

### Session Creation and Execution

```
Client              WebSocket Server      SessionManager        DockerManager
  │                      │                      │                     │
  ├─CREATE_SESSION──────>│                      │                     │
  │                      │                      │                     │
  │                      ├─createSession()─────>│                     │
  │                      │                      │                     │
  │                      │                      ├─createContainer()──>│
  │                      │                      │                     │
  │                      │                      │     docker run      │
  │                      │                      │<─containerId────────┤
  │                      │                      │                     │
  │                      │<─TerminalSession ────┤                     │
  │                      │                      │                     │
  │<─SESSION_INFO────────┤                      │                     │
  │                      │                      │                     │
  ├─EXECUTE──────────────>│                      │                     │
  │                      │                      │                     │
  │                      ├─executeCode()───────>│                     │
  │                      │                      │                     │
  │                      │                      ├─executeCode()──────>│
  │                      │                      │                     │
  │                      │                      │  docker exec        │
  │                      │                      │<─ExecutionResult────┤
  │                      │                      │                     │
  │                      │<─ExecutionResult─────┤                     │
  │                      │                      │                     │
  │<─EXECUTE_RESULT──────┤                      │                     │
  │                      │                      │                     │
  ├─STOP─────────────────>│                     │                     │
  │                      │                      │                     │
  │                      ├─deleteSession()─────>│                     │
  │                      │                      │                     │
  │                      │                      ├─stopContainer()────>│
  │                      │                      │                     │
  │                      │                      │  docker stop/rm     │
  │                      │                      │<─success────────────┤
  │                      │                      │                     │
  │<─SESSION_INFO────────┤                      │                     │
  │ (TERMINATED)         │                      │                     │
```

### Cleanup Flow

```
CleanupTimer            SessionManager         DockerManager
      │                      │                      │
      ├─cleanupInactiveS..──>│                      │
      │                      │                      │
      │           ┌───────────────────────────┐   │
      │           │ For each session:         │   │
      │           │ - Check inactivity        │   │
      │           │ - Check timeout           │   │
      │           └───────────────────────────┘   │
      │                      │                     │
      │           ┌─────────────────────────────┐ │
      │           │ For inactive/timed-out:    │ │
      │           │ - Call deleteSession()     │ │
      │           └─────────────────────────────┘ │
      │                      │                     │
      │                      ├─stopContainer()────>│
      │                      │                     │
      │                      │   docker stop/rm    │
      │                      │<─success────────────┤
      │                      │                     │
      │    (removed from maps)                    │
      │                      │                     │
      │<─int cleanup count───┤                     │
      │                      │                     │
```

---

## Performance Considerations

### Latency Breakdown

| Operation | Typical | Max |
|-----------|---------|-----|
| Container creation | 100ms | 500ms |
| Code execution | Variable | timeout |
| Log retrieval | 50ms | 200ms |
| Session lookup | <1ms | - |
| Message parsing | <1ms | - |

### Scalability Limits

- **Memory**: 1000 containers × 512MB = 512GB RAM
- **CPU**: Depends on code complexity
- **Connections**: WebSocket server scales to thousands
- **Sessions per user**: Default 5, configurable

### Optimization Strategies

1. **Container Pool**: Pre-create containers for common types
2. **Lazy Load**: Load logs on demand, not all at once
3. **Batch Operations**: Group cleanup operations
4. **Cache**: Cache session lookups (if needed)

---

## Security Architecture

```
┌─────────────────────────────────────────┐
│    Untrusted User Code                  │
└─────────────────────────────┬───────────┘
                              │
                              ▼
              ┌───────────────────────────┐
              │  Docker Container         │
              │  - Isolated filesystem    │
              │  - No host access         │
              │  - Resource limits:       │
              │    - CPU: cgroups         │
              │    - Memory: cgroups      │
              │  - Timeout: signal        │
              │  - Network: isolated      │
              └───────────────┬───────────┘
                              │
                              ▼
              ┌───────────────────────────┐
              │  Container Runtime        │
              │  - No privileged access   │
              │  - Dropped capabilities   │
              │  - Read-only root (opt)   │
              └───────────────┬───────────┘
                              │
                              ▼
              ┌───────────────────────────┐
              │  Host Kernel              │
              │  - Seccomp filters        │
              │  - AppArmor profile       │
              │  - SELinux context        │
              └───────────────────────────┘
```

---

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────┐
│              Terminal Service Instance                  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  WebSocket Server Port: 9000                           │
│  ├─ Listens for client connections                    │
│  ├─ Routes messages to handlers                       │
│  └─ Streams responses back to clients                 │
│                                                         │
│  SessionManager                                        │
│  ├─ Tracks N sessions (default max: 5/user)          │
│  ├─ Enforces limits                                   │
│  └─ Triggers cleanup                                  │
│                                                         │
│  DockerManager                                         │
│  ├─ Manages N Docker containers                       │
│  ├─ Executes code                                     │
│  └─ Monitors resources                                │
│                                                         │
│  Docker Daemon                                         │
│  ├─ Runs containers                                   │
│  ├─ Enforces limits                                   │
│  └─ Reports status                                    │
│                                                         │
└─────────────────────────────────────────────────────────┘
     ▲                                           ▲
     │                                           │
   Clients                              Docker Host
   (WebSocket)                          Resources
```

---

**Architecture Version**: 1.0  
**Last Updated**: 2026-02-21
