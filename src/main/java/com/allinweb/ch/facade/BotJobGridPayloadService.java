package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.PayloadJson;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import java.util.List;

/** UI-independent owner of empty Bot Job and Component grid payload construction. */
public final class BotJobGridPayloadService {
    private static final BotJobGridPayloadService INSTANCE =
            new BotJobGridPayloadService(new DefaultDataPort(), new Gson());
    private final DataPort data;
    private final Gson gson;

    BotJobGridPayloadService(DataPort data, Gson gson) {
        this.data = data;
        this.gson = gson;
    }

    public static BotJobGridPayloadService getInstance() { return INSTANCE; }

    public Result build(BotJobLoadDTO botJob, Destination destination) {
        if (botJob == null || botJob.getId() == null || botJob.getId() <= 0) {
            throw new IllegalArgumentException("An active Bot Job is required");
        }
        boolean components = destination == Destination.COMPONENTS;
        List<BotJobLoadDTO> jobs = components ? data.componentJobs() : data.botJobs();
        List<BlockLoadDTO> blocks = components ? data.componentBlocks() : data.botJobBlocks();
        ErrorMessage warning = null;
        if (!jobs.isEmpty() && blocks.isEmpty()) {
            warning = components
                    ? data.loadComponentBlocks(botJob.getHomeBankingId(), botJob.getName())
                    : data.loadBotJobBlocks(botJob.getId(), botJob.getName());
        }
        int blockId = -1;
        String blockName = "1# Default Block";
        if (botJob.getBlockId() == null && !blocks.isEmpty() && blocks.get(0) != null) {
            BlockLoadDTO first = blocks.get(0);
            blockId = first.getId() == null ? -1 : first.getId();
            if (first.getName() != null) blockName = first.getName();
        }
        return new Result(gson.toJson(new PayloadJson(botJob.getId(), blockId, blockName, 0)), warning);
    }

    public enum Destination { BOT_JOB, COMPONENTS }
    public record Result(String json, ErrorMessage warning) {}

    interface DataPort {
        List<BotJobLoadDTO> botJobs();
        List<BlockLoadDTO> botJobBlocks();
        List<BotJobLoadDTO> componentJobs();
        List<BlockLoadDTO> componentBlocks();
        ErrorMessage loadBotJobBlocks(int botJobId, String botJobName);
        ErrorMessage loadComponentBlocks(int homeBankingId, String botJobName);
    }

    private static final class DefaultDataPort implements DataPort {
        private final PerformLists lists = PerformLists.getInstance();
        private final PerformDataBase database = PerformDataBase.getInstance();
        public List<BotJobLoadDTO> botJobs() { return lists.getListBotJob(); }
        public List<BlockLoadDTO> botJobBlocks() { return lists.getListBlock(); }
        public List<BotJobLoadDTO> componentJobs() { return lists.getListBotJobComp(); }
        public List<BlockLoadDTO> componentBlocks() { return lists.getListBlockComp(); }
        public ErrorMessage loadBotJobBlocks(int id, String name) { return database.loadBlocks(id, name, "block"); }
        public ErrorMessage loadComponentBlocks(int id, String name) {
            return database.loadBlocks(id, name, "component_block");
        }
    }
}
