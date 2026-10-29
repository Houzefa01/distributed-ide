package com.distributed_ide.javafx_client.service;

import com.distributed_ide.javafx_client.model.ProjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileServiceTest {

    @TempDir
    Path tempDir;

    private final FileService fileService = new FileService();

    @Test
    void readFile_and_writeFile_roundtrip() throws Exception {
        Path file = tempDir.resolve("hello.txt");
        fileService.writeFile(file, "hello world");
        assertEquals("hello world", fileService.readFile(file));
    }

    @Test
    void readFile_notFile_throws() {
        assertThrows(Exception.class, () -> fileService.readFile(tempDir));
        assertThrows(Exception.class, () -> fileService.readFile(tempDir.resolve("missing")));
    }

    @Test
    void listChildren_returnsSorted_dirsFirstThenFiles() throws Exception {
        Path sub = tempDir.resolve("subdir");
        Files.createDirectories(sub);
        Files.writeString(tempDir.resolve("a.txt"), "");
        Files.writeString(tempDir.resolve("b.txt"), "");
        List<Path> children = fileService.listChildren(tempDir);
        assertEquals(3, children.size());
        assertTrue(Files.isDirectory(children.get(0)));
        assertTrue(children.get(1).getFileName().toString().compareToIgnoreCase(children.get(2).getFileName().toString()) <= 0);
    }

    @Test
    void loadTree_buildsTree() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        ProjectNode root = fileService.loadTree(tempDir, 2);
        assertNotNull(root);
        assertTrue(root.isDirectory());
        assertFalse(root.getChildren().isEmpty());
    }
}
