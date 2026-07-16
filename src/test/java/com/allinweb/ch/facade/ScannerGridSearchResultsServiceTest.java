package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.SplitDTO;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerGridSearchResultsServiceTest {
    private final Gson gson = new Gson();

    @Test
    void buildsEmptyPayloadWithSortedBlocksForTheCurrentBotJob() {
        RecordingBlocks blocks = new RecordingBlocks();
        blocks.rows.add(block(3, 42, 3, "Third"));
        blocks.rows.add(block(1, 7, 1, "Other Job"));
        blocks.rows.add(block(2, 42, 2, "Second"));
        blocks.rows.add(block(4, null, 1, "Global"));
        ScannerGridSearchResultsService service =
                new ScannerGridSearchResultsService(new ScannerGridPublisher(new RecordingSender()), blocks);

        SplitDTO payload = service.emptyPayload(7, 42, "Payments");

        assertEquals(7, payload.getHomeBankingId());
        assertEquals(42, payload.getBotJobId());
        assertEquals("Payments", payload.getBotJobName());
        assertEquals(ScannerWorkspaceOperations.SEARCH_TOOL, payload.getType());
        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, payload.getSessionId());
        assertEquals(ScannerWorkspaceOperations.SEARCH_TERMS, payload.getOperationId());
        assertEquals(0, payload.getElementDetails().length);
        assertEquals(List.of("Global", "Second", "Third"), payload.getBlocks().stream()
                .map(block -> block.get(ScannerWorkspaceBlockOptions.BLOCK_NAME))
                .toList());
    }

    @Test
    void publishesResetThenResultChunks() {
        RecordingBlocks blocks = new RecordingBlocks();
        RecordingSender sender = new RecordingSender();
        ScannerGridSearchResultsService service =
                new ScannerGridSearchResultsService(new ScannerGridPublisher(sender), blocks);
        List<ElementDTO> elements = List.of(element("one"), element("two"), element("three"));

        ScannerGridSearchResultsService.Result result = service.publishResults(7, 42, "Payments", elements);

        assertEquals(3, result.elementCount());
        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, result.destinationSessionId());
        assertEquals(2, sender.messages.size());
        assertEquals(7, sender.messages.get(0).homeBankingId);
        assertEquals(0, sender.messages.get(1).homeBankingId);
        assertEquals(0, gson.fromJson(sender.messages.get(0).json, SplitDTO.class).getElementDetails().length);
        assertEquals(3, gson.fromJson(sender.messages.get(1).json, SplitDTO.class).getElementDetails().length);
    }

    @Test
    void publishesEmptyPayloadOnly() {
        RecordingSender sender = new RecordingSender();
        ScannerGridSearchResultsService service =
                new ScannerGridSearchResultsService(new ScannerGridPublisher(sender), new RecordingBlocks());

        service.publishEmpty(7, 42, "Payments");

        assertEquals(1, sender.messages.size());
        Message message = sender.messages.get(0);
        assertEquals(7, message.homeBankingId);
        assertEquals(ScannerWorkspaceSessions.SCANNER_GRID, message.sessionId);
        assertEquals(ScannerWorkspaceOperations.SEARCH_TERMS, message.operationId);
        assertEquals(0, gson.fromJson(message.json, SplitDTO.class).getElementDetails().length);
    }

    private static ElementDTO element(String name) {
        ElementDTO element = new ElementDTO();
        element.setDefinedName(name);
        return element;
    }

    private static BlockLoadDTO block(int id, Integer botJobId, Integer order, String name) {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(id);
        block.setBotJobId(botJobId);
        block.setBlockOrderNumber(order);
        block.setName(name);
        return block;
    }

    private static final class RecordingBlocks implements ScannerGridSearchResultsService.BlockPort {
        private final List<BlockLoadDTO> rows = new ArrayList<>();

        @Override
        public List<BlockLoadDTO> blocks() {
            return rows;
        }
    }

    private static final class RecordingSender implements ScannerGridPublisher.Sender {
        private final List<Message> messages = new ArrayList<>();

        @Override
        public void sendMessageJson(int homeBankingId, String sessionId, String json, String operationId) {
            messages.add(new Message(homeBankingId, sessionId, json, operationId));
        }
    }

    private record Message(int homeBankingId, String sessionId, String json, String operationId) {}
}
