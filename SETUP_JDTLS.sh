#!/bin/bash
# Setup Eclipse JDT Language Server (jdtls) - Downloaded from Maven Central

set -e

echo "═══════════════════════════════════════════════════════════"
echo "  🚀 INSTALLING ECLIPSE JDT LANGUAGE SERVER"
echo "═══════════════════════════════════════════════════════════"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Installation directory
JDTLS_HOME="${HOME}/.jdtls"
mkdir -p "$JDTLS_HOME"

echo -e "${BLUE}Installation path:${NC} $JDTLS_HOME"
echo ""

# ─────────────────────────────────────────────────────────────
# 1. Check Prerequisites
# ─────────────────────────────────────────────────────────────

echo -e "${BLUE}[1/4]${NC} Checking prerequisites..."

if ! command -v java &>/dev/null; then
    echo -e "  ${RED}✗${NC} Java not found. Install Java 21+"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
echo -e "  ${GREEN}✓${NC} Java $JAVA_VERSION found"

if ! command -v wget &>/dev/null && ! command -v curl &>/dev/null; then
    echo -e "  ${RED}✗${NC} wget or curl required"
    exit 1
fi
echo -e "  ${GREEN}✓${NC} Download tool found"
echo ""

# ─────────────────────────────────────────────────────────────
# 2. Download Eclipse JDT LS
# ─────────────────────────────────────────────────────────────

echo -e "${BLUE}[2/4]${NC} Downloading Eclipse JDT Language Server..."
echo "  Latest version from Maven Central..."

# Eclipse JDT LS versions - Using GitHub releases instead of Maven Central
JDTLS_VERSION="1.38.0"

# Detect architecture
ARCH=$(uname -m)
case "$ARCH" in
    x86_64)
        ARTIFACT_SUFFIX="linux.gtk.x86_64.tar.gz"
        ;;
    aarch64)
        ARTIFACT_SUFFIX="linux.gtk.aarch64.tar.gz"
        echo "  Using ARM64 build..."
        ;;
    *)
        echo -e "  ${RED}✗${NC} Unsupported architecture: $ARCH"
        exit 1
        ;;
esac

# Try GitHub releases first
JDTLS_URL="https://github.com/eclipse-jdtls/eclipse.jdt.ls/releases/download/v${JDTLS_VERSION}/jdt-language-server-${JDTLS_VERSION}-${ARTIFACT_SUFFIX}"

# Download
JDTLS_ARCHIVE="/tmp/jdtls-${JDTLS_VERSION}.tar.gz"

if [ -f "$JDTLS_ARCHIVE" ]; then
    echo "  Using cached download: $JDTLS_ARCHIVE"
else
    echo "  Downloading: $JDTLS_URL"
    if command -v wget &>/dev/null; then
        wget -q --show-progress "$JDTLS_URL" -O "$JDTLS_ARCHIVE" || {
            echo -e "  ${RED}✗${NC} Download failed"
            exit 1
        }
    else
        curl -L --progress-bar "$JDTLS_URL" -o "$JDTLS_ARCHIVE" || {
            echo -e "  ${RED}✗${NC} Download failed"
            exit 1
        }
    fi
fi

echo -e "  ${GREEN}✓${NC} Downloaded $(du -h "$JDTLS_ARCHIVE" | cut -f1)"
echo ""

# ─────────────────────────────────────────────────────────────
# 3. Extract
# ─────────────────────────────────────────────────────────────

echo -e "${BLUE}[3/4]${NC} Extracting Eclipse JDT LS..."
tar -xzf "$JDTLS_ARCHIVE" -C "$JDTLS_HOME" --strip-components=1 2>/dev/null || {
    echo -e "  ${RED}✗${NC} Extraction failed"
    exit 1
}
echo -e "  ${GREEN}✓${NC} Extracted to $JDTLS_HOME"
echo ""

# ─────────────────────────────────────────────────────────────
# 4. Create Wrapper Script
# ─────────────────────────────────────────────────────────────

echo -e "${BLUE}[4/4]${NC} Creating execution wrapper..."

# Create /usr/local/bin/jdtls wrapper
sudo tee /usr/local/bin/jdtls > /dev/null << 'WRAPPER_EOF'
#!/bin/bash
# Wrapper for Eclipse JDT Language Server
JDTLS_HOME="${JDTLS_HOME:-$HOME/.jdtls}"
if [ ! -d "$JDTLS_HOME" ]; then
    echo "Error: JDTLS_HOME not found: $JDTLS_HOME" >&2
    exit 1
fi

# Create workspace directory if needed
JDTLS_WORKSPACE="${JDTLS_WORKSPACE:-.jdtls-workspace}"
mkdir -p "$JDTLS_WORKSPACE"

# Run with proper Java options
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
    -data "$JDTLS_WORKSPACE" \
    "$@"
WRAPPER_EOF

sudo chmod +x /usr/local/bin/jdtls

# Set environment variable for LSP Bridge
export JDTLS_HOME="$JDTLS_HOME"
echo "export JDTLS_HOME='$JDTLS_HOME'" >> ~/.bashrc
echo "export PATH=\$PATH:/usr/local/bin" >> ~/.bashrc

echo -e "  ${GREEN}✓${NC} Created /usr/local/bin/jdtls"
echo ""

# ─────────────────────────────────────────────────────────────
# 5. Verification
# ─────────────────────────────────────────────────────────────

echo "═══════════════════════════════════════════════════════════"
echo "  ✅ VERIFICATION"
echo "═══════════════════════════════════════════════════════════"
echo ""

if command -v jdtls &>/dev/null; then
    echo -e "  ${GREEN}✓${NC} jdtls is now available in PATH"
    echo "    Command: jdtls"
    echo "    Location: $(which jdtls)"
else
    echo -e "  ${YELLOW}⚠${NC} Please reload your shell or run:"
    echo "    source ~/.bashrc"
fi

echo ""
echo -e "  ${GREEN}✓${NC} JDTLS_HOME = $JDTLS_HOME"
echo ""

# Cleanup
rm -f "$JDTLS_ARCHIVE"

echo "═══════════════════════════════════════════════════════════"
echo -e "${GREEN}Installation complete!${NC}"
echo ""
echo "Next: Run ./QUICK_START.sh"
echo "═══════════════════════════════════════════════════════════"
