package com.allinweb.ch.socket;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import com.allinweb.ch.socket.WebSocketSessionManager;

@Slf4j
public class ARWebSocketServer {

    //    private static final ARLogger logger;
    //    private static final ARPropertyManager arPropertyManager;
    protected static volatile ARWebSocketServer instance;
    public static final String LOOPBACK_ADDRESS = "127.0.0.1";
    private static final int MEMORY_LIST_WINDOW_WIDTH = 310;
    private static final int MEMORY_LIST_WINDOW_HEIGHT = 205;
    private Server jettyServer;
    private ServerContainer wsContainer;
    private int boundPort;
    private final DesktopAppBrowserLauncher desktopAppBrowserLauncher = new DesktopAppBrowserLauncher();

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

    /** Stops the loopback server only when application startup created it already. */
    public static void stopIfInitialized() {
        ARWebSocketServer current = instance;
        if (current != null) current.stopServer();
    }

    /**
     * Retires the logical ownership records for every detached desktop shell. Physical windows are
     * closed by the preceding {@code application.shutdown} broadcast, never by killing a generic
     * Chrome/Edge process.
     */
    public static void retireOwnedDesktopWorkspaces() {
        PageScannerWorkspaceCoordinator.getInstance().closeActive();
        OcrWorkspaceCoordinator.getInstance().closeAll();
        BotJobDetailsWindowCoordinator botJobWindows = BotJobDetailsWindowCoordinator.getInstance();
        String controlSession = botJobWindows.activeControlSessionId();
        if (!controlSession.isEmpty()) botJobWindows.retire(controlSession);
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
        // The runtime port is authoritative. Config persistence is best-effort and may still be
        // completing while the main React shell performs its bootstrap handshake.
        System.setProperty("ARWebChosenPort", String.valueOf(this.boundPort));

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

    /** Opens the UI in a desktop app window, with the user's default browser as a fallback. */
    private void openInBrowser(String url) {
        if (desktopAppBrowserLauncher.launch(url)) return;

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

    /** Opens or retargets the application's one Bot Job Details native window. */
    public BotJobDetailsWindowCoordinator.OpenResult openBotJobDesktopShell(
            int homeBankingId, int botJobId, long workspaceEpoch) {
        return BotJobDetailsWindowCoordinator.getInstance().open(
                new BotJobDetailsWindowCoordinator.Target(
                        botJobId, workspaceEpoch, homeBankingId));
    }

    /** Strict launcher used only by the single-window coordinator. */
    public boolean openBotJobDetailsDesktopShell(int botJobId, String controlSessionId) {
        return desktopAppBrowserLauncher.launch(
                botJobDetailsDesktopUrl(boundPort, botJobId, controlSessionId));
    }

    /** Opens one detached OCR workspace without falling back to a browser window with an address bar. */
    public boolean openOcrWorkspaceDesktopShell(OcrWorkspaceCoordinator.Kind kind, String sessionId) {
        return desktopAppBrowserLauncher.launch(ocrWorkspaceDesktopUrl(boundPort, kind, sessionId));
    }

    /** Opens one detached Page Scanner without falling back to a browser with an address bar. */
    public boolean openPageScannerDesktopShell(String sessionId) {
        return desktopAppBrowserLauncher.launch(pageScannerDesktopUrl(boundPort, sessionId));
    }

    /** Opens one detached floating React workspace without falling back to a browser with an address bar. */
    public boolean openDetachedWorkspaceDesktopShell(String sessionId) {
        return openDetachedWorkspaceDesktopShell(sessionId, -9999);
    }

    /** Opens one detached floating React workspace without falling back to a browser with an address bar. */
    public boolean openDetachedWorkspaceDesktopShell(String sessionId, int sourceBotJobId) {
        if (!DetachedWorkspaceSessions.isDetachedWorkspaceSession(sessionId)) {
            throw new IllegalArgumentException("A valid detached workspace session is required");
        }
        if (WebSocketSessionManager.isSessionOpen(sessionId)) {
            log.info("Detached workspace {} is already open; reusing the existing session", sessionId);
            return true;
        }
        String desktopUrl = detachedWorkspaceDesktopUrl(boundPort, sessionId, sourceBotJobId);
        if (DetachedWorkspaceSessions.MEMORY_LIST_MANAGER.equals(sessionId)) {
            return desktopAppBrowserLauncher.launch(
                    desktopUrl, MEMORY_LIST_WINDOW_WIDTH, MEMORY_LIST_WINDOW_HEIGHT);
        }
        return desktopAppBrowserLauncher.launch(desktopUrl);
    }

    /** Retires the detached Page Scanner bound to one exact Bot Job workspace epoch. */
    public boolean closePageScannerWorkspace(
            int homeBankingId, int botJobId, long workspaceEpoch) {
        return PageScannerWorkspaceCoordinator.getInstance()
                .closeForBotJob(homeBankingId, botJobId, workspaceEpoch);
    }

    /** Closes the one global Page Scanner when Bot Job Details itself is explicitly closed. */
    public boolean closeActivePageScannerWorkspace() {
        return PageScannerWorkspaceCoordinator.getInstance().closeActive();
    }

    static String pageScannerDesktopUrl(int port, String sessionId) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("A valid AR Web port is required");
        }
        if (!com.allinweb.ch.model.ScannerWorkspaceSessions.isPageScannerSession(sessionId)) {
            throw new IllegalArgumentException("A valid Page Scanner workspace session is required");
        }
        return "http://" + LOOPBACK_ADDRESS + ":" + port
                + "/?desktopShell=1&openPageScanner=preScan&pageScannerSession="
                + encodeQueryParameter(sessionId);
    }

    static String botJobDetailsDesktopUrl(
            int port, int botJobId, String controlSessionId) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("A valid AR Web port is required");
        }
        if (botJobId <= 0) {
            throw new IllegalArgumentException("A positive Bot Job ID is required");
        }
        if (!BotJobDetailsWindowCoordinator.isControlSessionId(controlSessionId)) {
            throw new IllegalArgumentException("A valid Bot Job Details window session is required");
        }
        return "http://" + LOOPBACK_ADDRESS + ":" + port
                + "/?desktopShell=1&openBotJob=" + botJobId
                + "&botJobWindowSession=" + encodeQueryParameter(controlSessionId);
    }

    static String ocrWorkspaceDesktopUrl(
            int port, OcrWorkspaceCoordinator.Kind kind, String sessionId) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("A valid AR Web port is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("An OCR workspace kind is required");
        }
        if (sessionId == null
                || OcrWorkspaceCoordinator.Kind.fromSessionId(sessionId) != kind) {
            throw new IllegalArgumentException("The OCR workspace session does not match its kind");
        }
        return "http://" + LOOPBACK_ADDRESS + ":" + port
                + "/?desktopShell=1&openOcr="
                + encodeQueryParameter(kind.routeValue())
                + "&ocrSession="
                + encodeQueryParameter(sessionId);
    }

    static String detachedWorkspaceDesktopUrl(int port, String sessionId) {
        return detachedWorkspaceDesktopUrl(port, sessionId, -9999);
    }

    static String detachedWorkspaceDesktopUrl(int port, String sessionId, int sourceBotJobId) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("A valid AR Web port is required");
        }
        if (!DetachedWorkspaceSessions.isDetachedWorkspaceSession(sessionId)) {
            throw new IllegalArgumentException("A valid detached workspace session is required");
        }
        String url = "http://" + LOOPBACK_ADDRESS + ":" + port
                + "/?desktopShell=1&openWorkspace=" + encodeQueryParameter(sessionId);
        if (sourceBotJobId > 0) {
            url += "&sourceBotJobId=" + sourceBotJobId;
        }
        return url;
    }

    private static String encodeQueryParameter(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
