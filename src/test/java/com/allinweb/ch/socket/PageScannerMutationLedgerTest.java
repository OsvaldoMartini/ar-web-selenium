package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PageScannerMutationLedgerTest {

    @Test
    void replaysIdenticalMutationWithoutExecutingItTwice() {
        PageScannerMutationLedger ledger = new PageScannerMutationLedger(4);
        AtomicInteger calls = new AtomicInteger();
        JsonObject body = body("request-1", 42);

        JsonObject first = ledger.executeOnce(
                "page-scanner-one", "request-1", "pageScanner.apply", body, () -> success(calls));
        JsonObject replay = ledger.executeOnce(
                "page-scanner-one", "request-1", "pageScanner.apply", body, () -> success(calls));

        assertEquals(1, calls.get());
        assertTrue(first.get("ok").getAsBoolean());
        assertTrue(replay.get("ok").getAsBoolean());
    }

    @Test
    void rejectsRequestIdReuseWithDifferentMutationData() {
        PageScannerMutationLedger ledger = new PageScannerMutationLedger(4);
        ledger.executeOnce(
                "page-scanner-one", "request-1", "pageScanner.apply", body("request-1", 42),
                () -> success(new AtomicInteger()));

        JsonObject rejected = ledger.executeOnce(
                "page-scanner-one", "request-1", "pageScanner.apply", body("request-1", 99),
                () -> success(new AtomicInteger()));

        assertFalse(rejected.get("ok").getAsBoolean());
        assertEquals("REQUEST_ID_REUSE", rejected.get("errorCode").getAsString());
    }

    @Test
    void clearsOnlyOneWorkspaceWithoutRetainingItsMutationBodies() {
        PageScannerMutationLedger ledger = new PageScannerMutationLedger(4);
        ledger.executeOnce(
                "page-scanner-one", "request-1", "pageScanner.apply", body("request-1", 42),
                () -> success(new AtomicInteger()));
        ledger.executeOnce(
                "page-scanner-two", "request-2", "pageScanner.apply", body("request-2", 43),
                () -> success(new AtomicInteger()));

        ledger.clearSession("page-scanner-one");

        assertEquals(1, ledger.size());
    }

    private static JsonObject body(String requestId, int blockId) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId);
        body.addProperty("blockId", blockId);
        return body;
    }

    private static JsonObject success(AtomicInteger calls) {
        calls.incrementAndGet();
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        return response;
    }
}
