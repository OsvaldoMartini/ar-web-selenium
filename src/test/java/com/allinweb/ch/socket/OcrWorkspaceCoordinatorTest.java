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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OcrWorkspaceCoordinatorTest {

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
        assertEquals(2, coordinator.activeWorkspaceCount());
        assertEquals(1, coordinator.bootstrap(first.sessionId()).homeBankingId());
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
