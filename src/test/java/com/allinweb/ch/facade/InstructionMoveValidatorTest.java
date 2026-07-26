package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.UpdatedRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstructionMoveValidatorTest {
    private final InstructionMoveValidator validator = new InstructionMoveValidator();

    @Test
    void acceptsOrdinarySwap() {
        List<InstructionLoad> current = List.of(row(1, "CLICK", null, 10, 1), row(2, "CLICK", null, 10, 2));
        assertNull(validator.validate(current, List.of(update(1, 10, 2), update(2, 10, 1))));
    }

    @Test
    void rejectsNoOpMove() {
        List<InstructionLoad> current = List.of(row(1, "CLICK", null, 10, 1), row(2, "CLICK", null, 10, 2));
        assertNotNull(validator.validate(current, List.of(update(1, 10, 1), update(2, 10, 2))));
    }

    @Test
    void rejectsConditionalBoundaryMovement() {
        List<InstructionLoad> current = List.of(
                row(1, "IF", 1, 10, 1), row(2, "CLICK", null, 10, 2),
                row(3, "ELSE", 1, 10, 3), row(4, "ENDIF", 1, 10, 4));
        assertNotNull(validator.validate(current, List.of(update(1, 10, 2), update(2, 10, 1))));
    }

    @Test
    void acceptsCompleteConditionalFamilyCrossBlockMove() {
        List<InstructionLoad> current = List.of(
                row(1, "IF", 1, 10, 1), row(2, "CLICK", null, 10, 2),
                row(3, "ELSE", 1, 10, 3), row(4, "ENDIF", 1, 10, 4),
                row(5, "CLICK", null, 20, 1));
        assertNull(validator.validate(current, List.of(
                update(1, 20, 2), update(2, 20, 3), update(3, 20, 4), update(4, 20, 5))));
    }

    @Test
    void rejectsLoopParentMovement() {
        List<InstructionLoad> current = List.of(
                row(1, "CLICK", null, 10, 1), row(2, "CLICK", null, 10, 2), row(3, "LOOP", 1, 10, 3));
        assertNotNull(validator.validate(current, List.of(update(1, 10, 2), update(2, 10, 1))));
    }

    @Test
    void acceptsCompleteLoopSpanCrossBlockMove() {
        List<InstructionLoad> current = List.of(
                row(1, "CLICK", null, 10, 1), row(2, "CLICK", null, 10, 2),
                row(3, "LOOP", 1, 10, 3), row(4, "CLICK", null, 20, 1));
        assertNull(validator.validate(current, List.of(
                update(1, 20, 2), update(2, 20, 3), update(3, 20, 4))));
    }

    @Test
    void rejectsDependentCommandCrossBlockMovement() {
        List<InstructionLoad> current = List.of(
                row(1, "INPUT", null, 10, 1), row(2, "SET", 1, 10, 2), row(3, "CLICK", null, 20, 1));
        assertNotNull(validator.validate(current, List.of(update(2, 20, 2))));
    }

    @Test
    void rejectsExcelGotoOnlyDestinationBlock() {
        List<InstructionLoad> current = List.of(
                row(1, "CLICK", null, 10, 1), row(2, "EXCEL GOTO", null, 10, 2),
                row(3, "CLICK", null, 20, 1));
        assertNotNull(validator.validate(current, List.of(
                update(1, 20, 2), update(2, 10, 1), update(3, 20, 1))));
    }

    @Test
    void allowsUnrelatedMoveDespitePreExistingWebFieldSeparation() {
        // Fix B: SET 2's parent INPUT 1 is in block 10 while SET 2 already sits in block 20 (a
        // pre-existing/legacy separation). An unrelated move that leaves that untouched family
        // alone (here: swap two CLICKs in block 30) must NOT be blocked by the stale separation.
        List<InstructionLoad> current = List.of(
                row(1, "INPUT", null, 10, 1), row(2, "SET", 1, 20, 1),
                row(3, "CLICK", null, 30, 1), row(4, "CLICK", null, 30, 2));
        assertNull(validator.validate(current, List.of(update(3, 30, 2), update(4, 30, 1))));
    }

    @Test
    void acceptsCompleteWebFieldFamilyCrossBlockMove() {
        // The whole Web-Field family (INPUT 1 + special child SET 2 + plain-action child CLICK 3)
        // moving together to block 20 stays co-located, so it is accepted — this is the state the
        // move-group resolver (Fix A) produces for a Web-Field drag.
        List<InstructionLoad> current = List.of(
                row(1, "INPUT", null, 10, 1), row(2, "SET", 1, 10, 2), row(3, "CLICK", 1, 10, 3),
                row(4, "CLICK", null, 20, 1));
        assertNull(validator.validate(current, List.of(
                update(1, 20, 2), update(2, 20, 3), update(3, 20, 4))));
    }

    private InstructionLoad row(int id, String action, Integer parentId, int blockId, int order) {
        InstructionLoad row = new InstructionLoad();
        row.setId(id);
        row.setActions(action);
        row.setParentId(parentId);
        row.setBlockId(blockId);
        row.setInstructionOrderNumber(order);
        return row;
    }

    private UpdatedRow update(int id, int blockId, int order) {
        UpdatedRow row = new UpdatedRow();
        row.setInstructionId(id);
        row.setBlockId(blockId);
        row.setInstructionOrderNumber(order);
        return row;
    }
}
