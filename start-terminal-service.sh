#!/bin/bash
# start-terminal-service.sh
# Launch the terminal-service as a standalone JAR

set -e

# Default configuration
PORT=${TERMINAL_PORT:-8766}
MAX_SESSIONS=${TERMINAL_MAX_SESSIONS:-50}
INACTIVITY_TIMEOUT=${TERMINAL_INACTIVITY_TIMEOUT:-300}
CPU_CORES=${TERMINAL_CPU_CORES:-2}
MEMORY_MB=${TERMINAL_MEMORY_MB:-512}
TIMEOUT_SEC=${TERMINAL_TIMEOUT_SEC:-60}

# Build the JAR if it doesn't exist
if [ ! -f "terminal-service/target/terminal-service-standalone.jar" ]; then
    echo "Building terminal-service..."
    mvn clean package -DskipTests -pl terminal-service -am
fi

# Launch the server
echo "Starting Terminal Service on port $PORT..."
echo "  Max sessions: $MAX_SESSIONS"
echo "  Inactivity timeout: ${INACTIVITY_TIMEOUT}s"
echo "  Default resources: ${CPU_CORES} CPU cores, ${MEMORY_MB}MB memory"
java -Dterminal.port=$PORT \
     -Dterminal.maxSessions=$MAX_SESSIONS \
     -Dterminal.inactivityTimeoutSeconds=$INACTIVITY_TIMEOUT \
     -Dterminal.defaultCpuCores=$CPU_CORES \
     -Dterminal.defaultMemoryMb=$MEMORY_MB \
     -Dterminal.defaultTimeoutSeconds=$TIMEOUT_SEC \
     -jar terminal-service/target/terminal-service-standalone.jar "$@"
