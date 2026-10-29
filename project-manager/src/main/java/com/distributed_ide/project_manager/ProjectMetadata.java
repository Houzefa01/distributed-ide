package com.distributed_ide.project_manager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages project metadata including contributors, collaborators, tags, and settings.
 */
public class ProjectMetadata {
    
    public static class Contributor {
        private final String userId;
        private final String name;
        private final String email;
        private final Instant joinedAt;
        private int commitCount;
        
        public Contributor(String userId, String name, String email) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.joinedAt = Instant.now();
            this.commitCount = 0;
        }
        
        public String getUserId() { return userId; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public Instant getJoinedAt() { return joinedAt; }
        public int getCommitCount() { return commitCount; }
        
        public void incrementCommitCount() {
            commitCount++;
        }
    }
    
    private final String projectId;
    private final Map<String, Contributor> contributors;
    private final Set<String> tags;
    private final Map<String, String> settings;
    private String repositoryUrl;
    private String primaryLanguage;
    private String license;
    private String version;
    private Instant lastActivityAt;
    
    public ProjectMetadata(String projectId) {
        this.projectId = projectId;
        this.contributors = new LinkedHashMap<>();
        this.tags = new HashSet<>();
        this.settings = new HashMap<>();
        this.version = "1.0.0";
    }
    
    // Contributor management
    public void addContributor(Contributor contributor) {
        contributors.put(contributor.getUserId(), contributor);
    }
    
    public void removeContributor(String userId) {
        contributors.remove(userId);
    }
    
    public Contributor getContributor(String userId) {
        return contributors.get(userId);
    }
    
    public Collection<Contributor> getAllContributors() {
        return new ArrayList<>(contributors.values());
    }
    
    public int getContributorCount() {
        return contributors.size();
    }
    
    public void recordContribution(String userId) {
        Contributor contributor = contributors.get(userId);
        if (contributor != null) {
            contributor.incrementCommitCount();
            updateActivity();
        }
    }
    
    // Tag management
    public void addTag(String tag) {
        tags.add(tag.toLowerCase());
        updateActivity();
    }
    
    public void removeTag(String tag) {
        tags.remove(tag.toLowerCase());
    }
    
    public Set<String> getTags() {
        return new HashSet<>(tags);
    }
    
    public void setTags(Collection<String> newTags) {
        tags.clear();
        newTags.forEach(tag -> tags.add(tag.toLowerCase()));
        updateActivity();
    }
    
    public boolean hasTag(String tag) {
        return tags.contains(tag.toLowerCase());
    }
    
    // Settings management
    public void setSetting(String key, String value) {
        settings.put(key, value);
        updateActivity();
    }
    
    public String getSetting(String key) {
        return settings.get(key);
    }
    
    public String getSetting(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }
    
    public Map<String, String> getAllSettings() {
        return new HashMap<>(settings);
    }
    
    // Project information
    public String getProjectId() { return projectId; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public String getPrimaryLanguage() { return primaryLanguage; }
    public String getLicense() { return license; }
    public String getVersion() { return version; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    
    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
        updateActivity();
    }
    
    public void setPrimaryLanguage(String primaryLanguage) {
        this.primaryLanguage = primaryLanguage;
        updateActivity();
    }
    
    public void setLicense(String license) {
        this.license = license;
        updateActivity();
    }
    
    public void setVersion(String version) {
        this.version = version;
        updateActivity();
    }
    
    private void updateActivity() {
        this.lastActivityAt = Instant.now();
    }
    
    /**
     * Gets a summary of project metadata.
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("projectId", projectId);
        summary.put("contributorCount", contributors.size());
        summary.put("tags", tags);
        summary.put("primaryLanguage", primaryLanguage);
        summary.put("license", license);
        summary.put("version", version);
        summary.put("lastActivity", lastActivityAt);
        return summary;
    }
}
