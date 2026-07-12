package com.allinweb.ch.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Structured response for bootstrap, metadata, environment, and state operations. */
public record BotJobDetailsResponse(
        boolean ok,
        String message,
        String requestId,
        int botJobId,
        BotJobDetailsState state,
        String errorCode,
        Map<String, String> fieldErrors) {

    public BotJobDetailsResponse {
        fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public static BotJobDetailsResponse success(
            String message, BotJobDetailsRequest request, BotJobDetailsState state) {
        return new BotJobDetailsResponse(
                true, message, request.requestId(), request.botJobId(), state, null, Map.of());
    }

    public static BotJobDetailsResponse failure(
            String message,
            String errorCode,
            BotJobDetailsRequest request,
            BotJobDetailsState state,
            Map<String, String> fieldErrors) {
        return new BotJobDetailsResponse(
                false,
                message,
                request == null ? "" : request.requestId(),
                request == null ? -1 : request.botJobId(),
                state,
                errorCode,
                fieldErrors == null ? Map.of() : new LinkedHashMap<>(fieldErrors));
    }
}
