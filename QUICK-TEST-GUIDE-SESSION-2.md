# Quick Test Guide - Tab Management & Keyboard Shortcuts

**Purpose**: Manual verification of Phase 2 improvements (keyboard shortcuts + dirty file indicators)

---

## 🧪 Test 1: Dirty File Indicators

### Test Steps
1. **Start the IDE**
   ```bash
   ./run_distributed_ide.sh
   ```

2. **Open a file**
   - Use File → Open → Select any `.java` or `.py` file
   - Observe the tab shows filename without special indicator

3. **Edit the file**
   - Click in the editor and make a small change (type a character)
   - **Expected**: Tab should show **yellow indicator** (theme-dependent color)
     - Dark theme: Yellow (#f1fa8c)
     - Light theme: Orange (#cc6633)
     - Dracula: Yellow (#f1fa8c)
     - Nord: Golden (#ebcb8b)
     - Modern: Amber (#fbbf24)
   - **Expected**: Tab label text becomes **bold**

4. **Save the file**
   - Press Ctrl+S or File → Save
   - **Expected**: Yellow indicator **disappears**
   - **Expected**: Tab label returns to normal weight

### Acceptance Criteria
- ✅ Dirty indicator appears immediately after first character typed
- ✅ Indicator color matches current theme
- ✅ Indicator disappears after save
- ✅ Multiple dirty tabs show indicator on all of them

---

## ⌨️ Test 2: Keyboard Shortcuts

### Prerequisites
- Open 3 or more files in tabs
- Arrange them: `file1.java`, `file2.py`, `file3.ts` (order matters)

### Test 2a: Ctrl+Tab (Next Tab)
1. Click on `file1.java` tab to make it active
2. Press **Ctrl+Tab**
   - **Expected**: Focus moves to `file2.py` tab
3. Press **Ctrl+Tab** again
   - **Expected**: Focus moves to `file3.ts` tab
4. Press **Ctrl+Tab** again
   - **Expected**: Focus wraps around back to `file1.java` (circular navigation)

### Test 2b: Ctrl+Shift+Tab (Previous Tab)
1. Click on `file3.ts` tab to make it active
2. Press **Ctrl+Shift+Tab**
   - **Expected**: Focus moves to `file2.py` tab
3. Press **Ctrl+Shift+Tab** again
   - **Expected**: Focus moves to `file1.java` tab
4. Press **Ctrl+Shift+Tab** again
   - **Expected**: Focus wraps around to `file3.ts` (circular navigation)

### Test 2c: Ctrl+W (Close Tab)
1. Click on `file2.py` tab to make it active
2. Press **Ctrl+W**
   - **Expected**: `file2.py` tab closes immediately
   - **Expected**: Focus moves to next available tab
3. Press **Ctrl+W** again on the new active tab
   - **Expected**: That tab closes as well
4. Should have only 1 tab remaining

### Test 2d: Ctrl+PageDown / Ctrl+PageUp (Alternative Navigation)
1. Open 3 files again
2. Press **Ctrl+PageDown**
   - **Expected**: Moves to next tab (same as Ctrl+Tab)
3. Press **Ctrl+PageUp**
   - **Expected**: Moves to previous tab (same as Ctrl+Shift+Tab)

### Acceptance Criteria
- ✅ Ctrl+Tab cycles through all tabs forward
- ✅ Ctrl+Shift+Tab cycles through all tabs backward
- ✅ Circular navigation works (wraps at ends)
- ✅ Ctrl+W closes current tab
- ✅ All shortcuts respond immediately (<100ms)
- ✅ No console errors on keyboard events

---

## 🎨 Test 3: Theme Switching

### Test Steps
1. **Open multiple files** with unsaved changes (dirty state)
2. **Navigate to View → Theme** (or settings)
3. **Switch to each theme** and verify dirty indicators:

#### Dark Theme
- [ ] Dirty indicator: Yellow (#f1fa8c)
- [ ] Text color: Yellow
- [ ] Bottom border: Visible yellow line

#### Light Theme
- [ ] Dirty indicator: Orange (#cc6633)
- [ ] Text color: Orange
- [ ] Contrasts well on light background

#### Dracula Theme
- [ ] Dirty indicator: Yellow (#f1fa8c)
- [ ] Matches Dracula palette
- [ ] Good contrast

#### Nord Theme
- [ ] Dirty indicator: Golden (#ebcb8b)
- [ ] Follows Nord color scheme
- [ ] Visible but not jarring

#### Modern Theme
- [ ] Dirty indicator: Amber (#fbbf24)
- [ ] Matches VSCode styling
- [ ] Professional appearance

### Acceptance Criteria
- ✅ All 5 themes show dirty indicator
- ✅ Colors are distinct from tab text
- ✅ Good contrast ratio (>4.5:1)
- ✅ Consistent styling across themes

---

## 📝 Test 4: Resource Cleanup

### Test Steps
1. **Open 20+ files** in tabs
2. **Close all tabs** using Ctrl+W repeatedly
3. **Monitor memory usage** (View → Dev Tools or Activity Monitor)

### Expected Behavior
- No memory leaks (should return to baseline after 1 minute)
- No warnings in console after closing
- No orphaned listeners or threads

### Command to Check (Terminal)
```bash
# Monitor Java process memory if available
jps -l  # Show Java processes
# Use top or Activity Monitor to watch memory
```

### Acceptance Criteria
- ✅ Memory returned after tab closure
- ✅ No console warnings
- ✅ No background threads hanging
- ✅ Highlight threads properly disposed

---

## 🔍 Test 5: Edge Cases

### Edge Case 5a: Single Tab Navigation
1. Open only **1 file** in a tab
2. Press **Ctrl+Tab** → Should stay on same tab (wraps to itself)
3. Press **Ctrl+Shift+Tab** → Should stay on same tab (wraps to itself)

### Edge Case 5b: Empty Tab Pane
1. Close **all** tabs
2. Press **Ctrl+Tab**, **Ctrl+Shift+Tab**, **Ctrl+W**
   - **Expected**: No crashes, graceful no-op

### Edge Case 5c: Rapid Switching
1. Open 3 files
2. Press **Ctrl+Tab** 10 times rapidly
   - **Expected**: Smooth cycling, no lag
3. Press **Ctrl+Shift+Tab** 10 times rapidly
   - **Expected**: Smooth reverse cycling

### Edge Case 5d: Dirty State During Navigation
1. Open 3 files
2. Make file#1 dirty
3. Navigate using Ctrl+Tab while dirty
   - **Expected**: Dirty indicator stays visible
4. Save while on different tab
   - **Expected**: Correct tab's indicator disappears

### Acceptance Criteria
- ✅ No crashes on edge cases
- ✅ Graceful handling of empty/single-tab states
- ✅ Rapid keyboard input handled smoothly
- ✅ Dirty state tracked correctly during navigation

---

## 📊 Test Summary Template

Use this checklist to verify all functionality:

```
Dirty File Indicators:
☐ Appears when file is edited
☐ Shows correct theme color
☐ Disappears after save
☐ Works on multiple dirty files

Keyboard Shortcuts:
☐ Ctrl+Tab navigates forward
☐ Ctrl+Shift+Tab navigates backward
☐ Ctrl+PageDown works (alternative)
☐ Ctrl+PageUp works (alternative)
☐ Ctrl+W closes tab
☐ Circular navigation works
☐ <100ms response time

Theme Support:
☐ Dark theme colors correct
☐ Light theme colors correct
☐ Dracula theme colors correct
☐ Nord theme colors correct
☐ Modern theme colors correct

Resource Management:
☐ Memory cleaned after closures
☐ No console warnings
☐ No orphaned threads

Edge Cases:
☐ Single tab navigation stable
☐ Empty pane handled gracefully
☐ Rapid switching smooth
☐ Dirty state accurate during nav
```

---

## 🐛 Troubleshooting

### Issue: Dirty indicator not appearing
- **Cause**: CSS not loaded or override by another rule
- **Fix**: Check theme CSS files for `.tab-dirty` definitions
- **Verify**: Open browser dev tools (F12) and inspect tab elements

### Issue: Keyboard shortcuts not working
- **Cause**: TabPane may not have focus
- **Fix**: Click on the tab area first, then try shortcut
- **Verify**: Check console for KeyEvent logs

### Issue: Memory leak after closing tabs
- **Cause**: CodeEditor listeners not properly disposed
- **Fix**: Verify `editor.dispose()` is called
- **Verify**: Check for leaked highlighting threads

### Issue: Dirty indicator persists after save
- **Cause**: `markClean()` not called by save handler
- **Fix**: Check save service integration
- **Verify**: Look for sync service callbacks

---

## 📈 Performance Benchmarks

After improvements, expect:

| Metric | Target | Acceptance |
|--------|--------|-----------|
| Tab switch time | <50ms | <100ms |
| Ctrl+W response | <50ms | <100ms |
| Memory per tab | <5MB | <10MB |
| Keyboard latency | <20ms | <50ms |
| Highlighting per KB | <10ms | <50ms |

---

## 📞 Reporting Issues

If tests fail, please include:
1. Step-by-step reproduction
2. Theme being used
3. Number of tabs/files open
4. Console output (if any errors)
5. Memory usage before/after
6. Java version (`java -version`)

