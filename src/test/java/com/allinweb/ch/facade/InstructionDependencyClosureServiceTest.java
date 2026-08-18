package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.InstructionDependencyClosureService.ErrorCode;
import com.allinweb.ch.facade.InstructionDependencyClosureService.Mode;
import com.allinweb.ch.facade.InstructionDependencyClosureService.Result;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.VariableLoadDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstructionDependencyClosureServiceTest {
    private final InstructionDependencyClosureService service =
            new InstructionDependencyClosureService();

    @Test
    void componentCopyResolvesParentChildrenAndCompleteVariableFamilyToFixedPoint() {
        List<InstructionLoad> rows = List.of(
                row(10, 7, 1, 1, "O", null, null, null),
                row(11, 7, 1, 2, "GET", 10, 100, 7),
                row(12, 7, 1, 3, "E", 10, 100, 7),
                row(13, 7, 1, 4, "C", 12, null, 7),
                row(14, 7, 1, 5, "O", null, null, null));

        Result result = service.resolve(
                rows, List.of(variable(100, 10)), 12, Mode.COMPONENT_COPY);

        assertTrue(result.successful());
        assertEquals(List.of(10, 11, 12, 13), ids(result));
        assertEquals(List.of(), result.requiredBlockIds());
    }

    @Test
    void combinesExistingNestedConditionalAndLoopSpans() {
        List<InstructionLoad> rows = List.of(
                row(1, 7, 1, 1, "O", null, null, null),
                row(2, 7, 1, 2, "IF", 2, null, 7),
                row(3, 7, 1, 3, "C", null, null, null),
                row(4, 7, 1, 4, "IF", 4, null, 7),
                row(5, 7, 1, 5, "O", null, null, null),
                row(6, 7, 1, 6, "ENDIF", 4, null, 7),
                row(7, 7, 1, 7, "ENDIF", 2, null, 7),
                row(8, 7, 1, 8, "LOOP", 1, null, 7));

        Result result = service.resolve(rows, List.of(), 5, Mode.BOT_JOB_MOVE);

        assertTrue(result.successful());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), ids(result));
    }

    @Test
    void componentCopyRecursivelyIncludesWholeExternalGotoBlocks() {
        List<InstructionLoad> rows = List.of(
                row(1, 1, 1, 1, "goto:3", null, null, 2),
                row(2, 1, 1, 2, "O", null, null, null),
                row(20, 2, 2, 1, "O", null, null, null),
                row(21, 2, 2, 2, "C", null, null, null),
                row(22, 2, 2, 3, "Excel GOTO:1", null, null, 3),
                row(30, 3, 3, 1, "O", null, null, null),
                row(31, 3, 3, 2, "C", null, null, null));

        Result result = service.resolve(rows, List.of(), 1, Mode.COMPONENT_COPY);

        assertTrue(result.successful());
        assertEquals(List.of(1, 20, 21, 22, 30, 31), ids(result));
        assertEquals(List.of(2, 3), result.requiredBlockIds());
    }

    @Test
    void botJobMoveDoesNotPullExternalGotoTargetBlock() {
        List<InstructionLoad> rows = List.of(
                row(1, 1, 1, 1, "GOTO", 20, null, 2),
                row(20, 2, 2, 1, "O", null, null, null),
                row(21, 2, 2, 2, "C", null, null, null));

        Result result = service.resolve(rows, List.of(), 1, Mode.BOT_JOB_MOVE);

        assertTrue(result.successful());
        assertEquals(List.of(1), ids(result));
        assertEquals(List.of(), result.requiredBlockIds());
    }

    @Test
    void selectingGotoTargetDoesNotPullIncomingCrossBlockGotoCallers() {
        List<InstructionLoad> rows = List.of(
                row(1, 1, 1, 1, "GOTO", 20, null, 2),
                row(20, 2, 2, 1, "O", null, null, null),
                row(21, 2, 2, 2, "C", 20, null, 2));

        Result result = service.resolve(rows, List.of(), 20, Mode.BOT_JOB_MOVE);

        assertTrue(result.successful());
        assertEquals(List.of(20, 21), ids(result));
        assertEquals(List.of(), result.requiredBlockIds());
    }

    @Test
    void refusesDanglingParentWithoutReturningPartialClosure() {
        List<InstructionLoad> rows =
                List.of(row(5, 7, 1, 1, "E", 999, null, 7));

        Result result = service.resolve(rows, List.of(), 5, Mode.COMPONENT_COPY);

        assertFalse(result.successful());
        assertEquals(ErrorCode.DANGLING_PARENT, result.error().code());
        assertEquals(5, result.error().instructionId());
        assertEquals(999, result.error().relatedId());
        assertEquals(List.of(), result.orderedInstructions());
    }

    @Test
    void refusesDanglingVariableAndVariableOwnerWithSpecificErrors() {
        InstructionLoad user = row(5, 7, 1, 1, "GET", null, 100, 7);

        Result missingVariable =
                service.resolve(List.of(user), List.of(), 5, Mode.COMPONENT_COPY);
        assertEquals(ErrorCode.DANGLING_VARIABLE, missingVariable.error().code());
        assertEquals(100, missingVariable.error().relatedId());

        Result missingOwner = service.resolve(
                List.of(user), List.of(variable(100, 999)), 5, Mode.COMPONENT_COPY);
        assertEquals(ErrorCode.DANGLING_VARIABLE_OWNER, missingOwner.error().code());
        assertEquals(999, missingOwner.error().relatedId());
    }

    @Test
    void refusesCopyWhenGotoTargetBlockIsNotInAuthoritativeGraph() {
        List<InstructionLoad> rows =
                List.of(row(1, 1, 1, 1, "EXCEL GOTO", null, null, 404));

        Result result = service.resolve(rows, List.of(), 1, Mode.COMPONENT_COPY);

        assertFalse(result.successful());
        assertEquals(ErrorCode.DANGLING_GOTO_TARGET_BLOCK, result.error().code());
        assertEquals(1, result.error().instructionId());
        assertEquals(404, result.error().relatedId());
    }

    private List<Integer> ids(Result result) {
        return result.orderedInstructions().stream().map(InstructionLoad::getId).toList();
    }

    private VariableLoadDTO variable(int id, int ownerInstructionId) {
        return new VariableLoadDTO(
                id,
                2,
                5,
                ownerInstructionId,
                "$String",
                "VAR-" + id,
                "",
                "",
                "",
                0);
    }

    private InstructionLoad row(
            int id,
            int blockId,
            int blockOrder,
            int instructionOrder,
            String action,
            Integer parentId,
            Integer variableId,
            Integer parentBlockId) {
        InstructionLoad row = new InstructionLoad();
        row.setId(id);
        row.setBlockId(blockId);
        row.setBlockOrderNumber(blockOrder);
        row.setInstructionOrderNumber(instructionOrder);
        row.setActions(action);
        row.setName(action + " " + id);
        row.setParentId(parentId);
        row.setVariableId(variableId);
        row.setParentBlockId(parentBlockId);
        return row;
    }
}
