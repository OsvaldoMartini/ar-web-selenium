package com.allinweb.ch.tests;

import com.allinweb.ch.component.pane.base.ARPane;
import java.net.URI;
import javafx.scene.layout.Pane;
import javax.websocket.*;

@ClientEndpoint
public class WebSocketClient extends ARPane {

    private Session session;

    // Called when the WebSocket connection is opened
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println("Connected to WebSocket server at: " + session.getRequestURI());

        // Sending a message to the server
        try {
            //            session.getBasicRemote().sendText("Hello from client!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Called when a message is received from the server
    @OnMessage
    public void onMessage(String message) {
        System.out.println("Received message from server: " + message);
    }

    // Called when the WebSocket connection is closed
    @OnClose
    public void onClose(Session session) {
        System.out.println("Connection closed.");
    }

    // Called when an error occurs
    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Error: " + throwable.getMessage());
    }

    // Method to connect to WebSocket Server
    public static void connect(String uri) {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(WebSocketClient.class, new URI(uri));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String serverUri =
                "ws://localhost:8181/websocket?sessionId=scannerReceiver"; // Replace with your actual WebSocket server
        // URL
        connect(serverUri);
    }

    @Override
    public Pane getPaneReference() {
        return null;
    }

    @Override
    public void initUIComponents() {}

    @Override
    public void initUIBehaviour() {}
}
