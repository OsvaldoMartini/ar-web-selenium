package com.allinweb.ch.model;

import java.util.Set;

public final class DetachedWorkspaceSessions {
    public static final String ORGANIZATION_MANAGER = "organizationManager";
    public static final String NEW_BOT_JOB_MANAGER = "newBotJobManager";
    public static final String CLONE_JOB_MANAGER = "cloneJobManager";
    public static final String CONFIG_MANAGER = "configManager";
    public static final String A_TEMPLATE_MANAGER = "aTemplateManager";
    public static final String COMPONENTS_MANAGER = ScannerWorkspaceSessions.COMPONENT_TASKS;
    public static final String COMMAND_EDITOR_MANAGER = "commandEditorManager";
    public static final String VARIABLES_MANAGER = "variablesManager";
    public static final String EXCEL_DATA_MANAGER = "excelDataManager";
    public static final String SMOKE_TEST_MANAGER = "smokeTestManager";
    public static final String RUNTIME_VARIABLES_MANAGER = "runtimeVariablesManager";
    public static final String MEMORY_LIST_MANAGER = "memoryListManager";
    public static final String PAGES_OPEN_MANAGER = "pagesOpenManager";
    public static final String ABOUT_PANEL = "aboutPanel";
    public static final String LICENSE_MANAGER = "licenseManager";

    private static final Set<String> DETACHED_SESSIONS = Set.of(
            ORGANIZATION_MANAGER,
            NEW_BOT_JOB_MANAGER,
            CLONE_JOB_MANAGER,
            CONFIG_MANAGER,
            A_TEMPLATE_MANAGER,
            COMPONENTS_MANAGER,
            COMMAND_EDITOR_MANAGER,
            VARIABLES_MANAGER,
            EXCEL_DATA_MANAGER,
            SMOKE_TEST_MANAGER,
            RUNTIME_VARIABLES_MANAGER,
            MEMORY_LIST_MANAGER,
            PAGES_OPEN_MANAGER,
            ABOUT_PANEL,
            LICENSE_MANAGER);

    private DetachedWorkspaceSessions() {}

    public static boolean isDetachedWorkspaceSession(String sessionId) {
        return sessionId != null && DETACHED_SESSIONS.contains(sessionId);
    }
}
