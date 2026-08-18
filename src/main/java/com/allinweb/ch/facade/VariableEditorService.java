package com.allinweb.ch.facade;

import com.allinweb.ch.model.*;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.socket.VariablesWorkspaceService;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Presentation-neutral variable CRUD used by the React instruction command panel. */
public final class VariableEditorService {
    private static final VariableEditorService INSTANCE = new VariableEditorService();
    private final PerformDataBase database = PerformDataBase.getInstance();
    private final PerformLists lists = PerformLists.getInstance();
    private final Gson gson = new Gson();
    private final PerformDBEngine engine = PerformDBEngine.getInstance();
    private final WebSocketSessionManager sessions = WebSocketSessionManager.getInstance();
    private final CompletedRequestCache completedRequests = new CompletedRequestCache(256);
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
        boolean creating = id == null || id < 0;
        String submittedName = string(draft, "name", "").trim();
        String type = string(draft, "type", "").trim();
        String value = string(draft, "value", "$EMPTY").trim();
        if (!creating && submittedName.isEmpty()) return failure("Variable name is required.");
        if (!"$String".equals(type) && !"#Numeric".equals(type)) return failure("Select String or Numeric type.");

        ErrorMessage loadError = database.loadAllVariablesByCriteria(
                context.variableTable, context.whereId, context.instructionId, context.instructionName);
        if (loadError != null) return failure(loadError.getErrorMessage());
        InstructionLoad parent = lists.getInstructionById(
                context.instructionTable, context.whereId, context.instructionId);
        String parentName = parent == null ? context.instructionName : parent.getName();
        String name = creating
                ? VariableDefinitionPolicy.generatedName(context.instructionId, parentName)
                : submittedName;
        VariableUserDTO storedVariable = creating
                ? null
                : lists.getListVariablesUser().stream()
                        .filter(item -> Objects.equals(item.getId(), id))
                        .findFirst()
                        .orElse(null);
        if (!creating && storedVariable == null) {
            return failure("The variable does not belong to the selected Web Field.");
        }
        boolean duplicate = lists.getListVariablesUser().stream()
                .anyMatch(item -> item.getName() != null
                        && item.getName().equalsIgnoreCase(name)
                        && !Objects.equals(item.getId(), id));
        if (duplicate) return failure("A variable with this name already exists.");

        VariableUserDTO variable = new VariableUserDTO(
                id == null ? -1 : id,
                type,
                name,
                value.isEmpty() ? "$EMPTY" : value,
                context.botJobId,
                parent == null ? context.instructionId : parent.getId(),
                parentName,
                string(draft, "localFormat", ""),
                string(draft, "delimiter", ""),
                string(draft, "usedVars", ""));

        ErrorMessage error;
        if (id == null || id < 0) {
            error = database.createVariable(
                    context.variableTable,
                    context.whereId,
                    variable);
        } else {
            List<ParentOperations> dependents;
            try {
                dependents = database.loadVariableDependents(
                        context.instructionTable,
                        context.whereId,
                        context.instructionId,
                        variable.getId());
            } catch (SQLException exception) {
                return failure("Variable commands could not be loaded: " + exception.getMessage());
            }
            operationRewriteService.rewrite(dependents, variable);
            error = database.updateVariableAndOperationsAtomic(
                    context.variableTable,
                    context.instructionTable,
                    context.whereId,
                    variable,
                    dependents);
            if (error == null) {
                lists.updateMemoryParentOpenName(
                        context.instructionTable, context.whereId, dependents);
            }
        }
        if (error != null) return failure(error.getErrorMessage());
        if (id != null && id >= 0) publishInstructionRefresh(context);
        if (!isComponentSession(context.sessionId)) {
            VariablesWorkspaceService.getInstance().notifyMutation(context.botJobId);
        }
        JsonObject response = list(body);
        response.addProperty("message", id == null || id < 0 ? "Variable created." : "Variable updated.");
        return response;
    }

    private boolean publishInstructionRefresh(Context context) {
        ErrorMessage error;
        if (isComponentSession(context.sessionId)) {
            error = database.loadComponentsComplete(
                    context.homeBankingId, context.botJobId, context.botJobName);
        } else {
            error = engine.loadCompleteJobs(context.botJobId);
        }
        if (error != null) return false;
        List<BotJobLoadDTO> jobs = isComponentSession(context.sessionId)
                ? lists.getListBotJobComp()
                : lists.getListBotJob();
        List<InstructionLoad> instructions = lists.buildJsonViewData(jobs);
        com.allinweb.ch.socket.InstructionRealtimePublisher.getInstance()
                .publishSnapshot(context.homeBankingId, context.sessionId, instructions);
        return true;
    }

    public JsonObject delete(JsonObject body) {
        return serializedMutation(body, () -> deleteOnce(body));
    }

    private JsonObject deleteOnce(JsonObject body) {
        Context context = context(body);
        int id = integer(body, "variableId", -1);
        ErrorMessage loadError = database.loadAllVariablesByCriteria(
                context.variableTable, context.whereId, context.instructionId, context.instructionName);
        if (loadError != null) return failure(loadError.getErrorMessage());
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
        if (!isComponentSession(context.sessionId)) {
            VariablesWorkspaceService.getInstance().notifyMutation(context.botJobId);
        }
        JsonObject response = list(body);
        response.addProperty("message", "Variable deleted.");
        return response;
    }

    private JsonObject serializedMutation(JsonObject body, java.util.function.Supplier<JsonObject> mutation) {
        String requestId = string(body, "requestId", "").trim();
        if (requestId.isEmpty()) return failure("Variable mutation request ID is required.");
        return completedRequests.execute(requestId, mutation, true);
    }

    private int currentUsageCount(Context context, int variableId) {
        boolean component = isComponentSession(context.sessionId);
        String sql = component
                ? "SELECT COUNT(*) FROM component_instruction"
                        + " WHERE variable_id=? AND home_banking_id=?"
                : "SELECT COUNT(*) FROM instruction_variable_slot"
                        + " WHERE variable_id=? AND bot_job_id=?";
        try (Connection connection = database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
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
        String session = string(body, "targetSessionId", ScannerWorkspaceSessions.BOT_JOB_TASKS);
        boolean component = isComponentSession(session);
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

    private static boolean isComponentSession(String sessionId) {
        return ScannerWorkspaceSessions.COMPONENT_TASKS.equals(sessionId);
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
