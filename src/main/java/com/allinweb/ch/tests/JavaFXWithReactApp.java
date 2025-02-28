package com.allinweb.ch.tests;

import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.socket.WebSocketStompServer;
import com.google.gson.Gson;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javax.websocket.server.ServerContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

public class JavaFXWithReactApp extends Application {
    private Server jettyServer;

    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    // Shutdown the executor when it's no longer needed
    public void shutdown() {
        executorService.shutdown();
    }

    private ObservableList<InstructionLoadDTO> blockLoopInstructions;

    @Override
    public void start(Stage primaryStage) {

        blockLoopInstructions = FXCollections.observableArrayList(getBlockLoopInstructions());

        // Create the WebView
        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();
        webEngine.javaScriptEnabledProperty().set(true);

        Gson gson = new Gson();
        String jsonData = gson.toJson(blockLoopInstructions);

        //        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
        //            if (newState == Worker.State.SUCCEEDED) {
        //                // Expose the JSBridge object to the JavaScript context
        //                JSObject window = (JSObject) webEngine.executeScript("window");
        //                window.setMember("javaBridge", new JSBridge());
        //            }
        //        });

        // Load your React app (ensure your HTML and JS files are correctly loaded)
        webEngine.load(getClass().getResource("/build/index.html").toExternalForm());
        // Alternatively, you can load from a server URL
        // webEngine.load("http://localhost:3000"); // Example URL of a React development server  // Convert the list to
        // JSON

        // Send the JSON data to React
        //  +-

        //        Delay the script execution to ensure the page has fully loaded
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                // After the page has successfully loaded
                try {
                    webEngine.executeScript("setTimeout(function() { window.receiveDataFromJava(JSON.stringify("
                            + jsonData + ")) }, 1000)");
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        });

        //        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
        //            if (newState == Worker.State.SUCCEEDED) {
        //                // Expose the JSBridge object to the JavaScript context
        //                JSObject window = (JSObject) webEngine.executeScript("window");
        //                window.setMember("javaBridge", new JSBridge());
        //
        //                // Schedule a task to interact with JavaScript every 10 seconds
        //                executorService.scheduleAtFixedRate(
        //                        () -> {
        //                            Platform.runLater(() -> {
        //                                // Your JavaScript interaction here
        //                                webEngine.executeScript(
        //                                        "setTimeout(function() { window.receiveDataFromJava(JSON.stringify(" +
        // jsonData
        //                                                + ")) }, 1000)");
        //                            });
        //                        },
        //                        0,
        //                        10,
        //                        TimeUnit.SECONDS); // Executes immediately and repeats every 10 seconds
        //            }
        //        });

        // Create the layout
        BorderPane root = new BorderPane();
        root.setCenter(webView);

        Scene scene = new Scene(root, 800, 600);

        // Set up the stage
        primaryStage.setTitle("JavaFX with React");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Start WebSocket server in a background thread
        new Thread(() -> {
                    try {
                        startWebSocketServer();
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                    }
                })
                .start();
    }

    public void startWebSocketServer() throws Exception {
        // Set up Jetty server to run WebSocket endpoint
        jettyServer = new Server(8080); // Server listens on port 8080
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);

        // Initialize WebSocket container
        ServerContainer wsContainer = WebSocketServerContainerInitializer.configureContext(context);
        //        wsContainer.addEndpoint(SimpleWebSocketServer.class); // Register WebSocket endpoint
        wsContainer.addEndpoint(WebSocketStompServer.class);

        // Start Jetty server
        jettyServer.start();
        System.out.println("WebSocket server started at ws://localhost:8080/websocket");
    }

    @Override
    public void stop() throws Exception {
        // Stop the Jetty server when JavaFX application stops
        if (jettyServer != null && jettyServer.isRunning()) {
            jettyServer.stop();
            jettyServer.join();
        }
        super.stop();
    }

    public class JSBridge {
        public void sendDataToJava(String data) {
            System.out.println("Received data from JavaScript: " + data);
            // Process the data here
        }
    }

    // Sample data with 8 blocks and 5 instructions each
    private ObservableList<InstructionLoadDTO> getBlockLoopInstructions() {
        return FXCollections.observableArrayList(
                // Block 1 (Default Block)
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        1,
                        1,
                        "SetValue",
                        "Description 1",
                        1,
                        1,
                        "Default Block",
                        true,
                        true,
                        3,
                        "SET",
                        4,
                        "firstName:Osvaldo",
                        null),

                // Block 2
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        2,
                        4,
                        "GetValue",
                        "Description 2",
                        2,
                        2,
                        "Block Test 2",
                        true,
                        true,
                        3,
                        "GET",
                        4,
                        "firstName:Osvaldo",
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        3,
                        3,
                        "Check",
                        "Description 3",
                        2,
                        2,
                        "Block Test 2",
                        true,
                        true,
                        3,
                        "CK",
                        4,
                        "firstName:Osvaldo",
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        4,
                        2,
                        "Instruction 4",
                        "Description 4",
                        2,
                        2,
                        "Block Test 2",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        5,
                        1,
                        "Instruction 5",
                        "Description 5",
                        2,
                        2,
                        "Block Test 2",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),

                // Block 3
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        6,
                        2,
                        "SetValue",
                        "Description 6",
                        3,
                        3,
                        "Block Test 3",
                        true,
                        true,
                        3,
                        "SET",
                        4,
                        "firstName:Osvaldo",
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        7,
                        1,
                        "GetValue",
                        "Description 7",
                        3,
                        3,
                        "Block Test 3",
                        true,
                        true,
                        3,
                        "GET",
                        4,
                        "firstName:Osvaldo",
                        null),

                // Block 4
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        8,
                        1,
                        "Instruction 8",
                        "Description 8",
                        4,
                        4,
                        "Block Test 4",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        9,
                        2,
                        "Check",
                        "Description 9",
                        4,
                        4,
                        "Block Test 4",
                        true,
                        true,
                        3,
                        "CK",
                        4,
                        "firstName:Osvaldo",
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        10,
                        3,
                        "Instruction 10",
                        "Description 10",
                        4,
                        4,
                        "Block Test 4",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        11,
                        4,
                        "Instruction 11",
                        "Description 11",
                        4,
                        4,
                        "Block Test 4",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        12,
                        5,
                        "Instruction 12",
                        "Description 12",
                        4,
                        4,
                        "Block Test 4",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),

                // Block 5
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        13,
                        1,
                        "Instruction 13",
                        "Description 13",
                        5,
                        5,
                        "Block Test 5",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        14,
                        2,
                        "Instruction 14",
                        "Description 14",
                        5,
                        5,
                        "Block Test 5",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        15,
                        3,
                        "Instruction 15",
                        "Description 15",
                        5,
                        5,
                        "Block Test 5",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        16,
                        4,
                        "Instruction 16",
                        "Description 16",
                        5,
                        5,
                        "Block Test 5",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        17,
                        5,
                        "Instruction 17",
                        "Description 17",
                        5,
                        5,
                        "Block Test 5",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),

                // Block 6
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        18,
                        1,
                        "Instruction 18",
                        "Description 18",
                        6,
                        6,
                        "Block Test 6",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        19,
                        2,
                        "Instruction 19",
                        "Description 19",
                        6,
                        6,
                        "Block Test 6",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        20,
                        3,
                        "Instruction 20",
                        "Description 20",
                        6,
                        6,
                        "Block Test 6",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        21,
                        4,
                        "Instruction 21",
                        "Description 21",
                        6,
                        6,
                        "Block Test 6",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        22,
                        5,
                        "Instruction 22",
                        "Description 22",
                        6,
                        6,
                        "Block Test 6",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),

                // Block 7
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        23,
                        1,
                        "Instruction 23",
                        "Description 23",
                        7,
                        7,
                        "Block Test 7",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        24,
                        2,
                        "Instruction 24",
                        "Description 24",
                        7,
                        7,
                        "Block Test 7",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        25,
                        3,
                        "Instruction 25",
                        "Description 25",
                        7,
                        7,
                        "Block Test 7",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        26,
                        4,
                        "Instruction 26",
                        "Description 26",
                        7,
                        7,
                        "Block Test 7",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        27,
                        5,
                        "Instruction 27",
                        "Description 27",
                        7,
                        7,
                        "Block Test 7",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),

                // Block 8
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        28,
                        1,
                        "Instruction 28",
                        "Description 28",
                        8,
                        8,
                        "Block Test 8",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        29,
                        2,
                        "Instruction 29",
                        "Description 29",
                        8,
                        8,
                        "Block Test 8",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        30,
                        3,
                        "Instruction 30",
                        "Description 30",
                        8,
                        8,
                        "Block Test 8",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        31,
                        4,
                        "Instruction 31",
                        "Description 31",
                        8,
                        8,
                        "Block Test 8",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null),
                new InstructionLoadDTO(
                        1,
                        11,
                        "JobName",
                        32,
                        5,
                        "Instruction 32",
                        "Description 32",
                        8,
                        8,
                        "Block Test 8",
                        true,
                        true,
                        3,
                        "click",
                        0,
                        null,
                        null));
    }

    public void initializeMainDatabase(String dbUrl, File dbFile) {

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (Statement stmt = conn.createStatement()) {

                // Create home_banking table
                String createHomeBankingTableSQL = "CREATE TABLE home_banking ("
                        + "ID AUTOINCREMENT PRIMARY KEY, "
                        + "url MEMO, "
                        + "name TEXT(255), "
                        + "priority TEXT(255), "
                        + "options_config MEMO, "
                        + "username TEXT(255), "
                        + "password TEXT(255))";
                stmt.executeUpdate(createHomeBankingTableSQL);

                // Create bot_job table
                String createBotJobTableSQL = "CREATE TABLE bot_job ("
                        + "ID AUTOINCREMENT PRIMARY KEY, "
                        + "name TEXT(255) UNIQUE, "
                        + "description TEXT(255), "
                        + "priority TEXT(255), "
                        + "home_banking_id INTEGER)";
                stmt.executeUpdate(createBotJobTableSQL);

                // Add foreign key constraint after table creation
                String addForeignKeySQL = "ALTER TABLE bot_job "
                        + "ADD CONSTRAINT FK_HomeBanking FOREIGN KEY (home_banking_id) "
                        + "REFERENCES home_banking(ID) ON DELETE CASCADE";
                stmt.executeUpdate(addForeignKeySQL);

                // Create block table with a foreign key reference to bot_job
                String createBlockTableSQL = "CREATE TABLE block ("
                        + "ID INTEGER PRIMARY KEY, "
                        + "block_order_number INTEGER NOT NULL, "
                        + "name TEXT NOT NULL, "
                        + "description TEXT, "
                        + "type_id INTEGER, "
                        + "export_file TEXT, "
                        + "active YESNO NOT NULL, "
                        + "wait INTEGER, "
                        + "bot_job_id INTEGER, "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(ID) ON DELETE CASCADE)";
                stmt.executeUpdate(createBlockTableSQL);

                // Create instruction table with foreign key references to block and bot_job
                String createInstructionTableSQL = "CREATE TABLE instruction ("
                        + "ID INTEGER PRIMARY KEY, "
                        + "instruction_order_number INTEGER NOT NULL, "
                        + "actions TEXT, "
                        + "name TEXT, "
                        + "xpath TEXT(10000), "
                        + "coordinates TEXT, "
                        + "force_coordinates YESNO, "
                        + "iframe_xpath TEXT, "
                        + "description TEXT, "
                        + "operation TEXT, "
                        + "optional YESNO, "
                        + "block_marked YESNO, "
                        + "default_value TEXT, "
                        + "action_custom_max_wait_sec INTEGER, "
                        + "on_hold_seconds INTEGER, "
                        + "codified YESNO, "
                        + "export_to_abr YESNO, "
                        + "active YESNO NOT NULL, "
                        + "block_id INTEGER, "
                        + "variable_id INTEGER, "
                        + "parent_id INTEGER, "
                        + "bot_job_id INTEGER, "
                        + "FOREIGN KEY (block_id) REFERENCES block(ID) ON DELETE CASCADE, "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(ID) ON DELETE CASCADE)";
                stmt.executeUpdate(createInstructionTableSQL);

                String createReferenceTableSQL = "CREATE TABLE reference ("
                        + "ID INTEGER PRIMARY KEY, "
                        + "reference_type TEXT, "
                        + "value TEXT, "
                        + "instruction_id INTEGER NOT NULL, "
                        + "bot_job_id INTEGER, "
                        + "FOREIGN KEY (instruction_id) REFERENCES instruction(ID) ON DELETE CASCADE, "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(ID) ON DELETE CASCADE)";
                stmt.executeUpdate(createReferenceTableSQL);

                String createComplexInstructionTableSQL = "CREATE TABLE complex_instruction ("
                        + "ID INTEGER PRIMARY KEY, "
                        + "instruction_id INTEGER, "
                        + "order_number INTEGER NOT NULL, "
                        + "instruction TEXT, "
                        + "way TEXT, "
                        + "bot_job_id INTEGER, "
                        + "FOREIGN KEY (instruction_id) REFERENCES instruction(ID) ON DELETE CASCADE, "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(ID) ON DELETE CASCADE)";
                stmt.executeUpdate(createComplexInstructionTableSQL);

                String createVariableTableSQL = "CREATE TABLE variable ("
                        + "ID INTEGER PRIMARY KEY, "
                        + "type TEXT, "
                        + "name TEXT, "
                        + "value TEXT, "
                        + "instruction_id INTEGER, "
                        + "bot_job_id INTEGER, "
                        + "FOREIGN KEY (instruction_id) REFERENCES instruction(ID) ON DELETE CASCADE, "
                        + "FOREIGN KEY (bot_job_id) REFERENCES bot_job(ID) ON DELETE CASCADE)";
                stmt.executeUpdate(createVariableTableSQL);

                String createConfigurationTableSQL = "CREATE TABLE configuration ("
                        + "ID INTEGER PRIMARY KEY, "
                        + "pathJava TEXT, "
                        + "logLevel TEXT, "
                        + "pathDB TEXT, "
                        + "interactionTimeoutSec TEXT, "
                        + "pathLog TEXT, "
                        + "defaultInstructionStopSeconds TEXT, "
                        + "pathReport TEXT, "
                        + "browser TEXT, "
                        + "dataBaseType TEXT, "
                        + "pageUpdateTimeoutSec TEXT, "
                        + "pathPriority TEXT, "
                        + "pathEngine TEXT, "
                        + "pathExcel TEXT, "
                        + "pathExport TEXT, "
                        + "socketPort TEXT, "
                        + "blockLimit TEXT, "
                        + "pathJavaFx TEXT)";
                stmt.executeUpdate(createConfigurationTableSQL);

                String createComponentBlockTableSQL = "CREATE TABLE component_block ("
                        + "ID INTEGER PRIMARY KEY, "
                        + "home_banking_id INTEGER, "
                        + "bot_job_id INTEGER, "
                        + "block_order_number INTEGER NOT NULL, "
                        + "name TEXT NOT NULL, "
                        + "description TEXT, "
                        + "type_id INTEGER, "
                        + "export_file TEXT, "
                        + "active YESNO, "
                        + "wait INTEGER)";
                stmt.executeUpdate(createComponentBlockTableSQL);

                String createComponentInstructionTableSQL = "CREATE TABLE component_instruction ("
                        + "ID INTEGER PRIMARY KEY, "
                        + "instruction_order_number INTEGER NOT NULL, "
                        + "actions TEXT, "
                        + "name TEXT NOT NULL, "
                        + "path TEXT, "
                        + "coordinates TEXT, "
                        + "force_coordinates YESNO, "
                        + "iframe_xpath TEXT, "
                        + "description TEXT, "
                        + "operation TEXT, "
                        + "optional YESNO, "
                        + "block_marked YESNO, "
                        + "default_value TEXT, "
                        + "action_custom_max_wait_sec INTEGER, "
                        + "on_hold_seconds INTEGER, "
                        + "codified YESNO, "
                        + "export_to_abr YESNO, "
                        + "active YESNO, "
                        + "block_id INTEGER, "
                        + "executed YESNO, "
                        + "priority TEXT, "
                        + "variable_id INTEGER, "
                        + "parent_id INTEGER, "
                        + "bot_job_id INTEGER)";
                stmt.executeUpdate(createComponentInstructionTableSQL);

                String createComponentReferenceTableSQL = "CREATE TABLE component_reference ("
                        + "ID INTEGER PRIMARY KEY, "
                        + "reference_type TEXT, "
                        + "value TEXT, "
                        + "instruction_id INTEGER NOT NULL, "
                        + "bot_job_id INTEGER, "
                        + "FOREIGN KEY (instruction_id) REFERENCES component_instruction(ID) ON DELETE CASCADE)";
                stmt.executeUpdate(createComponentReferenceTableSQL);

                String createComponentVariableTableSQL = "CREATE TABLE component_variable ("
                        + "ID INTEGER PRIMARY KEY, "
                        + "type TEXT, "
                        + "name TEXT, "
                        + "value TEXT, "
                        + "instruction_id INTEGER, "
                        + "bot_job_id INTEGER, "
                        + "FOREIGN KEY (instruction_id) REFERENCES component_instruction(ID) ON DELETE CASCADE)";
                stmt.executeUpdate(createComponentVariableTableSQL);

                String createComponentComplexTableSQL = "CREATE TABLE component_complex ("
                        + "ID INTEGER PRIMARY KEY, "
                        + "instruction_id INTEGER, "
                        + "order_number INTEGER NOT NULL, "
                        + "instruction TEXT, "
                        + "way TEXT, "
                        + "bot_job_id INTEGER, "
                        + "FOREIGN KEY (instruction_id) REFERENCES component_instruction(ID) ON DELETE CASCADE)";
                stmt.executeUpdate(createComponentComplexTableSQL);
            }
            System.out.println(String.format("Database %s has been created!", dbFile.getName()));
        } catch (SQLException error) {
            System.out.println("initializeDatabase\nError: " + error.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
