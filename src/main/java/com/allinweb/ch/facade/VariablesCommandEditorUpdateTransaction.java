package com.allinweb.ch.facade;

import com.allinweb.ch.db.InstructionGraphStateRepository;
import com.allinweb.ch.db.InstructionVariableCommandConfigRepository;
import com.allinweb.ch.db.InstructionVariableCommandConfigRepository.StoredConfiguration;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceResult;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceStatus;
import com.allinweb.ch.db.InstructionGraphStateRepository.GraphState;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.model.InstructionGraphMutationV3.WorkspaceKind;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.VariableLoadDTO;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Configuration;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.ConfigurationKind;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Placement;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.PlacementKind;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Atomic persistence dedicated to UPDATE from the new Variables Command Editor modal. */
public final class VariablesCommandEditorUpdateTransaction {
    private static final int ORDER_OFFSET = 1_000_000;
    private static final Set<String> CONDITIONAL_BOUNDARIES =
            Set.of("IF", "ELSEIF", "ELSE", "ENDIF");
    private final InstructionGraphStateRepository stateRepository;
    private final InstructionGraphRevisionService revisionService;
    private final InstructionVariableCommandConfigRepository commandConfigurations;
    private final VariablesConditionalFamilyDissolvePersistence conditionalFamilyDissolve =
            new VariablesConditionalFamilyDissolvePersistence();

    public VariablesCommandEditorUpdateTransaction() {
        this(
                new InstructionGraphStateRepository(),
                new InstructionGraphRevisionService(),
                new InstructionVariableCommandConfigRepository());
    }

    VariablesCommandEditorUpdateTransaction(
            InstructionGraphStateRepository stateRepository,
            InstructionGraphRevisionService revisionService) {
        this(stateRepository, revisionService, new InstructionVariableCommandConfigRepository());
    }

    VariablesCommandEditorUpdateTransaction(
            InstructionGraphStateRepository stateRepository,
            InstructionGraphRevisionService revisionService,
            InstructionVariableCommandConfigRepository commandConfigurations) {
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.revisionService = Objects.requireNonNull(revisionService, "revisionService");
        this.commandConfigurations = Objects.requireNonNull(
                commandConfigurations, "commandConfigurations");
    }

    public UpdateResult execute(
            Connection connection,
            AuthenticatedBotJob owner,
            VariablesCommandEditorUpdateV1.Request request)
            throws SQLException {
        requireOpen(connection);
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");
        if (!connection.getAutoCommit()) {
            throw new SQLException("Variables command update requires an unbound connection.");
        }
        connection.setAutoCommit(false);
        boolean restoreAutoCommit = true;
        try {
            requireOwner(connection, owner.owner());
            GraphState state = stateRepository.loadOrCreate(connection, owner.owner());
            Graph before = loadGraph(connection, owner.owner(), state);
            Plan plan = validateAndPlan(owner, request, before);
            conditionalFamilyDissolve.deleteSiblings(
                    connection,
                    owner.owner(),
                    plan.conditionalFamilyDeleteIds());
            persistConfiguration(
                    connection,
                    owner.owner().homeBankingId(),
                    owner.owner().ownerId(),
                    plan);
            persistPlacement(connection, owner.owner().ownerId(), plan);

            AdvanceResult advance = stateRepository.compareAndSetIncrement(
                    connection, owner.owner(), request.baseGraphVersion());
            if (!advance.advanced()) {
                throw refused(
                        advance.status() == AdvanceStatus.MISSING
                                ? "COMMAND_UPDATE_GRAPH_STATE_MISSING"
                                : "COMMAND_UPDATE_GRAPH_VERSION_STALE",
                        "The Variables graph changed before the command update completed.");
            }
            Graph after = loadGraph(connection, owner.owner(), advance.state());
            verify(plan, after);
            verifyTypedConfiguration(
                    connection,
                    owner.owner().homeBankingId(),
                    owner.owner().ownerId(),
                    plan);
            connection.commit();
            return new UpdateResult(
                    owner.owner(), owner.workspaceEpoch(), request.requestId().trim(),
                    plan.source().id(), plan.targetBlockId(), plan.finalOrder(),
                    state.version(), advance.state().version(), after.revision(), false);
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
            VariablesCommandEditorUpdateV1.Request request,
            Graph graph) throws MutationRefusedException {
        if (!Objects.equals(request.contractVersion(), VariablesCommandEditorUpdateV1.CONTRACT_VERSION)) {
            throw refused("COMMAND_UPDATE_CONTRACT_UNSUPPORTED", "The command-update contract is not supported.");
        }
        if (blank(request.requestId())) {
            throw refused("COMMAND_UPDATE_REQUEST_ID_REQUIRED", "A command-update request ID is required.");
        }
        if (blank(request.bindingEpoch())) {
            throw refused("COMMAND_UPDATE_BINDING_REQUIRED", "The Variables binding epoch is required.");
        }
        if (!Objects.equals(request.workspaceEpoch(), owner.workspaceEpoch())) {
            throw refused("COMMAND_UPDATE_WORKSPACE_CHANGED", "The Bot Job workspace changed before the command update.");
        }
        if (!Objects.equals(request.baseGraphVersion(), graph.state().version())) {
            throw refused("COMMAND_UPDATE_GRAPH_VERSION_STALE", "The Variables graph version changed before the command update.");
        }
        if (blank(request.graphRevision()) || !request.graphRevision().trim().equals(graph.revision())) {
            throw refused("COMMAND_UPDATE_GRAPH_REVISION_STALE", "The Variables graph changed before the command update.");
        }
        InstructionRow source = graph.instructions().get(request.sourceInstructionId());
        if (source == null) {
            throw refused("COMMAND_UPDATE_SOURCE_MISSING", "The selected command no longer exists.");
        }
        if (request.targetBlockId() == null || !graph.blockIds().contains(request.targetBlockId())) {
            throw refused("COMMAND_UPDATE_TARGET_BLOCK_INVALID", "Select a current target Block.");
        }
        String sourceAction = CommandRegistry.canonicalize(source.action());
        String targetAction = blank(request.targetAction())
                ? sourceAction
                : CommandRegistry.canonicalize(request.targetAction());
        boolean commandChanged = !targetAction.equals(sourceAction);
        if (commandChanged && !CommandRegistry.isEditorTargetable(targetAction)) {
            throw refused(
                    "COMMAND_UPDATE_TARGET_ACTION_INVALID",
                    "The selected target command is not supported by the Command Editor.");
        }
        // PARKED 2026-08-03 (FE owns IF-family rules): React does not offer transforming a command
        // into an IF-family boundary; the refusal moved to the client.
        // if (commandChanged && CONDITIONAL_BOUNDARIES.contains(targetAction)) {
        //     throw refused(
        //             "COMMAND_UPDATE_CONDITIONAL_TARGET_REQUIRES_ADD",
        //             "Use ADD Command to create IF or ELSEIF so its complete family is generated.");
        // }
        Configuration configuration = requireConfiguration(
                graph, targetAction, request.configuration());
        Placement placement = request.placement();
        if (placement == null || placement.kind() == null) {
            throw refused("COMMAND_UPDATE_PLACEMENT_REQUIRED", "Select a command placement.");
        }
        // PARKED 2026-08-03 (FE owns IF-family rules): the Command Editor locks a boundary's
        // placement to KEEP unless it is being transformed (ComponentEditorModal position lock).
        // if (!commandChanged && CONDITIONAL_BOUNDARIES.contains(sourceAction)
        //         && (request.targetBlockId() != source.blockId()
        //                 || placement.kind() != PlacementKind.KEEP)) {
        //     throw refused(
        //             "COMMAND_UPDATE_CONDITIONAL_FAMILY_TRANSFER_REQUIRED",
        //             "Keep an IF-family boundary in its current position, or transfer the complete family together.");
        // }

        // PARKED 2026-08-03 (FE owns IF-family rules): the dissolve scope is authored and
        // confirmed by React (commandEditorConditionalFamilyImpact.ts + warning modal). Java no
        // longer recomputes or cross-checks it — it persists the submitted boundary list.
        // Persistence still rolls back when a submitted row no longer exists
        // (VariablesConditionalFamilyDissolvePersistence.deleteBoundary).
        List<Integer> submittedConditionalFamilyDeleteIds =
                submittedConditionalFamilyDeleteIds(request, source);
        boolean conditionalFamilyDissolve = commandChanged
                && CONDITIONAL_BOUNDARIES.contains(sourceAction);
        // if (conditionalFamilyDissolve) {
        //     if (!Boolean.TRUE.equals(request.allowConditionalFamilyDissolve())) {
        //         throw refused(
        //                 "COMMAND_UPDATE_CONDITIONAL_CONFIRMATION_REQUIRED",
        //                 "Confirm removal of the remaining IF-family boundaries.");
        //     }
        //     ... expected-vs-submitted scope comparison removed with the parked
        //     expectedConditionalFamilyDeleteIds computation below ...
        // } else if (Boolean.TRUE.equals(request.allowConditionalFamilyDissolve())
        //         || !submittedConditionalFamilyDeleteIds.isEmpty()) {
        //     throw refused(
        //             "COMMAND_UPDATE_CONDITIONAL_SCOPE_UNEXPECTED",
        //             "IF-family removal is not valid for this command update.");
        // }
        List<Integer> conditionalFamilyDeleteIds = conditionalFamilyDissolve
                ? submittedConditionalFamilyDeleteIds
                : List.of();
        LinkedHashSet<Integer> sourceExclusions =
                new LinkedHashSet<>(conditionalFamilyDeleteIds);
        sourceExclusions.add(source.id());
        List<InstructionRow> sourceRows = rowsFor(
                graph, source.blockId(), sourceExclusions);
        List<InstructionRow> targetRows = source.blockId() == request.targetBlockId()
                ? sourceRows
                : rowsFor(graph, request.targetBlockId(), source.id());
        int targetIndex;
        if (placement.kind() == PlacementKind.KEEP) {
            if (source.blockId() != request.targetBlockId()) {
                throw refused("COMMAND_UPDATE_KEEP_CROSS_BLOCK", "Keep current position is available only in the current Block.");
            }
            targetIndex = (int) targetRows.stream()
                    .filter(row -> row.order() < source.order())
                    .count();
        } else if (placement.kind() == PlacementKind.TOP) {
            targetIndex = 0;
        } else if (placement.kind() == PlacementKind.END) {
            targetIndex = targetRows.size();
        } else {
            Integer referenceId = placement.referenceInstructionId();
            InstructionRow reference = graph.instructions().get(referenceId);
            if (reference == null || reference.id() == source.id()
                    || reference.blockId() != request.targetBlockId()) {
                throw refused("COMMAND_UPDATE_REFERENCE_INVALID", "Select a current instruction in the target Block.");
            }
            targetIndex = targetRows.indexOf(reference) + 1;
            if (targetIndex <= 0) {
                throw refused("COMMAND_UPDATE_REFERENCE_INVALID", "The placement reference is not in the target Block.");
            }
        }
        ArrayList<InstructionRow> finalTargetRows = new ArrayList<>(targetRows);
        finalTargetRows.add(targetIndex, source);
        RelationshipImpact relationshipImpact = relationshipImpact(
                source, request.targetBlockId(), finalTargetRows);
        if (!commandChanged
                && relationshipImpact.requiresDisconnect()
                && !Boolean.TRUE.equals(request.allowRelationshipDisconnect())) {
            throw refused(
                    "COMMAND_UPDATE_RELATIONSHIP_CONFIRMATION_REQUIRED",
                    "The selected placement breaks a parent or Block connection. Confirm the disconnection before continuing.");
        }
        return new Plan(
                source, request.targetBlockId(), configuration,
                List.copyOf(sourceRows), List.copyOf(finalTargetRows), targetIndex + 1,
                relationshipImpact, targetAction, commandChanged,
                conditionalFamilyDeleteIds);
    }

    /**
     * PARKED 2026-08-03 (FE owns IF-family rules): no longer called. React authors the dissolve
     * scope (commandEditorConditionalFamilyImpact.ts) and Java persists it. Kept for reference
     * until the FE-owned contract is user-accepted.
     */
    @SuppressWarnings("unused")
    private static List<Integer> expectedConditionalFamilyDeleteIds(
            Graph graph,
            InstructionRow source)
            throws MutationRefusedException {
        List<InstructionRow> boundaries = graph.instructions().values().stream()
                .filter(row -> row.blockId() == source.blockId())
                .filter(row -> CONDITIONAL_BOUNDARIES.contains(
                        CommandRegistry.canonicalize(row.action())))
                .sorted(Comparator.comparingInt(InstructionRow::order)
                        .thenComparingInt(InstructionRow::id))
                .toList();
        List<InstructionRow> roots = boundaries.stream()
                .filter(row -> "IF".equals(CommandRegistry.canonicalize(row.action())))
                .toList();
        List<InstructionRow> family;
        if (roots.size() == 1) {
            InstructionRow root = roots.get(0);
            if (source.id() != root.id() && !Objects.equals(source.parentId(), root.id())) {
                throw refused(
                        "COMMAND_UPDATE_CONDITIONAL_FAMILY_AMBIGUOUS",
                        "The selected boundary does not belong to the Block's IF root.");
            }
            family = boundaries.stream()
                    .filter(row -> row.id() == root.id()
                            || Objects.equals(row.parentId(), root.id()))
                    .toList();
            if (family.size() != boundaries.size()) {
                throw refused(
                        "COMMAND_UPDATE_CONDITIONAL_FAMILY_AMBIGUOUS",
                        "Repair the IF-family links before changing a boundary into another command.");
            }
        } else if (roots.isEmpty()) {
            LinkedHashSet<Integer> missingRootIds = boundaries.stream()
                    .map(InstructionRow::parentId)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (missingRootIds.size() > 1 || !isUnambiguousOrphanSuffix(boundaries)) {
                throw refused(
                        "COMMAND_UPDATE_CONDITIONAL_FAMILY_AMBIGUOUS",
                        "The orphan IF-family boundaries are ambiguous and were not changed.");
            }
            family = boundaries;
        } else {
            throw refused(
                    "COMMAND_UPDATE_CONDITIONAL_FAMILY_AMBIGUOUS",
                    "A Block with more than one IF root must be repaired before changing a boundary.");
        }
        return family.stream()
                .filter(row -> row.id() != source.id())
                .map(InstructionRow::id)
                .toList();
    }

    private static boolean isUnambiguousOrphanSuffix(List<InstructionRow> boundaries) {
        boolean elseSeen = false;
        boolean endifSeen = false;
        for (InstructionRow row : boundaries) {
            String action = CommandRegistry.canonicalize(row.action());
            if ("ELSEIF".equals(action)) {
                if (elseSeen || endifSeen) return false;
            } else if ("ELSE".equals(action)) {
                if (elseSeen || endifSeen) return false;
                elseSeen = true;
            } else if ("ENDIF".equals(action)) {
                if (endifSeen) return false;
                endifSeen = true;
            } else {
                return false;
            }
        }
        return !boundaries.isEmpty();
    }

    /**
     * PARKED 2026-08-03 (FE owns IF-family rules): the former per-id business validation
     * (boundary action, same-block, exactly-once refusals) moved to React. Java keeps only
     * persistence-grade sanitation — drop nulls, non-positive ids, duplicates, and the source
     * row itself, then trust the authored list.
     */
    private static List<Integer> submittedConditionalFamilyDeleteIds(
            VariablesCommandEditorUpdateV1.Request request,
            InstructionRow source) {
        List<Integer> submitted = request.conditionalFamilyDeleteIds() == null
                ? List.of()
                : request.conditionalFamilyDeleteIds();
        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        for (Integer instructionId : submitted) {
            if (instructionId == null || instructionId <= 0 || instructionId == source.id()) {
                continue;
            }
            unique.add(instructionId);
        }
        return List.copyOf(unique);
    }

    private Configuration requireConfiguration(
            Graph graph, String action, Configuration configuration)
            throws MutationRefusedException {
        if (configuration == null || configuration.kind() == null) {
            throw refused("COMMAND_UPDATE_CONFIGURATION_REQUIRED", "A typed command configuration is required.");
        }
        if (configuration.kind() == ConfigurationKind.NONE) {
            if (!CommandRegistry.hasNoEditorConfiguration(action)) {
                throw refused(
                        "COMMAND_UPDATE_CONFIGURATION_MISMATCH",
                        "The selected command requires its typed configuration.");
            }
            return configuration;
        }
        if (configuration.kind() == ConfigurationKind.LOOP && !"LOOP".equals(action)
                || configuration.kind() == ConfigurationKind.REFRESH_LOOP && !"REFRESH_LOOP".equals(action)
                || configuration.kind() == ConfigurationKind.WAIT && !"H".equals(action)
                || configuration.kind() == ConfigurationKind.CHECK_VALUE && !"CK".equals(action)
                || configuration.kind() == ConfigurationKind.EXTERNAL_CHECK
                        && !Set.of("CSV CHECK", "PDF CHECK").contains(action)
                || configuration.kind() == ConfigurationKind.EXCEL_WRITE && !"E".equals(action)
                || configuration.kind() == ConfigurationKind.GOTO && !"GOTO".equals(action)
                || configuration.kind() == ConfigurationKind.SWIPE
                        && !Set.of("SWIPE_UP", "SWIPE_DOWN").contains(action)
                // PARKED 2026-08-03 (FE owns IF-family rules): CONDITIONAL kind/action match is
                // authored by React.
                // || configuration.kind() == ConfigurationKind.CONDITIONAL
                //         && !Set.of("IF", "ELSEIF").contains(action)
        ) {
            throw refused("COMMAND_UPDATE_CONFIGURATION_MISMATCH", "The submitted configuration does not match the selected command.");
        }
        if (configuration.kind() == ConfigurationKind.WAIT) {
            requireEditorInteger(configuration.waitSeconds(), "Wait seconds");
        } else if (configuration.kind() == ConfigurationKind.LOOP
                || configuration.kind() == ConfigurationKind.REFRESH_LOOP) {
            requireEditorInteger(configuration.intervalSeconds(), "Interval seconds");
            requireEditorInteger(configuration.iterations(), "Iterations");
        } else if (configuration.kind() == ConfigurationKind.CHECK_VALUE
                || configuration.kind() == ConfigurationKind.EXTERNAL_CHECK) {
            requireVariableOnlyCheck(graph, configuration);
        } else if (configuration.kind() == ConfigurationKind.EXCEL_WRITE
                && blank(configuration.outputKey())) {
            throw refused("COMMAND_UPDATE_OUTPUT_KEY_REQUIRED", "Enter an ExcelWrite output key.");
        } else if (configuration.kind() == ConfigurationKind.GOTO
                || configuration.kind() == ConfigurationKind.SWIPE) {
            requireEditorInteger(configuration.count(), "Repetition count");
        }
        // PARKED 2026-08-03 (FE owns IF-family rules): the IF/ELSEIF condition content is
        // validated by React; Java persists the authored configuration verbatim.
        // else if (configuration.kind() == ConfigurationKind.CONDITIONAL) {
        //     requireConditional(graph, configuration);
        // }
        return configuration;
    }

    private void persistConfiguration(
            Connection connection, int homeBankingId, int botJobId, Plan plan)
            throws SQLException {
        Configuration configuration = plan.configuration();
        if (plan.commandChanged()) {
            persistCommandChange(connection, homeBankingId, botJobId, plan);
            return;
        }
        if (configuration.kind() == ConfigurationKind.NONE) return;
        if (isTypedVariableConfiguration(configuration.kind())) {
            commandConfigurations.upsert(
                    connection,
                    homeBankingId,
                    botJobId,
                    plan.source().id(),
                    plan.targetAction(),
                    configuration);
            if (isVariableOnlyCheck(configuration.kind())) {
                persistVariableOnlyCheckConnections(
                        connection, homeBankingId, botJobId, plan);
            }
            return;
        }
        String sql = configuration.kind() == ConfigurationKind.WAIT
                ? "UPDATE instruction SET on_hold_seconds=? WHERE id=? AND bot_job_id=?"
                : "UPDATE instruction SET operation=? WHERE id=? AND bot_job_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (configuration.kind() == ConfigurationKind.WAIT) {
                statement.setInt(1, configuration.waitSeconds());
            } else if (configuration.kind() == ConfigurationKind.LOOP
                    || configuration.kind() == ConfigurationKind.REFRESH_LOOP) {
                statement.setString(1, configuration.intervalSeconds() + ":" + configuration.iterations());
            } else if (configuration.kind() == ConfigurationKind.GOTO
                    || configuration.kind() == ConfigurationKind.SWIPE) {
                statement.setString(1, String.valueOf(configuration.count()));
            }
            statement.setInt(2, plan.source().id());
            statement.setInt(3, botJobId);
            if (statement.executeUpdate() != 1) {
                throw refused("COMMAND_UPDATE_SOURCE_CHANGED", "The selected command changed before it could be updated.");
            }
        }
    }

    /**
     * Rewrites the instruction into the selected target command: actions column, intrinsic
     * values (operation / on_hold_seconds), variable binding, and the shadow configuration.
     * Owned variables are released into memory (producer cleared), never deleted.
     */
    private void persistCommandChange(
            Connection connection, int homeBankingId, int botJobId, Plan plan)
            throws SQLException {
        Configuration configuration = plan.configuration();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE instruction SET actions=?,name=?,operation=?,on_hold_seconds=?,"
                        + "parent_id=NULL,parent_block_id=NULL"
                        + " WHERE id=? AND bot_job_id=?")) {
            statement.setString(1, plan.targetAction());
            statement.setString(2, CommandRegistry.transformedName(plan.targetAction()));
            String operation = configuration.kind() == ConfigurationKind.LOOP
                            || configuration.kind() == ConfigurationKind.REFRESH_LOOP
                    ? configuration.intervalSeconds() + ":" + configuration.iterations()
                    : configuration.kind() == ConfigurationKind.GOTO
                                    || configuration.kind() == ConfigurationKind.SWIPE
                            ? String.valueOf(configuration.count())
                            : null;
            if (operation == null) statement.setNull(3, java.sql.Types.VARCHAR);
            else statement.setString(3, operation);
            if (configuration.kind() == ConfigurationKind.WAIT) {
                statement.setInt(4, configuration.waitSeconds());
            } else {
                statement.setNull(4, java.sql.Types.INTEGER);
            }
            statement.setInt(5, plan.source().id());
            statement.setInt(6, botJobId);
            if (statement.executeUpdate() != 1) {
                throw refused("COMMAND_UPDATE_SOURCE_CHANGED", "The selected command changed before it could be transformed.");
            }
        }
        clearVariableSlots(connection, homeBankingId, botJobId, plan.source().id());
        if (isVariableOnlyCheck(configuration.kind())
                && configuration.leftVariableId() != null) {
            insertVariableSlot(
                    connection, homeBankingId, botJobId,
                    plan.source().id(), "LEFT", configuration.leftVariableId());
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE bot_job_variable_definition SET producer_instruction_id=NULL"
                        + " WHERE home_banking_id=? AND bot_job_id=? AND producer_instruction_id=?")) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            statement.setInt(3, plan.source().id());
            statement.executeUpdate();
        }
        // A command-type transformation is a full relationship reset. Release every
        // direct child so React can reconnect it explicitly under the target policy.
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE instruction SET parent_id=NULL,parent_block_id=NULL"
                        + " WHERE bot_job_id=? AND parent_id=?")) {
            statement.setInt(1, botJobId);
            statement.setInt(2, plan.source().id());
            statement.executeUpdate();
        }
        if (isTypedVariableConfiguration(configuration.kind())) {
            commandConfigurations.upsert(
                    connection, homeBankingId, botJobId,
                    plan.source().id(), plan.targetAction(), configuration);
        } else {
            commandConfigurations.delete(connection, homeBankingId, botJobId, plan.source().id());
        }
    }

    private void persistVariableOnlyCheckConnections(
            Connection connection, int homeBankingId, int botJobId, Plan plan)
            throws SQLException {
        clearVariableSlot(
                connection, homeBankingId, botJobId, plan.source().id(), "LEFT");
        Integer leftVariableId = plan.configuration().leftVariableId();
        if (leftVariableId != null) {
            insertVariableSlot(
                    connection, homeBankingId, botJobId,
                    plan.source().id(), "LEFT", leftVariableId);
        }
    }

    private static void clearVariableSlots(
            Connection connection, int homeBankingId, int botJobId, int instructionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM instruction_variable_slot"
                        + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=?")) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            statement.setInt(3, instructionId);
            statement.executeUpdate();
        }
    }

    private static void clearVariableSlot(
            Connection connection, int homeBankingId, int botJobId,
            int instructionId, String slot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM instruction_variable_slot"
                        + " WHERE home_banking_id=? AND bot_job_id=?"
                        + " AND instruction_id=? AND slot=?")) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            statement.setInt(3, instructionId);
            statement.setString(4, slot);
            statement.executeUpdate();
        }
    }

    private static void insertVariableSlot(
            Connection connection, int homeBankingId, int botJobId,
            int instructionId, String slot, int variableId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO instruction_variable_slot"
                        + " (home_banking_id,bot_job_id,instruction_id,slot,variable_id,"
                        + "slot_revision,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            statement.setInt(3, instructionId);
            statement.setString(4, slot);
            statement.setInt(5, variableId);
            statement.executeUpdate();
        }
    }

    private void persistPlacement(Connection connection, int botJobId, Plan plan)
            throws SQLException {
        boolean crossBlock = plan.source().blockId() != plan.targetBlockId();
        LinkedHashSet<Integer> affectedBlocks = new LinkedHashSet<>();
        affectedBlocks.add(plan.source().blockId());
        affectedBlocks.add(plan.targetBlockId());
        for (Integer blockId : affectedBlocks) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE instruction SET instruction_order_number=instruction_order_number+? WHERE bot_job_id=? AND block_id=?")) {
                statement.setInt(1, ORDER_OFFSET);
                statement.setInt(2, botJobId);
                statement.setInt(3, blockId);
                statement.executeUpdate();
            }
        }
        if (crossBlock) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE instruction SET block_id=?,parent_id=NULL,parent_block_id=NULL WHERE id=? AND bot_job_id=?")) {
                statement.setInt(1, plan.targetBlockId());
                statement.setInt(2, plan.source().id());
                statement.setInt(3, botJobId);
                if (statement.executeUpdate() != 1) {
                    throw refused("COMMAND_UPDATE_MOVE_FAILED", "The command could not be moved to the target Block.");
                }
            }
            updateOrders(connection, botJobId, plan.sourceRows(), plan.source().id());
        } else {
            clearInvalidRelationships(connection, botJobId, plan);
        }
        updateOrders(connection, botJobId, plan.targetRows(), null);
    }

    private void clearInvalidRelationships(Connection connection, int botJobId, Plan plan)
            throws SQLException {
        RelationshipImpact impact = plan.relationshipImpact();
        if (!impact.requiresDisconnect()) return;
        String assignment = impact.clearParentId() && impact.clearParentBlockId()
                ? "parent_id=NULL,parent_block_id=NULL"
                : impact.clearParentId() ? "parent_id=NULL" : "parent_block_id=NULL";
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE instruction SET " + assignment + " WHERE id=? AND bot_job_id=?")) {
            statement.setInt(1, plan.source().id());
            statement.setInt(2, botJobId);
            if (statement.executeUpdate() != 1) {
                throw refused(
                        "COMMAND_UPDATE_RELATIONSHIP_CLEAR_FAILED",
                        "The invalid instruction connection could not be cleared.");
            }
        }
    }

    private void updateOrders(
            Connection connection, int botJobId, List<InstructionRow> rows, Integer excludedId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE instruction SET instruction_order_number=? WHERE id=? AND bot_job_id=?")) {
            int order = 0;
            for (InstructionRow row : rows) {
                if (Objects.equals(row.id(), excludedId)) continue;
                order++;
                statement.setInt(1, order);
                statement.setInt(2, row.id());
                statement.setInt(3, botJobId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void verify(Plan plan, Graph after) throws MutationRefusedException {
        InstructionRow updated = after.instructions().get(plan.source().id());
        if (updated == null || updated.blockId() != plan.targetBlockId()
                || updated.order() != plan.finalOrder()) {
            throw refused("COMMAND_UPDATE_VERIFICATION_FAILED", "The committed command position could not be verified.");
        }
        if ((plan.relationshipImpact().clearParentId() && updated.parentId() != null)
                || (plan.relationshipImpact().clearParentBlockId()
                        && updated.parentBlockId() != null)) {
            throw refused(
                    "COMMAND_UPDATE_RELATIONSHIP_NOT_CLEARED",
                    "The invalid instruction connection was not cleared.");
        }
        Configuration configuration = plan.configuration();
        if (plan.commandChanged()) {
            Set<Integer> deletedBoundaryIds =
                    Set.copyOf(plan.conditionalFamilyDeleteIds());
            if (deletedBoundaryIds.stream().anyMatch(after.instructions()::containsKey)
                    || after.instructions().values().stream()
                            .anyMatch(row -> row.parentId() != null
                                    && deletedBoundaryIds.contains(row.parentId()))) {
                throw refused(
                        "COMMAND_UPDATE_CONDITIONAL_FAMILY_NOT_REMOVED",
                        "The previous IF-family boundaries were not removed completely.");
            }
            if (!plan.targetAction().equals(CommandRegistry.canonicalize(updated.action()))) {
                throw refused(
                        "COMMAND_UPDATE_VERIFICATION_FAILED",
                        "The committed command type could not be verified.");
            }
            boolean expectsNullOperation = configuration.kind() == ConfigurationKind.WAIT
                    || configuration.kind() == ConfigurationKind.NONE
                    || isTypedVariableConfiguration(configuration.kind());
            if (expectsNullOperation && updated.operation() != null) {
                throw refused(
                        "COMMAND_UPDATE_VERIFICATION_FAILED",
                        "The transformed command still carries the previous intrinsic values.");
            }
            String expectedName = CommandRegistry.transformedName(plan.targetAction());
            if (!Objects.equals(expectedName, updated.name())) {
                throw refused(
                        "COMMAND_UPDATE_VERIFICATION_FAILED",
                        "The transformed command name could not be verified.");
            }
            Integer expectedVariableId = isVariableOnlyCheck(configuration.kind())
                    ? configuration.leftVariableId()
                    : null;
            if (updated.parentId() != null || updated.parentBlockId() != null
                    || !Objects.equals(updated.variableId(), expectedVariableId)) {
                throw refused(
                        "COMMAND_UPDATE_VERIFICATION_FAILED",
                        "The transformed command still carries previous connections.");
            }
            if (after.instructions().values().stream()
                    .anyMatch(row -> Objects.equals(row.parentId(), plan.source().id()))) {
                throw refused(
                        "COMMAND_UPDATE_VERIFICATION_FAILED",
                        "Children of the transformed command were not released.");
            }
            if (configuration.kind() != ConfigurationKind.WAIT
                    && updated.onHoldSeconds() != null) {
                throw refused(
                        "COMMAND_UPDATE_VERIFICATION_FAILED",
                        "The transformed command still carries the previous Wait value.");
            }
        }
        if (isVariableOnlyCheck(configuration.kind())
                && (updated.parentId() != null
                        || updated.parentBlockId() != null
                        || !Objects.equals(updated.variableId(), configuration.leftVariableId()))) {
            throw refused(
                    "COMMAND_UPDATE_CHECK_CONNECTION_NOT_COMMITTED",
                    "The variable-only Check connection could not be verified.");
        }
        if (configuration.kind() == ConfigurationKind.WAIT) {
            if (!Objects.equals(updated.onHoldSeconds(), configuration.waitSeconds())) {
                throw refused("COMMAND_UPDATE_VERIFICATION_FAILED", "The committed Wait value could not be verified.");
            }
        } else if (configuration.kind() == ConfigurationKind.LOOP
                || configuration.kind() == ConfigurationKind.REFRESH_LOOP) {
            String expected = configuration.intervalSeconds() + ":" + configuration.iterations();
            if (!expected.equals(updated.operation())) {
                throw refused("COMMAND_UPDATE_VERIFICATION_FAILED", "The committed loop values could not be verified.");
            }
        } else if (configuration.kind() == ConfigurationKind.GOTO
                || configuration.kind() == ConfigurationKind.SWIPE) {
            if (!String.valueOf(configuration.count()).equals(updated.operation())) {
                throw refused(
                        "COMMAND_UPDATE_VERIFICATION_FAILED",
                        "The committed repetition count could not be verified.");
            }
        }
        LinkedHashSet<Integer> verifiedBlocks = new LinkedHashSet<>();
        verifiedBlocks.add(plan.source().blockId());
        verifiedBlocks.add(plan.targetBlockId());
        for (Integer blockId : verifiedBlocks) {
            List<InstructionRow> rows = rowsFor(after, blockId, -1);
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).order() != index + 1) {
                    throw refused("COMMAND_UPDATE_ORDER_INVALID", "The command order was not saved contiguously.");
                }
            }
        }
    }

    private Graph loadGraph(Connection connection, OwnerKey owner, GraphState state)
            throws SQLException {
        LinkedHashSet<Integer> blockIds = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM block WHERE bot_job_id=? ORDER BY block_order_number,id")) {
            statement.setInt(1, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) blockIds.add(rows.getInt("id"));
            }
        }
        LinkedHashMap<Integer, InstructionRow> instructions = new LinkedHashMap<>();
        List<InstructionLoad> revisionRows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,instruction_order_number,actions,name,operation,on_hold_seconds,"
                        + "block_id,parent_block_id,parent_id,"
                        + "(SELECT ivs.variable_id FROM instruction_variable_slot ivs"
                        + " WHERE ivs.home_banking_id=? AND ivs.bot_job_id=instruction.bot_job_id"
                        + " AND ivs.instruction_id=instruction.id"
                        + " AND ivs.slot=CASE UPPER(TRIM(instruction.actions))"
                        + " WHEN 'CK' THEN 'LEFT' WHEN 'CHECKVALUE' THEN 'LEFT'"
                        + " WHEN 'CSV CHECK' THEN 'LEFT' WHEN 'PDF CHECK' THEN 'LEFT'"
                        + " WHEN 'GET' THEN 'GET_WRITE' WHEN 'SET' THEN 'READ_SET'"
                        + " WHEN 'E' THEN 'READ' ELSE NULL END LIMIT 1) AS variable_id"
                        + " FROM instruction WHERE bot_job_id=?"
                        + " ORDER BY block_id,instruction_order_number,id")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    InstructionRow row = new InstructionRow(
                            rows.getInt("id"), rows.getInt("instruction_order_number"),
                            rows.getString("actions"), rows.getString("name"),
                            rows.getString("operation"),
                            nullableInteger(rows, "on_hold_seconds"), rows.getInt("block_id"),
                            nullableInteger(rows, "variable_id"),
                            nullableInteger(rows, "parent_block_id"),
                            nullableInteger(rows, "parent_id"));
                    instructions.put(row.id(), row);
                    revisionRows.add(row.asRevisionRow());
                }
            }
        }
        List<VariableLoadDTO> variables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,producer_instruction_id FROM bot_job_variable_definition WHERE home_banking_id=? AND bot_job_id=? ORDER BY id")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    variables.add(new VariableLoadDTO(
                            rows.getInt("id"), null, owner.ownerId(),
                            nullableInteger(rows, "producer_instruction_id"),
                            null, null, null, null, null, 0));
                }
            }
        }
        return new Graph(
                state, Set.copyOf(blockIds), Map.copyOf(instructions),
                variables.stream().map(VariableLoadDTO::getId).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                revisionService.revision(revisionRows, variables));
    }

    private static List<InstructionRow> rowsFor(Graph graph, int blockId, int excludedId) {
        return rowsFor(graph, blockId, Set.of(excludedId));
    }

    private static List<InstructionRow> rowsFor(
            Graph graph,
            int blockId,
            Set<Integer> excludedIds) {
        return graph.instructions().values().stream()
                .filter(row -> row.blockId() == blockId && !excludedIds.contains(row.id()))
                .sorted(Comparator.comparingInt(InstructionRow::order).thenComparingInt(InstructionRow::id))
                .toList();
    }

    private static RelationshipImpact relationshipImpact(
            InstructionRow source,
            int targetBlockId,
            List<InstructionRow> finalTargetRows) {
        if (source.blockId() != targetBlockId) {
            return new RelationshipImpact(
                    source.parentId() != null,
                    source.parentBlockId() != null);
        }
        if (source.parentId() == null || source.parentId() == source.id()) {
            return RelationshipImpact.NONE;
        }
        int sourceIndex = -1;
        int parentIndex = -1;
        for (int index = 0; index < finalTargetRows.size(); index++) {
            InstructionRow row = finalTargetRows.get(index);
            if (row.id() == source.id()) sourceIndex = index;
            if (row.id() == source.parentId()) parentIndex = index;
        }
        boolean parentBlockMismatch = source.parentBlockId() != null
                && source.parentBlockId() != targetBlockId;
        boolean parentWillNotPrecede = parentIndex < 0 || parentIndex >= sourceIndex;
        if (!parentBlockMismatch && !parentWillNotPrecede) return RelationshipImpact.NONE;
        return new RelationshipImpact(true, source.parentBlockId() != null);
    }

    private void requireOwner(Connection connection, OwnerKey owner) throws SQLException {
        if (owner.workspaceKind() != WorkspaceKind.BOT_JOB) {
            throw refused("COMMAND_UPDATE_BOT_JOB_REQUIRED", "Command update requires a Bot Job owner.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM bot_job WHERE id=? AND home_banking_id=?")) {
            statement.setInt(1, owner.ownerId());
            statement.setInt(2, owner.homeBankingId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw refused("COMMAND_UPDATE_OWNER_MISMATCH", "The organization does not own this Bot Job.");
                }
            }
        }
    }

    private static void requireEditorInteger(Integer value, String label)
            throws MutationRefusedException {
        if (value == null || value < 1 || value > 9999) {
            throw refused("COMMAND_UPDATE_VALUE_INVALID", label + " must be between 1 and 9999.");
        }
    }

    private static void requireVariableOnlyCheck(Graph graph, Configuration configuration)
            throws MutationRefusedException {
        // PARKED 2026-08-03 (FE owns rules — user order): CHECKVALUE may exist and be
        // updated WITHOUT variables; AUTO VARIABLES (rules 5+6) fills the operands later.
        // Java keeps only persistence-grade referential checks on ids that ARE submitted.
        // Set<String> operators = Set.of(
        //         "=", "!=", ">", "<", ">=", "<=", "contains", "startsWith",
        //         "endsWith");
        // if (!operators.contains(configuration.comparisonOperator())) {
        //     throw refused("COMMAND_UPDATE_OPERATOR_INVALID", "Select a supported comparison operator.");
        // }
        if (configuration.leftVariableId() != null
                && !graph.variableIds().contains(configuration.leftVariableId())) {
            throw refused(
                    "COMMAND_UPDATE_LEFT_VARIABLE_INVALID",
                    "Select a current Bot Job variable as the first comparison value.");
        }
        if (configuration.operandVariableId() != null
                && !graph.variableIds().contains(configuration.operandVariableId())) {
            throw refused(
                    "COMMAND_UPDATE_OPERAND_VARIABLE_INVALID",
                    "Select a current Bot Job variable as the second comparison value.");
        }
    }

    private static void requireComparison(Graph graph, Configuration configuration)
            throws MutationRefusedException {
        Set<String> operators = Set.of(
                "=", "!=", ">", "<", ">=", "<=", "contains", "startsWith",
                "endsWith", "isEmpty", "isNotEmpty");
        if (!operators.contains(configuration.comparisonOperator())) {
            throw refused("COMMAND_UPDATE_OPERATOR_INVALID", "Select a supported comparison operator.");
        }
        String operandKind = configuration.operandKind();
        if (operandKind == null
                || !Set.of("LITERAL", "VARIABLE", "EMPTY", "VOID").contains(operandKind)) {
            throw refused("COMMAND_UPDATE_OPERAND_KIND_INVALID", "Select a supported comparison operand.");
        }
        if ("VARIABLE".equals(operandKind)) {
            if (configuration.operandVariableId() == null
                    || !graph.variableIds().contains(configuration.operandVariableId())) {
                throw refused(
                        "COMMAND_UPDATE_OPERAND_VARIABLE_INVALID",
                        "Select a current Bot Job variable as the comparison operand.");
            }
        } else if (configuration.operandVariableId() != null) {
            throw refused(
                    "COMMAND_UPDATE_OPERAND_VARIABLE_UNEXPECTED",
                    "Only a VARIABLE operand can carry a variable ID.");
        }
    }

    private static void requireConditional(Graph graph, Configuration configuration)
            throws MutationRefusedException {
        String source = configuration.conditionSource();
        if ("PREVIOUS_RESULT".equals(source)) {
            if (configuration.leftVariableId() != null
                    || configuration.operandVariableId() != null) {
                throw refused(
                        "COMMAND_UPDATE_CONDITION_LEFT_UNEXPECTED",
                        "Previous-result conditions cannot carry variable references.");
            }
            return;
        }
        if (!"VARIABLE_COMPARISON".equals(source)) {
            throw refused(
                    "COMMAND_UPDATE_CONDITION_SOURCE_INVALID",
                    "Select a supported condition source.");
        }
        if (configuration.leftVariableId() == null
                || !graph.variableIds().contains(configuration.leftVariableId())) {
            throw refused(
                    "COMMAND_UPDATE_CONDITION_LEFT_INVALID",
                    "Select a current Bot Job variable for the left condition value.");
        }
        requireComparison(graph, configuration);
    }

    private static boolean isTypedVariableConfiguration(ConfigurationKind kind) {
        return kind == ConfigurationKind.CHECK_VALUE
                || kind == ConfigurationKind.EXTERNAL_CHECK
                || kind == ConfigurationKind.EXCEL_WRITE
                || kind == ConfigurationKind.CONDITIONAL;
    }

    private static boolean isVariableOnlyCheck(ConfigurationKind kind) {
        return kind == ConfigurationKind.CHECK_VALUE
                || kind == ConfigurationKind.EXTERNAL_CHECK;
    }

    private void verifyTypedConfiguration(
            Connection connection,
            int homeBankingId,
            int botJobId,
            Plan plan)
            throws SQLException {
        if (!isTypedVariableConfiguration(plan.configuration().kind())) return;
        StoredConfiguration stored = commandConfigurations.load(
                connection, homeBankingId, botJobId, plan.source().id());
        Configuration expected = plan.configuration();
        if (stored == null
                || !Objects.equals(stored.operandKind(), expected.operandKind())
                || !Objects.equals(stored.conditionSource(), expected.conditionSource())
                || !Objects.equals(stored.leftVariableId(), expected.leftVariableId())
                || !Objects.equals(stored.comparisonOperator(), expected.comparisonOperator())
                || !Objects.equals(stored.operandRawValue(), expected.operandRawValue())
                || !Objects.equals(stored.operandVariableId(), expected.operandVariableId())
                || !Objects.equals(stored.outputKey(), expected.outputKey())
                || !Objects.equals(stored.outputColumn(), expected.outputColumn())
                || !Objects.equals(stored.outputFile(), expected.outputFile())
                || !Objects.equals(stored.externalSourceKey(), expected.externalSourceKey())
                || !Objects.equals(stored.formatPolicy(), expected.formatPolicy())) {
            throw refused(
                    "COMMAND_UPDATE_CONFIGURATION_NOT_COMMITTED",
                    "The typed command configuration could not be verified.");
        }
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static Integer nullableInteger(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }
    private static MutationRefusedException refused(String code, String message) {
        return new MutationRefusedException(code, message);
    }
    private static void requireOpen(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) throw new SQLException("An open database connection is required.");
    }
    private static boolean rollback(Connection connection, Throwable failure) {
        try { connection.rollback(); return true; }
        catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); return false; }
    }
    private static void close(Connection connection, Throwable failure) {
        try { connection.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
    }
    private static void restoreAutoCommit(Connection connection) {
        try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
    }

    private record InstructionRow(
            int id, int order, String action, String name, String operation,
            Integer onHoldSeconds,
            int blockId, Integer variableId, Integer parentBlockId, Integer parentId) {
        private InstructionLoad asRevisionRow() {
            InstructionLoad row = new InstructionLoad();
            row.setId(id); row.setInstructionOrderNumber(order); row.setActions(action);
            row.setOperation(operation); row.setBlockId(blockId); row.setVariableId(variableId);
            row.setParentBlockId(parentBlockId); row.setParentId(parentId);
            return row;
        }
    }
    private record Graph(GraphState state, Set<Integer> blockIds,
                         Map<Integer, InstructionRow> instructions,
                         Set<Integer> variableIds, String revision) {}
    private record Plan(InstructionRow source, int targetBlockId, Configuration configuration,
                        List<InstructionRow> sourceRows, List<InstructionRow> targetRows,
                        int finalOrder, RelationshipImpact relationshipImpact,
                        String targetAction, boolean commandChanged,
                        List<Integer> conditionalFamilyDeleteIds) {}

    private record RelationshipImpact(boolean clearParentId, boolean clearParentBlockId) {
        private static final RelationshipImpact NONE = new RelationshipImpact(false, false);
        private boolean requiresDisconnect() { return clearParentId || clearParentBlockId; }
    }

    public record UpdateResult(
            OwnerKey owner, long workspaceEpoch, String requestId, int instructionId,
            int targetBlockId, int instructionOrderNumber, long previousGraphVersion,
            long committedGraphVersion, String graphRevision, boolean duplicate) {
        public UpdateResult asDuplicate() {
            return new UpdateResult(owner, workspaceEpoch, requestId, instructionId,
                    targetBlockId, instructionOrderNumber, previousGraphVersion,
                    committedGraphVersion, graphRevision, true);
        }
    }
}
