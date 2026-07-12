package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.model.BotJobDetailsRequest;
import com.allinweb.ch.model.BotJobDetailsResponse;
import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BotJobDetailsMutationLedgerTest {

    @Test
    void executesIdenticalMetadataRequestOnlyOnceAndRejectsChangedReuse() {
        BotJobDetailsMutationLedger ledger = new BotJobDetailsMutationLedger(4);
        AtomicInteger mutations = new AtomicInteger();
        BotJobDetailsRequest request = request("save-1");

        BotJobDetailsResponse first = ledger.executeOnce(
                request, "metadata", "name=A", () -> response(request, mutations));
        BotJobDetailsResponse duplicate = ledger.executeOnce(
                request, "metadata", "name=A", () -> response(request, mutations));
        BotJobDetailsResponse conflict = ledger.executeOnce(
                request, "metadata", "name=B", () -> response(request, mutations));

        assertSame(first, duplicate);
        assertEquals(1, mutations.get());
        assertFalse(conflict.ok());
        assertEquals("REQUEST_ID_REUSE", conflict.errorCode());
    }

    @Test
    void correlatesAndReplaysUnexpectedMutationFailure() {
        BotJobDetailsMutationLedger ledger = new BotJobDetailsMutationLedger(4);
        AtomicInteger mutations = new AtomicInteger();
        BotJobDetailsRequest request = request("save-failed");

        BotJobDetailsResponse first = ledger.executeOnce(request, "metadata", "same", () -> {
            mutations.incrementAndGet();
            throw new IllegalStateException("database disconnected");
        });
        BotJobDetailsResponse duplicate = ledger.executeOnce(request, "metadata", "same", () -> {
            mutations.incrementAndGet();
            return response(request, mutations);
        });

        assertSame(first, duplicate);
        assertEquals(1, mutations.get());
        assertFalse(first.ok());
        assertEquals("MUTATION_EXCEPTION", first.errorCode());
        assertEquals("save-failed", first.requestId());
        assertEquals(42, first.botJobId());
    }

    private BotJobDetailsRequest request(String requestId) {
        return new BotJobDetailsRequest("botJobTasks", requestId, 42, new JsonObject());
    }

    private BotJobDetailsResponse response(BotJobDetailsRequest request, AtomicInteger mutations) {
        mutations.incrementAndGet();
        return BotJobDetailsResponse.success("saved", request, null);
    }
}
