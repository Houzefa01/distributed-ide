package com.distributed_ide.terminal.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates terminal commands before execution
 * Whitelist-based approach: only allow safe commands
 * Blocks dangerous patterns like command substitution, device files, etc.
 * 
 * FIX #3 IMPLEMENTATION: Command Injection Prevention
 */
public class CommandValidator {
    
    private static final Logger LOG = LoggerFactory.getLogger(CommandValidator.class);
    
    // Allowed executable binaries
    private static final Set<String> ALLOWED_BINARIES = new HashSet<>(Arrays.asList(
        "bash", "sh", "python", "python3", "java", "javac",
        "mvn", "npm", "node", "git",
        "ls", "cat", "grep", "find", "wc", "head", "tail",
        "mkdir", "touch", "chmod", "chown",
        "curl", "wget", "ping", "netstat",
        "ps", "kill", "jobs", "fg", "bg",
        "maven", "gradle", "ant"
    ));
    
    // Commands that should only have read access
    private static final Set<String> READ_ONLY_COMMANDS = new HashSet<>(Arrays.asList(
        "cat", "grep", "find", "ls", "head", "tail", "wc", "curl", "wget"
    ));
    
    // Dangerous patterns to block
    private static final Pattern[] DANGEROUS_PATTERNS = {
        Pattern.compile(".*\\$\\(.*\\).*"),           // Command substitution $(...)
        Pattern.compile(".*`.*`.*"),                   // Backtick substitution
        Pattern.compile(".*(rm|mkfs|dd|shred)\\s+.*"), // Dangerous commands
        Pattern.compile(".*<(\\(|\\d).*"),            // Input redirection <
        Pattern.compile(".*(exec|source|exit|eval)\\b.*"), // Dangerous builtins (no dot!)
        Pattern.compile(".*(/dev/(zero|random|null|urandom)).*"), // Device files
        Pattern.compile(".*;\\s*.*"),                  // Command chaining with ;
        Pattern.compile(".*\\|\\s*.*"),                // Piping
        Pattern.compile(".*&&.*"),                     // Logical AND
        Pattern.compile(".*\\|\\|.*"),                 // Logical OR
    };
    
    /**
     * Validates a command before execution
     * 
     * @param command   Full command string to validate
     * @return          true if command is allowed, false otherwise
     */
    public boolean validate(String command) {
        if (command == null || command.trim().isEmpty()) {
            LOG.warn("❌ Empty command");
            return false;
        }
        
        // 1. Extract binary name
        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0) {
            return false;
        }
        
        String binary = parts[0];
        
        // Remove path prefix if present (e.g., /usr/bin/python → python)
        if (binary.contains("/")) {
            binary = binary.substring(binary.lastIndexOf('/') + 1);
        }
        
        // 2. Check if binary is whitelisted
        if (!ALLOWED_BINARIES.contains(binary)) {
            LOG.warn("❌ Command not whitelisted: {}", binary);
            return false;
        }
        
        // 3. Check for dangerous patterns
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(command).matches()) {
                LOG.warn("❌ Dangerous pattern detected in: {}", command);
                return false;
            }
        }
        
        // 4. Check for network restrictions (curl/wget only to localhost)
        if (binary.equals("curl") || binary.equals("wget")) {
            if (!command.contains("localhost") && 
                !command.contains("127.0.0.1") &&
                !command.contains("0.0.0.0")) {
                LOG.warn("❌ External network access forbidden: {}", command);
                return false;
            }
        }
        
        // 5. Path access restrictions
        if (isPathModificationCommand(binary)) {
            String targetPath = extractTargetPath(command, parts);
            if (!isAllowedPath(targetPath)) {
                LOG.warn("❌ Path access forbidden: {} (restrict to /workspace, /tmp)", targetPath);
                return false;
            }
        }
        
        LOG.info("✅ Command validated: {}", binary);
        return true;
    }
    
    /**
     * Check if command modifies filesystem
     */
    private boolean isPathModificationCommand(String binary) {
        return binary.matches("(mv|rm|chmod|chown|mkdir|rmdir|ln|cp)");
    }
    
    /**
     * Extract target path from command
     */
    private String extractTargetPath(String command, String[] parts) {
        if (parts.length < 2) return "";
        
        // Usually second argument is the path
        String path = parts[1];
        // Remove quotes if present
        return path.replaceAll("^\"|\"$|^'|'$", "");
    }
    
    /**
     * Check if path is allowed (workspace or tmp only)
     */
    private boolean isAllowedPath(String path) {
        if (path == null || path.isEmpty()) {
            return true;  // No path specified is OK
        }
        
        return path.startsWith("/workspace") || 
               path.startsWith("/tmp") ||
               path.startsWith("./") ||
               path.startsWith("../") ||
               !path.startsWith("/");  // Relative paths are OK
    }
}
