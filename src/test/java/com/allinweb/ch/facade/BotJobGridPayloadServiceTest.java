package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BotJobGridPayloadServiceTest {

    @Test
    void usesCanonicalFirstBlockForEachDestination() {
        Data data = new Data();
        data.botJobs.add(new BotJobLoadDTO());
        data.componentJobs.add(new BotJobLoadDTO());
        data.botBlocks.add(block(11, "Login"));
        data.componentBlocks.add(block(22, "Header"));
        BotJobGridPayloadService service = new BotJobGridPayloadService(data, new Gson());

        assertTrue(service.build(job(), BotJobGridPayloadService.Destination.BOT_JOB).json()
                .contains("\"blockId\":11"));
        assertTrue(service.build(job(), BotJobGridPayloadService.Destination.COMPONENTS).json()
                .contains("\"blockId\":22"));
    }

    @Test
    void returnsLoadWarningAndDefaultPayloadWhenBlocksStayEmpty() {
        Data data = new Data();
        data.botJobs.add(new BotJobLoadDTO());
        data.warning = new ErrorMessage("DB", "Blocks", "failed");
        BotJobGridPayloadService service = new BotJobGridPayloadService(data, new Gson());

        BotJobGridPayloadService.Result result =
                service.build(job(), BotJobGridPayloadService.Destination.BOT_JOB);

        assertSame(data.warning, result.warning());
        assertTrue(result.json().contains("\"blockId\":-1"));
    }

    private static BotJobLoadDTO job() {
        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setId(42);
        job.setName("Payments");
        job.setHomeBankingId(7);
        return job;
    }

    private static BlockLoadDTO block(int id, String name) {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(id);
        block.setName(name);
        return block;
    }

    private static final class Data implements BotJobGridPayloadService.DataPort {
        private final List<BotJobLoadDTO> botJobs = new ArrayList<>();
        private final List<BlockLoadDTO> botBlocks = new ArrayList<>();
        private final List<BotJobLoadDTO> componentJobs = new ArrayList<>();
        private final List<BlockLoadDTO> componentBlocks = new ArrayList<>();
        private ErrorMessage warning;
        public List<BotJobLoadDTO> botJobs() { return botJobs; }
        public List<BlockLoadDTO> botJobBlocks() { return botBlocks; }
        public List<BotJobLoadDTO> componentJobs() { return componentJobs; }
        public List<BlockLoadDTO> componentBlocks() { return componentBlocks; }
        public ErrorMessage loadBotJobBlocks(int id, String name) { return warning; }
        public ErrorMessage loadComponentBlocks(int id, String name) { return warning; }
    }
}
