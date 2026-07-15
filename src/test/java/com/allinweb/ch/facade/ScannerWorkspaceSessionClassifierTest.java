package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import org.junit.jupiter.api.Test;

class ScannerWorkspaceSessionClassifierTest {

    private final ScannerWorkspaceSessionClassifier classifier = new ScannerWorkspaceSessionClassifier();

    @Test
    void instructionWorkspaceIncludesBotJobAndScannerSessionsByContainsMatch() {
        assertTrue(classifier.isInstructionWorkspaceSession(ScannerWorkspaceSessions.BOT_JOB_TASKS + ":42"));
        assertTrue(classifier.isInstructionWorkspaceSession(ScannerWorkspaceSessions.SCANNER_TOOL + ":42"));
        assertTrue(classifier.isInstructionWorkspaceSession(ScannerWorkspaceSessions.SCANNER_GRID + ":42"));
        assertTrue(classifier.isInstructionWorkspaceSession(ScannerWorkspaceSessions.MOBILE_SCANNER_GRID + ":42"));
        assertTrue(classifier.isInstructionWorkspaceSession(ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE + ":42"));
        assertFalse(classifier.isInstructionWorkspaceSession(ScannerWorkspaceSessions.COMPONENT_TASKS));
        assertFalse(classifier.isInstructionWorkspaceSession(null));
    }

    @Test
    void scannerSessionsUseExactMatchForDedicatedChecks() {
        assertTrue(classifier.isScannerGridSession(ScannerWorkspaceSessions.SCANNER_GRID));
        assertTrue(classifier.isScannerToolSession(ScannerWorkspaceSessions.SCANNER_TOOL));
        assertTrue(classifier.isScannerElementPaneSession(ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE));

        assertFalse(classifier.isScannerGridSession(ScannerWorkspaceSessions.SCANNER_GRID + ":42"));
        assertFalse(classifier.isScannerToolSession(ScannerWorkspaceSessions.SCANNER_TOOL + ":42"));
        assertFalse(classifier.isScannerElementPaneSession(ScannerWorkspaceSessions.SCANNER_ELEMENT_PANE + ":42"));
    }
}
