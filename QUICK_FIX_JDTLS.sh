#!/bin/bash
# Quick Fix for jdtls Error
# One-command solution to resolve "jdtls not found" issue

set -e

echo "════════════════════════════════════════════════════════════"
echo "  🔧 DISTRIBUTED IDE - JDTLS QUICK FIX"
echo "════════════════════════════════════════════════════════════"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check prerequisites
echo -e "${BLUE}Step 1: Checking prerequisites...${NC}"

if ! command -v java &>/dev/null; then
    echo -e "  ${RED}✗${NC} Java not found"
    exit 1
fi
echo -e "  ${GREEN}✓${NC} Java available"

if ! command -v git &>/dev/null; then
    echo -e "  ${RED}✗${NC} Git not found"
    exit 1
fi
echo -e "  ${GREEN}✓${NC} Git available"

if ! command -v mvn &>/dev/null; then
    echo -e "  ${RED}✗${NC} Maven not found"
    exit 1
fi
echo -e "  ${GREEN}✓${NC} Maven available"
echo ""

# Show options
echo -e "${BLUE}Step 2: Choose installation method${NC}"
echo ""
echo "1 - STUB ONLY (minimal, instant, limited features)"
echo "2 - BUILD FROM SOURCE (recommended, 5-10 min, full features)"
echo ""
read -p "Enter choice (1 or 2): " CHOICE

case "$CHOICE" in
    1)
        echo ""
        echo -e "${BLUE}Installing jdtls stub...${NC}"
        chmod +x /home/hsavatra/distributed-ide/CREATE_JDTLS_STUB.sh
        /home/hsavatra/distributed-ide/CREATE_JDTLS_STUB.sh
        
        echo ""
        echo -e "${GREEN}✅ DONE!${NC}"
        echo ""
        echo "Now run:"
        echo "  source ~/.bashrc"
        echo "  ./QUICK_START.sh"
        echo ""
        echo "Note: Java features will be limited (LSP disabled)"
        ;;
    2)
        echo ""
        echo -e "${BLUE}Building jdtls from source (this may take 5-10 minutes)...${NC}"
        echo ""
        chmod +x /home/hsavatra/distributed-ide/BUILD_JDTLS_FROM_SOURCE.sh
        /home/hsavatra/distributed-ide/BUILD_JDTLS_FROM_SOURCE.sh
        
        echo ""
        echo -e "${GREEN}✅ DONE!${NC}"
        echo ""
        echo "Now run:"
        echo "  source ~/.bashrc"
        echo "  ./QUICK_START.sh"
        echo ""
        echo "Full Java language support is now enabled!"
        ;;
    *)
        echo -e "${RED}Invalid choice${NC}"
        exit 1
        ;;
esac

echo ""
echo "════════════════════════════════════════════════════════════"
