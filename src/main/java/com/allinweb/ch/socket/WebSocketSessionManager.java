package com.allinweb.ch.socket;

import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.SplitDTO;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketSessionManager {

    private static final ConcurrentHashMap<String, Session> activeSessions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Session, String> sessionIds = new ConcurrentHashMap<>();
    private static final Map<Session, Semaphore> outboundGates = new WeakHashMap<>();
    private static final Object sessionRegistryLock = new Object();
    protected static volatile WebSocketSessionManager instance;

    // Private constructor to prevent instantiation
    public WebSocketSessionManager() {}

    public static WebSocketSessionManager getInstance() {
        if (instance == null) {
            synchronized (WebSocketSessionManager.class) {
                if (instance == null) {
                    instance = new WebSocketSessionManager();
                }
            }
        }
        return instance;
    }

    private BiConsumer<String, String> logFn;

    public void setLogger(BiConsumer<String, String> logger) {
        this.logFn = logger;
    }

    private void appendLog(String msg, String level) {
        if (logFn != null) logFn.accept(msg, level);
    }

    /**
     * Registers a transport session without replacing an already-live connection.
     *
     * @return {@code true} when the session is registered, otherwise {@code false}
     *         when another open transport already owns the logical session ID.
     */
    public static boolean addSession(String sessionId, Session session) {
        if (Strings.isNullOrEmpty(sessionId) || session == null) {
            return false;
        }

        synchronized (sessionRegistryLock) {
            String existingSessionId = sessionIds.get(session);
            if (existingSessionId != null && !existingSessionId.equals(sessionId)) {
                return false;
            }

            Session existing = activeSessions.get(sessionId);
            if (existing != null && existing != session && existing.isOpen()) {
                return false;
            }

            if (existing != null && existing != session) {
                sessionIds.remove(existing, sessionId);
            }
            activeSessions.put(sessionId, session);
            sessionIds.put(session, sessionId);
            return true;
        }
    }

    /**
     * Registers a transport session, closing out whichever transport currently owns the logical
     * session ID instead of rejecting the new one. Used for sessions where only one live workspace
     * makes sense at a time (e.g. "botJobTasks" -- the backend has a single active Bot Job
     * workspace) so that opening it in a new tab takes over rather than being refused.
     */
    public static void takeOverSession(String sessionId, Session newSession) {
        if (Strings.isNullOrEmpty(sessionId) || newSession == null) {
            return;
        }
        synchronized (sessionRegistryLock) {
            Session existing = activeSessions.get(sessionId);
            if (existing != null && existing != newSession) {
                sessionIds.remove(existing, sessionId);
                if (existing.isOpen()) {
                    try {
                        existing.close(new CloseReason(
                                CloseReason.CloseCodes.NORMAL_CLOSURE, "Superseded by a newer tab"));
                    } catch (IOException e) {
                        log.debug("Error closing superseded session {}: {}", sessionId, e.getMessage());
                    }
                }
            }
            activeSessions.put(sessionId, newSession);
            sessionIds.put(newSession, sessionId);
        }
    }

    /**
     * Removes only the exact logical-ID/transport pair. A delayed close callback
     * from an older connection therefore cannot remove a newer replacement.
     */
    public static boolean removeSession(String sessionId, Session session) {
        if (Strings.isNullOrEmpty(sessionId) || session == null) {
            return false;
        }
        synchronized (sessionRegistryLock) {
            if (!activeSessions.remove(sessionId, session)) {
                return false;
            }
            sessionIds.remove(session, sessionId);
            return true;
        }
    }

    /**
     * Retires the exact transport that currently owns a logical session ID.
     *
     * <p>The registry entry is removed before closing the socket so a replacement React transport can
     * connect immediately. A delayed {@code onClose} callback from this transport remains harmless
     * because {@link #removeSession(String, Session)} only removes an exact registered pair.
     */
    public static boolean closeSession(String sessionId) {
        if (Strings.isNullOrEmpty(sessionId)) {
            return false;
        }

        Session session;
        synchronized (sessionRegistryLock) {
            session = activeSessions.remove(sessionId);
            if (session == null) {
                return false;
            }
            sessionIds.remove(session, sessionId);
        }

        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException | RuntimeException error) {
            log.debug("Unable to close retired session {}: {}", sessionId, error.getMessage());
        }
        return true;
    }

    public static Session getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    public static ConcurrentHashMap<String, Session> getAllSessions() {
        return activeSessions;
    }

    public static boolean containsSession(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }

    public static boolean isSessionOpen(String sessionId) {
        Session session = activeSessions.get(sessionId);
        return session != null && session.isOpen();
    }

    public static void sendMessageJson(
            int homeBankingId, Session session, String sessionId, String body, String operationId) {
        if (session != null && session.isOpen()) {
            try {
                JsonObject jsonMessage = new JsonObject();
                jsonMessage.addProperty("homeBankingId", homeBankingId);
                jsonMessage.addProperty("sessionId", sessionId);
                jsonMessage.addProperty("body", body);
                if (operationId != null && !operationId.isEmpty()) {
                    jsonMessage.addProperty("operationId", operationId);
                }
                sendText(session, jsonMessage.toString());
            } catch (IOException e) {
                log.error("Error sending message to session " + sessionId + ": " + e.getMessage());
            }
        } else {
            log.error("Session " + sessionId + " not found or closed.");
        }
    }

    // Method to get the session ID based on the session object
    public String getSessionIdBySession(Session session) {
        return session == null ? null : sessionIds.get(session);
    }

    static void clearSessions() {
        synchronized (sessionRegistryLock) {
            activeSessions.clear();
            sessionIds.clear();
        }
        synchronized (outboundGates) {
            outboundGates.clear();
        }
    }

    public void broadcastMessageToAll(int homeBankingId, String message) {
        for (Session session : getAllSessions().values()) { // Looping correctly
            if (session.isOpen()) {
                sendMessageJson(homeBankingId, session, "Broad-All", message, null);
            }
        }
    }

    public void broadcastJsonToAll(int homeBankingId, String body, String operationId) {
        for (Map.Entry<String, Session> entry : activeSessions.entrySet()) {
            Session session = entry.getValue();
            if (session.isOpen()) {
                sendMessageJson(homeBankingId, session, entry.getKey(), body, operationId);
            }
        }
    }

    public void broadcastMessageToAll(int homeBankingId, String broadTo, String body, String operationId) {
        for (Map.Entry<String, Session> entry : activeSessions.entrySet()) {
            String sessionKey = entry.getKey();
            Session session = entry.getValue();

            // Ensure session is open before sending
            if (session.isOpen() && sessionKey.contains(broadTo)) {
                try {
                    sendMessageJson(homeBankingId, session, entry.getKey(), body, operationId);
                } catch (Exception e) {
                    log.error("Failed to send message to session: " + sessionKey);
                    e.printStackTrace();
                }
            }
        }
    }

    // Method to send a message to a specific session ID
    public void sendMessageJson(String sessionId, String message) {
        Session session = getAllSessions().get(sessionId);

        if (session != null && session.isOpen()) {
            try {
                sendText(session, message);
            } catch (IOException e) {
                log.debug("Cannot send to session {}: {}", sessionId, e.getMessage());
            }
        } else {
            // ARWeb may not be running - silently skip
            log.debug("Session {} not available - ARWeb may be offline.", sessionId);
        }
    }

    // Method to send a message to a specific session ID
    public Session sendMessageJson(int homeBankingId, String sessionId, String body, String operationId) {
        Session session = getAllSessions().get(sessionId);

        if (session != null && session.isOpen()) {
            try {
                JsonObject jsonMessage = new JsonObject();
                jsonMessage.addProperty("body", body);
                jsonMessage.addProperty("sessionId", sessionId);
                jsonMessage.addProperty("homeBankingId", homeBankingId);
                if (operationId != null && !operationId.isEmpty()) {
                    jsonMessage.addProperty("operationId", operationId);
                }
                sendText(session, jsonMessage.toString());
                return session;
            } catch (IOException e) {
                log.debug("Cannot send to session {}: {}", sessionId, e.getMessage());
            }
        } else {
            // ARWeb may not be running - silently skip
            log.debug("Session {} not available - ARWeb may be offline.", sessionId);
        }
        return null;
    }

    //    public static void sendMessageJson(int homeBankingId, String sessionId, String msg1, String msg2) {
    //        Session session = getAllSessions().get(sessionId);
    //
    //        if (session != null && session.isOpen()) {
    //            try {
    //                JsonObject jsonMessage = new JsonObject();
    //                jsonMessage.addProperty("body", msg1);
    //                jsonMessage.addProperty("sessionId", sessionId);
    //                jsonMessage.addProperty("homeBankingId", homeBankingId);
    //                if (msg2 != null && !msg2.isEmpty()) {
    //                    jsonMessage.addProperty("operationId", msg2);
    //                }
    //                session.getBasicRemote().sendText(jsonMessage.toString());
    //            } catch (IOException e) {
    //                log.error("Error sending message to session " + sessionId + ": " + e.getMessage());
    //            }
    //        } else {
    //            log.error("Session " + sessionId + " not found or closed.");
    //        }
    //    }

    private void sendMessageJson(Session session, String msg1, String msg2) {
        if (session != null && session.isOpen()) {
            try {
                // Create a JSON object with the key "body" and the provided message
                JsonObject jsonMessage = new JsonObject();
                jsonMessage.addProperty("body", msg1);
                if (!Strings.isNullOrEmpty(msg2)) {
                    jsonMessage.addProperty("footer", msg2);
                }
                // Convert the JSON object to a string
                String jsonString = jsonMessage.toString();

                // Send the JSON string over WebSocket
                sendText(session, jsonString);
            } catch (IOException e) {
                log.error("Error sending message to session " + session.getId() + ": " + e.getMessage());
            }
        }
    }

    public void sendChunks(
            List<ElementDTO> elements, int chunkSize, SplitDTO splitDTO, String server, String routingKey) {
        if (elements == null || elements.isEmpty()) {
            appendLog("No elements to send.", "warn");
            return;
        }

        appendLog("Sending " + elements.size() + " elements in chunks of " + chunkSize, "info");

        for (int i = 0; i < elements.size(); i += chunkSize) {

            int end = Math.min(i + chunkSize, elements.size());
            List<ElementDTO> chunk = elements.subList(i, end);

            // update DTO
            splitDTO.setElementDetails(chunk.toArray(new ElementDTO[0]));

            // serialize
            String jsonData = new Gson().toJson(splitDTO);

            // log
            appendLog("Sending chunk " + (i / chunkSize + 1) + " containing " + chunk.size() + " elements", "info");

            // send
            sendMessageJson(0, server, jsonData, routingKey);
        }
    }

    /**
     * Serializes every blocking/async write for one JSR-356 transport. Jetty rejects concurrent
     * writes with messages such as "Blocking message pending ... for BLOCKING"; scanner status,
     * chunks, OCR callbacks, pings, and retarget events can legitimately originate on different
     * worker threads, so they must share one outbound gate.
     */
    public static void sendText(Session session, String message) throws IOException {
        if (session == null) throw new IOException("WebSocket session is unavailable");
        Semaphore gate = outboundGate(session);
        boolean acquired = false;
        try {
            gate.acquire();
            acquired = true;
            if (!session.isOpen()) throw new IOException("WebSocket session is closed");
            try {
                session.getBasicRemote().sendText(message);
            } catch (RuntimeException sendFailure) {
                throw new IOException("WebSocket transport rejected the outbound message", sendFailure);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to send a WebSocket message", interrupted);
        } finally {
            if (acquired) gate.release();
        }
    }

    static CompletableFuture<Void> sendTextAcknowledged(Session session, String message) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        if (session == null || !session.isOpen()) {
            completion.completeExceptionally(new IOException("WebSocket session is unavailable"));
            return completion;
        }

        Semaphore gate = outboundGate(session);
        try {
            gate.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            completion.completeExceptionally(
                    new IOException("Interrupted while waiting to send a WebSocket message", interrupted));
            return completion;
        }

        if (!session.isOpen()) {
            gate.release();
            completion.completeExceptionally(new IOException("WebSocket session is closed"));
            return completion;
        }

        AtomicBoolean released = new AtomicBoolean();
        Runnable releaseGate = () -> {
            if (released.compareAndSet(false, true)) {
                gate.release();
            }
        };
        try {
            session.getAsyncRemote().sendText(message, result -> {
                releaseGate.run();
                if (result.isOK()) {
                    completion.complete(null);
                } else {
                    Throwable failure = result.getException();
                    completion.completeExceptionally(failure == null
                            ? new IOException("Unable to send WebSocket message")
                            : failure);
                }
            });
        } catch (RuntimeException sendFailure) {
            releaseGate.run();
            completion.completeExceptionally(sendFailure);
        }
        return completion;
    }

    private static Semaphore outboundGate(Session session) {
        synchronized (outboundGates) {
            return outboundGates.computeIfAbsent(session, ignored -> new Semaphore(1, true));
        }
    }
}
