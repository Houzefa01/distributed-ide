# 🎨 UI/UX REFACTORIZATION PLAN

## Priority 1: CRITICAL

### 1.1 Coloration Syntaxique
- **Problème**: Styles CSS définis mais application incohérente
- **Fix**: Vérifier et améliorer la pipeline complète
- **Fichiers**: CodeEditor.java, SyntaxHighlighter.java, ide-*.css

### 1.2 Gestion Tabs  
- **Problème**: EditorTabPaneView code mort + EditorManager non cohérent
- **Fix**: Éliminer duplication, fixer proprement fermeture + dirty state
- **Fichiers**: EditorManager.java, MainLayout.java, ide-*.css

### 1.3 Save/AutoSave
- **Problème**: Non vérifié, pas de feedback
- **Fix**: Ajourer validation + indication visuelle
- **Fichiers**: MainLayoutController.java, StatusBarView.java

## Priority 2: HIGH

### 2.1 Raccourcis Clavier
- Ctrl+W (Close Tab) ✅ probablement présent
- Ctrl+Tab (Switch Tabs) ✅ probablement présent  
- Ctrl+/ (Toggle Comment) ❌ absent
- Ctrl+Shift+P (Command Palette) ❌ absent
- Ctrl+P (Quick Open) ❌ absent

### 2.2 Performance UI
- Pas de virtual scrolling sur grands projets
- Pas de debounce sur events lourds
- Pas de memory cleanup

## Priority 3: MEDIUM

### 3.1 Restauration Session
- Pas de sauvegarde fichiers ouverts

### 3.2 Barre Statut
- Language display
- Encodage
- CRDT status

---

## ÉTAPES DE CORRECTION

1. Fixer CodeEditor + application des styles
2. Nettoyer EditorManager + tabs
3. Vérifier save  
4. Ajouter raccourcis clavier essentiels
5. Optimiser perf
6. Tester complet

**Total: 4-6 heures**
