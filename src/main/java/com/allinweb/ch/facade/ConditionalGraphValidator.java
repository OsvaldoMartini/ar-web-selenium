package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/** Validates nested IF/ELSEIF/ELSE/ENDIF grammar and parent ownership. */
public final class ConditionalGraphValidator {
    private static final Set<String> BRANCH_BOUNDARIES = Set.of("ELSEIF", "ELSE", "ENDIF");

    public String validate(List<InstructionLoad> rows) {
        Deque<Frame> families = new ArrayDeque<>();
        for (InstructionLoad row : rows) {
            if (row == null || row.getActions() == null) continue;
            String action = row.getActions().trim().toUpperCase();
            if ("IF".equals(action)) {
                if (row.getId() == null) return "IF boundary has no instruction ID.";
                if (row.getParentId() == null || !row.getParentId().equals(row.getId())) {
                    return "IF root does not reference its own instruction ID.";
                }
                families.push(new Frame(row.getId()));
                continue;
            }
            if (!BRANCH_BOUNDARIES.contains(action)) continue;
            if (families.isEmpty()) return action + " has no matching IF.";
            Frame family = families.peek();
            if (row.getParentId() == null || !row.getParentId().equals(family.rootId)) {
                return action + " does not reference its matching IF.";
            }
            if ("ELSEIF".equals(action) && family.elseSeen) return "ELSEIF appears after ELSE.";
            if ("ELSE".equals(action)) {
                if (family.elseSeen) return "An IF family contains more than one ELSE.";
                family.elseSeen = true;
            }
            if ("ENDIF".equals(action)) families.pop();
        }
        return families.isEmpty() ? null : "IF family has no matching ENDIF.";
    }

    private static final class Frame {
        private final Integer rootId;
        private boolean elseSeen;

        private Frame(Integer rootId) {
            this.rootId = rootId;
        }
    }
}
