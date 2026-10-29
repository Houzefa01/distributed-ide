# 🚀 IDE SETTINGS — QUICK REFERENCE GUIDE FOR DEVELOPERS

**TL;DR:** Review + improve IDE settings UX/UI  
**Time:** 1-2 weeks for full implementation  
**Effort:** 1 senior dev fulltime  

---

## 📋 WHAT NEEDS FIXING?

### Critical Issues (Fix First)
```
1. ❌ No preview of setting changes before applying
   → Fix: Add real-time preview panel to SettingsDialog

2. ❌ 3 themes have WCAG contrast failures
   → Fix: Adjust colors, unify CSS to use base theme

3. ❌ 6 essential IDE shortcuts missing
   → Fix: Add Ctrl+B, Ctrl+J, Ctrl+Shift+M, etc.

4. ❌ Settings changes not persisted on workspace restart
   → Fix: Save/restore workspace state

5. ❌ Synchronous I/O freezes UI on settings save
   → Fix: Move I/O to background thread (CompletableFuture)
```

### Medium Issues (Fix After)
```
1. ⚠️ CSS 60% duplication (5 independent theme files)
   → Fix: Create ide-theme-base.css, other themes import

2. ⚠️ No validation on settings input
   → Fix: Add SettingsValidator with rules

3. ⚠️ No automatic dark mode detection
   → Fix: SystemThemeDetector for Windows/macOS/Linux
```

---

## 🗂️ FILE STRUCTURE

### New Files to Create
```
javafx-client/src/main/java/
├── com/distributed_ide/javafx_client/
│   ├── service/
│   │   ├── SettingsPreviewManager.java     [NEW]
│   │   ├── SettingsListener.java           [NEW]
│   │   └── SettingsChangeEvent.java        [NEW]
│   │
│   ├── settings/validation/
│   │   ├── ValidationRule.java             [NEW]
│   │   └── SettingsValidator.java          [NEW]
│   │
│   └── theme/
│       ├── SystemThemeDetector.java        [NEW]
│       └── ThemeValidator.java             [NEW]

javafx-client/src/main/resources/themes/
├── ide-theme-base.css                      [NEW]
├── ide-dark.css                            [UPDATE] @import base
├── ide-light.css                           [UPDATE] @import base
├── ide-nord.css                            [UPDATE] @import base
├── ide-dracula.css                         [UPDATE] @import base
└── modern.css                              [UPDATE] @import base
```

### Files to Modify
```
SettingsDialog.java          ← Add preview pane + validation listeners
SettingsManager.java         ← Add listeners + async I/O
MainLayoutController.java    ← Add 6 missing keybindings
DistributedIDEApp.java       ← Add auto dark mode detection
IDEConfig.java               ← (Optional) add new configs
```

---

## 🎯 PRIORITY 1: QUICK WINS (Week 1)

### Task 1.1: Live Preview [2-3h]
**Goal:** Real-time preview of settings before applying

**Steps:**
1. Create `SettingsPreviewManager.java`
   - Methods: `previewFontSize()`, `previewTheme()`, etc.
   - Use `Platform.runLater()` for thread safety

2. Update `SettingsDialog.java`
   - Add `.valueProperty().addListener()` to all controls
   - Call `previewManager.previewSetting()` on change
   - Add preview pane next to editor tab

3. Test
   - Open Settings → Adjust font size → See preview update
   - Change theme → Syntax highlighting preview updates
   - Click Cancel → Changes discarded
   - Click OK → Changes persisted

**Code Template:**
```java
// In SettingsDialog constructor
fontSizeSpinner.valueProperty().addListener((obs, old, newVal) ->
    previewManager.previewSetting("editor.fontSize", newVal)
);

// Preview pane in createEditorTab()
VBox preview = new VBox();
preview.setStyle("-fx-border-color: #cccccc;");
// Add code area with syntax highlighting
return new HBox(content, preview);
```

**Files:** `SettingsPreviewManager.java`, `SettingsDialog.java`  
**Commit:** `feat: add live preview to settings dialog`

---

### Task 1.2: Validation [1-2h]
**Goal:** Prevent invalid settings values

**Steps:**
1. Create `SettingsValidator.java`
   - IntRangeRule (fontSize 8-32, tabSize 2-8)
   - ChoiceRule (theme in {dark, light, nord, dracula, modern})
   - PatternRule (excludePatterns matches regex)

2. Create `ValidationRule<T>` interface
   - Methods: `validate(T value)`, `getErrorMessage()`

3. Update `SettingsDialog.java`
   - Add `focusedProperty().addListener()` to fields
   - Call `validateField()` on focus loss
   - Show error dialogs for invalid input

4. Test
   - Try setting fontSize=50 → Error alert
   - Try setting tabSize=10 → Error alert
   - Valid values → No error

**Code Template:**
```java
// In SettingsValidator static init
registerRule("editor.fontSize",
    new IntRangeRule(8, 32, "Font size must be 8-32pt"));

// In SettingsDialog
fontSizeSpinner.focusedProperty().addListener((obs, was, isFocused) -> {
    if (!isFocused) {
        validateField("editor.fontSize", fontSizeSpinner.getValue());
    }
});
```

**Files:** `SettingsValidator.java`, `ValidationRule.java`, `SettingsDialog.java`  
**Commit:** `feat: add settings input validation`

---

### Task 1.3: Auto Dark Mode [1-2h]
**Goal:** Auto-detect and apply system theme on first launch

**Steps:**
1. Create `SystemThemeDetector.java`
   - Method: `detectSystemTheme()` → DARK | LIGHT | UNKNOWN
   - Detect Windows: Registry lookup
   - Detect macOS: `defaults read AppleInterfaceStyle`
   - Detect Linux: `gsettings get org.gnome.desktop.interface gtk-application-prefer-dark-theme`

2. Update `DistributedIDEApp.start()`
   - Get current theme from SettingsManager
   - If null/empty: auto-detect and save
   - Apply detected theme

3. Update `SettingsDialog.java`
   - Add "Follow system preference" checkbox
   - Add "Sync with System Now" button

4. Test on Windows/macOS/Linux
   - First launch with system dark mode → IDE dark mode applied
   - Click "Sync Now" with light mode → IDE light mode applied

**Code Template:**
```java
// In DistributedIDEApp.start()
String theme = settingsManager.getString("theme.current", null);
if (theme == null) {
    SystemThemeDetector.SystemTheme st = SystemThemeDetector.detectSystemTheme();
    String ideTheme = st == DARK ? "ide-dark" : "ide-light";
    settingsManager.put("theme.current", ideTheme);
}

// In SystemThemeDetector.detec tWindowsTheme()
String result = executeCommand("reg", "query",
    "HKEY_CURRENT_USER\\...\\AppsUseLightTheme");
return result.contains("0") ? DARK : LIGHT;
```

**Files:** `SystemThemeDetector.java`, `DistributedIDEApp.java`, `SettingsDialog.java`  
**Commit:** `feat: auto-detect system dark/light mode`

---

### Task 1.4: Theme Unification [7-8h]
**Goal:** Reduce CSS duplication (5,000 → 2,000 lines)

**Steps:**
1. Create `ide-theme-base.css` [NEW]
   - Define all CSS rules (button, tab, text-input, etc.)
   - Use variables: `-ide-bg-primary`, `-ide-fg-primary`, etc.
   - WCAG AAA contrast ratios verified

2. Update all 5 theme files
   - Add `@import url("ide-theme-base.css");`
   - Remove duplicate rules
   - Keep ONLY color variable overrides

3. Create `ThemeValidator.java`
   - Method: `validateTheme()` → ValidationReport
   - Check all critical color pairs
   - Ensure >= 4.5:1 contrast (AA)
   - Report WCAG failures

4. Test
   - Compile CSS without errors
   - All 5 themes render correctly
   - Run `ThemeValidator` on each theme
   - Fix any WCAG failures

**Code Template:**
```css
/* ide-theme-base.css */
.button {
    -fx-background-color: -ide-bg-input;
    -fx-text-fill: -ide-fg-primary;
    /* Uses variables, no hardcoded colors */
}

/* ide-dark.css */
@import url("ide-theme-base.css");
.root {
    -ide-bg-input: #3c3c3c;
    -ide-fg-primary: #cccccc;
    /* Only color overrides */
}
```

```java
// In ThemeValidator
double ratio = calculateContrast("#cccccc", "#1e1e1e");
if (ratio < 4.5) {
    report.fail("Primary text on primary BG", ratio, 4.5);
}
```

**Files:** `ide-theme-base.css`, all `ide-*.css`, `ThemeValidator.java`  
**Commit:** `refactor: unify themes with base.css, fix WCAG`

---

## 📈 PRIORITY 2: ADVANCED (Week 2)

### Task 2.1: Event-Driven Config [2-3h]
**Goal:** Thread-safe settings updates with auto-persistence

**Steps:**
1. Create `SettingsListener.java` interface
   - Method: `onSettingChanged(SettingsChangeEvent)`

2. Create `SettingsChangeEvent.java`
   - Fields: key, oldValue, newValue, timestamp
   - Helper methods: `isThemeChange()`, `isFontChange()`, etc.

3. Update `SettingsManager.java`
   - Add `listeners: List<SettingsListener>`
   - Update `put()` to fire events
   - Move I/O to background thread (ExecutorService)
   - Return `CompletableFuture<Void>` from `saveAsync()`

4. Components subscribe to settings
   - `ThemeManager implements SettingsListener`
   - Subscribe in constructor: `settingsManager.addListener(this)`
   - React to relevant settings changes

5. Test
   - Change font size → EditorManager updates
   - Change theme → ThemeManager reloads CSS
   - Rapid setting changes → No UI freeze

**Code Template:**
```java
// SettingsManager
public void put(String key, Object value) {
    Object oldValue = get(key);
    putInternal(key, value);
    
    SettingsChangeEvent event = new SettingsChangeEvent(key, oldValue, value);
    listeners.forEach(l -> l.onSettingChanged(event));
    
    saveAsync(); // Non-blocking!
}

public CompletableFuture<Void> saveAsync() {
    return CompletableFuture.runAsync(() -> {
        try {
            MAPPER.writeValue(settingsFile.toFile(), root);
        } catch (IOException e) {
            LOG.error("Failed to save", e);
        }
    }, executor);
}

// ThemeManager
public class ThemeManager implements SettingsListener {
    @Override
    public void onSettingChanged(SettingsChangeEvent event) {
        if ("theme.current".equals(event.getKey())) {
            applyTheme((String) event.getNewValue());
        }
    }
}
```

**Files:** `SettingsListener.java`, `SettingsChangeEvent.java`, `SettingsManager.java`, `ThemeManager.java`  
**Commit:** `refactor: event-driven settings with async I/O`

---

### Task 2.2: Keybindings [4-5h]
**Goal:** Add missing IDE shortcuts

**New Shortcuts:**
```
Ctrl+B          Toggle Explorer sidebar
Ctrl+J          Toggle Terminal
Ctrl+Shift+M    Toggle Activity Bar
Ctrl+K Ctrl+0   Fold all code
Ctrl+K Ctrl+J   Unfold all code
Ctrl+Alt+↑/↓    Duplicate line up/down
```

**Steps:**
1. Update `MainLayoutController.wireKeyboardShortcuts()` (added Ctrl+T theme toggle)
   - Add 6 new cases in main stage `onKeyPressed()`
   - Implement handlers

2. Consider creating `KeyBindingsManager.java`
   - Store all keybindings in map
   - Allow customization (future)

3. Test
   - Ctrl+B → Explorer toggles
   - Ctrl+J → Terminal toggles
   - Ctrl+K Ctrl+0 → Code folds

**Code Template:**
```java
case B -> {
    if (event.isControlDown() && !event.isAltDown()) {
        layout.toggleExplorer();  // or toggleActivityBar
        event.consume();
    }
}

case J -> {
    if (event.isControlDown() && !event.isAltDown()) {
        layout.toggleTerminal();
        event.consume();
    }
}
```

**Files:** `MainLayoutController.java`, (optional) `KeyBindingsManager.java`  
**Commit:** `feat: add 6 missing IDE keybindings`

---

### Task 2.3: Workspace Restoration [4-5h]
**Goal:** Remember open files & positions

**Data to Save:**
```json
{
  "openFiles": ["/path/to/file1.java", "/path/to/file2.java"],
  "activeFile": "/path/to/file1.java",
  "editorStates": {
    "/path/to/file1.java": {
      "scrollLine": 42,
      "caretLine": 50,
      "caretColumn": 15
    }
  }
}
```

**Steps:**
1. Create `WorkspaceState.java` record
2. Create `WorkspaceManager.java`
   - Method: `saveState()` → JSON file
   - Method: `restoreState()` → Load + reopen files
3. Update `DistributedIDEApp`
   - On shutdown: `workspaceManager.saveState()`
   - On startup: `workspaceManager.restoreState()`
4. Update `EditorManager`
   - Track scroll/caret positions per file
   - Restore on file open

**Files:** `WorkspaceState.java`, `WorkspaceManager.java`, `DistributedIDEApp.java`, `EditorManager.java`  
**Commit:** `feat: persist and restore workspace state`

---

## 🧪 TESTING CHECKLIST

### Manual Testing (UI)
- [ ] Open Settings → Adjust all controls → See preview
- [ ] Invalid input (fontSize=50) → Error dialog
- [ ] Change theme → Preview updates + syntax highlighting changes
- [ ] First launch with dark OS → IDE dark theme applied
- [ ] Each theme renders correctly (no visual glitches)

### Unit Testing
- [ ] SettingsValidator: 10+ test cases
  - fontSize: valid (14), invalid (50, 0, -1)
  - fontFamily: valid ("Consolas"), invalid ("Comic Sans")
  - themeColor: valid ("ide-dark"), invalid ("custom-theme")
  
- [ ] SystemThemeDetector
  - Windows: Mock registry (return DARK/LIGHT)
  - macOS: Mock command execution
  - Linux: Mock gsettings command
  
- [ ] SettingsManager
  - Listener notification on change
  - Async save completes
  - Validation errors prevent save

- [ ] SettingsDialog
  - Preview updates on slider change
  - OK button persists + closes dialog
  - Cancel button discards changes
  - Reset button confirms + resets all

### Integration Testing
- [ ] Apply theme → EditorManager respects new font
- [ ] Change auto-save delay → Auto-save interval updates
- [ ] Rapid setting changes → No race conditions
- [ ] Multiple windows → All get setting updates

### Cross-Platform Testing
- [ ] Windows: Keybindings, dark mode detection
- [ ] macOS: Keybindings, dark mode detection
- [ ] Linux: Keybindings, dark mode detection

---

## 📦 BUILD & RELEASE

### Compile Check
```bash
mvn clean compile -q
# Expected: SUCCESS (0 errors, 0 warnings)
```

### Test Execution
```bash
mvn clean test -q
# Expected: ALL PASS
```

### Package Check
```bash
mvn clean package -DskipTests -q
# Expected: distribut ed-ide-1.0.0-SNAPSHOT.jar created
```

### CSS Validation (Optional)
```bash
# Use online CSS validator with ide-theme-base.css
# All 5 theme files should inherit without errors
```

---

## 📝 GIT WORKFLOW

### Branch Strategy
```bash
git checkout -b feat/ide-settings-refactor

# Or separate branches per task:
git checkout -b feat/settings-live-preview
git checkout -b feat/settings-validation
git checkout -b feat/dark-mode-auto
git checkout -b refactor/theme-unification
git checkout -b refactor/event-driven-config
```

### Commit Messages
```
feat: add live preview to settings dialog
feat: add settings input validation framework
feat: auto-detect system dark/light mode
refactor: unify 5 themes into base + overrides
refactor: event-driven settings with async I/O
feat: add 6 missing IDE keybindings
feat: persist and restore workspace state
```

### PR Description Template
```markdown
## 🎯 Goal
[What problem does this solve?]

## 📝 Changes
- [List changes]

## ✅ Testing
- [ ] Manual tested on Windows/macOS/Linux
- [ ] Unit tests pass (X new tests added)
- [ ] No warnings/errors on compile

## 📊 Metrics
- Files modified: X
- Lines added: Y
- CSS duplication reduced: Z%

## 🔗 Related Issues
Closes #123
```

---

## 💾 CONFIGURATION FILES

### settings.json Location
```bash
~/.distributed-ide/settings.json
```

### Default Structure
```json
{
  "editor": {
    "fontSize": 14,
    "fontFamily": "Consolas",
    "tabSize": 4,
    "wordWrap": false,
    "minimap": true,
    "autoSave": false,
    "autoSaveDelaySeconds": 30
  },
  "theme": {
    "current": "ide-dark",
    "autoDetect": false
  },
  "window": {
    "width": 1400,
    "height": 900,
    "maximized": false
  },
  "workspace": {
    "openFiles": [],
    "activeFile": null
  },
  "search": {
    "excludePatterns": "node_modules,target,.git"
  }
}
```

---

## 🚨 POTENTIAL PITFALLS

### 1. CSS Order of Import
```css
/* ❌ WRONG */
.root { -ide-button-color: #007acc; }
@import url("ide-theme-base.css");

/* ✅ CORRECT */
@import url("ide-theme-base.css");
.root { -ide-button-color: #007acc; }
```

### 2. Thread Safety
```java
// ❌ WRONG: blocking main thread
MAPPER.writeValue(file, root);

// ✅ CORRECT: background thread
CompletableFuture.runAsync(() -> MAPPER.writeValue(file, root));
```

### 3. Listener Memory Leaks
```java
// ❌ WRONG: listener never unsubscribed
settingsManager.addListener(this);

// ✅ CORRECT: unsubscribe on close
component.onClose(() -> settingsManager.removeListener(this));
```

### 4. Contrast Calculation
```java
// Remember: formula is (L1+0.05)/(L2+0.05) where L = luminance
// Not just "are colors different?"
// Test with actual color values
```

---

## 📞 KEY CONTACTS

| Role | Purpose | Time |
|------|---------|------|
| Product Owner | Approve priorities | 2h/week |
| QA Lead | Testing + validation | 8h/week |
| Tech Lead | Architecture review | 4h/week |
| UX Designer | A11y + visual polish | 4h/week |

---

## 📚 REFERENCES

### JavaFX
- [javafx.scene.control](https://openjfx.io/javadoc/21/)
- [CSS Reference](https://openjfx.io/javadoc/21/javafx.graphics/javafx/scene/doc-files/cssref.html)
- Platform.runLater() for UI thread safety

### WCAG 2.1
- [Contrast Minimum](https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html)
- [Formula for contrast ratio](https://www.w3.org/TR/WCAG20-TECHS/G17.html)

### Best Practices
- Settings should be non-blocking (async I/O)
- Always validate user input
- Use observer pattern for updates
- Provide real-time feedback (preview)

---

## ✅ SIGN-OFF CHECKLIST

- [ ] All tasks assigned
- [ ] Timeline agreed (Week 1: 16h, Week 2: 20h)
- [ ] Testing strategy defined
- [ ] Git workflow established
- [ ] Code review process clear
- [ ] Ready to start

---

**Ready to Start?** 🚀

Next Step: Pick Task 1.1 (Live Preview) and begin!

Questions? → See **IDE-SETTINGS-FULL-AUDIT.md** for deep dive
