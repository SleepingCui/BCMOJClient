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

    // UI组件
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
        primaryStage.setTitle("题目评测客户端");
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
        Button runButton = new Button("开始评测");
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
        dbName = new TextField("coding_problems");
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
        TitledPane dbPane = new TitledPane("数据库配置", dbGrid);
        dbPane.setCollapsible(false);
        return dbPane;
    }

    private VBox createInputConfigSection() {
        VBox inputBox = new VBox(5);
        problemInput = new TextField();
        problemInput.setPromptText("请输入题目ID");
        securityCheck = new CheckBox("启用安全检查");
        errorMode = new CheckBox("注入错误配置");
        errorType = new ComboBox<>();
        errorType.getItems().addAll(
                "1 - 缺少 timeLimit",
                "2 - timeLimit 为负数",
                "3 - 缺少 securityCheck",
                "4 - checkpoints 不是对象",
                "5 - checkpoints 缺少 _out",
                "6 - checkpoints 为空对象"
        );
        errorType.getSelectionModel().selectFirst();
        errorType.setDisable(true);
        errorMode.setOnAction(e -> errorType.setDisable(!errorMode.isSelected()));
        inputBox.getChildren().addAll(new Label("题目ID:"), problemInput, securityCheck, errorMode, errorType);

        return inputBox;
    }

    private HBox createFileSelectionSection() {
        HBox fileBox = new HBox(10);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        cppPathDisplay = new TextField();
        cppPathDisplay.setEditable(false);
        cppPathDisplay.setPromptText("请选择C++文件");
        cppPathDisplay.setPrefWidth(400);
        Button chooseButton = new Button("选择 C++ 文件");
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
                new Label("输出日志:"),
                outputBox
        );

        return outputSection;
    }

    private void selectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择 C++ 文件");
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
                    Platform.runLater(() -> log("正在获取题目信息..."));
                    ProblemData problemData = databaseService.getProblemFromDatabase(problemId, dbConfig);

                    Platform.runLater(() -> log(String.format("题目: %s，共 %d 个样例", problemData.problem().get("title"), problemData.examples().size())));
                    String jsonConfig = buildJsonConfig(problemData);
                    Platform.runLater(() -> log("生成的配置 JSON:\n" + jsonConfig));
                    Platform.runLater(() -> log("开始发送文件和配置..."));
                    List<String> responses = networkService.sendAndReceive(
                            cppFile, jsonConfig, serverHost, serverPort,
                            progress -> Platform.runLater(() -> progressBar.setProgress(progress))
                    );
                    Platform.runLater(() -> processResponses(responses));

                } catch (Exception e) {
                    Platform.runLater(() -> log("<错误> " + e.getMessage()));
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
        log("\n=== 评测结果 ===");
        EvaluationResult result = ResponseProcessor.processResponses(responses, resultMapping);
        for (TestCaseResult testCase : result.testResults()) {
            log(String.format("样例 %s: %s - %dms", testCase.index(), testCase.resultText(), testCase.timeUsed()));
        }
        log(String.format("\n总样例数: %d, 通过: %d, 平均用时: %.2fms", result.totalTests(), result.accepted(), result.averageTime()));
    }

    private void log(String message) {
        outputBox.appendText(message + "\n");
    }

    private void showError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setHeaderText(null);
        alert.setContentText("C++ 文件不存在或未选择");
        alert.showAndWait();
    }

}
