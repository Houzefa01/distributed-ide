package com.distributed_ide.javafx_client.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Cross-platform OS detection and tool availability utility.
 * Provides centralized detection and resolution of:
 * - Operating system (Windows, macOS, Linux)
 * - Available shell interpreters
 * - Tool paths (Maven, Java, Python, etc.)
 * - Platform-specific executable extensions
 *
 * Usage:
 *   OSDetector.OperatingSystem os = OSDetector.detectOS();
 *   String shell = OSDetector.getShell();
 *   String mvn = OSDetector.getCommandPath("mvn");
 */
public class OSDetector {

    private static final Logger LOG = LoggerFactory.getLogger(OSDetector.class);

    /**
     * Enumeration of supported operating systems.
     */
    public enum OperatingSystem {
        WINDOWS("Windows"),
        MACOS("macOS"),
        LINUX("Linux"),
        UNKNOWN("Unknown");

        private final String displayName;

        OperatingSystem(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Detects the current operating system.
     * @return The detected OperatingSystem
     */
    public static OperatingSystem detectOS() {
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("win")) {
            return OperatingSystem.WINDOWS;
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            return OperatingSystem.MACOS;
        } else if (osName.contains("linux") || osName.contains("nux")) {
            return OperatingSystem.LINUX;
        } else {
            LOG.warn("Unknown operating system: {}", osName);
            return OperatingSystem.UNKNOWN;
        }
    }

    /**
     * Gets the file separator for the current platform.
     * @return "/" on Unix-like systems, "\" on Windows
     */
    public static String getFileSeparator() {
        return File.separator;
    }

    /**
     * Gets the path separator for the current platform.
     * @return ":" on Unix-like systems, ";" on Windows
     */
    public static String getPathSeparator() {
        return File.pathSeparator;
    }

    /**
     * Detects the available shell for command execution.
     * - Windows: Uses cmd.exe
     * - macOS: Prefers zsh (Catalina+), falls back to bash or SHELL env var
     * - Linux: Uses bash, zsh, or sh (in order of preference)
     *
     * @return The path to the available shell, never null
     * @throws RuntimeException if no suitable shell found (rare)
     */
    public static String getShell() throws RuntimeException {
        OperatingSystem os = detectOS();

        if (os == OperatingSystem.WINDOWS) {
            return resolveWindowsShell();
        } else {
            return resolveUnixLikeShell();
        }
    }

    /**
     * Gets shell arguments for executing a command interactively.
     * - Windows: ["/Q"] for quiet mode
     * - Unix-like: ["-i"] for interactive mode
     *
     * @return Array of shell arguments
     */
    public static String[] getShellArgs(boolean interactive) {
        OperatingSystem os = detectOS();

        if (os == OperatingSystem.WINDOWS) {
            return new String[]{"/Q"}; // Quiet mode
        } else {
            return interactive ? new String[]{"-i"} : new String[]{};
        }
    }

    /**
     * Resolves the path to a command, checking system PATH and common locations.
     * Handles platform-specific executable extensions (.exe, .cmd, .bat on Windows).
     *
     * @param command The command name (e.g., "mvn", "java", "python3")
     * @return The resolved command path, or the original command if not found
     */
    public static String getCommandPath(String command) {
        OperatingSystem os = detectOS();

        if (os == OperatingSystem.WINDOWS) {
            return findWindowsCommand(command);
        } else {
            return findUnixCommand(command);
        }
    }

    /**
     * Checks if a command is available in the system PATH or at a standard location.
     * @param command The command name to check
     * @return true if the command is available
     */
    public static boolean isCommandAvailable(String command) {
        try {
            String resolvedCommand = getCommandPath(command);
            OperatingSystem os = detectOS();

            String[] whereCmd = os == OperatingSystem.WINDOWS
                    ? new String[]{"where", resolvedCommand}
                    : new String[]{"which", resolvedCommand};

            ProcessBuilder pb = new ProcessBuilder(whereCmd);
            pb.redirectErrorStream(true);

            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            LOG.debug("Command availability check failed for '{}'", command, e);
            return false;
        }
    }

    /**
     * Tries multiple command variants and returns the first available one.
     * Useful for Python (python/python3), git (git/git.exe), etc.
     *
     * @param commandVariants Array of command variants to try
     * @return First available command, or Optional.empty() if none found
     */
    public static Optional<String> findFirstAvailable(String... commandVariants) {
        for (String cmd : commandVariants) {
            if (isCommandAvailable(cmd)) {
                LOG.debug("Found available command: {}", cmd);
                return Optional.of(cmd);
            }
        }
        LOG.debug("No available command found in variants: {}", Arrays.toString(commandVariants));
        return Optional.empty();
    }

    /**
     * Gets the JAVA_HOME directory if set.
     * @return Optional containing JAVA_HOME path, or empty if not set
     */
    public static Optional<String> getJavaHome() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            return Optional.of(javaHome);
        }
        return Optional.empty();
    }

    /**
     * Resolves the javac (Java compiler) path.
     * Tries JAVA_HOME/bin/javac first, then searches PATH.
     *
     * @return Path to javac executable, or "javac" if not found (assumes PATH)
     */
    public static String getJavacPath() {
        Optional<String> javaHome = getJavaHome();
        if (javaHome.isPresent()) {
            String javacPath = javaHome.get() + File.separator + "bin" + File.separator + "javac";
            if (detectOS() == OperatingSystem.WINDOWS) {
                javacPath += ".exe";
            }
            if (new File(javacPath).exists()) {
                return javacPath;
            }
        }
        return "javac"; // Fallback to PATH
    }

    /**
     * Resolves the java (Java runtime) path.
     * Tries JAVA_HOME/bin/java first, then searches PATH.
     *
     * @return Path to java executable, or "java" if not found (assumes PATH)
     */
    public static String getJavaPath() {
        Optional<String> javaHome = getJavaHome();
        if (javaHome.isPresent()) {
            String javaPath = javaHome.get() + File.separator + "bin" + File.separator + "java";
            if (detectOS() == OperatingSystem.WINDOWS) {
                javaPath += ".exe";
            }
            if (new File(javaPath).exists()) {
                return javaPath;
            }
        }
        return "java"; // Fallback to PATH
    }

    /**
     * Resolves the Maven command based on OS.
     * On Windows, uses "mvn.cmd", on Unix-like systems uses "mvn".
     *
     * @return "mvn.cmd" on Windows, "mvn" on other systems
     */
    public static String getMavenCommand() {
        return detectOS() == OperatingSystem.WINDOWS ? "mvn.cmd" : "mvn";
    }

    /**
     * Resolves Python command, trying python3 first, then python.
     * @return First available Python command, or "python3" as final fallback
     */
    public static String getPythonCommand() {
        Optional<String> python = findFirstAvailable("python3", "python", "py");
        if (python.isPresent()) {
            return python.get();
        }
        LOG.warn("Python not found in PATH, using fallback 'python3'");
        return "python3";
    }

    /**
     * Gets the system architecture (x86_64, arm64, etc.)
     * @return Architecture string from os.arch property
     */
    public static String getArchitecture() {
        return System.getProperty("os.arch", "unknown");
    }

    /**
     * Checks if running on Apple Silicon (ARM64) Mac.
     * @return true if on Apple Silicon, false otherwise
     */
    public static boolean isAppleSilicon() {
        return detectOS() == OperatingSystem.MACOS && 
               System.getProperty("os.arch").equals("aarch64");
    }

    /**
     * Gets a user-friendly OS description including architecture.
     * Example: "Windows 11 (x86_64)" or "macOS 13.2 (arm64)"
     *
     * @return Description string
     */
    public static String getSystemDescription() {
        String osName = System.getProperty("os.name", "Unknown");
        String osVersion = System.getProperty("os.version", "Unknown");
        String arch = System.getProperty("os.arch", "unknown");
        return String.format("%s %s (%s)", osName, osVersion, arch);
    }

    // ═══════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════

    private static String resolveWindowsShell() {
        // Try to find cmd.exe in standard Windows location
        String[] cmdPaths = {
                "C:\\Windows\\System32\\cmd.exe",
                "C:\\WINNT\\System32\\cmd.exe" // For legacy Windows versions
        };

        for (String path : cmdPaths) {
            if (new File(path).exists()) {
                LOG.debug("Found Windows shell at: {}", path);
                return path;
            }
        }

        // Fallback to PATH
        if (isCommandAvailable("cmd")) {
            LOG.debug("Found cmd.exe in PATH");
            return "cmd";
        }

        LOG.error("Unable to find Windows shell (cmd.exe)");
        return "cmd"; // Last resort
    }

    private static String resolveUnixLikeShell() {
        // First, try SHELL environment variable (standard Unix approach)
        String shellEnv = System.getenv("SHELL");
        if (shellEnv != null && !shellEnv.isEmpty() && new File(shellEnv).exists()) {
            LOG.debug("Using SHELL environment variable: {}", shellEnv);
            return shellEnv;
        }

        OperatingSystem os = detectOS();

        // macOS-specific preferences
        if (os == OperatingSystem.MACOS) {
            // Prefer zsh on macOS (default since Catalina)
            if (new File("/bin/zsh").exists()) {
                LOG.debug("Using /bin/zsh on macOS");
                return "/bin/zsh";
            }
            // Fallback to bash
            if (new File("/bin/bash").exists()) {
                LOG.debug("Using /bin/bash on macOS");
                return "/bin/bash";
            }
        } else {
            // Linux and others: bash > zsh > sh
            List<String> shellPaths = Arrays.asList("/bin/bash", "/bin/zsh", "/bin/sh");
            for (String shellPath : shellPaths) {
                if (new File(shellPath).exists()) {
                    LOG.debug("Using shell: {}", shellPath);
                    return shellPath;
                }
            }
        }

        LOG.error("Unable to find suitable shell on {}", os.getDisplayName());
        throw new RuntimeException("No suitable shell found on " + os.getDisplayName());
    }

    private static String findWindowsCommand(String command) {
        // Try exact command first
        if (isInPath(command)) {
            return command;
        }

        // Try with .exe extension
        if (isInPath(command + ".exe")) {
            return command + ".exe";
        }

        // Try with .cmd extension
        if (isInPath(command + ".cmd")) {
            return command + ".cmd";
        }

        // Try with .bat extension
        if (isInPath(command + ".bat")) {
            return command + ".bat";
        }

        LOG.debug("Command '{}' not found with standard extensions, using as-is", command);
        return command; // Fallback
    }

    private static String findUnixCommand(String command) {
        // Try exact command first (most common)
        if (isInPath(command)) {
            return command;
        }

        // Try common installation locations
        String[] commonPaths = {
                "/usr/local/bin",
                "/usr/bin",
                "/bin",
                "/opt/homebrew/bin" // For macOS M1/M2 (Apple Silicon)
        };

        for (String path : commonPaths) {
            String fullPath = path + File.separator + command;
            if (new File(fullPath).exists() && new File(fullPath).canExecute()) {
                LOG.debug("Found '{}' at: {}", command, fullPath);
                return fullPath;
            }
        }

        LOG.debug("Command '{}' not found in standard locations, using as-is", command);
        return command; // Fallback to PATH
    }

    private static boolean isInPath(String command) {
        try {
            OperatingSystem os = detectOS();
            String[] cmd = os == OperatingSystem.WINDOWS
                    ? new String[]{"where", command}
                    : new String[]{"which", command};

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
