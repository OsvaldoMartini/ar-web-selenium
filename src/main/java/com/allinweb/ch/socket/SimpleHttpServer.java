// File: src/main/java/com/allinweb/ch/socket/SimpleHttpServer.java
package com.allinweb.ch.socket;

import com.allinweb.ch.util.ARLogger;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;

/**
 * A simple HTTP server using Jetty to serve a specific index.html file. The server will return the
 * port it started on.
 */
public class SimpleHttpServer {
    private static Server httpServer;
    private static final ARLogger logger = ARLogger.getInstance(SimpleHttpServer.class);

    /**
     * Starts the HTTP server on the specified port, serving the given index.html file. If the
     * requestedPort is 0, a dynamic available port will be chosen. The index.html file is expected to
     * be a resource loadable by the classloader.
     *
     * @param requestedPort The desired port for the HTTP server. If 0, a dynamic port will be used.
     * @param filePath The resource path (e.g., "/build/index.html") to the index.html file to be
     *     served.
     * @return The actual port number on which the HTTP server started.
     * @throws Exception If the server fails to start, the requested port is in use, or the index.html
     *     file is invalid/not found.
     */
    public static int start(int requestedPort, String filePath) throws Exception {
        int actualPort = requestedPort;
        // Determine the actual port to use
        if (requestedPort == 0) {
            try (ServerSocket serverSocket = new ServerSocket(0)) { // Port 0 = auto-assign
                actualPort = serverSocket.getLocalPort();
                logger.info("Dynamically assigned HTTP port: " + actualPort);
            } catch (IOException e) {
                logger.severe("Failed to find a dynamic port: " + e.getMessage());
                throw new IOException("Failed to find an available dynamic port.", e);
            }
        } else {
            if (isPortInUse(requestedPort)) {
                throw new Exception("Port " + requestedPort + " is already in use for HTTP server.");
            }
        }

        Path indexHtmlFilePath;
        try {
            // Get the URL for the resource. This is typically from the classpath (e.g., target/classes).
            URL resourceUrl = SimpleHttpServer.class.getResource(filePath);
            if (resourceUrl == null) {
                logger.severe("Resource not found for path: " + filePath + ". Please ensure it's on the classpath.");
                throw new IOException("Resource not found for path: " + filePath);
            }
            // Convert URL to URI, then to Path to correctly handle file system paths from resources.
            indexHtmlFilePath = Paths.get(resourceUrl.toURI());
            logger.info("index.html resolved to file system path: " + indexHtmlFilePath.toAbsolutePath());
        } catch (Exception error) {
            logger.severe("Failed to resolve index.html path for resource '" + filePath + "': " + error.getMessage());
            throw new IOException("Failed to resolve index.html path for resource: " + filePath, error);
        }

        // Validate the indexHtmlFilePath
        if (!Files.exists(indexHtmlFilePath)) {
            // This check should ideally not be hit if resourceUrl != null, but as a safeguard.
            throw new IOException("index.html file not found at resolved path: " + indexHtmlFilePath.toAbsolutePath());
        }
        if (!Files.isRegularFile(indexHtmlFilePath)) {
            throw new IOException(
                    "Provided index.html path is not a regular file: " + indexHtmlFilePath.toAbsolutePath());
        }

        httpServer = new Server(actualPort);

        // Get the parent directory of the index.html file to serve as the base resource
        Path baseDirectory = indexHtmlFilePath.getParent();
        if (baseDirectory == null) { // Handle case where index.html is in the root of a drive/filesystem
            baseDirectory = Paths.get("./"); // Use current directory as fallback base
            logger.warning("Index.html has no parent directory. Using current directory as base: "
                    + baseDirectory.toAbsolutePath());
        }

        // Get the filename of the index.html for welcome files
        String welcomeFileName = indexHtmlFilePath.getFileName().toString();

        // Configure ResourceHandler to serve files from the specified base directory
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(false); // Do not list directory contents
        resourceHandler.setWelcomeFiles(
                new String[] {welcomeFileName}); // Serve the provided index.html as the default file
        resourceHandler.setBaseResource(Resource.newResource(baseDirectory.toUri()));

        // Create a ContextHandler to wrap the ResourceHandler and set the context path
        ContextHandler context = new ContextHandler();
        context.setContextPath("/"); // Serve from the root context
        context.setHandler(resourceHandler);

        // Add the context handler to the server
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new org.eclipse.jetty.server.Handler[] {context});
        httpServer.setHandler(handlers);

        // Start the server
        httpServer.start();
        logger.info("HTTP Server started at http://localhost:"
                + actualPort
                + " serving: "
                + indexHtmlFilePath.toAbsolutePath());
        System.out.println("HTTP Server started at http://localhost:"
                + actualPort
                + " serving: "
                + indexHtmlFilePath.toAbsolutePath());

        return actualPort; // Return the actual port used
    }

    /**
     * Stops the HTTP server.
     *
     * @throws Exception If the server fails to stop.
     */
    public static void stop() throws Exception {
        if (httpServer != null && httpServer.isStarted()) {
            httpServer.stop();
            httpServer.join(); // Wait for the server to gracefully stop
            logger.info("HTTP Server stopped.");
            System.out.println("HTTP Server stopped.");
        }
    }

    /**
     * Checks if a given port is currently in use.
     *
     * @param port The port number to check.
     * @return true if the port is in use, false otherwise.
     */
    private static boolean isPortInUse(int port) {
        // Attempt to bind to the port; if successful, it's available, otherwise it's in use.
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // If we successfully bind, immediately close it as we only wanted to check availability
            return false; // Port is available
        } catch (IOException e) {
            return true; // Port is already in use
        }
    }
}
