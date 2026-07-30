package com.allinweb.ch.facade.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.BotJobKey;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.Definition;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.ValueSource;
import com.allinweb.ch.facade.actions.RuntimeVariableValue.VoidReason;
import com.allinweb.ch.model.VariableLoadDTO;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RuntimeVariableMemoryRegistryTest {
    private static final BotJobKey FIRST = new BotJobKey(901, 9901);
    private static final BotJobKey SECOND = new BotJobKey(901, 9902);
    private final RuntimeVariableMemoryRegistry registry =
            RuntimeVariableMemoryRegistry.getInstance();

    @AfterEach
    void cleanUp() {
        registry.remove(FIRST);
        registry.remove(SECOND);
    }

    @Test
    void startsVoidAndPreservesAValueAcrossDefinitionRefreshAndStoreInstances() {
        RuntimeVariableStore first =
                new RuntimeVariableStore(FIRST.homeBankingId(), FIRST.botJobId());
        first.reconcileDefinitions(List.of(variable(7, "Amount")), true);

        assertTrue(first.read(7).isVoid());
        assertEquals(VoidReason.NO_PRODUCER_YET, first.read(7).voidReason());
        assertTrue(first.write(7, "42.00"));

        RuntimeVariableStore replacement =
                new RuntimeVariableStore(FIRST.homeBankingId(), FIRST.botJobId());
        replacement.reconcileDefinitions(List.of(variable(7, "Renamed amount")), true);

        assertEquals("42.00", replacement.read(7).value());
        assertEquals(
                "Renamed amount",
                registry.snapshot(FIRST).variables().get(0).name());
    }

    @Test
    void manualWriteIsVisibleToExecutionAndBotJobsRemainIsolated() {
        registry.reconcileDefinitions(
                FIRST, List.of(new Definition(7, "Amount", "$String")), true);
        registry.reconcileDefinitions(
                SECOND, List.of(new Definition(7, "Amount", "$String")), true);

        registry.write(FIRST, 7, "", ValueSource.MANUAL);

        RuntimeVariableStore first =
                new RuntimeVariableStore(FIRST.homeBankingId(), FIRST.botJobId());
        RuntimeVariableStore second =
                new RuntimeVariableStore(SECOND.homeBankingId(), SECOND.botJobId());
        assertEquals("", first.read(7).value());
        assertTrue(second.read(7).isVoid());
        assertEquals(
                ValueSource.MANUAL,
                registry.snapshot(FIRST).variables().get(0).source());
    }

    @Test
    void authoritativeCatalogRejectsUndefinedWritesAndNewDefinitionStartsVoid() {
        registry.reconcileDefinitions(
                FIRST, List.of(new Definition(7, "Amount", "$String")), true);

        assertFalse(registry.write(FIRST, 99, "hidden", ValueSource.EXECUTION));
        assertEquals(
                VoidReason.MISSING_BINDING,
                registry.read(FIRST, 99).voidReason());

        registry.reconcileDefinitions(
                FIRST,
                List.of(
                        new Definition(7, "Amount", "$String"),
                        new Definition(99, "Later", "$String")),
                true);

        var later = registry.snapshot(FIRST).variables().stream()
                .filter(item -> item.variableId() == 99)
                .findFirst()
                .orElseThrow();
        assertEquals(RuntimeVariableValue.State.VOID, later.state());
        assertEquals(VoidReason.NO_PRODUCER_YET, later.voidReason());
    }

    @Test
    void sameValueFromDifferentWriterAdvancesProvenance() {
        registry.reconcileDefinitions(
                FIRST, List.of(new Definition(7, "Amount", "$String")), true);
        assertTrue(registry.write(FIRST, 7, "42", ValueSource.EXECUTION));
        var execution = registry.snapshot(FIRST);

        assertTrue(registry.write(FIRST, 7, "42", ValueSource.MANUAL));
        var manual = registry.snapshot(FIRST);

        assertEquals(execution.revision() + 1L, manual.revision());
        assertEquals(
                execution.variables().get(0).entryRevision() + 1L,
                manual.variables().get(0).entryRevision());
        assertEquals(ValueSource.MANUAL, manual.variables().get(0).source());
    }

    @Test
    void lifecycleRemovalCanRetireOneBotJobOrAnEntireDatabase() {
        registry.reconcileDefinitions(
                FIRST, List.of(new Definition(7, "First", "$String")), true);
        registry.reconcileDefinitions(
                SECOND, List.of(new Definition(7, "Second", "$String")), true);

        registry.removeBotJob(FIRST.botJobId());

        assertTrue(registry.snapshot(FIRST).variables().isEmpty());
        assertEquals(1, registry.snapshot(SECOND).variables().size());

        registry.clearAll();

        assertTrue(registry.snapshot(SECOND).variables().isEmpty());
    }

    private VariableLoadDTO variable(int id, String name) {
        return new VariableLoadDTO(
                id,
                FIRST.homeBankingId(),
                FIRST.botJobId(),
                189,
                "$String",
                name,
                "",
                "",
                "",
                0);
    }
}
