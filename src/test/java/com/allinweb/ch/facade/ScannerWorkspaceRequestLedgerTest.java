package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.allinweb.ch.model.ScannerWorkspaceResponse;
import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ScannerWorkspaceRequestLedgerTest {

    @Test
    void duplicateRequestReturnsCachedResponseAndExecutesOnlyOnce() {
        ScannerWorkspaceRequestLedger ledger = new ScannerWorkspaceRequestLedger(4);
        AtomicInteger executions = new AtomicInteger();
        ScannerWorkspaceRequest request = request("scannerGrid", "same-id", 42, "REFRESH_STATE");

        ScannerWorkspaceResponse first = ledger.executeOnce(
                request, "scanner.actionResponse", () -> success(request, executions));
        ScannerWorkspaceResponse duplicate = ledger.executeOnce(
                request, "scanner.actionResponse", () -> success(request, executions));

        assertSame(first, duplicate);
        assertEquals(1, executions.get());
        assertTrue(duplicate.ok());
    }

    @Test
    void rejectsSameRequestIdWithDifferentBody() {
        ScannerWorkspaceRequestLedger ledger = new ScannerWorkspaceRequestLedger(4);
        AtomicInteger executions = new AtomicInteger();
        ScannerWorkspaceRequest first = request("scannerGrid", "same-id", 42, "REFRESH_STATE");
        ScannerWorkspaceRequest conflicting = request("scannerGrid", "same-id", 42, "CLEAR_GRID");
        ledger.executeOnce(first, "scanner.actionResponse", () -> success(first, executions));

        ScannerWorkspaceResponse response = ledger.executeOnce(
                conflicting, "scanner.actionResponse", () -> success(conflicting, executions));

        assertEquals(1, executions.get());
        assertFalse(response.ok());
        assertEquals("REQUEST_ID_REUSE", response.errorCode());
        assertEquals("CLEAR_GRID", response.action());
    }

    @Test
    void conflictingRequestWithMalformedActionDoesNotReportAction() {
        ScannerWorkspaceRequestLedger ledger = new ScannerWorkspaceRequestLedger(4);
        AtomicInteger executions = new AtomicInteger();
        ScannerWorkspaceRequest first = request("scannerGrid", "same-id", 42, "REFRESH_STATE");
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "same-id");
        body.addProperty("botJobId", 42);
        body.add("action", new JsonObject());
        ScannerWorkspaceRequest conflicting = new ScannerWorkspaceRequest("scannerGrid", "same-id", 42, body);
        ledger.executeOnce(first, "scanner.actionResponse", () -> success(first, executions));

        ScannerWorkspaceResponse response = ledger.executeOnce(
                conflicting, "scanner.actionResponse", () -> success(conflicting, executions));

        assertEquals(1, executions.get());
        assertFalse(response.ok());
        assertEquals("REQUEST_ID_REUSE", response.errorCode());
        assertEquals(null, response.action());
    }

    @Test
    void scopesSameRequestIdBySessionAndOperation() {
        ScannerWorkspaceRequestLedger ledger = new ScannerWorkspaceRequestLedger(4);
        AtomicInteger executions = new AtomicInteger();
        ScannerWorkspaceRequest scanner = request("scannerGrid", "shared-id", 42, "REFRESH_STATE");
        ScannerWorkspaceRequest preScanner = request("preScannerGrid", "shared-id", 42, "REFRESH_STATE");

        ledger.executeOnce(scanner, "scanner.actionResponse", () -> success(scanner, executions));
        ledger.executeOnce(preScanner, "scanner.actionResponse", () -> success(preScanner, executions));
        ledger.executeOnce(scanner, "scanner.bootstrapResponse", () -> success(scanner, executions));

        assertEquals(3, executions.get());
    }

    @Test
    void evictsOldestEntryWhenCapacityIsExceeded() {
        ScannerWorkspaceRequestLedger ledger = new ScannerWorkspaceRequestLedger(1);
        AtomicInteger executions = new AtomicInteger();
        ScannerWorkspaceRequest first = request("scannerGrid", "first", 42, "REFRESH_STATE");
        ScannerWorkspaceRequest second = request("scannerGrid", "second", 42, "REFRESH_STATE");

        ledger.executeOnce(first, "scanner.actionResponse", () -> success(first, executions));
        ledger.executeOnce(second, "scanner.actionResponse", () -> success(second, executions));
        ledger.executeOnce(first, "scanner.actionResponse", () -> success(first, executions));

        assertEquals(3, executions.get());
    }

    private ScannerWorkspaceResponse success(ScannerWorkspaceRequest request, AtomicInteger executions) {
        executions.incrementAndGet();
        return ScannerWorkspaceResponse.success("ok", request, null);
    }

    private ScannerWorkspaceRequest request(String sessionId, String requestId, int botJobId, String action) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId);
        body.addProperty("botJobId", botJobId);
        body.addProperty("action", action);
        return new ScannerWorkspaceRequest(sessionId, requestId, botJobId, body);
    }
}
