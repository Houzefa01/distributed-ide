# LSP Bridge

Pont WebSocket entre le backend et des serveurs LSP externes (JSON-RPC 2.0).

## Fonctionnalités

- **Démarrage et gestion de processus LSP** via `ProcessBuilder`
- **Support** : Eclipse JDT (Java), Pyright (Python), TypeScript Language Server
- **Communication** : JSON-RPC 2.0 sur stdio (format LSP Content-Length)
- **Transmission** : diagnostics en temps réel, autocomplétion, requêtes LSP
- **Intégration sync-server** (optionnelle) :
  - Envoi de `DOCUMENT_JOIN` / `DOCUMENT_LEAVE` à l’ouverture/fermeture de document
  - Réception des `BATCH_OPERATIONS` / `OPERATION` : réplique CRDT locale → texte → envoi `textDocument/didChange` au LSP (async)
  - Les diagnostics renvoyés par le LSP (`textDocument/publishDiagnostics`) sont transmis au client JavaFX via WebSocket
- **Logging** : SLF4J
- **Gestion d’erreurs** : fermeture propre des processus et sessions

## Utilisation

1. Démarrer l’application : `mvn spring-boot:run` (depuis le module) ou lancer le JAR.
2. Se connecter en WebSocket à `ws://localhost:8081/lsp`.
3. Envoyer un message d’init :  
   `{"method":"init","params":{"language":"java","rootUri":"file:///chemin/workspace"}}`  
   Langages : `java`, `python`, `typescript` (ou `py`, `ts`).
4. Ensuite envoyer les messages JSON-RPC LSP (initialize, textDocument/didOpen, textDocument/completion, etc.) ; les réponses et notifications (ex. `textDocument/publishDiagnostics`) sont renvoyées sur la même connexion.

## Configuration

- `server.port` : port HTTP/WebSocket (défaut : 8081).
- `lsp.bridge.sync-server-url` : URL WebSocket du sync-server (ex. `ws://localhost:8765`). Si renseigné : le bridge s’abonne aux documents (DOCUMENT_JOIN), reçoit les opérations CRDT, applique sur une réplique locale, envoie le contenu au LSP (didChange) et transmet les diagnostics au client. Vide = désactivé.
- **JDT (Java)** : variable d’environnement `JDTLS_HOME` ou commande `jdtls` dans le PATH.
- **Pyright** : `npx pyright-langserver` par défaut ; `-Dlsp.bridge.pyright.npx=false` pour utiliser la commande `pyright-langserver` directement.
- **TypeScript** : `npx typescript-language-server --stdio`.

## Structure des packages

- `config` : types de serveurs, configuration processus, propriétés Spring
- `transport` : encadrement LSP (Content-Length) lecture/écriture
- `process` : gestion du cycle de vie des processus LSP
- `bridge` : session de pont (réécriture d’ids, dispatch clients)
- `websocket` : handler et configuration WebSocket Spring
- `sync` : adaptateur optionnel vers le sync-server

## Tests

`mvn test` exécute les tests unitaires (transport, config, process manager, registry, sync adapter).
