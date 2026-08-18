package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobDetailsRequest;
import com.allinweb.ch.model.BotJobDetailsResponse;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Bounded idempotency ledger for synchronous Bot Job Details mutations. */
public final class BotJobDetailsMutationLedger {

    private static final int DEFAULT_MAX_ENTRIES = 512;

    private final int maxEntries;
    private final LinkedHashMap<String, Entry> requests = new LinkedHashMap<>();

    public BotJobDetailsMutationLedger() {
        this(DEFAULT_MAX_ENTRIES);
    }

    BotJobDetailsMutationLedger(int maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    public synchronized BotJobDetailsResponse executeOnce(
            BotJobDetailsRequest request,
            String operation,
            String fingerprint,
            Supplier<BotJobDetailsResponse> mutation) {
        String key = request.sessionId() + ':' + operation + ':' + request.requestId();
        Entry existing = requests.get(key);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                return BotJobDetailsResponse.failure(
                        "Bot Job Details requestId was reused with different data",
                        "REQUEST_ID_REUSE",
                        request,
                        existing.response().state(),
                        Map.of());
            }
            return existing.response();
        }

        BotJobDetailsResponse response;
        try {
            response = mutation.get();
        } catch (RuntimeException error) {
            String message = error.getMessage();
            response = BotJobDetailsResponse.failure(
                    message == null || message.isBlank()
                            ? "Bot Job Details mutation failed"
                            : message,
                    "MUTATION_EXCEPTION",
                    request,
                    null,
                    Map.of());
        }
        if (response == null) throw new IllegalStateException("Bot Job Details mutation returned no response");
        requests.put(key, new Entry(fingerprint, response));
        trimEntries();
        return response;
    }

    private void trimEntries() {
        while (requests.size() > maxEntries) {
            Iterator<String> oldest = requests.keySet().iterator();
            if (!oldest.hasNext()) break;
            oldest.next();
            oldest.remove();
        }
    }

    private record Entry(String fingerprint, BotJobDetailsResponse response) {}
}
