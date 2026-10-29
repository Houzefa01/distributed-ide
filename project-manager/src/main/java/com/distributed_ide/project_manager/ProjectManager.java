package com.distributed_ide.project_manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central project manager for creating, retrieving, and managing projects.
 * Thread-safe with support for multi-language projects and CRDT integration.
 */
public class ProjectManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectManager.class);
    
    private final Map<String, Project> projects;
    private final Map<String, ProjectStructure> structures;
    private final Map<String, ProjectMetadata> metadataMap;
    private final Map<String, Set<String>> userProjects;
    private int maxProjectsPerUser;
    
    public ProjectManager() {
        this.projects = new ConcurrentHashMap<>();
        this.structures = new ConcurrentHashMap<>();
        this.metadataMap = new ConcurrentHashMap<>();
        this.userProjects = new ConcurrentHashMap<>();
        this.maxProjectsPerUser = 20;
    }
    
    public ProjectManager(int maxProjectsPerUser) {
        this();
        this.maxProjectsPerUser = maxProjectsPerUser;
    }
    
    /**
     * Creates a new project.
     */
    public Project createProject(String projectId, String name, Project.ProjectType type, String ownerUserId) {
        if (projects.containsKey(projectId)) {
            throw new IllegalArgumentException("Project " + projectId + " already exists");
        }
        
        Set<String> userProj = userProjects.computeIfAbsent(ownerUserId, k -> ConcurrentHashMap.newKeySet());
        if (userProj.size() >= maxProjectsPerUser) {
            throw new IllegalStateException("User has reached maximum projects limit: " + maxProjectsPerUser);
        }
        
        Project project = new Project(projectId, name, type);
        project.setOwnerUserId(ownerUserId);
        project.setStatus(Project.ProjectStatus.OPEN);
        
        projects.put(projectId, project);
        structures.put(projectId, new ProjectStructure(projectId));
        metadataMap.put(projectId, new ProjectMetadata(projectId));
        userProj.add(projectId);
        
        logger.info("Created project: {} (type: {}, owner: {})", projectId, type, ownerUserId);
        return project;
    }
    
    /**
     * Gets a project by ID.
     */
    public Project getProject(String projectId) {
        return projects.get(projectId);
    }
    
    /**
     * Gets the file structure of a project.
     */
    public ProjectStructure getProjectStructure(String projectId) {
        return structures.get(projectId);
    }
    
    /**
     * Gets the metadata of a project.
     */
    public ProjectMetadata getProjectMetadata(String projectId) {
        return metadataMap.get(projectId);
    }
    
    /**
     * Lists all projects for a user.
     */
    public List<Project> getUserProjects(String userId) {
        Set<String> projectIds = userProjects.get(userId);
        if (projectIds == null) {
            return new ArrayList<>();
        }
        
        return projectIds.stream()
                .map(projects::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * Lists all open projects for a user.
     */
    public List<Project> getUserOpenProjects(String userId) {
        return getUserProjects(userId).stream()
                .filter(Project::isOpen)
                .collect(Collectors.toList());
    }
    
    /**
     * Lists projects by type.
     */
    public List<Project> getProjectsByType(Project.ProjectType type) {
        return projects.values().stream()
                .filter(p -> p.getType() == type)
                .collect(Collectors.toList());
    }
    
    /**
     * Lists all projects.
     */
    public Collection<Project> getAllProjects() {
        return new ArrayList<>(projects.values());
    }
    
    /**
     * Adds a file to a project.
     */
    public void addFileToProject(String projectId, ProjectFile file, String crdtDocumentId) {
        Project project = getProject(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        
        ProjectStructure structure = getProjectStructure(projectId);
        file.setCrdtDocumentId(crdtDocumentId);
        structure.addFile(file);
        project.incrementFileCount();
        
        logger.debug("Added file to project {}: {} (CRDT: {})", projectId, file.getRelativePath(), crdtDocumentId);
    }
    
    /**
     * Removes a file from a project.
     */
    public void removeFileFromProject(String projectId, String fileId) {
        Project project = getProject(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        
        ProjectStructure structure = getProjectStructure(projectId);
        structure.removeFile(fileId);
        project.decrementFileCount();
        
        logger.debug("Removed file from project {}: {}", projectId, fileId);
    }
    
    /**
     * Gets a file from a project.
     */
    public ProjectFile getFile(String projectId, String fileId) {
        ProjectStructure structure = structures.get(projectId);
        return structure != null ? structure.getFile(fileId) : null;
    }
    
    /**
     * Gets all files in a project.
     */
    public Collection<ProjectFile> getProjectFiles(String projectId) {
        ProjectStructure structure = structures.get(projectId);
        return structure != null ? structure.getAllFiles() : new ArrayList<>();
    }
    
    /**
     * Closes a project.
     */
    public void closeProject(String projectId) {
        Project project = getProject(projectId);
        if (project != null) {
            project.setStatus(Project.ProjectStatus.CLOSED);
            logger.info("Closed project: {}", projectId);
        }
    }
    
    /**
     * Archives a project.
     */
    public void archiveProject(String projectId) {
        Project project = getProject(projectId);
        if (project != null) {
            project.setStatus(Project.ProjectStatus.ARCHIVED);
            logger.info("Archived project: {}", projectId);
        }
    }
    
    /**
     * Deletes a project (removes from manager).
     */
    public void deleteProject(String projectId) {
        Project project = projects.remove(projectId);
        if (project != null) {
            structures.remove(projectId);
            metadataMap.remove(projectId);
            
            if (project.getOwnerUserId() != null) {
                Set<String> userProj = userProjects.get(project.getOwnerUserId());
                if (userProj != null) {
                    userProj.remove(projectId);
                }
            }
            
            logger.info("Deleted project: {}", projectId);
        }
    }
    
    /**
     * Gets statistics for a project.
     */
    public Map<String, Object> getProjectStats(String projectId) {
        Project project = getProject(projectId);
        if (project == null) {
            return new HashMap<>();
        }
        
        ProjectStructure structure = getProjectStructure(projectId);
        ProjectMetadata metadata = getProjectMetadata(projectId);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("projectId", projectId);
        stats.put("name", project.getName());
        stats.put("type", project.getType());
        stats.put("status", project.getStatus());
        stats.put("totalFiles", project.getTotalFiles());
        stats.put("createdAt", project.getCreatedAt());
        stats.put("owner", project.getOwnerUserId());
        
        if (structure != null) {
            stats.put("fileStructure", structure.getTotalFileCount());
        }
        
        if (metadata != null) {
            stats.put("contributors", metadata.getContributorCount());
            stats.put("tags", metadata.getTags());
        }
        
        return stats;
    }
    
    /**
     * Searches for files by name pattern across all projects.
     */
    public List<ProjectFile> searchFiles(String pattern) {
        return projects.keySet().stream()
                .map(structures::get)
                .flatMap(s -> s.findFiles(pattern).stream())
                .collect(Collectors.toList());
    }
    
    /**
     * Gets the total project count.
     */
    public int getTotalProjectCount() {
        return projects.size();
    }
    
    /**
     * Gets the total file count across all projects.
     */
    public int getTotalFileCount() {
        return structures.values().stream()
                .mapToInt(ProjectStructure::getTotalFileCount)
                .sum();
    }
    
    /**
     * Sets the maximum projects per user.
     */
    public void setMaxProjectsPerUser(int max) {
        this.maxProjectsPerUser = max;
    }
}
