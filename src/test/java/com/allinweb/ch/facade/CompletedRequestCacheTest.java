package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CompletedRequestCacheTest {
    @Test
    void successfulDuplicateExecutesOnceAndReturnsDefensiveCopy() {
        CompletedRequestCache cache = new CompletedRequestCache(2);
        AtomicInteger executions = new AtomicInteger();
        JsonObject first = cache.execute("request-1", () -> response(true, executions.incrementAndGet()), true);
        first.addProperty("value", 99);

        JsonObject duplicate = cache.execute("request-1", () -> response(true, executions.incrementAndGet()), true);

        assertEquals(1, executions.get());
        assertEquals(1, duplicate.get("value").getAsInt());
    }

    @Test
    void failedRequestRemainsRetryable() {
        CompletedRequestCache cache = new CompletedRequestCache(2);
        AtomicInteger executions = new AtomicInteger();

        cache.execute("request-1", () -> response(false, executions.incrementAndGet()), true);
        cache.execute("request-1", () -> response(true, executions.incrementAndGet()), true);

        assertEquals(2, executions.get());
        assertEquals(2, cache.get("request-1").get("value").getAsInt());
    }

    @Test
    void evictsOldestCompletedRequestAtCapacity() {
        CompletedRequestCache cache = new CompletedRequestCache(2);
        cache.remember("one", response(true, 1));
        cache.remember("two", response(true, 2));
        cache.remember("three", response(true, 3));

        assertNull(cache.get("one"));
        assertEquals(2, cache.get("two").get("value").getAsInt());
        assertEquals(3, cache.get("three").get("value").getAsInt());
    }

    private JsonObject response(boolean ok, int value) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", ok);
        response.addProperty("value", value);
        return response;
    }
}
