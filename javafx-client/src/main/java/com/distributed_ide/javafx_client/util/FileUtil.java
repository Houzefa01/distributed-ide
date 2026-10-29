package com.distributed_ide.javafx_client.util;

import java.nio.file.Path;
import java.util.Comparator;

/** Utilitaires fichiers. */
public final class FileUtil {

    private FileUtil() {}

    public static String fileName(Path path) {
        if (path == null) return "";
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }

    public static Comparator<Path> pathComparator() {
        return (a, b) -> {
            boolean ad = a.toFile().isDirectory();
            boolean bd = b.toFile().isDirectory();
            if (ad != bd) return ad ? -1 : 1;
            return fileName(a).compareToIgnoreCase(fileName(b));
        };
    }
}
