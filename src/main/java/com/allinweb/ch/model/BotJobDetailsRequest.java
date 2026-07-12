package com.allinweb.ch.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Transport-bound request context for Bot Job Details operations.
 *
 * <p>The WebSocket transport session is authoritative. A client may repeat that session ID in the
 * envelope, but it cannot route a request through a different registered session.
 */
public record BotJobDetailsRequest(String sessionId, String requestId, int botJobId, JsonObject body) {

    public BotJobDetailsRequest {
        sessionId = requireNonBlank(sessionId, "Bot Job Details transport session is required");
        requestId = requireNonBlank(requestId, "Bot Job Details requestId is required");
        if (botJobId <= 0) {
            throw new IllegalArgumentException("Bot Job Details botJobId must be positive");
        }
        if (body == null) {
            throw new IllegalArgumentException("Bot Job Details request body is required");
        }
        body = body.deepCopy();
    }

    /** Parses a client envelope while keeping the WebSocket transport session authoritative. */
    public static BotJobDetailsRequest parse(JsonObject envelope, String transportSessionId) {
        String boundSessionId =
                requireNonBlank(transportSessionId, "Bot Job Details transport session is required");
        if (envelope == null) {
            throw new IllegalArgumentException("Bot Job Details envelope is required");
        }

        String claimedSessionId = optionalString(envelope, "sessionId");
        if (claimedSessionId != null && !claimedSessionId.equals(boundSessionId)) {
            throw new IllegalArgumentException("Bot Job Details session does not match the transport session");
        }

        JsonObject parsedBody = parseBody(envelope);
        String requestId = requiredString(parsedBody, "requestId", "Bot Job Details requestId is required");
        int botJobId = requiredPositiveInteger(parsedBody, "botJobId");
        return new BotJobDetailsRequest(boundSessionId, requestId, botJobId, parsedBody);
    }

    /** Best-effort correlation for structured errors when full request validation fails. */
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

    /** Returns a defensive copy so callers cannot mutate the validated request context. */
    @Override
    public JsonObject body() {
        return body.deepCopy();
    }

    private static JsonObject parseBody(JsonObject envelope) {
        if (!envelope.has("body") || envelope.get("body").isJsonNull()) {
            throw new IllegalArgumentException("Bot Job Details request body is required");
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
                throw new IllegalArgumentException("Bot Job Details request body must be valid JSON", invalidJson);
            }
        }
        throw new IllegalArgumentException("Bot Job Details request body must be a JSON object");
    }

    private static String optionalString(JsonObject source, String field) {
        if (!source.has(field) || source.get(field).isJsonNull()) {
            return null;
        }
        JsonElement value = source.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Bot Job Details " + field + " must be a string");
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
            throw new IllegalArgumentException("Bot Job Details " + field + " must be positive");
        }
        try {
            int value = Integer.parseInt(source.get(field).getAsString());
            if (value > 0) {
                return value;
            }
        } catch (RuntimeException ignored) {
            // The shared validation message below is the public contract.
        }
        throw new IllegalArgumentException("Bot Job Details " + field + " must be positive");
    }

    private static String requireNonBlank(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }

    public record Correlation(String requestId, int botJobId) {}
}
