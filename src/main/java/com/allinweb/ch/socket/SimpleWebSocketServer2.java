package com.allinweb.ch.socket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/websocket2")
public class SimpleWebSocketServer2 {

    // Store all connected sessions
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());

    private final Gson gson = new Gson();

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("New connection established: " + session.getId());
        sendMessage(session, "Welcome! You are connected.");
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            // Parse the message as JSON (if applicable)
            JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
            String type = jsonMessage.has("type") ? jsonMessage.get("type").getAsString() : "unknown";

            // Handle the message based on its type
            switch (type) {
                case "greet":
                    String name =
                            jsonMessage.has("name") ? jsonMessage.get("name").getAsString() : "guest";
                    sendMessage(session, "Hello, " + name + "!");
                    break;
                case "broadcast":
                    String broadcastMessage = jsonMessage.has("message")
                            ? jsonMessage.get("message").getAsString()
                            : "";
                    broadcastMessage("Broadcast: " + broadcastMessage);
                    break;
                default:
                    sendMessage(session, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            sendMessage(session, "Error processing message: " + e.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        System.out.println("Connection closed: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("Error in session " + session.getId() + ": " + throwable.getMessage());
    }

    private void sendMessage(Session session, String message) {
        try {
            if (session.isOpen()) {
                session.getBasicRemote().sendText(message);
            }
        } catch (IOException e) {
            System.err.println("Error sending message to session " + session.getId() + ": " + e.getMessage());
        }
    }

    private void broadcastMessage(String message) {
        synchronized (sessions) {
            for (Session session : sessions) {
                sendMessage(session, message);
            }
        }
    }
}
