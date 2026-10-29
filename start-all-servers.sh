#!/bin/bash
# start-all-servers.sh
# Launch all three servers (sync, signaling, terminal) as standalone JARs

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Starting Distributed IDE Servers${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Create logs directory if it doesn't exist
mkdir -p logs

# Build all servers if needed
if [ ! -f "sync-server/target/sync-server-standalone.jar" ] || \
   [ ! -f "signaling-server/target/signaling-server-standalone.jar" ] || \
   [ ! -f "terminal-service/target/terminal-service-standalone.jar" ]; then
    echo -e "${YELLOW}Building all servers...${NC}"
    mvn clean package -DskipTests -pl sync-server,signaling-server,terminal-service -am
    echo ""
fi

# Start Sync Server
echo -e "${GREEN}[1/3] Starting Sync Server on port ${SYNC_PORT:-8765}...${NC}"
./start-sync-server.sh > logs/sync-server.log 2>&1 &
SYNC_PID=$!
echo "  PID: $SYNC_PID"
echo "  Log: logs/sync-server.log"
sleep 2

# Start Signaling Server
echo -e "${GREEN}[2/3] Starting Signaling Server on port ${SIGNALING_PORT:-8082}...${NC}"
./start-signaling-server.sh > logs/signaling-server.log 2>&1 &
SIGNALING_PID=$!
echo "  PID: $SIGNALING_PID"
echo "  Log: logs/signaling-server.log"
sleep 2

# Start Terminal Service
echo -e "${GREEN}[3/3] Starting Terminal Service on port ${TERMINAL_PORT:-8766}...${NC}"
./start-terminal-service.sh > logs/terminal-service.log 2>&1 &
TERMINAL_PID=$!
echo "  PID: $TERMINAL_PID"
echo "  Log: logs/terminal-service.log"
sleep 2

# Save PIDs to file for stop script
echo "$SYNC_PID" > .server-pids
echo "$SIGNALING_PID" >> .server-pids
echo "$TERMINAL_PID" >> .server-pids

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}All servers started successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Server PIDs:"
echo "  Sync Server:     $SYNC_PID"
echo "  Signaling Server: $SIGNALING_PID"
echo "  Terminal Service: $TERMINAL_PID"
echo ""
echo "To view logs:"
echo "  tail -f logs/sync-server.log"
echo "  tail -f logs/signaling-server.log"
echo "  tail -f logs/terminal-service.log"
echo ""
echo "To stop all servers:"
echo "  ./stop-all-servers.sh"
echo ""
echo -e "${YELLOW}Servers are running in background...${NC}"
