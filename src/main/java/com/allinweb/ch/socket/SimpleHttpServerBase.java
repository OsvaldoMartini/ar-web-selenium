// File: src/main/java/com/allinweb/ch/socket/SimpleHttpServer.java (or a suitable package)
package com.allinweb.ch.socket;

import com.allinweb.ch.util.ARLogger;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;

/**
 * A simple HTTP server using Jetty to serve static files from a 'web' directory. If
 * 'web/index.html' does not exist, it will create a basic one.
 */
public class SimpleHttpServerBase {
  private static Server httpServer;
  private static final String WEB_DIR_NAME = "web";
  private static final ARLogger logger = ARLogger.getInstance(SimpleHttpServerBase.class);

  /**
   * Starts the HTTP server on the specified port. It will attempt to create a 'web' directory and
   * an 'index.html' inside it if they don't already exist.
   *
   * @param port The port on which the HTTP server will listen.
   * @throws Exception If the server fails to start or the port is in use.
   */
  public static void start(int port) throws Exception {
    if (isPortInUse(port)) {
      throw new Exception("Port " + port + " is already in use for HTTP server.");
    }

    httpServer = new Server(port);

    // Define the path for static content
    // This assumes the 'web' directory will be relative to where the application is run.
    Path staticContentPath = Paths.get(WEB_DIR_NAME).toAbsolutePath();

    // Ensure the 'web' directory exists
    if (!Files.exists(staticContentPath)) {
      try {
        Files.createDirectories(staticContentPath);
        logger.info("Created static content directory: " + staticContentPath);
      } catch (IOException e) {
        logger.severe(
            "Failed to create static content directory: "
                + staticContentPath
                + " Error: "
                + e.getMessage());
        throw new IOException("Failed to create static content directory.", e);
      }
    }

    // Ensure a default index.html exists in the 'web' directory
    Path indexHtmlPath = staticContentPath.resolve("index.html");
    if (!Files.exists(indexHtmlPath)) {
      String htmlContent =
          "<!DOCTYPE html>\n"
              + "<html lang=\"en\">\n"
              + "<head>\n"
              + "    <meta charset=\"UTF-8\">\n"
              + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
              + "    <title>AR Web Bot HTTP Server</title>\n"
              + "    <style>\n"
              + "        body { font-family: 'Inter', sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; background-color: #f4f7f6; color: #333; }\n"
              + "        .container { background-color: #ffffff; padding: 40px; border-radius: 12px; box-shadow: 0 8px 24px rgba(0,0,0,0.1); text-align: center; border: 1px solid #e0e0e0;}\n"
              + "        h1 { color: #2c3e50; margin-bottom: 20px; font-size: 2.2em; }\n"
              + "        p { color: #7f8c8d; font-size: 1.1em; line-height: 1.6; }\n"
              + "        .port-info { margin-top: 25px; padding: 15px; background-color: #ecf0f1; border-radius: 8px; border: 1px solid #bdc3c7; display: inline-block; }\n"
              + "        .port-info strong { color: #2980b9; }\n"
              + "    </style>\n"
              + "</head>\n"
              + "<body>\n"
              + "    <div class=\"container\">\n"
              + "        <h1>Welcome to AR Web Bot HTTP Server!</h1>\n"
              + "        <p>This page is served statically from the <code>/"
              + WEB_DIR_NAME
              + "</code> directory.</p>\n"
              + "        <p>Your WebSocket server is also running on a separate port, ready for connections.</p>\n"
              + "        <div class=\"port-info\">\n"
              + "            <p>HTTP Server running on: <strong>http://localhost:"
              + port
              + "</strong></p>\n"
              + "            <p>WebSocket Server running on: <strong>ws://localhost:[WebSocketPort]/websocket</strong></p>\n"
              + "        </div>\n"
              + "    </div>\n"
              + "</body>\n"
              + "</html>";
      try {
        Files.write(indexHtmlPath, htmlContent.getBytes());
        logger.info("Created default index.html at: " + indexHtmlPath);
      } catch (IOException e) {
        logger.severe(
            "Failed to create default index.html: " + indexHtmlPath + " Error: " + e.getMessage());
        throw new IOException("Failed to create default index.html.", e);
      }
    }

    // Configure ResourceHandler to serve files from the static content path
    ResourceHandler resourceHandler = new ResourceHandler();
    resourceHandler.setDirectoriesListed(false); // Do not list directory contents
    resourceHandler.setWelcomeFiles(
        new String[] {"index.html"}); // Serve index.html as the default file
    resourceHandler.setBaseResource(Resource.newResource(staticContentPath.toUri()));

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
    logger.info("HTTP Server started at http://localhost:" + port);
    System.out.println("HTTP Server started at http://localhost:" + port);
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
