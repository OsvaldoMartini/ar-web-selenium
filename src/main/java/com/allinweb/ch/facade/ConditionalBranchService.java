package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Resolves the exact row span owned by one ELSEIF branch. */
public final class ConditionalBranchService {
    private static final Set<String> SIBLING_BOUNDARIES = Set.of("ELSEIF", "ELSE", "ENDIF");

    public List<Integer> elseIfBranchIds(List<InstructionLoad> rows, int elseIfId) {
        List<InstructionLoad> ordered = rows.stream()
                .filter(row -> row != null && row.getId() != null)
                .sorted(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                        ? Integer.MAX_VALUE
                        : row.getInstructionOrderNumber()))
                .toList();
        int start = indexOf(ordered, elseIfId);
        if (start < 0) return List.of();
        InstructionLoad boundary = ordered.get(start);
        if (!"ELSEIF".equals(boundary.getActions()) || boundary.getParentId() == null) return List.of();

        Integer rootId = boundary.getParentId();
        List<Integer> ids = new ArrayList<>();
        for (int index = start; index < ordered.size(); index++) {
            InstructionLoad row = ordered.get(index);
            if (index > start
                    && SIBLING_BOUNDARIES.contains(row.getActions())
                    && rootId.equals(row.getParentId())) break;
            ids.add(row.getId());
        }
        return ids;
    }

    private int indexOf(List<InstructionLoad> rows, int id) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).getId() == id) return index;
        }
        return -1;
    }
}
