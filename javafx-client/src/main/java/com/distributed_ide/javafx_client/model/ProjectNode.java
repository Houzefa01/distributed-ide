package com.distributed_ide.javafx_client.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Nœud de l'arborescence projet (fichier ou répertoire).
 */
public class ProjectNode {

    private final String name;
    private final String fullPath;
    private final boolean directory;
    private final ObservableList<ProjectNode> children = FXCollections.observableArrayList();

    public ProjectNode(String name, String fullPath, boolean directory) {
        this.name = Objects.requireNonNull(name);
        this.fullPath = fullPath != null ? fullPath : "";
        this.directory = directory;
    }

    public static ProjectNode directory(Path path) {
        String p = path != null ? path.toString() : "";
        String name = p.isEmpty() ? "/" : (path.getFileName() != null ? path.getFileName().toString() : p);
        return new ProjectNode(name, p, true);
    }

    public static ProjectNode file(Path path) {
        String p = path != null ? path.toString() : "";
        String name = path != null && path.getFileName() != null ? path.getFileName().toString() : p;
        return new ProjectNode(name, p, false);
    }

    public String getName() { return name; }
    public String getFullPath() { return fullPath; }
    public boolean isDirectory() { return directory; }
    public ObservableList<ProjectNode> getChildren() { return children; }
}
