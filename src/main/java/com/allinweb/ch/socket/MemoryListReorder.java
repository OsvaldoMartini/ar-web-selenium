package com.allinweb.ch.socket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure, dependency-free core of the Memory List drag &amp; drop reorder.
 *
 * <p>Extracted from {@link MemoryListWorkspaceService} so the reorder contract can be unit tested
 * with synthetic data — no WebSocket transport, database, or JavaFX. The semantics are exactly what
 * the detached Memory List page sends when a row is dragged: the full set of row keys in their new
 * order. A reorder is accepted only when the requested keys are a complete permutation of the
 * current rows (same count, every key known, no duplicates); anything else is rejected without
 * mutating state so the next snapshot re-publishes the unchanged order.
 */
public final class MemoryListReorder {

    /** Failure messages kept identical to the pre-extraction inline handler. */
    public static final String INCOMPLETE =
            "Memory List order is incomplete. Refresh and try again.";
    public static final String MISSING_OR_DUPLICATE =
            "Memory List order contains a missing or duplicate row.";

    private MemoryListReorder() {}

    /** Outcome of a reorder attempt: either the validated ordered keys or an error message. */
    public static final class Outcome {
        private final boolean ok;
        private final List<String> orderedKeys;
        private final String error;

        private Outcome(boolean ok, List<String> orderedKeys, String error) {
            this.ok = ok;
            this.orderedKeys = orderedKeys;
            this.error = error;
        }

        public boolean ok() {
            return ok;
        }

        /** Non-null and safe to copy into the canonical order when {@link #ok()} is true. */
        public List<String> orderedKeys() {
            return orderedKeys;
        }

        /** Non-null user-facing reason when {@link #ok()} is false. */
        public String error() {
            return error;
        }
    }

    /**
     * Validates a requested drag &amp; drop order against the current rows.
     *
     * @param currentOrderSize number of rows currently in the list
     * @param validKeys the set of currently known row keys (the canonical item keys)
     * @param requestedKeys the row keys in the order the user dragged them into; {@code null} or a
     *     size mismatch is treated as an incomplete order
     */
    public static Outcome resolve(
            int currentOrderSize, Set<String> validKeys, List<String> requestedKeys) {
        if (requestedKeys == null || requestedKeys.size() != currentOrderSize) {
            return new Outcome(false, null, INCOMPLETE);
        }
        List<String> next = new ArrayList<>(requestedKeys.size());
        Set<String> unique = new HashSet<>();
        for (String key : requestedKeys) {
            if (key == null || validKeys == null || !validKeys.contains(key) || !unique.add(key)) {
                return new Outcome(false, null, MISSING_OR_DUPLICATE);
            }
            next.add(key);
        }
        return new Outcome(true, next, null);
    }
}
