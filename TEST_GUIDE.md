# 🧪 Guide de Test - Distributed IDE

## Test Rapide (2 minutes)

### Étape 1 : Lancer l'IDE

```bash
./QUICK_START.sh
```

**Résultat attendu** :
```
═══════════════════════════════════════════════════════════
  🚀 DISTRIBUTED IDE - QUICK START
═══════════════════════════════════════════════════════════

[1/7] Checking Java version...
✓ Java 21 found
[2/7] Checking Maven...
✓ Maven 3.8.7 found
[3/7] Checking for existing builds...
✓ Standalone JARs found
[4/7] Starting backend servers...
  → Starting Sync Server on port 8765...
  → Starting Signaling Server on port 8082...
  → Starting Terminal Service on port 8766...
[5/7] Waiting for servers to be ready...
✓ Sync Server is ready
✓ Signaling Server is ready
✓ Terminal Service is ready
✓ Backend services started
[6/7] Preparing JavaFX client...
[7/7] Launching Distributed IDE...

═══════════════════════════════════════════════════════════
✓ Distributed IDE is now running!
═══════════════════════════════════════════════════════════
```

### Étape 2 : Vérifier l'Interface

**Fenêtre JavaFX devrait s'ouvrir avec** :
- ✅ Menu bar (File, Edit, View, Git, etc.)
- ✅ Toolbar avec icônes
- ✅ Explorateur de fichiers à gauche
- ✅ Éditeur central
- ✅ Terminal en bas
- ✅ Panneaux Git et Output

### Étape 3 : Tests Fonctionnels Basiques

#### 3.1 Ouvrir un Projet

1. Cliquer `File > Open Project`
2. Sélectionner un dossier (ex: `/home/hsavatra/Bureau/Serveur`)
3. **Attendu** : Arbre de fichiers s'affiche à gauche

#### 3.2 Créer un Fichier

1. Cliquer `File > New File`
2. Entrer nom : `test.java`
3. **Attendu** : Nouveau fichier créé et ouvert

#### 3.3 Éditer du Code

1. Taper dans l'éditeur :
```java
public class Test {
    public static void main(String[] args) {
        System.out.println("Hello from Distributed IDE!");
    }
}
```

2. **Attendu** :
   - ✅ Coloration syntaxique appliquée
   - ✅ Indentation automatique
   - ✅ Pas d'erreurs de performance

#### 3.4 Sauvegarder

1. `Ctrl+S` ou `File > Save`
2. **Attendu** : Message "File saved" dans la barre de statut

#### 3.5 Changer le Thème

1. `View > Themes > Dracula` (ou autre)
2. **Attendu** : Thème change immédiatement

### Étape 4 : Vérifier les Serveurs

```bash
# Dans un autre terminal
ps aux | grep standalone
```

**Attendu** :
```
user  12345  ... java -jar sync-server/target/sync-server-standalone.jar
user  12346  ... java -jar signaling-server/target/signaling-server-standalone.jar
user  12347  ... java -jar terminal-service/target/terminal-service-standalone.jar
```

### Étape 5 : Vérifier les Logs

```bash
tail -f logs/sync-server.log
```

**Attendu** :
```
INFO  SyncServerApp - Sync server starting on port 8765...
INFO  SyncServerApp - Server started successfully
```

### Étape 6 : Arrêter l'IDE

1. Fermer la fenêtre JavaFX **OU**
2. `Ctrl+C` dans le terminal

**Attendu** :
```
Cleaning up...
✓ All services stopped
Goodbye! 👋
```

---

## Test des Fonctionnalités Avancées (10 minutes)

### 1. Terminal Intégré

**Test** :
1. Cliquer sur l'onglet "Terminal" en bas
2. Essayer de se connecter au service terminal
3. Exécuter une commande

**Attendu** :
- Si connecté : Commande s'exécute
- Si non connecté : Message d'erreur clair

### 2. Git

**Test** :
1. Ouvrir un projet avec Git (`File > Open Project`)
2. Vérifier le panneau Git à gauche
3. Si pas de repo : Essayer `Git > Initialize Repository`

**Attendu** :
- Branches listées
- Status visible
- Boutons fonctionnels

### 3. Plugins

**Test** :
1. `Settings > Plugins`
2. Voir la liste des plugins (vide si premier lancement)

**Attendu** :
- Interface s'affiche
- Pas d'erreurs

### 4. Recherche

**Test** :
1. `Ctrl+F` pour rechercher
2. Entrer un texte
3. Cliquer "Find Next"

**Attendu** :
- Boîte de dialogue s'ouvre
- Recherche fonctionne

---

## Tests de Performance

### 1. Temps de Démarrage

**Mesure** :
```bash
time ./QUICK_START.sh
```

**Attendu** : < 30 secondes (avec JARs déjà compilés)

### 2. Utilisation Mémoire

**Mesure** :
```bash
ps aux | grep java | awk '{print $6/1024 " MB"}'
```

**Attendu** :
- Client JavaFX : ~300-500 MB
- Chaque serveur : ~100-200 MB

### 3. Réactivité UI

**Test** :
- Ouvrir un gros fichier (>1000 lignes)
- Scroller rapidement
- Taper du texte

**Attendu** : Fluide, pas de lag

---

## Tests de Robustesse

### 1. Redémarrage Serveur

**Test** :
```bash
# Tuer un serveur manuellement
kill $(cat logs/sync-server.pid)

# Vérifier comportement client
```

**Attendu** :
- Client détecte déconnexion
- Message d'erreur informatif
- Pas de crash

### 2. Fichier Corrompu

**Test** :
1. Créer un fichier avec caractères bizarres
2. Essayer d'ouvrir

**Attendu** :
- Erreur gérée proprement
- Pas de crash

### 3. Projet Inexistant

**Test** :
1. `File > Open Project`
2. Sélectionner un dossier inexistant

**Attendu** :
- Message d'erreur clair
- Retour à l'état normal

---

## Checklist Complète

### Démarrage
- [ ] Java 21+ détecté
- [ ] Maven détecté
- [ ] JARs standalone trouvés ou compilés
- [ ] 3 serveurs démarrent
- [ ] Client JavaFX se lance
- [ ] UI s'affiche correctement

### Interface
- [ ] Menu bar visible
- [ ] Toolbar visible
- [ ] Explorateur fichiers fonctionne
- [ ] Éditeur fonctionne
- [ ] Thèmes changent
- [ ] Barre de statut affiche info

### Fonctionnalités
- [ ] Nouveau fichier
- [ ] Ouvrir fichier
- [ ] Sauvegarder fichier
- [ ] Coloration syntaxique
- [ ] Recherche/Remplace
- [ ] Git (si repo disponible)
- [ ] Terminal (connexion)
- [ ] Plugins (interface)

### Performance
- [ ] Démarrage < 30 sec
- [ ] Mémoire < 1 GB total
- [ ] UI fluide
- [ ] Pas de lag

### Arrêt
- [ ] Fermeture propre
- [ ] Serveurs arrêtés
- [ ] Pas de processus zombie
- [ ] Logs corrects

---

## Résolution de Problèmes

### Problème : Serveur ne démarre pas

**Symptôme** :
```
✗ Sync Server failed to start
```

**Solutions** :
```bash
# Vérifier le log
cat logs/sync-server.log

# Vérifier le port
lsof -i :8765

# Tuer processus existant
kill $(lsof -t -i :8765)

# Relancer
./QUICK_START.sh
```

### Problème : Client ne se connecte pas

**Symptôme** :
```
WARN Terminal WebSocket error
java.net.ConnectException: Connexion refusée
```

**Solutions** :
```bash
# Vérifier que les serveurs tournent
ps aux | grep standalone

# Vérifier les ports
lsof -i :8765,8082,8766

# Relancer les serveurs
./start-all-servers.sh
```

### Problème : Out of Memory

**Symptôme** :
```
java.lang.OutOfMemoryError: Java heap space
```

**Solutions** :
```bash
# Augmenter mémoire Java
export MAVEN_OPTS="-Xmx2048m"

# Relancer
./QUICK_START.sh
```

---

## Rapport de Test

### Template

```markdown
# Test Report - [Date]

## Environnement
- OS: Linux/macOS/Windows
- Java: 21
- Maven: 3.8.7

## Résultats

### Démarrage
- [ ] ✅ / ❌ - Java détecté
- [ ] ✅ / ❌ - Maven détecté
- [ ] ✅ / ❌ - Build réussi
- [ ] ✅ / ❌ - Serveurs démarrés
- [ ] ✅ / ❌ - Client lancé

### Fonctionnalités
- [ ] ✅ / ❌ - Ouvrir projet
- [ ] ✅ / ❌ - Créer fichier
- [ ] ✅ / ❌ - Éditer code
- [ ] ✅ / ❌ - Sauvegarder
- [ ] ✅ / ❌ - Changer thème

### Performance
- Temps démarrage: ___ sec
- Mémoire totale: ___ MB
- UI fluide: ✅ / ❌

### Bugs Trouvés
1. [Description bug]
2. [Description bug]

### Commentaires
[Vos observations]
```

---

## Conclusion

Ce guide couvre :
- ✅ Test rapide (2 min)
- ✅ Test complet (10 min)
- ✅ Tests de performance
- ✅ Tests de robustesse
- ✅ Résolution de problèmes

**Tous les tests devraient passer avec le script optimisé `QUICK_START.sh` !**
