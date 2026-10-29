package com.distributed_ide.project_manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProjectFile class.
 */
public class ProjectFileTest {
    
    private ProjectFile projectFile;
    
    @BeforeEach
    public void setUp() {
        projectFile = new ProjectFile("file-1", "project-1", "src/main/java/App.java", ProjectFile.FileType.JAVA);
    }
    
    @Test
    public void testProjectFileCreation() {
        assertEquals("file-1", projectFile.getFileId());
        assertEquals("project-1", projectFile.getProjectId());
        assertEquals("src/main/java/App.java", projectFile.getRelativePath());
        assertEquals(ProjectFile.FileType.JAVA, projectFile.getFileType());
        assertEquals(ProjectFile.FileStatus.CREATED, projectFile.getStatus());
        assertNotNull(projectFile.getCreatedAt());
    }
    
    @Test
    public void testCrdtDocumentAssociation() {
        assertNull(projectFile.getCrdtDocumentId());
        
        projectFile.setCrdtDocumentId("crdt-doc-1");
        assertEquals("crdt-doc-1", projectFile.getCrdtDocumentId());
    }
    
    @Test
    public void testFileTypeFromExtension() {
        assertEquals(ProjectFile.FileType.JAVA, ProjectFile.FileType.fromExtension(".java"));
        assertEquals(ProjectFile.FileType.PYTHON, ProjectFile.FileType.fromExtension(".py"));
        assertEquals(ProjectFile.FileType.HTML, ProjectFile.FileType.fromExtension(".html"));
        assertEquals(ProjectFile.FileType.CSS, ProjectFile.FileType.fromExtension(".css"));
        assertEquals(ProjectFile.FileType.JAVASCRIPT, ProjectFile.FileType.fromExtension(".js"));
        assertEquals(ProjectFile.FileType.OTHER, ProjectFile.FileType.fromExtension(".unknown"));
    }
    
    @Test
    public void testFilePathParsing() {
        assertEquals("App.java", projectFile.getFileName());
        assertEquals("src/main/java", projectFile.getDirectory());
    }
    
    @Test
    public void testRootLevelFile() {
        ProjectFile rootFile = new ProjectFile("file-2", "project-1", "README.md", ProjectFile.FileType.MARKDOWN);
        assertEquals("README.md", rootFile.getFileName());
        assertEquals("", rootFile.getDirectory());
    }
    
    @Test
    public void testFileMetadata() {
        projectFile.setMetadata("author", "john");
        projectFile.setMetadata("version", "1.0");
        
        assertEquals("john", projectFile.getMetadata("author"));
        assertEquals("1.0", projectFile.getMetadata("version"));
        
        assertEquals(2, projectFile.getAllMetadata().size());
    }
    
    @Test
    public void testStatusUpdate() {
        assertEquals(ProjectFile.FileStatus.CREATED, projectFile.getStatus());
        
        projectFile.setStatus(ProjectFile.FileStatus.MODIFIED);
        assertEquals(ProjectFile.FileStatus.MODIFIED, projectFile.getStatus());
    }
    
    @Test
    public void testFileModification() {
        assertNull(projectFile.getModifiedAt());
        assertNull(projectFile.getLastModifiedBy());
        
        projectFile.updateModification("user-1");
        
        assertNotNull(projectFile.getModifiedAt());
        assertEquals("user-1", projectFile.getLastModifiedBy());
        assertEquals(ProjectFile.FileStatus.MODIFIED, projectFile.getStatus());
    }
    
    @Test
    public void testMultipleFileTypes() {
        ProjectFile pythonFile = new ProjectFile("file-2", "project-1", "script.py", ProjectFile.FileType.PYTHON);
        assertEquals(ProjectFile.FileType.PYTHON, pythonFile.getFileType());
        
        ProjectFile htmlFile = new ProjectFile("file-3", "project-1", "index.html", ProjectFile.FileType.HTML);
        assertEquals(ProjectFile.FileType.HTML, htmlFile.getFileType());
    }
}
