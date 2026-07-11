package com.allinweb.ch.facade;

import com.allinweb.ch.model.*;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final Map<String, JsonObject> completedRequests = new LinkedHashMap<>();
    private final VariableOperationRewriteService operationRewriteService = new VariableOperationRewriteService();

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
        return serializedMutation(body, () -> saveOnce(body));
    }

    private JsonObject saveOnce(JsonObject body) {
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
            error = prepareDependentOperations(context, variable);
            if (error == null) {
                error = database.updateVariableAndOperationsAtomic(
                        context.variableTable,
                        context.instructionTable,
                        context.whereId,
                        variable,
                        lists.getListParentOperations());
            }
            if (error == null) {
                lists.updateMemoryParentOpenName(
                        context.instructionTable, context.whereId, lists.getListParentOperations());
            }
        }
        if (error != null) return failure(error.getErrorMessage());
        if (id != null && id >= 0) publishInstructionRefresh(context);
        JsonObject response = list(body);
        response.addProperty("message", id == null || id < 0 ? "Variable created." : "Variable updated.");
        return response;
    }

    private ErrorMessage prepareDependentOperations(Context context, VariableUserDTO variable) {
        ErrorMessage error = database.loadAllParents(
                context.instructionTable, context.whereId, context.instructionId);
        if (error != null) return error;
        operationRewriteService.rewrite(lists.getListParentOperations(), variable);
        return null;
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
        return serializedMutation(body, () -> deleteOnce(body));
    }

    private JsonObject deleteOnce(JsonObject body) {
        Context context = context(body);
        int id = integer(body, "variableId", -1);
        database.loadAllVariablesByCriteria(
                context.variableTable, context.whereId, context.instructionId, context.instructionName);
        VariableUserDTO variable = lists.getListVariablesUser().stream()
                .filter(item -> Objects.equals(item.getId(), id))
                .findFirst()
                .orElse(null);
        if (variable == null) return failure("Variable was not found.");
        int usages = currentUsageCount(context, id);
        if (usages < 0) return failure("Variable usage could not be verified, so deletion was refused.");
        if (usages > 0) {
            JsonObject response = failure("Variable is used by " + usages + " instruction(s) and cannot be deleted.");
            response.addProperty("usageCount", usages);
            response.addProperty("canDelete", false);
            return response;
        }
        ErrorMessage error = database.deleteUserData(context.variableTable, context.whereId, id);
        if (error != null) return failure(error.getErrorMessage());
        JsonObject response = list(body);
        response.addProperty("message", "Variable deleted.");
        return response;
    }

    private JsonObject serializedMutation(JsonObject body, java.util.function.Supplier<JsonObject> mutation) {
        String requestId = string(body, "requestId", "").trim();
        if (requestId.isEmpty()) return failure("Variable mutation request ID is required.");
        synchronized (completedRequests) {
            JsonObject completed = completedRequests.get(requestId);
            if (completed != null) return completed.deepCopy();
            JsonObject response = mutation.get();
            if (response.has("ok") && response.get("ok").getAsBoolean()) {
                completedRequests.put(requestId, response.deepCopy());
                while (completedRequests.size() > 256) {
                    completedRequests.remove(completedRequests.keySet().iterator().next());
                }
            }
            return response;
        }
    }

    private int currentUsageCount(Context context, int variableId) {
        String ownerColumn = "componentTasks".equals(context.sessionId) ? "home_banking_id" : "bot_job_id";
        String sql = "SELECT COUNT(*) FROM " + context.instructionTable
                + " WHERE variable_id=? AND " + ownerColumn + "=?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setInt(1, variableId);
            statement.setInt(2, context.whereId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : -1;
            }
        } catch (SQLException exception) {
            return -1;
        }
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
