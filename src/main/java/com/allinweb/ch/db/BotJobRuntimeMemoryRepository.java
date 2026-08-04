package com.allinweb.ch.db;

import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.MemoryState;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.OwnerKey;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.RuntimeValue;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.ValueSource;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.ValueState;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.VoidReason;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Caller-transaction-owned persistence for durable Bot Job runtime memory.
 *
 * <p>All predicates include both owner IDs. The repository preserves raw values exactly; it never
 * trims, parses, localizes, or substitutes sentinels. {@code VALUE("")} and {@code VOID} therefore
 * remain distinct database states.
 */
public final class BotJobRuntimeMemoryRepository {
    /**
     * Since 2026-08-03 (user decision: fewer tables, no new tables) the three runtime
     * counters live as columns on the existing instruction_graph_state row instead of
     * the dropped bot_job_runtime_memory table. NULL columns = memory not initialized.
     */
    public static final String STATE_TABLE = "instruction_graph_state";
    public static final String VALUE_TABLE = "bot_job_runtime_variable_value";
    private static final String STATE_WHERE =
            " WHERE workspace_kind = 'BOT_JOB' AND home_banking_id = ? AND owner_id = ?";

    private final Clock clock;
    private final InstructionGraphStateRepository graphStates =
            new InstructionGraphStateRepository();

    public BotJobRuntimeMemoryRepository() {
        this(Clock.systemUTC());
    }

    BotJobRuntimeMemoryRepository(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public MemoryState loadOrCreateMemory(Connection connection, OwnerKey owner)
            throws SQLException {
        requireOpen(connection);
        Objects.requireNonNull(owner, "owner");
        Optional<MemoryState> current = loadMemory(connection, owner);
        if (current.isPresent()) {
            return current.get();
        }

        graphStates.loadOrCreate(
                connection,
                InstructionGraphStateRepository.OwnerKey.botJob(
                        owner.homeBankingId(), owner.botJobId()));
        long nextVariableId = loadNextDefinitionId(connection, owner);
        String sql = "UPDATE " + STATE_TABLE
                + " SET runtime_revision = 0, reset_generation = 0, next_variable_id = ?,"
                + " updated_at = ?" + STATE_WHERE + " AND next_variable_id IS NULL";
        try (PreparedStatement initialize = connection.prepareStatement(sql)) {
            initialize.setLong(1, nextVariableId);
            initialize.setTimestamp(2, Timestamp.from(clock.instant()));
            bindOwner(initialize, owner, 3);
            initialize.executeUpdate();
        }
        return loadMemory(connection, owner)
                .orElseThrow(() -> new SQLException("Runtime memory was not visible after insert"));
    }

    public Optional<MemoryState> loadMemory(Connection connection, OwnerKey owner)
            throws SQLException {
        requireOpen(connection);
        Objects.requireNonNull(owner, "owner");
        String sql = "SELECT runtime_revision, reset_generation, next_variable_id,"
                + " created_at, updated_at FROM " + STATE_TABLE + STATE_WHERE;
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            bindOwner(select, owner, 1);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                long nextVariableId = rows.getLong("next_variable_id");
                if (rows.wasNull()) {
                    return Optional.empty();
                }
                return Optional.of(new MemoryState(
                        owner,
                        rows.getLong("runtime_revision"),
                        rows.getLong("reset_generation"),
                        nextVariableId,
                        instant(rows, "created_at"),
                        instant(rows, "updated_at")));
            }
        }
    }

    /**
     * Reserves one owner-scoped variable ID using compare-and-set on the memory row.
     *
     * @return the reserved ID, or {@code -1} when another writer changed the counter
     */
    public long reserveNextVariableId(
            Connection connection,
            OwnerKey owner,
            long expectedNextVariableId)
            throws SQLException {
        requireOpen(connection);
        if (expectedNextVariableId <= 0L || expectedNextVariableId == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Expected next variable ID is invalid");
        }
        String sql = "UPDATE " + STATE_TABLE
                + " SET next_variable_id = ?, updated_at = ?"
                + STATE_WHERE + " AND next_variable_id = ?";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            update.setLong(1, expectedNextVariableId + 1L);
            update.setTimestamp(2, Timestamp.from(clock.instant()));
            bindOwner(update, owner, 3);
            update.setLong(5, expectedNextVariableId);
            int changed = update.executeUpdate();
            if (changed > 1) {
                throw new SQLException("Runtime memory owner key was not unique");
            }
            return changed == 1 ? expectedNextVariableId : -1L;
        }
    }

    public List<RuntimeValue> loadValues(Connection connection, OwnerKey owner)
            throws SQLException {
        requireOpen(connection);
        String sql = "SELECT variable_id, value_state, raw_value, void_reason, value_source,"
                + " entry_revision, last_execution_id, updated_at FROM " + VALUE_TABLE
                + " WHERE home_banking_id = ? AND bot_job_id = ? ORDER BY variable_id";
        List<RuntimeValue> values = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            bindOwner(select, owner, 1);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    values.add(readValue(rows));
                }
            }
        }
        return List.copyOf(values);
    }

    public Optional<RuntimeValue> loadValue(
            Connection connection,
            OwnerKey owner,
            long variableId)
            throws SQLException {
        requireOpen(connection);
        String sql = "SELECT variable_id, value_state, raw_value, void_reason, value_source,"
                + " entry_revision, last_execution_id, updated_at FROM " + VALUE_TABLE
                + " WHERE home_banking_id = ? AND bot_job_id = ? AND variable_id = ?";
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            bindOwner(select, owner, 1);
            select.setLong(3, variableId);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? Optional.of(readValue(rows)) : Optional.empty();
            }
        }
    }

    public RuntimeValue insertInitial(
            Connection connection,
            OwnerKey owner,
            long variableId,
            ValueState state,
            String rawValue,
            VoidReason voidReason,
            ValueSource source)
            throws SQLException {
        validateValue(state, rawValue, voidReason);
        Instant now = clock.instant();
        String sql = "INSERT INTO " + VALUE_TABLE
                + " (home_banking_id, bot_job_id, variable_id, value_state, raw_value,"
                + " void_reason, value_source, entry_revision, last_execution_id, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            bindOwner(insert, owner, 1);
            insert.setLong(3, variableId);
            insert.setString(4, state.name());
            insert.setString(5, rawValue);
            insert.setString(6, voidReason == null ? null : voidReason.name());
            insert.setString(7, source.name());
            insert.setLong(8, 0L);
            insert.setObject(9, null);
            insert.setTimestamp(10, Timestamp.from(now));
            if (insert.executeUpdate() != 1) {
                throw new SQLException("Initial runtime value was not inserted exactly once");
            }
        }
        return new RuntimeValue(
                variableId,
                state,
                rawValue,
                voidReason,
                source,
                0L,
                null,
                now);
    }

    /**
     * Compares and replaces one value.
     *
     * @return {@code true} on success, {@code false} when the entry revision was stale
     */
    public boolean compareAndSetValue(
            Connection connection,
            OwnerKey owner,
            RuntimeValue next,
            long expectedEntryRevision)
            throws SQLException {
        Objects.requireNonNull(next, "next");
        validateValue(next.state(), next.rawValue(), next.voidReason());
        String sql = "UPDATE " + VALUE_TABLE
                + " SET value_state = ?, raw_value = ?, void_reason = ?, value_source = ?,"
                + " entry_revision = ?, last_execution_id = ?, updated_at = ?"
                + " WHERE home_banking_id = ? AND bot_job_id = ? AND variable_id = ?"
                + " AND entry_revision = ?";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            update.setString(1, next.state().name());
            update.setString(2, next.rawValue());
            update.setString(3, next.voidReason() == null ? null : next.voidReason().name());
            update.setString(4, next.source().name());
            update.setLong(5, next.entryRevision());
            setNullableLong(update, 6, next.lastExecutionId());
            update.setTimestamp(7, Timestamp.from(next.updatedAt()));
            bindOwner(update, owner, 8);
            update.setLong(10, next.variableId());
            update.setLong(11, expectedEntryRevision);
            int changed = update.executeUpdate();
            if (changed > 1) {
                throw new SQLException("Runtime value owner key was not unique");
            }
            return changed == 1;
        }
    }

    /**
     * Marks every current value VOID while preserving definitions and incrementing each entry
     * revision.
     */
    public int clearAllValues(
            Connection connection,
            OwnerKey owner,
            VoidReason reason,
            ValueSource source,
            Long executionId)
            throws SQLException {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(source, "source");
        String sql = "UPDATE " + VALUE_TABLE
                + " SET value_state = 'VOID', raw_value = NULL, void_reason = ?,"
                + " value_source = ?, entry_revision = entry_revision + 1,"
                + " last_execution_id = ?, updated_at = ?"
                + " WHERE home_banking_id = ? AND bot_job_id = ?";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            update.setString(1, reason.name());
            update.setString(2, source.name());
            setNullableLong(update, 3, executionId);
            update.setTimestamp(4, Timestamp.from(clock.instant()));
            bindOwner(update, owner, 5);
            return update.executeUpdate();
        }
    }

    public int deleteValue(
            Connection connection,
            OwnerKey owner,
            long variableId)
            throws SQLException {
        String sql = "DELETE FROM " + VALUE_TABLE
                + " WHERE home_banking_id = ? AND bot_job_id = ? AND variable_id = ?";
        try (PreparedStatement delete = connection.prepareStatement(sql)) {
            bindOwner(delete, owner, 1);
            delete.setLong(3, variableId);
            return delete.executeUpdate();
        }
    }

    /**
     * Advances the Bot Job-wide revision with optimistic concurrency.
     *
     * @param incrementResetGeneration whether this is the explicit RESET/CLEAR ALL boundary
     */
    public Optional<MemoryState> compareAndSetMemoryRevision(
            Connection connection,
            OwnerKey owner,
            long expectedRuntimeRevision,
            boolean incrementResetGeneration)
            throws SQLException {
        if (expectedRuntimeRevision < 0L || expectedRuntimeRevision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Expected runtime revision is invalid");
        }
        String sql = "UPDATE " + STATE_TABLE
                + " SET runtime_revision = ?, reset_generation = reset_generation + ?,"
                + " updated_at = ?" + STATE_WHERE
                + " AND runtime_revision = ?";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            update.setLong(1, expectedRuntimeRevision + 1L);
            update.setInt(2, incrementResetGeneration ? 1 : 0);
            update.setTimestamp(3, Timestamp.from(clock.instant()));
            bindOwner(update, owner, 4);
            update.setLong(6, expectedRuntimeRevision);
            int changed = update.executeUpdate();
            if (changed > 1) {
                throw new SQLException("Runtime memory owner key was not unique");
            }
            return changed == 1 ? loadMemory(connection, owner) : Optional.empty();
        }
    }

    public int cleanup(Connection connection, OwnerKey owner) throws SQLException {
        requireOpen(connection);
        try (PreparedStatement deleteValues = connection.prepareStatement(
                        "DELETE FROM " + VALUE_TABLE
                                + " WHERE home_banking_id = ? AND bot_job_id = ?");
                PreparedStatement resetMemory = connection.prepareStatement(
                        "UPDATE " + STATE_TABLE
                                + " SET runtime_revision = NULL, reset_generation = NULL,"
                                + " next_variable_id = NULL, updated_at = ?" + STATE_WHERE)) {
            bindOwner(deleteValues, owner, 1);
            int changed = deleteValues.executeUpdate();
            resetMemory.setTimestamp(1, Timestamp.from(clock.instant()));
            bindOwner(resetMemory, owner, 2);
            changed += resetMemory.executeUpdate();
            return changed;
        }
    }

    public Instant now() {
        return clock.instant();
    }

    private static long loadNextDefinitionId(Connection connection, OwnerKey owner)
            throws SQLException {
        String sql = "SELECT MAX(id) FROM " + BotJobVariableDefinitionRepository.TABLE
                + " WHERE home_banking_id = ? AND bot_job_id = ?";
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            bindOwner(select, owner, 1);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    return 1L;
                }
                long max = rows.getLong(1);
                return rows.wasNull() ? 1L : Math.addExact(max, 1L);
            }
        }
    }

    private static RuntimeValue readValue(ResultSet rows) throws SQLException {
        ValueState state = ValueState.valueOf(rows.getString("value_state"));
        String voidReasonText = rows.getString("void_reason");
        return new RuntimeValue(
                rows.getLong("variable_id"),
                state,
                rows.getString("raw_value"),
                voidReasonText == null ? null : VoidReason.valueOf(voidReasonText),
                ValueSource.valueOf(rows.getString("value_source")),
                rows.getLong("entry_revision"),
                nullableLong(rows, "last_execution_id"),
                instant(rows, "updated_at"));
    }

    private static void validateValue(
            ValueState state,
            String rawValue,
            VoidReason voidReason) {
        Objects.requireNonNull(state, "state");
        if (state == ValueState.VALUE) {
            Objects.requireNonNull(rawValue, "VALUE requires raw text; empty is valid");
            if (voidReason != null) {
                throw new IllegalArgumentException("VALUE cannot carry a VOID reason");
            }
        } else {
            if (rawValue != null) {
                throw new IllegalArgumentException("VOID cannot carry raw text");
            }
            Objects.requireNonNull(voidReason, "VOID requires a reason");
        }
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column);
        if (value == null) {
            throw new SQLException("Runtime memory timestamp " + column + " is missing");
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof Number epochMillis) {
            return Instant.ofEpochMilli(epochMillis.longValue());
        }
        String text = value.toString();
        try {
            long epoch = Long.parseLong(text);
            return Math.abs(epoch) < 10_000_000_000L
                    ? Instant.ofEpochSecond(epoch)
                    : Instant.ofEpochMilli(epoch);
        } catch (NumberFormatException ignored) {
            // Continue with the ISO/JDBC timestamp formats used by other database drivers.
        }
        try {
            return Instant.parse(text);
        } catch (RuntimeException ignored) {
            try {
                return Timestamp.valueOf(text).toInstant();
            } catch (RuntimeException invalidTimestamp) {
                throw new SQLException(
                        "Runtime memory timestamp " + column + " is invalid: " + text,
                        invalidTimestamp);
            }
        }
    }

    private static Long nullableLong(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private static void setNullableLong(
            PreparedStatement statement,
            int parameter,
            Long value)
            throws SQLException {
        if (value == null) {
            statement.setObject(parameter, null);
        } else {
            statement.setLong(parameter, value);
        }
    }

    private static void bindOwner(
            PreparedStatement statement,
            OwnerKey owner,
            int firstParameter)
            throws SQLException {
        statement.setInt(firstParameter, owner.homeBankingId());
        statement.setInt(firstParameter + 1, owner.botJobId());
    }

    private static void requireOpen(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("An open caller-owned database connection is required");
        }
    }
}
