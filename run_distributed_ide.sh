#!/bin/bash
# run_distributed_ide.sh
# Script pour compiler et lancer le IDE complet "distributed-ide"

set -e  # Stop si une commande échoue

# 1️⃣ Compile tout le projet
echo "==> Compilation du projet complet..."
mvn clean install

# 2️⃣ Lancer le sync-server
echo "==> Lancement de sync-server..."
cd sync-server
mvn exec:java -Dexec.mainClass="com.distributed_ide.sync.server.SyncServerMain" &
SYNC_PID=$!
cd ..

# 3️⃣ Lancer terminal-service
echo "==> Lancement de terminal-service..."
cd terminal-service
mvn exec:java -Dexec.mainClass="com.distributed_ide.terminal.server.TerminalServiceMain" &
TERMINAL_PID=$!
cd ..

# 4️⃣ Lancer signaling-server (WebRTC, audio/video)
echo "==> Lancement de signaling-server..."
cd signaling-server
mvn exec:java -Dexec.mainClass="com.distributed_ide.signaling.server.SignalingServerMain" &
SIGNAL_PID=$!
cd ..

# 5️⃣ Pause courte pour que les serveurs démarrent
sleep 5

# 6️⃣ Lancer le client JavaFX
echo "==> Lancement du client JavaFX..."
cd javafx-client
mvn javafx:run
cd ..

# 7️⃣ Stop automatique des serveurs lorsque le client ferme
echo "==> Arrêt des serveurs..."
kill $SYNC_PID
kill $TERMINAL_PID
kill $SIGNAL_PID

echo "==> IDE terminé."