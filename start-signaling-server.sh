#!/bin/bash
# start-signaling-server.sh
# Launch the signaling-server as a standalone JAR

set -e

# Default configuration
PORT=${SIGNALING_PORT:-8082}
MAX_PARTICIPANTS=${SIGNALING_MAX_PARTICIPANTS:-4}
INACTIVITY_TIMEOUT=${SIGNALING_INACTIVITY_TIMEOUT:-300000}

# Build the JAR if it doesn't exist
if [ ! -f "signaling-server/target/signaling-server-standalone.jar" ]; then
    echo "Building signaling-server..."
    mvn clean package -DskipTests -pl signaling-server -am
fi

# Launch the server
echo "Starting Signaling Server on port $PORT..."
echo "  Max participants per room: $MAX_PARTICIPANTS"
echo "  Inactivity timeout: ${INACTIVITY_TIMEOUT}ms"
java -Dsignaling.port=$PORT \
     -Dsignaling.room.maxParticipants=$MAX_PARTICIPANTS \
     -Dsignaling.room.inactivityTimeoutMs=$INACTIVITY_TIMEOUT \
     -jar signaling-server/target/signaling-server-standalone.jar "$@"
