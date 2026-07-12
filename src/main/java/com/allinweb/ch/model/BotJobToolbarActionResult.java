package com.allinweb.ch.model;

/** Completion returned after a React-owned toolbar command runs in the desktop host. */
public record BotJobToolbarActionResult(
        boolean ok,
        String action,
        String message,
        String selectedPath) {

    public static BotJobToolbarActionResult success(BotJobToolbarAction action, String message) {
        return success(action, message, null);
    }

    public static BotJobToolbarActionResult success(
            BotJobToolbarAction action, String message, String selectedPath) {
        return new BotJobToolbarActionResult(true, action.name(), message, selectedPath);
    }

    public static BotJobToolbarActionResult failure(BotJobToolbarAction action, String message) {
        return new BotJobToolbarActionResult(
                false, action == null ? "" : action.name(), message, null);
    }
}
