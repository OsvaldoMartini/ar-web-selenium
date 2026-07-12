package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobToolbarAction;
import com.allinweb.ch.model.BotJobToolbarActionResult;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Bounded idempotency ledger for parameterized Bot Job toolbar actions. */
public final class BotJobDetailsToolbarLedger {

    private static final int DEFAULT_MAX_ENTRIES = 512;

    private final int maxEntries;
    private final LinkedHashMap<String, Entry> requests = new LinkedHashMap<>();

    public BotJobDetailsToolbarLedger() {
        this(DEFAULT_MAX_ENTRIES);
    }

    BotJobDetailsToolbarLedger(int maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    public synchronized CompletableFuture<BotJobToolbarActionResult> executeOnce(
            String sessionId,
            String requestId,
            int botJobId,
            BotJobToolbarAction action,
            String payloadFingerprint,
            Supplier<CompletableFuture<BotJobToolbarActionResult>> operation) {
        String key = sessionId + ':' + requestId;
        String fingerprint = botJobId + ":" + action.name() + ":" + safe(payloadFingerprint);
        Entry existing = requests.get(key);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                return CompletableFuture.completedFuture(BotJobToolbarActionResult.failure(
                        action, "Bot Job Details requestId was reused with different toolbar parameters"));
            }
            return existing.future();
        }

        CompletableFuture<BotJobToolbarActionResult> created = operation.get();
        if (created == null) throw new IllegalStateException("Bot Job toolbar action returned no future");
        requests.put(key, new Entry(fingerprint, created));
        created.whenComplete((ignored, failure) -> trimCompletedEntriesSafely());
        trimCompletedEntries();
        return created;
    }

    private synchronized void trimCompletedEntriesSafely() {
        trimCompletedEntries();
    }

    private void trimCompletedEntries() {
        int completedEntries = 0;
        for (Entry entry : requests.values()) {
            if (entry.future().isDone()) completedEntries++;
        }
        if (completedEntries <= maxEntries) return;
        Iterator<Map.Entry<String, Entry>> iterator = requests.entrySet().iterator();
        while (completedEntries > maxEntries && iterator.hasNext()) {
            Map.Entry<String, Entry> entry = iterator.next();
            if (entry.getValue().future().isDone()) {
                iterator.remove();
                completedEntries--;
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record Entry(String fingerprint, CompletableFuture<BotJobToolbarActionResult> future) {}
}
