package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Resolves the contiguous parent-to-loop span owned by LOOP and REFRESH_LOOP. */
public final class LoopGroupService {
    private static final Set<String> LOOP_ACTIONS = Set.of("LOOP", "REFRESH_LOOP");

    public List<Integer> groupIds(List<InstructionLoad> rows, int loopId) {
        List<InstructionLoad> ordered = rows.stream()
                .filter(row -> row != null && row.getId() != null)
                .sorted(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                        ? Integer.MAX_VALUE
                        : row.getInstructionOrderNumber()))
                .toList();
        int loopIndex = indexOf(ordered, loopId);
        if (loopIndex < 0) return List.of();
        InstructionLoad loop = ordered.get(loopIndex);
        if (loop.getActions() == null || !LOOP_ACTIONS.contains(loop.getActions()) || loop.getParentId() == null) {
            return List.of();
        }
        int parentIndex = indexOf(ordered, loop.getParentId());
        if (parentIndex < 0 || loop.getBlockId() == null
                || !loop.getBlockId().equals(ordered.get(parentIndex).getBlockId())) return List.of();
        int first = Math.min(parentIndex, loopIndex);
        int last = Math.max(parentIndex, loopIndex);
        return ordered.subList(first, last + 1).stream().map(InstructionLoad::getId).toList();
    }

    private int indexOf(List<InstructionLoad> rows, int id) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).getId() == id) return index;
        }
        return -1;
    }
}
