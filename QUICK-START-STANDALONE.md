# Quick Start - Serveurs Standalone

## Installation et Lancement Rapide

### Prérequis
- Java 21+
- Maven 3.8+

### 1. Compiler les Serveurs

```bash
mvn clean package -DskipTests -pl sync-server,signaling-server,terminal-service -am
```

**Résultat :**
- `sync-server/target/sync-server-standalone.jar` (12 MB)
- `signaling-server/target/signaling-server-standalone.jar` (8 MB)
- `terminal-service/target/terminal-service-standalone.jar` (32 MB)

### 2. Lancer Tous les Serveurs

```bash
./start-all-servers.sh
```

**Ports par défaut :**
- Sync Server: 8765
- Signaling Server: 8082
- Terminal Service: 8766

### 3. Vérifier les Logs

```bash
tail -f logs/*.log
```

### 4. Arrêter les Serveurs

```bash
./stop-all-servers.sh
```

## Lancement Individuel

### Sync Server

```bash
./start-sync-server.sh
```

Avec configuration personnalisée :
```bash
SYNC_PORT=9000 SYNC_NODE_ID=node-2 ./start-sync-server.sh
```

### Signaling Server

```bash
./start-signaling-server.sh
```

Avec configuration personnalisée :
```bash
SIGNALING_PORT=9001 SIGNALING_MAX_PARTICIPANTS=8 ./start-signaling-server.sh
```

### Terminal Service

```bash
./start-terminal-service.sh
```

Avec configuration personnalisée :
```bash
TERMINAL_PORT=9002 TERMINAL_MAX_SESSIONS=100 ./start-terminal-service.sh
```

## Lancement Manuel (Sans Scripts)

### Avec Defaults

```bash
java -jar sync-server/target/sync-server-standalone.jar
java -jar signaling-server/target/signaling-server-standalone.jar
java -jar terminal-service/target/terminal-service-standalone.jar
```

### Avec Arguments

```bash
# Sync Server: port 9000, nodeId "primary"
java -jar sync-server/target/sync-server-standalone.jar 9000 primary

# Signaling Server: port 9001, 8 participants max, timeout 600000ms
java -jar signaling-server/target/signaling-server-standalone.jar 9001 8 600000

# Terminal Service: port 9002, 100 sessions max
java -jar terminal-service/target/terminal-service-standalone.jar 9002 100
```

### Avec Propriétés Système

```bash
# Sync Server
java -Dsync.port=9000 -Dsync.nodeId=primary \
     -jar sync-server/target/sync-server-standalone.jar

# Signaling Server
java -Dsignaling.port=9001 \
     -Dsignaling.room.maxParticipants=8 \
     -jar signaling-server/target/signaling-server-standalone.jar

# Terminal Service
java -Dterminal.port=9002 \
     -Dterminal.maxSessions=100 \
     -jar terminal-service/target/terminal-service-standalone.jar
```

## Vérification

### Processus en Cours d'Exécution

```bash
ps aux | grep "java.*standalone.jar"
```

### Ports Ouverts

```bash
netstat -tlnp | grep -E "(8765|8082|8766)"
```

### Test de Connexion

```bash
curl -I http://localhost:8765  # sync-server
curl -I http://localhost:8082  # signaling-server
curl -I http://localhost:8766  # terminal-service
```

## Configuration

### Variables d'Environnement

#### Sync Server
- `SYNC_PORT` - Port du serveur (défaut: 8765)
- `SYNC_NODE_ID` - ID du nœud (défaut: sync-1)

#### Signaling Server
- `SIGNALING_PORT` - Port du serveur (défaut: 8082)
- `SIGNALING_MAX_PARTICIPANTS` - Max participants par room (défaut: 4)
- `SIGNALING_INACTIVITY_TIMEOUT` - Timeout en ms (défaut: 300000)

#### Terminal Service
- `TERMINAL_PORT` - Port du serveur (défaut: 8766)
- `TERMINAL_MAX_SESSIONS` - Sessions max (défaut: 50)
- `TERMINAL_INACTIVITY_TIMEOUT` - Timeout en secondes (défaut: 300)
- `TERMINAL_CPU_CORES` - CPU cores par défaut (défaut: 2)
- `TERMINAL_MEMORY_MB` - Mémoire par défaut en MB (défaut: 512)
- `TERMINAL_TIMEOUT_SEC` - Timeout par défaut en secondes (défaut: 60)

## Logs

### Logs en Temps Réel

```bash
# Tous les logs
tail -f logs/*.log

# Un serveur spécifique
tail -f logs/sync-server.log
tail -f logs/signaling-server.log
tail -f logs/terminal-service.log
```

### Logs Archivés

Les logs sont sauvegardés dans le répertoire `logs/` :
- `logs/sync-server.log`
- `logs/signaling-server.log`
- `logs/terminal-service.log`

## Troubleshooting

### Port Déjà Utilisé

Si un port est déjà utilisé, changez-le :
```bash
SYNC_PORT=9000 ./start-sync-server.sh
```

### Serveur Ne Démarre Pas

1. Vérifier les logs :
   ```bash
   cat logs/sync-server.log
   ```

2. Vérifier que Java 21+ est installé :
   ```bash
   java -version
   ```

3. Vérifier que le JAR existe :
   ```bash
   ls -lh sync-server/target/sync-server-standalone.jar
   ```

### Serveur Ne S'Arrête Pas

```bash
# Forcer l'arrêt
pkill -9 -f "java.*standalone.jar"

# Ou manuellement
kill -9 $(ps aux | grep '[j]ava.*standalone.jar' | awk '{print $2}')
```

### Mémoire Insuffisante

Augmenter la heap Java :
```bash
java -Xmx2G -jar terminal-service/target/terminal-service-standalone.jar
```

## Documentation Complète

Pour plus de détails, voir :
- **STANDALONE-SERVERS.md** - Guide complet d'utilisation
- **STANDALONE-REPORT.md** - Rapport de synthèse technique

## Architecture

```
distributed-ide/
├── sync-server/
│   ├── src/main/java/.../SyncServerApp.java      # Classe Main
│   ├── pom.xml                                     # Config Maven
│   └── target/sync-server-standalone.jar          # JAR standalone
├── signaling-server/
│   ├── src/main/java/.../SignalingServerApp.java  # Classe Main
│   ├── pom.xml                                     # Config Maven
│   └── target/signaling-server-standalone.jar     # JAR standalone
├── terminal-service/
│   ├── src/main/java/.../TerminalServiceApp.java  # Classe Main
│   ├── pom.xml                                     # Config Maven
│   └── target/terminal-service-standalone.jar     # JAR standalone
├── start-sync-server.sh                            # Script lancement
├── start-signaling-server.sh                       # Script lancement
├── start-terminal-service.sh                       # Script lancement
├── start-all-servers.sh                            # Lancer tous
├── stop-all-servers.sh                             # Arrêter tous
└── logs/                                           # Logs des serveurs
```

## Support

Pour toute question ou problème :
1. Consulter les logs dans `logs/`
2. Vérifier la configuration dans les scripts
3. Lire la documentation complète dans STANDALONE-SERVERS.md

---

**Note :** Les serveurs utilisent WebSocket pour la communication. Assurez-vous que les ports ne sont pas bloqués par un firewall.
