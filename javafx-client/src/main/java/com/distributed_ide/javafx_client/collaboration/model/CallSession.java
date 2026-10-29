package com.distributed_ide.javafx_client.collaboration.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Représente une session d'appel audio/vidéo/screen-share.
 * Gère le statut, les participants et les médias.
 */
public class CallSession {
    private final String callId;
    private final String initiatorClientId;
    private final Set<String> participantClientIds;
    private final CallType type;  // AUDIO | VIDEO | SCREEN_SHARE
    private final CallStatus status;  // RINGING, ACTIVE, ENDED
    private final long startedAt;
    private final long endedAt;
    private final Map<String, CallParticipant> participants;

    public enum CallType {
        AUDIO,
        VIDEO,
        SCREEN_SHARE
    }

    public enum CallStatus {
        RINGING,      // Appel entrant/sortant
        ACTIVE,       // Appel en cours
        DECLINED,     // Décliné par participante
        MISSED,       // Non répondu
        ENDED         // Terminé
    }

    public static class CallParticipant {
        public final String clientId;
        public boolean audioMuted;
        public boolean videoDisabled;
        public String state;  // connecting | connected | failed
        public long joinedAt;

        public CallParticipant(String clientId) {
            this.clientId = clientId;
            this.audioMuted = false;
            this.videoDisabled = false;
            this.state = "connecting";
            this.joinedAt = System.currentTimeMillis();
        }
    }

    private CallSession(Builder builder) {
        this.callId = builder.callId;
        this.initiatorClientId = builder.initiatorClientId;
        this.participantClientIds = new HashSet<>(builder.participantClientIds);
        this.type = builder.type;
        this.status = builder.status;
        this.startedAt = builder.startedAt;
        this.endedAt = builder.endedAt;
        this.participants = new HashMap<>(builder.participants);
    }

    public static class Builder {
        private String callId;
        private String initiatorClientId;
        private Set<String> participantClientIds = new HashSet<>();
        private CallType type = CallType.AUDIO;
        private CallStatus status = CallStatus.RINGING;
        private long startedAt = 0;
        private long endedAt = 0;
        private Map<String, CallParticipant> participants = new HashMap<>();

        public Builder callId(String callId) { this.callId = callId; return this; }
        public Builder initiatorClientId(String id) { this.initiatorClientId = id; return this; }
        public Builder addParticipant(String clientId) { 
            this.participantClientIds.add(clientId);
            this.participants.put(clientId, new CallParticipant(clientId));
            return this; 
        }
        public Builder type(CallType type) { this.type = type; return this; }
        public Builder status(CallStatus status) { this.status = status; return this; }
        public Builder startedAt(long ts) { this.startedAt = ts; return this; }
        public Builder endedAt(long ts) { this.endedAt = ts; return this; }

        public CallSession build() {
            Objects.requireNonNull(callId, "callId must not be null");
            Objects.requireNonNull(initiatorClientId, "initiatorClientId must not be null");
            return new CallSession(this);
        }
    }

    // Getters
    public String getCallId() { return callId; }
    public String getInitiatorClientId() { return initiatorClientId; }
    public Set<String> getParticipantClientIds() { return new HashSet<>(participantClientIds); }
    public CallType getType() { return type; }
    public CallStatus getStatus() { return status; }
    public long getStartedAt() { return startedAt; }
    public long getEndedAt() { return endedAt; }
    public Map<String, CallParticipant> getParticipants() { return new HashMap<>(participants); }

    public long getDurationMs() {
        if (status == CallStatus.RINGING) return 0;
        long end = endedAt > 0 ? endedAt : System.currentTimeMillis();
        return end - startedAt;
    }

    public boolean isActive() {
        return status == CallStatus.ACTIVE;
    }

    public boolean isEnded() {
        return status == CallStatus.ENDED;
    }

    public int getParticipantCount() {
        return participantClientIds.size();
    }

    @Override
    public String toString() {
        return "CallSession{" +
                "callId='" + callId + '\'' +
                ", type=" + type +
                ", status=" + status +
                ", participantCount=" + getParticipantCount() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallSession that = (CallSession) o;
        return Objects.equals(callId, that.callId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callId);
    }
}
