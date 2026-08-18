package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceAction;
import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.google.gson.JsonElement;

final class ScannerWorkspaceActionParser {

    ScannerWorkspaceAction parse(ScannerWorkspaceRequest request) {
        JsonElement action = request.body().get("action");
        if (action == null || action.isJsonNull()) {
            throw new IllegalArgumentException("Scanner action is required");
        }
        if (!action.isJsonPrimitive() || !action.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Scanner action must be a string");
        }
        return ScannerWorkspaceAction.parse(action.getAsString());
    }
}
