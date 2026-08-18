package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerEmptyPayloadServiceTest {
    private final ScannerEmptyPayloadService service = new ScannerEmptyPayloadService();
    private final Gson gson = new Gson();

    @Test
    void defaultPayloadLoadsBotJobBlocksWhenMissing() {
        RecordingOperations operations = new RecordingOperations();
        operations.hasBotJobs = true;
        BotJobLoadDTO botJob = botJob(9, null);

        String json = gson.toJson(service.buildDefault(botJob, operations));

        assertEquals("{\"id\":9,\"blockId\":5,\"name\":\"5# Loaded\",\"instructionId\":0}", json);
        assertEquals(List.of("botBlocks", "loadBot:9", "botBlocks"), operations.calls);
    }

    @Test
    void botJobDestinationUsesBotJobBlocks() {
        RecordingOperations operations = new RecordingOperations();
        operations.botBlocks = List.of(block(4, "4# Login"));

        String json = gson.toJson(service.buildForDestination(ScannerWorkspaceSessions.BOT_JOB_TASKS, botJob(9, null), operations));

        assertEquals("{\"id\":9,\"blockId\":4,\"name\":\"4# Login\",\"instructionId\":0}", json);
        assertEquals(List.of("botBlocks"), operations.calls);
    }

    @Test
    void componentDestinationLoadsComponentBlocksWhenMissing() {
        RecordingOperations operations = new RecordingOperations();
        operations.hasComponentJobs = true;

        String json = gson.toJson(
                service.buildForDestination(ScannerWorkspaceSessions.COMPONENT_TASKS, botJobWithHomeBanking(9, 12), operations));

        assertEquals("{\"id\":9,\"blockId\":6,\"name\":\"6# Component\",\"instructionId\":0}", json);
        assertEquals(List.of("componentBlocks", "loadComponent:12", "componentBlocks"), operations.calls);
    }

    private static BotJobLoadDTO botJob(int id, Integer blockId) {
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(id);
        botJob.setBlockId(blockId);
        return botJob;
    }

    private static BotJobLoadDTO botJobWithHomeBanking(int id, int homeBankingId) {
        BotJobLoadDTO botJob = botJob(id, null);
        botJob.setHomeBankingId(homeBankingId);
        return botJob;
    }

    private static BlockLoadDTO block(Integer id, String name) {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(id);
        block.setName(name);
        return block;
    }

    private static final class RecordingOperations implements ScannerEmptyPayloadService.Operations {
        private final List<String> calls = new ArrayList<>();
        private boolean hasBotJobs;
        private boolean hasComponentJobs;
        private List<BlockLoadDTO> botBlocks = new ArrayList<>();
        private List<BlockLoadDTO> componentBlocks = new ArrayList<>();

        @Override
        public boolean hasBotJobs() {
            return hasBotJobs;
        }

        @Override
        public boolean hasComponentJobs() {
            return hasComponentJobs;
        }

        @Override
        public List<BlockLoadDTO> botJobBlocks() {
            calls.add("botBlocks");
            return botBlocks;
        }

        @Override
        public List<BlockLoadDTO> componentBlocks() {
            calls.add("componentBlocks");
            return componentBlocks;
        }

        @Override
        public void loadBotJobBlocks(int botJobId) {
            calls.add("loadBot:" + botJobId);
            botBlocks = List.of(block(5, "5# Loaded"));
        }

        @Override
        public void loadComponentBlocks(int homeBankingId) {
            calls.add("loadComponent:" + homeBankingId);
            componentBlocks = List.of(block(6, "6# Component"));
        }
    }
}
