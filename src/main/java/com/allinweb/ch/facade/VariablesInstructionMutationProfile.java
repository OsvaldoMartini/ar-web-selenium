package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphInstructionFact;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphSnapshot;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.model.InstructionGraphMutationV3;
import com.allinweb.ch.model.InstructionGraphMutationV3.LayoutRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Server-side safety profile for the first Variables instruction-drag release.
 *
 * <p>React owns the authoring decision and submits the complete final layout. This class does not
 * choose a target, expand a connected group, or rewrite relationships. It only proves that the
 * submitted request is the narrow operation advertised by the Variables page: one eligible
 * variable command reinserted inside its current block, with no relationship mutation and no
 * structural boundary crossed.
 */
public final class VariablesInstructionMutationProfile {

    public static final String PROFILE_ID = "VARIABLES_INDIVIDUAL_ROW_V1";

    private static final Set<String> ELIGIBLE_VARIABLE_ACTIONS =
            Set.of("GET", "SET", "E", "CK", "PDF CHECK", "CSV CHECK");
    private static final Set<String> STRUCTURAL_ACTIONS =
            Set.of("IF", "ELSEIF", "ELSE", "ENDIF", "LOOP", "REFRESH_LOOP", "GOTO", "EXCEL GOTO");
    private static final Comparator<LayoutRow> ROW_ORDER =
            Comparator.comparingInt((LayoutRow row) -> row.blockOrderNumber())
                    .thenComparingInt(LayoutRow::instructionOrderNumber)
                    .thenComparingInt(LayoutRow::instructionId);

    public void validate(
            InstructionGraphMutationV3.Request request,
            GraphSnapshot authoritative)
            throws MutationRefusedException {
        if (request == null || authoritative == null) {
            refuse(
                    "VARIABLES_PROFILE_MISSING_STATE",
                    "The Variables instruction move requires an authoritative graph.");
        }
        if (request.mutationKind() != InstructionGraphMutationV3.MutationKind.ROW_MOVE) {
            refuse(
                    "VARIABLES_ROW_MOVE_ONLY",
                    "The Variables page accepts only individual instruction reordering.");
        }
        if (!request.instructionRelationPatches().isEmpty()
                || !request.variableBindingPatches().isEmpty()
                || !request.variableOwnerPatches().isEmpty()) {
            refuse(
                    "VARIABLES_PATCH_NOT_ALLOWED",
                    "Variables instruction reordering cannot modify relationships or variable ownership.");
        }
        if (!Objects.equals(request.baseGraphVersion(), authoritative.graphVersion())
                || request.graphRevision() == null
                || !request.graphRevision().trim().equals(authoritative.graphRevision())) {
            refuse(
                    "VARIABLES_GRAPH_CHANGED",
                    "The Variables instruction graph changed before the move was submitted.");
        }

        Map<Integer, LayoutRow> before = indexLayout(authoritative.layoutRows(), "authoritative");
        Map<Integer, LayoutRow> after = indexLayout(request.layoutRows(), "submitted");
        if (!before.keySet().equals(after.keySet())) {
            refuse(
                    "VARIABLES_LAYOUT_INCOMPLETE",
                    "The Variables move must submit the complete authoritative instruction layout.");
        }

        Map<Integer, GraphInstructionFact> facts = indexFacts(authoritative.instructionFacts());
        if (!before.keySet().equals(facts.keySet())) {
            refuse(
                    "VARIABLES_FACTS_INCOMPLETE",
                    "The authoritative Variables instruction facts are incomplete.");
        }
        verifyFactCoordinates(before, facts);
        verifyStableBlocks(before, after);
        verifyContiguousOrders(after);

        Integer draggedId = request.draggedInstructionId();
        LayoutRow sourceBefore = draggedId == null ? null : before.get(draggedId);
        LayoutRow sourceAfter = draggedId == null ? null : after.get(draggedId);
        GraphInstructionFact sourceFact = draggedId == null ? null : facts.get(draggedId);
        if (sourceBefore == null || sourceAfter == null || sourceFact == null) {
            refuse(
                    "VARIABLES_SOURCE_MISSING",
                    "The Variables move must identify one authoritative instruction.");
        }
        if (sourceFact.variableId() == null
                || sourceFact.variableId() <= 0
                || !ELIGIBLE_VARIABLE_ACTIONS.contains(
                        CommandRegistry.canonicalize(sourceFact.action()))) {
            refuse(
                    "VARIABLES_SOURCE_NOT_ELIGIBLE",
                    "Only GET, SET, E, CK, PDF CHECK, or CSV CHECK can be dragged in Variables.");
        }
        if (sourceBefore.blockId().intValue() != sourceAfter.blockId().intValue()) {
            refuse(
                    "VARIABLES_CROSS_BLOCK_NOT_READY",
                    "This Variables release moves one command only inside its current block.");
        }
        if (sourceBefore.instructionOrderNumber().intValue()
                == sourceAfter.instructionOrderNumber().intValue()) {
            refuse(
                    "VARIABLES_NO_CHANGE",
                    "The submitted Variables instruction order did not change.");
        }

        verifySingleReinsert(before, after, draggedId);
        verifyNoStructuralBoundaryCrossed(
                before, after, facts, draggedId, sourceBefore.blockId());
    }

    private Map<Integer, LayoutRow> indexLayout(
            List<LayoutRow> rows,
            String label)
            throws MutationRefusedException {
        if (rows == null || rows.isEmpty()) {
            refuse(
                    "VARIABLES_LAYOUT_INCOMPLETE",
                    "The " + label + " Variables instruction layout is empty.");
        }
        Map<Integer, LayoutRow> indexed = new LinkedHashMap<>();
        for (LayoutRow row : rows) {
            if (row == null
                    || row.instructionId() == null
                    || row.instructionId() <= 0
                    || row.blockId() == null
                    || row.blockId() <= 0
                    || row.blockOrderNumber() == null
                    || row.blockOrderNumber() <= 0
                    || row.instructionOrderNumber() == null
                    || row.instructionOrderNumber() <= 0
                    || indexed.putIfAbsent(row.instructionId(), row) != null) {
                refuse(
                        "VARIABLES_LAYOUT_INVALID",
                        "The " + label + " Variables instruction layout is invalid.");
            }
        }
        return indexed;
    }

    private Map<Integer, GraphInstructionFact> indexFacts(
            List<GraphInstructionFact> rows)
            throws MutationRefusedException {
        Map<Integer, GraphInstructionFact> indexed = new LinkedHashMap<>();
        if (rows == null) {
            refuse(
                    "VARIABLES_FACTS_INCOMPLETE",
                    "The authoritative Variables instruction facts are missing.");
        }
        for (GraphInstructionFact row : rows) {
            if (row == null
                    || row.instructionId() <= 0
                    || indexed.putIfAbsent(row.instructionId(), row) != null) {
                refuse(
                        "VARIABLES_FACTS_INCOMPLETE",
                        "The authoritative Variables instruction facts are invalid.");
            }
        }
        return indexed;
    }

    private void verifyFactCoordinates(
            Map<Integer, LayoutRow> layout,
            Map<Integer, GraphInstructionFact> facts)
            throws MutationRefusedException {
        for (LayoutRow row : layout.values()) {
            GraphInstructionFact fact = facts.get(row.instructionId());
            if (fact == null
                    || fact.blockId() != row.blockId()
                    || fact.blockOrderNumber() != row.blockOrderNumber()
                    || fact.instructionOrderNumber() != row.instructionOrderNumber()) {
                refuse(
                        "VARIABLES_FACTS_STALE",
                        "The authoritative Variables facts do not match the instruction layout.");
            }
        }
    }

    private void verifyStableBlocks(
            Map<Integer, LayoutRow> before,
            Map<Integer, LayoutRow> after)
            throws MutationRefusedException {
        for (Map.Entry<Integer, LayoutRow> entry : before.entrySet()) {
            LayoutRow current = entry.getValue();
            LayoutRow candidate = after.get(entry.getKey());
            if (!Objects.equals(current.blockId(), candidate.blockId())) {
                refuse(
                        "VARIABLES_CROSS_BLOCK_NOT_READY",
                        "This Variables release does not move instructions between blocks.");
            }
            if (!Objects.equals(
                    current.blockOrderNumber(), candidate.blockOrderNumber())) {
                refuse(
                        "VARIABLES_BLOCK_ORDER_LOCKED",
                        "Variables instruction dragging cannot reorder blocks.");
            }
        }
    }

    private void verifyContiguousOrders(
            Map<Integer, LayoutRow> layout)
            throws MutationRefusedException {
        Map<Integer, List<LayoutRow>> byBlock = rowsByBlock(layout.values());
        for (List<LayoutRow> rows : byBlock.values()) {
            rows.sort(ROW_ORDER);
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).instructionOrderNumber() != index + 1) {
                    refuse(
                            "VARIABLES_ORDER_INVALID",
                            "Variables instruction order must remain unique and contiguous.");
                }
            }
        }
    }

    private void verifySingleReinsert(
            Map<Integer, LayoutRow> before,
            Map<Integer, LayoutRow> after,
            int draggedId)
            throws MutationRefusedException {
        Map<Integer, List<LayoutRow>> beforeByBlock = rowsByBlock(before.values());
        Map<Integer, List<LayoutRow>> afterByBlock = rowsByBlock(after.values());
        for (Map.Entry<Integer, List<LayoutRow>> entry : beforeByBlock.entrySet()) {
            List<Integer> beforeIds = orderedIdsWithout(entry.getValue(), draggedId);
            List<Integer> afterIds = orderedIdsWithout(
                    afterByBlock.getOrDefault(entry.getKey(), List.of()),
                    draggedId);
            if (!beforeIds.equals(afterIds)) {
                refuse(
                        "VARIABLES_NOT_SINGLE_REINSERT",
                        "Variables dragging may reinsert only the selected instruction.");
            }
        }
    }

    private void verifyNoStructuralBoundaryCrossed(
            Map<Integer, LayoutRow> before,
            Map<Integer, LayoutRow> after,
            Map<Integer, GraphInstructionFact> facts,
            int draggedId,
            int sourceBlockId)
            throws MutationRefusedException {
        LayoutRow sourceBefore = before.get(draggedId);
        LayoutRow sourceAfter = after.get(draggedId);
        for (GraphInstructionFact fact : facts.values()) {
            if (fact.instructionId() == draggedId
                    || fact.blockId() != sourceBlockId
                    || !STRUCTURAL_ACTIONS.contains(
                            CommandRegistry.canonicalize(fact.action()))) {
                continue;
            }
            LayoutRow boundaryBefore = before.get(fact.instructionId());
            LayoutRow boundaryAfter = after.get(fact.instructionId());
            int oldSide = Integer.compare(
                    boundaryBefore.instructionOrderNumber(),
                    sourceBefore.instructionOrderNumber());
            int newSide = Integer.compare(
                    boundaryAfter.instructionOrderNumber(),
                    sourceAfter.instructionOrderNumber());
            if (oldSide != newSide) {
                refuse(
                        "VARIABLES_STRUCTURAL_BOUNDARY",
                        "The Variables move crosses a LOOP, IF, or navigation boundary.");
            }
        }
    }

    private Map<Integer, List<LayoutRow>> rowsByBlock(
            Iterable<LayoutRow> rows) {
        Map<Integer, List<LayoutRow>> byBlock = new HashMap<>();
        for (LayoutRow row : rows) {
            byBlock.computeIfAbsent(row.blockId(), ignored -> new ArrayList<>()).add(row);
        }
        return byBlock;
    }

    private List<Integer> orderedIdsWithout(
            List<LayoutRow> rows,
            int excludedId) {
        return rows.stream()
                .sorted(ROW_ORDER)
                .map(LayoutRow::instructionId)
                .filter(instructionId -> instructionId != excludedId)
                .toList();
    }

    private void refuse(String code, String message)
            throws MutationRefusedException {
        throw new MutationRefusedException(code, message);
    }
}
