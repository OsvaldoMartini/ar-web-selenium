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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.websocket.RemoteEndpoint;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
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

    @Test
    void serializesConcurrentBlockingWritesForTheSameTransport() throws Exception {
        Session session = openSession();
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        when(session.getBasicRemote()).thenReturn(remote);
        CountDownLatch firstWriteEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstWrite = new CountDownLatch(1);
        CountDownLatch secondCallStarted = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
                    if (writes.incrementAndGet() == 1) {
                        firstWriteEntered.countDown();
                        assertTrue(releaseFirstWrite.await(5, TimeUnit.SECONDS));
                    }
                    return null;
                })
                .when(remote)
                .sendText(org.mockito.ArgumentMatchers.anyString());

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                WebSocketSessionManager.sendText(session, "first");
                return null;
            });
            assertTrue(firstWriteEntered.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> {
                secondCallStarted.countDown();
                WebSocketSessionManager.sendText(session, "second");
                return null;
            });
            assertTrue(secondCallStarted.await(5, TimeUnit.SECONDS));

            Thread.sleep(75);
            assertEquals(1, writes.get(), "The second Jetty BasicRemote write must wait at the shared gate");
            releaseFirstWrite.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(2, writes.get());
        } finally {
            releaseFirstWrite.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void asyncAcknowledgedWriteHoldsGateUntilJettyCallbackCompletes() throws Exception {
        Session session = openSession();
        RemoteEndpoint.Async async = mock(RemoteEndpoint.Async.class);
        RemoteEndpoint.Basic basic = mock(RemoteEndpoint.Basic.class);
        when(session.getAsyncRemote()).thenReturn(async);
        when(session.getBasicRemote()).thenReturn(basic);
        java.util.concurrent.atomic.AtomicReference<SendHandler> handler =
                new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
                    handler.set(invocation.getArgument(1));
                    return null;
                })
                .when(async)
                .sendText(org.mockito.ArgumentMatchers.eq("async"), org.mockito.ArgumentMatchers.any());

        var acknowledged = WebSocketSessionManager.sendTextAcknowledged(session, "async");
        assertFalse(acknowledged.isDone());

        var executor = Executors.newSingleThreadExecutor();
        try {
            var blocked = executor.submit(() -> {
                WebSocketSessionManager.sendText(session, "blocking");
                return null;
            });
            Thread.sleep(75);
            assertFalse(blocked.isDone(), "A blocking write must wait for the async acknowledgement");

            handler.get().onResult(new SendResult());
            acknowledged.get(5, TimeUnit.SECONDS);
            blocked.get(5, TimeUnit.SECONDS);
            verify(basic).sendText("blocking");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void synchronousAsyncFailureReleasesSharedGate() throws Exception {
        Session session = openSession();
        RemoteEndpoint.Async async = mock(RemoteEndpoint.Async.class);
        RemoteEndpoint.Basic basic = mock(RemoteEndpoint.Basic.class);
        when(session.getAsyncRemote()).thenReturn(async);
        when(session.getBasicRemote()).thenReturn(basic);
        org.mockito.Mockito.doThrow(new IllegalStateException("transport failed"))
                .when(async)
                .sendText(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());

        assertTrue(WebSocketSessionManager.sendTextAcknowledged(session, "async").isCompletedExceptionally());
        WebSocketSessionManager.sendText(session, "after-failure");

        verify(basic).sendText("after-failure");
    }

    private Session openSession() {
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
