# PROJECT MANAGER - Architecture Guide

**Document Version**: 1.0.0  
**Last Updated**: 2026-02-21  
**Scope**: project-manager module design and implementation details

---

## System Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    ProjectManager                           │
│              (Central Orchestration)                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │     Project                    ProjectStructure     │ │
│  │  (Configuration)             (File Organization)   │ │
│  │                                                    │ │
│  │  - projectId                 - DirectoryNode      │ │
│  │  - name, type                - directoryCache     │ │
│  │  - status                    - fileIndex          │ │
│  │  - ownerUserId               - Tree traversal    │ │
│  │  - totalFiles                - Path resolution   │ │
│  │  - metadata                                      │ │
│  └──────────────────────────────────────────────────┘ │
│                         │                               │
│                         ├─────────────┬─────────────┐   │
│                         ↓             ↓             ↓   │
│  ┌──────────────┐  ┌──────────┐  ┌────────────┐        │
│  │ ProjectFile  │  │ Project  │  │ProjectMeta │        │
│  │ (Per-file)   │  │Metadata  │  │data        │        │
│  ├──────────────┤  ├──────────┤  ├────────────┤        │
│  │ fileId       │  │tags      │  │contributor │        │
│  │ relativePath │  │settings  │  │repository  │        │
│  │ fileType     │  │license   │  │version     │        │
│  │ crdtDocId ◄──┼──┼──◄ MAPS ►───┤ language   │        │
│  │ status       │  │version   │  │            │        │
│  │ metadata     │  └──────────┘  └────────────┘        │
│  └──────────────┘                                      │
│         │                                              │
│         └─────────────────────────┐                   │
│                                   ↓                   │
│                        ┌──────────────────┐           │
│                        │  CRDT Document   │           │
│                        │   (External)     │           │
│                        └──────────────────┘           │
│                                                       │
└─────────────────────────────────────────────────────────────┘
```

---

## Key Component Descriptions

### ProjectManager (Orchestrator)

**Role**: Central coordinator for all project operations

**Responsibilities**:
- Project lifecycle (create, retrieve, delete)
- User project association and limits
- Cross-project queries (search, statistics)
- Integration with sub-components

**Thread Safety**:
- Uses ConcurrentHashMap for all shared state
- Per-user limit enforcement (atomic check)
- No explicit synchronization needed

**Data Structure**:
```java
ConcurrentHashMap<projectId, Project> projects
ConcurrentHashMap<projectId, ProjectStructure> structures
ConcurrentHashMap<projectId, ProjectMetadata> metadataMap
ConcurrentHashMap<userId, Set<projectId>> userProjects
```

### Project (Configuration Container)

**Role**: Model for a single project with metadata

**Responsibilities**:
- Store project properties (name, type, status)
- Track file count
- Manage ownership
- Store project-level metadata

**State Transitions**:
```
CREATED → OPEN → CLOSED
           ├──→ ARCHIVED
           └──→ ARCHIVED
```

**Timestamps**:
- `createdAt`: Project creation
- `modifiedAt`: Last property change
- `closedAt`: Project closure (if applicable)

### ProjectStructure (File Tree Management)

**Role**: Efficient hierarchical file organization

**Tree Structure** (example):
```
root (/)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── App.java (fileId: f1, crdtId: crdt1)
│   │   │   └── Main.java (fileId: f2, crdtId: crdt2)
│   │   └── resources/
│   │       └── config.xml (fileId: f3, crdtId: crdt3)
│   └── test/
│       └── AppTest.java (fileId: f4, crdtId: crdt4)
├── README.md (fileId: f5, crdtId: crdt5)
└── pom.xml (fileId: f6, crdtId: crdt6)
```

**Caching Strategy**:
- `directoryCache`: Maps normalized paths to DirectoryNode objects
- `fileIndex`: Maps file IDs to ProjectFile objects
- Path Normalization: All paths stored as "{dir}/" format

**Path Normalization Examples**:
```
Input: "src/main/java"  → Normalized: "/src/main/java/"
Input: "src/main/java/" → Normalized: "/src/main/java/"
Input: "/src/main/java" → Normalized: "/src/main/java/"
Input: "" → Normalized: "/"
```

### ProjectFile (Individual File Model)

**Role**: Represents a file with CRDT document mapping

**Attributes**:
- **Identity**: fileId (unique), projectId (scoped unique combined)
- **Location**: relativePath, pathComponents (dir + filename)
- **Type**: FileType enum (Java, Python, etc.)
- **Collaboration**: crdtDocumentId (1:1 mapping)
- **Status**: File lifecycle (CREATED, MODIFIED, etc.)
- **Content**: sizeInBytes, contentHash for integrity
- **History**: createdAt, modifiedAt, lastModifiedBy

**File Type Detection**:
```java
.java → JAVA
.py → PYTHON
.html → HTML
.css → CSS
.js → JAVASCRIPT
.xml → XML
.json → JSON
.md → MARKDOWN
.yaml → YAML
(others) → OTHER
```

### ProjectMetadata (Metadata Repository)

**Role**: Centralized metadata and collaboration tracking

**Components**:

1. **Contributors**: LinkedHashMap<userId, Contributor>
   - Track who contributed
   - Count commits per contributor
   - Preserve contribution order

2. **Tags**: Set<String> (lowercase normalized)
   - Project classification
   - Category labels
   - Case-insensitive searches

3. **Settings**: Map<String, String>
   - Build tool (maven, gradle, npm)
   - Java version
   - Encoding (UTF-8, etc.)
   - Custom settings

4. **Project Info**:
   - Repository URL
   - Primary language
   - License
   - Version (semantic versioning)
   - Last activity timestamp

---

## Data Flow Sequences

### Sequence 1: Create Project with File

```
User: createProject("proj-1", "App", JAVA, "user-1")
 │
 ├─→ ProjectManager: Create + validate limits
 │    ├─→ Check: user-1 projects < maxPerUser ✓
 │    ├─→ Create: new Project("proj-1", ...)
 │    ├─→ Create: new ProjectStructure("proj-1")
 │    ├─→ Create: new ProjectMetadata("proj-1")
 │    ├─→ Cache entries
 │    └─→ Associate: userProjects["user-1"] += "proj-1"
 │
 └─→ Return: Project object
```

### Sequence 2: Add File with CRDT Document

```
User: addFileToProject("proj-1", file, "crdt-doc-1")
 │
 ├─→ ProjectManager:
 │    ├─→ Get: Project("proj-1")
 │    ├─→ Set: file.crdtDocumentId = "crdt-doc-1"
 │    ├─→ Call: structure.addFile(file)
 │    │    ├─→ Extract: directory = "src/main/java"
 │    │    ├─→ Normalize: "/src/main/java/"
 │    │    ├─→ Call: ensureDirectoryExists("/src/main/java/")
 │    │    │    ├─→ Split: ["", "src", "main", "java", ""]
 │    │    │    ├─→ Create: "/src/" → cache
 │    │    │    ├─→ Create: "/src/main/" → cache
 │    │    │    ├─→ Create: "/src/main/java/" → cache
 │    │    │    └─→ Link: subDirectory relationships
 │    │    ├─→ Add: file to "/src/main/java/"
 │    │    └─→ Index: fileIndex["file-1"] = file
 │    └─→ Update: project.totalFiles++
 │
 └─→ Result: File added, CRDT mapped, structure maintained
```

### Sequence 3: Search for Files

```
User: searchFiles("App")
 │
 ├─→ ProjectManager:
 │    ├─→ For each projectId in projects:
 │    │    ├─→ Get: ProjectStructure
 │    │    ├─→ For each fileId in structure.fileIndex:
 │    │    │    ├─→ Get: ProjectFile
 │    │    │    ├─→ Check: file.getFileName().contains("App")
 │    │    │    └─→ Add to results if match
 │    └─→ Return: List of matching files
 │
 └─→ Time: O(total_files) linear scan
```

---

## State Management

### Project State Machine

```
         ┌─────────────┐
         │   CREATED   │
         └──────┬──────┘
                │
         ┌──────▼──────┐
         │    OPEN     │
         └──┬────────┬─┘
            │        │
       ┌────▼┐   ┌───▼───┐
       │CLOSED   │ARCHIVED│
       └────────────────┘
```

### File State Machine

```
    ┌────────────┐
    │  CREATED   │
    └──────┬─────┘
           │
    ┌──────▼──────┐
    │  MODIFIED   │
    └──┬────────┬─┘
       │        │
       └────┬───┘
            │
       ┌────▼─────┐
       │  SYNCED   │
       └────┬──────┘
            │
       ┌────▼──────┐
       │  DELETED   │
       └────────────┘
```

---

## Thread Safety Design

### ConcurrentHashMap Usage

**Why ConcurrentHashMap instead of synchronized?**
1. Better concurrent performance
2. Lock striping (multiple locks, not global)
3. Built-in iterator consistency
4. Atomic operations for compute operations

**Collections Used**:
```java
projects: ConcurrentHashMap<String, Project>
structures: ConcurrentHashMap<String, ProjectStructure>
metadataMap: ConcurrentHashMap<String, ProjectMetadata>
userProjects: ConcurrentHashMap<String, Set<String>>
  └─ Inner Set: ConcurrentHashMap.newKeySet()
```

### Per-User Project Limit

**Thread-Safe Check**:
```java
Set<String> userProj = userProjects.computeIfAbsent(userId, k → newKeySet());
if (userProj.size() >= maxProjectsPerUser) {
    throw IllegalStateException;
}
```

**Behavior**:
- Atomic check + insertion to prevent race
- ConcurrentHashMap.newKeySet() provides thread-safe set
- No explicit locks needed

---

## Memory Efficiency

### Directory Path Caching

**Problem**: Traversing deep paths repeatedly is expensive

**Solution**: Cache all created paths
```
Cache hit: O(1) lookup
Cache miss: O(d) creation (d = depth), then O(1) future lookups
```

**Memory Trade-Off**: Store full path strings
- Benefit: Super-fast lookups
- Cost: Memory for path strings (minimal for typical projects)

### File Index Optimization

**Problem**: Finding a file by ID requires tree traversal

**Solution**: Maintain fileIndex HashMap
```
Direct lookup: O(1) by file ID
vs. Tree search: O(n) where n = total files
```

---

## Integration Architecture

### With CRDT Core

**File-CRDT Mapping**:
- ProjectFile.crdtDocumentId → External CRDT Document ID
- 1:1 relationship: One file per CRDT document
- Set at file creation time
- Enables per-file collaborative editing

**Workflow**:
```
User A edits file → ProjectFile fetches crdtDocId
     ↓
Send to CRDT engine with document ID
     ↓
CRDT generates operation
     ↓
Broadcast to all users (via Sync Server)
     ↓
User B's client receives edit + applies to file
```

### With Sync Server

**Document Scoping**:
- ProjectManager tracks which files (documents) exist
- SyncServer synchronizes those documents
- File removal triggers sync cleanup

**Batching**:
- Per-project file organization enables batch operations
- SyncServer can optimize batch syncs per-project

### With Terminal Service

**Code Execution Context**:
- Terminal session references project
- Can access ProjectStructure for file context
- Execute code on specific files
- Store execution results with file

### With Signaling Server

**Collaboration Coordination**:
- Project defines collaboration scope
- Users in same project room for signaling
- Real-time presence per-project
- Coordination for concurrent editing

---

## Error Handling

### Project Creation Errors

```
User limit exceeded:
  → IllegalStateException("User has reached maximum projects limit")
  
Duplicate project ID:
  → IllegalArgumentException("Project already exists")
  
Invalid parameters:
  → NullPointerException (precondition checks)
```

### File Operations

```
Project not found:
  → IllegalArgumentException("Project not found")
  
Invalid path:
  → Handled by path normalization (flexible)
  
File not found:
  → returns null (caller checks)
```

---

## Performance Considerations

### Operation Complexities

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| createProject | O(1) | Hash map insertion + limit check |
| addFile | O(d) | d = directory depth for path creation |
| getFile | O(1) | Direct hash map lookup by ID |
| removeFile | O(d) | d = directory depth for cleanup |
| getFilesByType | O(n) | n = total files in project |
| searchFiles | O(total) | Scan all files across projects |
| getFilesInDirectory | O(m) | m = files in that directory |
| getUserProjects | O(u) | u = user's project count |

### Optimization Opportunities

1. **File Type Filtering**: Currently scans all files
   - Could maintain FileType → Files index
   - Trade-off: Memory vs. search speed

2. **File Search**: Currently linear scan
   - Could use Trie for prefix search
   - Trade-off: Memory vs. search speed

3. **Project Statistics**: Recomputed on request
   - Could cache and invalidate on changes
   - Trade-off: Memory vs. computation

---

## Extensibility Points

### 1. Custom File Types
```java
// Extend ProjectFile.FileType enum
HTML, CSS, JAVASCRIPT, // existing
XML, JSON, MARKDOWN,   // existing
RUST, GO, CPP,         // extensions
```

### 2. Additional Metadata
```java
// Add to ProjectMetadata
- buildToolConfig
- cicdConfiguration
- deploymentTargets
- environmentVariables
```

### 3. File Versioning
```java
// Track file version history
- versions: List<FileVersion>
- currentVersion: int
- rollback capabilities
```

### 4. Project Templates
```java
// Pre-configured project structures
- JavaMavenTemplate
- PythonFlaskTemplate
- ReactTemplate
```

---

## Security Considerations

### User Isolation
- Projects scoped to owner
- Other modules implement access control
- No cross-owner file access in this module

### Path Traversal Prevention
- Relative paths only (no ".." allowed)
- Paths must be within project
- `isInDirectory()` validates containment

### Project Limits
- Per-user project limits prevent abuse
- Configurable thresholds
- Enforced atomically

---

## Testing Strategy

### Unit Test Coverage (67 tests)

**By Component**:
- ProjectFile: 9 tests (creation, metadata, CRDT mapping)
- Project: 11 tests (lifecycle, properties, status)
- ProjectStructure: 12 tests (tree operations, queries)
- ProjectMetadata: 16 tests (contributors, tags, settings)
- ProjectManager: 19 tests (CRUD, limits, integration)

**Coverage Areas**:
- Happy path operations
- Boundary conditions (empty lists, single items)
- Error conditions (not found, invalid operations)
- Multi-user scenarios
- Concurrent operations (by nature of current design)

---

**Document Version**: 1.0.0  
**Last Updated**: 2026-02-21  
**Status**: COMPLETE ✅
