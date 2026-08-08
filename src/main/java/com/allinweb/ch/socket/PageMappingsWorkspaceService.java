package com.allinweb.ch.socket;

import com.allinweb.ch.db.ScannedElementRepository;
import com.allinweb.ch.facade.BotJobDetailsWorkspaceRegistry;
import com.allinweb.ch.facade.PageScanUrlRedactor;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

/** Owner-scoped backend seam for the detached Page Mappings explorer. */
@Slf4j
public final class PageMappingsWorkspaceService {

    static final String RETARGET_OPERATION = "pageMappings.retarget";
    private static final String SESSION_ID = DetachedWorkspaceSessions.PAGE_MAPPINGS_MANAGER;
    private static final Gson JSON = new Gson();
    private static final long MAX_MANIFEST_BYTES = 1_000_000L;
    private static final long MAX_METADATA_BYTES = 1_000_000L;
    private static final long MAX_JSON_ARTIFACT_BYTES = 16_000_000L;
    private static final long MAX_SCREENSHOT_BYTES = 8_000_000L;
    private static final Set<String> CAPTURE_FILES = Set.of(
            "elements.json", "rects.json", "meta.json", "screenshot.png");
    private static final PageMappingsWorkspaceService INSTANCE = new PageMappingsWorkspaceService();

    private final Object bindingLock = new Object();
    private final BotJobOwnerResolver botJobOwnerResolver;
    private final PageScannerOwnerResolver pageScannerOwnerResolver;
    private final WindowAccess windowAccess;
    private final RetargetPublisher retargetPublisher;
    private final RetargetObserver retargetObserver;
    private final ExactTransportAuthorizer transportAuthorizer;
    private final SnapshotRootResolver snapshotRootResolver;
    private Binding binding;

    public static PageMappingsWorkspaceService getInstance() {
        return INSTANCE;
    }

    private PageMappingsWorkspaceService() {
        this(
                PageMappingsWorkspaceService::resolveBotJobOwner,
                PageMappingsWorkspaceService::resolvePageScannerOwner,
                new WindowAccess() {
                    @Override
                    public boolean isOpen() {
                        return WebSocketSessionManager.isSessionOpen(SESSION_ID);
                    }

                    @Override
                    public boolean openOrFocus(int botJobId) {
                        return PagesOpenWorkspaceService.getInstance().openOrFocusDetachedWorkspace(
                                SESSION_ID,
                                botJobId,
                                "Page Mappings requested for this Bot Job.");
                    }
                },
                PageMappingsWorkspaceService::publishRetarget,
                (previous, current) -> MemoryListWorkspaceService.getInstance()
                        .pageMappingsRetargeted(previous, current),
                PageMappingsWorkspaceService::isExactRegisteredTransport,
                PageMappingsWorkspaceService::configuredSnapshotRoot);
    }

    PageMappingsWorkspaceService(
            BotJobOwnerResolver botJobOwnerResolver,
            PageScannerOwnerResolver pageScannerOwnerResolver,
            WindowAccess windowAccess,
            RetargetPublisher retargetPublisher,
            RetargetObserver retargetObserver,
            ExactTransportAuthorizer transportAuthorizer) {
        this(
                botJobOwnerResolver,
                pageScannerOwnerResolver,
                windowAccess,
                retargetPublisher,
                retargetObserver,
                transportAuthorizer,
                PageMappingsWorkspaceService::configuredSnapshotRoot);
    }

    PageMappingsWorkspaceService(
            BotJobOwnerResolver botJobOwnerResolver,
            PageScannerOwnerResolver pageScannerOwnerResolver,
            WindowAccess windowAccess,
            RetargetPublisher retargetPublisher,
            RetargetObserver retargetObserver,
            ExactTransportAuthorizer transportAuthorizer,
            SnapshotRootResolver snapshotRootResolver) {
        this.botJobOwnerResolver = Objects.requireNonNull(botJobOwnerResolver);
        this.pageScannerOwnerResolver = Objects.requireNonNull(pageScannerOwnerResolver);
        this.windowAccess = Objects.requireNonNull(windowAccess);
        this.retargetPublisher = Objects.requireNonNull(retargetPublisher);
        this.retargetObserver = Objects.requireNonNull(retargetObserver);
        this.transportAuthorizer = Objects.requireNonNull(transportAuthorizer);
        this.snapshotRootResolver = Objects.requireNonNull(snapshotRootResolver);
    }

    /** Opens Page Mappings for the active, server-owned Bot Job Details workspace. */
    public JsonObject openForBotJob(int botJobId) {
        try {
            return open(botJobOwnerResolver.resolve(botJobId), null);
        } catch (IllegalArgumentException unauthorized) {
            return failure(null, unauthorized.getMessage());
        } catch (RuntimeException failure) {
            log.warn("Unable to open Page Mappings for Bot Job {}", botJobId, failure);
            return failure(null, "Page Mappings workspace could not be opened.");
        }
    }

    /** Opens Page Mappings using only the context owned by the exact Page Scanner transport. */
    public JsonObject openFromPageScanner(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        try {
            requireExactPageScannerTransport(requesterSessionId, requesterTransport);
            OwnerTarget target = pageScannerOwnerResolver.resolve(requesterSessionId);
            validateOwnerAssertions(body, target, null);
            return open(target, body);
        } catch (IllegalArgumentException unauthorized) {
            return failure(body, unauthorized.getMessage());
        } catch (RuntimeException failure) {
            log.warn("Unable to open Page Mappings from Page Scanner {}", requesterSessionId, failure);
            return failure(body, "Page Mappings workspace could not be opened.");
        }
    }

    /** Loads owner-filtered capture history for the exact detached Page Mappings transport. */
    public JsonObject bootstrap(
            JsonObject body,
            String requesterSessionId,
            Session requesterTransport,
            Connection connection) {
        Binding authorized;
        try {
            authorized = authorizeDetachedRequest(body, requesterSessionId, requesterTransport);
        } catch (IllegalArgumentException unauthorized) {
            return failure(body, unauthorized.getMessage());
        }

        JsonObject response = baseResponse(body, authorized);
        response.addProperty("ok", true);
        response.addProperty("sessionId", SESSION_ID);
        JsonArray snapshots = new JsonArray();
        String sql = "SELECT scan_id, home_url_id, page_key, page_url, captured_at, element_count, "
                + "artifact_path, manifest_sha256, status, pinned "
                + "FROM page_scan_snapshot WHERE home_banking_id = ? AND bot_job_id = ? "
                + "ORDER BY captured_at DESC";
        try (PreparedStatement statement = Objects.requireNonNull(connection).prepareStatement(sql)) {
            statement.setInt(1, authorized.homeBankingId());
            statement.setInt(2, authorized.botJobId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    JsonObject snapshot = new JsonObject();
                    snapshot.addProperty("scanId", rows.getString("scan_id"));
                    if (rows.getObject("home_url_id") != null) {
                        snapshot.addProperty("homeUrlId", rows.getInt("home_url_id"));
                    }
                    snapshot.addProperty("pageKey", rows.getString("page_key"));
                    snapshot.addProperty(
                            "pageUrl", PageScanUrlRedactor.redact(rows.getString("page_url")));
                    snapshot.addProperty("capturedAt", rows.getString("captured_at"));
                    snapshot.addProperty("elementCount", rows.getInt("element_count"));
                    snapshot.addProperty("artifactPath", rows.getString("artifact_path"));
                    snapshot.addProperty("manifestSha256", rows.getString("manifest_sha256"));
                    snapshot.addProperty("status", rows.getString("status"));
                    snapshot.addProperty("pinned", rows.getInt("pinned") != 0);
                    snapshots.add(snapshot);
                }
            }
        } catch (Exception failure) {
            log.error(
                    "Unable to load Page Mappings snapshots for homeBankingId={} botJobId={}",
                    authorized.homeBankingId(),
                    authorized.botJobId(),
                    failure);
            response.addProperty("ok", false);
            response.addProperty("message", "Page Mappings history is unavailable.");
        }
        response.add("snapshots", snapshots);
        return response;
    }

    /** Loads one artifact that belongs to the exact currently bound owner. */
    public JsonObject capture(
            JsonObject body,
            String requesterSessionId,
            Session requesterTransport,
            Connection connection) {
        Binding authorized;
        try {
            authorized = authorizeDetachedRequest(body, requesterSessionId, requesterTransport);
        } catch (IllegalArgumentException unauthorized) {
            return failure(body, unauthorized.getMessage());
        }

        String scanId = string(body, "scanId");
        if (scanId.isBlank()) {
            return failure(body, "A valid scan ID is required.", authorized);
        }
        CaptureRow selected;
        try (PreparedStatement statement = Objects.requireNonNull(connection).prepareStatement(
                "SELECT scan_id, page_key, page_url, captured_at, element_count, artifact_path, manifest_sha256 "
                        + "FROM page_scan_snapshot "
                        + "WHERE scan_id = ? AND home_banking_id = ? AND bot_job_id = ? "
                        + "AND status = 'READY'")) {
            statement.setString(1, scanId);
            statement.setInt(2, authorized.homeBankingId());
            statement.setInt(3, authorized.botJobId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return captureFailure(
                            body, "The selected scan capture was not found.", authorized, scanId);
                }
                selected = new CaptureRow(
                        rows.getString("scan_id"),
                        rows.getString("page_key"),
                        rows.getString("page_url"),
                        rows.getString("captured_at"),
                        rows.getInt("element_count"),
                        rows.getString("artifact_path"),
                        rows.getString("manifest_sha256"));
            }
        } catch (Exception failure) {
            return captureFailure(
                    body, "The selected scan capture was not found.", authorized, scanId);
        }

        JsonObject response = baseResponse(body, authorized);
        try {
            VerifiedCapture capture = verifyCapture(
                    snapshotRootResolver.resolve(), selected, authorized, connection);
            response.addProperty("ok", true);
            response.addProperty("scanId", selected.scanId());
            response.addProperty("pageKey", selected.pageKey());
            response.addProperty("capturedAt", selected.capturedAt());
            response.addProperty("manifestSha256", selected.manifestSha256());
            response.add("elements", capture.elements());
            response.add("rectangles", capture.rectangles());
            response.add("viewport", capture.viewport());
            response.addProperty(
                    "screenshotBase64", Base64.getEncoder().encodeToString(capture.screenshot()));
            response.addProperty("screenshotMime", "image/png");
            return response;
        } catch (Exception failure) {
            log.warn("Unable to load Page Mappings capture {}", scanId, failure);
            return captureFailure(
                    body, "The selected scan artifact could not be loaded.", authorized, scanId);
        }
    }

    private static VerifiedCapture verifyCapture(
            Path configuredRoot,
            CaptureRow selected,
            Binding owner,
            Connection connection)
            throws Exception {
        requireText(selected.scanId(), "scan ID");
        requireText(selected.pageKey(), "page key");
        requireText(selected.capturedAt(), "capture timestamp");
        requireSha256(selected.manifestSha256(), "stored manifest checksum");
        if (selected.elementCount() < 0) {
            throw new IOException("The capture element count is invalid");
        }

        Path folder = resolveCaptureFolder(configuredRoot, selected.artifactPath(), owner);
        if (folder.getFileName() == null
                || !folder.getFileName().toString().endsWith(selected.scanId())) {
            throw new IOException("The capture artifact folder does not match its scan ID");
        }
        Path manifestFile = captureFile(folder, "manifest.json");
        byte[] manifestBytes = readLimited(manifestFile, MAX_MANIFEST_BYTES);
        if (!selected.manifestSha256().equalsIgnoreCase(sha256(manifestBytes))) {
            throw new IOException("The capture manifest checksum does not match its registry row");
        }
        JsonObject manifest = parseObject(manifestBytes, "capture manifest");
        requireEquals(text(manifest, "format"), "page-scan-snapshot-v1", "manifest format");
        requireEquals(text(manifest, "scanId"), selected.scanId(), "manifest scan ID");
        requireEquals(text(manifest, "capturedAt"), selected.capturedAt(), "manifest timestamp");
        requireInteger(manifest, "elementCount", selected.elementCount(), "manifest element count");
        JsonObject manifestOwner = requiredObject(manifest, "owner", "manifest owner");
        requireInteger(
                manifestOwner,
                "homeBankingId",
                owner.homeBankingId(),
                "manifest organization owner");
        requireInteger(
                manifestOwner, "botJobId", owner.botJobId(), "manifest Bot Job owner");
        JsonObject manifestPage = requiredObject(manifest, "page", "manifest page");
        requireEquals(text(manifestPage, "pageKey"), selected.pageKey(), "manifest page key");
        requireEquals(
                PageScanUrlRedactor.redact(text(manifestPage, "url")),
                PageScanUrlRedactor.redact(selected.pageUrl()),
                "manifest redacted page URL");

        JsonObject manifestFiles = requiredObject(manifest, "files", "manifest files");
        if (!manifestFiles.keySet().equals(CAPTURE_FILES)) {
            throw new IOException("The capture manifest file set is invalid");
        }
        Map<String, byte[]> verifiedFiles = new HashMap<>();
        for (String fileName : CAPTURE_FILES) {
            String expectedHash = text(manifestFiles, fileName);
            requireSha256(expectedHash, "manifest checksum for " + fileName);
            Path file = captureFile(folder, fileName);
            long maximum = maximumArtifactBytes(fileName);
            byte[] verifiedBytes = readLimited(file, maximum);
            if (!expectedHash.equalsIgnoreCase(sha256(verifiedBytes))) {
                throw new IOException("The capture file checksum is invalid: " + fileName);
            }
            verifiedFiles.put(fileName, verifiedBytes);
        }

        JsonArray elements = parseArray(
                verifiedFiles.get("elements.json"),
                "capture elements");
        if (elements.size() != selected.elementCount()) {
            throw new IOException("The capture element membership is incomplete");
        }
        JsonArray sourceRectangles = parseArray(
                verifiedFiles.get("rects.json"),
                "capture rectangles");
        JsonObject metadata = parseObject(
                verifiedFiles.get("meta.json"),
                "capture metadata");
        requireEquals(text(metadata, "scanId"), selected.scanId(), "metadata scan ID");
        requireEquals(text(metadata, "pageKey"), selected.pageKey(), "metadata page key");
        requireEquals(text(metadata, "capturedAt"), selected.capturedAt(), "metadata timestamp");
        requireInteger(
                metadata,
                "homeBankingId",
                owner.homeBankingId(),
                "metadata organization owner");
        requireInteger(metadata, "botJobId", owner.botJobId(), "metadata Bot Job owner");
        requireInteger(metadata, "elementCount", selected.elementCount(), "metadata element count");
        JsonObject manifestCapture = requiredObject(manifest, "capture", "manifest capture geometry");
        JsonObject metadataCapture = requiredObject(metadata, "capture", "metadata capture geometry");
        if (!manifestCapture.equals(metadataCapture)) {
            throw new IOException("The capture geometry metadata is inconsistent");
        }

        JsonObject viewport = viewport(metadataCapture);
        JsonArray enrichedElements = enrichElements(
                connection,
                owner.homeBankingId(),
                owner.botJobId(),
                selected.pageKey(),
                elements);
        JsonArray rectangles = rectangles(
                sourceRectangles,
                enrichedElements,
                "FULL_PAGE".equals(text(viewport, "screenshotScope")));
        byte[] screenshot = verifiedFiles.get("screenshot.png");
        if (screenshot.length == 0) {
            throw new IOException("The capture screenshot is empty");
        }
        return new VerifiedCapture(enrichedElements, rectangles, viewport, screenshot);
    }

    private static Path configuredSnapshotRoot() throws IOException {
        String configured = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_DB);
        if (configured == null || configured.isBlank()) {
            throw new IOException("The Page Scanner artifact root is not configured");
        }
        return Path.of(configured)
                .toAbsolutePath()
                .normalize()
                .resolve("page_diagnostics")
                .resolve("Scanned")
                .normalize();
    }

    private static Path resolveCaptureFolder(
            Path configuredRoot, String artifactPath, Binding owner) throws IOException {
        if (configuredRoot == null
                || !Files.isDirectory(configuredRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(configuredRoot)) {
            throw new IOException("The Page Scanner artifact root is unavailable");
        }
        String storedPath = requireText(artifactPath, "artifact path");
        Path relative;
        try {
            relative = Path.of(storedPath).normalize();
        } catch (RuntimeException invalidPath) {
            throw new IOException("The capture artifact path is invalid", invalidPath);
        }
        if (relative.isAbsolute()
                || relative.getNameCount() < 3
                || "..".equals(relative.getName(0).toString())) {
            throw new IOException("The capture artifact path is outside its owner root");
        }
        Path expectedOwner = Path.of(
                "org-" + owner.homeBankingId(), "bot-job-" + owner.botJobId());
        if (!relative.startsWith(expectedOwner)) {
            throw new IOException("The capture artifact path does not match its owner");
        }

        Path root = configuredRoot.toRealPath();
        Path cursor = root;
        for (Path segment : relative) {
            cursor = cursor.resolve(segment);
            if (Files.isSymbolicLink(cursor)
                    || !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Linked capture artifact paths are not allowed");
            }
        }
        Path folder = root.resolve(relative).normalize();
        if (!folder.startsWith(root)
                || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The capture artifact folder is unavailable");
        }
        Path realFolder = folder.toRealPath();
        if (!realFolder.startsWith(root)) {
            throw new IOException("The capture artifact folder escaped its root");
        }
        return realFolder;
    }

    private static Path captureFile(Path folder, String fileName) throws IOException {
        if (folder == null || fileName == null || fileName.isBlank()) {
            throw new IOException("A required capture file is unavailable");
        }
        Path file = folder.resolve(fileName).normalize();
        if (!folder.equals(file.getParent())
                || Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("A required capture file is unavailable: " + fileName);
        }
        return file;
    }

    private static JsonArray enrichElements(
            Connection connection,
            int homeBankingId,
            int botJobId,
            String pageKey,
            JsonArray elements)
            throws Exception {
        List<String> hashes = new ArrayList<>(elements.size());
        for (JsonElement value : elements) {
            if (value == null || value.isJsonNull() || !value.isJsonObject()) {
                hashes.add("");
                continue;
            }
            JsonObject element = value.getAsJsonObject();
            element.addProperty("pageKey", pageKey);
            ElementDTO dto = JSON.fromJson(element, ElementDTO.class);
            hashes.add(ScannedElementRepository.pageScopedHash(pageKey, dto));
        }

        Map<String, RegistryIdentity> registry = new HashMap<>();
        String sql = "SELECT id, element_hash, last_scanned_at, scan_count FROM scanned_element "
                + "WHERE home_banking_id = ? AND bot_job_id = ? AND page_key = ?";
        try (PreparedStatement statement = Objects.requireNonNull(connection).prepareStatement(sql)) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            statement.setString(3, pageKey);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    RegistryIdentity identity = new RegistryIdentity(
                            rows.getLong("id"),
                            rows.getString("element_hash"),
                            rows.getString("last_scanned_at"),
                            rows.getInt("scan_count"));
                    requireRegistryIdentity(identity);
                    if (registry.putIfAbsent(identity.elementHash(), identity) != null) {
                        throw new IOException("The scanned element registry contains duplicate identities");
                    }
                }
            }
        }

        for (int index = 0; index < elements.size(); index++) {
            JsonElement value = elements.get(index);
            if (value == null || value.isJsonNull() || !value.isJsonObject()) continue;
            RegistryIdentity identity = registry.get(hashes.get(index));
            if (identity == null) continue;
            JsonObject element = value.getAsJsonObject();
            element.addProperty("scannedElementId", identity.scannedElementId());
            element.addProperty("elementHash", identity.elementHash());
            element.addProperty("lastScannedAt", identity.lastScannedAt());
            element.addProperty("scanCount", identity.scanCount());
        }
        return elements;
    }

    private static void requireRegistryIdentity(RegistryIdentity identity) throws IOException {
        if (identity.scannedElementId() <= 0
                || identity.scanCount() <= 0
                || identity.lastScannedAt() == null
                || identity.lastScannedAt().isBlank()) {
            throw new IOException("The scanned element registry identity is incomplete");
        }
        requireSha256(identity.elementHash(), "scanned element identity");
    }

    private static JsonObject viewport(JsonObject capture) throws IOException {
        double cssWidth = requiredPositiveNumber(capture, "cssWidth");
        double cssHeight = requiredPositiveNumber(capture, "cssHeight");
        double devicePixelRatio = requiredPositiveNumber(capture, "devicePixelRatio");
        String storedScope = text(capture, "screenshotScope").trim().toLowerCase(Locale.ROOT);
        String responseScope;
        if ("viewport".equals(storedScope)) responseScope = "VIEWPORT";
        else if ("full_page".equals(storedScope)) responseScope = "FULL_PAGE";
        else throw new IOException("The capture screenshot scope is invalid");

        JsonObject viewport = new JsonObject();
        viewport.addProperty("cssWidth", cssWidth);
        viewport.addProperty("cssHeight", cssHeight);
        viewport.addProperty("devicePixelRatio", devicePixelRatio);
        viewport.addProperty("screenshotScope", responseScope);
        return viewport;
    }

    private static JsonArray rectangles(
            JsonArray source, JsonArray elements, boolean fullPage) throws IOException {
        JsonArray result = new JsonArray();
        Set<Integer> correlated = new HashSet<>();
        for (JsonElement value : source) {
            if (value == null || !value.isJsonObject()) {
                throw new IOException("The capture rectangle entry is invalid");
            }
            JsonObject rectangle = value.getAsJsonObject();
            int elementIndex = requiredNonNegativeInteger(rectangle, "elementIndex");
            if (elementIndex >= elements.size() || !correlated.add(elementIndex)) {
                throw new IOException("The capture rectangle correlation is invalid");
            }
            if (!requiredBoolean(rectangle, "found")) continue;
            JsonObject bounds = requiredObject(rectangle, "bounds", "capture rectangle bounds");
            double x = requiredFiniteNumber(bounds, fullPage ? "pageX" : "x");
            double y = requiredFiniteNumber(bounds, fullPage ? "pageY" : "y");
            double width = requiredPositiveNumber(bounds, "width");
            double height = requiredPositiveNumber(bounds, "height");

            JsonObject output = new JsonObject();
            output.addProperty("elementIndex", elementIndex);
            output.addProperty("x", x);
            output.addProperty("y", y);
            output.addProperty("width", width);
            output.addProperty("height", height);
            JsonElement element = elements.get(elementIndex);
            if (element != null && element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("scannedElementId")) {
                    output.add("scannedElementId", object.get("scannedElementId"));
                }
                if (object.has("elementHash")) {
                    output.add("elementHash", object.get("elementHash"));
                }
            }
            result.add(output);
        }
        return result;
    }

    private static byte[] readLimited(Path file, long maximumBytes) throws IOException {
        if (maximumBytes < 0 || maximumBytes >= Integer.MAX_VALUE) {
            throw new IOException("The capture file size limit is invalid");
        }
        try (InputStream input = Files.newInputStream(
                file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes((int) maximumBytes + 1);
            if (bytes.length > maximumBytes) {
                throw new IOException("The capture file exceeds its safe size");
            }
            return bytes;
        } catch (UnsupportedOperationException noFollowUnsupported) {
            throw new IOException("The capture file cannot be opened safely", noFollowUnsupported);
        }
    }

    private static JsonObject parseObject(byte[] bytes, String label) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IOException("The " + label + " is not an object");
            return parsed.getAsJsonObject();
        } catch (RuntimeException invalidJson) {
            throw new IOException("The " + label + " is invalid", invalidJson);
        }
    }

    private static JsonArray parseArray(byte[] bytes, String label) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            if (!parsed.isJsonArray()) throw new IOException("The " + label + " is not an array");
            return parsed.getAsJsonArray();
        } catch (RuntimeException invalidJson) {
            throw new IOException("The " + label + " is invalid", invalidJson);
        }
    }

    private static long maximumArtifactBytes(String fileName) {
        return switch (fileName) {
            case "meta.json" -> MAX_METADATA_BYTES;
            case "screenshot.png" -> MAX_SCREENSHOT_BYTES;
            default -> MAX_JSON_ARTIFACT_BYTES;
        };
    }

    private static String sha256(byte[] bytes) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }

    private static String requireText(String value, String label) throws IOException {
        if (value == null || value.isBlank()) throw new IOException("The " + label + " is missing");
        return value;
    }

    private static void requireEquals(String actual, String expected, String label)
            throws IOException {
        if (!Objects.equals(actual, expected)) {
            throw new IOException("The " + label + " does not match");
        }
    }

    private static void requireSha256(String value, String label) throws IOException {
        if (value == null || !value.matches("(?i)[0-9a-f]{64}")) {
            throw new IOException("The " + label + " is invalid");
        }
    }

    private static JsonObject requiredObject(JsonObject parent, String field, String label)
            throws IOException {
        if (parent == null || !parent.has(field) || !parent.get(field).isJsonObject()) {
            throw new IOException("The " + label + " is missing");
        }
        return parent.getAsJsonObject(field);
    }

    private static void requireInteger(
            JsonObject object, String field, int expected, String label) throws IOException {
        if (requiredNonNegativeInteger(object, field) != expected) {
            throw new IOException("The " + label + " does not match");
        }
    }

    private static int requiredNonNegativeInteger(JsonObject object, String field)
            throws IOException {
        double value = requiredFiniteNumber(object, field);
        if (value < 0 || value > Integer.MAX_VALUE || value != Math.rint(value)) {
            throw new IOException("The capture integer field is invalid: " + field);
        }
        return (int) value;
    }

    private static boolean requiredBoolean(JsonObject object, String field) throws IOException {
        if (object == null
                || !object.has(field)
                || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isBoolean()) {
            throw new IOException("The capture boolean field is invalid: " + field);
        }
        return object.get(field).getAsBoolean();
    }

    private static double requiredPositiveNumber(JsonObject object, String field)
            throws IOException {
        double value = requiredFiniteNumber(object, field);
        if (value <= 0) throw new IOException("The capture number is invalid: " + field);
        return value;
    }

    private static double requiredFiniteNumber(JsonObject object, String field) throws IOException {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            throw new IOException("The capture number is missing: " + field);
        }
        try {
            double value = object.get(field).getAsDouble();
            if (!Double.isFinite(value)) throw new IOException("The capture number is invalid: " + field);
            return value;
        } catch (RuntimeException invalidNumber) {
            throw new IOException("The capture number is invalid: " + field, invalidNumber);
        }
    }

    /**
     * Authorizes a Page Mappings Memory List contribution against the current owner and epoch.
     * Client owner fields remain assertions only and never choose the server-side scope.
     */
    Binding authorizeMemoryListSource(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        return authorizeDetachedRequest(body, requesterSessionId, requesterTransport);
    }

    private JsonObject open(OwnerTarget target, JsonObject request) {
        if (target.homeBankingId() <= 0 || target.botJobId() <= 0 || target.workspaceEpoch() <= 0) {
            return failure(request, "Page Mappings requires an active Bot Job owner.");
        }

        synchronized (bindingLock) {
            Binding previous = binding;
            boolean changed = previous == null || !previous.matches(target);
            Binding candidate = changed
                    ? new Binding(
                            UUID.randomUUID().toString(),
                            target.workspaceEpoch(),
                            target.homeBankingId(),
                            target.botJobId(),
                            target.botJobName())
                    : previous;
            boolean alreadyOpen = windowAccess.isOpen();
            binding = candidate;
            final boolean opened;
            try {
                opened = windowAccess.openOrFocus(candidate.botJobId());
            } catch (RuntimeException launchFailure) {
                binding = previous;
                throw launchFailure;
            }
            if (!opened) {
                binding = previous;
                return failure(request, "Page Mappings workspace could not be opened.");
            }

            boolean retargetPublished = changed && (alreadyOpen || windowAccess.isOpen());
            if (retargetPublished && !retargetPublisher.publish(candidate)) {
                binding = previous;
                return failure(request, "The existing Page Mappings page could not be retargeted.");
            }
            if (changed) {
                try {
                    retargetObserver.retargeted(previous, candidate);
                } catch (RuntimeException cleanupFailure) {
                    binding = previous;
                    log.warn("Unable to clear the previous Page Mappings Memory List owner", cleanupFailure);
                    if (retargetPublished
                            && previous != null
                            && !retargetPublisher.publish(previous)) {
                        log.error("Unable to publish the Page Mappings retarget rollback");
                    }
                    return failure(request, "Page Mappings owner could not be changed safely.");
                }
            }

            JsonObject response = baseResponse(request, candidate);
            response.addProperty("ok", true);
            response.addProperty("alreadyOpen", alreadyOpen);
            response.addProperty("retargeted", retargetPublished);
            response.addProperty(
                    "message",
                    retargetPublished
                            ? "Page Mappings workspace retargeted."
                            : alreadyOpen
                                    ? "Page Mappings workspace focused."
                            : "Page Mappings workspace opened.");
            return response;
        }
    }

    private Binding authorizeDetachedRequest(
            JsonObject body, String requesterSessionId, Session requesterTransport) {
        if (!SESSION_ID.equals(requesterSessionId)
                || !transportAuthorizer.isExact(requesterSessionId, requesterTransport)) {
            throw new IllegalArgumentException(
                    "The detached Page Mappings transport is not authoritative.");
        }
        synchronized (bindingLock) {
            if (binding == null) {
                throw new IllegalArgumentException("Page Mappings has no active Bot Job owner.");
            }
            validateOwnerAssertions(body, binding.asTarget(), binding.bindingEpoch());
            JsonObject nested = object(body, "snapshot");
            if (nested != null) {
                validateOwnerAssertions(nested, binding.asTarget(), binding.bindingEpoch());
            }
            return binding;
        }
    }

    private void requireExactPageScannerTransport(String sessionId, Session transport) {
        if (!ScannerWorkspaceSessions.isPageScannerSession(sessionId)
                || !transportAuthorizer.isExact(sessionId, transport)) {
            throw new IllegalArgumentException("The Page Scanner transport is not authoritative.");
        }
    }

    private static void validateOwnerAssertions(
            JsonObject body, OwnerTarget target, String expectedBindingEpoch) {
        assertInteger(body, "homeBankingId", target.homeBankingId());
        assertInteger(body, "botJobId", target.botJobId());
        assertLong(body, "workspaceEpoch", target.workspaceEpoch());
        if (expectedBindingEpoch != null) {
            assertEpoch(body, "bindingEpoch", expectedBindingEpoch);
            assertEpoch(body, "sourceBindingEpoch", expectedBindingEpoch);
        }
    }

    private static void assertInteger(JsonObject body, String field, int expected) {
        long asserted = assertedPositiveLong(body, field);
        if (asserted > 0 && asserted != expected) {
            throw new IllegalArgumentException("Page Mappings owner changed. Refresh this page.");
        }
    }

    private static void assertLong(JsonObject body, String field, long expected) {
        long asserted = assertedPositiveLong(body, field);
        if (asserted > 0 && asserted != expected) {
            throw new IllegalArgumentException("Page Mappings owner changed. Refresh this page.");
        }
    }

    private static long assertedPositiveLong(JsonObject body, String field) {
        if (body == null || !body.has(field) || body.get(field).isJsonNull()) return 0;
        try {
            long value = body.get(field).getAsLong();
            return Math.max(0, value);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Page Mappings owner assertion is invalid.");
        }
    }

    private static void assertEpoch(JsonObject body, String field, String expected) {
        if (body == null || !body.has(field) || body.get(field).isJsonNull()) return;
        String asserted = string(body, field).trim();
        if (!asserted.isEmpty() && !asserted.equals(expected)) {
            throw new IllegalArgumentException("Page Mappings owner changed. Refresh this page.");
        }
    }

    private static OwnerTarget resolveBotJobOwner(int botJobId) {
        BotJobDetailsWorkspaceRegistry.Snapshot active =
                BotJobDetailsWorkspaceRegistry.getInstance().require(botJobId);
        return new OwnerTarget(
                active.homeBankingId(),
                active.botJobId(),
                active.workspaceEpoch(),
                active.name());
    }

    private static OwnerTarget resolvePageScannerOwner(String requesterSessionId) {
        PageScannerWorkspaceCoordinator.WorkspaceContext context =
                PageScannerWorkspaceCoordinator.getInstance()
                        .authoritativeContext(requesterSessionId);
        return new OwnerTarget(
                context.homeBankingId(),
                context.botJobId(),
                context.workspaceEpoch(),
                context.botJobName());
    }

    private static boolean isExactRegisteredTransport(String sessionId, Session transport) {
        return transport != null
                && transport.isOpen()
                && WebSocketSessionManager.getSession(sessionId) == transport;
    }

    private static boolean publishRetarget(Binding current) {
        Session transport = WebSocketSessionManager.getSession(SESSION_ID);
        if (!isExactRegisteredTransport(SESSION_ID, transport)) return false;

        JsonObject body = baseResponse(null, current);
        body.addProperty("ok", true);
        body.addProperty("retargeted", true);
        body.addProperty("message", "Page Mappings workspace retargeted.");
        JsonObject envelope = new JsonObject();
        envelope.addProperty("homeBankingId", current.homeBankingId());
        envelope.addProperty("sessionId", SESSION_ID);
        envelope.addProperty("operationId", RETARGET_OPERATION);
        envelope.addProperty("body", body.toString());
        try {
            WebSocketSessionManager.sendText(transport, envelope.toString());
            return true;
        } catch (IOException | RuntimeException sendFailure) {
            log.warn("Unable to publish Page Mappings retarget", sendFailure);
            return false;
        }
    }

    private static JsonObject baseResponse(JsonObject request, Binding owner) {
        JsonObject response = new JsonObject();
        copyRequestId(response, request);
        response.addProperty("bindingEpoch", owner.bindingEpoch());
        response.addProperty("workspaceEpoch", owner.workspaceEpoch());
        response.addProperty("homeBankingId", owner.homeBankingId());
        response.addProperty("botJobId", owner.botJobId());
        response.addProperty("botJobName", owner.botJobName());
        return response;
    }

    private static JsonObject failure(JsonObject request, String message) {
        JsonObject response = new JsonObject();
        copyRequestId(response, request);
        response.addProperty("ok", false);
        response.addProperty(
                "message",
                message == null || message.isBlank()
                        ? "Page Mappings request was rejected."
                        : message);
        return response;
    }

    private static JsonObject failure(JsonObject request, String message, Binding owner) {
        JsonObject response = baseResponse(request, owner);
        response.addProperty("ok", false);
        response.addProperty("message", message);
        return response;
    }

    private static JsonObject captureFailure(
            JsonObject request, String message, Binding owner, String scanId) {
        JsonObject response = failure(request, message, owner);
        if (scanId != null && !scanId.isBlank()) response.addProperty("scanId", scanId);
        return response;
    }

    private static void copyRequestId(JsonObject target, JsonObject source) {
        String requestId = string(source, "requestId");
        if (!requestId.isBlank()) target.addProperty("requestId", requestId);
    }

    private static String string(JsonObject body, String field) {
        if (body == null || !body.has(field) || body.get(field).isJsonNull()) return "";
        try {
            return body.get(field).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String text(JsonObject body, String field) {
        return string(body, field);
    }

    private static JsonObject object(JsonObject body, String field) {
        if (body == null || !body.has(field) || !body.get(field).isJsonObject()) return null;
        return body.getAsJsonObject(field);
    }

    record OwnerTarget(
            int homeBankingId,
            int botJobId,
            long workspaceEpoch,
            String botJobName) {
        OwnerTarget {
            botJobName = botJobName == null ? "" : botJobName;
        }
    }

    record Binding(
            String bindingEpoch,
            long workspaceEpoch,
            int homeBankingId,
            int botJobId,
            String botJobName) {
        Binding {
            bindingEpoch = Objects.requireNonNull(bindingEpoch);
            botJobName = botJobName == null ? "" : botJobName;
        }

        boolean matches(OwnerTarget target) {
            return homeBankingId == target.homeBankingId()
                    && botJobId == target.botJobId()
                    && workspaceEpoch == target.workspaceEpoch();
        }

        OwnerTarget asTarget() {
            return new OwnerTarget(homeBankingId, botJobId, workspaceEpoch, botJobName);
        }
    }

    @FunctionalInterface
    interface BotJobOwnerResolver {
        OwnerTarget resolve(int botJobId);
    }

    @FunctionalInterface
    interface PageScannerOwnerResolver {
        OwnerTarget resolve(String requesterSessionId);
    }

    interface WindowAccess {
        boolean isOpen();

        boolean openOrFocus(int botJobId);
    }

    @FunctionalInterface
    interface RetargetPublisher {
        boolean publish(Binding current);
    }

    @FunctionalInterface
    interface RetargetObserver {
        void retargeted(Binding previous, Binding current);
    }

    @FunctionalInterface
    interface ExactTransportAuthorizer {
        boolean isExact(String sessionId, Session transport);
    }

    @FunctionalInterface
    interface SnapshotRootResolver {
        Path resolve() throws IOException;
    }

    private record CaptureRow(
            String scanId,
            String pageKey,
            String pageUrl,
            String capturedAt,
            int elementCount,
            String artifactPath,
            String manifestSha256) {}

    private record RegistryIdentity(
            long scannedElementId, String elementHash, String lastScannedAt, int scanCount) {}

    private record VerifiedCapture(
            JsonArray elements, JsonArray rectangles, JsonObject viewport, byte[] screenshot) {}
}
