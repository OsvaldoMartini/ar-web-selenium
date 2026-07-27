package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.UpdatedRow;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final InstructionMoveGroupService moveGroupService = new InstructionMoveGroupService();

    public String validate(List<InstructionLoad> current, List<UpdatedRow> updates) {
        if (updates == null || updates.isEmpty()) return "No instruction movement was supplied.";
        Map<Integer, ProposedRow> proposed = new HashMap<>();
        Map<Integer, Integer> blockOrders = new HashMap<>();
        for (InstructionLoad row : current) {
            if (row == null || row.getId() == null) continue;
            if (row.getBlockId() == null || row.getInstructionOrderNumber() == null) {
                return "The current instruction graph contains a missing block or order.";
            }
            if (row.getBlockOrderNumber() != null) {
                blockOrders.putIfAbsent(row.getBlockId(), row.getBlockOrderNumber());
            }
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
            row.blockOrder = blockOrders.get(update.getBlockId());
        }
        if (proposed.values().stream().noneMatch(ProposedRow::moved)) {
            return "ROW_MOVE did not change any instruction position.";
        }
        String conditionalMoveError = validateConditionalMovement(current, proposed);
        if (conditionalMoveError != null) return conditionalMoveError;
        String orderingError = validateBlockOrdering(proposed);
        if (orderingError != null) return orderingError;
        String excelGotoError = validateExcelGotoBlocks(proposed);
        if (excelGotoError != null) return excelGotoError;
        String parentError = validateParentRelationships(proposed);
        if (parentError != null) return parentError;
        String variableOrderError = validateVariableOrder(proposed);
        return variableOrderError == null
                ? validateLoopRelationships(proposed)
                : variableOrderError;
    }

    private String validateExcelGotoBlocks(Map<Integer, ProposedRow> proposed) {
        Map<Integer, List<ProposedRow>> blocks = new HashMap<>();
        proposed.values().forEach(row -> blocks.computeIfAbsent(row.blockId, ignored -> new ArrayList<>()).add(row));
        for (List<ProposedRow> rows : blocks.values()) {
            if (!rows.isEmpty() && rows.stream().allMatch(row -> "EXCEL GOTO".equalsIgnoreCase(row.action))) {
                return "EXCEL GOTO cannot be left as the only instruction in a block.";
            }
        }
        return null;
    }

    private String validateConditionalMovement(List<InstructionLoad> current, Map<Integer, ProposedRow> proposed) {
        for (InstructionLoad row : current) {
            if (row == null || row.getId() == null || !"IF".equals(row.getActions())) continue;
            List<InstructionLoad> group = moveGroupService.resolve(current, row.getId());
            String error = coherentGroupError(group, proposed, "Conditional families");
            if (error != null) return error;
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
            int firstOrder = Math.min(parent.originalOrder, loop.originalOrder);
            int lastOrder = Math.max(parent.originalOrder, loop.originalOrder);
            List<InstructionLoad> span = proposed.values().stream()
                    .filter(member -> member.originalBlockId == parent.originalBlockId
                            && member.originalOrder >= firstOrder && member.originalOrder <= lastOrder)
                    .sorted(Comparator.comparingInt(member -> member.originalOrder))
                    .map(ProposedRow::asOriginalInstruction)
                    .toList();
            String error = coherentGroupError(span, proposed, "Loop spans");
            if (error != null) return error;
        }
        return null;
    }

    private String coherentGroupError(List<InstructionLoad> group, Map<Integer, ProposedRow> proposed, String label) {
        List<ProposedRow> members = group.stream()
                .map(InstructionLoad::getId)
                .map(proposed::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (members.stream().noneMatch(ProposedRow::moved)) return null;
        if (members.stream().anyMatch(member -> !member.moved())) return label + " must move as one complete group.";
        int destinationBlock = members.get(0).blockId;
        int orderDelta = members.get(0).order - members.get(0).originalOrder;
        boolean coherent = members.stream().allMatch(member -> member.blockId == destinationBlock
                && member.order - member.originalOrder == orderDelta);
        return coherent ? null : label + " must preserve their relative order while moving.";
    }

    private String validateParentRelationships(Map<Integer, ProposedRow> proposed) {
        for (ProposedRow command : proposed.values()) {
            if (command.parentId == null || CONDITIONAL_BOUNDARIES.contains(command.action)
                    || LOOP_ACTIONS.contains(command.action)) continue;
            ProposedRow parent = proposed.get(command.parentId);
            // Fix B: only enforce Web-Field parent-block coherence for families TOUCHED by
            // this move. A pre-existing separation (or orphaned parentId) in a family that
            // did not move must not block an otherwise-valid, unrelated move — mirrors how
            // conditional/loop groups already skip when nothing in the group moved.
            boolean touched = command.moved() || (parent != null && parent.moved());
            if (!touched) continue;
            if (parent == null) return "A command references a missing parent instruction.";
            if (parent.blockId != command.blockId) {
                return "Commands with Web Field dependencies must remain in their parent block.";
            }
            if (parent.order >= command.order) {
                return "A Web Field must run before the commands that depend on it.";
            }
        }
        return null;
    }

    private String validateVariableOrder(Map<Integer, ProposedRow> proposed) {
        for (ProposedRow consumer : proposed.values()) {
            if (!VariableDefinitionPolicy.isConsumer(consumer.action) || consumer.variableId == null) continue;
            List<ProposedRow> producers = proposed.values().stream()
                    .filter(row -> VariableDefinitionPolicy.isProducer(row.action))
                    .filter(row -> java.util.Objects.equals(row.variableId, consumer.variableId))
                    .toList();
            if (producers.isEmpty()) continue;
            boolean touched =
                    consumer.moved() || producers.stream().anyMatch(ProposedRow::moved);
            if (!touched) continue;
            boolean producerRunsFirst =
                    producers.stream().anyMatch(producer -> runsBeforeOrOrderUnknown(producer, consumer));
            if (!producerRunsFirst) {
                return "GET must run before "
                        + CommandRegistry.canonicalize(consumer.action)
                        + " instruction #"
                        + consumer.id
                        + " that reads variable #"
                        + consumer.variableId
                        + ".";
            }
        }
        return null;
    }

    private boolean runsBeforeOrOrderUnknown(ProposedRow producer, ProposedRow consumer) {
        if (producer.blockId == consumer.blockId) return producer.order < consumer.order;
        if (producer.blockOrder == null || consumer.blockOrder == null) {
            // Some legacy/component snapshots do not yet transport block order. Preserve those
            // graphs until the aggregate migration can backfill authoritative execution ranks.
            return true;
        }
        return producer.blockOrder < consumer.blockOrder;
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
        private final Integer variableId;
        private final int originalBlockId;
        private final int originalOrder;
        private final Integer originalBlockOrder;
        private int blockId;
        private int order;
        private Integer blockOrder;

        private ProposedRow(
                int id,
                String action,
                Integer parentId,
                Integer variableId,
                int blockId,
                Integer blockOrder,
                int order) {
            this.id = id;
            this.action = action;
            this.parentId = parentId;
            this.variableId = variableId;
            this.originalBlockId = blockId;
            this.originalOrder = order;
            this.originalBlockOrder = blockOrder;
            this.blockId = blockId;
            this.order = order;
            this.blockOrder = blockOrder;
        }

        private static ProposedRow from(InstructionLoad row) {
            return new ProposedRow(
                    row.getId(),
                    row.getActions(),
                    row.getParentId(),
                    row.getVariableId(),
                    row.getBlockId(),
                    row.getBlockOrderNumber(),
                    row.getInstructionOrderNumber());
        }

        private InstructionLoad asInstruction() {
            InstructionLoad row = new InstructionLoad();
            row.setId(id);
            row.setActions(action);
            row.setParentId(parentId);
            row.setVariableId(variableId);
            row.setBlockId(blockId);
            row.setBlockOrderNumber(blockOrder);
            row.setInstructionOrderNumber(order);
            return row;
        }

        private InstructionLoad asOriginalInstruction() {
            InstructionLoad row = new InstructionLoad();
            row.setId(id);
            row.setActions(action);
            row.setParentId(parentId);
            row.setVariableId(variableId);
            row.setBlockId(originalBlockId);
            row.setBlockOrderNumber(originalBlockOrder);
            row.setInstructionOrderNumber(originalOrder);
            return row;
        }

        private boolean moved() {
            return blockId != originalBlockId || order != originalOrder;
        }
    }
}
