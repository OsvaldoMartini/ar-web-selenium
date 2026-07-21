package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MainApplicationControlLifecycleTest {

    @Test
    void recognizesOnlyTheStablePrimaryApplicationControlSession() {
        assertTrue(MainApplicationControlLifecycle.isControlSessionId("mainApplicationControl"));
        assertFalse(MainApplicationControlLifecycle.isControlSessionId("mainDashboard"));
        assertFalse(MainApplicationControlLifecycle.isControlSessionId("botJobTasks"));
        assertFalse(MainApplicationControlLifecycle.isControlSessionId(null));
    }

    @Test
    void disconnectedPrimaryWindowRequestsShutdownOnlyAfterReconnectGrace() {
        AtomicInteger shutdownRequests = new AtomicInteger();
        List<ScheduledTask> scheduled = new ArrayList<>();
        MainApplicationControlLifecycle lifecycle = new MainApplicationControlLifecycle(
                ignored -> false,
                shutdownRequests::incrementAndGet,
                (delay, task) -> scheduled.add(new ScheduledTask(delay, task)),
                Duration.ofSeconds(2));

        lifecycle.connected(MainApplicationControlLifecycle.SESSION_ID);
        lifecycle.disconnected(MainApplicationControlLifecycle.SESSION_ID);

        assertEquals(0, shutdownRequests.get());
        assertEquals(1, scheduled.size());
        assertEquals(Duration.ofSeconds(2), scheduled.get(0).delay());

        scheduled.get(0).task().run();
        scheduled.get(0).task().run();
        assertEquals(1, shutdownRequests.get());
    }

    @Test
    void reconnectWithinGraceMakesTheStaleCloseCallbackHarmless() {
        AtomicInteger shutdownRequests = new AtomicInteger();
        List<ScheduledTask> scheduled = new ArrayList<>();
        MainApplicationControlLifecycle lifecycle = new MainApplicationControlLifecycle(
                ignored -> true,
                shutdownRequests::incrementAndGet,
                (delay, task) -> scheduled.add(new ScheduledTask(delay, task)),
                Duration.ofSeconds(2));

        lifecycle.connected(MainApplicationControlLifecycle.SESSION_ID);
        lifecycle.disconnected(MainApplicationControlLifecycle.SESSION_ID);
        lifecycle.connected(MainApplicationControlLifecycle.SESSION_ID);
        scheduled.get(0).task().run();

        assertEquals(0, shutdownRequests.get());
    }

    @Test
    void newerDisconnectSupersedesOlderDelayedCallbackAndStillShutsDownOnce() {
        AtomicInteger shutdownRequests = new AtomicInteger();
        List<ScheduledTask> scheduled = new ArrayList<>();
        MainApplicationControlLifecycle lifecycle = new MainApplicationControlLifecycle(
                ignored -> false,
                shutdownRequests::incrementAndGet,
                (delay, task) -> scheduled.add(new ScheduledTask(delay, task)),
                Duration.ofMillis(50));

        lifecycle.connected(MainApplicationControlLifecycle.SESSION_ID);
        lifecycle.disconnected(MainApplicationControlLifecycle.SESSION_ID);
        lifecycle.disconnected(MainApplicationControlLifecycle.SESSION_ID);

        assertEquals(2, scheduled.size());
        scheduled.get(0).task().run();
        assertEquals(0, shutdownRequests.get());
        scheduled.get(1).task().run();
        scheduled.get(1).task().run();
        assertEquals(1, shutdownRequests.get());
    }

    @Test
    void anOpenReplacementTransportSuppressesShutdownEvenBeforeConnectedCallback() {
        AtomicBoolean transportOpen = new AtomicBoolean(true);
        AtomicInteger shutdownRequests = new AtomicInteger();
        List<Runnable> scheduled = new ArrayList<>();
        MainApplicationControlLifecycle lifecycle = new MainApplicationControlLifecycle(
                ignored -> transportOpen.get(),
                shutdownRequests::incrementAndGet,
                (delay, task) -> scheduled.add(task),
                Duration.ofSeconds(1));

        lifecycle.connected(MainApplicationControlLifecycle.SESSION_ID);
        lifecycle.disconnected(MainApplicationControlLifecycle.SESSION_ID);
        scheduled.get(0).run();

        assertEquals(0, shutdownRequests.get());
    }

    @Test
    void rejectsInvalidSessionAndNonPositiveGrace() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MainApplicationControlLifecycle(
                        ignored -> false,
                        () -> {},
                        (delay, task) -> {},
                        Duration.ZERO));

        MainApplicationControlLifecycle lifecycle = new MainApplicationControlLifecycle(
                ignored -> false,
                () -> {},
                (delay, task) -> {},
                Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> lifecycle.connected("mainDashboard"));
        assertThrows(IllegalArgumentException.class, () -> lifecycle.disconnected("mainDashboard"));
    }

    private record ScheduledTask(Duration delay, Runnable task) {}
}
