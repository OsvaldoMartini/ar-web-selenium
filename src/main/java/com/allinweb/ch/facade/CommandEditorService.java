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
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Pane-free backend for the React instruction command panel. */
public final class CommandEditorService {

    private static final CommandEditorService INSTANCE = new CommandEditorService();
    private final PerformDataBase database = PerformDataBase.getInstance();
    private final PerformDBEngine engine = PerformDBEngine.getInstance();
    private final PerformLists lists = PerformLists.getInstance();
    private final WebSocketSessionManager sessions = WebSocketSessionManager.getInstance();
    private final Gson gson = new Gson();
    private final CommandOperationCodec operationCodec = new CommandOperationCodec();
    private final Map<String, JsonObject> completedRequests = new LinkedHashMap<>();

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
        String requestId = string(body, "requestId", "").trim();
        if (!requestId.isEmpty()) {
            synchronized (completedRequests) {
                JsonObject completed = completedRequests.get(requestId);
                if (completed != null) return completed.deepCopy();
            }
        }
        JsonObject response = new JsonObject();
        String targetSession = string(body, "targetSessionId", "botJobTasks");
        String mode = string(body, "mode", "after");
        String action = string(body, "action", "").trim().toUpperCase();
        String name = string(body, "name", action).trim();
        String operation;
        int hold = integer(body, "hold", defaultHold(action));

        if (action.isEmpty() || name.isEmpty()) return failure("Command and name are required.");
        if (integer(body, "blockId", -1) < 1) return failure("A valid block is required.");
        if ("edit".equals(mode) && !editableCommand(targetSession, integer(body, "instructionId", -1))) {
            return failure("Original Web Fields and conditional boundaries cannot be converted with Edit Command.");
        }
        if ("EXCEL GOTO".equals(action)
                && excelGotoExists(targetSession, integer(body, "botJobId", -1), integer(body, "homeBankingId", -1),
                        "edit".equals(mode) ? integer(body, "instructionId", -1) : -1)) {
            return failure("Only one EXCEL GOTO command is allowed in this job.");
        }
        Integer parentId = nullableInteger(body, "parentId");
        Integer variableId = nullableInteger(body, "variableId");
        Integer parentBlockId = nullableInteger(body, "parentBlockId");
        if (requiresWebField(action) && parentId == null) return failure("Select a compatible Web Field.");
        if (requiresVariable(action) && variableId == null) return failure("Select a Variable for the Web Field.");
        if (requiresBlock(action) && parentBlockId == null) return failure("Select a destination Block.");
        if (parentId != null && !webFieldBelongsToBlock(targetSession, parentId, integer(body, "blockId", -1))) {
            return failure("The selected Web Field is outside the reference instruction block.");
        }
        if (variableId != null && parentId != null && !variableBelongsToWebField(targetSession, variableId, parentId, integer(body, "botJobId", -1), integer(body, "homeBankingId", -1))) {
            return failure("The selected Variable does not belong to the selected Web Field.");
        }
        try {
            operation = operationCodec.encode(body, action);
        } catch (Exception exception) {
            return failure(exception.getMessage());
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
        if (!requestId.isEmpty()) rememberCompleted(requestId, response);
        return response;
    }

    public JsonObject insertElseIf(JsonObject body) {
        String targetSession = string(body, "targetSessionId", "botJobTasks");
        int whereId = "componentTasks".equals(targetSession)
                ? integer(body, "homeBankingId", -1)
                : integer(body, "botJobId", -1);
        int blockId = integer(body, "blockId", -1);
        int anchorId = integer(body, "instructionId", -1);
        String instructionTable = "componentTasks".equals(targetSession)
                ? "component_instruction"
                : "instruction";
        ErrorMessage error = database.loadInstructions(whereId, blockId, -1, instructionTable);
        if (error != null) return failure(error.getErrorMessage());

        List<InstructionLoad> blockRows = ("componentTasks".equals(targetSession)
                        ? lists.getListInstructionComp()
                        : lists.getListInstruction())
                .stream()
                .filter(row -> row != null && row.getBlockId() != null && row.getBlockId() == blockId)
                .sorted(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                        ? Integer.MAX_VALUE
                        : row.getInstructionOrderNumber()))
                .toList();

        Integer rootId = enclosingIfRoot(blockRows, anchorId);
        if (rootId == null) return failure("The selected instruction is not inside a valid IF family.");

        InstructionLoad boundary = blockRows.stream()
                .filter(row -> row.getParentId() != null && row.getParentId().equals(rootId))
                .filter(row -> "ELSE".equals(row.getActions()) || "ENDIF".equals(row.getActions()))
                .findFirst()
                .orElse(null);
        if (boundary == null) return failure("The IF family has no valid ELSE or ENDIF boundary.");

        SplitDTO split = new SplitDTO();
        split.setType("INSERT_BEFORE");
        split.setSessionId(targetSession);
        split.setHomeBankingId(integer(body, "homeBankingId", -1));
        split.setBotJobId(integer(body, "botJobId", -1));
        split.setBotJobName(string(body, "botJobName", ""));
        split.setBlockId(blockId);
        split.setBlockName(string(body, "blockName", ""));
        split.setBlockOrderNumber(integer(body, "blockOrderNumber", 1));
        split.setInstructionId(boundary.getId());
        split.setInstructionName("ELSEIF");
        split.setInstructionOrderNumber(boundary.getInstructionOrderNumber());
        split.setActions("ELSEIF");
        split.setOperation("ELSEIF");
        split.setParentId(rootId);

        error = database.preFillNewInstruction("ELSEIF", "ELSEIF", "ELSEIF", "ELSEIF", 1, split, false);
        if (error != null) return failure(error.getErrorMessage());
        return refreshAfterMutation(split, "ELSEIF inserted.", body);
    }

    private Integer enclosingIfRoot(List<InstructionLoad> rows, int anchorId) {
        Deque<Integer> roots = new ArrayDeque<>();
        for (InstructionLoad row : rows) {
            String action = row.getActions();
            if ("IF".equals(action) && row.getId() != null) roots.push(row.getId());
            if (row.getId() != null && row.getId() == anchorId) {
                if (Set.of("ELSE", "ENDIF").contains(action)) return null;
                return roots.peek();
            }
            if ("ENDIF".equals(action) && !roots.isEmpty()) roots.pop();
        }
        return null;
    }

    private JsonObject refreshAfterMutation(SplitDTO split, String message, JsonObject request) {
        ErrorMessage error;
        if ("componentTasks".equals(split.getSessionId())) {
            error = database.loadComponentsComplete(split.getHomeBankingId(), split.getBotJobId(), split.getBotJobName());
        } else {
            error = engine.loadCompleteJobs(split.getBotJobId());
        }
        if (error != null) return failure(error.getErrorMessage());
        List<BotJobLoadDTO> jobs = "componentTasks".equals(split.getSessionId())
                ? lists.getListBotJobComp()
                : lists.getListBotJob();
        List<InstructionLoad> instructions = lists.buildJsonViewData(jobs);
        String operationId = "componentTasks".equals(split.getSessionId()) ? "componentsUpdate" : "updateInstructions";
        sessions.sendMessageJson(split.getHomeBankingId(), split.getSessionId(), gson.toJson(instructions), operationId);
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("message", message);
        response.add("instructions", gson.toJsonTree(instructions));
        if (request.has("requestId")) response.add("requestId", request.get("requestId"));
        return response;
    }

    private void rememberCompleted(String requestId, JsonObject response) {
        synchronized (completedRequests) {
            completedRequests.put(requestId, response.deepCopy());
            while (completedRequests.size() > 256) {
                String oldest = completedRequests.keySet().iterator().next();
                completedRequests.remove(oldest);
            }
        }
    }

    private boolean editableCommand(String sessionId, int instructionId) {
        List<InstructionLoad> instructions = "componentTasks".equals(sessionId)
                ? lists.getListInstructionComp()
                : lists.getListInstruction();
        return instructions.stream().anyMatch(row -> row != null
                && row.getId() != null
                && row.getId() == instructionId
                && isSpecialAction(row.getActions())
                && !Set.of("IF", "ELSEIF", "ELSE", "ENDIF").contains(row.getActions()));
    }

    private boolean excelGotoExists(
            String sessionId, int botJobId, int homeBankingId, int excludedInstructionId) {
        boolean component = "componentTasks".equals(sessionId);
        String table = component ? "component_instruction" : "instruction";
        String owner = component ? "home_banking_id" : "bot_job_id";
        int ownerId = component ? homeBankingId : botJobId;
        String sql = "SELECT COUNT(*) FROM " + table
                + " WHERE " + owner + "=? AND actions='EXCEL GOTO' AND id<>?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, ownerId);
            statement.setInt(2, excludedInstructionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        } catch (SQLException exception) {
            return true;
        }
    }

    private JsonArray commandCatalog() {
        JsonArray commands = new JsonArray();
        add(commands, "SET", "Set Value", "variable", "webField", "variable");
        add(commands, "GET", "Get Value", "variable", "webField", "variable");
        add(commands, "CK", "Check Value", "variable", "webField", "variable", "operator");
        add(commands, "PDF CHECK", "PDF Check", "variable", "webField", "variable", "operator");
        add(commands, "CSV CHECK", "CSV Check", "variable", "webField", "variable", "operator");
        add(commands, "E", "Extract Field", "variable", "webField", "variable");
        add(commands, "IF", "IF", "none");
        add(commands, "GOTO", "GOTO", "block", "block", "count");
        add(commands, "EXCEL GOTO", "Excel GOTO", "block", "block");
        add(commands, "LOOP", "Loop", "number", "webField", "interval", "count");
        add(commands, "REFRESH_LOOP", "Refresh Loop", "number", "webField", "interval", "count");
        add(commands, "REFRESH", "Refresh", "none");
        add(commands, "NEXT_ENTER", "Next / Enter", "none");
        add(commands, "SWIPE_UP", "Swipe Up", "number", "count");
        add(commands, "SWIPE_DOWN", "Swipe Down", "number", "count");
        add(commands, "H", "Wait", "number", "hold");
        add(commands, "PAUSE", "Pause", "none");
        add(commands, "Q", "Close Browser", "none");
        add(commands, "P", "Screenshot", "none");
        return commands;
    }

    private void add(JsonArray target, String code, String label, String targetType, String... fields) {
        JsonObject command = new JsonObject();
        command.addProperty("code", code);
        command.addProperty("label", label);
        command.addProperty("target", targetType);
        JsonArray fieldDefinitions = new JsonArray();
        for (String field : fields) fieldDefinitions.add(field);
        command.add("fields", fieldDefinitions);
        target.add(command);
    }

    private JsonObject failure(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("error", message == null ? "Command operation failed." : message);
        return response;
    }

    private static int defaultHold(String action) {
        return "H".equals(action) ? 5 : 1;
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

    private boolean variableBelongsToWebField(
            String sessionId, int variableId, int webFieldId, int botJobId, int homeBankingId) {
        boolean component = "componentTasks".equals(sessionId);
        String table = component ? "component_variable" : "variable";
        String ownerColumn = component ? "home_banking_id" : "bot_job_id";
        int ownerId = component ? homeBankingId : botJobId;
        String sql = "SELECT COUNT(*) FROM " + table
                + " WHERE id=? AND instruction_id=? AND " + ownerColumn + "=?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, variableId);
            statement.setInt(2, webFieldId);
            statement.setInt(3, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        } catch (SQLException exception) {
            return false;
        }
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
