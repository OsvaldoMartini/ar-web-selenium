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

    @Test
    void resolvesWebFieldFamilyIncludingPlainActionChildren() {
        // Fix A: a Web Field (O 20) with a special child (SET 21) AND a plain-action child (C 22),
        // all in one block. Dragging ANY member must resolve the whole family so none is left behind
        // to trip InstructionMoveValidator's "must remain in their parent block" (which flags any
        // parentId child, not just special actions). Previously the plain child was excluded.
        List<InstructionLoad> rows = List.of(
                row(20, 1, "O", null), row(21, 2, "SET", 20), row(22, 3, "C", 20), row(23, 4, "O", null));

        assertEquals(List.of(20, 21, 22), ids(service.resolve(rows, 22))); // drag the plain-action child
        assertEquals(List.of(20, 21, 22), ids(service.resolve(rows, 21))); // drag the special child
        assertEquals(List.of(20, 21, 22), ids(service.resolve(rows, 20))); // drag the parent Web Field
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
