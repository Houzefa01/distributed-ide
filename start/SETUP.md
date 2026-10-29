# Distributed IDE - Distribution Package

## 🚀 Démarrage Rapide

### Linux / macOS
```bash
cd distributed-ide
./start/run.sh
```

### Windows
```cmd
cd distributed-ide
start\run.bat
```

## 📋 Prérequis

- **Java 17+** (Java 21 recommandé pour les meilleures performances)
- OpenJDK ou Oracle JDK
- Linux, macOS ou Windows

## 🔍 Détection Java Automatique

Les scripts de lancement ont une détection automatique de Java:

1. Vérifient la variable d'environnement `$JAVA_HOME`
2. Recherchent java dans le PATH système
3. Vérifient les emplacements par défaut (macOS : `/Library/Java/`, Windows : `C:\Program Files\Java\`)

### Configuration Manuelle (Optionnel)

Si Java n'est pas trouvé automatiquement:

```bash
# Linux / macOS
export JAVA_HOME=/path/to/jdk
./start/run.sh

# Windows (CMD)
set JAVA_HOME=C:\path\to\jdk
start\run.bat

# Windows (PowerShell)
$env:JAVA_HOME = "C:\path\to\jdk"
.\start\run.bat
```

## 📁 Structure de la Distribution

```
distributed-ide/
├── distributed-ide.jar         # JAR principal de l'application
├── start/
│   ├── run.sh                  # Script de lancement (Linux/macOS)
│   ├── run.bat                 # Script de lancement (Windows)
│   ├── lib/                    # Toutes les dépendances Maven
│   ├── logs/                   # Logs d'exécution
│   └── config/                 # Fichiers de configuration
├── README.md                   # Documentation
└── ARCHITECTURE.md             # Architecture du projet
```

## ⚙️ Options JVM

Les scripts de lancement configurent automatiquement:

| Paramètre | Valeur | Description |
|-----------|--------|-------------|
| **Heap Min** | 512M | Mémoire initiale |
| **Heap Max** | 2048M | Mémoire maximale |
| **GC** | G1GC | Optimisé pour les pauses courtes |
| **Encoding** | UTF-8 | Support des caractères internationaux |

### Personnalisation des Options JVM

Editez les scripts `start/run.sh` ou `start/run.bat` et modifiez la section `JVM_OPTS`.

**Exemple** (Linux/macOS):
```bash
JVM_OPTS="$JVM_OPTS -Xms1024m"  # Augmente heap min à 1GB
JVM_OPTS="$JVM_OPTS -Xmx4096m"  # Augmente heap max à 4GB
```

## 📊 Fichiers de Logs

Les logs d'exécution sont sauvegardés dans:
```
start/logs/ide-YYYYMMDD-HHMMSS.log
```

## 🔧 Dépannage

### "Java not found"
**Solution:**
1. Installez Java 17+ : [AdoptOpenJDK](https://adoptopenjdk.net/) ou [Eclipse Temurin](https://adoptium.net/)
2. Définissez `$JAVA_HOME` pointant vers votre installation JDK
3. Relancez le script

### Performance Lente
**Solution:**
1. Augmentez `Xmx` dans les options JVM (ex: `Xmx4096m` pour 4GB)
2. Vérifiez que vous utilisez Java 21
3. Vérifiez les ressources système (CPU, mémoire disponible)

### Utilisation Mémoire Élevée
**Solution:**
1. Les caches LSP peuvent croître - ils s'auto-nettoient après 1000 entrées
2. Terminal peut accumuler des processus - relancez l'IDE
3. Vérifiez l'onglet "Memory" dans les paramètres de développement

## 📚 Documentation

- [README.md](./README.md) - Vue d'ensemble du projet
- [ARCHITECTURE.md](./ARCHITECTURE.md) - Architecture détaillée
- [start/logs/](./start/logs/) - Logs d'exécution pour débogage

## 💾 Installation Locale

Pour installer la distribution localement:

1. **Linux/macOS:**
   ```bash
   sudo mv distributed-ide /opt/
   sudo ln -s /opt/distributed-ide/start/run.sh /usr/local/bin/distributed-ide
   distributed-ide  # Lancer de n'importe où
   ```

2. **Windows:**
   - Allez dans le répertoire `distributed-ide`
   - Créez un raccourci vers `start\run.bat`
   - Placez le raccourci sur le Bureau ou dans le Menu Démarrer

## 🐛 Rapport de Bugs

Si vous rencontrez des problèmes:
1. Vérifiez les logs dans `start/logs/`
2. Consultez la section Dépannage ci-dessus
3. Collectez les fichiers logs et les étapes de reproduction

## 📝 Version

- **Distribution Version:** javafx-client-1.0.0-SNAPSHOT
- **Java Target:** 21
- **Date de Build:** Février 2025

---

**Prêt?** Tapez `./start/run.sh` (Linux/macOS) ou `start\run.bat` (Windows) et commencez! 🎉
