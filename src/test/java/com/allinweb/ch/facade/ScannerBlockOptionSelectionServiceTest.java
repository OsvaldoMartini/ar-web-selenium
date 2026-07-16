package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockOptions;
import org.junit.jupiter.api.Test;

class ScannerBlockOptionSelectionServiceTest {
    private final ScannerBlockOptionSelectionService service = new ScannerBlockOptionSelectionService();

    @Test
    void createsSentinelOption() {
        BlockOptions sentinel = service.createBlockSentinel();

        assertEquals(ScannerBlockOptionSelectionService.CREATE_BLOCK_SENTINEL_TEXT, sentinel.getText());
        assertEquals(ScannerBlockOptionSelectionService.CREATE_BLOCK_SENTINEL_ID, sentinel.getBlockId());
        assertTrue(service.isCreateBlockSentinel(sentinel));
    }

    @Test
    void identifiesRealBlocks() {
        assertTrue(service.isRealBlock(new BlockOptions("1# Login", "Login", null, 12, 1)));
        assertFalse(service.isRealBlock(service.createBlockSentinel()));
        assertFalse(service.isRealBlock(new BlockOptions("Missing", "Missing", null, null, null)));
        assertFalse(service.isRealBlock(new BlockOptions("Negative", "Negative", null, -1, null)));
    }
}
