package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SaveComponentServiceTest {
    private final SaveComponentService service = SaveComponentService.getInstance();

    @Test
    void bootstrapReturnsSourceSummaryAndDefaultDescription() {
        JsonObject body = validContext();
        Map<String, Object> response = service.bootstrap(body);
        assertTrue((Boolean) response.get("ok"));
        assertEquals("Payments", response.get("name"));
        assertEquals("Payments description", response.get("description"));
        assertEquals(1, response.get("instructionCount"));
    }

    @Test
    void rejectsMissingOwnershipAndEmptyInstructionSource() {
        assertFalse((Boolean) service.bootstrap(new JsonObject()).get("ok"));
        JsonObject body = validContext();
        body.add("instructions", new JsonArray());
        assertEquals("The source block has no instructions.", service.bootstrap(body).get("error"));
    }

    @Test
    void rejectsInvalidNameAndDescriptionBeforeDatabaseCalls() {
        JsonObject body = validContext();
        body.addProperty("name", ""); body.addProperty("description", "Description");
        assertEquals("Enter a valid component name.", service.save(body).get("error"));
        body.addProperty("name", "Component"); body.addProperty("description", "");
        assertEquals("Enter a valid component description.", service.save(body).get("error"));
    }

    private JsonObject validContext() {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "component-test");
        body.addProperty("sessionId", "botJobTasks");
        body.addProperty("homeBankingId", 2);
        body.addProperty("botJobId", 19);
        body.addProperty("botJobName", "Home Banking");
        body.addProperty("blockId", 10);
        body.addProperty("blockName", "Payments");
        JsonArray instructions = new JsonArray();
        JsonObject instruction = new JsonObject();
        instruction.addProperty("id", 100);
        instruction.addProperty("blockId", 10);
        instruction.addProperty("blockOrderNumber", 2);
        instruction.addProperty("instructionOrderNumber", 1);
        instructions.add(instruction);
        body.add("instructions", instructions);
        return body;
    }
}
