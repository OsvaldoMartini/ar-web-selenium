package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.allinweb.ch.db.ScannedElementRepository;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.PageScanSnapshotFileSecurity;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.websocket.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class PageMappingsWorkspaceServiceTest {

    private static final Gson JSON = new Gson();

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void configureSnapshotStorage() {
        ARPropertyManager.getInstance()
                .getProperties()
                .setProperty(ARPropertyEnum.PATH_DB.getValue(), temporaryDirectory.toString());
    }

    @Test
    void pageMappingsIsRegisteredAsAFixedDetachedPresentation() {
        assertTrue(PagesOpenWorkspaceService.isFixedPresentationSession(
                DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER));
    }

    @Test
    void initialOpenBindsTheServerOwnerAndBootstrapQueriesOnlyThatOwner() throws Exception {
        AtomicReference<Integer> openedBotJob = new AtomicReference<>();
        AtomicReference<String> windowCapability = new AtomicReference<>();
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

                    @Override
                    public boolean openOrFocus(int botJobId, String capability) {
                        openedBotJob.set(botJobId);
                        windowCapability.set(capability);
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
        assertTrue(windowCapability.get().length() >= 43);
        assertFalse(opened.has("windowCapability"));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            connection.createStatement().executeUpdate(
                    "CREATE TABLE page_scan_snapshot ("
                            + "scan_id TEXT, home_banking_id INTEGER, bot_job_id INTEGER, "
                            + "home_url_id INTEGER, page_key TEXT, page_url TEXT, captured_at TEXT, "
                            + "element_count INTEGER, artifact_path TEXT, manifest_sha256 TEXT, "
                            + "status TEXT, pinned INTEGER)");
            connection.createStatement().executeUpdate(
                    "INSERT INTO page_scan_snapshot VALUES "
                            + "('owned', 7, 42, NULL, 'payments', "
                            + "'https://client:password@safe.invalid/accounts?token=secret#private', "
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

            assertTrue(response.get("ok").getAsBoolean(), response::toString);
            assertEquals("bootstrap-1", response.get("requestId").getAsString());
            assertEquals(1, response.getAsJsonArray("snapshots").size());
            assertEquals(
                    "owned",
                    response.getAsJsonArray("snapshots")
                            .get(0)
                            .getAsJsonObject()
                            .get("scanId")
                            .getAsString());
            assertEquals(
                    "https://safe.invalid/accounts",
                    response.getAsJsonArray("snapshots")
                            .get(0)
                            .getAsJsonObject()
                            .get("pageUrl")
                            .getAsString());
            assertFalse(response.toString().contains("password"));
            assertFalse(response.toString().contains("token"));
            assertFalse(response.toString().contains("artifactPath"));
            assertFalse(response.toString().contains("owned/"));
        }
    }

    @Test
    void readyCaptureReturnsCorrelatedVerifiedGeometryAndAuthoritativeRegistryIdentity()
            throws Exception {
        Path root = temporaryDirectory.resolve("verified").resolve("Scanned");
        PageMappingsWorkspaceService service = captureService(root);
        JsonObject opened = service.openForBotJob(42);
        String pageKey = "url-v1:payments";
        String scanId = "11111111-1111-1111-1111-111111111111";
        String capturedAt = "2026-08-07T12:00:00Z";

        ElementDTO first = new ElementDTO();
        first.setXPath("//input[@id='iban']");
        first.setCssSelector("#iban");
        first.setTagName("input");
        first.setCoordinates("999,999,999,999");
        first.setDefinedName("IBAN");
        ElementDTO second = new ElementDTO();
        second.setXPath("//button[@id='submit']");
        second.setTagName("button");
        second.setCoordinates("888,888,888,888");
        JsonArray elements = JSON.toJsonTree(List.of(first, second)).getAsJsonArray();
        JsonArray rectangles = new JsonArray();
        rectangles.add(rectangle(0, true, 10, 20, 110, 220, 80, 24));
        rectangles.add(rectangle(1, false, 0, 0, 0, 0, 0, 0));
        Artifact artifact = artifact(
                root,
                scanId,
                7,
                42,
                pageKey,
                capturedAt,
                elements,
                rectangles,
                captureMetadata("full_page"));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createCaptureTables(connection);
            insertSnapshot(connection, artifact, "READY", "https://bank.example/accounts");
            String elementHash = ScannedElementRepository.pageScopedHash(pageKey, first);
            insertRegistry(
                    connection,
                    91,
                    7,
                    42,
                    pageKey,
                    elementHash,
                    "2026-08-07T12:01:00Z",
                    3);
            // Same locator identity outside the bound owner must never win enrichment.
            insertRegistry(
                    connection,
                    92,
                    8,
                    42,
                    pageKey,
                    elementHash,
                    "2026-08-07T12:02:00Z",
                    9);

            JsonObject request = captureRequest("capture-valid", scanId, opened);
            JsonObject response = service.capture(
                    request,
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    mock(Session.class),
                    connection);

            assertTrue(response.get("ok").getAsBoolean(), response::toString);
            assertEquals("capture-valid", response.get("requestId").getAsString());
            assertEquals(opened.get("bindingEpoch").getAsString(), response.get("bindingEpoch").getAsString());
            assertEquals(scanId, response.get("scanId").getAsString());
            assertEquals(pageKey, response.get("pageKey").getAsString());
            assertEquals(capturedAt, response.get("capturedAt").getAsString());
            assertEquals(artifact.manifestSha256(), response.get("manifestSha256").getAsString());
            assertEquals(
                    Base64.getEncoder().encodeToString("png-current-scan".getBytes(StandardCharsets.UTF_8)),
                    response.get("screenshotBase64").getAsString());

            JsonObject viewport = response.getAsJsonObject("viewport");
            assertEquals(1200.0d, viewport.get("cssWidth").getAsDouble());
            assertEquals(900.0d, viewport.get("cssHeight").getAsDouble());
            assertEquals(2.0d, viewport.get("devicePixelRatio").getAsDouble());
            assertEquals("FULL_PAGE", viewport.get("screenshotScope").getAsString());

            JsonObject enriched = response.getAsJsonArray("elements").get(0).getAsJsonObject();
            assertEquals(91, enriched.get("scannedElementId").getAsInt());
            assertEquals(elementHash, enriched.get("elementHash").getAsString());
            assertEquals(3, enriched.get("scanCount").getAsInt());
            assertEquals("2026-08-07T12:01:00Z", enriched.get("lastScannedAt").getAsString());
            assertEquals(pageKey, enriched.get("pageKey").getAsString());
            assertFalse(response.getAsJsonArray("elements").get(1).getAsJsonObject().has("scannedElementId"));

            // FULL_PAGE overlays use pageX/pageY, never ElementDTO.coordinates or viewport x/y.
            assertEquals(1, response.getAsJsonArray("rectangles").size());
            JsonObject overlay = response.getAsJsonArray("rectangles").get(0).getAsJsonObject();
            assertEquals(0, overlay.get("elementIndex").getAsInt());
            assertEquals(110.0d, overlay.get("x").getAsDouble());
            assertEquals(220.0d, overlay.get("y").getAsDouble());
            assertEquals(91, overlay.get("scannedElementId").getAsInt());
            assertEquals(elementHash, overlay.get("elementHash").getAsString());
        }
    }

    @Test
    void viewportCaptureUsesCssViewportCoordinatesWithoutApplyingDevicePixelRatio()
            throws Exception {
        Path root = temporaryDirectory.resolve("viewport").resolve("Scanned");
        PageMappingsWorkspaceService service = captureService(root);
        JsonObject opened = service.openForBotJob(42);
        String scanId = "12121212-1212-1212-1212-121212121212";

        ElementDTO element = new ElementDTO();
        element.setXPath("//input[@id='account']");
        element.setCoordinates("900,900,900,900");
        JsonArray elements = JSON.toJsonTree(List.of(element)).getAsJsonArray();
        JsonArray rectangles = new JsonArray();
        rectangles.add(rectangle(0, true, 15, 25, 115, 225, 75, 30));
        Artifact artifact = artifact(
                root,
                scanId,
                7,
                42,
                "url-v1:viewport",
                "2026-08-07T12:05:00Z",
                elements,
                rectangles,
                captureMetadata("viewport"));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createCaptureTables(connection);
            insertSnapshot(connection, artifact, "READY", "https://bank.example/accounts");

            JsonObject response = service.capture(
                    captureRequest("capture-viewport", scanId, opened),
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    mock(Session.class),
                    connection);

            assertTrue(response.get("ok").getAsBoolean(), response::toString);
            JsonObject viewport = response.getAsJsonObject("viewport");
            assertEquals("VIEWPORT", viewport.get("screenshotScope").getAsString());
            assertEquals(2.0d, viewport.get("devicePixelRatio").getAsDouble());
            JsonObject overlay = response.getAsJsonArray("rectangles").get(0).getAsJsonObject();
            assertEquals(15.0d, overlay.get("x").getAsDouble());
            assertEquals(25.0d, overlay.get("y").getAsDouble());
            assertEquals(75.0d, overlay.get("width").getAsDouble());
            assertEquals(30.0d, overlay.get("height").getAsDouble());
        }
    }

    @Test
    void captureRefusesNonReadyAndCrossOwnerRowsButPreservesRequestCorrelation()
            throws Exception {
        Path root = temporaryDirectory.resolve("not-ready").resolve("Scanned");
        PageMappingsWorkspaceService service = captureService(root);
        JsonObject opened = service.openForBotJob(42);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createSnapshotTable(connection);
            insertSnapshotRow(
                    connection,
                    "22222222-2222-2222-2222-222222222222",
                    7,
                    42,
                    "page-a",
                    "https://bank.example/a",
                    "2026-08-07T12:00:00Z",
                    0,
                    "org-7/bot-job-42/a/22222222-2222-2222-2222-222222222222",
                    "a".repeat(64),
                    "STAGED");
            insertSnapshotRow(
                    connection,
                    "33333333-3333-3333-3333-333333333333",
                    7,
                    42,
                    "page-b",
                    "https://bank.example/b",
                    "2026-08-07T12:01:00Z",
                    0,
                    "org-7/bot-job-42/b/33333333-3333-3333-3333-333333333333",
                    "b".repeat(64),
                    "FAILED");
            insertSnapshotRow(
                    connection,
                    "44444444-4444-4444-4444-444444444444",
                    8,
                    42,
                    "page-c",
                    "https://bank.example/c",
                    "2026-08-07T12:02:00Z",
                    0,
                    "org-8/bot-job-42/c/44444444-4444-4444-4444-444444444444",
                    "c".repeat(64),
                    "READY");

            for (String scanId : List.of(
                    "22222222-2222-2222-2222-222222222222",
                    "33333333-3333-3333-3333-333333333333",
                    "44444444-4444-4444-4444-444444444444")) {
                JsonObject response = service.capture(
                        captureRequest("reject-" + scanId.charAt(0), scanId, opened),
                        DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                        mock(Session.class),
                        connection);
                assertFalse(response.get("ok").getAsBoolean());
                assertEquals(scanId, response.get("scanId").getAsString());
                assertEquals(
                        opened.get("bindingEpoch").getAsString(),
                        response.get("bindingEpoch").getAsString());
                assertEquals("reject-" + scanId.charAt(0), response.get("requestId").getAsString());
            }
        }
    }

    @Test
    void captureRejectsManifestAndArtifactChecksumTampering() throws Exception {
        Path root = temporaryDirectory.resolve("tampering").resolve("Scanned");
        PageMappingsWorkspaceService service = captureService(root);
        JsonObject opened = service.openForBotJob(42);
        JsonArray elements = new JsonArray();
        JsonArray rectangles = new JsonArray();
        JsonObject metadata = captureMetadata("viewport");
        Artifact manifestTamper = artifact(
                root,
                "55555555-5555-5555-5555-555555555555",
                7,
                42,
                "page-manifest",
                "2026-08-07T13:00:00Z",
                elements,
                rectangles,
                metadata);
        Artifact fileTamper = artifact(
                root,
                "66666666-6666-6666-6666-666666666666",
                7,
                42,
                "page-file",
                "2026-08-07T13:01:00Z",
                elements,
                rectangles,
                metadata);
        Files.writeString(manifestTamper.folder().resolve("manifest.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(fileTamper.folder().resolve("rects.json"), "[{\"tampered\":true}]", StandardCharsets.UTF_8);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createSnapshotTable(connection);
            insertSnapshot(connection, manifestTamper, "READY", "https://bank.example/accounts");
            insertSnapshot(connection, fileTamper, "READY", "https://bank.example/accounts");
            for (Artifact artifact : List.of(manifestTamper, fileTamper)) {
                JsonObject response = service.capture(
                        captureRequest("tamper", artifact.scanId(), opened),
                        DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                        mock(Session.class),
                        connection);
                assertFalse(response.get("ok").getAsBoolean());
                assertEquals(artifact.scanId(), response.get("scanId").getAsString());
            }
        }
    }

    @Test
    void captureRejectsARehashedManifestWithWrongSemanticIdentityAndUnsafePath()
            throws Exception {
        Path root = temporaryDirectory.resolve("identity").resolve("Scanned");
        PageMappingsWorkspaceService service = captureService(root);
        JsonObject opened = service.openForBotJob(42);
        String scanId = "77777777-7777-7777-7777-777777777777";
        Artifact artifact = artifact(
                root,
                scanId,
                7,
                42,
                "page-identity",
                "2026-08-07T14:00:00Z",
                new JsonArray(),
                new JsonArray(),
                captureMetadata("viewport"));
        JsonObject manifest = JSON.fromJson(
                Files.readString(artifact.folder().resolve("manifest.json")), JsonObject.class);
        manifest.getAsJsonObject("owner").addProperty("homeBankingId", 999);
        byte[] changedManifest = manifest.toString().getBytes(StandardCharsets.UTF_8);
        Files.write(artifact.folder().resolve("manifest.json"), changedManifest);
        Artifact rehashed = artifact.withManifestSha256(sha256(changedManifest));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createSnapshotTable(connection);
            insertSnapshot(connection, rehashed, "READY", "https://bank.example/accounts");
            insertSnapshotRow(
                    connection,
                    "88888888-8888-8888-8888-888888888888",
                    7,
                    42,
                    "page-path",
                    "https://bank.example/path",
                    "2026-08-07T14:01:00Z",
                    0,
                    "../outside/88888888-8888-8888-8888-888888888888",
                    "8".repeat(64),
                    "READY");

            JsonObject wrongOwner = service.capture(
                    captureRequest("wrong-owner", scanId, opened),
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    mock(Session.class),
                    connection);
            JsonObject traversal = service.capture(
                    captureRequest(
                            "traversal",
                            "88888888-8888-8888-8888-888888888888",
                            opened),
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    mock(Session.class),
                    connection);
            assertFalse(wrongOwner.get("ok").getAsBoolean());
            assertFalse(traversal.get("ok").getAsBoolean());
            assertEquals(scanId, wrongOwner.get("scanId").getAsString());
            assertEquals(
                    "88888888-8888-8888-8888-888888888888",
                    traversal.get("scanId").getAsString());
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
    void stalePageScannerTransportIsRejectedBeforeItsOwnerCanBeResolved() {
        Session staleTransport = mock(Session.class);
        AtomicInteger ownerResolutions = new AtomicInteger();
        PageMappingsWorkspaceService service = new PageMappingsWorkspaceService(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, 21, "Payments"),
                sessionId -> {
                    ownerResolutions.incrementAndGet();
                    return new PageMappingsWorkspaceService.OwnerTarget(7, 42, 21, "Payments");
                },
                closedWindow(),
                binding -> true,
                (previous, current) -> {},
                (sessionId, transport) -> false);

        JsonObject response = service.openFromPageScanner(
                new JsonObject(), "page-scanner-stale", staleTransport);

        assertFalse(response.get("ok").getAsBoolean());
        assertEquals(0, ownerResolutions.get());
    }

    @Test
    void orphanedOpenWindowUsesTheAuthoritativeInvalidationOverload() {
        AtomicInteger invalidations = new AtomicInteger();
        PageMappingsWorkspaceService.WindowAccess window = new PageMappingsWorkspaceService.WindowAccess() {
            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public boolean openOrFocus(int botJobId) {
                return true;
            }

            @Override
            public void invalidate(
                    PageMappingsWorkspaceService.Binding retired,
                    PageMappingsWorkspaceService.Binding alternate,
                    String reason) {
                invalidations.incrementAndGet();
            }
        };
        PageMappingsWorkspaceService service = service(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, 21, "Payments"),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(7, 42, 21, "Payments"),
                window,
                binding -> true,
                (previous, current) -> {});

        JsonObject response = service.openForBotJob(42);

        assertFalse(response.get("ok").getAsBoolean());
        assertEquals(1, invalidations.get());
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
    void failedRetargetPublicationInvalidatesAndClosesFailClosed() {
        AtomicBoolean open = new AtomicBoolean();
        AtomicBoolean refusePublication = new AtomicBoolean();
        AtomicBoolean invalidated = new AtomicBoolean();
        Session pageMappingsTransport = mock(Session.class);
        PageMappingsWorkspaceService.WindowAccess window = new PageMappingsWorkspaceService.WindowAccess() {
            @Override
            public boolean isOpen() {
                return open.get();
            }

            @Override
            public boolean openOrFocus(int botJobId) {
                return true;
            }

            @Override
            public void invalidate(
                    PageMappingsWorkspaceService.Binding retired, String reason) {
                invalidated.set(true);
            }
        };
        PageMappingsWorkspaceService service = new PageMappingsWorkspaceService(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, id + 100L, "Job " + id),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(7, 1, 1, "unused"),
                window,
                binding -> !refusePublication.get(),
                (previous, current) -> {},
                (sessionId, transport) -> transport == pageMappingsTransport);

        JsonObject first = service.openForBotJob(41);
        open.set(true);
        refusePublication.set(true);
        JsonObject failed = service.openForBotJob(42);

        assertTrue(first.get("ok").getAsBoolean());
        assertFalse(failed.get("ok").getAsBoolean());
        assertTrue(invalidated.get());
        JsonObject oldOwnerAssertion = new JsonObject();
        oldOwnerAssertion.addProperty("botJobId", 41);
        oldOwnerAssertion.addProperty("bindingEpoch", first.get("bindingEpoch").getAsString());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.authorizeMemoryListSource(
                        oldOwnerAssertion,
                        DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                        pageMappingsTransport));
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

        assertThrows(
                IllegalArgumentException.class,
                () -> service.authorizeMemoryListSource(
                        new JsonObject(),
                        DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                        exact));
        JsonObject missingCaptureEpoch = new JsonObject();
        missingCaptureEpoch.addProperty("scanId", "scan-without-epoch");
        assertFalse(service.capture(
                        missingCaptureEpoch,
                        DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                        exact,
                        null)
                .get("ok")
                .getAsBoolean());

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

    @Test
    void oldDetachedRequestCannotPassAuthorizationAcrossANewTransportTakeover()
            throws Exception {
        Session oldTransport = mock(Session.class);
        Session replacementTransport = mock(Session.class);
        AtomicReference<Session> authoritativeTransport =
                new AtomicReference<>(oldTransport);
        AtomicBoolean open = new AtomicBoolean();
        AtomicInteger launches = new AtomicInteger();
        CountDownLatch retargetOwnsBindingLock = new CountDownLatch(1);
        CountDownLatch releaseRetarget = new CountDownLatch(1);
        CountDownLatch staleRequestStarted = new CountDownLatch(1);
        CountDownLatch staleAuthorizationChecked = new CountDownLatch(1);
        PageMappingsWorkspaceService.WindowAccess window = new PageMappingsWorkspaceService.WindowAccess() {
            @Override
            public boolean isOpen() {
                return open.get();
            }

            @Override
            public boolean openOrFocus(int botJobId) {
                return openOrFocus(botJobId, "unused");
            }

            @Override
            public boolean openOrFocus(int botJobId, String capability) {
                if (launches.incrementAndGet() == 2) {
                    retargetOwnsBindingLock.countDown();
                    try {
                        assertTrue(releaseRetarget.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                }
                return true;
            }
        };
        PageMappingsWorkspaceService service = new PageMappingsWorkspaceService(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, id + 100L, "Job " + id),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(7, 1, 1, "unused"),
                window,
                binding -> true,
                (previous, current) -> {},
                (sessionId, transport) -> {
                    if (transport == oldTransport) staleAuthorizationChecked.countDown();
                    return authoritativeTransport.get() == transport;
                });
        assertTrue(service.openForBotJob(41).get("ok").getAsBoolean());
        open.set(true);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<JsonObject> retarget = workers.submit(() -> service.openForBotJob(42));
            assertTrue(retargetOwnsBindingLock.await(5, TimeUnit.SECONDS));
            Future<JsonObject> staleRead = workers.submit(() -> {
                staleRequestStarted.countDown();
                try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
                    connection.createStatement().executeUpdate(
                            "CREATE TABLE page_scan_snapshot ("
                                    + "scan_id TEXT, home_banking_id INTEGER, bot_job_id INTEGER, "
                                    + "home_url_id INTEGER, page_key TEXT, page_url TEXT, "
                                    + "captured_at TEXT, element_count INTEGER, artifact_path TEXT, "
                                    + "manifest_sha256 TEXT, status TEXT, pinned INTEGER)");
                    return service.bootstrap(
                            new JsonObject(),
                            DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                            oldTransport,
                            connection);
                }
            });
            assertTrue(staleRequestStarted.await(5, TimeUnit.SECONDS));
            assertFalse(staleAuthorizationChecked.await(100, TimeUnit.MILLISECONDS));

            authoritativeTransport.set(replacementTransport);
            releaseRetarget.countDown();

            assertTrue(retarget.get(5, TimeUnit.SECONDS).get("ok").getAsBoolean());
            assertFalse(staleRead.get(5, TimeUnit.SECONDS).get("ok").getAsBoolean());
            assertTrue(staleAuthorizationChecked.await(5, TimeUnit.SECONDS));
        } finally {
            releaseRetarget.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void capabilityIsStrongPreservedForRetargetAndRotatedForFreshLaunch() {
        AtomicBoolean open = new AtomicBoolean();
        AtomicBoolean pending = new AtomicBoolean();
        List<String> launchedCapabilities = new ArrayList<>();
        PageMappingsWorkspaceService.WindowAccess window = new PageMappingsWorkspaceService.WindowAccess() {
            @Override
            public boolean isOpen() {
                return open.get();
            }

            @Override
            public boolean isLaunchPending() {
                return pending.get();
            }

            @Override
            public boolean openOrFocus(int botJobId) {
                return true;
            }

            @Override
            public boolean openOrFocus(int botJobId, String capability) {
                launchedCapabilities.add(capability);
                return true;
            }
        };
        PageMappingsWorkspaceService service = service(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, id + 100L, "Job " + id),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(7, 1, 1, "unused"),
                window,
                binding -> true,
                (previous, current) -> {});

        JsonObject first = service.openForBotJob(41);
        String firstCapability = launchedCapabilities.get(0);
        assertTrue(firstCapability.length() >= 43);
        assertFalse(first.has("windowCapability"));
        assertTrue(service.authorizeWindowTransport(
                capabilityTransport(firstCapability)));
        assertFalse(service.authorizeWindowTransport(capabilityTransport("wrong")));
        assertFalse(service.authorizeWindowTransport(capabilityTransport(null)));

        pending.set(true);
        JsonObject pendingReuse = service.openForBotJob(41);
        assertEquals(firstCapability, launchedCapabilities.get(1));
        assertEquals(
                first.get("bindingEpoch").getAsString(),
                pendingReuse.get("bindingEpoch").getAsString());

        pending.set(false);
        open.set(true);
        JsonObject retargeted = service.openForBotJob(42);
        assertEquals(firstCapability, launchedCapabilities.get(2));
        assertNotEquals(
                first.get("bindingEpoch").getAsString(),
                retargeted.get("bindingEpoch").getAsString());

        open.set(false);
        JsonObject fresh = service.openForBotJob(42);
        String freshCapability = launchedCapabilities.get(3);
        assertNotEquals(firstCapability, freshCapability);
        assertNotEquals(
                retargeted.get("bindingEpoch").getAsString(),
                fresh.get("bindingEpoch").getAsString());
        assertFalse(service.authorizeWindowTransport(
                capabilityTransport(firstCapability)));
        assertTrue(service.authorizeWindowTransport(
                capabilityTransport(freshCapability)));
    }

    @Test
    void freshLaunchObserverFailureRevokesTheCandidateAndClosesFailClosed() {
        AtomicReference<PageMappingsWorkspaceService.Binding> launched = new AtomicReference<>();
        AtomicReference<PageMappingsWorkspaceService.Binding> invalidated = new AtomicReference<>();
        AtomicReference<String> invalidationReason = new AtomicReference<>();
        PageMappingsWorkspaceService.WindowAccess window = new PageMappingsWorkspaceService.WindowAccess() {
            @Override
            public boolean isOpen() {
                return false;
            }

            @Override
            public boolean openOrFocus(int botJobId) {
                return true;
            }

            @Override
            public boolean openOrFocus(int botJobId, String capability) {
                launched.set(new PageMappingsWorkspaceService.Binding(
                        "not-authoritative", capability, 21, 7, botJobId, "Payments"));
                return true;
            }

            @Override
            public void invalidate(
                    PageMappingsWorkspaceService.Binding binding, String reason) {
                invalidated.set(binding);
                invalidationReason.set(reason);
            }
        };
        PageMappingsWorkspaceService service = service(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, 21, "Payments"),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(7, 42, 21, "Payments"),
                window,
                binding -> true,
                (previous, current) -> {
                    throw new IllegalStateException("memory cleanup failed");
                });

        JsonObject response = service.openForBotJob(42);

        assertFalse(response.get("ok").getAsBoolean());
        assertTrue(response.get("message").getAsString().contains("safely"));
        assertEquals(42, invalidated.get().botJobId());
        assertEquals(launched.get().windowCapability(), invalidated.get().windowCapability());
        assertTrue(invalidationReason.get().contains("ownership"));
        assertFalse(service.authorizeWindowTransport(
                capabilityTransport(launched.get().windowCapability())));
    }

    @Test
    void returningToOwnerARejectsBufferedEpochFromThePreviousAVisit() {
        AtomicBoolean open = new AtomicBoolean();
        Session exact = mock(Session.class);
        PageMappingsWorkspaceService service = new PageMappingsWorkspaceService(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, id + 100L, "Job " + id),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(7, 1, 1, "unused"),
                window(open),
                binding -> true,
                (previous, current) -> {},
                (sessionId, transport) -> transport == exact);
        JsonObject firstA = service.openForBotJob(41);
        open.set(true);
        service.openForBotJob(42);
        JsonObject secondA = service.openForBotJob(41);

        JsonObject buffered = new JsonObject();
        buffered.addProperty("sourceBindingEpoch", firstA.get("bindingEpoch").getAsString());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.authorizeMemoryListSource(
                        buffered,
                        DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                        exact));
        buffered.addProperty(
                "sourceBindingEpoch", secondA.get("bindingEpoch").getAsString());
        assertEquals(
                41,
                service.authorizeMemoryListSource(
                                buffered,
                                DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                                exact)
                        .botJobId());
    }

    @Test
    void authorizedMemoryOpenFinishesBeforeCommittedDeleteAndCannotRelaunchAfterIt()
            throws Exception {
        AtomicReference<String> memoryOwner = new AtomicReference<>();
        AtomicBoolean memoryWindowOpen = new AtomicBoolean();
        CountDownLatch operationEntered = new CountDownLatch(1);
        CountDownLatch releaseOperation = new CountDownLatch(1);
        PageMappingsWorkspaceService service = service(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, 21, "Payments"),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(7, 42, 21, "Payments"),
                invalidatingWindow(memoryWindowOpen),
                binding -> true,
                (previous, current) -> {
                    if (previous != null && current == null) {
                        memoryOwner.compareAndSet(previous.bindingEpoch(), null);
                        memoryWindowOpen.set(false);
                    }
                });
        JsonObject opened = service.openForBotJob(42);
        JsonObject request = epochRequest(opened);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> memoryOpen = workers.submit(() ->
                    service.withAuthorizedMemoryListSource(
                            request,
                            DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                            mock(Session.class),
                            owner -> {
                                operationEntered.countDown();
                                await(releaseOperation);
                                memoryOwner.set(owner.bindingEpoch());
                                memoryWindowOpen.set(true);
                                return true;
                            }));
            assertTrue(operationEntered.await(5, TimeUnit.SECONDS));
            Future<Boolean> deletion = workers.submit(() -> service.botJobsDeleted(List.of(42)));

            assertFalse(deletion.isDone());
            releaseOperation.countDown();

            assertTrue(memoryOpen.get(5, TimeUnit.SECONDS));
            assertTrue(deletion.get(5, TimeUnit.SECONDS));
            assertNull(memoryOwner.get());
            assertFalse(memoryWindowOpen.get());
        } finally {
            releaseOperation.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void authorizedMemorySyncFinishesBeforeRetargetAndItsOldOwnerIsThenCleared()
            throws Exception {
        AtomicBoolean liveWindow = new AtomicBoolean();
        AtomicReference<String> memoryOwner = new AtomicReference<>();
        CountDownLatch operationEntered = new CountDownLatch(1);
        CountDownLatch releaseOperation = new CountDownLatch(1);
        PageMappingsWorkspaceService service = service(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, id + 100L, "Job " + id),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(7, 42, 142, "Job 42"),
                window(liveWindow),
                binding -> true,
                (previous, current) -> {
                    if (previous != null
                            && (current == null
                                    || !previous.bindingEpoch().equals(current.bindingEpoch()))) {
                        memoryOwner.compareAndSet(previous.bindingEpoch(), null);
                    }
                });
        JsonObject opened = service.openForBotJob(42);
        liveWindow.set(true);
        JsonObject request = epochRequest(opened);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> memorySync = workers.submit(() ->
                    service.withAuthorizedMemoryListSource(
                            request,
                            DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                            mock(Session.class),
                            owner -> {
                                operationEntered.countDown();
                                await(releaseOperation);
                                memoryOwner.set(owner.bindingEpoch());
                                return true;
                            }));
            assertTrue(operationEntered.await(5, TimeUnit.SECONDS));
            Future<JsonObject> retarget = workers.submit(() -> service.openForBotJob(43));

            assertFalse(retarget.isDone());
            releaseOperation.countDown();

            assertTrue(memorySync.get(5, TimeUnit.SECONDS));
            assertTrue(retarget.get(5, TimeUnit.SECONDS).get("ok").getAsBoolean());
            assertNull(memoryOwner.get());
        } finally {
            releaseOperation.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void authorizedMemorySummaryCannotSurviveDeleteOrSameBotJobIdReuse()
            throws Exception {
        AtomicBoolean liveWindow = new AtomicBoolean();
        AtomicLong workspaceEpoch = new AtomicLong(21);
        AtomicReference<String> summaryOwner = new AtomicReference<>();
        CountDownLatch operationEntered = new CountDownLatch(1);
        CountDownLatch releaseOperation = new CountDownLatch(1);
        PageMappingsWorkspaceService.WindowAccess window = invalidatingWindow(liveWindow);
        PageMappingsWorkspaceService service = service(
                id -> new PageMappingsWorkspaceService.OwnerTarget(
                        7, id, workspaceEpoch.get(), "Payments"),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(
                        7, 42, workspaceEpoch.get(), "Payments"),
                window,
                binding -> true,
                (previous, current) -> {
                    if (previous != null && current == null) {
                        summaryOwner.compareAndSet(previous.bindingEpoch(), null);
                    }
                });
        JsonObject firstOpen = service.openForBotJob(42);
        liveWindow.set(true);
        JsonObject staleRequest = epochRequest(firstOpen);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> summary = workers.submit(() ->
                    service.withAuthorizedMemoryListSource(
                            staleRequest,
                            DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                            mock(Session.class),
                            owner -> {
                                operationEntered.countDown();
                                await(releaseOperation);
                                summaryOwner.set(owner.bindingEpoch());
                                return true;
                            }));
            assertTrue(operationEntered.await(5, TimeUnit.SECONDS));
            Future<Boolean> deletion = workers.submit(() -> service.botJobsDeleted(List.of(42)));
            assertFalse(deletion.isDone());
            releaseOperation.countDown();

            assertTrue(summary.get(5, TimeUnit.SECONDS));
            assertTrue(deletion.get(5, TimeUnit.SECONDS));
            assertNull(summaryOwner.get());

            workspaceEpoch.incrementAndGet();
            JsonObject secondOpen = service.openForBotJob(42);
            assertTrue(secondOpen.get("ok").getAsBoolean());
            assertNotEquals(
                    firstOpen.get("bindingEpoch").getAsString(),
                    secondOpen.get("bindingEpoch").getAsString());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.withAuthorizedMemoryListSource(
                            staleRequest,
                            DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                            mock(Session.class),
                            owner -> true));
            assertEquals(
                    secondOpen.get("bindingEpoch").getAsString(),
                    service.withAuthorizedMemoryListSource(
                            epochRequest(secondOpen),
                            DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                            mock(Session.class),
                            PageMappingsWorkspaceService.Binding::bindingEpoch));
        } finally {
            releaseOperation.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void committedDeletionClearsMemoryOwnerAndRetiresBotJobRegistry() {
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(42);
        botJob.setName("Payments");
        botJob.setHomeBankingId(7);
        BotJobDetailsWorkspaceRegistry registry =
                BotJobDetailsWorkspaceRegistry.getInstance();
        registry.activate(botJob, false);
        List<PageMappingsWorkspaceService.Binding> retired = new ArrayList<>();
        AtomicReference<PageMappingsWorkspaceService.Binding> invalidated =
                new AtomicReference<>();
        PageMappingsWorkspaceService.WindowAccess window = new PageMappingsWorkspaceService.WindowAccess() {
            @Override
            public boolean isOpen() {
                return false;
            }

            @Override
            public boolean openOrFocus(int botJobId) {
                return true;
            }

            @Override
            public void invalidate(
                    PageMappingsWorkspaceService.Binding binding, String reason) {
                invalidated.set(binding);
            }
        };
        PageMappingsWorkspaceService service = service(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, 21, "Payments"),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(7, 42, 21, "Payments"),
                window,
                binding -> false,
                (previous, current) -> {
                    if (previous != null && current == null) retired.add(previous);
                });
        JsonObject opened = service.openForBotJob(42);

        assertFalse(service.botJobsDeleted(List.of(99)));
        assertTrue(service.botJobsDeleted(List.of(42)));
        assertEquals(1, retired.size());
        assertEquals(42, retired.get(0).botJobId());
        assertEquals(42, invalidated.get().botJobId());
        assertThrows(IllegalArgumentException.class, () -> registry.require(42));
        JsonObject invalidation = PageMappingsWorkspaceService.invalidationBody(
                invalidated.get(), "deleted");
        assertTrue(invalidation.get("invalidated").getAsBoolean());
        assertEquals(
                opened.get("bindingEpoch").getAsString(),
                invalidation.get("bindingEpoch").getAsString());
        assertEquals(7, invalidation.get("homeBankingId").getAsInt());
        assertEquals(42, invalidation.get("botJobId").getAsInt());
    }

    private static Session capabilityTransport(String capability) {
        Session session = mock(Session.class);
        Map<String, List<String>> parameters = capability == null
                ? Map.of("sessionId", List.of(DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER))
                : Map.of(
                        "sessionId",
                        List.of(DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER),
                        "windowCapability",
                        List.of(capability));
        when(session.getRequestParameterMap()).thenReturn(parameters);
        return session;
    }

    private static PageMappingsWorkspaceService captureService(Path root) {
        return new PageMappingsWorkspaceService(
                id -> new PageMappingsWorkspaceService.OwnerTarget(7, id, 21, "Payments"),
                sessionId -> new PageMappingsWorkspaceService.OwnerTarget(7, 42, 21, "Payments"),
                closedWindow(),
                binding -> false,
                (previous, current) -> {},
                (sessionId, transport) -> true,
                () -> root);
    }

    private static JsonObject captureRequest(
            String requestId, String scanId, JsonObject opened) {
        JsonObject request = new JsonObject();
        request.addProperty("requestId", requestId);
        request.addProperty("scanId", scanId);
        request.addProperty("bindingEpoch", opened.get("bindingEpoch").getAsString());
        return request;
    }

    private static void createCaptureTables(Connection connection) throws Exception {
        createSnapshotTable(connection);
        connection.createStatement().executeUpdate(
                "CREATE TABLE scanned_element ("
                        + "id INTEGER PRIMARY KEY, home_banking_id INTEGER, bot_job_id INTEGER, "
                        + "page_key TEXT, element_hash TEXT, last_scanned_at TEXT, scan_count INTEGER, "
                        + "defined_name TEXT, client_named TEXT)");
    }

    private static void createSnapshotTable(Connection connection) throws Exception {
        connection.createStatement().executeUpdate(
                "CREATE TABLE page_scan_snapshot ("
                        + "scan_id TEXT, home_banking_id INTEGER, bot_job_id INTEGER, "
                        + "home_url_id INTEGER, page_key TEXT, page_url TEXT, captured_at TEXT, "
                        + "element_count INTEGER, artifact_path TEXT, manifest_sha256 TEXT, "
                        + "status TEXT, pinned INTEGER)");
    }

    private static void insertRegistry(
            Connection connection,
            long id,
            int homeBankingId,
            int botJobId,
            String pageKey,
            String elementHash,
            String lastScannedAt,
            int scanCount)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO scanned_element "
                        + "(id, home_banking_id, bot_job_id, page_key, element_hash, last_scanned_at, scan_count) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, id);
            statement.setInt(2, homeBankingId);
            statement.setInt(3, botJobId);
            statement.setString(4, pageKey);
            statement.setString(5, elementHash);
            statement.setString(6, lastScannedAt);
            statement.setInt(7, scanCount);
            statement.executeUpdate();
        }
    }

    private static void insertSnapshot(
            Connection connection, Artifact artifact, String status, String pageUrl)
            throws Exception {
        insertSnapshotRow(
                connection,
                artifact.scanId(),
                artifact.homeBankingId(),
                artifact.botJobId(),
                artifact.pageKey(),
                pageUrl,
                artifact.capturedAt(),
                artifact.elementCount(),
                artifact.artifactPath(),
                artifact.manifestSha256(),
                status);
    }

    private static void insertSnapshotRow(
            Connection connection,
            String scanId,
            int homeBankingId,
            int botJobId,
            String pageKey,
            String pageUrl,
            String capturedAt,
            int elementCount,
            String artifactPath,
            String manifestSha256,
            String status)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO page_scan_snapshot "
                        + "(scan_id, home_banking_id, bot_job_id, home_url_id, page_key, page_url, "
                        + "captured_at, element_count, artifact_path, manifest_sha256, status, pinned) "
                        + "VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, 0)")) {
            statement.setString(1, scanId);
            statement.setInt(2, homeBankingId);
            statement.setInt(3, botJobId);
            statement.setString(4, pageKey);
            statement.setString(5, pageUrl);
            statement.setString(6, capturedAt);
            statement.setInt(7, elementCount);
            statement.setString(8, artifactPath);
            statement.setString(9, manifestSha256);
            statement.setString(10, status);
            statement.executeUpdate();
        }
    }

    private static Artifact artifact(
            Path root,
            String scanId,
            int homeBankingId,
            int botJobId,
            String pageKey,
            String capturedAt,
            JsonArray elements,
            JsonArray rectangles,
            JsonObject capture)
            throws Exception {
        Path folder = root.resolve("org-" + homeBankingId)
                .resolve("bot-job-" + botJobId)
                .resolve("page")
                .resolve(capturedAt.replace(':', '-') + "-" + scanId);
        Files.createDirectories(folder);
        byte[] screenshot = "png-current-scan".getBytes(StandardCharsets.UTF_8);
        byte[] elementBytes = elements.toString().getBytes(StandardCharsets.UTF_8);
        byte[] rectangleBytes = rectangles.toString().getBytes(StandardCharsets.UTF_8);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("scanId", scanId);
        metadata.addProperty("homeBankingId", homeBankingId);
        metadata.addProperty("botJobId", botJobId);
        metadata.addProperty("pageKey", pageKey);
        metadata.addProperty("pageUrl", "https://bank.example/accounts");
        metadata.addProperty("normalizedUrl", "https://bank.example/accounts");
        metadata.addProperty("capturedAt", capturedAt);
        metadata.addProperty("elementCount", elements.size());
        metadata.add("capture", capture.deepCopy());
        byte[] metadataBytes = metadata.toString().getBytes(StandardCharsets.UTF_8);

        Files.write(folder.resolve("screenshot.png"), screenshot);
        Files.write(folder.resolve("elements.json"), elementBytes);
        Files.write(folder.resolve("rects.json"), rectangleBytes);
        Files.write(folder.resolve("meta.json"), metadataBytes);

        JsonObject files = new JsonObject();
        files.addProperty("screenshot.png", sha256(screenshot));
        files.addProperty("elements.json", sha256(elementBytes));
        files.addProperty("rects.json", sha256(rectangleBytes));
        files.addProperty("meta.json", sha256(metadataBytes));
        JsonObject owner = new JsonObject();
        owner.addProperty("homeBankingId", homeBankingId);
        owner.addProperty("botJobId", botJobId);
        JsonObject page = new JsonObject();
        page.addProperty("pageKey", pageKey);
        page.addProperty("url", "https://bank.example/accounts");
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", "page-scan-snapshot-v1");
        manifest.addProperty("scanId", scanId);
        manifest.addProperty("capturedAt", capturedAt);
        manifest.add("owner", owner);
        manifest.add("page", page);
        manifest.add("capture", capture.deepCopy());
        manifest.addProperty("elementCount", elements.size());
        manifest.add("files", files);
        byte[] manifestBytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
        Files.write(folder.resolve("manifest.json"), manifestBytes);
        PageScanSnapshotFileSecurity.secureExistingRoot(root);
        PageScanSnapshotFileSecurity.requirePrivateCaptureDirectory(root, folder);
        String artifactPath = root.relativize(folder).toString().replace('\\', '/');
        return new Artifact(
                scanId,
                homeBankingId,
                botJobId,
                pageKey,
                capturedAt,
                elements.size(),
                artifactPath,
                sha256(manifestBytes),
                folder);
    }

    private static JsonObject captureMetadata(String scope) {
        JsonObject capture = new JsonObject();
        capture.addProperty("screenshotScope", scope);
        capture.addProperty("devicePixelRatio", 2.0d);
        capture.addProperty("cssWidth", 1200.0d);
        capture.addProperty("cssHeight", 900.0d);
        capture.addProperty("pixelWidth", 2400);
        capture.addProperty("pixelHeight", 1800);
        capture.addProperty("scrollX", 100.0d);
        capture.addProperty("scrollY", 200.0d);
        capture.addProperty("viewFingerprint", "a".repeat(64));
        capture.addProperty("fingerprintNodeCount", 2);
        return capture;
    }

    private static JsonObject rectangle(
            int elementIndex,
            boolean found,
            double x,
            double y,
            double pageX,
            double pageY,
            double width,
            double height) {
        JsonObject rectangle = new JsonObject();
        rectangle.addProperty("elementIndex", elementIndex);
        rectangle.addProperty("xPath", "//element[" + (elementIndex + 1) + "]");
        rectangle.addProperty("iframeXPath", "");
        rectangle.addProperty("found", found);
        if (found) {
            JsonObject bounds = new JsonObject();
            bounds.addProperty("x", x);
            bounds.addProperty("y", y);
            bounds.addProperty("pageX", pageX);
            bounds.addProperty("pageY", pageY);
            bounds.addProperty("width", width);
            bounds.addProperty("height", height);
            rectangle.add("bounds", bounds);
        }
        return rectangle;
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder(digest.length * 2);
        for (byte part : digest) {
            value.append(Character.forDigit((part >> 4) & 0x0f, 16));
            value.append(Character.forDigit(part & 0x0f, 16));
        }
        return value.toString();
    }

    private record Artifact(
            String scanId,
            int homeBankingId,
            int botJobId,
            String pageKey,
            String capturedAt,
            int elementCount,
            String artifactPath,
            String manifestSha256,
            Path folder) {
        Artifact withManifestSha256(String replacement) {
            return new Artifact(
                    scanId,
                    homeBankingId,
                    botJobId,
                    pageKey,
                    capturedAt,
                    elementCount,
                    artifactPath,
                    replacement,
                    folder);
        }
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

    private static PageMappingsWorkspaceService.WindowAccess invalidatingWindow(
            AtomicBoolean open) {
        return new PageMappingsWorkspaceService.WindowAccess() {
            @Override
            public boolean isOpen() {
                return open.get();
            }

            @Override
            public boolean openOrFocus(int botJobId) {
                return true;
            }

            @Override
            public void invalidate(
                    PageMappingsWorkspaceService.Binding retired,
                    PageMappingsWorkspaceService.Binding alternate,
                    String reason) {
                open.set(false);
            }
        };
    }

    private static JsonObject epochRequest(JsonObject opened) {
        JsonObject request = new JsonObject();
        request.addProperty(
                "sourceBindingEpoch", opened.get("bindingEpoch").getAsString());
        return request;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
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
