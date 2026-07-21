package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class CommandEditorCanonicalOperationBodyTest {
    @Test
    void removesStaleSelectionsForEveryRegisteredCommand() {
        for (JsonElement element : CommandRegistry.catalog()) {
            JsonObject definition = element.getAsJsonObject();
            String action = definition.get("code").getAsString();
            boolean needsWebField = CommandRegistry.requires(action, "webField");
            boolean needsVariable = CommandRegistry.requires(action, "variable");
            boolean needsBlock = CommandRegistry.requires(action, "block");
            JsonObject raw = new JsonObject();
            raw.addProperty("parentId", 101);
            raw.addProperty("variableId", 202);
            raw.addProperty("parentBlockId", 303);

            JsonObject canonical = CommandEditorService.canonicalOperationBody(
                    raw,
                    needsWebField ? 101 : null,
                    needsVariable ? 202 : null,
                    needsBlock ? 303 : null);

            assertEquals(needsWebField, canonical.has("parentId"), action + " parentId");
            assertEquals(needsVariable, canonical.has("variableId"), action + " variableId");
            assertEquals(needsBlock, canonical.has("parentBlockId"), action + " parentBlockId");
        }
    }

    @Test
    void loopKeepsOnlyItsWebFieldRelationship() {
        JsonObject raw = new JsonObject();
        raw.addProperty("parentId", 101);
        raw.addProperty("variableId", 0);
        raw.addProperty("parentBlockId", 0);
        raw.addProperty("interval", 5);
        raw.addProperty("count", 100);

        JsonObject canonical = CommandEditorService.canonicalOperationBody(raw, 101, null, null);

        assertEquals(101, canonical.get("parentId").getAsInt());
        assertEquals(false, canonical.has("variableId"));
        assertEquals(false, canonical.has("parentBlockId"));
        assertEquals(5, canonical.get("interval").getAsInt());
        assertEquals(100, canonical.get("count").getAsInt());
    }

    @Test
    void correlatesRejectedCommandWithTheOriginalRequest() {
        JsonObject request = new JsonObject();
        request.addProperty("requestId", "loop-request-17");
        JsonObject rejected = new JsonObject();
        rejected.addProperty("ok", false);
        rejected.addProperty("error", "Select a compatible Web Field.");

        JsonObject correlated = CommandEditorService.correlateResponse(request, rejected);

        assertEquals("loop-request-17", correlated.get("requestId").getAsString());
        assertFalse(correlated.get("ok").getAsBoolean());
        assertEquals("Select a compatible Web Field.", correlated.get("error").getAsString());
    }
}
