package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.javafx_client.model.ProjectCreationResult;
import com.distributed_ide.javafx_client.model.ProjectType;
import javafx.scene.control.Alert;
import javafx.scene.control.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Integrates Project Creation Wizard into the main IDE menu.
 * Provides menu items and handling for project creation workflow.
 */
public class WizardIntegration {

    private static final Logger LOG = LoggerFactory.getLogger(WizardIntegration.class);

    private Consumer<ProjectCreationResult> onProjectCreatedCallback;
    private Consumer<String> openProjectCallback;  // Callback to open project in IDE

    /**
     * Create a menu item for creating a new Maven project
     */
    public MenuItem createNewMavenProjectItem() {
        MenuItem item = new MenuItem("New Maven Project...");
        item.setOnAction(e -> showWizard(ProjectType.MAVEN));
        return item;
    }

    /**
     * Create a menu item for creating a new Java Simple project
     */
    public MenuItem createNewJavaSimpleProjectItem() {
        MenuItem item = new MenuItem("New Java Simple Project...");
        item.setOnAction(e -> showWizard(ProjectType.JAVA_SIMPLE));
        return item;
    }

    /**
     * Create a menu item for creating a new Python project
     */
    public MenuItem createNewPythonProjectItem() {
        MenuItem item = new MenuItem("New Python Project...");
        item.setOnAction(e -> showWizard(ProjectType.PYTHON));
        return item;
    }

    /**
     * Show the project creation wizard
     */
    private void showWizard(ProjectType type) {
        LOG.info("Opening project creation wizard for type: {}", type);

        ProjectCreationWizard wizard = new ProjectCreationWizard();
        wizard.setOnProjectCreated(result -> {
            LOG.info("Project created: {}", result);

            // Show success message
            if (result.isSuccess()) {
                showSuccessMessage(result);

                // Call callback if registered
                if (onProjectCreatedCallback != null) {
                    onProjectCreatedCallback.accept(result);
                }

                // Open project if callback is registered
                if (openProjectCallback != null) {
                    openProjectCallback.accept(result.getProjectPath().toString());
                }
            }
        });

        wizard.show();
    }

    /**
     * Show generic wizard (for "New Project" menu item)
     */
    public void showGenericWizard() {
        showWizard(null);  // This will show step 1 (type selection)
    }

    private void showSuccessMessage(ProjectCreationResult result) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Project Created");
        alert.setHeaderText("Project Successfully Created");
        alert.setContentText("Project: " + result.getProjectName() +
                "\nType: " + result.getProjectType() +
                "\nLocation: " + result.getProjectPath() +
                "\n\n" + result.getMessage());
        alert.showAndWait();
    }

    /**
     * Set callback for when project is created
     */
    public void setOnProjectCreated(Consumer<ProjectCreationResult> callback) {
        this.onProjectCreatedCallback = callback;
    }

    /**
     * Set callback for opening a project
     */
    public void setOpenProjectCallback(Consumer<String> callback) {
        this.openProjectCallback = callback;
    }
}
