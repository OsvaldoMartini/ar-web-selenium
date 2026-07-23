package com.allinweb.ch.model;

import java.util.Set;

public final class DetachedWorkspaceSessions {
    public static final String NEW_BOT_JOB_MANAGER = "newBotJobManager";
    public static final String CLONE_JOB_MANAGER = "cloneJobManager";
    public static final String CONFIG_MANAGER = "configManager";
    public static final String A_TEMPLATE_MANAGER = "aTemplateManager";
    public static final String MEMORY_LIST_MANAGER = "memoryListManager";
    public static final String PAGES_OPEN_MANAGER = "pagesOpenManager";
    public static final String ABOUT_PANEL = "aboutPanel";
    public static final String LICENSE_MANAGER = "licenseManager";

    private static final Set<String> DETACHED_SESSIONS = Set.of(
            NEW_BOT_JOB_MANAGER,
            CLONE_JOB_MANAGER,
            CONFIG_MANAGER,
            A_TEMPLATE_MANAGER,
            MEMORY_LIST_MANAGER,
            PAGES_OPEN_MANAGER,
            ABOUT_PANEL,
            LICENSE_MANAGER);

    private DetachedWorkspaceSessions() {}

    public static boolean isDetachedWorkspaceSession(String sessionId) {
        return sessionId != null && DETACHED_SESSIONS.contains(sessionId);
    }
}
