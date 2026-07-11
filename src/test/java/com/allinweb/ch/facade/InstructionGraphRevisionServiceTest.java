package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.allinweb.ch.model.InstructionLoad;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstructionGraphRevisionServiceTest {
    private final InstructionGraphRevisionService service = new InstructionGraphRevisionService();

    @Test
    void revisionIsStableWhenInputListOrderChanges() {
        InstructionLoad first = row(1, 10, 1, "CLICK", null);
        InstructionLoad second = row(2, 10, 2, "SET", 1);

        assertEquals(service.revision(List.of(first, second)), service.revision(List.of(second, first)));
    }

    @Test
    void staleRevisionDiffersAfterAnyPersistedGraphFieldChanges() {
        InstructionLoad row = row(1, 10, 1, "CLICK", null);
        String original = service.revision(List.of(row));

        row.setInstructionOrderNumber(2);

        assertNotEquals(original, service.revision(List.of(row)));
    }

    private InstructionLoad row(int id, int blockId, int order, String action, Integer parentId) {
        InstructionLoad row = new InstructionLoad();
        row.setId(id);
        row.setBlockId(blockId);
        row.setInstructionOrderNumber(order);
        row.setActions(action);
        row.setParentId(parentId);
        row.setOperation(action);
        return row;
    }
}
