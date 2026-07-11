package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.UpdatedRow;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates a proposed partial ROW_MOVE against the complete current instruction graph. */
public final class InstructionMoveValidator {
    private static final Set<String> CONDITIONAL_BOUNDARIES = Set.of("IF", "ELSEIF", "ELSE", "ENDIF");
    private static final Set<String> LOOP_ACTIONS = Set.of("LOOP", "REFRESH_LOOP");
    private final ConditionalGraphValidator conditionalValidator = new ConditionalGraphValidator();

    public String validate(List<InstructionLoad> current, List<UpdatedRow> updates) {
        if (updates == null || updates.isEmpty()) return "No instruction movement was supplied.";
        Map<Integer, InstructionLoad> originals = new HashMap<>();
        Map<Integer, ProposedRow> proposed = new HashMap<>();
        for (InstructionLoad row : current) {
            if (row == null || row.getId() == null) continue;
            if (row.getBlockId() == null || row.getInstructionOrderNumber() == null) {
                return "The current instruction graph contains a missing block or order.";
            }
            originals.put(row.getId(), row);
            proposed.put(row.getId(), ProposedRow.from(row));
        }
        Set<Integer> submittedIds = new HashSet<>();
        for (UpdatedRow update : updates) {
            if (update == null || update.getInstructionId() == null || !submittedIds.add(update.getInstructionId())) {
                return "ROW_MOVE contains a missing or duplicate instruction ID.";
            }
            ProposedRow row = proposed.get(update.getInstructionId());
            if (row == null) return "ROW_MOVE references an instruction outside the current owner.";
            if (update.getBlockId() == null || update.getBlockId() < 1
                    || update.getInstructionOrderNumber() == null || update.getInstructionOrderNumber() < 1) {
                return "ROW_MOVE contains an invalid block or instruction order.";
            }
            row.blockId = update.getBlockId();
            row.order = update.getInstructionOrderNumber();
        }
        String conditionalMoveError = validateConditionalMovement(current, originals, proposed);
        if (conditionalMoveError != null) return conditionalMoveError;
        String orderingError = validateBlockOrdering(proposed);
        if (orderingError != null) return orderingError;
        return validateLoopRelationships(proposed);
    }

    private String validateConditionalMovement(List<InstructionLoad> current, Map<Integer, InstructionLoad> originals,
            Map<Integer, ProposedRow> proposed) {
        Map<Integer, List<InstructionLoad>> blocks = groupCurrentBlocks(current);
        for (List<InstructionLoad> rows : blocks.values()) {
            Deque<Integer> roots = new ArrayDeque<>();
            for (InstructionLoad row : rows) {
                if ("IF".equals(row.getActions()) && row.getId() != null) roots.push(row.getId());
                if (!roots.isEmpty() && row.getId() != null) {
                    ProposedRow moved = proposed.get(row.getId());
                    InstructionLoad original = originals.get(row.getId());
                    if (moved != null && original != null && moved.blockId != original.getBlockId()) {
                        return "Instructions inside an IF family cannot move to another block independently.";
                    }
                }
                if (CONDITIONAL_BOUNDARIES.contains(row.getActions()) && row.getId() != null) {
                    ProposedRow moved = proposed.get(row.getId());
                    if (moved != null && moved.order != row.getInstructionOrderNumber()) {
                        return "Conditional boundaries cannot be reordered independently.";
                    }
                }
                if ("ENDIF".equals(row.getActions()) && !roots.isEmpty()) roots.pop();
            }
        }
        return null;
    }

    private String validateBlockOrdering(Map<Integer, ProposedRow> proposed) {
        Map<Integer, List<ProposedRow>> blocks = new HashMap<>();
        proposed.values().forEach(row -> blocks.computeIfAbsent(row.blockId, ignored -> new ArrayList<>()).add(row));
        for (List<ProposedRow> rows : blocks.values()) {
            rows.sort(Comparator.comparingInt(row -> row.order));
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).order != index + 1) return "ROW_MOVE leaves duplicate or missing instruction orders.";
            }
            List<InstructionLoad> graphRows = rows.stream().map(ProposedRow::asInstruction).toList();
            String graphError = conditionalValidator.validate(graphRows);
            if (graphError != null) return "ROW_MOVE creates an invalid conditional graph: " + graphError;
        }
        return null;
    }

    private String validateLoopRelationships(Map<Integer, ProposedRow> proposed) {
        for (ProposedRow loop : proposed.values()) {
            if (!LOOP_ACTIONS.contains(loop.action)) continue;
            ProposedRow parent = loop.parentId == null ? null : proposed.get(loop.parentId);
            if (parent == null || parent.blockId != loop.blockId) {
                return "LOOP and REFRESH_LOOP must remain in the same block as their parent Web Field.";
            }
        }
        return null;
    }

    private Map<Integer, List<InstructionLoad>> groupCurrentBlocks(List<InstructionLoad> current) {
        Map<Integer, List<InstructionLoad>> blocks = new HashMap<>();
        for (InstructionLoad row : current) {
            if (row != null && row.getBlockId() != null) blocks.computeIfAbsent(row.getBlockId(), ignored -> new ArrayList<>()).add(row);
        }
        blocks.values().forEach(rows -> rows.sort(Comparator.comparingInt(row -> row.getInstructionOrderNumber() == null
                ? Integer.MAX_VALUE : row.getInstructionOrderNumber())));
        return blocks;
    }

    private static final class ProposedRow {
        private final int id;
        private final String action;
        private final Integer parentId;
        private int blockId;
        private int order;

        private ProposedRow(int id, String action, Integer parentId, int blockId, int order) {
            this.id = id;
            this.action = action;
            this.parentId = parentId;
            this.blockId = blockId;
            this.order = order;
        }

        private static ProposedRow from(InstructionLoad row) {
            return new ProposedRow(row.getId(), row.getActions(), row.getParentId(), row.getBlockId(), row.getInstructionOrderNumber());
        }

        private InstructionLoad asInstruction() {
            InstructionLoad row = new InstructionLoad();
            row.setId(id);
            row.setActions(action);
            row.setParentId(parentId);
            row.setBlockId(blockId);
            row.setInstructionOrderNumber(order);
            return row;
        }
    }
}
