package com.distributed_ide.project_manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProjectMetadata class.
 */
public class ProjectMetadataTest {
    
    private ProjectMetadata metadata;
    
    @BeforeEach
    public void setUp() {
        metadata = new ProjectMetadata("proj-1");
    }
    
    @Test
    public void testMetadataCreation() {
        assertEquals("proj-1", metadata.getProjectId());
        assertEquals("1.0.0", metadata.getVersion());
        assertNull(metadata.getPrimaryLanguage());
        assertNull(metadata.getLicense());
    }
    
    @Test
    public void testAddContributor() {
        ProjectMetadata.Contributor contributor = new ProjectMetadata.Contributor("user-1", "John Doe", "john@example.com");
        metadata.addContributor(contributor);
        
        assertEquals(1, metadata.getContributorCount());
        assertNotNull(metadata.getContributor("user-1"));
        assertEquals("John Doe", metadata.getContributor("user-1").getName());
    }
    
    @Test
    public void testRemoveContributor() {
        ProjectMetadata.Contributor contributor = new ProjectMetadata.Contributor("user-1", "John Doe", "john@example.com");
        metadata.addContributor(contributor);
        
        metadata.removeContributor("user-1");
        assertNull(metadata.getContributor("user-1"));
        assertEquals(0, metadata.getContributorCount());
    }
    
    @Test
    public void testRecordContribution() {
        ProjectMetadata.Contributor contributor = new ProjectMetadata.Contributor("user-1", "John Doe", "john@example.com");
        metadata.addContributor(contributor);
        
        assertEquals(0, contributor.getCommitCount());
        
        metadata.recordContribution("user-1");
        assertEquals(1, metadata.getContributor("user-1").getCommitCount());
        
        metadata.recordContribution("user-1");
        assertEquals(2, metadata.getContributor("user-1").getCommitCount());
    }
    
    @Test
    public void testMultipleContributors() {
        ProjectMetadata.Contributor c1 = new ProjectMetadata.Contributor("user-1", "John", "john@example.com");
        ProjectMetadata.Contributor c2 = new ProjectMetadata.Contributor("user-2", "Jane", "jane@example.com");
        ProjectMetadata.Contributor c3 = new ProjectMetadata.Contributor("user-3", "Mike", "mike@example.com");
        
        metadata.addContributor(c1);
        metadata.addContributor(c2);
        metadata.addContributor(c3);
        
        assertEquals(3, metadata.getContributorCount());
        assertEquals(3, metadata.getAllContributors().size());
    }
    
    @Test
    public void testAddTag() {
        metadata.addTag("java");
        metadata.addTag("web");
        
        assertTrue(metadata.hasTag("java"));
        assertTrue(metadata.hasTag("web"));
        assertEquals(2, metadata.getTags().size());
    }
    
    @Test
    public void testTagCaseInsensitivity() {
        metadata.addTag("Java");
        assertTrue(metadata.hasTag("java"));
        assertTrue(metadata.hasTag("JAVA"));
    }
    
    @Test
    public void testRemoveTag() {
        metadata.addTag("java");
        assertTrue(metadata.hasTag("java"));
        
        metadata.removeTag("java");
        assertFalse(metadata.hasTag("java"));
    }
    
    @Test
    public void testSetTags() {
        metadata.setTags(java.util.List.of("java", "web", "database"));
        
        assertEquals(3, metadata.getTags().size());
        assertTrue(metadata.hasTag("java"));
        assertTrue(metadata.hasTag("web"));
        assertTrue(metadata.hasTag("database"));
    }
    
    @Test
    public void testSetting() {
        metadata.setSetting("build.tool", "maven");
        assertEquals("maven", metadata.getSetting("build.tool"));
    }
    
    @Test
    public void testSettingWithDefault() {
        assertEquals("gradle", metadata.getSetting("build.tool", "gradle"));
        
        metadata.setSetting("build.tool", "maven");
        assertEquals("maven", metadata.getSetting("build.tool", "gradle"));
    }
    
    @Test
    public void testMultipleSettings() {
        metadata.setSetting("build.tool", "maven");
        metadata.setSetting("java.version", "21");
        metadata.setSetting("encoding", "UTF-8");
        
        assertEquals(3, metadata.getAllSettings().size());
        assertEquals("maven", metadata.getSetting("build.tool"));
        assertEquals("21", metadata.getSetting("java.version"));
    }
    
    @Test
    public void testProjectInformation() {
        metadata.setRepositoryUrl("https://github.com/user/repo");
        metadata.setPrimaryLanguage("Java");
        metadata.setLicense("MIT");
        metadata.setVersion("2.0.0");
        
        assertEquals("https://github.com/user/repo", metadata.getRepositoryUrl());
        assertEquals("Java", metadata.getPrimaryLanguage());
        assertEquals("MIT", metadata.getLicense());
        assertEquals("2.0.0", metadata.getVersion());
    }
    
    @Test
    public void testLastActivityUpdate() {
        assertNull(metadata.getLastActivityAt());
        
        metadata.addTag("java");
        assertNotNull(metadata.getLastActivityAt());
    }
    
    @Test
    public void testGetSummary() {
        ProjectMetadata.Contributor contributor = new ProjectMetadata.Contributor("user-1", "John", "john@example.com");
        metadata.addContributor(contributor);
        metadata.addTag("java");
        metadata.setPrimaryLanguage("Java");
        metadata.setLicense("MIT");
        
        var summary = metadata.getSummary();
        assertEquals("proj-1", summary.get("projectId"));
        assertEquals(1, summary.get("contributorCount"));
        assertEquals("Java", summary.get("primaryLanguage"));
        assertEquals("MIT", summary.get("license"));
    }
    
    @Test
    public void testContributorInfo() {
        ProjectMetadata.Contributor contributor = new ProjectMetadata.Contributor("user-1", "John Doe", "john@example.com");
        
        assertEquals("user-1", contributor.getUserId());
        assertEquals("John Doe", contributor.getName());
        assertEquals("john@example.com", contributor.getEmail());
        assertNotNull(contributor.getJoinedAt());
    }
}
