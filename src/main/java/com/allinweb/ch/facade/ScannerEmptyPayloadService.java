package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.PayloadJson;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import java.util.Collections;
import java.util.List;

public final class ScannerEmptyPayloadService {
    private final ScannerEmptyPayloadBuilder builder = new ScannerEmptyPayloadBuilder();

    public PayloadJson buildDefault(BotJobLoadDTO currentBotJob, Operations operations) {
        if (operations.hasBotJobs() && operations.botJobBlocks().isEmpty()) {
            operations.loadBotJobBlocks(currentBotJob.getId());
        }
        return builder.build(currentBotJob, operations.botJobBlocks());
    }

    public PayloadJson buildForDestination(String destination, BotJobLoadDTO currentBotJob, Operations operations) {
        List<BlockLoadDTO> blocks = Collections.emptyList();
        if (destination.equalsIgnoreCase(ScannerWorkspaceSessions.BOT_JOB_TASKS)) {
            if (operations.hasBotJobs() && operations.botJobBlocks().isEmpty()) {
                operations.loadBotJobBlocks(currentBotJob.getId());
            }
            blocks = operations.botJobBlocks();
        } else if (destination.equalsIgnoreCase(ScannerWorkspaceSessions.COMPONENT_TASKS)) {
            if (operations.hasComponentJobs() && operations.componentBlocks().isEmpty()) {
                operations.loadComponentBlocks(currentBotJob.getHomeBankingId());
            }
            blocks = operations.componentBlocks();
        }
        return builder.build(currentBotJob, blocks);
    }

    public interface Operations {
        boolean hasBotJobs();

        boolean hasComponentJobs();

        List<BlockLoadDTO> botJobBlocks();

        List<BlockLoadDTO> componentBlocks();

        void loadBotJobBlocks(int botJobId);

        void loadComponentBlocks(int homeBankingId);
    }
}
