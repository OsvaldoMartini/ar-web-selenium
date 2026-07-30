package com.allinweb.ch.facade.actions;

import com.allinweb.ch.facade.actions.RuntimeVariableValue.VoidReason;
import com.allinweb.ch.model.VariableLoadDTO;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Per-input-row variable memory.
 *
 * <p>The store is keyed by the stable numeric variable ID. Display names are never keys, which
 * prevents unrelated instructions with missing metadata from sharing one placeholder entry.
 */
public final class RuntimeVariableStore {
    private final Map<Integer, RuntimeVariableValue> values = new HashMap<>();
    private final Set<Integer> knownVariableIds = new HashSet<>();
    private VoidReason unresolvedReason = VoidReason.NO_PRODUCER_YET;
    private boolean metadataAvailable = true;

    public void reset(
            Collection<VariableLoadDTO> definitions,
            boolean metadataAvailable) {
        values.clear();
        knownVariableIds.clear();
        this.metadataAvailable = metadataAvailable;
        unresolvedReason = metadataAvailable
                ? VoidReason.NO_PRODUCER_YET
                : VoidReason.METADATA_UNAVAILABLE;
        if (definitions == null) {
            return;
        }
        definitions.stream()
                .filter(Objects::nonNull)
                .map(VariableLoadDTO::getId)
                .filter(RuntimeVariableStore::validId)
                .forEach(id -> {
                    knownVariableIds.add(id);
                    values.put(id, RuntimeVariableValue.voidValue(unresolvedReason));
                });
    }

    public RuntimeVariableValue read(Integer variableId) {
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
        if (validId(variableId)) {
            values.put(variableId, RuntimeVariableValue.voidValue(reason));
        }
    }

    public boolean write(Integer variableId, String value) {
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
