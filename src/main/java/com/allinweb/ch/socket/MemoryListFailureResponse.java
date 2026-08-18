package com.allinweb.ch.socket;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Builds the failure-only Memory List response contract.
 *
 * <p>A failure does not establish an authoritative workspace, but it must carry enough of the
 * submitted request identity for the exact requester to settle its pending operation. Ambiguous
 * or malformed workspace assertions are deliberately omitted.
 */
final class MemoryListFailureResponse {

    private MemoryListFailureResponse() {}

    static JsonObject create(
            JsonObject request,
            String sessionId,
            int maximumCorrelationCharacters,
            String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("message", message);
        response.addProperty("sessionId", sessionId);
        copyBoundedText(request, response, "requestId", maximumCorrelationCharacters);
        copyBoundedText(request, response, "ownerEpoch", maximumCorrelationCharacters);

        Long workspaceEpoch = unambiguousWorkspaceEpoch(request);
        if (workspaceEpoch != null) {
            response.addProperty("workspaceEpoch", workspaceEpoch);
        }
        return response;
    }

    private static void copyBoundedText(
            JsonObject request,
            JsonObject response,
            String field,
            int maximumCharacters) {
        if (request == null || !request.has(field)) return;
        try {
            String value = request.get(field).getAsString();
            if (!value.isBlank() && value.length() <= maximumCharacters) {
                response.addProperty(field, value);
            }
        } catch (RuntimeException ignored) {
            // Invalid correlation data is never reflected into a response.
        }
    }

    private static Long unambiguousWorkspaceEpoch(JsonObject request) {
        PositiveLongAssertion direct = positiveLongAssertion(request, "workspaceEpoch");
        PositiveLongAssertion nested = positiveLongAssertion(object(request, "snapshot"), "workspaceEpoch");
        if ((direct.present && direct.value == null) || (nested.present && nested.value == null)) {
            return null;
        }
        if (direct.value != null && nested.value != null && !direct.value.equals(nested.value)) {
            return null;
        }
        return direct.value != null ? direct.value : nested.value;
    }

    private static PositiveLongAssertion positiveLongAssertion(JsonObject source, String field) {
        if (source == null || !source.has(field)) return new PositiveLongAssertion(false, null);
        try {
            long value = source.get(field).getAsLong();
            return new PositiveLongAssertion(true, value > 0 ? value : null);
        } catch (RuntimeException invalidValue) {
            return new PositiveLongAssertion(true, null);
        }
    }

    private static JsonObject object(JsonObject source, String field) {
        if (source == null || !source.has(field)) return null;
        JsonElement value = source.get(field);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private record PositiveLongAssertion(boolean present, Long value) {}
}
