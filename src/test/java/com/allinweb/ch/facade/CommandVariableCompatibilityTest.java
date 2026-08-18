package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommandVariableCompatibilityTest {
    @Test
    void acceptsSupportedVariableTypesForVariableCommands() {
        assertTrue(CommandRegistry.supportsVariableType("SET", "$String"));
        assertTrue(CommandRegistry.supportsVariableType("GET", "#Numeric"));
        assertTrue(CommandRegistry.supportsVariableType("CSV CHECK", "$String"));
    }

    @Test
    void deniesVariablesForUnknownAndNonVariableCommands() {
        assertFalse(CommandRegistry.supportsVariableType("UNKNOWN", "$String"));
        assertFalse(CommandRegistry.supportsVariableType("PAUSE", "$String"));
        assertFalse(CommandRegistry.supportsVariableType("SET", "$Unsupported"));
        assertFalse(CommandRegistry.supportsVariableType("SET", null));
    }

    @Test
    void aliasesShareCanonicalRegistryRules() {
        assertTrue(CommandRegistry.isCommand("HOLD"));
        assertTrue(CommandRegistry.isCommand("SCREEN"));
        assertTrue(CommandRegistry.isCommand("QUIT"));
        assertEquals(CommandRegistry.isEditableCommand("H"), CommandRegistry.isEditableCommand("HOLD"));
        assertEquals(CommandRegistry.isEditableCommand("P"), CommandRegistry.isEditableCommand("SCREEN"));
        assertEquals(CommandRegistry.isEditableCommand("Q"), CommandRegistry.isEditableCommand("QUIT"));
    }
}
