package com.allinweb.ch.facade.execution;

import com.allinweb.ch.db.InstructionGraphStateRepository;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.BlockFact;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.InstructionFact;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.Owner;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.VariableFact;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Read-only authoritative loader for one Bot Job execution graph.
 *
 * <p>The repository does not use {@code PerformLists}: those lists are mutable process-wide
 * projections and legacy loading silently normalizes EXCEL GOTO. Every fact is instead read from
 * one caller-independent database connection and constrained by the exact organization/Bot Job
 * owner. No row is repaired or persisted while preparing execution.
 */
public final class ExecutionPreflightSnapshotRepository {
    private final ConnectionProvider connections;
    private final InstructionGraphStateRepository graphStateRepository;

    public ExecutionPreflightSnapshotRepository(ConnectionProvider connections) {
        this(connections, new InstructionGraphStateRepository());
    }

    ExecutionPreflightSnapshotRepository(
            ConnectionProvider connections,
            InstructionGraphStateRepository graphStateRepository) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.graphStateRepository =
                Objects.requireNonNull(graphStateRepository, "graphStateRepository");
    }

    public LoadedSnapshot load(Owner owner) throws SQLException {
        Objects.requireNonNull(owner, "owner");
        try (Connection connection = connections.open()) {
            requireOwnedBotJob(connection, owner);
            List<BlockFact> blocks = loadBlocks(connection, owner);
            List<InstructionFact> instructions = loadInstructions(connection, owner);
            List<VariableFact> variables = loadVariables(connection, owner);
            OptionalLong graphVersion = loadGraphVersion(connection, owner);
            return new LoadedSnapshot(
                    new ExecutionPreflightSnapshot(owner, blocks, instructions, variables),
                    graphVersion);
        }
    }

    private void requireOwnedBotJob(Connection connection, Owner owner) throws SQLException {
        String sql =
                "SELECT id FROM bot_job WHERE id = ? AND home_banking_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, owner.botJobId());
            statement.setInt(2, owner.homeBankingId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException(
                            "Bot Job #"
                                    + owner.botJobId()
                                    + " is not owned by organization #"
                                    + owner.homeBankingId());
                }
                if (rows.next()) {
                    throw new SQLException(
                            "Bot Job owner query returned duplicate rows for #"
                                    + owner.botJobId());
                }
            }
        }
    }

    private List<BlockFact> loadBlocks(Connection connection, Owner owner) throws SQLException {
        String sql =
                "SELECT id, block_order_number, active"
                        + " FROM block WHERE bot_job_id = ?"
                        + " ORDER BY block_order_number, id";
        List<BlockFact> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, owner.botJobId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new BlockFact(
                            rows.getInt("id"),
                            rows.getInt("block_order_number"),
                            rows.getBoolean("active")));
                }
            }
        }
        return List.copyOf(result);
    }

    private List<InstructionFact> loadInstructions(
            Connection connection,
            Owner owner)
            throws SQLException {
        String sql =
                "SELECT i.id, i.block_id, i.instruction_order_number, i.actions,"
                        + " i.tag_name, i.active, i.parent_id, i.parent_block_id, i.variable_id"
                        + " FROM instruction i"
                        + " JOIN block b ON b.id = i.block_id"
                        + " WHERE b.bot_job_id = ?"
                        + " ORDER BY b.block_order_number, i.instruction_order_number, i.id";
        List<InstructionFact> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, owner.botJobId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new InstructionFact(
                            rows.getInt("id"),
                            rows.getInt("block_id"),
                            rows.getInt("instruction_order_number"),
                            rows.getString("actions"),
                            rows.getString("tag_name"),
                            rows.getBoolean("active"),
                            nullableInteger(rows, "parent_id"),
                            nullableInteger(rows, "parent_block_id"),
                            nullableInteger(rows, "variable_id")));
                }
            }
        }
        return List.copyOf(result);
    }

    private List<VariableFact> loadVariables(Connection connection, Owner owner)
            throws SQLException {
        String sql =
                "SELECT id, variable_type AS type,"
                        + " producer_instruction_id AS instruction_id"
                        + " FROM bot_job_variable_definition"
                        + " WHERE home_banking_id = ? AND bot_job_id = ? ORDER BY id";
        List<VariableFact> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.botJobId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new VariableFact(
                            rows.getInt("id"),
                            rows.getString("type"),
                            nullableInteger(rows, "instruction_id")));
                }
            }
        }
        return List.copyOf(result);
    }

    private OptionalLong loadGraphVersion(Connection connection, Owner owner) {
        try {
            Optional<InstructionGraphStateRepository.GraphState> state =
                    graphStateRepository.load(
                            connection,
                            OwnerKey.botJob(owner.homeBankingId(), owner.botJobId()));
            return state.isPresent()
                    ? OptionalLong.of(state.get().version())
                    : OptionalLong.empty();
        } catch (SQLException unavailable) {
            // P5 is not active yet and installations may not have migrated graph state. The
            // content snapshot remains useful in P4 shadow mode; hard enforcement must require it.
            return OptionalLong.empty();
        }
    }

    private static Integer nullableInteger(ResultSet rows, String column)
            throws SQLException {
        Object value = rows.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        int parsed = Integer.parseInt(String.valueOf(value));
        return rows.wasNull() ? null : parsed;
    }

    public record LoadedSnapshot(
            ExecutionPreflightSnapshot snapshot,
            OptionalLong graphVersion) {
        public LoadedSnapshot {
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            graphVersion =
                    graphVersion == null ? OptionalLong.empty() : graphVersion;
        }
    }

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection open() throws SQLException;
    }
}
