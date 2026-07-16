package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerBlockValidationServiceTest {
    private final ScannerBlockValidationService service = new ScannerBlockValidationService();

    @Test
    void createsMissingBlockAndRefreshesLoadedBlocks() {
        RecordingOperations operations = new RecordingOperations();
        operations.createdBlockId = 42;
        operations.blocksLoaded = true;

        ScannerBlockValidationService.Result result = service.validate("block", 7, operations);

        assertEquals(42, result.currentBlockId());
        assertEquals(42, result.returnBlockId());
        assertFalse(result.showNoBlockSelected());
        assertEquals(List.of("create:block:7", "load:block:7", "refresh"), operations.calls);
    }

    @Test
    void usesSelectedBlockWhenNoBlockIsCreated() {
        RecordingOperations operations = new RecordingOperations();
        operations.selectedBlock = new ScannerBlockValidationService.SelectedBlock(99, 4);

        ScannerBlockValidationService.Result result = service.validate("block", 7, operations);

        assertEquals(99, result.currentBlockId());
        assertEquals(3, result.executeSpecificBlock());
        assertEquals(99, result.returnBlockId());
        assertFalse(result.showNoBlockSelected());
        assertEquals(List.of("create:block:7", "selected"), operations.calls);
    }

    @Test
    void reportsMissingSelectionAndKeepsOriginalReturnValue() {
        RecordingOperations operations = new RecordingOperations();
        operations.createdBlockId = 0;

        ScannerBlockValidationService.Result result = service.validate("block", 7, operations);

        assertEquals(-1, result.currentBlockId());
        assertEquals(0, result.executeSpecificBlock());
        assertEquals(0, result.returnBlockId());
        assertTrue(result.showNoBlockSelected());
        assertEquals(List.of("create:block:7", "selected"), operations.calls);
    }

    private static final class RecordingOperations implements ScannerBlockValidationService.Operations {
        private final List<String> calls = new ArrayList<>();
        private int createdBlockId;
        private boolean blocksLoaded;
        private ScannerBlockValidationService.SelectedBlock selectedBlock;

        @Override
        public int createBlockIfNone(String blockTable, int ownerId) {
            calls.add("create:" + blockTable + ":" + ownerId);
            return createdBlockId;
        }

        @Override
        public boolean loadBlocks(int ownerId, String blockTable) {
            calls.add("load:" + blockTable + ":" + ownerId);
            return blocksLoaded;
        }

        @Override
        public void refreshBlocks() {
            calls.add("refresh");
        }

        @Override
        public ScannerBlockValidationService.SelectedBlock selectedBlock() {
            calls.add("selected");
            return selectedBlock;
        }
    }
}
