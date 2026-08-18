package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceSessions;
import java.util.List;

public final class BotJobWorkspaceSessions {
    private static final List<String> STATE_TARGETS = List.of(
            ScannerWorkspaceSessions.BOT_JOB_TASKS,
            ScannerWorkspaceSessions.COMPONENT_TASKS,
            ScannerWorkspaceSessions.PRE_SCANNER_GRID);

    private BotJobWorkspaceSessions() {}

    public static List<String> stateTargets() {
        return STATE_TARGETS;
    }
}
