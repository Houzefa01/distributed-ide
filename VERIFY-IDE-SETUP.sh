#!/bin/bash

# Environment Verification Checklist
# Ensures all components are ready for IDE launch

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PASSED=0
FAILED=0
WARNING=0

echo "╔══════════════════════════════════════════════════════════════════════════════╗"
echo "║                   🔍 DISTRIBUTED IDE VERIFICATION CHECKLIST                 ║"
echo "╚══════════════════════════════════════════════════════════════════════════════╝"
echo ""

# Function to check command
check_command() {
    local name=$1
    local cmd=$2
    if command -v "$cmd" &> /dev/null; then
        echo "  ✅ $name"
        ((PASSED++))
    else
        echo "  ❌ $name (NOT FOUND)"
        ((FAILED++))
    fi
}

# Function to check file
check_file() {
    local name=$1
    local path=$2
    if [ -f "$path" ]; then
        echo "  ✅ $name"
        ((PASSED++))
    else
        echo "  ❌ $name (NOT FOUND: $path)"
        ((FAILED++))
    fi
}

# Function to check directory
check_dir() {
    local name=$1
    local path=$2
    if [ -d "$path" ]; then
        echo "  ✅ $name"
        ((PASSED++))
    else
        echo "  ❌ $name (NOT FOUND: $path)"
        ((FAILED++))
    fi
}

# Function to check port availability
check_port() {
    local port=$1
    local name=$2
    if command -v lsof &> /dev/null; then
        if ! lsof -i :$port &> /dev/null 2>&1; then
            echo "  ✅ Port $port ($name)"
            ((PASSED++))
        else
            echo "  ⚠️  Port $port IN USE ($name) - Will need to stop existing processes"
            ((WARNING++))
        fi
    else
        echo "  ℹ️  Port $port ($name) - lsof not available, assuming OK"
        ((PASSED++))
    fi
}

# SYSTEM TOOLS
echo "🔧 SYSTEM TOOLS"
echo "───────────────"
check_command "Java" "java"
check_command "Maven" "mvn"
check_command "Git" "git"
echo ""

# JAVA ENVIRONMENTS
echo "☕ JAVA ENVIRONMENTS"
echo "────────────────────"
if command -v java &> /dev/null; then
    JAVA_VER=$(java -version 2>&1 | grep -oP '(?<=version ")[0-9]+' || echo "unknown")
    if [ "$JAVA_VER" = "21" ]; then
        echo "  ✅ Java 21.x"
        ((PASSED++))
    else
        echo "  ⚠️  Java $JAVA_VER (Expected 21.x) - IDE still works"
        ((WARNING++))
    fi
fi

if command -v mvn &> /dev/null; then
    MVN_VER=$(mvn --version 2>&1 | grep "Apache Maven" | grep -oP '[0-9]+\.[0-9]+\.[0-9]+')
    if [[ "$MVN_VER" == 3.9.* ]]; then
        echo "  ✅ Maven $MVN_VER"
        ((PASSED++))
    else
        echo "  ⚠️  Maven $MVN_VER (Expected 3.9.x) - Some builds may fail"
        ((WARNING++))
    fi
fi
echo ""

# JDTLS CONFIGURATION
echo "🚀 JDTLS CONFIGURATION"
echo "──────────────────────"
if [ -x "$HOME/.local/bin/jdtls" ]; then
    echo "  ✅ jdtls executable in PATH"
    ((PASSED++))
else
    echo "  ❌ jdtls not in PATH"
    ((FAILED++))
fi

if [ -d "$HOME/.jdtls" ]; then
    echo "  ✅ JDTLS_HOME directory exists"
    ((PASSED++))
else
    echo "  ⚠️  JDTLS_HOME directory not found - Will use stub"
    ((WARNING++))
fi

if grep -q "JDTLS_HOME" "$HOME/.bashrc" 2>/dev/null; then
    echo "  ✅ JDTLS_HOME in ~/.bashrc"
    ((PASSED++))
else
    echo "  ⚠️  JDTLS_HOME not in ~/.bashrc - Run source ~/.bashrc"
    ((WARNING++))
fi
echo ""

# IDE PROJECT FILES
echo "📁 IDE PROJECT FILES"
echo "────────────────────"
check_file "QUICK_START.sh" "$SCRIPT_DIR/QUICK_START.sh"
check_file "START-IDE.sh" "$SCRIPT_DIR/START-IDE.sh"
check_file "pom.xml" "$SCRIPT_DIR/pom.xml"
check_file "BUILD_JDTLS_FROM_SOURCE.sh" "$SCRIPT_DIR/BUILD_JDTLS_FROM_SOURCE.sh"
echo ""

# COMPILED JAR FILES
echo "📦 COMPILED JAR FILES"
echo "─────────────────────"
if [ -f "$SCRIPT_DIR/sync-server/target/sync-server-1.0.0-SNAPSHOT-exec.jar" ]; then
    echo "  ✅ sync-server compiled"
    ((PASSED++))
else
    echo "  ⚠️  sync-server not compiled - Will build automatically"
    ((WARNING++))
fi

if [ -f "$SCRIPT_DIR/signaling-server/target/signaling-server-1.0.0-SNAPSHOT-exec.jar" ]; then
    echo "  ✅ signaling-server compiled"
    ((PASSED++))
else
    echo "  ⚠️  signaling-server not compiled - Will build automatically"
    ((WARNING++))
fi

if [ -f "$SCRIPT_DIR/terminal-service/target/terminal-service-1.0.0-SNAPSHOT-exec.jar" ]; then
    echo "  ✅ terminal-service compiled"
    ((PASSED++))
else
    echo "  ⚠️  terminal-service not compiled - Will build automatically"
    ((WARNING++))
fi

if [ -f "$SCRIPT_DIR/lsp-bridge/target/lsp-bridge-1.0.0-SNAPSHOT-exec.jar" ]; then
    echo "  ✅ lsp-bridge compiled"
    ((PASSED++))
else
    echo "  ⚠️  lsp-bridge not compiled - Will build automatically"
    ((WARNING++))
fi
echo ""

# PORT AVAILABILITY
echo "🔌 PORT AVAILABILITY"
echo "────────────────────"
check_port 8765 "Sync Server"
check_port 8082 "Signaling Server"
check_port 8766 "Terminal Service"
check_port 8081 "LSP Bridge"
echo ""

# DOCUMENTATION
echo "📚 DOCUMENTATION"
echo "────────────────"
check_file "IDE-STARTUP-READY.md" "$SCRIPT_DIR/IDE-STARTUP-READY.md"
check_file "SESSION-8-NAVIGATION-GUIDE.md" "$SCRIPT_DIR/SESSION-8-NAVIGATION-GUIDE.md"
check_file "JDTLS_SETUP_GUIDE.txt" "$SCRIPT_DIR/JDTLS_SETUP_GUIDE.txt"
echo ""

# SUMMARY
echo "╔══════════════════════════════════════════════════════════════════════════════╗"
echo "║                              VERIFICATION RESULT                            ║"
echo "╚══════════════════════════════════════════════════════════════════════════════╝"
echo ""
echo "  ✅ Passed:   $PASSED"
echo "  ⚠️  Warnings: $WARNING"
echo "  ❌ Failed:   $FAILED"
echo ""

if [ $FAILED -eq 0 ]; then
    echo "🎉 ALL CRITICAL CHECKS PASSED!"
    echo ""
    if [ $WARNING -gt 0 ]; then
        echo "ℹ️  Some warnings detected, but IDE should still work."
        echo "   Review the warnings above if you encounter issues."
    fi
    echo ""
    echo "Ready to start? Run:"
    echo ""
    echo "    ./START-IDE.sh"
    echo ""
    exit 0
else
    echo "❌ Some checks failed. Please resolve the issues above."
    echo ""
    if grep -q "JDTLS_HOME" "$HOME/.bashrc" 2>/dev/null; then
        echo "Run: source ~/.bashrc && ./VERIFY-IDE-SETUP.sh"
    else
        echo "Try: source ~/.bashrc && ./VERIFY-IDE-SETUP.sh"
    fi
    echo ""
    exit 1
fi
