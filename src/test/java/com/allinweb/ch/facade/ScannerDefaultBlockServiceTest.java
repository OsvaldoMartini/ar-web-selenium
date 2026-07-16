package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.util.ErrorMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerDefaultBlockServiceTest {
    private final ScannerDefaultBlockService service = new ScannerDefaultBlockService();

    @Test
    void createsDefaultBlockWhenLoadedBlocksAreEmpty() {
        RecordingOperations operations = new RecordingOperations();
        operations.blocksEmpty = true;
        operations.createdIds = List.of(12);

        int result = service.createIfNone("block", 7, operations);

        assertEquals(12, result);
        assertEquals(List.of("load:7:block", "empty", "initiate:block:7:Default Block:1:false", "ids"), operations.calls);
    }

    @Test
    void skipsCreationWhenLoadFails() {
        RecordingOperations operations = new RecordingOperations();
        operations.loadError = new ErrorMessage("Load", "Failed", "boom");

        int result = service.createIfNone("block", 7, operations);

        assertEquals(-1, result);
        assertEquals(List.of("load:7:block"), operations.calls);
    }

    @Test
    void skipsCreationWhenBlocksExist() {
        RecordingOperations operations = new RecordingOperations();
        operations.blocksEmpty = false;

        int result = service.createIfNone("block", 7, operations);

        assertEquals(-1, result);
        assertEquals(List.of("load:7:block", "empty"), operations.calls);
    }

    @Test
    void reportsCreateFailure() {
        RecordingOperations operations = new RecordingOperations();
        operations.blocksEmpty = true;
        operations.initiateError = new ErrorMessage("Create", "Failed", "boom");

        int result = service.createIfNone("block", 7, operations);

        assertEquals(-1, result);
        assertEquals(
                List.of("load:7:block", "empty", "initiate:block:7:Default Block:1:false", "failed:boom"),
                operations.calls);
    }

    private static final class RecordingOperations implements ScannerDefaultBlockService.Operations {
        private final List<String> calls = new ArrayList<>();
        private ErrorMessage loadError;
        private ErrorMessage initiateError;
        private boolean blocksEmpty;
        private List<Integer> createdIds = List.of();

        @Override
        public ErrorMessage loadBlocks(int ownerId, String blockTable) {
            calls.add("load:" + ownerId + ":" + blockTable);
            return loadError;
        }

        @Override
        public boolean blocksEmpty() {
            calls.add("empty");
            return blocksEmpty;
        }

        @Override
        public ErrorMessage initiateBlock(
                String blockTable,
                int ownerId,
                String blockName,
                String blockDescription,
                int blockOrder,
                boolean forceOrder) {
            calls.add("initiate:" + blockTable + ":" + ownerId + ":" + blockName + ":" + blockOrder + ":" + forceOrder);
            return initiateError;
        }

        @Override
        public List<Integer> createdBlockIds() {
            calls.add("ids");
            return createdIds;
        }

        @Override
        public void showOperationFailed(ErrorMessage error) {
            calls.add("failed:" + error.getErrorMessage());
        }
    }
}
