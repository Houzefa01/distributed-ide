package com.distributed_ide.javafx_client.webrtc;

import com.distributed_ide.signaling.model.SignalingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gère les ICE candidates en attente avant que la remote description soit reçue.
 * 
 * ✅ Buffer les candidats si remote description pas encore reçue
 * ✅ Valide les candidats
 * ✅ Flushe automatiquement quand remote description arrive
 * ✅ Nettoyage automatique des candidats périmés
 */
public class IceCandidateBuffer {
    private static final Logger LOG = LoggerFactory.getLogger(IceCandidateBuffer.class);
    private static final long CANDIDATE_MAX_AGE_MS = 30000; // 30 sec

    private final Map<String, Queue<CandidateEntry>> candidatesByPeer = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> remoteDescriptionSet = new ConcurrentHashMap<>();

    /**
     * Enregistre un ICE candidate.
     * Si la remote description est déjà reçue, applique directement le candidate.
     * Sinon, le buffer pour application ultérieure.
     * 
     * @return true si le candidate a été ajouté au buffer, false s'il a été appliqué directement
     */
    public boolean addCandidate(String peerId, SignalingMessage.IceCandidate candidate, 
                               WebRtcPeerConnection peer) {
        if (!isValidCandidate(candidate)) {
            LOG.warn("❌ Candidate invalide pour peer {}: {}", peerId, candidate);
            return false;
        }

        AtomicBoolean hasRemoteDesc = remoteDescriptionSet.computeIfAbsent(peerId, k -> new AtomicBoolean(false));

        if (hasRemoteDesc.get()) {
            // Remote description déjà reçue, appliquer directement
            LOG.debug("✅ Candidate appliqué directement pour peer {}: {}", peerId, candidate);
            try {
                peer.addIceCandidate(candidate);
                return false;
            } catch (Exception e) {
                LOG.error("❌ Erreur lors de l'ajout du candidate pour {}: {}", peerId, e.getMessage());
                return false;
            }
        }

        // Buffer le candidate pour application ultérieure
        candidatesByPeer.computeIfAbsent(peerId, k -> new LinkedList<>())
            .offer(new CandidateEntry(candidate, System.currentTimeMillis()));
        LOG.debug("📦 Candidate bufferedisé pour peer {} ({} en attente)", peerId, candidatesByPeer.get(peerId).size());
        return true;
    }

    /**
     * Marque la remote description comme reçue et applique tous les candidats en buffer.
     * 
     * @return Nombre de candidats appliqués
     */
    public int flushCandidates(String peerId, WebRtcPeerConnection peer) {
        AtomicBoolean hasRemoteDesc = remoteDescriptionSet.computeIfAbsent(peerId, k -> new AtomicBoolean(true));
        hasRemoteDesc.set(true);

        Queue<CandidateEntry> candidates = candidatesByPeer.remove(peerId);
        if (candidates == null || candidates.isEmpty()) {
            LOG.debug("📭 Aucun candidat en attente pour peer {}", peerId);
            return 0;
        }

        int flushed = 0;
        while (!candidates.isEmpty()) {
            try {
                CandidateEntry entry = candidates.poll();
                if (entry != null && !isExpired(entry)) {
                    peer.addIceCandidate(entry.candidate);
                    flushed++;
                } else {
                    LOG.debug("⏳ Candidate périmé ignoré pour peer {}", peerId);
                }
            } catch (Exception e) {
                LOG.error("❌ Erreur lors du flush des candidats pour {}: {}", peerId, e.getMessage());
            }
        }

        LOG.info("✅ {} candidats appliqués pour peer {}", flushed, peerId);
        return flushed;
    }

    /**
     * Nettoie tous les candidats en attente pour un peer.
     */
    public void clear(String peerId) {
        candidatesByPeer.remove(peerId);
        remoteDescriptionSet.remove(peerId);
        LOG.debug("🗑️  Candidats nettoyés pour peer {}", peerId);
    }

    /**
     * Nettoie tous les candidats et réinitialise l'état.
     */
    public void clearAll() {
        candidatesByPeer.clear();
        remoteDescriptionSet.clear();
        LOG.debug("🗑️  Tous les candidats nettoyés");
    }

    /**
     * @return Vrai si la remote description est reçue pour ce peer
     */
    public boolean hasRemoteDescription(String peerId) {
        return remoteDescriptionSet.getOrDefault(peerId, new AtomicBoolean(false)).get();
    }

    /**
     * @return Nombre de candidats en attente pour ce peer
     */
    public int getPendingCandidateCount(String peerId) {
        Queue<CandidateEntry> q = candidatesByPeer.get(peerId);
        return q != null ? q.size() : 0;
    }

    private boolean isValidCandidate(SignalingMessage.IceCandidate candidate) {
        if (candidate == null) return false;
        // Validations basiques
        if (candidate.getCandidate() == null || candidate.getCandidate().isEmpty()) return false;
        if (candidate.getSdpMLineIndex() < 0) return false;
        return true;
    }

    private boolean isExpired(CandidateEntry entry) {
        return System.currentTimeMillis() - entry.timestamp > CANDIDATE_MAX_AGE_MS;
    }

    /**
     * Entrée interne pour stocker un candidate avec son timestamp.
     */
    private static class CandidateEntry {
        final SignalingMessage.IceCandidate candidate;
        final long timestamp;

        CandidateEntry(SignalingMessage.IceCandidate candidate, long timestamp) {
            this.candidate = candidate;
            this.timestamp = timestamp;
        }
    }
}
