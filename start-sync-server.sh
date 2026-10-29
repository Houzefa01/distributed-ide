#!/bin/bash
# start-sync-server.sh
# Launch the sync-server as a standalone JAR

set -e

# Default configuration
PORT=${SYNC_PORT:-8765}
NODE_ID=${SYNC_NODE_ID:-sync-1}

# Build the JAR if it doesn't exist
if [ ! -f "sync-server/target/sync-server-standalone.jar" ]; then
    echo "Building sync-server..."
    mvn clean package -DskipTests -pl sync-server -am
fi

# Launch the server
echo "Starting Sync Server on port $PORT (nodeId=$NODE_ID)..."
java -Dsync.port=$PORT \
     -Dsync.nodeId=$NODE_ID \
     -jar sync-server/target/sync-server-standalone.jar "$@"
