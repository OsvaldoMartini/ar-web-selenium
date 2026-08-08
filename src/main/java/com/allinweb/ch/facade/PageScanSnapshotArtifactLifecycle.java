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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Coordinates Bot Job deletion with the immutable Page Scanner artifact tree.
 *
 * <p>The database cannot participate in a filesystem transaction. This lifecycle first moves only
 * exact {@code org-N/bot-job-N} roots into a same-volume pending folder. A database rollback moves
 * them back; a commit removes the pending copy. A journal and {@link #reconcile(Connection)} make a
 * process crash recoverable by treating the {@code bot_job} table as the final authority.
 */
@Slf4j
final class PageScanSnapshotArtifactLifecycle {

    private static final String PENDING_FOLDER = ".delete-pending";
    private static final String JOURNAL = "manifest.tsv";
    private static final String JOURNAL_VERSION = "page-scan-delete-v1";

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
        return stageCandidates(discover(botJobIds));
    }

    Plan stageAll() throws IOException {
        return stageCandidates(discoverAll());
    }

    private Plan stageCandidates(List<Entry> candidates) throws IOException {
        if (candidates.isEmpty()) return Plan.none();
        for (Entry candidate : candidates) validateEntry(candidate);

        Path pendingRoot = safePendingRoot(true);
        Path batch = pendingRoot.resolve(UUID.randomUUID().toString()).normalize();
        requireWithin(pendingRoot, batch);
        Files.createDirectory(batch);
        PageScanSnapshotFileSecurity.secureDirectory(batch);
        Plan plan = new Plan(batch, List.copyOf(candidates));
        try {
            writeJournal(plan);
            for (Entry entry : plan.entries()) {
                Path source = resolveArtifact(entry.originalRelative());
                if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) continue;
                verifyMovableDirectory(source);
                PageScanSnapshotFileSecurity.requirePrivateDirectoryTree(snapshotRoot, source);
                PageScanSnapshotFileSecurity.secureDirectory(source);
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
                PageScanSnapshotFileSecurity.secureDirectory(staged);
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

    /** Resolves any crash-left pending deletion using current Bot Job existence as authority. */
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
                Map<Integer, Boolean> exists = botJobExistence(connection, plan.entries());
                reconcile(plan, exists);
            }
        }
    }

    private void reconcile(Plan plan, Map<Integer, Boolean> exists) throws IOException {
        IOException failure = null;
        for (Entry entry : plan.entries()) {
            Path staged = resolvePending(plan.batch(), entry.pendingRelative());
            if (!Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) continue;
            try {
                if (Boolean.TRUE.equals(exists.get(entry.botJobId()))) {
                    Path original = resolveArtifact(entry.originalRelative());
                    if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Refusing to overwrite a recreated Page Scanner artifact root: "
                                + original);
                    }
                    PageScanSnapshotFileSecurity.secureDirectory(staged);
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
                    entries.add(new Entry(botJobId, portable(relative), portable(relative)));
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
                        entries.add(new Entry(botJobId, portable(relative), portable(relative)));
                    }
                }
            }
        }
        return entries;
    }

    private void writeJournal(Plan plan) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(JOURNAL_VERSION);
        for (Entry entry : plan.entries()) {
            lines.add(entry.botJobId() + "\t" + entry.originalRelative() + "\t"
                    + entry.pendingRelative());
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
            throw new IOException("Unsupported or missing Page Scanner deletion journal version");
        }
        List<Entry> entries = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] parts = lines.get(index).split("\\t", -1);
            if (parts.length != 3) throw new IOException("Invalid deletion journal row " + index);
            int botJobId;
            try {
                botJobId = Integer.parseInt(parts[0]);
            } catch (NumberFormatException invalidId) {
                throw new IOException("Invalid Bot Job ID in deletion journal row " + index, invalidId);
            }
            if (botJobId <= 0) throw new IOException("Invalid Bot Job ID in deletion journal row " + index);
            Entry entry = new Entry(botJobId, parts[1], parts[2]);
            validateEntry(entry);
            resolveArtifact(entry.originalRelative());
            resolvePending(batch, entry.pendingRelative());
            entries.add(entry);
        }
        return new Plan(batch.toAbsolutePath().normalize(), List.copyOf(entries));
    }

    private Map<Integer, Boolean> botJobExistence(Connection connection, List<Entry> entries)
            throws SQLException {
        Map<Integer, Boolean> result = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT id FROM bot_job WHERE id=?")) {
            for (Entry entry : entries) {
                if (result.containsKey(entry.botJobId())) continue;
                select.setInt(1, entry.botJobId());
                try (ResultSet rows = select.executeQuery()) {
                    result.put(entry.botJobId(), rows.next());
                }
            }
        }
        return result;
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
                || !entry.originalRelative().matches("org-[0-9]+/bot-job-[1-9][0-9]*")
                || !entry.originalRelative().endsWith("/" + expectedSuffix)
                || !entry.originalRelative().equals(entry.pendingRelative())) {
            throw new IOException("Unsafe Page Scanner deletion journal entry for Bot Job "
                    + entry.botJobId());
        }
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

    record Plan(Path batch, List<Entry> entries) {
        static Plan none() { return new Plan(null, List.of()); }
        boolean empty() { return batch == null || entries == null || entries.isEmpty(); }
    }

    private record Entry(int botJobId, String originalRelative, String pendingRelative) {}
}
