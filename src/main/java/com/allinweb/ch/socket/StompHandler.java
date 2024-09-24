package com.allinweb.ch.socket;

import java.io.IOException;
import javax.websocket.Session;

public class StompHandler {

    public void handleFrame(StompFrame frame, Session session) throws IOException {
        switch (frame.getCommand()) {
            case "CONNECT":
                handleConnect(frame, session);
                break;
            case "SEND":
                handleSend(frame, session);
                break;
            case "SUBSCRIBE":
                handleSubscribe(frame, session);
                break;
            case "DISCONNECT":
                handleDisconnect(frame, session);
                break;
            default:
                System.out.println("Unknown STOMP command: " + frame.getCommand());
        }
    }

    private void handleConnect(StompFrame frame, Session session) throws IOException {
        System.out.println("Client connected: " + session.getId());
        // Send a CONNECTED frame back
        StompFrame connectedFrame = new StompFrame("CONNECTED");
        connectedFrame.setHeader("version", "1.2");
        session.getBasicRemote().sendText(connectedFrame.toString());
    }

    private void handleSend(StompFrame frame, Session session) {
        System.out.println("Message sent from client: " + frame.getBody());
        // Handle message sending (e.g., broadcast to other clients, store in a queue, etc.)
    }

    private void handleSubscribe(StompFrame frame, Session session) {
        String destination = frame.getHeader("destination");
        System.out.println("Client subscribed to: " + destination);
        // Handle subscriptions (e.g., register the client to receive messages on a topic)
    }

    private void handleDisconnect(StompFrame frame, Session session) throws IOException {
        System.out.println("Client disconnected: " + session.getId());
        // Handle disconnection (e.g., clean up resources, notify other clients, etc.)
    }
}
