package com.allinweb.ch.model;

import com.allinweb.ch.facade.execution.ExecutionPreflightReport;

/** Completion returned after a React-owned toolbar command runs in the desktop host. */
public record BotJobToolbarActionResult(
        boolean ok,
        String action,
        String message,
        String selectedPath,
        ExecutionPreflightReport executionPreflight) {

    public static BotJobToolbarActionResult success(BotJobToolbarAction action, String message) {
        return success(action, message, null);
    }

    public static BotJobToolbarActionResult success(
            BotJobToolbarAction action, String message, String selectedPath) {
        return new BotJobToolbarActionResult(true, action.name(), message, selectedPath, null);
    }

    public static BotJobToolbarActionResult failure(BotJobToolbarAction action, String message) {
        return new BotJobToolbarActionResult(
                false, action == null ? "" : action.name(), message, null, null);
    }

    /** Returns the same toolbar outcome enriched with its warning-only preflight observation. */
    public BotJobToolbarActionResult withExecutionPreflight(
            ExecutionPreflightReport executionPreflight) {
        return new BotJobToolbarActionResult(
                ok, action, message, selectedPath, executionPreflight);
    }
}
