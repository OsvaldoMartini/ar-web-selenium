package com.allinweb.ch.facade;

import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphInstructionFact;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.GraphSnapshot;
import com.allinweb.ch.facade.BotJobGraphMutationTransaction.MutationRefusedException;
import com.allinweb.ch.model.InstructionGraphMutationV3;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationPatch;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationState;
import com.allinweb.ch.model.InstructionGraphMutationV3.LayoutRow;
import com.allinweb.ch.model.InstructionGraphMutationV3.PatchOperation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Server-side safety profile for an explicitly planned Variables cross-block consumer move.
 *
 * <p>React owns the destination, insertion point, and explicit disconnect/reconnect decision.
 * Java does not select or infer any of those authoring choices. This profile only proves that the
 * complete submitted graph is exactly one eligible consumer reinserted into another structurally
 * flat block and that its one relationship patch is safe against authoritative database facts.
 */
public final class VariablesCrossBlockInstructionMutationProfile {

    public static final String PROFILE_ID = "VARIABLES_INDIVIDUAL_CROSS_BLOCK_V1";

    private static final Set<String> ELIGIBLE_CONSUMER_ACTIONS =
            Set.of("E", "CK", "PDF CHECK", "CSV CHECK");
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
                    "VARIABLES_CROSS_PROFILE_MISSING_STATE",
                    "The Variables cross-block move requires an authoritative graph.");
        }
        if (request.mutationKind() != InstructionGraphMutationV3.MutationKind.ROW_MOVE) {
            refuse(
                    "VARIABLES_CROSS_ROW_MOVE_ONLY",
                    "The Variables cross-block profile accepts only one instruction move.");
        }
        if (!request.variableBindingPatches().isEmpty()
                || !request.variableOwnerPatches().isEmpty()) {
            refuse(
                    "VARIABLES_CROSS_VARIABLE_PATCH_NOT_ALLOWED",
                    "A Variables cross-block move cannot modify variable binding or ownership.");
        }
        if (!Objects.equals(request.baseGraphVersion(), authoritative.graphVersion())
                || request.graphRevision() == null
                || !request.graphRevision().trim().equals(authoritative.graphRevision())) {
            refuse(
                    "VARIABLES_CROSS_GRAPH_CHANGED",
                    "The Variables instruction graph changed before the move was submitted.");
        }

        Map<Integer, LayoutRow> before = indexLayout(authoritative.layoutRows(), "authoritative");
        Map<Integer, LayoutRow> after = indexLayout(request.layoutRows(), "submitted");
        if (!before.keySet().equals(after.keySet())) {
            refuse(
                    "VARIABLES_CROSS_LAYOUT_INCOMPLETE",
                    "The Variables cross-block move must submit the complete instruction layout.");
        }

        Map<Integer, GraphInstructionFact> facts = indexFacts(authoritative.instructionFacts());
        if (!before.keySet().equals(facts.keySet())) {
            refuse(
                    "VARIABLES_CROSS_FACTS_INCOMPLETE",
                    "The authoritative Variables instruction facts are incomplete.");
        }
        verifyFactCoordinates(before, facts);
        verifyContiguousOrders(before);
        Map<Integer, Integer> blockOrders = authoritativeBlockOrders(before);
        verifyFixedBlocksAndOrders(before, after, blockOrders, request.draggedInstructionId());
        verifyContiguousOrders(after);

        Integer draggedId = request.draggedInstructionId();
        LayoutRow sourceBefore = draggedId == null ? null : before.get(draggedId);
        LayoutRow sourceAfter = draggedId == null ? null : after.get(draggedId);
        GraphInstructionFact sourceFact = draggedId == null ? null : facts.get(draggedId);
        if (sourceBefore == null || sourceAfter == null || sourceFact == null) {
            refuse(
                    "VARIABLES_CROSS_SOURCE_MISSING",
                    "The Variables cross-block move must identify one authoritative instruction.");
        }
        if (sourceFact.variableId() == null
                || sourceFact.variableId() <= 0
                || !ELIGIBLE_CONSUMER_ACTIONS.contains(
                        CommandRegistry.canonicalize(sourceFact.action()))) {
            refuse(
                    "VARIABLES_CROSS_SOURCE_NOT_ELIGIBLE",
                    "Only E, CK, PDF CHECK, or CSV CHECK consumers can move between blocks.");
        }
        if (Objects.equals(sourceBefore.blockId(), sourceAfter.blockId())) {
            refuse(
                    "VARIABLES_CROSS_BLOCK_REQUIRED",
                    "This Variables profile requires a move into another block.");
        }

        verifySingleCrossBlockReinsert(before, after, draggedId);
        verifySourceRemainsPopulated(after, sourceBefore.blockId());
        verifyHealthySourceRelation(sourceFact, sourceBefore, before, facts);
        verifyFlatBlocks(facts, sourceBefore.blockId(), sourceAfter.blockId());
        verifyNoDirectDependants(facts, draggedId);
        verifyRelationshipPatch(
                request.instructionRelationPatches(),
                sourceFact,
                sourceAfter,
                after,
                facts);
    }

    private Map<Integer, LayoutRow> indexLayout(List<LayoutRow> rows, String label)
            throws MutationRefusedException {
        if (rows == null || rows.isEmpty()) {
            refuse(
                    "VARIABLES_CROSS_LAYOUT_INCOMPLETE",
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
                        "VARIABLES_CROSS_LAYOUT_INVALID",
                        "The " + label + " Variables instruction layout is invalid.");
            }
        }
        return indexed;
    }

    private Map<Integer, GraphInstructionFact> indexFacts(List<GraphInstructionFact> rows)
            throws MutationRefusedException {
        Map<Integer, GraphInstructionFact> indexed = new LinkedHashMap<>();
        if (rows == null) {
            refuse(
                    "VARIABLES_CROSS_FACTS_INCOMPLETE",
                    "The authoritative Variables instruction facts are missing.");
        }
        for (GraphInstructionFact row : rows) {
            if (row == null
                    || row.instructionId() <= 0
                    || indexed.putIfAbsent(row.instructionId(), row) != null) {
                refuse(
                        "VARIABLES_CROSS_FACTS_INCOMPLETE",
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
                        "VARIABLES_CROSS_FACTS_STALE",
                        "The authoritative Variables facts do not match the instruction layout.");
            }
        }
    }

    private Map<Integer, Integer> authoritativeBlockOrders(
            Map<Integer, LayoutRow> before)
            throws MutationRefusedException {
        Map<Integer, Integer> blockOrders = new LinkedHashMap<>();
        for (LayoutRow row : before.values()) {
            Integer existing = blockOrders.putIfAbsent(row.blockId(), row.blockOrderNumber());
            if (existing != null && !existing.equals(row.blockOrderNumber())) {
                refuse(
                        "VARIABLES_CROSS_BLOCK_ORDER_INVALID",
                        "The authoritative Variables block order is inconsistent.");
            }
        }
        return blockOrders;
    }

    private void verifyFixedBlocksAndOrders(
            Map<Integer, LayoutRow> before,
            Map<Integer, LayoutRow> after,
            Map<Integer, Integer> blockOrders,
            Integer draggedId)
            throws MutationRefusedException {
        for (Map.Entry<Integer, LayoutRow> entry : after.entrySet()) {
            LayoutRow candidate = entry.getValue();
            Integer authoritativeOrder = blockOrders.get(candidate.blockId());
            if (authoritativeOrder == null
                    || !authoritativeOrder.equals(candidate.blockOrderNumber())) {
                refuse(
                        "VARIABLES_CROSS_BLOCK_ORDER_LOCKED",
                        "Variables instruction dragging cannot add or reorder blocks.");
            }
            if (!Objects.equals(entry.getKey(), draggedId)
                    && !Objects.equals(
                            before.get(entry.getKey()).blockId(), candidate.blockId())) {
                refuse(
                        "VARIABLES_CROSS_NOT_SINGLE_REINSERT",
                        "Only the selected Variables instruction may change blocks.");
            }
        }
    }

    private void verifyContiguousOrders(Map<Integer, LayoutRow> layout)
            throws MutationRefusedException {
        for (List<LayoutRow> rows : rowsByBlock(layout.values()).values()) {
            rows.sort(ROW_ORDER);
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).instructionOrderNumber() != index + 1) {
                    refuse(
                            "VARIABLES_CROSS_ORDER_INVALID",
                            "Variables instruction order must remain unique and contiguous.");
                }
            }
        }
    }

    private void verifySingleCrossBlockReinsert(
            Map<Integer, LayoutRow> before,
            Map<Integer, LayoutRow> after,
            int draggedId)
            throws MutationRefusedException {
        Map<Integer, List<LayoutRow>> beforeByBlock = rowsByBlock(before.values());
        Map<Integer, List<LayoutRow>> afterByBlock = rowsByBlock(after.values());
        for (Integer blockId : beforeByBlock.keySet()) {
            List<Integer> beforeIds =
                    orderedIdsWithout(beforeByBlock.get(blockId), draggedId);
            List<Integer> afterIds = orderedIdsWithout(
                    afterByBlock.getOrDefault(blockId, List.of()), draggedId);
            if (!beforeIds.equals(afterIds)) {
                refuse(
                        "VARIABLES_CROSS_NOT_SINGLE_REINSERT",
                        "Variables cross-block dragging may reinsert only the selected instruction.");
            }
        }
    }

    private void verifyHealthySourceRelation(
            GraphInstructionFact sourceFact,
            LayoutRow sourceBefore,
            Map<Integer, LayoutRow> before,
            Map<Integer, GraphInstructionFact> facts)
            throws MutationRefusedException {
        Integer parentId = sourceFact.parentId();
        Integer parentBlockId = sourceFact.parentBlockId();
        LayoutRow parentRow = parentId == null ? null : before.get(parentId);
        GraphInstructionFact parentFact =
                parentId == null ? null : facts.get(parentId);
        if (parentId == null
                || parentId <= 0
                || parentBlockId == null
                || parentBlockId <= 0
                || parentRow == null
                || parentFact == null
                || CommandRegistry.isSpecialAction(parentFact.action())
                || !Objects.equals(parentBlockId, sourceBefore.blockId())
                || !Objects.equals(parentRow.blockId(), sourceBefore.blockId())
                || parentRow.instructionOrderNumber()
                        >= sourceBefore.instructionOrderNumber()) {
            refuse(
                    "VARIABLES_CROSS_SOURCE_RELATION_INVALID",
                    "The selected Variables consumer must start connected to an earlier parent in its source block.");
        }
    }

    private void verifySourceRemainsPopulated(
            Map<Integer, LayoutRow> after,
            int sourceBlockId)
            throws MutationRefusedException {
        boolean populated = after.values().stream()
                .anyMatch(row -> row.blockId() == sourceBlockId);
        if (!populated) {
            refuse(
                    "VARIABLES_CROSS_EMPTY_SOURCE_BLOCK",
                    "Moving the last instruction out of a Variables block is not available yet.");
        }
    }

    private void verifyFlatBlocks(
            Map<Integer, GraphInstructionFact> facts,
            int sourceBlockId,
            int destinationBlockId)
            throws MutationRefusedException {
        for (GraphInstructionFact fact : facts.values()) {
            if ((fact.blockId() == sourceBlockId || fact.blockId() == destinationBlockId)
                    && STRUCTURAL_ACTIONS.contains(
                            CommandRegistry.canonicalize(fact.action()))) {
                refuse(
                        "VARIABLES_CROSS_STRUCTURAL_BLOCK",
                        "Cross-block Variables dragging requires structurally flat source and destination blocks.");
            }
        }
    }

    private void verifyNoDirectDependants(
            Map<Integer, GraphInstructionFact> facts,
            int draggedId)
            throws MutationRefusedException {
        boolean referenced = facts.values().stream()
                .anyMatch(fact -> fact.instructionId() != draggedId
                        && Objects.equals(fact.parentId(), draggedId));
        if (referenced) {
            refuse(
                    "VARIABLES_CROSS_SOURCE_HAS_DEPENDANTS",
                    "The selected Variables instruction is still the parent of another instruction.");
        }
    }

    private void verifyRelationshipPatch(
            List<InstructionRelationPatch> patches,
            GraphInstructionFact sourceFact,
            LayoutRow sourceAfter,
            Map<Integer, LayoutRow> after,
            Map<Integer, GraphInstructionFact> facts)
            throws MutationRefusedException {
        if (patches == null || patches.size() != 1) {
            refuse(
                    "VARIABLES_CROSS_RELATION_PATCH_REQUIRED",
                    "A Variables cross-block move requires exactly one relationship decision.");
        }
        InstructionRelationPatch patch = patches.get(0);
        InstructionRelationState current = new InstructionRelationState(
                sourceFact.parentId(), sourceFact.parentBlockId());
        if (patch == null
                || !Objects.equals(patch.instructionId(), sourceFact.instructionId())
                || patch.relationKind()
                        != InstructionGraphMutationV3.InstructionRelationKind.ELEMENT_TARGET
                || (patch.operation() != PatchOperation.CLEAR
                        && patch.operation() != PatchOperation.SET)
                || patch.expected() == null
                || patch.replacement() == null
                || !current.equals(patch.expected())) {
            refuse(
                    "VARIABLES_CROSS_RELATION_PATCH_INVALID",
                    "The Variables cross-block relationship decision does not match the authoritative row.");
        }

        InstructionRelationState replacement = patch.replacement();
        if (patch.operation() == PatchOperation.CLEAR) {
            if (replacement.parentId() != null || replacement.parentBlockId() != null) {
                refuse(
                        "VARIABLES_CROSS_CLEAR_INVALID",
                        "Disconnect requires explicit null parentId and parentBlockId values.");
            }
            return;
        }

        Integer targetId = replacement.parentId();
        Integer targetBlockId = replacement.parentBlockId();
        LayoutRow targetRow = targetId == null ? null : after.get(targetId);
        GraphInstructionFact targetFact = targetId == null ? null : facts.get(targetId);
        if (targetId == null
                || targetId <= 0
                || targetBlockId == null
                || targetBlockId <= 0
                || targetRow == null
                || targetFact == null
                || !Objects.equals(targetRow.blockId(), sourceAfter.blockId())
                || !Objects.equals(targetBlockId, sourceAfter.blockId())
                || targetRow.instructionOrderNumber()
                        >= sourceAfter.instructionOrderNumber()
                || CommandRegistry.isSpecialAction(targetFact.action())) {
            refuse(
                    "VARIABLES_CROSS_TARGET_INVALID",
                    "Reconnect requires an earlier Web Field in the destination block.");
        }
    }

    private Map<Integer, List<LayoutRow>> rowsByBlock(Iterable<LayoutRow> rows) {
        Map<Integer, List<LayoutRow>> byBlock = new HashMap<>();
        for (LayoutRow row : rows) {
            byBlock.computeIfAbsent(row.blockId(), ignored -> new ArrayList<>()).add(row);
        }
        return byBlock;
    }

    private List<Integer> orderedIdsWithout(List<LayoutRow> rows, int excludedId) {
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
