# 🔍 AUDIT UI/UX COMPLET - DISTRIBUTED IDE

**Date**: 23 février 2026  
**Status**: En cours  
**Objectif**: Refactoriser l'UI vers un IDE professionnel (VS Code-like)

---

## 📋 RÉSUMÉ EXÉCUTIF

L'IDE a une bonne **architecture de base** mais souffre de plusieurs problèmes d'implémentation et d'intégration :

| Catégorie | Sévérité | Problème | Impact |
|-----------|----------|---------|--------|
| Coloration syntaxique | 🔴 CRITIQUE | Noir & blanc uniquement | Très mauvaise UX |
| Gestion tabs | 🟠 HAUTE | TextArea vs CodeEditor confusion | Incohérence |
| Thème | 🟡 MOYENNE | CSS appliqué mais pas complet | UI incohérente |
| Save/AutoSave | 🟠 HAUTE | Pas vérifiée/testée | Perte de données |
| Performance | 🟡 MOYENNE | Pas d'optimisation UI visible | Potential freezes |
| Raccourcis clavier | 🟡 MOYENNE | Incomplets | Productivité ↓ |
| État application | 🟠 HAUTE | Pas de restauration session | Frustrant |

---

## 🔥 PROBLÈMES DÉTECTÉS

### 1️⃣ COLORATION SYNTAXIQUE CASSÉE

**Symptôme**: Tout est noir & blanc  
**Cause Probable**: CSS styles manquants pour les tokens de syntaxe  
**Localisation**: CodeEditor + themes CSS

**Code actuel**:
```java
// CodeEditor.java applique highlighting:
StyleSpans<Collection<String>> highlighting = SyntaxHighlighter.computeHighlighting(text, language);
// mais... où sont les CSS styles pour .keyword, .string, .comment ?
```

**CSS Issue**:
```css
/* Dans ide-dark.css - CES STYLES MANQUENT ! */
.keyword { -fx-fill: red; }    /* ❌ N'existe pas */
.string { -fx-fill: green; }   /* ❌ N'existe pas */
.comment { -fx-fill: blue; }   /* ❌ N'existe pas */
```

**Impact**: Même si le tokenizer fonctionne, rien n'est rendu visuellement.

---

### 2️⃣ DEUX SYSTÈMES D'ÉDITEURS EN CONFLIT

**Problème**: Deux implémentations incompatibles coexistent:

- **EditorTabPaneView.java** (ligne 51): Utilise `TextArea` basique
  - Pas de coloration syntaxique
  - Pas de minimap
  - Pas de ligne numbers avancée

- **EditorManager.java** (ligne 83): Utilise `CodeEditor` complète
  - Avec coloration syntaxique
  - Avec minimap
  - Avec ligne numbers professionnelle

**Question**: Lequel est activement utilisé?  
→ À vérifier dans MainLayoutController.java

**Impact**: Incohérence, confusion, pas d'utilisation optimale des ressources.

---

### 3️⃣ CSS THÈME INCOMPLET

**Trouvé**: `/resources/themes/ide-dark.css` (1451 lignes) - bien structuré  
**Manquant**: Styles pour tokens de syntaxe

```css
/* Besoins identifiés */
.keyword { }           /* Mots-clés Java/Python/JS */
.string { }            /* Chaînes de caractères */
.comment { }           /* Commentaires */
.number { }            /* Littéraux numériques */
.classname { }         /* Noms de classe */
.method { }            /* Noms de méthodes */
.annotation { }        /* Annotations/Décorateurs */
.type { }              /* Types primitifs */
.bracket-match { }     /* Bracket matching */
```

---

### 4️⃣ GESTION TABS FRAGILE

**Problème 1**: Fermeture non propre  
```java
// EditorTabPaneView.java ligne 68
tab.setOnCloseRequest(e -> {
    pathToTab.remove(pathStr);
    pathToEditor.remove(pathStr);  // ❌ Pas d'appel dispose()
});
// Resources non nettoyées → fuite mémoire potentielle!
```

**Problème 2**: Pas d'indication fichier modifié  
```java
// EditorManager.java ligne 179 - utilise "● "
tab.setText("● " + tab.getText());  // ❌ Hackish, pas de CSS proper
// Devrait: label visuel cohérent, maybe asterisk, colored dot, etc.
```

**Problème 3**: Pas de support Ctrl+W / Middle Click  
→ À rechercher dans les handlers

---

### 5️⃣ AUTO-SAVE NON VÉRIFIÉ

**Chemin du code**:
- MainLayoutController.java → save logic
- FileService.java → disk I/O
- Pas d'indication visuelle de la sauvegarde
- Pas de vérification si ça fonctionne réellement

**Questions sans réponse**:
- Le fichier est-il vraiment écrit sur le disque?
- Y-a-t-il un conflit CRDT lors du save?
- La sauvegarde bloque-t-elle l'UI?

---

### 6️⃣ RACCOURCIS CLAVIER INCOMPLETS

**Attendus** (standard IDE):
- [x] Ctrl+S (Save) - probablement présent
- [x] Ctrl+Shift+S (Save All) - probablement présent
- [ ] Ctrl+P (Quick Open) - à vérifier
- [ ] Ctrl+F (Find) - existe mais qualité?
- [ ] Ctrl+H (Find & Replace) - à vérifier
- [ ] Ctrl+/ (Toggle Comment) - à vérifier
- [ ] Ctrl+Tab (Next Tab) - à vérifier
- [ ] Ctrl+Shift+Tab (Prev Tab) - à vérifier
- [ ] Ctrl+W (Close Tab) - à vérifier
- [ ] Ctrl+Shift+P (Command Palette) - probablement absent
- [ ] Alt+Up/Down (Move Line) - à vérifier
- [ ] Ctrl+D (Quick Select) - à vérifier

---

### 7️⃣ PAS DE RESTAURATION DE SESSION

**Attendu**:
- L'IDE se souvient des fichiers ouverts
- Restaure l'onglet actif
-restore scrollPos

**Actuel**: À vérifier

---

### 8️⃣ STATE SYNC INCOHÉRENT

**Problèmes potentiels**:
- Tab ouvert mais pas dans CRDT
- CRDT mis à jour mais éditor pas refresh
- Fichier fermé mais listener pas nettoyé
- Remote cursor pas render correctement

---

## 🏗️ ARCHITECTURE ACTUELLE

```
DistributedIDEApp.java
  └─ MainLayout.java (structure UI)
      ├─ MainLayoutController.java (ligic)
      ├─ EditorManager.java (multi-tab + CodeEditor)
      │   └─ CodeEditor.java (professionnel, avec highlight)
      │       ├─ SyntaxHighlighter.java ✅ (tokens)
      │       ├─ SyntaxErrorChecker.java ✅ (coloration erreurs)
      │       └─ LineNumberFactory ✅ (numéros lignesleur, erreurs)
      │
      └─ Services:
          ├─ FileService (lecture/écriture fichiers)
          ├─ SyncService (CRDT sync)
          ├─ ThemeManager ✅ (thèmes)
          ├─ LSPManager (diagnostics)
          └─ etc.
```

**Observations**:
- ✅ CodeEditor bien structuré
- ✅ SyntaxHighlighter complet
- ❌ CSS tokens manquants
- ❌ Pas d'intégration complète CodeEditor ↔ Sync

---

## 📝 TESTS À FAIRE

### ✅ Workflow basique
- [ ] New File → crée réellement?
- [ ] Open File → affiche contenu?
- [ ] Edit → CRDT sync?
- [ ] Save → écrit disque?
- [ ] Close Tab → propre?

### ✅ Coloration
- [ ] Ouvrir Java file → coloration?
- [ ] Ouvrir Python file → coloration?
- [ ] Keywords = couleur?
- [ ] Strings = couleur?
- [ ] Comments = couleur?

### ✅ Tabs
- [ ] Modifier fichier → marque "*"?
- [ ] Fermer tab → listener nettoyé?
- [x] Ctrl+W → ferme?
- [ ] Middle click → ferme?

### ✅ Performance
- [ ] Gros fichier (10MB) → pas de freeze?
- [ ] 50 tabs ouverts → performance?
- [ ] Real-time collab → latency OK?

---

## 🎯 PLAN DE CORRECTION

### Phase 1: Coloration syntaxique ✅
1. Ajouter CSS pour tokens (keyword, string, comment, etc.)
2. Vérifier appliqué dans tous les thèmes
3. Tester sur Java, Python, JS, JSON

### Phase 2: Gestion tabs
1. Fixer fermeture propre (dispose)
2. Ajouter indication fichier modifié (CSS)
3. Support clavier (Ctrl+W, Ctrl+Tab)
4. Support souris (Middle click)

### Phase 3: Auto-save
1. Vérifier logique marche réellement
2. Ajouter indication visuelle
3. Tester conflit CRDT

### Phase 4: Raccourcis clavier
1. Implémenter Ctrl+P (Quick Open)
2. Implémenter Ctrl+/ (Toggle Comment)
3. Implémenter Ctrl+Shift+P (Command Palette basique)
4. Autres raccourcis manquants

### Phase 5: Ergonomie
1. Restauration session
2. Barre statut améliorée
3. Theme toggle facile
4. Settings accessible

### Phase 6: Performance
1. Virtualisation si grosse arborescence
2. Debounce events lourds
3. Cleanup listeners
4. Memory profiling

---

## 📂 FICHIERS À MODIFIER

**Priorité HAUTE**:
1. `ide-dark.css` (+ autres thèmes) - ajouter tokens styles
2. `CodeEditor.java` - vérification + optimisation
3. `EditorManager.java` - fixer gestion tabs, raccourcis clavier
4. `MainLayoutController.java` - logique save/autosave

**Priorité MOYENNE**:
5. `ThemeManager.java` - vérifier appliqué correctement
6. `SyntaxHighlighter.java` - audit logique
7. `ProjectTreeView.java` - perf sur gros projet

**Priorité BASSE**:
8. Autres services UI

---

## ✅ PROCHAINES ÉTAPES

1. **Lire** MainLayoutController.java pour comprendre flow complet
2. **Identifier** quel système d'editor est vraiment utilisé
3. **Ajouter** CSS tokens styles
4. **Tester** coloration syntaxique
5. **Fixer** gestion tabs
6. **Implémenter** raccourcis clavier
7. **Optimiser** performance
8. **Valider** tout fonctionne

---

**Durée estimée transition**:
- Audit complet: 30 min ✅ (en cours)
- Corrections: 3-4 heures
- Tests: 1 heure
- **Total**: ~5 heures pour IDE professionnel

