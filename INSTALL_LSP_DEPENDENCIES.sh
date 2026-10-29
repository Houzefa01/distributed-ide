#!/bin/bash
# Install LSP Server Dependencies for Distributed IDE
# Installs: jdtls (Java), pylsp (Python), typescript-language-server (TypeScript)

set -e

echo "═══════════════════════════════════════════════════════════"
echo "  📦 LSP SERVER DEPENDENCIES INSTALLER"
echo "═══════════════════════════════════════════════════════════"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ─────────────────────────────────────────────────────────────
# 1. SETUP & PREREQUISITES
# ─────────────────────────────────────────────────────────────

# Detect OS
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    OS="linux"
    PKG_MANAGER=""
    if command -v apt-get &>/dev/null; then
        PKG_MANAGER="apt"
    elif command -v pacman &>/dev/null; then
        PKG_MANAGER="pacman"
    elif command -v dnf &>/dev/null; then
        PKG_MANAGER="dnf"
    fi
elif [[ "$OSTYPE" == "darwin"* ]]; then
    OS="macos"
    PKG_MANAGER="brew"
else
    OS="windows"
    PKG_MANAGER="unknown"
fi

echo -e "${BLUE}Detected OS:${NC} $OS"
echo -e "${BLUE}Package Manager:${NC} ${PKG_MANAGER:-unknown}"
echo ""

# Create LSP installation directory
LSP_DIR="${HOME}/.distributed-ide/lsp-servers"
mkdir -p "$LSP_DIR"
echo -e "${BLUE}LSP Servers Directory:${NC} $LSP_DIR"
echo ""

# ─────────────────────────────────────────────────────────────
# 2. JAVA (jdtls) - ECLIPSE JDT LANGUAGE SERVER
# ─────────────────────────────────────────────────────────────

echo -e "${BLUE}[1/3]${NC} Installing jdtls (Java Language Server)..."

# Check if jdtls is already in PATH
if command -v jdtls &>/dev/null; then
    echo -e "  ${GREEN}✓${NC} jdtls already installed ($(jdtls -v 2>/dev/null || echo 'unknown version'))"
else
    echo -e "  ${YELLOW}→${NC} Installing jdtls..."
    
    case "$PKG_MANAGER" in
        apt)
            echo "    Using apt-get..."
            sudo apt-get update -qq
            if sudo apt-get install -y jdtls &>/dev/null; then
                echo -e "    ${GREEN}✓${NC} jdtls installed via apt"
            else
                echo -e "    ${RED}✗${NC} Failed to install jdtls via apt"
                echo "    Try: sudo apt install jdtls"
            fi
            ;;
        pacman)
            echo "    Using pacman..."
            if sudo pacman -S jdtls --noconfirm &>/dev/null; then
                echo -e "    ${GREEN}✓${NC} jdtls installed via pacman"
            else
                echo -e "    ${RED}✗${NC} Failed to install jdtls via pacman"
                echo "    Try: sudo pacman -S jdtls"
            fi
            ;;
        dnf)
            echo "    Using dnf..."
            if sudo dnf install -y jdtls &>/dev/null; then
                echo -e "    ${GREEN}✓${NC} jdtls installed via dnf"
            else
                echo -e "    ${RED}✗${NC} Failed to install jdtls via dnf"
                echo "    Try: sudo dnf install jdtls"
            fi
            ;;
        brew)
            echo "    Using brew..."
            if brew install jdtls &>/dev/null; then
                echo -e "    ${GREEN}✓${NC} jdtls installed via brew"
            else
                echo -e "    ${RED}✗${NC} Failed to install jdtls via brew"
                echo "    Try: brew install jdtls"
            fi
            ;;
        *)
            echo -e "    ${YELLOW}⚠${NC}  Could not detect package manager"
            echo "    Manual install options:"
            echo "      - Ubuntu/Debian: sudo apt install jdtls"
            echo "      - Fedora/CentOS: sudo dnf install jdtls"
            echo "      - Arch: sudo pacman -S jdtls"
            echo "      - macOS: brew install jdtls"
            echo "      - Or download from: https://github.com/eclipse-jdtls/eclipse.jdt.ls/wiki/Running-the-JAVA-LS-server-from-the-command-line"
            ;;
    esac
fi
echo ""

# ─────────────────────────────────────────────────────────────
# 3. PYTHON (pylsp) - PYTHON LANGUAGE SERVER
# ─────────────────────────────────────────────────────────────

echo -e "${BLUE}[2/3]${NC} Installing pylsp (Python Language Server)..."

# Check if pylsp is already available
if python3 -m pylsp &>/dev/null 2>&1; then
    echo -e "  ${GREEN}✓${NC} pylsp already installed"
elif command -v pylsp &>/dev/null; then
    echo -e "  ${GREEN}✓${NC} pylsp already in PATH"
else
    echo -e "  ${YELLOW}→${NC} Installing pylsp..."
    
    if command -v pip3 &>/dev/null; then
        if pip3 install python-lsp-server &>/dev/null; then
            echo -e "    ${GREEN}✓${NC} pylsp installed via pip3"
        else
            echo -e "    ${RED}✗${NC} Failed to install pylsp"
            echo "    Try: pip3 install python-lsp-server"
        fi
    else
        echo -e "    ${RED}✗${NC} pip3 not found"
        echo "    Try: pip3 install python-lsp-server"
    fi
fi
echo ""

# ─────────────────────────────────────────────────────────────
# 4. TYPESCRIPT (typescript-language-server)
# ─────────────────────────────────────────────────────────────

echo -e "${BLUE}[3/3]${NC} Installing typescript-language-server (TypeScript/JavaScript Language Server)..."

# Check if typescript-language-server is already available
if command -v typescript-language-server &>/dev/null; then
    echo -e "  ${GREEN}✓${NC} typescript-language-server already installed"
else
    echo -e "  ${YELLOW}→${NC} Installing typescript-language-server..."
    
    if command -v npm &>/dev/null; then
        if npm install -g typescript-language-server typescript &>/dev/null; then
            echo -e "    ${GREEN}✓${NC} typescript-language-server installed via npm"
        else
            echo -e "    ${RED}✗${NC} Failed to install typescript-language-server"
            echo "    Try: npm install -g typescript-language-server typescript"
        fi
    else
        echo -e "    ${RED}✗${NC} npm not found"
        echo "    Try: npm install -g typescript-language-server typescript"
    fi
fi
echo ""

# ─────────────────────────────────────────────────────────────
# 5. VERIFICATION
# ─────────────────────────────────────────────────────────────

echo "═══════════════════════════════════════════════════════════"
echo "  📋 VERIFICATION"
echo "═══════════════════════════════════════════════════════════"
echo ""

JAVA_OK=false
PYTHON_OK=false
TYPESCRIPT_OK=false

if command -v jdtls &>/dev/null; then
    echo -e "  ${GREEN}✓${NC} jdtls : $(jdtls -v 2>/dev/null || echo 'installed')"
    JAVA_OK=true
else
    echo -e "  ${RED}✗${NC} jdtls : NOT FOUND"
fi

if command -v pylsp &>/dev/null || python3 -m pylsp &>/dev/null 2>&1; then
    echo -e "  ${GREEN}✓${NC} pylsp : installed"
    PYTHON_OK=true
else
    echo -e "  ${RED}✗${NC} pylsp : NOT FOUND"
fi

if command -v typescript-language-server &>/dev/null; then
    echo -e "  ${GREEN}✓${NC} typescript-language-server : installed"
    TYPESCRIPT_OK=true
else
    echo -e "  ${RED}✗${NC} typescript-language-server : NOT FOUND"
fi

echo ""
echo "═══════════════════════════════════════════════════════════"

if [ "$JAVA_OK" = true ] || [ "$PYTHON_OK" = true ] || [ "$TYPESCRIPT_OK" = true ]; then
    echo -e "${GREEN}  ✓ LSP dependencies installed!${NC}"
    echo ""
    echo "  Next step: Run ./QUICK_START.sh to start the IDE"
else
    echo -e "${YELLOW}  ⚠ No LSP servers installed. Please install manually.${NC}"
    echo ""
    echo "  Install jdtls:"
    echo "    - Ubuntu/Debian: sudo apt install jdtls"
    echo "    - Fedora: sudo dnf install jdtls"
    echo "    - Arch: sudo pacman -S jdtls"
    echo "    - macOS: brew install jdtls"
    echo ""
    echo "  Install pylsp:"
    echo "    pip3 install python-lsp-server"
    echo ""
    echo "  Install typescript-language-server:"
    echo "    npm install -g typescript-language-server typescript"
fi

echo "═══════════════════════════════════════════════════════════"
