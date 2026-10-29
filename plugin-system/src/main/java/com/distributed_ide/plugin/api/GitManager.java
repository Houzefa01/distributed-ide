package com.distributed_ide.plugin.api;


import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Provides access to Git functionality for plugins.
 */
public interface GitManager {
    
    /**
     * Check if the current directory is a Git repository.
     * 
     * @return true if in a Git repository
     */
    boolean isGitRepository();
    
    /**
     * Get the current branch.
     * 
     * @return Current branch, or empty if not a Git repository
     */
    
    Optional<String> getCurrentBranch();
    
    /**
     * Get all branches.
     * 
     * @return List of branches
     */
    
    List<String> getBranches();
    
    /**
     * Get Git status.
     * 
     * @return CompletableFuture with Git status
     */
    
    CompletableFuture<GitStatus> getStatus();
    
    /**
     * Stage files.
     * 
     * @param files Files to stage
     * @return CompletableFuture with success status
     */
    
    CompletableFuture<Boolean> stage( List<Path> files);
    
    /**
     * Unstage files.
     * 
     * @param files Files to unstage
     * @return CompletableFuture with success status
     */
    
    CompletableFuture<Boolean> unstage( List<Path> files);
    
    /**
     * Commit changes.
     * 
     * @param message Commit message
     * @return CompletableFuture with commit hash
     */
    
    CompletableFuture<String> commit( String message);
    
    /**
     * Push changes.
     * 
     * @param remote Remote name
     * @param branch Branch name
     * @return CompletableFuture with success status
     */
    
    CompletableFuture<Boolean> push( String remote,  String branch);
    
    /**
     * Pull changes.
     * 
     * @param remote Remote name
     * @param branch Branch name
     * @return CompletableFuture with success status
     */
    
    CompletableFuture<Boolean> pull( String remote,  String branch);
    
    interface GitStatus {
        boolean isClean();
        
        
        List<StatusFile> getModifiedFiles();
        
        
        List<StatusFile> getStagedFiles();
        
        
        List<StatusFile> getUntrackedFiles();
        
        
        String getAhead();
        
        
        String getBehind();
    }
    
    interface StatusFile {
        
        Path getPath();
        
        
        FileStatus getStatus();
        
        
        String getOldPath();
    }
    
    enum FileStatus {
        MODIFIED, ADDED, DELETED, RENAMED, UNTRACKED
    }
}
