package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.javafx_client.model.ProjectNode;
import com.distributed_ide.javafx_client.service.FileService;
import com.distributed_ide.javafx_client.util.IconManager;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Project tree view (TreeView).
 * Displays the file tree with icons and full context menu.
 */
public class ProjectTreeView {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectTreeView.class);

    // Clipboard for cut/copy operations
    private static class ClipboardData {
        enum Operation { CUT, COPY }
        Path path;
        Operation operation;

        ClipboardData(Path path, Operation operation) {
            this.path = path;
            this.operation = operation;
        }
    }

    private static volatile ClipboardData clipboardData = null;

    private final TreeView<ProjectNode> treeView;
    private final FileService fileService;
    private Consumer<Path> onFileOpen;
    private volatile Path rootPath;

    // State preservation for tree expansion
    private final Set<String> expandedPaths = new HashSet<>();  // Paths of expanded tree nodes
    private final HashMap<String, TreeItem<ProjectNode>> nodeCache = new HashMap<>();  // Cache of TreeItem by path

    // Background executor for async tree IO (avoids blocking FX thread)
    private static final ExecutorService TREE_IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tree-io");
        t.setDaemon(true);
        return t;
    });

    // Background executor for system file operations (reveal, etc)
    private static final ExecutorService SYSTEM_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "system-executor");
        t.setDaemon(true);
        return t;
    });

    // Callbacks for operations
    private Runnable onRefreshRequest;
    private Consumer<Path> onFileDeleted;
    private Consumer<Path> onFileCreated;

    public ProjectTreeView(FileService fileService) {
        this.fileService = fileService;
        treeView = new TreeView<>();
        treeView.setShowRoot(true);

        // Configure with icons
        treeView.setCellFactory(tv -> new ProjectTreeCell());

        // Double-click to open
        treeView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                TreeItem<ProjectNode> item = treeView.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue() != null && !item.getValue().isDirectory() && onFileOpen != null) {
                    Path path = Paths.get(item.getValue().getFullPath());
                    onFileOpen.accept(path);
                }
            }
        });
    }

    public TreeView<ProjectNode> getView() {
        return treeView;
    }

    public void setOnFileOpen(Consumer<Path> handler) {
        this.onFileOpen = handler;
    }

    public void setOnRefreshRequest(Runnable handler) {
        this.onRefreshRequest = handler;
    }

    public void setOnFileDeleted(Consumer<Path> handler) {
        this.onFileDeleted = handler;
    }

    public void setOnFileCreated(Consumer<Path> handler) {
        this.onFileCreated = handler;
    }

    /**
     * Loads and displays the tree from a root directory.
     */
    public void loadRoot(Path rootPath) {
        if (rootPath == null || !rootPath.toFile().isDirectory()) {
            this.rootPath = null;
            treeView.setRoot(null);
            nodeCache.clear();
            expandedPaths.clear();
            return;
        }
        this.rootPath = rootPath;
        nodeCache.clear();
        expandedPaths.clear();
        
        ProjectNode root = fileService.loadTree(rootPath, 1);
        TreeItem<ProjectNode> rootItem = toLazyTreeItem(root);
        rootItem.setExpanded(true);
        expandedPaths.add(root.getFullPath());
        treeView.setRoot(rootItem);
    }

    public Path getRootPath() {
        return rootPath;
    }

    /**
     * Save expanded state of all nodes in the tree.
     * Called before refresh to preserve user's expanded folders.
     */
    private void saveExpandedState(TreeItem<ProjectNode> item) {
        if (item == null) return;
        
        if (item.getValue() != null && item.isExpanded()) {
            expandedPaths.add(item.getValue().getFullPath());
            nodeCache.put(item.getValue().getFullPath(), item);
        }
        
        for (TreeItem<ProjectNode> child : item.getChildren()) {
            saveExpandedState(child);
        }
    }

    /**
     * Restore expanded state of nodes after refresh.
     * Expands nodes that were previously expanded.
     */
    private void restoreExpandedState(TreeItem<ProjectNode> item) {
        if (item == null) return;
        
        if (item.getValue() != null && expandedPaths.contains(item.getValue().getFullPath())) {
            item.setExpanded(true);
        }
        
        for (TreeItem<ProjectNode> child : item.getChildren()) {
            restoreExpandedState(child);
        }
    }

    /**
     * Smart refresh: preserves expanded state and selection.
     * Much better UX than full tree rebuild.
     */
    public void refresh() {
        if (rootPath == null) return;
        
        TreeItem<ProjectNode> root = treeView.getRoot();
        if (root == null) {
            loadRoot(rootPath);
            return;
        }
        
        // Save expanded paths before refresh
        saveExpandedState(root);
        
        // Save current selection
        TreeItem<ProjectNode> selectedItem = treeView.getSelectionModel().getSelectedItem();
        String selectedPath = selectedItem != null && selectedItem.getValue() != null ? 
            selectedItem.getValue().getFullPath() : null;
        
        // Perform smart refresh on root children
        ProjectNode rootNode = root.getValue();
        if (rootNode != null) {
            Path rootPath = Paths.get(rootNode.getFullPath());
            CompletableFuture.supplyAsync(() -> fileService.loadTree(rootPath, 1), TREE_IO_EXECUTOR)
                    .thenAcceptAsync(loadedRoot -> {
                        refreshNodeChildren(root, loadedRoot);
                        restoreExpandedState(root);
                        
                        // Restore selection if possible
                        if (selectedPath != null) {
                            restoreSelection(root, selectedPath);
                        }
                    }, Platform::runLater)
                    .exceptionally(ex -> {
                        LOG.warn("Smart refresh failed", ex);
                        // Fallback to full reload on error
                        Platform.runLater(() -> loadRoot(rootPath));
                        return null;
                    });
        }
    }

    /**
     * Refresh a specific node directory (smart update for a single folder).
     * Called when files are created/deleted/moved in a specific directory.
     */
    public void refreshPath(Path changedPath) {
        if (rootPath == null || treeView.getRoot() == null) return;
        
        TreeItem<ProjectNode> root = treeView.getRoot();
        TreeItem<ProjectNode> targetItem = findTreeItemByPath(root, changedPath);
        
        if (targetItem != null) {
            ProjectNode targetNode = targetItem.getValue();
            if (targetNode != null && targetNode.isDirectory()) {
                // If the changed path is a directory, refresh its children
                boolean wasExpanded = targetItem.isExpanded();
                refreshNodeChildren(targetItem, null);
                if (wasExpanded) {
                    targetItem.setExpanded(true);
                }
            } else if (targetItem.getParent() != null) {
                // If it's a file, refresh the parent directory
                TreeItem<ProjectNode> parentItem = targetItem.getParent();
                ProjectNode parentNode = parentItem.getValue();
                if (parentNode != null && parentNode.isDirectory()) {
                    boolean wasExpanded = parentItem.isExpanded();
                    refreshNodeChildren(parentItem, null);
                    if (wasExpanded) {
                        parentItem.setExpanded(true);
                    }
                }
            }
        } else {
            // If not found, refresh the entire tree (full reload)
            refresh();
        }
    }

    /**
     * Find a TreeItem by its file path.
     * Used to locate nodes for targeted refresh.
     */
    private TreeItem<ProjectNode> findTreeItemByPath(TreeItem<ProjectNode> item, Path targetPath) {
        if (item == null || item.getValue() == null) return null;
        
        String itemPath = item.getValue().getFullPath();
        String targetPathStr = targetPath.toString();
        
        if (itemPath.equals(targetPathStr)) {
            return item;
        }
        
        // Search in children
        for (TreeItem<ProjectNode> child : item.getChildren()) {
            TreeItem<ProjectNode> found = findTreeItemByPath(child, targetPath);
            if (found != null) return found;
        }
        
        return null;
    }

    /**
     * Refresh children of a specific tree node.
     * Called for smart/surgical updates.
     */
    private void refreshNodeChildren(TreeItem<ProjectNode> parentItem, ProjectNode loadedParent) {
        ProjectNode parentNode = parentItem.getValue();
        if (parentNode == null || !parentNode.isDirectory()) return;
        
        // If not provided, load from filesystem
        if (loadedParent == null) {
            try {
                Path parentPath = Paths.get(parentNode.getFullPath());
                loadedParent = fileService.loadTree(parentPath, 1);
            } catch (Exception e) {
                LOG.warn("Failed to refresh node children", e);
                return;
            }
        }
        
        // Get current children
        Set<String> newChildNames = new HashSet<>();
        for (ProjectNode child : loadedParent.getChildren()) {
            newChildNames.add(child.getFullPath());
        }
        
        // Remove old children that no longer exist
        parentItem.getChildren().removeIf(treeItem -> {
            ProjectNode node = treeItem.getValue();
            return node != null && !newChildNames.contains(node.getFullPath());
        });
        
        // Add or update children
        Set<String> existingNames = new HashSet<>();
        for (TreeItem<ProjectNode> treeItem : parentItem.getChildren()) {
            if (treeItem.getValue() != null) {
                existingNames.add(treeItem.getValue().getFullPath());
            }
        }
        
        for (ProjectNode newChild : loadedParent.getChildren()) {
            if (!existingNames.contains(newChild.getFullPath())) {
                parentItem.getChildren().add(toLazyTreeItem(newChild));
            }
        }
    }

    /**
     * Try to restore selection to a specific path.
     * Useful after refresh to keep user's selection.
     */
    private void restoreSelection(TreeItem<ProjectNode> item, String targetPath) {
        if (item == null || item.getValue() == null) return;
        
        if (item.getValue().getFullPath().equals(targetPath)) {
            treeView.getSelectionModel().select(item);
            return;
        }
        
        for (TreeItem<ProjectNode> child : item.getChildren()) {
            restoreSelection(child, targetPath);
        }
    }

    private static final ProjectNode PLACEHOLDER = new ProjectNode("Loading...", "", false);

    private TreeItem<ProjectNode> toLazyTreeItem(ProjectNode node) {
        TreeItem<ProjectNode> item = new TreeItem<>(node);
        if (node.isDirectory()) {
            if (node.getChildren().isEmpty()) {
                item.getChildren().add(new TreeItem<>(PLACEHOLDER));
            } else {
                for (ProjectNode child : node.getChildren()) {
                    item.getChildren().add(toLazyTreeItem(child));
                }
            }
            item.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
                if (isExpanded && item.getChildren().size() == 1
                        && item.getChildren().get(0).getValue() == PLACEHOLDER) {
                    loadChildrenLazily(item);
                }
            });
        }
        return item;
    }

    private void loadChildrenLazily(TreeItem<ProjectNode> parentItem) {
        ProjectNode parentNode = parentItem.getValue();
        if (parentNode == null) return;
        Path parentPath = Paths.get(parentNode.getFullPath());
        // Load children off FX thread to avoid UI freeze on large directories
        CompletableFuture.supplyAsync(() -> fileService.loadTree(parentPath, 1), TREE_IO_EXECUTOR)
                .thenAcceptAsync(loaded -> {
                    parentItem.getChildren().clear();
                    for (ProjectNode child : loaded.getChildren()) {
                        parentItem.getChildren().add(toLazyTreeItem(child));
                    }
                }, Platform::runLater)
                .exceptionally(ex -> {
                    LOG.warn("Lazy load failed for {}", parentPath, ex);
                    Platform.runLater(() -> parentItem.getChildren().clear());
                    return null;
                });
    }

    // ═══════════════════════════════════════════════════════════
    //  CELL WITH ICON AND CONTEXT MENU
    // ═══════════════════════════════════════════════════════════

    private class ProjectTreeCell extends TreeCell<ProjectNode> {

        private final ContextMenu contextMenu;

        // Menu items
        private final MenuItem openItem;
        private final MenuItem renameItem;
        private final MenuItem deleteItem;
        private final SeparatorMenuItem sep1;
        private final MenuItem newFileItem;
        private final MenuItem newFolderItem;
        private final SeparatorMenuItem sep2;
        private final MenuItem cutItem;
        private final MenuItem copyItem;
        private final MenuItem pasteItem;
        private final SeparatorMenuItem sep3;
        private final MenuItem copyPathItem;
        private final MenuItem revealItem;

        public ProjectTreeCell() {
            // Create context menu
            openItem = new MenuItem("Ouvrir");
            renameItem = new MenuItem("Renommer");
            deleteItem = new MenuItem("Supprimer");
            sep1 = new SeparatorMenuItem();
            newFileItem = new MenuItem("Nouveau fichier");
            newFolderItem = new MenuItem("Nouveau dossier");
            sep2 = new SeparatorMenuItem();
            cutItem = new MenuItem("Couper");
            copyItem = new MenuItem("Copier");
            pasteItem = new MenuItem("Coller");
            sep3 = new SeparatorMenuItem();
            copyPathItem = new MenuItem("Copier le chemin");
            revealItem = new MenuItem("Afficher dans le système");

            contextMenu = new ContextMenu(
                    openItem, renameItem, deleteItem, sep1,
                    newFileItem, newFolderItem, sep2,
                    cutItem, copyItem, pasteItem, sep3,
                    copyPathItem, revealItem
            );

            // Actions
            openItem.setOnAction(e -> handleOpen());
            renameItem.setOnAction(e -> handleRename());
            deleteItem.setOnAction(e -> handleDelete());
            newFileItem.setOnAction(e -> handleNewFile());
            newFolderItem.setOnAction(e -> handleNewFolder());
            cutItem.setOnAction(e -> handleCut());
            copyItem.setOnAction(e -> handleCopy());
            pasteItem.setOnAction(e -> handlePaste());
            copyPathItem.setOnAction(e -> handleCopyPath());
            revealItem.setOnAction(e -> handleRevealInSystem());

            // Show menu on right-click
            setOnContextMenuRequested(e -> {
                ProjectNode node = getItem();
                if (node != null) {
                    updateContextMenu(node);
                    contextMenu.show(this, e.getScreenX(), e.getScreenY());
                }
            });

            // ── Drag & Drop ──────────────────────────────────────
            setOnDragDetected(event -> {
                ProjectNode node = getItem();
                if (node == null || isEmpty()) return;
                Dragboard db = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent cc = new ClipboardContent();
                cc.putString(node.getFullPath());
                db.setContent(cc);
                event.consume();
            });

            setOnDragOver(event -> {
                if (event.getGestureSource() != this && event.getDragboard().hasString()) {
                    ProjectNode target = getItem();
                    if (target != null && target.isDirectory()) {
                        event.acceptTransferModes(TransferMode.MOVE);
                    }
                }
                event.consume();
            });

            setOnDragEntered(event -> {
                if (event.getGestureSource() != this && event.getDragboard().hasString()) {
                    ProjectNode target = getItem();
                    if (target != null && target.isDirectory()) {
                        getStyleClass().add("drag-over");
                    }
                }
                event.consume();
            });

            setOnDragExited(event -> {
                getStyleClass().remove("drag-over");
                event.consume();
            });

            setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    ProjectNode target = getItem();
                    if (target != null && target.isDirectory()) {
                        Path sourcePath = Paths.get(db.getString());
                        Path targetDir = Paths.get(target.getFullPath());
                        Path dest = targetDir.resolve(sourcePath.getFileName());
                        try {
                            Files.move(sourcePath, dest, StandardCopyOption.ATOMIC_MOVE);
                            LOG.info("Moved {} to {}", sourcePath, dest);
                            success = true;
                            Platform.runLater(() -> refresh());
                        } catch (IOException ex) {
                            LOG.error("Drag move failed", ex);
                            showError("Move failed", ex.getMessage());
                        }
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });
        }

        @Override
        protected void updateItem(ProjectNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.getName());
                // Icon by type
                setGraphic(IconManager.getFileIcon(item.getName(), item.isDirectory()));
            }
        }

        private void updateContextMenu(ProjectNode node) {
            boolean isDir = node.isDirectory();
            openItem.setVisible(!isDir);
            newFileItem.setVisible(isDir);
            newFolderItem.setVisible(isDir);
            // Enable paste only if in a directory and clipboard has data
            pasteItem.setDisable(isDir == false || clipboardData == null);
        }

        // ═══════════════════════════════════════════════════════════
        //  HANDLERS
        // ═══════════════════════════════════════════════════════════

        private void handleOpen() {
            ProjectNode node = getItem();
            if (node != null && !node.isDirectory() && onFileOpen != null) {
                onFileOpen.accept(Paths.get(node.getFullPath()));
            }
        }

        private void handleRename() {
            ProjectNode node = getItem();
            if (node == null) return;

            TextInputDialog dialog = new TextInputDialog(node.getName());
            dialog.setTitle("Renommer");
            dialog.setHeaderText("Entrez le nouveau nom :");
            dialog.setContentText("Nom :");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(newName -> {
                if (newName.trim().isEmpty()) {
                    showError("Echec du renommage", "Le nom ne peut pas être vide");
                    return;
                }
                if (newName.equals(node.getName())) {
                    return; // No change
                }
                // Check for invalid characters (cross-platform)
                if (newName.matches(".*[/\\\\:*?\"<>|].*")) {
                    showError("Echec du renommage", "Le nom contient des caractères invalides (/\\:*?\"<>|)");
                    return;
                }
                try {
                    Path oldPath = Paths.get(node.getFullPath());
                    Path newPath = oldPath.getParent().resolve(newName);
                    if (Files.exists(newPath)) {
                        showError("Echec du renommage", "Un fichier avec ce nom existe déjà");
                        return;
                    }
                    Files.move(oldPath, newPath);
                    LOG.info("Renamed {} to {}", oldPath, newPath);
                    // Smart refresh: only update parent directory
                    refreshPath(oldPath.getParent());
                } catch (IOException e) {
                    LOG.error("Rename failed", e);
                    String msg = e.getMessage();
                    if (msg == null || msg.isEmpty()) {
                        msg = "Impossible de renommer le fichier. Le fichier peut être verrouillé ou protégé.";
                    }
                    showError("Echec du renommage", msg);
                }
            });
        }

        private void handleDelete() {
            ProjectNode node = getItem();
            if (node == null) return;

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmer la suppression");
            confirm.setHeaderText("Supprimer " + node.getName() + "?");
            confirm.setContentText("Cette action ne peut pas être annulée.");

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    Path path = Paths.get(node.getFullPath());
                    // Check if file/directory exists before deletion
                    if (!Files.exists(path)) {
                        showError("Echec de la suppression", "Le fichier n'existe pas (il a peut-être déjà été supprimé)");
                        refresh();
                        return;
                    }
                    if (node.isDirectory()) {
                        deleteDirectory(path);
                    } else {
                        Files.delete(path);
                    }
                    LOG.info("Deleted {}", path);
                    // Smart refresh: only update parent directory
                    refreshPath(path.getParent());
                    if (onFileDeleted != null) {
                        onFileDeleted.accept(path);
                    }
                } catch (IOException e) {
                    LOG.error("Delete failed", e);
                    String msg = e.getMessage();
                    if (msg == null || msg.isEmpty()) {
                        msg = "Impossible de supprimer le fichier. Le fichier peut être verrouillé, protégé ou en cours d'utilisation.";
                    }
                    showError("Echec de la suppression", msg);
                }
            }
        }

        private void handleNewFile() {
            ProjectNode node = getItem();
            if (node == null || !node.isDirectory()) return;

            TextInputDialog dialog = new TextInputDialog("newfile.txt");
            dialog.setTitle("New File");
            dialog.setHeaderText("Enter file name:");
            dialog.setContentText("Name:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(fileName -> {
                if (fileName.trim().isEmpty()) {
                    showError("Create failed", "Name cannot be empty");
                    return;
                }
                try {
                    Path parentPath = Paths.get(node.getFullPath());
                    Path newFile = parentPath.resolve(fileName);
                    if (Files.exists(newFile)) {
                        showError("Create failed", "File already exists");
                        return;
                    }
                    Files.createFile(newFile);
                    LOG.info("Created file {}", newFile);
                    // Smart refresh: only update parent directory
                    refreshPath(parentPath);
                    if (onFileCreated != null) {
                        onFileCreated.accept(newFile);
                    }
                } catch (IOException e) {
                    LOG.error("Create file failed", e);
                    showError("Create failed", e.getMessage());
                }
            });
        }

        private void handleNewFolder() {
            ProjectNode node = getItem();
            if (node == null || !node.isDirectory()) return;

            TextInputDialog dialog = new TextInputDialog("newfolder");
            dialog.setTitle("New Folder");
            dialog.setHeaderText("Enter folder name:");
            dialog.setContentText("Name:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(folderName -> {
                if (folderName.trim().isEmpty()) {
                    showError("Create failed", "Name cannot be empty");
                    return;
                }
                try {
                    Path parentPath = Paths.get(node.getFullPath());
                    Path newFolder = parentPath.resolve(folderName);
                    if (Files.exists(newFolder)) {
                        showError("Create failed", "Folder already exists");
                        return;
                    }
                    Files.createDirectory(newFolder);
                    LOG.info("Created folder {}", newFolder);
                    // Smart refresh: only update parent directory
                    refreshPath(parentPath);
                } catch (IOException e) {
                    LOG.error("Create folder failed", e);
                    showError("Create failed", e.getMessage());
                }
            });
        }

        private void handleCopyPath() {
            ProjectNode node = getItem();
            if (node == null) return;

            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(node.getFullPath());
            clipboard.setContent(content);
            LOG.debug("Copied path to clipboard: {}", node.getFullPath());
        }

        private void handleCut() {
            ProjectNode node = getItem();
            if (node == null) return;

            Path path = Paths.get(node.getFullPath());
            clipboardData = new ClipboardData(path, ClipboardData.Operation.CUT);
            LOG.info("Cut: {}", path);
            treeView.refresh();
        }

        private void handleCopy() {
            ProjectNode node = getItem();
            if (node == null) return;

            Path path = Paths.get(node.getFullPath());
            clipboardData = new ClipboardData(path, ClipboardData.Operation.COPY);
            LOG.info("Copy: {}", path);
            treeView.refresh();
        }

        private void handlePaste() {
            ProjectNode node = getItem();
            if (node == null || !node.isDirectory() || clipboardData == null) return;

            Path targetDir = Paths.get(node.getFullPath());
            Path sourcePath = clipboardData.path;
            boolean isCut = clipboardData.operation == ClipboardData.Operation.CUT;

            // Execute paste in background to avoid blocking UI
            TREE_IO_EXECUTOR.submit(() -> {
                try {
                    if (!Files.exists(sourcePath)) {
                        Platform.runLater(() -> showError("Paste failed", "Source no longer exists"));
                        return;
                    }

                    Path targetPath = targetDir.resolve(sourcePath.getFileName());

                    // Avoid overwriting
                    if (Files.exists(targetPath)) {
                        Platform.runLater(() -> showError("Paste failed", "Destination already exists"));
                        return;
                    }

                    // Copy or move
                    if (sourcePath.toFile().isDirectory()) {
                        copyDirectory(sourcePath, targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }

                    // If cut, delete the source
                    if (isCut) {
                        if (sourcePath.toFile().isDirectory()) {
                            deleteDirectory(sourcePath);
                        } else {
                            Files.deleteIfExists(sourcePath);
                        }
                        clipboardData = null; // Clear clipboard after cut
                    }

                    LOG.info("Paste completed: {} -> {}", sourcePath, targetPath);
                    Platform.runLater(() -> {
                        // Smart refresh: update target directory and source (if cut)
                        refreshPath(targetDir.toAbsolutePath());
                        if (isCut && sourcePath.getParent() != null) {
                            refreshPath(sourcePath.getParent());
                        }
                        if (onFileCreated != null) {
                            onFileCreated.accept(targetPath);
                        }
                    });
                } catch (IOException e) {
                    LOG.error("Paste failed", e);
                    Platform.runLater(() -> showError("Paste failed", e.getMessage()));
                }
            });
        }

        private void copyDirectory(Path src, Path dest) throws IOException {
            Files.createDirectory(dest);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(src)) {
                for (Path file : stream) {
                    Path targetFile = dest.resolve(file.getFileName());
                    if (Files.isDirectory(file)) {
                        copyDirectory(file, targetFile);
                    } else {
                        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }

        private void handleRevealInSystem() {
            ProjectNode node = getItem();
            if (node == null) return;

            // Execute desktop.open() on a background thread to avoid blocking the UI
            SYSTEM_EXECUTOR.submit(() -> {
                try {
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                        File file = new File(node.getFullPath());
                        if (file.exists()) {
                            if (file.isDirectory()) {
                                desktop.open(file);
                            } else {
                                desktop.open(file.getParentFile());
                            }
                            LOG.info("Revealed in system: {}", file);
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Reveal in system failed", e);
                    // Show error on FX thread
                    Platform.runLater(() -> showError("Reveal failed", e.getMessage()));
                }
            });
        }

        private void deleteDirectory(Path path) throws IOException {
            try (var stream = Files.walk(path)) {
                stream.sorted((a, b) -> -a.compareTo(b)) // Reverse order to delete children first
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                LOG.error("Failed to delete {}", p, e);
                            }
                        });
            }
        }

        private void showError(String title, String message) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            });
        }
    }
}
