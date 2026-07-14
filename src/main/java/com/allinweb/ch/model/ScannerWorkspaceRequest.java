package com.allinweb.ch.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public record ScannerWorkspaceRequest(String sessionId, String requestId, int botJobId, JsonObject body) {

    public ScannerWorkspaceRequest {
        sessionId = requireNonBlank(sessionId, "Scanner transport session is required");
        requestId = requireNonBlank(requestId, "Scanner requestId is required");
        if (botJobId <= 0) {
            throw new IllegalArgumentException("Scanner botJobId must be positive");
        }
        if (body == null) {
            throw new IllegalArgumentException("Scanner request body is required");
        }
        body = body.deepCopy();
    }

    public static ScannerWorkspaceRequest parse(JsonObject envelope, String transportSessionId) {
        String boundSessionId = requireNonBlank(transportSessionId, "Scanner transport session is required");
        if (envelope == null) {
            throw new IllegalArgumentException("Scanner envelope is required");
        }
        String claimedSessionId = optionalString(envelope, "sessionId");
        if (claimedSessionId != null && !claimedSessionId.equals(boundSessionId)) {
            throw new IllegalArgumentException("Scanner session does not match the transport session");
        }
        JsonObject parsedBody = parseBody(envelope);
        String requestId = requiredString(parsedBody, "requestId", "Scanner requestId is required");
        int botJobId = requiredPositiveInteger(parsedBody, "botJobId");
        return new ScannerWorkspaceRequest(boundSessionId, requestId, botJobId, parsedBody);
    }

    public static Correlation correlation(JsonObject envelope) {
        JsonObject parsedBody;
        try {
            parsedBody = parseBody(envelope);
        } catch (RuntimeException ignored) {
            return new Correlation("", -1);
        }
        String requestId;
        try {
            String parsedRequestId = optionalString(parsedBody, "requestId");
            requestId = parsedRequestId == null ? "" : parsedRequestId;
        } catch (RuntimeException ignored) {
            requestId = "";
        }
        int botJobId;
        try {
            botJobId = requiredPositiveInteger(parsedBody, "botJobId");
        } catch (RuntimeException ignored) {
            botJobId = -1;
        }
        return new Correlation(requestId, botJobId);
    }

    @Override
    public JsonObject body() {
        return body.deepCopy();
    }

    private static JsonObject parseBody(JsonObject envelope) {
        if (!envelope.has("body") || envelope.get("body").isJsonNull()) {
            throw new IllegalArgumentException("Scanner request body is required");
        }
        JsonElement bodyElement = envelope.get("body");
        if (bodyElement.isJsonObject()) {
            return bodyElement.getAsJsonObject().deepCopy();
        }
        if (bodyElement.isJsonPrimitive() && bodyElement.getAsJsonPrimitive().isString()) {
            try {
                JsonElement parsed = JsonParser.parseString(bodyElement.getAsString());
                if (parsed.isJsonObject()) {
                    return parsed.getAsJsonObject();
                }
            } catch (RuntimeException invalidJson) {
                throw new IllegalArgumentException("Scanner request body must be valid JSON", invalidJson);
            }
        }
        throw new IllegalArgumentException("Scanner request body must be a JSON object");
    }

    private static String optionalString(JsonObject source, String field) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            return null;
        }
        JsonElement value = source.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Scanner " + field + " must be a string");
        }
        String parsed = value.getAsString().trim();
        return parsed.isEmpty() ? null : parsed;
    }

    private static String requiredString(JsonObject source, String field, String errorMessage) {
        String value = optionalString(source, field);
        if (value == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }

    private static int requiredPositiveInteger(JsonObject source, String field) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Scanner " + field + " must be positive");
        }
        try {
            int value = Integer.parseInt(source.get(field).getAsString());
            if (value > 0) {
                return value;
            }
        } catch (RuntimeException ignored) {
            // Shared public validation message below.
        }
        throw new IllegalArgumentException("Scanner " + field + " must be positive");
    }

    private static String requireNonBlank(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }

    public record Correlation(String requestId, int botJobId) {}
}
