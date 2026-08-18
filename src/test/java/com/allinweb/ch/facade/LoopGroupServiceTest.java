package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.InstructionLoad;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoopGroupServiceTest {
    private final LoopGroupService service = new LoopGroupService();

    @Test
    void resolvesParentBodyAndLoopBoundary() {
        List<InstructionLoad> rows = List.of(
                row(1, "CLICK", null, 10), row(2, "GET", 1, 10),
                row(3, "REFRESH_LOOP", 1, 10), row(4, "CLICK", null, 10));
        assertEquals(List.of(1, 2, 3), service.groupIds(rows, 3));
    }

    @Test
    void rejectsCrossBlockParent() {
        assertEquals(List.of(), service.groupIds(
                List.of(row(1, "CLICK", null, 10), row(2, "LOOP", 1, 20)), 2));
    }

    private InstructionLoad row(int id, String action, Integer parentId, int blockId) {
        InstructionLoad row = new InstructionLoad();
        row.setId(id);
        row.setActions(action);
        row.setParentId(parentId);
        row.setInstructionOrderNumber(id);
        row.setBlockId(blockId);
        return row;
    }
}
