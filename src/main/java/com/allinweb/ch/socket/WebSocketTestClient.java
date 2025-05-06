package com.allinweb.ch.socket;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.logging.Logger;
import javax.websocket.*;

@ClientEndpoint
public class WebSocketTestClient {

    private static final CountDownLatch latch = new CountDownLatch(1);
    private static Session session;

    private static final String KEYSTORE_PASSWORD = "Martini!383940";
    private static final String TRUSTSTORE_PASSWORD = "changeit";
    //    private static final String WSS_URI = "wss://localhost:61757/websocket";
    private static final String WSS_URI = "wss://localhost:" + "61757" + "/websocket?sessionId=" + "sessionId-61757";
    private static final ScheduledExecutorService pingScheduler = Executors.newScheduledThreadPool(1);
    private static final Logger logger = Logger.getLogger(WebSocketTestClient.class.getName());

    public static void main(String[] args) {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        URI uri;

        try {
            // Load keystore from resources and copy to a temp file
            String keystorePassword = "Martini!383940";
            File keystoreTempFile = copyResourceToTempFile("keystore.jks", "keystore", ".jks");
            System.setProperty("javax.net.ssl.keyStore", keystoreTempFile.getAbsolutePath());
            System.setProperty("javax.net.ssl.keyStorePassword", keystorePassword);

            // Load truststore from resources and copy to a temp file
            String truststorePassword = "Martini!383940";
            File truststoreTempFile = copyResourceToTempFile("truststore.jks", "truststore", ".jks");
            System.setProperty("javax.net.ssl.trustStore", truststoreTempFile.getAbsolutePath());
            System.setProperty("javax.net.ssl.trustStorePassword", truststorePassword);
        } catch (Exception erroTemp) {

        }

        try {
            uri = new URI(WSS_URI);
            container.connectToServer(WebSocketTestClient.class, uri);
            latch.await();
            startPingScheduler();
        } catch (Exception e) {
            logger.severe("Error during WebSocket connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void startPingScheduler() {
        pingScheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        if (session != null && session.isOpen()) {
                            session.getBasicRemote().sendText("ping-wss");
                            logger.info("Ping sent.");
                        }
                    } catch (Exception e) {
                        logger.severe("Error sending ping: " + e.getMessage());
                        e.printStackTrace();
                    }
                },
                0,
                10,
                TimeUnit.SECONDS);
    }

    @OnOpen
    public void onOpen(Session session) {
        WebSocketTestClient.session = session;
        logger.info("Connected to the WebSocket server");
        latch.countDown(); // Release the latch after connection is established
    }

    @OnMessage
    public void onMessage(String message) {
        logger.info("Received message: " + message);
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        logger.info("Closed WebSocket connection: " + closeReason.getReasonPhrase());
        pingScheduler.shutdown(); // Shutdown the ping scheduler when connection closes
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        logger.severe("Error in WebSocket communication: " + throwable.getMessage());
    }

    private static File copyResourceToTempFile(String resourceName, String prefix, String suffix) throws IOException {
        URL resourceUrl = WebSocketTestClient.class.getClassLoader().getResource(resourceName);
        if (resourceUrl == null) {
            throw new FileNotFoundException("Resource not found: " + resourceName);
        }

        File tempFile = Files.createTempFile(prefix, suffix).toFile();
        tempFile.deleteOnExit();

        try (InputStream in = resourceUrl.openStream();
                OutputStream out = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        return tempFile;
    }
}
