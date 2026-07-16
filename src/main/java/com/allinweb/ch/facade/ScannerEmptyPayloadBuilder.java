package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.PayloadJson;
import java.util.List;

public final class ScannerEmptyPayloadBuilder {
    private static final int NO_BLOCK_ID = -1;
    private static final String DEFAULT_BLOCK_NAME = "1# Default Block";

    public PayloadJson build(BotJobLoadDTO currentBotJob, List<BlockLoadDTO> blocks) {
        int blockId = NO_BLOCK_ID;
        String blockName = DEFAULT_BLOCK_NAME;

        if (currentBotJob.getBlockId() == null && blocks != null && !blocks.isEmpty()) {
            BlockLoadDTO first = blocks.get(0);
            if (first != null) {
                blockId = first.getId() == null ? NO_BLOCK_ID : first.getId();
                blockName = first.getName() == null ? DEFAULT_BLOCK_NAME : first.getName();
            }
        }

        return new PayloadJson(currentBotJob.getId(), blockId, blockName, 0);
    }
}
