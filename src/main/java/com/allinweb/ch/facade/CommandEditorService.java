package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

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

        ErrorMessage error = database.loadAllVariablesByCriteria(
                variableTable, whereId, instructionId, instructionName);
        if (error == null) error = database.loadBlocks(whereId, "", blockTable);

        response.addProperty("ok", error == null);
        response.add("variables", gson.toJsonTree(lists.getListVariablesUser()));
        response.add("blocks", gson.toJsonTree(
                "componentTasks".equals(sessionId) ? lists.getListBlockComp() : lists.getListBlock()));
        response.add("commands", commandCatalog());
        if (error != null) response.addProperty("error", error.getErrorMessage());
        return response;
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
        split.setVariableId(nullableInteger(body, "variableId"));
        split.setParentId(nullableInteger(body, "parentId"));
        split.setParentBlockId(nullableInteger(body, "parentBlockId"));

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
