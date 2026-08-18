package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import java.util.List;
import java.util.Set;

/** Validates that a block split keeps conditional and loop relationships together. */
public final class InstructionSplitValidator {
    private static final Set<String> CONDITIONAL_BOUNDARIES = Set.of("IF", "ELSEIF", "ELSE", "ENDIF");
    private static final Set<String> LOOP_ACTIONS = Set.of("LOOP", "REFRESH_LOOP");

    private final ConditionalGraphValidator conditionalValidator = new ConditionalGraphValidator();

    public String validate(List<InstructionLoad> rows, int anchorId) {
        if (rows == null || rows.size() <= 1) return "A split requires instructions on both sides.";
        String conditionalError = conditionalValidator.validate(rows);
        if (conditionalError != null) return conditionalError;

        int splitIndex = indexOf(rows, anchorId);
        if (splitIndex < 0) return "The split anchor no longer exists.";
        if (splitIndex >= rows.size() - 1) return "The last instruction cannot start an empty split block.";

        InstructionLoad anchor = rows.get(splitIndex);
        if (anchor.getActions() != null && CONDITIONAL_BOUNDARIES.contains(anchor.getActions().trim().toUpperCase())) {
            return "A conditional boundary cannot be used as a split anchor.";
        }
        if (insideConditionalFamily(rows, splitIndex)) return "A split cannot divide an IF family.";

        for (int loopIndex = 0; loopIndex < rows.size(); loopIndex++) {
            InstructionLoad loop = rows.get(loopIndex);
            if (loop == null || loop.getActions() == null
                    || !LOOP_ACTIONS.contains(loop.getActions().trim().toUpperCase())) continue;
            if (loop.getParentId() == null) return "A loop has no parent instruction.";
            int parentIndex = indexOf(rows, loop.getParentId());
            if (parentIndex < 0 || (parentIndex <= splitIndex) != (loopIndex <= splitIndex)) {
                return "A split cannot separate a loop from its parent instruction.";
            }
        }
        return null;
    }

    private boolean insideConditionalFamily(List<InstructionLoad> rows, int splitIndex) {
        int depth = 0;
        for (int index = 0; index <= splitIndex; index++) {
            InstructionLoad row = rows.get(index);
            String action = row == null || row.getActions() == null ? "" : row.getActions().trim().toUpperCase();
            if ("IF".equals(action)) depth++;
            if ("ENDIF".equals(action)) depth--;
        }
        return depth > 0;
    }

    private int indexOf(List<InstructionLoad> rows, int instructionId) {
        for (int index = 0; index < rows.size(); index++) {
            InstructionLoad row = rows.get(index);
            if (row != null && row.getId() != null && row.getId() == instructionId) return index;
        }
        return -1;
    }
}
