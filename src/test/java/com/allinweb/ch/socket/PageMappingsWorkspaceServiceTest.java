package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.allinweb.ch.db.ScannedElementRepository;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
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
import java.util.concurrent.atomic.AtomicReference;
import javax.websocket.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PageMappingsWorkspaceServiceTest {

    private static final Gson JSON = new Gson();

    @TempDir
    Path temporaryDirectory;

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
            assertEquals(
                    "https://safe.invalid/accounts",
                    response.getAsJsonArray("snapshots")
                            .get(0)
                            .getAsJsonObject()
                            .get("pageUrl")
                            .getAsString());
            assertFalse(response.toString().contains("password"));
            assertFalse(response.toString().contains("token"));
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

            JsonObject request = captureRequest("capture-valid", scanId);
            JsonObject response = service.capture(
                    request,
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    mock(Session.class),
                    connection);

            assertTrue(response.get("ok").getAsBoolean());
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
        service.openForBotJob(42);
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
                    captureRequest("capture-viewport", scanId),
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    mock(Session.class),
                    connection);

            assertTrue(response.get("ok").getAsBoolean());
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
                        captureRequest("reject-" + scanId.charAt(0), scanId),
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
        service.openForBotJob(42);
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
                        captureRequest("tamper", artifact.scanId()),
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
        service.openForBotJob(42);
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
                    captureRequest("wrong-owner", scanId),
                    DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER,
                    mock(Session.class),
                    connection);
            JsonObject traversal = service.capture(
                    captureRequest("traversal", "88888888-8888-8888-8888-888888888888"),
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

    private static JsonObject captureRequest(String requestId, String scanId) {
        JsonObject request = new JsonObject();
        request.addProperty("requestId", requestId);
        request.addProperty("scanId", scanId);
        return request;
    }

    private static void createCaptureTables(Connection connection) throws Exception {
        createSnapshotTable(connection);
        connection.createStatement().executeUpdate(
                "CREATE TABLE scanned_element ("
                        + "id INTEGER PRIMARY KEY, home_banking_id INTEGER, bot_job_id INTEGER, "
                        + "page_key TEXT, element_hash TEXT, last_scanned_at TEXT, scan_count INTEGER)");
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
