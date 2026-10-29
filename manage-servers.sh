#!/bin/bash

# Script de gestion des serveurs optimisés du cluster distributed-ide
# Usage: ./manage-servers.sh {start|stop|restart|status}

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOGS_DIR="${PROJECT_DIR}/logs"
PIDS_FILE="${LOGS_DIR}/server-pids.txt"

# Créer le répertoire logs s'il n'existe pas
mkdir -p "$LOGS_DIR"

# Paramètres JVM optimisés
SYNC_JVM="-Xmx4g -Xms4g -server"
SERVICE_JVM="-Xmx2g -Xms2g -server"
COMMON_JVM="-Djava.net.preferIPv4Stack=true -Dcom.sun.nio.maxCachedBufferSize=131072 -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

function start_server() {
    local name=$1
    local jar=$2
    local xmx=$3
    
    if pgrep -f "$jar" > /dev/null 2>&1; then
        echo "⚠️  $name est déjà en cours d'exécution"
        return
    fi
    
    echo "🚀 Démarrage de $name..."
    cd "$PROJECT_DIR"
    
    java $xmx -server $COMMON_JVM -jar "$jar" >> "$LOGS_DIR/${name}.log" 2>&1 &
    local pid=$!
    echo $pid >> "$PIDS_FILE"
    
    sleep 2
    if ps -p $pid > /dev/null; then
        echo "✅ $name démarré (PID: $pid)"
    else
        echo "❌ Erreur au démarrage de $name"
        return 1
    fi
}

function stop_server() {
    local name=$1
    local pattern=$2
    
    local pids=$(pgrep -f "$pattern" 2>/dev/null || true)
    if [ -z "$pids" ]; then
        echo "⚠️  $name n'est pas en cours d'exécution"
        return
    fi
    
    echo "⏹️  Arrêt de $name..."
    for pid in $pids; do
        kill $pid 2>/dev/null || true
        sleep 1
        if ! ps -p $pid > /dev/null 2>&1; then
            echo "✅ $name arrêté (PID: $pid)"
        else
            kill -9 $pid 2>/dev/null || true
            echo "✅ $name force-arrêté (PID: $pid)"
        fi
    done
}

function status_servers() {
    echo "╔════════════════════════════════════════════════╗"
    echo "║           STATUT DES SERVEURS                  ║"
    echo "╚════════════════════════════════════════════════╝"
    echo ""
    
    for server in "sync-server" "signaling-server" "terminal-service"; do
        if pgrep -f "$server.*standalone.jar" > /dev/null 2>&1; then
            local pid=$(pgrep -f "$server.*standalone.jar" | head -1)
            local mem=$(ps -p $pid -o rss= 2>/dev/null | awk '{printf "%.1f MB", $1/1024}' || echo "N/A")
            echo "✅ $server"
            echo "   PID: $pid | Mémoire: $mem"
        else
            echo "❌ $server (arrêté)"
        fi
    done
    
    echo ""
    echo "📊 Mémoire totale consommée:"
    local total=$(pgrep -f "standalone.jar" | xargs ps -o rss= 2>/dev/null | awk '{sum+=$1} END {printf "%.1f MB", sum/1024}' || echo "N/A")
    echo "   $total"
}

function display_help() {
    cat << 'EOF'
Usage: ./manage-servers.sh {start|stop|restart|status|logs}

Commandes:
  start     - Démarrer tous les serveurs
  stop      - Arrêter tous les serveurs  
  restart   - Redémarrer tous les serveurs
  status    - Afficher l'état des serveurs
  logs      - Afficher les logs en temps réel

Exemples:
  ./manage-servers.sh start
  ./manage-servers.sh status
  ./manage-servers.sh logs
EOF
}

case "${1:-help}" in
    start)
        start_server "sync-server" "sync-server/target/sync-server-standalone.jar" "$SYNC_JVM"
        start_server "signaling-server" "signaling-server/target/signaling-server-standalone.jar" "$SERVICE_JVM"
        start_server "terminal-service" "terminal-service/target/terminal-service-standalone.jar" "$SERVICE_JVM"
        echo ""
        status_servers
        ;;
    stop)
        stop_server "sync-server" "sync-server.*standalone.jar"
        stop_server "signaling-server" "signaling-server.*standalone.jar"
        stop_server "terminal-service" "terminal-service.*standalone.jar"
        ;;
    restart)
        $0 stop
        sleep 2
        $0 start
        ;;
    status)
        status_servers
        ;;
    logs)
        clear
        echo "📊 LOGS EN TEMPS RÉEL (Ctrl+C pour quitter)"
        echo "═════════════════════════════════════════════════════════"
        tail -f "$LOGS_DIR"/*.log 2>/dev/null || echo "Aucun fichier log trouvé"
        ;;
    *)
        display_help
        exit 1
        ;;
esac
