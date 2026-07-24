package com.allinweb.ch.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Direct drag &amp; drop reorder tests for the Memory List, using synthetic row keys only — no
 * WebSocket, database, or JavaFX. Mirrors what the detached Memory List page sends when a row is
 * dragged: the full list of row keys in their new order. Run in isolation with:
 *
 * <pre>mvn -Dtest=MemoryListReorderTest test</pre>
 */
class MemoryListReorderTest {

    /** 10 synthetic rows, matching the frontend key shape "SOURCE:id". */
    private static Set<String> tenSyntheticKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (int n = 1; n <= 10; n++) {
            keys.add((n % 2 == 0 ? "PAGE_SCANNER:" : "BOT_JOB:") + n);
        }
        return keys;
    }

    private static List<String> orderedKeys() {
        return new ArrayList<>(tenSyntheticKeys());
    }

    @Test
    void dragMovingFirstRowToLastIsAccepted() {
        Set<String> valid = tenSyntheticKeys();
        List<String> current = orderedKeys();

        // Drag row 1 to the bottom: [1,2,...,10] -> [2,...,10,1].
        List<String> dragged = new ArrayList<>(current.subList(1, current.size()));
        dragged.add(current.get(0));

        MemoryListReorder.Outcome outcome =
                MemoryListReorder.resolve(current.size(), valid, dragged);

        assertTrue(outcome.ok(), "a complete permutation must be accepted");
        assertNull(outcome.error());
        assertEquals(dragged, outcome.orderedKeys(), "the new order must be preserved exactly");
        assertEquals(current.get(0), outcome.orderedKeys().get(9), "row 1 must land at the bottom");
    }

    @Test
    void draggingAdjacentRowsSwapsThem() {
        Set<String> valid = tenSyntheticKeys();
        List<String> current = orderedKeys();

        // Swap rows 3 and 4.
        List<String> dragged = new ArrayList<>(current);
        java.util.Collections.swap(dragged, 2, 3);

        MemoryListReorder.Outcome outcome =
                MemoryListReorder.resolve(current.size(), valid, dragged);

        assertTrue(outcome.ok());
        assertEquals(current.get(3), outcome.orderedKeys().get(2));
        assertEquals(current.get(2), outcome.orderedKeys().get(3));
    }

    @Test
    void identityReorderIsStillAValidPermutation() {
        Set<String> valid = tenSyntheticKeys();
        List<String> current = orderedKeys();

        MemoryListReorder.Outcome outcome =
                MemoryListReorder.resolve(current.size(), valid, new ArrayList<>(current));

        assertTrue(outcome.ok());
        assertEquals(current, outcome.orderedKeys());
    }

    @Test
    void wrongRowCountIsRejectedAsIncomplete() {
        Set<String> valid = tenSyntheticKeys();
        List<String> tooFew = new ArrayList<>(orderedKeys().subList(0, 9));

        MemoryListReorder.Outcome outcome = MemoryListReorder.resolve(10, valid, tooFew);

        assertFalse(outcome.ok());
        assertEquals(MemoryListReorder.INCOMPLETE, outcome.error());
        assertNull(outcome.orderedKeys());
    }

    @Test
    void nullOrderIsRejectedAsIncomplete() {
        MemoryListReorder.Outcome outcome =
                MemoryListReorder.resolve(10, tenSyntheticKeys(), null);

        assertFalse(outcome.ok());
        assertEquals(MemoryListReorder.INCOMPLETE, outcome.error());
    }

    @Test
    void unknownRowKeyIsRejected() {
        Set<String> valid = tenSyntheticKeys();
        List<String> dragged = orderedKeys();
        dragged.set(4, "BOT_JOB:9999"); // a key that is not part of the list

        MemoryListReorder.Outcome outcome =
                MemoryListReorder.resolve(dragged.size(), valid, dragged);

        assertFalse(outcome.ok());
        assertEquals(MemoryListReorder.MISSING_OR_DUPLICATE, outcome.error());
    }

    @Test
    void duplicateRowKeyIsRejected() {
        Set<String> valid = tenSyntheticKeys();
        List<String> dragged = orderedKeys();
        dragged.set(5, dragged.get(0)); // same key appears twice, count still 10

        MemoryListReorder.Outcome outcome =
                MemoryListReorder.resolve(dragged.size(), valid, dragged);

        assertFalse(outcome.ok());
        assertEquals(MemoryListReorder.MISSING_OR_DUPLICATE, outcome.error());
    }

    @Test
    void nullKeyInsideOrderIsRejected() {
        Set<String> valid = tenSyntheticKeys();
        List<String> dragged = orderedKeys();
        dragged.set(1, null);

        MemoryListReorder.Outcome outcome =
                MemoryListReorder.resolve(dragged.size(), valid, dragged);

        assertFalse(outcome.ok());
        assertEquals(MemoryListReorder.MISSING_OR_DUPLICATE, outcome.error());
    }

    @Test
    void everySingleAdjacentDragAcrossTheListIsAccepted() {
        Set<String> valid = tenSyntheticKeys();
        List<String> base = orderedKeys();

        // Exhaustively exercise "grab row i, drop on row i+1" for the whole list.
        for (int i = 0; i < base.size() - 1; i++) {
            List<String> dragged = new ArrayList<>(base);
            java.util.Collections.swap(dragged, i, i + 1);
            MemoryListReorder.Outcome outcome =
                    MemoryListReorder.resolve(base.size(), valid, dragged);
            assertTrue(outcome.ok(), "adjacent drag at index " + i + " must be accepted");
            assertEquals(dragged, outcome.orderedKeys());
        }
    }

    @Test
    void reversingTheWholeListIsAValidReorder() {
        Set<String> valid = tenSyntheticKeys();
        List<String> reversed = orderedKeys();
        java.util.Collections.reverse(reversed);

        MemoryListReorder.Outcome outcome =
                MemoryListReorder.resolve(reversed.size(), valid, reversed);

        assertTrue(outcome.ok());
        assertEquals(reversed, outcome.orderedKeys());
        // Sanity: full reversal is still a permutation of the same key set.
        assertEquals(new LinkedHashSet<>(Arrays.asList(reversed.toArray(new String[0]))).size(), 10);
    }
}
