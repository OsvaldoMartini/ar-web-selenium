package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.InstructionLoad;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstructionDeleteImpactServiceTest {
    private final InstructionDeleteImpactService service = new InstructionDeleteImpactService();

    @Test
    void resolvesOrdinaryRowAndCompleteConditionalFamily() {
        List<InstructionLoad> rows = List.of(
                row(1, 1, "IF", 1), row(2, 2, "CLICK", 1), row(3, 3, "ELSE", 1),
                row(4, 4, "CLICK", 1), row(5, 5, "ENDIF", 1), row(6, 6, "CLICK", null));

        assertEquals(List.of(1, 2, 3, 4, 5), ids(service.resolve(rows.get(2), rows)));
        assertEquals(List.of(6), ids(service.resolve(rows.get(5), rows)));
    }

    @Test
    void resolvesOnlySelectedElseIfBranch() {
        List<InstructionLoad> rows = List.of(
                row(1, 1, "IF", 1), row(2, 2, "CLICK", 1), row(3, 3, "ELSEIF", 1),
                row(4, 4, "CLICK", 3), row(5, 5, "ELSE", 1), row(6, 6, "ENDIF", 1));

        assertEquals(List.of(3, 4), ids(service.resolve(rows.get(2), rows)));
    }

    @Test
    void resolvesLoopParentToBoundarySpan() {
        List<InstructionLoad> rows = List.of(
                row(10, 1, "CLICK", null), row(11, 2, "SET", 10), row(12, 3, "CLICK", null),
                row(13, 4, "LOOP", 10));

        assertEquals(List.of(10, 11, 12, 13), ids(service.resolve(rows.get(3), rows)));
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
