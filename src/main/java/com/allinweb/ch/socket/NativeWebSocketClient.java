package com.allinweb.ch.socket;

import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.websocket.*;

@ClientEndpoint
public class NativeWebSocketClient {

    private static CountDownLatch latch = new CountDownLatch(1); // Synchronization for WebSocket connection
    private static SSLContext sslContext;

    public static void main(String[] args) {
        try {
            String uri = "wss://localhost:61757/websocket"; // The WebSocket server URL
            latch = new CountDownLatch(1);
            // Load the keystore
            InputStream keyStoreStream = WebSocketTestClient.class.getResourceAsStream("/keystore.jks");
            if (keyStoreStream == null) {
                throw new Exception("Keystore not found.");
            }
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(keyStoreStream, "Martini!383940".toCharArray());

            // Load the truststore.  This is crucial for the client to trust the server's certificate.
            InputStream trustStoreStream =
                    WebSocketTestClient.class.getResourceAsStream("/truststore.jks"); // Create a truststore.jks
            if (trustStoreStream == null) {
                throw new Exception("Truststore not found.");
            }
            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(trustStoreStream, "Martini!383940".toCharArray());

            // Set up SSL context
            sslContext = SSLContext.getInstance("TLSv1.2"); // Specify the protocol
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore); // Use the truststore here
            KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, "Martini!383940".toCharArray());
            sslContext.init(
                    keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());

            // Connect to WebSocket server
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            System.out.println("Connecting to " + uri);
            Session session = container.connectToServer(NativeWebSocketClient.class, URI.create(uri));

            latch.await(); // Wait for connection to be established

            // Sending a message to the server once connected
            session.getBasicRemote().sendText("Hello, WebSocket server!");

            // After sending, you can close the connection
            session.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("Connected to WebSocket server: " + session.getId());
        latch.countDown(); // Unlock the latch once the connection is established
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("Received message from server: " + message);
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("WebSocket closed: " + closeReason.getReasonPhrase());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        throwable.printStackTrace();
    }
}
