package com.allinweb.ch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BotJobWorkspaceActionTest {

    @Test
    void parsesKnownActionsCaseInsensitively() {
        assertEquals(BotJobWorkspaceAction.REFRESH, BotJobWorkspaceAction.parse("refresh"));
        assertEquals(BotJobWorkspaceAction.SHOW_COMPONENTS, BotJobWorkspaceAction.parse("show_components"));
        assertEquals(BotJobWorkspaceAction.SHOW_PRE_SCAN, BotJobWorkspaceAction.parse("SHOW_PRE_SCAN"));
        assertEquals(
                BotJobWorkspaceAction.OPEN_ORGANIZATIONS,
                BotJobWorkspaceAction.parse("open_organizations"));
    }

    @Test
    void rejectsMissingAndUnknownActions() {
        assertThrows(IllegalArgumentException.class, () -> BotJobWorkspaceAction.parse(null));
        assertThrows(IllegalArgumentException.class, () -> BotJobWorkspaceAction.parse("OPEN_HIDDEN_SCANNER"));
    }
}
