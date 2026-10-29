# Distributed IDE

IDE de bureau **collaboratif et distribué** en **Java 21 / JavaFX** (projet de mémoire,
ENSET). Plusieurs développeurs éditent les mêmes fichiers en temps réel, sans conflit,
grâce à des CRDT. L'exécution du code se fait dans des conteneurs Docker.

## Architecture (Maven multi-modules)

| Module | Rôle | Technologies clés |
|---|---|---|
| `crdt-core` | Structures CRDT pour l'édition concurrente, jetons JWT | Jackson, JJWT, Guava |
| `sync-server` | Serveur de synchronisation des documents | Netty, Java-WebSocket |
| `signaling-server` | Signalisation et présence des participants | Netty, Java-WebSocket |
| `terminal-service` | Terminaux multi-session isolés dans Docker | docker-java |
| `project-manager` | Gestion des projets et des fichiers | Netty, WebSocket |
| `lsp-bridge` | Passerelle Language Server Protocol (autocomplétion, diagnostics) | LSP4J, Spring Boot WebSocket |
| `plugin-system` | Chargement dynamique de plugins | JavaFX |
| `javafx-client` | Interface de l'IDE : éditeur, Git intégré, thèmes | JavaFX, JGit, jsoup |

Autres dossiers : `example-java-simple/` et `example-python/` (projets de démonstration),
`Class_latex/` et `*.tex` (mémoire et présentation Beamer), fichiers `*.md`
(rapports d'audit et guides).

## Compilation et lancement

Prérequis : JDK 21, Maven 3.9+, Docker (pour le terminal).

```bash
mvn clean install -DskipTests
./start-all-servers.sh     # sync, signaling, terminal, project-manager, lsp-bridge
./START-IDE.sh             # client JavaFX
./stop-all-servers.sh
```

Le support LSP Java demande d'installer Eclipse JDT LS (`SETUP_JDTLS.sh`).

> Les binaires (`dist/`, `windows/`, archive du paquet Windows, `*.jar`) ne sont pas
> versionnés. L'ancien README est conservé dans `README-ORIGINAL.md`.
