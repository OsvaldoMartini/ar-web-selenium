package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobWorkspaceActionResult;
import com.allinweb.ch.model.BotJobWorkspaceAction;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Bounded idempotency ledger for workspace actions keyed by transport session and request ID. */
public final class BotJobDetailsActionLedger {

    private static final int DEFAULT_MAX_ENTRIES = 512;

    private final int maxEntries;
    private final LinkedHashMap<String, Entry> requests =
            new LinkedHashMap<>();

    public BotJobDetailsActionLedger() {
        this(DEFAULT_MAX_ENTRIES);
    }

    BotJobDetailsActionLedger(int maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    public synchronized CompletableFuture<BotJobWorkspaceActionResult> executeOnce(
            String sessionId,
            String requestId,
            int botJobId,
            BotJobWorkspaceAction action,
            Supplier<CompletableFuture<BotJobWorkspaceActionResult>> operation) {
        String key = sessionId + ':' + requestId;
        String fingerprint = botJobId + ":" + action.name();
        Entry existing = requests.get(key);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                return CompletableFuture.completedFuture(BotJobWorkspaceActionResult.failure(
                        action, "Bot Job Details requestId was reused for a different action"));
            }
            return existing.future();
        }

        CompletableFuture<BotJobWorkspaceActionResult> created = operation.get();
        if (created == null) throw new IllegalStateException("Bot Job Details action returned no future");
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
        Iterator<Map.Entry<String, Entry>> iterator =
                requests.entrySet().iterator();
        while (completedEntries > maxEntries && iterator.hasNext()) {
            Map.Entry<String, Entry> entry = iterator.next();
            if (entry.getValue().future().isDone()) {
                iterator.remove();
                completedEntries--;
            }
        }
    }

    private record Entry(String fingerprint, CompletableFuture<BotJobWorkspaceActionResult> future) {}
}
