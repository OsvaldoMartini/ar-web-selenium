package com.allinweb.ch.facade;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.VariableLoadDTO;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the complete instruction aggregate that must travel with one selected instruction.
 *
 * <p>The service is deliberately persistence-free. Callers must load an authoritative owner-scoped
 * instruction/variable graph before invoking it, then validate the returned source graph again in
 * the transaction that applies the copy or move.
 *
 * <p>{@link Mode#COMPONENT_COPY} recursively includes every instruction in an external block
 * referenced by GOTO or EXCEL GOTO. Those external whole blocks are reported separately through
 * {@link Result#requiredBlockIds()}. {@link Mode#BOT_JOB_MOVE} and
 * {@link Mode#BOT_JOB_COPY} leave referenced GOTO blocks in place while still resolving parent,
 * conditional, loop, and variable relationships. A Bot Job copy can therefore preserve an
 * intentional cross-block navigation target while remapping relationships inside the copied
 * group.
 */
public final class InstructionDependencyClosureService {
    private static final Comparator<InstructionLoad> INSTRUCTION_ORDER = Comparator
            .comparingInt((InstructionLoad row) -> valueOrMaximum(row.getBlockOrderNumber()))
            .thenComparingInt(row -> valueOrMaximum(row.getBlockId()))
            .thenComparingInt(row -> valueOrMaximum(row.getInstructionOrderNumber()))
            .thenComparingInt(row -> valueOrMaximum(row.getId()));

    private final InstructionMoveGroupService moveGroupService;

    public InstructionDependencyClosureService() {
        this(new InstructionMoveGroupService());
    }

    InstructionDependencyClosureService(InstructionMoveGroupService moveGroupService) {
        this.moveGroupService = moveGroupService;
    }

    public Result resolve(
            List<InstructionLoad> instructions,
            List<VariableLoadDTO> variables,
            int selectedInstructionId,
            Mode mode) {
        if (mode == null) {
            return Result.failed(error(
                    ErrorCode.INVALID_REQUEST,
                    "A dependency resolution mode is required.",
                    selectedInstructionId,
                    null));
        }
        if (selectedInstructionId <= 0) {
            return Result.failed(error(
                    ErrorCode.INVALID_REQUEST,
                    "A positive selected instruction ID is required.",
                    selectedInstructionId,
                    null));
        }

        IndexResult indexed = index(instructions, variables);
        if (indexed.error() != null) return Result.failed(indexed.error());
        GraphIndex graph = indexed.graph();
        if (!graph.instructionsById().containsKey(selectedInstructionId)) {
            return Result.failed(error(
                    ErrorCode.INSTRUCTION_NOT_FOUND,
                    "The selected instruction does not exist in the supplied owner graph.",
                    selectedInstructionId,
                    null));
        }

        Set<Integer> includedIds = new LinkedHashSet<>();
        Set<Integer> expandedIds = new LinkedHashSet<>();
        Set<Integer> requiredBlockIds = new LinkedHashSet<>();
        Deque<Integer> pending = new ArrayDeque<>();
        include(selectedInstructionId, includedIds, pending);

        while (!pending.isEmpty()) {
            int currentId = pending.removeFirst();
            if (!expandedIds.add(currentId)) continue;
            InstructionLoad current = graph.instructionsById().get(currentId);

            for (InstructionLoad grouped : moveGroupService.resolve(graph.instructions(), currentId)) {
                include(grouped.getId(), includedIds, pending);
            }

            boolean crossBlockGoto = isCrossBlockGoto(current);
            Integer parentId = current.getParentId();
            if (parentId != null && !parentId.equals(currentId) && !crossBlockGoto) {
                if (!graph.instructionsById().containsKey(parentId)) {
                    return Result.failed(error(
                            ErrorCode.DANGLING_PARENT,
                            "An instruction references a parent outside the supplied owner graph.",
                            currentId,
                            parentId));
                }
                include(parentId, includedIds, pending);
            }
            for (InstructionLoad child : graph.childrenByParent().getOrDefault(currentId, List.of())) {
                // A GOTO's parent_id points into its destination block. That is a cross-block
                // navigation reference, not an ownership edge: selecting the destination must not
                // pull every incoming GOTO, and moving a GOTO must not move its destination.
                if (!isCrossBlockGoto(child)) {
                    include(child.getId(), includedIds, pending);
                }
            }

            Set<Integer> connectedVariableIds = new LinkedHashSet<>();
            if (current.getVariableId() != null) connectedVariableIds.add(current.getVariableId());
            graph.variablesByOwner()
                    .getOrDefault(currentId, List.of())
                    .forEach(variable -> connectedVariableIds.add(variable.getId()));
            for (Integer variableId : connectedVariableIds) {
                ClosureError variableError =
                        includeVariableFamily(graph, currentId, variableId, includedIds, pending);
                if (variableError != null) return Result.failed(variableError);
            }

            if (mode == Mode.COMPONENT_COPY && crossBlockGoto) {
                ClosureError gotoError = includeGotoTarget(
                        graph, current, includedIds, pending, requiredBlockIds);
                if (gotoError != null) return Result.failed(gotoError);
            }
        }

        List<InstructionLoad> orderedInstructions = graph.instructions().stream()
                .filter(row -> includedIds.contains(row.getId()))
                .sorted(INSTRUCTION_ORDER)
                .toList();
        List<Integer> orderedRequiredBlocks = requiredBlockIds.stream()
                .sorted(Comparator.comparingInt((Integer blockId) -> graph.blockOrder(blockId))
                        .thenComparingInt(Integer::intValue))
                .toList();
        return Result.success(orderedInstructions, orderedRequiredBlocks);
    }

    private IndexResult index(
            List<InstructionLoad> suppliedInstructions, List<VariableLoadDTO> suppliedVariables) {
        if (suppliedInstructions == null) {
            return IndexResult.failed(error(
                    ErrorCode.INVALID_REQUEST,
                    "An authoritative instruction graph is required.",
                    null,
                    null));
        }

        List<InstructionLoad> instructions = new ArrayList<>(suppliedInstructions.size());
        Map<Integer, InstructionLoad> instructionsById = new LinkedHashMap<>();
        Map<Integer, List<InstructionLoad>> rowsByBlock = new LinkedHashMap<>();
        Map<Integer, List<InstructionLoad>> childrenByParent = new LinkedHashMap<>();
        for (InstructionLoad instruction : suppliedInstructions) {
            if (instruction == null || instruction.getId() == null || instruction.getId() <= 0) {
                return IndexResult.failed(error(
                        ErrorCode.INVALID_INSTRUCTION,
                        "The owner graph contains an instruction without a positive ID.",
                        instruction == null ? null : instruction.getId(),
                        null));
            }
            if (instruction.getBlockId() == null || instruction.getBlockId() <= 0) {
                return IndexResult.failed(error(
                        ErrorCode.INVALID_INSTRUCTION_BLOCK,
                        "An instruction is not attached to a positive block ID.",
                        instruction.getId(),
                        instruction.getBlockId()));
            }
            if (instructionsById.putIfAbsent(instruction.getId(), instruction) != null) {
                return IndexResult.failed(error(
                        ErrorCode.DUPLICATE_INSTRUCTION_ID,
                        "The owner graph contains a duplicate instruction ID.",
                        instruction.getId(),
                        null));
            }
            instructions.add(instruction);
            rowsByBlock
                    .computeIfAbsent(instruction.getBlockId(), ignored -> new ArrayList<>())
                    .add(instruction);
            if (instruction.getParentId() != null) {
                childrenByParent
                        .computeIfAbsent(instruction.getParentId(), ignored -> new ArrayList<>())
                        .add(instruction);
            }
        }
        instructions.sort(INSTRUCTION_ORDER);
        rowsByBlock.values().forEach(rows -> rows.sort(INSTRUCTION_ORDER));
        childrenByParent.values().forEach(rows -> rows.sort(INSTRUCTION_ORDER));

        Map<Integer, VariableLoadDTO> variablesById = new LinkedHashMap<>();
        Map<Integer, List<VariableLoadDTO>> variablesByOwner = new LinkedHashMap<>();
        List<VariableLoadDTO> variables =
                suppliedVariables == null ? List.of() : suppliedVariables;
        for (VariableLoadDTO variable : variables) {
            if (variable == null || variable.getId() == null || variable.getId() <= 0) {
                return IndexResult.failed(error(
                        ErrorCode.INVALID_VARIABLE,
                        "The owner graph contains a variable without a positive ID.",
                        null,
                        variable == null ? null : variable.getId()));
            }
            if (variablesById.putIfAbsent(variable.getId(), variable) != null) {
                return IndexResult.failed(error(
                        ErrorCode.DUPLICATE_VARIABLE_ID,
                        "The owner graph contains a duplicate variable ID.",
                        null,
                        variable.getId()));
            }
            if (variable.getInstructionId() != null) {
                variablesByOwner
                        .computeIfAbsent(variable.getInstructionId(), ignored -> new ArrayList<>())
                        .add(variable);
            }
        }

        Map<Integer, List<InstructionLoad>> usersByVariable = new LinkedHashMap<>();
        for (InstructionLoad instruction : instructions) {
            if (instruction.getVariableId() != null) {
                usersByVariable
                        .computeIfAbsent(instruction.getVariableId(), ignored -> new ArrayList<>())
                        .add(instruction);
            }
        }
        usersByVariable.values().forEach(rows -> rows.sort(INSTRUCTION_ORDER));

        return IndexResult.success(new GraphIndex(
                List.copyOf(instructions),
                Map.copyOf(instructionsById),
                immutableLists(rowsByBlock),
                immutableLists(childrenByParent),
                Map.copyOf(variablesById),
                immutableLists(variablesByOwner),
                immutableLists(usersByVariable)));
    }

    private ClosureError includeVariableFamily(
            GraphIndex graph,
            int currentInstructionId,
            int variableId,
            Set<Integer> includedIds,
            Deque<Integer> pending) {
        VariableLoadDTO variable = graph.variablesById().get(variableId);
        if (variable == null) {
            return error(
                    ErrorCode.DANGLING_VARIABLE,
                    "An instruction references a variable outside the supplied owner graph.",
                    currentInstructionId,
                    variableId);
        }
        Integer ownerId = variable.getInstructionId();
        if (ownerId == null || ownerId <= 0) {
            return error(
                    ErrorCode.MISSING_VARIABLE_OWNER,
                    "A connected variable does not have an owning instruction.",
                    currentInstructionId,
                    variableId);
        }
        if (!graph.instructionsById().containsKey(ownerId)) {
            return error(
                    ErrorCode.DANGLING_VARIABLE_OWNER,
                    "A connected variable owner is outside the supplied instruction graph.",
                    currentInstructionId,
                    ownerId);
        }
        include(ownerId, includedIds, pending);
        for (InstructionLoad user : graph.usersByVariable().getOrDefault(variableId, List.of())) {
            include(user.getId(), includedIds, pending);
        }
        return null;
    }

    private static boolean isCrossBlockGoto(InstructionLoad instruction) {
        return instruction != null
                && CommandRegistry.isCrossBlockNavigation(
                        instruction.getActions(),
                        instruction.getParentBlockId(),
                        instruction.getBlockId());
    }

    private ClosureError includeGotoTarget(
            GraphIndex graph,
            InstructionLoad instruction,
            Set<Integer> includedIds,
            Deque<Integer> pending,
            Set<Integer> requiredBlockIds) {
        Integer targetBlockId = instruction.getParentBlockId();
        if (targetBlockId == null || targetBlockId <= 0) {
            return error(
                    ErrorCode.MISSING_GOTO_TARGET_BLOCK,
                    "A component GOTO does not reference a positive target block.",
                    instruction.getId(),
                    targetBlockId);
        }
        if (targetBlockId.equals(instruction.getBlockId())) return null;

        List<InstructionLoad> targetRows = graph.rowsByBlock().get(targetBlockId);
        if (targetRows == null || targetRows.isEmpty()) {
            return error(
                    ErrorCode.DANGLING_GOTO_TARGET_BLOCK,
                    "A component GOTO references a block outside the supplied owner graph.",
                    instruction.getId(),
                    targetBlockId);
        }
        requiredBlockIds.add(targetBlockId);
        for (InstructionLoad targetRow : targetRows) {
            include(targetRow.getId(), includedIds, pending);
        }
        return null;
    }

    private static void include(
            Integer instructionId, Set<Integer> includedIds, Deque<Integer> pending) {
        if (instructionId != null && includedIds.add(instructionId)) {
            pending.addLast(instructionId);
        }
    }

    private static ClosureError error(
            ErrorCode code, String message, Integer instructionId, Integer relatedId) {
        return new ClosureError(code, message, instructionId, relatedId);
    }

    private static int valueOrMaximum(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private static <K, V> Map<K, List<V>> immutableLists(Map<K, List<V>> source) {
        Map<K, List<V>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }

    public enum Mode {
        COMPONENT_COPY,
        BOT_JOB_COPY,
        BOT_JOB_MOVE
    }

    public enum ErrorCode {
        INVALID_REQUEST,
        INVALID_INSTRUCTION,
        INVALID_INSTRUCTION_BLOCK,
        DUPLICATE_INSTRUCTION_ID,
        INSTRUCTION_NOT_FOUND,
        DANGLING_PARENT,
        INVALID_VARIABLE,
        DUPLICATE_VARIABLE_ID,
        DANGLING_VARIABLE,
        MISSING_VARIABLE_OWNER,
        DANGLING_VARIABLE_OWNER,
        MISSING_GOTO_TARGET_BLOCK,
        DANGLING_GOTO_TARGET_BLOCK
    }

    public record ClosureError(
            ErrorCode code, String message, Integer instructionId, Integer relatedId) {}

    public record Result(
            List<InstructionLoad> orderedInstructions,
            List<Integer> requiredBlockIds,
            ClosureError error) {
        public Result {
            orderedInstructions =
                    orderedInstructions == null ? List.of() : List.copyOf(orderedInstructions);
            requiredBlockIds =
                    requiredBlockIds == null ? List.of() : List.copyOf(requiredBlockIds);
        }

        public boolean successful() {
            return error == null;
        }

        private static Result success(
                List<InstructionLoad> instructions, List<Integer> requiredBlockIds) {
            return new Result(instructions, requiredBlockIds, null);
        }

        private static Result failed(ClosureError error) {
            return new Result(List.of(), List.of(), error);
        }
    }

    private record IndexResult(GraphIndex graph, ClosureError error) {
        private static IndexResult success(GraphIndex graph) {
            return new IndexResult(graph, null);
        }

        private static IndexResult failed(ClosureError error) {
            return new IndexResult(null, error);
        }
    }

    private record GraphIndex(
            List<InstructionLoad> instructions,
            Map<Integer, InstructionLoad> instructionsById,
            Map<Integer, List<InstructionLoad>> rowsByBlock,
            Map<Integer, List<InstructionLoad>> childrenByParent,
            Map<Integer, VariableLoadDTO> variablesById,
            Map<Integer, List<VariableLoadDTO>> variablesByOwner,
            Map<Integer, List<InstructionLoad>> usersByVariable) {
        private int blockOrder(int blockId) {
            return rowsByBlock.getOrDefault(blockId, List.of()).stream()
                    .map(InstructionLoad::getBlockOrderNumber)
                    .filter(value -> value != null)
                    .min(Integer::compareTo)
                    .orElse(Integer.MAX_VALUE);
        }
    }
}
