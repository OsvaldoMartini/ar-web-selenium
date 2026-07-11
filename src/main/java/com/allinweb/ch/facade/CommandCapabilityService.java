package com.allinweb.ch.facade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Adds context-sensitive create/edit availability to canonical command definitions. */
public final class CommandCapabilityService {

    public JsonArray catalog(String selectedAction, boolean excelGotoConflict) {
        JsonArray capabilities = new JsonArray();
        boolean selectedEditable = CommandRegistry.isEditableCommand(selectedAction);
        for (JsonElement element : CommandRegistry.catalog()) {
            JsonObject command = element.getAsJsonObject();
            String code = command.get("code").getAsString();
            boolean familyRoot = "IF".equals(code);
            boolean excelConflict = "EXCEL GOTO".equals(code) && excelGotoConflict;
            command.addProperty("insertAllowed", !excelConflict);
            command.addProperty("editAllowed", selectedEditable && !familyRoot && !excelConflict);
            if (excelConflict) {
                command.addProperty("disabledReason", "Only one EXCEL GOTO command is allowed in this job.");
            }
            capabilities.add(command);
        }
        return capabilities;
    }
}
