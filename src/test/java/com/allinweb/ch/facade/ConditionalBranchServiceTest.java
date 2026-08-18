package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.InstructionLoad;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionalBranchServiceTest {
    private final ConditionalBranchService service = new ConditionalBranchService();

    @Test
    void resolvesElseIfBodyBeforeElse() {
        List<InstructionLoad> rows = List.of(
                row(1, "IF", 1), row(2, "CLICK", null), row(3, "ELSEIF", 1),
                row(4, "GET", null), row(5, "ELSE", 1), row(6, "CLICK", null), row(7, "ENDIF", 1));
        assertEquals(List.of(3, 4), service.elseIfBranchIds(rows, 3));
    }

    @Test
    void includesNestedConditionalFamilyInsideBranch() {
        List<InstructionLoad> rows = List.of(
                row(1, "IF", 1), row(2, "ELSEIF", 1), row(3, "IF", 3), row(4, "CLICK", null),
                row(5, "ENDIF", 3), row(6, "ELSEIF", 1), row(7, "ENDIF", 1));
        assertEquals(List.of(2, 3, 4, 5), service.elseIfBranchIds(rows, 2));
    }

    private InstructionLoad row(int id, String action, Integer parentId) {
        InstructionLoad row = new InstructionLoad();
        row.setId(id);
        row.setActions(action);
        row.setParentId(parentId);
        row.setInstructionOrderNumber(id);
        row.setBlockId(10);
        return row;
    }
}
