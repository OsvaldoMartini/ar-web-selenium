package com.allinweb.ch.facade;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads a complete, Bot Job-scoped variable relationship graph without touching the shared
 * {@link PerformLists} caches used by the instruction grids.
 */
@Slf4j
public final class VariableRelationshipService {
    private static final VariableRelationshipService INSTANCE =
            new VariableRelationshipService(() -> PerformDataBase.getInstance().getConnection());

    private static final Comparator<CommandRow> COMMAND_ORDER = Comparator
            .comparingInt((CommandRow row) -> order(row.blockOrder()))
            .thenComparingInt(row -> order(row.instructionOrder()))
            .thenComparingInt(CommandRow::id);

    private final ConnectionProvider connections;
    private final Gson gson = new Gson();

    VariableRelationshipService(ConnectionProvider connections) {
        this.connections = connections;
    }

    public static VariableRelationshipService getInstance() {
        return INSTANCE;
    }

    /**
     * Returns declarations, command relationships, edges, and integrity diagnostics for one Bot
     * Job. A failed query returns a structured error and never a misleading empty successful graph.
     */
    public JsonObject load(int botJobId) {
        if (botJobId <= 0) return failure("A positive Bot Job ID is required.");
        try (Connection connection = connections.open()) {
            List<BlockRow> blocks = loadBlocks(connection, botJobId);
            List<VariableRow> variables = loadVariables(connection, botJobId);
            List<CommandRow> commands = loadCommands(connection, botJobId);
            return graph(botJobId, blocks, variables, commands);
        } catch (SQLException error) {
            log.warn(
                    "Variable relationship SQL failed for Bot Job {}: {}",
                    botJobId,
                    safe(error.getMessage()));
            return failure("Variable relationships could not be loaded.");
        }
    }

    private List<BlockRow> loadBlocks(Connection connection, int botJobId) throws SQLException {
        String sql = "SELECT id, block_order_number, name, active"
                + " FROM block WHERE bot_job_id=? ORDER BY block_order_number, id";
        List<BlockRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, botJobId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new BlockRow(
                            result.getInt("id"),
                            nullableInteger(result, "block_order_number"),
                            result.getString("name"),
                            nullableBoolean(result, "active")));
                }
            }
        }
        return List.copyOf(rows);
    }

    private List<VariableRow> loadVariables(Connection connection, int botJobId)
            throws SQLException {
        String sql = "SELECT v.id, v.type, v.name, v.value, v.local_format, v.delimiter,"
                + " v.instruction_id AS owner_instruction_id,"
                + " owner.id AS resolved_owner_id,"
                + " owner.name AS owner_name, owner.actions AS owner_action,"
                + " owner.block_id AS owner_block_id,"
                + " owner.instruction_order_number AS owner_instruction_order,"
                + " owner.active AS owner_active,"
                + " owner_block.id AS resolved_owner_block_id,"
                + " owner_block.name AS owner_block_name,"
                + " owner_block.block_order_number AS owner_block_order,"
                + " owner_block.active AS owner_block_active"
                + " FROM variable v"
                + " LEFT JOIN instruction owner"
                + " ON owner.id=v.instruction_id AND owner.bot_job_id=v.bot_job_id"
                + " LEFT JOIN block owner_block"
                + " ON owner_block.id=owner.block_id AND owner_block.bot_job_id=v.bot_job_id"
                + " WHERE v.bot_job_id=? ORDER BY v.id";
        List<VariableRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, botJobId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new VariableRow(
                            result.getInt("id"),
                            result.getString("type"),
                            result.getString("name"),
                            result.getString("value"),
                            result.getString("local_format"),
                            result.getString("delimiter"),
                            nullableInteger(result, "owner_instruction_id"),
                            nullableInteger(result, "resolved_owner_id"),
                            result.getString("owner_name"),
                            result.getString("owner_action"),
                            nullableInteger(result, "owner_block_id"),
                            nullableInteger(result, "resolved_owner_block_id"),
                            result.getString("owner_block_name"),
                            nullableInteger(result, "owner_block_order"),
                            nullableInteger(result, "owner_instruction_order"),
                            nullableBoolean(result, "owner_active"),
                            nullableBoolean(result, "owner_block_active")));
                }
            }
        }
        return List.copyOf(rows);
    }

    private List<CommandRow> loadCommands(Connection connection, int botJobId)
            throws SQLException {
        String sql = "SELECT instruction.id, instruction.name, instruction.actions,"
                + " instruction.operation, instruction.variable_id, instruction.parent_id,"
                + " instruction.parent_block_id, instruction.block_id,"
                + " instruction.instruction_order_number, instruction.active,"
                + " block.id AS resolved_block_id, block.name AS block_name,"
                + " block.block_order_number, block.active AS block_active"
                + " FROM instruction"
                + " LEFT JOIN block"
                + " ON block.id=instruction.block_id AND block.bot_job_id=instruction.bot_job_id"
                + " WHERE instruction.bot_job_id=? AND instruction.variable_id IS NOT NULL"
                + " ORDER BY block.block_order_number, instruction.instruction_order_number,"
                + " instruction.id";
        List<CommandRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, botJobId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new CommandRow(
                            result.getInt("id"),
                            result.getString("name"),
                            result.getString("actions"),
                            result.getString("operation"),
                            result.getInt("variable_id"),
                            nullableInteger(result, "parent_id"),
                            nullableInteger(result, "parent_block_id"),
                            nullableInteger(result, "block_id"),
                            nullableInteger(result, "resolved_block_id"),
                            result.getString("block_name"),
                            nullableInteger(result, "block_order_number"),
                            nullableInteger(result, "instruction_order_number"),
                            nullableBoolean(result, "active"),
                            nullableBoolean(result, "block_active")));
                }
            }
        }
        return List.copyOf(rows);
    }

    private JsonObject graph(
            int botJobId,
            List<BlockRow> blocks,
            List<VariableRow> variables,
            List<CommandRow> commands) {
        Map<Integer, List<CommandRow>> commandsByVariable = new LinkedHashMap<>();
        commands.forEach(command -> commandsByVariable
                .computeIfAbsent(command.variableId(), ignored -> new ArrayList<>())
                .add(command));
        commandsByVariable.values().forEach(rows -> rows.sort(COMMAND_ORDER));

        Map<Integer, Integer> declarationsByOwner = new LinkedHashMap<>();
        variables.stream()
                .filter(variable -> variable.ownerInstructionId() != null)
                .forEach(variable -> declarationsByOwner.merge(
                        variable.ownerInstructionId(), 1, Integer::sum));

        JsonArray blockJson = new JsonArray();
        blocks.forEach(block -> blockJson.add(block(block)));

        JsonArray variableJson = new JsonArray();
        JsonArray edges = new JsonArray();
        JsonArray graphDiagnostics = new JsonArray();
        int producerCount = 0;
        int consumerCount = 0;
        int literalAssignmentCount = 0;
        int unusedCount = 0;
        int inactiveLinkCount = 0;
        int warningCount = 0;

        Map<Integer, VariableRow> variablesById = new LinkedHashMap<>();
        for (VariableRow variable : variables) {
            variablesById.put(variable.id(), variable);
            List<CommandRow> related = commandsByVariable
                    .getOrDefault(variable.id(), List.of())
                    .stream()
                    // The owning Web Field declares the variable. A stale self-link must not turn
                    // that field into an apparent executable variable command.
                    .filter(command -> variable.resolvedOwnerId() == null
                            || command.id() != variable.resolvedOwnerId())
                    .toList();
            JsonArray diagnostics = new JsonArray();

            if (variable.ownerInstructionId() == null || variable.resolvedOwnerId() == null) {
                diagnostics.add(diagnostic(
                        "MISSING_OWNER",
                        "ERROR",
                        "The variable declaration does not have an authoritative Web Field.",
                        variable.id(),
                        variable.ownerInstructionId()));
                warningCount++;
            } else {
                edges.add(edge(
                        "owner:" + variable.ownerInstructionId() + ":variable:" + variable.id(),
                        "instruction:" + variable.ownerInstructionId(),
                        "variable:" + variable.id(),
                        "DECLARES"));
                if (declarationsByOwner.getOrDefault(variable.ownerInstructionId(), 0) > 1) {
                    diagnostics.add(diagnostic(
                            "DUPLICATE_DECLARATION",
                            "WARNING",
                            "This Web Field owns more than one variable declaration.",
                            variable.id(),
                            variable.ownerInstructionId()));
                    warningCount++;
                }
                if (variable.ownerBlockId() == null
                        || variable.resolvedOwnerBlockId() == null) {
                    diagnostics.add(diagnostic(
                            "OWNER_BLOCK_MISMATCH",
                            "ERROR",
                            "The variable owner is not attached to a block in this Bot Job.",
                            variable.id(),
                            variable.ownerInstructionId()));
                    warningCount++;
                }
            }

            List<CommandRow> effectiveRelated = related.stream()
                    .filter(VariableRelationshipService::isEffectivelyActive)
                    .toList();
            inactiveLinkCount += related.size() - effectiveRelated.size();
            List<CommandRow> producers = effectiveRelated.stream()
                    .filter(command -> VariableDefinitionPolicy.isProducer(command.action()))
                    .toList();
            List<CommandRow> consumers = effectiveRelated.stream()
                    .filter(command -> VariableDefinitionPolicy.isConsumer(command.action()))
                    .toList();
            producerCount += producers.size();
            consumerCount += consumers.size();
            literalAssignmentCount += effectiveRelated.stream()
                    .filter(command -> "SET".equals(CommandRegistry.canonicalize(command.action())))
                    .count();
            if (related.isEmpty()) unusedCount++;

            if (!consumers.isEmpty() && producers.isEmpty()) {
                diagnostics.add(diagnostic(
                        "MISSING_PRODUCER",
                        "ERROR",
                        "One or more commands read this variable, but no GET command produces it.",
                        variable.id(),
                        null));
                warningCount++;
            }
            if (producers.size() > 1) {
                diagnostics.add(diagnostic(
                        "MULTIPLE_PRODUCERS",
                        "WARNING",
                        "More than one GET command writes this variable.",
                        variable.id(),
                        null));
                warningCount++;
            }
            if (!producers.isEmpty()) {
                CommandRow firstProducer = producers.stream().min(COMMAND_ORDER).orElseThrow();
                for (CommandRow consumer : consumers) {
                    if (COMMAND_ORDER.compare(consumer, firstProducer) < 0) {
                        diagnostics.add(diagnostic(
                                "CONSUMER_BEFORE_PRODUCER",
                                "ERROR",
                                "A variable consumer is ordered before its first GET producer.",
                                variable.id(),
                                consumer.id()));
                        warningCount++;
                    }
                }
            }

            JsonArray commandJson = new JsonArray();
            for (CommandRow command : related) {
                String role = role(command.action());
                commandJson.add(command(command, role));
                if (command.blockId() == null || command.resolvedBlockId() == null) {
                    diagnostics.add(diagnostic(
                            "COMMAND_BLOCK_MISMATCH",
                            "ERROR",
                            "A linked command is not attached to a block in this Bot Job.",
                            variable.id(),
                            command.id()));
                    warningCount++;
                } else if (variable.resolvedOwnerBlockId() != null
                        && !variable.resolvedOwnerBlockId().equals(command.resolvedBlockId())) {
                    diagnostics.add(diagnostic(
                            "COMMAND_OWNER_BLOCK_MISMATCH",
                            "WARNING",
                            "A linked command is stored in a different block than its variable owner.",
                            variable.id(),
                            command.id()));
                    warningCount++;
                }
                if ("PRODUCER".equals(role)
                        && variable.resolvedOwnerId() != null
                        && (command.parentId() == null
                                || !variable.resolvedOwnerId().equals(command.parentId())
                                || (variable.resolvedOwnerBlockId() != null
                                        && command.resolvedBlockId() != null
                                        && !variable.resolvedOwnerBlockId()
                                                .equals(command.resolvedBlockId())))) {
                    diagnostics.add(diagnostic(
                            "PRODUCER_OWNER_MISMATCH",
                            "ERROR",
                            "The GET producer does not point to this variable's owning Web Field.",
                            variable.id(),
                            command.id()));
                    warningCount++;
                }
                if ("PRODUCER".equals(role)) {
                    edges.add(edge(
                            "command:" + command.id() + ":writes:" + variable.id(),
                            "instruction:" + command.id(),
                            "variable:" + variable.id(),
                            "WRITES"));
                } else if ("CONSUMER".equals(role)) {
                    edges.add(edge(
                            "variable:" + variable.id() + ":reads:" + command.id(),
                            "variable:" + variable.id(),
                            "instruction:" + command.id(),
                            "READS"));
                } else if ("LITERAL_ASSIGNMENT".equals(role)
                        && variable.ownerInstructionId() != null) {
                    edges.add(edge(
                            "command:" + command.id() + ":assigns:" + variable.ownerInstructionId(),
                            "instruction:" + command.id(),
                            "instruction:" + variable.ownerInstructionId(),
                            "ASSIGNS_LITERAL"));
                } else if ("INVALID_LINK".equals(role)) {
                    diagnostics.add(diagnostic(
                            "NON_VARIABLE_ACTION_LINK",
                            "WARNING",
                            "A non-variable command carries this variable ID.",
                            variable.id(),
                            command.id()));
                    edges.add(edge(
                            "command:" + command.id() + ":invalid:" + variable.id(),
                            "instruction:" + command.id(),
                            "variable:" + variable.id(),
                            "INVALID_LINK"));
                    warningCount++;
                }
            }

            JsonObject item = variable(variable);
            item.addProperty("unused", related.isEmpty());
            item.add("commands", commandJson);
            item.add("diagnostics", diagnostics);
            variableJson.add(item);
        }

        for (CommandRow command : commands) {
            if (variablesById.containsKey(command.variableId())) continue;
            graphDiagnostics.add(diagnostic(
                    "DANGLING_VARIABLE_LINK",
                    "ERROR",
                    "An instruction references a variable that does not exist in this Bot Job.",
                    command.variableId(),
                    command.id()));
            warningCount++;
        }

        JsonObject summary = new JsonObject();
        summary.addProperty("variableCount", variables.size());
        summary.addProperty("producerCount", producerCount);
        summary.addProperty("consumerCount", consumerCount);
        summary.addProperty("literalAssignmentCount", literalAssignmentCount);
        summary.addProperty("unusedCount", unusedCount);
        summary.addProperty("inactiveLinkCount", inactiveLinkCount);
        summary.addProperty("warningCount", warningCount);

        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("botJobId", botJobId);
        response.add("summary", summary);
        response.add("blocks", blockJson);
        response.add("variables", variableJson);
        response.add("edges", edges);
        response.add("diagnostics", graphDiagnostics);
        response.addProperty("graphRevision", revision(response));
        return response;
    }

    private JsonObject block(BlockRow row) {
        JsonObject json = new JsonObject();
        json.addProperty("id", row.id());
        nullable(json, "order", row.order());
        json.addProperty("name", safe(row.name()));
        nullable(json, "active", row.active());
        return json;
    }

    private JsonObject variable(VariableRow row) {
        JsonObject json = new JsonObject();
        json.addProperty("id", row.id());
        json.addProperty("name", safe(row.name()));
        json.addProperty("type", safe(row.type()));
        json.addProperty("configuredValue", safe(row.value()));
        json.addProperty("localFormat", safe(row.localFormat()));
        json.addProperty("delimiter", safe(row.delimiter()));
        if (row.ownerInstructionId() == null || row.resolvedOwnerId() == null) {
            json.add("owner", null);
            return json;
        }
        JsonObject owner = new JsonObject();
        owner.addProperty("instructionId", row.ownerInstructionId());
        owner.addProperty("instructionName", safe(row.ownerName()));
        owner.addProperty("action", safe(row.ownerAction()));
        nullable(owner, "blockId", row.ownerBlockId());
        owner.addProperty("blockName", safe(row.ownerBlockName()));
        nullable(owner, "blockOrder", row.ownerBlockOrder());
        nullable(owner, "instructionOrder", row.ownerInstructionOrder());
        nullable(owner, "active", row.ownerActive());
        nullable(owner, "blockActive", row.ownerBlockActive());
        json.add("owner", owner);
        return json;
    }

    private JsonObject command(CommandRow row, String role) {
        JsonObject json = new JsonObject();
        json.addProperty("instructionId", row.id());
        json.addProperty("instructionName", safe(row.name()));
        json.addProperty("action", CommandRegistry.canonicalize(row.action()));
        json.addProperty("role", role);
        json.addProperty("operation", safe(row.operation()));
        nullable(json, "parentId", row.parentId());
        nullable(json, "parentBlockId", row.parentBlockId());
        nullable(json, "blockId", row.blockId());
        json.addProperty("blockName", safe(row.blockName()));
        nullable(json, "blockOrder", row.blockOrder());
        nullable(json, "instructionOrder", row.instructionOrder());
        nullable(json, "active", row.active());
        nullable(json, "blockActive", row.blockActive());
        json.addProperty("effectiveActive", isEffectivelyActive(row));
        return json;
    }

    private static String role(String action) {
        if (VariableDefinitionPolicy.isProducer(action)) return "PRODUCER";
        if (VariableDefinitionPolicy.isConsumer(action)) return "CONSUMER";
        if ("SET".equals(CommandRegistry.canonicalize(action))) return "LITERAL_ASSIGNMENT";
        return "INVALID_LINK";
    }

    private static JsonObject edge(String id, String from, String to, String type) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("from", from);
        json.addProperty("to", to);
        json.addProperty("type", type);
        return json;
    }

    private static JsonObject diagnostic(
            String code, String severity, String message, Integer variableId, Integer instructionId) {
        JsonObject json = new JsonObject();
        json.addProperty("code", code);
        json.addProperty("severity", severity);
        json.addProperty("message", message);
        nullable(json, "variableId", variableId);
        nullable(json, "instructionId", instructionId);
        return json;
    }

    private String revision(JsonObject graph) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(gson.toJson(graph).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte item : digest) value.append(String.format("%02x", item & 0xff));
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static JsonObject failure(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("preserveSnapshot", true);
        response.addProperty(
                "error",
                message == null || message.isBlank()
                        ? "Variable relationships could not be loaded."
                        : message);
        return response;
    }

    private static int order(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private static boolean isEffectivelyActive(CommandRow row) {
        return row != null
                && Boolean.TRUE.equals(row.active())
                && Boolean.TRUE.equals(row.blockActive());
    }

    private static Integer nullableInteger(ResultSet result, String column)
            throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet result, String column)
            throws SQLException {
        boolean value = result.getBoolean(column);
        return result.wasNull() ? null : value;
    }

    private static void nullable(JsonObject json, String key, Number value) {
        if (value == null) json.add(key, null);
        else json.addProperty(key, value);
    }

    private static void nullable(JsonObject json, String key, Boolean value) {
        if (value == null) json.add(key, null);
        else json.addProperty(key, value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    private record BlockRow(int id, Integer order, String name, Boolean active) {}

    private record VariableRow(
            int id,
            String type,
            String name,
            String value,
            String localFormat,
            String delimiter,
            Integer ownerInstructionId,
            Integer resolvedOwnerId,
            String ownerName,
            String ownerAction,
            Integer ownerBlockId,
            Integer resolvedOwnerBlockId,
            String ownerBlockName,
            Integer ownerBlockOrder,
            Integer ownerInstructionOrder,
            Boolean ownerActive,
            Boolean ownerBlockActive) {}

    private record CommandRow(
            int id,
            String name,
            String action,
            String operation,
            int variableId,
            Integer parentId,
            Integer parentBlockId,
            Integer blockId,
            Integer resolvedBlockId,
            String blockName,
            Integer blockOrder,
            Integer instructionOrder,
            Boolean active,
            Boolean blockActive) {}
}
