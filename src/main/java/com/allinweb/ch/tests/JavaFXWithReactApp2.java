package com.allinweb.ch.tests;

import com.allinweb.ch.component.model.BlockLoopInstructionLoadDTO;
import com.allinweb.ch.socket.WebSocketStompServer;
import com.google.gson.Gson;
import java.awt.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javax.websocket.server.ServerContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

public class JavaFXWithReactApp2 extends Application {
    private Server jettyServer;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private ObservableList<BlockLoopInstructionLoadDTO> blockLoopInstructions;
    private WebEngine webEngine;
    private int currentIndex = 0; // Keep track of the current element

    @Override
    public void start(Stage primaryStage) {
        blockLoopInstructions = FXCollections.observableArrayList(getBlockLoopInstructions());

        // Create the WebView and WebEngine
        WebView webView = new WebView();
        webEngine = webView.getEngine();
        webEngine.javaScriptEnabledProperty().set(true);

        Gson gson = new Gson();
        String jsonData = gson.toJson(blockLoopInstructions);

        // Load your React app (ensure your HTML and JS files are correctly loaded)
        webEngine.load(getClass().getResource("/build/index.html").toExternalForm());

        // Send the JSON data to React
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                // After the page has successfully loaded
                try {
                    // Inject the CSS for navigable and highlighted elements
                    String css = ".navigable {\n" + "    padding: 5px;\n"
                            + "    margin: 10px;\n"
                            + "}\n"
                            + ".highlight {\n"
                            + "    background-color: yellow;\n"
                            + "    border: 2px solid red;\n"
                            + "}";

                    // Inject CSS into the page
                    webEngine.executeScript("var style = document.createElement('style');" + "style.type = 'text/css';"
                            + "style.innerHTML = `"
                            + css + "`;" + "document.head.appendChild(style);");

                    // Inject the JavaScript for element navigation
                    String script = "let currentIndex = 0;\n" + "function navigateElement(index) {\n"
                            + "    const elements = document.querySelectorAll('.navigable');\n"
                            + "    if (elements.length > 0) {\n"
                            + "        index = Math.max(0, Math.min(index, elements.length - 1));\n"
                            + "        const element = elements[index];\n"
                            + "        element.scrollIntoView({ behavior: 'smooth' });\n"
                            + "        element.classList.add('highlight');\n"
                            + "    }\n"
                            + "}\n"
                            + "function goToNextItem() {\n"
                            + "    currentIndex++;\n"
                            + "    navigateElement(currentIndex);\n"
                            + "}\n"
                            + "function goToPreviousItem() {\n"
                            + "    currentIndex--;\n"
                            + "    navigateElement(currentIndex);\n"
                            + "}\n";

                    // Execute the JavaScript injection after the page loads
                    webEngine.executeScript(script);

                    // Send data to React
                    webEngine.executeScript("setTimeout(function() { window.receiveDataFromJava(JSON.stringify("
                            + jsonData + ")) }, 1000)");

                    // Initialize navigation
                    initializeNavigation();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        });

        // Create Previous and Next buttons
        Button previousButton = new Button("Previous");
        Button nextButton = new Button("Next");

        // Event handler for Previous button
        previousButton.setOnAction(event -> {
            // Call a JavaScript function to go to the previous item
            try {
                webEngine.executeScript("goToPreviousItem()");
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        });

        // Event handler for Next button
        nextButton.setOnAction(event -> {
            // Call a JavaScript function to go to the next item
            try {
                webEngine.executeScript("goToNextItem()");
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        });

        // Create a horizontal box for buttons
        HBox buttonBox = new HBox(10, previousButton, nextButton);

        // Create the layout
        BorderPane root = new BorderPane();
        root.setTop(buttonBox); // Place buttons at the top
        root.setCenter(webView);

        Scene scene = new Scene(root, 800, 600);
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
        jettyServer = new Server(8080);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);

        // Initialize WebSocket container
        ServerContainer wsContainer = WebSocketServerContainerInitializer.configureContext(context);
        wsContainer.addEndpoint(WebSocketStompServer.class);

        jettyServer.start();
        System.out.println("WebSocket server started at ws://localhost:8080/websocket");
    }

    @Override
    public void stop() throws Exception {
        if (jettyServer != null && jettyServer.isRunning()) {
            jettyServer.stop();
            jettyServer.join();
        }
        super.stop();
    }

    private void initializeNavigation() {
        // Reset or initialize the current index if needed
        currentIndex = 0; // For example, start from the first element
    }

    private void navigatePrevious() {
        // Decrease the current index and execute the JavaScript to navigate to the previous element
        if (currentIndex > 0) {
            currentIndex--;
            executeJavaScriptToNavigate(currentIndex);
        }
    }

    private void navigateNext() {
        // Increase the current index and execute the JavaScript to navigate to the next element
        currentIndex++;
        executeJavaScriptToNavigate(currentIndex);
    }

    private void executeJavaScriptToNavigate(int index) {
        try {
            // Execute the JavaScript to navigate the page, such as scrolling or changing elements
            String script = "window.navigateElement(" + index
                    + ");"; // Assuming you have a function in the HTML page called `navigateElement`
            webEngine.executeScript(script);
        } catch (Exception e) {
            System.err.println("Error executing JavaScript: " + e.getMessage());
        }
    }

    // Sample data with 8 blocks and 5 instructions each

    // Sample data with 8 blocks and 5 instructions each
    private ObservableList<BlockLoopInstructionLoadDTO> getBlockLoopInstructions() {
        return FXCollections.observableArrayList(
                // Block 1 (Default Block)
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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
                new BlockLoopInstructionLoadDTO(
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

    public static void main(String[] args) {
        launch(args);
    }
}
