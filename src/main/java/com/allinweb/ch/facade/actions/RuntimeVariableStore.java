package com.allinweb.ch.facade.actions;

import com.allinweb.ch.facade.actions.RuntimeVariableValue.VoidReason;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.BotJobKey;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.Definition;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry.ValueSource;
import com.allinweb.ch.model.VariableLoadDTO;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime variable access for either one isolated caller or one persistent Bot Job memory.
 *
 * <p>The store is keyed by the stable numeric variable ID. Display names are never keys, which
 * prevents unrelated instructions with missing metadata from sharing one placeholder entry.
 */
public final class RuntimeVariableStore {
    private final Map<Integer, RuntimeVariableValue> values = new HashMap<>();
    private final Set<Integer> knownVariableIds = new HashSet<>();
    private final RuntimeVariableMemoryRegistry registry;
    private final BotJobKey owner;
    private VoidReason unresolvedReason = VoidReason.NO_PRODUCER_YET;
    private boolean metadataAvailable = true;

    /** Creates an isolated store for non-Bot-Job callers and focused unit tests. */
    public RuntimeVariableStore() {
        registry = null;
        owner = null;
    }

    /** Creates a store backed by the process-local memory for one exact Bot Job owner. */
    public RuntimeVariableStore(int homeBankingId, int botJobId) {
        if (homeBankingId > 0 && botJobId > 0) {
            registry = RuntimeVariableMemoryRegistry.getInstance();
            owner = new BotJobKey(homeBankingId, botJobId);
        } else {
            // Runtime variables are optional execution support. An incomplete launch identity
            // must never prevent a Bot Job from running; fall back to the isolated VOID-aware
            // store used by legacy/non-Bot-Job callers.
            registry = null;
            owner = null;
        }
    }

    /**
     * Legacy name retained for callers outside the runtime backend.
     *
     * <p>Definition refresh no longer clears produced values. Runtime memory is intentionally
     * retained until a command or authorized manual edit changes it.
     */
    public void reset(
            Collection<VariableLoadDTO> definitions,
            boolean metadataAvailable) {
        reconcileDefinitions(definitions, metadataAvailable);
    }

    public void reconcileDefinitions(
            Collection<VariableLoadDTO> definitions,
            boolean metadataAvailable) {
        if (registry != null) {
            List<Definition> runtimeDefinitions = definitions == null
                    ? List.of()
                    : definitions.stream()
                            .filter(Objects::nonNull)
                            .filter(variable -> validId(variable.getId()))
                            .map(variable -> new Definition(
                                    variable.getId(),
                                    variable.getName(),
                                    variable.getType()))
                            .toList();
            registry.reconcileDefinitions(
                    owner, runtimeDefinitions, metadataAvailable);
            return;
        }

        this.metadataAvailable = metadataAvailable;
        unresolvedReason = metadataAvailable
                ? VoidReason.NO_PRODUCER_YET
                : VoidReason.METADATA_UNAVAILABLE;
        if (!metadataAvailable) {
            return;
        }

        Set<Integer> nextIds = definitions == null
                ? Set.of()
                : definitions.stream()
                .filter(Objects::nonNull)
                .map(VariableLoadDTO::getId)
                .filter(RuntimeVariableStore::validId)
                .collect(java.util.stream.Collectors.toSet());
        knownVariableIds.clear();
        knownVariableIds.addAll(nextIds);
        values.keySet().removeIf(id -> !nextIds.contains(id));
        nextIds.forEach(id -> values.putIfAbsent(
                id, RuntimeVariableValue.voidValue(unresolvedReason)));
    }

    public RuntimeVariableValue read(Integer variableId) {
        if (registry != null) return registry.read(owner, variableId);
        if (!validId(variableId)) {
            return RuntimeVariableValue.voidValue(VoidReason.MISSING_BINDING);
        }
        RuntimeVariableValue currentValue = values.get(variableId);
        if (currentValue != null) {
            return currentValue;
        }
        if (!metadataAvailable) {
            return RuntimeVariableValue.voidValue(VoidReason.METADATA_UNAVAILABLE);
        }
        if (!knownVariableIds.contains(variableId)) {
            return RuntimeVariableValue.voidValue(VoidReason.MISSING_BINDING);
        }
        return RuntimeVariableValue.voidValue(unresolvedReason);
    }

    public void markVoid(Integer variableId, VoidReason reason) {
        if (registry != null) {
            registry.markVoid(owner, variableId, reason, ValueSource.EXECUTION);
            return;
        }
        if (validId(variableId)) {
            values.put(variableId, RuntimeVariableValue.voidValue(reason));
        }
    }

    public boolean write(Integer variableId, String value) {
        if (registry != null) {
            return registry.write(
                    owner, variableId, value, ValueSource.EXECUTION);
        }
        if (!validId(variableId) || value == null) {
            return false;
        }
        // A positive instruction-carried ID is a safe run-scoped key even when metadata is
        // temporarily unavailable. It never persists or invents a database relationship, and it
        // lets a later producer recover VOID for downstream commands in the same run.
        values.put(variableId, RuntimeVariableValue.value(value));
        return true;
    }

    private static boolean validId(Integer variableId) {
        return variableId != null && variableId > 0;
    }
}
