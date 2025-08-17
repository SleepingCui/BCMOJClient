package org.bcmoj.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.bcmoj.client.db.DatabaseConfig;
import org.bcmoj.client.db.DatabaseService;
import org.bcmoj.client.net.NetworkService;
import org.bcmoj.client.net.ResponseProcessor;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodingClient extends Application {
    private TextField dbHost, dbPort, dbUser, dbPass, dbName;
    private TextField problemInput;
    private CheckBox securityCheck, errorMode;
    private ComboBox<String> errorType;
    private TextField cppPathDisplay;
    private TextArea outputBox;
    private ProgressBar progressBar;
    private DatabaseConfig dbConfig;
    private final String serverHost = "localhost";
    private final int serverPort = 12345;
    private Map<Integer, String> resultMapping;
    private DatabaseService databaseService;
    private NetworkService networkService;

    @Override
    public void start(Stage primaryStage) {
        initializeServices();
        initializeResultMapping();
        primaryStage.setTitle("Judge Client");
        primaryStage.setScene(new Scene(createMainLayout(), 850, 650));
        primaryStage.show();
    }

    private void initializeServices() {
        dbConfig = new DatabaseConfig("localhost", 3306, "root", "password", "coding_problems");
        databaseService = new DatabaseService();
        networkService = new NetworkService();
        initializeResultMapping();
    }

    private void initializeResultMapping() {
        resultMapping = new HashMap<>();
        resultMapping.put(-5, "Security Check Failed");
        resultMapping.put(-4, "Compile Error");
        resultMapping.put(-3, "Wrong Answer");
        resultMapping.put(2, "Real Time Limit Exceeded");
        resultMapping.put(4, "Runtime Error");
        resultMapping.put(5, "System Error");
        resultMapping.put(1, "Accepted");
    }

    private VBox createMainLayout() {
        VBox mainLayout = new VBox(10);
        mainLayout.setPadding(new Insets(10));
        mainLayout.getChildren().add(createDatabaseConfigSection());
        mainLayout.getChildren().add(createInputConfigSection());
        mainLayout.getChildren().add(createFileSelectionSection());
        Button runButton = new Button("Start");
        runButton.setOnAction(e -> runEvaluation());
        mainLayout.getChildren().add(runButton);
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        mainLayout.getChildren().add(progressBar);
        mainLayout.getChildren().add(createOutputSection());

        return mainLayout;
    }

    private TitledPane createDatabaseConfigSection() {
        GridPane dbGrid = new GridPane();
        dbGrid.setHgap(10);
        dbGrid.setVgap(5);
        dbHost = new TextField("localhost");
        dbPort = new TextField("3306");
        dbUser = new TextField("root");
        dbPass = new PasswordField();
        dbPass.setText("password");
        dbName = new TextField("bcmoj");
        dbGrid.add(new Label("Host:"), 0, 0);
        dbGrid.add(dbHost, 1, 0);
        dbGrid.add(new Label("Port:"), 2, 0);
        dbGrid.add(dbPort, 3, 0);
        dbGrid.add(new Label("User:"), 0, 1);
        dbGrid.add(dbUser, 1, 1);
        dbGrid.add(new Label("Password:"), 2, 1);
        dbGrid.add(dbPass, 3, 1);
        dbGrid.add(new Label("Database:"), 0, 2);
        dbGrid.add(dbName, 1, 2);
        TitledPane dbPane = new TitledPane("Database config", dbGrid);
        dbPane.setCollapsible(false);
        return dbPane;
    }

    private VBox createInputConfigSection() {
        VBox inputBox = new VBox(5);
        problemInput = new TextField();
        problemInput.setPromptText("Problem ID");
        securityCheck = new CheckBox("Enable security check");
        errorMode = new CheckBox("Insert error config");
        errorType = new ComboBox<>();
        errorType.getItems().addAll(
                "1 - No timeLimit",
                "2 - timeLimit is a negative number",
                "3 - No securityCheck",
                "4 - Checkpoints not a object",
                "5 - No _out",
                "6 - Empty object"
        );
        errorType.getSelectionModel().selectFirst();
        errorType.setDisable(true);
        errorMode.setOnAction(e -> errorType.setDisable(!errorMode.isSelected()));
        inputBox.getChildren().addAll(new Label("Problem ID:"), problemInput, securityCheck, errorMode, errorType);

        return inputBox;
    }

    private HBox createFileSelectionSection() {
        HBox fileBox = new HBox(10);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        cppPathDisplay = new TextField();
        cppPathDisplay.setEditable(false);
        cppPathDisplay.setPromptText("Choose Cpp file");
        cppPathDisplay.setPrefWidth(400);
        Button chooseButton = new Button("Choose Cpp file");
        chooseButton.setOnAction(e -> selectFile());
        fileBox.getChildren().addAll(cppPathDisplay, chooseButton);
        return fileBox;
    }

    private VBox createOutputSection() {
        VBox outputSection = new VBox(5);
        outputBox = new TextArea();
        outputBox.setEditable(false);
        outputBox.setPrefRowCount(15);
        outputSection.getChildren().addAll(
                new Label("Logs:"),
                outputBox
        );
        return outputSection;
    }

    private void selectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Cpp file");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("C++ Files", "*.cpp", "*.cc", "*.cxx"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            cppPathDisplay.setText(selectedFile.getAbsolutePath());
        }
    }

    private void runEvaluation() {
        Task<Void> task = new Task<>() {
            @SuppressWarnings("CallToPrintStackTrace")
            @Override
            protected Void call() {
                try {
                    updateDatabaseConfig();
                    int problemId = Integer.parseInt(problemInput.getText().trim());
                    String cppFile = cppPathDisplay.getText();

                    if (cppFile.isEmpty() || !Files.exists(new File(cppFile).toPath())) {
                        Platform.runLater(() -> showError());
                        return null;
                    }
                    Platform.runLater(() -> log("Getting problem info..."));
                    ProblemData problemData = databaseService.getProblemFromDatabase(problemId, dbConfig);

                    Platform.runLater(() -> log(String.format("Problem: %s, Examples: %d", problemData.problem().get("title"), problemData.examples().size())));
                    String jsonConfig = buildJsonConfig(problemData);
                    Platform.runLater(() -> log("Config:\n" + jsonConfig));
                    Platform.runLater(() -> log("Sending data"));
                    List<String> responses = networkService.sendAndReceive(cppFile, jsonConfig, serverHost, serverPort, progress -> Platform.runLater(() -> progressBar.setProgress(progress)));
                    Platform.runLater(() -> processResponses(responses));

                } catch (Exception e) {
                    Platform.runLater(() -> log("<Error> " + e.getMessage()));
                    e.printStackTrace();
                }
                return null;
            }
        };

        new Thread(task).start();
    }

    private void updateDatabaseConfig() {
        dbConfig = new DatabaseConfig(dbHost.getText().trim(), Integer.parseInt(dbPort.getText().trim()), dbUser.getText().trim(), dbPass.getText().trim(), dbName.getText().trim()
        );
    }

    private String buildJsonConfig(ProblemData problemData) {
        return JsonConfigBuilder.buildConfig(problemData, securityCheck.isSelected(), errorMode.isSelected(), errorType.getSelectionModel().getSelectedIndex() + 1
        );
    }

    private void processResponses(List<String> responses) {
        EvaluationResult result = ResponseProcessor.processResponses(responses, resultMapping);
        for (TestCaseResult testCase : result.testResults()) {
            log(String.format("Example %s: %s - %dms", testCase.index(), testCase.resultText(), testCase.timeUsed()));
        }
        log(String.format("\nTotalExamples: %d, AC: %d, AvgTime: %.2fms", result.totalTests(), result.accepted(), result.averageTime()));
    }

    private void log(String message) {
        outputBox.appendText(message + "\n");
    }

    private void showError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText("file not exist");
        alert.showAndWait();
    }

}
