package com.allinweb.ch.facade;

import com.allinweb.ch.model.*;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.List;

/** JavaFX-free variable CRUD used by the React instruction command panel. */
public final class VariableEditorService {
    private static final VariableEditorService INSTANCE = new VariableEditorService();
    private final PerformDataBase database = PerformDataBase.getInstance();
    private final PerformLists lists = PerformLists.getInstance();
    private final Gson gson = new Gson();
    private final PerformDBEngine engine = PerformDBEngine.getInstance();
    private final WebSocketSessionManager sessions = WebSocketSessionManager.getInstance();

    private VariableEditorService() {}

    public static VariableEditorService getInstance() {
        return INSTANCE;
    }

    public JsonObject list(JsonObject body) {
        Context context = context(body);
        ErrorMessage error = database.loadAllVariablesByCriteria(
                context.variableTable, context.whereId, context.instructionId, context.instructionName);
        JsonObject response = new JsonObject();
        response.addProperty("ok", error == null);
        response.add("variables", gson.toJsonTree(lists.getListVariablesUser()));
        if (error != null) response.addProperty("error", error.getErrorMessage());
        return response;
    }

    public JsonObject save(JsonObject body) {
        Context context = context(body);
        JsonObject draft = body != null && body.has("variable") ? body.getAsJsonObject("variable") : new JsonObject();
        Integer id = nullableInteger(draft, "id");
        String name = string(draft, "name", "").trim();
        String type = string(draft, "type", "").trim();
        String value = string(draft, "value", "$EMPTY").trim();
        if (name.isEmpty()) return failure("Variable name is required.");
        if (!"$String".equals(type) && !"#Numeric".equals(type)) return failure("Select String or Numeric type.");

        database.loadAllVariablesByCriteria(
                context.variableTable, context.whereId, context.instructionId, context.instructionName);
        boolean duplicate = lists.getListVariablesUser().stream()
                .anyMatch(item -> item.getName() != null
                        && item.getName().equalsIgnoreCase(name)
                        && !Objects.equals(item.getId(), id));
        if (duplicate) return failure("A variable with this name already exists.");

        InstructionLoad parent = lists.getInstructionById(
                context.instructionTable, context.whereId, context.instructionId);
        VariableUserDTO variable = new VariableUserDTO(
                id == null ? -1 : id,
                type,
                name,
                value.isEmpty() ? "$EMPTY" : value,
                context.botJobId,
                parent == null ? context.instructionId : parent.getId(),
                parent == null ? context.instructionName : parent.getName(),
                string(draft, "localFormat", ""),
                string(draft, "delimiter", ""),
                string(draft, "usedVars", ""));

        ErrorMessage error;
        if (id == null || id < 0) {
            error = database.createVariable(variable);
        } else {
            error = database.updateUserData(context.variableTable, context.whereId, variable);
            if (error == null) error = rewriteDependentOperations(context, variable);
        }
        if (error != null) return failure(error.getErrorMessage());
        if (id != null && id >= 0) publishInstructionRefresh(context);
        JsonObject response = list(body);
        response.addProperty("message", id == null || id < 0 ? "Variable created." : "Variable updated.");
        return response;
    }

    private ErrorMessage rewriteDependentOperations(Context context, VariableUserDTO variable) {
        ErrorMessage error = database.loadAllParents(
                context.instructionTable, context.whereId, context.instructionId);
        if (error != null) return error;
        String typedName = ("#Numeric".equals(variable.getType()) ? "#" : "$") + variable.getName();
        String value = variable.getValue() == null || variable.getValue().isBlank() ? "$EMPTY" : variable.getValue();
        for (ParentOperations dependent : lists.getListParentOperations()) {
            String[] parts = dependent.getOperations() == null
                    ? new String[0]
                    : dependent.getOperations().split(":", -1);
            switch (dependent.getActions()) {
                case "SET" -> dependent.setOperations((parts.length > 0 ? parts[0] : variable.getParentName()) + ":" + value);
                case "GET" -> dependent.setOperations((parts.length > 0 ? parts[0] : variable.getParentName()) + ":" + typedName);
                case "CK", "PDF CHECK", "CSV CHECK" -> dependent.setOperations(
                        typedName + ":" + (parts.length > 1 ? parts[1] : "=") + ":" + value);
                case "E" -> dependent.setOperations(typedName);
                default -> { }
            }
        }
        error = database.rowsUpdateParentName(
                context.instructionTable, context.whereId, lists.getListParentOperations());
        if (error == null) {
            lists.updateMemoryParentOpenName(
                    context.instructionTable, context.whereId, lists.getListParentOperations());
        }
        return error;
    }

    private void publishInstructionRefresh(Context context) {
        ErrorMessage error;
        if ("componentTasks".equals(context.sessionId)) {
            error = database.loadComponentsComplete(
                    context.homeBankingId, context.botJobId, context.botJobName);
        } else {
            error = engine.loadCompleteJobs(context.botJobId);
        }
        if (error != null) return;
        List<BotJobLoadDTO> jobs = "componentTasks".equals(context.sessionId)
                ? lists.getListBotJobComp()
                : lists.getListBotJob();
        List<InstructionLoad> instructions = lists.buildJsonViewData(jobs);
        sessions.sendMessageJson(
                context.homeBankingId,
                context.sessionId,
                gson.toJson(instructions),
                "componentTasks".equals(context.sessionId) ? "componentsUpdate" : "updateInstructions");
    }

    public JsonObject delete(JsonObject body) {
        Context context = context(body);
        int id = integer(body, "variableId", -1);
        database.loadAllVariablesByCriteria(
                context.variableTable, context.whereId, context.instructionId, context.instructionName);
        VariableUserDTO variable = lists.getListVariablesUser().stream()
                .filter(item -> Objects.equals(item.getId(), id))
                .findFirst()
                .orElse(null);
        if (variable == null) return failure("Variable was not found.");
        int usages = integerValue(variable.getUsedVars());
        if (usages > 0) return failure("Variable is used by " + usages + " instruction(s) and cannot be deleted.");
        ErrorMessage error = database.deleteUserData(context.variableTable, context.whereId, id);
        if (error != null) return failure(error.getErrorMessage());
        JsonObject response = list(body);
        response.addProperty("message", "Variable deleted.");
        return response;
    }

    private Context context(JsonObject body) {
        String session = string(body, "targetSessionId", "botJobTasks");
        boolean component = "componentTasks".equals(session);
        int botJobId = integer(body, "botJobId", -1);
        return new Context(
                session,
                component ? "component_variable" : "variable",
                component ? "component_instruction" : "instruction",
                component ? integer(body, "homeBankingId", -1) : botJobId,
                botJobId,
                integer(body, "homeBankingId", -1),
                string(body, "botJobName", ""),
                integer(body, "instructionId", -1),
                string(body, "instructionName", ""));
    }

    private JsonObject failure(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("error", message == null ? "Variable operation failed." : message);
        return response;
    }

    private static int integerValue(String value) {
        try { return Integer.parseInt(value == null || value.isBlank() ? "0" : value); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static String string(JsonObject body, String key, String fallback) {
        return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject body, String key, int fallback) {
        try { return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsInt() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static Integer nullableInteger(JsonObject body, String key) {
        return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsInt() : null;
    }

    private record Context(
            String sessionId,
            String variableTable,
            String instructionTable,
            int whereId,
            int botJobId,
            int homeBankingId,
            String botJobName,
            int instructionId,
            String instructionName) {}
}
