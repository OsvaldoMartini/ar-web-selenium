package com.allinweb.ch.facade.execution;

import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.BLOCK_TARGET_EQUALS_CONTAINING_BLOCK;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.CONDITIONAL_ROOT_MISMATCH;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.CONDITIONAL_ROOT_NOT_SELF;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.DANGLING_BLOCK_TARGET;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.DANGLING_ELEMENT_TARGET;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.DANGLING_LOOP_ANCHOR;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.DANGLING_VARIABLE_BINDING;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.DUPLICATE_ELSE;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.ELEMENT_TARGET_ORDER;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.ELEMENT_TARGET_WRONG_BLOCK;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.ELSEIF_AFTER_ELSE;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.INACTIVE_BLOCK_TARGET;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.INACTIVE_ELEMENT_TARGET;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.INACTIVE_LOOP_ANCHOR;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.INCOMPATIBLE_ELEMENT_TARGET;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.INCOMPATIBLE_LOOP_ANCHOR;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.INCOMPATIBLE_VARIABLE_TYPE;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.LOOP_ANCHOR_ORDER;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.LOOP_ANCHOR_WRONG_BLOCK;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.MISSING_BLOCK_TARGET;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.MISSING_ELEMENT_TARGET;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.MISSING_ENDIF;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.MISSING_LOOP_ANCHOR;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.MISSING_RUNTIME_VALUE_WRITER;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.MISSING_VARIABLE_BINDING;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.ORPHAN_CONDITIONAL_BOUNDARY;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.RUNTIME_VALUE_WRITER_AFTER_READER;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.RUNTIME_VALUE_WRITER_OUTSIDE_SCOPE;
import static com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode.SELECTED_BLOCK_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.execution.ExecutionPreflightResult.Issue;
import com.allinweb.ch.facade.execution.ExecutionPreflightResult.IssueCode;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.BlockFact;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.InstructionFact;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.Owner;
import com.allinweb.ch.facade.execution.ExecutionPreflightSnapshot.VariableFact;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ExecutionRelationshipPreflightServiceTest {
    private static final Owner OWNER = new Owner(2, 5);
    private final ExecutionRelationshipPreflightService service =
            new ExecutionRelationshipPreflightService();

    @Test
    void returnsReadyForAValidActiveGraphAndPreservesExactOwnerAndIds() {
        ExecutionPreflightSnapshot snapshot = snapshot(
                List.of(block(10, 1, true), block(20, 2, true)),
                List.of(
                        element(1, 10, 1, "input", true),
                        row(2, 10, 2, "GET", true, 1, null, 100),
                        row(3, 10, 3, "E", true, 1, null, 100),
                        row(4, 10, 4, "SET", true, 1, null, 101),
                        row(5, 10, 5, "CK", true, 1, null, 101),
                        row(6, 10, 6, "IF", true, 6, null, null),
                        row(7, 10, 7, "ELSE", true, 6, null, null),
                        row(8, 10, 8, "ENDIF", true, 6, null, null),
                        row(9, 10, 9, "LOOP", true, 1, null, null),
                        row(10, 10, 10, "GOTO", true, null, 20, null),
                        row(11, 10, 11, "EXCEL GOTO", true, null, 20, null),
                        element(12, 20, 1, "button", true)),
                List.of(
                        variable(100, "$String", 1),
                        variable(101, "#Numeric", null)));

        ExecutionPreflightResult result = service.preflight(snapshot, RunScope.all());

        assertTrue(result.ready());
        assertEquals(OWNER, result.owner());
        assertEquals(List.of(10, 20), result.reachableBlockIds());
        assertEquals(
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
                result.reachableInstructionIds());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void ignoresBrokenInactiveRowsAndRowsInInactiveBlocks() {
        ExecutionPreflightSnapshot snapshot = snapshot(
                List.of(block(10, 1, true), block(20, 2, false)),
                List.of(
                        element(1, 10, 1, "button", true),
                        row(2, 10, 2, "GET", false, null, null, null),
                        row(3, 20, 1, "LOOP", true, null, null, null)),
                List.of());

        ExecutionPreflightResult result = service.preflight(snapshot, RunScope.all());

        assertTrue(result.ready());
        assertEquals(List.of(10), result.reachableBlockIds());
        assertEquals(List.of(1), result.reachableInstructionIds());
    }

    @Test
    void reportsEveryElementTargetFailureAgainstItsExactSourceInstruction() {
        ExecutionPreflightSnapshot snapshot = snapshot(
                List.of(block(10, 1, true), block(20, 2, true)),
                List.of(
                        element(1, 10, 1, "input", true),
                        element(2, 20, 1, "input", true),
                        row(3, 10, 2, "H", true, null, null, null),
                        element(4, 10, 3, "input", false),
                        element(5, 10, 20, "input", true),
                        element(6, 10, 4, "div", true),
                        row(11, 10, 5, "GET", true, null, null, 100),
                        row(12, 10, 6, "GET", true, 999, null, 100),
                        row(13, 10, 7, "GET", true, 2, null, 100),
                        row(14, 10, 8, "GET", true, 3, null, 100),
                        row(15, 10, 9, "GET", true, 4, null, 100),
                        row(16, 10, 10, "GET", true, 5, null, 100),
                        row(17, 10, 11, "SET", true, 6, null, 100)),
                List.of(variable(100, "$String", null)));

        ExecutionPreflightResult result = service.preflight(snapshot, RunScope.all());

        assertFalse(result.ready());
        assertIssue(result, 11, MISSING_ELEMENT_TARGET);
        assertIssue(result, 12, DANGLING_ELEMENT_TARGET);
        assertIssue(result, 13, ELEMENT_TARGET_WRONG_BLOCK);
        assertIssue(result, 14, INCOMPATIBLE_ELEMENT_TARGET);
        assertIssue(result, 15, INACTIVE_ELEMENT_TARGET);
        assertIssue(result, 16, ELEMENT_TARGET_ORDER);
        assertIssue(result, 17, INCOMPATIBLE_ELEMENT_TARGET);
    }

    @Test
    void reportsLoopAnchorFailuresWithoutTreatingPositionalBodyRowsAsDependencies() {
        ExecutionPreflightSnapshot snapshot = snapshot(
                List.of(block(10, 1, true), block(20, 2, true)),
                List.of(
                        element(1, 10, 1, "button", true),
                        element(2, 20, 1, "button", true),
                        row(3, 10, 2, "H", true, null, null, null),
                        element(4, 10, 3, "button", false),
                        element(5, 10, 20, "button", true),
                        row(11, 10, 5, "LOOP", true, null, null, null),
                        row(12, 10, 6, "REFRESH_LOOP", true, 999, null, null),
                        row(13, 10, 7, "LOOP", true, 2, null, null),
                        row(14, 10, 8, "LOOP", true, 3, null, null),
                        row(15, 10, 9, "LOOP", true, 4, null, null),
                        row(16, 10, 10, "LOOP", true, 5, null, null)),
                List.of());

        ExecutionPreflightResult result = service.preflight(snapshot, RunScope.all());

        assertIssue(result, 11, MISSING_LOOP_ANCHOR);
        assertIssue(result, 12, DANGLING_LOOP_ANCHOR);
        assertIssue(result, 13, LOOP_ANCHOR_WRONG_BLOCK);
        assertIssue(result, 14, INCOMPATIBLE_LOOP_ANCHOR);
        assertIssue(result, 15, INACTIVE_LOOP_ANCHOR);
        assertIssue(result, 16, LOOP_ANCHOR_ORDER);
    }

    @Test
    void reportsNestedConditionalStructureWithStableBoundaryIds() {
        ExecutionPreflightSnapshot snapshot = snapshot(
                List.of(
                        block(10, 1, true),
                        block(20, 2, true),
                        block(30, 3, true),
                        block(40, 4, true),
                        block(50, 5, true),
                        block(60, 6, true)),
                List.of(
                        row(11, 10, 1, "IF", true, null, null, null),
                        row(12, 10, 2, "ENDIF", true, 11, null, null),
                        row(21, 20, 1, "ELSE", true, 999, null, null),
                        row(31, 30, 1, "IF", true, 31, null, null),
                        row(32, 30, 2, "ELSE", true, 31, null, null),
                        row(33, 30, 3, "ELSEIF", true, 31, null, null),
                        row(34, 30, 4, "ENDIF", true, 31, null, null),
                        row(41, 40, 1, "IF", true, 41, null, null),
                        row(42, 40, 2, "ELSE", true, 41, null, null),
                        row(43, 40, 3, "ELSE", true, 41, null, null),
                        row(44, 40, 4, "ENDIF", true, 41, null, null),
                        row(51, 50, 1, "IF", true, 51, null, null),
                        row(52, 50, 2, "ENDIF", true, 999, null, null),
                        row(61, 60, 1, "IF", true, 61, null, null)),
                List.of());

        ExecutionPreflightResult result = service.preflight(snapshot, RunScope.all());

        assertIssue(result, 11, CONDITIONAL_ROOT_NOT_SELF);
        assertIssue(result, 21, ORPHAN_CONDITIONAL_BOUNDARY);
        assertIssue(result, 33, ELSEIF_AFTER_ELSE);
        assertIssue(result, 43, DUPLICATE_ELSE);
        assertIssue(result, 52, CONDITIONAL_ROOT_MISMATCH);
        assertIssue(result, 51, MISSING_ENDIF);
        assertIssue(result, 61, MISSING_ENDIF);
    }

    @Test
    void requiresOwnedActiveDifferentNavigationTargetsForGotoAndExcelGoto() {
        ExecutionPreflightSnapshot snapshot = snapshot(
                List.of(
                        block(10, 1, true),
                        block(20, 2, true),
                        block(30, 3, false)),
                List.of(
                        row(1, 10, 1, "GOTO", true, null, null, null),
                        row(2, 10, 2, "GOTO", true, null, 999, null),
                        row(3, 10, 3, "GOTO", true, null, 10, null),
                        row(4, 10, 4, "GOTO", true, null, 30, null),
                        row(5, 20, 1, "EXCEL GOTO", true, null, 20, null),
                        row(6, 20, 2, "EXCEL GOTO", true, null, 10, null)),
                List.of());

        ExecutionPreflightResult result = service.preflight(snapshot, RunScope.all());

        assertIssue(result, 1, MISSING_BLOCK_TARGET);
        assertIssue(result, 2, DANGLING_BLOCK_TARGET);
        assertIssue(result, 3, BLOCK_TARGET_EQUALS_CONTAINING_BLOCK);
        assertIssue(result, 4, INACTIVE_BLOCK_TARGET);
        assertIssue(result, 5, BLOCK_TARGET_EQUALS_CONTAINING_BLOCK);
        assertNoIssue(result, 6);
    }

    @Test
    void validatesVariableBindingAndRequiresGetOrSetBeforeRuntimeReaders() {
        ExecutionPreflightSnapshot snapshot = snapshot(
                List.of(block(10, 1, true)),
                List.of(
                        element(1, 10, 1, "input", true),
                        row(2, 10, 2, "GET", true, 1, null, null),
                        row(3, 10, 3, "GET", true, 1, null, 999),
                        row(4, 10, 4, "GET", true, 1, null, 300),
                        row(5, 10, 5, "E", true, 1, null, 100),
                        row(6, 10, 6, "CK", true, 1, null, 101),
                        row(7, 10, 7, "GET", true, 1, null, 101)),
                List.of(
                        variable(100, "$String", null),
                        variable(101, "$String", 1),
                        variable(300, "Boolean", null)));

        ExecutionPreflightResult result = service.preflight(snapshot, RunScope.all());

        assertIssue(result, 2, MISSING_VARIABLE_BINDING);
        assertIssue(result, 3, DANGLING_VARIABLE_BINDING);
        assertIssue(result, 4, INCOMPATIBLE_VARIABLE_TYPE);
        assertIssue(result, 5, MISSING_RUNTIME_VALUE_WRITER);
        assertIssue(result, 6, RUNTIME_VALUE_WRITER_AFTER_READER);
        assertNoIssue(result, 7);
    }

    @Test
    void reportsWhenAReaderWriterExistsButWillNotRunInOneScope() {
        ExecutionPreflightSnapshot snapshot = snapshot(
                List.of(block(10, 1, true), block(20, 2, true)),
                List.of(
                        element(1, 10, 1, "input", true),
                        row(2, 10, 2, "GET", true, 1, null, 100),
                        element(3, 20, 1, "input", true),
                        row(4, 20, 2, "E", true, 3, null, 100)),
                List.of(variable(100, "$String", 1)));

        ExecutionPreflightResult result = service.preflight(snapshot, RunScope.one(20));

        assertEquals(List.of(20), result.reachableBlockIds());
        assertIssue(result, 4, RUNTIME_VALUE_WRITER_OUTSIDE_SCOPE);
    }

    @Test
    void appliesAllFromBlockAndOneScopesOnlyToActiveReachableRows() {
        ExecutionPreflightSnapshot snapshot = snapshot(
                List.of(
                        block(10, 1, true),
                        block(20, 2, true),
                        block(30, 3, true)),
                List.of(
                        row(11, 10, 1, "GET", true, null, null, null),
                        element(21, 20, 1, "button", true),
                        row(31, 30, 1, "LOOP", true, null, null, null)),
                List.of());

        ExecutionPreflightResult one = service.preflight(snapshot, RunScope.one(20));
        ExecutionPreflightResult from = service.preflight(snapshot, RunScope.fromBlock(20));
        ExecutionPreflightResult all = service.preflight(snapshot, RunScope.all());

        assertTrue(one.ready());
        assertEquals(List.of(20), one.reachableBlockIds());
        assertEquals(List.of(21), one.reachableInstructionIds());

        assertFalse(from.ready());
        assertEquals(List.of(20, 30), from.reachableBlockIds());
        assertIssue(from, 31, MISSING_LOOP_ANCHOR);
        assertNoIssue(from, 11);

        assertFalse(all.ready());
        assertIssue(all, 11, MISSING_ELEMENT_TARGET);
        assertIssue(all, 31, MISSING_LOOP_ANCHOR);
    }

    @Test
    void includesValidNavigationTargetsInTheReachableOneScope() {
        ExecutionPreflightSnapshot snapshot = snapshot(
                List.of(block(10, 1, true), block(20, 2, true)),
                List.of(
                        row(11, 10, 1, "GET", true, null, null, null),
                        row(21, 20, 1, "GOTO", true, null, 10, null)),
                List.of());

        ExecutionPreflightResult result = service.preflight(snapshot, RunScope.one(20));

        assertEquals(List.of(10, 20), result.reachableBlockIds());
        assertIssue(result, 11, MISSING_ELEMENT_TARGET);
        assertNoIssue(result, 21);
    }

    @Test
    void blocksAnUnknownSelectedBlockWithoutInventingARepair() {
        ExecutionPreflightSnapshot snapshot = snapshot(
                List.of(block(10, 1, true)),
                List.of(element(1, 10, 1, "button", true)),
                List.of());

        ExecutionPreflightResult result = service.preflight(snapshot, RunScope.one(999));

        assertFalse(result.ready());
        assertEquals(List.of(), result.reachableBlockIds());
        assertEquals(Set.of(SELECTED_BLOCK_NOT_FOUND), codes(result));
        assertEquals(999, result.issues().get(0).blockId());
        assertEquals(null, result.issues().get(0).instructionId());
    }

    @Test
    void snapshotsScopesAndResultsAreDefensivelyImmutable() {
        List<BlockFact> mutableBlocks = new ArrayList<>();
        mutableBlocks.add(block(10, 1, true));
        ExecutionPreflightSnapshot snapshot =
                snapshot(mutableBlocks, List.of(element(1, 10, 1, "button", true)), List.of());
        mutableBlocks.clear();

        ExecutionPreflightResult result = service.preflight(snapshot, RunScope.all());

        assertEquals(List.of(10), result.reachableBlockIds());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.blocks().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.reachableBlockIds().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new RunScope(RunScope.Kind.ALL, 10));
    }

    private static ExecutionPreflightSnapshot snapshot(
            List<BlockFact> blocks,
            List<InstructionFact> instructions,
            List<VariableFact> variables) {
        return new ExecutionPreflightSnapshot(OWNER, blocks, instructions, variables);
    }

    private static BlockFact block(int id, int order, boolean active) {
        return new BlockFact(id, order, active);
    }

    private static InstructionFact element(
            int id, int blockId, int order, String tagName, boolean active) {
        return new InstructionFact(
                id, blockId, order, "C", tagName, active, null, null, null);
    }

    private static InstructionFact row(
            int id,
            int blockId,
            int order,
            String action,
            boolean active,
            Integer parentId,
            Integer parentBlockId,
            Integer variableId) {
        return new InstructionFact(
                id,
                blockId,
                order,
                action,
                null,
                active,
                parentId,
                parentBlockId,
                variableId);
    }

    private static VariableFact variable(
            int id, String type, Integer ownerInstructionId) {
        return new VariableFact(id, type, ownerInstructionId);
    }

    private static void assertIssue(
            ExecutionPreflightResult result, int instructionId, IssueCode code) {
        assertTrue(
                result.issues().stream().anyMatch(issue ->
                        Integer.valueOf(instructionId).equals(issue.instructionId())
                                && issue.code() == code),
                () -> "Missing " + code + " for instruction #" + instructionId
                        + "; actual=" + result.issues());
    }

    private static void assertNoIssue(
            ExecutionPreflightResult result, int instructionId) {
        List<Issue> found = result.issues().stream()
                .filter(issue -> Integer.valueOf(instructionId).equals(issue.instructionId()))
                .toList();
        assertTrue(found.isEmpty(), () -> "Unexpected issues for instruction #"
                + instructionId + ": " + found);
    }

    private static Set<IssueCode> codes(ExecutionPreflightResult result) {
        return result.issues().stream().map(Issue::code).collect(Collectors.toSet());
    }
}
