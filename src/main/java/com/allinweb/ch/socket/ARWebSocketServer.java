package com.allinweb.ch.socket;

import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

// Assuming this class exists
// Assuming this class exists

public class ARWebSocketServer {

    protected static volatile ARWebSocketServer instance;

    // Private constructor to prevent instantiation
    private ARWebSocketServer() throws Exception {
        // Initialize if necessary
        startServer();
    }

    public static ARWebSocketServer getInstance() throws Exception {
        if (instance == null) {
            synchronized (ARWebSocketServer.class) {
                if (instance == null) {
                    instance = new ARWebSocketServer();
                }
            }
        }
        return instance;
    }

    private String BIND_IP_ADDRESS = "192.168.1.24";

    private Server jettyServer;
    private ServerContainer wsContainer;
    private int boundPort;

    private static final ARLogger logger;
    private static final ARPropertyManager arPropertyManager;
    private static final WebSocketSessionManager webSocketSessionManager;

    // --- Constructor ---
    // Inject dependencies required by the server (ARPropertyManager, WebSocketSessionManager)
    static {
        logger = ARLogger.getInstance(ARWebSocketServer.class);
        arPropertyManager = ARPropertyManager.getInstance();
        webSocketSessionManager = WebSocketSessionManager.getInstance();
    }

    /**
     * Starts the Jetty WebSocket server.
     * Determines the port and bind address, then initializes and starts Jetty.
     *
     * @throws Exception if the server cannot be started (e.g., port in use).
     */
    public void startServer() throws Exception {
        // 1. Determine the port to use
        int initialPort = getInitialPort();
        this.boundPort = initialPort; // Tentatively set the port

        // 2. Check if the determined port is already in use
        if (isPortInUse(this.boundPort)) {
            logger.warning("Initial port " + this.boundPort
                    + " is already in use. Checking for fallback port from properties.");

            // Try to get a fallback fixed port from properties if the initial one is busy
            String fallbackPortStr = arPropertyManager.getProperty(ARPropertyEnum.PORT_SOCKET);
            if (fallbackPortStr != null) {
                try {
                    int fallbackPort = Integer.parseInt(fallbackPortStr);
                    if (isPortInUse(fallbackPort)) {
                        logger.severe("Fallback port " + fallbackPort + " is also in use. Cannot start server.");
                        throw new IOException("Cannot start server: Both initial and fallback ports are in use.");
                    }
                    this.boundPort = fallbackPort;
                    logger.info("Using fallback port: " + this.boundPort);
                } catch (NumberFormatException e) {
                    logger.severe("Invalid port number in properties: " + fallbackPortStr + " " + e.getMessage());
                    throw new IOException("Invalid port number in properties.");
                }
            } else {
                logger.severe("No fallback port defined in properties. Cannot start server.");
                throw new IOException(
                        "Cannot start server: Initial port " + initialPort + " is in use and no fallback defined.");
            }
        }

        // 3. Determine the IP address to bind to
        // You can add a property like ARPropertyEnum.BIND_IP_ADDRESS for flexibility
        // Default to "0.0.0.0" to listen on all interfaces.

        // 4. Initialize Jetty Server with specific binding
        jettyServer = new Server(boundPort);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);

        wsContainer = WebSocketServerContainerInitializer.configureContext(context);
        wsContainer.setDefaultMaxSessionIdleTimeout(0);
        wsContainer.addEndpoint(SimpleWebSocketServer.class); // Register your WebSocket endpoint

        // 7. Start the Jetty Server
        jettyServer.start();
        logger.info("Jetty WebSocket server started on ws://" + BIND_IP_ADDRESS + ":" + this.boundPort + "/websocket");
        logger.info("Current active WebSocket sessions: "
                + webSocketSessionManager.getAllSessions().size());
    }

    /**
     * Stops the Jetty WebSocket server.
     */
    public void stopServer() {
        if (jettyServer != null && jettyServer.isStarted()) {
            try {
                jettyServer.stop();
                jettyServer.destroy(); // Release resources
                logger.info("WebSocket server stopped.");
            } catch (Exception e) {
                logger.severe("Error stopping WebSocket server: " + e.getMessage());
            }
        }
    }

    /**
     * Gets the port the server is currently bound to.
     * @return The bound port, or -1 if the server hasn't started yet.
     */
    public int getBoundPort() {
        return boundPort;
    }

    /**
     * Determines the initial port for the server.
     * First tries to get any available ephemeral port and persists it to properties.
     * If finding an ephemeral port fails, it falls back to a default fixed port.
     * The chosen port is always persisted to properties for consistency across runs.
     */
    private int getInitialPort() {
        int defaultFixedPort = 54525; // A known default port if no ephemeral or previous setting works
        int chosenPort;

        try (ServerSocket tempSocket = new ServerSocket(0)) {
            tempSocket.setReuseAddress(true); // Allow immediate reuse of the address
            chosenPort = tempSocket.getLocalPort();
            logger.info("Found available ephemeral port: " + chosenPort);
        } catch (IOException e) {
            // If finding an ephemeral port fails, log the warning and fall back to the fixed default
            logger.warning("Could not find an ephemeral port. Falling back to default fixed port: " + defaultFixedPort
                    + ". Error: " + e.getMessage());
            chosenPort = defaultFixedPort;
        }

        // 2. Persist the chosen port to properties
        arPropertyManager.setProperty(ARPropertyEnum.PORT_SOCKET.getValue(), String.valueOf(chosenPort));
        logger.info("Set " + ARPropertyEnum.PORT_SOCKET.getValue() + " to: " + chosenPort + " in properties.");

        return chosenPort;
    }

    /**
     * Checks if a given port is currently in use.
     * @param port The port number to check.
     * @return true if the port is in use, false otherwise.
     */
    private boolean isPortInUse(int port) {
        if (port < 1 || port > 65535) {
            logger.severe("Invalid port number provided for check: " + port);
            return true; // Treat as in use or problematic
        }
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true); // Allow reuse of address for quick release
            return false; // Port is available
        } catch (SocketException e) {
            String message = e.getMessage();
            if (message != null
                    && (message.contains("Address already in use") || message.contains("socket bind failed"))) {
                return true; // Port is definitely in use
            }
            logger.warning("Unexpected SocketException when checking port " + port + ": " + message);
            return true; // Assume in use for other socket exceptions
        } catch (IOException e) {
            logger.warning("IOException when checking port " + port + ": " + e.getMessage());
            return true; // Assume in use for general IO exceptions
        }
    }
}
