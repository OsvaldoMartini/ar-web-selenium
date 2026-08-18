package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.SplitDTO;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds and publishes scanner-grid search result payloads. */
public final class ScannerGridSearchResultsService {
    private static final int DEFAULT_CHUNK_SIZE = 25;

    private final ScannerGridPublisher publisher;
    private final BlockPort blocks;

    public ScannerGridSearchResultsService(ScannerGridPublisher publisher, BlockPort blocks) {
        this.publisher = publisher;
        this.blocks = blocks;
    }

    public SplitDTO emptyPayload(int homeBankingId, int botJobId, String botJobName) {
        return payload(homeBankingId, botJobId, botJobName, new ElementDTO[0]);
    }

    public void publishEmpty(int homeBankingId, int botJobId, String botJobName) {
        publisher.publishScannerGridSearchTerms(homeBankingId, emptyPayload(homeBankingId, botJobId, botJobName));
    }

    public Result publishResults(int homeBankingId, int botJobId, String botJobName, List<ElementDTO> elements) {
        publisher.publishScannerGridSearchTerms(homeBankingId, emptyPayload(homeBankingId, botJobId, botJobName));
        ElementDTO[] details = elements == null ? new ElementDTO[0] : elements.toArray(new ElementDTO[0]);
        SplitDTO payload = payload(homeBankingId, botJobId, botJobName, details);
        publisher.publishScannerGridSearchTermsChunks(0, payload, DEFAULT_CHUNK_SIZE);
        return new Result(details.length, publisher.destinationSessionId());
    }

    private SplitDTO payload(int homeBankingId, int botJobId, String botJobName, ElementDTO[] elements) {
        return publisher.searchTermsPayload(homeBankingId, botJobId, botJobName, elements, blockOptions(botJobId));
    }

    private List<Map<String, Object>> blockOptions(int botJobId) {
        return blocks.blocks().stream()
                .filter(block -> block != null && block.getId() != null)
                .filter(block -> block.getBotJobId() == null || Objects.equals(block.getBotJobId(), botJobId))
                .sorted(Comparator.comparingInt(
                        block -> block.getBlockOrderNumber() == null ? Integer.MAX_VALUE : block.getBlockOrderNumber()))
                .map(ScannerWorkspaceBlockOptions::from)
                .toList();
    }

    public record Result(int elementCount, String destinationSessionId) {}

    public interface BlockPort {
        List<BlockLoadDTO> blocks();
    }
}
