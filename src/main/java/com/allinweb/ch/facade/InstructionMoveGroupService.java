package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves the complete connected instruction group represented by one dragged row. */
public final class InstructionMoveGroupService {
    public List<InstructionLoad> resolve(List<InstructionLoad> rows, int instructionId) {
        InstructionLoad selected = rows.stream()
                .filter(row -> row != null && row.getId() != null && row.getId() == instructionId)
                .findFirst()
                .orElse(null);
        if (selected == null || selected.getBlockId() == null) return List.of();

        List<InstructionLoad> block = rows.stream()
                .filter(row -> row != null && selected.getBlockId().equals(row.getBlockId()))
                .sorted(Comparator.comparingInt(this::order))
                .toList();
        int selectedIndex = indexOf(block, instructionId);
        Set<Integer> ids = new LinkedHashSet<>();
        addSmallestConditionalSpan(block, selectedIndex, ids);
        addSmallestLoopSpan(block, selectedIndex, ids);
        addDependentFamily(block, selected, ids);
        if (ids.isEmpty()) ids.add(instructionId);
        return block.stream().filter(row -> ids.contains(row.getId())).toList();
    }

    private void addSmallestConditionalSpan(List<InstructionLoad> block, int selectedIndex, Set<Integer> ids) {
        Deque<Integer> starts = new ArrayDeque<>();
        int bestStart = -1;
        int bestEnd = Integer.MAX_VALUE;
        for (int index = 0; index < block.size(); index++) {
            String action = block.get(index).getActions();
            if ("IF".equals(action)) starts.push(index);
            if ("ENDIF".equals(action) && !starts.isEmpty()) {
                int start = starts.pop();
                if (start <= selectedIndex && selectedIndex <= index && index - start < bestEnd - bestStart) {
                    bestStart = start;
                    bestEnd = index;
                }
            }
        }
        addRange(block, bestStart, bestEnd, ids);
    }

    private void addSmallestLoopSpan(List<InstructionLoad> block, int selectedIndex, Set<Integer> ids) {
        int bestStart = -1;
        int bestEnd = Integer.MAX_VALUE;
        for (int boundaryIndex = 0; boundaryIndex < block.size(); boundaryIndex++) {
            InstructionLoad boundary = block.get(boundaryIndex);
            if (!Set.of("LOOP", "REFRESH_LOOP").contains(boundary.getActions()) || boundary.getParentId() == null) {
                continue;
            }
            int parentIndex = indexOf(block, boundary.getParentId());
            if (parentIndex < 0) continue;
            int start = Math.min(parentIndex, boundaryIndex);
            int end = Math.max(parentIndex, boundaryIndex);
            if (start <= selectedIndex && selectedIndex <= end && end - start < bestEnd - bestStart) {
                bestStart = start;
                bestEnd = end;
            }
        }
        addRange(block, bestStart, bestEnd, ids);
    }

    private void addDependentFamily(List<InstructionLoad> block, InstructionLoad selected, Set<Integer> ids) {
        Integer parentId = selected.getParentId();
        boolean selectedIsCommand = CommandRegistry.isSpecialAction(selected.getActions())
                && parentId != null
                && !parentId.equals(selected.getId());
        int rootId = selectedIsCommand ? parentId : selected.getId();
        block.stream()
                .filter(row -> row.getId() != null
                        && (row.getId() == rootId
                                || (row.getParentId() != null && row.getParentId() == rootId
                                        && CommandRegistry.isSpecialAction(row.getActions()))))
                .forEach(row -> ids.add(row.getId()));
    }

    private void addRange(List<InstructionLoad> block, int start, int end, Set<Integer> ids) {
        if (start < 0 || end >= block.size()) return;
        for (int index = start; index <= end; index++) ids.add(block.get(index).getId());
    }

    private int indexOf(List<InstructionLoad> rows, int id) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).getId() != null && rows.get(index).getId() == id) return index;
        }
        return -1;
    }

    private int order(InstructionLoad row) {
        return row.getInstructionOrderNumber() == null ? Integer.MAX_VALUE : row.getInstructionOrderNumber();
    }
}
