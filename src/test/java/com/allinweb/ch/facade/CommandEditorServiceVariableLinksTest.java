package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.VariableLoadDTO;
import com.google.gson.JsonArray;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandEditorServiceVariableLinksTest {

    @Test
    void variableLinksExposeRawVariableOwnersAndTypesIncludingNullOwners() {
        JsonArray links = CommandEditorService.variableLinks(List.of(
                variable(101, 11, "$String"),
                variable(102, null, "#Numeric")));

        assertEquals(2, links.size());
        assertEquals(101, links.get(0).getAsJsonObject().get("id").getAsInt());
        assertEquals(
                11,
                links.get(0).getAsJsonObject().get("instructionId").getAsInt());
        assertEquals(
                "$String",
                links.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals(102, links.get(1).getAsJsonObject().get("id").getAsInt());
        assertTrue(
                links.get(1).getAsJsonObject().get("instructionId").isJsonNull());
        assertEquals(
                "#Numeric",
                links.get(1).getAsJsonObject().get("type").getAsString());
    }

    @Test
    void variableLinksAlwaysReturnAnArray() {
        assertEquals(0, CommandEditorService.variableLinks(null).size());
        assertEquals(0, CommandEditorService.variableLinks(List.of()).size());
    }

    private VariableLoadDTO variable(int id, Integer instructionId, String type) {
        return new VariableLoadDTO(
                id,
                2,
                5,
                instructionId,
                type,
                null,
                null,
                null,
                null,
                0);
    }
}
