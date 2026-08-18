package com.allinweb.ch.socket;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Bounded idempotency ledger for detached Page Scanner database mutations. */
final class PageScannerMutationLedger {

    private static final int DEFAULT_MAX_ENTRIES = 512;
    private static final PageScannerMutationLedger INSTANCE =
            new PageScannerMutationLedger(DEFAULT_MAX_ENTRIES);

    private final int maxEntries;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    PageScannerMutationLedger() {
        this(DEFAULT_MAX_ENTRIES);
    }

    PageScannerMutationLedger(int maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    static PageScannerMutationLedger getInstance() {
        return INSTANCE;
    }

    synchronized JsonObject executeOnce(
            String workspaceSessionId,
            String requestId,
            String operation,
            JsonObject body,
            Supplier<JsonObject> mutation) {
        String session = requireNonBlank(workspaceSessionId, "Page Scanner session is required");
        String request = requireNonBlank(requestId, "Page Scanner requestId is required");
        String operationId = requireNonBlank(operation, "Page Scanner operation is required");
        Objects.requireNonNull(body, "Page Scanner request body is required");
        Objects.requireNonNull(mutation, "Page Scanner mutation is required");

        String key = session + ':' + operationId + ':' + request;
        String fingerprint = fingerprint(body);
        Entry existing = entries.get(key);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                JsonObject rejected = new JsonObject();
                rejected.addProperty("ok", false);
                rejected.addProperty("requestId", request);
                rejected.addProperty("errorCode", "REQUEST_ID_REUSE");
                rejected.addProperty("message", "Page Scanner requestId was reused with different data");
                return rejected;
            }
            return existing.response().deepCopy();
        }

        JsonObject response = mutation.get();
        if (response == null) throw new IllegalStateException("Page Scanner mutation returned no response");
        entries.put(key, new Entry(fingerprint, response.deepCopy()));
        trim();
        return response.deepCopy();
    }

    synchronized void clearSession(String workspaceSessionId) {
        if (workspaceSessionId == null || workspaceSessionId.isBlank()) return;
        String prefix = workspaceSessionId.trim() + ':';
        entries.keySet().removeIf(key -> key.startsWith(prefix));
    }

    synchronized int size() {
        return entries.size();
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

    private static String fingerprint(JsonObject body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(body.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private record Entry(String fingerprint, JsonObject response) {}
}
