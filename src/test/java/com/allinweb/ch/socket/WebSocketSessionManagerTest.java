package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebSocketSessionManagerTest {

    private final WebSocketSessionManager manager = new WebSocketSessionManager();

    @BeforeEach
    void startWithEmptyRegistry() {
        WebSocketSessionManager.clearSessions();
    }

    @AfterEach
    void clearRegistry() {
        WebSocketSessionManager.clearSessions();
    }

    @Test
    void rejectsDuplicateLiveSessionWithoutReplacingOriginal() {
        Session original = openSession();
        Session duplicate = openSession();

        assertTrue(WebSocketSessionManager.addSession("botJobTasks", original));
        assertFalse(WebSocketSessionManager.addSession("botJobTasks", duplicate));

        assertSame(original, WebSocketSessionManager.getSession("botJobTasks"));
        assertEquals("botJobTasks", manager.getSessionIdBySession(original));
        assertNull(manager.getSessionIdBySession(duplicate));
    }

    @Test
    void replacesClosedSessionAndRemovesOnlyExactTransportPair() {
        Session original = mock(Session.class);
        Session replacement = openSession();
        when(original.isOpen()).thenReturn(false);

        assertTrue(WebSocketSessionManager.addSession("scannerGrid", original));
        assertTrue(WebSocketSessionManager.addSession("scannerGrid", replacement));
        assertFalse(WebSocketSessionManager.removeSession("scannerGrid", original));

        assertSame(replacement, WebSocketSessionManager.getSession("scannerGrid"));
        assertNull(manager.getSessionIdBySession(original));
        assertEquals("scannerGrid", manager.getSessionIdBySession(replacement));
        assertTrue(WebSocketSessionManager.removeSession("scannerGrid", replacement));
        assertNull(WebSocketSessionManager.getSession("scannerGrid"));
        assertNull(manager.getSessionIdBySession(replacement));
    }

    @Test
    void rejectsInvalidRegistrationAndRemovalInputs() {
        Session session = openSession();

        assertFalse(WebSocketSessionManager.addSession("", session));
        assertFalse(WebSocketSessionManager.addSession("valid", null));
        assertFalse(WebSocketSessionManager.removeSession("", session));
        assertFalse(WebSocketSessionManager.removeSession("valid", null));
    }

    @Test
    void oneTransportCannotClaimTwoLogicalSessionIds() {
        Session session = openSession();

        assertTrue(WebSocketSessionManager.addSession("mainDashboard", session));
        assertFalse(WebSocketSessionManager.addSession("botJobTasks", session));
        assertNull(WebSocketSessionManager.getSession("botJobTasks"));
        assertEquals("mainDashboard", manager.getSessionIdBySession(session));
    }

    private Session openSession() {
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
