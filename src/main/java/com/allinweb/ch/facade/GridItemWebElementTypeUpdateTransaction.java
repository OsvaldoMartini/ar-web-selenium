package com.allinweb.ch.facade;

import com.allinweb.ch.db.InstructionGraphStateRepository;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceResult;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceStatus;
import com.allinweb.ch.db.InstructionGraphStateRepository.GraphState;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.model.GridItemWebElementTypeContracts.Request;
import com.allinweb.ch.model.GridItemWebElementTypeContracts.WebElementType;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.VariableLoadDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Atomic persistence boundary dedicated to one GridItem Web Element execution-type change. */
public final class GridItemWebElementTypeUpdateTransaction {
    private final InstructionGraphStateRepository stateRepository;
    private final InstructionGraphRevisionService revisionService;

    public GridItemWebElementTypeUpdateTransaction() {
        this(new InstructionGraphStateRepository(), new InstructionGraphRevisionService());
    }

    GridItemWebElementTypeUpdateTransaction(
            InstructionGraphStateRepository stateRepository,
            InstructionGraphRevisionService revisionService) {
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.revisionService = Objects.requireNonNull(revisionService, "revisionService");
    }

    public UpdateResult execute(
            Connection connection,
            AuthenticatedBotJob authenticatedOwner,
            Request request)
            throws SQLException {
        requireOpen(connection);
        Objects.requireNonNull(authenticatedOwner, "authenticatedOwner");
        Objects.requireNonNull(request, "request");
        if (!connection.getAutoCommit()) {
            throw new SQLException(
                    "GridItem Web Element type update requires an unbound connection.");
        }

        connection.setAutoCommit(false);
        boolean restoreAutoCommit = true;
        try {
            OwnerKey owner = authenticatedOwner.owner();
            requireOwnedBotJob(connection, owner);
            Graph before = loadGraph(connection, owner);
            InstructionRow selected = validate(
                    authenticatedOwner, request, before);
            WebElementType previousType = storedType(selected.action());
            String replacementAction = persistedAction(
                    request.replacementType(), selected.name());

            if (replacementAction.equals(selected.action())) {
                connection.commit();
                return new UpdateResult(
                        owner,
                        authenticatedOwner.workspaceEpoch(),
                        request.requestId(),
                        request.instructionId(),
                        previousType,
                        request.replacementType(),
                        replacementAction,
                        before.state().version(),
                        before.state().version(),
                        before.revision(),
                        false,
                        false);
            }

            updateExactlyOne(
                    connection,
                    owner.ownerId(),
                    selected.id(),
                    selected.action(),
                    replacementAction);

            AdvanceResult advance = stateRepository.compareAndSetIncrement(
                    connection, owner, request.baseGraphVersion());
            if (!advance.advanced()) {
                throw refused(
                        advance.status() == AdvanceStatus.MISSING
                                ? "WEB_ELEMENT_TYPE_GRAPH_STATE_MISSING"
                                : "WEB_ELEMENT_TYPE_GRAPH_VERSION_STALE",
                        "The Bot Job graph changed before the Web Element type was saved.");
            }

            Graph after = loadGraph(connection, owner);
            InstructionRow committed = after.instructions().get(selected.id());
            verify(before, after, selected, committed, replacementAction, advance.state());
            connection.commit();
            return new UpdateResult(
                    owner,
                    authenticatedOwner.workspaceEpoch(),
                    request.requestId(),
                    request.instructionId(),
                    previousType,
                    request.replacementType(),
                    replacementAction,
                    before.state().version(),
                    after.state().version(),
                    after.revision(),
                    true,
                    false);
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

    private InstructionRow validate(
            AuthenticatedBotJob authenticatedOwner,
            Request request,
            Graph graph)
            throws MutationRefusedException {
        OwnerKey owner = authenticatedOwner.owner();
        if (request.homeBankingId() != owner.homeBankingId()
                || request.botJobId() != owner.ownerId()) {
            throw refused(
                    "WEB_ELEMENT_TYPE_OWNER_MISMATCH",
                    "The Web Element does not belong to the active Bot Job.");
        }
        if (request.workspaceEpoch() != authenticatedOwner.workspaceEpoch()) {
            throw refused(
                    "WEB_ELEMENT_TYPE_WORKSPACE_CHANGED",
                    "The Bot Job workspace changed before the Web Element type was saved.");
        }
        if (request.baseGraphVersion() != graph.state().version()) {
            throw refused(
                    "WEB_ELEMENT_TYPE_GRAPH_VERSION_STALE",
                    "The Bot Job graph changed. Refresh before changing the Web Element type.");
        }
        if (!request.graphRevision().equalsIgnoreCase(graph.revision())) {
            throw refused(
                    "WEB_ELEMENT_TYPE_GRAPH_REVISION_STALE",
                    "The Bot Job graph changed. Refresh before changing the Web Element type.");
        }

        InstructionRow selected = graph.instructions().get(request.instructionId());
        if (selected == null) {
            throw refused(
                    "WEB_ELEMENT_TYPE_INSTRUCTION_MISSING",
                    "The selected Web Element no longer exists in this Bot Job.");
        }
        WebElementType actual = storedType(selected.action());
        if (actual != request.expectedType()) {
            throw refused(
                    "WEB_ELEMENT_TYPE_EXPECTED_STALE",
                    "The Web Element type changed. Refresh before trying again.");
        }
        return selected;
    }

    private Graph loadGraph(Connection connection, OwnerKey owner) throws SQLException {
        GraphState state = stateRepository.loadOrCreate(connection, owner);
        Map<Integer, Map<String, Integer>> slots = loadVariableSlots(connection, owner);
        Map<Integer, InstructionRow> instructions = new LinkedHashMap<>();
        List<InstructionLoad> revisionRows = new ArrayList<>();

        String instructionSql = "SELECT id,block_id,instruction_order_number,actions,name,"
                + "parent_id,parent_block_id,operation FROM instruction"
                + " WHERE bot_job_id=? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(instructionSql)) {
            statement.setInt(1, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int id = rows.getInt("id");
                    String action = rows.getString("actions");
                    String primarySlot = primaryVariableSlot(action);
                    Integer variableId = primarySlot == null
                            ? null
                            : slots.getOrDefault(id, Map.of()).get(primarySlot);
                    InstructionRow instruction = new InstructionRow(
                            id,
                            rows.getInt("block_id"),
                            rows.getInt("instruction_order_number"),
                            action,
                            rows.getString("name"),
                            nullableInteger(rows, "parent_id"),
                            nullableInteger(rows, "parent_block_id"),
                            variableId,
                            rows.getString("operation"));
                    instructions.put(id, instruction);
                    revisionRows.add(instruction.asRevisionRow());
                }
            }
        }

        List<VariableLoadDTO> variableOwners = new ArrayList<>();
        String variableSql = "SELECT id,producer_instruction_id FROM bot_job_variable_definition"
                + " WHERE home_banking_id=? AND bot_job_id=? ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(variableSql)) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    variableOwners.add(new VariableLoadDTO(
                            rows.getInt("id"),
                            owner.homeBankingId(),
                            owner.ownerId(),
                            nullableInteger(rows, "producer_instruction_id"),
                            null,
                            null,
                            null,
                            null,
                            null,
                            0));
                }
            }
        }

        return new Graph(
                state,
                instructions,
                revisionService.revision(revisionRows, variableOwners));
    }

    private static Map<Integer, Map<String, Integer>> loadVariableSlots(
            Connection connection, OwnerKey owner)
            throws SQLException {
        Map<Integer, Map<String, Integer>> result = new LinkedHashMap<>();
        String sql = "SELECT instruction_id,slot,variable_id FROM instruction_variable_slot"
                + " WHERE home_banking_id=? AND bot_job_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.computeIfAbsent(
                                    rows.getInt("instruction_id"),
                                    ignored -> new LinkedHashMap<>())
                            .put(rows.getString("slot"), rows.getInt("variable_id"));
                }
            }
        }
        return result;
    }

    private static void updateExactlyOne(
            Connection connection,
            int botJobId,
            int instructionId,
            String expectedAction,
            String replacementAction)
            throws SQLException {
        String sql = "UPDATE instruction SET actions=?"
                + " WHERE id=? AND bot_job_id=? AND actions=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, replacementAction);
            statement.setInt(2, instructionId);
            statement.setInt(3, botJobId);
            statement.setString(4, expectedAction);
            if (statement.executeUpdate() != 1) {
                throw refused(
                        "WEB_ELEMENT_TYPE_WRITE_CONFLICT",
                        "The Web Element changed before its type could be saved.");
            }
        }
    }

    private static void verify(
            Graph before,
            Graph after,
            InstructionRow previous,
            InstructionRow committed,
            String replacementAction,
            GraphState advancedState)
            throws MutationRefusedException {
        if (after.state().version() != advancedState.version()
                || after.state().version() != before.state().version() + 1L) {
            throw refused(
                    "WEB_ELEMENT_TYPE_FINAL_VERSION_INVALID",
                    "The Web Element type graph version was not advanced exactly once.");
        }
        if (before.instructions().size() != after.instructions().size()
                || committed == null
                || !replacementAction.equals(committed.action())) {
            throw refused(
                    "WEB_ELEMENT_TYPE_NOT_COMMITTED",
                    "The Web Element type could not be verified after saving.");
        }
        if (!previous.sameNonActionState(committed)) {
            throw refused(
                    "WEB_ELEMENT_TYPE_RELATIONSHIP_CHANGED",
                    "The Web Element relationship changed during the type update.");
        }
        if (before.revision().equals(after.revision())) {
            throw refused(
                    "WEB_ELEMENT_TYPE_REVISION_UNCHANGED",
                    "The Web Element graph revision did not record the type update.");
        }
    }

    static WebElementType storedType(String action) throws MutationRefusedException {
        String canonical = CommandRegistry.canonicalize(action);
        return switch (canonical) {
            case "I", "INPUT" -> WebElementType.INPUT;
            case "O", "OUTPUT" -> WebElementType.OUTPUT;
            // OTHER is a legacy physical Web Element action dispatched through CLICK by both
            // PerformActions and PlaywrightBridge. The next persisted state is normalized to C.
            case "C", "CLICK", "W", "OTHER" -> WebElementType.CLICK;
            default -> throw refused(
                    "WEB_ELEMENT_TYPE_NOT_ELIGIBLE",
                    "Only INPUT, OUTPUT, and CLICK Web Elements can change execution type.");
        };
    }

    static String persistedAction(WebElementType type, String canonicalName)
            throws MutationRefusedException {
        if (type == null) {
            throw refused(
                    "WEB_ELEMENT_TYPE_REQUIRED", "Select a Web Element execution type.");
        }
        if (type == WebElementType.CLICK) return "C";
        if (canonicalName == null || canonicalName.isBlank()) {
            throw refused(
                    "WEB_ELEMENT_TYPE_NAME_REQUIRED",
                    "INPUT and OUTPUT Web Elements require a canonical instruction name.");
        }
        return (type == WebElementType.INPUT ? "I:" : "O:") + canonicalName.trim();
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

    private static void requireOwnedBotJob(Connection connection, OwnerKey owner)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM bot_job WHERE id=? AND home_banking_id=?")) {
            statement.setInt(1, owner.ownerId());
            statement.setInt(2, owner.homeBankingId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw refused(
                            "WEB_ELEMENT_TYPE_OWNER_MISMATCH",
                            "The organization does not own the requested Bot Job.");
                }
            }
        }
    }

    private static Integer nullableInteger(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static void requireOpen(Connection connection) throws SQLException {
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

    private static void close(Connection connection, Throwable failure) {
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
            // The caller still owns the connection; the transaction outcome is already final.
        }
    }

    private static MutationRefusedException refused(String code, String message) {
        return new MutationRefusedException(code, message);
    }

    private record InstructionRow(
            int id,
            int blockId,
            int order,
            String action,
            String name,
            Integer parentId,
            Integer parentBlockId,
            Integer variableId,
            String operation) {
        private InstructionLoad asRevisionRow() {
            InstructionLoad row = new InstructionLoad();
            row.setId(id);
            row.setBlockId(blockId);
            row.setInstructionOrderNumber(order);
            row.setActions(action);
            row.setParentId(parentId);
            row.setParentBlockId(parentBlockId);
            row.setVariableId(variableId);
            row.setOperation(operation);
            return row;
        }

        private boolean sameNonActionState(InstructionRow other) {
            return other != null
                    && id == other.id
                    && blockId == other.blockId
                    && order == other.order
                    && Objects.equals(name, other.name)
                    && Objects.equals(parentId, other.parentId)
                    && Objects.equals(parentBlockId, other.parentBlockId)
                    && Objects.equals(variableId, other.variableId)
                    && Objects.equals(operation, other.operation);
        }
    }

    private record Graph(
            GraphState state,
            Map<Integer, InstructionRow> instructions,
            String revision) {}

    public record UpdateResult(
            OwnerKey owner,
            long workspaceEpoch,
            String requestId,
            int instructionId,
            WebElementType previousType,
            WebElementType committedType,
            String committedAction,
            long previousGraphVersion,
            long committedGraphVersion,
            String graphRevision,
            boolean changed,
            boolean duplicate) {
        public UpdateResult asDuplicate() {
            return new UpdateResult(
                    owner,
                    workspaceEpoch,
                    requestId,
                    instructionId,
                    previousType,
                    committedType,
                    committedAction,
                    previousGraphVersion,
                    committedGraphVersion,
                    graphRevision,
                    changed,
                    true);
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
}
