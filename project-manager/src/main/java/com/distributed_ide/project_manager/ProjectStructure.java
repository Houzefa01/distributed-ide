package com.distributed_ide.project_manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the file structure (directory tree) of a project.
 * Provides efficient lookup, traversal, and path resolution.
 */
public class ProjectStructure {
    
    public static class DirectoryNode {
        private final String name;
        private final String path;
        private final List<DirectoryNode> subDirectories;
        private final List<ProjectFile> files;
        
        public DirectoryNode(String name, String path) {
            this.name = name;
            this.path = path;
            this.subDirectories = new ArrayList<>();
            this.files = new ArrayList<>();
        }
        
        public String getName() { return name; }
        public String getPath() { return path; }
        public List<DirectoryNode> getSubDirectories() { return new ArrayList<>(subDirectories); }
        public List<ProjectFile> getFiles() { return new ArrayList<>(files); }
        
        public void addSubDirectory(DirectoryNode dir) {
            subDirectories.add(dir);
        }
        
        public void addFile(ProjectFile file) {
            files.add(file);
        }
        
        public int getFileCount() {
            int count = files.size();
            for (DirectoryNode dir : subDirectories) {
                count += dir.getFileCount();
            }
            return count;
        }
        
        public void removeFile(ProjectFile file) {
            files.remove(file);
        }
    }
    
    private final String projectId;
    private final DirectoryNode root;
    private final Map<String, DirectoryNode> directoryCache;
    private final Map<String, ProjectFile> fileIndex;
    
    public ProjectStructure(String projectId) {
        this.projectId = projectId;
        this.root = new DirectoryNode("root", "/");
        this.directoryCache = new ConcurrentHashMap<>();
        this.directoryCache.put("/", root);
        this.fileIndex = new ConcurrentHashMap<>();
    }
    
    /**
     * Adds a file to the structure, creating intermediate directories if needed.
     */
    public void addFile(ProjectFile file) {
        String directory = file.getDirectory();
        if (directory.isEmpty()) {
            directory = "/";
        } else if (!directory.endsWith("/")) {
            directory = directory + "/";
        }
        
        DirectoryNode dirNode = ensureDirectoryExists(directory);
        dirNode.addFile(file);
        fileIndex.put(file.getFileId(), file);
    }
    
    /**
     * Removes a file from the structure.
     */
    public void removeFile(String fileId) {
        ProjectFile file = fileIndex.remove(fileId);
        if (file != null) {
            String directory = file.getDirectory();
            if (directory.isEmpty()) {
                directory = "/";
            } else if (!directory.endsWith("/")) {
                directory = directory + "/";
            }
            DirectoryNode dirNode = directoryCache.get(directory);
            if (dirNode != null) {
                dirNode.removeFile(file);
            }
        }
    }
    
    /**
     * Ensures a directory exists in the structure, creating intermediate paths.
     */
    private DirectoryNode ensureDirectoryExists(String path) {
        if (directoryCache.containsKey(path)) {
            return directoryCache.get(path);
        }
        
        // Normalize path to end with /
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        
        // Split path and create intermediate directories
        String[] parts = path.split("/");
        DirectoryNode current = root;
        StringBuilder currentPath = new StringBuilder("/");
        
        for (String part : parts) {
            if (part.isEmpty()) continue;
            
            currentPath.append(part).append("/");
            String fullPath = currentPath.toString();
            
            DirectoryNode next = directoryCache.get(fullPath);
            if (next == null) {
                next = new DirectoryNode(part, fullPath);
                directoryCache.put(fullPath, next);
                current.addSubDirectory(next);
            }
            current = next;
        }
        
        return current;
    }
    
    /**
     * Gets the directory structure as a tree.
     */
    public DirectoryNode getRoot() {
        return root;
    }
    
    /**
     * Gets a directory by path.
     */
    public DirectoryNode getDirectory(String path) {
        // Normalize path
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.endsWith("/") && !path.isEmpty()) {
            path = path + "/";
        }
        return directoryCache.get(path);
    }
    
    /**
     * Gets a file by ID.
     */
    public ProjectFile getFile(String fileId) {
        return fileIndex.get(fileId);
    }
    
    /**
     * Gets all files with a specific extension.
     */
    public List<ProjectFile> getFilesByType(ProjectFile.FileType fileType) {
        return fileIndex.values().stream()
                .filter(f -> f.getFileType() == fileType)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all files in a specific directory (non-recursive).
     */
    public List<ProjectFile> getFilesInDirectory(String directory) {
        // Normalize directory path
        if (!directory.startsWith("/")) {
            directory = "/" + directory;
        }
        if (!directory.endsWith("/") && !directory.isEmpty()) {
            directory = directory + "/";
        }
        DirectoryNode dirNode = directoryCache.get(directory);
        return dirNode != null ? dirNode.getFiles() : new ArrayList<>();
    }
    
    /**
     * Gets all files in a directory recursively.
     */
    public List<ProjectFile> getAllFilesInDirectory(String directory) {
        // Normalize directory path
        if (!directory.startsWith("/")) {
            directory = "/" + directory;
        }
        if (!directory.endsWith("/") && !directory.isEmpty()) {
            directory = directory + "/";
        }
        DirectoryNode dirNode = directoryCache.get(directory);
        if (dirNode == null) {
            return new ArrayList<>();
        }
        
        List<ProjectFile> result = new ArrayList<>(dirNode.getFiles());
        for (DirectoryNode subDir : dirNode.getSubDirectories()) {
            result.addAll(getAllFilesInDirectory(subDir.getPath()));
        }
        return result;
    }
    
    /**
     * Gets all files in the project.
     */
    public Collection<ProjectFile> getAllFiles() {
        return new ArrayList<>(fileIndex.values());
    }
    
    /**
     * Gets the total file count in the project.
     */
    public int getTotalFileCount() {
        return fileIndex.size();
    }
    
    /**
     * Gets directory statistics.
     */
    public Map<String, Integer> getDirectoryFileCount() {
        Map<String, Integer> stats = new HashMap<>();
        for (String dirPath : directoryCache.keySet()) {
            DirectoryNode dir = directoryCache.get(dirPath);
            stats.put(dirPath, dir.getFileCount());
        }
        // Also add root directory count
        stats.put("root", root.getFileCount());
        return stats;
    }
    
    /**
     * Finds files matching a pattern (by name).
     */
    public List<ProjectFile> findFiles(String namePattern) {
        return fileIndex.values().stream()
                .filter(f -> f.getFileName().contains(namePattern))
                .collect(Collectors.toList());
    }
    
    /**
     * Gets a file by relative path.
     */
    public ProjectFile getFileByPath(String relativePath) {
        return fileIndex.values().stream()
                .filter(f -> f.getRelativePath().equals(relativePath))
                .findFirst()
                .orElse(null);
    }
}
