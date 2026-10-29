#!/bin/bash

set -e

echo "═══════════════════════════════════════════════════════════"
echo "  ⬇️  DOWNLOADING ECLIPSE JDT LANGUAGE SERVER (BINARY)"
echo "═══════════════════════════════════════════════════════════"

JDTLS_HOME="${HOME}/.jdtls"
TEMP_DIR="/tmp/jdtls-download-$$"

# Create directories
mkdir -p "$TEMP_DIR"
mkdir -p "$JDTLS_HOME/bin"
mkdir -p "$JDTLS_HOME/config_linux"

echo "[1/4] Downloading latest jdtls release..."

# Try multiple sources
DOWNLOAD_URL="https://github.com/eclipse/eclipse.jdt.ls/releases/download/v1.37.0/jdt-language-server-1.37.0-202412181534.tar.gz"

# Use curl with retry logic
if command -v curl &> /dev/null; then
    if curl -L -f -o "$TEMP_DIR/jdtls.tar.gz" "$DOWNLOAD_URL" 2>/dev/null; then
        echo "  ✓ Downloaded from GitHub"
    else
        echo "  ⚠️  GitHub download failed, using fallback source..."
        # Fallback to Maven Central mirror
        DOWNLOAD_URL="https://repo1.maven.org/maven2/org/eclipse/jdtls/org.eclipse.jdt.ls.product/1.37.0/org.eclipse.jdt.ls.product-1.37.0-linux.gtk.x86_64.tar.gz"
        curl -L -f -o "$TEMP_DIR/jdtls.tar.gz" "$DOWNLOAD_URL" || {
            echo "  ✗ Download failed from all sources"
            exit 1
        }
        echo "  ✓ Downloaded from Maven Central"
    fi
else
    echo "  ✗ curl not found"
    exit 1
fi

echo "[2/4] Extracting archive..."
cd "$TEMP_DIR"
tar -xzf jdtls.tar.gz || {
    echo "  ✗ Extraction failed"
    exit 1
}
echo "  ✓ Extracted successfully"

echo "[3/4] Setting up installation..."

# Copy extracted files to jdtls home (skip the top-level jdt-language-server dir if it exists)
if [ -d "$TEMP_DIR/jdt-language-server" ]; then
    cp -r "$TEMP_DIR/jdt-language-server"/* "$JDTLS_HOME/" 2>/dev/null || true
else
    cp -r "$TEMP_DIR"/* "$JDTLS_HOME/" 2>/dev/null || true
fi

# Create wrapper script
cat > "$JDTLS_HOME/bin/jdtls" << 'EOF'
#!/bin/bash
# Eclipse JDT Language Server Wrapper

JDTLS_HOME="${JDTLS_HOME:-~/.jdtls}"
LAUNCHER="$JDTLS_HOME/plugins/org.eclipse.equinox.launcher_*.jar"

# Use the first matching launcher jar
for jar in $LAUNCHER; do
    if [ -f "$jar" ]; then
        LAUNCHER="$jar"
        break
    fi
done

if [ ! -f "$LAUNCHER" ]; then
    echo "ERROR: jdtls launcher not found at $LAUNCHER"
    echo "Try running: ./BUILD_JDTLS_FROM_SOURCE.sh or ./SETUP_JDTLS_BINARY.sh"
    exit 1
fi

# Default JVM args
JVM_ARGS="${JVM_ARGS:--Xmx1G -XX:+UseG1GC -XX:+UseStringDeduplication}"

# Start jdtls
exec java $JVM_ARGS \
    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1044,quiet=y \
    -Declipse.application=org.eclipse.jdt.ls.core.id1 \
    -Dosgi.bundles.defaultStartLevel=4 \
    -Declipse.product=org.eclipse.jdt.ls.core.product \
    -Dlog.level=INFO \
    -noverify \
    -Dfile.encoding=UTF-8 \
    -cp "$LAUNCHER" org.eclipse.equinox.launcher.Main "$@"
EOF

chmod +x "$JDTLS_HOME/bin/jdtls"
echo "  ✓ Wrapper created"

echo "[4/4] Installing to PATH..."

# Create symlink
mkdir -p "$HOME/.local/bin"
ln -sf "$JDTLS_HOME/bin/jdtls" "$HOME/.local/bin/jdtls"

# Add JDTLS_HOME to .bashrc if not already there
if ! grep -q "export JDTLS_HOME" "$HOME/.bashrc"; then
    echo "export JDTLS_HOME=\"$JDTLS_HOME\"" >> "$HOME/.bashrc"
fi

# Add ~/.local/bin to PATH if not already there
if ! grep -q "\.local/bin" "$HOME/.bashrc"; then
    echo "export PATH=\"\$HOME/.local/bin:\$PATH\"" >> "$HOME/.bashrc"
fi

echo "  ✓ Symlink: $HOME/.local/bin/jdtls → $JDTLS_HOME/bin/jdtls"
echo "  ✓ Environment: JDTLS_HOME added to ~/.bashrc"

# Cleanup
rm -rf "$TEMP_DIR"

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  ✅ INSTALLATION COMPLETE"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "📍 JDTLS Home:         $JDTLS_HOME"
echo "📍 Executable:         $HOME/.local/bin/jdtls"
echo "📍 Version:            1.37.0"
echo ""
echo "To use immediately:"
echo "  source ~/.bashrc"
echo "  which jdtls"
echo ""
echo "To test:"
echo "  jdtls --version"
echo ""
