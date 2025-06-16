package com.allinweb.ch.component.scene;

import com.allinweb.ch.component.pane.ARMainPane;
import com.allinweb.ch.component.pane.base.IARPane;
import com.allinweb.ch.component.scene.base.ARScene;
import com.allinweb.ch.socket.*;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.IOException;
import java.net.ServerSocket;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javax.websocket.server.ServerContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.openqa.selenium.WebDriver;

public class ARMainScene extends ARScene {

  private static final Double SCENE_HEIGHT = 600D;
  private static final Double SCENE_WIDTH = 700D;
  private static final String TITLE = "AR Web Bot Job List";

  private static Server jettyServer;
  private static ServerContainer wsContainer;

  private ObservableList<WebDriver> webDriverList = FXCollections.observableArrayList();
  private static final ARPropertyManager arPropertyManager;
  private static final WebSocketSessionManager webSocketSessionManager;
  private static final ARWebSocketServerIP arWebSocketServerIP;
  private static final ARWebSocketServer arWebSocketServer;

  static {
    arPropertyManager = ARPropertyManager.getInstance();
    webSocketSessionManager = WebSocketSessionManager.getInstance();
    try {
      arWebSocketServerIP = ARWebSocketServerIP.getInstance();
    } catch (Exception error) {
      ARLogger.getInstance(ARMainScene.class)
          .severe("ARWebSocketServerIP with IP failed " + error.getMessage());

      throw new RuntimeException(error);
    }
    try {
      arWebSocketServer = ARWebSocketServer.getInstance();
    } catch (Exception error) {
      ARLogger.getInstance(ARMainScene.class)
          .severe("ARWebSocketServer NO IP failed " + error.getMessage());
      throw new RuntimeException(error);
    }
  }

  public ARMainScene() {
    super();
  }

  @Override
  public IARPane buildPane() {
    //        initiateJetty();
    return new ARMainPane(webDriverList);
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

  private void handleCloseRequest(WindowEvent event) {

    stopWebSocketServer();

    System.out.println("Handle Close: Exiting Threads and Quitting WebDriver");

    // Interrupt running threads
    threadList.forEach(this::interruptThread);

    // Close WebDriver if it's initialized
    closeWebDrivers();
  }

  private static void initiateJetty() {

    int portInitial = 54525;
    try (ServerSocket serverSocket = new ServerSocket(0)) { // Port 0 = auto-assign
      portInitial = serverSocket.getLocalPort();
      System.out.println("Available port: " + portInitial);
      arPropertyManager.setProperty(
          ARPropertyEnum.PORT_SOCKET.getValue(), String.valueOf(portInitial));
    } catch (IOException e) {
      System.out.println("Fixed Port : " + 54525);
      arPropertyManager.setProperty(ARPropertyEnum.PORT_SOCKET.getValue(), String.valueOf(54525));
      portInitial = 54525;
    }

    String portSocket = arPropertyManager.getProperty(ARPropertyEnum.PORT_SOCKET);
    if (portSocket != null) {
      portInitial = Integer.parseInt(portSocket);
    }

    // Start WebSocket server in a background thread
    int finalPort = portInitial;
    new Thread(
            () -> {
              try {
                startWebSocketServer(finalPort);
              } catch (Exception error) {
                ARLogger.getInstance(ARMainScene.class)
                    .severe("Port : " + finalPort + " error : " + error.getMessage());

                //                        performMessage.errorMessage(
                //                                "Port Error", "Port %d already in Use!",
                // String.valueOf(finalPort), null, null, 350);
              }
            })
        .start();
  }

  public static void startWebSocketServer(int port) throws Exception {
    // Check if the port is available
    if (isPortInUse(port)) {
      throw new Exception("Port " + port + " is already in use.");
    }

    // Set up Jetty server to run WebSocket endpoint
    jettyServer = new Server(port); // Server listens on port 8080
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    jettyServer.setHandler(context);

    // Initialize WebSocket container
    wsContainer = WebSocketServerContainerInitializer.configureContext(context);
    wsContainer.setDefaultMaxSessionIdleTimeout(0);
    wsContainer.addEndpoint(SimpleWebSocketServer.class); // Register SimpleWebSocketServer

    // Start Jetty server
    jettyServer.start();
    System.out.println("Server started at ws://localhost:" + port + "/websocket");

    //        // Example: Retrieve all active sessions
    //        activeSessions = simpleWebSocketServer.getAllSessions();
    System.out.println("Active sessions: " + webSocketSessionManager.getAllSessions().size());

    //        // Example of how to start the HTTP server
    //        int httpPort = 8080; // Or dynamically assign as shown in ARMainScene
    //        new Thread(
    //                        () -> {
    //                            try {
    //                                SimpleHttpServerBase.start(httpPort);
    //                            } catch (Exception e) {
    //                                // Handle any errors that occur during server startup
    //                                System.err.println(
    //                                        "Failed to start HTTP server on port " + httpPort + ":
    // " +
    // e.getMessage());
    //                            }
    //                        },
    //                        "Http-Server-Start-Thread")
    //                .start();
  }

  // Method to check if the port is already in use
  private static boolean isPortInUse(int port) {
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      return false; // Port is available
    } catch (IOException e) {
      return true; // Port is already in use
    }
  }

  // Method to close all WebDriver instances
  private void closeWebDrivers() {
    for (WebDriver driver : webDriverList) {
      try {
        Platform.runLater(() -> webDriverList.remove(driver));
        Platform.runLater(driver::quit);
        ARLogger.getInstance(ARMainScene.class).info("WebDriver closed.");
      } catch (Exception e) {
        ARLogger.getInstance(ARMainScene.class)
            .warning("Error closing WebDriver: " + e.getMessage());
      }
    }
    Platform.runLater(
        () -> {
          webDriverList.clear();
          System.exit(0);
        });
  }

  public void stopWebSocketServer() {
    if (jettyServer != null && jettyServer.isStarted()) {
      try {
        jettyServer.stop();
      } catch (Exception e) {
        ARLogger.getInstance(ARMainScene.class)
            .severe("stopWebSocketServer  \nError: " + e.getMessage());
      }
      jettyServer.destroy();
      ARLogger.getInstance(ARMainScene.class).info("WebSocket server stopped.");
      //            System.out.println("WebSocket server stopped.");
    }
  }
}
