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
    private static CodingClient instance;

    private TextField dbHost, dbPort, dbUser, dbPass, dbName;
    private TextField problemInput;
    private CheckBox securityCheck, enableO2, errorMode, useNewFormat;
    private ComboBox<String> compareMode, errorType;
    private TextField cppPathDisplay;
    private TextArea outputBox;
    private ProgressBar progressBar;
    private DatabaseConfig dbConfig;
    private Map<Integer, String> resultMapping;
    private DatabaseService databaseService;
    private NetworkService networkService;
    private TextField serverHostField, serverPortField;
    private TextArea customJsonInput;
    private CheckBox useCustomJson;
    private TextArea problemInfoArea;
    private TextField timeoutField;


    @Override
    public void start(Stage primaryStage) {
        instance = this;
        initializeServices();
        initializeResultMapping();
        primaryStage.setTitle("BCMOJ Judge Client");
        primaryStage.setScene(new Scene(createMainLayout(), 896, 815));
        primaryStage.show();
        primaryStage.widthProperty().addListener((obs, oldVal, newVal) -> System.out.println("Window resized: width=" + newVal + ", height=" + primaryStage.getHeight()));
        primaryStage.heightProperty().addListener((obs, oldVal, newVal) -> System.out.println("Window resized: width=" + primaryStage.getWidth() + ", height=" + newVal));
    }

    private void initializeServices() {
        dbConfig = new DatabaseConfig("localhost", 3306, "root", "password", "bcmoj");
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
        resultMapping.put(3, "Memory Limit Exceeded"); // Added MLE status
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
        mainLayout.getChildren().add(createBottomBar());

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

        serverHostField = new TextField("localhost");
        serverPortField = new TextField("12345");
        Button testDbBtn = new Button("Test DB");
        testDbBtn.setOnAction(e -> testDatabaseConnection());
        Button testServerBtn = new Button("Test Server");
        testServerBtn.setOnAction(e -> testServerConnection());

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
        dbGrid.add(new Label("Server IP:"), 0, 3);
        dbGrid.add(serverHostField, 1, 3);
        dbGrid.add(new Label("Server Port:"), 2, 3);
        dbGrid.add(serverPortField, 3, 3);
        dbGrid.add(testDbBtn, 1, 4);
        dbGrid.add(testServerBtn, 3, 4);
        TitledPane dbPane = new TitledPane("Database & Server config", dbGrid);
        dbPane.setCollapsible(false);
        return dbPane;
    }

    private VBox createInputConfigSection() {
        VBox inputBox = new VBox(5);
        problemInput = new TextField();
        problemInput.setPromptText("Problem ID");
        problemInput.textProperty().addListener((obs, oldV, newV) -> {
            if (!newV.trim().isEmpty()) {
                new Thread(this::loadProblemInfo).start();
            }
        });
        securityCheck = new CheckBox("Enable security check");
        enableO2 = new CheckBox("Enable O2 optimization");
        compareMode = new ComboBox<>();
        compareMode.getItems().addAll(
                "1 - Strict match",
                "2 - Ignore spaces",
                "3 - Case insensitive",
                "4 - Float tolerant"
        );
        compareMode.getSelectionModel().selectFirst();
        useNewFormat = new CheckBox("Use new format (v1.0.13-beta or later)");
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
        useCustomJson = new CheckBox("Use custom JSON");
        customJsonInput = new TextArea();
        customJsonInput.setPromptText("Paste your JSON config here...");
        customJsonInput.setPrefRowCount(12);
        customJsonInput.setDisable(true);
        Button chooseJsonButton = new Button("Choose JSON file");
        chooseJsonButton.setDisable(true);
        chooseJsonButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select JSON file");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
            File file = fileChooser.showOpenDialog(null);
            if (file != null && file.exists()) {
                try {
                    String content = Files.readString(file.toPath());
                    customJsonInput.setText(content);
                } catch (Exception ex) {
                    showError("Failed to read JSON file: " + ex.getMessage());
                }
            }
        });
        useCustomJson.setOnAction(e -> {
            boolean useCustom = useCustomJson.isSelected();
            customJsonInput.setDisable(!useCustom);
            chooseJsonButton.setDisable(!useCustom);
            problemInput.setDisable(useCustom);
            securityCheck.setDisable(useCustom);
            enableO2.setDisable(useCustom);
            compareMode.setDisable(useCustom);
            useNewFormat.setDisable(useCustom);
        });
        problemInfoArea = new TextArea();
        problemInfoArea.setEditable(false);
        problemInfoArea.setPrefRowCount(6);
        problemInfoArea.setPromptText("Problem info will appear here...");
        VBox jsonBox = new VBox(5, customJsonInput, chooseJsonButton);
        HBox problemBox = new HBox(10, new VBox(new Label("Problem ID:"), problemInput, securityCheck, enableO2, useNewFormat, new Label("Compare mode:"), compareMode, errorMode, errorType, useCustomJson, jsonBox), problemInfoArea);
        inputBox.getChildren().add(problemBox);
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
        outputSection.getChildren().addAll(new Label("Logs:"), outputBox);
        return outputSection;
    }

    private HBox createBottomBar() {
        HBox bottomBar = new HBox(10);
        bottomBar.setPadding(new Insets(5));
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String javafxVersion = System.getProperty("javafx.version", "Unknown");
        VBox statusBox = new VBox(2);
        Label line1 = new Label("BCMOJ Judge Client");
        Label line2 = new Label("Running on " + osName + "(" + osVersion + ") with Java " + javaVersion + " JavaFX " + javafxVersion);
        statusBox.getChildren().addAll(line1, line2);

        timeoutField = new TextField("200000");
        timeoutField.setPrefWidth(100);
        Label timeoutLabel = new Label("Response Timeout (ms):");
        Button clearLogsBtn = new Button("Clear Logs");
        clearLogsBtn.setOnAction(e -> outputBox.clear());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bottomBar.getChildren().addAll(statusBox, spacer, timeoutLabel, timeoutField, clearLogsBtn);
        return bottomBar;
    }



    private void selectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Cpp file");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("C++ Files", "*.cpp", "*.cc", "*.cxx"), new FileChooser.ExtensionFilter("All Files", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            cppPathDisplay.setText(selectedFile.getAbsolutePath());
        }
    }

    private void runEvaluation() {
        outputBox.clear();
        Task<Void> task = new Task<>() {
            @SuppressWarnings("CallToPrintStackTrace")
            @Override
            protected Void call() {
                try {
                    updateDatabaseConfig();
                    String cppFile = cppPathDisplay.getText();
                    if (cppFile.isEmpty() || !Files.exists(new File(cppFile).toPath())) {
                        Platform.runLater(() -> showError());
                        return null;
                    }
                    String serverIp = serverHostField.getText().trim();
                    int serverPort = Integer.parseInt(serverPortField.getText().trim());
                    String jsonConfig;
                    if (useCustomJson.isSelected()) {
                        final String finalJson = customJsonInput.getText().trim();
                        Platform.runLater(() -> log("Using custom JSON:\n" + finalJson));
                        jsonConfig = finalJson;
                    } else {
                        int problemId = Integer.parseInt(problemInput.getText().trim());
                        ProblemData problemData = databaseService.getProblemFromDatabase(problemId, dbConfig);
                        jsonConfig = buildJsonConfig(problemData);
                    }
                    jsonConfig = JsonConfigBuilder.applyErrorConfig(jsonConfig, errorMode.isSelected(), errorType.getSelectionModel().getSelectedIndex() + 1, useNewFormat.isSelected());
                    final String finalJson = jsonConfig;
                    Platform.runLater(() -> log("Final JSON:\n" + finalJson));
                    int timeout = Integer.parseInt(timeoutField.getText().trim());
                    List<String> responses = networkService.sendAndReceive(cppFile, jsonConfig, serverIp, serverPort, timeout, progress -> Platform.runLater(() -> progressBar.setProgress(progress)));   //100000ms timeout
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
        dbConfig = new DatabaseConfig(dbHost.getText().trim(), Integer.parseInt(dbPort.getText().trim()), dbUser.getText().trim(), dbPass.getText().trim(), dbName.getText().trim());
    }

    private String buildJsonConfig(ProblemData problemData) {
        return JsonConfigBuilder.buildConfig(problemData, securityCheck.isSelected(), enableO2.isSelected(), compareMode.getSelectionModel().getSelectedIndex() + 1, errorMode.isSelected(), errorType.getSelectionModel().getSelectedIndex() + 1, useNewFormat.isSelected());
    }

    private void processResponses(List<String> responses) {
        EvaluationResult result = ResponseProcessor.processResponses(responses, resultMapping);
        for (TestCaseResult testCase : result.testResults()) {
            System.out.println(testCase);
            log(String.format("Checkpoint %s: %s - %sms - %sKB", testCase.index(), testCase.resultText(), testCase.timeUsed(), testCase.memoryUsed()));
        }
        log(String.format("\nTotal: %d, AC: %d, AvgTime: %.2fms, AvgMem: %dKB", result.totalTests(), result.accepted(), result.averageTime(), result.averageMemory()));
    }

    public static void log(String message) {
        if (instance != null) {
            Platform.runLater(() -> instance.outputBox.appendText(message + "\n"));
        }
    }
    private void showError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText("file not exist");
        alert.showAndWait();
    }

    private void testDatabaseConnection() {
        try {
            updateDatabaseConfig();
            databaseService.testConnection(dbConfig);
            showInfo("Database connection successful.");
        } catch (Exception e) {
            showError("Database connection failed: " + e.getMessage());
        }
    }

    private void testServerConnection() {
        try {
            String ip = serverHostField.getText().trim();
            int port = Integer.parseInt(serverPortField.getText().trim());
            networkService.testConnection(ip, port);
            showInfo("Server connection successful.");
        } catch (Exception e) {
            showError("Server connection failed: " + e.getMessage());
        }
    }
    private void loadProblemInfo() {
        try {
            if (problemInput.getText().trim().isEmpty()) return;
            int problemId = Integer.parseInt(problemInput.getText().trim());
            ProblemData problemData = databaseService.getProblemFromDatabase(problemId, dbConfig);
            StringBuilder sb = new StringBuilder();
            sb.append("Title: ").append(problemData.problem().get("title")).append("\n");
            sb.append("Time Limit: ").append(problemData.problem().get("time_limit")).append("ms").append("\n");
            if (!problemData.examples().isEmpty()) {
                Map<String, String> example = problemData.examples().get(0);
                sb.append("Example Input: ").append(example.get("input")).append("\n");
                sb.append("Example Output: ").append(example.get("output")).append("\n");
            }
            String text = sb.toString();
            Platform.runLater(() -> problemInfoArea.setText(text));
        } catch (Exception e) {
            Platform.runLater(() -> problemInfoArea.setText("Error loading problem: " + e.getMessage()));
        }
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

}