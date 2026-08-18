package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BotJobDetailsWindowCoordinatorTest {

    private static final String FIRST_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String SECOND_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

    @BeforeEach
    void startWithEmptySocketRegistry() {
        WebSocketSessionManager.clearSessions();
    }

    @AfterEach
    void clearSocketRegistry() {
        WebSocketSessionManager.clearSessions();
    }

    @Test
    void firstOpenLaunchesOneWindowWithAnUnguessableControlSession() {
        AtomicInteger launchedBotJobId = new AtomicInteger();
        AtomicReference<String> launchedSession = new AtomicReference<>();
        BotJobDetailsWindowCoordinator coordinator = coordinator(
                () -> FIRST_ID,
                (botJobId, controlSessionId) -> {
                    launchedBotJobId.set(botJobId);
                    launchedSession.set(controlSessionId);
                    return true;
                },
                sessionId -> false,
                (sessionId, target) -> true);

        BotJobDetailsWindowCoordinator.OpenResult opened = coordinator.open(target(42, 1, 7));

        assertTrue(opened.ok());
        assertTrue(opened.launched());
        assertFalse(opened.alreadyOpen());
        assertFalse(opened.targetPublished());
        assertEquals(42, launchedBotJobId.get());
        assertEquals(BotJobDetailsWindowCoordinator.CONTROL_SESSION_PREFIX + FIRST_ID, launchedSession.get());
        assertEquals(launchedSession.get(), opened.controlSessionId());
        assertTrue(BotJobDetailsWindowCoordinator.isControlSessionId(opened.controlSessionId()));
        assertEquals(1, coordinator.activeWindowCount());
        assertEquals(target(42, 1, 7), coordinator.activeTarget());
    }

    @Test
    void connectedWindowPublishesLatestTargetAndNeverLaunchesASecondWindow() {
        AtomicBoolean connected = new AtomicBoolean(false);
        AtomicInteger launches = new AtomicInteger();
        List<BotJobDetailsWindowCoordinator.Target> published = new ArrayList<>();
        BotJobDetailsWindowCoordinator coordinator = coordinator(
                () -> FIRST_ID,
                (botJobId, controlSessionId) -> {
                    launches.incrementAndGet();
                    return true;
                },
                sessionId -> connected.get(),
                (sessionId, target) -> published.add(target));

        BotJobDetailsWindowCoordinator.OpenResult first = coordinator.open(target(42, 1, 7));
        connected.set(true);
        assertTrue(coordinator.connected(first.controlSessionId()));

        BotJobDetailsWindowCoordinator.OpenResult switched = coordinator.open(target(43, 2, 8));

        assertTrue(switched.ok());
        assertFalse(switched.launched());
        assertTrue(switched.alreadyOpen());
        assertTrue(switched.retargeted());
        assertTrue(switched.targetPublished());
        assertEquals(first.controlSessionId(), switched.controlSessionId());
        assertEquals(1, launches.get());
        assertEquals(List.of(target(42, 1, 7), target(43, 2, 8)), published);
        assertEquals(target(43, 2, 8), coordinator.activeTarget());
    }

    @Test
    void requestsDuringInitialConnectionGraceUpdateLatestTargetWithoutRelaunching() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        AtomicInteger launches = new AtomicInteger();
        AtomicReference<BotJobDetailsWindowCoordinator.Target> published = new AtomicReference<>();
        BotJobDetailsWindowCoordinator coordinator = coordinator(
                () -> FIRST_ID,
                (botJobId, controlSessionId) -> {
                    launches.incrementAndGet();
                    return true;
                },
                clock,
                sessionId -> false,
                (sessionId, target) -> {
                    published.set(target);
                    return true;
                });

        BotJobDetailsWindowCoordinator.OpenResult first = coordinator.open(target(42, 1, 7));
        clock.advance(BotJobDetailsWindowCoordinator.INITIAL_CONNECTION_GRACE.minusMillis(1));
        BotJobDetailsWindowCoordinator.OpenResult latest = coordinator.open(target(99, 3, 10));

        assertFalse(latest.launched());
        assertTrue(latest.alreadyOpen());
        assertTrue(latest.retargeted());
        assertFalse(latest.targetPublished());
        assertEquals(first.controlSessionId(), latest.controlSessionId());
        assertEquals(1, launches.get());

        assertTrue(coordinator.connected(first.controlSessionId()));
        assertEquals(target(99, 3, 10), published.get());
    }

    @Test
    void altF4RecoveryWaitsForReconnectGraceThenRelaunchesOnceWithNewestTarget() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        AtomicBoolean connectionOpen = new AtomicBoolean(false);
        List<Integer> launchedJobs = new ArrayList<>();
        AtomicReference<Runnable> deferredRecovery = new AtomicReference<>();
        BotJobDetailsWindowCoordinator coordinator = new BotJobDetailsWindowCoordinator(
                (botJobId, controlSessionId) -> {
                    launchedJobs.add(botJobId);
                    return true;
                },
                () -> FIRST_ID,
                clock,
                sessionId -> connectionOpen.get(),
                (sessionId, target) -> true,
                BotJobDetailsWindowCoordinator.INITIAL_CONNECTION_GRACE,
                BotJobDetailsWindowCoordinator.RECONNECT_GRACE,
                (delay, task) -> deferredRecovery.set(task));

        BotJobDetailsWindowCoordinator.OpenResult first = coordinator.open(target(42, 1, 7));
        connectionOpen.set(true);
        assertTrue(coordinator.connected(first.controlSessionId()));
        connectionOpen.set(false);
        assertTrue(coordinator.disconnected(first.controlSessionId()));

        BotJobDetailsWindowCoordinator.OpenResult reopened = coordinator.open(target(43, 2, 8));
        BotJobDetailsWindowCoordinator.OpenResult duringRelaunch = coordinator.open(target(44, 3, 9));

        assertFalse(reopened.launched());
        assertTrue(reopened.alreadyOpen());
        assertEquals(first.controlSessionId(), reopened.controlSessionId());
        assertFalse(duringRelaunch.launched());
        assertEquals(first.controlSessionId(), duringRelaunch.controlSessionId());
        assertEquals(List.of(42), launchedJobs);

        clock.advance(BotJobDetailsWindowCoordinator.RECONNECT_GRACE);
        deferredRecovery.get().run();

        assertEquals(List.of(42, 44), launchedJobs);
        assertEquals(target(44, 3, 9), coordinator.activeTarget());
    }

    @Test
    void transientDisconnectReconnectsWithoutLaunchingADuplicateNativeWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        AtomicBoolean connectionOpen = new AtomicBoolean(false);
        AtomicInteger launches = new AtomicInteger();
        AtomicReference<Runnable> deferredRecovery = new AtomicReference<>();
        List<BotJobDetailsWindowCoordinator.Target> published = new ArrayList<>();
        BotJobDetailsWindowCoordinator coordinator = new BotJobDetailsWindowCoordinator(
                (botJobId, controlSessionId) -> {
                    launches.incrementAndGet();
                    return true;
                },
                () -> FIRST_ID,
                clock,
                sessionId -> connectionOpen.get(),
                (sessionId, target) -> published.add(target),
                BotJobDetailsWindowCoordinator.INITIAL_CONNECTION_GRACE,
                BotJobDetailsWindowCoordinator.RECONNECT_GRACE,
                (delay, task) -> deferredRecovery.set(task));

        BotJobDetailsWindowCoordinator.OpenResult first = coordinator.open(target(42, 1, 7));
        connectionOpen.set(true);
        coordinator.connected(first.controlSessionId());
        connectionOpen.set(false);
        coordinator.disconnected(first.controlSessionId());
        BotJobDetailsWindowCoordinator.OpenResult pending = coordinator.open(target(43, 2, 8));

        assertFalse(pending.launched());
        assertEquals(1, launches.get());
        connectionOpen.set(true);
        coordinator.connected(first.controlSessionId());
        clock.advance(BotJobDetailsWindowCoordinator.RECONNECT_GRACE);
        deferredRecovery.get().run();

        assertEquals(1, launches.get());
        assertEquals(target(43, 2, 8), published.get(published.size() - 1));
    }

    @Test
    void initialConnectionGraceExpiryRelaunchesTheSameLogicalWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        AtomicInteger launches = new AtomicInteger();
        BotJobDetailsWindowCoordinator coordinator = coordinator(
                () -> FIRST_ID,
                (botJobId, controlSessionId) -> {
                    launches.incrementAndGet();
                    return true;
                },
                clock,
                sessionId -> false,
                (sessionId, target) -> true);

        BotJobDetailsWindowCoordinator.OpenResult first = coordinator.open(target(42, 1, 7));
        clock.advance(BotJobDetailsWindowCoordinator.INITIAL_CONNECTION_GRACE);
        BotJobDetailsWindowCoordinator.OpenResult reopened = coordinator.open(target(43, 2, 8));

        assertTrue(reopened.launched());
        assertEquals(first.controlSessionId(), reopened.controlSessionId());
        assertEquals(2, launches.get());
        assertEquals(target(43, 2, 8), coordinator.activeTarget());
    }

    @Test
    void failedLaunchAndThrownLaunchReleaseTheGlobalReservation() {
        ArrayDeque<String> ids = new ArrayDeque<>(List.of(FIRST_ID, SECOND_ID, FIRST_ID));
        AtomicInteger attempts = new AtomicInteger();
        BotJobDetailsWindowCoordinator coordinator = coordinator(
                ids::remove,
                (botJobId, controlSessionId) -> attempts.incrementAndGet() > 1,
                sessionId -> false,
                (sessionId, target) -> true);

        BotJobDetailsWindowCoordinator.OpenResult unavailable = coordinator.open(target(42, 1, 7));
        assertFalse(unavailable.ok());
        assertEquals("", unavailable.controlSessionId());
        assertEquals(0, coordinator.activeWindowCount());

        BotJobDetailsWindowCoordinator.OpenResult opened = coordinator.open(target(43, 2, 8));
        assertTrue(opened.ok());
        assertEquals(BotJobDetailsWindowCoordinator.CONTROL_SESSION_PREFIX + SECOND_ID, opened.controlSessionId());

        assertTrue(coordinator.retire(opened.controlSessionId()));
        BotJobDetailsWindowCoordinator throwing = coordinator(
                ids::remove,
                (botJobId, controlSessionId) -> {
                    throw new IllegalStateException("launch failed");
                },
                sessionId -> false,
                (sessionId, target) -> true);
        assertThrows(IllegalStateException.class, () -> throwing.open(target(44, 3, 9)));
        assertEquals(0, throwing.activeWindowCount());
    }

    @Test
    void validatesAndOwnsOnlyTheExactControlSession() {
        String sessionId = BotJobDetailsWindowCoordinator.CONTROL_SESSION_PREFIX + FIRST_ID;
        String anotherSession = BotJobDetailsWindowCoordinator.CONTROL_SESSION_PREFIX + SECOND_ID;
        assertTrue(BotJobDetailsWindowCoordinator.isControlSessionId(sessionId));
        assertFalse(BotJobDetailsWindowCoordinator.isControlSessionId(null));
        assertFalse(BotJobDetailsWindowCoordinator.isControlSessionId("botJobTasks"));
        assertFalse(BotJobDetailsWindowCoordinator.isControlSessionId(
                BotJobDetailsWindowCoordinator.CONTROL_SESSION_PREFIX + "not-a-uuid"));
        assertFalse(BotJobDetailsWindowCoordinator.isControlSessionId(
                BotJobDetailsWindowCoordinator.CONTROL_SESSION_PREFIX + FIRST_ID.toUpperCase()));

        BotJobDetailsWindowCoordinator coordinator = coordinator(
                () -> FIRST_ID,
                (botJobId, controlSessionId) -> true,
                ignored -> false,
                (ignored, target) -> true);
        coordinator.open(target(42, 1, 7));

        assertTrue(coordinator.isActiveControlSession(sessionId));
        assertFalse(coordinator.isActiveControlSession(anotherSession));
        assertThrows(IllegalArgumentException.class, () -> coordinator.connected(anotherSession));
        assertThrows(IllegalArgumentException.class, () -> coordinator.connected("bad-session"));
        assertFalse(coordinator.disconnected(anotherSession));
        assertFalse(coordinator.retire(anotherSession));
        assertTrue(coordinator.retire(sessionId));
        assertEquals(0, coordinator.activeWindowCount());
    }

    @Test
    void concurrentOpenRequestsStillProduceOneNativeLaunch() throws Exception {
        AtomicInteger launches = new AtomicInteger();
        BotJobDetailsWindowCoordinator coordinator = coordinator(
                () -> FIRST_ID,
                (botJobId, controlSessionId) -> {
                    launches.incrementAndGet();
                    return true;
                },
                sessionId -> false,
                (sessionId, target) -> true);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<BotJobDetailsWindowCoordinator.OpenResult>> requests = new ArrayList<>();
            for (int index = 1; index <= 24; index++) {
                int botJobId = index;
                requests.add(() -> coordinator.open(target(botJobId, botJobId, botJobId)));
            }

            var results = executor.invokeAll(requests);
            for (var result : results) {
                assertEquals(
                        BotJobDetailsWindowCoordinator.CONTROL_SESSION_PREFIX + FIRST_ID,
                        result.get().controlSessionId());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, launches.get());
        assertEquals(1, coordinator.activeWindowCount());
    }

    @Test
    void productionPublisherSendsTypedAuthoritativeTargetOnTheExactSession() throws Exception {
        String sessionId = BotJobDetailsWindowCoordinator.CONTROL_SESSION_PREFIX + FIRST_ID;
        Session transport = mock(Session.class);
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        when(transport.isOpen()).thenReturn(true);
        when(transport.getBasicRemote()).thenReturn(remote);
        assertTrue(WebSocketSessionManager.addSession(sessionId, transport));

        assertTrue(BotJobDetailsWindowCoordinator.publishTarget(sessionId, target(42, 91, 7)));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(remote).sendText(message.capture());
        JsonObject envelope = JsonParser.parseString(message.getValue()).getAsJsonObject();
        assertEquals(sessionId, envelope.get("sessionId").getAsString());
        assertEquals(7, envelope.get("homeBankingId").getAsInt());
        assertEquals(
                BotJobDetailsWindowCoordinator.TARGET_OPERATION,
                envelope.get("operationId").getAsString());
        JsonObject body = JsonParser.parseString(envelope.get("body").getAsString()).getAsJsonObject();
        assertEquals(sessionId, body.get("controlSessionId").getAsString());
        assertEquals(42, body.get("botJobId").getAsInt());
        assertEquals(91, body.get("workspaceEpoch").getAsLong());
        assertEquals(7, body.get("homeBankingId").getAsInt());
    }

    private static BotJobDetailsWindowCoordinator coordinator(
            java.util.function.Supplier<String> ids,
            BotJobDetailsWindowCoordinator.WindowLauncher launcher,
            BotJobDetailsWindowCoordinator.ConnectionProbe connectionProbe,
            BotJobDetailsWindowCoordinator.TargetPublisher publisher) {
        return coordinator(
                ids,
                launcher,
                Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC),
                connectionProbe,
                publisher);
    }

    private static BotJobDetailsWindowCoordinator coordinator(
            java.util.function.Supplier<String> ids,
            BotJobDetailsWindowCoordinator.WindowLauncher launcher,
            Clock clock,
            BotJobDetailsWindowCoordinator.ConnectionProbe connectionProbe,
            BotJobDetailsWindowCoordinator.TargetPublisher publisher) {
        return new BotJobDetailsWindowCoordinator(launcher, ids, clock, connectionProbe, publisher);
    }

    private static BotJobDetailsWindowCoordinator.Target target(
            int botJobId, long workspaceEpoch, int homeBankingId) {
        return new BotJobDetailsWindowCoordinator.Target(botJobId, workspaceEpoch, homeBankingId);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
