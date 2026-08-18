package com.allinweb.ch.model;

import java.util.Locale;

/** Native and execution commands exposed by the React Bot Job Details operations panel. */
public enum BotJobToolbarAction {
    OPEN_EXCEL,
    GENERATE_EXCEL,
    OPEN_REPORT,
    SET_NAVIGATION_TIME,
    LAUNCH,
    REFRESH_BLOCKS,
    PREFLIGHT,
    TEST_RUN,
    STOP_TEST_RUN,
    EXPORT_JOB,
    IMPORT_JOB,
    CHOOSE_TRANSFER_PATH,
    CREATE_BAT;

    public static BotJobToolbarAction parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Bot Job toolbar action is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalidAction) {
            throw new IllegalArgumentException("Unsupported Bot Job toolbar action: " + value, invalidAction);
        }
    }
}
