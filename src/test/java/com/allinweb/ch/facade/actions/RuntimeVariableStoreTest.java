package com.allinweb.ch.facade.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.actions.RuntimeVariableValue.State;
import com.allinweb.ch.facade.actions.RuntimeVariableValue.VoidReason;
import com.allinweb.ch.model.VariableLoadDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeVariableStoreTest {

    @Test
    void keepsProducedEmptyDistinctFromVoid() {
        RuntimeVariableStore store = new RuntimeVariableStore();
        store.reset(List.of(variable(7)), true);

        assertEquals(State.VOID, store.read(7).state());
        assertEquals(VoidReason.NO_PRODUCER_YET, store.read(7).voidReason());

        assertTrue(store.write(7, ""));

        RuntimeVariableValue empty = store.read(7);
        assertEquals(State.VALUE, empty.state());
        assertTrue(empty.isEmptyValue());
        assertEquals("", empty.value());
        assertNull(empty.voidReason());
    }

    @Test
    void failedProducerReplacesEarlierValueWithVoid() {
        RuntimeVariableStore store = new RuntimeVariableStore();
        store.reset(List.of(variable(7)), true);
        assertTrue(store.write(7, "old-row-value"));

        store.markVoid(7, VoidReason.PRODUCER_FAILED);

        RuntimeVariableValue result = store.read(7);
        assertTrue(result.isVoid());
        assertEquals(VoidReason.PRODUCER_FAILED, result.voidReason());
        assertNull(result.value());
    }

    @Test
    void resetPreventsAValueLeakingIntoTheNextInputRow() {
        RuntimeVariableStore store = new RuntimeVariableStore();
        store.reset(List.of(variable(7)), true);
        store.write(7, "row-one");

        store.reset(List.of(variable(7)), true);

        assertTrue(store.read(7).isVoid());
        assertEquals(VoidReason.NO_PRODUCER_YET, store.read(7).voidReason());
    }

    @Test
    void missingMetadataAndMissingBindingHaveDifferentReasons() {
        RuntimeVariableStore store = new RuntimeVariableStore();
        store.reset(List.of(), false);

        assertEquals(VoidReason.METADATA_UNAVAILABLE, store.read(7).voidReason());
        assertEquals(VoidReason.MISSING_BINDING, store.read(null).voidReason());
        assertTrue(store.write(7, "metadata-outage-value"));
        assertEquals("metadata-outage-value", store.read(7).value());
        assertFalse(store.write(null, "value"));

        store.reset(List.of(variable(8)), true);
        assertEquals(VoidReason.MISSING_BINDING, store.read(7).voidReason());
        assertTrue(store.write(7, "dangling-runtime-value"));
        assertEquals("dangling-runtime-value", store.read(7).value());
    }

    private VariableLoadDTO variable(int id) {
        return new VariableLoadDTO(
                id,
                2,
                5,
                189,
                "$String",
                "Amount",
                "",
                "",
                "",
                0);
    }
}
