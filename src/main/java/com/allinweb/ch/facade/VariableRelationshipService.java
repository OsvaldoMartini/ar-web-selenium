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
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads a complete, Bot Job-scoped variable relationship graph without touching the shared
 * {@link PerformLists} caches used by the instruction grids.
 */
@Slf4j
public final class VariableRelationshipService {
    private static final VariableRelationshipService INSTANCE =
            new VariableRelationshipService(() -> PerformDataBase.getInstance().getConnection());

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
                + " instruction.operation, instruction.tag_name, instruction.variable_id,"
                + " instruction.parent_id,"
                + " instruction.parent_block_id, instruction.block_id,"
                + " instruction.instruction_order_number, instruction.active,"
                + " block.id AS resolved_block_id, block.name AS block_name,"
                + " block.block_order_number, block.active AS block_active"
                + " FROM instruction"
                + " LEFT JOIN block"
                + " ON block.id=instruction.block_id AND block.bot_job_id=instruction.bot_job_id"
                + " WHERE instruction.bot_job_id=?"
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
                            result.getString("tag_name"),
                            nullableInteger(result, "variable_id"),
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

    /**
     * Emits ONLY raw facts under {@code graphKind: "RAW_FACTS_V1"}. React
     * (variablesGraph.ts) is the source of truth for the semantic layer — command roles,
     * relationship edges, integrity diagnostics, and the summary — recomputed on every
     * realtime snapshot. Java deliberately performs no classification here.
     */
    private JsonObject graph(
            int botJobId,
            List<BlockRow> blocks,
            List<VariableRow> variables,
            List<CommandRow> commands) {
        JsonArray blockJson = new JsonArray();
        blocks.forEach(block -> blockJson.add(block(block)));

        JsonArray variableJson = new JsonArray();
        variables.forEach(variable -> variableJson.add(rawVariable(variable)));

        JsonArray commandJson = new JsonArray();
        commands.forEach(command -> commandJson.add(rawCommand(command)));

        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("botJobId", botJobId);
        response.addProperty("graphKind", "RAW_FACTS_V1");
        response.add("blocks", blockJson);
        response.add("rawVariables", variableJson);
        response.add("rawCommands", commandJson);
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

    private JsonObject rawVariable(VariableRow row) {
        JsonObject json = new JsonObject();
        json.addProperty("id", row.id());
        json.addProperty("name", safe(row.name()));
        json.addProperty("type", safe(row.type()));
        json.addProperty("configuredValue", safe(row.value()));
        json.addProperty("localFormat", safe(row.localFormat()));
        json.addProperty("delimiter", safe(row.delimiter()));
        nullable(json, "ownerInstructionId", row.ownerInstructionId());
        nullable(json, "resolvedOwnerId", row.resolvedOwnerId());
        json.addProperty("ownerName", safe(row.ownerName()));
        json.addProperty("ownerAction", safe(row.ownerAction()));
        nullable(json, "ownerBlockId", row.ownerBlockId());
        nullable(json, "resolvedOwnerBlockId", row.resolvedOwnerBlockId());
        json.addProperty("ownerBlockName", safe(row.ownerBlockName()));
        nullable(json, "ownerBlockOrder", row.ownerBlockOrder());
        nullable(json, "ownerInstructionOrder", row.ownerInstructionOrder());
        nullable(json, "ownerActive", row.ownerActive());
        nullable(json, "ownerBlockActive", row.ownerBlockActive());
        return json;
    }

    private JsonObject rawCommand(CommandRow row) {
        JsonObject json = new JsonObject();
        json.addProperty("instructionId", row.id());
        json.addProperty("instructionName", safe(row.name()));
        json.addProperty("action", safe(row.action()));
        json.addProperty("operation", safe(row.operation()));
        json.addProperty("tagName", safe(row.tagName()));
        nullable(json, "variableId", row.variableId());
        nullable(json, "parentId", row.parentId());
        nullable(json, "parentBlockId", row.parentBlockId());
        nullable(json, "blockId", row.blockId());
        nullable(json, "resolvedBlockId", row.resolvedBlockId());
        json.addProperty("blockName", safe(row.blockName()));
        nullable(json, "blockOrder", row.blockOrder());
        nullable(json, "instructionOrder", row.instructionOrder());
        nullable(json, "active", row.active());
        nullable(json, "blockActive", row.blockActive());
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
            String tagName,
            Integer variableId,
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
