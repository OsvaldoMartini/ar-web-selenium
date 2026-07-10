package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/** Pane-free backend for the React instruction command panel. */
public final class CommandEditorService {

    private static final CommandEditorService INSTANCE = new CommandEditorService();
    private final PerformDataBase database = PerformDataBase.getInstance();
    private final PerformDBEngine engine = PerformDBEngine.getInstance();
    private final PerformLists lists = PerformLists.getInstance();
    private final WebSocketSessionManager sessions = WebSocketSessionManager.getInstance();
    private final Gson gson = new Gson();

    private CommandEditorService() {}

    public static CommandEditorService getInstance() {
        return INSTANCE;
    }

    public JsonObject bootstrap(JsonObject body) {
        JsonObject response = new JsonObject();
        String sessionId = string(body, "targetSessionId", "botJobTasks");
        int whereId = "componentTasks".equals(sessionId)
                ? integer(body, "homeBankingId", -1)
                : integer(body, "botJobId", -1);
        String variableTable = "componentTasks".equals(sessionId) ? "component_variable" : "variable";
        String blockTable = "componentTasks".equals(sessionId) ? "component_block" : "block";
        int instructionId = integer(body, "instructionId", -1);
        String instructionName = string(body, "instructionName", "");

        ErrorMessage error = database.loadInstructions(
                whereId, -1, -1, "componentTasks".equals(sessionId) ? "component_instruction" : "instruction");
        if (error == null) error = database.loadBlocks(whereId, "", blockTable);

        response.addProperty("ok", error == null);
        response.add("variables", loadVariables(variableTable, whereId));
        response.add("webFields", webFields(sessionId));
        response.add("blocks", gson.toJsonTree(
                "componentTasks".equals(sessionId) ? lists.getListBlockComp() : lists.getListBlock()));
        response.add("commands", commandCatalog());
        if (error != null) response.addProperty("error", error.getErrorMessage());
        return response;
    }

    private JsonArray webFields(String sessionId) {
        List<InstructionLoad> instructions = "componentTasks".equals(sessionId)
                ? lists.getListInstructionComp()
                : lists.getListInstruction();
        JsonArray rows = new JsonArray();
        for (InstructionLoad instruction : instructions) {
            if (instruction == null || instruction.getId() == null || isSpecialAction(instruction.getActions())) continue;
            JsonObject row = new JsonObject();
            row.addProperty("id", instruction.getId());
            row.addProperty("name", instruction.getName());
            row.addProperty("actions", instruction.getActions());
            row.addProperty("tagName", instruction.getTagName());
            row.addProperty("blockId", instruction.getBlockId());
            row.addProperty("blockName", instruction.getBlockName());
            row.addProperty("instructionOrderNumber", instruction.getInstructionOrderNumber());
            rows.add(row);
        }
        return rows;
    }

    private JsonArray loadVariables(String table, int whereId) {
        JsonArray rows = new JsonArray();
        String ownerColumn = "component_variable".equals(table) ? "home_banking_id" : "bot_job_id";
        String instructionTable = "component_variable".equals(table) ? "component_instruction" : "instruction";
        String sql = "SELECT v.id,v.type,v.name,v.value,v.local_format,v.delimiter,v.instruction_id,"
                + "COUNT(i.variable_id) used_vars FROM " + table + " v LEFT JOIN " + instructionTable
                + " i ON i.variable_id=v.id WHERE v." + ownerColumn + "=? "
                + "GROUP BY v.id,v.type,v.name,v.value,v.local_format,v.delimiter,v.instruction_id ORDER BY v.id";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, whereId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    JsonObject row = new JsonObject();
                    row.addProperty("id", result.getInt("id"));
                    row.addProperty("type", result.getString("type"));
                    row.addProperty("name", result.getString("name"));
                    row.addProperty("value", result.getString("value"));
                    row.addProperty("localFormat", result.getString("local_format"));
                    row.addProperty("delimiter", result.getString("delimiter"));
                    row.addProperty("instructionId", result.getInt("instruction_id"));
                    row.addProperty("usedVars", result.getInt("used_vars"));
                    rows.add(row);
                }
            }
        } catch (SQLException exception) {
            JsonObject error = new JsonObject();
            error.addProperty("error", exception.getMessage());
            rows.add(error);
        }
        return rows;
    }

    private boolean isSpecialAction(String action) {
        if (action == null || action.isBlank()) return false;
        String base = action.split(":", 2)[0].trim().toUpperCase();
        return Set.of(
                        "SET", "GET", "CK", "Q", "QUIT", "E", "P", "SCREEN", "H", "HOLD", "GOTO",
                        "IF", "ELSEIF", "ELSE", "ENDIF", "PAUSE", "REFRESH", "LOOP", "REFRESH_LOOP",
                        "NEXT_ENTER", "SWIPE_UP", "SWIPE_DOWN", "EXCEL GOTO", "NEXT ROW", "CSV CHECK",
                        "PDF CHECK")
                .contains(base);
    }

    public JsonObject apply(JsonObject body) {
        JsonObject response = new JsonObject();
        String targetSession = string(body, "targetSessionId", "botJobTasks");
        String mode = string(body, "mode", "after");
        String action = string(body, "action", "").trim().toUpperCase();
        String name = string(body, "name", action).trim();
        String operation = string(body, "operation", "").trim();
        int hold = integer(body, "hold", defaultHold(action));

        if (action.isEmpty() || name.isEmpty()) return failure("Command and name are required.");
        if (integer(body, "blockId", -1) < 1) return failure("A valid block is required.");
        Integer parentId = nullableInteger(body, "parentId");
        Integer variableId = nullableInteger(body, "variableId");
        Integer parentBlockId = nullableInteger(body, "parentBlockId");
        if (requiresWebField(action) && parentId == null) return failure("Select a compatible Web Field.");
        if (requiresVariable(action) && variableId == null) return failure("Select a Variable for the Web Field.");
        if (requiresBlock(action) && parentBlockId == null) return failure("Select a destination Block.");
        if (parentId != null && !webFieldBelongsToBlock(targetSession, parentId, integer(body, "blockId", -1))) {
            return failure("The selected Web Field is outside the reference instruction block.");
        }

        SplitDTO split = new SplitDTO();
        split.setType("edit".equals(mode) ? "EDIT_OPERATION" : "before".equals(mode) ? "INSERT_BEFORE" : "INSERT_AFTER");
        split.setSessionId(targetSession);
        split.setHomeBankingId(integer(body, "homeBankingId", -1));
        split.setBotJobId(integer(body, "botJobId", -1));
        split.setBotJobName(string(body, "botJobName", ""));
        split.setBlockId(integer(body, "blockId", -1));
        split.setBlockName(string(body, "blockName", ""));
        split.setBlockOrderNumber(integer(body, "blockOrderNumber", 1));
        split.setInstructionId(integer(body, "instructionId", -1));
        split.setInstructionName(string(body, "instructionName", name));
        split.setInstructionOrderNumber(integer(body, "instructionOrderNumber", 1));
        split.setActions(action);
        split.setOperation(operation);
        split.setVariableId(variableId);
        split.setParentId(parentId);
        split.setParentBlockId(parentBlockId);

        ErrorMessage error = database.preFillNewInstruction(name, name, action, operation, hold, split, false);
        if (error != null) return failure(error.getErrorMessage());

        if ("componentTasks".equals(targetSession)) {
            error = database.loadComponentsComplete(split.getHomeBankingId(), split.getBotJobId(), split.getBotJobName());
        } else {
            error = engine.loadCompleteJobs(split.getBotJobId());
        }
        if (error != null) return failure(error.getErrorMessage());

        List<BotJobLoadDTO> jobs = "componentTasks".equals(targetSession)
                ? lists.getListBotJobComp()
                : lists.getListBotJob();
        List<InstructionLoad> instructions = lists.buildJsonViewData(jobs);
        String operationId = "componentTasks".equals(targetSession) ? "componentsUpdate" : "updateInstructions";
        sessions.sendMessageJson(split.getHomeBankingId(), targetSession, gson.toJson(instructions), operationId);

        response.addProperty("ok", true);
        response.addProperty("message", "Command saved.");
        response.add("instructions", gson.toJsonTree(instructions));
        if (body.has("requestId")) response.add("requestId", body.get("requestId"));
        return response;
    }

    private JsonArray commandCatalog() {
        JsonArray commands = new JsonArray();
        add(commands, "SET", "Set Value", "variable");
        add(commands, "GET", "Get Value", "variable");
        add(commands, "CK", "Check Value", "variable");
        add(commands, "PDF CHECK", "PDF Check", "variable");
        add(commands, "CSV CHECK", "CSV Check", "variable");
        add(commands, "E", "Extract Field", "variable");
        add(commands, "IF", "IF", "none");
        add(commands, "GOTO", "GOTO", "block");
        add(commands, "EXCEL GOTO", "Excel GOTO", "block");
        add(commands, "LOOP", "Loop", "number");
        add(commands, "REFRESH_LOOP", "Refresh Loop", "number");
        add(commands, "REFRESH", "Refresh", "none");
        add(commands, "NEXT_ENTER", "Next / Enter", "none");
        add(commands, "SWIPE_UP", "Swipe Up", "number");
        add(commands, "SWIPE_DOWN", "Swipe Down", "number");
        add(commands, "HOLD", "Wait", "number");
        add(commands, "PAUSE", "Pause", "none");
        add(commands, "QUIT", "Close Browser", "none");
        add(commands, "SCREEN", "Screenshot", "none");
        return commands;
    }

    private void add(JsonArray target, String code, String label, String targetType) {
        JsonObject command = new JsonObject();
        command.addProperty("code", code);
        command.addProperty("label", label);
        command.addProperty("target", targetType);
        target.add(command);
    }

    private JsonObject failure(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("error", message == null ? "Command operation failed." : message);
        return response;
    }

    private static int defaultHold(String action) {
        return "HOLD".equals(action) ? 5 : 1;
    }

    private static boolean requiresWebField(String action) {
        return Set.of("SET", "GET", "CK", "PDF CHECK", "CSV CHECK", "E", "LOOP", "REFRESH_LOOP")
                .contains(action);
    }

    private static boolean requiresVariable(String action) {
        return Set.of("SET", "GET", "CK", "PDF CHECK", "CSV CHECK", "E").contains(action);
    }

    private static boolean requiresBlock(String action) {
        return Set.of("GOTO", "EXCEL GOTO").contains(action);
    }

    private boolean webFieldBelongsToBlock(String sessionId, int instructionId, int blockId) {
        List<InstructionLoad> instructions = "componentTasks".equals(sessionId)
                ? lists.getListInstructionComp()
                : lists.getListInstruction();
        return instructions.stream().anyMatch(row -> row != null
                && row.getId() != null
                && row.getId() == instructionId
                && row.getBlockId() != null
                && row.getBlockId() == blockId
                && !isSpecialAction(row.getActions()));
    }

    private static String string(JsonObject body, String key, String fallback) {
        return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject body, String key, int fallback) {
        try {
            return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Integer nullableInteger(JsonObject body, String key) {
        return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsInt() : null;
    }
}
