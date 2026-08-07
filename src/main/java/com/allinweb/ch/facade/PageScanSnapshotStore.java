package com.allinweb.ch.facade;

import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.util.PageDiagnosticDumper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
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
import java.util.ArrayList;
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

    public static Snapshot persist(
            Connection connection,
            int homeBankingId,
            int botJobId,
            Integer homeUrlId,
            String botJobName,
            ScannedPageIdentity page,
            List<ElementDTO> elements,
            String diagnosticPath)
            throws Exception {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(page, "page");
        Path root = safeRoot(diagnosticPath);
        String scanId = UUID.randomUUID().toString();
        String capturedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String safeOwner = "org-" + positive(homeBankingId) + "/bot-job-" + positive(botJobId);
        String finalName = capturedAt.replace(':', '-') + "-" + scanId;
        Path ownerRoot = root.resolve(safeOwner).resolve(safePageName(page.pageKey()));
        Files.createDirectories(ownerRoot);
        Path staging = ownerRoot.resolve("." + finalName + ".staging");
        Path target = ownerRoot.resolve(finalName);
        Files.createDirectories(staging);
        try {
            ElementDTO[] values = elements == null ? new ElementDTO[0] : elements.toArray(new ElementDTO[0]);
            write(staging.resolve("elements.json"), JSON.toJson(values));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("scanId", scanId);
            metadata.put("homeBankingId", homeBankingId);
            metadata.put("botJobId", botJobId);
            metadata.put("botJobName", value(botJobName));
            metadata.put("homeUrlId", homeUrlId);
            metadata.put("pageKey", page.pageKey());
            metadata.put("pageUrl", page.actualUrl());
            metadata.put("normalizedUrl", page.normalizedUrl());
            metadata.put("capturedAt", capturedAt);
            metadata.put("elementCount", values.length);
            write(staging.resolve("meta.json"), JSON.toJson(metadata));
            copyLegacyArtifacts(root.getParent(), staging);

            Map<String, String> checksums = checksums(staging);
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("format", "page-scan-snapshot-v1");
            manifest.put("scanId", scanId);
            manifest.put("capturedAt", capturedAt);
            manifest.put("owner", Map.of("homeBankingId", homeBankingId, "botJobId", botJobId));
            manifest.put("page", Map.of("pageKey", page.pageKey(), "url", page.actualUrl()));
            manifest.put("elementCount", values.length);
            manifest.put("files", checksums);
            write(staging.resolve("manifest.json"), JSON.toJson(manifest));
            String manifestHash = sha256(Files.readAllBytes(staging.resolve("manifest.json")));
            moveIntoPlace(staging, target);
            String relative = root.relativize(target).toString().replace('\\', '/');
            insert(connection, scanId, homeBankingId, botJobId, homeUrlId, page, capturedAt,
                    values.length, relative, manifestHash, "READY");
            return new Snapshot(scanId, relative, values.length, manifestHash, "READY");
        } catch (Exception failure) {
            deleteTree(staging);
            log.error("Page scan snapshot failed for botJobId={} pageKey={}: {}",
                    botJobId, page.pageKey(), failure.getMessage(), failure);
            try {
                insert(connection, scanId, homeBankingId, botJobId, homeUrlId, page, capturedAt,
                        elements == null ? 0 : elements.size(), "", "", "FAILED");
            } catch (SQLException recordFailure) {
                log.error("Could not record failed page scan snapshot {}: {}", scanId, recordFailure.getMessage());
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

    private static void copyLegacyArtifacts(Path diagnosticsRoot, Path staging) throws IOException {
        if (diagnosticsRoot == null || !Files.isDirectory(diagnosticsRoot)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(diagnosticsRoot, "page-BJ*")) {
            for (Path source : files) {
                if (Files.isRegularFile(source)) {
                    Files.copy(source, staging.resolve(source.getFileName().toString()), StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
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

    private static void write(Path path, String value) throws IOException {
        Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void moveIntoPlace(Path staging, Path target) throws IOException {
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(staging, target);
        }
    }

    private static void insert(Connection connection, String scanId, int homeBankingId, int botJobId,
            Integer homeUrlId, ScannedPageIdentity page, String capturedAt, int elementCount,
            String artifactPath, String manifestHash, String status) throws SQLException {
        String sql = "INSERT INTO page_scan_snapshot "
                + "(scan_id, home_banking_id, bot_job_id, home_url_id, page_key, page_url, captured_at, "
                + "element_count, artifact_path, manifest_sha256, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scanId);
            statement.setInt(2, homeBankingId);
            statement.setInt(3, botJobId);
            if (homeUrlId == null) statement.setNull(4, java.sql.Types.INTEGER); else statement.setInt(4, homeUrlId);
            statement.setString(5, page.pageKey());
            statement.setString(6, page.actualUrl());
            statement.setString(7, capturedAt);
            statement.setInt(8, elementCount);
            statement.setString(9, artifactPath);
            statement.setString(10, manifestHash);
            statement.setString(11, status);
            statement.executeUpdate();
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

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var files = Files.walk(root)) {
            files.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    public record Snapshot(String scanId, String artifactPath, int elementCount, String manifestSha256, String status) {}

    private static final class SnapshotIOException extends RuntimeException {
        private final IOException cause;
        private SnapshotIOException(IOException cause) { super(cause); this.cause = cause; }
    }
}
