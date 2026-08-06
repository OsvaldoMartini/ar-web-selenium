package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DetachedCommandEditorOperationPolicyTest {

    @Test
    void allowsOnlyTheTwoModernCommandMutationOperations() {
        assertTrue(SimpleWebSocketServer.isAllowedFromDetachedCommandEditorTransport(
                "variablesWorkspace.commandEditor.update"));
        assertTrue(SimpleWebSocketServer.isAllowedFromDetachedCommandEditorTransport(
                "variablesWorkspace.commandEditor.copy"));

        assertFalse(SimpleWebSocketServer.isAllowedFromDetachedCommandEditorTransport(
                "variablesWorkspace.commandEditor.create"));
        assertFalse(SimpleWebSocketServer.isAllowedFromDetachedCommandEditorTransport(
                "variablesWorkspace.commands.delete"));
    }
}
