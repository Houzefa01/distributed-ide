#!/bin/bash

# Distributed IDE Quick Launcher
# Prepares environment and starts the IDE with all services

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "═══════════════════════════════════════════════════════════════"
echo "  🚀 DISTRIBUTED IDE LAUNCHER"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Step 1: Activate environment
echo "[1/3] Activating environment..."
if [ -f "$HOME/.bashrc" ]; then
    source "$HOME/.bashrc"
    echo "  ✓ Environment variables loaded"
else
    echo "  ⚠️  ~/.bashrc not found"
fi

# Step 2: Verify jdtls
echo "[2/3] Verifying jdtls..."
if command -v jdtls &> /dev/null; then
    JDTLS_PATH=$(which jdtls)
    echo "  ✓ jdtls found: $JDTLS_PATH"
else
    echo "  ⚠️  jdtls not in PATH (using degraded mode)"
fi

# Step 3: Check ports
echo "[3/3] Checking service ports..."
ERROR=0

check_port() {
    local port=$1
    local name=$2
    if lsof -i :$port &> /dev/null; then
        echo "  ⚠️  Port $port already in use ($name)"
        ERROR=1
    else
        echo "  ✓ Port $port free ($name)"
    fi
}

check_port 8765 "Sync Server"
check_port 8082 "Signaling Server"
check_port 8766 "Terminal Service"
check_port 8081 "LSP Bridge"

if [ $ERROR -eq 1 ]; then
    echo ""
    echo "⚠️  Some ports are in use. Options:"
    echo "  1. Kill the IDE: pkill -f QUICK_START.sh"
    echo "  2. Wait for services to stop"
    echo "  3. Run: pkill -f 'java.*sync-server && pkill -f 'java.*signaling'"
    echo ""
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  Starting services..."
echo "═══════════════════════════════════════════════════════════════"
echo ""

cd "$SCRIPT_DIR"
exec ./QUICK_START.sh
