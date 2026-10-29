package com.distributed_ide.javafx_client.collaboration.service;

import com.distributed_ide.javafx_client.collaboration.model.ClientPresence;
import com.distributed_ide.javafx_client.collaboration.model.ClientPresence.PresenceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Gère la présence des clients collaborateurs en temps réel.
 * Détecte l'inactivité, met à jour les statuts, et notifie les observateurs.
 */
public class ClientPresenceManager {
    private static final Logger LOG = LoggerFactory.getLogger(ClientPresenceManager.class);

    // Seuils de statut
    private static final long IDLE_THRESHOLD_MS = 2 * 60_000;      // 2 min
    private static final long AWAY_THRESHOLD_MS = 5 * 60_000;      // 5 min
    private static final long MAX_OFFLINE_DURATION_MS = 30 * 60_000; // 30 min

    // Couleurs prédéfinies pour les clients
    private static final String[] COLORS = {
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#FFA07A", "#98D8C8",
            "#F7DC6F", "#BB8FCE", "#85C1E2", "#F8B88B", "#A9DFBF"
    };

    private final Map<String, ClientPresence> clientsMap = new ConcurrentHashMap<>();
    private final List<PresenceListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService statusUpdateScheduler;
    private volatile ScheduledFuture<?> statusUpdateTask;
    private volatile int colorIndex = 0;

    public interface PresenceListener {
        void onClientJoined(ClientPresence presence);
        void onClientLeft(String clientId, String username);
        void onStatusChanged(String clientId, PresenceStatus newStatus, PresenceStatus oldStatus);
        void onDocumentChanged(String clientId, String newDocId, String oldDocId);
        void onActivityDetected(String clientId);
    }

    public ClientPresenceManager() {
        this.statusUpdateScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "presence-status-updater");
            t.setDaemon(true);
            return t;
        });
        startStatusUpdateScheduler();
    }

    /**
     * Enregistre la présence d'un client.
     */
    public void register(ClientPresence presence) {
        if (presence == null || presence.getClientId() == null) {
            LOG.warn("Cannot register null presence");
            return;
        }

        String clientId = presence.getClientId();
        ClientPresence existing = clientsMap.get(clientId);

        if (existing != null) {
            LOG.debug("Client {} already registered, updating", clientId);
            // Update existing presence
            clientsMap.put(clientId, presence);
        } else {
            LOG.info("Client registered: {} ({})", presence.getUsername(), clientId);
            clientsMap.put(clientId, presence);
            notifyClientJoined(presence);
        }
    }

    /**
     * Supprime la présence d'un client.
     */
    public void unregister(String clientId) {
        ClientPresence presence = clientsMap.remove(clientId);
        if (presence != null) {
            LOG.info("Client unregistered: {} ({})", presence.getUsername(), clientId);
            notifyClientLeft(clientId, presence.getUsername());
        }
    }

    /**
     * Met à jour le statut de présence.
     */
    public void updateStatus(String clientId, PresenceStatus newStatus) {
        ClientPresence presence = clientsMap.get(clientId);
        if (presence == null) {
            LOG.warn("Client {} not found", clientId);
            return;
        }

        if (presence.getStatus() != newStatus) {
            ClientPresence updated = new ClientPresence.Builder()
                    .clientId(presence.getClientId())
                    .username(presence.getUsername())
                    .role(presence.getRole())
                    .status(newStatus)
                    .currentDocument(presence.getCurrentDocument())
                    .lastActivityTimestamp(System.currentTimeMillis())
                    .color(presence.getColor())
                    .audioEnabled(presence.isAudioEnabled())
                    .videoEnabled(presence.isVideoEnabled())
                    .sessionToken(presence.getSessionToken())
                    .connectedSince(presence.getConnectedSince())
                    .build();

            clientsMap.put(clientId, updated);
            LOG.debug("Client {} status changed: {} -> {}", clientId, presence.getStatus(), newStatus);
            notifyStatusChanged(clientId, newStatus, presence.getStatus());
        }
    }

    /**
     * Met à jour le document en cours d'édition.
     */
    public void updateCurrentDocument(String clientId, String newDocId) {
        ClientPresence presence = clientsMap.get(clientId);
        if (presence == null) {
            LOG.warn("Client {} not found", clientId);
            return;
        }

        String oldDocId = presence.getCurrentDocument();
        if (!java.util.Objects.equals(oldDocId, newDocId)) {
            ClientPresence updated = new ClientPresence.Builder()
                    .clientId(presence.getClientId())
                    .username(presence.getUsername())
                    .role(presence.getRole())
                    .status(PresenceStatus.EDITING)
                    .currentDocument(newDocId)
                    .lastActivityTimestamp(System.currentTimeMillis())
                    .color(presence.getColor())
                    .audioEnabled(presence.isAudioEnabled())
                    .videoEnabled(presence.isVideoEnabled())
                    .sessionToken(presence.getSessionToken())
                    .connectedSince(presence.getConnectedSince())
                    .build();

            clientsMap.put(clientId, updated);
            LOG.debug("Client {} document changed: {} -> {}", clientId, oldDocId, newDocId);
            notifyDocumentChanged(clientId, newDocId, oldDocId);
        }
    }

    /**
     * Signale de l'activité pour un client (typewriter, scroll, etc.).
     */
    public void recordActivity(String clientId) {
        ClientPresence presence = clientsMap.get(clientId);
        if (presence == null) {
            return;
        }

        // Update timestamp but keep existing status
        ClientPresence updated = new ClientPresence.Builder()
                .clientId(presence.getClientId())
                .username(presence.getUsername())
                .role(presence.getRole())
                .status(presence.getStatus() == PresenceStatus.OFFLINE ? PresenceStatus.ONLINE : presence.getStatus())
                .currentDocument(presence.getCurrentDocument())
                .lastActivityTimestamp(System.currentTimeMillis())
                .color(presence.getColor())
                .audioEnabled(presence.isAudioEnabled())
                .videoEnabled(presence.isVideoEnabled())
                .sessionToken(presence.getSessionToken())
                .connectedSince(presence.getConnectedSince())
                .build();

        clientsMap.put(clientId, updated);
        notifyActivityDetected(clientId);
    }

    /**
     * Obtient tous les clients en ligne.
     */
    public List<ClientPresence> getOnlineClients() {
        return clientsMap.values().stream()
                .filter(c -> c.getStatus() != PresenceStatus.OFFLINE)
                .collect(Collectors.toList());
    }

    /**
     * Obtient tous les clients.
     */
    public List<ClientPresence> getAllClients() {
        return new ArrayList<>(clientsMap.values());
    }

    /**
     * Obtient un client par ID.
     */
    public Optional<ClientPresence> getClient(String clientId) {
        return Optional.ofNullable(clientsMap.get(clientId));
    }

    /**
     * Obtient tous les clients éditant un document spécifique.
     */
    public List<ClientPresence> getClientsEditingDocument(String documentId) {
        if (documentId == null) return Collections.emptyList();
        return clientsMap.values().stream()
                .filter(c -> documentId.equals(c.getCurrentDocument()))
                .collect(Collectors.toList());
    }

    /**
     * Obtient la prochaine couleur pour un nouveau client.
     */
    public String getNextColor() {
        String color = COLORS[colorIndex % COLORS.length];
        colorIndex++;
        return color;
    }

    /**
     * Ajoute un observateur de changements de présence.
     */
    public void addListener(PresenceListener listener) {
        listeners.add(listener);
    }

    /**
     * Retire un observateur.
     */
    public void removeListener(PresenceListener listener) {
        listeners.remove(listener);
    }

    /**
     * Arrête le manager et nettoie les ressources.
     */
    public void shutdown() {
        LOG.info("Shutting down ClientPresenceManager");
        if (statusUpdateTask != null) {
            statusUpdateTask.cancel(false);
        }
        statusUpdateScheduler.shutdown();
        clientsMap.clear();
        listeners.clear();
    }

    // ============= Private Methods =============

    private void startStatusUpdateScheduler() {
        statusUpdateTask = statusUpdateScheduler.scheduleAtFixedRate(
                this::updateInactiveStatuses,
                5, 5, TimeUnit.SECONDS
        );
    }

    /**
     * Détecte l'inactivité et met à jour les statuts.
     */
    private void updateInactiveStatuses() {
        long now = System.currentTimeMillis();

        for (Map.Entry<String, ClientPresence> entry : new HashMap<>(clientsMap).entrySet()) {
            String clientId = entry.getKey();
            ClientPresence presence = entry.getValue();

            if (presence.getStatus() == PresenceStatus.OFFLINE) {
                // Auto-cleanup after too long offline
                if (now - presence.getLastActivityTimestamp() > MAX_OFFLINE_DURATION_MS) {
                    unregister(clientId);
                }
                continue;
            }

            long idleDuration = now - presence.getLastActivityTimestamp();

            if (idleDuration > AWAY_THRESHOLD_MS) {
                if (presence.getStatus() != PresenceStatus.AWAY) {
                    updateStatus(clientId, PresenceStatus.AWAY);
                }
            } else if (idleDuration > IDLE_THRESHOLD_MS) {
                if (presence.getStatus() != PresenceStatus.IDLE) {
                    updateStatus(clientId, PresenceStatus.IDLE);
                }
            } else if (presence.getStatus() == PresenceStatus.IDLE || presence.getStatus() == PresenceStatus.AWAY) {
                // Activity detected, go back to ONLINE
                updateStatus(clientId, PresenceStatus.ONLINE);
            }
        }
    }

    private void notifyClientJoined(ClientPresence presence) {
        for (PresenceListener listener : listeners) {
            try {
                listener.onClientJoined(presence);
            } catch (Exception e) {
                LOG.error("Error notifying listener of client joined", e);
            }
        }
    }

    private void notifyClientLeft(String clientId, String username) {
        for (PresenceListener listener : listeners) {
            try {
                listener.onClientLeft(clientId, username);
            } catch (Exception e) {
                LOG.error("Error notifying listener of client left", e);
            }
        }
    }

    private void notifyStatusChanged(String clientId, PresenceStatus newStatus, PresenceStatus oldStatus) {
        for (PresenceListener listener : listeners) {
            try {
                listener.onStatusChanged(clientId, newStatus, oldStatus);
            } catch (Exception e) {
                LOG.error("Error notifying listener of status change", e);
            }
        }
    }

    private void notifyDocumentChanged(String clientId, String newDocId, String oldDocId) {
        for (PresenceListener listener : listeners) {
            try {
                listener.onDocumentChanged(clientId, newDocId, oldDocId);
            } catch (Exception e) {
                LOG.error("Error notifying listener of document change", e);
            }
        }
    }

    private void notifyActivityDetected(String clientId) {
        for (PresenceListener listener : listeners) {
            try {
                listener.onActivityDetected(clientId);
            } catch (Exception e) {
                LOG.error("Error notifying listener of activity", e);
            }
        }
    }
}
