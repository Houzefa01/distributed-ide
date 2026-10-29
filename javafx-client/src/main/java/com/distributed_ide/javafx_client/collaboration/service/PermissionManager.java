package com.distributed_ide.javafx_client.collaboration.service;

import com.distributed_ide.javafx_client.collaboration.model.PermissionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Gère les rôles et permissions des clients collaborateurs.
 * Admin, Editor, Viewer avec contrôle d'accès par document.
 */
public class PermissionManager {
    private static final Logger LOG = LoggerFactory.getLogger(PermissionManager.class);

    private final Map<String, PermissionModel> permissionsMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> lockedDocuments = new ConcurrentHashMap<>();
    private final List<PermissionListener> listeners = new CopyOnWriteArrayList<>();
    private volatile String currentClientId;
    private volatile int maxClients = 50;

    public interface PermissionListener {
        void onPermissionGranted(String clientId, String role);
        void onPermissionRevoked(String clientId);
        void onDocumentLocked(String documentId, String reason);
        void onDocumentUnlocked(String documentId);
        void onClientKicked(String clientId, String reason);
    }

    /**
     * Définit l'ID du client actuel (pour vérifications d'autorité).
     */
    public void setCurrentClientId(String clientId) {
        this.currentClientId = clientId;
    }

    /**
     * Accorde une permission à un client.
     */
    public void grantPermission(PermissionModel permission) {
        if (permission == null || permission.getClientId() == null) {
            LOG.warn("Cannot grant null permission");
            return;
        }

        String clientId = permission.getClientId();
        permissionsMap.put(clientId, permission);
        LOG.info("Permission granted to client {}: role={}", clientId, permission.getRole());
        notifyPermissionGranted(clientId, permission.getRole());
    }

    /**
     * Révoque une permission pour un client.
     */
    public void revokePermission(String clientId) {
        PermissionModel removed = permissionsMap.remove(clientId);
        if (removed != null) {
            LOG.info("Permission revoked for client {}", clientId);
            notifyPermissionRevoked(clientId);
        }
    }

    /**
     * Obtient la permission d'un client.
     */
    public Optional<PermissionModel> getPermission(String clientId) {
        return Optional.ofNullable(permissionsMap.get(clientId));
    }

    /**
     * Obtient le rôle d'un client.
     */
    public String getRole(String clientId) {
        return getPermission(clientId)
                .map(PermissionModel::getRole)
                .orElse("viewer");
    }

    /**
     * Vérifie si un client peut éditer un document.
     */
    public boolean canEditDocument(String clientId, String documentId) {
        // Check if document is locked
        if (isDocumentLocked(documentId)) {
            return false;
        }

        Optional<PermissionModel> permission = getPermission(clientId);
        if (permission.isEmpty()) {
            return false;  // No permission
        }

        PermissionModel perm = permission.get();
        
        // Check role
        if (!perm.getRole().equalsIgnoreCase("editor") && 
            !perm.getRole().equalsIgnoreCase("admin")) {
            return false;  // Viewer role can't edit
        }

        // Check document access
        return perm.hasAccessToDocument(documentId);
    }

    /**
     * Vérifie si un client peut lire un document.
     */
    public boolean canReadDocument(String clientId, String documentId) {
        Optional<PermissionModel> permission = getPermission(clientId);
        if (permission.isEmpty()) {
            return false;  // No permission
        }

        return permission.get().hasAccessToDocument(documentId);
    }

    /**
     * Vérifie si un client a un rôle admin.
     */
    public boolean isAdmin(String clientId) {
        return "admin".equalsIgnoreCase(getRole(clientId));
    }

    /**
     * Vérifie si un client peut expulser d'autres clients.
     */
    public boolean canKickClients(String clientId) {
        return getPermission(clientId)
                .map(PermissionModel::isCanKickClients)
                .orElse(false);
    }

    /**
     * Vérifie si un client peut partager des projets.
     */
    public boolean canShareProjects(String clientId) {
        return getPermission(clientId)
                .map(PermissionModel::isCanShareProjects)
                .orElse(false);
    }

    /**
     * Vérifie si un client peut commencer des appels.
     */
    public boolean canStartCalls(String clientId) {
        return getPermission(clientId)
                .map(PermissionModel::isCanStartCalls)
                .orElse(true);  // Appels autorisés par défaut
    }

    /**
     * Verrouille un document (lecture seule).
     */
    public void lockDocument(String documentId, String reason) {
        if (documentId == null) {
            LOG.warn("Cannot lock null documentId");
            return;
        }

        lockedDocuments.computeIfAbsent(documentId, k -> ConcurrentHashMap.newKeySet())
                .add(reason != null ? reason : "Locked by admin");
        
        LOG.info("Document locked: {} (reason: {})", documentId, reason);
        notifyDocumentLocked(documentId, reason);
    }

    /**
     * Déverrouille un document.
     */
    public void unlockDocument(String documentId) {
        if (documentId == null) return;

        lockedDocuments.remove(documentId);
        LOG.info("Document unlocked: {}", documentId);
        notifyDocumentUnlocked(documentId);
    }

    /**
     * Vérifie si un document est verrouillé.
     */
    public boolean isDocumentLocked(String documentId) {
        return lockedDocuments.containsKey(documentId);
    }

    /**
     * Obtient la raison du verrouillage d'un document.
     */
    public Optional<String> getLockReason(String documentId) {
        Set<String> reasons = lockedDocuments.get(documentId);
        if (reasons == null || reasons.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(reasons.iterator().next());
    }

    /**
     * Défini le nombre maximum de clients autorisés.
     */
    public void setMaxClients(int maxClients) {
        this.maxClients = maxClients;
        LOG.info("Max clients set to: {}", maxClients);
    }

    /**
     * Obtient le nombre maximum de clients.
     */
    public int getMaxClients() {
        return maxClients;
    }

    /**
     * Compte les clients actuels avec une permission valide.
     */
    public int getConnectedClientCount() {
        return (int) permissionsMap.values().stream()
                .filter(PermissionModel::isValid)
                .count();
    }

    /**
     * Vérifie si on peut accepter de nouveaux clients.
     */
    public boolean canAcceptNewClient() {
        return getConnectedClientCount() < maxClients;
    }

    /**
     * Obtient tous les clients avec un rôle spécifique.
     */
    public List<String> getClientsByRole(String role) {
        return permissionsMap.entrySet().stream()
                .filter(e -> role.equalsIgnoreCase(e.getValue().getRole()) && e.getValue().isValid())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Ajoute un observateur de changements de permissions.
     */
    public void addListener(PermissionListener listener) {
        listeners.add(listener);
    }

    /**
     * Retire un observateur.
     */
    public void removeListener(PermissionListener listener) {
        listeners.remove(listener);
    }

    /**
     * Arrête le manager et nettoie les ressources.
     */
    public void shutdown() {
        LOG.info("Shutting down PermissionManager");
        permissionsMap.clear();
        lockedDocuments.clear();
        listeners.clear();
    }

    // ============= Private Methods =============

    private void notifyPermissionGranted(String clientId, String role) {
        for (PermissionListener listener : listeners) {
            try {
                listener.onPermissionGranted(clientId, role);
            } catch (Exception e) {
                LOG.error("Error notifying permission granted", e);
            }
        }
    }

    private void notifyPermissionRevoked(String clientId) {
        for (PermissionListener listener : listeners) {
            try {
                listener.onPermissionRevoked(clientId);
            } catch (Exception e) {
                LOG.error("Error notifying permission revoked", e);
            }
        }
    }

    private void notifyDocumentLocked(String documentId, String reason) {
        for (PermissionListener listener : listeners) {
            try {
                listener.onDocumentLocked(documentId, reason);
            } catch (Exception e) {
                LOG.error("Error notifying document locked", e);
            }
        }
    }

    private void notifyDocumentUnlocked(String documentId) {
        for (PermissionListener listener : listeners) {
            try {
                listener.onDocumentUnlocked(documentId);
            } catch (Exception e) {
                LOG.error("Error notifying document unlocked", e);
            }
        }
    }
}
