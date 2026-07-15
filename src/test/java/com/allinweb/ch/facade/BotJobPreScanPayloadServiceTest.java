package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.util.ErrorMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BotJobPreScanPayloadServiceTest {

    @Test
    void buildsCanonicalSortedJobScopedPayload() {
        List<BlockLoadDTO> blocks = new ArrayList<>(List.of(
                block(3, 42, 30, "Third"),
                block(1, 42, 10, "First"),
                block(2, 99, 20, "Other Job"),
                block(4, null, 15, "Shared")));
        BotJobPreScanPayloadService service = new BotJobPreScanPayloadService(new Data(blocks, null));
        ElementDTO element = new ElementDTO();

        BotJobPreScanPayloadService.Result result = service.build(job(), List.of(element));

        assertEquals(42, result.payload().getBotJobId());
        assertEquals(ScannerWorkspaceSessions.PRE_SCANNER_GRID, result.payload().getSessionId());
        assertEquals(ScannerWorkspaceOperations.SEARCH_TERMS, result.payload().getOperationId());
        assertEquals(1, result.payload().getElementDetails().length);
        assertEquals(List.of(1, 4, 3), result.payload().getBlocks().stream()
                .map(option -> (Integer) option.get("blockId"))
                .toList());
    }

    @Test
    void exposesSearchTermsFieldName() {
        BotJobPreScanPayloadService service = new BotJobPreScanPayloadService(new Data(List.of(), null));

        assertEquals(ScannerWorkspaceOperations.SEARCH_TERMS, service.searchTermsFieldName());
    }

    @Test
    void emptyCacheLoadsOnceAndReturnsDatabaseWarningWithEmptyElements() {
        List<BlockLoadDTO> blocks = new ArrayList<>();
        ErrorMessage warning = new ErrorMessage("DB", "Blocks", "failed");
        Data data = new Data(blocks, warning);
        BotJobPreScanPayloadService service = new BotJobPreScanPayloadService(data);

        BotJobPreScanPayloadService.Result result = service.build(job(), null);

        assertEquals(1, data.loads.get());
        assertSame(warning, result.warning());
        assertEquals(0, result.payload().getElementDetails().length);
    }

    private static BotJobLoadDTO job() {
        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setId(42);
        job.setName("Payments");
        job.setHomeBankingId(7);
        return job;
    }

    private static BlockLoadDTO block(int id, Integer jobId, int order, String name) {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(id);
        block.setBotJobId(jobId);
        block.setBlockOrderNumber(order);
        block.setName(name);
        return block;
    }

    private static final class Data implements BotJobPreScanPayloadService.DataPort {
        private final List<BlockLoadDTO> blocks;
        private final ErrorMessage warning;
        private final AtomicInteger loads = new AtomicInteger();
        private Data(List<BlockLoadDTO> blocks, ErrorMessage warning) {
            this.blocks = blocks;
            this.warning = warning;
        }
        public List<BlockLoadDTO> blocks() { return blocks; }
        public ErrorMessage loadBlocks(int botJobId, String botJobName) { loads.incrementAndGet(); return warning; }
    }
}
