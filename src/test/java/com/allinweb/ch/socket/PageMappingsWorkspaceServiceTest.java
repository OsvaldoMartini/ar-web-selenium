package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.websocket.Session;
import org.junit.jupiter.api.Test;

class PageMappingsWorkspaceServiceTest {

    @Test
    void pageMappingsIsRegisteredAsAFixedDetachedPresentation() {
        assertTrue(PagesOpenWorkspaceService.isFixedPresentationSession(
                DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER));
    }

    @Test
    void initialOpenBindsTheServerOwnerAndBootstrapQueriesOnlyThatOwner() throws Exception {
        AtomicReference<Integer> openedBotJob = new AtomicReference<>();
        PageMappingsWorkspaceService service = service(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, 11, "Payments"),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(99, 99, 99, "unused"),
                new PageMappingsWorkspaceService.WindowAccess() {
                    @Override
                    public boolean isOpen() {
                        return false;
                    }

                    @Override
                    public boolean openOrFocus(int botJobId) {
                        openedBotJob.set(botJobId);
                        return true;
                    }
                },
                binding -> false,
                (previous, current) -> {});

        JsonObject opened = service.openForBotJob(42);

        assertTrue(opened.get("ok").getAsBoolean());
        assertEquals(42, openedBotJob.get());
        assertEquals(7, opened.get("homeBankingId").getAsInt());
        assertEquals(42, opened.get("botJobId").getAsInt());
        assertEquals(11, opened.get("workspaceEpoch").getAsLong());

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            connection.createStatement().executeUpdate(
                    "CREATE TABLE page_scan_snapshot ("
                            + "scan_id TEXT, home_banking_id INTEGER, bot_job_id INTEGER, "
                            + "home_url_id INTEGER, page_key TEXT, page_url TEXT, captured_at TEXT, "
                            + "element_count INTEGER, artifact_path TEXT, manifest_sha256 TEXT, "
                            + "status TEXT, pinned INTEGER)");
            connection.createStatement().executeUpdate(
                    "INSERT INTO page_scan_snapshot VALUES "
                            + "('owned', 7, 42, NULL, 'payments', 'https://safe.invalid', "
                            + "'2026-08-07T10:00:00Z', 3, 'owned', 'sha', 'READY', 0), "
                            + "('other', 8, 43, NULL, 'other', 'https://other.invalid', "
                            + "'2026-08-07T11:00:00Z', 4, 'other', 'sha2', 'READY', 0)");
            JsonObject request = new JsonObject();
            request.addProperty("requestId", "bootstrap-1");
            JsonObject response = service.bootstrap(
                    request,
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    mock(Session.class),
                    connection);

            assertTrue(response.get("ok").getAsBoolean());
            assertEquals("bootstrap-1", response.get("requestId").getAsString());
            assertEquals(1, response.getAsJsonArray("snapshots").size());
            assertEquals(
                    "owned",
                    response.getAsJsonArray("snapshots")
                            .get(0)
                            .getAsJsonObject()
                            .get("scanId")
                            .getAsString());
        }
    }

    @Test
    void scannerOpenUsesServerContextAndRejectsClientOwnerSpoofing() {
        Session scannerTransport = mock(Session.class);
        AtomicReference<String> resolvedSession = new AtomicReference<>();
        PageMappingsWorkspaceService service = new PageMappingsWorkspaceService(
                id -> new PageMappingsWorkspaceService.OwnerTarget(1, id, 1, "unused"),
                sessionId -> {
                    resolvedSession.set(sessionId);
                    return new PageMappingsWorkspaceService.OwnerTarget(7, 42, 13, "Payments");
                },
                closedWindow(),
                binding -> false,
                (previous, current) -> {},
                (sessionId, transport) -> transport == scannerTransport);

        JsonObject spoofed = new JsonObject();
        spoofed.addProperty("homeBankingId", 8);
        spoofed.addProperty("botJobId", 42);
        JsonObject rejected = service.openFromPageScanner(
                spoofed, "page-scanner-authoritative", scannerTransport);

        assertFalse(rejected.get("ok").getAsBoolean());
        assertEquals("page-scanner-authoritative", resolvedSession.get());

        JsonObject compatibleOldClient = new JsonObject();
        compatibleOldClient.addProperty("homeBankingId", 0);
        compatibleOldClient.addProperty("botJobId", 42);
        JsonObject accepted = service.openFromPageScanner(
                compatibleOldClient, "page-scanner-authoritative", scannerTransport);

        assertTrue(accepted.get("ok").getAsBoolean());
        assertEquals(7, accepted.get("homeBankingId").getAsInt());
        assertEquals(13, accepted.get("workspaceEpoch").getAsLong());
    }

    @Test
    void reusedWindowPublishesAuthoritativeRetargetAndNotifiesMemoryListOwner() {
        AtomicBoolean open = new AtomicBoolean();
        AtomicReference<PageMappingsWorkspaceService.Binding> published = new AtomicReference<>();
        List<PageMappingsWorkspaceService.Binding> observed = new ArrayList<>();
        PageMappingsWorkspaceService service = service(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, id + 100L, "Job " + id),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(7, 1, 1, "unused"),
                window(open),
                binding -> {
                    published.set(binding);
                    return true;
                },
                (previous, current) -> {
                    if (previous != null) observed.add(previous);
                    observed.add(current);
                });

        JsonObject first = service.openForBotJob(41);
        open.set(true);
        JsonObject second = service.openForBotJob(42);

        assertTrue(first.get("ok").getAsBoolean());
        assertTrue(second.get("ok").getAsBoolean());
        assertTrue(second.get("retargeted").getAsBoolean());
        assertEquals(42, published.get().botJobId());
        assertEquals(7, published.get().homeBankingId());
        assertEquals(142, published.get().workspaceEpoch());
        assertEquals(3, observed.size());
        assertEquals(41, observed.get(1).botJobId());
        assertEquals(42, observed.get(2).botJobId());
        assertNotEquals(
                first.get("bindingEpoch").getAsString(),
                second.get("bindingEpoch").getAsString());
    }

    @Test
    void failedRetargetPublicationRollsBackTheAuthoritativeBinding() {
        AtomicBoolean open = new AtomicBoolean();
        AtomicBoolean refusePublication = new AtomicBoolean();
        Session pageMappingsTransport = mock(Session.class);
        PageMappingsWorkspaceService service = new PageMappingsWorkspaceService(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, id + 100L, "Job " + id),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(7, 1, 1, "unused"),
                window(open),
                binding -> !refusePublication.get(),
                (previous, current) -> {},
                (sessionId, transport) -> transport == pageMappingsTransport);

        JsonObject first = service.openForBotJob(41);
        open.set(true);
        refusePublication.set(true);
        JsonObject failed = service.openForBotJob(42);

        assertTrue(first.get("ok").getAsBoolean());
        assertFalse(failed.get("ok").getAsBoolean());
        JsonObject oldOwnerAssertion = new JsonObject();
        oldOwnerAssertion.addProperty("botJobId", 41);
        oldOwnerAssertion.addProperty("bindingEpoch", first.get("bindingEpoch").getAsString());
        PageMappingsWorkspaceService.Binding restored = service.authorizeMemoryListSource(
                oldOwnerAssertion,
                DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                pageMappingsTransport);
        assertEquals(41, restored.botJobId());
        assertEquals(first.get("bindingEpoch").getAsString(), restored.bindingEpoch());
    }

    @Test
    void detachedOperationsRequireExactTransportOwnerAndCurrentEpoch() {
        Session exact = mock(Session.class);
        Session stale = mock(Session.class);
        PageMappingsWorkspaceService service = new PageMappingsWorkspaceService(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, 21, "Payments"),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(7, 42, 21, "Payments"),
                closedWindow(),
                binding -> false,
                (previous, current) -> {},
                (sessionId, transport) -> transport == exact);
        JsonObject opened = service.openForBotJob(42);

        JsonObject request = new JsonObject();
        request.addProperty("botJobId", 42);
        request.addProperty("bindingEpoch", opened.get("bindingEpoch").getAsString());
        PageMappingsWorkspaceService.Binding authorized = service.authorizeMemoryListSource(
                request,
                DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                exact);
        assertEquals(42, authorized.botJobId());

        JsonObject staleEpoch = request.deepCopy();
        staleEpoch.addProperty("bindingEpoch", "stale");
        JsonObject rejected = service.bootstrap(
                staleEpoch,
                DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                exact,
                null);
        assertFalse(rejected.get("ok").getAsBoolean());

        JsonObject rejectedTransport = service.bootstrap(
                request,
                DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                stale,
                null);
        assertFalse(rejectedTransport.get("ok").getAsBoolean());
        assertSame(
                authorized,
                service.authorizeMemoryListSource(
                        request,
                        DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                        exact));
    }

    private static PageMappingsWorkspaceService service(
            PageMappingsWorkspaceService.BotJobOwnerResolver botJobResolver,
            PageMappingsWorkspaceService.PageScannerOwnerResolver scannerResolver,
            PageMappingsWorkspaceService.WindowAccess window,
            PageMappingsWorkspaceService.RetargetPublisher publisher,
            PageMappingsWorkspaceService.RetargetObserver observer) {
        return new PageMappingsWorkspaceService(
                botJobResolver,
                scannerResolver,
                window,
                publisher,
                observer,
                (sessionId, transport) -> true);
    }

    private static PageMappingsWorkspaceService.WindowAccess closedWindow() {
        return new PageMappingsWorkspaceService.WindowAccess() {
            @Override
            public boolean isOpen() {
                return false;
            }

            @Override
            public boolean openOrFocus(int botJobId) {
                return true;
            }
        };
    }

    private static PageMappingsWorkspaceService.WindowAccess window(AtomicBoolean open) {
        return new PageMappingsWorkspaceService.WindowAccess() {
            @Override
            public boolean isOpen() {
                return open.get();
            }

            @Override
            public boolean openOrFocus(int botJobId) {
                return true;
            }
        };
    }
}
