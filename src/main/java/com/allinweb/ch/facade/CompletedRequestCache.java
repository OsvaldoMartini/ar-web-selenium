package com.allinweb.ch.facade;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Bounded, defensive-copy idempotency cache for serialized JSON mutations. */
final class CompletedRequestCache {
    private final int capacity;
    private final Map<String, JsonObject> completed = new LinkedHashMap<>();

    CompletedRequestCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Cache capacity must be positive.");
        this.capacity = capacity;
    }

    synchronized JsonObject execute(String requestId, Supplier<JsonObject> mutation, boolean rememberSuccess) {
        JsonObject cached = get(requestId);
        if (cached != null) return cached;
        JsonObject response = mutation.get();
        if (rememberSuccess && successful(response)) remember(requestId, response);
        return response;
    }

    synchronized JsonObject get(String requestId) {
        if (requestId == null || requestId.isBlank()) return null;
        JsonObject response = completed.get(requestId);
        return response == null ? null : response.deepCopy();
    }

    synchronized void remember(String requestId, JsonObject response) {
        if (requestId == null || requestId.isBlank() || response == null) return;
        completed.put(requestId, response.deepCopy());
        while (completed.size() > capacity) completed.remove(completed.keySet().iterator().next());
    }

    private boolean successful(JsonObject response) {
        return response != null && response.has("ok") && response.get("ok").getAsBoolean();
    }
}
