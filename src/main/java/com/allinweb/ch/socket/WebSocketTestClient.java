package com.allinweb.ch.socket;

import javax.websocket.*;
import java.net.URI;
import java.util.concurrent.*;
import java.util.logging.Logger;

@ClientEndpoint
public class WebSocketTestClient {

    private static final CountDownLatch latch = new CountDownLatch(1);
    private static Session session;

    private static final String KEYSTORE_PASSWORD = "Martini!383940";
    private static final String TRUSTSTORE_PASSWORD = "changeit";
    private static final String WSS_URI = "wss://localhost:61757/websocket";

    private static final ScheduledExecutorService pingScheduler = Executors.newScheduledThreadPool(1);
    private static final Logger logger = Logger.getLogger(WebSocketTestClient.class.getName());

    public static void main(String[] args) throws Exception {

        // WebSocket container
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();

        // Create a URI for the WebSocket connection
        URI uri = new URI(WSS_URI);

        // Connect to the WebSocket server
        container.connectToServer(WebSocketTestClient.class, uri);

        // Wait for the WebSocket connection to be established
        latch.await();

        // Schedule ping every 10 seconds
        pingScheduler.scheduleAtFixedRate(() -> {
            try {
                if (session != null && session.isOpen()) {
                    session.getBasicRemote().sendText("ping-wss");
                    logger.info("Ping sent.");
                }
            } catch (Exception e) {
                logger.severe("Error sending ping: " + e.getMessage());
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    @OnOpen
    public void onOpen(Session session) {
        WebSocketTestClient.session = session;
        logger.info("Connected to the WebSocket server");
        latch.countDown();  // Release the latch after connection is established
    }

    @OnMessage
    public void onMessage(String message) {
        logger.info("Received message: " + message);
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        logger.info("Closed WebSocket connection: " + closeReason.getReasonPhrase());
        pingScheduler.shutdown();  // Shutdown the ping scheduler when connection closes
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        logger.severe("Error in WebSocket communication: " + throwable.getMessage());
    }
}
