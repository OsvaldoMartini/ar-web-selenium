package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.allinweb.ch.model.InstructionLoad;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionalGraphValidatorTest {
    private final ConditionalGraphValidator validator = new ConditionalGraphValidator();

    @Test
    void acceptsNestedFamiliesAndMultipleElseIfBranches() {
        List<InstructionLoad> rows = List.of(
                row(1, "IF", 1), row(2, "IF", 2), row(3, "ELSE", 2), row(4, "ENDIF", 2),
                row(5, "ELSEIF", 1), row(6, "ELSEIF", 1), row(7, "ELSE", 1), row(8, "ENDIF", 1));

        assertNull(validator.validate(rows));
    }

    @Test
    void rejectsElseIfAfterElse() {
        assertNotNull(validator.validate(List.of(
                row(1, "IF", 1), row(2, "ELSE", 1), row(3, "ELSEIF", 1), row(4, "ENDIF", 1))));
    }

    @Test
    void rejectsBoundaryWithWrongParent() {
        assertNotNull(validator.validate(List.of(row(1, "IF", 1), row(2, "ELSE", 99), row(3, "ENDIF", 1))));
    }

    @Test
    void rejectsUnclosedFamily() {
        assertNotNull(validator.validate(List.of(row(1, "IF", 1), row(2, "CLICK", null))));
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
