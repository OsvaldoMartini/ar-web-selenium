package com.allinweb.ch.model;

import java.util.Locale;

/** Commands owned by the React Bot Job Details workspace header. */
public enum BotJobWorkspaceAction {
    REFRESH,
    SHOW_BOT_JOB,
    SHOW_COMPONENTS,
    SHOW_VARIABLES,
    SHOW_EXCEL_DATA,
    SHOW_SMOKE_TEST,
    HIDE_COMPONENTS,
    SHOW_PRE_SCAN,
    OPEN_ORGANIZATIONS,
    CLOSE;

    public static BotJobWorkspaceAction parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Bot Job Details action is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalidAction) {
            throw new IllegalArgumentException("Unsupported Bot Job Details action: " + value, invalidAction);
        }
    }
}
