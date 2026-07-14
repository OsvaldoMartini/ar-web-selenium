package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class ScannerEmptyPayloadBuilderTest {
    private final Gson gson = new Gson();

    @Test
    void buildUsesFirstBlockWhenBotJobHasNoSelectedBlock() {
        ScannerEmptyPayloadBuilder builder = new ScannerEmptyPayloadBuilder();
        BotJobLoadDTO botJob = botJob(9, null);

        String json = gson.toJson(builder.build(botJob, java.util.List.of(block(4, "4# Login"))));

        assertEquals("{\"id\":9,\"blockId\":4,\"name\":\"4# Login\",\"instructionId\":0}", json);
    }

    @Test
    void buildUsesDefaultBlockWhenBlocksAreMissing() {
        ScannerEmptyPayloadBuilder builder = new ScannerEmptyPayloadBuilder();
        BotJobLoadDTO botJob = botJob(9, null);

        String json = gson.toJson(builder.build(botJob, java.util.List.of()));

        assertEquals("{\"id\":9,\"blockId\":-1,\"name\":\"1# Default Block\",\"instructionId\":0}", json);
    }

    @Test
    void buildKeepsDefaultWhenBotJobAlreadyHasSelectedBlock() {
        ScannerEmptyPayloadBuilder builder = new ScannerEmptyPayloadBuilder();
        BotJobLoadDTO botJob = botJob(9, 44);

        String json = gson.toJson(builder.build(botJob, java.util.List.of(block(4, "4# Login"))));

        assertEquals("{\"id\":9,\"blockId\":-1,\"name\":\"1# Default Block\",\"instructionId\":0}", json);
    }

    @Test
    void buildHandlesNullFirstBlockProperties() {
        ScannerEmptyPayloadBuilder builder = new ScannerEmptyPayloadBuilder();
        BotJobLoadDTO botJob = botJob(9, null);

        String json = gson.toJson(builder.build(botJob, java.util.List.of(block(null, null))));

        assertEquals("{\"id\":9,\"blockId\":-1,\"name\":\"1# Default Block\",\"instructionId\":0}", json);
    }

    private static BotJobLoadDTO botJob(int id, Integer blockId) {
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(id);
        botJob.setBlockId(blockId);
        return botJob;
    }

    private static BlockLoadDTO block(Integer id, String name) {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(id);
        block.setName(name);
        return block;
    }
}
