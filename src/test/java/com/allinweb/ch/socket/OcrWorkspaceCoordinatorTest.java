package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.JsonArray;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OcrWorkspaceCoordinatorTest {

    @Test
    void detachedPageScannerUsesLiveBackendContextInsteadOfClaimedIds() {
        AtomicReference<String> resolvedSession = new AtomicReference<>();
        OcrWorkspaceCoordinator coordinator = new OcrWorkspaceCoordinator(
                (kind, sessionId) -> true,
                () -> "ocr-window-from-page-scanner",
                Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC),
                sessionId -> {
                    resolvedSession.set(sessionId);
                    return new OcrWorkspaceCoordinator.PageScannerContext(7, 42, 11);
                });
        String pageScannerSession = "page-scanner-9cb0468e-4822-4d7a-91a8-f314c57f5ad4";

        OcrWorkspaceCoordinator.OpenResult opened = coordinator.open(
                new OcrWorkspaceCoordinator.OpenRequest(
                        OcrWorkspaceCoordinator.Kind.CONFIG,
                        pageScannerSession,
                        999,
                        888,
                        777,
                        new JsonArray()));
        OcrWorkspaceCoordinator.BootstrapContext bootstrap = coordinator.bootstrap(opened.sessionId());

        assertEquals(pageScannerSession, resolvedSession.get());
        assertEquals(7, bootstrap.homeBankingId());
        assertEquals(42, bootstrap.botJobId());
        assertEquals(11, bootstrap.homeUrlId());
    }

    @Test
    void opensConfigWithUniqueSessionAndFourHourReloadSafeContext() {
        Instant now = Instant.parse("2026-07-18T12:00:00Z");
        MutableClock clock = new MutableClock(now);
        AtomicReference<OcrWorkspaceCoordinator.Kind> launchedKind = new AtomicReference<>();
        AtomicReference<String> launchedSession = new AtomicReference<>();
        OcrWorkspaceCoordinator coordinator = new OcrWorkspaceCoordinator(
                (kind, sessionId) -> {
                    launchedKind.set(kind);
                    launchedSession.set(sessionId);
                    return true;
                },
                () -> "config-id",
                clock);

        OcrWorkspaceCoordinator.OpenResult opened = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.CONFIG,
                ScannerWorkspaceSessions.PRE_SCANNER_GRID,
                7,
                42,
                11,
                new JsonArray()));

        assertTrue(opened.ok());
        assertEquals("ocr-config-config-id", opened.sessionId());
        assertEquals(now.plus(Duration.ofHours(4)), opened.expiresAt());
        assertEquals(OcrWorkspaceCoordinator.Kind.CONFIG, launchedKind.get());
        assertEquals(opened.sessionId(), launchedSession.get());
        assertTrue(OcrWorkspaceCoordinator.isWorkspaceSessionId(opened.sessionId()));

        OcrWorkspaceCoordinator.BootstrapContext context = coordinator.bootstrap(opened.sessionId());
        assertEquals(ScannerWorkspaceSessions.PRE_SCANNER_GRID, context.sourceScannerSessionId());
        assertEquals(7, context.homeBankingId());
        assertEquals(42, context.botJobId());
        assertEquals(11, context.homeUrlId());
        assertEquals(now, context.createdAt());
    }

    @Test
    void rejectsUnknownSessionsAndUntrustedSources() {
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("config"));
        OcrWorkspaceCoordinator coordinator = coordinator(ids, (kind, sessionId) -> true);

        assertThrows(IllegalArgumentException.class, () -> coordinator.bootstrap("ocr-config-missing"));
        assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                        OcrWorkspaceCoordinator.Kind.CONFIG,
                        "mainDashboard",
                        1,
                        2,
                        null,
                        new JsonArray())));
        assertFalse(OcrWorkspaceCoordinator.isWorkspaceSessionId("ocr-config-"));
        assertFalse(OcrWorkspaceCoordinator.isWorkspaceSessionId("preScannerGrid"));
    }

    @Test
    void retriesIdCollisionsWithoutReplacingAnExistingWorkspace() {
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("same", "same", "different"));
        OcrWorkspaceCoordinator coordinator = coordinator(ids, (kind, sessionId) -> true);

        OcrWorkspaceCoordinator.OpenResult first = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.CONFIG,
                ScannerWorkspaceSessions.SCANNER_GRID,
                1,
                2,
                null,
                new JsonArray()));
        OcrWorkspaceCoordinator.OpenResult second = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.CONFIG,
                ScannerWorkspaceSessions.PRE_SCANNER_GRID,
                3,
                4,
                null,
                new JsonArray()));

        assertEquals("ocr-config-same", first.sessionId());
        assertEquals("ocr-config-different", second.sessionId());
        assertNotEquals(first.sessionId(), second.sessionId());
        assertEquals(1, coordinator.activeWorkspaceCount());
        assertEquals(1, coordinator.activeWindowCount(OcrWorkspaceCoordinator.Kind.CONFIG));
        assertThrows(IllegalArgumentException.class, () -> coordinator.bootstrap(first.sessionId()));
        assertEquals(3, coordinator.bootstrap(second.sessionId()).homeBankingId());
    }

    @Test
    void repeatedConfigOpenFocusesTheSamePhysicalWindowAndLogicalSession() {
        AtomicInteger launches = new AtomicInteger();
        List<Retarget> retargets = new ArrayList<>();
        OcrWorkspaceCoordinator coordinator = singletonCoordinator(
                new ArrayDeque<>(List.of("one")),
                (kind, sessionId) -> {
                    launches.incrementAndGet();
                    return true;
                },
                sessionId -> true,
                (previous, current) -> {
                    retargets.add(new Retarget(previous, current));
                    return true;
                });

        OcrWorkspaceCoordinator.OpenRequest request = new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.CONFIG,
                ScannerWorkspaceSessions.SCANNER_GRID,
                7,
                42,
                11,
                new JsonArray());
        OcrWorkspaceCoordinator.OpenResult first = coordinator.open(request);
        OcrWorkspaceCoordinator.OpenResult second = coordinator.open(request);

        assertTrue(first.ok());
        assertTrue(second.ok());
        assertEquals(first.sessionId(), second.sessionId());
        assertEquals(1, launches.get());
        assertEquals(1, coordinator.activeWorkspaceCount());
        assertEquals(1, retargets.size());
        assertEquals(first.sessionId(), retargets.get(0).previous().sessionId());
        assertEquals(first.sessionId(), retargets.get(0).current().sessionId());
    }

    @Test
    void connectedConfigWindowRetargetsWithFreshLogicalSessionInsteadOfLaunchingAgain() {
        AtomicInteger launches = new AtomicInteger();
        List<Retarget> retargets = new ArrayList<>();
        OcrWorkspaceCoordinator coordinator = singletonCoordinator(
                new ArrayDeque<>(List.of("old", "new")),
                (kind, sessionId) -> {
                    launches.incrementAndGet();
                    return true;
                },
                sessionId -> true,
                (previous, current) -> {
                    retargets.add(new Retarget(previous, current));
                    return true;
                });

        OcrWorkspaceCoordinator.OpenResult first = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.CONFIG,
                ScannerWorkspaceSessions.SCANNER_GRID,
                7,
                42,
                11,
                new JsonArray()));
        OcrWorkspaceCoordinator.OpenResult second = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.CONFIG,
                ScannerWorkspaceSessions.PRE_SCANNER_GRID,
                9,
                88,
                12,
                new JsonArray()));

        assertTrue(second.ok());
        assertNotEquals(first.sessionId(), second.sessionId());
        assertEquals(1, launches.get(), "retargeting must reuse the existing native app window");
        assertEquals(1, coordinator.activeWorkspaceCount());
        assertFalse(coordinator.isActiveWorkspace(first.sessionId()));
        assertTrue(coordinator.isActiveWorkspace(second.sessionId()));
        assertThrows(IllegalArgumentException.class, () -> coordinator.bootstrap(first.sessionId()));
        OcrWorkspaceCoordinator.BootstrapContext target = coordinator.bootstrap(second.sessionId());
        assertEquals(9, target.homeBankingId());
        assertEquals(88, target.botJobId());
        assertEquals(ScannerWorkspaceSessions.PRE_SCANNER_GRID, target.sourceScannerSessionId());
        assertEquals(1, retargets.size());
        assertEquals(first.sessionId(), retargets.get(0).previous().sessionId());
        assertEquals(second.sessionId(), retargets.get(0).current().sessionId());
    }

    @Test
    void requestDuringInitialLaunchRebindsPendingWindowWithoutSecondLaunchOrSession() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        AtomicInteger launches = new AtomicInteger();
        AtomicInteger retargets = new AtomicInteger();
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("pending"));
        OcrWorkspaceCoordinator coordinator = new OcrWorkspaceCoordinator(
                (kind, sessionId) -> {
                    launches.incrementAndGet();
                    return true;
                },
                ids::remove,
                clock,
                sessionId -> new OcrWorkspaceCoordinator.PageScannerContext(1, 2, null),
                sessionId -> false,
                (previous, current) -> {
                    retargets.incrementAndGet();
                    return true;
                },
                Duration.ofSeconds(15));

        OcrWorkspaceCoordinator.OpenResult first = coordinator.open(configRequest(
                ScannerWorkspaceSessions.SCANNER_GRID, 1, 2));
        clock.advance(Duration.ofSeconds(2));
        OcrWorkspaceCoordinator.OpenResult second = coordinator.open(configRequest(
                ScannerWorkspaceSessions.PRE_SCANNER_GRID, 3, 4));

        assertEquals(first.sessionId(), second.sessionId());
        assertEquals(1, launches.get());
        assertEquals(0, retargets.get());
        OcrWorkspaceCoordinator.BootstrapContext bootstrap = coordinator.bootstrap(second.sessionId());
        assertEquals(3, bootstrap.homeBankingId());
        assertEquals(4, bootstrap.botJobId());
        assertEquals(ScannerWorkspaceSessions.PRE_SCANNER_GRID, bootstrap.sourceScannerSessionId());
    }

    @Test
    void closedPhysicalWindowWaitsForReconnectGraceThenLaunchesOneReplacement() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        AtomicBoolean connected = new AtomicBoolean(true);
        AtomicBoolean allowRetarget = new AtomicBoolean(false);
        AtomicInteger launches = new AtomicInteger();
        List<ScheduledTask> scheduledTasks = new ArrayList<>();
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("old", "rejected", "replacement"));
        OcrWorkspaceCoordinator coordinator = new OcrWorkspaceCoordinator(
                (kind, sessionId) -> {
                    launches.incrementAndGet();
                    return true;
                },
                ids::remove,
                clock,
                sessionId -> new OcrWorkspaceCoordinator.PageScannerContext(1, 2, null),
                sessionId -> connected.get(),
                (previous, current) -> allowRetarget.get(),
                Duration.ofSeconds(15),
                Duration.ofSeconds(2),
                (delay, task) -> scheduledTasks.add(new ScheduledTask(delay, task)));

        OcrWorkspaceCoordinator.OpenResult first = coordinator.open(configRequest(
                ScannerWorkspaceSessions.SCANNER_GRID, 1, 2));
        coordinator.bootstrap(first.sessionId());
        OcrWorkspaceCoordinator.OpenResult rejected = coordinator.open(configRequest(
                ScannerWorkspaceSessions.PRE_SCANNER_GRID, 3, 4));

        assertFalse(rejected.ok());
        assertEquals(first.sessionId(), rejected.sessionId());
        assertEquals(1, coordinator.bootstrap(first.sessionId()).homeBankingId());
        assertEquals(1, coordinator.activeWorkspaceCount());

        connected.set(false);
        allowRetarget.set(true);
        assertTrue(coordinator.disconnected(first.sessionId()));
        OcrWorkspaceCoordinator.OpenResult reconnecting = coordinator.open(configRequest(
                ScannerWorkspaceSessions.PRE_SCANNER_GRID, 3, 4));

        assertTrue(reconnecting.ok());
        assertEquals(first.sessionId(), reconnecting.sessionId());
        assertEquals(1, launches.get(), "a transient disconnect must not launch a duplicate window");
        assertEquals(1, scheduledTasks.size());

        clock.advance(Duration.ofSeconds(1));
        scheduledTasks.remove(0).task().run();
        assertEquals(1, launches.get());
        assertEquals(1, scheduledTasks.size());

        clock.advance(Duration.ofSeconds(1));
        scheduledTasks.remove(0).task().run();
        assertEquals(2, launches.get());
        assertThrows(IllegalArgumentException.class, () -> coordinator.bootstrap(first.sessionId()));
        assertEquals(3, coordinator.bootstrap("ocr-config-replacement").homeBankingId());
        assertEquals(1, coordinator.activeWindowCount(OcrWorkspaceCoordinator.Kind.CONFIG));
    }

    @Test
    void transientReconnectRetargetsExistingPhysicalWindowWithoutLaunchingReplacement() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        AtomicBoolean connected = new AtomicBoolean(true);
        AtomicInteger launches = new AtomicInteger();
        List<ScheduledTask> scheduledTasks = new ArrayList<>();
        List<Retarget> retargets = new ArrayList<>();
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("old", "retargeted"));
        OcrWorkspaceCoordinator coordinator = new OcrWorkspaceCoordinator(
                (kind, sessionId) -> {
                    launches.incrementAndGet();
                    return true;
                },
                ids::remove,
                clock,
                sessionId -> new OcrWorkspaceCoordinator.PageScannerContext(1, 2, null),
                sessionId -> connected.get(),
                (previous, current) -> {
                    retargets.add(new Retarget(previous, current));
                    return true;
                },
                Duration.ofSeconds(15),
                Duration.ofSeconds(2),
                (delay, task) -> scheduledTasks.add(new ScheduledTask(delay, task)));

        OcrWorkspaceCoordinator.OpenResult first = coordinator.open(configRequest(
                ScannerWorkspaceSessions.SCANNER_GRID, 1, 2));
        coordinator.bootstrap(first.sessionId());
        connected.set(false);
        assertTrue(coordinator.disconnected(first.sessionId()));
        OcrWorkspaceCoordinator.OpenResult reconnecting = coordinator.open(configRequest(
                ScannerWorkspaceSessions.PRE_SCANNER_GRID, 3, 4));

        assertTrue(reconnecting.ok());
        assertEquals(1, launches.get());
        assertEquals(1, scheduledTasks.size());

        connected.set(true);
        clock.advance(Duration.ofSeconds(2));
        scheduledTasks.remove(0).task().run();

        assertEquals(1, launches.get());
        assertEquals(1, retargets.size());
        assertEquals(first.sessionId(), retargets.get(0).previous().sessionId());
        assertEquals("ocr-config-retargeted", retargets.get(0).current().sessionId());
        assertFalse(coordinator.isActiveWorkspace(first.sessionId()));
        assertTrue(coordinator.isActiveWorkspace("ocr-config-retargeted"));
        assertEquals(3, coordinator.bootstrap("ocr-config-retargeted").homeBankingId());
    }

    @Test
    void expiryIsExactAndFailedLaunchRollsBackTheProvisionalContext() {
        Instant now = Instant.parse("2026-07-18T12:00:00Z");
        MutableClock clock = new MutableClock(now);
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("failed", "throws", "open"));
        OcrWorkspaceCoordinator coordinator = new OcrWorkspaceCoordinator(
                (kind, sessionId) -> {
                    if (sessionId.endsWith("throws")) throw new IllegalStateException("Browser launch failed");
                    return !sessionId.endsWith("failed");
                },
                ids::remove,
                clock);
        OcrWorkspaceCoordinator.OpenResult failed = coordinator.open(configRequest(
                ScannerWorkspaceSessions.SCANNER_GRID, 1, 2));

        assertFalse(failed.ok());
        assertEquals("", failed.sessionId());
        assertEquals(0, coordinator.activeWorkspaceCount());
        assertThrows(
                IllegalStateException.class,
                () -> coordinator.open(configRequest(
                        ScannerWorkspaceSessions.SCANNER_GRID, 1, 2)));
        assertEquals(0, coordinator.activeWorkspaceCount());

        OcrWorkspaceCoordinator.OpenResult opened = coordinator.open(configRequest(
                ScannerWorkspaceSessions.SCANNER_GRID, 1, 2));
        assertTrue(opened.ok());
        assertEquals(1, coordinator.activeWorkspaceCount());
        clock.advance(Duration.ofHours(4).minusMillis(1));
        assertEquals(opened.sessionId(), coordinator.bootstrap(opened.sessionId()).sessionId());
        clock.advance(Duration.ofMillis(1));
        assertThrows(IllegalArgumentException.class, () -> coordinator.bootstrap(opened.sessionId()));
        assertEquals(0, coordinator.activeWorkspaceCount());
    }

    private static OcrWorkspaceCoordinator coordinator(
            ArrayDeque<String> ids, OcrWorkspaceCoordinator.WorkspaceLauncher launcher) {
        return new OcrWorkspaceCoordinator(
                launcher,
                ids::remove,
                Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC));
    }

    private static OcrWorkspaceCoordinator singletonCoordinator(
            ArrayDeque<String> ids,
            OcrWorkspaceCoordinator.WorkspaceLauncher launcher,
            OcrWorkspaceCoordinator.WorkspaceConnectionProbe connectionProbe,
            OcrWorkspaceCoordinator.WorkspaceRetargetNotifier retargetNotifier) {
        return new OcrWorkspaceCoordinator(
                launcher,
                ids::remove,
                Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC),
                sessionId -> new OcrWorkspaceCoordinator.PageScannerContext(1, 2, null),
                connectionProbe,
                retargetNotifier,
                Duration.ofSeconds(15));
    }

    private static OcrWorkspaceCoordinator.OpenRequest configRequest(
            String transportSessionId, int homeBankingId, int botJobId) {
        return new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.CONFIG,
                transportSessionId,
                homeBankingId,
                botJobId,
                null,
                new JsonArray());
    }

    private record Retarget(
            OcrWorkspaceCoordinator.BootstrapContext previous,
            OcrWorkspaceCoordinator.BootstrapContext current) {}

    private record ScheduledTask(Duration delay, Runnable task) {}

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
