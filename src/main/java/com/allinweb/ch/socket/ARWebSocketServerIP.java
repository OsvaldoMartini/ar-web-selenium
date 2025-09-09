package com.allinweb.ch.socket;

// Assuming this class exists
// Assuming this class exists

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;

@Slf4j
public class ARWebSocketServerIP {

    protected static volatile ARWebSocketServerIP instance;
    private String BIND_IP_ADDRESS = "0.0.0.0";
    private Server jettyServer;
    private ServerContainer wsContainer;
    private int boundPort;
    private int fallBackPortIP = 54526; //
    // Private constructor to prevent instantiation
    private ARWebSocketServerIP() throws Exception {

        startServer();
    }

    public static ARWebSocketServerIP getInstance() {
        if (instance == null) {
            synchronized (ARWebSocketServerIP.class) {
                if (instance == null) {
                    try {
                        instance = new ARWebSocketServerIP();
                    } catch (Exception e) {
                        log.error("Failed to initialize ARWebSocketServerIP.", e);
                        throw new RuntimeException("ARWebSocketServerIP initialization failed", e);
                    }
                }
            }
        }
        return instance;
    }

    /**
     * Starts the Jetty WebSocket server.
     * Determines the port and bind address, then initializes and starts Jetty.
     *
     * @throws Exception if the server cannot be started (e.g., port in use).
     */
    public void startServer() throws Exception {
        String portStr = System.getProperty("ARWebChosenPortIP");
        if (portStr == null) {
            log.error("No port defined in system properties (ARWebChosenPortIP).");
            throw new IOException("No port defined in system properties (ARWebChosenPortIP).");
        }

        int initialPort;
        try {
            initialPort = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            log.error("Invalid port number: {}", portStr, e);
            throw new IOException("Invalid port number: " + portStr, e);
        }

        this.boundPort = initialPort;

        if (isPortInUse(this.boundPort)) {
            if (!isPortInUse(fallBackPortIP)) {
                this.boundPort = fallBackPortIP;
                log.warn("Initial port {} in use, using fallback port {}", initialPort, this.boundPort);
            } else {
                log.error(
                        "Cannot start server: Both initial port {} and fallback port {} are in use.",
                        initialPort,
                        fallBackPortIP);
                throw new IOException("Cannot start server: Both initial port " + initialPort + " and fallback port "
                        + fallBackPortIP + " are in use.");
            }
        }

        // 3. Determine the IP address to bind to
        // You can add a property like ARPropertyEnum.BIND_IP_ADDRESS for flexibility
        // Default to "0.0.0.0" to listen on all interfaces.

        // 4. Initialize Jetty Server with specific binding
        jettyServer = new Server();
        InetSocketAddress socketAddress = new InetSocketAddress(BIND_IP_ADDRESS, this.boundPort);
        ServerConnector connector = new ServerConnector(jettyServer);
        connector.setHost(socketAddress.getHostString());
        connector.setPort(socketAddress.getPort());
        jettyServer.addConnector(connector);

        // 5. Set up ServletContextHandler for WebSocket Endpoints
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);

        // 6. Initialize WebSocket container
        // Note: For Jetty 11+ and Jakarta EE 9+, ensure your pom.xml uses jakarta.websocket-api
        // and org.eclipse.jetty.websocket:websocket-jakarta-server
        wsContainer = WebSocketServerContainerInitializer.configureContext(context);
        wsContainer.setDefaultMaxSessionIdleTimeout(0);
        wsContainer.addEndpoint(SimpleWebSocketServer.class); // Register your WebSocket endpoint

        // 7. Start the Jetty Server
        jettyServer.start();
        //        loggerlog.info("Jetty WebSocket server started on ws://" + BIND_IP_ADDRESS + ":" + this.boundPort +
        // "/websocket");
        //        loggerlog.info("Current active WebSocket sessions: " +
        // webSocketSessionManager.getAllSessions().size());
    }

    /**
     * Stops the Jetty WebSocket server.
     */
    public void stopServer() {
        if (jettyServer != null && jettyServer.isStarted()) {
            try {
                jettyServer.stop();
                jettyServer.destroy(); // Release resources
                //                loggerlog.info("WebSocket server stopped.");
            } catch (Exception e) {
                log.error("Error stopping WebSocket server.", e);
                //                loggerlog.error("Error stopping WebSocket server: " + e.getMessage());
            }
        }
    }

    /**
     * Gets the port the server is currently bound to.
     *
     * @return The bound port, or -1 if the server hasn't started yet.
     */
    public int getBoundPort() {
        return boundPort;
    }

    //    /**
    //     * Determines the initial port for the server.
    //     * First tries to get any available ephemeral port and persists it to properties.
    //     * If finding an ephemeral port fails, it falls back to a default fixed port.
    //     * The chosen port is always persisted to properties for consistency across runs.
    //     */
    //    private int getInitialPort() {
    //        int defaultFixedPort = 54525; // A known default port if no ephemeral or previous setting works
    //        int chosenPort;
    //
    //        try (ServerSocket tempSocket = new ServerSocket(0)) {
    //            tempSocket.setReuseAddress(true); // Allow immediate reuse of the address
    //            chosenPort = tempSocket.getLocalPort();
    //            //            loggerlog.info("Found available ephemeral port: " + chosenPort);
    //        } catch (IOException e) {
    //            // If finding an ephemeral port fails, log the warning and fall back to the fixed default
    //            //            loggerlog.warn("Could not find an ephemeral port. Falling back to default fixed port: "
    // +
    //            // defaultFixedPort + ". Error: " + e.getMessage());
    //            chosenPort = defaultFixedPort;
    //        }
    //
    //        // 2. Persist the chosen port to properties
    //        //        arPropertyManager.setProperty(ARPropertyEnum.PORT_SOCKET.getValue(),
    // String.valueOf(chosenPort));
    //        System.setProperty("ARWebChosenPortIP", String.valueOf(chosenPort));
    //        //        loggerlog.info("Set " + ARPropertyEnum.PORT_SOCKET.getValue() + " to: " + chosenPort + " in
    //        // properties.");
    //
    //        return chosenPort;
    //    }

    /**
     * Checks if a given port is currently in use.
     *
     * @param port The port number to check.
     * @return true if the port is in use, false otherwise.
     */
    private boolean isPortInUse(int port) {
        if (port < 1 || port > 65535) {
            //            loggerlog.error("Invalid port number provided for check: " + port);
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
            //            loggerlog.warn("Unexpected SocketException when checking port " + port + ": " + message);
            return true; // Assume in use for other socket exceptions
        } catch (IOException e) {
            //            loggerlog.warn("IOException when checking port " + port + ": " + e.getMessage());
            return true; // Assume in use for general IO exceptions
        }
    }
}
