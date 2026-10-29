package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.javafx_client.model.ProjectCreationRequest;
import com.distributed_ide.javafx_client.model.ProjectCreationResult;
import com.distributed_ide.javafx_client.model.ProjectType;
import com.distributed_ide.javafx_client.service.ProjectCreationService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

/**
 * Assistant de création de projet professionnel.
 * Étape 1: Sélectionner le type de projet
 * Étape 2: Configurer les détails du projet
 * Étape 3: Réviser et créer
 */
public class ProjectCreationWizard extends Stage {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectCreationWizard.class);
    private static final ProjectCreationService CREATION_SERVICE = new ProjectCreationService();

    private int currentStep = 1;
    private ProjectType selectedType = null;  // Commence nullable
    private Path parentDirectory = Paths.get(System.getProperty("user.home")).resolve("Projets");

    // Données du wizard
    private String projectName = "";
    private String packageName = "com.example.app";
    private String mainClassName = "App";
    private String jdkVersion = "21";
    private String pythonVersion = "3.12";
    private boolean createVenv = false;
    private boolean createMainFile = true;
    private String groupId = "com.example";
    private String version = "1.0-SNAPSHOT";

    // Composants UI
    private final Label stepLabel = new Label();
    private final Label descriptionLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar();
    private final VBox contentArea = new VBox(15);
    private final Button backButton = new Button("← Précédent");
    private final Button nextButton = new Button("Suivant →");
    private final Button cancelButton = new Button("Annuler");

    // Callback
    private Consumer<ProjectCreationResult> onProjectCreated;

    public ProjectCreationWizard() {
        setTitle("Nouveau Projet");
        setWidth(900);
        setHeight(700);
        setResizable(false);
        setOnCloseRequest(e -> cancel());

        BorderPane root = createRootLayout();
        Scene scene = new Scene(root);
        setScene(scene);

        try {
            if (!Files.exists(parentDirectory)) {
                Files.createDirectories(parentDirectory);
            }
        } catch (Exception e) {
            LOG.warn("Impossible de créer le dossier des projets: {}", e.getMessage());
            parentDirectory = Paths.get(System.getProperty("user.home"));
        }
    }

    private BorderPane createRootLayout() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-padding: 0;");

        root.setTop(createHeader());

        contentArea.setPadding(new Insets(30));
        contentArea.setStyle("-fx-border-color: #f0f0f0; -fx-border-width: 1 0 1 0;");
        ScrollPane scrollPane = new ScrollPane(contentArea);
        scrollPane.setFitToWidth(true);
        root.setCenter(scrollPane);

        root.setBottom(createButtonBar());

        showStep1();
        return root;
    }

    private VBox createHeader() {
        VBox header = new VBox(8);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        stepLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #333;");
        descriptionLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666; -fx-wrap-text: true;");
        descriptionLabel.setWrapText(true);

        progressBar.setPrefWidth(400);
        progressBar.setStyle("-fx-padding: 5 0 0 0;");

        header.getChildren().addAll(stepLabel, descriptionLabel, progressBar);
        return header;
    }

    private HBox createButtonBar() {
        HBox buttonBar = new HBox(10);
        buttonBar.setPadding(new Insets(20));
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0; -fx-background-color: #f8f9fa;");

        backButton.setPrefWidth(110);
        nextButton.setPrefWidth(110);
        cancelButton.setPrefWidth(110);

        backButton.setStyle("-fx-font-size: 11;");
        nextButton.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");
        cancelButton.setStyle("-fx-font-size: 11;");

        backButton.setOnAction(e -> previousStep());
        nextButton.setOnAction(e -> nextStep());
        cancelButton.setOnAction(e -> cancel());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        buttonBar.getChildren().addAll(spacer, backButton, nextButton, cancelButton);
        return buttonBar;
    }

    private void showStep1() {
        currentStep = 1;
        stepLabel.setText("Étape 1 de 3 : Type de Projet");
        descriptionLabel.setText("Sélectionnez le type de projet que vous souhaitez créer.");
        progressBar.setProgress(1.0 / 3.0);

        contentArea.getChildren().clear();
        ToggleGroup typeGroup = new ToggleGroup();

        // Section Java
        VBox javaSection = createTypeSection("☕ PROJETS JAVA", typeGroup);

        RadioButton simpleJavaRadio = new RadioButton("  Projet Java Simple");
        simpleJavaRadio.setToggleGroup(typeGroup);
        simpleJavaRadio.setStyle("-fx-font-size: 12; -fx-padding: 5;");
        simpleJavaRadio.selectedProperty().addListener((obs, old, selected) -> {
            if (selected) selectedType = ProjectType.JAVA_SIMPLE;
            updateButtons();
        });

        Label simpleDesc = new Label("Structure basique : src/, bin/, lib/, ExJavaConfig/\nIdéal pour : Apprentissage, scripts simples, petits projets");
        simpleDesc.setStyle("-fx-font-size: 11; -fx-text-fill: #666; -fx-padding: 0 0 0 30;");

        RadioButton mavenRadio = new RadioButton("  Projet Maven Java");
        mavenRadio.setToggleGroup(typeGroup);
        mavenRadio.setStyle("-fx-font-size: 12; -fx-padding: 5;");
        mavenRadio.selectedProperty().addListener((obs, old, selected) -> {
            if (selected) selectedType = ProjectType.MAVEN;
            updateButtons();
        });

        Label mavenDesc = new Label("Système de build professionnel avec pom.xml\nIdéal pour : Applications d'entreprise, bibliothèques, builds complexes");
        mavenDesc.setStyle("-fx-font-size: 11; -fx-text-fill: #666; -fx-padding: 0 0 0 30;");

        javaSection.getChildren().addAll(
                simpleJavaRadio, simpleDesc,
                new Separator(),
                mavenRadio, mavenDesc
        );

        // Section Python
        VBox pythonSection = createTypeSection("🐍 PROJETS PYTHON", typeGroup);

        RadioButton pythonRadio = new RadioButton("  Projet Python");
        pythonRadio.setToggleGroup(typeGroup);
        pythonRadio.setStyle("-fx-font-size: 12; -fx-padding: 5;");
        pythonRadio.selectedProperty().addListener((obs, old, selected) -> {
            if (selected) selectedType = ProjectType.PYTHON;
            updateButtons();
        });

        Label pythonDesc = new Label("Support de l'environnement virtuel\nIdéal pour : Scripts Python, applications Python");
        pythonDesc.setStyle("-fx-font-size: 11; -fx-text-fill: #666; -fx-padding: 0 0 0 30;");

        pythonSection.getChildren().addAll(pythonRadio, pythonDesc);

        contentArea.getChildren().addAll(javaSection, pythonSection);
        updateButtons();
    }

    private VBox createTypeSection(String title, ToggleGroup group) {
        VBox section = new VBox(8);
        section.setStyle("-fx-border-color: #e8e8e8; -fx-border-width: 1; -fx-padding: 15; -fx-border-radius: 5;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #333;");

        section.getChildren().add(titleLabel);
        return section;
    }

    private void showStep2() {
        currentStep = 2;
        stepLabel.setText("Étape 2 de 3 : Configuration du Projet");
        descriptionLabel.setText("Entrez les détails de votre projet.");
        progressBar.setProgress(2.0 / 3.0);

        contentArea.getChildren().clear();

        String typeInfo = switch (selectedType) {
            case MAVEN -> "📁 Projet Maven - Système de build professionnel";
            case JAVA_SIMPLE -> "📁 Projet Java Simple - Structure de base";
            case PYTHON -> "📁 Projet Python - Environnement virtuel supporté";
            default -> "";
        };

        Label typeInfoLabel = new Label(typeInfo);
        typeInfoLabel.setStyle("-fx-padding: 10; -fx-background-color: #e3f2fd; -fx-border-color: #64b5f6; -fx-border-width: 1; -fx-text-fill: #1976d2; -fx-border-radius: 3;");
        contentArea.getChildren().add(typeInfoLabel);

        GridPane commonGrid = createGridPane();
        addGridRow(commonGrid, 0, "Nom du projet:", createTextField(projectName, text -> projectName = text));
        addGridRow(commonGrid, 1, "Répertoire parent:", createDirectorySelector(parentDirectory, path -> parentDirectory = path));

        contentArea.getChildren().addAll(
                new Label("Informations Générales"),
                commonGrid,
                new Separator()
        );

        if (selectedType == ProjectType.MAVEN) {
            GridPane mavenGrid = createGridPane();
            addGridRow(mavenGrid, 0, "Nom du package:", createTextField(packageName, text -> packageName = text));
            addGridRow(mavenGrid, 1, "Group ID:", createTextField(groupId, text -> groupId = text));
            addGridRow(mavenGrid, 2, "Classe principale:", createTextField(mainClassName, text -> mainClassName = text));
            addGridRow(mavenGrid, 3, "Version JDK:", createComboBox(new String[]{"11", "17", "21"}, jdkVersion, text -> jdkVersion = text));

            Label helpLabel = new Label("💡 Le nom du package doit être au format : com.votreentreprise.appname");
            helpLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");

            contentArea.getChildren().addAll(
                    new Label("Configuration Maven"),
                    mavenGrid,
                    helpLabel
            );

        } else if (selectedType == ProjectType.JAVA_SIMPLE) {
            GridPane javaGrid = createGridPane();
            addGridRow(javaGrid, 0, "Nom du package:", createTextField(packageName, text -> packageName = text));
            addGridRow(javaGrid, 1, "Classe principale:", createTextField(mainClassName, text -> mainClassName = text));

            Label helpLabel = new Label("💡 Dossiers créés :\n  • src/  - Fichiers source Java\n  • bin/  - Classes compilées\n  • lib/  - Bibliothèques (JAR)");
            helpLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");

            contentArea.getChildren().addAll(
                    new Label("Configuration Java"),
                    javaGrid,
                    helpLabel
            );

        } else if (selectedType == ProjectType.PYTHON) {
            GridPane pythonGrid = createGridPane();
            addGridRow(pythonGrid, 0, "Version Python:", createComboBox(new String[]{"3.8", "3.9", "3.10", "3.11", "3.12"}, pythonVersion, text -> pythonVersion = text));

            CheckBox venvCheckBox = new CheckBox("Créer un environnement virtuel");
            venvCheckBox.setSelected(createVenv);
            venvCheckBox.selectedProperty().addListener((obs, old, selected) -> createVenv = selected);

            contentArea.getChildren().addAll(
                    new Label("Configuration Python"),
                    pythonGrid,
                    venvCheckBox
            );
        }

        contentArea.getChildren().add(new Separator());
        CheckBox mainFileCheckBox = new CheckBox("Créer un fichier main");
        mainFileCheckBox.setSelected(createMainFile);
        mainFileCheckBox.selectedProperty().addListener((obs, old, selected) -> createMainFile = selected);
        contentArea.getChildren().add(mainFileCheckBox);

        updateButtons();
    }

    private GridPane createGridPane() {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(10));
        return grid;
    }

    private void addGridRow(GridPane grid, int row, String label, Node control) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-min-width: 150;");
        grid.add(labelNode, 0, row);
        grid.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
    }

    private TextField createTextField(String defaultValue, Consumer<String> onChanged) {
        TextField field = new TextField(defaultValue);
        field.textProperty().addListener((obs, old, value) -> onChanged.accept(value));
        field.setPrefHeight(35);
        return field;
    }

    private HBox createDirectorySelector(Path path, Consumer<Path> onChanged) {
        HBox box = new HBox(10);
        TextField field = new TextField(path.toString());
        field.setEditable(false);
        field.setPrefHeight(35);

        Button browseButton = new Button("Parcourir...");
        browseButton.setPrefWidth(100);
        browseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Sélectionner un répertoire");
            chooser.setInitialDirectory(path.toFile());
            File selectedDir = chooser.showDialog(getScene().getWindow());
            if (selectedDir != null) {
                Path selectedPath = selectedDir.toPath();
                field.setText(selectedPath.toString());
                parentDirectory = selectedPath;
                onChanged.accept(selectedPath);
            }
        });

        HBox.setHgrow(field, Priority.ALWAYS);
        box.getChildren().addAll(field, browseButton);
        return box;
    }

    private ComboBox<String> createComboBox(String[] options, String defaultValue, Consumer<String> onChanged) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll(options);
        combo.setValue(defaultValue);
        combo.setPrefHeight(35);
        combo.valueProperty().addListener((obs, old, value) -> {
            if (value != null) onChanged.accept(value);
        });
        return combo;
    }

    private void showStep3() {
        currentStep = 3;
        stepLabel.setText("Étape 3 de 3 : Révision et Création");
        descriptionLabel.setText("Vérifiez vos paramètres avant de créer le projet.");
        progressBar.setProgress(3.0 / 3.0);

        contentArea.getChildren().clear();

        String typeEmoji = switch (selectedType) {
            case MAVEN -> "☕";
            case JAVA_SIMPLE -> "📝";
            case PYTHON -> "🐍";
            default -> "📦";
        };

        Label typeLabel = new Label(typeEmoji + " " + selectedType);
        typeLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #0066cc;");
        contentArea.getChildren().add(typeLabel);

        VBox infoSection = createSummarySection("📋 Informations du Projet");
        infoSection.getChildren().add(createSummaryRow("Type:", selectedType.toString()));
        infoSection.getChildren().add(createSummaryRow("Nom:", projectName));
        infoSection.getChildren().add(createSummaryRow("Emplacement:", parentDirectory.resolve(projectName).toString()));
        contentArea.getChildren().add(infoSection);

        VBox configSection = createSummarySection("⚙️ Configuration");
        if (selectedType == ProjectType.MAVEN) {
            configSection.getChildren().add(createSummaryRow("Package:", packageName));
            configSection.getChildren().add(createSummaryRow("Group ID:", groupId));
            configSection.getChildren().add(createSummaryRow("Classe principale:", mainClassName));
            configSection.getChildren().add(createSummaryRow("Version JDK:", jdkVersion));
        } else if (selectedType == ProjectType.JAVA_SIMPLE) {
            configSection.getChildren().add(createSummaryRow("Package:", packageName));
            configSection.getChildren().add(createSummaryRow("Classe principale:", mainClassName));
        } else if (selectedType == ProjectType.PYTHON) {
            configSection.getChildren().add(createSummaryRow("Version Python:", pythonVersion));
            configSection.getChildren().add(createSummaryRow("Env. virtuel:", createVenv ? "Oui" : "Non"));
        }
        contentArea.getChildren().add(configSection);

        VBox structSection = createSummarySection("📁 Structure des Dossiers");
        String structure = buildFolderStructure();
        Label structLabel = new Label(structure);
        structLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 10;");
        structSection.getChildren().add(structLabel);
        contentArea.getChildren().add(structSection);

        updateButtons();
    }

    private VBox createSummarySection(String title) {
        VBox section = new VBox(5);
        section.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 1; -fx-padding: 10; -fx-border-radius: 3; -fx-spacing: 5;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");
        section.getChildren().add(titleLabel);

        return section;
    }

    private HBox createSummaryRow(String label, String value) {
        HBox row = new HBox(10);
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-font-weight: bold; -fx-min-width: 120;");
        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-text-fill: #333;");
        valueNode.setWrapText(true);
        row.getChildren().addAll(labelNode, valueNode);
        return row;
    }

    private String buildFolderStructure() {
        return switch (selectedType) {
            case MAVEN -> projectName + "/\n" +
                    "├── pom.xml\n" +
                    "├── src/\n" +
                    "│   ├── main/java/\n" +
                    "│   └── test/java/\n" +
                    "├── target/\n" +
                    "└── README.md";
            case JAVA_SIMPLE -> projectName + "/\n" +
                    "├── src/\n" +
                    "├── bin/\n" +
                    "├── lib/\n" +
                    "├── ExJavaConfig/\n" +
                    "└── README.md";
            case PYTHON -> projectName + "/\n" +
                    "├── src/\n" +
                    (createVenv ? "├── venv/\n" : "") +
                    "└── README.md";
            default -> "";
        };
    }

    private void nextStep() {
        if (currentStep == 1) {
            if (selectedType == null) {
                showError("Sélection requise", "Veuillez sélectionner un type de projet.");
                return;
            }
            showStep2();
        } else if (currentStep == 2) {
            if (!validateStep2()) {
                return;
            }
            showStep3();
        } else if (currentStep == 3) {
            createProject();
        }
    }

    private void previousStep() {
        if (currentStep == 2) {
            showStep1();
        } else if (currentStep == 3) {
            showStep2();
        }
    }

    private boolean validateStep2() {
        if (projectName == null || projectName.isBlank()) {
            showError("Validation", "Le nom du projet est requis.");
            return false;
        }

        if (projectName.contains(" ")) {
            showError("Validation", "Le nom du projet ne doit pas contenir d'espaces.");
            return false;
        }

        if (selectedType == ProjectType.MAVEN || selectedType == ProjectType.JAVA_SIMPLE) {
            if (packageName == null || packageName.isBlank()) {
                showError("Validation", "Le nom du package est requis.");
                return false;
            }
            if (mainClassName == null || mainClassName.isBlank()) {
                showError("Validation", "Le nom de la classe principale est requis.");
                return false;
            }
        }

        return true;
    }

    private void createProject() {
        ProjectCreationRequest request = new ProjectCreationRequest.Builder()
                .projectType(selectedType)
                .projectName(projectName)
                .parentDirectory(parentDirectory)
                .packageName(packageName)
                .mainClassName(mainClassName)
                .jdkVersion(jdkVersion)
                .pythonVersion(pythonVersion)
                .createVenv(createVenv)
                .createMainFile(createMainFile)
                .groupId(groupId != null ? groupId : packageName)
                .version(version)
                .build();

        disableControls();

        CREATION_SERVICE.createProjectAsync(request)
                .thenAccept(result -> {
                    Platform.runLater(() -> {
                        if (result.isSuccess()) {
                            if (onProjectCreated != null) {
                                onProjectCreated.accept(result);
                            }
                            close();
                        } else {
                            showError("Erreur", result.getMessage());
                            enableControls();
                        }
                    });
                })
                .exceptionally(e -> {
                    Platform.runLater(() -> {
                        showError("Erreur", "Impossible de créer le projet: " + e.getMessage());
                        enableControls();
                    });
                    return null;
                });
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void cancel() {
        close();
    }

    private void updateButtons() {
        backButton.setDisable(currentStep == 1);
        nextButton.setText(currentStep == 3 ? "Créer" : "Suivant →");
        cancelButton.setDisable(false);

        if (currentStep == 1) {
            nextButton.setDisable(selectedType == null);
        } else {
            nextButton.setDisable(false);
        }
    }

    private void disableControls() {
        backButton.setDisable(true);
        nextButton.setDisable(true);
        cancelButton.setDisable(true);
    }

    private void enableControls() {
        updateButtons();
    }

    public void setOnProjectCreated(Consumer<ProjectCreationResult> callback) {
        this.onProjectCreated = callback;
    }
}
