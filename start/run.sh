#!/bin/bash

################################################################################
# Distributed IDE - Launcher Script (Linux/macOS)                             #
# Détecte automatiquement Java, définit les options JVM et lance l'IDE        #
################################################################################

set -e

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# CONFIGURATION
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IDE_HOME="$SCRIPT_DIR"
LIB_DIR="$IDE_HOME/lib"
LOG_DIR="$IDE_HOME/logs"
CONFIG_DIR="$IDE_HOME/config"

# Fichier de sortie des logs
LOG_FILE="$LOG_DIR/ide-$(date +%Y%m%d-%H%M%S).log"

# Classe principale
MAIN_CLASS="com.distributed_ide.javafx_client.DistributedIDEApp"

# Options JVM
MIN_JAVA_VERSION=17
RECOMMENDED_JAVA_VERSION=21

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# COULEURS POUR L'AFFICHAGE
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# FONCTIONS UTILITAIRES
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

log() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}✓${NC} $1"
}

error() {
    echo -e "${RED}✗${NC} $1" >&2
}

warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# DÉTECTION DE JAVA
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

find_java() {
    log "🔍 Recherche de Java..."
    
    # 1. Vérifier JAVA_HOME
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_CMD="$JAVA_HOME/bin/java"
        success "Java trouvé via \$JAVA_HOME"
        return 0
    fi
    
    # 2. Tenter de détecter Java sur le système
    if command -v java &> /dev/null; then
        JAVA_CMD="$(command -v java)"
        success "Java trouvé dans PATH"
        return 0
    fi
    
    # 3. Emplacements par défaut macOS
    if [ -x /usr/libexec/java_home ]; then
        JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null)
        if [ -x "$JAVA_HOME/bin/java" ]; then
            JAVA_CMD="$JAVA_HOME/bin/java"
            success "Java trouvé dans macOS"
            return 0
        fi
    fi
    
    for JAVA_PATH in /Library/Java/JavaVirtualMachines/*/Contents/Home/bin/java; do
        if [ -x "$JAVA_PATH" ]; then
            JAVA_CMD="$JAVA_PATH"
            success "Java trouvé dans macOS"
            return 0
        fi
    done
    
    # 4. Emplacements par défaut Linux
    for JAVA_PATH in /usr/bin/java /usr/local/bin/java /opt/java/bin/java; do
        if [ -x "$JAVA_PATH" ]; then
            JAVA_CMD="$JAVA_PATH"
            success "Java trouvé dans Linux"
            return 0
        fi
    done
    
    error "Java n'a pas été trouvé!"
    echo ""
    echo "Pour résoudre ce problème:"
    echo "  1. Installez Java ${MIN_JAVA_VERSION}+ (OpenJDK recommandé)"
    echo "  2. Définissez \$JAVA_HOME:"
    echo "     export JAVA_HOME=/chemin/vers/jdk"
    echo "  3. Relancez ce script"
    echo ""
    return 1
}

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# VÉRIFICATION VERSION JAVA
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

check_java_version() {
    log "🔎 Vérification de la version de Java..."
    
    JAVA_VERSION=$($JAVA_CMD -version 2>&1 | grep -oP 'version "\K[0-9.]+' | head -1)
    JAVA_MAJOR=$(echo $JAVA_VERSION | cut -d. -f1)
    
    if [ -z "$JAVA_MAJOR" ]; then
        JAVA_MAJOR=$($JAVA_CMD -version 2>&1 | grep -oP '(?<=release )\d+' | head -1)
    fi
    
    success "Java ${JAVA_MAJOR} détecté (${JAVA_VERSION})"
    
    if [ "$JAVA_MAJOR" -lt "$MIN_JAVA_VERSION" ]; then
        error "Java ${MIN_JAVA_VERSION}+ est requis (trouvé: ${JAVA_MAJOR})"
        return 1
    fi
    
    if [ "$JAVA_MAJOR" -lt "$RECOMMENDED_JAVA_VERSION" ]; then
        warning "Java ${RECOMMENDED_JAVA_VERSION} recommandé pour les meilleures performances"
    fi
    
    return 0
}

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# VÉRIFICATION DES DOSSIERS
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

check_directories() {
    log "📁 Vérification des dossiers..."
    
    if [ ! -d "$LIB_DIR" ]; then
        error "Dossier $LIB_DIR non trouvé"
        echo "Assurez-vous d'avoir exécuté: mvn clean package"
        return 1
    fi
    
    # Le JAR peut être dans IDE_HOME ou dans start/ (après extraction de distribution)
    if [ ! -f "$IDE_HOME/distributed-ide.jar" ] && [ ! -f "$(dirname $IDE_HOME)/distributed-ide.jar" ]; then
        error "Fichier $IDE_HOME/distributed-ide.jar non trouvé"
        echo "Assurez-vous d'avoir exécuté: mvn clean package"
        return 1
    fi
    
    # Detect where the JAR is located
    if [ -f "$(dirname $IDE_HOME)/distributed-ide.jar" ]; then
        JAR_LOCATION="$(dirname $IDE_HOME)/distributed-ide.jar"
    else
        JAR_LOCATION="$IDE_HOME/distributed-ide.jar"
    fi
    
    success "Fichiers trouvés"
    return 0
}

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# CONSTRUCTION DU CLASSPATH
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

build_classpath() {
    log "🔗 Construction du classpath..."
    
    CLASSPATH="$JAR_LOCATION"
    
    # Ajouter tous les JARs du dossier lib
    for jar in "$LIB_DIR"/*.jar; do
        if [ -f "$jar" ]; then
            CLASSPATH="$CLASSPATH:$jar"
        fi
    done
    
    success "Classpath construit"
}

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# OPTIONS JVM OPTIMISÉES
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

build_jvm_options() {
    log "⚙️  Configuration des options JVM..."
    
    JVM_OPTS=""
    
    # Mémoire
    JVM_OPTS="$JVM_OPTS -Xms512m"        # Heap initial
    JVM_OPTS="$JVM_OPTS -Xmx2048m"       # Heap max
    
    # Garbage Collection optimisé
    JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"                    # G1GC optimisé
    JVM_OPTS="$JVM_OPTS -XX:+UseStringDeduplication"    # Dédupliquer strings
    JVM_OPTS="$JVM_OPTS -XX:MaxGCPauseMillis=200"       # Pauses GC courtes
    
    # Encoding UTF-8
    JVM_OPTS="$JVM_OPTS -Dfile.encoding=UTF-8"
    JVM_OPTS="$JVM_OPTS -Dsun.jnu.encoding=UTF-8"
    
    # JavaFX (si sur macOS M1/M2)
    if [[ "$OSTYPE" == "darwin"* ]]; then
        JVM_OPTS="$JVM_OPTS --add-opens javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED"
    fi
    
    # Logs
    JVM_OPTS="$JVM_OPTS -Dlogging.file=$LOG_FILE"
    
    # Mode headless désactivé (on veut la GUI)
    JVM_OPTS="$JVM_OPTS -Djava.awt.headless=false"
    
    success "Options JVM configurées"
}

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# LANCEMENT DE L'IDE
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

launch_ide() {
    log "🚀 Lancement de Distributed IDE..."
    echo ""
    
    # Exporter pour les processus enfants
    export CLASSPATH
    export JAVA_HOME
    
    # Afficher les options de lancement (debug)
    # echo "Debug: $JAVA_CMD $JVM_OPTS -cp $CLASSPATH $MAIN_CLASS"
    
    # Lancer l'IDE
    "$JAVA_CMD" $JVM_OPTS -cp "$CLASSPATH" "$MAIN_CLASS" "$@"
    
    LAUNCH_STATUS=$?
    
    if [ $LAUNCH_STATUS -eq 0 ]; then
        success "IDE fermé proprement"
    else
        error "IDE s'est fermé avec le code $LAUNCH_STATUS"
    fi
    
    return $LAUNCH_STATUS
}

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# NETTOYAGE FINAL
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

cleanup() {
    # Optionnel: nettoyer les fichiers temporaires
    if [ -n "$LOG_FILE" ] && [ -f "$LOG_FILE" ]; then
        success "Logs sauvegardés dans: $LOG_FILE"
    fi
}

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# POINT D'ENTRÉE PRINCIPAL
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

main() {
    echo ""
    echo "==========================================="
    echo "     Distributed IDE Launcher"
    echo "==========================================="
    echo ""
    
    # Étape 1: Trouver Java
    if ! find_java; then
        exit 1
    fi
    
    # Étape 2: Vérifier la version
    if ! check_java_version; then
        exit 1
    fi
    
    # Étape 3: Vérifier les fichiers
    if ! check_directories; then
        exit 1
    fi
    
    # Étape 4: Construire classpath
    build_classpath
    
    # Étape 5: Options JVM
    build_jvm_options
    
    # Étape 6: Lancer l'IDE
    launch_ide "$@"
    RESULT=$?
    
    # Étape 7: Nettoyage
    cleanup
    
    exit $RESULT
}

# Gérer Ctrl+C
trap 'echo ""; warning "Interruption utilisateur"; exit 130' INT TERM

# Lancer
main "$@"
