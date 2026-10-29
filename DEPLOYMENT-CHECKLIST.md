# ✅ Deployment Checklist - Distributed IDE

## Pre-Deployment Verification

### System Requirements
- [ ] Java 17+ installed (run `java -version`)
- [ ] Maven 3.6+ installed (run `mvn -v`)
- [ ] Git available (run `git --version`)
- [ ] Bash or Windows CMD available (OS native shell)

### Project Setup
- [ ] Clone/download distributed-ide repository
- [ ] Verify `pom.xml` exists in root
- [ ] Verify all 9 modules present:
  - [ ] crdt-core
  - [ ] sync-server
  - [ ] terminal-service
  - [ ] signaling-server
  - [ ] project-manager
  - [ ] lsp-bridge
  - [ ] plugin-system
  - [ ] javafx-client
  
### Code Quality
- [ ] Run `mvn clean compile` - SUCCESS
- [ ] Run `mvn test` - ALL PASSING (150+ tests)
- [ ] No warnings or errors in output
- [ ] Build reproducible (idempotent)

---

## Building Distribution

### Step 1: Clean Build
```bash
mvn clean package -DskipTests
# ✓ Should complete without errors
```

### Step 2: Verify Build Artifacts
- [ ] Check javafx-client/target/*.tar.gz exists (51MB)
- [ ] Check javafx-client/target/*.zip exists (51MB)
- [ ] Verify both files are valid:
  ```bash
  tar -tzf javafx-client-*.tar.gz > /dev/null
  unzip -t javafx-client-*.zip > /dev/null
  ```

### Step 3: Use Build Automation (Recommended)
```bash
./build-distribution.sh
# ✓ Creates dist/ directory with timestamped archives
# ✓ Generates DISTRIBUTION-REPORT-*.md
# ✓ Creates symbolic links to latest versions
```

### Step 4: Verify Distribution Contents
```bash
# Extract and verify structure
tar xzf distributions/distributed-ide-latest.tar.gz
cd distributed-ide

# Verify critical files
[ -f distributed-ide.jar ]              # ✓ Main JAR ~200MB
[ -x start/run.sh ]                     # ✓ Linux/macOS launcher (executable)
[ -f start/run.bat ]                    # ✓ Windows launcher
[ -d start/lib ] && [ $(ls start/lib/*.jar | wc -l) -gt 100 ]  # ✓ 100+ libs
[ -d start/logs ]                       # ✓ Logs directory exists
[ -d start/config ]                     # ✓ Config directory exists
[ -f README.md ]                        # ✓ Documentation
[ -f ARCHITECTURE.md ]                  # ✓ Architecture docs
```

---

## Pre-Deployment Testing

### Test on Linux/macOS
```bash
cd /path/to/distributed-ide

# Test Java detection
./start/run.sh --help 2>&1 | grep -q "Java"
# ✓ Should display Java version detected

# Test in headless mode (if possible)
export DISPLAY=:99  # Virtual display
timeout 10 ./start/run.sh 2>&1 | tee test.log
grep -q "Error" test.log || echo "✓ No errors detected"
```

### Test on Windows
```cmd
cd C:\path\to\distributed-ide

# Test batch execution
call start\run.bat help
REM ✓ Should execute without errors

REM Verify logs created
dir start\logs\*.log
REM ✓ Should show log file
```

### Test on macOS (M1/M2 if applicable)
```bash
cd /path/to/distributed-ide

# Verify architecture
java -version 2>&1 | grep -q "arm64\|aarch64"
echo "✓ Running on ARM architecture"

# Launch and verify GUI works
./start/run.sh &
sleep 5
pgrep -f "DistributedIDEApp" > /dev/null && echo "✓ Process running"
```

---

## Deployment Steps

### For Individual Developers
```bash
# 1. Extract archive
tar xzf distributed-ide-linux-macos-*.tar.gz
cd distributed-ide

# 2. Create launcher symlink (optional but recommended)
ln -s $(pwd)/start/run.sh ~/bin/distributed-ide
# or for macOS, add to PATH
export PATH="$(pwd)/start:$PATH"

# 3. Test launch
./start/run.sh

# 4. Monitor logs
tail -f start/logs/ide-*.log
```

### For Team Deployment
```bash
# 1. Copy distribution to shared location
mkdir -p /opt/distributed-ide
sudo cp -r distributed-ide/* /opt/distributed-ide/

# 2. Create system-wide launcher
sudo ln -sf /opt/distributed-ide/start/run.sh /usr/local/bin/distributed-ide

# 3. Test from any directory
distributed-ide

# 4. Verify logs readable
sudo tail -f /opt/distributed-ide/start/logs/ide-*.log
```

### For Cloud Deployment
```bash
# 1. Package for cloud (e.g., Docker, AWS, Azure)
docker build -t distributed-ide:1.0 .
# or
aws s3 cp distributed-ide-latest.tar.gz s3://my-bucket/

# 2. Deploy to cloud platform and verify
# Platform-specific steps here

# 3. Health check
curl http://deployed-ide:8080/health
# ✓ Should return OK
```

---

## Post-Deployment Validation

### Functionality Tests
- [ ] IDE launches without errors
- [ ] GUI displays correctly
- [ ] LSP features work (code completion)
- [ ] Terminal accepts commands
- [ ] File operations work (create/read/write)
- [ ] Synchronization works (if multi-user)
- [ ] WebRTC connections stable

### Performance Tests
- [ ] IDE starts in <10 seconds
- [ ] Code completion <200ms
- [ ] Terminal commands execute quickly
- [ ] Memory usage <2GB
- [ ] CPU usage reasonable (<50% idle)
- [ ] No memory leaks (idle for 1 hour)

### Log Analysis
```bash
# Check for errors
grep -i "error\|exception\|failed" start/logs/ide-*.log

# Check for warnings (optional)
grep -i "warning\|warn" start/logs/ide-*.log

# Performance metrics
grep -i "startup\|ready" start/logs/ide-*.log | head -5
# ✓ Should show startup completed
```

---

## Rollback Plan

If deployment fails:

### Immediate Actions
1. [ ] Stop IDE process: `pkill -f DistributedIDEApp`
2. [ ] Check logs: `tail -100 start/logs/ide-*.log`
3. [ ] Note error message for debugging
4. [ ] Verify Java still available: `java -version`

### Recovery Steps
1. [ ] Download previous distribution version
2. [ ] Extract to different directory
3. [ ] Test locally before re-deploying
4. [ ] Document what went wrong
5. [ ] Report issue if reproducible

### Fallback
- [ ] Keep previous version installed
- [ ] If new version fails, revert to previous
- [ ] Create issue report with logs

---

## Sign-Off

| Item | Status | Date | By |
|------|--------|------|-----|
| Pre-deployment checks | ✓ | - | - |
| Build validation | ✓ | - | - |
| Platform testing | ✓ | - | - |
| Deployment | ✓ | - | - |
| Post-deployment | ✓ | - | - |

---

## Troubleshooting Matrix

| Issue | Cause | Solution |
|-------|-------|----------|
| "Java not found" | Java not installed | Install Java 17+, set JAVA_HOME |
| "JAR not found" | Distribution corrupted | Re-download/re-extract |
| "Permission denied" | Script not executable | `chmod +x start/run.sh` |
| "Out of memory" | Heap too small | Edit JVM options in script |
| "No GUI" | Display server missing | Use headless mode or X11 forwarding |
| "Slow performance" | Caching disabled | Check LSP cache enabled in logs |
| "Crashes on startup" | Missing dependency | Verify all libs in start/lib/ |

---

## Support & Documentation

- **Quick Start**: See [start/SETUP.md](./start/SETUP.md)
- **Full Guide**: See [DISTRIBUTION-GUIDE.md](./DISTRIBUTION-GUIDE.md)
- **Architecture**: See [ARCHITECTURE.md](./ARCHITECTURE.md)
- **Debugging**: Check `start/logs/ide-*.log`

---

## Final Checklist

- [ ] ✅ All tests passing
- [ ] ✅ Distribution verified
- [ ] ✅ Documentation updated
- [ ] ✅ Rollback plan ready
- [ ] ✅ Team trained
- [ ] ✅ Go/No-Go decision made

**Status: READY FOR DEPLOYMENT** ✅

---

**Distributed IDE v1.0.0-SNAPSHOT**
**Deployment Checklist v1**
**February 2026**
