package com.distributed_ide.project_manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Project class.
 */
public class ProjectTest {
    
    private Project project;
    
    @BeforeEach
    public void setUp() {
        project = new Project("proj-1", "My Project", Project.ProjectType.JAVA_PROJECT);
    }
    
    @Test
    public void testProjectCreation() {
        assertEquals("proj-1", project.getProjectId());
        assertEquals("My Project", project.getName());
        assertEquals(Project.ProjectType.JAVA_PROJECT, project.getType());
        assertEquals(Project.ProjectStatus.CREATED, project.getStatus());
        assertEquals(0, project.getTotalFiles());
        assertNotNull(project.getCreatedAt());
    }
    
    @Test
    public void testProjectTypes() {
        Project javaProj = new Project("p1", "Java Proj", Project.ProjectType.JAVA_PROJECT);
        assertEquals("Java", javaProj.getType().getDisplayName());
        
        Project pythonProj = new Project("p2", "Python Proj", Project.ProjectType.PYTHON_PROJECT);
        assertEquals("Python", pythonProj.getType().getDisplayName());
        
        Project webProj = new Project("p3", "Web Proj", Project.ProjectType.WEB_PROJECT);
        assertEquals("Web", webProj.getType().getDisplayName());
        
        Project multiProj = new Project("p4", "Multi Proj", Project.ProjectType.MULTI_LANGUAGE);
        assertEquals("Multi-Language", multiProj.getType().getDisplayName());
    }
    
    @Test
    public void testProjectOwnership() {
        assertNull(project.getOwnerUserId());
        
        project.setOwnerUserId("user-1");
        assertEquals("user-1", project.getOwnerUserId());
    }
    
    @Test
    public void testProjectDescription() {
        assertNull(project.getDescription());
        
        project.setDescription("A sample project");
        assertEquals("A sample project", project.getDescription());
    }
    
    @Test
    public void testProjectBasePath() {
        assertNull(project.getBasePath());
        
        project.setBasePath("/workspace/project1");
        assertEquals("/workspace/project1", project.getBasePath());
    }
    
    @Test
    public void testProjectStatus() {
        assertEquals(Project.ProjectStatus.CREATED, project.getStatus());
        
        project.setStatus(Project.ProjectStatus.OPEN);
        assertEquals(Project.ProjectStatus.OPEN, project.getStatus());
        assertTrue(project.isOpen());
        assertFalse(project.isClosed());
        
        project.setStatus(Project.ProjectStatus.CLOSED);
        assertTrue(project.isClosed());
        assertFalse(project.isOpen());
        assertNotNull(project.getClosedAt());
    }
    
    @Test
    public void testFileCount() {
        assertEquals(0, project.getTotalFiles());
        
        project.incrementFileCount();
        assertEquals(1, project.getTotalFiles());
        
        project.incrementFileCount();
        project.incrementFileCount();
        assertEquals(3, project.getTotalFiles());
        
        project.decrementFileCount();
        assertEquals(2, project.getTotalFiles());
    }
    
    @Test
    public void testProjectMetadata() {
        project.setMetadata("key1", "value1");
        project.setMetadata("key2", 42);
        
        assertEquals("value1", project.getMetadata("key1"));
        assertEquals(42, project.getMetadata("key2"));
        
        assertEquals(2, project.getAllMetadata().size());
    }
    
    @Test
    public void testProjectModificationTime() {
        var initialModified = project.getModifiedAt();
        
        project.setDescription("Updated");
        assertNotNull(project.getModifiedAt());
        
        if (initialModified != null) {
            assertTrue(project.getModifiedAt().isAfter(initialModified));
        }
    }
    
    @Test
    public void testProjectEquality() {
        Project project1 = new Project("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT);
        Project project2 = new Project("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT);
        Project project3 = new Project("proj-2", "Project 2", Project.ProjectType.JAVA_PROJECT);
        
        assertEquals(project1, project2);
        assertNotEquals(project1, project3);
    }
    
    @Test
    public void testArchivedStatus() {
        assertFalse(project.isArchived());
        
        project.setStatus(Project.ProjectStatus.ARCHIVED);
        assertTrue(project.isArchived());
    }
}
