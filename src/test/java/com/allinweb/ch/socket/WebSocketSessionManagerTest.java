package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
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

        assertTrue(WebSocketSessionManager.addSession(ScannerWorkspaceSessions.BOT_JOB_TASKS, original));
        assertFalse(WebSocketSessionManager.addSession(ScannerWorkspaceSessions.BOT_JOB_TASKS, duplicate));

        assertSame(original, WebSocketSessionManager.getSession(ScannerWorkspaceSessions.BOT_JOB_TASKS));
        assertEquals(ScannerWorkspaceSessions.BOT_JOB_TASKS, manager.getSessionIdBySession(original));
        assertNull(manager.getSessionIdBySession(duplicate));
    }

    @Test
    void replacesClosedSessionAndRemovesOnlyExactTransportPair() {
        Session original = mock(Session.class);
        Session replacement = openSession();
        when(original.isOpen()).thenReturn(false);

        assertTrue(WebSocketSessionManager.addSession(ScannerWorkspaceSessions.SCANNER_GRID, original));
        assertTrue(WebSocketSessionManager.addSession(ScannerWorkspaceSessions.SCANNER_GRID, replacement));
        assertFalse(WebSocketSessionManager.removeSession(ScannerWorkspaceSessions.SCANNER_GRID, original));

        assertSame(replacement, WebSocketSessionManager.getSession(ScannerWorkspaceSessions.SCANNER_GRID));
        assertNull(manager.getSessionIdBySession(original));
        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, manager.getSessionIdBySession(replacement));
        assertTrue(WebSocketSessionManager.removeSession(ScannerWorkspaceSessions.SCANNER_GRID, replacement));
        assertNull(WebSocketSessionManager.getSession(ScannerWorkspaceSessions.SCANNER_GRID));
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
        assertFalse(WebSocketSessionManager.addSession(ScannerWorkspaceSessions.BOT_JOB_TASKS, session));
        assertNull(WebSocketSessionManager.getSession(ScannerWorkspaceSessions.BOT_JOB_TASKS));
        assertEquals("mainDashboard", manager.getSessionIdBySession(session));
    }

    @Test
    void retiringLogicalSessionClosesAndUnregistersItsExactTransport() throws Exception {
        Session session = openSession();
        assertTrue(WebSocketSessionManager.addSession(ScannerWorkspaceSessions.BOT_JOB_TASKS, session));

        assertTrue(WebSocketSessionManager.closeSession(ScannerWorkspaceSessions.BOT_JOB_TASKS));
        assertFalse(WebSocketSessionManager.closeSession(ScannerWorkspaceSessions.BOT_JOB_TASKS));

        verify(session).close();
        assertNull(WebSocketSessionManager.getSession(ScannerWorkspaceSessions.BOT_JOB_TASKS));
        assertNull(manager.getSessionIdBySession(session));

        Session replacement = openSession();
        assertTrue(WebSocketSessionManager.addSession(ScannerWorkspaceSessions.BOT_JOB_TASKS, replacement));
        assertSame(replacement, WebSocketSessionManager.getSession(ScannerWorkspaceSessions.BOT_JOB_TASKS));
    }

    private Session openSession() {
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
