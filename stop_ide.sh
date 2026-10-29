#!/bin/bash
# Stop all Distributed IDE services
# Author: Claude AI Assistant

echo "🛑 Stopping Distributed IDE services..."

# Stop Sync Server
if [ -f logs/sync-server.pid ]; then
    PID=$(cat logs/sync-server.pid)
    if ps -p $PID > /dev/null 2>&1; then
        kill $PID
        echo "✓ Sync Server stopped (PID: $PID)"
    fi
    rm logs/sync-server.pid
fi

# Stop Signaling Server
if [ -f logs/signaling-server.pid ]; then
    PID=$(cat logs/signaling-server.pid)
    if ps -p $PID > /dev/null 2>&1; then
        kill $PID
        echo "✓ Signaling Server stopped (PID: $PID)"
    fi
    rm logs/signaling-server.pid
fi

# Stop Terminal Service
if [ -f logs/terminal-service.pid ]; then
    PID=$(cat logs/terminal-service.pid)
    if ps -p $PID > /dev/null 2>&1; then
        kill $PID
        echo "✓ Terminal Service stopped (PID: $PID)"
    fi
    rm logs/terminal-service.pid
fi

# Kill any remaining Java processes for this IDE
pkill -f "distributed-ide.*jar" 2>/dev/null || true

echo "✓ All Distributed IDE services stopped"
