package com.allinweb.ch.model;

/** Structured completion emitted after a React workspace action has run on the presentation executor. */
public record BotJobWorkspaceActionResult(
        boolean ok,
        String action,
        String message,
        String activeSurface,
        boolean componentsVisible) {

    public static BotJobWorkspaceActionResult success(
            BotJobWorkspaceAction action, String message, String activeSurface, boolean componentsVisible) {
        return new BotJobWorkspaceActionResult(true, action.name(), message, activeSurface, componentsVisible);
    }

    public static BotJobWorkspaceActionResult failure(BotJobWorkspaceAction action, String message) {
        return new BotJobWorkspaceActionResult(
                false, action == null ? "" : action.name(), message, "unknown", false);
    }
}
