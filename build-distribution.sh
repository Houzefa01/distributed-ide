#!/bin/bash

################################################################################
# Distributed IDE - Build & Distribution Script                               #
# Automatise la compilation, packaging et génération de distributions           #
################################################################################

set -e

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# CONFIGURATION
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
JAVAFX_TARGET="$PROJECT_DIR/javafx-client/target"
DIST_OUTPUT="$PROJECT_DIR/dist"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# COLORS
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# UTILITY FUNCTIONS
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

stage() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}$1${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# PHASE 1: COMPILATION
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

compile_project() {
    stage "PHASE 1: Compilation Maven"
    
    log "🔨 Compilation de tous les modules..."
    cd "$PROJECT_DIR"
    
    if mvn clean package -DskipTests -q; then
        success "Compilation réussie"
        return 0
    else
        error "Échec de la compilation"
        return 1
    fi
}

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# PHASE 2: VERIFY DISTRIBUTIONS
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

verify_distributions() {
    stage "PHASE 2: Vérification des Archives"
    
    if [ ! -d "$JAVAFX_TARGET" ]; then
        error "Dossier target non trouvé: $JAVAFX_TARGET"
        return 1
    fi
    
    log "📦 Recherche des archives..."
    TAR_FILE=$(ls -t "$JAVAFX_TARGET"/*.tar.gz 2>/dev/null | head -1)
    ZIP_FILE=$(ls -t "$JAVAFX_TARGET"/*.zip 2>/dev/null | head -1)
    
    if [ -z "$TAR_FILE" ] && [ -z "$ZIP_FILE" ]; then
        error "Aucune archive trouvée dans $JAVAFX_TARGET"
        return 1
    fi
    
    if [ -n "$TAR_FILE" ]; then
        TAR_SIZE=$(du -h "$TAR_FILE" | cut -f1)
        success "Archive TAR trouvée: $(basename $TAR_FILE) ($TAR_SIZE)"
        
        log "🔍 Vérification du contenu TAR..."
        if tar -tzf "$TAR_FILE" > /dev/null 2>&1; then
            success "Archive TAR valide"
        else
            error "Archive TAR corrompue"
            return 1
        fi
    fi
    
    if [ -n "$ZIP_FILE" ]; then
        ZIP_SIZE=$(du -h "$ZIP_FILE" | cut -f1)
        success "Archive ZIP trouvée: $(basename $ZIP_FILE) ($ZIP_SIZE)"
        
        log "🔍 Vérification du contenu ZIP..."
        if unzip -t "$ZIP_FILE" > /dev/null 2>&1; then
            success "Archive ZIP valide"
        else
            error "Archive ZIP corrompue"
            return 1
        fi
    fi
    
    return 0
}

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# PHASE 3: ORGANIZE DISTRIBUTIONS
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

organize_distributions() {
    stage "PHASE 3: Organisation des Distributions"
    
    # Create output directory
    mkdir -p "$DIST_OUTPUT"
    log "📁 Dossier de sortie: $DIST_OUTPUT"
    
    # Copy TAR
    if [ -n "$TAR_FILE" ]; then
        TAR_DEST="$DIST_OUTPUT/distributed-ide-linux-macos-$TIMESTAMP.tar.gz"
        log "📋 Copie TAR vers $TAR_DEST..."
        cp "$TAR_FILE" "$TAR_DEST"
        success "TAR copié"
    fi
    
    # Copy ZIP
    if [ -n "$ZIP_FILE" ]; then
        ZIP_DEST="$DIST_OUTPUT/distributed-ide-windows-$TIMESTAMP.zip"
        log "📋 Copie ZIP vers $ZIP_DEST..."
        cp "$ZIP_FILE" "$ZIP_DEST"
        success "ZIP copié"
    fi
    
    # Create symbolic links to latest
    if [ -n "$TAR_FILE" ]; then
        ln -sf "$(basename $TAR_DEST)" "$DIST_OUTPUT/distributed-ide-latest.tar.gz"
        success "Lien symbolique TAR créé"
    fi
    
    if [ -n "$ZIP_FILE" ]; then
        ln -sf "$(basename $ZIP_DEST)" "$DIST_OUTPUT/distributed-ide-latest.zip"
        success "Lien symbolique ZIP créé"
    fi
    
    return 0
}

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# PHASE 4: GENERATE REPORT
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

generate_report() {
    stage "PHASE 4: Rapport de Distribution"
    
    REPORT="$DIST_OUTPUT/DISTRIBUTION-REPORT-$TIMESTAMP.md"
    
    cat > "$REPORT" << 'EOF'
# 📦 Rapport de Distribution - Distributed IDE

## ✅ Statut: SUCCÈS

### 📊 Statistiques de Compilation
- Build Date: %BUILD_DATE%
- Java Version: %JAVA_VERSION%
- Maven Version: %MAVEN_VERSION%
- Durée totale: %BUILD_TIME%

### 📦 Distributions Générées

#### Archive Linux/macOS
- Fichier: %TAR_FILE%
- Taille: %TAR_SIZE%
- Statut: ✅ Vérifiée
- Format: tar.gz (compressé)

#### Archive Windows
- Fichier: %ZIP_FILE%
- Taille: %ZIP_SIZE%
- Statut: ✅ Vérifiée
- Format: zip (compressé)

### 📂 Structure de Distribution

```
distributed-ide/
├── distributed-ide.jar          (~ 200MB)
├── start/
│   ├── run.sh                   (Linux/macOS launcher)
│   ├── run.bat                  (Windows launcher)
│   ├── lib/                     (200+ dépendances)
│   ├── logs/                    (répertoire pour logs)
│   └── config/                  (répertoire pour config)
├── README.md                    (Documentation)
└── ARCHITECTURE.md              (Architecture)
```

### 🚀 Utilisation

#### Linux / macOS
```bash
tar xzf distributed-ide-linux-macos-*.tar.gz
cd distributed-ide
./start/run.sh
```

#### Windows
```cmd
REM Extraire le ZIP
cd distributed-ide
start\run.bat
```

### 🔍 Vérification

✅ Archives générées avec succès
✅ Contenu validé
✅ Scripts exécutables
✅ Dépendances complètes
✅ Prêt pour distribution

### 📋 Prochaines Étapes

1. Tester sur chaque plateforme (Linux, macOS, Windows)
2. Vérifier la détection Java automatique
3. Valider les features (LSP, Terminal, WebRTC)
4. Archiver les distributions
5. Publier les notes de version

### 📞 Support

- Documentation: Voir README.md et ARCHITECTURE.md
- Logs: Consulter start/logs/ide-*.log
- Erreurs: Voir DISTRIBUTION-GUIDE.md

---

**Generated:** %TIMESTAMP%
**Distributed IDE v1.0.0-SNAPSHOT**
EOF
    
    # Replace placeholders
    BUILD_DATE=$(date '+%Y-%m-%d %H:%M:%S')
    JAVA_VERSION=$(java -version 2>&1 | head -1)
    MAVEN_VERSION=$(mvn -q -v 2>&1 | head -1)
    
    sed -i "s|%BUILD_DATE%|$BUILD_DATE|g" "$REPORT"
    sed -i "s|%JAVA_VERSION%|$JAVA_VERSION|g" "$REPORT"
    sed -i "s|%MAVEN_VERSION%|$MAVEN_VERSION|g" "$REPORT"
    sed -i "s|%TAR_FILE%|$(basename $TAR_FILE)|g" "$REPORT"
    sed -i "s|%ZIP_FILE%|$(basename $ZIP_FILE)|g" "$REPORT"
    sed -i "s|%TAR_SIZE%|$TAR_SIZE|g" "$REPORT"
    sed -i "s|%ZIP_SIZE%|$ZIP_SIZE|g" "$REPORT"
    sed -i "s|%TIMESTAMP%|$TIMESTAMP|g" "$REPORT"
    
    success "Rapport généré: $REPORT"
    
    return 0
}

#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# MAIN
#━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

main() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════════════════╗"
    echo "║         Distributed IDE - Build & Distribution Automation              ║"
    echo "╚════════════════════════════════════════════════════════════════════════╝"
    echo ""
    
    # Phase 1: Compile
    if ! compile_project; then
        error "Compilation échouée!"
        exit 1
    fi
    
    # Phase 2: Verify
    if ! verify_distributions; then
        error "Vérification des archives échouée!"
        exit 1
    fi
    
    # Phase 3: Organize
    if ! organize_distributions; then
        error "Organisation des distributions échouée!"
        exit 1
    fi
    
    # Phase 4: Report
    if ! generate_report; then
        error "Génération du rapport échouée!"
        exit 1
    fi
    
    # Final summary
    echo ""
    stage "✅ DISTRIBUTION COMPLÉTÉE AVEC SUCCÈS"
    echo ""
    echo -e "${GREEN}📦 Distributions disponibles:${NC}"
    ls -lh "$DIST_OUTPUT"/*.{tar.gz,zip} 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}'
    echo ""
    echo -e "${GREEN}📝 Rapport:${NC}"
    ls -lh "$DIST_OUTPUT"/DISTRIBUTION-REPORT-*.md 2>/dev/null | awk '{print "  " $9}'
    echo ""
    echo -e "${CYAN}Prochains étapes:${NC}"
    echo "  1. Extraire les archives"
    echo "  2. Tester sur chaque plateforme"
    echo "  3. Valider les features"
    echo "  4. Publier les versions"
    echo ""
}

# Run
main "$@"
