# 🏗️ Architecture du Système d'Exécution de Projets

**Date**: 23 février 2026  
**Version**: 1.0  
**Auteur**: Architecture Team

---

## 📑 Table des Matières

1. [Vue d'ensemble](#vue-densemble)
2. [Composants Principaux](#composants-principaux)
3. [Flux de Données](#flux-de-données)
4. [Séquences d'Exécution](#séquences-dexécution)
5. [Gestion des Threads](#gestion-des-threads)
6. [Gestion des Erreurs](#gestion-des-erreurs)
7. [Performance](#performance)
8. [Extensibilité](#extensibilité)

---

## Vue d'ensemble

Le système d'exécution intelligent permet à l'IDE JavaFX de:

- **Détecter** automatiquement le type de projet
- **Charger** les configurations spécifiques (launch.json)
- **Compiler** et **exécuter** des projets sans intervention manuelle
- **Gérer** les processus de façon propre et sécurisée
- **Afficher** les résultats en temps réel

### Principes de Conception

✅ **Pas de blocage UI**: Tout est asynchrone  
✅ **Separation of Concerns**: Logique métier séparée de la UI  
✅ **Cache intelligent**: Évite les scans répétés  
✅ **Gestion propre des ressources**: Aucune fuite processus  
✅ **Logging complet**: Tracabilité des opérations  

---

## Composants Principaux

### 1. ProjectTypeDetector

```
┌──────────────────────────────────────┐
│    ProjectTypeDetector               │
├──────────────────────────────────────┤
│ + detectProjectType(Path)            │
│ + invalidateCache(Path)              │
├──────────────────────────────────────┤
│ - typeCache: Map<String, Type>       │
│ - lastCheckTime: Map<String, Long>   │
└──────────────────────────────────────┘
```

**Responsabilités:**
- Détecter le type de projet (Maven, Java Simple, Python)
- Mettre en cache les résultats (5 secondes)
- Implémenter la logique de priorité

**Algorithme de détection:**

```
1. Vérifier cache
   └─ Si valide, retourner
   
2. Détecter (dans l'ordre de priorité):
   ├─ pom.xml → MAVEN
   ├─ .java + ExJavaConfig/launch.json → JAVA_SIMPLE
   ├─ .py + ExPython/launch.json → PYTHON
   ├─ .java files → JAVA_SIMPLE
   ├─ .py files → PYTHON
   └─ Rien → UNKNOWN
   
3. Cacher le résultat
4. Retourner le type
```

**Cache:**
```java
Map<String, ProjectType> typeCache = new ConcurrentHashMap<>();
// Validité: 5 secondes
// Thread-safe: ConcurrentHashMap
// Invalidation: manuelle ou expiration
```

---

### 2. LaunchConfigLoader

```
┌──────────────────────────────────────────────┐
│   LaunchConfigLoader                         │
├──────────────────────────────────────────────┤
│ + loadJavaLaunchConfig(Path)                 │
│ + loadPythonLaunchConfig(Path)               │
│ - createDefaultJavaConfig()                  │
│ - createDefaultPythonConfig()                │
├──────────────────────────────────────────────┤
│ - objectMapper: ObjectMapper (Jackson)       │
└──────────────────────────────────────────────┘
```

**Responsabilités:**
- Charger les fichiers JSON (ExJavaConfig/launch.json, ExPython/launch.json)
- Valider les configurations
- Fournir des defaults si fichier absent
- Gérer les erreurs gracieusement

**Processus de chargement:**

```
loadJavaLaunchConfig(projectRoot)
  │
  ├─ Chemin: projectRoot/ExJavaConfig/launch.json
  │
  ├─ Fichier existe?
  │  ├─ NON: Log warning, retourner DEFAULT
  │  └─ OUI: Charger JSON
  │
  ├─ Parser JSON avec Jackson
  │
  ├─ Valider:
  │  ├─ mainClass non-null et non-vide
  │  └─ Sinon: retourner DEFAULT
  │
  └─ Retourner config (validée)
```

**Configuration par défaut (Java):**
```json
{
  "mainClass": "Main",
  "vmArgs": "-Xmx512m",
  "programArgs": "",
  "classPathExtra": "./lib/*"
}
```

**Configuration par défaut (Python):**
```json
{
  "mainFile": "main.py",
  "args": "",
  "pythonPath": "python3"
}
```

---

### 3. JavaSimpleExecutor

```
┌──────────────────────────────────────────┐
│   JavaSimpleExecutor                     │
├──────────────────────────────────────────┤
│ + execute(Path, JavaLaunchConfig)        │
│ - compileProject(Path)                   │
│ - executeCompiledProject(Path, Config)   │
├──────────────────────────────────────────┤
│ - Uses: javac, java (ProcessBuilder)     │
└──────────────────────────────────────────┘
```

**Flux d'exécution:**

```
execute(projectRoot, config)
  │
  ├─ PHASE 1: Compilation
  │  ├─ Scaner src/ pour .java files
  │  ├─ Créer bin/ si nécessaire
  │  ├─ Construire commande:
  │  │   javac -d bin -cp lib/* src/**/*.java
  │  ├─ Exécuter compilation
  │  └─ Retourner erreur si exitCode != 0
  │
  └─ PHASE 2: Exécution
     ├─ Construire classpath:
     │   bin:lib/*
     │
     ├─ Construire commande:
     │   java -cp bin:lib/* [vmArgs] MainClass [args]
     │
     ├─ Créer ProcessBuilder
     ├─ Définir workingDirectory
     └─ Start et retourner Process
```

**Commande javac complète:**
```bash
javac -d bin -cp lib/* src/Hello.java src/Utils.java ...
```

**Commande java complète:**
```bash
java -Xmx512m -Xms256m -cp bin:lib/* com.example.Main arg1 arg2
```

---

### 4. MavenExecutor

```
┌────────────────────────────────────┐
│   MavenExecutor                    │
├────────────────────────────────────┤
│ + execute(Path)                    │
│ + executeJar(Path)                 │
├────────────────────────────────────┤
│ - Uses: mvn (ProcessBuilder)       │
└────────────────────────────────────┘
```

**Stratégie d'exécution:**

```
Method 1: mvn exec:java (PRÉFÉRÉ)
  ├─ Compile automatiquement si nécessaire
  ├─ Exécute directement la classe principale
  └─ Commande: mvn exec:java -DskipTests=true

Method 2: mvn package + java -jar (FALLBACK)
  ├─ Compile et package: mvn clean package
  ├─ Chercher JAR dans target/
  └─ Exécuter: java -jar target/app.jar
```

---

### 5. PythonExecutor

```
┌────────────────────────────────────┐
│   PythonExecutor                   │
├────────────────────────────────────┤
│ + execute(Path, PythonLaunchConfig)│
├────────────────────────────────────┤
│ - Uses: python3 (ProcessBuilder)   │
└────────────────────────────────────┘
```

**Flux d'exécution:**

```
execute(projectRoot, config)
  │
  ├─ Valider: mainFile existe?
  │  └─ NON: Lever IOException
  │
  ├─ Construire commande:
  │   python3 main.py arg1 arg2
  │
  ├─ Créer ProcessBuilder
  ├─ Définir workingDirectory
  └─ Start et retourner Process
```

---

### 6. ProjectExecutionManager (Orchestrateur)

```
┌───────────────────────────────────────────────────────────┐
│          ProjectExecutionManager                          │
├───────────────────────────────────────────────────────────┤
│ + executeAsync(Path, Consumer, Consumer, Consumer)        │
│ + stop()                                                  │
│ + shutdown()                                              │
│ + invalidateCache(Path)                                   │
├───────────────────────────────────────────────────────────┤
│ Services:                                                 │
│ - projectTypeDetector: ProjectTypeDetector                │
│ - launchConfigLoader: LaunchConfigLoader                  │
│ - javaSimpleExecutor: JavaSimpleExecutor                  │
│ - mavenExecutor: MavenExecutor                            │
│ - pythonExecutor: PythonExecutor                          │
│                                                           │
│ ThreadPools:                                              │
│ - executionThreadPool: ExecutorService (2 threads)       │
│ - streamReaderThreadPool: ExecutorService (4 threads)    │
│                                                           │
│ Processus:                                                │
│ - currentProcess: volatile Process                        │
└───────────────────────────────────────────────────────────┘
```

**Architecture d'exécution:**

```
┌─────────────────────────────────────────────────────────────┐
│                   Thread Pool d'Exécution                    │
│                  (executionThreadPool - 2)                  │
└────────────────┬────────────────────────────────────────────┘
                 │
         ┌───────▼──────────┐
         │ executeAsync()   │
         └───────┬──────────┘
                 │
         ┌───────▼────────────────────────┐
         │ 1. Détecter type de projet     │
         │ 2. Charger configuration       │
         │ 3. Préparer et lancer Process  │
         │ 4. Attendre exit code          │
         └───────┬────────────────────────┘
                 │
         ┌───────┴──────────────────────────────┐
         │                                      │
      ┌──▼──┐ (stdout)              (stderr) ┌─▼──┐
      │Thread│ streamReaderThreadPool  │Thread│
      │ 2    │         (4 threads)     │ 3    │
      └──┬──┘                          └─┬───┘
         │ readStream()                  │ readStream()
         │                               │
         └──────────┬────────────────────┘
                    │
            ┌───────▼────────────────┐
            │ Callbacks:             │
            │ - outputConsumer       │
            │ - errorConsumer        │
            │ - completionCallback   │
            └───────┬────────────────┘
                    │
            ┌───────▼────────────────┐
            │ Affichage dans UI      │
            │ (Platform.runLater)    │
            └────────────────────────┘
```

---

## Flux de Données

### Séquence Complète d'Exécution

```
UTILISATEUR
    │
    └── Clique "Run"
         │
         └──► MainLayoutController.handleProjectRun()
              │
              ├── Get projectRoot
              │
              └──► projectExecutionManager.executeAsync(
                   projectRoot,
                   outputConsumer,
                   errorConsumer,
                   completionCallback
              )
                   │
                   ├── [THREAD: executionThreadPool]
                   │   │
                   │   ├─► ProjectTypeDetector.detectProjectType()
                   │   │    ├─ Check cache
                   │   │    ├─ Scan filesystem
                   │   │    └─ Return ProjectType
                   │   │
                   │   ├─► LaunchConfigLoader.load*Config()
                   │   │    ├─ Load JSON ou default
                   │   │    └─ Validate config
                   │   │
                   │   ├─► Executor.execute()
                   │   │    ├─ Compiler (si Java)
                   │   │    └─ Start ProcessBuilder
                   │   │
                   │   ├── Process.waitFor()
                   │   │
                   │   └── Process.destroy() [cleanup]
                   │
                   ├── [THREAD: streamReaderThreadPool-1]
                   │   └─► readStream(stdout)
                   │        └─► outputConsumer.accept(line)
                   │
                   ├── [THREAD: streamReaderThreadPool-2]
                   │   └─► readStream(stderr)
                   │        └─► errorConsumer.accept(line)
                   │
                   └── [MAIN THREAD]
                       └─► Platform.runLater()
                            └─► UI.updateOutput()
```

---

## Séquences d'Exécution

### Cas 1: Projet Maven

```
PROJECT DETECTION:
  Maven project detected (pom.xml found)
  
EXECUTION:
  ├─ mvn exec:java
  ├─ Maven compile automatiquement si nécessaire
  └─ Main class exécutée via maven-exec-plugin
  
RESULT:
  └─ stdout/stderr affichés en temps réel
```

### Cas 2: Projet Java Simple

```
PROJECT DETECTION:
  Java Simple project detected (.java + ExJavaConfig/launch.json)
  
LOAD CONFIG:
  ├─ loadJavaLaunchConfig()
  └─ Get mainClass, vmArgs, programArgs, classPathExtra
  
COMPILATION:
  ├─ Scan src/ pour .java files
  ├─ javac -d bin -cp lib/* src/**/*.java
  └─ Erreur si compilation échoue (exitCode != 0)
  
EXECUTION:
  ├─ java -cp bin:lib/* [vmArgs] MainClass [args]
  └─ Afficher output en temps réel
  
CLEANUP:
  └─ Process.destroy()
```

### Cas 3: Projet Python

```
PROJECT DETECTION:
  Python project detected (.py + ExPython/launch.json)
  
LOAD CONFIG:
  ├─ loadPythonLaunchConfig()
  └─ Get mainFile, args, pythonPath
  
VALIDATION:
  ├─ Check si mainFile existe
  └─ Erreur si fichier manquant
  
EXECUTION:
  ├─ python3 main.py [args]
  └─ Afficher output en temps réel
  
CLEANUP:
  └─ Process.destroy()
```

---

## Gestion des Threads

### Thread Pools

```
┌────────────────────────────────────────────────┐
│         ProjectExecutionManager                │
├────────────────────────────────────────────────┤
│                                                │
│  executionThreadPool                           │
│  ├─ Fixed Pool: 2 threads                      │
│  ├─ Nom: project-executor-%d                   │
│  ├─ Daemon: true (n'empêche pas shutdown)      │
│  └─ Usage: Lancer les exécutions de projets    │
│                                                │
│  streamReaderThreadPool                        │
│  ├─ Fixed Pool: 4 threads                      │
│  ├─ Nom: stream-reader-%d                      │
│  ├─ Daemon: true                               │
│  └─ Usage: Lire stdout/stderr en parallèle     │
│                                                │
└────────────────────────────────────────────────┘
```

### Modèle de Concurrence

```
Problem: Reading stdout and stderr sequentially can deadlock
  - Si stderr plein (pipe buffer) et stdout bloqué
  - Thread bloqué sur stdout ne peut pas lire stderr

Solution: Lecture parallèle (2 threads différents)
  - Thread 1: Lit stdout en continu
  - Thread 2: Lit stderr en continu
  - Aucun risque de deadlock
  - Future.get() attend les deux

Code:
  Future<?> stdoutFuture = pool.submit(() -> readStream(stdout));
  Future<?> stderrFuture = pool.submit(() -> readStream(stderr));
  
  stdoutFuture.get();  // Attendre fin stdout
  stderrFuture.get();  // Attendre fin stderr
```

### Lifecycle

```
┌─────────────────────────────────┐
│  IDE Start                       │
│  ProjectExecutionManager init    │
│  ├─ Create thread pools ✓       │
│  └─ Pools ready                 │
└──────────────┬──────────────────┘
               │
               ├── executeAsync() #1
               │   ├─ Alloc 2 threads (1+1 pour stream)
               │   └─ Complete + threads return to pool
               │
               ├── executeAsync() #2
               │   ├─ Reuse threads
               │   └─ Complete
               │
               └── IDE Close
                   shutdown()
                   ├─ executorService.shutdownNow()
                   ├─ streamReaderService.shutdownNow()
                   └─ All threads terminated ✓
```

---

## Gestion des Erreurs

### Hiérarchie des Erreurs

```
ProjectionExecutionManager.executeAsync()
│
├─ IOException (LaunchConfigLoader)
│  ├─ File not found → Use default config
│  └─ JSON parse error → Log + default config
│
├─ IOException (Executor)
│  ├─ Compilation failed → Error consumer + cleanup
│  ├─ File not found → Error consumer + cleanup
│  └─ Process creation failed → Error consumer + cleanup
│
├─ InterruptedException
│  ├─ User interrupt
│  ├─ Thread.currentThread().interrupt()
│  └─ Cleanup + rethrow
│
└─ Unexpected Exception
   ├─ Log error
   ├─ Error consumer message
   └─ Cleanup
```

### Error Messages

```
✗ Unable to detect project type.
✗ Failed to start process
✗ Execution error: <message>
⚠ Execution interrupted
⚠ Stream reading error
```

### Cleanup (finally bloc)

```java
finally {
    if (currentProcess != null) {
        try {
            currentProcess.destroy();
        } catch (Exception e) {
            LOG.warn("Error destroying process", e);
        }
        currentProcess = null;
    }
}
```

**Garanties:**
- Process toujours destroy() (même en exception)
- currentProcess remis à null
- Aucune fuite processus

---

## Performance

### Optimisations

#### 1. Cache du Type de Projet

```
Sans cache:
  - Every run: filesystem scan
  - Impact: ~100-500ms par scan
  
Avec cache:
  - First run: filesystem scan (~100-500ms)
  - [5 sec window]: return cached (~1ms)
  - After 5 sec: re-scan si run à nouveau
  
Bénéfice:
  - Pour 10 runs en 5 sec: 9 * 100-500ms saved!
```

#### 2. Lecture Parallèle des Flux

```
Séquentiel (ancien):
  ├─ Read stdout (peut prendre 1s)
  └─ Read stderr (après stdout)
    Total: ~2s

Parallèle (nouveau):
  ├─ Read stdout (1 thread, 1s)
  └─ Read stderr (1 thread différent, 1s)
    Total: ~1s (50% faster!)
```

#### 3. Daemon Threads

```
Impact: 
- IDE peut fermer sans attendre threads asynchrones
- Pas de blogage du shutdown
- Processus toujours nettoyés (destroy() dans finally)
```

### Profil de Performance

```
Scenario: Execute Maven project

Phase 1: Type Detection
  ├─ Cache hit: 1ms
  └─ Cache miss: 100-500ms

Phase 2: Load Config
  ├─ Cache miss: 10-50ms
  └─ Cache hit: 5-10ms

Phase 3: Execute
  ├─ Maven compilation: variable (5s-2m)
  ├─ Maven exec:java: variable (2s-30s)
  └─ Stream reading: parallel (~stream duration)

Total: Depends on project size

Typical:
  ├─ Java Simple (small): 1-5 seconds
  ├─ Maven (medium): 10-30 seconds
  └─ Python (small): 1-3 seconds
```

---

## Extensibilité

### Ajouter un Nouveau Type de Projet

**Exemple: NodeJS**

```java
// 1. Ajouter enum
public enum ProjectType {
    MAVEN,
    JAVA_SIMPLE,
    PYTHON,
    NODEJS,  // <- NEW
    UNKNOWN
}

// 2. Créer NodeJsLaunchConfig
public class NodeJsLaunchConfig {
    private String mainFile;
    private String args;
    private String nodePath = "node";
    // getters/setters
}

// 3. Créer NodeJsExecutor
public class NodeJsExecutor {
    public Process execute(Path projectRoot, NodeJsLaunchConfig config) {
        List<String> cmd = new ArrayList<>();
        cmd.add(config.getNodePath());
        cmd.add(config.getMainFile());
        // args...
        return new ProcessBuilder(cmd).start();
    }
}

// 4. Mettre à jour ProjectTypeDetector
private ProjectType performDetection(Path projectRoot) {
    // ... existing checks ...
    
    // NEW: Check for Node.js
    if (hasFile(projectRoot, "package.json") && hasPythonFiles(projectRoot)) {
        return ProjectType.NODEJS;
    }
}

// 5. Mettre à jour ProjectExecutionManager
private Process prepareAndExecute(...) {
    switch (projectType) {
        // ... existing cases ...
        case NODEJS:
            NodeJsLaunchConfig config = loader.loadNodeJsConfig(projectRoot);
            return nodeJsExecutor.execute(projectRoot, config);
    }
}

// 6. Mettre à jour LaunchConfigLoader
public NodeJsLaunchConfig loadNodeJsConfig(Path projectRoot) {
    Path configPath = projectRoot.resolve("ExNodeJs/launch.json");
    // Load with defaults...
}
```

---

## Intégration avec l'IDE

### MainLayoutController

```java
// En haut de la classe
private final ProjectExecutionManager projectExecutionManager = new ProjectExecutionManager();

// handleProjectRun()
private void handleProjectRun() {
    Path root = layout.getProjectTreeView().getRootPath();
    
    projectExecutionManager.executeAsync(
        root,
        line -> Platform.runLater(() -> layout.getOutputView().appendOutput("Run", line)),
        line -> Platform.runLater(() -> layout.getOutputView().appendOutput("Run", line)),
        exitCode -> Platform.runLater(() -> {
            // Update UI with result
        })
    );
}

// handleStopCode()
private void handleStopCode() {
    projectExecutionManager.stop();
}

// Shutdown (à la fermeture de l'IDE)
public void shutdown() {
    projectExecutionManager.shutdown();
}
```

---

## Logging

### Configuration

```
En utilisant SLF4J + Logback (ou autre backend)

Loggers:
  ├─ ProjectTypeDetector: DEBUG logs
  ├─ LaunchConfigLoader: DEBUG logs (sauf errors)
  ├─ Executors: INFO logs
  └─ ProjectExecutionManager: INFO logs
```

### Exemples de Logs

```
[DEBUG] Using cached project type MAVEN for /home/user/my-project
[DEBUG] Loaded Java config: mainClass=com.example.Main
[INFO] Detected project type: JAVA_SIMPLE for /home/user/project
[ERROR] Compilation failed with exit code: 1
[WARN] Java launch.json not found at ..., using defaults
```

---

## Testing

### Unit Tests

```java
// ProjectTypeDetectorTest
@Test
public void testMavenDetection() {
    // Create temp dir with pom.xml
    ProjectType type = detector.detectProjectType(mavenProjectPath);
    Assert.assertEquals(ProjectType.MAVEN, type);
}

@Test
public void testCacheValidity() {
    // First call: no cache
    // Second call (< 5s): cache hit
    // Third call (> 5s): new scan
}

// JavaSimpleExecutorTest
@Test
public void testCompilation() {
    // Create Java project
    // Execute
    // Verify bin/ has .class files
}

// ProjectExecutionManagerTest
@Test
public void testAsyncExecution() {
    // Call executeAsync
    // Capture output
    // Verify callbacks called
}
```

### Integration Tests

```
Test complete workflows:
  1. Maven project: detect → load → execute
  2. Java Simple: detect → compile → execute
  3. Python: detect → execute
  4. Error handling: invalid config → graceful failure
```

---

## Conclusion

Ce système fournit une exécution intelligent et robuste de projets dans l'IDE JavaFX, avec:

✅ Détection automatique  
✅ Configuration flexible (launch.json)  
✅ Pas de blocage UI (async/threads)  
✅ Gestion propre des ressources  
✅ Performance optimisée  
✅ Extensibilité future  

**Version complète et production-ready.**
