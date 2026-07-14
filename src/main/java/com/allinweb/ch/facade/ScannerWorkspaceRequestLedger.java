package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerWorkspaceAction;
import com.allinweb.ch.model.ScannerWorkspaceRequest;
import com.allinweb.ch.model.ScannerWorkspaceResponse;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

/** Bounded idempotency ledger for typed Scanner workspace requests. */
public final class ScannerWorkspaceRequestLedger {
    private static final int DEFAULT_MAX_ENTRIES = 512;

    private final int maxEntries;
    private final LinkedHashMap<String, Entry> requests = new LinkedHashMap<>();
    private final ScannerWorkspaceActionParser actionParser = new ScannerWorkspaceActionParser();

    public ScannerWorkspaceRequestLedger() {
        this(DEFAULT_MAX_ENTRIES);
    }

    ScannerWorkspaceRequestLedger(int maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    public synchronized ScannerWorkspaceResponse executeOnce(
            ScannerWorkspaceRequest request,
            String operationId,
            Supplier<ScannerWorkspaceResponse> operation) {
        String key = request.sessionId() + ':' + operationId + ':' + request.requestId();
        String fingerprint = fingerprint(request, operationId);
        Entry existing = requests.get(key);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                return ScannerWorkspaceResponse.failure(
                        "Scanner requestId was reused with different data",
                        "REQUEST_ID_REUSE",
                        request,
                        actionOrNull(request));
            }
            return existing.response();
        }

        ScannerWorkspaceResponse response = operation.get();
        if (response == null) throw new IllegalStateException("Scanner operation returned no response");
        requests.put(key, new Entry(fingerprint, response));
        trimEntries();
        return response;
    }

    private String fingerprint(ScannerWorkspaceRequest request, String operationId) {
        return operationId + ':' + request.botJobId() + ':' + request.body();
    }

    private ScannerWorkspaceAction actionOrNull(ScannerWorkspaceRequest request) {
        try {
            return actionParser.parse(request);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void trimEntries() {
        while (requests.size() > maxEntries) {
            Iterator<String> oldest = requests.keySet().iterator();
            if (!oldest.hasNext()) break;
            oldest.next();
            oldest.remove();
        }
    }

    private record Entry(String fingerprint, ScannerWorkspaceResponse response) {}
}
