# 🚀 Guide d'Exécution de Projets - Système Intelligent

Ce guide explique le système de détection automatique et d'exécution intelligente des projets dans l'IDE JavaFX.

---

## 🎯 Fonctionnalités

- ✅ **Détection automatique** du type de projet (Maven, Java Simple, Python)
- ✅ **Compilation automatique** pour projets Java simples
- ✅ **Lecture des configurations** launch.json avant chaque exécution
- ✅ **Gestion asynchrone** sans blocage UI
- ✅ **Cache intelligent** du type de projet
- ✅ **Gestion propre des processus** (cleanup, interruption)
- ✅ **Affichage temps réel** de stdout et stderr

---

## 📂 Architecture des Projets

### 1. Projet Maven ✅

**Structure minimale:**
```
my-maven-project/
├── pom.xml
├── src/
│   ├── main/java/
│   └── test/java/
└── target/
```

**Exécution:**
- Détection: Présence de `pom.xml`
- Commande: `mvn exec:java`
- Alternative: `mvn package` puis `java -jar target/app.jar`

**Configuration automatique:**
Aucune configuration requise - Maven gère tout!

---

### 2. Projet Java Simple 📚

**Structure requise:**
```
my-java-project/
├── src/
│   └── *.java
├── lib/                        ← Dépendances JAR
│   └── *.jar
├── bin/                        ← Généré automatiquement
└── ExJavaConfig/
    └── launch.json            ← REQUIS
```

**Fichier configuration: `ExJavaConfig/launch.json`**

```json
{
  "mainClass": "com.example.Main",
  "vmArgs": "-Xmx512m -Xms256m",
  "programArgs": "arg1 arg2",
  "classPathExtra": "./lib/*"
}
```

**Fields:**
- `mainClass` (obligatoire): Classe principale avec package (ex: `com.example.Main`)
- `vmArgs` (optionnel): Arguments JVM (ex: `-Xmx512m`)
- `programArgs` (optionnel): Arguments du programme (ex: `--port=8080`)
- `classPathExtra` (optionnel): Chemin vers les JARs supplémentaires

**Exécution:**
1. IDE détecte fichiers `.java` + présence de `ExJavaConfig/launch.json`
2. IDE compile: `javac -d bin -cp "lib/" src/**/.java`
3. IDE exécute: `java -cp "bin:lib/*" com.example.Main arg1 arg2`

---

### 3. Projet Python 🐍

**Structure requise:**
```
my-python-project/
├── main.py                  ← Fichier principal
├── *.py                     ← Autres fichiers
└── ExPython/
    └── launch.json         ← REQUIS
```

**Fichier configuration: `ExPython/launch.json`**

```json
{
  "mainFile": "main.py",
  "args": "arg1 arg2",
  "pythonPath": "python3"
}
```

**Fields:**
- `mainFile` (obligatoire): Fichier Python à exécuter (chemin relatif)
- `args` (optionnel): Arguments de ligne de commande
- `pythonPath` (optionnel): Chemin de l'interpréteur Python (par défaut: `python3`)

**Exécution:**
1. IDE détecte fichiers `.py` + présence de `ExPython/launch.json`
2. IDE valide que le fichier existe
3. IDE exécute: `python3 main.py arg1 arg2`

---

## ⚡ Flux d'Exécution

```
┌─────────────────────────────────────────────────────────┐
│ Utilisateur clique "Run" (Ctrl+Alt+X)                   │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
        ┌────────────────────┐
        │ Détecter le type   │  ← Cache (5 sec)
        │ de projet          │
        └────────┬───────────┘
                 │
        ┌────────┴────────────────────────┐
        │                                  │
        ▼                                  ▼
    ┌───────────┐                   ┌─────────────┐
    │ MAVEN     │                   │ JAVA SIMPLE │
    └─────▼─────┘                   └──────▼──────┘
          │                                │
          │ mvn compile                    │ javac + Launch.json
          ▼                                ▼
    ┌───────────┐                   ┌─────────────┐
    │ Run       │                   │ Compile     │
    │ exec:java │                   │ code        │
    └─────▼─────┘                   └──────▼──────┘
          │                                │
          │                                ▼
          │                         ┌─────────────┐
          │                         │ Run java    │
          │                         │ -cp bin/*   │
          │                         └──────▼──────┘
          │                                │
          └────────────────┬───────────────┘
                           │
               ┌───────────┴───────────┐
               │                       │
               ▼                       ▼
        ┌─────────────┐         ┌──────────┐
        │ Read stdout │         │ Reed stderr
        │ (parallel)  │         │ (parallel)
        └──────▼──────┘         └────▼─────┘
               │                      │
               └──────────┬───────────┘
                          │
                          ▼
              ┌──────────────────────┐
              │ Afficher output tab  │
              │ + statut final       │
              └──────────────────────┘
```

---

## 🔧 Détection Automatique

### Priorités:

1. **Maven**: Si `pom.xml` existe → `MAVEN`
2. **Java Simple**: Si `.java` files + `ExJavaConfig/launch.json` → `JAVA_SIMPLE`
3. **Python**: Si `.py` files + `ExPython/launch.json` → `PYTHON`
4. **Fallback**: Si `.java` files (sans launch.json) → `JAVA_SIMPLE`
5. **Fallback**: Si `.py` files (sans launch.json) → `PYTHON`
6. **Unknown**: Si rien ne correspond → `UNKNOWN`

### Cache:

- Le type de projet est **mis en cache pendant 5 secondes**
- Réduit les scans filesystem répétés
- Cache invalidé automatiquement après modifications

---

## ✅ Exemples Complets

### Exemple 1: Projet Java Simple

**Structure:**
```
hello-world/
├── src/
│   └── Hello.java
├── lib/
│   └── commons-lang3-3.12.0.jar
├── bin/
├── ExJavaConfig/launch.json
```

**`ExJavaConfig/launch.json`:**
```json
{
  "mainClass": "Hello",
  "vmArgs": "",
  "programArgs": "",
  "classPathExtra": "./lib/*"
}
```

**`src/Hello.java`:**
```java
import org.apache.commons.lang3.StringUtils;

public class Hello {
    public static void main(String[] args) {
        String padded = StringUtils.center("Hello", 50, "=");
        System.out.println(padded);
    }
}
```

### Exemple 2: Projet Maven

**`pom.xml`:**
```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>my-app</artifactId>
    <version>1.0</version>
    
    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>1.7.36</version>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
                <configuration>
                    <mainClass>com.example.App</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### Exemple 3: Projet Python

**Structure:**
```
python-app/
├── main.py
├── utils.py
├── config.py
├── ExPython/launch.json
```

**`ExPython/launch.json`:**
```json
{
  "mainFile": "main.py",
  "args": "--verbose --port=8080",
  "pythonPath": "python3"
}
```

**`main.py`:**
```python
import sys
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--verbose', action='store_true')
parser.add_argument('--port', type=int, default=8000)

args = parser.parse_args()

print(f"Starting app on port {args.port}")
if args.verbose:
    print("Verbose mode enabled")
```

---

## 🎮 Raccourcis Clavier

| Action | Raccourci |
|--------|-----------|
| Run | `Ctrl+Alt+X` |
| Stop | `Ctrl+Shift+X` |

---

## ⚙️ Configuration de l'IDE

### Paramètres de l'Exécuteur

Les paramètres suivants peuvent être ajustés dans le code:

```java
// Timeout de cache du détecteur de type
CACHE_VALIDITY_MS = 5000; // 5 secondes

// Nombre de threads pour l'exécution
executionThreadPool = Executors.newFixedThreadPool(2);

// Nombre de threads pour lire les flux
streamReaderThreadPool = Executors.newFixedThreadPool(4);
```

---

## 🐛 Troubleshooting

### "Unable to detect project type"

**Solution:**
- Vérifier que le projet a la structure requise
- Pour Java: créer `ExJavaConfig/launch.json`
- Pour Python: créer `ExPython/launch.json`
- Pour Maven: vérifier `pom.xml` existe

### "Compilation failed"

**Solution:**
- Vérifier les erreurs dans l'output tab
- Vérifier que `src/` contient des fichiers `.java`
- Vérifier que les imports sont corrects

### "Main file not found"

**Solution:**
- Vérifier le chemin dans `launch.json`
- Vérifier que le chemin est relatif au projet root
- Vérifier l'extension du fichier

### "Java: command not found"

**Solution:**
- Ajouter Java au PATH
- Utiliser une version de Java compatible (8+)

### "python3: command not found"

**Solution:**
- Installer Python 3
- Changer `pythonPath` dans `ExPython/launch.json`
- Essayer `python` au lieu de `python3`

---

## 📚 Documentation des Classes

### ProjectTypeDetector

```java
// Détecter le type
ProjectType type = detector.detectProjectType(projectRoot);

// Invalider le cache
detector.invalidateCache(projectRoot);
```

### LaunchConfigLoader

```java
// Charger config Java
JavaLaunchConfig javaConfig = loader.loadJavaLaunchConfig(projectRoot);

// Charger config Python
PythonLaunchConfig pythonConfig = loader.loadPythonLaunchConfig(projectRoot);

// Les fichiers manquants retournent une config par défaut
```

### ProjectExecutionManager

```java
ProjectExecutionManager manager = new ProjectExecutionManager();

// Lancer l'exécution
manager.executeAsync(
    projectRoot,
    outputLine -> System.out.println(outputLine),
    errorLine -> System.err.println(errorLine),
    exitCode -> System.out.println("Exit: " + exitCode)
);

// Arrêter le processus
manager.stop();

// Arrêter proprement
manager.shutdown();
```

---

## 🎓 Best Practices

1. **Toujours créer `launch.json`** pour les projets Java et Python
2. **Utiliser Maven** pour les projets complexes
3. **Organiser le code** en packages (Java)
4. **Tester localement** avant d'exécuter via l'IDE
5. **Vérifier les permissions d'exécution** pour Python (Unix)
6. **Garder `lib/`** répertoire propre (Java Simple)

---

## 🔄 Architecture Interne

### Flux de Détection

```java
// 1. Vérifier le cache
if (cached && notExpired) {
    return cached;
}

// 2. Vérifier pom.xml → MAVEN
if (pom.xml exists) {
    return MAVEN;
}

// 3. Vérifier .java + launch.json → JAVA_SIMPLE
if (java files && ExJavaConfig/launch.json) {
    return JAVA_SIMPLE;
}

// 4. Vérifier .py + launch.json → PYTHON
if (py files && ExPython/launch.json) {
    return PYTHON;
}

// 5. Fallback: .java → JAVA_SIMPLE
if (java files) {
    return JAVA_SIMPLE;
}

// 6. Fallback: .py → PYTHON
if (py files) {
    return PYTHON;
}

// 7. Défaut
return UNKNOWN;
```

### Gestion Asynchrone

- **Thread 1**: Exécution du projet (compilation + lancement)
- **Thread 2-3**: Lecture stdout et stderr (en parallèle)
- **Main Thread**: UI reste responsive

---

## 📝 Notes

- Le système utilise **SLF4J** pour le logging
- Les logs sont affichés dans le fichier `.*/ide.log`
- Les processus sont **nettoyés automatiquement** à la fin
- Les threads sont des **daemon threads** (n'empêchent pas la fermeture)

---

**Version**: 1.0  
**Dernière mise à jour**: 23 février 2026
