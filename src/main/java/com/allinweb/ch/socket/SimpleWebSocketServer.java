//package com.allinweb.ch.socket;
//
//import java.io.IOException;
//import java.util.Collections;
//import java.util.HashSet;
//import java.util.Set;
//import javax.websocket.OnClose;
//import javax.websocket.OnMessage;
//import javax.websocket.OnOpen;
//import javax.websocket.Session;
//import javax.websocket.server.ServerEndpoint;
//
//@ServerEndpoint("/websocket2")
//public class SimpleWebSocketServer {
//
//    private static Set<Session> clients = Collections.synchronizedSet(new HashSet<>());
//
//    @OnOpen
//    public void onOpen(Session session) {
//        clients.add(session);
//        System.out.println("New client connected: " + session.getId());
//    }
//
//    @OnMessage
//    public void onMessage(String message, Session session) throws IOException {
//        System.out.println("Received message: " + message);
//
//        // Broadcast the message to all connected clients
//        for (Session client : clients) {
//            if (client.isOpen() && !client.equals(session)) {
//                client.getBasicRemote().sendText("Echo: " + message);
//            }
//        }
//    }
//
//    @OnClose
//    public void onClose(Session session) {
//        clients.remove(session);
//        System.out.println("Client disconnected: " + session.getId());
//    }
//}
