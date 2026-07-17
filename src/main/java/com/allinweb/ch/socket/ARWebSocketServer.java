package com.allinweb.ch.socket;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

@Slf4j
public class ARWebSocketServer {

    //    private static final ARLogger logger;
    //    private static final ARPropertyManager arPropertyManager;
    protected static volatile ARWebSocketServer instance;
    public static final String LOOPBACK_ADDRESS = "127.0.0.1";
    private Server jettyServer;
    private ServerContainer wsContainer;
    private int boundPort;

    private int fallBackPort = 54525; //

    // Private constructor to prevent instantiation
    private ARWebSocketServer() throws Exception {

        startServer();
    }

    public static ARWebSocketServer getInstance() {
        if (instance == null) {
            synchronized (ARWebSocketServer.class) {
                if (instance == null) {
                    try {
                        instance = new ARWebSocketServer();
                    } catch (Exception e) {
                        log.error("Failed to initialize ARWebSocketServer.", e);
                        throw new RuntimeException("ARWebSocketServer initialization failed", e);
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
        String portStr = System.getProperty("ARWebChosenPort");
        if (portStr == null) {
            log.error("No port defined in system properties (ARWebChosenPort).");
            throw new IOException("No port defined in system properties (ARWebChosenPort).");
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
            if (!isPortInUse(fallBackPort)) {
                this.boundPort = fallBackPort;
                log.warn("Initial port {} in use, using fallback port {}", initialPort, this.boundPort);
            } else {
                log.error(
                        "Cannot start server: Both initial port {} and fallback port {} are in use.",
                        initialPort,
                        fallBackPort);
                throw new IOException("Cannot start server: Both initial port " + initialPort + " and fallback port "
                        + fallBackPort + " are in use.");
            }
        }

        // This is an in-process desktop control channel. Never expose it on LAN interfaces.
        jettyServer = new Server();
        jettyServer.addConnector(createLoopbackConnector(jettyServer, boundPort));
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);

        // Serve the built React UI (src/main/resources/build) from the same origin/port as the
        // WebSocket endpoint below, so the page can open its bootstrap WebSocket at
        // ws://<window.location.host>/websocket without needing to discover the port separately.
        URL buildResource = getClass().getClassLoader().getResource("build");
        if (buildResource != null) {
            context.setBaseResource(Resource.newResource(buildResource));
            context.setWelcomeFiles(new String[] {"index.html"});
            ServletHolder staticHolder = new ServletHolder("static", DefaultServlet.class);
            staticHolder.setInitParameter("dirAllowed", "false");
            context.addServlet(staticHolder, "/");
        } else {
            log.warn("No 'build' resources found on classpath; React UI will not be served over HTTP.");
        }

        wsContainer = WebSocketServerContainerInitializer.configureContext(context);
        wsContainer.setDefaultMaxSessionIdleTimeout(0);
        // Bulk grid operations (select all -> insert all) send every scanned element in one
        // JSON message; the 64KB Jetty default kills the scanner workspace session with TOO_BIG.
        wsContainer.setDefaultMaxTextMessageBufferSize(8 * 1024 * 1024);
        wsContainer.setDefaultMaxBinaryMessageBufferSize(8 * 1024 * 1024);
        wsContainer.addEndpoint(SimpleWebSocketServer.class); // Register your WebSocket endpoint

        // Start the Jetty Server
        jettyServer.start();
        if (buildResource != null) {
            String url = "http://" + LOOPBACK_ADDRESS + ":" + boundPort;
            log.info("AR Web Scanner UI available at {}", url);
            openInBrowser(url);
        }
    }

    /**
     * Opens the UI in the user's default browser -- there is no embedded window anymore (the JCEF
     * shell was removed), so without this nothing tells the user the app is ready.
     */
    private void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception e) {
            log.warn("Desktop.browse failed, falling back to OS command: {}", e.getMessage());
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (IOException e) {
            log.warn("Could not auto-open browser at {}: {}", url, e.getMessage());
        }
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

    static ServerConnector createLoopbackConnector(Server server, int port) {
        ServerConnector connector = new ServerConnector(server);
        connector.setHost(LOOPBACK_ADDRESS);
        connector.setPort(port);
        return connector;
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
    //        System.setProperty("ARWebChosenPort", String.valueOf(chosenPort));
    //        //        arPropertyManager.setProperty(ARPropertyEnum.PORT_SOCKET.getValue(),
    // String.valueOf(chosenPort));
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
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true); // Allow reuse of the address for quick release
            serverSocket.bind(new InetSocketAddress(LOOPBACK_ADDRESS, port));
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
