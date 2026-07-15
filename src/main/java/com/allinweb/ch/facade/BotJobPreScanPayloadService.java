package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.ElementDTO;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.model.SplitDTO;
import com.allinweb.ch.util.ErrorMessage;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** UI-independent owner of Pre Scan element and canonical block-option payload construction. */
public final class BotJobPreScanPayloadService {

    private static final BotJobPreScanPayloadService INSTANCE =
            new BotJobPreScanPayloadService(new DefaultDataPort());

    private final DataPort data;

    BotJobPreScanPayloadService(DataPort data) {
        this.data = data;
    }

    public static BotJobPreScanPayloadService getInstance() {
        return INSTANCE;
    }

    public Result build(BotJobLoadDTO botJob, List<ElementDTO> elements) {
        if (botJob == null || botJob.getId() == null || botJob.getId() <= 0) {
            throw new IllegalArgumentException("An active Bot Job is required");
        }
        ErrorMessage warning = null;
        if (data.blocks().isEmpty()) warning = data.loadBlocks(botJob.getId(), botJob.getName());
        List<Map<String, Object>> options = data.blocks().stream()
                .filter(block -> block != null && block.getId() != null)
                .filter(block -> block.getBotJobId() == null || botJob.getId().equals(block.getBotJobId()))
                .sorted(Comparator.comparingInt(
                        block -> block.getBlockOrderNumber() == null
                                ? Integer.MAX_VALUE
                                : block.getBlockOrderNumber()))
                .map(ScannerWorkspaceBlockOptions::from)
                .toList();

        SplitDTO payload = new SplitDTO();
        payload.setHomeBankingId(botJob.getHomeBankingId());
        payload.setBotJobId(botJob.getId());
        payload.setBotJobName(botJob.getName());
        payload.setType(ScannerWorkspaceOperations.SEARCH_TOOL);
        payload.setSessionId(ScannerWorkspaceSessions.PRE_SCANNER_GRID);
        payload.setOperationId(ScannerWorkspaceOperations.SEARCH_TERMS);
        payload.setElementDetails((elements == null ? List.<ElementDTO>of() : elements).toArray(new ElementDTO[0]));
        payload.setBlocks(options);
        return new Result(payload, warning);
    }

    public record Result(SplitDTO payload, ErrorMessage warning) {}

    interface DataPort {
        List<BlockLoadDTO> blocks();
        ErrorMessage loadBlocks(int botJobId, String botJobName);
    }

    private static final class DefaultDataPort implements DataPort {
        private final PerformLists lists = PerformLists.getInstance();
        private final PerformDataBase database = PerformDataBase.getInstance();
        public List<BlockLoadDTO> blocks() { return lists.getListBlock(); }
        public ErrorMessage loadBlocks(int botJobId, String botJobName) {
            return database.loadBlocks(botJobId, botJobName, "block");
        }
    }
}
