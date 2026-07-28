package com.allinweb.ch.socket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    public static final String CONNECTED_GROUP_SPLIT =
            "Connected Memory List instructions must move together.";

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

    /**
     * Validates a complete permutation and additionally guarantees that members
     * of one dependency group stay contiguous and retain their internal order.
     */
    public static Outcome resolveGrouped(
            List<String> currentOrder,
            Set<String> validKeys,
            Map<String, String> dependencyGroupByItem,
            List<String> requestedKeys) {
        Outcome base = resolve(
                currentOrder == null ? 0 : currentOrder.size(),
                validKeys,
                requestedKeys);
        if (!base.ok()) return base;
        if (currentOrder == null
                || dependencyGroupByItem == null
                || dependencyGroupByItem.isEmpty()) {
            return base;
        }

        Map<String, List<String>> currentGroups = groupedMembers(
                currentOrder, dependencyGroupByItem);
        for (List<String> members : currentGroups.values()) {
            if (members.size() <= 1) continue;
            int first = base.orderedKeys().indexOf(members.get(0));
            if (first < 0 || first + members.size() > base.orderedKeys().size()) {
                return new Outcome(false, null, CONNECTED_GROUP_SPLIT);
            }
            for (int index = 0; index < members.size(); index++) {
                if (!members.get(index).equals(base.orderedKeys().get(first + index))) {
                    return new Outcome(false, null, CONNECTED_GROUP_SPLIT);
                }
            }
        }
        return base;
    }

    /** Returns every row that must be removed with the selected connected row. */
    public static List<String> connectedRemovalKeys(
            List<String> currentOrder,
            Map<String, String> dependencyGroupByItem,
            String selectedKey) {
        if (selectedKey == null || currentOrder == null || !currentOrder.contains(selectedKey)) {
            return List.of();
        }
        String selectedGroup = dependencyGroupByItem == null
                ? null
                : dependencyGroupByItem.get(selectedKey);
        if (selectedGroup == null || selectedGroup.isBlank()) return List.of(selectedKey);
        List<String> connected = currentOrder.stream()
                .filter(key -> selectedGroup.equals(dependencyGroupByItem.get(key)))
                .toList();
        return connected.isEmpty() ? List.of(selectedKey) : connected;
    }

    private static Map<String, List<String>> groupedMembers(
            List<String> order, Map<String, String> dependencyGroupByItem) {
        Map<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        for (String itemKey : order) {
            String groupKey = dependencyGroupByItem.get(itemKey);
            if (groupKey == null || groupKey.isBlank()) continue;
            grouped.computeIfAbsent(groupKey, ignored -> new LinkedHashSet<>()).add(itemKey);
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        grouped.forEach((key, members) -> result.put(key, List.copyOf(members)));
        return result;
    }
}
