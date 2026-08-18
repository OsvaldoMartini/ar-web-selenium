package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommandEditorServiceWorkspaceCapabilitiesTest {

    @Test
    void relationshipChipsAreAdvertisedForBotJobOnlyDuringP3() {
        assertTrue(CommandEditorService.newMemoryCapabilitiesResponse(false, true)
                .getAsJsonObject("workspaceCapabilities")
                .get("relationshipChipsV1")
                .getAsBoolean());
        assertFalse(CommandEditorService.newMemoryCapabilitiesResponse(true, true)
                .getAsJsonObject("workspaceCapabilities")
                .get("relationshipChipsV1")
                .getAsBoolean());
    }
}
