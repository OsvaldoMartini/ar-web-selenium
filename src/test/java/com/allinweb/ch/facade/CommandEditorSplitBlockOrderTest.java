package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BlockOrderDetailDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandEditorSplitBlockOrderTest {

    @Test
    void acceptsLaterBlocksByIdentityWhenPayloadOrderDiffersFromDatabaseOrder() {
        List<BlockLoadDTO> expected = List.of(block(242, 2), block(45, 3), block(44, 4), block(8, 5));
        List<BlockOrderDetailDTO> submittedInNumericIdOrder =
                List.of(update(8, 6), update(44, 5), update(45, 4), update(242, 3));

        assertNull(CommandEditorService.validateLaterBlockOrders(5, expected, submittedInNumericIdOrder));
    }

    @Test
    void rejectsDuplicateOrStaleLaterBlockUpdates() {
        List<BlockLoadDTO> expected = List.of(block(242, 2), block(45, 3));

        assertEquals(
                "The later block order updates are stale or invalid.",
                CommandEditorService.validateLaterBlockOrders(
                        5, expected, List.of(update(242, 3), update(242, 4))));
        assertEquals(
                "The later block order updates are stale or invalid.",
                CommandEditorService.validateLaterBlockOrders(
                        5, expected, List.of(update(242, 3), update(45, 99))));
    }

    private static BlockLoadDTO block(int id, int order) {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(id);
        block.setBlockOrderNumber(order);
        return block;
    }

    private static BlockOrderDetailDTO update(int id, int order) {
        BlockOrderDetailDTO update = new BlockOrderDetailDTO();
        update.setBlockId(id);
        update.setBotJobId(5);
        update.setBlockOrderNumber(order);
        return update;
    }
}
