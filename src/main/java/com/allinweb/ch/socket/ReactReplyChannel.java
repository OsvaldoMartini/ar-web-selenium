package com.allinweb.ch.socket;

/**
 * Tracks which WebSocket session is currently being served by {@link SimpleWebSocketServer#onMessage}
 * on the calling thread. Since the JCEF-embedded browser shell (and its direct JS-injection push) was
 * removed, session-switch replies like {@code react.session.open} must be delivered back on whichever
 * session actually sent the triggering request -- that session is the only one guaranteed to be open --
 * rather than the (possibly not-yet-connected) target session being navigated to.
 */
public final class ReactReplyChannel {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private ReactReplyChannel() {}

    static void set(String sessionId) {
        CURRENT.set(sessionId);
    }

    static void clear() {
        CURRENT.remove();
    }

    public static String getOrDefault(String fallback) {
        String current = CURRENT.get();
        return current != null ? current : fallback;
    }
}
