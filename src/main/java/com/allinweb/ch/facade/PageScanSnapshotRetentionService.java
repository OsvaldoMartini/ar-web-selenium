package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/** Owner-scoped snapshot operations governed by one explicit system-wide retention policy. */
@Slf4j
public final class PageScanSnapshotRetentionService {

    public static final int MAX_RETENTION_DAYS = 3_650;
    public static final int MAX_CAPTURES_PER_PAGE = 1_000;
    public static final int MAX_PURGE_BATCH = 100;
    public static final int DEFAULT_RETENTION_DAYS = 30;
    private static final int MAX_READY_ROWS_PER_OWNER = 100_000;

    private static final PageScanSnapshotRetentionService INSTANCE =
            new PageScanSnapshotRetentionService(ARPropertyManager.getInstance());

    private final ARPropertyManager properties;

    public static PageScanSnapshotRetentionService getInstance() {
        return INSTANCE;
    }

    private PageScanSnapshotRetentionService(ARPropertyManager properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    public Policy configuredPolicy() {
        return new Policy(
                configuredInteger(
                        ARPropertyEnum.PAGE_SCAN_RETENTION_DAYS,
                        DEFAULT_RETENTION_DAYS,
                        0,
                        MAX_RETENTION_DAYS),
                configuredInteger(
                        ARPropertyEnum.PAGE_SCAN_RETENTION_MAX_UNPINNED_PER_PAGE,
                        0,
                        0,
                        MAX_CAPTURES_PER_PAGE));
    }

    public Summary savePolicy(
            Connection connection,
            int homeBankingId,
            int botJobId,
            int retentionDays,
            int maxUnpinnedPerPage)
            throws IOException, SQLException {
        PageScanSnapshotStorageHealth.requireHealthy();
        synchronized (PageScanSnapshotLifecycleLock.MONITOR) {
            Policy policy = new Policy(retentionDays, maxUnpinnedPerPage);
            Summary summary = evaluate(
                            loadReady(connection, homeBankingId, botJobId),
                            homeBankingId,
                            botJobId,
                            policy,
                            Instant.now())
                    .summary();
            Map<String, String> updates = new LinkedHashMap<>();
            updates.put(
                    ARPropertyEnum.PAGE_SCAN_RETENTION_DAYS.getValue(),
                    Integer.toString(policy.retentionDays()));
            updates.put(
                    ARPropertyEnum.PAGE_SCAN_RETENTION_MAX_UNPINNED_PER_PAGE.getValue(),
                    Integer.toString(policy.maxUnpinnedPerPage()));
            properties.setPropertiesChecked(updates);
            return summary;
        }
    }

    public Summary summary(
            Connection connection,
            int homeBankingId,
            int botJobId)
            throws SQLException {
        try {
            PageScanSnapshotStorageHealth.requireHealthy();
        } catch (IOException unavailable) {
            throw new SQLException(unavailable.getMessage(), unavailable);
        }
        synchronized (PageScanSnapshotLifecycleLock.MONITOR) {
            Evaluation evaluation = evaluate(
                    loadReady(connection, homeBankingId, botJobId),
                    homeBankingId,
                    botJobId,
                    configuredPolicy(),
                    Instant.now());
            return evaluation.summary();
        }
    }

    public PinResult pin(
            Connection connection,
            int homeBankingId,
            int botJobId,
            String scanId,
            boolean pinned)
            throws SQLException {
        try {
            PageScanSnapshotStorageHealth.requireHealthy();
        } catch (IOException unavailable) {
            throw new SQLException(unavailable.getMessage(), unavailable);
        }
        if (scanId == null || scanId.isBlank() || scanId.length() > 80) {
            throw new IllegalArgumentException("A valid Page Mapping scan ID is required.");
        }
        synchronized (PageScanSnapshotLifecycleLock.MONITOR) {
            boolean previousAutoCommit = connection.getAutoCommit();
            if (!previousAutoCommit) {
                throw new SQLException("Page Mapping pinning requires its own database transaction.");
            }
            int previousIsolation = connection.getTransactionIsolation();
            boolean commitAttempted = false;
            boolean committed = false;
            try {
                if (previousIsolation != Connection.TRANSACTION_SERIALIZABLE) {
                    connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                }
                connection.setAutoCommit(false);
                List<SnapshotRow> ready = loadReady(connection, homeBankingId, botJobId);
                SnapshotRow current = ready.stream()
                        .filter(row -> scanId.equals(row.scanId()))
                        .findFirst()
                        .orElseThrow(() -> new StaleSnapshotException(scanId));
                List<SnapshotRow> projected = ready.stream()
                        .map(row -> scanId.equals(row.scanId())
                                ? new SnapshotRow(
                                        row.scanId(),
                                        row.homeBankingId(),
                                        row.botJobId(),
                                        row.pageKey(),
                                        row.capturedAt(),
                                        row.artifactPath(),
                                        pinned)
                                : row)
                        .toList();
                Summary projectedSummary = evaluate(
                                projected,
                                homeBankingId,
                                botJobId,
                                configuredPolicy(),
                                Instant.now())
                        .summary();
                String update = "UPDATE page_scan_snapshot SET pinned = ?"
                        + " WHERE scan_id = ? AND home_banking_id = ? AND bot_job_id = ?"
                        + " AND status = 'READY' AND pinned = ?";
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    statement.setInt(1, pinned ? 1 : 0);
                    statement.setString(2, scanId);
                    statement.setInt(3, homeBankingId);
                    statement.setInt(4, botJobId);
                    statement.setInt(5, current.pinned() ? 1 : 0);
                    if (statement.executeUpdate() != 1) {
                        throw new StaleSnapshotException(scanId);
                    }
                }
                commitAttempted = true;
                connection.commit();
                committed = true;
                return new PinResult(scanId, pinned, projectedSummary);
            } catch (SQLException | RuntimeException failure) {
                if (!commitAttempted) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                } else if (!committed) {
                    log.error(
                            "Page Mapping pin commit outcome is unknown; reload capture history",
                            failure);
                    throw new PinOutcomeUnknownException(
                            "The capture pin outcome is unknown. Reload Page Mappings before changing it again.",
                            failure);
                }
                throw failure;
            } finally {
                if (!commitAttempted || committed) {
                    restoreConnectionState(connection, previousIsolation, previousAutoCommit);
                }
            }
        }
    }

    public PurgeResult purgeConfigured(
            Connection connection,
            int homeBankingId,
            int botJobId)
            throws Exception {
        return purgeConfiguredInternal(connection, homeBankingId, botJobId, null);
    }

    /**
     * Purges only when the configured policy still matches the policy presented to the caller.
     * A mismatch is fail-closed and requires the caller to reload before asking again.
     */
    public PurgeResult purgeConfigured(
            Connection connection,
            int homeBankingId,
            int botJobId,
            Policy expectedPolicy)
            throws Exception {
        return purgeConfiguredInternal(
                connection,
                homeBankingId,
                botJobId,
                Objects.requireNonNull(expectedPolicy, "Expected retention policy is required."));
    }

    /** Convenience overload for wire-contract callers that already validated both integers. */
    public PurgeResult purgeConfigured(
            Connection connection,
            int homeBankingId,
            int botJobId,
            int expectedRetentionDays,
            int expectedMaxUnpinnedPerPage)
            throws Exception {
        return purgeConfigured(
                connection,
                homeBankingId,
                botJobId,
                new Policy(expectedRetentionDays, expectedMaxUnpinnedPerPage));
    }

    private PurgeResult purgeConfiguredInternal(
            Connection connection,
            int homeBankingId,
            int botJobId,
            Policy expectedPolicy)
            throws Exception {
        PageScanSnapshotStorageHealth.requireHealthy();
        synchronized (PageScanSnapshotLifecycleLock.MONITOR) {
            Policy policy = configuredPolicy();
            requireExpectedPolicy(expectedPolicy, policy);
            Evaluation evaluation = evaluate(
                    loadReady(connection, homeBankingId, botJobId),
                    homeBankingId,
                    botJobId,
                    policy,
                    Instant.now());
            if (!policy.enabled() || evaluation.eligible().isEmpty()) {
                return new PurgeResult(
                        List.of(), false, evaluation.summary());
            }
            return purge(connection, evaluation);
        }
    }

    /** Reconciles only filesystem retention journals; it never creates or changes DB schema. */
    public void reconcile(Connection connection) throws IOException, SQLException {
        PageScanSnapshotStorageHealth.requireHealthy();
        synchronized (PageScanSnapshotLifecycleLock.MONITOR) {
            PageScanSnapshotRetentionLifecycle.configured().reconcile(connection);
        }
    }

    private PurgeResult purge(Connection connection, Evaluation evaluation) throws Exception {
        boolean previousAutoCommit = connection.getAutoCommit();
        if (!previousAutoCommit) {
            throw new SQLException(
                    "Page Mapping retention requires its own database transaction.");
        }
        int previousIsolation = connection.getTransactionIsolation();
        PageScanSnapshotRetentionLifecycle artifacts =
                PageScanSnapshotRetentionLifecycle.configured();
        PageScanSnapshotRetentionLifecycle.Plan plan =
                PageScanSnapshotRetentionLifecycle.Plan.none();
        boolean committed = false;
        boolean commitAttempted = false;
        try {
            if (previousIsolation != Connection.TRANSACTION_SERIALIZABLE) {
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            }
            connection.setAutoCommit(false);
            Policy transactionPolicy = configuredPolicy();
            requireExpectedPolicy(evaluation.summary().policy(), transactionPolicy);
            // Re-evaluate under the write transaction so a concurrent pin cannot be deleted.
            Evaluation current = evaluate(
                    loadReady(
                            connection,
                            evaluation.summary().homeBankingId(),
                            evaluation.summary().botJobId()),
                    evaluation.summary().homeBankingId(),
                    evaluation.summary().botJobId(),
                    transactionPolicy,
                    Instant.now());
            if (current.eligible().isEmpty()) {
                commitAttempted = true;
                connection.commit();
                committed = true;
                return new PurgeResult(List.of(), false, current.summary());
            }

            List<SnapshotRow> batch = current.eligible().subList(
                    0, Math.min(MAX_PURGE_BATCH, current.eligible().size()));
            plan = artifacts.stage(batch);
            String delete = "DELETE FROM page_scan_snapshot"
                    + " WHERE scan_id = ? AND home_banking_id = ? AND bot_job_id = ?"
                    + " AND pinned = 0 AND status = 'READY'";
            try (PreparedStatement statement = connection.prepareStatement(delete)) {
                for (SnapshotRow row : batch) {
                    statement.setString(1, row.scanId());
                    statement.setInt(2, row.homeBankingId());
                    statement.setInt(3, row.botJobId());
                    if (statement.executeUpdate() != 1) {
                        throw new StaleSnapshotException(row.scanId());
                    }
                }
            }
            commitAttempted = true;
            connection.commit();
            committed = true;

            boolean cleanupPending = false;
            try {
                artifacts.purge(plan);
            } catch (IOException cleanupFailure) {
                cleanupPending = true;
                log.error(
                        "Page Mapping retention committed but pending artifacts need startup cleanup",
                        cleanupFailure);
            }
            Summary summary = new Summary(
                    current.summary().homeBankingId(),
                    current.summary().botJobId(),
                    current.summary().policy(),
                    Math.max(0, current.summary().readyCount() - batch.size()),
                    current.summary().pinnedCount(),
                    Math.max(0, current.eligible().size() - batch.size()));
            return new PurgeResult(
                    batch.stream().map(SnapshotRow::scanId).toList(),
                    cleanupPending,
                    summary);
        } catch (Exception failure) {
            if (!commitAttempted) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                try {
                    artifacts.restore(plan);
                } catch (IOException restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            } else if (!committed) {
                // A driver can lose the connection after the server committed but before it
                // acknowledged commit(). Leave the journal staged: startup reconciliation checks
                // the authoritative rows and then restores or purges without guessing here.
                log.error(
                        "Page Mapping retention commit outcome is unknown; staged artifacts were left for reconciliation",
                        failure);
                throw new PurgeOutcomeUnknownException(
                        "The purge outcome is unknown. Reload Page Mappings before starting a new purge.",
                        failure);
            }
            throw failure;
        } finally {
            if (!commitAttempted || committed) {
                restoreConnectionState(connection, previousIsolation, previousAutoCommit);
            }
        }
    }

    private static void restoreConnectionState(
            Connection connection, int previousIsolation, boolean previousAutoCommit) {
        try {
            if (connection.getAutoCommit() != previousAutoCommit) {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException restorationFailure) {
            log.warn("Page Mapping retention could not restore auto-commit", restorationFailure);
        }
        try {
            if (connection.getTransactionIsolation() != previousIsolation) {
                connection.setTransactionIsolation(previousIsolation);
            }
        } catch (SQLException restorationFailure) {
            log.warn("Page Mapping retention could not restore transaction isolation", restorationFailure);
        }
    }

    private static void requireExpectedPolicy(Policy expected, Policy current)
            throws StaleRetentionPolicyException {
        if (expected != null && !expected.equals(current)) {
            throw new StaleRetentionPolicyException(expected, current);
        }
    }

    private static List<SnapshotRow> loadReady(
            Connection connection,
            int homeBankingId,
            int botJobId)
            throws SQLException {
        if (homeBankingId <= 0 || botJobId <= 0) {
            throw new IllegalArgumentException("A valid Page Mapping owner is required.");
        }
        String sql = "SELECT scan_id, page_key, captured_at, artifact_path, pinned"
                + " FROM page_scan_snapshot"
                + " WHERE home_banking_id = ? AND bot_job_id = ? AND status = 'READY'"
                + " ORDER BY captured_at DESC, scan_id DESC";
        List<SnapshotRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(MAX_READY_ROWS_PER_OWNER + 1);
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (rows.size() >= MAX_READY_ROWS_PER_OWNER) {
                        throw new SQLException(
                                "Page Mapping retention history exceeds its safe processing limit.");
                    }
                    rows.add(new SnapshotRow(
                            result.getString("scan_id"),
                            homeBankingId,
                            botJobId,
                            result.getString("page_key"),
                            result.getString("captured_at"),
                            result.getString("artifact_path"),
                            result.getInt("pinned") != 0));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static Evaluation evaluate(
            List<SnapshotRow> rows,
            int homeBankingId,
            int botJobId,
            Policy policy,
            Instant now) {
        Map<String, List<TimedRow>> pages = new LinkedHashMap<>();
        int pinnedCount = 0;
        for (SnapshotRow row : rows) {
            if (row.homeBankingId() != homeBankingId || row.botJobId() != botJobId) {
                throw new IllegalStateException("Page Mapping retention owner changed.");
            }
            if (row.scanId() == null || row.scanId().isBlank()
                    || row.pageKey() == null || row.pageKey().isBlank()
                    || row.artifactPath() == null || row.artifactPath().isBlank()) {
                throw new IllegalStateException(
                        "A READY Page Mapping capture has invalid retention metadata.");
            }
            final Instant capturedAt;
            try {
                capturedAt = Instant.parse(row.capturedAt());
            } catch (RuntimeException malformed) {
                throw new IllegalStateException(
                        "A READY Page Mapping capture has an invalid timestamp.", malformed);
            }
            if (row.pinned()) pinnedCount++;
            pages.computeIfAbsent(row.pageKey(), ignored -> new ArrayList<>())
                    .add(new TimedRow(row, capturedAt));
        }

        List<SnapshotRow> eligible = new ArrayList<>();
        if (policy.enabled()) {
            Instant cutoff = policy.retentionDays() > 0
                    ? now.minus(policy.retentionDays(), ChronoUnit.DAYS)
                    : null;
            Comparator<TimedRow> newestFirst = Comparator
                    .comparing(TimedRow::capturedAt)
                    .thenComparing(value -> value.row().scanId())
                    .reversed();
            for (List<TimedRow> pageRows : pages.values()) {
                List<TimedRow> unpinned = pageRows.stream()
                        .filter(value -> !value.row().pinned())
                        .sorted(newestFirst)
                        .toList();
                for (int index = 1; index < unpinned.size(); index++) {
                    TimedRow candidate = unpinned.get(index);
                    boolean expired = cutoff != null && candidate.capturedAt().isBefore(cutoff);
                    boolean overflow = policy.maxUnpinnedPerPage() > 0
                            && index >= policy.maxUnpinnedPerPage();
                    if (expired || overflow) eligible.add(candidate.row());
                }
            }
        }
        eligible.sort(Comparator
                .comparing((SnapshotRow row) -> Instant.parse(row.capturedAt()))
                .thenComparing(SnapshotRow::scanId));
        return new Evaluation(
                List.copyOf(eligible),
                new Summary(
                        homeBankingId,
                        botJobId,
                        policy,
                        rows.size(),
                        pinnedCount,
                        eligible.size()));
    }

    private int configuredInteger(
            ARPropertyEnum property, int fallback, int minimum, int maximum) {
        String raw = properties.getProperty(property);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= minimum && value <= maximum ? value : fallback;
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    public record Policy(int retentionDays, int maxUnpinnedPerPage) {
        public Policy {
            if (retentionDays < 0 || retentionDays > MAX_RETENTION_DAYS) {
                throw new IllegalArgumentException(
                        "Retention days must be between 0 and " + MAX_RETENTION_DAYS + ".");
            }
            if (maxUnpinnedPerPage < 0
                    || maxUnpinnedPerPage > MAX_CAPTURES_PER_PAGE) {
                throw new IllegalArgumentException(
                        "Captures per page must be between 0 and "
                                + MAX_CAPTURES_PER_PAGE + ".");
            }
        }

        public boolean enabled() {
            return retentionDays > 0 || maxUnpinnedPerPage > 0;
        }
    }

    public record Summary(
            int homeBankingId,
            int botJobId,
            Policy policy,
            int readyCount,
            int pinnedCount,
            int eligibleCount) {}

    public record PinResult(String scanId, boolean pinned, Summary summary) {}

    public record PurgeResult(
            List<String> purgedScanIds,
            boolean cleanupPending,
            Summary summary) {
        public PurgeResult {
            purgedScanIds = purgedScanIds == null ? List.of() : List.copyOf(purgedScanIds);
        }
    }

    public static final class PurgeOutcomeUnknownException extends Exception {
        public PurgeOutcomeUnknownException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The pin transaction may have committed even though the driver did not acknowledge it. */
    public static final class PinOutcomeUnknownException extends SQLException {
        public PinOutcomeUnknownException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The selected READY snapshot disappeared or changed before its pin update could commit. */
    public static final class StaleSnapshotException extends SQLException {
        private final String scanId;

        public StaleSnapshotException(String scanId) {
            super("The selected Page Mapping capture changed or is no longer available.");
            this.scanId = scanId == null ? "" : scanId;
        }

        public String scanId() {
            return scanId;
        }
    }

    /** The caller's displayed retention policy is no longer the configured source of truth. */
    public static final class StaleRetentionPolicyException extends SQLException {
        private final Policy expectedPolicy;
        private final Policy currentPolicy;

        public StaleRetentionPolicyException(Policy expectedPolicy, Policy currentPolicy) {
            super("Snapshot retention policy changed. Reload Page Mappings before purging.");
            this.expectedPolicy = expectedPolicy;
            this.currentPolicy = currentPolicy;
        }

        public Policy expectedPolicy() {
            return expectedPolicy;
        }

        public Policy currentPolicy() {
            return currentPolicy;
        }
    }

    record SnapshotRow(
            String scanId,
            int homeBankingId,
            int botJobId,
            String pageKey,
            String capturedAt,
            String artifactPath,
            boolean pinned) {}

    private record TimedRow(SnapshotRow row, Instant capturedAt) {}

    private record Evaluation(List<SnapshotRow> eligible, Summary summary) {}
}
