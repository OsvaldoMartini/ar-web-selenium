package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PageScannerWorkspaceCoordinatorTest {

    @AfterEach
    void clearSocketRegistry() {
        WebSocketSessionManager.clearSessions();
    }

    @Test
    void opensUniqueWorkspaceAndKeepsTrustedBotJobContextForBootstrap() {
        Instant now = Instant.parse("2026-07-20T12:00:00Z");
        AtomicReference<String> launchedSession = new AtomicReference<>();
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> {
                    launchedSession.set(sessionId);
                    return true;
                },
                () -> "window-42",
                Clock.fixed(now, ZoneOffset.UTC));

        PageScannerWorkspaceCoordinator.OpenResult opened = coordinator.open(request(context(7, 42, "Payments")));

        assertTrue(opened.ok());
        assertTrue(opened.launched());
        assertFalse(opened.alreadyOpen());
        assertEquals("page-scanner-window-42", opened.sessionId());
        assertEquals(opened.sessionId(), launchedSession.get());
        assertEquals(now.plus(Duration.ofHours(4)), opened.expiresAt());

        PageScannerWorkspaceCoordinator.BootstrapContext bootstrap = coordinator.bootstrap(opened.sessionId());
        assertEquals(opened.sessionId(), bootstrap.sessionId());
        assertEquals(ScannerWorkspaceSessions.BOT_JOB_TASKS, bootstrap.sourceBotJobSessionId());
        assertEquals(context(7, 42, "Payments"), bootstrap.context());
        assertEquals(now, bootstrap.createdAt());
        assertEquals(opened.expiresAt(), bootstrap.expiresAt());
    }

    @Test
    void authoritativeContextReadsServerOwnerWithoutMarkingScannerConnected() {
        PageScannerWorkspaceCoordinator coordinator = coordinator(
                new ArrayDeque<>(List.of("owner-read")),
                sessionId -> true);
        PageScannerWorkspaceCoordinator.WorkspaceContext expected =
                context(7, 42, "Payments");
        PageScannerWorkspaceCoordinator.OpenResult opened =
                coordinator.open(request(expected));

        PageScannerWorkspaceCoordinator.WorkspaceContext authorized =
                coordinator.authoritativeContext(opened.sessionId());

        assertEquals(expected, authorized);
        assertFalse(coordinator.disconnected(opened.sessionId()));
    }

    @Test
    void repeatedOpenForTheSameBotJobReturnsExistingWorkspaceWithoutRelaunching() {
        AtomicInteger launchCount = new AtomicInteger();
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("first", "unused"));
        PageScannerWorkspaceCoordinator coordinator = coordinator(
                ids,
                sessionId -> {
                    launchCount.incrementAndGet();
                    return true;
                });

        PageScannerWorkspaceCoordinator.OpenResult first = coordinator.open(request(context(7, 42, "Payments")));
        PageScannerWorkspaceCoordinator.OpenResult repeated =
                coordinator.open(request(context(7, 42, "Payments")));

        assertEquals(first.sessionId(), repeated.sessionId());
        assertTrue(repeated.ok());
        assertFalse(repeated.launched());
        assertTrue(repeated.alreadyOpen());
        assertEquals(1, launchCount.get());
        assertEquals("Payments", coordinator.bootstrap(first.sessionId()).context().botJobName());
    }

    @Test
    void connectedContextChangeRetargetsOnePhysicalWindowToAFreshLogicalSession() {
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("stale", "current"));
        AtomicInteger launchCount = new AtomicInteger();
        List<PageScannerWorkspaceCoordinator.CloseReason> cleanupReasons = new ArrayList<>();
        List<String> retargets = new ArrayList<>();
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> {
                    launchCount.incrementAndGet();
                    return true;
                },
                ids::remove,
                Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC),
                4,
                (context, reason) -> cleanupReasons.add(reason),
                sessionId -> true,
                PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE,
                (context, reason, message) -> false,
                (previous, current, message) -> {
                    retargets.add(previous.sessionId() + "->" + current.sessionId());
                    return true;
                });
        PageScannerWorkspaceCoordinator.OpenResult stale =
                coordinator.open(request(context(7, 42, "Payments")));
        PageScannerWorkspaceCoordinator.WorkspaceContext changed =
                new PageScannerWorkspaceCoordinator.WorkspaceContext(
                        7,
                        42,
                        1,
                        "Payments Updated",
                        12,
                        "https://bank.example/new-login",
                        "firefox",
                        "--private",
                        "C:\\ARWeb\\updated-data");

        PageScannerWorkspaceCoordinator.OpenResult current = coordinator.open(request(changed));

        assertEquals("page-scanner-stale", stale.sessionId());
        assertEquals("page-scanner-current", current.sessionId());
        assertFalse(current.launched());
        assertTrue(current.alreadyOpen());
        assertEquals(1, launchCount.get());
        assertEquals(List.of(PageScannerWorkspaceCoordinator.CloseReason.SUPERSEDED), cleanupReasons);
        assertEquals(List.of("page-scanner-stale->page-scanner-current"), retargets);
        assertEquals(1, coordinator.activeWorkspaceCount());
        assertEquals(changed, coordinator.bootstrap(current.sessionId()).context());
        assertThrows(IllegalArgumentException.class, () -> coordinator.bootstrap(stale.sessionId()));
    }

    @Test
    void differentBotJobsReuseOnePhysicalWindowAndOneGlobalWorkspaceSlot() {
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("first", "second"));
        AtomicInteger launchCount = new AtomicInteger();
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> {
                    launchCount.incrementAndGet();
                    return true;
                },
                ids::remove,
                Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC),
                1,
                (context, reason) -> {},
                sessionId -> true,
                PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE,
                (context, reason, message) -> false,
                (previous, current, message) -> true);

        PageScannerWorkspaceCoordinator.OpenResult first = coordinator.open(request(context(7, 42, "Payments")));
        assertTrue(coordinator.open(request(context(7, 42, "Payments"))).alreadyOpen());
        PageScannerWorkspaceCoordinator.OpenResult second = coordinator.open(request(context(7, 43, "Transfers")));
        assertEquals("page-scanner-second", second.sessionId());
        assertFalse(second.launched());
        assertTrue(second.alreadyOpen());
        assertEquals(1, launchCount.get());
        assertEquals(1, coordinator.activeWorkspaceCount());
        assertThrows(IllegalArgumentException.class, () -> coordinator.bootstrap(first.sessionId()));
        assertEquals(43, coordinator.bootstrap(second.sessionId()).context().botJobId());
    }

    @Test
    void latestBotJobWinsWhileTheSingleNativeWindowIsStillOpening() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        AtomicInteger launchCount = new AtomicInteger();
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> {
                    launchCount.incrementAndGet();
                    return true;
                },
                () -> "opening",
                clock,
                1,
                (context, reason) -> {},
                sessionId -> false,
                PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE,
                (context, reason, message) -> false,
                (previous, current, message) -> {
                    throw new AssertionError("An unconnected window must not require retarget delivery");
                });

        PageScannerWorkspaceCoordinator.OpenResult first =
                coordinator.open(request(context(7, 42, "Payments")));
        PageScannerWorkspaceCoordinator.OpenResult latest =
                coordinator.open(request(context(7, 43, "Transfers")));

        assertEquals(first.sessionId(), latest.sessionId());
        assertEquals(1, launchCount.get());
        assertEquals(1, coordinator.activeWorkspaceCount());
        assertEquals(43, coordinator.bootstrap(latest.sessionId()).context().botJobId());
    }

    @Test
    void oldBotJobCloseCannotRetireTheRetargetedScannerBinding() {
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("old", "current"));
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> true,
                ids::remove,
                Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC),
                1,
                (context, reason) -> {},
                sessionId -> true,
                PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE,
                (context, reason, message) -> false,
                (previous, current, message) -> true);

        PageScannerWorkspaceCoordinator.OpenResult old =
                coordinator.open(request(context(7, 42, "Payments")));
        PageScannerWorkspaceCoordinator.OpenResult current =
                coordinator.open(request(context(7, 43, "Transfers")));

        assertFalse(coordinator.closeForBotJob(7, 42, 1));
        assertFalse(coordinator.isActiveWorkspace(old.sessionId()));
        assertTrue(coordinator.isActiveWorkspace(current.sessionId()));
    }

    @Test
    void launchFailureRollsBackBothSessionAndOnePerBotJobReservation() {
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("unavailable", "throws", "success"));
        List<String> launches = new ArrayList<>();
        PageScannerWorkspaceCoordinator coordinator = coordinator(ids, sessionId -> {
            launches.add(sessionId);
            if (sessionId.endsWith("throws")) {
                throw new IllegalStateException("Browser launch failed");
            }
            return sessionId.endsWith("success");
        });

        PageScannerWorkspaceCoordinator.OpenResult unavailable = coordinator.open(request(context(7, 42, "Payments")));
        assertFalse(unavailable.ok());
        assertEquals("", unavailable.sessionId());
        assertEquals(0, coordinator.activeWorkspaceCount());

        assertThrows(
                IllegalStateException.class,
                () -> coordinator.open(request(context(7, 42, "Payments"))));
        assertEquals(0, coordinator.activeWorkspaceCount());

        PageScannerWorkspaceCoordinator.OpenResult opened = coordinator.open(request(context(7, 42, "Payments")));
        assertTrue(opened.ok());
        assertEquals("page-scanner-success", opened.sessionId());
        assertEquals(3, launches.size());
    }

    @Test
    void expiresAtExactlyFourHoursAndThenAllowsAReplacementForTheBotJob() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("first", "replacement"));
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> true,
                ids::remove,
                clock);
        PageScannerWorkspaceCoordinator.OpenResult first = coordinator.open(request(context(7, 42, "Payments")));

        clock.advance(Duration.ofHours(4).minusMillis(1));
        assertEquals(first.sessionId(), coordinator.bootstrap(first.sessionId()).sessionId());
        clock.advance(Duration.ofMillis(1));
        assertThrows(IllegalArgumentException.class, () -> coordinator.bootstrap(first.sessionId()));

        PageScannerWorkspaceCoordinator.OpenResult replacement =
                coordinator.open(request(context(7, 42, "Payments")));
        assertEquals("page-scanner-replacement", replacement.sessionId());
        assertEquals(1, coordinator.activeWorkspaceCount());
    }

    @Test
    void rejectsUntrustedTransportInvalidContextAndUnsafeGeneratedIds() {
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> true,
                () -> "unsafe/id",
                Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC));

        assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.open(new PageScannerWorkspaceCoordinator.OpenRequest(
                        ScannerWorkspaceSessions.SCANNER_GRID,
                        context(7, 42, "Payments"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.open(request(context(0, 42, "Payments"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.open(request(context(7, 42, "   "))));
        assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.open(request(context(7, 42, "Payments"))));
        assertEquals(0, coordinator.activeWorkspaceCount());
    }

    @Test
    void botJobCloseRequiresExactEpochAndReleasesOwnedResources() {
        AtomicReference<PageScannerWorkspaceCoordinator.CloseReason> closeReason = new AtomicReference<>();
        AtomicReference<String> closedSession = new AtomicReference<>();
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> true,
                () -> "owned-workspace",
                Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC),
                4,
                (context, reason) -> {
                    closedSession.set(context.sessionId());
                    closeReason.set(reason);
                });
        PageScannerWorkspaceCoordinator.OpenResult opened =
                coordinator.open(request(context(7, 42, "Payments")));

        assertFalse(coordinator.closeForBotJob(7, 42, 99));
        assertTrue(coordinator.isActiveWorkspace(opened.sessionId()));
        assertTrue(coordinator.closeForBotJob(7, 42, 1));

        assertEquals(opened.sessionId(), closedSession.get());
        assertEquals(PageScannerWorkspaceCoordinator.CloseReason.BOT_JOB_CLOSED, closeReason.get());
        assertFalse(coordinator.isActiveWorkspace(opened.sessionId()));
    }

    @Test
    void botJobInvalidationPublishesExactSessionEventBeforeRetiringContextAndLedger() throws Exception {
        AtomicReference<PageScannerWorkspaceCoordinator> coordinatorReference = new AtomicReference<>();
        AtomicBoolean activeDuringNotification = new AtomicBoolean(false);
        AtomicBoolean activeDuringCleanup = new AtomicBoolean(true);
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> true,
                () -> "notified-workspace",
                Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC),
                4,
                (context, reason) -> activeDuringCleanup.set(
                        coordinatorReference.get().isActiveWorkspace(context.sessionId())),
                sessionId -> true,
                PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE,
                (context, reason, message) -> {
                    activeDuringNotification.set(
                            coordinatorReference.get().isActiveWorkspace(context.sessionId()));
                    return PageScannerWorkspaceCoordinator.publishWorkspaceClosed(context, reason, message);
                });
        coordinatorReference.set(coordinator);
        PageScannerWorkspaceCoordinator.OpenResult opened =
                coordinator.open(request(context(7, 42, "Payments")));

        Session transport = mock(Session.class);
        RemoteEndpoint.Basic basicRemote = mock(RemoteEndpoint.Basic.class);
        when(transport.isOpen()).thenReturn(true);
        when(transport.getBasicRemote()).thenReturn(basicRemote);
        assertTrue(WebSocketSessionManager.addSession(opened.sessionId(), transport));

        PageScannerMutationLedger ledger = PageScannerMutationLedger.getInstance();
        ledger.clearSession(opened.sessionId());
        int ledgerSizeBefore = ledger.size();
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("requestId", "mutation-before-close");
        ledger.executeOnce(
                opened.sessionId(),
                "mutation-before-close",
                "pageScanner.apply",
                requestBody,
                () -> {
                    JsonObject response = new JsonObject();
                    response.addProperty("ok", true);
                    return response;
                });

        assertTrue(coordinator.closeForBotJob(7, 42, 1));

        ArgumentCaptor<String> envelopeCaptor = ArgumentCaptor.forClass(String.class);
        verify(basicRemote).sendText(envelopeCaptor.capture());
        JsonObject envelope = JsonParser.parseString(envelopeCaptor.getValue()).getAsJsonObject();
        assertEquals(opened.sessionId(), envelope.get("sessionId").getAsString());
        assertEquals(7, envelope.get("homeBankingId").getAsInt());
        assertEquals(
                PageScannerWorkspaceCoordinator.WORKSPACE_CLOSED_OPERATION,
                envelope.get("operationId").getAsString());
        JsonObject payload = JsonParser.parseString(envelope.get("body").getAsString()).getAsJsonObject();
        assertTrue(payload.get("closed").getAsBoolean());
        assertEquals(opened.sessionId(), payload.get("sessionId").getAsString());
        assertEquals(42, payload.get("botJobId").getAsInt());
        assertEquals(1, payload.get("workspaceEpoch").getAsLong());
        assertEquals("BOT_JOB_CLOSED", payload.get("reason").getAsString());
        assertEquals(
                "The active Bot Job was closed. This Page Scanner workspace is no longer available.",
                payload.get("message").getAsString());

        assertTrue(activeDuringNotification.get());
        assertFalse(activeDuringCleanup.get());
        assertFalse(coordinator.isActiveWorkspace(opened.sessionId()));
        assertEquals(ledgerSizeBefore, ledger.size());
        assertSame(transport, WebSocketSessionManager.getSession(opened.sessionId()));
    }

    @Test
    void expiryNotifiesButExplicitCloseUsesOnlyItsCorrelatedResponsePath() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("expired", "explicit"));
        List<WorkspaceNotice> notices = new ArrayList<>();
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> true,
                ids::remove,
                clock,
                4,
                (context, reason) -> {},
                sessionId -> true,
                PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE,
                (context, reason, message) -> {
                    notices.add(new WorkspaceNotice(context.sessionId(), reason, message));
                    return true;
                });
        PageScannerWorkspaceCoordinator.OpenResult expired =
                coordinator.open(request(context(7, 42, "Payments")));

        clock.advance(PageScannerWorkspaceCoordinator.WORKSPACE_TTL);
        coordinator.purgeExpired();

        assertEquals(1, notices.size());
        assertEquals(expired.sessionId(), notices.get(0).sessionId());
        assertEquals(PageScannerWorkspaceCoordinator.CloseReason.EXPIRED, notices.get(0).reason());
        assertEquals(
                "This Page Scanner workspace expired. Open it again from Bot Job Details.",
                notices.get(0).message());

        PageScannerWorkspaceCoordinator.OpenResult explicit =
                coordinator.open(request(context(7, 42, "Payments")));
        assertTrue(coordinator.close(explicit.sessionId()));
        assertEquals(1, notices.size());
    }

    @Test
    void doesNotDuplicateAWindowWhileItsInitialWebSocketConnectionIsStillStarting() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        AtomicInteger launchCount = new AtomicInteger();
        PageScannerWorkspaceCoordinator coordinator = connectionAwareCoordinator(
                clock,
                launchCount,
                sessionId -> false);

        PageScannerWorkspaceCoordinator.OpenResult first =
                coordinator.open(request(context(7, 42, "Payments")));
        clock.advance(PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE.minusMillis(1));
        PageScannerWorkspaceCoordinator.OpenResult stillOpening =
                coordinator.open(request(context(7, 42, "Payments")));

        assertEquals(first.sessionId(), stillOpening.sessionId());
        assertFalse(stillOpening.launched());
        assertTrue(stillOpening.alreadyOpen());
        assertEquals("Page Scanner workspace is opening.", stillOpening.message());
        assertEquals(1, launchCount.get());
    }

    @Test
    void relaunchesTheSameLogicalSessionWhenInitialConnectionGraceExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        AtomicInteger launchCount = new AtomicInteger();
        PageScannerWorkspaceCoordinator coordinator = connectionAwareCoordinator(
                clock,
                launchCount,
                sessionId -> false);
        PageScannerWorkspaceCoordinator.OpenResult first =
                coordinator.open(request(context(7, 42, "Payments")));

        clock.advance(PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE);
        PageScannerWorkspaceCoordinator.OpenResult reopened =
                coordinator.open(request(context(7, 42, "Payments")));

        assertEquals(first.sessionId(), reopened.sessionId());
        assertTrue(reopened.launched());
        assertFalse(reopened.alreadyOpen());
        assertEquals("Page Scanner workspace reopened.", reopened.message());
        assertEquals(2, launchCount.get());
        assertEquals(1, coordinator.activeWorkspaceCount());
    }

    @Test
    void altF4AfterBootstrapDefersOneRelaunchUntilReconnectGraceExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        AtomicInteger launchCount = new AtomicInteger();
        AtomicBoolean connected = new AtomicBoolean(false);
        ArrayDeque<Runnable> deferred = new ArrayDeque<>();
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> {
                    launchCount.incrementAndGet();
                    return true;
                },
                () -> "connection-aware",
                clock,
                1,
                (context, reason) -> {},
                sessionId -> connected.get(),
                PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE,
                (context, reason, message) -> false,
                (previous, current, message) -> true,
                PageScannerWorkspaceCoordinator.RECONNECT_GRACE,
                (delay, task) -> deferred.add(task));
        PageScannerWorkspaceCoordinator.OpenResult first =
                coordinator.open(request(context(7, 42, "Payments")));

        connected.set(true);
        assertEquals(first.sessionId(), coordinator.bootstrap(first.sessionId()).sessionId());
        connected.set(false);
        assertTrue(coordinator.disconnected(first.sessionId()));
        PageScannerWorkspaceCoordinator.OpenResult reopened =
                coordinator.open(request(context(7, 42, "Payments")));

        assertEquals(first.sessionId(), reopened.sessionId());
        assertFalse(reopened.launched());
        assertTrue(reopened.alreadyOpen());
        assertEquals(1, launchCount.get());
        assertEquals(1, deferred.size());

        clock.advance(PageScannerWorkspaceCoordinator.RECONNECT_GRACE);
        deferred.remove().run();
        assertEquals(2, launchCount.get());
    }

    @Test
    void transientReconnectDuringGraceDoesNotLaunchDuplicateScannerWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        AtomicInteger launchCount = new AtomicInteger();
        AtomicBoolean connected = new AtomicBoolean(false);
        ArrayDeque<Runnable> deferred = new ArrayDeque<>();
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> {
                    launchCount.incrementAndGet();
                    return true;
                },
                () -> "reconnecting",
                clock,
                1,
                (context, reason) -> {},
                sessionId -> connected.get(),
                PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE,
                (context, reason, message) -> false,
                (previous, current, message) -> true,
                PageScannerWorkspaceCoordinator.RECONNECT_GRACE,
                (delay, task) -> deferred.add(task));
        PageScannerWorkspaceCoordinator.OpenResult first =
                coordinator.open(request(context(7, 42, "Payments")));
        connected.set(true);
        coordinator.bootstrap(first.sessionId());
        connected.set(false);
        coordinator.disconnected(first.sessionId());

        coordinator.open(request(context(7, 42, "Payments")));
        connected.set(true);
        coordinator.bootstrap(first.sessionId());
        clock.advance(PageScannerWorkspaceCoordinator.RECONNECT_GRACE);
        deferred.remove().run();

        assertEquals(1, launchCount.get());
        assertEquals(1, coordinator.activeWorkspaceCount());
        assertTrue(coordinator.isActiveWorkspace(first.sessionId()));
    }

    @Test
    void failedRetargetKeepsPreviousWorkspaceAndResourcesIntact() {
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("old", "new"));
        List<PageScannerWorkspaceCoordinator.CloseReason> cleanupReasons = new ArrayList<>();
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> true,
                ids::remove,
                Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC),
                1,
                (context, reason) -> cleanupReasons.add(reason),
                sessionId -> true,
                PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE,
                (context, reason, message) -> false,
                (previous, current, message) -> false);
        PageScannerWorkspaceCoordinator.OpenResult previous =
                coordinator.open(request(context(7, 42, "Payments")));

        PageScannerWorkspaceCoordinator.OpenResult failed =
                coordinator.open(request(context(7, 43, "Transfers")));

        assertFalse(failed.ok());
        assertEquals(previous.sessionId(), failed.sessionId());
        assertTrue(coordinator.isActiveWorkspace(previous.sessionId()));
        assertEquals(42, coordinator.bootstrap(previous.sessionId()).context().botJobId());
        assertEquals(List.of(), cleanupReasons);
    }

    @Test
    void closeActiveRetiresTheSingleScannerEvenAfterItsOriginalBotJobChanged() {
        ArrayDeque<String> ids = new ArrayDeque<>(List.of("old", "current"));
        List<PageScannerWorkspaceCoordinator.CloseReason> cleanupReasons = new ArrayList<>();
        PageScannerWorkspaceCoordinator coordinator = new PageScannerWorkspaceCoordinator(
                sessionId -> true,
                ids::remove,
                Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC),
                1,
                (context, reason) -> cleanupReasons.add(reason),
                sessionId -> true,
                PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE,
                (context, reason, message) -> false,
                (previous, current, message) -> true);
        coordinator.open(request(context(7, 42, "Payments")));
        PageScannerWorkspaceCoordinator.OpenResult current =
                coordinator.open(request(context(7, 43, "Transfers")));

        assertTrue(coordinator.closeActive());
        assertFalse(coordinator.isActiveWorkspace(current.sessionId()));
        assertEquals(
                List.of(
                        PageScannerWorkspaceCoordinator.CloseReason.SUPERSEDED,
                        PageScannerWorkspaceCoordinator.CloseReason.BOT_JOB_CLOSED),
                cleanupReasons);
    }

    @Test
    void connectedWorkspaceRemainsSingleEvenAfterLaunchGraceExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T12:00:00Z"));
        AtomicInteger launchCount = new AtomicInteger();
        PageScannerWorkspaceCoordinator coordinator = connectionAwareCoordinator(
                clock,
                launchCount,
                sessionId -> true);
        PageScannerWorkspaceCoordinator.OpenResult first =
                coordinator.open(request(context(7, 42, "Payments")));

        clock.advance(PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE.plusSeconds(1));
        PageScannerWorkspaceCoordinator.OpenResult repeated =
                coordinator.open(request(context(7, 42, "Payments")));

        assertEquals(first.sessionId(), repeated.sessionId());
        assertFalse(repeated.launched());
        assertTrue(repeated.alreadyOpen());
        assertEquals(1, launchCount.get());
    }

    private static PageScannerWorkspaceCoordinator connectionAwareCoordinator(
            Clock clock,
            AtomicInteger launchCount,
            PageScannerWorkspaceCoordinator.WorkspaceConnectionProbe connectionProbe) {
        return new PageScannerWorkspaceCoordinator(
                sessionId -> {
                    launchCount.incrementAndGet();
                    return true;
                },
                () -> "connection-aware",
                clock,
                4,
                (context, reason) -> {},
                connectionProbe,
                PageScannerWorkspaceCoordinator.INITIAL_CONNECTION_GRACE);
    }

    private static PageScannerWorkspaceCoordinator coordinator(
            ArrayDeque<String> ids,
            PageScannerWorkspaceCoordinator.WorkspaceLauncher launcher) {
        return new PageScannerWorkspaceCoordinator(
                launcher,
                ids::remove,
                Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC));
    }

    private static PageScannerWorkspaceCoordinator.OpenRequest request(
            PageScannerWorkspaceCoordinator.WorkspaceContext context) {
        return new PageScannerWorkspaceCoordinator.OpenRequest(
                ScannerWorkspaceSessions.BOT_JOB_TASKS,
                context);
    }

    private static PageScannerWorkspaceCoordinator.WorkspaceContext context(
            int homeBankingId,
            int botJobId,
            String botJobName) {
        return new PageScannerWorkspaceCoordinator.WorkspaceContext(
                homeBankingId,
                botJobId,
                1,
                botJobName,
                11,
                "https://bank.example/login",
                "chromium",
                "--start-maximized",
                "C:\\ARWeb\\data");
    }

    private record WorkspaceNotice(
            String sessionId,
            PageScannerWorkspaceCoordinator.CloseReason reason,
            String message) {}

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
