# ⚡ QUICK REFERENCE - Système d'Exécution de Projets

---

## 🎯 TL;DR

L'IDE peut maintenant détecter et exécuter automatiquement:
- ✅ Projets Maven (`pom.xml`)
- ✅ Projets Java Simple (avec `ExJavaConfig/launch.json`)
- ✅ Projets Python (avec `ExPython/launch.json`)

---

## 📋 QUICK SETUP

### Pour un Projet Java Simple

**Structure:**
```
my-project/
├── src/
│   └── Main.java
├── lib/
│   └── (optional JARs)
├── bin/                    ← Créé automatiquement
└── ExJavaConfig/
    └── launch.json        ← CRÉER CECI
```

**Créer `ExJavaConfig/launch.json`:**
```json
{
  "mainClass": "Main",
  "vmArgs": "-Xmx512m",
  "programArgs": "",
  "classPathExtra": "./lib/*"
}
```

**Run**: Clique "Run" → IDE compile + exécute ✓

---

### Pour un Projet Python

**Structure:**
```
my-project/
├── main.py
├── other.py
└── ExPython/
    └── launch.json        ← CRÉER CECI
```

**Créer `ExPython/launch.json`:**
```json
{
  "mainFile": "main.py",
  "args": "",
  "pythonPath": "python3"
}
```

**Run**: Clique "Run" → IDE exécute ✓

---

### Pour un Projet Maven

**C'est tout!** L'IDE détecte automatiquement `pom.xml`

**Run**: Clique "Run" → `mvn exec:java` ✓

---

## 🎮 COMMANDES

| Action | Shortcut | Effet |
|--------|----------|-------|
| **Run** | Ctrl+Alt+X | Compile + Execute |
| **Stop** | Ctrl+Shift+X | Arrête l'exécution |

---

## 📊 launch.json Fields

### Java Simple

| Field | Required | Example | Notes |
|-------|----------|---------|-------|
| `mainClass` | ✓ | `com.example.Main` | Package + classname |
| `vmArgs` | ✗ | `-Xmx512m` | JVM options |
| `programArgs` | ✗ | `arg1 arg2` | Program arguments |
| `classPathExtra` | ✗ | `./lib/*` | Extra JARs |

### Python

| Field | Required | Example | Notes |
|-------|----------|---------|-------|
| `mainFile` | ✓ | `main.py` | Relative path |
| `args` | ✗ | `--port=8080` | CLI arguments |
| `pythonPath` | ✗ | `python3` | Python binary |

---

## ⚡ PERFORMANCE

- **Type Detection**: Cache 5 secondes (~95% faster)
- **Stream Reading**: Parallèle (no deadlock)
- **UI**: Jamais bloquée (async)

---

## 🐛 TROUBLESHOOTING

### "Unable to detect project type"
→ Créer `ExJavaConfig/launch.json` ou `ExPython/launch.json`

### "Compilation failed"
→ Vérifier les erreurs dans l'output tab

### "Main file not found"
→ Vérifier le chemin dans `launch.json`

### "java: command not found"
→ Installer Java ou ajouter au PATH

---

## 📝 EXAMPLE FILES

**Java Simple:**
- [example-java-simple/ExJavaConfig/launch.json](./example-java-simple/ExJavaConfig/launch.json)

**Python:**
- [example-python/ExPython/launch.json](./example-python/ExPython/launch.json)

---

## 📚 DOCUMENTATION COMPLÈTE

- **USER GUIDE**: [PROJECT-EXECUTION-GUIDE.md](./PROJECT-EXECUTION-GUIDE.md)
- **ARCHITECTURE**: [ARCHITECTURE-EXECUTION.md](./ARCHITECTURE-EXECUTION.md)
- **IMPLEMENTATION**: [IMPLEMENTATION-GUIDE.md](./IMPLEMENTATION-GUIDE.md)
- **SUMMARY**: [COMPLETION-SUMMARY.md](./COMPLETION-SUMMARY.md)

---

## ✅ CHECKLIST

Avant de lancer "Run":

- [ ] Projet ouvert
- [ ] Pour Java Simple: `ExJavaConfig/launch.json` créé
- [ ] Pour Python: `ExPython/launch.json` créé
- [ ] Pour Maven: `pom.xml` présent

---

## 🚀 LANCER UN PROJET

```
1. Ouvrir dossier projet
2. Créer launch.json si needed
3. Cliquer "Run" (Ctrl+Alt+X)
4. Voir output en temps réel
5. Cliquer "Stop" (Ctrl+Shift+X) pour arrêter
```

---

**FAIT! Profitez! 🎉**
