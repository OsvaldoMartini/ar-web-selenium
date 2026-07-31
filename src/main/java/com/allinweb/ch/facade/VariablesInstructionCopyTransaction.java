package com.allinweb.ch.facade;

import com.allinweb.ch.db.InstructionGraphStateRepository;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceResult;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceStatus;
import com.allinweb.ch.db.InstructionGraphStateRepository.GraphState;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.DefinitionDraft;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.MutationResult;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.ValueState;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableService;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.VariableLoadDTO;
import com.allinweb.ch.model.VariablesInstructionCopyV1;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Atomic fresh-ID instruction copy authored entirely by the Variables React workspace.
 *
 * <p>Java never discovers or enlarges the submitted source set. It verifies the exact owner,
 * graph version/revision, target block, and scope shape, then copies the submitted rows in their
 * submitted order. Existing rows are never rewritten.
 */
public final class VariablesInstructionCopyTransaction {
    private static final BotJobRuntimeVariableService RUNTIME_VARIABLES =
            new BotJobRuntimeVariableService();

    private static final String INSTRUCTION_SELECT = """
            SELECT id, instruction_order_number, actions, name, xpath, coordinates,
                   force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root,
                   css_selector, description, operation, optional, block_marked,
                   default_value, action_custom_max_wait_sec, on_hold_seconds, codified,
                   export_to_abr, active, block_id, variable_id, parent_block_id, parent_id,
                   bot_job_id, client_named
              FROM instruction
             WHERE bot_job_id = ?
             ORDER BY id
            """;
    private static final String INSTRUCTION_INSERT = """
            INSERT INTO instruction (
                   instruction_order_number, actions, name, xpath, coordinates,
                   force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root,
                   css_selector, description, operation, optional, block_marked,
                   default_value, action_custom_max_wait_sec, on_hold_seconds, codified,
                   export_to_abr, active, block_id, variable_id, parent_block_id, parent_id,
                   bot_job_id, client_named)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final FaultInjector NO_FAULTS = ignored -> {};

    private final InstructionGraphStateRepository stateRepository;
    private final InstructionGraphRevisionService revisionService;
    private final FaultInjector faultInjector;

    public VariablesInstructionCopyTransaction() {
        this(
                new InstructionGraphStateRepository(),
                new InstructionGraphRevisionService(),
                NO_FAULTS);
    }

    VariablesInstructionCopyTransaction(
            InstructionGraphStateRepository stateRepository,
            InstructionGraphRevisionService revisionService,
            FaultInjector faultInjector) {
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.revisionService = Objects.requireNonNull(revisionService, "revisionService");
        this.faultInjector = Objects.requireNonNull(faultInjector, "faultInjector");
    }

    public CopyResult execute(
            Connection connection,
            AuthenticatedBotJob owner,
            VariablesInstructionCopyV1.Request request)
            throws SQLException {
        requireOpenConnection(connection);
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");
        if (!connection.getAutoCommit()) {
            throw new SQLException(
                    "Variables instruction copy requires an unbound auto-commit connection.");
        }

        connection.setAutoCommit(false);
        boolean restoreAutoCommit = true;
        try {
            requireOwnedBotJob(connection, owner.owner());
            GraphState graphState = stateRepository.loadOrCreate(connection, owner.owner());
            AuthoritativeGraph before =
                    loadGraph(connection, owner.owner(), graphState);
            CopyPlan plan = validateAndPlan(owner, request, before);

            int firstTargetOrder =
                    before.instructions().values().stream()
                                    .filter(row -> row.blockId() == plan.targetBlockId())
                                    .map(InstructionRow::order)
                                    .max(Integer::compareTo)
                                    .orElse(0)
                            + 1;
            LinkedHashMap<Integer, Integer> generatedInstructionIds =
                    insertInstructions(
                            connection,
                            owner.owner().ownerId(),
                            plan.sourceRows(),
                            plan.targetBlockId(),
                            firstTargetOrder);
            faultInjector.at(TransactionPhase.AFTER_INSTRUCTIONS_INSERTED);

            LinkedHashMap<Integer, Integer> generatedVariableIds =
                    copyOwnedVariables(
                            connection,
                            owner.owner(),
                            plan.variablesToClone(),
                            generatedInstructionIds);
            faultInjector.at(TransactionPhase.AFTER_VARIABLES_INSERTED);

            LinkedHashMap<Integer, ExpectedInstruction> expectedCopies =
                    applyRelationshipsAndVariables(
                            connection,
                            owner.owner().ownerId(),
                            plan,
                            before,
                            generatedInstructionIds,
                            generatedVariableIds,
                            firstTargetOrder);
            int copiedReferenceCount =
                    copyReferences(
                            connection,
                            owner.owner().ownerId(),
                            before.references(),
                            generatedInstructionIds);
            faultInjector.at(TransactionPhase.AFTER_GRAPH_COPIED);

            AdvanceResult advance = stateRepository.compareAndSetIncrement(
                    connection, owner.owner(), request.baseGraphVersion());
            if (!advance.advanced()) {
                throw new MutationRefusedException(
                        advance.status() == AdvanceStatus.MISSING
                                ? "GRAPH_VERSION_STATE_MISSING"
                                : "GRAPH_VERSION_CAS_STALE",
                        "The Variables graph version changed before instruction copy completed.");
            }
            faultInjector.at(TransactionPhase.AFTER_VERSION_ADVANCE);

            AuthoritativeGraph after =
                    loadGraph(connection, owner.owner(), advance.state());
            verifyFinalState(
                    before,
                    after,
                    plan,
                    generatedInstructionIds,
                    generatedVariableIds,
                    expectedCopies,
                    copiedReferenceCount);
            faultInjector.at(TransactionPhase.AFTER_FINAL_VERIFICATION);

            connection.commit();
            return new CopyResult(
                    owner.owner(),
                    owner.workspaceEpoch(),
                    request.requestId().trim(),
                    request.scope(),
                    request.selectedInstructionId(),
                    plan.targetBlockId(),
                    List.copyOf(plan.sourceInstructionIds()),
                    immutableOrderedMap(generatedInstructionIds),
                    immutableOrderedMap(generatedVariableIds),
                    copiedReferenceCount,
                    graphState.version(),
                    advance.state().version(),
                    after.graphRevision(),
                    false);
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

    private CopyPlan validateAndPlan(
            AuthenticatedBotJob owner,
            VariablesInstructionCopyV1.Request request,
            AuthoritativeGraph authoritative)
            throws MutationRefusedException {
        if (!Objects.equals(
                request.contractVersion(), VariablesInstructionCopyV1.CONTRACT_VERSION)) {
            throw refused(
                    "VARIABLE_COPY_CONTRACT_UNSUPPORTED",
                    "The Variables instruction-copy contract version is not supported.");
        }
        if (request.requestId() == null || request.requestId().trim().isEmpty()) {
            throw refused(
                    "VARIABLE_COPY_REQUEST_ID_REQUIRED",
                    "A Variables instruction-copy request ID is required.");
        }
        if (request.bindingEpoch() == null || request.bindingEpoch().trim().isEmpty()) {
            throw refused(
                    "VARIABLE_COPY_BINDING_REQUIRED",
                    "The Variables binding epoch is required.");
        }
        if (!Objects.equals(request.workspaceEpoch(), owner.workspaceEpoch())) {
            throw refused(
                    "VARIABLE_COPY_WORKSPACE_CHANGED",
                    "The Bot Job workspace changed before instruction copy.");
        }
        if (!Objects.equals(
                request.baseGraphVersion(), authoritative.graphState().version())) {
            throw refused(
                    "VARIABLE_COPY_GRAPH_VERSION_STALE",
                    "The Variables graph version changed before instruction copy.");
        }
        String requestedRevision =
                request.graphRevision() == null ? "" : request.graphRevision().trim();
        if (requestedRevision.isEmpty()
                || !requestedRevision.equals(authoritative.graphRevision())) {
            throw refused(
                    "VARIABLE_COPY_GRAPH_REVISION_STALE",
                    "The Variables graph changed before instruction copy.");
        }
        if (request.targetBlockId() == null
                || request.targetBlockId() <= 0
                || !authoritative.blocks().containsKey(request.targetBlockId())) {
            throw refused(
                    "VARIABLE_COPY_TARGET_BLOCK_INVALID",
                    "Select a current Bot Job block for the instruction copy.");
        }
        if (request.selectedInstructionId() == null
                || request.selectedInstructionId() <= 0) {
            throw refused(
                    "VARIABLE_COPY_SELECTED_ID_REQUIRED",
                    "Select one authoritative instruction to copy.");
        }
        if (request.scope() == null) {
            throw refused(
                    "VARIABLE_COPY_SCOPE_REQUIRED",
                    "Select ONLY_INSTRUCTION or WITH_PARENTS.");
        }

        LinkedHashSet<Integer> sourceIds = new LinkedHashSet<>();
        for (Integer instructionId : request.sourceInstructionIds()) {
            if (instructionId == null
                    || instructionId <= 0
                    || !sourceIds.add(instructionId)) {
                throw refused(
                        "VARIABLE_COPY_SOURCE_IDS_INVALID",
                        "Instruction copy requires unique positive source instruction IDs.");
            }
        }
        if (sourceIds.isEmpty() || !sourceIds.contains(request.selectedInstructionId())) {
            throw refused(
                    "VARIABLE_COPY_SELECTION_INCOMPLETE",
                    "The exact source list must contain the selected instruction.");
        }
        if (request.scope() == VariablesInstructionCopyV1.Scope.ONLY_INSTRUCTION
                && (sourceIds.size() != 1
                        || !sourceIds.iterator()
                                .next()
                                .equals(request.selectedInstructionId()))) {
            throw refused(
                    "VARIABLE_COPY_ONLY_REQUIRES_SELECTED",
                    "ONLY_INSTRUCTION must submit exactly the selected instruction.");
        }

        List<InstructionRow> sourceRows = new ArrayList<>(sourceIds.size());
        for (Integer sourceId : sourceIds) {
            InstructionRow source = authoritative.instructions().get(sourceId);
            if (source == null) {
                throw refused(
                        "VARIABLE_COPY_SOURCE_NOT_OWNED",
                        "A selected instruction does not belong to the current Bot Job.");
            }
            sourceRows.add(source);
            validateSourceVariable(source, authoritative);
            validateNavigationTarget(source, request.targetBlockId(), authoritative);
        }

        if (request.scope() == VariablesInstructionCopyV1.Scope.WITH_PARENTS) {
            for (InstructionRow source : sourceRows) {
                if (isExternalNavigation(source)) continue;
                Integer parentId = source.parentId();
                if (parentId != null && !sourceIds.contains(parentId)) {
                    throw refused(
                            "VARIABLE_COPY_PARENT_NOT_SELECTED",
                            "WITH_PARENTS requires every ordinary parent in the exact React selection.");
                }
                if (parentId != null
                        && !authoritative.instructions().containsKey(parentId)) {
                    throw refused(
                            "VARIABLE_COPY_PARENT_NOT_OWNED",
                            "A selected instruction references a parent outside the current Bot Job.");
                }
            }
        }

        LinkedHashMap<Integer, VariableRow> variablesToClone = new LinkedHashMap<>();
        if (request.scope() == VariablesInstructionCopyV1.Scope.WITH_PARENTS) {
            for (VariableRow variable : authoritative.variables().values()) {
                if (variable.ownerInstructionId() != null
                        && sourceIds.contains(variable.ownerInstructionId())) {
                    variablesToClone.put(variable.id(), variable);
                }
            }
        }
        return new CopyPlan(
                request.scope(),
                request.targetBlockId(),
                request.selectedInstructionId(),
                sourceIds,
                List.copyOf(sourceRows),
                variablesToClone);
    }

    private void validateSourceVariable(
            InstructionRow source, AuthoritativeGraph authoritative)
            throws MutationRefusedException {
        if (source.variableId() != null
                && !authoritative.variables().containsKey(source.variableId())) {
            throw refused(
                    "VARIABLE_COPY_VARIABLE_NOT_OWNED",
                    "A selected instruction references a variable outside the current Bot Job.");
        }
    }

    private void validateNavigationTarget(
            InstructionRow source,
            int targetBlockId,
            AuthoritativeGraph authoritative)
            throws MutationRefusedException {
        if (!isExternalNavigation(source)) return;
        Integer navigationBlockId = source.parentBlockId();
        if (navigationBlockId == null
                || !authoritative.blocks().containsKey(navigationBlockId)) {
            throw refused(
                    "VARIABLE_COPY_NAVIGATION_TARGET_MISSING",
                    "A selected GOTO references a block outside the current Bot Job.");
        }
        if (navigationBlockId == targetBlockId) {
            throw refused(
                    "VARIABLE_COPY_NAVIGATION_TARGET_EQUALS_DESTINATION",
                    "A copied GOTO or EXCEL GOTO cannot target its containing block.");
        }
        if (source.parentId() != null) {
            InstructionRow target = authoritative.instructions().get(source.parentId());
            if (target == null || target.blockId() != navigationBlockId) {
                throw refused(
                        "VARIABLE_COPY_NAVIGATION_INSTRUCTION_MISSING",
                        "A selected GOTO references an instruction outside its target block.");
            }
        }
    }

    private LinkedHashMap<Integer, Integer> insertInstructions(
            Connection connection,
            int botJobId,
            List<InstructionRow> sourceRows,
            int targetBlockId,
            int firstTargetOrder)
            throws SQLException {
        LinkedHashMap<Integer, Integer> generatedIds = new LinkedHashMap<>();
        try (PreparedStatement statement =
                connection.prepareStatement(INSTRUCTION_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            int targetOrder = firstTargetOrder;
            for (InstructionRow source : sourceRows) {
                bindInstructionInsert(
                        statement, source, targetOrder++, targetBlockId, botJobId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Instruction copy did not create exactly one fresh row.");
                }
                generatedIds.put(
                        source.id(), generatedId(statement, "Variables instruction copy"));
            }
        }
        return generatedIds;
    }

    private LinkedHashMap<Integer, Integer> copyOwnedVariables(
            Connection connection,
            OwnerKey owner,
            Map<Integer, VariableRow> variablesToClone,
            Map<Integer, Integer> generatedInstructionIds)
            throws SQLException {
        LinkedHashMap<Integer, Integer> generatedVariableIds = new LinkedHashMap<>();
        if (variablesToClone.isEmpty()) return generatedVariableIds;
        for (VariableRow source : variablesToClone.values()) {
            Integer generatedOwner =
                    generatedInstructionIds.get(source.ownerInstructionId());
            if (generatedOwner == null) {
                throw refused(
                        "VARIABLE_COPY_OWNER_NOT_SELECTED",
                        "A cloned variable owner was not included in the exact source list.");
            }
            MutationResult created = RUNTIME_VARIABLES.createDefinition(
                    connection,
                    new com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.OwnerKey(
                            owner.homeBankingId(), owner.ownerId()),
                    new DefinitionDraft(
                            text(source.type()),
                            text(source.name()),
                            text(source.value()),
                            text(source.localFormat()),
                            text(source.delimiter()),
                            generatedOwner.longValue(),
                            ValueState.VOID,
                            null),
                    null);
            if (!created.applied() || created.definition() == null) {
                throw new SQLException(created.message());
            }
            generatedVariableIds.put(
                    source.id(), Math.toIntExact(created.definition().id()));
        }
        return generatedVariableIds;
    }

    private LinkedHashMap<Integer, ExpectedInstruction> applyRelationshipsAndVariables(
            Connection connection,
            int botJobId,
            CopyPlan plan,
            AuthoritativeGraph authoritative,
            Map<Integer, Integer> generatedInstructionIds,
            Map<Integer, Integer> generatedVariableIds,
            int firstTargetOrder)
            throws SQLException {
        String update = "UPDATE instruction "
                + "SET variable_id=?,parent_block_id=?,parent_id=? "
                + "WHERE id=? AND bot_job_id=?";
        LinkedHashMap<Integer, ExpectedInstruction> expected = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            int targetOrder = firstTargetOrder;
            for (InstructionRow source : plan.sourceRows()) {
                Integer generatedId = generatedInstructionIds.get(source.id());
                if (generatedId == null) {
                    throw new SQLException(
                            "A selected instruction has no generated copy ID.");
                }

                Integer variableId = source.variableId();
                if (variableId != null && generatedVariableIds.containsKey(variableId)) {
                    variableId = generatedVariableIds.get(variableId);
                }

                Relationship relationship =
                        copiedRelationship(
                                source,
                                plan,
                                authoritative,
                                generatedInstructionIds);
                bindNullableInteger(statement, 1, variableId);
                bindNullableInteger(statement, 2, relationship.parentBlockId());
                bindNullableInteger(statement, 3, relationship.parentId());
                statement.setInt(4, generatedId);
                statement.setInt(5, botJobId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "A copied instruction relationship was not updated exactly once.");
                }
                expected.put(
                        source.id(),
                        new ExpectedInstruction(
                                generatedId,
                                targetOrder++,
                                plan.targetBlockId(),
                                variableId,
                                relationship.parentBlockId(),
                                relationship.parentId()));
            }
        }
        return expected;
    }

    private Relationship copiedRelationship(
            InstructionRow source,
            CopyPlan plan,
            AuthoritativeGraph authoritative,
            Map<Integer, Integer> generatedInstructionIds)
            throws MutationRefusedException {
        if (isExternalNavigation(source)) {
            return new Relationship(source.parentId(), source.parentBlockId());
        }
        if (source.parentId() == null) {
            return Relationship.disconnected();
        }
        Integer generatedParent = generatedInstructionIds.get(source.parentId());
        if (generatedParent != null) {
            return new Relationship(generatedParent, plan.targetBlockId());
        }
        if (plan.scope() == VariablesInstructionCopyV1.Scope.ONLY_INSTRUCTION) {
            InstructionRow existingParent =
                    authoritative.instructions().get(source.parentId());
            if (existingParent != null
                    && existingParent.blockId() == plan.targetBlockId()) {
                return new Relationship(existingParent.id(), existingParent.blockId());
            }
            return Relationship.disconnected();
        }
        throw refused(
                "VARIABLE_COPY_PARENT_NOT_SELECTED",
                "WITH_PARENTS requires every ordinary parent in the exact React selection.");
    }

    private int copyReferences(
            Connection connection,
            int botJobId,
            List<ReferenceRow> sourceReferences,
            Map<Integer, Integer> generatedInstructionIds)
            throws SQLException {
        String insert = "INSERT INTO reference "
                + "(reference_type,value,instruction_id,bot_job_id) VALUES (?,?,?,?)";
        int copied = 0;
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            for (ReferenceRow source : sourceReferences) {
                Integer generatedInstructionId =
                        generatedInstructionIds.get(source.instructionId());
                if (generatedInstructionId == null) continue;
                statement.setObject(1, source.referenceType());
                statement.setObject(2, source.value());
                statement.setInt(3, generatedInstructionId);
                statement.setInt(4, botJobId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Instruction reference copy did not create exactly one row.");
                }
                copied++;
            }
        }
        return copied;
    }

    private AuthoritativeGraph loadGraph(
            Connection connection, OwnerKey owner, GraphState state)
            throws SQLException {
        int botJobId = owner.ownerId();
        LinkedHashMap<Integer, BlockRow> blocks = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,block_order_number FROM block "
                        + "WHERE bot_job_id=? ORDER BY block_order_number,id")) {
            statement.setInt(1, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    BlockRow block =
                            new BlockRow(
                                    rows.getInt("id"),
                                    rows.getInt("block_order_number"));
                    blocks.put(block.id(), block);
                }
            }
        }

        LinkedHashMap<Integer, InstructionRow> instructions = new LinkedHashMap<>();
        List<InstructionLoad> revisionInstructions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(INSTRUCTION_SELECT)) {
            statement.setInt(1, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    InstructionRow row = InstructionRow.from(rows);
                    instructions.put(row.id(), row);
                    revisionInstructions.add(row.asRevisionInstruction());
                }
            }
        }

        LinkedHashMap<Integer, VariableRow> variables = new LinkedHashMap<>();
        List<VariableLoadDTO> revisionVariables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,variable_type AS type,name,configured_value AS value,"
                        + "local_format,delimiter,producer_instruction_id AS instruction_id "
                        + "FROM bot_job_variable_definition"
                        + " WHERE home_banking_id=? AND bot_job_id=? ORDER BY id")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    VariableRow variable = VariableRow.from(rows);
                    variables.put(variable.id(), variable);
                    revisionVariables.add(new VariableLoadDTO(
                            variable.id(),
                            null,
                            botJobId,
                            variable.ownerInstructionId(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            0));
                }
            }
        }

        List<ReferenceRow> references = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,reference_type,value,instruction_id "
                        + "FROM reference WHERE bot_job_id=? ORDER BY id")) {
            statement.setInt(1, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) references.add(ReferenceRow.from(rows));
            }
        }

        return new AuthoritativeGraph(
                state,
                blocks,
                instructions,
                variables,
                List.copyOf(references),
                revisionService.revision(revisionInstructions, revisionVariables));
    }

    private void verifyFinalState(
            AuthoritativeGraph before,
            AuthoritativeGraph after,
            CopyPlan plan,
            Map<Integer, Integer> generatedInstructionIds,
            Map<Integer, Integer> generatedVariableIds,
            Map<Integer, ExpectedInstruction> expectedCopies,
            int copiedReferenceCount)
            throws MutationRefusedException {
        if (after.graphState().version() != before.graphState().version() + 1L) {
            throw refused(
                    "VARIABLE_COPY_VERSION_MISMATCH",
                    "The Variables graph version was not advanced exactly once.");
        }
        if (!before.blocks().equals(after.blocks())) {
            throw refused(
                    "VARIABLE_COPY_BLOCK_CATALOG_CHANGED",
                    "Instruction copy must preserve the Bot Job block catalog.");
        }
        if (after.instructions().size()
                != before.instructions().size() + generatedInstructionIds.size()) {
            throw refused(
                    "VARIABLE_COPY_INSTRUCTION_COUNT_MISMATCH",
                    "The final instruction count does not match the exact copy plan.");
        }
        for (Map.Entry<Integer, InstructionRow> source : before.instructions().entrySet()) {
            if (!source.getValue().equals(after.instructions().get(source.getKey()))) {
                throw refused(
                        "VARIABLE_COPY_SOURCE_CHANGED",
                        "Instruction copy changed existing instruction #" + source.getKey() + ".");
            }
        }
        for (InstructionRow source : plan.sourceRows()) {
            ExpectedInstruction expected = expectedCopies.get(source.id());
            InstructionRow copied =
                    expected == null
                            ? null
                            : after.instructions().get(expected.generatedInstructionId());
            if (copied == null
                    || !source.sameCopyFields(copied)
                    || copied.order() != expected.order()
                    || copied.blockId() != expected.blockId()
                    || !Objects.equals(copied.variableId(), expected.variableId())
                    || !Objects.equals(
                            copied.parentBlockId(), expected.parentBlockId())
                    || !Objects.equals(copied.parentId(), expected.parentId())) {
                throw refused(
                        "VARIABLE_COPY_FINAL_INSTRUCTION_MISMATCH",
                        "A generated instruction does not match the exact copy plan.");
            }
        }

        if (after.variables().size()
                != before.variables().size() + generatedVariableIds.size()) {
            throw refused(
                    "VARIABLE_COPY_VARIABLE_COUNT_MISMATCH",
                    "The final variable count does not match the exact copy plan.");
        }
        for (Map.Entry<Integer, VariableRow> source : before.variables().entrySet()) {
            if (!source.getValue().equals(after.variables().get(source.getKey()))) {
                throw refused(
                        "VARIABLE_COPY_SOURCE_VARIABLE_CHANGED",
                        "Instruction copy changed existing variable #" + source.getKey() + ".");
            }
        }
        for (Map.Entry<Integer, Integer> generated : generatedVariableIds.entrySet()) {
            VariableRow source = before.variables().get(generated.getKey());
            VariableRow copied = after.variables().get(generated.getValue());
            Integer generatedOwner =
                    source == null
                            ? null
                            : generatedInstructionIds.get(source.ownerInstructionId());
            if (source == null
                    || copied == null
                    || !source.sameCopyFields(copied)
                    || !Objects.equals(copied.ownerInstructionId(), generatedOwner)) {
                throw refused(
                        "VARIABLE_COPY_FINAL_VARIABLE_MISMATCH",
                        "A generated variable does not match the exact copy plan.");
            }
        }

        int actualCopiedReferences = 0;
        for (ReferenceRow reference : after.references()) {
            if (generatedInstructionIds.containsValue(reference.instructionId())) {
                actualCopiedReferences++;
            }
        }
        if (actualCopiedReferences != copiedReferenceCount
                || after.references().size()
                        != before.references().size() + copiedReferenceCount) {
            throw refused(
                    "VARIABLE_COPY_REFERENCE_COUNT_MISMATCH",
                    "The final locator-reference count does not match the exact copy plan.");
        }
        for (ReferenceRow source : before.references()) {
            if (!after.references().contains(source)) {
                throw refused(
                        "VARIABLE_COPY_SOURCE_REFERENCE_CHANGED",
                        "Instruction copy changed an existing locator reference.");
            }
        }
    }

    private void requireOwnedBotJob(Connection connection, OwnerKey owner)
            throws SQLException {
        if (owner.workspaceKind()
                != com.allinweb.ch.model.InstructionGraphMutationV3.WorkspaceKind.BOT_JOB) {
            throw refused(
                    "VARIABLE_COPY_BOT_JOB_REQUIRED",
                    "Variables instruction copy requires a Bot Job owner.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM bot_job WHERE id=? AND home_banking_id=?")) {
            statement.setInt(1, owner.ownerId());
            statement.setInt(2, owner.homeBankingId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw refused(
                            "VARIABLE_COPY_OWNER_MISMATCH",
                            "The authenticated organization does not own this Bot Job.");
                }
            }
        }
    }

    private boolean isExternalNavigation(InstructionRow source) {
        return CommandRegistry.isCrossBlockNavigation(
                source.actions() == null ? null : source.actions().toString(),
                source.parentBlockId(),
                source.blockId());
    }

    private void bindInstructionInsert(
            PreparedStatement statement,
            InstructionRow source,
            int order,
            int blockId,
            int botJobId)
            throws SQLException {
        int parameter = 1;
        statement.setInt(parameter++, order);
        statement.setObject(parameter++, source.actions());
        statement.setObject(parameter++, source.name());
        statement.setObject(parameter++, source.xpath());
        statement.setObject(parameter++, source.coordinates());
        statement.setObject(parameter++, source.forceCoordinates());
        statement.setObject(parameter++, source.iframeXPath());
        statement.setObject(parameter++, source.tagName());
        statement.setObject(parameter++, source.shadowHost());
        statement.setObject(parameter++, source.shadowRoot());
        statement.setObject(parameter++, source.cssSelector());
        statement.setObject(parameter++, source.description());
        statement.setObject(parameter++, source.operation());
        statement.setObject(parameter++, source.optional());
        statement.setObject(parameter++, source.blockMarked());
        statement.setObject(parameter++, source.defaultValue());
        statement.setObject(parameter++, source.actionCustomMaxWaitSec());
        statement.setObject(parameter++, source.onHoldSeconds());
        statement.setObject(parameter++, source.codified());
        statement.setObject(parameter++, source.exportToAbr());
        statement.setObject(parameter++, source.active());
        statement.setInt(parameter++, blockId);
        statement.setNull(parameter++, Types.INTEGER);
        statement.setNull(parameter++, Types.INTEGER);
        statement.setNull(parameter++, Types.INTEGER);
        statement.setInt(parameter++, botJobId);
        statement.setObject(parameter, source.clientNamed());
    }

    private int generatedId(PreparedStatement statement, String entity)
            throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException(entity + " insert returned no generated ID.");
            }
            return keys.getInt(1);
        }
    }

    private static Integer nullableInteger(ResultSet rows, String column)
            throws SQLException {
        Object value = rows.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static void bindNullableInteger(
            PreparedStatement statement, int parameter, Integer value)
            throws SQLException {
        if (value == null) statement.setNull(parameter, Types.INTEGER);
        else statement.setInt(parameter, value);
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

    public record CopyResult(
            OwnerKey owner,
            long workspaceEpoch,
            String requestId,
            VariablesInstructionCopyV1.Scope scope,
            int selectedInstructionId,
            int targetBlockId,
            List<Integer> sourceInstructionIds,
            Map<Integer, Integer> generatedInstructionIds,
            Map<Integer, Integer> generatedVariableIds,
            int copiedReferenceCount,
            long previousGraphVersion,
            long committedGraphVersion,
            String graphRevision,
            boolean duplicate) {

        public CopyResult {
            sourceInstructionIds = List.copyOf(sourceInstructionIds);
            generatedInstructionIds = immutableOrderedMap(generatedInstructionIds);
            generatedVariableIds = immutableOrderedMap(generatedVariableIds);
        }

        public CopyResult asDuplicate() {
            return new CopyResult(
                    owner,
                    workspaceEpoch,
                    requestId,
                    scope,
                    selectedInstructionId,
                    targetBlockId,
                    sourceInstructionIds,
                    generatedInstructionIds,
                    generatedVariableIds,
                    copiedReferenceCount,
                    previousGraphVersion,
                    committedGraphVersion,
                    graphRevision,
                    true);
        }
    }

    private static <K, V> Map<K, V> immutableOrderedMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    enum TransactionPhase {
        AFTER_INSTRUCTIONS_INSERTED,
        AFTER_VARIABLES_INSERTED,
        AFTER_GRAPH_COPIED,
        AFTER_VERSION_ADVANCE,
        AFTER_FINAL_VERIFICATION
    }

    @FunctionalInterface
    interface FaultInjector {
        void at(TransactionPhase phase) throws SQLException;
    }

    private record CopyPlan(
            VariablesInstructionCopyV1.Scope scope,
            int targetBlockId,
            int selectedInstructionId,
            LinkedHashSet<Integer> sourceInstructionIds,
            List<InstructionRow> sourceRows,
            LinkedHashMap<Integer, VariableRow> variablesToClone) {

        private CopyPlan {
            sourceInstructionIds =
                    new LinkedHashSet<>(sourceInstructionIds);
            sourceRows = List.copyOf(sourceRows);
            variablesToClone = new LinkedHashMap<>(variablesToClone);
        }
    }

    private record AuthoritativeGraph(
            GraphState graphState,
            LinkedHashMap<Integer, BlockRow> blocks,
            LinkedHashMap<Integer, InstructionRow> instructions,
            LinkedHashMap<Integer, VariableRow> variables,
            List<ReferenceRow> references,
            String graphRevision) {

        private AuthoritativeGraph {
            blocks = new LinkedHashMap<>(blocks);
            instructions = new LinkedHashMap<>(instructions);
            variables = new LinkedHashMap<>(variables);
            references = List.copyOf(references);
        }
    }

    private record BlockRow(int id, int order) {}

    private record Relationship(Integer parentId, Integer parentBlockId) {
        private static Relationship disconnected() {
            return new Relationship(null, null);
        }
    }

    private record ExpectedInstruction(
            int generatedInstructionId,
            int order,
            int blockId,
            Integer variableId,
            Integer parentBlockId,
            Integer parentId) {}

    private record VariableRow(
            int id,
            Object type,
            Object name,
            Object value,
            Object localFormat,
            Object delimiter,
            Integer ownerInstructionId) {

        private static VariableRow from(ResultSet rows) throws SQLException {
            return new VariableRow(
                    rows.getInt("id"),
                    rows.getObject("type"),
                    rows.getObject("name"),
                    rows.getObject("value"),
                    rows.getObject("local_format"),
                    rows.getObject("delimiter"),
                    nullableInteger(rows, "instruction_id"));
        }

        private boolean sameCopyFields(VariableRow other) {
            return other != null
                    && Objects.equals(type, other.type)
                    && Objects.equals(name, other.name)
                    && Objects.equals(value, other.value)
                    && Objects.equals(localFormat, other.localFormat)
                    && Objects.equals(delimiter, other.delimiter);
        }
    }

    private record ReferenceRow(
            int id, Object referenceType, Object value, Integer instructionId) {

        private static ReferenceRow from(ResultSet rows) throws SQLException {
            return new ReferenceRow(
                    rows.getInt("id"),
                    rows.getObject("reference_type"),
                    rows.getObject("value"),
                    nullableInteger(rows, "instruction_id"));
        }
    }

    private record InstructionRow(
            int id,
            int order,
            Object actions,
            Object name,
            Object xpath,
            Object coordinates,
            Object forceCoordinates,
            Object iframeXPath,
            Object tagName,
            Object shadowHost,
            Object shadowRoot,
            Object cssSelector,
            Object description,
            Object operation,
            Object optional,
            Object blockMarked,
            Object defaultValue,
            Object actionCustomMaxWaitSec,
            Object onHoldSeconds,
            Object codified,
            Object exportToAbr,
            Object active,
            int blockId,
            Integer variableId,
            Integer parentBlockId,
            Integer parentId,
            int botJobId,
            Object clientNamed) {

        private static InstructionRow from(ResultSet rows) throws SQLException {
            return new InstructionRow(
                    rows.getInt("id"),
                    rows.getInt("instruction_order_number"),
                    rows.getObject("actions"),
                    rows.getObject("name"),
                    rows.getObject("xpath"),
                    rows.getObject("coordinates"),
                    rows.getObject("force_coordinates"),
                    rows.getObject("iframe_xpath"),
                    rows.getObject("tag_name"),
                    rows.getObject("shadow_host"),
                    rows.getObject("shadow_root"),
                    rows.getObject("css_selector"),
                    rows.getObject("description"),
                    rows.getObject("operation"),
                    rows.getObject("optional"),
                    rows.getObject("block_marked"),
                    rows.getObject("default_value"),
                    rows.getObject("action_custom_max_wait_sec"),
                    rows.getObject("on_hold_seconds"),
                    rows.getObject("codified"),
                    rows.getObject("export_to_abr"),
                    rows.getObject("active"),
                    rows.getInt("block_id"),
                    nullableInteger(rows, "variable_id"),
                    nullableInteger(rows, "parent_block_id"),
                    nullableInteger(rows, "parent_id"),
                    rows.getInt("bot_job_id"),
                    rows.getObject("client_named"));
        }

        private InstructionLoad asRevisionInstruction() {
            InstructionLoad row = new InstructionLoad();
            row.setId(id);
            row.setInstructionOrderNumber(order);
            row.setActions(actions == null ? null : actions.toString());
            row.setOperation(operation == null ? null : operation.toString());
            row.setBlockId(blockId);
            row.setVariableId(variableId);
            row.setParentBlockId(parentBlockId);
            row.setParentId(parentId);
            return row;
        }

        private boolean sameCopyFields(InstructionRow other) {
            return other != null
                    && Objects.equals(actions, other.actions)
                    && Objects.equals(name, other.name)
                    && Objects.equals(xpath, other.xpath)
                    && Objects.equals(coordinates, other.coordinates)
                    && Objects.equals(forceCoordinates, other.forceCoordinates)
                    && Objects.equals(iframeXPath, other.iframeXPath)
                    && Objects.equals(tagName, other.tagName)
                    && Objects.equals(shadowHost, other.shadowHost)
                    && Objects.equals(shadowRoot, other.shadowRoot)
                    && Objects.equals(cssSelector, other.cssSelector)
                    && Objects.equals(description, other.description)
                    && Objects.equals(operation, other.operation)
                    && Objects.equals(optional, other.optional)
                    && Objects.equals(blockMarked, other.blockMarked)
                    && Objects.equals(defaultValue, other.defaultValue)
                    && Objects.equals(
                            actionCustomMaxWaitSec, other.actionCustomMaxWaitSec)
                    && Objects.equals(onHoldSeconds, other.onHoldSeconds)
                    && Objects.equals(codified, other.codified)
                    && Objects.equals(exportToAbr, other.exportToAbr)
                    && Objects.equals(active, other.active)
                    && Objects.equals(clientNamed, other.clientNamed);
        }
    }
}
