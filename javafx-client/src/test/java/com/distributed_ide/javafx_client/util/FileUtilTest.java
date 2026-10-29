package com.distributed_ide.javafx_client.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void fileName_withNormalPath_returnsLastName() {
        assertEquals("foo.txt", FileUtil.fileName(Paths.get("/a/b/foo.txt")));
        assertEquals("bar", FileUtil.fileName(Paths.get("bar")));
    }

    @Test
    void fileName_null_returnsEmpty() {
        assertEquals("", FileUtil.fileName(null));
    }

    @Test
    void pathComparator_directoriesBeforeFiles() throws Exception {
        Comparator<Path> cmp = FileUtil.pathComparator();
        Path dir = tempDir.resolve("dir");
        Path file = tempDir.resolve("file.txt");
        Files.createDirectories(dir);
        Files.writeString(file, "x");
        assertTrue(cmp.compare(dir, file) < 0);
        assertTrue(cmp.compare(file, dir) > 0);
    }

    @Test
    void pathComparator_sameType_sortsByNameIgnoreCase() {
        Comparator<Path> cmp = FileUtil.pathComparator();
        assertTrue(cmp.compare(Paths.get("a"), Paths.get("B")) < 0);
        assertTrue(cmp.compare(Paths.get("B"), Paths.get("a")) > 0);
    }
}
