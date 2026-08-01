package com.allinweb.ch.facade.execution;

import com.allinweb.ch.facade.CommandRegistry;
import com.allinweb.ch.facade.execution.ExecutionPreflightResult.Issue;
import com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode;
import com.allinweb.ch.facade.execution.ExecutionPreflightResult.RelationshipKind;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.BlockFact;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.InstructionFact;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.VariableFact;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure runtime relationship diagnostic over one immutable, owner-scoped execution snapshot.
 *
 * <p>This service does not load or mutate data, infer reconnect patches, or change Active flags.
 * React remains responsible for authoring diagnostics. Structural issues remain distinguishable
 * from warning-only variable health so callers cannot turn variable diagnostics into an execution
 * permission gate.
 */
public final class ExecutionRelationshipPreflightService {
    private static final Set<String> ELEMENT_TARGET_ACTIONS =
            Set.of("GET", "SET", "E", "CK", "PDF CHECK", "CSV CHECK");
    private static final Set<String> RUNTIME_READER_ACTIONS = Set.of("E", "CK");
    private static final Set<String> RUNTIME_WRITER_ACTIONS = Set.of("GET", "SET");
    private static final Set<String> LOOP_ACTIONS = Set.of("LOOP", "REFRESH_LOOP");
    private static final Set<String> LOOP_COMMAND_ANCHOR_ACTIONS =
            Set.of("GET", "SET", "E", "GOTO");
    private static final Set<String> CONDITIONAL_ACTIONS =
            Set.of("IF", "ELSEIF", "ELSE", "ENDIF");
    private static final Set<String> NAVIGATION_ACTIONS = Set.of("GOTO", "EXCEL GOTO");

    public ExecutionPreflightResult preflight(
            ExecutionPreflightSnapshot snapshot, RunScope runScope) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(runScope, "runScope");

        IssueCollector issues = new IssueCollector();
        IndexedFacts facts = index(snapshot, issues);
        LinkedHashSet<Integer> reachableBlocks =
                initialReachableBlocks(facts, runScope, issues);
        expandNavigationTargets(facts, reachableBlocks);

        List<InstructionFact> reachableRows = facts.orderedInstructions().stream()
                .filter(row -> reachableBlocks.contains(row.blockId()))
                .filter(row -> isEffectivelyActive(row, facts.blocksById()))
                .toList();
        Set<Integer> reachableInstructionIds = new HashSet<>();
        reachableRows.forEach(row -> reachableInstructionIds.add(row.id()));

        for (InstructionFact row : reachableRows) {
            validateElementTarget(row, facts, issues);
            validateVariableBinding(row, facts, issues);
            validateVariableWriterOrder(
                    row, facts, reachableInstructionIds, issues);
            validateLoopAnchor(row, facts, issues);
            validateBlockTarget(row, facts, issues);
        }
        validateConditionalStructure(reachableRows, facts, issues);

        List<Integer> orderedReachableBlockIds = facts.orderedBlocks().stream()
                .filter(block -> reachableBlocks.contains(block.id()))
                .map(BlockFact::id)
                .toList();
        List<Integer> orderedReachableInstructionIds =
                reachableRows.stream().map(InstructionFact::id).toList();
        List<Issue> orderedIssues = issues.sorted(facts);

        return new ExecutionPreflightResult(
                ExecutionPreflightResult.statusFor(orderedIssues),
                snapshot.owner(),
                runScope,
                orderedReachableBlockIds,
                orderedReachableInstructionIds,
                orderedIssues);
    }

    private IndexedFacts index(
            ExecutionPreflightSnapshot snapshot, IssueCollector issues) {
        Map<Integer, BlockFact> blocksById = new LinkedHashMap<>();
        for (BlockFact block : snapshot.blocks()) {
            if (blocksById.putIfAbsent(block.id(), block) != null) {
                issues.add(
                        IssueCode.DUPLICATE_BLOCK_ID,
                        RelationshipKind.SNAPSHOT,
                        block.id(),
                        null,
                        "The execution snapshot contains Block #" + block.id() + " more than once.");
            }
        }

        Map<Integer, InstructionFact> instructionsById = new LinkedHashMap<>();
        for (InstructionFact row : snapshot.instructions()) {
            if (instructionsById.putIfAbsent(row.id(), row) != null) {
                issues.add(
                        IssueCode.DUPLICATE_INSTRUCTION_ID,
                        RelationshipKind.SNAPSHOT,
                        row.blockId(),
                        row.id(),
                        "The execution snapshot contains instruction #" + row.id() + " more than once.");
            }
            if (!blocksById.containsKey(row.blockId())) {
                issues.add(
                        IssueCode.INSTRUCTION_BLOCK_NOT_FOUND,
                        RelationshipKind.SNAPSHOT,
                        row.blockId(),
                        row.id(),
                        "Instruction #" + row.id() + " references an unowned Block #"
                                + row.blockId() + ".");
            }
        }

        Map<Integer, VariableFact> variablesById = new LinkedHashMap<>();
        for (VariableFact variable : snapshot.variables()) {
            if (variablesById.putIfAbsent(variable.id(), variable) != null) {
                issues.add(
                        IssueCode.DUPLICATE_VARIABLE_ID,
                        RelationshipKind.SNAPSHOT,
                        null,
                        null,
                        "The execution snapshot contains variable #" + variable.id() + " more than once.");
            }
        }

        Comparator<BlockFact> blockOrder =
                Comparator.comparingInt(BlockFact::order).thenComparingInt(BlockFact::id);
        List<BlockFact> orderedBlocks =
                blocksById.values().stream().sorted(blockOrder).toList();
        Comparator<InstructionFact> instructionOrder = Comparator
                .comparingInt((InstructionFact row) -> {
                    BlockFact block = blocksById.get(row.blockId());
                    return block == null ? Integer.MAX_VALUE : block.order();
                })
                .thenComparingInt(InstructionFact::blockId)
                .thenComparingInt(InstructionFact::order)
                .thenComparingInt(InstructionFact::id);
        List<InstructionFact> orderedInstructions =
                snapshot.instructions().stream().sorted(instructionOrder).toList();

        return new IndexedFacts(
                blocksById,
                instructionsById,
                variablesById,
                orderedBlocks,
                orderedInstructions,
                instructionOrder);
    }

    private LinkedHashSet<Integer> initialReachableBlocks(
            IndexedFacts facts, RunScope runScope, IssueCollector issues) {
        LinkedHashSet<Integer> reachable = new LinkedHashSet<>();
        if (runScope.kind() == RunScope.Kind.ALL) {
            facts.orderedBlocks().stream()
                    .filter(BlockFact::active)
                    .map(BlockFact::id)
                    .forEach(reachable::add);
            return reachable;
        }

        BlockFact selected = facts.blocksById().get(runScope.selectedBlockId());
        if (selected == null) {
            issues.add(
                    IssueCode.SELECTED_BLOCK_NOT_FOUND,
                    RelationshipKind.RUN_SCOPE,
                    runScope.selectedBlockId(),
                    null,
                    "The requested Block #" + runScope.selectedBlockId()
                            + " is not owned by this Bot Job.");
            return reachable;
        }

        if (runScope.kind() == RunScope.Kind.ONE) {
            if (selected.active()) {
                reachable.add(selected.id());
            }
            return reachable;
        }

        boolean selectedReached = false;
        for (BlockFact block : facts.orderedBlocks()) {
            if (block.id() == selected.id()) {
                selectedReached = true;
            }
            if (selectedReached && block.active()) {
                reachable.add(block.id());
            }
        }
        return reachable;
    }

    /**
     * A valid GOTO target can execute even when ONE or FROM_BLOCK did not initially include it.
     * Add those owned active target Blocks transitively so their active instructions are checked.
     */
    private void expandNavigationTargets(
            IndexedFacts facts, LinkedHashSet<Integer> reachableBlocks) {
        boolean changed;
        do {
            changed = false;
            for (InstructionFact row : facts.orderedInstructions()) {
                if (!reachableBlocks.contains(row.blockId())
                        || !isEffectivelyActive(row, facts.blocksById())
                        || !NAVIGATION_ACTIONS.contains(action(row))
                        || row.parentBlockId() == null
                        || row.parentBlockId() == row.blockId()) {
                    continue;
                }
                BlockFact target = facts.blocksById().get(row.parentBlockId());
                if (target != null && target.active() && reachableBlocks.add(target.id())) {
                    changed = true;
                }
            }
        } while (changed);
    }

    private void validateElementTarget(
            InstructionFact row, IndexedFacts facts, IssueCollector issues) {
        String action = action(row);
        if (!ELEMENT_TARGET_ACTIONS.contains(action)) {
            return;
        }
        Integer parentId = row.parentId();
        if (parentId == null) {
            relationshipIssue(
                    issues,
                    IssueCode.MISSING_ELEMENT_TARGET,
                    RelationshipKind.ELEMENT_TARGET,
                    row,
                    action + " has no Web Element target.");
            return;
        }
        InstructionFact target = facts.instructionsById().get(parentId);
        if (target == null) {
            relationshipIssue(
                    issues,
                    IssueCode.DANGLING_ELEMENT_TARGET,
                    RelationshipKind.ELEMENT_TARGET,
                    row,
                    action + " references missing Web Element instruction #" + parentId + ".");
            return;
        }
        if (target.blockId() != row.blockId()) {
            relationshipIssue(
                    issues,
                    IssueCode.ELEMENT_TARGET_WRONG_BLOCK,
                    RelationshipKind.ELEMENT_TARGET,
                    row,
                    action + " target instruction #" + parentId + " is in another Block.");
            return;
        }
        if (!supportsElement(action, target)) {
            relationshipIssue(
                    issues,
                    IssueCode.INCOMPATIBLE_ELEMENT_TARGET,
                    RelationshipKind.ELEMENT_TARGET,
                    row,
                    action + " target instruction #" + parentId + " is not a compatible Web Element.");
            return;
        }
        if (!isEffectivelyActive(target, facts.blocksById())) {
            relationshipIssue(
                    issues,
                    IssueCode.INACTIVE_ELEMENT_TARGET,
                    RelationshipKind.ELEMENT_TARGET,
                    row,
                    action + " target instruction #" + parentId + " is inactive.");
            return;
        }
        if (target.order() >= row.order()) {
            relationshipIssue(
                    issues,
                    IssueCode.ELEMENT_TARGET_ORDER,
                    RelationshipKind.ELEMENT_TARGET,
                    row,
                    action + " target instruction #" + parentId + " must execute first.");
        }
    }

    private void validateVariableBinding(
            InstructionFact row, IndexedFacts facts, IssueCollector issues) {
        String action = action(row);
        if (!ELEMENT_TARGET_ACTIONS.contains(action)) {
            return;
        }
        Integer variableId = row.variableId();
        if (variableId == null) {
            relationshipIssue(
                    issues,
                    IssueCode.MISSING_VARIABLE_BINDING,
                    RelationshipKind.VARIABLE_BINDING,
                    row,
                    action + " has no variable binding.");
            return;
        }
        VariableFact variable = facts.variablesById().get(variableId);
        if (variable == null) {
            relationshipIssue(
                    issues,
                    IssueCode.DANGLING_VARIABLE_BINDING,
                    RelationshipKind.VARIABLE_BINDING,
                    row,
                    action + " references missing variable #" + variableId + ".");
            return;
        }
        if (!CommandRegistry.supportsVariableType(action, variable.type())) {
            relationshipIssue(
                    issues,
                    IssueCode.INCOMPATIBLE_VARIABLE_TYPE,
                    RelationshipKind.VARIABLE_BINDING,
                    row,
                    action + " cannot use variable #" + variableId + " of type "
                            + String.valueOf(variable.type()) + ".");
        }
    }

    private void validateVariableWriterOrder(
            InstructionFact row,
            IndexedFacts facts,
            Set<Integer> reachableInstructionIds,
            IssueCollector issues) {
        String action = action(row);
        if (!RUNTIME_READER_ACTIONS.contains(action)
                || row.variableId() == null
                || !facts.variablesById().containsKey(row.variableId())) {
            return;
        }

        List<InstructionFact> activeWriters = facts.orderedInstructions().stream()
                .filter(candidate -> row.variableId().equals(candidate.variableId()))
                .filter(candidate -> RUNTIME_WRITER_ACTIONS.contains(action(candidate)))
                .filter(candidate -> isEffectivelyActive(candidate, facts.blocksById()))
                .toList();
        boolean hasReachableWriterBefore = activeWriters.stream()
                .anyMatch(writer -> reachableInstructionIds.contains(writer.id())
                        && facts.instructionOrder().compare(writer, row) < 0);
        if (hasReachableWriterBefore) {
            return;
        }

        boolean hasReachableWriterAfter = activeWriters.stream()
                .anyMatch(writer -> reachableInstructionIds.contains(writer.id())
                        && facts.instructionOrder().compare(writer, row) >= 0);
        if (hasReachableWriterAfter) {
            relationshipIssue(
                    issues,
                    IssueCode.RUNTIME_VALUE_WRITER_AFTER_READER,
                    RelationshipKind.VARIABLE_ORDER,
                    row,
                    action + " reads variable #" + row.variableId()
                            + " before an active GET or SET writes it.");
            return;
        }

        boolean hasWriterOutsideScope = activeWriters.stream()
                .anyMatch(writer -> !reachableInstructionIds.contains(writer.id()));
        if (hasWriterOutsideScope) {
            relationshipIssue(
                    issues,
                    IssueCode.RUNTIME_VALUE_WRITER_OUTSIDE_SCOPE,
                    RelationshipKind.VARIABLE_ORDER,
                    row,
                    action + " reads variable #" + row.variableId()
                            + " but its active GET or SET is outside this run scope.");
            return;
        }

        relationshipIssue(
                issues,
                IssueCode.MISSING_RUNTIME_VALUE_WRITER,
                RelationshipKind.VARIABLE_ORDER,
                row,
                action + " reads variable #" + row.variableId()
                        + " without an active GET or SET writer.");
    }

    private void validateLoopAnchor(
            InstructionFact row, IndexedFacts facts, IssueCollector issues) {
        String action = action(row);
        if (!LOOP_ACTIONS.contains(action)) {
            return;
        }
        Integer parentId = row.parentId();
        if (parentId == null) {
            relationshipIssue(
                    issues,
                    IssueCode.MISSING_LOOP_ANCHOR,
                    RelationshipKind.LOOP_ANCHOR,
                    row,
                    action + " has no compatible anchor.");
            return;
        }
        InstructionFact target = facts.instructionsById().get(parentId);
        if (target == null) {
            relationshipIssue(
                    issues,
                    IssueCode.DANGLING_LOOP_ANCHOR,
                    RelationshipKind.LOOP_ANCHOR,
                    row,
                    action + " references missing anchor instruction #" + parentId + ".");
            return;
        }
        if (target.blockId() != row.blockId()) {
            relationshipIssue(
                    issues,
                    IssueCode.LOOP_ANCHOR_WRONG_BLOCK,
                    RelationshipKind.LOOP_ANCHOR,
                    row,
                    action + " anchor instruction #" + parentId + " is in another Block.");
            return;
        }
        if (!isCompatibleLoopAnchor(target)) {
            relationshipIssue(
                    issues,
                    IssueCode.INCOMPATIBLE_LOOP_ANCHOR,
                    RelationshipKind.LOOP_ANCHOR,
                    row,
                    action + " anchor instruction #" + parentId + " is not compatible.");
            return;
        }
        if (!isEffectivelyActive(target, facts.blocksById())) {
            relationshipIssue(
                    issues,
                    IssueCode.INACTIVE_LOOP_ANCHOR,
                    RelationshipKind.LOOP_ANCHOR,
                    row,
                    action + " anchor instruction #" + parentId + " is inactive.");
            return;
        }
        if (target.order() >= row.order()) {
            relationshipIssue(
                    issues,
                    IssueCode.LOOP_ANCHOR_ORDER,
                    RelationshipKind.LOOP_ANCHOR,
                    row,
                    action + " anchor instruction #" + parentId + " must execute first.");
        }
    }

    private void validateBlockTarget(
            InstructionFact row, IndexedFacts facts, IssueCollector issues) {
        String action = action(row);
        if (!NAVIGATION_ACTIONS.contains(action)) {
            return;
        }
        Integer targetId = row.parentBlockId();
        if (targetId == null) {
            relationshipIssue(
                    issues,
                    IssueCode.MISSING_BLOCK_TARGET,
                    RelationshipKind.BLOCK_TARGET,
                    row,
                    action + " has no destination Block.");
            return;
        }
        BlockFact target = facts.blocksById().get(targetId);
        if (target == null) {
            relationshipIssue(
                    issues,
                    IssueCode.DANGLING_BLOCK_TARGET,
                    RelationshipKind.BLOCK_TARGET,
                    row,
                    action + " references unowned destination Block #" + targetId + ".");
            return;
        }
        if (target.id() == row.blockId()) {
            relationshipIssue(
                    issues,
                    IssueCode.BLOCK_TARGET_EQUALS_CONTAINING_BLOCK,
                    RelationshipKind.BLOCK_TARGET,
                    row,
                    action + " destination must differ from containing Block #" + row.blockId() + ".");
            return;
        }
        if (!target.active()) {
            relationshipIssue(
                    issues,
                    IssueCode.INACTIVE_BLOCK_TARGET,
                    RelationshipKind.BLOCK_TARGET,
                    row,
                    action + " destination Block #" + targetId + " is inactive.");
        }
    }

    private void validateConditionalStructure(
            List<InstructionFact> reachableRows,
            IndexedFacts facts,
            IssueCollector issues) {
        Map<Integer, List<InstructionFact>> rowsByBlock = new LinkedHashMap<>();
        for (InstructionFact row : reachableRows) {
            if (CONDITIONAL_ACTIONS.contains(action(row))) {
                rowsByBlock.computeIfAbsent(row.blockId(), ignored -> new ArrayList<>()).add(row);
            }
        }

        for (List<InstructionFact> rows : rowsByBlock.values()) {
            rows.sort(facts.instructionOrder());
            Deque<ConditionalFrame> stack = new ArrayDeque<>();
            for (InstructionFact row : rows) {
                String action = action(row);
                if ("IF".equals(action)) {
                    if (!Integer.valueOf(row.id()).equals(row.parentId())) {
                        relationshipIssue(
                                issues,
                                IssueCode.CONDITIONAL_ROOT_NOT_SELF,
                                RelationshipKind.CONDITIONAL_ROOT,
                                row,
                                "IF instruction #" + row.id() + " must reference itself.");
                    }
                    stack.push(new ConditionalFrame(row));
                    continue;
                }

                if (stack.isEmpty()) {
                    relationshipIssue(
                            issues,
                            IssueCode.ORPHAN_CONDITIONAL_BOUNDARY,
                            RelationshipKind.CONDITIONAL_ROOT,
                            row,
                            action + " instruction #" + row.id() + " has no active matching IF.");
                    continue;
                }

                ConditionalFrame frame = stack.peek();
                if (!Integer.valueOf(frame.root().id()).equals(row.parentId())) {
                    relationshipIssue(
                            issues,
                            IssueCode.CONDITIONAL_ROOT_MISMATCH,
                            RelationshipKind.CONDITIONAL_ROOT,
                            row,
                            action + " instruction #" + row.id() + " does not reference IF #"
                                    + frame.root().id() + ".");
                    continue;
                }
                if ("ELSEIF".equals(action) && frame.elseSeen()) {
                    relationshipIssue(
                            issues,
                            IssueCode.ELSEIF_AFTER_ELSE,
                            RelationshipKind.CONDITIONAL_ROOT,
                            row,
                            "ELSEIF instruction #" + row.id() + " appears after ELSE.");
                    continue;
                }
                if ("ELSE".equals(action)) {
                    if (frame.elseSeen()) {
                        relationshipIssue(
                                issues,
                                IssueCode.DUPLICATE_ELSE,
                                RelationshipKind.CONDITIONAL_ROOT,
                                row,
                                "IF #" + frame.root().id() + " contains more than one ELSE.");
                    } else {
                        frame.markElseSeen();
                    }
                    continue;
                }
                if ("ENDIF".equals(action)) {
                    stack.pop();
                }
            }
            for (ConditionalFrame frame : stack) {
                relationshipIssue(
                        issues,
                        IssueCode.MISSING_ENDIF,
                        RelationshipKind.CONDITIONAL_ROOT,
                        frame.root(),
                        "IF instruction #" + frame.root().id() + " has no active matching ENDIF.");
            }
        }
    }

    private static boolean supportsElement(String action, InstructionFact target) {
        return isWebElement(target)
                && (!"SET".equals(action) || CommandRegistry.supportsTag(action, target.tagName()));
    }

    private static boolean isWebElement(InstructionFact row) {
        String canonical = action(row);
        return !CommandRegistry.isSpecialAction(canonical) && !"BACK".equals(canonical);
    }

    private static boolean isCompatibleLoopAnchor(InstructionFact row) {
        return isWebElement(row) || LOOP_COMMAND_ANCHOR_ACTIONS.contains(action(row));
    }

    private static boolean isEffectivelyActive(
            InstructionFact row, Map<Integer, BlockFact> blocksById) {
        BlockFact block = blocksById.get(row.blockId());
        return row.active() && block != null && block.active();
    }

    private static String action(InstructionFact row) {
        return CommandRegistry.canonicalize(row.action()).toUpperCase(Locale.ROOT);
    }

    private static void relationshipIssue(
            IssueCollector issues,
            IssueCode code,
            RelationshipKind kind,
            InstructionFact row,
            String message) {
        issues.add(code, kind, row.blockId(), row.id(), message);
    }

    private record IndexedFacts(
            Map<Integer, BlockFact> blocksById,
            Map<Integer, InstructionFact> instructionsById,
            Map<Integer, VariableFact> variablesById,
            List<BlockFact> orderedBlocks,
            List<InstructionFact> orderedInstructions,
            Comparator<InstructionFact> instructionOrder) {}

    private static final class ConditionalFrame {
        private final InstructionFact root;
        private boolean elseSeen;

        private ConditionalFrame(InstructionFact root) {
            this.root = root;
        }

        private InstructionFact root() {
            return root;
        }

        private boolean elseSeen() {
            return elseSeen;
        }

        private void markElseSeen() {
            elseSeen = true;
        }
    }

    private static final class IssueCollector {
        private final Map<IssueKey, Issue> issues = new LinkedHashMap<>();

        private void add(
                IssueCode code,
                RelationshipKind kind,
                Integer blockId,
                Integer instructionId,
                String message) {
            IssueKey key = new IssueKey(code, blockId, instructionId);
            issues.putIfAbsent(key, new Issue(code, kind, blockId, instructionId, message));
        }

        private List<Issue> sorted(IndexedFacts facts) {
            Map<Integer, Integer> blockOrder = new HashMap<>();
            facts.orderedBlocks().forEach(block -> blockOrder.put(block.id(), block.order()));
            Map<Integer, Integer> instructionOrder = new HashMap<>();
            facts.orderedInstructions().forEach(row -> instructionOrder.put(row.id(), row.order()));
            return issues.values().stream()
                    .sorted(Comparator
                            .comparingInt((Issue issue) ->
                                    issue.blockId() == null
                                            ? Integer.MAX_VALUE
                                            : blockOrder.getOrDefault(issue.blockId(), Integer.MAX_VALUE))
                            .thenComparingInt(issue ->
                                    issue.instructionId() == null
                                            ? Integer.MAX_VALUE
                                            : instructionOrder.getOrDefault(
                                                    issue.instructionId(), Integer.MAX_VALUE))
                            .thenComparing(issue -> issue.kind().ordinal())
                            .thenComparing(issue -> issue.code().name()))
                    .toList();
        }
    }

    private record IssueKey(IssueCode code, Integer blockId, Integer instructionId) {}
}
