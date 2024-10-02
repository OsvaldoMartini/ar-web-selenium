package com.allinweb.ch.tests;

import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.socket.WebSocketStompServer;
import com.google.gson.Gson;
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

    private ObservableList<BlockLoopInstructionLoadDTO> blockLoopInstructions;

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
                    e.printStackTrace();
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
                        e.printStackTrace();
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
    private ObservableList<BlockLoopInstructionLoadDTO> getBlockLoopInstructions() {
        return FXCollections.observableArrayList(
                // Block 1 (Default Block)
                new BlockLoopInstructionLoadDTO(
                        11, 1, 1, "SetValue", "Description 1", 1, 1, "Default Block", "SET", 4, "firstName:Osvaldo"),

                // Block 2
                new BlockLoopInstructionLoadDTO(
                        11, 2, 4, "GetValue", "Description 2", 2, 2, "Block Test 2", "GET", 4, "firstName:Osvaldo"),
                new BlockLoopInstructionLoadDTO(
                        11, 3, 3, "Check", "Description 3", 2, 2, "Block Test 2", "CK", 4, "firstName:Osvaldo"),
                new BlockLoopInstructionLoadDTO(
                        11, 4, 2, "Instruction 4", "Description 4", 2, 2, "Block Test 2", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 5, 1, "Instruction 5", "Description 5", 2, 2, "Block Test 2", "click", 0, null),

                // Block 3
                new BlockLoopInstructionLoadDTO(
                        11, 6, 2, "SetValue", "Description 6", 3, 3, "Block Test 3", "SET", 4, "firstName:Osvaldo"),
                new BlockLoopInstructionLoadDTO(
                        11, 7, 1, "GetValue", "Description 7", 3, 3, "Block Test 3", "GET", 4, "firstName:Osvaldo"),

                // Block 4
                new BlockLoopInstructionLoadDTO(
                        11, 8, 1, "Instruction 8", "Description 8", 4, 4, "Block Test 4", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 9, 2, "Check", "Description 9", 4, 4, "Block Test 4", "CK", 4, "firstName:Osvaldo"),
                new BlockLoopInstructionLoadDTO(
                        11, 10, 3, "Instruction 10", "Description 10", 4, 4, "Block Test 4", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 11, 4, "Instruction 11", "Description 11", 4, 4, "Block Test 4", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 12, 5, "Instruction 12", "Description 12", 4, 4, "Block Test 4", "click", 0, null),

                // Block 5
                new BlockLoopInstructionLoadDTO(
                        11, 13, 1, "Instruction 13", "Description 13", 5, 5, "Block Test 5", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 14, 2, "Instruction 14", "Description 14", 5, 5, "Block Test 5", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 15, 3, "Instruction 15", "Description 15", 5, 5, "Block Test 5", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 16, 4, "Instruction 16", "Description 16", 5, 5, "Block Test 5", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 17, 5, "Instruction 17", "Description 17", 5, 5, "Block Test 5", "click", 0, null),

                // Block 6
                new BlockLoopInstructionLoadDTO(
                        11, 18, 1, "Instruction 18", "Description 18", 6, 6, "Block Test 6", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 19, 2, "Instruction 19", "Description 19", 6, 6, "Block Test 6", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 20, 3, "Instruction 20", "Description 20", 6, 6, "Block Test 6", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 21, 4, "Instruction 21", "Description 21", 6, 6, "Block Test 6", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 22, 5, "Instruction 22", "Description 22", 6, 6, "Block Test 6", "click", 0, null),

                // Block 7
                new BlockLoopInstructionLoadDTO(
                        11, 23, 1, "Instruction 23", "Description 23", 7, 7, "Block Test 7", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 24, 2, "Instruction 24", "Description 24", 7, 7, "Block Test 7", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 25, 3, "Instruction 25", "Description 25", 7, 7, "Block Test 7", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 26, 4, "Instruction 26", "Description 26", 7, 7, "Block Test 7", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 27, 5, "Instruction 27", "Description 27", 7, 7, "Block Test 7", "click", 0, null),

                // Block 8
                new BlockLoopInstructionLoadDTO(
                        11, 28, 1, "Instruction 28", "Description 28", 8, 8, "Block Test 8", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 29, 2, "Instruction 29", "Description 29", 8, 8, "Block Test 8", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 30, 3, "Instruction 30", "Description 30", 8, 8, "Block Test 8", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 31, 4, "Instruction 31", "Description 31", 8, 8, "Block Test 8", "click", 0, null),
                new BlockLoopInstructionLoadDTO(
                        11, 32, 5, "Instruction 32", "Description 32", 8, 8, "Block Test 8", "click", 0, null));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
