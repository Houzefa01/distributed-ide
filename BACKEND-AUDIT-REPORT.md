# 🔍 AUDIT COMPLET DU BACKEND - SYSTÈME COLLABORATIF

**Date**: 23 février 2026  
**Statut**: 🔴 EN COURS D'AUDIT  
**Objectif**: Audit + corrections + optimisations pour 500+ clients

---

## 📋 TABLE DES MATIÈRES
1. Executive Summary
2. Findings par serveur
3. Bugs critiques trouvés
4. Optimisations proposées
5. Corrections appliquées
6. Recommandations de sécurité

---

## 1️⃣ FINDINGS - SYNC SERVER (SyncWebSocketServer.java)

### 🔴 BUGS CRITIQUES

#### BUG #1: Race Condition sur `lastWriterByDocument`
**Localisation**: Lines 261-262, 312-318  
**Description**:
```java
lastWriterByDocument.put(msg.getDocumentId(), session);
```
**Problème**: Pas de synchronisation si plusieurs clients écrivent simultanément au même document.
**Impact**: Peut causer des batches perdues ou un flushing incorrect lors de concurrent writes.

#### BUG #2: Fuite Mémoire sur `flushTasks`
**Localisation**: Lines 268-275  
**Problème**: Si un flush est déjà en cours et un nouveau est schedulé, l'ancien `ScheduledFuture` n'est pas cancellé correctement.
```java
// AVANT (Bug):
flushTasks.put(docId, ...);  // Remplace l'ancienne task sans canceler

// APRÈS (Fix):
ScheduledFuture<?> old = flushTasks.put(docId, ...);
if (old != null) old.cancel(false);
```
**Impact**: Memory leak croissant avec le nombre de documents actifs.

#### BUG #3: Null Pointer dans `broadcastToSubscribers`
**Localisation**: Pas visible dans ce snippet mais très probable  
**Problème**: Si `documentSubscribers.get(docId)` retourne null après la suppression de la dernière subscription.
**Impact**: NPE pouvant déconnecter les clients.

#### BUG #4: Pas de validation du `serverToken` dans `onOpen`
**Localisation**: Lines 95-107  
**Problème**: `session.setAuthenticated(true)` sans vérifier si le token fourni est correct.
```java
if (serverToken == null || serverToken.isEmpty()) {
    session.setAuthenticated(true);  // ← Trop permissif
}
```
**Impact**: N'importe quel client peut se connecter si le token n'est pas strictement requis.

#### BUG #5: Fuites de sessions orphelines
**Localisation**: `onClose` (lines 110-130)  
**Problème**: Si `connectedClients.get(conn)` est null (peut arriver en race conditions), la session n'est pas cleanup.
**Impact**: Ressources accumulent lentement.

### ⚠️ PROBLÈMES de PERFORMANCE

#### PERF #1: Broadcast inefficace
**Issue**: `broadcastToSubscribers` envoie à TOUS les abonnés, même si aucun n'est intéressé.
**Solution**: Implémenter room-based isolation + selective subscription

#### PERF #2: JSON parsing à chaque message
**Issue**: ObjectMapper.readValue() créé à chaque message.
**Solution**: Réutiliser ObjectMapper + caching

#### PERF #3: Rate limiting trop permissif
**Issue**: 200 ops/sec par client = 100K ops/sec pour 500 clients.
**Solution**: Adapter rate limiting par type de message

---

## 2️⃣ FINDINGS - SIGNALING SERVER (SignalingWebSocketServer.java)

### 🔴 BUGS CRITIQUES

#### BUG #6: SessionMap peut avoir des sessions orphelines
**Localisation**: Lines 41-49  
**Problème**: Si `conn` est fermé abruptement, `sessionMap.remove(conn)` peut échouer.
**Impact**: SessionMap croît indéfiniment.

#### BUG #7: Pas de validation du roomId
**Localisation**: Lines 97-98  
**Problème**:
```java
String roomId = msg.getRoomId();
if (userId == null || roomId == null) {  // Pas de validation de format
```
**Impact**: Path traversal possible, injection JSON.

#### BUG #8: `notifyUserLeft/Joined` potentiellement NPE
**Localisation**: Lines 138-142  
**Problème**: Si `room.getParticipant(targetUserId)` retourne null.
**Impact**: Crash serveur.

#### BUG #9: Pas de timeout sur les connections inactives
**Localisation**: Aucun heartbeat visible  
**Impact**: Connections zombies accumulent, DoS.

#### BUG #10: Pas de rate limiting sur SDP/ICE
**Localisation**: `handleSdpMessage`, `handleIceCandidate`  
**Impact**: Flood possible, consommation mémoire explosive.

---

## 3️⃣ FINDINGS - TERMINAL SERVICE (ThreadSafeTerminalWebSocketServer.java)

### 🔴 BUGS CRITIQUES

#### BUG #11: Exécution sans timeout utilisateur visible
**Localisation**: Pas visible complètement  
**Impact**: Client peut faire bloquer le serveur indéfiniment.

#### BUG #12: Pas de isolation process/namespace
**Localisation**: Docker simulation non visible  
**Impact**: Code malveillant peut accéder à d'autres sessions.

---

## 4️⃣ FINDINGS - LSP BRIDGE (LspWebSocketHandler.java)

### ⚠️ PROBLÈMES

#### ISSUE #1: Pas de message size limit
**Impact**: Payload géante peut crash JVM.

#### ISSUE #2: Pas de recovery si sync-server crash
**Impact**: LSP orphelin, clients bloqués.

---

## 5️⃣ CRDT ENGINE

### Risk: Pas de détection des causal violations
**Impact**: Merge conflict résolu mal en cas de partition réseau.

---

## 📊 SUMMARY DES FINDINGS

| # | Catégorie | Sévérité | Count | Impact |
|----|-----------|----------|-------|--------|
| 1-5 | Sync Server | 🔴 Critical | 5 | Memory leak, corruption data, disconnects |
| 6-10 | Signaling | 🔴 Critical | 5 | DoS, crash, zombie sessions |
| 11-12 | Terminal | 🔴 Critical | 2 | Security, performance |
| 13+ | LSP Bridge | ⚠️ High | 2 | Reliability |
| & | CRDT | ⚠️ Medium | 1 | Data consistency edge case |
| **TOTAL** | | | **15** | **Production blocker** |

---

## 🎯 CORRECTIONS À APPLIQUER

### Priority 1 (CRITICAL - Apply Now)
1. Fix race condition `lastWriterByDocument` → Sync
2. Fix memory leak `flushTasks` → Sync
3. Fix null pointer in broadcasts → Sync
4. Fix orphaned sessions → Signaling
5. Add roomId validation → Signaling
6. Add ICE/SDP rate limiting → Signaling
7. Add heartbeat/timeout → Signaling
8. Add message size limit → All servers

### Priority 2 (HIGH - Apply Soon)
1. Implement room-based isolation
2. Improve broadcast efficiency
3. Add connection pooling
4. Add metrics/monitoring
5. Add graceful shutdown

### Priority 3 (OPTIMIZATION - Nice to Have)
1. Implement batch compression
2. Add client-side caching
3. Implement adaptive rate limiting
4. Add auto-recovery
5. Implement client-side reconnect with state
