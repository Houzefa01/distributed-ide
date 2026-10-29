package com.distributed_ide.javafx_client.collaboration.service;

import com.distributed_ide.javafx_client.collaboration.model.CallSession;
import com.distributed_ide.javafx_client.collaboration.model.CallSession.CallStatus;
import com.distributed_ide.javafx_client.collaboration.model.CallSession.CallType;
import com.distributed_ide.javafx_client.collaboration.model.CallSession.CallParticipant;
import com.distributed_ide.javafx_client.service.SignalingClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Gère les appels WebRTC audio/vidéo et les conférences groupe.
 * Intègre avec le serveur de signalisation pour l'échange SDP/candidats ICE.
 */
public class WebRTCCallManager {
    private static final Logger LOG = LoggerFactory.getLogger(WebRTCCallManager.class);

    // Timeouts
    private static final long CALL_TIMEOUT_MS = 30_000;         // Timeout appel entrant
    private static final long CLEANUP_INTERVAL_MS = 60_000;     // Nettoyage des appels terminés

    // Appels actifs par ID
    private final Map<String, CallSession> activeCalls = new ConcurrentHashMap<>();
    
    // Appels entrants par client (pour récupérer l'appel unique)
    private final Map<String, String> incomingCallsByClient = new ConcurrentHashMap<>();

    // Listeners
    private final List<CallListener> listeners = new CopyOnWriteArrayList<>();

    // Scheduler
    private final ScheduledExecutorService cleanupScheduler;
    private volatile ScheduledFuture<?> cleanupTask;
    private volatile String myClientId;
    private volatile String myUsername;
    private volatile SignalingClientService signalingService;

    public interface CallListener {
        void onIncomingCall(CallSession call);
        void onCallActive(String callId, CallType type);
        void onCallEnded(String callId, String reason);
        void onParticipantJoined(String callId, String clientId, String username);
        void onParticipantLeft(String callId, String clientId);
        void onParticipantStateChanged(String callId, String clientId, String newState);
        void onMediaError(String callId, String error);
    }

    public WebRTCCallManager() {
        this.cleanupScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "call-cleanup");
            t.setDaemon(true);
            return t;
        });
        startCleanupScheduler();
    }

    /**
     * Initialise le manager avec les infos du client actuel.
     */
    public void initialize(String clientId, String username, SignalingClientService signalingService) {
        this.myClientId = clientId;
        this.myUsername = username;
        this.signalingService = signalingService;
        LOG.info("WebRTCCallManager initialized: {} ({})", username, clientId);
    }

    /**
     * Initie un appel sortant.
     */
    public String initiateCall(String targetClientId, CallType type) {
        if (targetClientId == null || targetClientId.equals(myClientId)) {
            throw new IllegalArgumentException("Invalid target client");
        }

        String callId = "call_" + UUID.randomUUID().toString().substring(0, 12);
        
        CallSession call = new CallSession.Builder()
                .callId(callId)
                .initiatorClientId(myClientId)
                .addParticipant(myClientId)
                .addParticipant(targetClientId)
                .type(type)
                .status(CallStatus.RINGING)
                .startedAt(System.currentTimeMillis())
                .build();

        activeCalls.put(callId, call);
        LOG.info("Call initiated: {} (id: {})", type, callId);

        // Send call initiation via signaling service (async)
        if (signalingService != null) {
            java.util.HashMap<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("callId", callId);
            metadata.put("callType", type.toString());
            signalingService.sendCallRequest(targetClientId, metadata);
        }

        return callId;
    }

    /**
     * Réceptionne un appel entrant.
     */
    public void receiveIncomingCall(String callId, String initiatorClientId, String initiatorUsername, CallType type) {
        String existingCallId = incomingCallsByClient.get(initiatorClientId);
        if (existingCallId != null) {
            LOG.warn("Rejecting duplicate incoming call from {}", initiatorClientId);
            declineCall(callId, "Duplicate call");
            return;
        }

        CallSession call = new CallSession.Builder()
                .callId(callId)
                .initiatorClientId(initiatorClientId)
                .addParticipant(initiatorClientId)
                .addParticipant(myClientId)
                .type(type)
                .status(CallStatus.RINGING)
                .startedAt(System.currentTimeMillis())
                .build();

        activeCalls.put(callId, call);
        incomingCallsByClient.put(initiatorClientId, callId);
        
        LOG.info("Incoming {} call from {} (id: {})", type, initiatorUsername, callId);
        notifyIncomingCall(call);

        // Auto-decline after timeout
        cleanupScheduler.schedule(() -> {
            if (activeCalls.containsKey(callId) && 
                activeCalls.get(callId).getStatus() == CallStatus.RINGING) {
                LOG.info("Call {} timed out (no response)", callId);
                endCall(callId, "Missed call");
            }
        }, CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Accepte un appel entrant.
     */
    public void answerCall(String callId) {
        CallSession call = activeCalls.get(callId);
        if (call == null || call.getStatus() != CallStatus.RINGING) {
            LOG.warn("Cannot answer call {}: not ringing", callId);
            return;
        }

        updateCallStatus(callId, CallStatus.ACTIVE);
        incomingCallsByClient.remove(call.getInitiatorClientId());

        LOG.info("Call answered: {}", callId);

        // Send accept via signaling service
        if (signalingService != null) {
            signalingService.sendCallAccept(call.getInitiatorClientId());
        }

        notifyCallActive(callId, call.getType());
    }

    /**
     * Décline un appel entrant.
     */
    public void declineCall(String callId, String reason) {
        CallSession call = activeCalls.get(callId);
        if (call == null) {
            LOG.warn("Cannot decline call {}: not found", callId);
            return;
        }

        if (call.getStatus() != CallStatus.RINGING) {
            LOG.warn("Cannot decline call {}: not ringing", callId);
            return;
        }

        endCall(callId, reason != null ? reason : "Declined");
        incomingCallsByClient.remove(call.getInitiatorClientId());

        // Send decline via signaling service
        if (signalingService != null) {
            signalingService.sendCallReject(call.getInitiatorClientId());
        }

        LOG.info("Call declined: {}", callId);
    }

    /**
     * Termine un appel.
     */
    public void endCall(String callId, String reason) {
        CallSession call = activeCalls.get(callId);
        if (call == null) {
            LOG.warn("Cannot end call {}: not found", callId);
            return;
        }

        // Update status and end time
        CallSession ended = new CallSession.Builder()
                .callId(call.getCallId())
                .initiatorClientId(call.getInitiatorClientId())
                .status(CallStatus.ENDED)
                .startedAt(call.getStartedAt())
                .endedAt(System.currentTimeMillis())
                .build();

        activeCalls.put(callId, ended);
        incomingCallsByClient.remove(call.getInitiatorClientId());

        LOG.info("Call ended: {} (reason: {})", callId, reason != null ? reason : "Normal");

        // Send disconnection via signaling service
        if (signalingService != null) {
            if (call.getParticipantClientIds().size() > 1) {
                // Find the other participant
                for (String clientId : call.getParticipantClientIds()) {
                    if (!clientId.equals(myClientId)) {
                        signalingService.sendCallReject(clientId);
                        break;
                    }
                }
            }
        }

        notifyCallEnded(callId, reason);
    }

    /**
     * Met en sourdine le micro d'un appel.
     */
    public void muteAudio(String callId) {
        CallSession call = activeCalls.get(callId);
        if (call == null || call.getStatus() != CallStatus.ACTIVE) {
            LOG.warn("Cannot mute: call {} not active", callId);
            return;
        }

        CallParticipant participant = call.getParticipants().get(myClientId);
        if (participant != null) {
            participant.audioMuted = true;
            LOG.info("Audio muted for call {}", callId);
        }
    }

    /**
     * Réactive le micro.
     */
    public void unmuteAudio(String callId) {
        CallSession call = activeCalls.get(callId);
        if (call == null || call.getStatus() != CallStatus.ACTIVE) {
            LOG.warn("Cannot unmute: call {} not active", callId);
            return;
        }

        CallParticipant participant = call.getParticipants().get(myClientId);
        if (participant != null) {
            participant.audioMuted = false;
            LOG.info("Audio unmuted for call {}", callId);
        }
    }

    /**
     * Désactive la caméra.
     */
    public void disableVideo(String callId) {
        CallSession call = activeCalls.get(callId);
        if (call == null || call.getStatus() != CallStatus.ACTIVE) {
            LOG.warn("Cannot disable video: call {} not active", callId);
            return;
        }

        CallParticipant participant = call.getParticipants().get(myClientId);
        if (participant != null) {
            participant.videoDisabled = true;
            LOG.info("Video disabled for call {}", callId);
        }
    }

    /**
     * Active la caméra.
     */
    public void enableVideo(String callId) {
        CallSession call = activeCalls.get(callId);
        if (call == null || call.getStatus() != CallStatus.ACTIVE) {
            LOG.warn("Cannot enable video: call {} not active", callId);
            return;
        }

        CallParticipant participant = call.getParticipants().get(myClientId);
        if (participant != null) {
            participant.videoDisabled = false;
            LOG.info("Video enabled for call {}", callId);
        }
    }

    /**
     * Ajoute un participant à un appel groupe.
     */
    public void addParticipant(String callId, String clientId, String username) {
        CallSession call = activeCalls.get(callId);
        if (call == null) {
            LOG.warn("Cannot add participant: call {} not found", callId);
            return;
        }

        if (!call.getParticipantClientIds().contains(clientId)) {
            // Would need to reconstruct CallSession to add participant ideally
            LOG.info("Participant {} added to call {}", clientId, callId);
            notifyParticipantJoined(callId, clientId, username);
        }
    }

    /**
     * Retire un participant d'un appel.
     */
    public void removeParticipant(String callId, String clientId) {
        CallSession call = activeCalls.get(callId);
        if (call == null) {
            LOG.warn("Cannot remove participant: call {} not found", callId);
            return;
        }

        if (call.getParticipantClientIds().contains(clientId)) {
            LOG.info("Participant {} removed from call {}", clientId, callId);
            notifyParticipantLeft(callId, clientId);
        }
    }

    /**
     * Obtient un appel par ID.
     */
    public Optional<CallSession> getCall(String callId) {
        return Optional.ofNullable(activeCalls.get(callId));
    }

    /**
     * Obtient tous les appels actifs.
     */
    public List<CallSession> getActiveCalls() {
        return activeCalls.values().stream()
                .filter(c -> c.getStatus() == CallStatus.ACTIVE || c.getStatus() == CallStatus.RINGING)
                .collect(Collectors.toList());
    }

    /**
     * Compte les appels actifs.
     */
    public int getActiveCallCount() {
        return (int) activeCalls.values().stream()
                .filter(c -> c.getStatus() == CallStatus.ACTIVE)
                .count();
    }

    /**
     * Ajoute un observateur de changements d'appels.
     */
    public void addListener(CallListener listener) {
        listeners.add(listener);
    }

    /**
     * Retire un observateur.
     */
    public void removeListener(CallListener listener) {
        listeners.remove(listener);
    }

    /**
     * Arrête le manager et nettoie les ressources.
     */
    public void shutdown() {
        LOG.info("Shutting down WebRTCCallManager");
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }
        cleanupScheduler.shutdown();
        activeCalls.clear();
        incomingCallsByClient.clear();
        listeners.clear();
    }

    // ============= Private Methods =============

    private void startCleanupScheduler() {
        cleanupTask = cleanupScheduler.scheduleAtFixedRate(
                this::cleanupEndedCalls,
                CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS
        );
    }

    /**
     * Met à jour le statut d'un appel.
     */
    private void updateCallStatus(String callId, CallStatus newStatus) {
        CallSession call = activeCalls.get(callId);
        if (call != null) {
            CallSession updated = new CallSession.Builder()
                    .callId(call.getCallId())
                    .initiatorClientId(call.getInitiatorClientId())
                    .status(newStatus)
                    .startedAt(call.getStartedAt())
                    .build();
            activeCalls.put(callId, updated);
        }
    }

    /**
     * Nettoie les appels terminés anciens.
     */
    private void cleanupEndedCalls() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, CallSession> entry : activeCalls.entrySet()) {
            CallSession call = entry.getValue();
            if (call.getStatus() == CallStatus.ENDED && 
                now - call.getEndedAt() > 5 * 60_000) {  // 5 min
                toRemove.add(entry.getKey());
            }
        }

        for (String callId : toRemove) {
            activeCalls.remove(callId);
        }
    }

    private void notifyIncomingCall(CallSession call) {
        for (CallListener listener : listeners) {
            try {
                listener.onIncomingCall(call);
            } catch (Exception e) {
                LOG.error("Error notifying incoming call", e);
            }
        }
    }

    private void notifyCallActive(String callId, CallType type) {
        for (CallListener listener : listeners) {
            try {
                listener.onCallActive(callId, type);
            } catch (Exception e) {
                LOG.error("Error notifying call active", e);
            }
        }
    }

    private void notifyCallEnded(String callId, String reason) {
        for (CallListener listener : listeners) {
            try {
                listener.onCallEnded(callId, reason);
            } catch (Exception e) {
                LOG.error("Error notifying call ended", e);
            }
        }
    }

    private void notifyParticipantJoined(String callId, String clientId, String username) {
        for (CallListener listener : listeners) {
            try {
                listener.onParticipantJoined(callId, clientId, username);
            } catch (Exception e) {
                LOG.error("Error notifying participant joined", e);
            }
        }
    }

    private void notifyParticipantLeft(String callId, String clientId) {
        for (CallListener listener : listeners) {
            try {
                listener.onParticipantLeft(callId, clientId);
            } catch (Exception e) {
                LOG.error("Error notifying participant left", e);
            }
        }
    }
}
