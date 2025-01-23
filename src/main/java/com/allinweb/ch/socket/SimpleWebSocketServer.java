package com.allinweb.ch.socket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/websocket")
public class SimpleWebSocketServer {

    // Store all connected sessions
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
    private final Gson gson = new Gson();

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("New connection: Session ID = " + session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            // Parse the incoming message (assuming JSON format)
            JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
            String type = jsonMessage.has("type") ? jsonMessage.get("type").getAsString() : "unknown";

            // Process the message based on its type
            switch (type) {
                case "broadcast":
                    String broadcastMessage = jsonMessage.get("body").getAsString();
                    broadcastMessageToAll(broadcastMessage);
                    break;
                case "echo":
                    sendMessageJson(session, "Echo: " + jsonMessage.get("body").getAsString());
                    break;
                default:
                    sendMessageJson(session, "Unknown message type: " + type);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            sendMessageJson(session, "Error processing message.");
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        System.out.println("Connection closed: Session ID = " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("Error in session " + session.getId() + ": " + throwable.getMessage());
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing session: " + e.getMessage());
        }
    }

    private void broadcastMessageToAll(String message) {
        synchronized (sessions) {
            for (Session session : sessions) {
                if (session.isOpen()) {
                    sendMessageJson(session, message);
                }
            }
        }
    }

    private void sendMessage(Session session, String message) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            System.err.println("Error sending message to session " + session.getId() + ": " + e.getMessage());
        }
    }

    private void sendMessageJson(Session session, String message) {
        try {
            // Create a JSON object with the key "body" and the provided message
            JsonObject jsonMessage = new JsonObject();
            jsonMessage.addProperty("body", message);

            // Convert the JSON object to a string
            String jsonString = jsonMessage.toString();

            // Send the JSON string over WebSocket
            session.getBasicRemote().sendText(jsonString);
        } catch (IOException e) {
            System.err.println("Error sending message to session " + session.getId() + ": " + e.getMessage());
        }
    }
}
