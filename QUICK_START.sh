#!/bin/bash
# Quick Start Script for Distributed IDE
# Starts backend servers + JavaFX client in one command.
# Usage: ./QUICK_START.sh [--rebuild]

# ── Do NOT use set -e: background processes + wait don't mix well with it ──

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "═══════════════════════════════════════════════════════════"
echo "  🚀 DISTRIBUTED IDE - QUICK START"
echo "═══════════════════════════════════════════════════════════"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Ports
SYNC_PORT=8765
SIGNALING_PORT=8082
TERMINAL_PORT=8766
LSP_BRIDGE_PORT=8081

# ── Helpers ──────────────────────────────────────────────────

# Portable check: is a TCP port in LISTEN state?
port_in_use() {
    local port=$1
    if command -v ss &>/dev/null; then
        ss -tlnH "sport = :$port" 2>/dev/null | grep -q LISTEN && return 0
    elif command -v lsof &>/dev/null; then
        lsof -Pi :"$port" -sTCP:LISTEN -t &>/dev/null && return 0
    elif command -v netstat &>/dev/null; then
        netstat -tln 2>/dev/null | grep -q ":$port " && return 0
    else
        # Last resort: try to connect
        (echo >/dev/tcp/localhost/"$port") 2>/dev/null && return 0
    fi
    return 1
}

# Wait until a port is listening (max ~20 s)
wait_for_port() {
    local port=$1 name=$2 max=20 i=0
    while [ $i -lt $max ]; do
        if port_in_use "$port"; then
            echo -e "  ${GREEN}✓${NC} $name ready (port $port)"
            return 0
        fi
        sleep 1
        i=$((i + 1))
    done
    echo -e "  ${RED}✗${NC} $name did not start (port $port) — check logs/$(echo "$name" | tr ' ' '-' | tr '[:upper:]' '[:lower:]').log"
    return 1
}

# Kill a process whose PID is stored in a file
kill_pid_file() {
    local f="$1"
    if [ -f "$f" ]; then
        local pid
        pid=$(cat "$f" 2>/dev/null)
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null
            # Give it a moment, then force-kill
            sleep 0.3
            kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
        fi
        rm -f "$f"
    fi
}

# Kill any process already listening on a port (avoids BindException)
free_port() {
    local port=$1
    if port_in_use "$port"; then
        echo -e "  ${YELLOW}⚠${NC} Port $port already in use — stopping existing process"
        local pids=""
        if command -v lsof &>/dev/null; then
            pids=$(lsof -ti :"$port" 2>/dev/null | tr '\n' ' ')
        elif command -v fuser &>/dev/null; then
            pids=$(fuser -n tcp "$port" 2>/dev/null | tr ' ' '\n' | grep -E '^[0-9]+$' | tr '\n' ' ')
        elif command -v ss &>/dev/null; then
            pids=$(ss -tlnp "sport = :$port" 2>/dev/null | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' | sort -u | tr '\n' ' ')
        fi

        if [ -n "$pids" ]; then
            echo "$pids" | xargs -r kill 2>/dev/null || true
        fi

        local i=0
        while port_in_use "$port" && [ $i -lt 50 ]; do
            sleep 0.1
            i=$((i + 1))
        done

        if port_in_use "$port" && [ -n "$pids" ]; then
            echo "$pids" | xargs -r kill -9 2>/dev/null || true
            i=0
            while port_in_use "$port" && [ $i -lt 50 ]; do
                sleep 0.1
                i=$((i + 1))
            done
        fi

        if port_in_use "$port"; then
            echo -e "  ${RED}✗${NC} Port $port still in use — please stop the process using it and re-run"
            return 1
        fi
    fi
    return 0
}

# ── Cleanup on exit ──────────────────────────────────────────

cleanup() {
    echo ""
    echo -e "${YELLOW}Cleaning up...${NC}"
    kill_pid_file "$SCRIPT_DIR/logs/sync-server.pid"
    kill_pid_file "$SCRIPT_DIR/logs/signaling-server.pid"
    kill_pid_file "$SCRIPT_DIR/logs/terminal-service.pid"
    kill_pid_file "$SCRIPT_DIR/logs/lsp-bridge.pid"
    # Also kill the JavaFX client if still running
    if [ -n "$CLIENT_PID" ] && kill -0 "$CLIENT_PID" 2>/dev/null; then
        kill "$CLIENT_PID" 2>/dev/null || true
    fi
    echo -e "${GREEN}✓${NC} All services stopped"
    echo "Goodbye! 👋"
}

trap cleanup EXIT INT TERM

# ── [1/7] Check Java ─────────────────────────────────────────

echo -e "${BLUE}[1/7]${NC} Checking Java version..."
if ! command -v java &>/dev/null; then
    echo -e "${RED}✗${NC} Java not found. Please install Java 21+"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
if [ "$JAVA_VERSION" -ge 21 ] 2>/dev/null; then
    echo -e "  ${GREEN}✓${NC} Java $JAVA_VERSION"
else
    echo -e "${RED}✗${NC} Java 21+ required (found $JAVA_VERSION)"
    exit 1
fi

# ── [2/7] Check Maven ────────────────────────────────────────

echo -e "${BLUE}[2/7]${NC} Checking Maven..."
if ! command -v mvn &>/dev/null; then
    echo -e "${RED}✗${NC} Maven not found. Please install Maven 3.8+"
    exit 1
fi
MVN_VERSION=$(mvn -version 2>&1 | head -1 | awk '{print $3}')
echo -e "  ${GREEN}✓${NC} Maven $MVN_VERSION"

# ── [3/7] Build if needed ────────────────────────────────────

echo -e "${BLUE}[3/7]${NC} Checking builds..."

SYNC_JAR="$SCRIPT_DIR/sync-server/target/sync-server-standalone.jar"
SIGNAL_JAR="$SCRIPT_DIR/signaling-server/target/signaling-server-standalone.jar"
TERMINAL_JAR="$SCRIPT_DIR/terminal-service/target/terminal-service-standalone.jar"
LSP_BRIDGE_JAR="$SCRIPT_DIR/lsp-bridge/target/lsp-bridge-1.0.0-SNAPSHOT-exec.jar"

NEED_BUILD=false
if [ "$1" = "--rebuild" ]; then
    NEED_BUILD=true
    echo -e "  ${YELLOW}⚠${NC} --rebuild flag: forcing full build"
elif [ ! -f "$SYNC_JAR" ] || [ ! -f "$SIGNAL_JAR" ] || [ ! -f "$TERMINAL_JAR" ] || [ ! -f "$LSP_BRIDGE_JAR" ]; then
    NEED_BUILD=true
fi

if [ "$NEED_BUILD" = true ]; then
    echo -e "  Building server JARs (this may take a minute)..."
    # Skip quality checks (checkstyle, spotbugs) for fast startup; they run in CI
    if mvn -f "$SCRIPT_DIR/pom.xml" clean package -DskipTests \
         -pl sync-server,signaling-server,terminal-service,lsp-bridge -am -q \
         -Dcheckstyle.skip=true -Dspotbugs.skip=true; then
        echo -e "  ${GREEN}✓${NC} Build successful"
    else
        echo -e "  ${RED}✗${NC} Build failed"
        exit 1
    fi
else
    echo -e "  ${GREEN}✓${NC} Standalone JARs found"
fi

# ── [4/7] Start backend servers ──────────────────────────────

echo -e "${BLUE}[4/7]${NC} Starting backend servers..."
mkdir -p "$SCRIPT_DIR/logs"

# Free ports if something is already bound (prevents BindException)
free_port $SYNC_PORT || exit 1
free_port $SIGNALING_PORT || exit 1
free_port $TERMINAL_PORT || exit 1
free_port $LSP_BRIDGE_PORT || exit 1

echo "  → Sync Server (port $SYNC_PORT)..."
java -jar "$SYNC_JAR" > "$SCRIPT_DIR/logs/sync-server.log" 2>&1 &
echo $! > "$SCRIPT_DIR/logs/sync-server.pid"

echo "  → Signaling Server (port $SIGNALING_PORT)..."
java -jar "$SIGNAL_JAR" > "$SCRIPT_DIR/logs/signaling-server.log" 2>&1 &
echo $! > "$SCRIPT_DIR/logs/signaling-server.pid"

echo "  → Terminal Service (port $TERMINAL_PORT)..."
java -jar "$TERMINAL_JAR" > "$SCRIPT_DIR/logs/terminal-service.log" 2>&1 &
echo $! > "$SCRIPT_DIR/logs/terminal-service.pid"

echo "  → LSP Bridge Server (port $LSP_BRIDGE_PORT)..."
java -jar "$LSP_BRIDGE_JAR" --server.port=$LSP_BRIDGE_PORT > "$SCRIPT_DIR/logs/lsp-bridge.log" 2>&1 &
echo $! > "$SCRIPT_DIR/logs/lsp-bridge.pid"

# ── [5/7] Wait for servers ───────────────────────────────────

echo -e "${BLUE}[5/7]${NC} Waiting for servers..."
SERVERS_OK=true
wait_for_port $SYNC_PORT      "Sync Server"      || SERVERS_OK=false
wait_for_port $SIGNALING_PORT  "Signaling Server"  || SERVERS_OK=false
wait_for_port $TERMINAL_PORT   "Terminal Service"   || SERVERS_OK=false
wait_for_port $LSP_BRIDGE_PORT "LSP Bridge Server" || SERVERS_OK=false

if [ "$SERVERS_OK" = true ]; then
    echo -e "  ${GREEN}✓${NC} All backend services ready"
else
    echo -e "  ${YELLOW}⚠${NC} Some services may not be ready — check logs/"
fi

# ── [6/7] Compile JavaFX client if needed ─────────────────────

echo -e "${BLUE}[6/7]${NC} Preparing JavaFX client..."
if [ ! -d "$SCRIPT_DIR/javafx-client/target/classes" ]; then
    echo "  Compiling javafx-client..."
    if mvn -f "$SCRIPT_DIR/javafx-client/pom.xml" compile -q; then
        echo -e "  ${GREEN}✓${NC} Client compiled"
    else
        echo -e "  ${RED}✗${NC} Client compilation failed"
        exit 1
    fi
else
    echo -e "  ${GREEN}✓${NC} Client already compiled"
fi

# ── [7/7] Launch JavaFX IDE ───────────────────────────────────

echo -e "${BLUE}[7/7]${NC} Launching Distributed IDE..."
echo ""

# Run javafx:run from the javafx-client directory, suppress GLib noise
mvn -f "$SCRIPT_DIR/javafx-client/pom.xml" javafx:run \
    2> >(grep -v "GLib-GIO-CRITICAL\|WARNING.*Gtk\|Fontconfig" >&2) &
CLIENT_PID=$!

echo "═══════════════════════════════════════════════════════════"
echo -e "${GREEN}  ✓ Distributed IDE is now running!${NC}"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "  📊 Servers:"
echo "     Sync Server      → ws://localhost:$SYNC_PORT"
echo "     Signaling Server → ws://localhost:$SIGNALING_PORT"
echo "     Terminal Service  → ws://localhost:$TERMINAL_PORT"
echo "     LSP Bridge       → ws://localhost:$LSP_BRIDGE_PORT"
echo ""
echo "  📝 Logs:  tail -f $SCRIPT_DIR/logs/*.log"
echo ""
echo "  💡 Press Ctrl+C to stop everything"
echo ""
echo "  ⚠  When using Collaboration → Host Server, pick a port"
echo "     OTHER than $SYNC_PORT (e.g. 9000) to avoid conflicts."
echo "═══════════════════════════════════════════════════════════"

# Wait for the JavaFX client to exit
wait $CLIENT_PID 2>/dev/null

# Cleanup is called automatically via the EXIT trap
