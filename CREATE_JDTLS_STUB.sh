#!/bin/bash
# Create a jdtls stub that gracefully handles missing jdtls
# This allows the IDE to start and handle Java files with LSD disabled

set -e

JDTLS_HOME="$HOME/.jdtls"
mkdir -p "$JDTLS_HOME/bin"

# Create the jdtls wrapper script
cat > "$JDTLS_HOME/bin/jdtls" << 'JDTLS_STUB'
#!/bin/bash
# jdtls Stub - Eclipse JDT Language Server Wrapper
# Falls back gracefully when full JDT LS is not available

JDTLS_HOME="${JDTLS_HOME:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
JDTLS_PLUGINS="$JDTLS_HOME/plugins"

# Check if full installation exists
if [ ! -d "$JDTLS_PLUGINS" ]; then
    cat >&2 << 'EOF'
╔════════════════════════════════════════════════════════════╗
║  ECLIPSE JDT LANGUAGE SERVER NOT FULLY INSTALLED          ║
╚════════════════════════════════════════════════════════════╝

The IDE can still work, but Java language features (autocomplete,
diagnostics, hover, etc.) will be disabled.

To install Eclipse JDT Language Server, run one of:

1. Option A - Build from source (recommended):
   cd ~/distributed-ide
   chmod +x BUILD_JDTLS_FROM_SOURCE.sh
   ./BUILD_JDTLS_FROM_SOURCE.sh

2. Option B - Manual download from GitHub:
   Visit: https://github.com/eclipse-jdtls/eclipse.jdt.ls/releases
   Extract to: ~/.jdtls

3. Option C - System package (if available):
   Ubuntu/Debian: sudo apt install eclipse-jdt-language-server
   Fedora:        sudo dnf install eclipse-jdt-language-server
   Arch:          sudo pacman -S eclipse-jdt-language-server

EOF
    exit 1
fi

# Run the full JDT LS if available
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
JDTLS_STUB

chmod +x "$JDTLS_HOME/bin/jdtls"

# Add to PATH
mkdir -p ~/.local/bin
ln -sf "$JDTLS_HOME/bin/jdtls" ~/.local/bin/jdtls 2>/dev/null || true

# Set environment
export JDTLS_HOME="$JDTLS_HOME"
if ! grep -q "JDTLS_HOME" ~/.bashrc 2>/dev/null; then
    echo "export JDTLS_HOME='$JDTLS_HOME'" >> ~/.bashrc
    echo "export PATH=\$PATH:~/.local/bin" >> ~/.bashrc
fi

echo "✅ jdtls stub created at: $JDTLS_HOME/bin/jdtls"
echo ""
echo "You can now run: ./QUICK_START.sh"
echo ""
echo "To complete the installation, run:"
echo "  ./BUILD_JDTLS_FROM_SOURCE.sh"
