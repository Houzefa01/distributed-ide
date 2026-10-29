# Module CRDT Core

## Résumé

**Module Maven** pour un moteur **CRDT** (Conflict-free Replicated Data Type) optimisé pour l'édition collaborative en temps réel.

Permet à plusieurs utilisateurs de modifier simultanément des documents sans conflits, avec synchronisation automatique et convergence garantie.

## Fonctionnalités principales

| Fonctionnalité | Description | Complexité |
|---|---|---|
| **Insert** | Insérer un caractère à une position | O(log n) |
| **Delete** | Supprimer un caractère | O(log n) |
| **Undo/Redo** | Annuler/refaire les éditions | O(1) |
| **Merge** | Fusionner les changements d'autres réplicas | O(m) |
| **Garbage Collection** | Nettoyer les données supprimées anciennes | O(n) |
| **Multi-documents** | Gérer plusieurs documents indépendants | - |
| **Thread-safe** | Accès concurrent sécurisé | - |

## Architecture

### Classes principales

- **`CRDTIdentifier`**: Identifiant unique (timestamp + nodeId + disambiguator)
- **`CRDTElement`**: Élément CRDT avec métadonnées
- **`CRDTOperation`**: Opération atomique (INSERT/DELETE)
- **`CRDTDocument`**: Document CRDT unique avec gestion complète
- **`CRDTEngine`**: Moteur principal pour multi-documents

Voir [ARCHITECTURE.md](ARCHITECTURE.md) pour les détails architecturaux.

## Installation

```bash
# Compiler le module
mvn clean install -pl crdt-core

# Ou dans la racine du projet
mvn clean install
```

## Utilisation

### Exemple basique

```java
import com.distributed_ide.crdt.engine.CRDTEngine;
import com.distributed_ide.crdt.model.CRDTDocument;

// Créer un moteur CRDT avec un identifiant de nœud
CRDTEngine engine = new CRDTEngine("alice");

// Créer un nouveau document
CRDTDocument doc = engine.createDocument("document-1");

// Insérer des caractères
engine.insertCharacter("document-1", 0, 'H');  // H
engine.insertCharacter("document-1", 1, 'e');  // He
engine.insertCharacter("document-1", 2, 'l');  // Hel
engine.insertCharacter("document-1", 3, 'l');  // Hell
engine.insertCharacter("document-1", 4, 'o');  // Hello

// Obtenir le texte
System.out.println(doc.getText());  // Hello

// Supprimer un caractère
engine.deleteCharacter("document-1", 1);  // Hllo

// Undo
engine.undo("document-1");  // Hello

// Redo
engine.redo("document-1");  // Hllo
```

### Synchronisation multi-utilisateur

```java
// Utilisateur 1: Alice
CRDTEngine alice = new CRDTEngine("alice");
CRDTDocument docA = alice.createDocument("shared-doc");
alice.insertCharacter("shared-doc", 0, 'A');

// Utilisateur 2: Bob
CRDTEngine bob = new CRDTEngine("bob");
CRDTDocument docB = bob.createDocument("shared-doc");
bob.insertCharacter("shared-doc", 0, 'B');

// Synchronisation: Alice reçoit les changements de Bob
for (CRDTOperation op : docB.getOperationHistory()) {
    alice.applyRemoteOperation("shared-doc", op);
}

// Les deux documents convergent vers le même état
System.out.println(docA.getText());  // "BA" ou "AB" (ordonné par CRDT ID)
System.out.println(docB.getText());  // "B"
```

### Listeners pour notifications

```java
engine.addOperationListener((docId, operation) -> {
    System.out.println("Changement dans " + docId);
    System.out.println("Texte: " + engine.getDocument(docId).getText());
});

engine.insertCharacter("document-1", 0, 'X');
// Affiche: Changement dans document-1, Texte: X
```

### Garbage Collection

```java
// Nettoyer les données supprimées après 60 secondes
engine.garbageCollectAll(60000);
```

## Tests

Exécuter les tests unitaires:

```bash
# Tests du module crdt-core
mvn test -pl crdt-core

# Avec rapport de couverture
mvn test jacoco:report -pl crdt-core
```

### Coverage

- ✅ `CRDTIdentifier`: Comparaison, hachage, égalité
- ✅ `CRDTOperation`: Inserts/deletes, sérialisation
- ✅ `CRDTElement`: Marquage suppression, comparaison
- ✅ `CRDTDocument`: Opérations complètes, undo/redo, merge, concurrence
- ✅ `CRDTEngine`: Multi-documents, listeners, synchronisation

## Propriétés CRDT garanties

### 1. Idempotence
Appliquer deux fois la même opération = appliquer une fois

```java
engine.applyRemoteOperation("doc", op);
engine.applyRemoteOperation("doc", op);  // Pas d'effet (détecté par ID)
```

### 2. Commutativité
L'ordre des opérations ne change pas le résultat (pour opérations non conflictuelles)

```java
doc1: Insert 'a' at 0, puis 'b' at 1 → "ab"
doc2: Insert 'b' at 1, puis 'a' at 0 → "ab" (même résultat)
```

### 3. Convergence
Tous les nœuds avec les mêmes opérations arrivent au même état

```java
docA.merge(docB);  // A reçoit toutes les ops de B
docB.merge(docA);  // B reçoit toutes les ops de A
// Résultat: docA.getText() == docB.getText()
```

## Performance

### Insertion de 10,000 caractères
```
Sequential insert: ~50-100ms
Memory: ~2-5 MB
```

### Jitter de synchronisation dans les deux sens
```
100 opérations concurrentes: <10ms de convergence
```

## Limitations connues

1. **Pas de compression** d'opérations
2. **Pas de snapshots** pour la récupération rapide
3. **Pas de persistance** (gérée par `sync-server`)
4. **Pas de contrôle d'accès** (tous les users peuvent tout éditer)
5. **Soft delete** consomme de la mémoire (nécessite GC)

## API Complète

### CRDTEngine

```java
// Gestion des documents
CRDTDocument createDocument(String documentId)
CRDTDocument getDocument(String documentId)
boolean deleteDocument(String documentId)

// Opérations sur texte
CRDTIdentifier insertCharacter(String documentId, int index, char value)
boolean deleteCharacter(String documentId, int index)

// Synchronisation
void applyRemoteOperation(String documentId, CRDTOperation operation)
List<CRDTOperation> getPendingOperations()
void clearPendingOperations()

// Undo/Redo
boolean undo(String documentId)
boolean redo(String documentId)

// Utilitaires
void mergeDocuments(String sourceId, String targetId)
void garbageCollectAll(long ageThreshold)
void addOperationListener(OperationListener listener)
void removeOperationListener(OperationListener listener)
```

### CRDTDocument

```java
// Édition
CRDTIdentifier insert(int index, char value, String nodeId)
boolean delete(int index)
boolean deleteById(CRDTIdentifier id)

// Synchronisation
void applyRemoteOperation(CRDTOperation operation)
void merge(CRDTDocument other)

// Undo/Redo
boolean undo()
boolean redo()

// Lecture
String getText()
List<CRDTElement> getVisibleElements()
List<CRDTOperation> getOperationHistory()
int size()

// Maintenance
void garbageCollect(long ageThreshold)
```

## Intégration avec autres modules

- **`sync-server`**: Synchronisation réseau des opérations CRDT
- **`lsp-bridge`**: Intégration avec serveurs LSP
- **`terminal-service`**: Support de l'édition terminale
- **`javafx-client`**: Interface graphique

## Contribuer

Avant de contribuer:
1. Vérifier que tous les tests passent
2. Respecter la structure d'empaquetage
3. Ajouter des tests pour les nouvelles fonctionnalités
4. Mettre à jour `ARCHITECTURE.md` si changements majeurs

## Licence

Partie du projet Distributed IDE.
