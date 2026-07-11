package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.InstructionLoad;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstructionMoveGroupServiceTest {
    private final InstructionMoveGroupService service = new InstructionMoveGroupService();

    @Test
    void resolvesCompleteConditionalSpan() {
        List<InstructionLoad> rows = List.of(
                row(1, 1, "IF", null), row(2, 2, "O", null), row(3, 3, "ELSEIF", 1),
                row(4, 4, "O", null), row(5, 5, "ELSE", 1), row(6, 6, "ENDIF", 1));

        assertEquals(List.of(1, 2, 3, 4, 5, 6), ids(service.resolve(rows, 4)));
    }

    @Test
    void resolvesLoopSpanAndDependentCommands() {
        List<InstructionLoad> rows = List.of(
                row(10, 1, "O", null), row(11, 2, "SET", 10), row(12, 3, "O", null),
                row(13, 4, "LOOP", 10));

        assertEquals(List.of(10, 11, 12, 13), ids(service.resolve(rows, 11)));
    }

    private List<Integer> ids(List<InstructionLoad> rows) {
        return rows.stream().map(InstructionLoad::getId).toList();
    }

    private InstructionLoad row(int id, int order, String action, Integer parentId) {
        InstructionLoad row = new InstructionLoad();
        row.setId(id);
        row.setBlockId(7);
        row.setInstructionOrderNumber(order);
        row.setName(action + " " + id);
        row.setActions(action);
        row.setParentId(parentId);
        return row;
    }
}
