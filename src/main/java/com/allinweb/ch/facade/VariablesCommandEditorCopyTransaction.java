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
import com.allinweb.ch.model.VariablesCommandEditorCreateV1;
import com.allinweb.ch.model.VariablesCommandEditorCopyV1;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Configuration;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.ConfigurationKind;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Placement;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.PlacementKind;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Atomic fresh-ID, relationship-free COPY NEW for the Variables Command Editor. */
public final class VariablesCommandEditorCopyTransaction {
    private static final int ORDER_OFFSET = 1_000_000;
    private static final String SELECT_INSTRUCTIONS = """
            SELECT id,instruction_order_number,actions,name,xpath,coordinates,
                   force_coordinates,iframe_xpath,tag_name,shadow_host,shadow_root,
                   css_selector,description,operation,optional,block_marked,default_value,
                   action_custom_max_wait_sec,on_hold_seconds,codified,export_to_abr,active,
                   block_id,variable_id,parent_block_id,parent_id,bot_job_id,client_named
              FROM instruction WHERE bot_job_id=?
             ORDER BY block_id,instruction_order_number,id
            """;
    private static final String INSERT_INSTRUCTION = """
            INSERT INTO instruction (
                   instruction_order_number,actions,name,xpath,coordinates,force_coordinates,
                   iframe_xpath,tag_name,shadow_host,shadow_root,css_selector,description,
                   operation,optional,block_marked,default_value,action_custom_max_wait_sec,
                   on_hold_seconds,codified,export_to_abr,active,block_id,variable_id,
                   parent_block_id,parent_id,bot_job_id,client_named)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    private final InstructionGraphStateRepository stateRepository;
    private final InstructionGraphRevisionService revisionService;
    private final InstructionVariableCommandConfigRepository commandConfigurations;
    private final VariablesCommandEditorCreateInsert commandCreateInsert;

    public VariablesCommandEditorCopyTransaction() {
        this(
                new InstructionGraphStateRepository(),
                new InstructionGraphRevisionService(),
                new InstructionVariableCommandConfigRepository(),
                new VariablesCommandEditorCreateInsert());
    }

    VariablesCommandEditorCopyTransaction(
            InstructionGraphStateRepository stateRepository,
            InstructionGraphRevisionService revisionService) {
        this(stateRepository, revisionService, new InstructionVariableCommandConfigRepository());
    }

    VariablesCommandEditorCopyTransaction(
            InstructionGraphStateRepository stateRepository,
            InstructionGraphRevisionService revisionService,
            InstructionVariableCommandConfigRepository commandConfigurations) {
        this(
                stateRepository,
                revisionService,
                commandConfigurations,
                new VariablesCommandEditorCreateInsert());
    }

    VariablesCommandEditorCopyTransaction(
            InstructionGraphStateRepository stateRepository,
            InstructionGraphRevisionService revisionService,
            InstructionVariableCommandConfigRepository commandConfigurations,
            VariablesCommandEditorCreateInsert commandCreateInsert) {
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.revisionService = Objects.requireNonNull(revisionService, "revisionService");
        this.commandConfigurations = Objects.requireNonNull(
                commandConfigurations, "commandConfigurations");
        this.commandCreateInsert = Objects.requireNonNull(
                commandCreateInsert, "commandCreateInsert");
    }

    public CopyResult execute(
            Connection connection,
            AuthenticatedBotJob owner,
            VariablesCommandEditorCopyV1.Request request) throws SQLException {
        if (request != null && Boolean.TRUE.equals(request.createBlank())) {
            throw refused(
                    "COMMAND_COPY_CREATE_NOT_ALLOWED",
                    "COPY NEW cannot create a blank command. Use Add Command.");
        }
        return executeInternal(connection, owner, request);
    }

    CopyResult executeCreate(
            Connection connection,
            AuthenticatedBotJob owner,
            VariablesCommandEditorCreateV1.Request request) throws SQLException {
        Objects.requireNonNull(request, "request");
        return executeInternal(
                connection,
                owner,
                new VariablesCommandEditorCopyV1.Request(
                        request.contractVersion(),
                        request.requestId(),
                        request.bindingEpoch(),
                        request.workspaceEpoch(),
                        request.baseGraphVersion(),
                        request.graphRevision(),
                        null,
                        true,
                        request.targetBlockId(),
                        request.placement(),
                        request.configuration(),
                        request.targetAction()));
    }

    private CopyResult executeInternal(
            Connection connection,
            AuthenticatedBotJob owner,
            VariablesCommandEditorCopyV1.Request request) throws SQLException {
        requireOpen(connection);
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");
        if (!connection.getAutoCommit()) {
            throw new SQLException("Variables command copy requires an unbound connection.");
        }
        connection.setAutoCommit(false);
        boolean restoreAutoCommit = true;
        try {
            requireOwner(connection, owner.owner());
            GraphState state = stateRepository.loadOrCreate(connection, owner.owner());
            Graph before = loadGraph(connection, owner.owner(), state);
            Plan plan = validateAndPlan(owner, request, before);
            offsetTargetOrders(connection, owner.owner().ownerId(), plan.targetBlockId());
            int generatedId = plan.createBlank()
                    ? commandCreateInsert.insert(
                            connection,
                            owner.owner().ownerId(),
                            plan.targetBlockId(),
                            plan.finalOrder(),
                            plan.targetAction(),
                            plan.configuration())
                    : insertCopy(connection, owner.owner().ownerId(), plan.source(), plan);
            if (isTypedVariableConfiguration(plan.configuration().kind())) {
                commandConfigurations.upsert(
                        connection,
                        owner.owner().homeBankingId(),
                        owner.owner().ownerId(),
                        generatedId,
                        plan.targetAction(),
                        plan.configuration());
            }
            restoreTargetOrders(
                    connection, owner.owner().ownerId(), generatedId, plan);

            AdvanceResult advance = stateRepository.compareAndSetIncrement(
                    connection, owner.owner(), request.baseGraphVersion());
            if (!advance.advanced()) {
                throw refused(
                        advance.status() == AdvanceStatus.MISSING
                                ? "COMMAND_COPY_GRAPH_STATE_MISSING"
                                : "COMMAND_COPY_GRAPH_VERSION_STALE",
                        "The Variables graph changed before command copy completed.");
            }
            Graph after = loadGraph(connection, owner.owner(), advance.state());
            verify(plan, generatedId, before, after);
            verifyTypedConfiguration(
                    connection,
                    owner.owner().homeBankingId(),
                    owner.owner().ownerId(),
                    generatedId,
                    plan.configuration());
            connection.commit();
            return new CopyResult(
                    owner.owner(), owner.workspaceEpoch(), request.requestId().trim(),
                    plan.source() == null ? 0 : plan.source().id(),
                    generatedId, plan.targetBlockId(), plan.finalOrder(),
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
            VariablesCommandEditorCopyV1.Request request,
            Graph graph) throws MutationRefusedException {
        if (!Objects.equals(request.contractVersion(), VariablesCommandEditorCopyV1.CONTRACT_VERSION)) {
            throw refused("COMMAND_COPY_CONTRACT_UNSUPPORTED", "The command-copy contract is not supported.");
        }
        if (blank(request.requestId()) || blank(request.bindingEpoch())) {
            throw refused("COMMAND_COPY_IDENTITY_REQUIRED", "Command copy requires request and binding IDs.");
        }
        if (!Objects.equals(request.workspaceEpoch(), owner.workspaceEpoch())) {
            throw refused("COMMAND_COPY_WORKSPACE_CHANGED", "The Bot Job workspace changed before command copy.");
        }
        if (!Objects.equals(request.baseGraphVersion(), graph.state().version())) {
            throw refused("COMMAND_COPY_GRAPH_VERSION_STALE", "The Variables graph version changed before command copy.");
        }
        if (blank(request.graphRevision()) || !request.graphRevision().trim().equals(graph.revision())) {
            throw refused("COMMAND_COPY_GRAPH_REVISION_STALE", "The Variables graph changed before command copy.");
        }
        boolean createBlank = Boolean.TRUE.equals(request.createBlank());
        InstructionRow source = createBlank
                ? null
                : graph.instructions().get(request.sourceInstructionId());
        if (!createBlank && source == null) {
            throw refused("COMMAND_COPY_SOURCE_MISSING", "The selected command no longer exists.");
        }
        if (request.targetBlockId() == null || !graph.blockIds().contains(request.targetBlockId())) {
            throw refused("COMMAND_COPY_TARGET_BLOCK_INVALID", "Select a current target Block.");
        }
        String sourceAction = createBlank
                ? ""
                : CommandRegistry.canonicalize(source.actionsText());
        String targetAction = blank(request.targetAction())
                ? sourceAction
                : CommandRegistry.canonicalize(request.targetAction());
        boolean commandChanged = createBlank || !targetAction.equals(sourceAction);
        if (commandChanged && !CommandRegistry.isEditorTargetable(targetAction)) {
            throw refused(
                    "COMMAND_COPY_TARGET_ACTION_INVALID",
                    "The selected target command is not supported by the Command Editor.");
        }
        Configuration configuration = requireConfiguration(
                graph, targetAction, request.configuration());
        Placement placement = request.placement();
        if (placement == null || placement.kind() == null || placement.kind() == PlacementKind.KEEP) {
            throw refused("COMMAND_COPY_PLACEMENT_REQUIRED", "Select Top, End, or After for COPY NEW.");
        }
        List<InstructionRow> targetRows = rowsFor(graph, request.targetBlockId());
        int index;
        if (placement.kind() == PlacementKind.TOP) index = 0;
        else if (placement.kind() == PlacementKind.END) index = targetRows.size();
        else {
            InstructionRow reference = graph.instructions().get(placement.referenceInstructionId());
            if (reference == null || reference.blockId() != request.targetBlockId()) {
                throw refused("COMMAND_COPY_REFERENCE_INVALID", "Select an instruction in the target Block.");
            }
            index = targetRows.indexOf(reference) + 1;
            if (index <= 0) throw refused("COMMAND_COPY_REFERENCE_INVALID", "The placement reference is invalid.");
        }
        return new Plan(
                source, request.targetBlockId(), configuration, targetRows, index, index + 1,
                targetAction, commandChanged, createBlank);
    }

    private Configuration requireConfiguration(
            Graph graph, String action, Configuration configuration)
            throws MutationRefusedException {
        if (configuration == null || configuration.kind() == null) {
            throw refused("COMMAND_COPY_CONFIGURATION_REQUIRED", "A typed command configuration is required.");
        }
        if (configuration.kind() == ConfigurationKind.NONE) {
            if (!CommandRegistry.hasNoEditorConfiguration(action)) {
                throw refused(
                        "COMMAND_COPY_CONFIGURATION_MISMATCH",
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
                || configuration.kind() == ConfigurationKind.CONDITIONAL
                        && !Set.of("IF", "ELSEIF").contains(action)) {
            throw refused("COMMAND_COPY_CONFIGURATION_MISMATCH", "The submitted configuration does not match the command.");
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
            throw refused("COMMAND_COPY_OUTPUT_KEY_REQUIRED", "Enter an ExcelWrite output key.");
        } else if (configuration.kind() == ConfigurationKind.GOTO
                || configuration.kind() == ConfigurationKind.SWIPE) {
            requireEditorInteger(configuration.count(), "Repetition count");
        } else if (configuration.kind() == ConfigurationKind.CONDITIONAL) {
            requireConditional(graph, configuration);
        }
        return configuration.withoutVariableReferences();
    }

    private void offsetTargetOrders(Connection connection, int botJobId, int blockId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE instruction SET instruction_order_number=instruction_order_number+? WHERE bot_job_id=? AND block_id=?")) {
            statement.setInt(1, ORDER_OFFSET);
            statement.setInt(2, botJobId);
            statement.setInt(3, blockId);
            statement.executeUpdate();
        }
    }

    private int insertCopy(Connection connection, int botJobId, InstructionRow source, Plan plan)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_INSTRUCTION, Statement.RETURN_GENERATED_KEYS)) {
            int parameter = 1;
            statement.setInt(parameter++, plan.finalOrder());
            statement.setObject(
                    parameter++, plan.commandChanged() ? plan.targetAction() : source.actions());
            statement.setObject(parameter++, copiedName(source, plan));
            statement.setObject(parameter++, source.xpath());
            statement.setObject(parameter++, source.coordinates());
            statement.setObject(parameter++, source.forceCoordinates());
            statement.setObject(parameter++, source.iframeXPath());
            statement.setObject(parameter++, source.tagName());
            statement.setObject(parameter++, source.shadowHost());
            statement.setObject(parameter++, source.shadowRoot());
            statement.setObject(parameter++, source.cssSelector());
            statement.setObject(parameter++, source.description());
            statement.setObject(
                    parameter++,
                    configuredOperation(source, plan.configuration(), plan.commandChanged()));
            statement.setObject(parameter++, source.optional());
            statement.setObject(parameter++, source.blockMarked());
            statement.setObject(parameter++, source.defaultValue());
            statement.setObject(parameter++, source.actionCustomMaxWaitSec());
            statement.setObject(
                    parameter++,
                    configuredHold(source, plan.configuration(), plan.commandChanged()));
            statement.setObject(parameter++, source.codified());
            statement.setObject(parameter++, source.exportToAbr());
            statement.setObject(parameter++, source.active());
            statement.setInt(parameter++, plan.targetBlockId());
            statement.setNull(parameter++, Types.INTEGER);
            statement.setNull(parameter++, Types.INTEGER);
            statement.setNull(parameter++, Types.INTEGER);
            statement.setInt(parameter++, botJobId);
            statement.setObject(parameter, source.clientNamed());
            if (statement.executeUpdate() != 1) throw new SQLException("COPY NEW did not insert exactly one instruction.");
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("COPY NEW returned no generated instruction ID.");
                return keys.getInt(1);
            }
        }
    }

    private void restoreTargetOrders(
            Connection connection, int botJobId, int generatedId, Plan plan) throws SQLException {
        ArrayList<Integer> orderedIds = new ArrayList<>();
        for (InstructionRow row : plan.targetRows()) orderedIds.add(row.id());
        orderedIds.add(plan.targetIndex(), generatedId);
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE instruction SET instruction_order_number=? WHERE id=? AND bot_job_id=?")) {
            for (int index = 0; index < orderedIds.size(); index++) {
                statement.setInt(1, index + 1);
                statement.setInt(2, orderedIds.get(index));
                statement.setInt(3, botJobId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void verify(Plan plan, int generatedId, Graph before, Graph after)
            throws MutationRefusedException {
        if (!plan.createBlank()) {
            InstructionRow originalBefore = before.instructions().get(plan.source().id());
            InstructionRow originalAfter = after.instructions().get(plan.source().id());
            if (originalBefore == null || !originalBefore.sameSourceState(originalAfter)) {
                throw refused("COMMAND_COPY_SOURCE_CHANGED", "COPY NEW changed the original instruction.");
            }
        }
        InstructionRow copy = after.instructions().get(generatedId);
        if (copy == null || copy.blockId() != plan.targetBlockId()
                || copy.order() != plan.finalOrder()
                || copy.variableId() != null || copy.parentId() != null
                || copy.parentBlockId() != null) {
            throw refused("COMMAND_COPY_VERIFICATION_FAILED", "The disconnected command copy could not be verified.");
        }
        boolean identityCopied = plan.createBlank()
                ? plan.targetAction().equals(copy.actionsText())
                : plan.commandChanged()
                ? plan.targetAction().equals(copy.actionsText())
                : Objects.equals(copy.actions(), plan.source().actions());
        Object expectedName = plan.createBlank()
                ? CommandRegistry.transformedName(plan.targetAction())
                : copiedName(plan.source(), plan);
        boolean nameCopied = expectedName == null
                ? copy.name() == null
                : copy.name() != null && expectedName.toString().equals(copy.name().toString());
        if (!identityCopied || !nameCopied) {
            throw refused("COMMAND_COPY_VERIFICATION_FAILED", "The command identity was not copied exactly.");
        }
        Configuration configuration = plan.configuration();
        if (configuration.kind() == ConfigurationKind.WAIT) {
            if (!Objects.equals(copy.onHoldSecondsInteger(), configuration.waitSeconds())) {
                throw refused("COMMAND_COPY_VERIFICATION_FAILED", "The copied Wait value could not be verified.");
            }
        } else {
            if (plan.commandChanged() && copy.onHoldSecondsInteger() != null) {
                throw refused(
                        "COMMAND_COPY_VERIFICATION_FAILED",
                        "The transformed copy still carries the previous Wait value.");
            }
            String expectedOperation = plan.createBlank()
                    ? configuredOperationForBlank(configuration)
                    : configuredOperation(
                            plan.source(), configuration, plan.commandChanged());
            if (!Objects.equals(
                    expectedOperation == null ? "" : expectedOperation, copy.operationText())) {
                throw refused("COMMAND_COPY_VERIFICATION_FAILED", "The copied intrinsic values could not be verified.");
            }
        }
        List<InstructionRow> rows = rowsFor(after, plan.targetBlockId());
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).order() != index + 1) {
                throw refused("COMMAND_COPY_ORDER_INVALID", "The target Block order is not contiguous.");
            }
        }
    }

    private Graph loadGraph(Connection connection, OwnerKey owner, GraphState state) throws SQLException {
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
        try (PreparedStatement statement = connection.prepareStatement(SELECT_INSTRUCTIONS)) {
            statement.setInt(1, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    InstructionRow row = InstructionRow.from(rows);
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
                while (rows.next()) variables.add(new VariableLoadDTO(
                        rows.getInt("id"), null, owner.ownerId(),
                        nullableInteger(rows, "producer_instruction_id"),
                        null, null, null, null, null, 0));
            }
        }
        return new Graph(state, Set.copyOf(blockIds), Map.copyOf(instructions),
                variables.stream().map(VariableLoadDTO::getId).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                revisionService.revision(revisionRows, variables));
    }

    /** A transformed copy is renamed like an in-place transform; plain copies keep the name. */
    private static Object copiedName(InstructionRow source, Plan plan) {
        if (!plan.commandChanged()) return source.name();
        return CommandRegistry.transformedName(plan.targetAction());
    }

    private static String configuredOperation(
            InstructionRow source, Configuration configuration, boolean commandChanged) {
        if (configuration.kind() == ConfigurationKind.WAIT
                || configuration.kind() == ConfigurationKind.NONE
                || isTypedVariableConfiguration(configuration.kind())) {
            // A transformed copy must not inherit the previous command's intrinsic values.
            return commandChanged ? null : source.operationText();
        }
        return configuration.kind() == ConfigurationKind.GOTO
                        || configuration.kind() == ConfigurationKind.SWIPE
                ? String.valueOf(configuration.count())
                : configuration.intervalSeconds() + ":" + configuration.iterations();
    }
    private static Object configuredHold(
            InstructionRow source, Configuration configuration, boolean commandChanged) {
        if (configuration.kind() == ConfigurationKind.WAIT) {
            return configuration.waitSeconds();
        }
        return commandChanged ? null : source.onHoldSeconds();
    }
    private static String configuredOperationForBlank(Configuration configuration) {
        if (configuration.kind() == ConfigurationKind.WAIT
                || configuration.kind() == ConfigurationKind.NONE
                || isTypedVariableConfiguration(configuration.kind())) {
            return null;
        }
        return configuration.kind() == ConfigurationKind.GOTO
                        || configuration.kind() == ConfigurationKind.SWIPE
                ? String.valueOf(configuration.count())
                : configuration.intervalSeconds() + ":" + configuration.iterations();
    }
    private static Integer configuredHoldForBlank(Configuration configuration) {
        return configuration.kind() == ConfigurationKind.WAIT
                ? configuration.waitSeconds()
                : null;
    }
    private static List<InstructionRow> rowsFor(Graph graph, int blockId) {
        return graph.instructions().values().stream().filter(row -> row.blockId() == blockId)
                .sorted(Comparator.comparingInt(InstructionRow::order).thenComparingInt(InstructionRow::id))
                .toList();
    }
    private void requireOwner(Connection connection, OwnerKey owner) throws SQLException {
        if (owner.workspaceKind() != WorkspaceKind.BOT_JOB) {
            throw refused("COMMAND_COPY_BOT_JOB_REQUIRED", "Command copy requires a Bot Job owner.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM bot_job WHERE id=? AND home_banking_id=?")) {
            statement.setInt(1, owner.ownerId()); statement.setInt(2, owner.homeBankingId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw refused("COMMAND_COPY_OWNER_MISMATCH", "The organization does not own this Bot Job.");
            }
        }
    }
    private static void requireEditorInteger(Integer value, String label) throws MutationRefusedException {
        if (value == null || value < 1 || value > 9999) throw refused("COMMAND_COPY_VALUE_INVALID", label + " must be between 1 and 9999.");
    }
    private static void requireVariableOnlyCheck(Graph graph, Configuration configuration)
            throws MutationRefusedException {
        Set<String> operators = Set.of(
                "=", "!=", ">", "<", ">=", "<=", "contains", "startsWith",
                "endsWith");
        if (!operators.contains(configuration.comparisonOperator())) {
            throw refused("COMMAND_COPY_OPERATOR_INVALID", "Select a supported comparison operator.");
        }
        if (configuration.leftVariableId() == null
                || !graph.variableIds().contains(configuration.leftVariableId())) {
            throw refused(
                    "COMMAND_COPY_LEFT_VARIABLE_INVALID",
                    "Select a current Bot Job variable as the first comparison value.");
        }
        if (!"VARIABLE".equals(configuration.operandKind())
                || configuration.operandVariableId() == null
                || !graph.variableIds().contains(configuration.operandVariableId())) {
            throw refused(
                    "COMMAND_COPY_OPERAND_VARIABLE_INVALID",
                    "Select a current Bot Job variable as the second comparison value.");
        }
    }

    private static void requireComparison(Graph graph, Configuration configuration)
            throws MutationRefusedException {
        Set<String> operators = Set.of(
                "=", "!=", ">", "<", ">=", "<=", "contains", "startsWith",
                "endsWith", "isEmpty", "isNotEmpty");
        if (!operators.contains(configuration.comparisonOperator())) {
            throw refused("COMMAND_COPY_OPERATOR_INVALID", "Select a supported comparison operator.");
        }
        String operandKind = configuration.operandKind();
        if (operandKind == null
                || !Set.of("LITERAL", "VARIABLE", "EMPTY", "VOID").contains(operandKind)) {
            throw refused("COMMAND_COPY_OPERAND_KIND_INVALID", "Select a supported comparison operand.");
        }
        if ("VARIABLE".equals(operandKind)) {
            if (configuration.operandVariableId() == null
                    || !graph.variableIds().contains(configuration.operandVariableId())) {
                throw refused(
                        "COMMAND_COPY_OPERAND_VARIABLE_INVALID",
                        "Select a current Bot Job variable as the comparison operand.");
            }
        } else if (configuration.operandVariableId() != null) {
            throw refused(
                    "COMMAND_COPY_OPERAND_VARIABLE_UNEXPECTED",
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
                        "COMMAND_COPY_CONDITION_LEFT_UNEXPECTED",
                        "Previous-result conditions cannot carry variable references.");
            }
            return;
        }
        if (!"VARIABLE_COMPARISON".equals(source)) {
            throw refused(
                    "COMMAND_COPY_CONDITION_SOURCE_INVALID",
                    "Select a supported condition source.");
        }
        if (configuration.leftVariableId() == null
                || !graph.variableIds().contains(configuration.leftVariableId())) {
            throw refused(
                    "COMMAND_COPY_CONDITION_LEFT_INVALID",
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
    private void verifyTypedConfiguration(
            Connection connection,
            int homeBankingId,
            int botJobId,
            int instructionId,
            Configuration expected)
            throws SQLException {
        if (!isTypedVariableConfiguration(expected.kind())) return;
        StoredConfiguration stored = commandConfigurations.load(
                connection, homeBankingId, botJobId, instructionId);
        if (stored == null
                || !Objects.equals(stored.conditionSource(), expected.conditionSource())
                || !Objects.equals(stored.leftVariableId(), expected.leftVariableId())
                || !Objects.equals(stored.operandKind(), expected.operandKind())
                || !Objects.equals(stored.comparisonOperator(), expected.comparisonOperator())
                || !Objects.equals(stored.operandRawValue(), expected.operandRawValue())
                || !Objects.equals(stored.operandVariableId(), expected.operandVariableId())
                || !Objects.equals(stored.outputKey(), expected.outputKey())
                || !Objects.equals(stored.outputColumn(), expected.outputColumn())
                || !Objects.equals(stored.outputFile(), expected.outputFile())
                || !Objects.equals(stored.externalSourceKey(), expected.externalSourceKey())
                || !Objects.equals(stored.formatPolicy(), expected.formatPolicy())) {
            throw refused(
                    "COMMAND_COPY_CONFIGURATION_NOT_COMMITTED",
                    "The copied typed command configuration could not be verified.");
        }
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static Integer nullableInteger(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column); return value == null ? null : ((Number) value).intValue();
    }
    private static MutationRefusedException refused(String code, String message) { return new MutationRefusedException(code, message); }
    private static void requireOpen(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) throw new SQLException("An open database connection is required.");
    }
    private static boolean rollback(Connection connection, Throwable failure) {
        try { connection.rollback(); return true; } catch (SQLException error) { failure.addSuppressed(error); return false; }
    }
    private static void close(Connection connection, Throwable failure) {
        try { connection.close(); } catch (SQLException error) { failure.addSuppressed(error); }
    }
    private static void restoreAutoCommit(Connection connection) {
        try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
    }

    private record InstructionRow(
            int id,int order,Object actions,Object name,Object xpath,Object coordinates,
            Object forceCoordinates,Object iframeXPath,Object tagName,Object shadowHost,
            Object shadowRoot,Object cssSelector,Object description,Object operation,Object optional,
            Object blockMarked,Object defaultValue,Object actionCustomMaxWaitSec,Object onHoldSeconds,
            Object codified,Object exportToAbr,Object active,int blockId,Integer variableId,
            Integer parentBlockId,Integer parentId,int botJobId,Object clientNamed) {
        private static InstructionRow from(ResultSet rows) throws SQLException {
            return new InstructionRow(
                    rows.getInt("id"),rows.getInt("instruction_order_number"),rows.getObject("actions"),
                    rows.getObject("name"),rows.getObject("xpath"),rows.getObject("coordinates"),
                    rows.getObject("force_coordinates"),rows.getObject("iframe_xpath"),rows.getObject("tag_name"),
                    rows.getObject("shadow_host"),rows.getObject("shadow_root"),rows.getObject("css_selector"),
                    rows.getObject("description"),rows.getObject("operation"),rows.getObject("optional"),
                    rows.getObject("block_marked"),rows.getObject("default_value"),
                    rows.getObject("action_custom_max_wait_sec"),rows.getObject("on_hold_seconds"),
                    rows.getObject("codified"),rows.getObject("export_to_abr"),rows.getObject("active"),
                    rows.getInt("block_id"),nullableInteger(rows,"variable_id"),
                    nullableInteger(rows,"parent_block_id"),nullableInteger(rows,"parent_id"),
                    rows.getInt("bot_job_id"),rows.getObject("client_named"));
        }
        private String actionsText() { return actions == null ? "" : actions.toString(); }
        private String operationText() { return operation == null ? "" : operation.toString(); }
        private Integer onHoldSecondsInteger() {
            return onHoldSeconds instanceof Number number ? number.intValue() : null;
        }
        private boolean sameSourceState(InstructionRow other) {
            return other != null && id == other.id && blockId == other.blockId
                    && sameStoredValue(actions, other.actions)
                    && sameStoredValue(name, other.name)
                    && sameStoredValue(xpath, other.xpath)
                    && sameStoredValue(coordinates, other.coordinates)
                    && sameStoredValue(forceCoordinates, other.forceCoordinates)
                    && sameStoredValue(iframeXPath, other.iframeXPath)
                    && sameStoredValue(tagName, other.tagName)
                    && sameStoredValue(shadowHost, other.shadowHost)
                    && sameStoredValue(shadowRoot, other.shadowRoot)
                    && sameStoredValue(cssSelector, other.cssSelector)
                    && sameStoredValue(description, other.description)
                    && sameStoredValue(operation, other.operation)
                    && sameStoredValue(optional, other.optional)
                    && sameStoredValue(blockMarked, other.blockMarked)
                    && sameStoredValue(defaultValue, other.defaultValue)
                    && sameStoredValue(actionCustomMaxWaitSec, other.actionCustomMaxWaitSec)
                    && Objects.equals(onHoldSecondsInteger(), other.onHoldSecondsInteger())
                    && sameStoredValue(codified, other.codified)
                    && sameStoredValue(exportToAbr, other.exportToAbr)
                    && sameStoredValue(active, other.active)
                    && Objects.equals(variableId, other.variableId)
                    && Objects.equals(parentBlockId, other.parentBlockId) && Objects.equals(parentId, other.parentId)
                    && botJobId == other.botJobId
                    && sameStoredValue(clientNamed, other.clientNamed);
        }
        private static boolean sameStoredValue(Object left, Object right) {
            if (left == right) return true;
            if (left == null || right == null) return false;
            if (left instanceof byte[] leftBytes && right instanceof byte[] rightBytes) {
                return Arrays.equals(leftBytes, rightBytes);
            }
            if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
                try {
                    return new BigDecimal(leftNumber.toString())
                            .compareTo(new BigDecimal(rightNumber.toString())) == 0;
                } catch (NumberFormatException ignored) {
                    return leftNumber.toString().equals(rightNumber.toString());
                }
            }
            return left.toString().equals(right.toString());
        }
        private InstructionLoad asRevisionRow() {
            // Revision parity: the hasher renders null as "null" and empty as "". The
            // capability and UPDATE loaders feed RAW nullable values, so this loader must
            // too — null-to-"" coercion here made every COPY refuse GRAPH_REVISION_STALE
            // on jobs containing NULL operation rows.
            InstructionLoad row = new InstructionLoad(); row.setId(id);
            row.setInstructionOrderNumber(order);
            row.setActions(actions == null ? null : actions.toString());
            row.setOperation(operation == null ? null : operation.toString());
            row.setBlockId(blockId); row.setVariableId(variableId);
            row.setParentBlockId(parentBlockId); row.setParentId(parentId); return row;
        }
    }
    private record Graph(GraphState state,Set<Integer> blockIds,
                         Map<Integer,InstructionRow> instructions,
                         Set<Integer> variableIds,String revision) {}
    private record Plan(InstructionRow source,int targetBlockId,Configuration configuration,
                        List<InstructionRow> targetRows,int targetIndex,int finalOrder,
                        String targetAction,boolean commandChanged,boolean createBlank) {}
    public record CopyResult(
            OwnerKey owner,long workspaceEpoch,String requestId,int sourceInstructionId,
            int createdInstructionId,int targetBlockId,int instructionOrderNumber,
            long previousGraphVersion,long committedGraphVersion,String graphRevision,
            boolean duplicate) {
        public CopyResult asDuplicate() { return new CopyResult(owner,workspaceEpoch,requestId,
                sourceInstructionId,createdInstructionId,targetBlockId,instructionOrderNumber,
                previousGraphVersion,committedGraphVersion,graphRevision,true); }
    }
}
