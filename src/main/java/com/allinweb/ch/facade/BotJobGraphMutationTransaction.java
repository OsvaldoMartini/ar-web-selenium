package com.allinweb.ch.facade;

import com.allinweb.ch.db.InstructionGraphStateRepository;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceResult;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceStatus;
import com.allinweb.ch.db.InstructionGraphStateRepository.GraphState;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.NormalizedInstruction;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.NormalizedMutation;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.NormalizedVariable;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.OwnerGraph;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.OwnerScope;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.StoredBlock;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.StoredInstruction;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.StoredVariable;
import com.allinweb.ch.facade.InstructionGraphMutationContractValidator.Validation;
import com.allinweb.ch.model.InstructionGraphMutationV3;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationKind;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.VariableLoadDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Additive Bot-Job-only atomic persistence foundation for instruction graph contract v3.
 *
 * <p>This transaction is intentionally not connected to WebSocket routing or any v2 writer. React
 * supplies the complete layout and explicit relationship patches. Java authenticates owner scope,
 * validates structural database facts, persists the exact normalized state, advances the
 * database-owned graph version, and verifies the committed candidate before one final commit.
 */
public final class BotJobGraphMutationTransaction {

    private static final FaultInjector NO_FAULTS = ignored -> {};

    private final InstructionGraphMutationContractValidator validator;
    private final InstructionGraphStateRepository stateRepository;
    private final InstructionGraphRevisionService revisionService;
    private final FaultInjector faultInjector;

    public BotJobGraphMutationTransaction() {
        this(
                new InstructionGraphMutationContractValidator(),
                new InstructionGraphStateRepository(),
                new InstructionGraphRevisionService(),
                NO_FAULTS);
    }

    BotJobGraphMutationTransaction(
            InstructionGraphMutationContractValidator validator,
            InstructionGraphStateRepository stateRepository,
            InstructionGraphRevisionService revisionService,
            FaultInjector faultInjector) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.revisionService = Objects.requireNonNull(revisionService, "revisionService");
        this.faultInjector = Objects.requireNonNull(faultInjector, "faultInjector");
    }

    /**
     * Starts and owns one transaction on the supplied connection.
     *
     * <p>The connection must arrive in auto-commit mode so this method cannot accidentally commit
     * unrelated caller work. It is restored to auto-commit after commit or rollback.
     */
    public CommitResult execute(
            Connection connection,
            AuthenticatedBotJob owner,
            InstructionGraphMutationV3.Request request)
            throws SQLException {
        return execute(connection, owner, request, null);
    }

    /**
     * Persists the narrow individual-row move advertised by the Variables page.
     *
     * <p>The React planner still owns the chosen target and complete final layout. The profile is
     * evaluated against the same authoritative snapshot and inside the same transaction used for
     * persistence, closing the inspection/write race without turning Java into a drag planner.
     */
    public CommitResult executeVariablesInstructionMove(
            Connection connection,
            AuthenticatedBotJob owner,
            InstructionGraphMutationV3.Request request)
            throws SQLException {
        return execute(
                connection,
                owner,
                request,
                new VariablesInstructionMutationProfile()::validate);
    }

    /**
     * Persists one explicitly planned Variables consumer move between structurally flat blocks.
     *
     * <p>The separate profile prevents the broader cross-block contract from weakening the
     * existing same-block release.
     */
    public CommitResult executeVariablesInstructionCrossBlockMove(
            Connection connection,
            AuthenticatedBotJob owner,
            InstructionGraphMutationV3.Request request)
            throws SQLException {
        return execute(
                connection,
                owner,
                request,
                new VariablesCrossBlockInstructionMutationProfile()::validate);
    }

    private CommitResult execute(
            Connection connection,
            AuthenticatedBotJob owner,
            InstructionGraphMutationV3.Request request,
            VariablesMutationProfile variablesProfile)
            throws SQLException {
        requireOpenConnection(connection);
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");
        if (!connection.getAutoCommit()) {
            throw new SQLException(
                    "Bot Job graph mutation requires an unbound auto-commit connection.");
        }

        connection.setAutoCommit(false);
        boolean restoreAutoCommit = true;
        try {
            AuthoritativeSnapshot before = loadAuthoritative(connection, owner);
            Validation validation = validator.validateAndNormalize(request, before.ownerGraph());
            if (!validation.successful()) {
                throw new MutationRefusedException(
                        validation.error().code().name(),
                        validation.error().message());
            }
            if (variablesProfile != null) {
                variablesProfile.validate(request, graphSnapshot(before));
            }

            NormalizedMutation mutation = validation.mutation();
            applyInstructions(connection, owner.owner().ownerId(), mutation.instructions());
            synchronizePrimaryVariableSlots(
                    connection,
                    owner.owner(),
                    mutation.instructions(),
                    before.actionsByInstruction());
            applyVariableOwners(connection, owner.owner().ownerId(), mutation.variables());
            faultInjector.at(TransactionPhase.AFTER_GRAPH_WRITES);

            AdvanceResult advance = stateRepository.compareAndSetIncrement(
                    connection,
                    owner.owner(),
                    mutation.baseGraphVersion());
            if (!advance.advanced()) {
                throw new MutationRefusedException(
                        advance.status() == AdvanceStatus.MISSING
                                ? "GRAPH_VERSION_STATE_MISSING"
                                : "GRAPH_VERSION_CAS_STALE",
                        "The instruction graph version changed before the transaction completed.");
            }
            faultInjector.at(TransactionPhase.AFTER_VERSION_ADVANCE);

            AuthoritativeSnapshot after = loadAuthoritative(connection, owner);
            verifyFinalState(before, after, mutation, advance.state());
            faultInjector.at(TransactionPhase.AFTER_FINAL_VERIFICATION);

            connection.commit();
            return new CommitResult(
                    owner.owner(),
                    owner.workspaceEpoch(),
                    request.requestId().trim(),
                    mutation.baseGraphVersion(),
                    advance.state().version(),
                    after.revision());
        } catch (SQLException | RuntimeException failure) {
            if (!rollback(connection, failure)) {
                restoreAutoCommit = false;
                closeAfterRollbackFailure(connection, failure);
            }
            throw failure;
        } finally {
            if (restoreAutoCommit) {
                restoreAutoCommit(connection);
            }
        }
    }

    /**
     * Reads the exact versioned graph facts required by a React mutation planner.
     *
     * <p>No movement or relationship semantics are inferred here. The returned layout, raw
     * persisted action, and relationship IDs are authoritative database facts. React classifies
     * the action and decides the complete intended final layout; Java later validates and
     * persists that exact submission.
     */
    public GraphSnapshot inspect(
            Connection connection,
            AuthenticatedBotJob owner)
            throws SQLException {
        requireOpenConnection(connection);
        Objects.requireNonNull(owner, "owner");
        if (!connection.getAutoCommit()) {
            throw new SQLException(
                    "Bot Job graph inspection requires an unbound auto-commit connection.");
        }

        return graphSnapshot(loadAuthoritative(connection, owner));
    }

    private GraphSnapshot graphSnapshot(AuthoritativeSnapshot snapshot) {
        Map<Integer, Integer> blockOrders = new LinkedHashMap<>();
        snapshot.ownerGraph().blocks().forEach(
                block -> blockOrders.put(block.id(), block.order()));

        List<GraphInstructionFact> instructionFacts =
                snapshot.ownerGraph().instructions().stream()
                        .map(instruction -> new GraphInstructionFact(
                                instruction.id(),
                                instruction.blockId(),
                                blockOrders.get(instruction.blockId()),
                                instruction.order(),
                                snapshot.actionsByInstruction()
                                        .getOrDefault(instruction.id(), ""),
                                instruction.parentId(),
                                instruction.parentBlockId(),
                                instruction.variableId()))
                        .sorted(java.util.Comparator
                                .comparingInt(GraphInstructionFact::blockOrderNumber)
                                .thenComparingInt(GraphInstructionFact::instructionOrderNumber)
                                .thenComparingInt(GraphInstructionFact::instructionId))
                        .toList();
        List<InstructionGraphMutationV3.LayoutRow> layoutRows =
                instructionFacts.stream()
                        .map(instruction -> new InstructionGraphMutationV3.LayoutRow(
                                instruction.instructionId(),
                                instruction.blockId(),
                                instruction.blockOrderNumber(),
                                instruction.instructionOrderNumber()))
                        .toList();
        List<GraphVariableFact> variableFacts =
                snapshot.ownerGraph().variables().stream()
                        .map(variable -> new GraphVariableFact(
                                variable.id(),
                                variable.instructionId()))
                        .sorted(java.util.Comparator.comparingInt(
                                GraphVariableFact::variableId))
                        .toList();
        return new GraphSnapshot(
                snapshot.ownerGraph().scope().graphVersion(),
                snapshot.revision(),
                layoutRows,
                instructionFacts,
                variableFacts);
    }

    private AuthoritativeSnapshot loadAuthoritative(
            Connection connection,
            AuthenticatedBotJob authenticatedOwner)
            throws SQLException {
        OwnerKey owner = authenticatedOwner.owner();
        requireOwnedBotJob(connection, owner);
        GraphState graphState = stateRepository.loadOrCreate(connection, owner);

        List<StoredBlock> blocks = loadBlocks(connection, owner.ownerId());
        List<StoredInstruction> instructions = new ArrayList<>();
        List<InstructionLoad> revisionInstructions = new ArrayList<>();
        Map<Integer, String> actionsByInstruction = new LinkedHashMap<>();
        Map<Integer, Map<String, Integer>> variableSlots =
                loadInstructionVariableSlots(connection, owner);
        String instructionSql = "SELECT id,block_id,instruction_order_number,actions,parent_id,"
                + "parent_block_id,operation FROM instruction"
                + " WHERE bot_job_id=? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(instructionSql)) {
            statement.setInt(1, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int id = rows.getInt("id");
                    int blockId = rows.getInt("block_id");
                    int order = rows.getInt("instruction_order_number");
                    String action = rows.getString("actions");
                    actionsByInstruction.put(id, action == null ? "" : action);
                    Integer parentId = nullableInteger(rows, "parent_id");
                    Integer parentBlockId = nullableInteger(rows, "parent_block_id");
                    String primarySlot = primaryVariableSlot(action);
                    Integer variableId = primarySlot == null
                            ? null
                            : variableSlots.getOrDefault(id, Map.of()).get(primarySlot);
                    instructions.add(new StoredInstruction(
                            id,
                            blockId,
                            order,
                            relationKind(action),
                            parentId,
                            parentBlockId,
                            variableId));

                    InstructionLoad revisionRow = new InstructionLoad();
                    revisionRow.setId(id);
                    revisionRow.setBlockId(blockId);
                    revisionRow.setInstructionOrderNumber(order);
                    revisionRow.setActions(action);
                    revisionRow.setParentId(parentId);
                    revisionRow.setParentBlockId(parentBlockId);
                    revisionRow.setVariableId(variableId);
                    revisionRow.setOperation(rows.getString("operation"));
                    revisionInstructions.add(revisionRow);
                }
            }
        }

        List<StoredVariable> variables = new ArrayList<>();
        List<VariableLoadDTO> revisionVariables = new ArrayList<>();
        String variableSql =
                "SELECT id,producer_instruction_id AS instruction_id"
                        + " FROM bot_job_variable_definition"
                        + " WHERE home_banking_id=? AND bot_job_id=? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(variableSql)) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int id = rows.getInt("id");
                    Integer instructionId = nullableInteger(rows, "instruction_id");
                    variables.add(new StoredVariable(id, instructionId));
                    revisionVariables.add(new VariableLoadDTO(
                            id,
                            null,
                            owner.ownerId(),
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

        String revision = revisionService.revision(revisionInstructions, revisionVariables);
        OwnerScope scope = new OwnerScope(
                InstructionGraphMutationV3.WorkspaceKind.BOT_JOB,
                owner.homeBankingId(),
                owner.ownerId(),
                authenticatedOwner.workspaceEpoch(),
                graphState.version(),
                revision);
        return new AuthoritativeSnapshot(
                new OwnerGraph(scope, blocks, instructions, variables),
                revision,
                actionsByInstruction);
    }

    private Map<Integer, Map<String, Integer>> loadInstructionVariableSlots(
            Connection connection,
            OwnerKey owner)
            throws SQLException {
        Map<Integer, Map<String, Integer>> slotsByInstruction = new LinkedHashMap<>();
        String sql = "SELECT instruction_id,slot,variable_id FROM instruction_variable_slot"
                + " WHERE home_banking_id=? AND bot_job_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    slotsByInstruction
                            .computeIfAbsent(rows.getInt("instruction_id"), ignored -> new LinkedHashMap<>())
                            .put(rows.getString("slot"), rows.getInt("variable_id"));
                }
            }
        }
        return slotsByInstruction;
    }

    private List<StoredBlock> loadBlocks(Connection connection, int botJobId)
            throws SQLException {
        List<StoredBlock> blocks = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,block_order_number FROM block"
                        + " WHERE bot_job_id=? ORDER BY block_order_number,id")) {
            statement.setInt(1, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    blocks.add(new StoredBlock(
                            rows.getInt("id"),
                            rows.getInt("block_order_number")));
                }
            }
        }
        return blocks;
    }

    private void requireOwnedBotJob(Connection connection, OwnerKey owner)
            throws SQLException {
        if (owner.workspaceKind() != InstructionGraphMutationV3.WorkspaceKind.BOT_JOB) {
            throw new MutationRefusedException(
                    "BOT_JOB_OWNER_REQUIRED",
                    "The Bot Job graph transaction cannot mutate a Component owner.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM bot_job WHERE id=? AND home_banking_id=?")) {
            statement.setInt(1, owner.ownerId());
            statement.setInt(2, owner.homeBankingId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new MutationRefusedException(
                            "BOT_JOB_OWNER_MISMATCH",
                            "The authenticated organization does not own the requested Bot Job.");
                }
            }
        }
    }

    private void applyInstructions(
            Connection connection,
            int botJobId,
            List<NormalizedInstruction> instructions)
            throws SQLException {
        String sql = "UPDATE instruction SET block_id=?,instruction_order_number=?,"
                + "parent_id=?,parent_block_id=? WHERE id=? AND bot_job_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (NormalizedInstruction instruction : instructions) {
                statement.setInt(1, instruction.blockId());
                statement.setInt(2, instruction.instructionOrderNumber());
                setNullableInteger(statement, 3, instruction.parentId());
                setNullableInteger(statement, 4, instruction.parentBlockId());
                statement.setInt(5, instruction.instructionId());
                statement.setInt(6, botJobId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Instruction #" + instruction.instructionId()
                                    + " was not updated exactly once.");
                }
            }
        }
    }

    private void applyVariableOwners(
            Connection connection,
            int botJobId,
            List<NormalizedVariable> variables)
            throws SQLException {
        String sql = "UPDATE bot_job_variable_definition"
                + " SET producer_instruction_id=?,updated_at=CURRENT_TIMESTAMP"
                + " WHERE id=? AND bot_job_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (NormalizedVariable variable : variables) {
                setNullableInteger(statement, 1, variable.instructionId());
                statement.setInt(2, variable.variableId());
                statement.setInt(3, botJobId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Variable #" + variable.variableId()
                                    + " was not updated exactly once.");
                }
            }
        }
    }

    /**
     * Persists directional primary relationships exclusively in instruction_variable_slot:
     * GET_WRITE for GET, READ_SET for SET, READ for E, and LEFT for CheckValue.
     */
    static void synchronizePrimaryVariableSlots(
            Connection connection,
            OwnerKey owner,
            List<NormalizedInstruction> instructions,
            Map<Integer, String> actionsByInstruction)
            throws SQLException {
        String selectSql = "SELECT variable_id FROM instruction_variable_slot"
                + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=? AND slot=?";
        String insertSql = "INSERT INTO instruction_variable_slot"
                + " (home_banking_id,bot_job_id,instruction_id,slot,variable_id,slot_revision,"
                + "created_at,updated_at) VALUES (?,?,?,?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)";
        String updateSql = "UPDATE instruction_variable_slot SET variable_id=?,"
                + "slot_revision=slot_revision+1,updated_at=CURRENT_TIMESTAMP"
                + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=? AND slot=?";
        String deleteSql = "DELETE FROM instruction_variable_slot"
                + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=? AND slot=?";
        try (PreparedStatement select = connection.prepareStatement(selectSql);
                PreparedStatement insert = connection.prepareStatement(insertSql);
                PreparedStatement update = connection.prepareStatement(updateSql);
                PreparedStatement delete = connection.prepareStatement(deleteSql)) {
            for (NormalizedInstruction instruction : instructions) {
                String slot = primaryVariableSlot(
                        actionsByInstruction.get(instruction.instructionId()));
                if (slot == null) continue;
                Integer current = null;
                select.setInt(1, owner.homeBankingId());
                select.setInt(2, owner.ownerId());
                select.setInt(3, instruction.instructionId());
                select.setString(4, slot);
                try (ResultSet rows = select.executeQuery()) {
                    if (rows.next()) current = rows.getInt("variable_id");
                }

                Integer desired = instruction.variableId();
                if (Objects.equals(current, desired)) continue;
                if (desired == null) {
                    delete.setInt(1, owner.homeBankingId());
                    delete.setInt(2, owner.ownerId());
                    delete.setInt(3, instruction.instructionId());
                    delete.setString(4, slot);
                    delete.executeUpdate();
                } else if (current == null) {
                    insert.setInt(1, owner.homeBankingId());
                    insert.setInt(2, owner.ownerId());
                    insert.setInt(3, instruction.instructionId());
                    insert.setString(4, slot);
                    insert.setInt(5, desired);
                    if (insert.executeUpdate() != 1) {
                        throw new SQLException("Primary variable slot was not inserted exactly once.");
                    }
                } else {
                    update.setInt(1, desired);
                    update.setInt(2, owner.homeBankingId());
                    update.setInt(3, owner.ownerId());
                    update.setInt(4, instruction.instructionId());
                    update.setString(5, slot);
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("Primary variable slot was not updated exactly once.");
                    }
                }
            }
        }
    }

    private static String primaryVariableSlot(String actionValue) {
        String action = CommandRegistry.canonicalize(actionValue);
        if ("CK".equals(action) || "CSV CHECK".equals(action) || "PDF CHECK".equals(action)) {
            return "LEFT";
        }
        if ("GET".equals(action)) return "GET_WRITE";
        if ("SET".equals(action)) return "READ_SET";
        if ("E".equals(action)) return "READ";
        return null;
    }

    private void verifyFinalState(
            AuthoritativeSnapshot before,
            AuthoritativeSnapshot after,
            NormalizedMutation mutation,
            GraphState advancedState)
            throws SQLException {
        if (after.ownerGraph().scope().graphVersion() != advancedState.version()) {
            throw new MutationRefusedException(
                    "FINAL_GRAPH_VERSION_MISMATCH",
                    "The graph version was not advanced exactly once.");
        }
        if (!before.ownerGraph().blocks().equals(after.ownerGraph().blocks())) {
            throw new MutationRefusedException(
                    "BLOCK_CATALOG_CHANGED",
                    "The Bot Job block catalog changed during instruction persistence.");
        }

        Map<Integer, StoredBlock> blocksById = new LinkedHashMap<>();
        after.ownerGraph().blocks().forEach(block -> blocksById.put(block.id(), block));
        Map<Integer, StoredInstruction> actualInstructions = new LinkedHashMap<>();
        after.ownerGraph().instructions().forEach(
                instruction -> actualInstructions.put(instruction.id(), instruction));
        if (actualInstructions.size() != mutation.instructions().size()) {
            throw finalStateMismatch("The final instruction ID set differs from the submitted graph.");
        }
        for (NormalizedInstruction expected : mutation.instructions()) {
            StoredInstruction actual = actualInstructions.get(expected.instructionId());
            StoredBlock block = blocksById.get(expected.blockId());
            if (actual == null
                    || block == null
                    || actual.blockId() != expected.blockId()
                    || block.order() != expected.blockOrderNumber()
                    || actual.order() != expected.instructionOrderNumber()
                    || actual.relationKind() != expected.relationKind()
                    || !Objects.equals(actual.parentId(), expected.parentId())
                    || !Objects.equals(actual.parentBlockId(), expected.parentBlockId())
                    || !Objects.equals(actual.variableId(), expected.variableId())) {
                throw finalStateMismatch(
                        "Instruction #" + expected.instructionId()
                                + " does not match the submitted final state.");
            }
        }

        Map<Integer, Integer> actualVariableOwners = new LinkedHashMap<>();
        after.ownerGraph().variables().forEach(
                variable -> actualVariableOwners.put(variable.id(), variable.instructionId()));
        if (actualVariableOwners.size() != mutation.variables().size()) {
            throw finalStateMismatch("The final variable ID set differs from the submitted graph.");
        }
        for (NormalizedVariable expected : mutation.variables()) {
            if (!actualVariableOwners.containsKey(expected.variableId())
                    || !Objects.equals(
                            actualVariableOwners.get(expected.variableId()),
                            expected.instructionId())) {
                throw finalStateMismatch(
                        "Variable #" + expected.variableId()
                                + " does not match the submitted final owner.");
            }
        }
    }

    private MutationRefusedException finalStateMismatch(String message) {
        return new MutationRefusedException("FINAL_STATE_MISMATCH", message);
    }

    /**
     * Maps the immutable persisted action into the structural relationship column family.
     *
     * <p>Known structural/navigation actions get their exact kind. All other persisted actions map
     * to ELEMENT_TARGET so legacy command rows that already carry parent IDs can be cleared and
     * reconnected without Java inventing action-specific authoring rules.
     */
    static InstructionRelationKind relationKind(String persistedAction) {
        String action = CommandRegistry.canonicalize(persistedAction);
        return switch (action) {
            case "GOTO", "EXCEL GOTO" -> InstructionRelationKind.BLOCK_TARGET;
            case "LOOP", "REFRESH_LOOP" -> InstructionRelationKind.LOOP_ANCHOR;
            case "IF", "ELSEIF", "ELSE", "ENDIF" ->
                    InstructionRelationKind.CONDITIONAL_ROOT;
            default -> InstructionRelationKind.ELEMENT_TARGET;
        };
    }

    private static Integer nullableInteger(ResultSet rows, String column)
            throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    private static void setNullableInteger(
            PreparedStatement statement,
            int parameter,
            Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(parameter, Types.INTEGER);
        } else {
            statement.setInt(parameter, value);
        }
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
            Connection connection,
            Throwable failure) {
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
            // The connection remains caller-owned; the mutation outcome has already been decided.
        }
    }

    public record AuthenticatedBotJob(
            OwnerKey owner,
            long workspaceEpoch) {

        public AuthenticatedBotJob {
            Objects.requireNonNull(owner, "owner");
            if (owner.workspaceKind() != InstructionGraphMutationV3.WorkspaceKind.BOT_JOB) {
                throw new IllegalArgumentException(
                        "AuthenticatedBotJob requires a BOT_JOB owner key.");
            }
            if (workspaceEpoch <= 0L) {
                throw new IllegalArgumentException("A positive workspace epoch is required.");
            }
        }

        public static AuthenticatedBotJob of(
                int homeBankingId,
                int botJobId,
                long workspaceEpoch) {
            return new AuthenticatedBotJob(
                    OwnerKey.botJob(homeBankingId, botJobId),
                    workspaceEpoch);
        }
    }

    public record CommitResult(
            OwnerKey owner,
            long workspaceEpoch,
            String requestId,
            long previousGraphVersion,
            long committedGraphVersion,
            String graphRevision) {}

    public record GraphInstructionFact(
            int instructionId,
            int blockId,
            int blockOrderNumber,
            int instructionOrderNumber,
            String action,
            Integer parentId,
            Integer parentBlockId,
            Integer variableId) {}

    /**
     * Exact persisted ownership fact for one Bot Job variable definition.
     *
     * <p>{@code ownerInstructionId} intentionally remains nullable and is not resolved against
     * the instruction catalog here. A null owner and a dangling positive owner are different
     * authoritative states that React must be able to compare-and-set without Java repairing or
     * hiding either one.
     */
    public record GraphVariableFact(
            int variableId,
            Integer ownerInstructionId) {}

    public record GraphSnapshot(
            long graphVersion,
            String graphRevision,
            List<InstructionGraphMutationV3.LayoutRow> layoutRows,
            List<GraphInstructionFact> instructionFacts,
            List<GraphVariableFact> variableFacts) {

        public GraphSnapshot {
            layoutRows = List.copyOf(layoutRows);
            instructionFacts = List.copyOf(instructionFacts);
            variableFacts = List.copyOf(variableFacts);
        }

        /**
         * Compatibility constructor for callers that do not author variable-owner relationships.
         */
        public GraphSnapshot(
                long graphVersion,
                String graphRevision,
                List<InstructionGraphMutationV3.LayoutRow> layoutRows,
                List<GraphInstructionFact> instructionFacts) {
            this(graphVersion, graphRevision, layoutRows, instructionFacts, List.of());
        }
    }

    public static final class MutationRefusedException extends SQLException {
        private final String code;

        MutationRefusedException(String code, String message) {
            super(message);
            this.code = Objects.requireNonNull(code, "code");
        }

        public String code() {
            return code;
        }
    }

    enum TransactionPhase {
        AFTER_GRAPH_WRITES,
        AFTER_VERSION_ADVANCE,
        AFTER_FINAL_VERIFICATION
    }

    @FunctionalInterface
    interface FaultInjector {
        void at(TransactionPhase phase) throws SQLException;
    }

    @FunctionalInterface
    private interface VariablesMutationProfile {
        void validate(InstructionGraphMutationV3.Request request, GraphSnapshot authoritative)
                throws MutationRefusedException;
    }

    private record AuthoritativeSnapshot(
            OwnerGraph ownerGraph,
            String revision,
            Map<Integer, String> actionsByInstruction) {

        private AuthoritativeSnapshot {
            actionsByInstruction = Map.copyOf(actionsByInstruction);
        }
    }
}
