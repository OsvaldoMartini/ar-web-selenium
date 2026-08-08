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
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Coordinates Bot Job deletion with the immutable Page Scanner artifact tree.
 *
 * <p>The database cannot participate in a filesystem transaction. This lifecycle first moves only
 * exact {@code org-N/bot-job-N} roots into a same-volume pending folder. A database rollback moves
 * them back; a commit removes the pending copy. A journal and {@link #reconcile(Connection)} make a
 * process crash recoverable by treating the exact owner and READY snapshot generation as the final
 * authority. Version-one journals remain readable for compatibility, while connection-aware
 * callers write generation-bound version-two journals when the authority schema is available.
 */
@Slf4j
final class PageScanSnapshotArtifactLifecycle {

    private static final String PENDING_FOLDER = ".delete-pending";
    private static final String JOURNAL = "manifest.tsv";
    private static final String JOURNAL_VERSION_V1 = "page-scan-delete-v1";
    private static final String JOURNAL_VERSION_V2 = "page-scan-delete-v2";
    private static final int MAX_GENERATION_SNAPSHOTS = 100_000;

    private final Path snapshotRoot;

    static PageScanSnapshotArtifactLifecycle configured() {
        String pathDb = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_DB);
        if (pathDb == null || pathDb.isBlank()) {
            return new PageScanSnapshotArtifactLifecycle(null);
        }
        return new PageScanSnapshotArtifactLifecycle(Path.of(pathDb)
                .resolve(PageDiagnosticDumper.SUBFOLDER)
                .resolve("Scanned"));
    }

    PageScanSnapshotArtifactLifecycle(Path snapshotRoot) {
        this.snapshotRoot = snapshotRoot == null ? null : snapshotRoot.toAbsolutePath().normalize();
    }

    Plan stage(Collection<Integer> submittedBotJobIds) throws IOException {
        Set<Integer> botJobIds = positiveIds(submittedBotJobIds);
        return stageCandidates(discover(botJobIds), JOURNAL_VERSION_V1);
    }

    /**
     * Stages selected roots with exact database owner and READY-snapshot generation authority.
     *
     * <p>The caller owns the surrounding destructive transaction. Authority is captured before its
     * DELETE statements, so crash recovery can distinguish a rolled-back generation from a reused
     * numeric Bot Job ID.</p>
     */
    Plan stage(Connection connection, Collection<Integer> submittedBotJobIds)
            throws IOException, SQLException {
        Set<Integer> botJobIds = positiveIds(submittedBotJobIds);
        return generationBoundCandidates(connection, discover(botJobIds));
    }

    Plan stageAll() throws IOException {
        return stageCandidates(discoverAll(), JOURNAL_VERSION_V1);
    }

    /** Stages every strict artifact root with exact database generation authority. */
    Plan stageAll(Connection connection) throws IOException, SQLException {
        return generationBoundCandidates(connection, discoverAll());
    }

    private Plan generationBoundCandidates(Connection connection, List<Entry> candidates)
            throws IOException, SQLException {
        if (connection == null) {
            throw new SQLException("Page Scanner deletion authority requires a database connection");
        }
        if (!supportsGenerationAuthority(connection)) {
            // Compatibility for old schemas and tests. New production schema always writes v2.
            return stageCandidates(candidates, JOURNAL_VERSION_V1);
        }
        List<Entry> bound = new ArrayList<>(candidates.size());
        for (Entry candidate : candidates) {
            validateEntry(candidate);
            int homeBankingId = homeBankingId(candidate.originalRelative());
            boolean ownerPresent = botJobOwnedBy(
                    connection, candidate.botJobId(), homeBankingId);
            SnapshotAuthority authority = readySnapshotAuthority(
                    connection, candidate.botJobId(), homeBankingId);
            bound.add(candidate.withAuthority(homeBankingId, ownerPresent, authority));
        }
        return stageCandidates(List.copyOf(bound), JOURNAL_VERSION_V2);
    }

    private Plan stageCandidates(List<Entry> candidates, String journalVersion) throws IOException {
        PageScanSnapshotStorageHealth.requireHealthy();
        if (candidates.isEmpty()) return Plan.none();
        for (Entry candidate : candidates) validateEntry(candidate);

        Path pendingRoot = safePendingRoot(true);
        Path batch = pendingRoot.resolve(UUID.randomUUID().toString()).normalize();
        requireWithin(pendingRoot, batch);
        Files.createDirectory(batch);
        PageScanSnapshotFileSecurity.secureDirectory(batch);
        Plan plan = new Plan(batch, List.copyOf(candidates), journalVersion);
        try {
            writeJournal(plan);
            for (Entry entry : plan.entries()) {
                Path source = resolveArtifact(entry.originalRelative());
                if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) continue;
                verifyMovableDirectory(source);
                PageScanSnapshotFileSecurity.secureDirectoryTree(snapshotRoot, source);
                PageScanSnapshotFileSecurity.requirePrivateDirectoryTree(snapshotRoot, source);
                Path destination = resolvePending(batch, entry.pendingRelative());
                PageScanSnapshotFileSecurity.createPrivateDirectories(
                        snapshotRoot, destination.getParent());
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
            Path staged = resolvePending(plan.batch(), entry.pendingRelative());
            if (!Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) continue;
            Path original = resolveArtifact(entry.originalRelative());
            if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
                IOException collision = new IOException(
                        "Refusing to overwrite a recreated Page Scanner artifact root: " + original);
                if (failure == null) failure = collision;
                else failure.addSuppressed(collision);
                continue;
            }
            try {
                PageScanSnapshotFileSecurity.secureDirectoryTree(snapshotRoot, staged);
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

    /** Resolves crash-left deletions using the authority recorded by each journal version. */
    void reconcile(Connection connection) throws IOException, SQLException {
        PageScanSnapshotStorageHealth.requireHealthy();
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
                        || Files.isSymbolicLink(batch)) {
                    continue;
                }
                Plan plan;
                try {
                    plan = readJournal(batch);
                } catch (IOException invalidJournal) {
                    log.error("Leaving invalid Page Scanner deletion journal {}: {}",
                            batch, invalidJournal.getMessage());
                    continue;
                }
                List<Boolean> restoreDecisions = restoreDecisions(connection, plan);
                reconcile(plan, restoreDecisions);
            }
        }
    }

    private void reconcile(Plan plan, List<Boolean> restoreDecisions) throws IOException {
        IOException failure = null;
        for (int index = 0; index < plan.entries().size(); index++) {
            Entry entry = plan.entries().get(index);
            Path staged = resolvePending(plan.batch(), entry.pendingRelative());
            if (!Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) continue;
            try {
                if (Boolean.TRUE.equals(restoreDecisions.get(index))) {
                    Path original = resolveArtifact(entry.originalRelative());
                    if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Refusing to overwrite a recreated Page Scanner artifact root: "
                                + original);
                    }
                    PageScanSnapshotFileSecurity.secureDirectoryTree(snapshotRoot, staged);
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

    private List<Entry> discover(Set<Integer> botJobIds) throws IOException {
        if (snapshotRoot == null || botJobIds.isEmpty()
                || !Files.isDirectory(snapshotRoot, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        Path rootReal = snapshotRoot.toRealPath();
        List<Entry> entries = new ArrayList<>();
        try (DirectoryStream<Path> organizations = Files.newDirectoryStream(snapshotRoot, "org-*")) {
            for (Path organization : organizations) {
                String organizationName = organization.getFileName().toString();
                if (!organizationName.matches("org-[0-9]+")
                        || !Files.isDirectory(organization, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(organization)) {
                    continue;
                }
                Path organizationReal = organization.toRealPath();
                requireWithin(rootReal, organizationReal);
                for (Integer botJobId : botJobIds) {
                    Path candidate = organization.resolve("bot-job-" + botJobId).normalize();
                    if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) continue;
                    verifyMovableDirectory(candidate);
                    requireWithin(rootReal, candidate.toRealPath());
                    Path relative = snapshotRoot.relativize(candidate);
                    entries.add(Entry.legacy(botJobId, portable(relative), portable(relative)));
                }
            }
        }
        return entries;
    }

    private List<Entry> discoverAll() throws IOException {
        if (snapshotRoot == null
                || !Files.isDirectory(snapshotRoot, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        Path rootReal = snapshotRoot.toRealPath();
        List<Entry> entries = new ArrayList<>();
        try (DirectoryStream<Path> organizations = Files.newDirectoryStream(snapshotRoot, "org-*")) {
            for (Path organization : organizations) {
                String organizationName = organization.getFileName().toString();
                if (!organizationName.matches("org-[0-9]+")
                        || !Files.isDirectory(organization, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(organization)) {
                    continue;
                }
                requireWithin(rootReal, organization.toRealPath());
                try (DirectoryStream<Path> botJobs = Files.newDirectoryStream(organization, "bot-job-*")) {
                    for (Path candidate : botJobs) {
                        String name = candidate.getFileName().toString();
                        if (!name.matches("bot-job-[1-9][0-9]*")) continue;
                        int botJobId;
                        try {
                            botJobId = Integer.parseInt(name.substring("bot-job-".length()));
                        } catch (NumberFormatException outOfRange) {
                            continue;
                        }
                        verifyMovableDirectory(candidate);
                        requireWithin(rootReal, candidate.toRealPath());
                        Path relative = snapshotRoot.relativize(candidate);
                        entries.add(Entry.legacy(botJobId, portable(relative), portable(relative)));
                    }
                }
            }
        }
        return entries;
    }

    private void writeJournal(Plan plan) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(plan.journalVersion());
        for (Entry entry : plan.entries()) {
            if (JOURNAL_VERSION_V2.equals(plan.journalVersion())) {
                lines.add(entry.botJobId() + "\t" + entry.homeBankingId() + "\t"
                        + (entry.ownerPresent() ? "1" : "0") + "\t"
                        + entry.snapshotCount() + "\t" + entry.snapshotAuthoritySha256() + "\t"
                        + entry.originalRelative() + "\t" + entry.pendingRelative());
            } else if (JOURNAL_VERSION_V1.equals(plan.journalVersion())) {
                lines.add(entry.botJobId() + "\t" + entry.originalRelative() + "\t"
                        + entry.pendingRelative());
            } else {
                throw new IOException("Unsupported Page Scanner deletion journal version");
            }
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
        if (lines.isEmpty()
                || (!JOURNAL_VERSION_V1.equals(lines.get(0))
                        && !JOURNAL_VERSION_V2.equals(lines.get(0)))) {
            throw new IOException("Unsupported or missing Page Scanner deletion journal version");
        }
        String journalVersion = lines.get(0);
        List<Entry> entries = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] parts = lines.get(index).split("\\t", -1);
            int expectedParts = JOURNAL_VERSION_V2.equals(journalVersion) ? 7 : 3;
            if (parts.length != expectedParts) {
                throw new IOException("Invalid deletion journal row " + index);
            }
            int botJobId;
            try {
                botJobId = Integer.parseInt(parts[0]);
            } catch (NumberFormatException invalidId) {
                throw new IOException("Invalid Bot Job ID in deletion journal row " + index, invalidId);
            }
            if (botJobId <= 0) throw new IOException("Invalid Bot Job ID in deletion journal row " + index);
            Entry entry;
            if (JOURNAL_VERSION_V2.equals(journalVersion)) {
                try {
                    int homeBankingId = Integer.parseInt(parts[1]);
                    boolean ownerPresent;
                    if ("1".equals(parts[2])) ownerPresent = true;
                    else if ("0".equals(parts[2])) ownerPresent = false;
                    else throw new IllegalArgumentException("invalid owner flag");
                    long snapshotCount = Long.parseLong(parts[3]);
                    entry = new Entry(
                            botJobId,
                            parts[5],
                            parts[6],
                            homeBankingId,
                            ownerPresent,
                            snapshotCount,
                            parts[4]);
                } catch (IllegalArgumentException invalidAuthority) {
                    throw new IOException(
                            "Invalid generation authority in deletion journal row " + index,
                            invalidAuthority);
                }
            } else {
                entry = Entry.legacy(botJobId, parts[1], parts[2]);
            }
            validateEntry(entry);
            if (JOURNAL_VERSION_V2.equals(journalVersion)) validateV2Authority(entry);
            resolveArtifact(entry.originalRelative());
            resolvePending(batch, entry.pendingRelative());
            entries.add(entry);
        }
        return new Plan(batch.toAbsolutePath().normalize(), List.copyOf(entries), journalVersion);
    }

    private List<Boolean> restoreDecisions(Connection connection, Plan plan) throws SQLException {
        if (connection == null) {
            throw new SQLException("Page Scanner deletion recovery requires a database connection");
        }
        List<Boolean> decisions = new ArrayList<>(plan.entries().size());
        if (JOURNAL_VERSION_V1.equals(plan.journalVersion())) {
            try (PreparedStatement select =
                    connection.prepareStatement("SELECT id FROM bot_job WHERE id=?")) {
                for (Entry entry : plan.entries()) {
                    select.setInt(1, entry.botJobId());
                    try (ResultSet rows = select.executeQuery()) {
                        decisions.add(rows.next());
                    }
                }
            }
            return List.copyOf(decisions);
        }
        if (!JOURNAL_VERSION_V2.equals(plan.journalVersion())
                || !supportsGenerationAuthority(connection)) {
            throw new SQLException(
                    "Cannot verify Page Scanner deletion generation authority with this schema");
        }
        for (Entry entry : plan.entries()) {
            SnapshotAuthority current = readySnapshotAuthority(
                    connection, entry.botJobId(), entry.homeBankingId());
            decisions.add(entry.ownerPresent()
                    && entry.snapshotCount() > 0
                    && botJobOwnedBy(connection, entry.botJobId(), entry.homeBankingId())
                    && entry.snapshotCount() == current.count()
                    && entry.snapshotAuthoritySha256().equals(current.sha256()));
        }
        return List.copyOf(decisions);
    }

    private Path safePendingRoot(boolean create) throws IOException {
        if (snapshotRoot == null) return null;
        Path pending = snapshotRoot.resolve(PENDING_FOLDER).normalize();
        requireWithin(snapshotRoot, pending);
        if (create) {
            Files.createDirectories(snapshotRoot);
            PageScanSnapshotFileSecurity.secureDirectory(snapshotRoot);
            PageScanSnapshotFileSecurity.createPrivateDirectories(snapshotRoot, pending);
        }
        if (!Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isDirectory(pending, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(pending)) {
            throw new IOException("Unsafe Page Scanner pending deletion folder: " + pending);
        }
        PageScanSnapshotFileSecurity.secureDirectory(pending);
        return pending.toAbsolutePath().normalize();
    }

    private Path resolveArtifact(String relative) throws IOException {
        if (snapshotRoot == null) throw new IOException("Page Scanner artifact root is unavailable");
        Path path = snapshotRoot.resolve(portablePath(relative)).normalize();
        requireWithin(snapshotRoot, path);
        if (path.equals(snapshotRoot) || path.startsWith(snapshotRoot.resolve(PENDING_FOLDER))) {
            throw new IOException("Unsafe Page Scanner artifact path: " + relative);
        }
        return path;
    }

    private Path resolvePending(Path batch, String relative) throws IOException {
        Path path = batch.resolve(portablePath(relative)).normalize();
        requireWithin(batch, path);
        if (path.equals(batch)) throw new IOException("Unsafe empty pending artifact path");
        return path;
    }

    private void verifyPendingBatch(Path batch) throws IOException {
        Path pending = safePendingRoot(false);
        if (pending == null) throw new IOException("Missing Page Scanner pending deletion folder");
        requireWithin(pending, batch.toAbsolutePath().normalize());
        if (batch.toAbsolutePath().normalize().equals(pending) || Files.isSymbolicLink(batch)) {
            throw new IOException("Unsafe Page Scanner pending deletion batch: " + batch);
        }
        PageScanSnapshotFileSecurity.secureDirectory(batch);
    }

    private static void verifyMovableDirectory(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Refusing to move a non-directory or linked artifact root: " + path);
        }
    }

    private static void validateEntry(Entry entry) throws IOException {
        String expectedSuffix = "bot-job-" + entry.botJobId();
        if (entry.originalRelative() == null
                || !entry.originalRelative().matches("org-[1-9][0-9]*/bot-job-[1-9][0-9]*")
                || !entry.originalRelative().endsWith("/" + expectedSuffix)
                || !entry.originalRelative().equals(entry.pendingRelative())) {
            throw new IOException("Unsafe Page Scanner deletion journal entry for Bot Job "
                    + entry.botJobId());
        }
    }

    private static void validateV2Authority(Entry entry) throws IOException {
        if (entry.homeBankingId() <= 0
                || entry.homeBankingId() != homeBankingId(entry.originalRelative())
                || entry.snapshotCount() < 0
                || entry.snapshotAuthoritySha256() == null
                || !entry.snapshotAuthoritySha256().matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid Page Scanner deletion generation authority for Bot Job "
                    + entry.botJobId());
        }
    }

    private static int homeBankingId(String relative) throws IOException {
        int separator = relative == null ? -1 : relative.indexOf('/');
        if (separator <= "org-".length()) {
            throw new IOException("Invalid Page Scanner organization path");
        }
        try {
            int homeBankingId = Integer.parseInt(relative.substring("org-".length(), separator));
            if (homeBankingId <= 0) throw new NumberFormatException("non-positive owner");
            return homeBankingId;
        } catch (NumberFormatException invalidOwner) {
            throw new IOException("Invalid Page Scanner organization path", invalidOwner);
        }
    }

    private static boolean supportsGenerationAuthority(Connection connection) throws SQLException {
        return hasColumns(connection, "bot_job", "id", "home_banking_id")
                && hasColumns(
                        connection,
                        "page_scan_snapshot",
                        "scan_id",
                        "home_banking_id",
                        "bot_job_id",
                        "artifact_path",
                        "manifest_sha256",
                        "status");
    }

    private static boolean hasColumns(Connection connection, String table, String... required)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> found = new LinkedHashSet<>();
        try (ResultSet columns = metadata.getColumns(null, null, null, null)) {
            while (columns.next()) {
                if (table.equalsIgnoreCase(columns.getString("TABLE_NAME"))) {
                    found.add(columns.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        for (String column : required) {
            if (!found.contains(column.toLowerCase(java.util.Locale.ROOT))) return false;
        }
        return true;
    }

    private static boolean botJobOwnedBy(
            Connection connection, int botJobId, int homeBankingId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM bot_job WHERE id=? AND home_banking_id=?")) {
            select.setInt(1, botJobId);
            select.setInt(2, homeBankingId);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static SnapshotAuthority readySnapshotAuthority(
            Connection connection, int botJobId, int homeBankingId) throws SQLException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
        List<SnapshotAuthorityRow> snapshots = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT scan_id, artifact_path, manifest_sha256 FROM page_scan_snapshot "
                        + "WHERE bot_job_id=? AND home_banking_id=? AND status='READY'")) {
            select.setInt(1, botJobId);
            select.setInt(2, homeBankingId);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    if (snapshots.size() >= MAX_GENERATION_SNAPSHOTS) {
                        throw new SQLException(
                                "Page Scanner deletion generation exceeds its safe snapshot limit");
                    }
                    snapshots.add(new SnapshotAuthorityRow(
                            rows.getString("scan_id"),
                            rows.getString("artifact_path"),
                            rows.getString("manifest_sha256")));
                }
            }
        }
        Comparator<String> textOrder = Comparator.nullsFirst(Comparator.naturalOrder());
        snapshots.sort(Comparator.comparing(SnapshotAuthorityRow::scanId, textOrder)
                .thenComparing(SnapshotAuthorityRow::artifactPath, textOrder)
                .thenComparing(SnapshotAuthorityRow::manifestSha256, textOrder));
        for (SnapshotAuthorityRow snapshot : snapshots) {
            updateDigest(digest, snapshot.scanId());
            updateDigest(digest, snapshot.artifactPath());
            updateDigest(digest, snapshot.manifestSha256());
        }
        return new SnapshotAuthority(snapshots.size(), hex(digest.digest()));
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
        int length = bytes == null ? -1 : bytes.length;
        digest.update((byte) (length >>> 24));
        digest.update((byte) (length >>> 16));
        digest.update((byte) (length >>> 8));
        digest.update((byte) length);
        if (bytes != null) digest.update(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static void requireWithin(Path root, Path candidate) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new IOException("Path escapes the Page Scanner artifact root: " + candidate);
        }
    }

    private static Set<Integer> positiveIds(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        Set<Integer> result = new LinkedHashSet<>();
        for (Integer id : ids) if (id != null && id > 0) result.add(id);
        return result;
    }

    private static Path portablePath(String value) throws IOException {
        if (value == null || value.isBlank() || value.contains("\t") || value.contains("\0")
                || value.contains("\\")) {
            throw new IOException("Invalid Page Scanner artifact path");
        }
        Path path = Path.of(value.replace('/', java.io.File.separatorChar));
        if (path.isAbsolute()) throw new IOException("Absolute Page Scanner artifact path is forbidden");
        return path;
    }

    private static String portable(Path value) {
        return value.toString().replace('\\', '/');
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

    record Plan(Path batch, List<Entry> entries, String journalVersion) {
        static Plan none() { return new Plan(null, List.of(), JOURNAL_VERSION_V2); }
        boolean empty() { return batch == null || entries == null || entries.isEmpty(); }
    }

    private record Entry(
            int botJobId,
            String originalRelative,
            String pendingRelative,
            int homeBankingId,
            boolean ownerPresent,
            long snapshotCount,
            String snapshotAuthoritySha256) {

        static Entry legacy(int botJobId, String originalRelative, String pendingRelative) {
            return new Entry(botJobId, originalRelative, pendingRelative, -1, false, -1, null);
        }

        Entry withAuthority(
                int authoritativeHomeBankingId,
                boolean authoritativeOwnerPresent,
                SnapshotAuthority authority) {
            return new Entry(
                    botJobId,
                    originalRelative,
                    pendingRelative,
                    authoritativeHomeBankingId,
                    authoritativeOwnerPresent,
                    authority.count(),
                    authority.sha256());
        }
    }

    private record SnapshotAuthority(long count, String sha256) {}

    private record SnapshotAuthorityRow(
            String scanId, String artifactPath, String manifestSha256) {}
}
