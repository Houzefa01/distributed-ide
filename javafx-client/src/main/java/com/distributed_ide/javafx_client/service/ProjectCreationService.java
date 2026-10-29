package com.distributed_ide.javafx_client.service;

import com.distributed_ide.javafx_client.model.ProjectCreationRequest;
import com.distributed_ide.javafx_client.model.ProjectCreationResult;
import com.distributed_ide.javafx_client.model.ProjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Orchestrates project creation wizard flow.
 * Validates input and delegates to ProjectScaffolder.
 */
public class ProjectCreationService {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectCreationService.class);
    private static final ProjectScaffolder SCAFFOLDER = new ProjectScaffolder();

    // Validation patterns
    private static final Pattern VALID_PROJECT_NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_\\-]*$");
    private static final Pattern VALID_PACKAGE_NAME = Pattern.compile("^[a-z]([a-z0-9]*(\\.[a-z][a-z0-9]*)*)?$");
    private static final Pattern VALID_CLASS_NAME = Pattern.compile("^[A-Z][a-zA-Z0-9]*$");

    /**
     * Validates the project creation request.
     * Returns a validation result with error messages if invalid.
     */
    public ValidationResult validateRequest(ProjectCreationRequest request) {
        LOG.debug("Validating project creation request: {}", request);

        // Validate project name
        if (request.getProjectName() == null || request.getProjectName().isBlank()) {
            return ValidationResult.error("Project name is required");
        }

        if (!VALID_PROJECT_NAME.matcher(request.getProjectName()).matches()) {
            return ValidationResult.error("Project name must start with a letter and contain only letters, numbers, dashes, and underscores");
        }

        if (request.getProjectName().length() > 50) {
            return ValidationResult.error("Project name must not exceed 50 characters");
        }

        // Validate parent directory
        if (request.getParentDirectory() == null) {
            return ValidationResult.error("Parent directory is required");
        }

        if (!Files.isDirectory(request.getParentDirectory())) {
            return ValidationResult.error("Parent directory does not exist: " + request.getParentDirectory());
        }

        if (!Files.isWritable(request.getParentDirectory())) {
            return ValidationResult.error("Parent directory is not writable: " + request.getParentDirectory());
        }

        // Check if project already exists
        Path projectPath = request.getParentDirectory().resolve(request.getProjectName());
        if (Files.exists(projectPath)) {
            return ValidationResult.error("Project directory already exists: " + projectPath);
        }

        // Type-specific validation
        return switch (request.getProjectType()) {
            case MAVEN -> validateMavenRequest(request);
            case JAVA_SIMPLE -> validateJavaSimpleRequest(request);
            case PYTHON -> validatePythonRequest(request);
            default -> ValidationResult.error("Unsupported project type: " + request.getProjectType());
        };
    }

    private ValidationResult validateMavenRequest(ProjectCreationRequest request) {
        // Validate package name
        if (request.getPackageName() == null || request.getPackageName().isBlank()) {
            return ValidationResult.error("Package name is required for Maven projects");
        }

        if (!VALID_PACKAGE_NAME.matcher(request.getPackageName()).matches()) {
            return ValidationResult.error("Invalid package name. Use format: com.example.app");
        }

        // Validate main class name
        if (request.getMainClassName() == null || request.getMainClassName().isBlank()) {
            return ValidationResult.error("Main class name is required");
        }

        if (!VALID_CLASS_NAME.matcher(request.getMainClassName()).matches()) {
            return ValidationResult.error("Invalid class name. Must start with uppercase letter");
        }

        // Validate group ID
        String groupId = request.getGroupId() != null ? request.getGroupId() : request.getPackageName();
        if (!VALID_PACKAGE_NAME.matcher(groupId).matches()) {
            return ValidationResult.error("Invalid group ID. Use format: com.example");
        }

        return ValidationResult.success();
    }

    private ValidationResult validateJavaSimpleRequest(ProjectCreationRequest request) {
        // Validate package name
        if (request.getPackageName() == null || request.getPackageName().isBlank()) {
            return ValidationResult.error("Package name is required for Java projects");
        }

        if (!VALID_PACKAGE_NAME.matcher(request.getPackageName()).matches()) {
            return ValidationResult.error("Invalid package name. Use format: com.example.app");
        }

        // Validate main class name
        if (request.getMainClassName() == null || request.getMainClassName().isBlank()) {
            return ValidationResult.error("Main class name is required");
        }

        if (!VALID_CLASS_NAME.matcher(request.getMainClassName()).matches()) {
            return ValidationResult.error("Invalid class name. Must start with uppercase letter");
        }

        return ValidationResult.success();
    }

    private ValidationResult validatePythonRequest(ProjectCreationRequest request) {
        // Python projects need minimal validation - just name and directory
        return ValidationResult.success();
    }

    /**
     * Creates a project asynchronously and returns a CompletableFuture with the result.
     */
    public CompletableFuture<ProjectCreationResult> createProjectAsync(ProjectCreationRequest request) {
        LOG.info("Starting async project creation: {}", request);

        // First validate
        ValidationResult validation = validateRequest(request);
        if (!validation.isValid()) {
            LOG.warn("Project creation request validation failed: {}", validation.getErrorMessage());
            CompletableFuture<ProjectCreationResult> future = new CompletableFuture<>();
            future.complete(new ProjectCreationResult.Builder()
                    .projectType(request.getProjectType())
                    .projectName(request.getProjectName())
                    .success(false)
                    .message("Validation failed: " + validation.getErrorMessage())
                    .build());
            return future;
        }

        // Create async
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProjectCreationResult result = SCAFFOLDER.createProject(request);
                LOG.info("Project creation completed: {}", result);
                return result;
            } catch (Exception e) {
                LOG.error("Error during project creation", e);
                return new ProjectCreationResult.Builder()
                        .projectType(request.getProjectType())
                        .projectName(request.getProjectName())
                        .success(false)
                        .message("Project creation failed: " + e.getMessage())
                        .error(e)
                        .build();
            }
        });
    }

    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
