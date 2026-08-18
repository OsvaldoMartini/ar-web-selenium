package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.model.BlockDetailsDTO;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BlockMoveDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerModalBlockCreationServiceTest {
    private final ScannerModalBlockCreationService service = new ScannerModalBlockCreationService();

    @Test
    void renumbersInsertsRefreshesAndPublishes() {
        RecordingOperations operations = new RecordingOperations();
        operations.blocks = List.of(block(7, 1, "Keep"), block(7, 2, "Shift"));

        ErrorMessage error = service.create("New", 2, context(), operations);

        assertNull(error);
        assertEquals("New", operations.inserted.getBlockName());
        assertEquals(2, operations.inserted.getBlockOrderNumber());
        assertEquals(7, operations.inserted.getBotJobId());
        assertEquals(
                List.of(
                        "blocks",
                        "update:7:1",
                        "memory:7:1",
                        "insert:7",
                        "reloadBlocks:7",
                        "reloadJobs:7",
                        "publish:11"),
                operations.calls);
    }

    @Test
    void returnsRenumberErrorBeforeInsert() {
        RecordingOperations operations = new RecordingOperations();
        operations.blocks = List.of(block(7, 2, "Shift"));
        operations.updateError = new ErrorMessage("Update", "Failed", "boom");

        ErrorMessage error = service.create("New", 2, context(), operations);

        assertSame(operations.updateError, error);
        assertEquals(List.of("blocks", "update:7:1"), operations.calls);
    }

    @Test
    void returnsInsertErrorAfterOptionalRenumber() {
        RecordingOperations operations = new RecordingOperations();
        operations.blocks = List.of(block(7, 1, "Keep"));
        operations.insertError = new ErrorMessage("Insert", "Failed", "boom");

        ErrorMessage error = service.create("New", 2, context(), operations);

        assertSame(operations.insertError, error);
        assertEquals(List.of("blocks", "insert:7"), operations.calls);
    }

    @Test
    void treatsBroadcastFailureAsNonFatal() {
        RecordingOperations operations = new RecordingOperations();
        operations.blocks = List.of();
        operations.failPublish = true;

        ErrorMessage error = service.create("New", 1, context(), operations);

        assertNull(error);
        assertEquals(
                List.of("blocks", "insert:7", "reloadBlocks:7", "reloadJobs:7", "publish:11", "publishFailed:boom"),
                operations.calls);
    }

    private static ScannerModalBlockCreationService.Context context() {
        return new ScannerModalBlockCreationService.Context(7, 11, new ScannerCreateBlockPlanner());
    }

    private static BlockLoadDTO block(Integer botJobId, Integer order, String name) {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(order == null ? null : order * 10);
        block.setBotJobId(botJobId);
        block.setHomeBankingId(101);
        block.setBlockOrderNumber(order);
        block.setName(name);
        return block;
    }

    private static final class RecordingOperations implements ScannerModalBlockCreationService.Operations {
        private final List<String> calls = new ArrayList<>();
        private List<BlockLoadDTO> blocks = List.of();
        private ErrorMessage updateError;
        private ErrorMessage insertError;
        private BlockDetailsDTO inserted;
        private boolean failPublish;

        @Override
        public List<BlockLoadDTO> blocks() {
            calls.add("blocks");
            return blocks;
        }

        @Override
        public ErrorMessage updateBlockOrder(int botJobId, List<BlockLoadDTO> toRenumber) {
            calls.add("update:" + botJobId + ":" + toRenumber.size());
            return updateError;
        }

        @Override
        public void updateMemoryBlockOrder(int botJobId, List<BlockLoadDTO> toRenumber) {
            calls.add("memory:" + botJobId + ":" + toRenumber.size());
        }

        @Override
        public ErrorMessage insertBlock(int botJobId, BlockDetailsDTO block) {
            calls.add("insert:" + botJobId);
            inserted = block;
            return insertError;
        }

        @Override
        public void reloadBlocks(int botJobId) {
            calls.add("reloadBlocks:" + botJobId);
        }

        @Override
        public void reloadCompleteJobs(int botJobId) {
            calls.add("reloadJobs:" + botJobId);
        }

        @Override
        public void publishUpdateBlocks(int homeBankingId, BlockMoveDTO signal) {
            calls.add("publish:" + homeBankingId);
            if (failPublish) {
                throw new IllegalStateException("boom");
            }
        }

        @Override
        public void publishUpdateBlocksFailed(Exception error) {
            calls.add("publishFailed:" + error.getMessage());
        }
    }
}
