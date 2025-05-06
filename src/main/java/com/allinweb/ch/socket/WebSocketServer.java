package com.allinweb.ch.socket;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import javax.websocket.server.ServerContainer;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

public class WebSocketServer {

    private static Server jettyServer;

    public static void start(int port) throws Exception {
        if (isPortInUse(port)) {
            throw new Exception("Port " + port + " is already in use.");
        }

        try {
            jettyServer = new Server();

            // Setup SSL connector
            ServerConnector sslConnector = createSslConnector(jettyServer, port);
            jettyServer.addConnector(sslConnector);

            // Setup context
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");
            jettyServer.setHandler(context);

            // Initialize WebSocket support
            ServerContainer wsContainer = WebSocketServerContainerInitializer.configureContext(context);
            wsContainer.setDefaultMaxSessionIdleTimeout(0);
            wsContainer.addEndpoint(SimpleWebSocketServer.class);

            // Start server
            jettyServer.start();
            System.out.println("WebSocket server started at wss://localhost:" + port + "/websocket");

            // Keep server running
            jettyServer.join();
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    private static ServerConnector createSslConnector(Server server, int port) throws Exception {
        // Load keystore from resources and copy to temp file
        InputStream keyStoreStream = WebSocketServer.class.getResourceAsStream("/keystore.jks");
        if (keyStoreStream == null) {
            throw new FileNotFoundException("Keystore not found in classpath at /keystore.jks");
        }

        File tempKeyStore = File.createTempFile("keystore", ".jks");
        Files.copy(keyStoreStream, tempKeyStore.toPath(), StandardCopyOption.REPLACE_EXISTING);
        tempKeyStore.deleteOnExit();

        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStorePath(tempKeyStore.getAbsolutePath());
        sslContextFactory.setKeyStorePassword("Martini!383940");
        sslContextFactory.setKeyStoreType("JKS");
        sslContextFactory.setWantClientAuth(false);
        sslContextFactory.setProtocol("TLSv1.2");

        ServerConnector sslConnector = new ServerConnector(
                server, new SslConnectionFactory(sslContextFactory, "http/1.1"), new HttpConnectionFactory());
        sslConnector.setPort(port);
        sslConnector.setIdleTimeout(30000);

        return sslConnector;
    }

    public static void main(String[] args) {
        try {
            start(61757);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isPortInUse(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}
