package com.allinweb.ch.facade;

import com.allinweb.ch.db.InstructionGraphStateRepository;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceResult;
import com.allinweb.ch.db.InstructionGraphStateRepository.GraphState;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.db.migrations.M20260803_InstructionVariableSlot;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.model.InstructionGraphMutationV3.WorkspaceKind;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.VariableLoadDTO;
import com.allinweb.ch.model.VariablesCheckLeftOperandV1;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Independent persistence transaction for exactly one CheckValue LEFT slot. */
public final class VariablesCheckLeftOperandTransaction {
    private static final Set<String> CHECK_ACTIONS =
            Set.of("CK", "CHECKVALUE", "PDF CHECK", "CSV CHECK");
    private final InstructionGraphStateRepository stateRepository =
            new InstructionGraphStateRepository();
    private final InstructionGraphRevisionService revisionService =
            new InstructionGraphRevisionService();

    public Result execute(
            Connection connection,
            AuthenticatedBotJob owner,
            VariablesCheckLeftOperandV1.Request request)
            throws SQLException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");
        if (connection == null || connection.isClosed()) {
            throw new SQLException("CheckValue LEFT mutation requires an open connection.");
        }
        if (!connection.getAutoCommit()) {
            throw new SQLException("CheckValue LEFT mutation requires an unbound connection.");
        }
        connection.setAutoCommit(false);
        boolean restoreAutoCommit = true;
        try {
            requireOwner(connection, owner.owner());
            validate(connection, owner, request);
            VariablesCheckLeftOperandV1.VariableBindingPatch patch = request.patch();
            int instructionId = patch.instructionId();
            String actions = instructionActions(
                    connection, owner.owner().ownerId(), instructionId);
            if (actions == null || !isCheckValue(actions)) {
                throw refused("CHECK_LEFT_INSTRUCTION_INVALID",
                        "LEFT can be changed only for a CheckValue command.");
            }
            Timestamp now = new Timestamp(System.currentTimeMillis());
            boolean changed = request.isDisconnect()
                    ? deleteLeftSlot(connection, owner.owner(), instructionId)
                    : connectLeftSlot(
                            connection, owner.owner(), instructionId,
                            patch.replacement().value(), now);
            long committedGraphVersion = request.baseGraphVersion();
            String committedGraphRevision = request.graphRevision().trim();
            if (changed) {
                AdvanceResult advanced = stateRepository.compareAndSetIncrement(
                        connection, owner.owner(), request.baseGraphVersion());
                if (!advanced.advanced()) {
                    throw refused("CHECK_LEFT_GRAPH_VERSION_STALE",
                            "The Variables graph changed before the LEFT connection committed.");
                }
                committedGraphVersion = advanced.state().version();
                committedGraphRevision = currentRevision(connection, owner.owner());
            }
            connection.commit();
            return new Result(
                    owner.owner(), owner.workspaceEpoch(), request.requestId().trim(),
                    instructionId,
                    patch.replacement() == null ? null : patch.replacement().value(), changed,
                    committedGraphVersion, committedGraphRevision);
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
            VariablesCheckLeftOperandV1.Request request)
            throws SQLException {
        if (!Objects.equals(
                request.contractVersion(), VariablesCheckLeftOperandV1.CONTRACT_VERSION)) {
            throw refused("CHECK_LEFT_CONTRACT_UNSUPPORTED",
                    "The CheckValue LEFT contract is not supported.");
        }
        if (request.requestId() == null || request.requestId().trim().isEmpty()) {
            throw refused("CHECK_LEFT_REQUEST_ID_REQUIRED", "A LEFT request ID is required.");
        }
        VariablesCheckLeftOperandV1.VariableBindingPatch patch = request.patch();
        if (patch == null || patch.instructionId() == null || patch.instructionId() <= 0) {
            throw refused("CHECK_LEFT_INSTRUCTION_REQUIRED",
                    "One CheckValue instruction ID is required.");
        }
        if (!"LEFT".equalsIgnoreCase(patch.slot())) {
            throw refused("CHECK_LEFT_SLOT_INVALID",
                    "graphMutationLeft accepts only slot LEFT.");
        }
        if (!request.isConnect() && !request.isDisconnect()) {
            throw refused("CHECK_LEFT_OPERATION_INVALID",
                    "LEFT supports only CONNECT or DISCONNECT.");
        }
        Integer expectedVariableId = patch.expected() == null
                ? null : patch.expected().value();
        Integer currentVariableId = currentLeftSlot(
                connection, owner.owner(), patch.instructionId());
        if (!Objects.equals(expectedVariableId, currentVariableId)) {
            throw refused("CHECK_LEFT_EXPECTED_MISMATCH",
                    "The CheckValue LEFT connection changed before this request.");
        }
        if (!Objects.equals(request.workspaceEpoch(), owner.workspaceEpoch())) {
            throw refused("CHECK_LEFT_WORKSPACE_CHANGED",
                    "The Bot Job workspace changed before the LEFT mutation.");
        }
        GraphState state = stateRepository.loadOrCreate(connection, owner.owner());
        if (!Objects.equals(request.baseGraphVersion(), state.version())) {
            throw refused("CHECK_LEFT_GRAPH_VERSION_STALE",
                    "The Variables graph version changed before the LEFT mutation.");
        }
        String revision = currentRevision(connection, owner.owner());
        if (request.graphRevision() == null
                || !request.graphRevision().trim().equals(revision)) {
            throw refused("CHECK_LEFT_GRAPH_REVISION_STALE",
                    "The Variables graph changed before the LEFT mutation.");
        }
        if (!request.isDisconnect()
                && (patch.replacement() == null
                        || patch.replacement().value() == null
                        || patch.replacement().value() <= 0
                        || !variableExists(
                                connection, owner.owner(), patch.replacement().value()))) {
            throw refused("CHECK_LEFT_VARIABLE_INVALID",
                    "Select a current Bot Job variable for the LEFT operand.");
        }
    }

    private static boolean connectLeftSlot(
            Connection connection,
            OwnerKey owner,
            int instructionId,
            int variableId,
            Timestamp now)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + M20260803_InstructionVariableSlot.TABLE
                        + " SET variable_id=?,slot_revision=slot_revision+1,updated_at=?"
                        + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=?"
                        + " AND slot=?")) {
            update.setInt(1, variableId);
            update.setTimestamp(2, now);
            update.setInt(3, owner.homeBankingId());
            update.setInt(4, owner.ownerId());
            update.setInt(5, instructionId);
            update.setString(6, M20260803_InstructionVariableSlot.SLOT_LEFT);
            if (update.executeUpdate() > 0) return true;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + M20260803_InstructionVariableSlot.TABLE
                        + " (home_banking_id,bot_job_id,instruction_id,slot,variable_id,"
                        + "slot_revision,created_at,updated_at) VALUES (?,?,?,?,?,1,?,?)")) {
            insert.setInt(1, owner.homeBankingId());
            insert.setInt(2, owner.ownerId());
            insert.setInt(3, instructionId);
            insert.setString(4, M20260803_InstructionVariableSlot.SLOT_LEFT);
            insert.setInt(5, variableId);
            insert.setTimestamp(6, now);
            insert.setTimestamp(7, now);
            insert.executeUpdate();
            return true;
        }
    }

    private static boolean deleteLeftSlot(
            Connection connection, OwnerKey owner, int instructionId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + M20260803_InstructionVariableSlot.TABLE
                        + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=?"
                        + " AND slot=?")) {
            delete.setInt(1, owner.homeBankingId());
            delete.setInt(2, owner.ownerId());
            delete.setInt(3, instructionId);
            delete.setString(4, M20260803_InstructionVariableSlot.SLOT_LEFT);
            return delete.executeUpdate() > 0;
        }
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

    private static Integer currentLeftSlot(
            Connection connection, OwnerKey owner, int instructionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT variable_id FROM " + M20260803_InstructionVariableSlot.TABLE
                        + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=?"
                        + " AND slot=?")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            statement.setInt(3, instructionId);
            statement.setString(4, M20260803_InstructionVariableSlot.SLOT_LEFT);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? nullableInteger(rows, "variable_id") : null;
            }
        }
    }

    private static boolean isCheckValue(String actions) {
        String canonical = CommandRegistry.canonicalize(actions);
        return canonical != null && CHECK_ACTIONS.contains(canonical.toUpperCase());
    }

    private String currentRevision(Connection connection, OwnerKey owner) throws SQLException {
        List<InstructionLoad> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,instruction_order_number,actions,operation,on_hold_seconds,block_id,"
                        + "(SELECT ivs.variable_id FROM instruction_variable_slot ivs"
                        + " WHERE ivs.home_banking_id=? AND ivs.bot_job_id=instruction.bot_job_id"
                        + " AND ivs.instruction_id=instruction.id"
                        + " AND ivs.slot=CASE UPPER(TRIM(instruction.actions))"
                        + " WHEN 'CK' THEN 'LEFT' WHEN 'CHECKVALUE' THEN 'LEFT'"
                        + " WHEN 'CSV CHECK' THEN 'LEFT' WHEN 'PDF CHECK' THEN 'LEFT'"
                        + " WHEN 'GET' THEN 'GET_WRITE' WHEN 'SET' THEN 'READ_SET'"
                        + " WHEN 'E' THEN 'READ' ELSE NULL END LIMIT 1) AS variable_id,"
                        + "parent_block_id,parent_id FROM instruction"
                        + " WHERE bot_job_id=? ORDER BY block_id,instruction_order_number,id")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
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

    private static void requireOwner(Connection connection, OwnerKey owner) throws SQLException {
        if (owner.workspaceKind() != WorkspaceKind.BOT_JOB) {
            throw refused("CHECK_LEFT_BOT_JOB_REQUIRED", "LEFT requires a Bot Job owner.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT home_banking_id FROM bot_job WHERE id=?")) {
            statement.setInt(1, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt(1) != owner.homeBankingId()) {
                    throw refused("CHECK_LEFT_OWNER_MISMATCH",
                            "The CheckValue does not belong to the active Bot Job.");
                }
            }
        }
    }

    private static Integer nullableInteger(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static MutationRefusedException refused(String code, String message) {
        return new MutationRefusedException(code, message);
    }

    public record Result(
            OwnerKey owner,
            long workspaceEpoch,
            String requestId,
            int instructionId,
            Integer leftVariableId,
            boolean changed,
            long committedGraphVersion,
            String graphRevision) {}
}
