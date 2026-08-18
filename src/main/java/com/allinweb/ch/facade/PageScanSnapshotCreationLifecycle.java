package com.allinweb.ch.facade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Reconciles crash-left STAGED snapshot rows and their non-authoritative files. */
final class PageScanSnapshotCreationLifecycle {

    private static final int MAX_STAGED_ROWS = 10_000;

    private PageScanSnapshotCreationLifecycle() {}

    static void reconcile(Connection connection) throws IOException, SQLException {
        if (connection == null || !tableExists(connection, "page_scan_snapshot")) return;
        if (!connection.getAutoCommit()) {
            throw new SQLException(
                    "Page Mapping creation recovery requires an independent database connection");
        }
        Path root = PageScanSnapshotStorageHealth.configuredSnapshotRoot();
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            PageScanSnapshotFileSecurity.secureExistingRoot(root);
        }
        for (StagedRow row : loadStaged(connection)) {
            Path owner = root.resolve("org-" + positive(row.homeBankingId()))
                    .resolve("bot-job-" + positive(row.botJobId()))
                    .resolve(safePageName(row.pageKey()))
                    .normalize();
            requireWithin(root, owner);
            String finalName = safeCapturedAt(row.capturedAt()) + "-" + safeScanId(row.scanId());
            deleteControlled(root, owner.resolve("." + finalName + ".staging").normalize());
            deleteControlled(root, owner.resolve(finalName).normalize());
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE page_scan_snapshot SET status='FAILED', artifact_path='', "
                            + "manifest_sha256='' WHERE scan_id=? AND home_banking_id=? "
                            + "AND bot_job_id=? AND status='STAGED'")) {
                update.setString(1, row.scanId());
                update.setInt(2, row.homeBankingId());
                update.setInt(3, row.botJobId());
                int changed = update.executeUpdate();
                if (changed != 1) {
                    throw new SQLException(
                            "A Page Mapping STAGED capture changed during startup recovery");
                }
            }
        }
    }

    private static List<StagedRow> loadStaged(Connection connection) throws SQLException {
        List<StagedRow> rows = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT scan_id,home_banking_id,bot_job_id,page_key,captured_at "
                        + "FROM page_scan_snapshot WHERE status='STAGED' ORDER BY captured_at")) {
            select.setMaxRows(MAX_STAGED_ROWS + 1);
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    if (rows.size() >= MAX_STAGED_ROWS) {
                        throw new SQLException(
                                "Page Mapping STAGED recovery exceeds its safe processing limit");
                    }
                    rows.add(new StagedRow(
                            result.getString("scan_id"),
                            result.getInt("home_banking_id"),
                            result.getInt("bot_job_id"),
                            result.getString("page_key"),
                            result.getString("captured_at")));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static void deleteControlled(Path root, Path candidate) throws IOException {
        requireWithin(root, candidate);
        if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) return;
        BasicFileAttributes attributes = Files.readAttributes(
                candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("A crash-left Page Mapping capture path is unsafe");
        }
        PageScanSnapshotFileSecurity.secureDirectoryTree(root, candidate);
        try (var paths = Files.walk(candidate)) {
            for (Path path : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static boolean tableExists(Connection connection, String tableName)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(null, null, null, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }

    private static String safeScanId(String value) throws IOException {
        if (value == null || !value.matches("[A-Za-z0-9-]{1,80}")) {
            throw new IOException("A STAGED Page Mapping scan ID is invalid");
        }
        return value;
    }

    private static String safePageName(String value) throws IOException {
        String safe = value == null ? "" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.isBlank() || safe.length() > 100) {
            throw new IOException("A STAGED Page Mapping page identity is invalid");
        }
        return safe;
    }

    private static String safeCapturedAt(String value) throws IOException {
        try {
            Instant.parse(value);
        } catch (RuntimeException invalid) {
            throw new IOException("A STAGED Page Mapping timestamp is invalid", invalid);
        }
        return value.replace(':', '-');
    }

    private static int positive(int value) throws IOException {
        if (value <= 0) throw new IOException("A STAGED Page Mapping owner is invalid");
        return value;
    }

    private static void requireWithin(Path root, Path candidate) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (normalizedCandidate.equals(normalizedRoot)
                || !normalizedCandidate.startsWith(normalizedRoot)) {
            throw new IOException("A STAGED Page Mapping path escaped snapshot storage");
        }
    }

    private record StagedRow(
            String scanId,
            int homeBankingId,
            int botJobId,
            String pageKey,
            String capturedAt) {}
}
