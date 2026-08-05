package com.allinweb.ch.facade;

import com.allinweb.ch.db.InstructionGraphStateRepository;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceResult;
import com.allinweb.ch.db.InstructionGraphStateRepository.AdvanceStatus;
import com.allinweb.ch.db.InstructionGraphStateRepository.GraphState;
import com.allinweb.ch.db.InstructionGraphStateRepository.OwnerKey;
import com.allinweb.ch.db.InstructionVariableCommandConfigRepository;
import com.allinweb.ch.db.InstructionVariableCommandConfigRepository.StoredConfiguration;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.AuthenticatedBotJob;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.model.InstructionGraphMutationV3.WorkspaceKind;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.VariableLoadDTO;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Configuration;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.ConfigurationKind;
import com.allinweb.ch.model.VariablesVariableAutoResolveV1;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Atomic variable auto-resolution for the Variables Resolve Connections flow.
 *
 * <p>Connection selection rules implemented here (server-authoritative):
 * <ul>
 *   <li>VARIABLE AUTO RESOLUTION — a variable command missing its variable gets the
 *       oldest compatible existing variable.</li>
 *   <li>DEFAULT VARIABLE CREATED — when no variable exists, a sequential
 *       {@code Variable_N} is created and connected.</li>
 *   <li>CHECKVALUE VARIABLES — CHECKVALUE (CK / PDF CHECK / CSV CHECK) needs two
 *       independent variables: the oldest becomes the left operand
 *       ({@code Left_Operand} when created), the next-oldest the right operand
 *       ({@code Right_Operand} when created), persisted in the typed shadow
 *       configuration.</li>
 * </ul>
 * Only MISSING slots are filled — existing bindings are never overwritten.
 */
public final class VariablesVariableAutoResolveTransaction {
    private static final Pattern DEFAULT_NAME = Pattern.compile("^Variable_(\\d+)$");
    private static final String DEFAULT_VARIABLE_TYPE = "$String";

    private final InstructionGraphStateRepository stateRepository;
    private final InstructionGraphRevisionService revisionService;
    private final InstructionVariableCommandConfigRepository commandConfigurations;

    public VariablesVariableAutoResolveTransaction() {
        this(
                new InstructionGraphStateRepository(),
                new InstructionGraphRevisionService(),
                new InstructionVariableCommandConfigRepository());
    }

    VariablesVariableAutoResolveTransaction(
            InstructionGraphStateRepository stateRepository,
            InstructionGraphRevisionService revisionService,
            InstructionVariableCommandConfigRepository commandConfigurations) {
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.revisionService = Objects.requireNonNull(revisionService, "revisionService");
        this.commandConfigurations = Objects.requireNonNull(
                commandConfigurations, "commandConfigurations");
    }

    public AutoResolveResult execute(
            Connection connection,
            AuthenticatedBotJob owner,
            VariablesVariableAutoResolveV1.Request request)
            throws SQLException {
        requireOpen(connection);
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");
        if (!connection.getAutoCommit()) {
            throw new SQLException("Variable auto-resolution requires an unbound connection.");
        }
        connection.setAutoCommit(false);
        boolean restoreAutoCommit = true;
        try {
            requireOwner(connection, owner.owner());
            GraphState state = stateRepository.loadOrCreate(connection, owner.owner());
            Graph before = loadGraph(connection, owner.owner(), state);
            List<SlotPlan> plan = validateAndPlan(owner, request, before);
            List<CreatedVariable> created = new ArrayList<>();
            int connectedExisting = 0;
            NameSequencer names = new NameSequencer(before.variableNames());
            for (SlotPlan slot : plan) {
                int variableId;
                if (slot.existingVariableId() != null) {
                    variableId = slot.existingVariableId();
                    connectedExisting++;
                } else {
                    String name = names.next(slot.role());
                    variableId = insertVariable(
                            connection,
                            owner.owner().homeBankingId(),
                            owner.owner().ownerId(),
                            name,
                            slot.producerInstructionId());
                    created.add(new CreatedVariable(
                            variableId, name, slot.instructionId(), slot.role().name()));
                }
                connectSlot(connection, owner.owner(), slot, variableId);
            }
            if (created.isEmpty() && connectedExisting == 0) {
                throw refused(
                        "VARIABLE_AUTO_RESOLVE_NO_CHANGES",
                        "Every selected command already has its variables connected.");
            }
            AdvanceResult advance = stateRepository.compareAndSetIncrement(
                    connection, owner.owner(), request.baseGraphVersion());
            if (!advance.advanced()) {
                throw refused(
                        advance.status() == AdvanceStatus.MISSING
                                ? "VARIABLE_AUTO_RESOLVE_GRAPH_STATE_MISSING"
                                : "VARIABLE_AUTO_RESOLVE_GRAPH_VERSION_STALE",
                        "The Variables graph changed before variable resolution completed.");
            }
            Graph after = loadGraph(connection, owner.owner(), advance.state());
            verify(plan, created, after);
            connection.commit();
            return new AutoResolveResult(
                    owner.owner(), owner.workspaceEpoch(), request.requestId().trim(),
                    List.copyOf(created), connectedExisting,
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

    private List<SlotPlan> validateAndPlan(
            AuthenticatedBotJob owner,
            VariablesVariableAutoResolveV1.Request request,
            Graph graph) throws MutationRefusedException {
        if (!Objects.equals(
                request.contractVersion(), VariablesVariableAutoResolveV1.CONTRACT_VERSION)) {
            throw refused(
                    "VARIABLE_AUTO_RESOLVE_CONTRACT_UNSUPPORTED",
                    "The variable auto-resolve contract is not supported.");
        }
        if (blank(request.requestId()) || blank(request.bindingEpoch())) {
            throw refused(
                    "VARIABLE_AUTO_RESOLVE_IDENTITY_REQUIRED",
                    "Variable resolution requires request and binding IDs.");
        }
        if (!Objects.equals(request.workspaceEpoch(), owner.workspaceEpoch())) {
            throw refused(
                    "VARIABLE_AUTO_RESOLVE_WORKSPACE_CHANGED",
                    "The Bot Job workspace changed before variable resolution.");
        }
        if (!Objects.equals(request.baseGraphVersion(), graph.state().version())) {
            throw refused(
                    "VARIABLE_AUTO_RESOLVE_GRAPH_VERSION_STALE",
                    "The Variables graph version changed before variable resolution.");
        }
        if (blank(request.graphRevision())
                || !request.graphRevision().trim().equals(graph.revision())) {
            throw refused(
                    "VARIABLE_AUTO_RESOLVE_GRAPH_REVISION_STALE",
                    "The Variables graph changed before variable resolution.");
        }
        List<Integer> requested = request.instructionIds() == null
                ? List.of()
                : request.instructionIds();
        if (requested.isEmpty()) {
            throw refused(
                    "VARIABLE_AUTO_RESOLVE_SCOPE_REQUIRED",
                    "Select at least one command for variable resolution.");
        }
        LinkedHashSet<Integer> scope = new LinkedHashSet<>();
        for (Integer instructionId : requested) {
            if (instructionId == null || !graph.instructions().containsKey(instructionId)) {
                throw refused(
                        "VARIABLE_AUTO_RESOLVE_SOURCE_MISSING",
                        "A selected command no longer exists.");
            }
            scope.add(instructionId);
        }

        // Oldest-first existing variables (rule: oldest compatible auto-selected).
        List<Integer> existingOldestFirst = new ArrayList<>(graph.variableIds());
        List<SlotPlan> plan = new ArrayList<>();
        for (InstructionRow row : graph.instructions().values()) {
            if (!scope.contains(row.id())) continue;
            String action = CommandRegistry.canonicalize(row.action());
            if (!CommandRegistry.usesVariableBinding(action)) continue;
            boolean check = isCheckCommand(action);
            Integer leftExisting = null;
            Integer rightExisting = null;
            if (row.variableId() == null) {
                leftExisting = existingOldestFirst.isEmpty() ? null : existingOldestFirst.get(0);
            }
            if (check) {
                Integer currentRight = graph.slots()
                        .getOrDefault(row.id(), Map.of())
                        .get("RIGHT");
                if (currentRight == null) {
                    Integer leftUsed = row.variableId() != null ? row.variableId() : leftExisting;
                    rightExisting = existingOldestFirst.stream()
                            .filter(candidate -> !Objects.equals(candidate, leftUsed))
                            .findFirst()
                            .orElse(null);
                    plan.add(new SlotPlan(
                            row.id(), action, SlotRole.CHECK_RIGHT, rightExisting, null));
                }
            }
            if (row.variableId() == null) {
                plan.add(new SlotPlan(
                        row.id(),
                        action,
                        check ? SlotRole.CHECK_LEFT : SlotRole.PRIMARY,
                        leftExisting,
                        !check && CommandRegistry.producesVariableValue(action)
                                ? row.id()
                                : null));
            }
        }
        return plan;
    }

    private int insertVariable(
            Connection connection,
            int homeBankingId,
            int botJobId,
            String name,
            Integer producerInstructionId)
            throws SQLException {
        // The table's primary key is (home_banking_id, bot_job_id, id): the variable id
        // is allocated per Bot Job, exactly like BotJobVariableDefinitionRepository's
        // callers do. Serialization via commitWorkspaceMutation makes MAX(id)+1 safe.
        int variableId;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(id),0)+1 FROM bot_job_variable_definition"
                        + " WHERE home_banking_id=? AND bot_job_id=?")) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("The next variable ID could not be allocated.");
                }
                variableId = rows.getInt(1);
            }
        }
        Timestamp now = Timestamp.from(Instant.now());
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bot_job_variable_definition"
                        + " (home_banking_id,bot_job_id,id,variable_type,name,configured_value,"
                        + "local_format,delimiter,producer_instruction_id,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,NULL,NULL,NULL,?,?,?)")) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            statement.setInt(3, variableId);
            statement.setString(4, DEFAULT_VARIABLE_TYPE);
            statement.setString(5, name);
            if (producerInstructionId == null) statement.setNull(6, Types.INTEGER);
            else statement.setInt(6, producerInstructionId);
            statement.setTimestamp(7, now);
            statement.setTimestamp(8, now);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Default variable insert did not create one row.");
            }
        }
        return variableId;
    }

    private void connectSlot(
            Connection connection, OwnerKey owner, SlotPlan slot, int variableId)
            throws SQLException {
        String slotName = slot.role() == SlotRole.CHECK_RIGHT
                ? "RIGHT"
                : slot.role() == SlotRole.CHECK_LEFT
                        ? "LEFT"
                        : primarySlot(slot.action());
        if (slotName == null) {
            throw refused(
                    "VARIABLE_AUTO_RESOLVE_SLOT_UNSUPPORTED",
                    "The command does not define a variable slot.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO instruction_variable_slot"
                        + " (home_banking_id,bot_job_id,instruction_id,slot,variable_id,"
                        + "slot_revision,created_at,updated_at)"
                        + " SELECT ?,?,?,?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP"
                        + " WHERE NOT EXISTS (SELECT 1 FROM instruction_variable_slot"
                        + " WHERE home_banking_id=? AND bot_job_id=?"
                        + " AND instruction_id=? AND slot=?)")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            statement.setInt(3, slot.instructionId());
            statement.setString(4, slotName);
            statement.setInt(5, variableId);
            statement.setInt(6, owner.homeBankingId());
            statement.setInt(7, owner.ownerId());
            statement.setInt(8, slot.instructionId());
            statement.setString(9, slotName);
            if (statement.executeUpdate() != 1) {
                throw refused(
                        "VARIABLE_AUTO_RESOLVE_SOURCE_CHANGED",
                        "A selected command slot changed before its variable could be connected.");
            }
        }
    }

    private static String primarySlot(String actionValue) {
        return switch (CommandRegistry.canonicalize(actionValue)) {
            case "CK", "CSV CHECK", "PDF CHECK" -> "LEFT";
            case "GET" -> "GET_WRITE";
            case "SET" -> "READ_SET";
            case "E" -> "READ";
            default -> null;
        };
    }

    private void verify(
            List<SlotPlan> plan, List<CreatedVariable> created, Graph after)
            throws MutationRefusedException {
        Set<Integer> variableIds = after.variableIds().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (CreatedVariable variable : created) {
            if (!variableIds.contains(variable.variableId())) {
                throw refused(
                        "VARIABLE_AUTO_RESOLVE_VERIFICATION_FAILED",
                        "A created default variable could not be verified.");
            }
        }
        for (SlotPlan slot : plan) {
            InstructionRow row = after.instructions().get(slot.instructionId());
            if (row == null) {
                throw refused(
                        "VARIABLE_AUTO_RESOLVE_VERIFICATION_FAILED",
                        "A resolved command could not be verified.");
            }
            if (slot.role() == SlotRole.CHECK_RIGHT) {
                Integer rightVariableId = after.slots()
                        .getOrDefault(slot.instructionId(), Map.of())
                        .get("RIGHT");
                if (rightVariableId == null || !variableIds.contains(rightVariableId)) {
                    throw refused(
                            "VARIABLE_AUTO_RESOLVE_VERIFICATION_FAILED",
                            "A CHECKVALUE right operand could not be verified.");
                }
            } else if (row.variableId() == null
                    || !variableIds.contains(row.variableId())) {
                throw refused(
                        "VARIABLE_AUTO_RESOLVE_VERIFICATION_FAILED",
                        "A connected variable could not be verified.");
            }
        }
    }

    private Graph loadGraph(Connection connection, OwnerKey owner, GraphState state)
            throws SQLException {
        LinkedHashMap<Integer, InstructionRow> instructions = new LinkedHashMap<>();
        List<InstructionLoad> revisionRows = new ArrayList<>();
        Map<Integer, Map<String, Integer>> slots = loadSlots(connection, owner);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,instruction_order_number,actions,operation,block_id,"
                        + "parent_block_id,parent_id FROM instruction WHERE bot_job_id=?"
                        + " ORDER BY block_id,instruction_order_number,id")) {
            statement.setInt(1, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String action = rows.getString("actions");
                    String primarySlot = primarySlot(action);
                    Integer variableId = primarySlot == null
                            ? null
                            : slots.getOrDefault(rows.getInt("id"), Map.of()).get(primarySlot);
                    InstructionRow row = new InstructionRow(
                            rows.getInt("id"), rows.getInt("instruction_order_number"),
                            action, rows.getString("operation"),
                            rows.getInt("block_id"), variableId,
                            nullableInteger(rows, "parent_block_id"),
                            nullableInteger(rows, "parent_id"));
                    instructions.put(row.id(), row);
                    revisionRows.add(row.asRevisionRow());
                }
            }
        }
        List<Integer> variableIds = new ArrayList<>();
        List<String> variableNames = new ArrayList<>();
        List<VariableLoadDTO> variables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,name,producer_instruction_id FROM bot_job_variable_definition"
                        + " WHERE home_banking_id=? AND bot_job_id=? ORDER BY id")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    variableIds.add(rows.getInt("id"));
                    variableNames.add(rows.getString("name"));
                    variables.add(new VariableLoadDTO(
                            rows.getInt("id"), null, owner.ownerId(),
                            nullableInteger(rows, "producer_instruction_id"),
                            null, null, null, null, null, 0));
                }
            }
        }
        Map<Integer, StoredConfiguration> configurations =
                commandConfigurations.loadForBotJob(connection, owner.ownerId());
        return new Graph(
                state, Map.copyOf(instructions), List.copyOf(variableIds),
                List.copyOf(variableNames), configurations, slots,
                revisionService.revision(revisionRows, variables));
    }

    private Map<Integer, Map<String, Integer>> loadSlots(
            Connection connection, OwnerKey owner) throws SQLException {
        Map<Integer, Map<String, Integer>> slots = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT instruction_id,slot,variable_id FROM instruction_variable_slot"
                        + " WHERE home_banking_id=? AND bot_job_id=?")) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.ownerId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    slots.computeIfAbsent(rows.getInt("instruction_id"), ignored -> new LinkedHashMap<>())
                            .put(rows.getString("slot"), rows.getInt("variable_id"));
                }
            }
        }
        Map<Integer, Map<String, Integer>> immutable = new LinkedHashMap<>();
        slots.forEach((instructionId, values) -> immutable.put(instructionId, Map.copyOf(values)));
        return Map.copyOf(immutable);
    }

    private void requireOwner(Connection connection, OwnerKey owner) throws SQLException {
        if (owner.workspaceKind() != WorkspaceKind.BOT_JOB) {
            throw refused(
                    "VARIABLE_AUTO_RESOLVE_BOT_JOB_REQUIRED",
                    "Variable resolution requires a Bot Job owner.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM bot_job WHERE id=? AND home_banking_id=?")) {
            statement.setInt(1, owner.ownerId());
            statement.setInt(2, owner.homeBankingId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw refused(
                            "VARIABLE_AUTO_RESOLVE_OWNER_MISMATCH",
                            "The organization does not own this Bot Job.");
                }
            }
        }
    }

    private static boolean isCheckCommand(String action) {
        return "CK".equals(action) || "PDF CHECK".equals(action) || "CSV CHECK".equals(action);
    }

    /** Sequential default names: Variable_N, Left_Operand(_N), Right_Operand(_N). */
    private static final class NameSequencer {
        private final Set<String> taken;
        private int nextVariableNumber;

        private NameSequencer(List<String> existingNames) {
            this.taken = new java.util.HashSet<>();
            int highest = 0;
            for (String name : existingNames) {
                if (name == null) continue;
                taken.add(name);
                Matcher matcher = DEFAULT_NAME.matcher(name.trim());
                if (matcher.matches()) {
                    highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
                }
            }
            this.nextVariableNumber = highest + 1;
        }

        private String next(SlotRole role) {
            if (role == SlotRole.PRIMARY) {
                String name;
                do {
                    name = "Variable_" + nextVariableNumber++;
                } while (taken.contains(name));
                taken.add(name);
                return name;
            }
            String base = role == SlotRole.CHECK_LEFT ? "Left_Operand" : "Right_Operand";
            String name = base;
            int suffix = 2;
            while (taken.contains(name)) name = base + "_" + suffix++;
            taken.add(name);
            return name;
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
        if (connection == null || connection.isClosed()) {
            throw new SQLException("An open database connection is required.");
        }
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

    enum SlotRole { PRIMARY, CHECK_LEFT, CHECK_RIGHT }

    private record SlotPlan(
            int instructionId, String action, SlotRole role,
            Integer existingVariableId, Integer producerInstructionId) {}

    private record InstructionRow(
            int id, int order, String action, String operation, int blockId,
            Integer variableId, Integer parentBlockId, Integer parentId) {
        private InstructionLoad asRevisionRow() {
            InstructionLoad row = new InstructionLoad();
            row.setId(id); row.setInstructionOrderNumber(order); row.setActions(action);
            row.setOperation(operation); row.setBlockId(blockId); row.setVariableId(variableId);
            row.setParentBlockId(parentBlockId); row.setParentId(parentId);
            return row;
        }
    }

    private record Graph(
            GraphState state, Map<Integer, InstructionRow> instructions,
            List<Integer> variableIds, List<String> variableNames,
            Map<Integer, StoredConfiguration> configurations,
            Map<Integer, Map<String, Integer>> slots, String revision) {}

    public record CreatedVariable(
            int variableId, String name, int instructionId, String role) {}

    public record AutoResolveResult(
            OwnerKey owner, long workspaceEpoch, String requestId,
            List<CreatedVariable> createdVariables, int connectedExisting,
            long previousGraphVersion, long committedGraphVersion, String graphRevision,
            boolean duplicate) {
        public AutoResolveResult asDuplicate() {
            return new AutoResolveResult(
                    owner, workspaceEpoch, requestId, createdVariables, connectedExisting,
                    previousGraphVersion, committedGraphVersion, graphRevision, true);
        }
    }
}
