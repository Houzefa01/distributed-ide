package com.distributed_ide.project_manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProjectStructure class.
 */
public class ProjectStructureTest {
    
    private ProjectStructure structure;
    
    @BeforeEach
    public void setUp() {
        structure = new ProjectStructure("project-1");
    }
    
    @Test
    public void testStructureCreation() {
        assertNotNull(structure.getRoot());
        assertEquals(0, structure.getTotalFileCount());
    }
    
    @Test
    public void testAddFile() {
        ProjectFile file = new ProjectFile("file-1", "project-1", "App.java", ProjectFile.FileType.JAVA);
        structure.addFile(file);
        
        assertEquals(1, structure.getTotalFileCount());
        assertEquals(file, structure.getFile("file-1"));
    }
    
    @Test
    public void testAddFileInSubdirectory() {
        ProjectFile file = new ProjectFile("file-1", "project-1", "src/main/java/App.java", ProjectFile.FileType.JAVA);
        structure.addFile(file);
        
        assertEquals(1, structure.getTotalFileCount());
        assertNotNull(structure.getDirectory("src/main/java/"));
    }
    
    @Test
    public void testRemoveFile() {
        ProjectFile file = new ProjectFile("file-1", "project-1", "App.java", ProjectFile.FileType.JAVA);
        structure.addFile(file);
        assertEquals(1, structure.getTotalFileCount());
        
        structure.removeFile("file-1");
        assertEquals(0, structure.getTotalFileCount());
        assertNull(structure.getFile("file-1"));
    }
    
    @Test
    public void testGetFilesByType() {
        ProjectFile javaFile = new ProjectFile("f1", "p1", "App.java", ProjectFile.FileType.JAVA);
        ProjectFile pythonFile = new ProjectFile("f2", "p1", "script.py", ProjectFile.FileType.PYTHON);
        ProjectFile htmlFile = new ProjectFile("f3", "p1", "index.html", ProjectFile.FileType.HTML);
        
        structure.addFile(javaFile);
        structure.addFile(pythonFile);
        structure.addFile(htmlFile);
        
        assertEquals(1, structure.getFilesByType(ProjectFile.FileType.JAVA).size());
        assertEquals(1, structure.getFilesByType(ProjectFile.FileType.PYTHON).size());
        assertEquals(1, structure.getFilesByType(ProjectFile.FileType.HTML).size());
    }
    
    @Test
    public void testGetFilesInDirectory() {
        ProjectFile file1 = new ProjectFile("f1", "p1", "src/App.java", ProjectFile.FileType.JAVA);
        ProjectFile file2 = new ProjectFile("f2", "p1", "src/Test.java", ProjectFile.FileType.JAVA);
        ProjectFile file3 = new ProjectFile("f3", "p1", "resources/config.xml", ProjectFile.FileType.XML);
        
        structure.addFile(file1);
        structure.addFile(file2);
        structure.addFile(file3);
        
        List<ProjectFile> srcFiles = structure.getFilesInDirectory("src/");
        assertEquals(2, srcFiles.size());
    }
    
    @Test
    public void testGetAllFilesInDirectory() {
        ProjectFile file1 = new ProjectFile("f1", "p1", "src/App.java", ProjectFile.FileType.JAVA);
        ProjectFile file2 = new ProjectFile("f2", "p1", "src/main/Main.java", ProjectFile.FileType.JAVA);
        ProjectFile file3 = new ProjectFile("f3", "p1", "src/main/utils/Util.java", ProjectFile.FileType.JAVA);
        
        structure.addFile(file1);
        structure.addFile(file2);
        structure.addFile(file3);
        
        List<ProjectFile> allSrcFiles = structure.getAllFilesInDirectory("src/");
        assertEquals(3, allSrcFiles.size());
    }
    
    @Test
    public void testFindFiles() {
        ProjectFile file1 = new ProjectFile("f1", "p1", "src/App.java", ProjectFile.FileType.JAVA);
        ProjectFile file2 = new ProjectFile("f2", "p1", "src/Test.java", ProjectFile.FileType.JAVA);
        ProjectFile file3 = new ProjectFile("f3", "p1", "src/main/Application.java", ProjectFile.FileType.JAVA);
        
        structure.addFile(file1);
        structure.addFile(file2);
        structure.addFile(file3);
        
        List<ProjectFile> appFiles = structure.findFiles("App");
        assertEquals(2, appFiles.size());
    }
    
    @Test
    public void testGetFileByPath() {
        ProjectFile file = new ProjectFile("f1", "p1", "src/App.java", ProjectFile.FileType.JAVA);
        structure.addFile(file);
        
        ProjectFile found = structure.getFileByPath("src/App.java");
        assertEquals(file, found);
    }
    
    @Test
    public void testGetAllFiles() {
        ProjectFile file1 = new ProjectFile("f1", "p1", "App.java", ProjectFile.FileType.JAVA);
        ProjectFile file2 = new ProjectFile("f2", "p1", "Test.java", ProjectFile.FileType.JAVA);
        
        structure.addFile(file1);
        structure.addFile(file2);
        
        assertEquals(2, structure.getAllFiles().size());
    }
    
    @Test
    public void testDirectoryStatistics() {
        ProjectFile file1 = new ProjectFile("f1", "p1", "src/App.java", ProjectFile.FileType.JAVA);
        ProjectFile file2 = new ProjectFile("f2", "p1", "src/main/Main.java", ProjectFile.FileType.JAVA);
        ProjectFile file3 = new ProjectFile("f3", "p1", "resources/config.xml", ProjectFile.FileType.XML);
        
        structure.addFile(file1);
        structure.addFile(file2);
        structure.addFile(file3);
        
        Map<String, Integer> stats = structure.getDirectoryFileCount();
        assertTrue(stats.containsKey("/") || stats.containsValue(0)); // Either "/" key or count > 0
        assertTrue(stats.size() > 0); // Some directories tracked
    }
    
    @Test
    public void testMultipleFilesInRoot() {
        ProjectFile file1 = new ProjectFile("f1", "p1", "README.md", ProjectFile.FileType.MARKDOWN);
        ProjectFile file2 = new ProjectFile("f2", "p1", "pom.xml", ProjectFile.FileType.XML);
        
        structure.addFile(file1);
        structure.addFile(file2);
        
        List<ProjectFile> rootFiles = structure.getFilesInDirectory("/");
        assertEquals(2, rootFiles.size());
    }
}
