package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import java.util.List;
import org.junit.jupiter.api.Test;

class BotJobWorkspaceSessionsTest {

    @Test
    void stateTargetsIncludeBotComponentAndPreScannerWorkspaces() {
        assertEquals(
                List.of(
                        ScannerWorkspaceSessions.BOT_JOB_TASKS,
                        ScannerWorkspaceSessions.COMPONENT_TASKS,
                        ScannerWorkspaceSessions.PRE_SCANNER_GRID),
                BotJobWorkspaceSessions.stateTargets());
    }

    @Test
    void stateTargetsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> BotJobWorkspaceSessions.stateTargets().add("other"));
    }
}
