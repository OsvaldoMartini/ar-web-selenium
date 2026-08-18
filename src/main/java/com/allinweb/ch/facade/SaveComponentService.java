package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockDetailsDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pane-free Save Component workflow boundary. */
public final class SaveComponentService {
    private static final SaveComponentService INSTANCE = new SaveComponentService();
    private static final PerformDataBase database = PerformDataBase.getInstance();
    private static final PerformLists lists = PerformLists.getInstance();
    private static final Gson gson = new Gson();

    private SaveComponentService() {}

    public static SaveComponentService getInstance() {
        return INSTANCE;
    }

    public Map<String, Object> bootstrap(JsonObject body) {
        Map<String, Object> response = validateContext(body);
        if (Boolean.FALSE.equals(response.get("ok"))) return response;
        response.put("name", str(body, "blockName"));
        response.put("description", str(body, "blockDescription").isBlank()
                ? str(body, "blockName") + " description" : str(body, "blockDescription"));
        response.put("instructionCount", body.has("instructions") && body.get("instructions").isJsonArray()
                ? body.getAsJsonArray("instructions").size() : 0);
        response.put("message", "Save Component data loaded");
        return response;
    }

    public Map<String, Object> save(JsonObject body) {
        Map<String, Object> response = validateContext(body);
        if (Boolean.FALSE.equals(response.get("ok"))) return response;
        String name = str(body, "name").trim();
        String description = str(body, "description").trim();
        if (!validText(name, 100)) return failure("Enter a valid component name.");
        if (!validText(description, 500)) return failure("Enter a valid component description.");

        int homeBankingId = integer(body, "homeBankingId");
        int botJobId = integer(body, "botJobId");
        String botJobName = str(body, "botJobName");
        var saved = database.loadSavedBlocksForBotJob(homeBankingId, botJobId, botJobName);
        if (saved.stream().anyMatch(block -> block.getName().equalsIgnoreCase(name))) {
            return failure("A component with this name already exists.");
        }

        BlockDetailsDTO details = gson.fromJson(body, BlockDetailsDTO.class);
        details.setBlockName(name);
        details.setBlockDescription(description);
        details.setHomeBankingId(homeBankingId);
        details.setBotJobId(botJobId);
        details.setBotJobName(botJobName);
        details.setSessionId(str(body, "sessionId"));
        details.setBlockOrderNumber(lists.getListBlockComp().isEmpty() ? 1 : lists.getListBlockComp().size() + 1);

        ErrorMessage error = database.createCompBlock(details);
        if (error == null) error = database.createCompInstructions(details);
        if (error == null) error = database.createCompVariables(details);
        if (error == null) error = database.createUpdateCompInstruction(details);
        if (error == null) error = database.createCompReferences(details);
        if (error == null) error = database.loadBlocks(homeBankingId, "", "component_block");
        if (error == null) error = database.updateBlockOrderNumber("component_block", homeBankingId, true);
        if (error == null) error = database.loadComponentsComplete(homeBankingId, botJobId, botJobName);
        if (error != null) return failure("Component could not be saved.");

        List<BotJobLoadDTO> source = lists.getListBotJobComp();
        List<InstructionLoad> instructions = source.isEmpty() ? List.of() : lists.buildJsonViewData(source);
        response.put("componentName", name);
        response.put("instructions", instructions);
        response.put("message", "Component saved");
        return response;
    }

    private Map<String, Object> validateContext(JsonObject body) {
        if (integer(body, "homeBankingId") <= 0 || integer(body, "botJobId") <= 0 || integer(body, "blockId") <= 0) {
            return failure("Save Component context is invalid.");
        }
        if (!body.has("instructions") || !body.get("instructions").isJsonArray()
                || body.getAsJsonArray("instructions").size() == 0) {
            return failure("The source block has no instructions.");
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("requestId", str(body, "requestId"));
        response.put("blockId", integer(body, "blockId"));
        return response;
    }

    private boolean validText(String value, int max) {
        return !value.isBlank() && value.length() <= max && !value.matches(".*[\\p{Cntrl}].*");
    }

    private Map<String, Object> failure(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", false);
        response.put("error", message);
        return response;
    }

    private String str(JsonObject body, String key) {
        return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : "";
    }

    private int integer(JsonObject body, String key) {
        try { return body != null && body.has(key) ? body.get(key).getAsInt() : -1; }
        catch (Exception ignored) { return -1; }
    }
}
