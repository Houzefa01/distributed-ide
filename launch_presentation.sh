#!/bin/bash

################################################################################
#  SCRIPT DE LANCEMENT - Présentation Beamer Distributed IDE
# 
#  Usage:
#    ./launch_presentation.sh                   # Ouvre le PDF
#    ./launch_presentation.sh compile           # Compile et ouvre
#    ./launch_presentation.sh fullscreen        # Mode présentation fullscreen
#    ./launch_presentation.sh clean             # Nettoie les fichiers
#
################################################################################

set -euo pipefail

# Couleurs pour l'output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Fichiers
TEX_FILE="PRESENTATION_DISTRIBUTED_IDE"
PDF_FILE="${TEX_FILE}.pdf"
LATEX_CMD="pdflatex"

# Répertoire du script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Messages helper
info() {
  echo -e "${BLUE}ℹ️  $1${NC}"
}

success() {
  echo -e "${GREEN}✅ $1${NC}"
}

error() {
  echo -e "${RED}❌ $1${NC}"
  exit 1
}

warning() {
  echo -e "${YELLOW}⚠️  $1${NC}"
}

# Vérifications préliminaires
check_dependencies() {
  info "Vérification des dépendances..."
  
  if ! command -v pdflatex &> /dev/null; then
    error "pdflatex n'est pas installé. Installez texlive-full: sudo apt install texlive-full"
  fi
  
  success "Dépendances OK"
}

# Compile le LaTeX
compile() {
  info "Compilation du LaTeX..."
  
  if [ ! -f "${TEX_FILE}.tex" ]; then
    error "Fichier ${TEX_FILE}.tex non trouvé!"
  fi
  
  # Première pass
  warning "Pass 1 de 2..."
  $LATEX_CMD -interaction=nonstopmode -halt-on-error "$TEX_FILE" > /dev/null 2>&1 || true
  
  # Deuxième pass (pour les références croisées)
  warning "Pass 2 de 2..."
  $LATEX_CMD -interaction=nonstopmode -halt-on-error "$TEX_FILE" > /dev/null 2>&1 || true
  
  if [ -f "$PDF_FILE" ]; then
    PDF_SIZE=$(du -h "$PDF_FILE" | cut -f1)
    success "PDF compilé avec succès: $PDF_FILE ($PDF_SIZE)"
  else
    error "Erreur de compilation: le PDF n'a pas été généré"
  fi
}

# Ouvre le PDF
open_pdf() {
  info "Ouverture du PDF..."
  
  if [ ! -f "$PDF_FILE" ]; then
    warning "PDF non trouvé. Compilation en cours..."
    compile
  fi
  
  # Détecte le système d'exploitation
  if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    if command -v evince &> /dev/null; then
      evince "$PDF_FILE" &
    elif command -v mupdf &> /dev/null; then
      mupdf "$PDF_FILE" &
    elif command -v xdg-open &> /dev/null; then
      xdg-open "$PDF_FILE" &
    else
      error "Aucun lecteur PDF trouvé. Installez evince ou mupdf"
    fi
  elif [[ "$OSTYPE" == "darwin"* ]]; then
    open "$PDF_FILE"
  else
    error "Système d'exploitation non supporté"
  fi
  
  success "PDF ouvert!"
}

# Mode présentation (fullscreen)
fullscreen() {
  info "Lancement en mode présentation fullscreen..."
  
  if [ ! -f "$PDF_FILE" ]; then
    compile
  fi
  
  # Utilise pdflatex avec les options appropriées
  if command -v pdfpc &> /dev/null; then
    # pdfpc = présentation PDF avec timer et notes
    info "Utilisation de pdfpc (lecteur de présentation avancé)"
    pdfpc "$PDF_FILE"
  elif command -v llpp &> /dev/null; then
    # llpp = lecteur PDF optimisé pour présentations
    llpp "$PDF_FILE"
  elif command -v evince &> /dev/null; then
    # evince avec fullscreen
    evince --fullscreen "$PDF_FILE" &
  else
    warning "Aucun lecteur de présentation avancé. Utilisant evince..."
    evince "$PDF_FILE" &
  fi
}

# Nettoie les fichiers temporaires
clean() {
  info "Nettoyage des fichiers temporaires..."
  
  rm -f "${TEX_FILE}".{aux,log,out,nav,snm,toc}
  rm -f "${TEX_FILE}".{fls,fdb_latexmk}
  rm -f *~
  
  success "Nettoyage terminé"
}

# Affiche les stats
stats() {
  info "Statistiques du projet:"
  
  if [ -f "${TEX_FILE}.tex" ]; then
    LINES=$(wc -l < "${TEX_FILE}.tex")
    FRAMES=$(grep -c '\\begin{frame}' "${TEX_FILE}.tex" || echo "0")
    TIKZ=$(grep -c '\\begin{tikzpicture}' "${TEX_FILE}.tex" || echo "0")
    echo "  Lignes LaTeX: $LINES"
    echo "  Nombre de slides: $FRAMES"
    echo "  Diagrammes TikZ: $TIKZ"
  fi
  
  if [ -f "$PDF_FILE" ]; then
    SIZE=$(du -h "$PDF_FILE" | cut -f1)
    PAGES=$(pdfinfo "$PDF_FILE" 2>/dev/null | grep "^Pages:" | awk '{print $2}' || echo "?")
    echo "  Taille PDF: $SIZE"
    echo "  Nombre de pages: $PAGES"
  else
    warning "PDF non compilé. Lancez: $0 compile"
  fi
}

# Affiche l'aide
show_help() {
  cat << EOF
╔════════════════════════════════════════════════════════════════════╗
║         Présentation Beamer - Distributed IDE                     ║
║         Script de Lancement Automatisé                            ║
╚════════════════════════════════════════════════════════════════════╝

Usage: $0 [COMMAND]

Commandes:
  (aucune)           Ouvre le PDF compilé
  compile            Compile le LaTeX → PDF
  open               Ouvre le PDF (même que sans argument)
  fullscreen         Lancement mode présentation fullscreen
  clean              Nettoie les fichiers temporaires
  rebuild            Clean + Compile
  stats              Affiche les statistiques
  help               Affiche cette aide

Exemples:
  $ $0                    # Ouvre le PDF
  $ $0 compile            # Compile et ouvre
  $ $0 fullscreen         # Présentation plein écran
  $ $0 rebuild            # Recompilation complète
  $ $0 stats              # Voir statistiques

Notes:
  - pdflatex doit être installé (texlive-full)
  - Pour mode présentation avancé, installez: pdfpc
  - Raccourcis clavier en présentation:
    • B = écran noir
    • W = écran blanc
    • N = compteur temps
    • S = vue miniatures
    • Espace = slide suivante
    • Échap = quitter

Aide complémentaire:
  Lisez: GUIDE_PRESENTATION_BEAMER.txt
  Léez: AUDIT_COMPLET_ET_PLAN_PRESENTATION_30MIN.txt

EOF
}

# Affiche l'intro
show_intro() {
  cat << EOF

╔════════════════════════════════════════════════════════════════════╗
║   📊 Distributed IDE - Presentation Beamer LaTeX                  ║
║   Audit Complet + Plan 30 Minutes                                 ║
║   Designer Professional Grade • Animations Fluides                ║
╚════════════════════════════════════════════════════════════════════╝

Contenu:
  • 26 slides professionnelles
  • Durée: 30 minutes
  • Ratio: 16:9 (moderne)
  • Diagrammes TikZ
  • Animations
  • Timing minute-by-minute

PDF: $(test -f "$PDF_FILE" && echo "✅ Prêt" || echo "❌ À compiler")

EOF
}

# Main
main() {
  show_intro
  
  local command="${1:-}"
  
  case "$command" in
    compile|c)
      check_dependencies
      compile
      open_pdf
      ;;
    open|o)
      check_dependencies
      open_pdf
      ;;
    fullscreen|f|present)
      check_dependencies
      if [ ! -f "$PDF_FILE" ]; then
        compile
      fi
      fullscreen
      ;;
    clean|cl)
      clean
      ;;
    rebuild|r)
      check_dependencies
      clean
      compile
      open_pdf
      ;;
    stats|st)
      stats
      ;;
    help|h|--help|-h)
      show_help
      ;;
    "")
      # Pas d'argument = ouvrir le PDF
      check_dependencies
      open_pdf
      ;;
    *)
      error "Commande inconnue: '$command'"
      show_help
      exit 1
      ;;
  esac
}

# Exécute main
main "$@"
