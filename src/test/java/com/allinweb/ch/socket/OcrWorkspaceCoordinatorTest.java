package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
                (homeBankingId, sourceScannerSessionId, suggestions) -> true,
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
                clock,
                (homeBankingId, sourceSessionId, suggestions) -> true);

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
    void configCanOpenResultsWithInheritedScannerScopeAndDefensiveParameterCopies() {
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("config", "results"));
        OcrWorkspaceCoordinator coordinator = coordinator(ids, (kind, sessionId) -> true);
        OcrWorkspaceCoordinator.OpenResult config = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.CONFIG,
                ScannerWorkspaceSessions.SCANNER_GRID,
                9,
                88,
                12,
                new JsonArray()));

        JsonArray parameters = parameters("300");
        OcrWorkspaceCoordinator.OpenResult results = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.RESULTS,
                config.sessionId(),
                999,
                999,
                999,
                parameters));
        parameters.get(0).getAsJsonObject().addProperty("value", "72");

        OcrWorkspaceCoordinator.BootstrapContext first = coordinator.bootstrap(results.sessionId());
        assertEquals(OcrWorkspaceCoordinator.Kind.RESULTS, first.kind());
        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, first.sourceScannerSessionId());
        assertEquals(9, first.homeBankingId());
        assertEquals(88, first.botJobId());
        assertEquals(12, first.homeUrlId());
        assertEquals("300", first.parameters().get(0).getAsJsonObject().get("value").getAsString());

        first.parameters().get(0).getAsJsonObject().addProperty("value", "96");
        assertEquals(
                "300",
                coordinator
                        .bootstrap(results.sessionId())
                        .parameters()
                        .get(0)
                        .getAsJsonObject()
                        .get("value")
                        .getAsString());
    }

    @Test
    void applySuggestionsPublishesValidatedRowsOnlyToTheBoundScanner() {
        AtomicReference<Integer> publishedHomeBankingId = new AtomicReference<>();
        AtomicReference<String> publishedSession = new AtomicReference<>();
        AtomicReference<List<OcrWorkspaceCoordinator.Suggestion>> publishedSuggestions = new AtomicReference<>();
        OcrWorkspaceCoordinator coordinator = new OcrWorkspaceCoordinator(
                (kind, sessionId) -> true,
                () -> "results",
                Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC),
                (homeBankingId, sourceSessionId, suggestions) -> {
                    publishedHomeBankingId.set(homeBankingId);
                    publishedSession.set(sourceSessionId);
                    publishedSuggestions.set(List.copyOf(suggestions));
                    return true;
                });
        OcrWorkspaceCoordinator.OpenResult results = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.RESULTS,
                ScannerWorkspaceSessions.PRE_SCANNER_GRID,
                7,
                42,
                null,
                new JsonArray()));

        OcrWorkspaceCoordinator.ApplyResult applied = coordinator.applySuggestions(
                results.sessionId(),
                List.of(
                        new OcrWorkspaceCoordinator.Suggestion(" /html/body/button ", " Login "),
                        new OcrWorkspaceCoordinator.Suggestion("/html/body/button", "Sign in"),
                        new OcrWorkspaceCoordinator.Suggestion("/html/body/input", "User name")));

        assertTrue(applied.published());
        assertEquals(ScannerWorkspaceSessions.PRE_SCANNER_GRID, applied.sourceScannerSessionId());
        assertEquals(2, applied.suggestionCount());
        assertEquals(7, publishedHomeBankingId.get());
        assertEquals(ScannerWorkspaceSessions.PRE_SCANNER_GRID, publishedSession.get());
        assertEquals(
                List.of(
                        new OcrWorkspaceCoordinator.Suggestion("/html/body/button", "Sign in"),
                        new OcrWorkspaceCoordinator.Suggestion("/html/body/input", "User name")),
                publishedSuggestions.get());
    }

    @Test
    void rejectsWrongWorkspaceKindsUnknownSessionsAndUntrustedSources() {
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("config", "results"));
        OcrWorkspaceCoordinator coordinator = coordinator(ids, (kind, sessionId) -> true);
        OcrWorkspaceCoordinator.OpenResult config = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.CONFIG,
                ScannerWorkspaceSessions.SCANNER_GRID,
                1,
                2,
                null,
                new JsonArray()));

        assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.applySuggestions(
                        config.sessionId(), List.of(new OcrWorkspaceCoordinator.Suggestion("/html", "Page"))));
        assertThrows(IllegalArgumentException.class, () -> coordinator.bootstrap("ocr-results-missing"));
        assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                        OcrWorkspaceCoordinator.Kind.CONFIG,
                        "mainDashboard",
                        1,
                        2,
                        null,
                        new JsonArray())));
        assertFalse(OcrWorkspaceCoordinator.isWorkspaceSessionId("ocr-results-"));
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
    void configAndResultsEachOwnExactlyOneIndependentPhysicalWindow() {
        AtomicInteger configLaunches = new AtomicInteger();
        AtomicInteger resultsLaunches = new AtomicInteger();
        OcrWorkspaceCoordinator coordinator = singletonCoordinator(
                new ArrayDeque<>(List.of("config", "results", "config-next", "results-next")),
                (kind, sessionId) -> {
                    (kind == OcrWorkspaceCoordinator.Kind.CONFIG ? configLaunches : resultsLaunches)
                            .incrementAndGet();
                    return true;
                },
                sessionId -> true,
                (previous, current) -> true);

        OcrWorkspaceCoordinator.OpenResult config = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.CONFIG,
                ScannerWorkspaceSessions.SCANNER_GRID,
                7,
                42,
                11,
                new JsonArray()));
        OcrWorkspaceCoordinator.OpenResult results = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.RESULTS,
                config.sessionId(),
                -1,
                -1,
                null,
                parameters("300")));
        OcrWorkspaceCoordinator.OpenResult nextConfig = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.CONFIG,
                ScannerWorkspaceSessions.PRE_SCANNER_GRID,
                9,
                88,
                12,
                new JsonArray()));
        OcrWorkspaceCoordinator.OpenResult nextResults = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.RESULTS,
                nextConfig.sessionId(),
                -1,
                -1,
                null,
                parameters("600")));

        assertNotEquals(config.sessionId(), nextConfig.sessionId());
        assertNotEquals(results.sessionId(), nextResults.sessionId());
        assertEquals(1, configLaunches.get());
        assertEquals(1, resultsLaunches.get());
        assertEquals(1, coordinator.activeWindowCount(OcrWorkspaceCoordinator.Kind.CONFIG));
        assertEquals(1, coordinator.activeWindowCount(OcrWorkspaceCoordinator.Kind.RESULTS));
        assertEquals(2, coordinator.activeWorkspaceCount());
        assertEquals("600", coordinator.bootstrap(nextResults.sessionId())
                .parameters().get(0).getAsJsonObject().get("value").getAsString());
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
                (homeBankingId, sourceSessionId, suggestions) -> true,
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
                (homeBankingId, sourceSessionId, suggestions) -> true,
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
                (homeBankingId, sourceSessionId, suggestions) -> true,
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
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("open", "failed", "throws"));
        List<String> launches = new ArrayList<>();
        OcrWorkspaceCoordinator coordinator = new OcrWorkspaceCoordinator(
                (kind, sessionId) -> {
                    launches.add(sessionId);
                    if (sessionId.endsWith("throws")) throw new IllegalStateException("Browser launch failed");
                    return !sessionId.endsWith("failed");
                },
                ids::remove,
                clock,
                (homeBankingId, sourceSessionId, suggestions) -> true);
        OcrWorkspaceCoordinator.OpenResult opened = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.RESULTS,
                ScannerWorkspaceSessions.SCANNER_GRID,
                1,
                2,
                null,
                new JsonArray()));
        OcrWorkspaceCoordinator.OpenResult failed = coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                OcrWorkspaceCoordinator.Kind.CONFIG,
                ScannerWorkspaceSessions.PRE_SCANNER_GRID,
                3,
                4,
                null,
                new JsonArray()));

        assertFalse(failed.ok());
        assertEquals("", failed.sessionId());
        assertThrows(
                IllegalStateException.class,
                () -> coordinator.open(new OcrWorkspaceCoordinator.OpenRequest(
                        OcrWorkspaceCoordinator.Kind.CONFIG,
                        ScannerWorkspaceSessions.PRE_SCANNER_GRID,
                        3,
                        4,
                        null,
                        new JsonArray())));
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
                Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC),
                (homeBankingId, sourceSessionId, suggestions) -> true);
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
                (homeBankingId, sourceSessionId, suggestions) -> true,
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

    private static JsonArray parameters(String value) {
        JsonObject parameter = new JsonObject();
        parameter.addProperty("category", "engine");
        parameter.addProperty("name", "user_defined_dpi");
        parameter.addProperty("valueType", "integer");
        parameter.addProperty("value", value);
        JsonArray parameters = new JsonArray();
        parameters.add(parameter);
        return parameters;
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
