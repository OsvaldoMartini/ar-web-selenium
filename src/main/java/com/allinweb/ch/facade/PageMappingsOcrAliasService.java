package com.allinweb.ch.facade;

import com.allinweb.ch.db.ScannedElementRepository;
import com.allinweb.ch.model.ScannedElement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Atomic persistence boundary for aliases accepted from Page Mappings OCR Review.
 *
 * <p>The detached page supplies revision references, never authority. This service reloads every
 * row by its complete server-owned organization, Bot Job, page and row identity, verifies the
 * scanner revision and current alias, and changes only {@code scanned_element.client_named}. A
 * stale or unauthorized row rolls back the complete batch.
 */
public final class PageMappingsOcrAliasService {

    private static final int MAX_ALIAS_LENGTH = 255;
    private static final int MAX_BATCH_SIZE = 1_000;
    private static final PageMappingsOcrAliasService INSTANCE =
            new PageMappingsOcrAliasService(() -> PerformDataBase.getInstance().getConnection());

    private final ConnectionProvider connections;

    public static PageMappingsOcrAliasService getInstance() {
        return INSTANCE;
    }

    PageMappingsOcrAliasService(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "Connection provider is required");
    }

    /** Apply an ordered alias batch as one SERIALIZABLE transaction. */
    public ApplyResult apply(
            int homeBankingId,
            int botJobId,
            String pageKey,
            List<AliasChange> requestedChanges)
            throws SQLException {
        Connection connection = connections.open();
        if (connection == null) {
            throw new SQLException("Page Mappings alias storage is unavailable");
        }
        Throwable operationFailure = null;
        try {
            return applyTransaction(
                    connection, homeBankingId, botJobId, pageKey, requestedChanges);
        } catch (SQLException | RuntimeException | Error failure) {
            operationFailure = failure;
            throw failure;
        } finally {
            closeConnection(connection, operationFailure);
        }
    }

    /** Package-visible transaction seam kept independent from WebSocket parsing and delivery. */
    ApplyResult applyTransaction(
            Connection connection,
            int homeBankingId,
            int botJobId,
            String pageKey,
            List<AliasChange> requestedChanges)
            throws SQLException {
        requireOpen(connection);
        String exactPageKey = requirePageKey(pageKey);
        List<AliasChange> changes = validateChanges(requestedChanges);
        if (homeBankingId <= 0 || botJobId <= 0) {
            throw refused(
                    "PAGE_MAPPINGS_OCR_ALIAS_OWNER_REQUIRED",
                    "An active organization and Bot Job are required.");
        }
        if (!connection.getAutoCommit()) {
            throw new SQLException(
                    "Page Mappings OCR alias apply requires an unbound connection");
        }

        int previousIsolation = connection.getTransactionIsolation();
        boolean isolationChanged = false;
        boolean transactionStarted = false;
        boolean commitAttempted = false;
        boolean committed = false;
        Throwable operationFailure = null;
        try {
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            isolationChanged = true;
            connection.setAutoCommit(false);
            transactionStarted = true;

            requireOwnedBotJob(connection, homeBankingId, botJobId);
            List<PreparedChange> prepared = prepare(
                    connection, homeBankingId, botJobId, exactPageKey, changes);

            for (PreparedChange change : prepared) {
                if (!change.changed()) continue;
                int affected = ScannedElementRepository.updateClientNamedExact(
                        connection,
                        homeBankingId,
                        botJobId,
                        exactPageKey,
                        change.request().scannedElementId(),
                        change.request().expectedElementHash(),
                        change.request().expectedLastScannedAt(),
                        change.request().expectedScanCount(),
                        change.request().expectedClientNamed(),
                        change.committedClientNamed());
                if (affected != 1) {
                    throw refused(
                            "PAGE_MAPPINGS_OCR_ALIAS_WRITE_CONFLICT",
                            "A Page Mapping changed before its name could be saved.");
                }
            }

            List<CommittedAlias> committedAliases = verifyCommitted(
                    connection, homeBankingId, botJobId, exactPageKey, prepared);
            commitAttempted = true;
            try {
                connection.commit();
            } catch (SQLException | RuntimeException commitFailure) {
                throw new AliasApplyOutcomeUnknownException(
                        "The OCR Review save outcome is unknown. Reload Page Mappings before saving names again.",
                        commitFailure);
            }
            committed = true;
            return new ApplyResult(
                    List.copyOf(committedAliases), changedCount(committedAliases));
        } catch (SQLException | RuntimeException | Error failure) {
            operationFailure = failure;
            if (transactionStarted && !commitAttempted) rollback(connection, failure);
            throw failure;
        } finally {
            // A rollback or setAutoCommit(true) after a failed commit acknowledgement can itself
            // decide the transaction. Leave the isolated connection untouched and close it.
            if (!commitAttempted || committed) {
                restoreIsolation(
                        connection,
                        previousIsolation,
                        isolationChanged,
                        operationFailure);
                restoreAutoCommit(connection, operationFailure);
            }
        }
    }

    private static List<PreparedChange> prepare(
            Connection connection,
            int homeBankingId,
            int botJobId,
            String pageKey,
            List<AliasChange> changes)
            throws SQLException {
        List<PreparedChange> prepared = new ArrayList<>(changes.size());
        for (AliasChange change : changes) {
            ScannedElement current = ScannedElementRepository.loadExact(
                            connection,
                            homeBankingId,
                            botJobId,
                            pageKey,
                            change.scannedElementId())
                    .orElseThrow(() -> refused(
                            "PAGE_MAPPINGS_OCR_ALIAS_ROW_MISSING",
                            "A selected Page Mapping is no longer available."));
            requireExpectedRevision(current, change);
            String committedClientNamed = normalizeClientNamed(change.clientNamed(), current);
            prepared.add(new PreparedChange(
                    change,
                    committedClientNamed,
                    !Objects.equals(current.getClientNamed(), committedClientNamed)));
        }
        return prepared;
    }

    private static List<CommittedAlias> verifyCommitted(
            Connection connection,
            int homeBankingId,
            int botJobId,
            String pageKey,
            List<PreparedChange> prepared)
            throws SQLException {
        List<CommittedAlias> result = new ArrayList<>(prepared.size());
        for (PreparedChange change : prepared) {
            ScannedElement committed = ScannedElementRepository.loadExact(
                            connection,
                            homeBankingId,
                            botJobId,
                            pageKey,
                            change.request().scannedElementId())
                    .orElseThrow(() -> refused(
                            "PAGE_MAPPINGS_OCR_ALIAS_VERIFY_MISSING",
                            "A saved Page Mapping could not be verified."));
            requireStableRevision(committed, change.request());
            if (!Objects.equals(
                    change.committedClientNamed(), committed.getClientNamed())) {
                throw refused(
                        "PAGE_MAPPINGS_OCR_ALIAS_VERIFY_FAILED",
                        "A Page Mapping name could not be verified after saving.");
            }
            result.add(new CommittedAlias(
                    committed.getId(),
                    value(committed.getElementHash()),
                    value(committed.getLastScannedAt()),
                    committed.getScanCount(),
                    committed.getClientNamed(),
                    change.changed()));
        }
        return result;
    }

    private static void requireExpectedRevision(ScannedElement current, AliasChange expected)
            throws AliasApplyRefusedException {
        requireStableRevision(current, expected);
        if (!Objects.equals(expected.expectedClientNamed(), current.getClientNamed())) {
            throw refused(
                    "PAGE_MAPPINGS_OCR_ALIAS_CURRENT_STALE",
                    "A Page Mapping name changed. Reload OCR Review before saving.");
        }
    }

    private static void requireStableRevision(ScannedElement current, AliasChange expected)
            throws AliasApplyRefusedException {
        if (!expected.expectedElementHash().equalsIgnoreCase(value(current.getElementHash()))
                || expected.expectedScanCount() != current.getScanCount()
                || !expected.expectedLastScannedAt().equals(value(current.getLastScannedAt()))) {
            throw refused(
                    "PAGE_MAPPINGS_OCR_ALIAS_REVISION_STALE",
                    "A Page Mapping changed. Reload OCR Review before saving.");
        }
    }

    private static List<AliasChange> validateChanges(List<AliasChange> requested)
            throws AliasApplyRefusedException {
        if (requested == null || requested.isEmpty()) {
            throw refused(
                    "PAGE_MAPPINGS_OCR_ALIAS_EMPTY",
                    "Select at least one OCR name to save.");
        }
        if (requested.size() > MAX_BATCH_SIZE) {
            throw refused(
                    "PAGE_MAPPINGS_OCR_ALIAS_BATCH_TOO_LARGE",
                    "Too many OCR names were selected at once.");
        }
        Set<Long> rowIds = new HashSet<>();
        List<AliasChange> result = new ArrayList<>(requested.size());
        for (AliasChange candidate : requested) {
            if (candidate == null || !candidate.valid()) {
                throw refused(
                        "PAGE_MAPPINGS_OCR_ALIAS_INVALID",
                        "A selected OCR name has an invalid revision reference.");
            }
            if (!rowIds.add(candidate.scannedElementId())) {
                throw refused(
                        "PAGE_MAPPINGS_OCR_ALIAS_DUPLICATE",
                        "The same Page Mapping cannot be saved twice in one request.");
            }
            String requestedAlias = trimToNull(candidate.clientNamed());
            if (requestedAlias != null && requestedAlias.length() > MAX_ALIAS_LENGTH) {
                throw refused(
                        "PAGE_MAPPINGS_OCR_ALIAS_TOO_LONG",
                        "A Page Mapping name is too long.");
            }
            result.add(new AliasChange(
                    candidate.scannedElementId(),
                    candidate.expectedElementHash().trim().toLowerCase(Locale.ROOT),
                    candidate.expectedLastScannedAt().trim(),
                    candidate.expectedScanCount(),
                    candidate.expectedClientNamed(),
                    requestedAlias));
        }
        return List.copyOf(result);
    }

    private static String normalizeClientNamed(String requested, ScannedElement current) {
        String normalized = trimToNull(requested);
        if (normalized == null
                || normalized.equals(current.getDefinedName())
                || normalized.equals(current.getSomeText())
                || normalized.equals(current.getTagName())) {
            return null;
        }
        return normalized;
    }

    private static String requirePageKey(String value) throws AliasApplyRefusedException {
        String pageKey = value == null ? "" : value.trim();
        if (pageKey.isEmpty() || pageKey.length() > 128) {
            throw refused(
                    "PAGE_MAPPINGS_OCR_ALIAS_PAGE_REQUIRED",
                    "Select a Page Mapping capture before saving OCR names.");
        }
        return pageKey;
    }

    private static void requireOwnedBotJob(
            Connection connection, int homeBankingId, int botJobId) throws SQLException {
        String sql = "SELECT id FROM bot_job WHERE id = ? AND home_banking_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, botJobId);
            statement.setInt(2, homeBankingId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw refused(
                            "PAGE_MAPPINGS_OCR_ALIAS_OWNER_MISMATCH",
                            "The selected Page Mapping does not belong to this Bot Job.");
                }
                if (rows.next()) {
                    throw new SQLException("Duplicate Bot Job owner identity");
                }
            }
        }
    }

    private static int changedCount(List<CommittedAlias> aliases) {
        int changed = 0;
        for (CommittedAlias alias : aliases) {
            if (alias.changed()) changed++;
        }
        return changed;
    }

    private static void requireOpen(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("An open database connection is required");
        }
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException | RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreIsolation(
            Connection connection,
            int previousIsolation,
            boolean isolationChanged,
            Throwable operationFailure) {
        if (!isolationChanged) return;
        try {
            connection.setTransactionIsolation(previousIsolation);
        } catch (SQLException | RuntimeException restorationFailure) {
            suppress(operationFailure, restorationFailure);
        }
    }

    private static void restoreAutoCommit(
            Connection connection, Throwable operationFailure) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException | RuntimeException restorationFailure) {
            suppress(operationFailure, restorationFailure);
        }
    }

    private static void closeConnection(
            Connection connection, Throwable operationFailure)
            throws AliasApplyOutcomeUnknownException {
        try {
            connection.close();
        } catch (SQLException | RuntimeException closeFailure) {
            if (operationFailure != null) {
                operationFailure.addSuppressed(closeFailure);
                return;
            }
            throw new AliasApplyOutcomeUnknownException(
                    "The OCR Review save outcome is unknown. Reload Page Mappings before saving names again.",
                    closeFailure);
        }
    }

    private static void suppress(Throwable operationFailure, Throwable cleanupFailure) {
        if (operationFailure != null && cleanupFailure != operationFailure) {
            operationFailure.addSuppressed(cleanupFailure);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String value(String source) {
        return source == null ? "" : source;
    }

    private static AliasApplyRefusedException refused(String code, String message) {
        return new AliasApplyRefusedException(code, message);
    }

    public record AliasChange(
            long scannedElementId,
            String expectedElementHash,
            String expectedLastScannedAt,
            int expectedScanCount,
            String expectedClientNamed,
            String clientNamed) {
        public boolean valid() {
            String hash = expectedElementHash == null
                    ? ""
                    : expectedElementHash.trim().toLowerCase(Locale.ROOT);
            String scannedAt = expectedLastScannedAt == null
                    ? ""
                    : expectedLastScannedAt.trim();
            return scannedElementId > 0
                    && hash.matches("[0-9a-f]{64}")
                    && !scannedAt.isEmpty()
                    && scannedAt.length() <= 40
                    && expectedScanCount > 0
                    && (expectedClientNamed == null
                            || expectedClientNamed.length() <= MAX_ALIAS_LENGTH);
        }
    }

    public record CommittedAlias(
            long scannedElementId,
            String elementHash,
            String lastScannedAt,
            int scanCount,
            String clientNamed,
            boolean changed) {}

    public record ApplyResult(List<CommittedAlias> aliases, int changedCount) {
        public ApplyResult {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }

    private record PreparedChange(
            AliasChange request, String committedClientNamed, boolean changed) {}

    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    public static final class AliasApplyRefusedException extends SQLException {
        private final String code;

        AliasApplyRefusedException(String code, String message) {
            super(message);
            this.code = Objects.requireNonNull(code, "code");
        }

        public String code() {
            return code;
        }
    }

    /** The alias transaction may have committed even though JDBC did not return a clean outcome. */
    public static final class AliasApplyOutcomeUnknownException extends SQLException {
        public AliasApplyOutcomeUnknownException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
