# 📚 Guide Complet - Distributed IDE

## Table des Matières
1. [Introduction](#introduction)
2. [Architecture](#architecture)
3. [Installation et Build](#installation-et-build)
4. [Lancement de l'IDE](#lancement-de-lide)
5. [Fonctionnalités](#fonctionnalités)
6. [Système de Plugins](#système-de-plugins)
7. [API et Extensibilité](#api-et-extensibilité)
8. [Tests](#tests)
9. [Dépannage](#dépannage)

---

## Introduction

**Distributed IDE** est un environnement de développement intégré moderne, inspiré de VSCode, développé en Java avec JavaFX. Il offre des fonctionnalités avancées de collaboration temps réel, un système de plugins extensible, et un terminal intégré avec exécution sécurisée dans Docker.

### Points Forts
- ✅ **Éditeur multi-langages** avec coloration syntaxique
- ✅ **Collaboration temps réel** via CRDT
- ✅ **Terminal intégré** avec Docker sandbox
- ✅ **Système de plugins** dynamique
- ✅ **Git intégré** complet
- ✅ **LSP** pour IntelliSense
- ✅ **Interface moderne** en JavaFX

---

## Architecture

### Modules Principaux

```
distributed-ide/
├── crdt-core/              # CRDT pour édition collaborative
├── sync-server/            # Serveur WebSocket de synchronisation
├── signaling-server/       # Serveur WebRTC signaling
├── terminal-service/       # Terminal + Docker sandbox
├── project-manager/        # Gestion de projets
├── lsp-bridge/             # Bridge LSP
├── plugin-system/          # Système de plugins
└── javafx-client/          # Client JavaFX principal
```

### Architecture Technique

```
┌─────────────────────────────────────────┐
│         JavaFX Client (UI)              │
├─────────────────────────────────────────┤
│  Editor │ Terminal │ Git │ Plugins     │
├─────────────────────────────────────────┤
│      Services Layer                     │
│  FileService │ LSPManager │ GitService  │
└──────────────┬──────────────────────────┘
               │
    ┌──────────┴──────────┐
    │                     │
┌───▼───────┐      ┌─────▼──────┐
│ Sync      │      │ Terminal   │
│ Server    │      │ Service    │
│ (WS)      │      │ (Docker)   │
└───────────┘      └────────────┘
```

---

## Installation et Build

### Prérequis

- **Java 21+** (obligatoire)
- **Maven 3.8+** (obligatoire)
- **Docker** (optionnel, pour terminal sandbox)
- **Git** (recommandé)

### Installation Java 21

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-21-jdk

# macOS (avec Homebrew)
brew install openjdk@21

# Vérifier l'installation
java -version
```

### Build du Projet

```bash
# Build complet avec tests
mvn clean install

# Build sans tests (plus rapide)
mvn clean install -DskipTests

# Build en mode verbeux
mvn clean install -X
```

### Build d'un Module Spécifique

```bash
cd <module-name>
mvn clean package
```

---

## Lancement de l'IDE

### Méthode 1 : Script Quick Start (Recommandé)

```bash
./QUICK_START.sh
```

Ce script :
1. Vérifie Java et Maven
2. Compile le projet
3. Lance tous les serveurs backend
4. Lance le client JavaFX
5. Arrête tout à la fermeture

### Méthode 2 : Lancement Manuel

**Étape 1 : Lancer les serveurs**

```bash
# Terminal 1 : Sync Server
java -jar sync-server/target/sync-server-1.0.0-SNAPSHOT.jar

# Terminal 2 : Signaling Server
java -jar signaling-server/target/signaling-server-1.0.0-SNAPSHOT.jar

# Terminal 3 : Terminal Service
java -jar terminal-service/target/terminal-service-1.0.0-SNAPSHOT.jar
```

**Étape 2 : Lancer le client**

```bash
cd javafx-client
mvn javafx:run
```

### Arrêt de l'IDE

```bash
./stop_ide.sh
```

---

## Fonctionnalités

### 1. Éditeur de Code

**Coloration Syntaxique**
- Support : Java, Python, JavaScript, TypeScript, Go, Rust, C/C++, HTML, CSS, Markdown
- Basé sur RichTextFX
- Thèmes personnalisables

**Fonctionnalités**
- Auto-complétion via LSP
- Recherche/Remplacement (Ctrl+F / Ctrl+H)
- Indentation automatique
- Multi-curseurs
- Split view (horizontal/vertical)

**Raccourcis Clavier**
```
Ctrl+N     : Nouveau fichier
Ctrl+O     : Ouvrir fichier
Ctrl+S     : Sauvegarder
Ctrl+Shift+S : Sauvegarder tout
Ctrl+F     : Rechercher
Ctrl+H     : Remplacer
Ctrl+/     : Commenter ligne
```

### 2. Terminal Intégré

**Caractéristiques**
- Multi-sessions (plusieurs terminals ouverts simultanément)
- Exécution dans Docker sandbox (isolation sécurisée)
- Gestion des ressources (CPU, mémoire, timeout)
- Historique des commandes

**Utilisation**
```bash
# Créer une nouvelle session terminal
Menu : Terminal > New Terminal

# Exécuter une commande
$ echo "Hello World"

# Gestion des ressources
Menu : Terminal > Configure Limits
```

### 3. Gestion de Fichiers

**Arbre de Projet**
- Navigation hiérarchique
- Opérations : Créer, Renommer, Supprimer, Copier, Déplacer
- Filtres et recherche
- Watcher pour auto-refresh

**Opérations Fichiers**
```java
// Nouveau fichier
fileService.createFile(path, content);

// Lire fichier
String content = fileService.readFile(path);

// Écrire fichier
fileService.writeFile(path, content);

// Supprimer fichier
fileService.deleteFile(path);
```

### 4. Git Intégré

**Commandes Supportées**
- Clone, Init
- Add, Commit, Push, Pull
- Branch, Checkout, Merge
- Diff, Log, Status

**Utilisation**
```bash
# Via menu
Git > Clone Repository
Git > Commit Changes
Git > Push

# Via Git Panel (vue gauche)
- Voir changements
- Stager/Unstager fichiers
- Créer commits
- Gérer branches
```

### 5. Collaboration Temps Réel

**CRDT (Conflict-free Replicated Data Types)**
- Édition collaborative sans conflit
- Synchronisation automatique
- Curseurs collaboratifs colorés

**Connexion**
```
1. Menu > Collaboration > Connect to Server
2. Entrer URL du Sync Server (ws://localhost:8081)
3. Rejoindre ou créer un document
4. Éditer collaborativement !
```

**Chat intégré**
```
Panel Collaboration > Chat
- Envoyer messages
- Voir messages des autres utilisateurs
- Timestamps
```

### 6. LSP (Language Server Protocol)

**Langages Supportés**
- Java (via Eclipse JDT LS)
- Python (via Pylance)
- JavaScript/TypeScript (via TypeScript LS)
- Go (via gopls)
- Rust (via rust-analyzer)

**Configuration**
```json
// .ide/lsp-config.json
{
  "servers": {
    "java": {
      "command": "java",
      "args": ["-jar", "/path/to/jdtls.jar"]
    }
  }
}
```

---

## Système de Plugins

### Architecture des Plugins

Le système de plugins offre :
- **Chargement dynamique** depuis JAR
- **ClassLoader isolé** par plugin
- **API riche** pour extensibilité
- **Cycle de vie complet** (LOADED → ENABLED → DISABLED → ERROR)

### Créer un Plugin

**Structure du Plugin**

```
my-plugin/
├── src/main/java/
│   └── com/example/MyPlugin.java
├── src/main/resources/
│   └── META-INF/
│       └── plugin.json
└── pom.xml
```

**Code du Plugin**

```java
package com.example;

import com.distributed_ide.plugin.api.*;

public class MyPlugin implements Plugin {

    private IDEContext context;

    @Override
    public void onEnable(IDEContext context) throws Exception {
        this.context = context;

        // Logger
        context.getLogger().info("My Plugin enabled!");

        // Ajouter un menu
        context.getUIManager().registerMenu(
            MenuRegistration.builder()
                .path("Tools/My Plugin/My Action")
                .action(() -> doSomething())
                .build()
        );

        // Écouter des événements
        context.getEventManager().subscribe("file.opened", event -> {
            context.getLogger().info("File opened: " + event.getData());
        });
    }

    @Override
    public void onDisable() {
        context.getLogger().info("My Plugin disabled!");
    }

    @Override
    public PluginMetadata getMetadata() {
        return new PluginMetadata(
            "my-plugin",
            "My Plugin",
            "1.0.0",
            "A sample plugin",
            "Your Name",
            "com.example.MyPlugin",
            "1.0.0",
            null,
            "https://example.com",
            "MIT"
        );
    }

    private void doSomething() {
        context.getUIManager().showNotification(
            "My Plugin",
            "Action executed!",
            NotificationType.INFO
        );
    }
}
```

**Métadonnées du Plugin** (`META-INF/plugin.json`)

```json
{
  "id": "my-plugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "description": "A sample plugin for Distributed IDE",
  "author": "Your Name",
  "mainClass": "com.example.MyPlugin",
  "minIdeVersion": "1.0.0",
  "website": "https://example.com",
  "license": "MIT"
}
```

**Build du Plugin**

```bash
mvn clean package
# Génère : target/my-plugin-1.0.0.jar
```

**Installer le Plugin**

```bash
# Copier le JAR dans le dossier plugins
cp target/my-plugin-1.0.0.jar ~/.distributed-ide/plugins/

# Ou via l'IDE
Menu > Settings > Plugins > Install from JAR
```

### API des Plugins

#### EditorManager
```java
// Ouvrir un fichier
context.getEditorManager().openFile("/path/to/file.java");

// Obtenir le contenu
String content = context.getEditorManager().getContent(path);

// Modifier le contenu
context.getEditorManager().setContent(path, newContent);
```

#### UIManager
```java
// Ajouter un menu
context.getUIManager().registerMenu(menuRegistration);

// Ajouter une toolbar
context.getUIManager().registerToolbar(toolbarRegistration);

// Afficher une notification
context.getUIManager().showNotification(title, message, type);
```

#### ProjectManager
```java
// Obtenir le projet actif
Optional<Project> project = context.getProjectManager().getActiveProject();

// Lister les fichiers du projet
List<Path> files = context.getProjectManager().listProjectFiles();
```

#### TerminalManager
```java
// Créer une session terminal
String sessionId = context.getTerminalManager().createSession();

// Exécuter une commande
context.getTerminalManager().executeCommand(sessionId, "ls -la");
```

#### EventManager
```java
// S'abonner à un événement
context.getEventManager().subscribe("file.saved", event -> {
    String path = (String) event.getData();
    // Traiter l'événement
});

// Publier un événement
context.getEventManager().publish("my-event", data);
```

---

## Tests

### Exécuter les Tests

```bash
# Tous les tests
mvn test

# Tests d'un module spécifique
cd javafx-client
mvn test

# Tests d'intégration
mvn verify
```

### Tests d'Intégration

Les tests d'intégration (`*IT.java`) testent l'ensemble du système :
- `GlobalIntegrationIT` : Test complet sync + CRDT
- `FileServiceIT` : Test du service de fichiers
- `GitServiceIT` : Test du service Git

---

## Dépannage

### Problème : "Port 8081 already in use"

**Solution** :
```bash
# Trouver le processus
lsof -i :8081

# Tuer le processus
kill -9 <PID>

# Ou utiliser le script de stop
./stop_ide.sh
```

### Problème : "Java version incompatible"

**Solution** :
```bash
# Vérifier la version
java -version

# Installer Java 21
sudo apt install openjdk-21-jdk

# Définir JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

### Problème : "Docker not found"

**Solution** :
```bash
# Installer Docker
sudo apt install docker.io

# Ajouter l'utilisateur au groupe docker
sudo usermod -aG docker $USER

# Redémarrer la session
```

### Problème : "JavaFX application won't start"

**Solution** :
```bash
# Vérifier les dépendances JavaFX
mvn dependency:tree | grep javafx

# Réinstaller
mvn clean install -U
```

### Logs

Consulter les logs pour diagnostic :
```bash
# Logs des serveurs
tail -f logs/sync-server.log
tail -f logs/terminal-service.log

# Logs du client
~/.distributed-ide/logs/client.log
```

---

## Configuration Avancée

### Fichiers de Configuration

```
~/.distributed-ide/
├── config/
│   ├── editor.json       # Configuration éditeur
│   ├── lsp.json          # Configuration LSP
│   ├── terminal.json     # Configuration terminal
│   └── plugins.json      # Configuration plugins
├── plugins/              # Plugins installés
├── themes/               # Thèmes personnalisés
└── logs/                 # Logs applicatifs
```

### Personnalisation des Thèmes

```json
// ~/.distributed-ide/themes/my-theme.json
{
  "name": "My Theme",
  "background": "#1e1e1e",
  "foreground": "#d4d4d4",
  "syntax": {
    "keyword": "#569cd6",
    "string": "#ce9178",
    "comment": "#6a9955"
  }
}
```

---

## Ressources

- **Documentation** : Consulter les README dans chaque module
- **Architecture** : Consulter les fichiers ARCHITECTURE.md
- **Issues** : https://github.com/anthropics/distributed-ide/issues
- **Exemples** : Dossier `plugins/example-plugin/`

---

## Conclusion

**Distributed IDE** est maintenant **opérationnel** et prêt à être utilisé comme un IDE moderne et professionnel. Toutes les fonctionnalités principales ont été implémentées et testées.

**Bon développement ! 🚀**
