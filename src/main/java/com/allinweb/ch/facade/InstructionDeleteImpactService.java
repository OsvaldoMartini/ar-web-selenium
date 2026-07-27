package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resolves the authoritative rows removed by one instruction deletion intent. */
public final class InstructionDeleteImpactService {
    private final ConditionalBranchService conditionalBranchService = new ConditionalBranchService();
    private final LoopGroupService loopGroupService = new LoopGroupService();

    public List<InstructionLoad> resolve(InstructionLoad selected, List<InstructionLoad> rows) {
        if (selected == null || selected.getId() == null) return List.of();
        String action = selected.getActions() == null ? "" : selected.getActions();
        Set<Integer> ids;
        if (Set.of("LOOP", "REFRESH_LOOP").contains(action)) {
            ids = new HashSet<>(loopGroupService.groupIds(rows, selected.getId()));
        } else if ("ELSEIF".equals(action)) {
            ids = new HashSet<>(conditionalBranchService.elseIfBranchIds(rows, selected.getId()));
        } else if (Set.of("IF", "ELSE", "ENDIF").contains(action)) {
            Integer rootId = "IF".equals(action) ? selected.getId() : selected.getParentId();
            ids = rootId == null ? Set.of(selected.getId()) : conditionalFamilyIds(rows, rootId);
        } else {
            ids = dependentFamilyIds(rows, selected.getId());
        }
        return rows.stream()
                .filter(row -> row != null && row.getId() != null && ids.contains(row.getId()))
                .sorted(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                        ? Integer.MAX_VALUE
                        : row.getInstructionOrderNumber()))
                .toList();
    }

    /**
     * Deletes an ordinary instruction together with every instruction that depends on it.
     * A LOOP boundary also owns its complete loop span, so deleting its Web Field cannot leave
     * a detached loop body behind.
     */
    private Set<Integer> dependentFamilyIds(List<InstructionLoad> rows, int rootId) {
        Set<Integer> ids = new HashSet<>();
        ids.add(rootId);
        boolean changed;
        do {
            changed = false;
            for (InstructionLoad row : rows) {
                if (row == null || row.getId() == null || row.getParentId() == null) continue;
                if (!ids.contains(row.getParentId()) || ids.contains(row.getId())) continue;
                if (Set.of("LOOP", "REFRESH_LOOP").contains(row.getActions())) {
                    List<Integer> loopIds = loopGroupService.groupIds(rows, row.getId());
                    if (loopIds.isEmpty()) changed |= ids.add(row.getId());
                    else changed |= ids.addAll(loopIds);
                } else {
                    changed |= ids.add(row.getId());
                }
            }
        } while (changed);
        return ids;
    }

    private Set<Integer> conditionalFamilyIds(List<InstructionLoad> rows, int rootId) {
        Set<Integer> ids = new HashSet<>();
        for (InstructionLoad row : rows) {
            if (row == null || row.getId() == null) continue;
            if (row.getId() == rootId || (row.getParentId() != null && row.getParentId() == rootId)) {
                ids.add(row.getId());
            }
        }
        return ids;
    }
}
