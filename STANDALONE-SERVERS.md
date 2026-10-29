# Serveurs Standalone - Guide Complet

Ce document décrit les classes Main et la configuration permettant de lancer les serveurs comme des JARs standalone.

## Modules Concernés

Les trois modules suivants ont été configurés pour être lancés comme des JARs standalone :

1. **sync-server** - Serveur de synchronisation CRDT
2. **signaling-server** - Serveur WebRTC signaling
3. **terminal-service** - Service de terminal distant

## Classes Main

### 1. SyncServerApp

**Fichier :** `/home/hsavatra/distributed-ide/sync-server/src/main/java/com/distributed_ide/sync_server/SyncServerApp.java`

**Fonctionnalités :**
- Lance le serveur WebSocket de synchronisation CRDT
- Port par défaut : 8765 (configurable via `-Dsync.port` ou argument)
- Node ID par défaut : "sync-1" (configurable via `-Dsync.nodeId` ou argument)
- Gère l'arrêt propre via shutdown hook
- Affiche des logs de démarrage

**Configuration :**
```java
// Paramètres système
-Dsync.port=8765
-Dsync.nodeId=sync-1

// Ou via arguments de ligne de commande
java -jar sync-server-standalone.jar [PORT] [NODE_ID]
```

### 2. SignalingServerApp

**Fichier :** `/home/hsavatra/distributed-ide/signaling-server/src/main/java/com/distributed_ide/signaling_server/SignalingServerApp.java`

**Fonctionnalités :**
- Lance le serveur WebSocket de signaling WebRTC
- Port par défaut : 8082 (configurable)
- Max participants par room : 4 (configurable)
- Timeout d'inactivité : 300000ms (5 minutes, configurable)
- Gère les rooms et les participants
- Shutdown hook pour arrêt propre

**Configuration :**
```java
// Paramètres système
-Dsignaling.port=8082
-Dsignaling.room.maxParticipants=4
-Dsignaling.room.inactivityTimeoutMs=300000

// Ou via arguments de ligne de commande
java -jar signaling-server-standalone.jar [PORT] [MAX_PARTICIPANTS] [TIMEOUT_MS]
```

### 3. TerminalServiceApp

**Fichier :** `/home/hsavatra/distributed-ide/terminal-service/src/main/java/com/distributed_ide/terminal_service/TerminalServiceApp.java`

**Fonctionnalités :**
- Lance le serveur WebSocket de terminal
- Port par défaut : 8766 (configurable)
- Gère les sessions terminaux avec Docker
- Configuration des ressources (CPU, mémoire, timeout)
- Shutdown hook qui arrête le serveur ET nettoie les sessions

**Configuration :**
```java
// Paramètres système
-Dterminal.port=8766
-Dterminal.maxSessions=50
-Dterminal.inactivityTimeoutSeconds=300
-Dterminal.defaultCpuCores=2
-Dterminal.defaultMemoryMb=512
-Dterminal.defaultTimeoutSeconds=60

// Ou via arguments de ligne de commande
java -jar terminal-service-standalone.jar [PORT] [MAX_SESSIONS] [TIMEOUT_SEC] [CPU_CORES] [MEMORY_MB] [DEFAULT_TIMEOUT]
```

## Configuration Maven

Chaque module a été configuré avec le **maven-shade-plugin** pour créer des JARs exécutables contenant toutes les dépendances (fat JAR / uber JAR).

### Configuration dans pom.xml

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.1</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>com.distributed_ide.MODULE.MODULEApp</mainClass>
                    </transformer>
                </transformers>
                <filters>
                    <filter>
                        <artifact>*:*</artifact>
                        <excludes>
                            <exclude>META-INF/*.SF</exclude>
                            <exclude>META-INF/*.DSA</exclude>
                            <exclude>META-INF/*.RSA</exclude>
                        </excludes>
                    </filter>
                </filters>
                <finalName>MODULE-standalone</finalName>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Compilation

Pour compiler tous les serveurs standalone :

```bash
mvn clean package -DskipTests -pl sync-server,signaling-server,terminal-service -am
```

Les JARs sont créés dans :
- `sync-server/target/sync-server-standalone.jar` (~12 MB)
- `signaling-server/target/signaling-server-standalone.jar` (~8 MB)
- `terminal-service/target/terminal-service-standalone.jar` (~32 MB)

## Scripts de Lancement

Trois scripts pratiques ont été créés pour lancer les serveurs facilement :

### 1. start-sync-server.sh

```bash
./start-sync-server.sh

# Avec configuration personnalisée
SYNC_PORT=9000 SYNC_NODE_ID=node-2 ./start-sync-server.sh
```

Variables d'environnement :
- `SYNC_PORT` : Port du serveur (défaut : 8765)
- `SYNC_NODE_ID` : ID du nœud (défaut : sync-1)

### 2. start-signaling-server.sh

```bash
./start-signaling-server.sh

# Avec configuration personnalisée
SIGNALING_PORT=9001 SIGNALING_MAX_PARTICIPANTS=8 ./start-signaling-server.sh
```

Variables d'environnement :
- `SIGNALING_PORT` : Port du serveur (défaut : 8082)
- `SIGNALING_MAX_PARTICIPANTS` : Max participants par room (défaut : 4)
- `SIGNALING_INACTIVITY_TIMEOUT` : Timeout en ms (défaut : 300000)

### 3. start-terminal-service.sh

```bash
./start-terminal-service.sh

# Avec configuration personnalisée
TERMINAL_PORT=9002 TERMINAL_MAX_SESSIONS=100 ./start-terminal-service.sh
```

Variables d'environnement :
- `TERMINAL_PORT` : Port du serveur (défaut : 8766)
- `TERMINAL_MAX_SESSIONS` : Sessions max (défaut : 50)
- `TERMINAL_INACTIVITY_TIMEOUT` : Timeout en secondes (défaut : 300)
- `TERMINAL_CPU_CORES` : CPU cores par défaut (défaut : 2)
- `TERMINAL_MEMORY_MB` : Mémoire par défaut en MB (défaut : 512)
- `TERMINAL_TIMEOUT_SEC` : Timeout par défaut en secondes (défaut : 60)

## Lancement Manuel

### Sans les scripts

```bash
# Sync Server
java -jar sync-server/target/sync-server-standalone.jar

# Signaling Server
java -jar signaling-server/target/signaling-server-standalone.jar

# Terminal Service
java -jar terminal-service/target/terminal-service-standalone.jar
```

### Avec arguments

```bash
# Sync Server : port 9000, nodeId "primary"
java -jar sync-server/target/sync-server-standalone.jar 9000 primary

# Signaling Server : port 9001, 8 participants max, timeout 600000ms
java -jar signaling-server/target/signaling-server-standalone.jar 9001 8 600000

# Terminal Service : port 9002, 100 sessions max
java -jar terminal-service/target/terminal-service-standalone.jar 9002 100
```

### Avec propriétés système

```bash
# Sync Server
java -Dsync.port=9000 -Dsync.nodeId=primary \
     -jar sync-server/target/sync-server-standalone.jar

# Signaling Server
java -Dsignaling.port=9001 \
     -Dsignaling.room.maxParticipants=8 \
     -Dsignaling.room.inactivityTimeoutMs=600000 \
     -jar signaling-server/target/signaling-server-standalone.jar

# Terminal Service
java -Dterminal.port=9002 \
     -Dterminal.maxSessions=100 \
     -Dterminal.inactivityTimeoutSeconds=600 \
     -jar terminal-service/target/terminal-service-standalone.jar
```

## Vérification

Pour vérifier que les serveurs fonctionnent :

```bash
# Vérifier les processus
ps aux | grep "java.*standalone.jar"

# Vérifier les ports
netstat -tlnp | grep -E "(8765|8082|8766)"

# Tester la connexion
curl -I http://localhost:8765  # sync-server
curl -I http://localhost:8082  # signaling-server
curl -I http://localhost:8766  # terminal-service
```

## Logs

Les logs sont affichés sur la console via Logback. Pour rediriger les logs vers un fichier :

```bash
# Avec redirection
./start-sync-server.sh > logs/sync-server.log 2>&1 &

# Ou avec tee pour voir ET sauvegarder
./start-sync-server.sh 2>&1 | tee logs/sync-server.log
```

## Architecture des Classes Main

Toutes les classes Main suivent la même structure :

1. **Parsing des arguments** : Via méthodes utilitaires `intArg()`, `longArg()`, `stringArg()`
2. **Configuration** : Lecture des propriétés système ou arguments CLI
3. **Création du serveur** : Instanciation avec la configuration
4. **Shutdown hook** : Pour arrêt propre (fermeture des connexions, cleanup)
5. **Démarrage** : Appel à `server.start()`
6. **Logs** : Affichage des informations de démarrage
7. **Attente** : `CountDownLatch` pour garder le processus actif

## Avantages des JARs Standalone

- **Portabilité** : Un seul fichier JAR contient tout
- **Déploiement simple** : Pas besoin d'installer les dépendances séparément
- **Version consistante** : Toutes les dépendances sont à la version correcte
- **Facilité d'utilisation** : Lancement avec `java -jar`
- **Configuration flexible** : Via arguments, propriétés système ou variables d'env

## Fichiers Modifiés/Créés

### Fichiers Modifiés

1. `/home/hsavatra/distributed-ide/sync-server/pom.xml` - Ajout du maven-shade-plugin
2. `/home/hsavatra/distributed-ide/signaling-server/pom.xml` - Ajout du maven-shade-plugin
3. `/home/hsavatra/distributed-ide/terminal-service/pom.xml` - Ajout du maven-shade-plugin

### Fichiers Créés

1. `/home/hsavatra/distributed-ide/start-sync-server.sh` - Script de lancement sync-server
2. `/home/hsavatra/distributed-ide/start-signaling-server.sh` - Script de lancement signaling-server
3. `/home/hsavatra/distributed-ide/start-terminal-service.sh` - Script de lancement terminal-service
4. `/home/hsavatra/distributed-ide/STANDALONE-SERVERS.md` - Documentation complète

### Fichiers Existants (Déjà Présents)

Les classes Main existaient déjà et étaient déjà bien structurées :
1. `sync-server/src/main/java/com/distributed_ide/sync_server/SyncServerApp.java`
2. `signaling-server/src/main/java/com/distributed_ide/signaling_server/SignalingServerApp.java`
3. `terminal-service/src/main/java/com/distributed_ide/terminal_service/TerminalServiceApp.java`

## Conclusion

Les trois serveurs (sync-server, signaling-server, terminal-service) sont maintenant configurés pour être lancés comme des JARs standalone. Chaque serveur a :

- ✅ Une classe Main fonctionnelle
- ✅ Configuration Maven avec maven-shade-plugin
- ✅ Un JAR exécutable avec toutes les dépendances
- ✅ Un script de lancement pratique
- ✅ Configuration flexible (CLI, propriétés, variables d'env)
- ✅ Gestion propre de l'arrêt (shutdown hooks)
- ✅ Logs de démarrage informatifs
