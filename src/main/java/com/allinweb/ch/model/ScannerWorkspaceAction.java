package com.allinweb.ch.model;

import java.util.Locale;

public enum ScannerWorkspaceAction {
    REFRESH_STATE,
    CLEAR_GRID;

    public static ScannerWorkspaceAction parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Scanner action is required");
        }
        try {
            return ScannerWorkspaceAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Unsupported Scanner action: " + value, invalid);
        }
    }
}
