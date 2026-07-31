package com.allinweb.ch.facade.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.actions.RuntimeVariableValue.State;
import com.allinweb.ch.facade.actions.RuntimeVariableValue.VoidReason;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.BotJobKey;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.Definition;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.ValueSource;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.OwnerKey;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableService;
import com.allinweb.ch.model.VariableLoadDTO;
import java.sql.SQLException;
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
    void preservesCanonicalBrowserTextWithoutLocaleNormalizationOrTrimming() {
        RuntimeVariableStore store = new RuntimeVariableStore();
        store.reset(List.of(variable(7)), true);

        List<String> browserValues = List.of(
                " 1.234,56 € ",
                "CHF\u00a01'234.50",
                "R$ 1.234,56",
                "07/30/2026",
                "2026-07-30",
                "VOID",
                "  ");

        for (String browserValue : browserValues) {
            assertTrue(store.write(7, browserValue));
            assertEquals(browserValue, store.read(7).value());
        }
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
    void definitionRefreshRetainsTheCurrentBotJobValue() {
        RuntimeVariableStore store = new RuntimeVariableStore();
        store.reset(List.of(variable(7)), true);
        store.write(7, "row-one");

        store.reset(List.of(variable(7)), true);

        assertEquals("row-one", store.read(7).value());
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

    @Test
    void incompleteLaunchIdentityFallsBackWithoutBlockingExecution() {
        RuntimeVariableStore store = new RuntimeVariableStore(0, 0);
        store.reset(List.of(variable(7)), true);

        assertTrue(store.write(7, "fallback-value"));
        assertEquals("fallback-value", store.read(7).value());
    }

    @Test
    void durableWriteFailureUsesExecutionLocalVoidWithoutPollutingCommittedCache() {
        RuntimeVariableMemoryRegistry registry = RuntimeVariableMemoryRegistry.getInstance();
        BotJobKey owner = new BotJobKey(2, 501);
        registry.remove(owner);
        registry.reconcileDefinitions(
                owner,
                List.of(new Definition(7, "Amount", "$String")),
                true);
        assertTrue(registry.write(owner, 7, "last-committed", ValueSource.EXECUTION));

        try {
            RuntimeVariableStore failingExecution = new RuntimeVariableStore(
                    registry,
                    owner,
                    new BotJobRuntimeVariableService(),
                    new OwnerKey(2, 501),
                    () -> {
                        throw new SQLException("database unavailable");
                    });

            assertFalse(failingExecution.write(7, "uncommitted"));
            assertEquals(State.VOID, failingExecution.read(7).state());
            assertEquals(
                    VoidReason.PRODUCER_FAILED,
                    failingExecution.read(7).voidReason());

            RuntimeVariableStore separateExecution =
                    new RuntimeVariableStore(registry, owner);
            assertEquals("last-committed", separateExecution.read(7).value());
        } finally {
            registry.remove(owner);
        }
    }

    @Test
    void failedDurableClearUsesRequestedVoidReasonOnlyInCurrentExecution() {
        RuntimeVariableMemoryRegistry registry = RuntimeVariableMemoryRegistry.getInstance();
        BotJobKey owner = new BotJobKey(2, 502);
        registry.remove(owner);
        registry.reconcileDefinitions(
                owner,
                List.of(new Definition(7, "Amount", "$String")),
                true);
        assertTrue(registry.write(owner, 7, "last-committed", ValueSource.EXECUTION));

        try {
            RuntimeVariableStore failingExecution = new RuntimeVariableStore(
                    registry,
                    owner,
                    new BotJobRuntimeVariableService(),
                    new OwnerKey(2, 502),
                    () -> {
                        throw new SQLException("database unavailable");
                    });

            failingExecution.markVoid(7, VoidReason.MISSING_PARENT);
            assertEquals(State.VOID, failingExecution.read(7).state());
            assertEquals(VoidReason.MISSING_PARENT, failingExecution.read(7).voidReason());
            assertEquals("last-committed", registry.read(owner, 7).value());
        } finally {
            registry.remove(owner);
        }
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
