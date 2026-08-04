package com.allinweb.ch.facade;

import com.allinweb.ch.db.InstructionGraphStateRepository;
import com.allinweb.ch.db.InstructionGraphStateRepository.GraphState;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.db.migrations.M20260803_InstructionVariableSlot;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.model.InstructionGraphMutationV3.WorkspaceKind;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.VariableLoadDTO;
import com.allinweb.ch.model.VariablesCheckOperandConnectV1;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persists the React-authored RIGHT-spot connections for CheckValue commands
 * (NEW variable rules step 1, 2026-08-03). FE owns the rule - Java only:
 * validates the CAS anchors, fills FREE spots (config operand + slot row),
 * never overwrites an occupied spot, and mirrors both storages:
 * instruction_variable_slot (authoritative going forward) and
 * instruction_variable_command_config.operand_kind/operand_variable_id
 * (legacy mirror the Engine and current UI read).
 */
public final class VariablesCheckOperandConnectTransaction {
    /** Matches FE binaryComparisonOperators (CheckValueCommandEditor.tsx). */
    private static final Set<String> BINARY_COMPARISON_OPERATORS = Set.of(
            "=", "!=", ">", "<", ">=", "<=", "contains", "startsWith", "endsWith");
    private static final Set<String> CHECK_ACTIONS =
            Set.of("CK", "PDF CHECK", "CSV CHECK");
    private final InstructionGraphStateRepository stateRepository =
            new InstructionGraphStateRepository();
    private final InstructionGraphRevisionService revisionService =
            new InstructionGraphRevisionService();

    public ConnectResult execute(
            Connection connection,
            AuthenticatedBotJob owner,
            VariablesCheckOperandConnectV1.Request request)
            throws SQLException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");
        if (connection == null || connection.isClosed()) {
            throw new SQLException("CheckValue operand connection requires an open connection.");
        }
        if (!connection.getAutoCommit()) {
            throw new SQLException("CheckValue operand connection requires an unbound connection.");
        }
        connection.setAutoCommit(false);
        boolean restoreAutoCommit = true;
        try {
            requireOwner(connection, owner.owner());
            validate(connection, owner, request);
            boolean release = request.isRelease();
            boolean updateOperator = request.isUpdateOperator();
            int rightVariableId = request.rightVariableId() == null
                    ? 0
                    : request.rightVariableId();
            Timestamp now = new Timestamp(System.currentTimeMillis());
            boolean hasSlotTable = tableExists(
                    connection, M20260803_InstructionVariableSlot.TABLE);
            boolean hasConfigTable = tableExists(
                    connection, "instruction_variable_command_config");
            int connected = 0;
            int skipped = 0;
            for (Integer instructionId : sanitizedIds(request.instructionIds())) {
                String actions = instructionActions(
                        connection, owner.owner().ownerId(), instructionId);
                if (actions == null) {
                    skipped++;
                    continue;
                }
                if (updateOperator) {
                    boolean wrote = hasConfigTable
                            && CHECK_ACTIONS.contains(CommandRegistry.canonicalize(actions))
                            && updateComparisonOperator(
                                    connection, owner.owner(), instructionId,
                                    actions, request.comparisonOperator().trim(), now);
                    if (wrote) connected++;
                    else skipped++;
                    continue;
                }
                boolean wroteConfig;
                boolean wroteSlot;
                if (release) {
                    wroteConfig = hasConfigTable
                            && releaseConfigRightSpot(
                                    connection, owner.owner(), instructionId, now);
                    wroteSlot = hasSlotTable
                            && releaseSlotRightSpot(
                                    connection, owner.owner(), instructionId);
                } else {
                    wroteConfig = hasConfigTable
                            && fillConfigRightSpot(
                                    connection, owner.owner(), instructionId,
                                    actions, rightVariableId, now);
                    wroteSlot = hasSlotTable
                            && fillSlotRightSpot(
                                    connection, owner.owner(), instructionId,
                                    rightVariableId, now);
                }
                if (wroteConfig || wroteSlot) connected++;
                else skipped++;
            }
            connection.commit();
            return new ConnectResult(
                    owner.owner(), owner.workspaceEpoch(),
                    request.requestId().trim(), rightVariableId, connected, skipped);
        } catch (SQLException | RuntimeException failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                restoreAutoCommit = false;
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        } finally {
            if (restoreAutoCommit && !connection.isClosed()) connection.setAutoCommit(true);
        }
    }

    private void validate(
            Connection connection,
            AuthenticatedBotJob owner,
            VariablesCheckOperandConnectV1.Request request)
            throws SQLException {
        if (!Objects.equals(
                request.contractVersion(), VariablesCheckOperandConnectV1.CONTRACT_VERSION)) {
            throw refused("CHECK_OPERAND_CONTRACT_UNSUPPORTED",
                    "The CheckValue operand contract is not supported.");
        }
        if (request.requestId() == null || request.requestId().trim().isEmpty()) {
            throw refused("CHECK_OPERAND_REQUEST_ID_REQUIRED",
                    "A CheckValue operand request ID is required.");
        }
        if (!Objects.equals(request.workspaceEpoch(), owner.workspaceEpoch())) {
            throw refused("CHECK_OPERAND_WORKSPACE_CHANGED",
                    "The Bot Job workspace changed before the operand connection.");
        }
        GraphState state = stateRepository.loadOrCreate(connection, owner.owner());
        if (!Objects.equals(request.baseGraphVersion(), state.version())) {
            throw refused("CHECK_OPERAND_GRAPH_VERSION_STALE",
                    "The Variables graph version changed before the operand connection.");
        }
        String revision = currentRevision(connection, owner.owner());
        if (request.graphRevision() == null
                || !request.graphRevision().trim().equals(revision)) {
            throw refused("CHECK_OPERAND_GRAPH_REVISION_STALE",
                    "The Variables graph changed before the operand connection.");
        }
        if (request.isUpdateOperator()) {
            String operator = request.comparisonOperator() == null
                    ? "" : request.comparisonOperator().trim();
            if (!BINARY_COMPARISON_OPERATORS.contains(operator)) {
                throw refused("CHECK_OPERAND_OPERATOR_INVALID",
                        "Select a supported comparison operator.");
            }
            return;
        }
        if (!request.isRelease()
                && (request.rightVariableId() == null
                        || request.rightVariableId() <= 0
                        || !variableExists(connection, owner.owner(), request.rightVariableId()))) {
            throw refused("CHECK_OPERAND_VARIABLE_INVALID",
                    "Select a current Bot Job variable for the second comparison value.");
        }
    }

    /**
     * Updates ONLY the stored comparison operator (2026-08-04 middle-shim dropdown).
     * Never touches operand_kind/operand_variable_id - connectivity is left exactly as
     * it was. Upserts because a CheckValue may not have a config row yet.
     */
    private static boolean updateComparisonOperator(
            Connection connection,
            OwnerKey owner,
            int instructionId,
            String actions,
            String comparisonOperator,
            Timestamp now)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE instruction_variable_command_config SET comparison_operator=?,"
                        + "config_revision=config_revision+1,updated_at=?"
                        + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=?")) {
            update.setString(1, comparisonOperator);
            update.setTimestamp(2, now);
            update.setInt(3, owner.homeBankingId());
            update.setInt(4, owner.ownerId());
            update.setInt(5, instructionId);
            if (update.executeUpdate() > 0) return true;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO instruction_variable_command_config"
                        + " (home_banking_id,bot_job_id,instruction_id,command_type,"
                        + "operand_kind,comparison_operator,config_revision,created_at,updated_at)"
                        + " VALUES (?,?,?,?,'VOID',?,1,?,?)")) {
            insert.setInt(1, owner.homeBankingId());
            insert.setInt(2, owner.ownerId());
            insert.setInt(3, instructionId);
            insert.setString(4, CommandRegistry.canonicalize(actions));
            insert.setString(5, comparisonOperator);
            insert.setTimestamp(6, now);
            insert.setTimestamp(7, now);
            insert.executeUpdate();
            return true;
        }
    }

    /** Clears an OCCUPIED config RIGHT spot (VARIABLE -> VOID); returns whether a write happened. */
    private static boolean releaseConfigRightSpot(
            Connection connection, OwnerKey owner, int instructionId, Timestamp now)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE instruction_variable_command_config SET operand_kind='VOID',"
                        + "operand_variable_id=NULL,config_revision=config_revision+1,updated_at=?"
                        + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=?"
                        + " AND operand_kind='VARIABLE'")) {
            update.setTimestamp(1, now);
            update.setInt(2, owner.homeBankingId());
            update.setInt(3, owner.ownerId());
            update.setInt(4, instructionId);
            return update.executeUpdate() > 0;
        }
    }

    /** Deletes the RIGHT slot row when present; returns whether a write happened. */
    private static boolean releaseSlotRightSpot(
            Connection connection, OwnerKey owner, int instructionId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + M20260803_InstructionVariableSlot.TABLE
                        + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=?"
                        + " AND slot=?")) {
            delete.setInt(1, owner.homeBankingId());
            delete.setInt(2, owner.ownerId());
            delete.setInt(3, instructionId);
            delete.setString(4, M20260803_InstructionVariableSlot.SLOT_RIGHT);
            return delete.executeUpdate() > 0;
        }
    }

    private static List<Integer> sanitizedIds(List<Integer> submitted) {
        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        for (Integer id : submitted) {
            if (id != null && id > 0) unique.add(id);
        }
        return List.copyOf(unique);
    }

    private static String instructionActions(
            Connection connection, int botJobId, int instructionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT actions FROM instruction WHERE bot_job_id=? AND id=?")) {
            statement.setInt(1, botJobId);
            statement.setInt(2, instructionId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    /** Fills the config RIGHT spot only when it is free; returns whether a write happened. */
    private static boolean fillConfigRightSpot(
            Connection connection,
            OwnerKey owner,
            int instructionId,
            String actions,
            int rightVariableId,
            Timestamp now)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE instruction_variable_command_config SET operand_kind='VARIABLE',"
                        + "operand_variable_id=?,config_revision=config_revision+1,updated_at=?"
                        + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=?"
                        + " AND (operand_kind IS NULL OR operand_kind<>'VARIABLE'"
                        + " OR operand_variable_id IS NULL)")) {
            update.setInt(1, rightVariableId);
            update.setTimestamp(2, now);
            update.setInt(3, owner.homeBankingId());
            update.setInt(4, owner.ownerId());
            update.setInt(5, instructionId);
            if (update.executeUpdate() > 0) return true;
        }
        if (configRowExists(connection, owner, instructionId)) return false;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO instruction_variable_command_config"
                        + " (home_banking_id,bot_job_id,instruction_id,command_type,"
                        + "operand_kind,operand_variable_id,config_revision,created_at,updated_at)"
                        + " VALUES (?,?,?,?,'VARIABLE',?,1,?,?)")) {
            insert.setInt(1, owner.homeBankingId());
            insert.setInt(2, owner.ownerId());
            insert.setInt(3, instructionId);
            insert.setString(4, CommandRegistry.canonicalize(actions));
            insert.setInt(5, rightVariableId);
            insert.setTimestamp(6, now);
            insert.setTimestamp(7, now);
            insert.executeUpdate();
            return true;
        }
    }

    private static boolean configRowExists(
            Connection connection, OwnerKey owner, int instructionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM instruction_variable_command_config"
                        + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=?")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            statement.setInt(3, instructionId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    /** Inserts the RIGHT slot row only when absent; returns whether a write happened. */
    private static boolean fillSlotRightSpot(
            Connection connection,
            OwnerKey owner,
            int instructionId,
            int rightVariableId,
            Timestamp now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM " + M20260803_InstructionVariableSlot.TABLE
                        + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=?"
                        + " AND slot=?")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            statement.setInt(3, instructionId);
            statement.setString(4, M20260803_InstructionVariableSlot.SLOT_RIGHT);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) return false;
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + M20260803_InstructionVariableSlot.TABLE
                        + " (home_banking_id,bot_job_id,instruction_id,slot,variable_id,"
                        + "slot_revision,created_at,updated_at) VALUES (?,?,?,?,?,1,?,?)")) {
            insert.setInt(1, owner.homeBankingId());
            insert.setInt(2, owner.ownerId());
            insert.setInt(3, instructionId);
            insert.setString(4, M20260803_InstructionVariableSlot.SLOT_RIGHT);
            insert.setInt(5, rightVariableId);
            insert.setTimestamp(6, now);
            insert.setTimestamp(7, now);
            insert.executeUpdate();
            return true;
        }
    }

    private static boolean variableExists(
            Connection connection, OwnerKey owner, int variableId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM bot_job_variable_definition"
                        + " WHERE home_banking_id=? AND bot_job_id=? AND id=?")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            statement.setInt(3, variableId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private String currentRevision(Connection connection, OwnerKey owner) throws SQLException {
        List<InstructionLoad> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,instruction_order_number,actions,operation,on_hold_seconds,block_id,"
                        + "variable_id,parent_block_id,parent_id FROM instruction"
                        + " WHERE bot_job_id=? ORDER BY block_id,instruction_order_number,id")) {
            statement.setInt(1, owner.ownerId());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    InstructionLoad row = new InstructionLoad();
                    row.setId(result.getInt("id"));
                    row.setInstructionOrderNumber(result.getInt("instruction_order_number"));
                    row.setActions(result.getString("actions"));
                    row.setOperation(result.getString("operation"));
                    row.setOnHoldSeconds(nullableInteger(result, "on_hold_seconds"));
                    row.setBlockId(result.getInt("block_id"));
                    row.setVariableId(nullableInteger(result, "variable_id"));
                    row.setParentBlockId(nullableInteger(result, "parent_block_id"));
                    row.setParentId(nullableInteger(result, "parent_id"));
                    rows.add(row);
                }
            }
        }
        List<VariableLoadDTO> variables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,producer_instruction_id FROM bot_job_variable_definition"
                        + " WHERE home_banking_id=? AND bot_job_id=? ORDER BY id")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    variables.add(new VariableLoadDTO(
                            result.getInt("id"), null, owner.ownerId(),
                            nullableInteger(result, "producer_instruction_id"),
                            null, null, null, null, null, 0));
                }
            }
        }
        return revisionService.revision(rows, variables);
    }

    private void requireOwner(Connection connection, OwnerKey owner) throws SQLException {
        if (owner.workspaceKind() != WorkspaceKind.BOT_JOB) {
            throw refused("CHECK_OPERAND_BOT_JOB_REQUIRED",
                    "The operand connection requires a Bot Job owner.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT home_banking_id FROM bot_job WHERE id=?")) {
            statement.setInt(1, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt(1) != owner.homeBankingId()) {
                    throw refused("CHECK_OPERAND_OWNER_MISMATCH",
                            "The selected commands do not belong to the active Bot Job.");
                }
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet tables = connection.getMetaData()
                .getTables(null, null, null, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }

    private static Integer nullableInteger(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static MutationRefusedException refused(String code, String message) {
        return new MutationRefusedException(code, message);
    }

    public record ConnectResult(
            OwnerKey owner,
            long workspaceEpoch,
            String requestId,
            int rightVariableId,
            int connectedCount,
            int skippedCount) {}
}
