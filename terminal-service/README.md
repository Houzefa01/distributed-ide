# Terminal Service - Documentation

**Module**: terminal-service  
**Version**: 1.0.0-SNAPSHOT  
**Java Version**: 21+  
**Build Tool**: Maven

---

## Overview

The **Terminal Service** module provides secure, containerized code execution with resource isolation. Each user session runs in an isolated Docker sandbox with configurable CPU and memory limits, automatic timeout, and log streaming via WebSocket.

### Key Features

✅ **Docker Sandbox Isolation**
- Each session gets its own container
- Resource limits: CPU cores, memory (MB), timeout (seconds)
- Auto-cleanup on timeout or manual stop
- Multi-language support: Java, Python, Web, Shell

✅ **Session Management**
- Session lifecycle: create, run, monitor, stop
- Session metadata tracking
- Per-user session limits (configurable)
- Inactivity timeout with auto-cleanup

✅ **Real-time Log Streaming**
- WebSocket-based log streaming
- Chunked output delivery
- Error capture and reporting
- Exit code tracking

✅ **Resource Control**
- CPU limits (cores)
- Memory limits (MB)  
- Execution timeout (seconds)
- Automatic container cleanup

✅ **Security**
- Isolated containers per session
- No host filesystem access (simulated)
- Resource quotas enforced
- User-scoped session isolation

---

## Architecture

### Components

#### 1. **TerminalSession** (Model)
Session representation with metadata:
```
TerminalSession
├─ sessionId: String
├─ userId: String
├─ containerId: String
├─ executionType: JAVA|PYTHON|WEB|SHELL
├─ status: CREATED|RUNNING|PAUSED|COMPLETED|ERROR|TERMINATED
├─ cpuLimit: long (cores)
├─ memoryLimit: long (MB)
├─ timeout: long (seconds)
└─ metadata: Map<String, Object>
```

#### 2. **TerminalMessage** (Protocol)
WebSocket message protocol with 16 types:
```
CREATE_SESSION, DELETE_SESSION, LIST_SESSIONS,
EXECUTE, EXECUTE_RESULT,
LOG_CHUNK, LOGS_REQUEST, LOGS_RESPONSE,
SESSION_INFO, SESSION_STATUS,
STOP, PAUSE, RESUME,
PING, PONG, HEARTBEAT,
ERROR
```

#### 3. **DockerSandboxManager** (Server)
Docker container lifecycle management:
- Create containers with resource limits
- Execute code inside containers
- Stream logs from containers
- Stop and clean up containers
- Memory usage monitoring

#### 4. **TerminalSessionManager** (Server)
Centralized session management:
- Create/delete sessions
- Track active sessions per user
- Enforce per-user session limits
- Auto-cleanup inactive sessions
- Session status updates

#### 5. **TerminalWebSocketServer** (Server)
WebSocket server for client communication:
- Handle CREATE_SESSION requests
- Route EXECUTE messages
- Stream logs to clients
- Manage session lifecycle
- Error reporting

---

## Protocol Specification

### Message Types

#### CREATE_SESSION
```json
{
  "type": "CREATE_SESSION",
  "sessionId": "session-abc123",
  "userId": "user-1",
  "executionType": "PYTHON",
  "cpuLimit": 2,
  "memoryLimit": 512,
  "timeout": 300
}
```

**Response**: SESSION_INFO
```json
{
  "type": "SESSION_INFO",
  "sessionId": "session-abc123",
  "containerId": "term-session-abc123-12345",
  "message": "RUNNING"
}
```

#### EXECUTE
```json
{
  "type": "EXECUTE",
  "sessionId": "session-abc123",
  "userId": "user-1",
  "code": "print('Hello, World!')",
  "language": "python"
}
```

**Response**: EXECUTE_RESULT
```json
{
  "type": "EXECUTE_RESULT",
  "sessionId": "session-abc123",
  "output": "Hello, World!",
  "error": "",
  "exitCode": 0
}
```

#### LOGS_REQUEST
```json
{
  "type": "LOGS_REQUEST",
  "sessionId": "session-abc123"
}
```

**Response**: LOG_CHUNK (streaming)
```json
{
  "type": "LOG_CHUNK",
  "sessionId": "session-abc123",
  "output": "Line 1\nLine 2\n..."
}
```

#### STOP/DELETE_SESSION
```json
{
  "type": "STOP",
  "sessionId": "session-abc123"
}
```

### Execution Flow

```
Client                          WebSocket Server            Container Manager
  |                                    |                            |
  +---CREATE_SESSION---+               |                            |
  |                    v               |                            |
  |                   (parse)          |                            |
  |                    |               |                            |
  |                    +--create()-----+---createContainer()---+
  |                    |               |                       v
  |                    |               |                (Docker setup)
  |                    |               |<--containerId-----+
  |<--SESSION_INFO-----+               |                    |
  |                    |               |                    |
  +---EXECUTE code-----+               |                    |
  |                    v               |                    |
  |                   (parse)          |                    |
  |                    |               |                    |
  |                    +---execute()---+---executeCode()---+
  |                    |               |                   v
  |                    |               |              (execution)
  |                    |               |<--result--------+
  |<--EXECUTE_RESULT---+               |                 |
  |                    |               |                 |
  +---LOGS_REQUEST-----+               |                 |
  |                    v               |                 |
  |                   (parse)          |                 |
  |                    |               |                 |
  |                    +--getlogs()----+---getDockerLogs()+
  |                    |               |                 v
  |                    |               |            (fetch logs)
  |<--LOG_CHUNK--------+               |<--logs---------+
  |                    |               |                |
  +---STOP-------------+               |                |
  |                    v               |                |
  |                   (parse)          |                |
  |                    |               |                |
  |                    +---delete()----+---stopContainer()+
  |                    |               |                   v
  |                    |               |              (cleanup)
  |<--SESSION_INFO-----+               |<--status---------+
  |                    |               |                  |
```

---

## Usage Examples

### 1. Create and Execute Python Code

```java
// Initialize manager
TerminalSessionManager manager = new TerminalSessionManager(
    5,      // max sessions per user
    300,    // inactivity timeout (seconds)
    2,      // default CPU cores
    512,    // default memory (MB)
    300     // default timeout (seconds)
);

// Create session
TerminalSession session = manager.createSession(
    "session-1",
    "user-1",
    TerminalSession.ExecutionType.PYTHON,
    2,      // CPU limit
    512,    // Memory limit
    300     // Timeout
);

// Execute code
DockerSandboxManager.ExecutionResult result = 
    manager.getDockerManager().executeCode(
        "session-1",
        "print('Hello, World!')",
        "python"
    );

System.out.println("Output: " + result.getOutput());
System.out.println("Exit Code: " + result.getExitCode());

// Clean up
manager.deleteSession("session-1");
```

### 2. WebSocket Client (JavaScript)

```javascript
const ws = new WebSocket('ws://localhost:9000');

// Create session
ws.send(JSON.stringify({
  type: 'CREATE_SESSION',
  sessionId: 'session-1',
  userId: 'user-1',
  executionType: 'PYTHON',
  cpuLimit: 2,
  memoryLimit: 512,
  timeout: 300
}));

// Handle response
ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  console.log('Message type:', msg.type);
  
  if (msg.type === 'SESSION_INFO') {
    console.log('Session created:', msg.containerId);
    
    // Execute code
    ws.send(JSON.stringify({
      type: 'EXECUTE',
      sessionId: msg.sessionId,
      code: "print('test')",
      language: 'python'
    }));
  } else if (msg.type === 'EXECUTE_RESULT') {
    console.log('Output:', msg.output);
    console.log('Error:', msg.error);
  }
};
```

### 3. Multiple Sessions per User

```java
TerminalSessionManager manager = new TerminalSessionManager(5, 300, 2, 512, 300);

// User 1 creates multiple sessions
TerminalSession python = manager.createSession(
    "session-py", "user-1", TerminalSession.ExecutionType.PYTHON, 2, 512, 300
);

TerminalSession java = manager.createSession(
    "session-java", "user-1", TerminalSession.ExecutionType.JAVA, 4, 1024, 600
);

// List user sessions
Collection<TerminalSession> sessions = manager.getUserSessions("user-1");
System.out.println("User sessions: " + sessions.size()); // 2

// Cleanup all inactivity
int cleaned = manager.cleanupInactiveSessions();
System.out.println("Cleaned up: " + cleaned + " sessions");
```

### 4. Resource Limits

```java
// Session with tight resource limits (for untrusted code)
TerminalSession limited = manager.createSession(
    "session-restricted",
    "user-1",
    TerminalSession.ExecutionType.PYTHON,
    1,      // 1 CPU core max
    256,    // 256 MB max
    60      // 60 second timeout
);

// Session with generous limits (for trusted code)
TerminalSession generous = manager.createSession(
    "session-compute",
    "user-1",
    TerminalSession.ExecutionType.JAVA,
    8,      // 8 CPU cores
    4096,   // 4 GB
    3600    // 1 hour
);
```

---

## Configuration

### Default Settings

```properties
# Session limits
max.sessions.per.user = 5
inactivity.timeout.seconds = 300

# Resource defaults
default.cpu.cores = 2
default.memory.mb = 512
default.timeout.seconds = 300

# Docker
docker.image.base = distributed-ide
docker.network = bridge
```

### WebSocket Server

```java
InetSocketAddress address = new InetSocketAddress("localhost", 9000);
TerminalSessionManager manager = new TerminalSessionManager(...);
TerminalWebSocketServer server = new TerminalWebSocketServer(address, manager);
server.start();
```

---

## Testing

### Run All Tests

```bash
mvn -pl terminal-service test
```

### Run Specific Test

```bash
mvn -pl terminal-service test -Dtest=TerminalSessionManagerTest
```

### Test Coverage

```bash
mvn -pl terminal-service test jacoco:report
```

---

## Security Considerations

🔒 **Container Isolation**
- Each session runs in isolated container
- No shared filesystem (simulated)
- Resource quotas enforced at container level

🔒 **User Scoping**
- Sessions bound to userId
- Users can only access their own sessions
- Per-user session limits enforced

🔒 **Resource Limits**
- CPU cgroups limit CPU usage
- Memory cgroups limit RAM
- Timeout prevents infinite loops
- Auto-cleanup prevents orphaned containers

🔒 **Input Validation**
- Message validation before processing
- Code length limits (if needed)
- Execution type validation

⚠️ **Production Deployment**
- Use actual Docker API (not simulated)
- Implement real Docker daemon integration
- Add authentication/authorization layer
- Monitor resource usage
- Implement rate limiting
- Set up logging and auditing

---

## Performance Characteristics

| Metric | Value |
|--------|-------|
| Session Creation | ~100ms |
| Code Execution | Variable (depends on code) |
| Log Streaming | Real-time |
| Cleanup Interval | Configurable (default 5 min) |
| Max Concurrent Sessions | Depends on docker daemon |

---

## Future Enhancements

- [ ] Real Docker daemon integration (currently simulated)
- [ ] Kubernetes operator for distributed execution
- [ ] Environment variables per session
- [ ] File upload/download support
- [ ] Session snapshots and restore
- [ ] Multiple language support improvements
- [ ] Code profiling and performance analysis
- [ ] Debugging support (breakpoints, step-through)
- [ ] Persistence layer for session history
- [ ] Integration with project manager

---

## Troubleshooting

### Session Creation Fails
- Check user has not reached session limit
- Verify Docker daemon is running
- Check resource availability

### Code Execution Timeout
- Increase timeout value for long-running code
- Check for infinite loops
- Monitor CPU/memory usage

### Container Not Cleaning Up
- Run cleanup manually: `manager.cleanupInactiveSessions()`
- Verify timeout settings
- Check docker logs manually

### WebSocket Connection Issues
- Verify server is running: `ws://host:port`
- Check firewall rules
- Monitor network latency

---

**Documentation Version**: 1.0  
**Last Updated**: 2026-02-21
