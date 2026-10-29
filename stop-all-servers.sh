#!/bin/bash
# stop-all-servers.sh
# Stop all running servers

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Stopping all servers...${NC}"
echo ""

# Stop servers by PID file
if [ -f ".server-pids" ]; then
    while IFS= read -r pid; do
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            echo "Stopping process $pid..."
            kill "$pid" 2>/dev/null || true
        fi
    done < .server-pids
    rm -f .server-pids
    echo -e "${GREEN}Servers stopped via PID file${NC}"
else
    echo -e "${YELLOW}No .server-pids file found${NC}"
fi

# Fallback: kill by JAR name
echo ""
echo "Checking for running servers..."
PIDS=$(ps aux | grep '[j]ava.*standalone.jar' | awk '{print $2}')

if [ -n "$PIDS" ]; then
    echo "Found running servers, stopping them..."
    echo "$PIDS" | while read -r pid; do
        if [ -n "$pid" ]; then
            echo "  Stopping PID $pid"
            kill "$pid" 2>/dev/null || true
        fi
    done
    sleep 2

    # Force kill if still running
    PIDS=$(ps aux | grep '[j]ava.*standalone.jar' | awk '{print $2}')
    if [ -n "$PIDS" ]; then
        echo -e "${RED}Some processes didn't stop, force killing...${NC}"
        echo "$PIDS" | while read -r pid; do
            if [ -n "$pid" ]; then
                kill -9 "$pid" 2>/dev/null || true
            fi
        done
    fi

    echo -e "${GREEN}All servers stopped${NC}"
else
    echo -e "${GREEN}No running servers found${NC}"
fi

echo ""
echo -e "${GREEN}Done!${NC}"
