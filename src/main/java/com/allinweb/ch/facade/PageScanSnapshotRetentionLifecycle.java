package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.PageDiagnosticDumper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/** Crash-recoverable filesystem half of owner-scoped snapshot retention. */
@Slf4j
final class PageScanSnapshotRetentionLifecycle {

    private static final String PENDING_FOLDER = ".retention-pending";
    private static final String JOURNAL = "manifest.tsv";
    private static final String JOURNAL_VERSION = "page-scan-retention-v2";

    private final Path snapshotRoot;

    static PageScanSnapshotRetentionLifecycle configured() {
        String pathDb = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_DB);
        Path root = pathDb == null || pathDb.isBlank()
                ? null
                : Path.of(pathDb)
                        .resolve(PageDiagnosticDumper.SUBFOLDER)
                        .resolve("Scanned");
        return new PageScanSnapshotRetentionLifecycle(root);
    }

    PageScanSnapshotRetentionLifecycle(Path snapshotRoot) {
        this.snapshotRoot = snapshotRoot == null ? null : snapshotRoot.toAbsolutePath().normalize();
    }

    Plan stage(List<PageScanSnapshotRetentionService.SnapshotRow> candidates) throws IOException {
        if (candidates == null || candidates.isEmpty()) return Plan.none();
        List<Entry> entries = new ArrayList<>(candidates.size());
        for (PageScanSnapshotRetentionService.SnapshotRow candidate : candidates) {
            entries.add(entry(candidate));
        }
        Path pendingRoot = safePendingRoot(true);
        Path batch = pendingRoot.resolve(UUID.randomUUID().toString()).normalize();
        requireWithin(pendingRoot, batch);
        Plan plan = new Plan(batch, List.copyOf(entries));
        try {
            Files.createDirectory(batch);
            PageScanSnapshotFileSecurity.secureDirectory(batch);
            writeJournal(plan);
            for (Entry entry : plan.entries()) {
                Path source = resolveOriginal(entry);
                requireMovableDirectory(source);
                PageScanSnapshotFileSecurity.requirePrivateDirectoryTree(snapshotRoot, source);
                PageScanSnapshotFileSecurity.secureCaptureDirectory(source);
                Path destination = resolvePending(plan.batch(), entry.scanId());
                move(source, destination);
                PageScanSnapshotFileSecurity.requirePrivateDirectory(destination);
            }
            return plan;
        } catch (IOException failure) {
            try {
                restore(plan);
            } catch (IOException restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            throw failure;
        }
    }

    void purge(Plan plan) throws IOException {
        if (plan == null || plan.empty()) return;
        verifyPendingBatch(plan.batch());
        deleteTree(plan.batch());
    }

    void restore(Plan plan) throws IOException {
        if (plan == null || plan.empty()) return;
        verifyPendingBatch(plan.batch());
        IOException failure = null;
        List<Entry> entries = plan.entries();
        for (int index = entries.size() - 1; index >= 0; index--) {
            Entry entry = entries.get(index);
            Path staged = resolvePending(plan.batch(), entry.scanId());
            if (!Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) continue;
            Path original = resolveOriginal(entry);
            if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
                IOException collision = new IOException(
                        "Refusing to overwrite a recreated Page Mapping capture");
                if (failure == null) failure = collision;
                else failure.addSuppressed(collision);
                continue;
            }
            try {
                PageScanSnapshotFileSecurity.secureCaptureDirectory(staged);
                PageScanSnapshotFileSecurity.createPrivateDirectories(
                        snapshotRoot, original.getParent());
                move(staged, original);
                PageScanSnapshotFileSecurity.requirePrivateDirectory(original);
            } catch (IOException restoreFailure) {
                if (failure == null) failure = restoreFailure;
                else failure.addSuppressed(restoreFailure);
            }
        }
        if (failure != null) throw failure;
        deleteTree(plan.batch());
    }

    /** Restores staged captures whose DB rows remain and deletes only already-committed purges. */
    void reconcile(Connection connection) throws IOException, SQLException {
        if (snapshotRoot == null) {
            return;
        }
        PageScanSnapshotFileSecurity.secureExistingRoot(snapshotRoot);
        if (!Files.isDirectory(snapshotRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path pendingRoot = safePendingRoot(false);
        if (pendingRoot == null) return;
        try (DirectoryStream<Path> batches = Files.newDirectoryStream(pendingRoot)) {
            for (Path batch : batches) {
                if (!Files.isDirectory(batch, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(batch)) continue;
                Plan plan;
                try {
                    plan = readJournal(batch);
                } catch (IOException invalid) {
                    log.error("Leaving invalid Page Mapping retention journal {}: {}",
                            batch, invalid.getMessage());
                    continue;
                }
                reconcile(connection, plan);
            }
        }
    }

    private void reconcile(Connection connection, Plan plan) throws IOException, SQLException {
        IOException failure = null;
        for (Entry entry : plan.entries()) {
            Path staged = resolvePending(plan.batch(), entry.scanId());
            if (!Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) continue;
            try {
                if (rowExists(connection, entry)) {
                    Path original = resolveOriginal(entry);
                    if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Refusing to overwrite a recreated Page Mapping capture");
                    }
                    PageScanSnapshotFileSecurity.secureCaptureDirectory(staged);
                    PageScanSnapshotFileSecurity.createPrivateDirectories(
                            snapshotRoot, original.getParent());
                    move(staged, original);
                    PageScanSnapshotFileSecurity.requirePrivateDirectory(original);
                } else {
                    deleteTree(staged);
                }
            } catch (IOException entryFailure) {
                if (failure == null) failure = entryFailure;
                else failure.addSuppressed(entryFailure);
            }
        }
        if (failure != null) throw failure;
        deleteTree(plan.batch());
    }

    private static boolean rowExists(Connection connection, Entry entry) throws SQLException {
        String sql = "SELECT scan_id FROM page_scan_snapshot"
                + " WHERE scan_id = ? AND home_banking_id = ? AND bot_job_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.scanId());
            statement.setInt(2, entry.homeBankingId());
            statement.setInt(3, entry.botJobId());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private Entry entry(PageScanSnapshotRetentionService.SnapshotRow candidate) throws IOException {
        if (candidate == null
                || candidate.scanId() == null
                || !candidate.scanId().matches("[A-Za-z0-9-]{1,80}")
                || candidate.homeBankingId() <= 0
                || candidate.botJobId() <= 0) {
            throw new IOException("Invalid Page Mapping retention identity");
        }
        String relative = candidate.artifactPath();
        Path parsed = portablePath(relative);
        String safePageKey = safePageName(candidate.pageKey());
        String expectedCaptureFolder = safeCapturedAt(candidate.capturedAt())
                + "-"
                + candidate.scanId();
        if (parsed.getNameCount() != 4
                || !("org-" + candidate.homeBankingId()).equals(parsed.getName(0).toString())
                || !("bot-job-" + candidate.botJobId()).equals(parsed.getName(1).toString())
                || !safePageKey.equals(parsed.getName(2).toString())
                || !expectedCaptureFolder.equals(parsed.getFileName().toString())) {
            throw new IOException("Page Mapping retention path has the wrong owner");
        }
        return new Entry(
                candidate.scanId(),
                candidate.homeBankingId(),
                candidate.botJobId(),
                safePageKey,
                candidate.capturedAt(),
                portable(parsed));
    }

    private void writeJournal(Plan plan) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(JOURNAL_VERSION);
        for (Entry entry : plan.entries()) {
            lines.add(entry.scanId() + "\t" + entry.homeBankingId() + "\t"
                    + entry.botJobId() + "\t" + entry.pageKey() + "\t"
                    + entry.capturedAt() + "\t" + entry.originalRelative());
        }
        Files.write(
                plan.batch().resolve(JOURNAL),
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        PageScanSnapshotFileSecurity.secureFile(plan.batch().resolve(JOURNAL));
    }

    private Plan readJournal(Path batch) throws IOException {
        verifyPendingBatch(batch);
        PageScanSnapshotFileSecurity.secureFile(batch.resolve(JOURNAL));
        List<String> lines = Files.readAllLines(batch.resolve(JOURNAL), StandardCharsets.UTF_8);
        if (lines.isEmpty() || !JOURNAL_VERSION.equals(lines.get(0))) {
            throw new IOException("Unsupported Page Mapping retention journal");
        }
        List<Entry> entries = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] parts = lines.get(index).split("\t", -1);
            if (parts.length != 6) throw new IOException("Invalid retention journal row");
            int homeBankingId;
            int botJobId;
            try {
                homeBankingId = Integer.parseInt(parts[1]);
                botJobId = Integer.parseInt(parts[2]);
            } catch (NumberFormatException invalid) {
                throw new IOException("Invalid retention journal owner", invalid);
            }
            Entry entry = entry(new PageScanSnapshotRetentionService.SnapshotRow(
                    parts[0], homeBankingId, botJobId, parts[3], parts[4], parts[5], false));
            // Reuse the authoritative path validator before trusting the journal.
            resolvePending(batch, entry.scanId());
            entries.add(entry);
        }
        return new Plan(batch.toAbsolutePath().normalize(), List.copyOf(entries));
    }

    private Path safePendingRoot(boolean create) throws IOException {
        if (snapshotRoot == null) throw new IOException("Page Mapping capture storage is unavailable");
        if (create && !Files.exists(snapshotRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Page Mapping capture storage is unavailable");
        }
        Path pending = snapshotRoot.resolve(PENDING_FOLDER).normalize();
        requireWithin(snapshotRoot, pending);
        if (create) {
            PageScanSnapshotFileSecurity.secureDirectory(snapshotRoot);
            PageScanSnapshotFileSecurity.createPrivateDirectories(snapshotRoot, pending);
        }
        if (!Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isDirectory(pending, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(pending)) {
            throw new IOException("Unsafe Page Mapping retention folder");
        }
        PageScanSnapshotFileSecurity.secureDirectory(pending);
        return pending.toAbsolutePath().normalize();
    }

    private Path resolveOriginal(Entry entry) throws IOException {
        Path path = snapshotRoot.resolve(portablePath(entry.originalRelative())).normalize();
        requireWithin(snapshotRoot, path);
        if (path.equals(snapshotRoot)
                || path.startsWith(snapshotRoot.resolve(PENDING_FOLDER))
                || path.startsWith(snapshotRoot.resolve(".delete-pending"))) {
            throw new IOException("Unsafe Page Mapping capture path");
        }
        return path;
    }

    private static Path resolvePending(Path batch, String scanId) throws IOException {
        if (scanId == null || !scanId.matches("[A-Za-z0-9-]{1,80}")) {
            throw new IOException("Invalid Page Mapping scan identity");
        }
        Path path = batch.resolve(scanId).normalize();
        requireWithin(batch, path);
        return path;
    }

    private void verifyPendingBatch(Path batch) throws IOException {
        Path pending = safePendingRoot(false);
        if (pending == null) throw new IOException("Missing Page Mapping retention folder");
        requireWithin(pending, batch.toAbsolutePath().normalize());
        if (batch.toAbsolutePath().normalize().equals(pending) || Files.isSymbolicLink(batch)) {
            throw new IOException("Unsafe Page Mapping retention batch");
        }
        PageScanSnapshotFileSecurity.secureDirectory(batch);
    }

    private static Path portablePath(String value) throws IOException {
        if (value == null || value.isBlank() || value.contains("\t") || value.contains("\0")
                || value.contains("\\")) {
            throw new IOException("Invalid Page Mapping artifact path");
        }
        Path path;
        try {
            path = Path.of(value.replace('/', java.io.File.separatorChar));
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid Page Mapping artifact path", invalid);
        }
        if (path.isAbsolute() || !path.normalize().equals(path)) {
            throw new IOException("Unsafe Page Mapping artifact path");
        }
        for (Path part : path) {
            if (".".equals(part.toString()) || "..".equals(part.toString())) {
                throw new IOException("Unsafe Page Mapping artifact path");
            }
        }
        return path;
    }

    private static String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String safePageName(String pageKey) throws IOException {
        String value = pageKey == null ? "" : pageKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (value.isBlank() || value.length() > 100) {
            throw new IOException("Invalid Page Mapping page identity");
        }
        return value;
    }

    private static String safeCapturedAt(String capturedAt) throws IOException {
        if (capturedAt == null || capturedAt.isBlank()) {
            throw new IOException("Invalid Page Mapping capture timestamp");
        }
        try {
            java.time.Instant.parse(capturedAt);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid Page Mapping capture timestamp", invalid);
        }
        return capturedAt.replace(':', '-');
    }

    private static void requireMovableDirectory(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Refusing to retain an unsafe Page Mapping artifact");
        }
    }

    private static void requireWithin(Path root, Path candidate) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new IOException("Page Mapping artifact path escapes storage");
        }
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, destination);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    record Plan(Path batch, List<Entry> entries) {
        static Plan none() { return new Plan(null, List.of()); }
        boolean empty() { return batch == null || entries == null || entries.isEmpty(); }
    }

    private record Entry(
            String scanId,
            int homeBankingId,
            int botJobId,
            String pageKey,
            String capturedAt,
            String originalRelative) {}
}
