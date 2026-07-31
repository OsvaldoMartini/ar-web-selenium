package com.allinweb.ch.db;

import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.Definition;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.DefinitionDraft;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.DefinitionPatch;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.OwnerKey;
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
 * Caller-transaction-owned persistence for {@code bot_job_variable_definition}.
 *
 * <p>This repository never reads or writes the legacy {@code variable} table. A configured/default
 * value belongs to the definition; the current runtime value belongs exclusively to
 * {@link BotJobRuntimeMemoryRepository}.
 */
public final class BotJobVariableDefinitionRepository {
    public static final String TABLE = "bot_job_variable_definition";

    private static final String SELECT_COLUMNS =
            "id, variable_type, name, configured_value, local_format, delimiter,"
                    + " producer_instruction_id, created_at, updated_at";

    private final Clock clock;

    public BotJobVariableDefinitionRepository() {
        this(Clock.systemUTC());
    }

    BotJobVariableDefinitionRepository(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean ownerExists(Connection connection, OwnerKey owner) throws SQLException {
        requireOpen(connection);
        Objects.requireNonNull(owner, "owner");
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT 1 FROM bot_job WHERE id = ? AND home_banking_id = ?")) {
            select.setInt(1, owner.botJobId());
            select.setInt(2, owner.homeBankingId());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next();
            }
        }
    }

    public List<Definition> loadAll(Connection connection, OwnerKey owner) throws SQLException {
        requireOwner(connection, owner);
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + TABLE
                + " WHERE home_banking_id = ? AND bot_job_id = ? ORDER BY id";
        List<Definition> definitions = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            bindOwner(select, owner, 1);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    definitions.add(readDefinition(rows, owner));
                }
            }
        }
        return List.copyOf(definitions);
    }

    public Optional<Definition> load(
            Connection connection,
            OwnerKey owner,
            long variableId)
            throws SQLException {
        requireOwner(connection, owner);
        requireVariableId(variableId);
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + TABLE
                + " WHERE home_banking_id = ? AND bot_job_id = ? AND id = ?";
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            bindOwner(select, owner, 1);
            select.setLong(3, variableId);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next()
                        ? Optional.of(readDefinition(rows, owner))
                        : Optional.empty();
            }
        }
    }

    public boolean producerAlreadyDefined(
            Connection connection,
            OwnerKey owner,
            Long producerInstructionId,
            Long excludedVariableId)
            throws SQLException {
        requireOwner(connection, owner);
        if (producerInstructionId == null) {
            return false;
        }
        String sql = "SELECT 1 FROM " + TABLE
                + " WHERE home_banking_id = ? AND bot_job_id = ?"
                + " AND producer_instruction_id = ?"
                + (excludedVariableId == null ? "" : " AND id <> ?");
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            bindOwner(select, owner, 1);
            select.setLong(3, producerInstructionId);
            if (excludedVariableId != null) {
                select.setLong(4, excludedVariableId);
            }
            try (ResultSet rows = select.executeQuery()) {
                return rows.next();
            }
        }
    }

    public boolean producerBelongsToOwner(
            Connection connection,
            OwnerKey owner,
            Long producerInstructionId)
            throws SQLException {
        requireOwner(connection, owner);
        if (producerInstructionId == null) {
            return true;
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT 1 FROM instruction WHERE id = ? AND bot_job_id = ?")) {
            select.setLong(1, producerInstructionId);
            select.setInt(2, owner.botJobId());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next();
            }
        }
    }

    public Definition insert(
            Connection connection,
            OwnerKey owner,
            long variableId,
            DefinitionDraft draft)
            throws SQLException {
        requireOwner(connection, owner);
        requireVariableId(variableId);
        Objects.requireNonNull(draft, "draft");
        Instant now = clock.instant();
        String sql = "INSERT INTO " + TABLE
                + " (home_banking_id, bot_job_id, id, variable_type, name, configured_value,"
                + " local_format, delimiter, producer_instruction_id, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            bindOwner(insert, owner, 1);
            insert.setLong(3, variableId);
            insert.setString(4, draft.type());
            insert.setString(5, draft.name());
            insert.setString(6, draft.configuredValue());
            insert.setString(7, draft.localFormat());
            insert.setString(8, draft.delimiter());
            setNullableLong(insert, 9, draft.producerInstructionId());
            insert.setTimestamp(10, Timestamp.from(now));
            insert.setTimestamp(11, Timestamp.from(now));
            if (insert.executeUpdate() != 1) {
                throw new SQLException("Variable definition was not inserted exactly once");
            }
        }
        return new Definition(
                variableId,
                owner,
                draft.type(),
                draft.name(),
                draft.configuredValue(),
                draft.localFormat(),
                draft.delimiter(),
                draft.producerInstructionId(),
                now,
                now);
    }

    public Optional<Definition> update(
            Connection connection,
            OwnerKey owner,
            long variableId,
            DefinitionPatch patch)
            throws SQLException {
        requireOwner(connection, owner);
        requireVariableId(variableId);
        Objects.requireNonNull(patch, "patch");
        Instant now = clock.instant();
        String sql = "UPDATE " + TABLE
                + " SET variable_type = ?, name = ?, configured_value = ?, local_format = ?,"
                + " delimiter = ?, producer_instruction_id = ?, updated_at = ?"
                + " WHERE home_banking_id = ? AND bot_job_id = ? AND id = ?";
        int updated;
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            update.setString(1, patch.type());
            update.setString(2, patch.name());
            update.setString(3, patch.configuredValue());
            update.setString(4, patch.localFormat());
            update.setString(5, patch.delimiter());
            setNullableLong(update, 6, patch.producerInstructionId());
            update.setTimestamp(7, Timestamp.from(now));
            bindOwner(update, owner, 8);
            update.setLong(10, variableId);
            updated = update.executeUpdate();
        }
        if (updated == 0) {
            return Optional.empty();
        }
        if (updated != 1) {
            throw new SQLException("Variable definition owner key was not unique");
        }
        return load(connection, owner, variableId);
    }

    public int delete(
            Connection connection,
            OwnerKey owner,
            long variableId)
            throws SQLException {
        requireOwner(connection, owner);
        requireVariableId(variableId);
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + TABLE
                        + " WHERE home_banking_id = ? AND bot_job_id = ? AND id = ?")) {
            bindOwner(delete, owner, 1);
            delete.setLong(3, variableId);
            return delete.executeUpdate();
        }
    }

    public int deleteAll(Connection connection, OwnerKey owner) throws SQLException {
        requireOwner(connection, owner);
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + TABLE
                        + " WHERE home_banking_id = ? AND bot_job_id = ?")) {
            bindOwner(delete, owner, 1);
            return delete.executeUpdate();
        }
    }

    private void requireOwner(Connection connection, OwnerKey owner) throws SQLException {
        requireOpen(connection);
        Objects.requireNonNull(owner, "owner");
        if (!ownerExists(connection, owner)) {
            throw new SQLException("Bot Job variable owner was not found: " + owner);
        }
    }

    private static Definition readDefinition(ResultSet rows, OwnerKey owner)
            throws SQLException {
        return new Definition(
                rows.getLong("id"),
                owner,
                rows.getString("variable_type"),
                rows.getString("name"),
                rows.getString("configured_value"),
                rows.getString("local_format"),
                rows.getString("delimiter"),
                nullableLong(rows, "producer_instruction_id"),
                instant(rows, "created_at"),
                instant(rows, "updated_at"));
    }

    private static void bindOwner(
            PreparedStatement statement,
            OwnerKey owner,
            int firstParameter)
            throws SQLException {
        statement.setInt(firstParameter, owner.homeBankingId());
        statement.setInt(firstParameter + 1, owner.botJobId());
    }

    private static Long nullableLong(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column);
        if (value == null) {
            throw new SQLException("Variable definition timestamp " + column + " is missing");
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
                        "Variable definition timestamp " + column + " is invalid: " + text,
                        invalidTimestamp);
            }
        }
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

    private static void requireVariableId(long variableId) {
        if (variableId <= 0L) {
            throw new IllegalArgumentException("Variable ID must be positive");
        }
    }

    private static void requireOpen(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("An open caller-owned database connection is required");
        }
    }
}
