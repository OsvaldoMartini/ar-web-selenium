package com.allinweb.ch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BotJobToolbarActionTest {

    @Test
    void parsesEverySupportedActionCaseInsensitively() {
        for (BotJobToolbarAction action : BotJobToolbarAction.values()) {
            assertEquals(action, BotJobToolbarAction.parse(action.name()));
            assertEquals(action, BotJobToolbarAction.parse("  " + action.name().toLowerCase() + "  "));
        }
    }

    @Test
    void rejectsMissingAndUnsupportedActionsWithPublicMessages() {
        IllegalArgumentException missing =
                assertThrows(IllegalArgumentException.class, () -> BotJobToolbarAction.parse(null));
        IllegalArgumentException blank =
                assertThrows(IllegalArgumentException.class, () -> BotJobToolbarAction.parse("  "));
        IllegalArgumentException unsupported =
                assertThrows(IllegalArgumentException.class, () -> BotJobToolbarAction.parse("DELETE_JOB"));

        assertEquals("Bot Job toolbar action is required", missing.getMessage());
        assertEquals("Bot Job toolbar action is required", blank.getMessage());
        assertTrue(unsupported.getMessage().contains("Unsupported Bot Job toolbar action: DELETE_JOB"));
    }
}
