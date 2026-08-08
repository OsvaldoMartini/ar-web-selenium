package com.allinweb.ch.facade;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.util.PageDiagnosticDumper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/** Writes one immutable artifact set for each completed Page Scanner observation. */
@Slf4j
public final class PageScanSnapshotStore {

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SNAPSHOT_SUBFOLDER = "Scanned";

    private PageScanSnapshotStore() {}

    static Snapshot persist(
            Connection connection,
            int homeBankingId,
            int botJobId,
            Integer homeUrlId,
            String botJobName,
            ScannedPageIdentity page,
            List<ElementDTO> elements,
            String diagnosticPath,
            ArtifactWriter artifactWriter)
            throws Exception {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(artifactWriter, "artifactWriter");
        String scanId = UUID.randomUUID().toString();
        String capturedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        ElementDTO[] values = elements == null ? new ElementDTO[0] : elements.toArray(new ElementDTO[0]);
        String redactedActualUrl = PageScanUrlRedactor.redact(page.actualUrl());
        String redactedNormalizedUrl = PageScanUrlRedactor.redact(page.normalizedUrl());
        boolean fingerprintColumn = hasViewFingerprintColumn(connection);
        boolean stagedRecorded = false;
        Path staging = null;
        Path target = null;
        try {
            insertStaged(
                    connection,
                    scanId,
                    homeBankingId,
                    botJobId,
                    homeUrlId,
                    page.pageKey(),
                    redactedActualUrl,
                    capturedAt,
                    values.length,
                    fingerprintColumn);
            stagedRecorded = true;

            Path root = safeRoot(diagnosticPath);
            String safeOwner = "org-" + positive(homeBankingId) + "/bot-job-" + positive(botJobId);
            String finalName = capturedAt.replace(':', '-') + "-" + scanId;
            Path ownerRoot = root.resolve(safeOwner).resolve(safePageName(page.pageKey()));
            staging = ownerRoot.resolve("." + finalName + ".staging");
            target = ownerRoot.resolve(finalName);
            Files.createDirectories(ownerRoot);
            Files.createDirectory(staging);

            CaptureMetadata capture = Objects.requireNonNullElseGet(
                    artifactWriter.write(staging), CaptureMetadata::unavailable);
            writeLimited(staging.resolve("elements.json"), JSON.toJson(values));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("scanId", scanId);
            metadata.put("homeBankingId", homeBankingId);
            metadata.put("botJobId", botJobId);
            metadata.put("botJobName", value(botJobName));
            metadata.put("homeUrlId", homeUrlId);
            metadata.put("pageKey", page.pageKey());
            metadata.put("pageUrl", redactedActualUrl);
            metadata.put("normalizedUrl", redactedNormalizedUrl);
            metadata.put("capturedAt", capturedAt);
            metadata.put("elementCount", values.length);
            metadata.put("capture", capture.asMap());
            writeLimited(staging.resolve("meta.json"), JSON.toJson(metadata));

            PageScanArtifactPolicy.validatePayload(staging);
            Map<String, String> checksums = checksums(staging);
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("format", "page-scan-snapshot-v1");
            manifest.put("scanId", scanId);
            manifest.put("capturedAt", capturedAt);
            manifest.put("owner", Map.of("homeBankingId", homeBankingId, "botJobId", botJobId));
            manifest.put("page", Map.of("pageKey", page.pageKey(), "url", redactedActualUrl));
            manifest.put("capture", capture.asMap());
            manifest.put("elementCount", values.length);
            manifest.put("files", checksums);
            byte[] manifestBytes = writeLimited(
                    staging.resolve("manifest.json"), JSON.toJson(manifest));
            String manifestHash = sha256(manifestBytes);
            moveIntoPlace(staging, target);
            String relative = root.relativize(target).toString().replace('\\', '/');
            markReady(
                    connection,
                    scanId,
                    homeBankingId,
                    botJobId,
                    values.length,
                    relative,
                    manifestHash,
                    capture.viewFingerprint(),
                    fingerprintColumn);
            return new Snapshot(scanId, relative, values.length, manifestHash, "READY");
        } catch (Exception failure) {
            cleanup(staging, failure);
            cleanup(target, failure);
            log.error("Page scan snapshot failed for botJobId={} pageKey={}: {}",
                    botJobId, page.pageKey(), failure.getMessage(), failure);
            if (stagedRecorded) {
                try {
                    markFailed(connection, scanId, homeBankingId, botJobId);
                } catch (SQLException recordFailure) {
                    failure.addSuppressed(recordFailure);
                    log.error("Could not record failed page scan snapshot {}: {}", scanId, recordFailure.getMessage());
                }
            }
            throw failure;
        }
    }

    private static Path safeRoot(String diagnosticPath) {
        if (diagnosticPath == null || diagnosticPath.isBlank()) {
            throw new IllegalArgumentException("Page Scanner diagnostic path is required");
        }
        Path root = Path.of(diagnosticPath).toAbsolutePath().normalize()
                .resolve(PageDiagnosticDumper.SUBFOLDER).resolve(SNAPSHOT_SUBFOLDER).normalize();
        return root;
    }

    private static String safePageName(String pageKey) {
        String value = pageKey == null ? "" : pageKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (value.isBlank() || value.length() > 100) throw new IllegalArgumentException("Invalid page identity");
        return value;
    }

    private static int positive(int value) {
        return Math.max(0, value);
    }

    private static Map<String, String> checksums(Path folder) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (var files = Files.walk(folder)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                try {
                    result.put(folder.relativize(path).toString().replace('\\', '/'), sha256(Files.readAllBytes(path)));
                } catch (IOException error) {
                    throw new SnapshotIOException(error);
                }
            });
        } catch (SnapshotIOException wrapped) {
            throw wrapped.cause;
        }
        return result;
    }

    private static byte[] writeLimited(Path path, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        PageScanArtifactPolicy.requireWritableSize(path.getFileName().toString(), bytes.length);
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return bytes;
    }

    private static void moveIntoPlace(Path staging, Path target) throws IOException {
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(staging, target);
        }
    }

    private static void insertStaged(
            Connection connection,
            String scanId,
            int homeBankingId,
            int botJobId,
            Integer homeUrlId,
            String pageKey,
            String pageUrl,
            String capturedAt,
            int elementCount,
            boolean fingerprintColumn)
            throws SQLException {
        String sql = fingerprintColumn
                ? "INSERT INTO page_scan_snapshot "
                        + "(scan_id, home_banking_id, bot_job_id, home_url_id, page_key, page_url, captured_at, "
                        + "element_count, artifact_path, manifest_sha256, status, view_fingerprint) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                : "INSERT INTO page_scan_snapshot "
                        + "(scan_id, home_banking_id, bot_job_id, home_url_id, page_key, page_url, captured_at, "
                        + "element_count, artifact_path, manifest_sha256, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scanId);
            statement.setInt(2, homeBankingId);
            statement.setInt(3, botJobId);
            if (homeUrlId == null) statement.setNull(4, java.sql.Types.INTEGER); else statement.setInt(4, homeUrlId);
            statement.setString(5, pageKey);
            statement.setString(6, pageUrl);
            statement.setString(7, capturedAt);
            statement.setInt(8, elementCount);
            statement.setString(9, "");
            statement.setString(10, "");
            statement.setString(11, "STAGED");
            if (fingerprintColumn) statement.setString(12, "");
            requireOne(statement.executeUpdate(), "create STAGED page scan snapshot");
        }
    }

    private static void markReady(
            Connection connection,
            String scanId,
            int homeBankingId,
            int botJobId,
            int elementCount,
            String artifactPath,
            String manifestHash,
            String viewFingerprint,
            boolean fingerprintColumn)
            throws SQLException {
        String sql = fingerprintColumn
                ? "UPDATE page_scan_snapshot SET element_count = ?, artifact_path = ?, "
                        + "manifest_sha256 = ?, view_fingerprint = ?, status = 'READY' "
                        + "WHERE scan_id = ? AND home_banking_id = ? AND bot_job_id = ? AND status = 'STAGED'"
                : "UPDATE page_scan_snapshot SET element_count = ?, artifact_path = ?, "
                        + "manifest_sha256 = ?, status = 'READY' "
                        + "WHERE scan_id = ? AND home_banking_id = ? AND bot_job_id = ? AND status = 'STAGED'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, elementCount);
            statement.setString(2, artifactPath);
            statement.setString(3, manifestHash);
            int ownerOffset = 4;
            if (fingerprintColumn) {
                statement.setString(4, value(viewFingerprint));
                ownerOffset = 5;
            }
            statement.setString(ownerOffset, scanId);
            statement.setInt(ownerOffset + 1, homeBankingId);
            statement.setInt(ownerOffset + 2, botJobId);
            requireOne(statement.executeUpdate(), "finalize READY page scan snapshot");
        }
    }

    static boolean hasViewFingerprintColumn(Connection connection) throws SQLException {
        try (java.sql.ResultSet columns =
                connection.getMetaData().getColumns(null, null, null, null)) {
            while (columns.next()) {
                if ("page_scan_snapshot".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && "view_fingerprint".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void markFailed(
            Connection connection, String scanId, int homeBankingId, int botJobId)
            throws SQLException {
        String sql = "UPDATE page_scan_snapshot SET artifact_path = '', manifest_sha256 = '', status = 'FAILED' "
                + "WHERE scan_id = ? AND home_banking_id = ? AND bot_job_id = ? AND status = 'STAGED'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scanId);
            statement.setInt(2, homeBankingId);
            statement.setInt(3, botJobId);
            requireOne(statement.executeUpdate(), "record FAILED page scan snapshot");
        }
    }

    private static void requireOne(int affected, String action) throws SQLException {
        if (affected != 1) {
            throw new SQLException("Could not " + action + "; affected rows=" + affected);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is required for page scan snapshots", unavailable);
        }
    }

    private static String value(String input) { return input == null ? "" : input; }

    private static void cleanup(Path root, Exception originalFailure) {
        if (root == null || !Files.exists(root)) return;
        try {
            deleteTree(root);
        } catch (IOException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            log.error("Could not remove failed page scan artifact {}: {}", root, cleanupFailure.getMessage());
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var files = Files.walk(root)) {
            List<Path> paths = files.sorted((a, b) -> b.compareTo(a)).toList();
            for (Path path : paths) Files.deleteIfExists(path);
        }
    }

    public record Snapshot(String scanId, String artifactPath, int elementCount, String manifestSha256, String status) {}

    @FunctionalInterface
    interface ArtifactWriter {
        CaptureMetadata write(Path staging) throws Exception;
    }

    record CaptureMetadata(
            String screenshotScope,
            double devicePixelRatio,
            double cssWidth,
            double cssHeight,
            int pixelWidth,
            int pixelHeight,
            double scrollX,
            double scrollY,
            String viewFingerprint,
            int fingerprintNodeCount) {

        CaptureMetadata(
                String screenshotScope,
                double devicePixelRatio,
                double cssWidth,
                double cssHeight,
                int pixelWidth,
                int pixelHeight,
                double scrollX,
                double scrollY) {
            this(
                    screenshotScope,
                    devicePixelRatio,
                    cssWidth,
                    cssHeight,
                    pixelWidth,
                    pixelHeight,
                    scrollX,
                    scrollY,
                    "",
                    0);
        }

        static CaptureMetadata unavailable() {
            return new CaptureMetadata(
                    "unavailable", 1.0d, 0.0d, 0.0d, 0, 0, 0.0d, 0.0d, "", 0);
        }

        Map<String, Object> asMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("screenshotScope", value(screenshotScope));
            values.put("devicePixelRatio", devicePixelRatio);
            values.put("cssWidth", cssWidth);
            values.put("cssHeight", cssHeight);
            values.put("pixelWidth", pixelWidth);
            values.put("pixelHeight", pixelHeight);
            values.put("scrollX", scrollX);
            values.put("scrollY", scrollY);
            values.put("viewFingerprint", value(viewFingerprint));
            values.put("fingerprintNodeCount", Math.max(0, fingerprintNodeCount));
            return values;
        }
    }

    private static final class SnapshotIOException extends RuntimeException {
        private final IOException cause;
        private SnapshotIOException(IOException cause) { super(cause); this.cause = cause; }
    }
}
