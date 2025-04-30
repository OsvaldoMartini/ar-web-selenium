package com.allinweb.ch.socket;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

// Simple WebSocket server endpoint (for demonstration)
@ServerEndpoint("/websocket")
public class SimpleWebSocketServer2 {

    private static final ConcurrentHashMap<String, Session> activeSessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("WebSocket connection opened: " + session.getId());
        activeSessions.put(session.getId(), session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        System.out.println("Received message: " + message + " from " + session.getId());
        // Echo the message back to the sender
        try {
            session.getBasicRemote().sendText("Echo: " + message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        System.err.println("WebSocket error for session " + session.getId() + ": " + error.getMessage());
        error.printStackTrace();
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("WebSocket connection closed: " + session.getId() + " with reason: " + closeReason);
        activeSessions.remove(session.getId());
    }

    // Method to get all active sessions
    public Collection<Session> getAllSessions() {
        return activeSessions.values();
    }
}
