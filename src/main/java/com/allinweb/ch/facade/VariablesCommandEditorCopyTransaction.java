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
import com.allinweb.ch.model.VariablesCommandEditorCopyV1;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Configuration;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.ConfigurationKind;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Placement;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.PlacementKind;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
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

    public VariablesCommandEditorCopyTransaction() {
        this(new InstructionGraphStateRepository(), new InstructionGraphRevisionService());
    }

    VariablesCommandEditorCopyTransaction(
            InstructionGraphStateRepository stateRepository,
            InstructionGraphRevisionService revisionService) {
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.revisionService = Objects.requireNonNull(revisionService, "revisionService");
    }

    public CopyResult execute(
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
            int generatedId = insertCopy(
                    connection, owner.owner().ownerId(), plan.source(), plan);
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
            connection.commit();
            return new CopyResult(
                    owner.owner(), owner.workspaceEpoch(), request.requestId().trim(),
                    plan.source().id(), generatedId, plan.targetBlockId(), plan.finalOrder(),
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
        InstructionRow source = graph.instructions().get(request.sourceInstructionId());
        if (source == null) throw refused("COMMAND_COPY_SOURCE_MISSING", "The selected command no longer exists.");
        if (request.targetBlockId() == null || !graph.blockIds().contains(request.targetBlockId())) {
            throw refused("COMMAND_COPY_TARGET_BLOCK_INVALID", "Select a current target Block.");
        }
        Configuration configuration = requireConfiguration(source, request.configuration());
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
        return new Plan(source, request.targetBlockId(), configuration, targetRows, index, index + 1);
    }

    private Configuration requireConfiguration(InstructionRow source, Configuration configuration)
            throws MutationRefusedException {
        if (configuration == null || configuration.kind() == null) {
            throw refused("COMMAND_COPY_CONFIGURATION_REQUIRED", "A typed command configuration is required.");
        }
        String action = CommandRegistry.canonicalize(source.actionsText());
        if (configuration.kind() == ConfigurationKind.LOOP && !"LOOP".equals(action)
                || configuration.kind() == ConfigurationKind.REFRESH_LOOP && !"REFRESH_LOOP".equals(action)
                || configuration.kind() == ConfigurationKind.WAIT && !"H".equals(action)) {
            throw refused("COMMAND_COPY_CONFIGURATION_MISMATCH", "The submitted configuration does not match the command.");
        }
        if (configuration.kind() == ConfigurationKind.WAIT) {
            requireEditorInteger(configuration.waitSeconds(), "Wait seconds");
        } else {
            requireEditorInteger(configuration.intervalSeconds(), "Interval seconds");
            requireEditorInteger(configuration.iterations(), "Iterations");
        }
        return configuration;
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
            statement.setObject(parameter++, configuredOperation(source, plan.configuration()));
            statement.setObject(parameter++, source.optional());
            statement.setObject(parameter++, source.blockMarked());
            statement.setObject(parameter++, source.defaultValue());
            statement.setObject(parameter++, source.actionCustomMaxWaitSec());
            statement.setObject(parameter++, configuredHold(source, plan.configuration()));
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
        InstructionRow originalBefore = before.instructions().get(plan.source().id());
        InstructionRow originalAfter = after.instructions().get(plan.source().id());
        if (originalBefore == null || !originalBefore.sameSourceState(originalAfter)) {
            throw refused("COMMAND_COPY_SOURCE_CHANGED", "COPY NEW changed the original instruction.");
        }
        InstructionRow copy = after.instructions().get(generatedId);
        if (copy == null || copy.blockId() != plan.targetBlockId()
                || copy.order() != plan.finalOrder()
                || copy.variableId() != null || copy.parentId() != null
                || copy.parentBlockId() != null) {
            throw refused("COMMAND_COPY_VERIFICATION_FAILED", "The disconnected command copy could not be verified.");
        }
        if (!Objects.equals(copy.actions(), plan.source().actions())
                || !Objects.equals(copy.name(), plan.source().name())) {
            throw refused("COMMAND_COPY_VERIFICATION_FAILED", "The command identity was not copied exactly.");
        }
        Configuration configuration = plan.configuration();
        if (configuration.kind() == ConfigurationKind.WAIT) {
            if (!Objects.equals(copy.onHoldSecondsInteger(), configuration.waitSeconds())) {
                throw refused("COMMAND_COPY_VERIFICATION_FAILED", "The copied Wait value could not be verified.");
            }
        } else if (!configuredOperation(plan.source(), configuration).equals(copy.operationText())) {
            throw refused("COMMAND_COPY_VERIFICATION_FAILED", "The copied loop values could not be verified.");
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
                revisionService.revision(revisionRows, variables));
    }

    private static String configuredOperation(InstructionRow source, Configuration configuration) {
        return configuration.kind() == ConfigurationKind.WAIT
                ? source.operationText()
                : configuration.intervalSeconds() + ":" + configuration.iterations();
    }
    private static Object configuredHold(InstructionRow source, Configuration configuration) {
        return configuration.kind() == ConfigurationKind.WAIT
                ? configuration.waitSeconds() : source.onHoldSeconds();
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
                    && Objects.equals(actions, other.actions) && Objects.equals(name, other.name)
                    && Objects.equals(xpath, other.xpath) && Objects.equals(coordinates, other.coordinates)
                    && Objects.equals(forceCoordinates, other.forceCoordinates)
                    && Objects.equals(iframeXPath, other.iframeXPath) && Objects.equals(tagName, other.tagName)
                    && Objects.equals(shadowHost, other.shadowHost) && Objects.equals(shadowRoot, other.shadowRoot)
                    && Objects.equals(cssSelector, other.cssSelector) && Objects.equals(description, other.description)
                    && Objects.equals(operation, other.operation) && Objects.equals(optional, other.optional)
                    && Objects.equals(blockMarked, other.blockMarked) && Objects.equals(defaultValue, other.defaultValue)
                    && Objects.equals(actionCustomMaxWaitSec, other.actionCustomMaxWaitSec)
                    && Objects.equals(onHoldSecondsInteger(), other.onHoldSecondsInteger())
                    && Objects.equals(codified, other.codified) && Objects.equals(exportToAbr, other.exportToAbr)
                    && Objects.equals(active, other.active) && Objects.equals(variableId, other.variableId)
                    && Objects.equals(parentBlockId, other.parentBlockId) && Objects.equals(parentId, other.parentId)
                    && botJobId == other.botJobId && Objects.equals(clientNamed, other.clientNamed);
        }
        private InstructionLoad asRevisionRow() {
            InstructionLoad row = new InstructionLoad(); row.setId(id);
            row.setInstructionOrderNumber(order); row.setActions(actionsText());
            row.setOperation(operationText()); row.setBlockId(blockId); row.setVariableId(variableId);
            row.setParentBlockId(parentBlockId); row.setParentId(parentId); return row;
        }
    }
    private record Graph(GraphState state,Set<Integer> blockIds,
                         Map<Integer,InstructionRow> instructions,String revision) {}
    private record Plan(InstructionRow source,int targetBlockId,Configuration configuration,
                        List<InstructionRow> targetRows,int targetIndex,int finalOrder) {}
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
