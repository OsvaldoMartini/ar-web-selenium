package com.allinweb.ch.model;

import java.util.Map;

public record ScannerWorkspaceResponse(
        boolean ok,
        String message,
        String requestId,
        int botJobId,
        String action,
        ScannerWorkspaceState state,
        String errorCode,
        Map<String, String> fieldErrors) {

    public ScannerWorkspaceResponse {
        fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public static ScannerWorkspaceResponse success(
            String message, ScannerWorkspaceRequest request, ScannerWorkspaceState state) {
        return new ScannerWorkspaceResponse(
                true, message, request.requestId(), request.botJobId(), null, state, null, Map.of());
    }

    public static ScannerWorkspaceResponse actionSuccess(
            ScannerWorkspaceAction action, String message, ScannerWorkspaceRequest request, ScannerWorkspaceState state) {
        return new ScannerWorkspaceResponse(
                true, message, request.requestId(), request.botJobId(), action.name(), state, null, Map.of());
    }

    public static ScannerWorkspaceResponse failure(
            String message, String errorCode, ScannerWorkspaceRequest request, ScannerWorkspaceAction action) {
        return new ScannerWorkspaceResponse(
                false,
                message,
                request == null ? "" : request.requestId(),
                request == null ? -1 : request.botJobId(),
                action == null ? null : action.name(),
                null,
                errorCode,
                Map.of());
    }
}
