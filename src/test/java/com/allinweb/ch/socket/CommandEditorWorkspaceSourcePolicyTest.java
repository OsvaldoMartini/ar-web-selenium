package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import org.junit.jupiter.api.Test;

class CommandEditorWorkspaceSourcePolicyTest {

    @Test
    void acceptsBothAuthoritativeInstructionGrids() {
        assertTrue(CommandEditorWorkspaceService.isSupportedInstructionSource(
                ScannerWorkspaceSessions.BOT_JOB_TASKS));
        assertTrue(CommandEditorWorkspaceService.isSupportedInstructionSource(
                ScannerWorkspaceSessions.COMPONENT_TASKS));
    }

    @Test
    void rejectsDetachedAndUnrelatedSessions() {
        assertFalse(CommandEditorWorkspaceService.isSupportedInstructionSource(
                CommandEditorWorkspaceService.WORKSPACE_SESSION_ID));
        assertFalse(CommandEditorWorkspaceService.isSupportedInstructionSource("pageScanner"));
        assertFalse(CommandEditorWorkspaceService.isSupportedInstructionSource(null));
    }

    @Test
    void modernCommandMutationsRemainBotJobOnly() {
        assertTrue(CommandEditorWorkspaceService.isSupportedModernCommandMutationSource(
                ScannerWorkspaceSessions.BOT_JOB_TASKS));
        assertFalse(CommandEditorWorkspaceService.isSupportedModernCommandMutationSource(
                ScannerWorkspaceSessions.COMPONENT_TASKS));
        assertFalse(CommandEditorWorkspaceService.isSupportedModernCommandMutationSource(
                CommandEditorWorkspaceService.WORKSPACE_SESSION_ID));
    }
}
