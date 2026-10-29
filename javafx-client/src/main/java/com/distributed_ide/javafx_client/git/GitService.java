package com.distributed_ide.javafx_client.git;

import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service Git complet basé sur JGit.
 *
 * Thread-safe, non-bloquant : toutes les opérations lourdes retournent CompletableFuture.
 *
 * Fonctionnalités :
 * - Clone, Init
 * - Status (staged, modified, untracked)
 * - Add, Commit
 * - Push, Pull
 * - Branch (list, create, checkout, delete)
 * - Diff (working tree vs HEAD, commit vs commit)
 * - Log (historique des commits)
 * - Credentials management
 */
public class GitService {

    private static final Logger LOG = LoggerFactory.getLogger(GitService.class);

    private final ExecutorService executor;
    private volatile Git git;
    private volatile Path repoPath;
    private volatile CredentialsProvider credentials;

    public GitService() {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "git-service-worker");
            t.setDaemon(true);
            return t;
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  MODELS
    // ═══════════════════════════════════════════════════════════

    public record FileStatus(String path, StatusType type) {}

    public enum StatusType {
        ADDED, MODIFIED, DELETED, UNTRACKED, CONFLICTING, MISSING
    }

    public record CommitInfo(String hash, String shortHash, String author, String message, long timestamp) {}

    public record DiffResult(String filePath, String header, String content, DiffEntry.ChangeType changeType) {}

    public record BranchInfo(String name, boolean isRemote, boolean isCurrent) {}

    // ═══════════════════════════════════════════════════════════
    //  CREDENTIALS
    // ═══════════════════════════════════════════════════════════

    public void setCredentials(String username, String password) {
        this.credentials = new UsernamePasswordCredentialsProvider(username, password);
    }

    public void clearCredentials() {
        this.credentials = null;
    }

    // ═══════════════════════════════════════════════════════════
    //  REPO LIFECYCLE
    // ═══════════════════════════════════════════════════════════

    /**
     * Ouvre un dépôt Git existant.
     */
    public CompletableFuture<Boolean> open(Path projectPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                close();
                File gitDir = findGitDir(projectPath.toFile());
                if (gitDir == null) {
                    LOG.debug("No .git directory found in {}", projectPath);
                    return false;
                }
                Repository repo = new FileRepositoryBuilder()
                        .setGitDir(gitDir)
                        .readEnvironment()
                        .build();
                git = new Git(repo);
                repoPath = gitDir.getParentFile().toPath();
                LOG.info("Git repo opened: {}", repoPath);
                return true;
            } catch (IOException e) {
                LOG.warn("Failed to open git repo: {}", e.getMessage());
                return false;
            }
        }, executor);
    }

    /**
     * Initialise un nouveau dépôt Git.
     */
    public CompletableFuture<Boolean> init(Path projectPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                close();
                git = Git.init().setDirectory(projectPath.toFile()).call();
                repoPath = projectPath;
                LOG.info("Git repo initialized: {}", projectPath);
                return true;
            } catch (GitAPIException e) {
                LOG.error("Git init failed: {}", e.getMessage());
                return false;
            }
        }, executor);
    }

    /**
     * Clone un dépôt distant.
     */
    public CompletableFuture<Boolean> cloneRepo(String remoteUrl, Path targetDir) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                close();
                CloneCommand cmd = Git.cloneRepository()
                        .setURI(remoteUrl)
                        .setDirectory(targetDir.toFile())
                        .setCloneAllBranches(true);
                if (credentials != null) {
                    cmd.setCredentialsProvider(credentials);
                }
                git = cmd.call();
                repoPath = targetDir;
                LOG.info("Cloned {} into {}", remoteUrl, targetDir);
                return true;
            } catch (GitAPIException e) {
                LOG.error("Git clone failed: {}", e.getMessage());
                return false;
            }
        }, executor);
    }

    public void close() {
        if (git != null) {
            git.close();
            git = null;
            repoPath = null;
        }
    }

    public boolean isOpen() {
        return git != null;
    }

    public Path getRepoPath() {
        return repoPath;
    }

    // ═══════════════════════════════════════════════════════════
    //  STATUS
    // ═══════════════════════════════════════════════════════════

    /**
     * Retourne le statut de tous les fichiers modifiés/ajoutés/supprimés.
     */
    public CompletableFuture<List<FileStatus>> status() {
        return CompletableFuture.supplyAsync(() -> {
            if (git == null) return Collections.emptyList();
            try {
                Status status = git.status().call();
                List<FileStatus> result = new ArrayList<>();
                for (String f : status.getAdded()) result.add(new FileStatus(f, StatusType.ADDED));
                for (String f : status.getChanged()) result.add(new FileStatus(f, StatusType.MODIFIED));
                for (String f : status.getModified()) result.add(new FileStatus(f, StatusType.MODIFIED));
                for (String f : status.getRemoved()) result.add(new FileStatus(f, StatusType.DELETED));
                for (String f : status.getMissing()) result.add(new FileStatus(f, StatusType.MISSING));
                for (String f : status.getUntracked()) result.add(new FileStatus(f, StatusType.UNTRACKED));
                for (String f : status.getConflicting()) result.add(new FileStatus(f, StatusType.CONFLICTING));
                result.sort(Comparator.comparing(FileStatus::path));
                return result;
            } catch (GitAPIException e) {
                LOG.warn("Git status failed: {}", e.getMessage());
                return Collections.emptyList();
            }
        }, executor);
    }

    /**
     * Nom de la branche courante.
     */
    public CompletableFuture<String> currentBranch() {
        return CompletableFuture.supplyAsync(() -> {
            if (git == null) return "";
            try {
                return git.getRepository().getBranch();
            } catch (IOException e) {
                return "";
            }
        }, executor);
    }

    // ═══════════════════════════════════════════════════════════
    //  ADD / COMMIT
    // ═══════════════════════════════════════════════════════════

    /**
     * Stage des fichiers (git add).
     */
    public CompletableFuture<Void> add(List<String> filePaths) {
        return CompletableFuture.runAsync(() -> {
            if (git == null) return;
            try {
                AddCommand cmd = git.add();
                for (String p : filePaths) {
                    cmd.addFilepattern(p);
                }
                cmd.call();
                LOG.debug("Staged {} files", filePaths.size());
            } catch (GitAPIException e) {
                LOG.error("Git add failed: {}", e.getMessage());
                throw new RuntimeException("Git add failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    /**
     * Stage tous les fichiers (git add -A).
     */
    public CompletableFuture<Void> addAll() {
        return CompletableFuture.runAsync(() -> {
            if (git == null) return;
            try {
                git.add().addFilepattern(".").call();
                // Also stage deletions
                git.add().addFilepattern(".").setUpdate(true).call();
                LOG.debug("Staged all files");
            } catch (GitAPIException e) {
                LOG.error("Git add all failed: {}", e.getMessage());
                throw new RuntimeException("Git add all failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    /**
     * Commit les fichiers stagés.
     */
    public CompletableFuture<CommitInfo> commit(String message, String authorName, String authorEmail) {
        return CompletableFuture.supplyAsync(() -> {
            if (git == null) throw new RuntimeException("No repo open");
            try {
                CommitCommand cmd = git.commit().setMessage(message);
                if (authorName != null && authorEmail != null) {
                    cmd.setAuthor(authorName, authorEmail);
                }
                RevCommit rev = cmd.call();
                LOG.info("Committed: {} — {}", rev.abbreviate(7).name(), message);
                return toCommitInfo(rev);
            } catch (GitAPIException | IOException e) {
                LOG.error("Git commit failed: {}", e.getMessage());
                throw new RuntimeException("Git commit failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    // ═══════════════════════════════════════════════════════════
    //  PUSH / PULL
    // ═══════════════════════════════════════════════════════════

    /**
     * Push vers le remote (origin).
     */
    public CompletableFuture<String> push() {
        return CompletableFuture.supplyAsync(() -> {
            if (git == null) throw new RuntimeException("No repo open");
            try {
                PushCommand cmd = git.push();
                if (credentials != null) cmd.setCredentialsProvider(credentials);
                var results = cmd.call();
                StringBuilder sb = new StringBuilder();
                results.forEach(r -> r.getRemoteUpdates().forEach(u ->
                        sb.append(u.getRemoteName()).append(": ").append(u.getStatus()).append("\n")
                ));
                String result = sb.toString().trim();
                LOG.info("Push result: {}", result);
                return result.isEmpty() ? "Push completed" : result;
            } catch (GitAPIException e) {
                LOG.error("Git push failed: {}", e.getMessage());
                throw new RuntimeException("Git push failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    /**
     * Pull depuis le remote (origin).
     */
    public CompletableFuture<String> pull() {
        return CompletableFuture.supplyAsync(() -> {
            if (git == null) throw new RuntimeException("No repo open");
            try {
                PullCommand cmd = git.pull();
                if (credentials != null) cmd.setCredentialsProvider(credentials);
                PullResult result = cmd.call();
                String msg = result.isSuccessful()
                        ? "Pull successful" + (result.getMergeResult() != null ? " (" + result.getMergeResult().getMergeStatus() + ")" : "")
                        : "Pull failed: " + (result.getMergeResult() != null ? result.getMergeResult().getMergeStatus() : "unknown");
                LOG.info(msg);
                return msg;
            } catch (GitAPIException e) {
                LOG.error("Git pull failed: {}", e.getMessage());
                throw new RuntimeException("Git pull failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    // ═══════════════════════════════════════════════════════════
    //  BRANCHES
    // ═══════════════════════════════════════════════════════════

    /**
     * Liste toutes les branches (locales + remote).
     */
    public CompletableFuture<List<BranchInfo>> branches() {
        return CompletableFuture.supplyAsync(() -> {
            if (git == null) return Collections.emptyList();
            try {
                String currentBranch = git.getRepository().getBranch();
                List<BranchInfo> result = new ArrayList<>();

                List<Ref> localBranches = git.branchList().call();
                for (Ref ref : localBranches) {
                    String name = Repository.shortenRefName(ref.getName());
                    result.add(new BranchInfo(name, false, name.equals(currentBranch)));
                }

                List<Ref> remoteBranches = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
                for (Ref ref : remoteBranches) {
                    String name = Repository.shortenRefName(ref.getName());
                    result.add(new BranchInfo(name, true, false));
                }

                return result;
            } catch (GitAPIException | IOException e) {
                LOG.warn("Git branches failed: {}", e.getMessage());
                return Collections.emptyList();
            }
        }, executor);
    }

    /**
     * Crée une nouvelle branche.
     */
    public CompletableFuture<Void> createBranch(String name) {
        return CompletableFuture.runAsync(() -> {
            if (git == null) return;
            try {
                git.branchCreate().setName(name).call();
                LOG.info("Branch created: {}", name);
            } catch (GitAPIException e) {
                LOG.error("Create branch failed: {}", e.getMessage());
                throw new RuntimeException("Create branch failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    /**
     * Checkout une branche.
     */
    public CompletableFuture<Void> checkout(String branchName) {
        return CompletableFuture.runAsync(() -> {
            if (git == null) return;
            try {
                git.checkout().setName(branchName).call();
                LOG.info("Checked out: {}", branchName);
            } catch (GitAPIException e) {
                LOG.error("Checkout failed: {}", e.getMessage());
                throw new RuntimeException("Checkout failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    /**
     * Supprime une branche locale.
     */
    public CompletableFuture<Void> deleteBranch(String branchName) {
        return CompletableFuture.runAsync(() -> {
            if (git == null) return;
            try {
                git.branchDelete().setBranchNames(branchName).setForce(true).call();
                LOG.info("Branch deleted: {}", branchName);
            } catch (GitAPIException e) {
                LOG.error("Delete branch failed: {}", e.getMessage());
                throw new RuntimeException("Delete branch failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    // ═══════════════════════════════════════════════════════════
    //  DIFF
    // ═══════════════════════════════════════════════════════════

    /**
     * Diff entre working tree et HEAD (modifications non commitées).
     */
    public CompletableFuture<List<DiffResult>> diffWorkingTree() {
        return CompletableFuture.supplyAsync(() -> {
            if (git == null) return Collections.emptyList();
            try {
                Repository repo = git.getRepository();
                List<DiffResult> results = new ArrayList<>();

                // Unstaged changes
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                     DiffFormatter df = new DiffFormatter(baos)) {
                    df.setRepository(repo);
                    AbstractTreeIterator headTree = getHeadTree(repo);
                    if (headTree != null) {
                        List<DiffEntry> entries = df.scan(headTree, new FileTreeIterator(repo));
                        for (DiffEntry entry : entries) {
                            baos.reset();
                            df.format(entry);
                            String filePath = entry.getChangeType() == DiffEntry.ChangeType.DELETE
                                    ? entry.getOldPath() : entry.getNewPath();
                            results.add(new DiffResult(
                                    filePath,
                                    entry.getChangeType().name(),
                                    baos.toString(),
                                    entry.getChangeType()
                            ));
                        }
                    }
                }
                return results;
            } catch (IOException e) {
                LOG.warn("Diff failed: {}", e.getMessage());
                return Collections.emptyList();
            }
        }, executor);
    }

    /**
     * Diff d'un fichier spécifique (working tree vs HEAD).
     */
    public CompletableFuture<String> diffFile(String relativePath) {
        return CompletableFuture.supplyAsync(() -> {
            if (git == null) return "";
            try {
                Repository repo = git.getRepository();
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                     DiffFormatter df = new DiffFormatter(baos)) {
                    df.setRepository(repo);
                    df.setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(relativePath));
                    AbstractTreeIterator headTree = getHeadTree(repo);
                    if (headTree != null) {
                        List<DiffEntry> entries = df.scan(headTree, new FileTreeIterator(repo));
                        for (DiffEntry entry : entries) {
                            df.format(entry);
                        }
                        return baos.toString();
                    }
                }
                return "";
            } catch (IOException e) {
                LOG.warn("Diff file failed: {}", e.getMessage());
                return "";
            }
        }, executor);
    }

    // ═══════════════════════════════════════════════════════════
    //  LOG
    // ═══════════════════════════════════════════════════════════

    /**
     * Historique des derniers N commits.
     */
    public CompletableFuture<List<CommitInfo>> log(int maxCount) {
        return CompletableFuture.supplyAsync(() -> {
            if (git == null) return Collections.emptyList();
            try {
                Iterable<RevCommit> commits = git.log().setMaxCount(maxCount).call();
                List<CommitInfo> result = new ArrayList<>();
                for (RevCommit rev : commits) {
                    result.add(toCommitInfo(rev));
                }
                return result;
            } catch (GitAPIException | IOException e) {
                LOG.warn("Git log failed: {}", e.getMessage());
                return Collections.emptyList();
            }
        }, executor);
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    private File findGitDir(File dir) {
        File current = dir;
        while (current != null) {
            File gitDir = new File(current, ".git");
            if (gitDir.exists() && gitDir.isDirectory()) {
                return gitDir;
            }
            current = current.getParentFile();
        }
        return null;
    }

    private AbstractTreeIterator getHeadTree(Repository repo) throws IOException {
        ObjectId head = repo.resolve("HEAD^{tree}");
        if (head == null) return null;
        CanonicalTreeParser parser = new CanonicalTreeParser();
        try (ObjectReader reader = repo.newObjectReader()) {
            parser.reset(reader, head);
        }
        return parser;
    }

    private CommitInfo toCommitInfo(RevCommit rev) throws IOException {
        PersonIdent author = rev.getAuthorIdent();
        return new CommitInfo(
                rev.getName(),
                rev.abbreviate(7).name(),
                author != null ? author.getName() : "Unknown",
                rev.getShortMessage(),
                rev.getCommitTime() * 1000L
        );
    }

    public void shutdown() {
        close();
        executor.shutdown();
    }
}
