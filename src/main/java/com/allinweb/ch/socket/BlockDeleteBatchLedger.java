package com.allinweb.ch.socket;

import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Bounded, fingerprinted idempotency ledger for atomic block batch deletions. */
final class BlockDeleteBatchLedger {

    private static final int DEFAULT_MAX_ENTRIES = 512;
    private static final BlockDeleteBatchLedger INSTANCE =
            new BlockDeleteBatchLedger(DEFAULT_MAX_ENTRIES);

    private final int maxEntries;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    BlockDeleteBatchLedger() {
        this(DEFAULT_MAX_ENTRIES);
    }

    BlockDeleteBatchLedger(int maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    static BlockDeleteBatchLedger getInstance() {
        return INSTANCE;
    }

    synchronized Outcome executeOnce(
            String scope,
            String requestId,
            JsonObject payload,
            Supplier<Outcome> mutation) {
        String scopedOwner = requireNonBlank(scope, "Block delete scope is required");
        String request = requireNonBlank(requestId, "Block delete requestId is required");
        Objects.requireNonNull(payload, "Block delete payload is required");
        Objects.requireNonNull(mutation, "Block delete mutation is required");

        String key = scopedOwner + ':' + request;
        String fingerprint = fingerprint(payload);
        Entry existing = entries.get(key);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                return Outcome.failure(new ErrorMessage(
                        "Delete Blocks Refused",
                        "Request ID was reused",
                        "The DELETE_BLOCKS requestId was reused with different data."));
            }
            return existing.outcome().asReplayed();
        }

        Outcome outcome;
        try {
            outcome = mutation.get();
        } catch (RuntimeException error) {
            String detail = error.getMessage();
            outcome = Outcome.failure(new ErrorMessage(
                    "Delete Blocks Error",
                    "Atomic block deletion failed",
                    detail == null || detail.isBlank() ? error.getClass().getSimpleName() : detail));
        }
        if (outcome == null) throw new IllegalStateException("Block delete mutation returned no outcome");
        entries.put(key, new Entry(fingerprint, outcome));
        trim();
        return outcome;
    }

    private void trim() {
        while (entries.size() > maxEntries) {
            Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
            if (!iterator.hasNext()) return;
            iterator.next();
            iterator.remove();
        }
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static String fingerprint(JsonObject payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    record Outcome(
            ErrorMessage error,
            List<Integer> deletedBlockIds,
            Integer retainedBlockId,
            boolean replayed) {
        Outcome {
            deletedBlockIds = deletedBlockIds == null ? List.of() : List.copyOf(deletedBlockIds);
        }

        static Outcome success(List<Integer> deletedBlockIds, Integer retainedBlockId) {
            return new Outcome(null, deletedBlockIds, retainedBlockId, false);
        }

        static Outcome failure(ErrorMessage error) {
            return new Outcome(Objects.requireNonNull(error), List.of(), null, false);
        }

        Outcome asReplayed() {
            return new Outcome(error, deletedBlockIds, retainedBlockId, true);
        }
    }

    private record Entry(String fingerprint, Outcome outcome) {}
}
