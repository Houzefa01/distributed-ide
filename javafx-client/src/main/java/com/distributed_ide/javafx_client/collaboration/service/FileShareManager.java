package com.distributed_ide.javafx_client.collaboration.service;

import com.distributed_ide.javafx_client.collaboration.model.ShareRequest;
import com.distributed_ide.javafx_client.collaboration.model.ShareRequest.ShareStatus;
import com.distributed_ide.javafx_client.collaboration.model.ShareRequest.ShareType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Gère le partage de projets et fichiers entre collaborateurs.
 * Demande d'acceptation, synchronisation du contenu, gestion d'état.
 */
public class FileShareManager {
    private static final Logger LOG = LoggerFactory.getLogger(FileShareManager.class);

    // Timeouts
    private static final long SHARE_REQUEST_TIMEOUT_MS = 5 * 60_000;  // 5 min
    private static final long CLEANUP_INTERVAL_MS = 60_000;            // 1 min

    // Demandes de partage par ID
    private final Map<String, ShareRequest> shareRequests = new ConcurrentHashMap<>();
    
    // Demandes entrantes par client (une seule active)
    private final Map<String, String> incomingSharesByClient = new ConcurrentHashMap<>();
    
    // Ressources partagées (projectId -> list of clients)
    private final Map<String, Set<String>> sharedResources = new ConcurrentHashMap<>();

    // Listeners
    private final List<ShareListener> listeners = new CopyOnWriteArrayList<>();

    // Scheduler
    private final ScheduledExecutorService cleanupScheduler;
    private volatile ScheduledFuture<?> cleanupTask;
    private volatile String myClientId;
    private volatile String myUsername;

    public interface ShareListener {
        void onShareRequested(ShareRequest request);
        void onShareAccepted(String shareRequestId, String resourcePath);
        void onShareRejected(String shareRequestId, String reason);
        void onResourceShared(String resourcePath, String clientId);
        void onResourceUnshared(String resourcePath, String clientId);
    }

    public FileShareManager() {
        this.cleanupScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "file-share-cleanup");
            t.setDaemon(true);
            return t;
        });
        startCleanupScheduler();
    }

    /**
     * Initialise le manager.
     */
    public void initialize(String clientId, String username) {
        this.myClientId = clientId;
        this.myUsername = username;
        LOG.info("FileShareManager initialized: {} ({})", username, clientId);
    }

    /**
     * Demande le partage d'un projet.
     */
    public String requestShareProject(Set<String> targetClientIds, String projectPath, String projectName) {
        return requestShare(ShareType.PROJECT, targetClientIds, projectPath, projectName);
    }

    /**
     * Demande le partage d'un fichier.
     */
    public String requestShareFile(Set<String> targetClientIds, String filePath, String fileName) {
        return requestShare(ShareType.FILE, targetClientIds, filePath, fileName);
    }

    /**
     * Demande le partage d'un dossier.
     */
    public String requestShareFolder(Set<String> targetClientIds, String folderPath, String folderName) {
        return requestShare(ShareType.FOLDER, targetClientIds, folderPath, folderName);
    }

    /**
     * Accepte une demande de partage.
     */
    public void acceptShare(String shareRequestId) {
        ShareRequest request = shareRequests.get(shareRequestId);
        if (request == null) {
            LOG.warn("Cannot accept share {}: not found", shareRequestId);
            return;
        }

        if (request.getStatus() != ShareStatus.PENDING) {
            LOG.warn("Cannot accept share {}: not pending", shareRequestId);
            return;
        }

        // Mark as accepted
        ShareRequest accepted = new ShareRequest.Builder()
                .shareRequestId(request.getShareRequestId())
                .initiatorClientId(request.getInitiatorClientId())
                .initiatorUsername(request.getInitiatorUsername())
                .targetClientIds(request.getTargetClientIds())
                .type(request.getType())
                .resourcePath(request.getResourcePath())
                .resourceName(request.getResourceName())
                .status(ShareStatus.ACCEPTED)
                .build();

        shareRequests.put(shareRequestId, accepted);
        incomingSharesByClient.remove(request.getInitiatorClientId());

        LOG.info("Share accepted: {} ({})", shareRequestId, request.getResourceName());

        // Register shared resource
        sharedResources.computeIfAbsent(request.getResourcePath(), k -> ConcurrentHashMap.newKeySet())
                .add(request.getInitiatorClientId());

        notifyShareAccepted(shareRequestId, request.getResourcePath());
    }

    /**
     * Décline une demande de partage.
     */
    public void declineShare(String shareRequestId, String reason) {
        ShareRequest request = shareRequests.get(shareRequestId);
        if (request == null) {
            LOG.warn("Cannot decline share {}: not found", shareRequestId);
            return;
        }

        if (request.getStatus() != ShareStatus.PENDING) {
            LOG.warn("Cannot decline share {}: not pending", shareRequestId);
            return;
        }

        // Mark as rejected
        ShareRequest rejected = new ShareRequest.Builder()
                .shareRequestId(request.getShareRequestId())
                .initiatorClientId(request.getInitiatorClientId())
                .initiatorUsername(request.getInitiatorUsername())
                .targetClientIds(request.getTargetClientIds())
                .type(request.getType())
                .resourcePath(request.getResourcePath())
                .resourceName(request.getResourceName())
                .status(ShareStatus.REJECTED)
                .build();

        shareRequests.put(shareRequestId, rejected);
        incomingSharesByClient.remove(request.getInitiatorClientId());

        LOG.info("Share declined: {} (reason: {})", shareRequestId, reason != null ? reason : "User declined");
        notifyShareRejected(shareRequestId, reason);
    }

    /**
     * Retire une ressource du partage.
     */
    public void unshareResource(String resourcePath) {
        Set<String> clients = sharedResources.remove(resourcePath);
        if (clients != null) {
            for (String clientId : clients) {
                LOG.info("Resource unshared: {} from {}", resourcePath, clientId);
                notifyResourceUnshared(resourcePath, clientId);
            }
        }
    }

    /**
     * Obtient une demande de partage.
     */
    public Optional<ShareRequest> getShareRequest(String shareRequestId) {
        return Optional.ofNullable(shareRequests.get(shareRequestId));
    }

    /**
     * Obtient toutes les demandes en attente.
     */
    public List<ShareRequest> getPendingRequests() {
        return shareRequests.values().stream()
                .filter(req -> req.getStatus() == ShareStatus.PENDING)
                .collect(Collectors.toList());
    }

    /**
     * Obtient les demandes en attente d'un client donnée.
     */
    public List<ShareRequest> getPendingRequestsFromClient(String initiatorClientId) {
        if (initiatorClientId == null) return new ArrayList<>();
        return getPendingRequests().stream()
                .filter(req -> req.getInitiatorClientId().equals(initiatorClientId))
                .collect(Collectors.toList());
    }

    /**
     * Vérifie si une ressource est partagée.
     */
    public boolean isResourceShared(String resourcePath) {
        return sharedResources.containsKey(resourcePath);
    }

    /**
     * Obtient les clients avec lesquels une ressource est partagée.
     */
    public Set<String> getClientsForResource(String resourcePath) {
        Set<String> clients = sharedResources.get(resourcePath);
        if (clients == null) return ConcurrentHashMap.newKeySet();
        return Set.copyOf(clients);
    }

    /**
     * Ajoute un observateur de changements de partage.
     */
    public void addListener(ShareListener listener) {
        listeners.add(listener);
    }

    /**
     * Retire un observateur.
     */
    public void removeListener(ShareListener listener) {
        listeners.remove(listener);
    }

    /**
     * Arrête le manager et nettoie les ressources.
     */
    public void shutdown() {
        LOG.info("Shutting down FileShareManager");
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }
        cleanupScheduler.shutdown();
        shareRequests.clear();
        incomingSharesByClient.clear();
        sharedResources.clear();
        listeners.clear();
    }

    // ============= Private Methods =============

    private String requestShare(ShareType type, Set<String> targetClientIds, String resourcePath, String resourceName) {
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            throw new IllegalArgumentException("resourcePath cannot be null or empty");
        }

        if (targetClientIds == null || targetClientIds.isEmpty()) {
            throw new IllegalArgumentException("targetClientIds cannot be null or empty");
        }

        String shareId = "share_" + UUID.randomUUID().toString().substring(0, 12);

        ShareRequest request = new ShareRequest.Builder()
                .shareRequestId(shareId)
                .initiatorClientId(myClientId)
                .initiatorUsername(myUsername)
                .targetClientIds(targetClientIds)
                .type(type)
                .resourcePath(resourcePath)
                .resourceName(resourceName != null ? resourceName : extractResourceName(resourcePath))
                .status(ShareStatus.PENDING)
                .build();

        shareRequests.put(shareId, request);

        LOG.info("Share requested: {} type={} targets={}", shareId, type, targetClientIds.size());
        notifyShareRequested(request);

        // Auto-expire after timeout
        cleanupScheduler.schedule(() -> {
            if (shareRequests.containsKey(shareId) && 
                shareRequests.get(shareId).getStatus() == ShareStatus.PENDING) {
                LOG.info("Share request {} timed out (no response)", shareId);
                declineShare(shareId, "Timeout");
            }
        }, SHARE_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        return shareId;
    }

    private void startCleanupScheduler() {
        cleanupTask = cleanupScheduler.scheduleAtFixedRate(
                this::cleanupExpiredRequests,
                CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS
        );
    }

    /**
     * Nettoie les demandes expirées ou traitées.
     */
    private void cleanupExpiredRequests() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, ShareRequest> entry : shareRequests.entrySet()) {
            ShareRequest req = entry.getValue();
            
            // Remove completed/rejected requests after 5 min
            if (req.getStatus() != ShareStatus.PENDING) {
                if (now - req.getCreatedAt() > 5 * 60_000) {
                    toRemove.add(entry.getKey());
                }
            }
        }

        for (String shareId : toRemove) {
            shareRequests.remove(shareId);
        }
    }

    /**
     * Extrait le nom de la ressource du chemin.
     */
    private String extractResourceName(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) return "Resource";
        
        String[] parts = resourcePath.split("[/\\\\]");
        String name = parts[parts.length - 1];
        return name.isEmpty() ? "Resource" : name;
    }

    private void notifyShareRequested(ShareRequest request) {
        for (ShareListener listener : listeners) {
            try {
                listener.onShareRequested(request);
            } catch (Exception e) {
                LOG.error("Error notifying share requested", e);
            }
        }
    }

    private void notifyShareAccepted(String shareRequestId, String resourcePath) {
        for (ShareListener listener : listeners) {
            try {
                listener.onShareAccepted(shareRequestId, resourcePath);
            } catch (Exception e) {
                LOG.error("Error notifying share accepted", e);
            }
        }
    }

    private void notifyShareRejected(String shareRequestId, String reason) {
        for (ShareListener listener : listeners) {
            try {
                listener.onShareRejected(shareRequestId, reason);
            } catch (Exception e) {
                LOG.error("Error notifying share rejected", e);
            }
        }
    }

    private void notifyResourceShared(String resourcePath, String clientId) {
        for (ShareListener listener : listeners) {
            try {
                listener.onResourceShared(resourcePath, clientId);
            } catch (Exception e) {
                LOG.error("Error notifying resource shared", e);
            }
        }
    }

    private void notifyResourceUnshared(String resourcePath, String clientId) {
        for (ShareListener listener : listeners) {
            try {
                listener.onResourceUnshared(resourcePath, clientId);
            } catch (Exception e) {
                LOG.error("Error notifying resource unshared", e);
            }
        }
    }
}
