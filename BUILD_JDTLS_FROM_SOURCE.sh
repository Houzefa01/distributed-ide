#!/bin/bash
# Build Eclipse JDT Language Server from source using Maven

set -e

echo "═══════════════════════════════════════════════════════════"
echo "  🚀 BUILDING ECLIPSE JDT LANGUAGE SERVER FROM SOURCE"
echo "═══════════════════════════════════════════════════════════"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Setup directory
BUILD_DIR="/tmp/jdtls-build"
JDTLS_HOME="${HOME}/.jdtls"

echo -e "${BLUE}Build directory:${NC} $BUILD_DIR"
echo -e "${BLUE}Install path:${NC} $JDTLS_HOME"
echo ""

# ─────────────────────────────────────────────────────────────
# 1. Clone repository
# ─────────────────────────────────────────────────────────────

echo -e "${BLUE}[1/3]${NC} Cloning eclipse.jdt.ls..."

if [ -d "$BUILD_DIR" ]; then
    rm -rf "$BUILD_DIR"
fi

git clone --depth=1 --branch=v1.37.0 \
    https://github.com/eclipse-jdtls/eclipse.jdt.ls.git \
    "$BUILD_DIR" 2>&1 | tail -3

cd "$BUILD_DIR"
echo -e "  ${GREEN}✓${NC} Cloned successfully"
echo ""

# ─────────────────────────────────────────────────────────────
# 2. Build with Maven
# ─────────────────────────────────────────────────────────────

echo -e "${BLUE}[2/3]${NC} Building with Maven (this may take 5-10 minutes)..."

if mvn clean package -DskipTests -q 2>&1 | tail -20; then
    echo -e "  ${GREEN}✓${NC} Build successful"
else
    echo -e "  ${RED}✗${NC} Build failed - check error above"
    exit 1
fi
echo ""

# ─────────────────────────────────────────────────────────────
# 3. Install
# ─────────────────────────────────────────────────────────────

echo -e "${BLUE}[3/3]${NC} Installing..."

mkdir -p "$JDTLS_HOME"

# Unpack the distribution
if [ -f "org.eclipse.jdt.ls.product/target/repository/plugins"/*.jar ]; then
    cp -r org.eclipse.jdt.ls.product/target/repository/* "$JDTLS_HOME/"
    echo -e "  ${GREEN}✓${NC} Installed to $JDTLS_HOME"
else
    echo -e "  ${RED}✗${NC} Build artifacts not found"
    exit 1
fi

# Create wrapper script
mkdir -p "$JDTLS_HOME/bin"
cat > "$JDTLS_HOME/bin/jdtls" << 'WRAPPER'
#!/bin/bash
JDTLS_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec java \
    -Declipse.application=org.eclipse.jdt.ls.core.id1 \
    -Dosgi.bundles.defaultStartLevel=4 \
    -Declipse.product=org.eclipse.jdt.ls.core.product \
    -Dfile.encoding=UTF-8 \
    -DrunEclipse \
    -Dlog.level=WARNING \
    -noverify \
    -Xms1g \
    -Xmx2g \
    -jar "$JDTLS_HOME/plugins/org.eclipse.equinox.launcher_"*.jar \
    -configuration "$JDTLS_HOME/config_linux" \
    -data "${JDTLS_WORKSPACE:-.jdtls-workspace}" \
    "$@"
WRAPPER
chmod +x "$JDTLS_HOME/bin/jdtls"

echo ""

# ─────────────────────────────────────────────────────────────
# 4. Setup environment
# ─────────────────────────────────────────────────────────────

# Add to PATH
mkdir -p ~/.local/bin
ln -sf "$JDTLS_HOME/bin/jdtls" ~/.local/bin/jdtls || true

# Also set JDTLS_HOME for LSP Bridge
echo "export JDTLS_HOME='$JDTLS_HOME'" >> ~/.bashrc 2>/dev/null || true
echo "export PATH=\$PATH:~/.local/bin" >> ~/.bashrc 2>/dev/null || true

echo "═══════════════════════════════════════════════════════════"
echo "  ✅ VERIFICATION"
echo "═══════════════════════════════════════════════════════════"
echo ""

if [ -x "$JDTLS_HOME/bin/jdtls" ]; then
    echo -e "  ${GREEN}✓${NC} jdtls installed at:"
    echo "    $JDTLS_HOME/bin/jdtls"
    echo ""
    echo -e "  ${GREEN}✓${NC} Environment:"
    echo "    export JDTLS_HOME='$JDTLS_HOME'"
else
    echo -e "  ${RED}✗${NC} Installation failed"
    exit 1
fi

echo ""
echo "═══════════════════════════════════════════════════════════"
echo -e "${GREEN}Build and installation complete!${NC}"
echo ""
echo "Next: source ~/.bashrc && ./QUICK_START.sh"
echo "═══════════════════════════════════════════════════════════"

# Cleanup
cd /
rm -rf "$BUILD_DIR"
