package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CompletedRequestCacheTest {
    @Test
    void successfulDuplicateMutationExecutesOnlyOnce() {
        CompletedRequestCache cache = new CompletedRequestCache(4);
        AtomicInteger executions = new AtomicInteger();

        JsonObject first = cache.execute("activation-1", () -> success(executions.incrementAndGet()), true);
        JsonObject duplicate = cache.execute("activation-1", () -> success(executions.incrementAndGet()), true);

        assertEquals(1, executions.get());
        assertEquals(first, duplicate);
    }

    @Test
    void returnedResponsesAreDefensiveCopies() {
        CompletedRequestCache cache = new CompletedRequestCache(2);
        JsonObject first = cache.execute("request-1", () -> success(1), true);
        first.addProperty("sequence", 999);

        assertEquals(1, cache.get("request-1").get("sequence").getAsInt());
    }

    @Test
    void failedMutationCanBeRetried() {
        CompletedRequestCache cache = new CompletedRequestCache(2);
        AtomicInteger executions = new AtomicInteger();
        JsonObject failure = new JsonObject();
        failure.addProperty("ok", false);

        cache.execute("activation-2", () -> { executions.incrementAndGet(); return failure; }, true);
        cache.execute("activation-2", () -> { executions.incrementAndGet(); return failure; }, true);

        assertEquals(2, executions.get());
        assertNull(cache.get("activation-2"));
    }

    @Test
    void boundedCacheEvictsOldestCompletedRequest() {
        CompletedRequestCache cache = new CompletedRequestCache(2);
        cache.execute("one", () -> success(1), true);
        cache.execute("two", () -> success(2), true);
        cache.execute("three", () -> success(3), true);

        assertNull(cache.get("one"));
        assertEquals(2, cache.get("two").get("sequence").getAsInt());
        assertEquals(3, cache.get("three").get("sequence").getAsInt());
    }

    @Test
    void rejectsInvalidCapacityAndIgnoresBlankIds() {
        assertThrows(IllegalArgumentException.class, () -> new CompletedRequestCache(0));
        CompletedRequestCache cache = new CompletedRequestCache(1);
        cache.remember("", success(1));
        assertNull(cache.get(""));
        assertFalse(cache.execute("blank-result", JsonObject::new, true).has("ok"));
    }

    private JsonObject success(int sequence) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("sequence", sequence);
        return response;
    }
}
