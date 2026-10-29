# Architecture du Moteur CRDT - `crdt-core`

## Vue d'ensemble

Le module `crdt-core` implémente un moteur **CRDT** (Conflict-free Replicated Data Type) optimisé pour l'édition collaborative en temps réel. Ce système permet à plusieurs utilisateurs de modifier simultanément le même document sans serveur centralisé, en résolvant automatiquement les conflits.

## Décisions architecturales majeures

### 1. Structure des identifiants CRDT (`CRDTIdentifier`)

```
Structure: (timestamp, nodeId, disambiguator)
Exemple: ID(1000, "node1", 0)
```

**Justification:**
- **Timestamp**: Horodatage logique pour garantir une causalité entre les opérations
- **NodeId**: Identifiant unique du nœud/utilisateur répliquant pour éviter les collisions
- **Disambiguator**: Index pour différencier les opérations simultanées du même nœud

**Avantages:**
- ✅ Ordre total garantit et déterministe
- ✅ Convergence forte (tous les nœuds arrivent au même état)
- ✅ Pas d'horloge centralisée requise

**Complexité:** O(log n) pour les comparaisons lexicographiques

### 2. Modèle de données (`CRDTElement`, `CRDTOperation`)

#### CRDTElement
- Représente un caractère avec métadonnées
- Contient: `(id, value, deleted, createdAt)`
- **Soft delete**: Marquage pour suppression sans suppression physique (important pour la réplication)

#### CRDTOperation
- Opérations atomiques: INSERT et DELETE
- Contient références aux éléments voisins pour le positionnement spatial
- Immutable pour la sécurité en concurrence

**Avantage du soft delete:**
```java
element.markDeleted()  // Marquer logiquement
// L'élément reste dans la structure pour les opérations distantes
// Garbage collection le supprimera plus tard
```

### 3. Structure interne du document (`CRDTDocument`)

**SortedMap basée sur ConcurrentSkipListMap:**

```
Avant: [e0] - [e1] - [e2] - [e3]  (List chaînée)
Après: BST équilibré (SkipList)
```

**Justification:**
- ✅ **O(log n) insert/delete** grâce à l'indexation ordonnée
- ✅ **O(log n) lookup** pour trouver les positions
- ✅ **Thread-safe** avec ConcurrentSkipListMap
- ✅ Itération ordonnée efficace en O(n)

**Alternatives considérées:**
| Approche | Insert | Lookup | Thread-safe | Notes |
|----------|--------|--------|-------------|-------|
| List simple | O(n) | O(n) | Non | Trop lent pour gros documents |
| Index de position | O(1) | O(1) | Oui | Problème: positions changent après insertions |
| SortedMap/SkipList | O(log n) | O(log n) | Oui | **Choisi** |
| Arbre B | O(log n) | O(log n) | Non | Implémentation complexe |

### 4. Undo/Redo (`Deque` Stack)

```java
operationHistory = [op1, op2, op3]  // Historique
undoStack = [op3]                   // Stack pour redo

undo() -> operationHistory = [op1, op2], undoStack = [op3]
redo() -> operationHistory = [op1, op2, op3], undoStack = []
```

**Propriétés:**
- ✅ O(1) pour undo/redo
- ✅ La nouvelle opération vide le stack redo
- ✅ Supporte plusieurs annulations successives

### 5. Synchronisation et fusion (`merge()`)

```
Doc A [op1, op2, op3]
Doc B [op1, op2, op4]  (op3 et op4 concurrents)

merge(B -> A):
Doc A [op1, op2, op3, op4]
Doc B [op1, op2, op4]

Résultat: Même texte final grâce aux identifiants CRDT
```

**Propriétés CRDT impliquées:**
- ✅ **Idempotence**: Appliquer la même opération 2× = 1× (grâce aux IDs uniques)
- ✅ **Commutativité**: Op1, Op2 = Op2, Op1 (grâce aux positions absolues via IDs)
- ✅ **Convergence**: Tous les nœuds avec mêmes opérations → même texte

### 6. Garbage Collection

**Défi:** Les éléments supprimés restent en mémoire pour la réplication

```java
document.garbageCollect(ageThreshold)
// Supprime physiquement les éléments:
// - deleted == true ET
// - age > ageThreshold
```

**Stratégie:**
- Utilise le timestamp `createdAt` pour éviter les suppressions prématurées
- Permet aux opérations distantes tardives d'arriver
- Réduit la consommation mémoire pour les longs documents

**Recommandation:** Appeler tous les 1-5 minutes ou après N opérations

### 7. Multi-documents (`CRDTEngine`)

```java
CRDTEngine engine = new CRDTEngine("node1");
CRDTDocument doc1 = engine.createDocument("doc1");
CRDTDocument doc2 = engine.createDocument("doc2");
```

**Avantages:**
- ✅ Gestion centralisée de plusieurs documents
- ✅ Synchronisation par document
- ✅ Listeners pour notifications en temps réel
- ✅ Historique des opérations en attente

### 8. Thread-safety

**Niveaux de synchronisation:**
1. **CRDTDocument**: `synchronized` sur les opérations critiques
2. **CRDTEngine**: `ConcurrentHashMap` pour concurrent access
3. **CRDTElement**: `volatile` pour le flag `deleted`

**Garanties:**
- ✅ Les documents peuvent être accédés par plusieurs threads
- ✅ Les opérations insert/delete sont atomiques
- ✅ Pas de race condition sur les IDs

## Exemple d'utilisation

```java
// Initialisation
CRDTEngine engine = new CRDTEngine("alice");
CRDTDocument doc = engine.createDocument("shopping-list");

// Édition locale
engine.insertCharacter("shopping-list", 0, 'A');  // A
engine.insertCharacter("shopping-list", 1, 'p');  // Ap
engine.insertCharacter("shopping-list", 2, 'p');  // App
engine.insertCharacter("shopping-list", 3, 'l');  // Appl
engine.insertCharacter("shopping-list", 4, 'e');  // Apple

// Réception d'opérations distantes (de bob)
CRDTIdentifier bobId = new CRDTIdentifier(100, "bob", 0);
CRDTOperation remoteOp = CRDTOperation.insert(bobId, '!', null, null, 100);
engine.applyRemoteOperation("shopping-list", remoteOp);

// Résultat converge automatiquement malgré les éditions concurrentes
System.out.println(doc.getText()); // Apple!

// Synchronisation
List<CRDTOperation> toSync = engine.getPendingOperations();
// Envoyer au serveur...
engine.clearPendingOperations();
```

## Complexité algorithmique

| Opération | Complexité | Justification |
|-----------|-----------|---------------|
| Insert | O(log n) | SkipList lookup + insertion |
| Delete (par index) | O(log n) | Find + mark deleted |
| Delete (par ID) | O(log n) | SortedMap.get() |
| GetText() | O(n) | Itération sur tous les éléments |
| Undo/Redo | O(1) | Pop/push sur Deque |
| Merge | O(m) | m = nombre d'opérations distantes |
| GC | O(n) | Itération + suppression sélective |

## Limitations et améliorations futures

### Limitations actuelles:
1. **Pas de compression d'opérations**: L'historique peut devenir très volumineux
2. **Pas de snapshots**: Inutile pour les clients rejoignant tard
3. **Pas de persistance**: Données perdues au redémarrage (voir `sync-server`)
4. **Pas de granularité de permission**: Tous les users peuvent tout éditer

### Améliorations prévues:
- [ ] Compression d'opérations (ex: 100× delete → 1 plage supprimée)
- [ ] Snapshots incrémentaux pour la récupération
- [ ] Intégration avec une base de données
- [ ] Contrôle d'accès par document
- [ ] Opérations de groupe atomiques
- [ ] Notification élevée de conflits détectés

## Tests inclus

### Couverture:
- ✅ Identifiants (comparaison, hashcode, ordre)
- ✅ Opérations (insert, delete, sérialisation)
- ✅ Éléments (marquage suppression, comparaison)
- ✅ Documents (insert/delete, undo/redo, merge, GC, concurrence)
- ✅ Moteur (multi-documents, listeners, synchronisation)

### Exécution:
```bash
mvn clean test -pl crdt-core
```

## Dépendances

```xml
<!-- Logging -->
<slf4j-api>2.0.7</slf4j-api>
<logback-classic>1.4.8</logback-classic>

<!-- Serialization (optionnel) -->
<gson>2.10.1</gson>

<!-- Testing -->
<junit-jupiter-api>5.10.0</junit-jupiter-api>
<junit-jupiter-engine>5.10.0</junit-jupiter-engine>
```

## Références

- RGA (Replicated Growable Array) - CRDT linéaire sans métadonnées
- Edit-based CRDT vs State-based CRDT
- Operational Transformation vs CRDT
- Vers une édition collaborative à très large échelle (Université Paris Cité)

## Conclusion

Le moteur CRDT `crdt-core` fournit une fondation solide pour l'édition collaborative avec:
- ✅ Convergence garantie
- ✅ Pas de conflit d'édition
- ✅ Performance O(log n)
- ✅ Thread-safe
- ✅ Multi-utilisateur
- ✅ Scalable pour les longs documents
