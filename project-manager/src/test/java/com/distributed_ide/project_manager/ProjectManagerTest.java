package com.distributed_ide.project_manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProjectManager class.
 */
public class ProjectManagerTest {
    
    private ProjectManager manager;
    
    @BeforeEach
    public void setUp() {
        manager = new ProjectManager();
    }
    
    @Test
    public void testManagerCreation() {
        assertNotNull(manager);
        assertEquals(0, manager.getTotalProjectCount());
    }
    
    @Test
    public void testCreateProject() {
        Project project = manager.createProject("proj-1", "My Project", Project.ProjectType.JAVA_PROJECT, "user-1");
        
        assertNotNull(project);
        assertEquals("proj-1", project.getProjectId());
        assertEquals("user-1", project.getOwnerUserId());
        assertEquals(Project.ProjectStatus.OPEN, project.getStatus());
        assertEquals(1, manager.getTotalProjectCount());
    }
    
    @Test
    public void testGetProject() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        
        Project found = manager.getProject("proj-1");
        assertNotNull(found);
        assertEquals("proj-1", found.getProjectId());
    }
    
    @Test
    public void testDuplicateProjectCreation() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        
        assertThrows(IllegalArgumentException.class, () -> {
            manager.createProject("proj-1", "Project 2", Project.ProjectType.JAVA_PROJECT, "user-2");
        });
    }
    
    @Test
    public void testGetProjectStructure() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        
        ProjectStructure structure = manager.getProjectStructure("proj-1");
        assertNotNull(structure);
        assertEquals(0, structure.getTotalFileCount());
    }
    
    @Test
    public void testGetProjectMetadata() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        
        ProjectMetadata metadata = manager.getProjectMetadata("proj-1");
        assertNotNull(metadata);
        assertEquals("proj-1", metadata.getProjectId());
    }
    
    @Test
    public void testAddFileToProject() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        
        ProjectFile file = new ProjectFile("f-1", "proj-1", "App.java", ProjectFile.FileType.JAVA);
        manager.addFileToProject("proj-1", file, "crdt-1");
        
        Project project = manager.getProject("proj-1");
        assertEquals(1, project.getTotalFiles());
        assertEquals("crdt-1", file.getCrdtDocumentId());
    }
    
    @Test
    public void testRemoveFileFromProject() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        
        ProjectFile file = new ProjectFile("f-1", "proj-1", "App.java", ProjectFile.FileType.JAVA);
        manager.addFileToProject("proj-1", file, "crdt-1");
        
        manager.removeFileFromProject("proj-1", "f-1");
        
        Project project = manager.getProject("proj-1");
        assertEquals(0, project.getTotalFiles());
    }
    
    @Test
    public void testGetUserProjects() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        manager.createProject("proj-2", "Project 2", Project.ProjectType.PYTHON_PROJECT, "user-1");
        manager.createProject("proj-3", "Project 3", Project.ProjectType.WEB_PROJECT, "user-2");
        
        List<Project> userProjects = manager.getUserProjects("user-1");
        assertEquals(2, userProjects.size());
    }
    
    @Test
    public void testGetUserOpenProjects() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        manager.createProject("proj-2", "Project 2", Project.ProjectType.PYTHON_PROJECT, "user-1");
        
        manager.closeProject("proj-2");
        
        List<Project> openProjects = manager.getUserOpenProjects("user-1");
        assertEquals(1, openProjects.size());
    }
    
    @Test
    public void testGetProjectsByType() {
        manager.createProject("proj-1", "Java Project", Project.ProjectType.JAVA_PROJECT, "user-1");
        manager.createProject("proj-2", "Java Project 2", Project.ProjectType.JAVA_PROJECT, "user-1");
        manager.createProject("proj-3", "Python Project", Project.ProjectType.PYTHON_PROJECT, "user-2");
        
        List<Project> javaProjects = manager.getProjectsByType(Project.ProjectType.JAVA_PROJECT);
        assertEquals(2, javaProjects.size());
    }
    
    @Test
    public void testCloseProject() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        
        manager.closeProject("proj-1");
        
        Project project = manager.getProject("proj-1");
        assertTrue(project.isClosed());
    }
    
    @Test
    public void testArchiveProject() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        
        manager.archiveProject("proj-1");
        
        Project project = manager.getProject("proj-1");
        assertTrue(project.isArchived());
    }
    
    @Test
    public void testDeleteProject() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        assertEquals(1, manager.getTotalProjectCount());
        
        manager.deleteProject("proj-1");
        assertEquals(0, manager.getTotalProjectCount());
        
        assertNull(manager.getProject("proj-1"));
    }
    
    @Test
    public void testMaxProjectsPerUser() {
        manager.setMaxProjectsPerUser(2);
        
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        manager.createProject("proj-2", "Project 2", Project.ProjectType.JAVA_PROJECT, "user-1");
        
        assertThrows(IllegalStateException.class, () -> {
            manager.createProject("proj-3", "Project 3", Project.ProjectType.JAVA_PROJECT, "user-1");
        });
    }
    
    @Test
    public void testGetAllProjects() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        manager.createProject("proj-2", "Project 2", Project.ProjectType.PYTHON_PROJECT, "user-1");
        manager.createProject("proj-3", "Project 3", Project.ProjectType.WEB_PROJECT, "user-2");
        
        assertEquals(3, manager.getAllProjects().size());
    }
    
    @Test
    public void testGetProjectStats() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        
        ProjectFile file = new ProjectFile("f-1", "proj-1", "App.java", ProjectFile.FileType.JAVA);
        manager.addFileToProject("proj-1", file, "crdt-1");
        
        var stats = manager.getProjectStats("proj-1");
        assertEquals("proj-1", stats.get("projectId"));
        assertEquals(1, stats.get("totalFiles"));
    }
    
    @Test
    public void testSearchFiles() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        
        ProjectFile file1 = new ProjectFile("f1", "proj-1", "App.java", ProjectFile.FileType.JAVA);
        ProjectFile file2 = new ProjectFile("f2", "proj-1", "Application.java", ProjectFile.FileType.JAVA);
        ProjectFile file3 = new ProjectFile("f3", "proj-1", "Test.java", ProjectFile.FileType.JAVA);
        
        manager.addFileToProject("proj-1", file1, "crdt-1");
        manager.addFileToProject("proj-1", file2, "crdt-2");
        manager.addFileToProject("proj-1", file3, "crdt-3");
        
        var results = manager.searchFiles("App");
        assertEquals(2, results.size());
    }
    
    @Test
    public void testTotalFileCount() {
        manager.createProject("proj-1", "Project 1", Project.ProjectType.JAVA_PROJECT, "user-1");
        manager.createProject("proj-2", "Project 2", Project.ProjectType.PYTHON_PROJECT, "user-2");
        
        ProjectFile file1 = new ProjectFile("f1", "proj-1", "App.java", ProjectFile.FileType.JAVA);
        ProjectFile file2 = new ProjectFile("f2", "proj-2", "script.py", ProjectFile.FileType.PYTHON);
        
        manager.addFileToProject("proj-1", file1, "crdt-1");
        manager.addFileToProject("proj-2", file2, "crdt-2");
        
        assertEquals(2, manager.getTotalFileCount());
    }
}
