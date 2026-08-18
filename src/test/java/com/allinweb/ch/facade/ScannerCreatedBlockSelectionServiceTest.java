package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockOptions;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerCreatedBlockSelectionServiceTest {

    private final ScannerCreatedBlockSelectionService service = new ScannerCreatedBlockSelectionService();

    @Test
    void findsCreatedBlockByNameIgnoringCase() {
        BlockOptions created = option("Login Flow", 7);

        assertEquals(created, service.findCreatedBlock(List.of(option("Other", 1), created), "login flow").orElseThrow());
    }

    @Test
    void ignoresSentinelAndEmptyInputs() {
        assertTrue(service.findCreatedBlock(null, "Login").isEmpty());
        assertTrue(service.findCreatedBlock(List.of(option("+ Create new block...", null)), "Create new block").isEmpty());
    }

    private static BlockOptions option(String value, Integer blockId) {
        return new BlockOptions(value, value, null, blockId, null);
    }
}
