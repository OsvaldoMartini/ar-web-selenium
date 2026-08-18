package com.allinweb.ch.db;

import com.allinweb.ch.model.InstructionGraphMutationV3.WorkspaceKind;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Database-owned optimistic version state for instruction graphs.
 *
 * <p>Every method operates on a caller-owned {@link Connection}. This repository never changes
 * auto-commit, commits, rolls back, or closes the connection, allowing a future graph mutation to
 * update rows and advance the graph version in one atomic transaction.
 */
public final class InstructionGraphStateRepository {

    public static final long INITIAL_VERSION = 0L;
    private static final String TABLE = "instruction_graph_state";

    private final Clock clock;

    public InstructionGraphStateRepository() {
        this(Clock.systemUTC());
    }

    InstructionGraphStateRepository(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Loads the owner's current database version, creating version zero when no row exists.
     *
     * <p>The insert is idempotent under the table's compound primary key. PostgreSQL and SQLite use
     * their native conflict-ignore syntax; other supported drivers use an insert plus duplicate
     * winner reload.
     */
    public GraphState loadOrCreate(Connection connection, OwnerKey owner) throws SQLException {
        requireConnection(connection);
        Objects.requireNonNull(owner, "owner");

        Optional<GraphState> existing = load(connection, owner);
        if (existing.isPresent()) {
            return existing.get();
        }

        insertInitialIfAbsent(connection, owner);
        return load(connection, owner)
                .orElseThrow(() -> new SQLException(
                        "Instruction graph state was not visible after load-or-create for " + owner));
    }

    /** Loads one owner state without creating it. */
    public Optional<GraphState> load(Connection connection, OwnerKey owner) throws SQLException {
        requireConnection(connection);
        Objects.requireNonNull(owner, "owner");

        String sql = "SELECT graph_version FROM " + TABLE
                + " WHERE workspace_kind = ? AND home_banking_id = ? AND owner_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindOwner(statement, owner, 1);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new GraphState(owner, rows.getLong("graph_version")));
            }
        }
    }

    /**
     * Atomically advances one owner from {@code expectedVersion} to {@code expectedVersion + 1}.
     *
     * <p>The database update predicate is the compare-and-set gate. A stale caller never writes,
     * and two callers submitting the same expected version can produce at most one winner.
     */
    public AdvanceResult compareAndSetIncrement(
            Connection connection,
            OwnerKey owner,
            long expectedVersion)
            throws SQLException {
        requireConnection(connection);
        Objects.requireNonNull(owner, "owner");
        if (expectedVersion < 0L || expectedVersion == Long.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Expected graph version must be between 0 and " + (Long.MAX_VALUE - 1L));
        }

        long nextVersion = expectedVersion + 1L;
        String sql = "UPDATE " + TABLE + " SET graph_version = ?, updated_at = ?"
                + " WHERE workspace_kind = ? AND home_banking_id = ? AND owner_id = ?"
                + " AND graph_version = ?";
        int updated;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, nextVersion);
            statement.setTimestamp(2, now());
            bindOwner(statement, owner, 3);
            statement.setLong(6, expectedVersion);
            updated = statement.executeUpdate();
        }
        if (updated == 1) {
            return new AdvanceResult(
                    AdvanceStatus.ADVANCED,
                    expectedVersion,
                    new GraphState(owner, nextVersion));
        }
        if (updated > 1) {
            throw new SQLException(
                    "Compound graph owner key was not unique for " + owner + "; rows updated=" + updated);
        }

        Optional<GraphState> current = load(connection, owner);
        return current
                .map(state -> new AdvanceResult(AdvanceStatus.STALE, expectedVersion, state))
                .orElseGet(() -> new AdvanceResult(AdvanceStatus.MISSING, expectedVersion, null));
    }

    /** Deletes only the state row for the supplied owner. Transaction ownership remains external. */
    public int cleanup(Connection connection, OwnerKey owner) throws SQLException {
        requireConnection(connection);
        Objects.requireNonNull(owner, "owner");

        String sql = "DELETE FROM " + TABLE
                + " WHERE workspace_kind = ? AND home_banking_id = ? AND owner_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindOwner(statement, owner, 1);
            return statement.executeUpdate();
        }
    }

    private void insertInitialIfAbsent(Connection connection, OwnerKey owner) throws SQLException {
        String columns = "(workspace_kind, home_banking_id, owner_id, graph_version,"
                + " created_at, updated_at)";
        String values = " VALUES (?, ?, ?, ?, ?, ?)";
        String product = databaseProduct(connection);
        String sql;
        if (product.contains("postgresql")) {
            sql = "INSERT INTO " + TABLE + " " + columns + values
                    + " ON CONFLICT (workspace_kind, home_banking_id, owner_id) DO NOTHING";
        } else if (product.contains("sqlite")) {
            sql = "INSERT OR IGNORE INTO " + TABLE + " " + columns + values;
        } else {
            sql = "INSERT INTO " + TABLE + " " + columns + values;
        }

        Timestamp now = now();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindOwner(statement, owner, 1);
            statement.setLong(4, INITIAL_VERSION);
            statement.setTimestamp(5, now);
            statement.setTimestamp(6, now);
            statement.executeUpdate();
        } catch (SQLException insertError) {
            // SQL Server and Access do not share a safe native conflict-ignore statement. If a
            // concurrent caller won the compound key race and the driver keeps the transaction
            // usable, treat its visible row as success. Otherwise preserve the original failure.
            try {
                if (load(connection, owner).isPresent()) {
                    return;
                }
            } catch (SQLException reloadError) {
                insertError.addSuppressed(reloadError);
            }
            throw insertError;
        }
    }

    private static void bindOwner(
            PreparedStatement statement,
            OwnerKey owner,
            int firstParameter)
            throws SQLException {
        statement.setString(firstParameter, owner.workspaceKind().name());
        statement.setInt(firstParameter + 1, owner.homeBankingId());
        statement.setInt(firstParameter + 2, owner.ownerId());
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }

    private static String databaseProduct(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        return product == null ? "" : product.toLowerCase(Locale.ROOT);
    }

    private static void requireConnection(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("An open caller-owned database connection is required");
        }
    }

    /**
     * Canonical workspace owner key.
     *
     * <p>For Components, {@code ownerId == homeBankingId}. For Bot Jobs, {@code ownerId ==
     * botJobId}. Keeping the workspace kind in the database primary key prevents numeric aliases.
     */
    public record OwnerKey(
            WorkspaceKind workspaceKind,
            int homeBankingId,
            int ownerId) {

        public OwnerKey {
            Objects.requireNonNull(workspaceKind, "workspaceKind");
            if (homeBankingId <= 0 || ownerId <= 0) {
                throw new IllegalArgumentException(
                        "Instruction graph owner IDs must be positive");
            }
            if (workspaceKind == WorkspaceKind.COMPONENT && ownerId != homeBankingId) {
                throw new IllegalArgumentException(
                        "A Component graph owner is its organization/homeBankingId");
            }
        }

        public static OwnerKey botJob(int homeBankingId, int botJobId) {
            return new OwnerKey(WorkspaceKind.BOT_JOB, homeBankingId, botJobId);
        }

        public static OwnerKey component(int homeBankingId) {
            return new OwnerKey(WorkspaceKind.COMPONENT, homeBankingId, homeBankingId);
        }
    }

    public record GraphState(OwnerKey owner, long version) {}

    public enum AdvanceStatus {
        ADVANCED,
        STALE,
        MISSING
    }

    public record AdvanceResult(
            AdvanceStatus status,
            long expectedVersion,
            GraphState state) {

        public boolean advanced() {
            return status == AdvanceStatus.ADVANCED;
        }
    }
}
