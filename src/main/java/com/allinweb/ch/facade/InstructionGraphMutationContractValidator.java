package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionGraphMutationV3;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationKind;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationPatch;
import com.allinweb.ch.model.InstructionGraphMutationV3.InstructionRelationState;
import com.allinweb.ch.model.InstructionGraphMutationV3.LayoutRow;
import com.allinweb.ch.model.InstructionGraphMutationV3.NullableId;
import com.allinweb.ch.model.InstructionGraphMutationV3.PatchOperation;
import com.allinweb.ch.model.InstructionGraphMutationV3.VariableBindingPatch;
import com.allinweb.ch.model.InstructionGraphMutationV3.VariableOwnerPatch;
import com.allinweb.ch.model.InstructionGraphMutationV3.WorkspaceKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure structural validator and normalizer for the additive instruction graph mutation contract.
 *
 * <p>This class deliberately has no database, WebSocket, action registry, conditional parser,
 * relationship-group resolver, or persistence dependency. React supplies the exact layout and
 * relationship intent. The future connection-scoped transaction can consume the normalized result
 * after it loads an authoritative owner graph.
 */
public final class InstructionGraphMutationContractValidator {

    public Validation validateAndNormalize(
            InstructionGraphMutationV3.Request request,
            OwnerGraph authoritativeGraph) {
        if (request == null) {
            return failure(ErrorCode.MISSING_REQUEST, "An instruction graph mutation request is required.");
        }
        if (authoritativeGraph == null || authoritativeGraph.scope() == null) {
            return failure(ErrorCode.MISSING_OWNER_GRAPH, "An authoritative owner graph is required.");
        }
        if (!Integer.valueOf(InstructionGraphMutationV3.CONTRACT_VERSION)
                .equals(request.contractVersion())) {
            return failure(
                    ErrorCode.UNSUPPORTED_CONTRACT,
                    "Instruction graph mutation contract version 3 is required.");
        }
        if (request.mutationKind() == null) {
            return failure(ErrorCode.INVALID_REQUEST, "A mutation kind is required.");
        }
        if (request.requestId() == null || request.requestId().isBlank()) {
            return failure(ErrorCode.INVALID_REQUEST, "A non-blank request ID is required.");
        }
        if (request.baseGraphVersion() == null || request.baseGraphVersion() < 0) {
            return failure(ErrorCode.INVALID_REQUEST, "A non-negative base graph version is required.");
        }
        if (request.graphRevision() == null || request.graphRevision().isBlank()) {
            return failure(ErrorCode.INVALID_REQUEST, "A graph revision is required.");
        }
        if (request.workspaceEpoch() == null || request.workspaceEpoch() <= 0) {
            return failure(ErrorCode.INVALID_REQUEST, "A positive workspace epoch is required.");
        }

        GraphIndexResult indexResult = index(authoritativeGraph);
        if (indexResult.error() != null) return Validation.failed(indexResult.error());
        GraphIndex index = indexResult.index();

        ValidationError ownerError = validateOwnerAndRevision(request, authoritativeGraph.scope());
        if (ownerError != null) return Validation.failed(ownerError);

        LayoutResult layoutResult = validateLayout(request.layoutRows(), index);
        if (layoutResult.error() != null) return Validation.failed(layoutResult.error());
        Map<Integer, LayoutRow> layoutByInstruction = layoutResult.layoutByInstruction();

        if (request.mutationKind() == InstructionGraphMutationV3.MutationKind.ROW_MOVE) {
            Integer draggedId = request.draggedInstructionId();
            if (draggedId == null || !index.instructionsById().containsKey(draggedId)) {
                return failure(
                        ErrorCode.INVALID_DRAGGED_INSTRUCTION,
                        "ROW_MOVE must identify one instruction owned by the authoritative graph.");
            }
        } else if (request.draggedInstructionId() != null
                && !index.instructionsById().containsKey(request.draggedInstructionId())) {
            return failure(
                    ErrorCode.INVALID_DRAGGED_INSTRUCTION,
                    "The diagnostic dragged instruction is outside the authoritative owner.");
        }

        Map<Integer, InstructionRelationState> finalRelations = new LinkedHashMap<>();
        Map<Integer, Integer> finalVariableBindings = new LinkedHashMap<>();
        for (StoredInstruction instruction : index.instructionsById().values()) {
            finalRelations.put(
                    instruction.id(),
                    new InstructionRelationState(
                            instruction.parentId(),
                            instruction.parentBlockId()));
            finalVariableBindings.put(instruction.id(), instruction.variableId());
        }

        PatchResult relationResult = validateInstructionRelationPatches(
                request.instructionRelationPatches(),
                index,
                layoutByInstruction,
                finalRelations);
        if (relationResult.error() != null) return Validation.failed(relationResult.error());

        VariableBindingResult bindingResult = validateVariableBindingPatches(
                request.variableBindingPatches(),
                index,
                finalVariableBindings);
        if (bindingResult.error() != null) return Validation.failed(bindingResult.error());

        Map<Integer, Integer> finalVariableOwners = new LinkedHashMap<>();
        index.variablesById().values().forEach(variable ->
                finalVariableOwners.put(variable.id(), variable.instructionId()));
        VariableOwnerResult ownerResult = validateVariableOwnerPatches(
                request.variableOwnerPatches(),
                index,
                finalVariableOwners);
        if (ownerResult.error() != null) return Validation.failed(ownerResult.error());

        ValidationError finalStateError = validateFinalState(
                index,
                layoutByInstruction,
                finalRelations,
                finalVariableBindings,
                finalVariableOwners);
        if (finalStateError != null) return Validation.failed(finalStateError);

        List<NormalizedInstruction> normalizedInstructions = layoutResult.orderedLayout().stream()
                .map(row -> {
                    StoredInstruction stored = index.instructionsById().get(row.instructionId());
                    InstructionRelationState relation =
                            finalRelations.get(row.instructionId());
                    return new NormalizedInstruction(
                            row.instructionId(),
                            row.blockId(),
                            row.blockOrderNumber(),
                            row.instructionOrderNumber(),
                            stored.relationKind(),
                            relation.parentId(),
                            relation.parentBlockId(),
                            finalVariableBindings.get(row.instructionId()));
                })
                .toList();
        List<NormalizedVariable> normalizedVariables = index.variablesById().values().stream()
                .sorted(Comparator.comparingInt(StoredVariable::id))
                .map(variable ->
                        new NormalizedVariable(variable.id(), finalVariableOwners.get(variable.id())))
                .toList();

        return Validation.success(new NormalizedMutation(
                request.contractVersion(),
                request.mutationKind(),
                request.requestId().trim(),
                request.baseGraphVersion(),
                request.graphRevision().trim(),
                request.workspaceEpoch(),
                authoritativeGraph.scope(),
                request.draggedInstructionId(),
                layoutResult.orderedLayout(),
                relationResult.normalizedPatches(),
                bindingResult.normalizedPatches(),
                ownerResult.normalizedPatches(),
                normalizedInstructions,
                normalizedVariables));
    }

    private ValidationError validateOwnerAndRevision(
            InstructionGraphMutationV3.Request request,
            OwnerScope scope) {
        InstructionGraphMutationV3.OwnerAssertion assertion = request.ownerAssertion();
        if (assertion == null
                || assertion.workspaceKind() != scope.workspaceKind()
                || !Objects.equals(assertion.homeBankingId(), scope.homeBankingId())
                || !Objects.equals(assertion.botJobId(), scope.botJobId())) {
            return error(
                    ErrorCode.OWNER_MISMATCH,
                    "The owner assertion does not match the authoritative workspace.");
        }
        if (!Objects.equals(request.workspaceEpoch(), scope.workspaceEpoch())) {
            return error(
                    ErrorCode.WORKSPACE_EPOCH_MISMATCH,
                    "The workspace epoch changed before the mutation was submitted.");
        }
        if (!Objects.equals(request.baseGraphVersion(), scope.graphVersion())) {
            return error(
                    ErrorCode.GRAPH_VERSION_MISMATCH,
                    "The database graph version changed before the mutation was submitted.");
        }
        if (!request.graphRevision().trim().equals(scope.graphRevision())) {
            return error(
                    ErrorCode.GRAPH_REVISION_MISMATCH,
                    "The instruction graph revision changed before the mutation was submitted.");
        }
        return null;
    }

    private GraphIndexResult index(OwnerGraph graph) {
        OwnerScope scope = graph.scope();
        if (scope.workspaceKind() == null
                || scope.homeBankingId() == null
                || scope.homeBankingId() <= 0
                || scope.workspaceEpoch() == null
                || scope.workspaceEpoch() <= 0
                || scope.graphVersion() == null
                || scope.graphVersion() < 0
                || scope.graphRevision() == null
                || scope.graphRevision().isBlank()) {
            return GraphIndexResult.failed(error(
                    ErrorCode.INVALID_OWNER_GRAPH,
                    "The authoritative owner scope is incomplete."));
        }
        if (scope.workspaceKind() == WorkspaceKind.BOT_JOB
                && (scope.botJobId() == null || scope.botJobId() <= 0)) {
            return GraphIndexResult.failed(error(
                    ErrorCode.INVALID_OWNER_GRAPH,
                    "A Bot Job owner requires a positive Bot Job ID."));
        }
        if (scope.workspaceKind() == WorkspaceKind.COMPONENT && scope.botJobId() != null) {
            return GraphIndexResult.failed(error(
                    ErrorCode.INVALID_OWNER_GRAPH,
                    "A Component owner must not carry a Bot Job ID."));
        }

        Map<Integer, StoredBlock> blocksById = new LinkedHashMap<>();
        Set<Integer> blockOrders = new HashSet<>();
        for (StoredBlock block : graph.blocks()) {
            if (block == null
                    || block.id() <= 0
                    || block.order() <= 0
                    || blocksById.putIfAbsent(block.id(), block) != null
                    || !blockOrders.add(block.order())) {
                return GraphIndexResult.failed(error(
                        ErrorCode.INVALID_OWNER_GRAPH,
                        "The authoritative block catalog contains an invalid or duplicate block."));
            }
        }

        Map<Integer, StoredInstruction> instructionsById = new LinkedHashMap<>();
        for (StoredInstruction instruction : graph.instructions()) {
            if (instruction == null
                    || instruction.id() <= 0
                    || instruction.blockId() <= 0
                    || instruction.order() <= 0
                    || !blocksById.containsKey(instruction.blockId())
                    || instructionsById.putIfAbsent(instruction.id(), instruction) != null) {
                return GraphIndexResult.failed(error(
                        ErrorCode.INVALID_OWNER_GRAPH,
                        "The authoritative instruction catalog contains an invalid or duplicate row."));
            }
        }

        Map<Integer, StoredVariable> variablesById = new LinkedHashMap<>();
        for (StoredVariable variable : graph.variables()) {
            if (variable == null
                    || variable.id() <= 0
                    || variablesById.putIfAbsent(variable.id(), variable) != null) {
                return GraphIndexResult.failed(error(
                        ErrorCode.INVALID_OWNER_GRAPH,
                        "The authoritative variable catalog contains an invalid or duplicate variable."));
            }
        }
        return GraphIndexResult.success(new GraphIndex(
                Map.copyOf(blocksById),
                Map.copyOf(instructionsById),
                Map.copyOf(variablesById)));
    }

    private LayoutResult validateLayout(
            List<LayoutRow> submitted,
            GraphIndex index) {
        if (submitted == null || submitted.size() != index.instructionsById().size()) {
            return LayoutResult.failed(error(
                    ErrorCode.INCOMPLETE_LAYOUT,
                    "The layout must contain every instruction owned by the authoritative graph."));
        }

        Map<Integer, LayoutRow> layoutByInstruction = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> ordersByBlock = new HashMap<>();
        for (LayoutRow row : submitted) {
            if (row == null
                    || row.instructionId() == null
                    || row.instructionId() <= 0
                    || row.blockId() == null
                    || row.blockId() <= 0
                    || row.blockOrderNumber() == null
                    || row.blockOrderNumber() <= 0
                    || row.instructionOrderNumber() == null
                    || row.instructionOrderNumber() <= 0) {
                return LayoutResult.failed(error(
                        ErrorCode.INVALID_LAYOUT_ROW,
                        "Every layout row requires positive instruction, block, and order values."));
            }
            if (layoutByInstruction.putIfAbsent(row.instructionId(), row) != null) {
                return LayoutResult.failed(error(
                        ErrorCode.DUPLICATE_LAYOUT_INSTRUCTION,
                        "The layout contains instruction #" + row.instructionId() + " more than once."));
            }
            if (!index.instructionsById().containsKey(row.instructionId())) {
                return LayoutResult.failed(error(
                        ErrorCode.CROSS_OWNER_INSTRUCTION,
                        "Instruction #" + row.instructionId() + " is outside the authoritative owner."));
            }
            StoredBlock block = index.blocksById().get(row.blockId());
            if (block == null) {
                return LayoutResult.failed(error(
                        ErrorCode.CROSS_OWNER_BLOCK,
                        "Destination block #" + row.blockId() + " is outside the authoritative owner."));
            }
            if (block.order() != row.blockOrderNumber()) {
                return LayoutResult.failed(error(
                        ErrorCode.BLOCK_ORDER_MISMATCH,
                        "Destination block #" + row.blockId() + " has a different authoritative order."));
            }
            if (!ordersByBlock
                    .computeIfAbsent(row.blockId(), ignored -> new HashSet<>())
                    .add(row.instructionOrderNumber())) {
                return LayoutResult.failed(error(
                        ErrorCode.DUPLICATE_INSTRUCTION_ORDER,
                        "Block #" + row.blockId() + " contains a duplicate instruction order."));
            }
        }
        if (!layoutByInstruction.keySet().equals(index.instructionsById().keySet())) {
            return LayoutResult.failed(error(
                    ErrorCode.INCOMPLETE_LAYOUT,
                    "The layout instruction IDs do not exactly match the authoritative owner."));
        }
        for (Map.Entry<Integer, Set<Integer>> entry : ordersByBlock.entrySet()) {
            Set<Integer> orders = entry.getValue();
            for (int order = 1; order <= orders.size(); order++) {
                if (!orders.contains(order)) {
                    return LayoutResult.failed(error(
                            ErrorCode.NON_CONTIGUOUS_INSTRUCTION_ORDER,
                            "Block #" + entry.getKey() + " has a missing instruction order."));
                }
            }
        }

        List<LayoutRow> ordered = layoutByInstruction.values().stream()
                .sorted(Comparator.comparingInt((LayoutRow row) -> row.blockOrderNumber())
                        .thenComparingInt(LayoutRow::instructionOrderNumber)
                        .thenComparingInt(LayoutRow::instructionId))
                .toList();
        return LayoutResult.success(Map.copyOf(layoutByInstruction), ordered);
    }

    private PatchResult validateInstructionRelationPatches(
            List<InstructionRelationPatch> patches,
            GraphIndex index,
            Map<Integer, LayoutRow> layoutByInstruction,
            Map<Integer, InstructionRelationState> finalRelations) {
        Set<Integer> patchedInstructions = new HashSet<>();
        List<InstructionRelationPatch> normalized = new ArrayList<>();
        for (InstructionRelationPatch patch : safe(patches)) {
            if (patch == null
                    || patch.instructionId() == null
                    || patch.instructionId() <= 0
                    || patch.relationKind() == null
                    || patch.operation() == null) {
                return PatchResult.failed(error(
                        ErrorCode.INVALID_RELATION_PATCH,
                        "Every instruction relationship patch requires an instruction, kind, and operation."));
            }
            if (!patchedInstructions.add(patch.instructionId())) {
                return PatchResult.failed(error(
                        ErrorCode.DUPLICATE_RELATION_PATCH,
                        "Instruction #" + patch.instructionId() + " has more than one relationship patch."));
            }
            StoredInstruction stored = index.instructionsById().get(patch.instructionId());
            if (stored == null) {
                return PatchResult.failed(error(
                        ErrorCode.CROSS_OWNER_INSTRUCTION,
                        "Relationship patch instruction #" + patch.instructionId()
                                + " is outside the authoritative owner."));
            }
            if (patch.relationKind() != stored.relationKind()) {
                return PatchResult.failed(error(
                        ErrorCode.RELATION_KIND_MISMATCH,
                        "Instruction #" + patch.instructionId()
                                + " relationship kind does not match the authoritative row."));
            }
            if (patch.expected() == null || patch.replacement() == null) {
                return PatchResult.failed(error(
                        ErrorCode.MISSING_RELATION_STATE,
                        "Relationship patches require complete expected and replacement state objects."));
            }
            InstructionRelationState current =
                    new InstructionRelationState(stored.parentId(), stored.parentBlockId());
            if (!current.equals(patch.expected())) {
                return PatchResult.failed(error(
                        ErrorCode.EXPECTED_RELATION_MISMATCH,
                        "Instruction #" + patch.instructionId()
                                + " relationship changed before the mutation was submitted."));
            }
            ValidationError operationError = validateRelationOperation(patch);
            if (operationError != null) return PatchResult.failed(operationError);

            InstructionRelationState replacement = patch.replacement();
            if (replacement.parentId() != null
                    && !index.instructionsById().containsKey(replacement.parentId())) {
                return PatchResult.failed(error(
                        ErrorCode.CROSS_OWNER_INSTRUCTION_TARGET,
                        "Relationship target instruction #" + replacement.parentId()
                                + " is outside the authoritative owner."));
            }
            if (replacement.parentBlockId() != null
                    && !index.blocksById().containsKey(replacement.parentBlockId())) {
                return PatchResult.failed(error(
                        ErrorCode.CROSS_OWNER_BLOCK_TARGET,
                        "Relationship target block #" + replacement.parentBlockId()
                                + " is outside the authoritative owner."));
            }
            if (patch.operation() == PatchOperation.SET
                    && patch.relationKind() != InstructionRelationKind.BLOCK_TARGET) {
                LayoutRow target = layoutByInstruction.get(replacement.parentId());
                if (target == null
                        || !Objects.equals(target.blockId(), replacement.parentBlockId())) {
                    return PatchResult.failed(error(
                            ErrorCode.PARENT_BLOCK_PROJECTION_MISMATCH,
                            "The submitted parent block does not match the target instruction's final block."));
                }
            }
            finalRelations.put(patch.instructionId(), replacement);
            normalized.add(patch);
        }
        normalized.sort(Comparator.comparingInt(InstructionRelationPatch::instructionId));
        return PatchResult.success(List.copyOf(normalized));
    }

    private ValidationError validateRelationOperation(InstructionRelationPatch patch) {
        InstructionRelationState expected = patch.expected();
        InstructionRelationState replacement = patch.replacement();
        if (patch.operation() == PatchOperation.KEEP && !expected.equals(replacement)) {
            return error(
                    ErrorCode.INVALID_RELATION_PATCH,
                    "KEEP must preserve the complete expected relationship state.");
        }
        if (patch.operation() == PatchOperation.CLEAR
                && (replacement.parentId() != null || replacement.parentBlockId() != null)) {
            return error(
                    ErrorCode.INVALID_RELATION_PATCH,
                    "CLEAR requires an explicit disconnected replacement state.");
        }
        if (patch.relationKind() == InstructionRelationKind.BLOCK_TARGET) {
            if (expected.parentId() != null || replacement.parentId() != null) {
                return error(
                        ErrorCode.INVALID_RELATION_PATCH,
                        "A block-target patch may modify parentBlockId only.");
            }
            if (patch.operation() == PatchOperation.SET
                    && (replacement.parentBlockId() == null
                            || replacement.parentBlockId() <= 0)) {
                return error(
                        ErrorCode.INVALID_RELATION_PATCH,
                        "SET on a block target requires a positive replacement parentBlockId.");
            }
        } else if (patch.operation() == PatchOperation.SET
                && (replacement.parentId() == null
                        || replacement.parentId() <= 0
                        || replacement.parentBlockId() == null
                        || replacement.parentBlockId() <= 0)) {
            return error(
                    ErrorCode.INVALID_RELATION_PATCH,
                    "SET on an instruction target requires positive parentId and parentBlockId values.");
        }
        return null;
    }

    private ValidationError validateFinalState(
            GraphIndex index,
            Map<Integer, LayoutRow> layoutByInstruction,
            Map<Integer, InstructionRelationState> finalRelations,
            Map<Integer, Integer> finalVariableBindings,
            Map<Integer, Integer> finalVariableOwners) {
        for (StoredInstruction instruction : index.instructionsById().values()) {
            LayoutRow containingRow = layoutByInstruction.get(instruction.id());
            InstructionRelationState relation = finalRelations.get(instruction.id());
            if (containingRow == null || relation == null) {
                return error(
                        ErrorCode.INVALID_FINAL_RELATION,
                        "The normalized graph is missing instruction #" + instruction.id() + ".");
            }

            Integer parentId = relation.parentId();
            Integer parentBlockId = relation.parentBlockId();
            InstructionRelationKind relationKind = instruction.relationKind();
            if (relationKind == InstructionRelationKind.BLOCK_TARGET) {
                if (parentId != null) {
                    return error(
                            ErrorCode.INVALID_FINAL_RELATION,
                            "A block-target instruction cannot carry parentId.");
                }
                if (parentBlockId != null) {
                    if (!index.blocksById().containsKey(parentBlockId)) {
                        return error(
                                ErrorCode.CROSS_OWNER_BLOCK_TARGET,
                                "Relationship target block #" + parentBlockId
                                        + " is outside the authoritative owner.");
                    }
                    if (Objects.equals(parentBlockId, containingRow.blockId())) {
                        return error(
                                ErrorCode.BLOCK_TARGET_EQUALS_CONTAINING_BLOCK,
                                "GOTO and EXCEL GOTO must target a block different from their containing block.");
                    }
                }
            } else if (parentId == null && parentBlockId == null) {
                // A disconnected instruction is a valid authoring state.
            } else {
                if (relationKind == null) {
                    return error(
                            ErrorCode.MISSING_RELATION_KIND,
                            "Connected instruction #" + instruction.id()
                                    + " requires an authoritative relationship kind.");
                }
                if (parentId == null || parentBlockId == null) {
                    return error(
                            ErrorCode.INVALID_FINAL_RELATION,
                            "Connected instruction #" + instruction.id()
                                    + " requires both parentId and parentBlockId.");
                }
                if (!index.instructionsById().containsKey(parentId)) {
                    return error(
                            ErrorCode.CROSS_OWNER_INSTRUCTION_TARGET,
                            "Relationship target instruction #" + parentId
                                    + " is outside the authoritative owner.");
                }
                if (!index.blocksById().containsKey(parentBlockId)) {
                    return error(
                            ErrorCode.CROSS_OWNER_BLOCK_TARGET,
                            "Relationship target block #" + parentBlockId
                                    + " is outside the authoritative owner.");
                }
                LayoutRow parentRow = layoutByInstruction.get(parentId);
                if (parentRow == null || !Objects.equals(parentRow.blockId(), parentBlockId)) {
                    return error(
                            ErrorCode.PARENT_BLOCK_PROJECTION_MISMATCH,
                            "Instruction #" + instruction.id()
                                    + " parentBlockId does not match its parent's final block.");
                }
            }

            Integer variableId = finalVariableBindings.get(instruction.id());
            if (variableId != null && !index.variablesById().containsKey(variableId)) {
                return error(
                        ErrorCode.CROSS_OWNER_VARIABLE_TARGET,
                        "Variable #" + variableId + " is outside the authoritative owner.");
            }
        }

        for (StoredVariable variable : index.variablesById().values()) {
            Integer ownerInstructionId = finalVariableOwners.get(variable.id());
            if (ownerInstructionId != null
                    && !index.instructionsById().containsKey(ownerInstructionId)) {
                return error(
                        ErrorCode.CROSS_OWNER_INSTRUCTION_TARGET,
                        "Variable owner instruction #" + ownerInstructionId
                                + " is outside the authoritative owner.");
            }
        }
        return null;
    }

    private VariableBindingResult validateVariableBindingPatches(
            List<VariableBindingPatch> patches,
            GraphIndex index,
            Map<Integer, Integer> finalBindings) {
        Set<Integer> patchedInstructions = new HashSet<>();
        List<VariableBindingPatch> normalized = new ArrayList<>();
        for (VariableBindingPatch patch : safe(patches)) {
            if (patch == null
                    || patch.instructionId() == null
                    || patch.instructionId() <= 0
                    || patch.operation() == null
                    || patch.expected() == null
                    || patch.replacement() == null) {
                return VariableBindingResult.failed(error(
                        ErrorCode.INVALID_VARIABLE_BINDING_PATCH,
                        "Every variable-binding patch requires complete expected and replacement values."));
            }
            if (!patchedInstructions.add(patch.instructionId())) {
                return VariableBindingResult.failed(error(
                        ErrorCode.DUPLICATE_VARIABLE_BINDING_PATCH,
                        "Instruction #" + patch.instructionId()
                                + " has more than one variable-binding patch."));
            }
            StoredInstruction stored = index.instructionsById().get(patch.instructionId());
            if (stored == null) {
                return VariableBindingResult.failed(error(
                        ErrorCode.CROSS_OWNER_INSTRUCTION,
                        "Variable-binding instruction #" + patch.instructionId()
                                + " is outside the authoritative owner."));
            }
            if (!Objects.equals(stored.variableId(), patch.expected().value())) {
                return VariableBindingResult.failed(error(
                        ErrorCode.EXPECTED_VARIABLE_BINDING_MISMATCH,
                        "Instruction #" + patch.instructionId()
                                + " variable binding changed before submission."));
            }
            ValidationError operationError = validateNullableIdOperation(
                    patch.operation(),
                    patch.expected(),
                    patch.replacement(),
                    "variable binding");
            if (operationError != null) return VariableBindingResult.failed(operationError);
            Integer replacement = patch.replacement().value();
            if (replacement != null && !index.variablesById().containsKey(replacement)) {
                return VariableBindingResult.failed(error(
                        ErrorCode.CROSS_OWNER_VARIABLE_TARGET,
                        "Variable #" + replacement + " is outside the authoritative owner."));
            }
            finalBindings.put(patch.instructionId(), replacement);
            normalized.add(patch);
        }
        normalized.sort(Comparator.comparingInt(VariableBindingPatch::instructionId));
        return VariableBindingResult.success(List.copyOf(normalized));
    }

    private VariableOwnerResult validateVariableOwnerPatches(
            List<VariableOwnerPatch> patches,
            GraphIndex index,
            Map<Integer, Integer> finalOwners) {
        Set<Integer> patchedVariables = new HashSet<>();
        List<VariableOwnerPatch> normalized = new ArrayList<>();
        for (VariableOwnerPatch patch : safe(patches)) {
            if (patch == null
                    || patch.variableId() == null
                    || patch.variableId() <= 0
                    || patch.operation() == null
                    || patch.expected() == null
                    || patch.replacement() == null) {
                return VariableOwnerResult.failed(error(
                        ErrorCode.INVALID_VARIABLE_OWNER_PATCH,
                        "Every variable-owner patch requires complete expected and replacement values."));
            }
            if (!patchedVariables.add(patch.variableId())) {
                return VariableOwnerResult.failed(error(
                        ErrorCode.DUPLICATE_VARIABLE_OWNER_PATCH,
                        "Variable #" + patch.variableId() + " has more than one owner patch."));
            }
            StoredVariable stored = index.variablesById().get(patch.variableId());
            if (stored == null) {
                return VariableOwnerResult.failed(error(
                        ErrorCode.CROSS_OWNER_VARIABLE_TARGET,
                        "Variable #" + patch.variableId() + " is outside the authoritative owner."));
            }
            if (!Objects.equals(stored.instructionId(), patch.expected().value())) {
                return VariableOwnerResult.failed(error(
                        ErrorCode.EXPECTED_VARIABLE_OWNER_MISMATCH,
                        "Variable #" + patch.variableId() + " owner changed before submission."));
            }
            ValidationError operationError = validateNullableIdOperation(
                    patch.operation(),
                    patch.expected(),
                    patch.replacement(),
                    "variable owner");
            if (operationError != null) return VariableOwnerResult.failed(operationError);
            Integer replacement = patch.replacement().value();
            if (replacement != null && !index.instructionsById().containsKey(replacement)) {
                return VariableOwnerResult.failed(error(
                        ErrorCode.CROSS_OWNER_INSTRUCTION_TARGET,
                        "Variable owner instruction #" + replacement
                                + " is outside the authoritative owner."));
            }
            finalOwners.put(patch.variableId(), replacement);
            normalized.add(patch);
        }
        normalized.sort(Comparator.comparingInt(VariableOwnerPatch::variableId));
        return VariableOwnerResult.success(List.copyOf(normalized));
    }

    private ValidationError validateNullableIdOperation(
            PatchOperation operation,
            NullableId expected,
            NullableId replacement,
            String label) {
        if (operation == PatchOperation.KEEP
                && !Objects.equals(expected.value(), replacement.value())) {
            return error(
                    ErrorCode.INVALID_NULLABLE_ID_PATCH,
                    "KEEP must preserve the expected " + label + ".");
        }
        if (operation == PatchOperation.CLEAR && replacement.value() != null) {
            return error(
                    ErrorCode.INVALID_NULLABLE_ID_PATCH,
                    "CLEAR requires an explicit null replacement " + label + ".");
        }
        if (operation == PatchOperation.SET
                && (replacement.value() == null || replacement.value() <= 0)) {
            return error(
                    ErrorCode.INVALID_NULLABLE_ID_PATCH,
                    "SET requires a positive replacement " + label + ".");
        }
        return null;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Validation failure(ErrorCode code, String message) {
        return Validation.failed(error(code, message));
    }

    private ValidationError error(ErrorCode code, String message) {
        return new ValidationError(code, message);
    }

    public enum ErrorCode {
        MISSING_REQUEST,
        MISSING_OWNER_GRAPH,
        UNSUPPORTED_CONTRACT,
        INVALID_REQUEST,
        INVALID_OWNER_GRAPH,
        OWNER_MISMATCH,
        WORKSPACE_EPOCH_MISMATCH,
        GRAPH_VERSION_MISMATCH,
        GRAPH_REVISION_MISMATCH,
        INVALID_DRAGGED_INSTRUCTION,
        INCOMPLETE_LAYOUT,
        INVALID_LAYOUT_ROW,
        DUPLICATE_LAYOUT_INSTRUCTION,
        CROSS_OWNER_INSTRUCTION,
        CROSS_OWNER_BLOCK,
        BLOCK_ORDER_MISMATCH,
        DUPLICATE_INSTRUCTION_ORDER,
        NON_CONTIGUOUS_INSTRUCTION_ORDER,
        INVALID_RELATION_PATCH,
        DUPLICATE_RELATION_PATCH,
        RELATION_KIND_MISMATCH,
        MISSING_RELATION_STATE,
        MISSING_RELATION_KIND,
        EXPECTED_RELATION_MISMATCH,
        INVALID_FINAL_RELATION,
        CROSS_OWNER_INSTRUCTION_TARGET,
        CROSS_OWNER_BLOCK_TARGET,
        PARENT_BLOCK_PROJECTION_MISMATCH,
        BLOCK_TARGET_EQUALS_CONTAINING_BLOCK,
        INVALID_VARIABLE_BINDING_PATCH,
        DUPLICATE_VARIABLE_BINDING_PATCH,
        EXPECTED_VARIABLE_BINDING_MISMATCH,
        CROSS_OWNER_VARIABLE_TARGET,
        INVALID_VARIABLE_OWNER_PATCH,
        DUPLICATE_VARIABLE_OWNER_PATCH,
        EXPECTED_VARIABLE_OWNER_MISMATCH,
        INVALID_NULLABLE_ID_PATCH
    }

    public record ValidationError(ErrorCode code, String message) {}

    public record OwnerScope(
            WorkspaceKind workspaceKind,
            Integer homeBankingId,
            Integer botJobId,
            Long workspaceEpoch,
            Long graphVersion,
            String graphRevision) {}

    public record StoredBlock(int id, int order) {}

    public record StoredInstruction(
            int id,
            int blockId,
            int order,
            InstructionRelationKind relationKind,
            Integer parentId,
            Integer parentBlockId,
            Integer variableId) {}

    public record StoredVariable(int id, Integer instructionId) {}

    public record OwnerGraph(
            OwnerScope scope,
            List<StoredBlock> blocks,
            List<StoredInstruction> instructions,
            List<StoredVariable> variables) {

        public OwnerGraph {
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
            instructions = instructions == null ? List.of() : List.copyOf(instructions);
            variables = variables == null ? List.of() : List.copyOf(variables);
        }
    }

    public record NormalizedInstruction(
            int instructionId,
            int blockId,
            int blockOrderNumber,
            int instructionOrderNumber,
            InstructionRelationKind relationKind,
            Integer parentId,
            Integer parentBlockId,
            Integer variableId) {}

    public record NormalizedVariable(int variableId, Integer instructionId) {}

    public record NormalizedMutation(
            int contractVersion,
            InstructionGraphMutationV3.MutationKind mutationKind,
            String requestId,
            long baseGraphVersion,
            String graphRevision,
            long workspaceEpoch,
            OwnerScope owner,
            Integer draggedInstructionId,
            List<LayoutRow> layoutRows,
            List<InstructionRelationPatch> instructionRelationPatches,
            List<VariableBindingPatch> variableBindingPatches,
            List<VariableOwnerPatch> variableOwnerPatches,
            List<NormalizedInstruction> instructions,
            List<NormalizedVariable> variables) {}

    public record Validation(
            NormalizedMutation mutation,
            ValidationError error) {

        public boolean successful() {
            return error == null;
        }

        private static Validation success(NormalizedMutation mutation) {
            return new Validation(mutation, null);
        }

        private static Validation failed(ValidationError error) {
            return new Validation(null, error);
        }
    }

    private record GraphIndex(
            Map<Integer, StoredBlock> blocksById,
            Map<Integer, StoredInstruction> instructionsById,
            Map<Integer, StoredVariable> variablesById) {}

    private record GraphIndexResult(GraphIndex index, ValidationError error) {
        private static GraphIndexResult success(GraphIndex index) {
            return new GraphIndexResult(index, null);
        }

        private static GraphIndexResult failed(ValidationError error) {
            return new GraphIndexResult(null, error);
        }
    }

    private record LayoutResult(
            Map<Integer, LayoutRow> layoutByInstruction,
            List<LayoutRow> orderedLayout,
            ValidationError error) {
        private static LayoutResult success(
                Map<Integer, LayoutRow> layoutByInstruction,
                List<LayoutRow> orderedLayout) {
            return new LayoutResult(layoutByInstruction, orderedLayout, null);
        }

        private static LayoutResult failed(ValidationError error) {
            return new LayoutResult(Map.of(), List.of(), error);
        }
    }

    private record PatchResult(
            List<InstructionRelationPatch> normalizedPatches,
            ValidationError error) {
        private static PatchResult success(List<InstructionRelationPatch> normalizedPatches) {
            return new PatchResult(normalizedPatches, null);
        }

        private static PatchResult failed(ValidationError error) {
            return new PatchResult(List.of(), error);
        }
    }

    private record VariableBindingResult(
            List<VariableBindingPatch> normalizedPatches,
            ValidationError error) {
        private static VariableBindingResult success(
                List<VariableBindingPatch> normalizedPatches) {
            return new VariableBindingResult(normalizedPatches, null);
        }

        private static VariableBindingResult failed(ValidationError error) {
            return new VariableBindingResult(List.of(), error);
        }
    }

    private record VariableOwnerResult(
            List<VariableOwnerPatch> normalizedPatches,
            ValidationError error) {
        private static VariableOwnerResult success(
                List<VariableOwnerPatch> normalizedPatches) {
            return new VariableOwnerResult(normalizedPatches, null);
        }

        private static VariableOwnerResult failed(ValidationError error) {
            return new VariableOwnerResult(List.of(), error);
        }
    }
}
