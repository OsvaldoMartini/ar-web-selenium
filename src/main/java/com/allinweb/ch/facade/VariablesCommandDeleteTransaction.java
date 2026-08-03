package com.allinweb.ch.facade;

import com.allinweb.ch.db.InstructionGraphStateRepository;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceResult;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceStatus;
import com.allinweb.ch.db.InstructionGraphStateRepository.GraphState;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.model.InstructionGraphMutationV3.WorkspaceKind;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.VariableLoadDTO;
import com.allinweb.ch.model.VariablesWorkspaceCommandDelete;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deletes the React-authored Variables-page command scope and disconnects only direct ID links.
 * Selecting an IF-family boundary sends the complete family via familyDeleteInstructionIds
 * (FE owns the rule — 2026-08-03); Java persists the list atomically. Positional body commands
 * and variable definitions are deliberately preserved.
 */
public final class VariablesCommandDeleteTransaction {
    private final InstructionGraphStateRepository stateRepository =
            new InstructionGraphStateRepository();
    private final InstructionGraphRevisionService revisionService =
            new InstructionGraphRevisionService();

    public DeleteResult execute(
            Connection connection,
            AuthenticatedBotJob owner,
            VariablesWorkspaceCommandDelete.Request request)
            throws SQLException {
        requireOpen(connection);
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");
        if (!connection.getAutoCommit()) {
            throw new SQLException("Variables command deletion requires an unbound connection.");
        }
        connection.setAutoCommit(false);
        boolean restoreAutoCommit = true;
        try {
            requireOwner(connection, owner.owner());
            GraphState state = stateRepository.loadOrCreate(connection, owner.owner());
            Graph before = loadGraph(connection, owner.owner(), state);
            Plan plan = validateAndPlan(owner, request, before);

            int disconnectedInstructions = clearParentLinks(
                    connection, owner.owner().ownerId(), plan);
            int disconnectedVariables = clearVariableOwners(
                    connection, owner.owner(), plan);
            for (int index = plan.deleteInstructionIds().size() - 1; index >= 0; index--) {
                int deleteId = plan.deleteInstructionIds().get(index);
                deleteOwnedRows(connection, owner.owner(), deleteId);
                deleteInstruction(connection, owner.owner().ownerId(), deleteId);
            }
            for (Integer blockId : plan.affectedBlockIds()) {
                normalizeBlockOrder(connection, owner.owner().ownerId(), blockId);
            }

            AdvanceResult advance = stateRepository.compareAndSetIncrement(
                    connection, owner.owner(), request.baseGraphVersion());
            if (!advance.advanced()) {
                throw refused(
                        advance.status() == AdvanceStatus.MISSING
                                ? "COMMAND_DELETE_GRAPH_STATE_MISSING"
                                : "COMMAND_DELETE_GRAPH_VERSION_STALE",
                        "The Variables graph changed before command deletion completed.");
            }
            Graph after = loadGraph(connection, owner.owner(), advance.state());
            verify(plan, after, disconnectedInstructions, disconnectedVariables);
            connection.commit();
            return new DeleteResult(
                    owner.owner(), owner.workspaceEpoch(), request.requestId().trim(),
                    plan.source().id(), disconnectedInstructions, disconnectedVariables,
                    state.version(), advance.state().version(), after.revision());
        } catch (SQLException | RuntimeException failure) {
            if (!rollback(connection, failure)) {
                restoreAutoCommit = false;
                close(connection, failure);
            }
            throw failure;
        } finally {
            if (restoreAutoCommit) restoreAutoCommit(connection);
        }
    }

    private Plan validateAndPlan(
            AuthenticatedBotJob owner,
            VariablesWorkspaceCommandDelete.Request request,
            Graph graph)
            throws MutationRefusedException {
        if (!Objects.equals(
                request.contractVersion(), VariablesWorkspaceCommandDelete.CONTRACT_VERSION)) {
            throw refused("COMMAND_DELETE_CONTRACT_UNSUPPORTED",
                    "The command-delete contract is not supported.");
        }
        if (blank(request.requestId())) {
            throw refused("COMMAND_DELETE_REQUEST_ID_REQUIRED",
                    "A command-delete request ID is required.");
        }
        if (!Objects.equals(request.workspaceEpoch(), owner.workspaceEpoch())) {
            throw refused("COMMAND_DELETE_WORKSPACE_CHANGED",
                    "The Bot Job workspace changed before command deletion.");
        }
        if (!Objects.equals(request.baseGraphVersion(), graph.state().version())) {
            throw refused("COMMAND_DELETE_GRAPH_VERSION_STALE",
                    "The Variables graph version changed before command deletion.");
        }
        if (blank(request.graphRevision())
                || !request.graphRevision().trim().equals(graph.revision())) {
            throw refused("COMMAND_DELETE_GRAPH_REVISION_STALE",
                    "The Variables graph changed before command deletion.");
        }
        InstructionRow source = graph.instructions().get(request.instructionId());
        if (source == null) {
            throw refused("COMMAND_DELETE_SOURCE_MISSING",
                    "The selected command no longer exists.");
        }
        if (!Objects.equals(request.expectedBlockId(), source.blockId())
                || !Objects.equals(request.expectedInstructionOrder(), source.order())) {
            throw refused("COMMAND_DELETE_SOURCE_CHANGED",
                    "The selected command moved before deletion. Review it and retry.");
        }

        // FE owns the IF-family delete scope (2026-08-03): the complete boundary list arrives in
        // familyDeleteInstructionIds. Persistence-grade sanitation only — drop nulls, non-positive
        // ids, duplicates, and the source itself; a vanished row still refuses and rolls back.
        LinkedHashSet<Integer> deleteIdSet = new LinkedHashSet<>();
        deleteIdSet.add(source.id());
        for (Integer familyId : request.familyDeleteInstructionIds()) {
            if (familyId == null || familyId <= 0 || familyId == source.id()) continue;
            if (graph.instructions().get(familyId) == null) {
                throw refused("COMMAND_DELETE_SOURCE_CHANGED",
                        "An IF-family boundary changed before deletion. Review it and retry.");
            }
            deleteIdSet.add(familyId);
        }
        List<Integer> deleteInstructionIds = List.copyOf(deleteIdSet);
        LinkedHashSet<Integer> affectedBlockIds = new LinkedHashSet<>();
        for (Integer deleteId : deleteInstructionIds) {
            affectedBlockIds.add(graph.instructions().get(deleteId).blockId());
        }

        List<Integer> expectedParentRepairs = graph.instructions().values().stream()
                .filter(row -> !deleteIdSet.contains(row.id()))
                .filter(row -> row.parentId() != null && deleteIdSet.contains(row.parentId()))
                .map(InstructionRow::id)
                .sorted()
                .toList();
        List<Integer> submittedParentRepairs = exactIds(
                request.parentRepairInstructionIds(), "parent repair");
        if (!expectedParentRepairs.equals(submittedParentRepairs)) {
            throw refused("COMMAND_DELETE_PARENT_PLAN_CHANGED",
                    "Direct command connections changed before deletion. Review and retry.");
        }

        List<Integer> expectedVariableOwners = graph.variables().entrySet().stream()
                .filter(entry -> entry.getValue() != null && deleteIdSet.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        List<Integer> submittedVariableOwners = exactIds(
                request.variableOwnerIds(), "variable owner");
        if (!expectedVariableOwners.equals(submittedVariableOwners)) {
            throw refused("COMMAND_DELETE_VARIABLE_PLAN_CHANGED",
                    "Variable owner connections changed before deletion. Review and retry.");
        }
        return new Plan(
                source, deleteInstructionIds, List.copyOf(affectedBlockIds),
                submittedParentRepairs, submittedVariableOwners);
    }

    private static List<Integer> exactIds(List<Integer> values, String label)
            throws MutationRefusedException {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        for (Integer value : values == null ? List.<Integer>of() : values) {
            if (value == null || value <= 0 || !ids.add(value)) {
                throw refused("COMMAND_DELETE_PLAN_INVALID",
                        "Every " + label + " ID must be unique and positive.");
            }
        }
        return ids.stream().sorted().toList();
    }

    private int clearParentLinks(Connection connection, int botJobId, Plan plan)
            throws SQLException {
        if (plan.parentRepairInstructionIds().isEmpty()) return 0;
        String placeholders = String.join(
                ",", Collections.nCopies(plan.deleteInstructionIds().size(), "?"));
        int updated = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE instruction SET parent_id=NULL,parent_block_id=NULL"
                        + " WHERE bot_job_id=? AND id=? AND parent_id IN (" + placeholders + ")")) {
            for (Integer instructionId : plan.parentRepairInstructionIds()) {
                statement.setInt(1, botJobId);
                statement.setInt(2, instructionId);
                int position = 3;
                for (Integer deleteId : plan.deleteInstructionIds()) {
                    statement.setInt(position++, deleteId);
                }
                updated += statement.executeUpdate();
            }
        }
        if (updated != plan.parentRepairInstructionIds().size()) {
            throw refused("COMMAND_DELETE_PARENT_CLEAR_FAILED",
                    "A direct command connection changed before it could be disconnected.");
        }
        return updated;
    }

    private int clearVariableOwners(Connection connection, OwnerKey owner, Plan plan)
            throws SQLException {
        if (plan.variableOwnerIds().isEmpty()) return 0;
        String placeholders = String.join(
                ",", Collections.nCopies(plan.deleteInstructionIds().size(), "?"));
        int updated = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE bot_job_variable_definition SET producer_instruction_id=NULL,"
                        + "updated_at=CURRENT_TIMESTAMP WHERE home_banking_id=? AND bot_job_id=?"
                        + " AND id=? AND producer_instruction_id IN (" + placeholders + ")")) {
            for (Integer variableId : plan.variableOwnerIds()) {
                statement.setInt(1, owner.homeBankingId());
                statement.setInt(2, owner.ownerId());
                statement.setInt(3, variableId);
                int position = 4;
                for (Integer deleteId : plan.deleteInstructionIds()) {
                    statement.setInt(position++, deleteId);
                }
                updated += statement.executeUpdate();
            }
        }
        if (updated != plan.variableOwnerIds().size()) {
            throw refused("COMMAND_DELETE_VARIABLE_CLEAR_FAILED",
                    "A variable owner connection changed before it could be disconnected.");
        }
        return updated;
    }

    private void deleteOwnedRows(Connection connection, OwnerKey owner, int instructionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM reference WHERE bot_job_id=? AND instruction_id=?")) {
            statement.setInt(1, owner.ownerId());
            statement.setInt(2, instructionId);
            statement.executeUpdate();
        }
        if (tableExists(connection, "instruction_variable_command_config")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM instruction_variable_command_config"
                            + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=?")) {
                statement.setInt(1, owner.homeBankingId());
                statement.setInt(2, owner.ownerId());
                statement.setInt(3, instructionId);
                statement.executeUpdate();
            }
        }
        if (tableExists(connection, "use_case_field_mapping")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM use_case_field_mapping"
                            + " WHERE bot_job_id=? AND bot_instruction_id=?")) {
                statement.setInt(1, owner.ownerId());
                statement.setInt(2, instructionId);
                statement.executeUpdate();
            }
        }
    }

    private void deleteInstruction(Connection connection, int botJobId, int instructionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM instruction WHERE bot_job_id=? AND id=?")) {
            statement.setInt(1, botJobId);
            statement.setInt(2, instructionId);
            if (statement.executeUpdate() != 1) {
                throw refused("COMMAND_DELETE_SOURCE_CHANGED",
                        "The selected command changed before it could be deleted.");
            }
        }
    }

    private void normalizeBlockOrder(Connection connection, int botJobId, int blockId)
            throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM instruction WHERE bot_job_id=? AND block_id=?"
                        + " ORDER BY instruction_order_number,id")) {
            statement.setInt(1, botJobId);
            statement.setInt(2, blockId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) ids.add(rows.getInt("id"));
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE instruction SET instruction_order_number=?"
                        + " WHERE bot_job_id=? AND id=?")) {
            for (int index = 0; index < ids.size(); index++) {
                statement.setInt(1, index + 1);
                statement.setInt(2, botJobId);
                statement.setInt(3, ids.get(index));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void verify(
            Plan plan,
            Graph after,
            int disconnectedInstructions,
            int disconnectedVariables)
            throws MutationRefusedException {
        for (Integer deleteId : plan.deleteInstructionIds()) {
            if (after.instructions().containsKey(deleteId)) {
                throw refused("COMMAND_DELETE_VERIFICATION_FAILED",
                        "A deleted command still exists after deletion.");
            }
        }
        Set<Integer> deleteIdSet = new LinkedHashSet<>(plan.deleteInstructionIds());
        if (disconnectedInstructions != plan.parentRepairInstructionIds().size()
                || after.instructions().values().stream()
                        .anyMatch(row -> row.parentId() != null
                                && deleteIdSet.contains(row.parentId()))
                || plan.parentRepairInstructionIds().stream()
                        .map(after.instructions()::get)
                        .filter(Objects::nonNull)
                        .anyMatch(row -> row.parentId() != null || row.parentBlockId() != null)) {
            throw refused("COMMAND_DELETE_PARENT_VERIFICATION_FAILED",
                    "A direct command connection was not disconnected.");
        }
        if (disconnectedVariables != plan.variableOwnerIds().size()
                || after.variables().values().stream()
                        .anyMatch(ownerId -> ownerId != null && deleteIdSet.contains(ownerId))) {
            throw refused("COMMAND_DELETE_VARIABLE_VERIFICATION_FAILED",
                    "A variable owner connection was not disconnected.");
        }
        for (Integer blockId : plan.affectedBlockIds()) {
            List<InstructionRow> survivingBlock = after.instructions().values().stream()
                    .filter(row -> row.blockId() == blockId)
                    .sorted(Comparator.comparingInt(InstructionRow::order))
                    .toList();
            for (int index = 0; index < survivingBlock.size(); index++) {
                if (survivingBlock.get(index).order() != index + 1) {
                    throw refused("COMMAND_DELETE_ORDER_VERIFICATION_FAILED",
                            "The surviving command order is not contiguous.");
                }
            }
        }
    }

    private Graph loadGraph(Connection connection, OwnerKey owner, GraphState state)
            throws SQLException {
        LinkedHashMap<Integer, InstructionRow> instructions = new LinkedHashMap<>();
        List<InstructionLoad> revisionRows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,instruction_order_number,actions,operation,on_hold_seconds,block_id,"
                        + "variable_id,parent_block_id,parent_id FROM instruction"
                        + " WHERE bot_job_id=? ORDER BY block_id,instruction_order_number,id")) {
            statement.setInt(1, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    InstructionRow row = new InstructionRow(
                            rows.getInt("id"), rows.getInt("instruction_order_number"),
                            rows.getString("actions"), rows.getString("operation"),
                            nullableInteger(rows, "on_hold_seconds"), rows.getInt("block_id"),
                            nullableInteger(rows, "variable_id"),
                            nullableInteger(rows, "parent_block_id"),
                            nullableInteger(rows, "parent_id"));
                    instructions.put(row.id(), row);
                    revisionRows.add(row.asRevisionRow());
                }
            }
        }
        LinkedHashMap<Integer, Integer> variables = new LinkedHashMap<>();
        List<VariableLoadDTO> revisionVariables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,producer_instruction_id FROM bot_job_variable_definition"
                        + " WHERE home_banking_id=? AND bot_job_id=? ORDER BY id")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int id = rows.getInt("id");
                    Integer producerId = nullableInteger(rows, "producer_instruction_id");
                    variables.put(id, producerId);
                    revisionVariables.add(new VariableLoadDTO(
                            id, null, owner.ownerId(), producerId,
                            null, null, null, null, null, 0));
                }
            }
        }
        return new Graph(
                state,
                Collections.unmodifiableMap(new LinkedHashMap<>(instructions)),
                Collections.unmodifiableMap(new LinkedHashMap<>(variables)),
                revisionService.revision(revisionRows, revisionVariables));
    }

    private void requireOwner(Connection connection, OwnerKey owner) throws SQLException {
        if (owner.workspaceKind() != WorkspaceKind.BOT_JOB) {
            throw refused("COMMAND_DELETE_BOT_JOB_REQUIRED",
                    "Command deletion requires a Bot Job owner.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT home_banking_id FROM bot_job WHERE id=?")) {
            statement.setInt(1, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt(1) != owner.homeBankingId()) {
                    throw refused("COMMAND_DELETE_OWNER_MISMATCH",
                            "The selected command does not belong to the active Bot Job.");
                }
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(null, null, null, new String[] {"TABLE"})) {
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

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static MutationRefusedException refused(String code, String message) {
        return new MutationRefusedException(code, message);
    }

    private static void requireOpen(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Variables command deletion requires an open connection.");
        }
    }

    private static boolean rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
            return true;
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
            return false;
        }
    }

    private static void restoreAutoCommit(Connection connection) throws SQLException {
        if (!connection.isClosed()) connection.setAutoCommit(true);
    }

    private static void close(Connection connection, Throwable failure) {
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private record Plan(
            InstructionRow source,
            List<Integer> deleteInstructionIds,
            List<Integer> affectedBlockIds,
            List<Integer> parentRepairInstructionIds,
            List<Integer> variableOwnerIds) {}

    private record Graph(
            GraphState state,
            Map<Integer, InstructionRow> instructions,
            Map<Integer, Integer> variables,
            String revision) {}

    private record InstructionRow(
            int id,
            int order,
            String actions,
            String operation,
            Integer onHoldSeconds,
            int blockId,
            Integer variableId,
            Integer parentBlockId,
            Integer parentId) {

        private InstructionLoad asRevisionRow() {
            InstructionLoad row = new InstructionLoad();
            row.setId(id);
            row.setInstructionOrderNumber(order);
            row.setActions(actions);
            row.setOperation(operation);
            row.setOnHoldSeconds(onHoldSeconds);
            row.setBlockId(blockId);
            row.setVariableId(variableId);
            row.setParentBlockId(parentBlockId);
            row.setParentId(parentId);
            return row;
        }
    }

    public record DeleteResult(
            OwnerKey owner,
            long workspaceEpoch,
            String requestId,
            int instructionId,
            int disconnectedInstructionCount,
            int disconnectedVariableCount,
            long previousGraphVersion,
            long committedGraphVersion,
            String graphRevision) {}
}
