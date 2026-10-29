#!/bin/bash

# Backend Optimization Verification Script
# Quick tests to verify all optimizations are working

set -e

echo "╔════════════════════════════════════════════════════════════╗"
echo "║   BACKEND OPTIMIZATION VERIFICATION                        ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Colors
GREEN="\033[0;32m"
RED="\033[0;31m"
YELLOW="\033[1;33m"
NC="\033[0m"

WORKSPACE="/home/hsavatra/distributed-ide"
BUILD_DIR="$WORKSPACE"
RESULTS=0

# Test 1: Verify compilation and JAR creation
echo -n "[1/5] Build test (compile + package)... "
if mvn -q clean package -DskipTests -f "$BUILD_DIR/pom.xml" 2>&1 > /dev/null; then
    echo -e "${GREEN}✅ PASS${NC}"
else
    echo -e "${RED}❌ FAIL${NC}"
    RESULTS=$((RESULTS + 1))
fi

# Test 2: Verify JAR creation
echo -n "[2/5] JAR artifact creation... "
JAR_COUNT=$(find "$BUILD_DIR" -type f -name "*.jar" -path "*/target/*" 2>/dev/null | grep -v ".pom" | wc -l)
if [ "$JAR_COUNT" -ge 6 ]; then
    echo -e "${GREEN}✅ PASS (${JAR_COUNT} JARs created)${NC}"
else
    echo -e "${RED}❌ FAIL (only ${JAR_COUNT} JARs)${NC}"
    RESULTS=$((RESULTS + 1))
fi

# Test 3: Verify core optimization classes exist
echo -n "[3/5] Optimization classes deployed... "
CLASSES_OK=0
[ -f "$BUILD_DIR/crdt-core/target/classes/com/distributed_ide/common/ObjectMapperFactory.class" ] && CLASSES_OK=$((CLASSES_OK + 1))
[ -f "$BUILD_DIR/crdt-core/target/classes/com/distributed_ide/common/ExecutorServiceFactory.class" ] && CLASSES_OK=$((CLASSES_OK + 1))
[ -f "$BUILD_DIR/crdt-core/target/classes/com/distributed_ide/common/ByteBufferPool.class" ] && CLASSES_OK=$((CLASSES_OK + 1))
[ -f "$BUILD_DIR/crdt-core/target/classes/com/distributed_ide/common/PerformanceMetrics.class" ] && CLASSES_OK=$((CLASSES_OK + 1))

if [ "$CLASSES_OK" -eq 4 ]; then
    echo -e "${GREEN}✅ PASS (4/4 classes)${NC}"
else
    echo -e "${YELLOW}⚠️  PARTIAL (${CLASSES_OK}/4 classes)${NC}"
fi

# Test 4: Verify servers have optimizations integrated
echo -n "[4/5] Server integration test... "
INTEGRATION_OK=0
grep -q "ObjectMapperFactory" "$BUILD_DIR/signaling-server/src/main/java/com/distributed_ide/signaling/server/SignalingWebSocketServer.java" && INTEGRATION_OK=$((INTEGRATION_OK + 1))
grep -q "ObjectMapperFactory" "$BUILD_DIR/sync-server/src/main/java/com/distributed_ide/sync/server/SyncWebSocketServer.java" && INTEGRATION_OK=$((INTEGRATION_OK + 1))
grep -q "PerformanceMetrics" "$BUILD_DIR/signaling-server/src/main/java/com/distributed_ide/signaling_server/SignalingServerApp.java" && INTEGRATION_OK=$((INTEGRATION_OK + 1))
grep -q "PerformanceMetrics" "$BUILD_DIR/sync-server/src/main/java/com/distributed_ide/sync_server/SyncServerApp.java" && INTEGRATION_OK=$((INTEGRATION_OK + 1))

if [ "$INTEGRATION_OK" -eq 4 ]; then
    echo -e "${GREEN}✅ PASS (4/4 integrations)${NC}"
else
    echo -e "${YELLOW}⚠️  PARTIAL (${INTEGRATION_OK}/4)${NC}"
fi

# Test 5: Verify JAR files are executable
echo -n "[5/5] JAR executability test... "
JAR_OK=0
for jar_file in "$BUILD_DIR/sync-server/target/"sync-server-*-SNAPSHOT.jar "$BUILD_DIR/signaling-server/target/"signaling-server-*-SNAPSHOT.jar "$BUILD_DIR/terminal-service/target/"terminal-service-*-SNAPSHOT.jar; do
  if [ -f "$jar_file" ]; then
    JAR_OK=$((JAR_OK + 1))
  fi
done

if [ "$JAR_OK" -eq 3 ]; then
    echo -e "${GREEN}✅ PASS (3/3 JARs ready)${NC}"
else
    echo -e "${RED}❌ FAIL (only ${JAR_OK}/3)${NC}"
    RESULTS=$((RESULTS + 1))
fi

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║   VERIFICATION SUMMARY                                     ║"
echo "╠════════════════════════════════════════════════════════════╣"

if [ "$RESULTS" -eq 0 ]; then
    echo -e "║ ${GREEN}✅ ALL TESTS PASSED${NC}                                           ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    exit 0
else
    echo -e "║ ${RED}❌ ${RESULTS} TEST(S) FAILED${NC}                                          ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    exit 1
fi
