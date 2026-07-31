package com.allinweb.ch.facade;

import com.allinweb.ch.db.InstructionGraphStateRepository;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceResult;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceStatus;
import com.allinweb.ch.db.InstructionGraphStateRepository.GraphState;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.VariableLoadDTO;
import com.allinweb.ch.model.VariablesWorkspaceVariableDelete;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Atomic Bot Job variable deletion selected entirely by the Variables React workspace.
 *
 * <p>This transaction has no dependency-discovery or cascade-planning logic. It clears
 * {@code instruction.variable_id} only for the submitted variable IDs and deletes exactly those
 * Bot Job-owned variable rows. Instructions, Web Elements, parent relationships, blocks,
 * references, operations, and Component tables are never deleted or rewritten.
 */
public final class VariablesVariableDeleteTransaction {
    private static final com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableService
            RUNTIME_VARIABLES =
                    new com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableService();

    private static final FaultInjector NO_FAULTS = ignored -> {};

    private final InstructionGraphStateRepository stateRepository;
    private final InstructionGraphRevisionService revisionService;
    private final FaultInjector faultInjector;

    public VariablesVariableDeleteTransaction() {
        this(
                new InstructionGraphStateRepository(),
                new InstructionGraphRevisionService(),
                NO_FAULTS);
    }

    VariablesVariableDeleteTransaction(
            InstructionGraphStateRepository stateRepository,
            InstructionGraphRevisionService revisionService,
            FaultInjector faultInjector) {
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.revisionService = Objects.requireNonNull(revisionService, "revisionService");
        this.faultInjector = Objects.requireNonNull(faultInjector, "faultInjector");
    }

    public DeleteResult execute(
            Connection connection,
            AuthenticatedBotJob owner,
            VariablesWorkspaceVariableDelete.Request request)
            throws SQLException {
        requireOpenConnection(connection);
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");
        if (!connection.getAutoCommit()) {
            throw new SQLException(
                    "Variables deletion requires an unbound auto-commit connection.");
        }

        connection.setAutoCommit(false);
        boolean restoreAutoCommit = true;
        try {
            requireOwnedBotJob(connection, owner.owner());
            GraphState graphState = stateRepository.loadOrCreate(connection, owner.owner());
            AuthoritativeGraph before = loadGraph(connection, owner.owner(), graphState);
            DeletePlan plan = validateAndPlan(owner, request, before);

            int expectedBindings = countInstructionBindings(
                    connection, owner.owner().ownerId(), plan.variableIds());
            int clearedBindings = clearInstructionBindings(
                    connection, owner.owner().ownerId(), plan.variableIds());
            if (clearedBindings != expectedBindings) {
                throw new SQLException(
                        "Variable bindings were not cleared exactly once; expected="
                                + expectedBindings
                                + ", actual="
                                + clearedBindings);
            }
            faultInjector.at(TransactionPhase.AFTER_BINDINGS_CLEARED);

            int deletedVariables = deleteVariables(
                    connection, owner.owner(), plan.variableIds());
            if (deletedVariables != plan.variableIds().size()) {
                throw new SQLException(
                        "Variables were not deleted exactly once; expected="
                                + plan.variableIds().size()
                                + ", actual="
                                + deletedVariables);
            }
            faultInjector.at(TransactionPhase.AFTER_VARIABLES_DELETED);

            AdvanceResult advance = stateRepository.compareAndSetIncrement(
                    connection, owner.owner(), request.baseGraphVersion());
            if (!advance.advanced()) {
                throw new MutationRefusedException(
                        advance.status() == AdvanceStatus.MISSING
                                ? "GRAPH_VERSION_STATE_MISSING"
                                : "GRAPH_VERSION_CAS_STALE",
                        "The Variables graph version changed before deletion completed.");
            }
            faultInjector.at(TransactionPhase.AFTER_VERSION_ADVANCE);

            AuthoritativeGraph after =
                    loadGraph(connection, owner.owner(), advance.state());
            verifyFinalState(before, after, plan, expectedBindings);
            faultInjector.at(TransactionPhase.AFTER_FINAL_VERIFICATION);

            connection.commit();
            return new DeleteResult(
                    owner.owner(),
                    owner.workspaceEpoch(),
                    request.requestId().trim(),
                    request.mode(),
                    List.copyOf(plan.variableIds()),
                    deletedVariables,
                    clearedBindings,
                    graphState.version(),
                    advance.state().version(),
                    after.graphRevision());
        } catch (SQLException | RuntimeException failure) {
            if (!rollback(connection, failure)) {
                restoreAutoCommit = false;
                closeAfterRollbackFailure(connection, failure);
            }
            throw failure;
        } finally {
            if (restoreAutoCommit) restoreAutoCommit(connection);
        }
    }

    private DeletePlan validateAndPlan(
            AuthenticatedBotJob owner,
            VariablesWorkspaceVariableDelete.Request request,
            AuthoritativeGraph authoritative)
            throws MutationRefusedException {
        if (!Objects.equals(
                request.contractVersion(),
                VariablesWorkspaceVariableDelete.CONTRACT_VERSION)) {
            throw refused(
                    "VARIABLE_DELETE_CONTRACT_UNSUPPORTED",
                    "The Variables deletion contract version is not supported.");
        }
        if (request.requestId() == null || request.requestId().trim().isEmpty()) {
            throw refused(
                    "VARIABLE_DELETE_REQUEST_ID_REQUIRED",
                    "A Variables deletion request ID is required.");
        }
        if (!Objects.equals(request.workspaceEpoch(), owner.workspaceEpoch())) {
            throw refused(
                    "VARIABLE_DELETE_WORKSPACE_CHANGED",
                    "The Bot Job workspace changed before variable deletion.");
        }
        if (!Objects.equals(
                request.baseGraphVersion(), authoritative.graphState().version())) {
            throw refused(
                    "VARIABLE_DELETE_GRAPH_VERSION_STALE",
                    "The Variables graph version changed before deletion.");
        }
        String requestedRevision =
                request.graphRevision() == null ? "" : request.graphRevision().trim();
        if (requestedRevision.isEmpty()
                || !requestedRevision.equals(authoritative.graphRevision())) {
            throw refused(
                    "VARIABLE_DELETE_GRAPH_REVISION_STALE",
                    "The Variables graph changed before deletion.");
        }
        if (request.mode() == null) {
            throw refused(
                    "VARIABLE_DELETE_MODE_REQUIRED",
                    "Select SINGLE or ALL variable deletion.");
        }

        LinkedHashSet<Integer> requestedIds = new LinkedHashSet<>();
        for (Integer variableId : request.variableIds()) {
            if (variableId == null || variableId <= 0 || !requestedIds.add(variableId)) {
                throw refused(
                        "VARIABLE_DELETE_IDS_INVALID",
                        "Variables deletion requires unique positive variable IDs.");
            }
        }
        if (requestedIds.isEmpty()) {
            throw refused(
                    "VARIABLE_DELETE_IDS_REQUIRED",
                    "Select at least one variable to delete.");
        }
        if (request.mode() == VariablesWorkspaceVariableDelete.Mode.SINGLE
                && requestedIds.size() != 1) {
            throw refused(
                    "VARIABLE_DELETE_SINGLE_REQUIRES_ONE",
                    "SINGLE deletion requires exactly one variable ID.");
        }

        LinkedHashSet<Integer> authoritativeIds =
                new LinkedHashSet<>(authoritative.variableIds());
        if (!authoritativeIds.containsAll(requestedIds)) {
            throw refused(
                    "VARIABLE_DELETE_NOT_OWNED",
                    "One or more selected variables do not belong to the current Bot Job.");
        }
        if (request.mode() == VariablesWorkspaceVariableDelete.Mode.ALL
                && !requestedIds.equals(authoritativeIds)) {
            throw refused(
                    "VARIABLE_DELETE_ALL_INCOMPLETE",
                    "ALL deletion must submit the complete current variable ID catalog.");
        }
        return new DeletePlan(requestedIds);
    }

    private AuthoritativeGraph loadGraph(
            Connection connection, OwnerKey owner, GraphState state)
            throws SQLException {
        int botJobId = owner.ownerId();
        List<InstructionLoad> instructions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,block_id,instruction_order_number,actions,parent_id,"
                        + "parent_block_id,variable_id,operation"
                        + " FROM instruction WHERE bot_job_id=? ORDER BY id")) {
            statement.setInt(1, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    InstructionLoad row = new InstructionLoad();
                    row.setId(rows.getInt("id"));
                    row.setBlockId(nullableInteger(rows, "block_id"));
                    row.setInstructionOrderNumber(
                            nullableInteger(rows, "instruction_order_number"));
                    row.setActions(rows.getString("actions"));
                    row.setParentId(nullableInteger(rows, "parent_id"));
                    row.setParentBlockId(nullableInteger(rows, "parent_block_id"));
                    row.setVariableId(nullableInteger(rows, "variable_id"));
                    row.setOperation(rows.getString("operation"));
                    instructions.add(row);
                }
            }
        }

        List<VariableLoadDTO> variables = new ArrayList<>();
        List<Integer> variableIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,producer_instruction_id AS instruction_id"
                        + " FROM bot_job_variable_definition"
                        + " WHERE home_banking_id=? AND bot_job_id=? ORDER BY id")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int id = rows.getInt("id");
                    Integer instructionId = nullableInteger(rows, "instruction_id");
                    variableIds.add(id);
                    variables.add(new VariableLoadDTO(
                            id,
                            null,
                            botJobId,
                            instructionId,
                            null,
                            null,
                            null,
                            null,
                            null,
                            0));
                }
            }
        }
        return new AuthoritativeGraph(
                state,
                List.copyOf(instructions),
                List.copyOf(variableIds),
                revisionService.revision(instructions, variables));
    }

    private void requireOwnedBotJob(Connection connection, OwnerKey owner)
            throws SQLException {
        if (owner.workspaceKind()
                != com.allinweb.ch.model.InstructionGraphMutationV3.WorkspaceKind.BOT_JOB) {
            throw refused(
                    "VARIABLE_DELETE_BOT_JOB_REQUIRED",
                    "Variables deletion requires a Bot Job owner.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM bot_job WHERE id=? AND home_banking_id=?")) {
            statement.setInt(1, owner.ownerId());
            statement.setInt(2, owner.homeBankingId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw refused(
                            "VARIABLE_DELETE_OWNER_MISMATCH",
                            "The authenticated organization does not own this Bot Job.");
                }
            }
        }
    }

    private int countInstructionBindings(
            Connection connection, int botJobId, Set<Integer> variableIds)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM instruction WHERE bot_job_id=? AND variable_id IN ("
                        + placeholders(variableIds.size())
                        + ")")) {
            statement.setInt(1, botJobId);
            bindIds(statement, variableIds, 2);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Variable binding count was not returned.");
                }
                return rows.getInt(1);
            }
        }
    }

    private int clearInstructionBindings(
            Connection connection, int botJobId, Set<Integer> variableIds)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE instruction SET variable_id=NULL"
                        + " WHERE bot_job_id=? AND variable_id IN ("
                        + placeholders(variableIds.size())
                        + ")")) {
            statement.setInt(1, botJobId);
            bindIds(statement, variableIds, 2);
            return statement.executeUpdate();
        }
    }

    private int deleteVariables(
            Connection connection, OwnerKey owner, Set<Integer> variableIds)
            throws SQLException {
        var result = RUNTIME_VARIABLES.deleteDefinitions(
                connection,
                new com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.OwnerKey(
                        owner.homeBankingId(), owner.ownerId()),
                variableIds.stream().map(Integer::longValue).toList(),
                null);
        if (!result.applied()) {
            throw new SQLException(result.message());
        }
        return variableIds.size();
    }

    private void verifyFinalState(
            AuthoritativeGraph before,
            AuthoritativeGraph after,
            DeletePlan plan,
            int expectedClearedBindings)
            throws SQLException {
        if (after.graphState().version() != before.graphState().version() + 1L) {
            throw refused(
                    "VARIABLE_DELETE_VERSION_MISMATCH",
                    "The Variables graph version was not advanced exactly once.");
        }
        if (after.variableIds().stream().anyMatch(plan.variableIds()::contains)) {
            throw refused(
                    "VARIABLE_DELETE_FINAL_STATE_MISMATCH",
                    "A selected variable remained after deletion.");
        }
        List<Integer> expectedRemaining = before.variableIds().stream()
                .filter(id -> !plan.variableIds().contains(id))
                .toList();
        if (!expectedRemaining.equals(after.variableIds())) {
            throw refused(
                    "VARIABLE_DELETE_FINAL_STATE_MISMATCH",
                    "Variables outside the selected deletion set changed.");
        }

        int actualClearedBindings = 0;
        if (before.instructions().size() != after.instructions().size()) {
            throw refused(
                    "VARIABLE_DELETE_INSTRUCTION_SET_CHANGED",
                    "Variable deletion must preserve every instruction.");
        }
        for (int index = 0; index < before.instructions().size(); index++) {
            InstructionLoad prior = before.instructions().get(index);
            InstructionLoad current = after.instructions().get(index);
            if (!sameInstructionExceptVariable(prior, current)) {
                throw refused(
                        "VARIABLE_DELETE_INSTRUCTION_CHANGED",
                        "Variable deletion changed instruction #" + prior.getId() + ".");
            }
            boolean selectedBinding =
                    prior.getVariableId() != null
                            && plan.variableIds().contains(prior.getVariableId());
            if (selectedBinding) {
                actualClearedBindings++;
                if (current.getVariableId() != null) {
                    throw refused(
                            "VARIABLE_DELETE_BINDING_REMAINED",
                            "Instruction #" + prior.getId()
                                    + " still references a deleted variable.");
                }
            } else if (!Objects.equals(prior.getVariableId(), current.getVariableId())) {
                throw refused(
                        "VARIABLE_DELETE_UNRELATED_BINDING_CHANGED",
                        "An unrelated instruction variable binding changed.");
            }
        }
        if (actualClearedBindings != expectedClearedBindings) {
            throw refused(
                    "VARIABLE_DELETE_BINDING_COUNT_MISMATCH",
                    "The deleted variable binding count changed during verification.");
        }
    }

    private static boolean sameInstructionExceptVariable(
            InstructionLoad left, InstructionLoad right) {
        return Objects.equals(left.getId(), right.getId())
                && Objects.equals(left.getBlockId(), right.getBlockId())
                && Objects.equals(
                        left.getInstructionOrderNumber(),
                        right.getInstructionOrderNumber())
                && Objects.equals(left.getActions(), right.getActions())
                && Objects.equals(left.getParentId(), right.getParentId())
                && Objects.equals(left.getParentBlockId(), right.getParentBlockId())
                && Objects.equals(left.getOperation(), right.getOperation());
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private static void bindIds(
            PreparedStatement statement, Set<Integer> ids, int firstParameter)
            throws SQLException {
        int parameter = firstParameter;
        for (Integer id : ids) statement.setInt(parameter++, id);
    }

    private static Integer nullableInteger(ResultSet rows, String column)
            throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    private static MutationRefusedException refused(String code, String message) {
        return new MutationRefusedException(code, message);
    }

    private static void requireOpenConnection(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("An open database connection is required.");
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

    private static void closeAfterRollbackFailure(
            Connection connection, Throwable failure) {
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // The commit/rollback result is already final.
        }
    }

    public record DeleteResult(
            OwnerKey owner,
            long workspaceEpoch,
            String requestId,
            VariablesWorkspaceVariableDelete.Mode mode,
            List<Integer> variableIds,
            int deletedCount,
            int clearedInstructionCount,
            long previousGraphVersion,
            long committedGraphVersion,
            String graphRevision) {

        public DeleteResult {
            variableIds = List.copyOf(variableIds);
        }
    }

    enum TransactionPhase {
        AFTER_BINDINGS_CLEARED,
        AFTER_VARIABLES_DELETED,
        AFTER_VERSION_ADVANCE,
        AFTER_FINAL_VERIFICATION
    }

    @FunctionalInterface
    interface FaultInjector {
        void at(TransactionPhase phase) throws SQLException;
    }

    private record DeletePlan(LinkedHashSet<Integer> variableIds) {
        private DeletePlan {
            variableIds = new LinkedHashSet<>(variableIds);
        }
    }

    private record AuthoritativeGraph(
            GraphState graphState,
            List<InstructionLoad> instructions,
            List<Integer> variableIds,
            String graphRevision) {}
}
