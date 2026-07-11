package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.allinweb.ch.model.InstructionLoad;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstructionSplitValidatorTest {
    private final InstructionSplitValidator validator = new InstructionSplitValidator();

    @Test
    void acceptsBoundaryAfterCompleteNestedConditionalFamily() {
        List<InstructionLoad> rows = List.of(
                row(1, "CLICK", null), row(2, "IF", 2), row(3, "IF", 3), row(4, "ENDIF", 3),
                row(5, "ELSE", 2), row(6, "ENDIF", 2), row(7, "GET", null), row(8, "CLICK", null));

        assertNull(validator.validate(rows, 7));
    }

    @Test
    void rejectsBoundaryInsideNestedConditionalFamily() {
        List<InstructionLoad> rows = List.of(
                row(1, "IF", 1), row(2, "CLICK", null), row(3, "IF", 3), row(4, "GET", null),
                row(5, "ENDIF", 3), row(6, "ENDIF", 1), row(7, "CLICK", null));

        assertNotNull(validator.validate(rows, 4));
    }

    @Test
    void rejectsBoundaryBetweenLoopParentAndLoop() {
        assertNotNull(validator.validate(
                List.of(row(1, "CLICK", null), row(2, "LOOP", 1), row(3, "GET", null)), 1));
    }

    @Test
    void acceptsBoundaryAfterLoopGroup() {
        assertNull(validator.validate(
                List.of(row(1, "CLICK", null), row(2, "REFRESH_LOOP", 1), row(3, "GET", null)), 2));
    }

    @Test
    void rejectsLastInstructionInsteadOfMovingBoundaryBackward() {
        assertNotNull(validator.validate(List.of(row(1, "CLICK", null), row(2, "GET", null)), 2));
    }

    private InstructionLoad row(int id, String action, Integer parentId) {
        InstructionLoad row = new InstructionLoad();
        row.setId(id);
        row.setActions(action);
        row.setParentId(parentId);
        row.setBlockId(10);
        row.setInstructionOrderNumber(id);
        return row;
    }
}
