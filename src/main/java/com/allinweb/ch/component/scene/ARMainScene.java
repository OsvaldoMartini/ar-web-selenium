package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARMainPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javax.websocket.server.ServerContainer;
import org.eclipse.jetty.server.Server;
import org.openqa.selenium.WebDriver;

public class ARMainScene extends ARScene {

    protected static volatile ARMainScene instance;

    // Private constructor to prevent instantiation
    private ARMainScene() {
        // Initialize if necessary
        super();
    }

    public static ARMainScene getInstance() {
        if (instance == null) {
            synchronized (ARMainScene.class) {
                if (instance == null) {
                    instance = new ARMainScene();
                }
            }
        }
        return instance;
    }

    private Stage modalStage;
    private Scene modalScene;

    private static final Double SCENE_HEIGHT = 600D;
    private static final Double SCENE_WIDTH = 700D;
    private static final String TITLE = "AR Web Bot Job List";

    private static Server jettyServer;
    private static ServerContainer wsContainer;

    private ObservableList<WebDriver> webDriverList = FXCollections.observableArrayList();

    private static final ARMainPane arMainPane;
    private static final ARPropertyManager arPropertyManager;
    //    private static final WebSocketSessionManager webSocketSessionManager;
    //    private static final ARWebSocketServerIP arWebSocketServerIP;
    //    private static final ARWebSocketServer arWebSocketServer;

    static {
        arMainPane = ARMainPane.getInstance();
        arPropertyManager = ARPropertyManager.getInstance();
        //        webSocketSessionManager = WebSocketSessionManager.getInstance();
        //        try {
        //            arWebSocketServerIP = ARWebSocketServerIP.getInstance();
        //        } catch (Exception error) {
        //            ARLogger.getInstance(ARMainScene.class).severe("ARWebSocketServerIP with IP failed " +
        // error.getMessage());
        //
        //            throw new RuntimeException(error);
        //        }
        //        try {
        //            arWebSocketServer = ARWebSocketServer.getInstance();
        //        } catch (Exception error) {
        //            ARLogger.getInstance(ARMainScene.class).severe("ARWebSocketServer NO IP failed " +
        // error.getMessage());
        //            throw new RuntimeException(error);
        //        }
    }

    @Override
    public IARPane buildPane() {
        //        initiateJetty();
        //        arMainPane.initialize(webDriverList);
        return arMainPane;
    }

    @Override
    public Double getSceneHeight() {
        return SCENE_HEIGHT;
    }

    @Override
    public Double getSceneWidth() {
        return SCENE_WIDTH;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public void setStageBehaviour(Stage stage) {
        super.setStageBehaviour(stage); // Call the parent class method

        // Only set the close request handler if it's not already set
        if (!isCloseHandlerSet) {
            stage.setOnCloseRequest(this::handleCloseRequest);
            isCloseHandlerSet = true; // Update the flag to prevent setting it again
        }
    }

    public void showModal() {

        arMainPane.initialize(webDriverList);

        if (modalStage == null) {
            modalStage = new Stage();
            IARPane pane = buildPane();
            if (pane != null) {
                modalScene = new Scene(pane.createPane(), getSceneWidth(), getSceneHeight());
                modalStage.setScene(modalScene);
                modalStage.setTitle(getTitle());
                modalStage.initModality(Modality.WINDOW_MODAL); // Changed to NONE
                modalStage.setAlwaysOnTop(true); // Set always on top
                modalStage.toFront();
                // Reset alwaysOnTop after showing so it behaves normally afterward
                modalStage.setAlwaysOnTop(false);

                // Once shown, reset AlwaysOnTop to false so it behaves normally
                modalStage.setOnShown(event -> {
                    Platform.runLater(() -> modalStage.setAlwaysOnTop(false));
                });

                // Set the onCloseRequest handler for the modal stage
                modalStage.setOnCloseRequest(event -> {
                    System.out.println("Handle Close (Modal Stage): Exiting Threads from Modal");
                    cleanupAndClose(modalStage);
                    event.consume(); // Prevent default close behavior if needed
                });

                //                // Set an event handler for when the window is closed (via the X button)
                //                modalStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                //                    @Override
                //                    public void handle(WindowEvent event) {
                //                        ARMainScene.setConfiguring(false); // Signal that the configuration is done
                //                    }
                //                });
            } else {
                // Handle the case where pane creation failed
                ARLogger.getInstance(ARConfigurationScene.class).severe("Failed to build pane for modal.");
                return;
            }
        }
        modalStage.setTitle(getTitle()); // Update title if it might have changed

        // Check if the stage is already showing
        if (!modalStage.isShowing()) {
            modalStage.showAndWait(); // Show and wait only if not already showing
        }
    }

    private void cleanupAndClose(Stage stage) {
        System.out.println("Cleanup and Close: Exiting Threads");
        // Interrupt running threads
        threadList.forEach(this::interruptThread);
        // Add any other cleanup logic here (e.g., WebDriver quit)
        stage.close();
    }

    private void handleCloseRequest(WindowEvent event) {

        //        stopWebSocketServer();

        System.out.println("Handle Close: Exiting Threads and Quitting WebDriver");

        // Interrupt running threads
        threadList.forEach(this::interruptThread);

        // Close WebDriver if it's initialized
        closeWebDrivers();
    }

    //    private static void initiateJetty() {
    //
    //        int portInitial = 54525;
    //        try (ServerSocket serverSocket = new ServerSocket(0)) { // Port 0 = auto-assign
    //            portInitial = serverSocket.getLocalPort();
    //            System.out.println("Available port: " + portInitial);
    //            arPropertyManager.setProperty(ARPropertyEnum.PORT_SOCKET.getValue(), String.valueOf(portInitial));
    //        } catch (IOException e) {
    //            System.out.println("Fixed Port : " + 54525);
    //            arPropertyManager.setProperty(ARPropertyEnum.PORT_SOCKET.getValue(), String.valueOf(54525));
    //            portInitial = 54525;
    //        }
    //
    //        String portSocket = arPropertyManager.getProperty(ARPropertyEnum.PORT_SOCKET);
    //        if (portSocket != null) {
    //            portInitial = Integer.parseInt(portSocket);
    //        }
    //
    //        // Start WebSocket server in a background thread
    //        int finalPort = portInitial;
    //        new Thread(() -> {
    //                    try {
    //                        startWebSocketServer(finalPort);
    //                    } catch (Exception error) {
    //                        ARLogger.getInstance(ARMainScene.class)
    //                                .severe("Port : " + finalPort + " error : " + error.getMessage());
    //
    //                        //                        performMessage.errorMessage(
    //                        //                                "Port Error", "Port %d already in Use!",
    //                        // String.valueOf(finalPort), null, null, 350);
    //                    }
    //                })
    //                .start();
    //    }

    //    public static void startWebSocketServer(int port) throws Exception {
    //        // Check if the port is available
    //        if (isPortInUse(port)) {
    //            throw new Exception("Port " + port + " is already in use.");
    //        }
    //
    //        // Set up Jetty server to run WebSocket endpoint
    //        jettyServer = new Server(port); // Server listens on port 8080
    //        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    //        context.setContextPath("/");
    //        jettyServer.setHandler(context);
    //
    //        // Initialize WebSocket container
    //        wsContainer = WebSocketServerContainerInitializer.configureContext(context);
    //        wsContainer.setDefaultMaxSessionIdleTimeout(0);
    //        wsContainer.addEndpoint(SimpleWebSocketServer.class); // Register SimpleWebSocketServer
    //
    //        // Start Jetty server
    //        jettyServer.start();
    //        System.out.println("Server started at ws://localhost:" + port + "/websocket");
    //
    //        //        // Example: Retrieve all active sessions
    //        //        activeSessions = simpleWebSocketServer.getAllSessions();
    //        System.out.println(
    //                "Active sessions: " + webSocketSessionManager.getAllSessions().size());
    //    }

    // Method to check if the port is already in use
    //    private static boolean isPortInUse(int port) {
    //        try (ServerSocket serverSocket = new ServerSocket(port)) {
    //            return false; // Port is available
    //        } catch (IOException e) {
    //            return true; // Port is already in use
    //        }
    //    }

    // Method to close all WebDriver instances
    private void closeWebDrivers() {
        for (WebDriver driver : webDriverList) {
            try {
                Platform.runLater(() -> webDriverList.remove(driver));
                Platform.runLater(driver::quit);
                ARLogger.getInstance(ARMainScene.class).info("WebDriver closed.");
            } catch (Exception e) {
                ARLogger.getInstance(ARMainScene.class).warning("Error closing WebDriver: " + e.getMessage());
            }
        }
        Platform.runLater(() -> {
            webDriverList.clear();
            System.exit(0);
        });
    }

    //    public void stopWebSocketServer() {
    //        if (jettyServer != null && jettyServer.isStarted()) {
    //            try {
    //                jettyServer.stop();
    //            } catch (Exception e) {
    //                ARLogger.getInstance(ARMainScene.class).severe("stopWebSocketServer  \nError: " + e.getMessage());
    //            }
    //            jettyServer.destroy();
    //            ARLogger.getInstance(ARMainScene.class).info("WebSocket server stopped.");
    //            //            System.out.println("WebSocket server stopped.");
    //        }
    //    }
}
