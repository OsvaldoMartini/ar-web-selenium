package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BotJobWorkspaceServiceTest {

    @Test
    void activationOwnsTheCompleteCacheLoadOrderAndReturnsBothSnapshots() {
        FakeDataPort data = new FakeDataPort();
        data.botJobs.add(new BotJobLoadDTO());
        data.components.add(new BotJobLoadDTO());
        data.instructions = List.of(InstructionLoad.builder().id(19).name("Account").build());
        List<String> excelCalls = new ArrayList<>();
        BotJobWorkspaceService service = new BotJobWorkspaceService(
                data,
                (job, duplicateName) -> excelCalls.add(job.getId() + ":" + duplicateName),
                new Gson());

        BotJobWorkspaceService.GridSnapshot result = service.activate(job("Web App"));

        assertEquals(List.of(
                "clearAll", "clearGrid", "variables", "fields", "clearGrid", "job", "blocks",
                "components", "componentBlocks"), data.calls);
        assertEquals(List.of("42:null"), excelCalls);
        assertTrue(result.botJobJson().contains("\"id\":19"));
        assertTrue(result.componentJson().contains("\"name\":\"Account\""));
        assertTrue(JsonParser.parseString(result.botJobJson()).isJsonArray());
    }

    @Test
    void activationFailureClearsEveryCacheAndKeepsTheOriginalDatabaseError() {
        FakeDataPort data = new FakeDataPort();
        data.pageFieldsError = new ErrorMessage("Database", "Page fields", "Unable to load fields");
        BotJobWorkspaceService service = new BotJobWorkspaceService(data, (job, name) -> {}, new Gson());

        BotJobWorkspaceService.WorkspaceLoadException failure = assertThrows(
                BotJobWorkspaceService.WorkspaceLoadException.class,
                () -> service.activate(job("Web App")));

        assertEquals("Unable to load fields", failure.getMessage());
        assertEquals(data.pageFieldsError, failure.error());
        assertEquals(List.of("clearAll", "clearGrid", "variables", "fields", "clearAll", "clearGrid"), data.calls);
    }

    @Test
    void refreshFailureClearsPartialGridCaches() {
        FakeDataPort data = new FakeDataPort();
        data.componentError = new ErrorMessage("Database", "Components", "Component load failed");
        BotJobWorkspaceService service = new BotJobWorkspaceService(data, (job, name) -> {}, new Gson());

        assertThrows(BotJobWorkspaceService.WorkspaceLoadException.class, () -> service.refresh(job("Web App")));

        assertEquals(List.of("clearGrid", "job", "blocks", "components", "clearGrid"), data.calls);
    }

    @Test
    void emptyGridsUseTheFirstCanonicalBlockInThePlaceholderPayload() {
        FakeDataPort data = new FakeDataPort();
        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(77);
        block.setBlockOrderNumber(1);
        block.setName("1# Login");
        block.setExportFile("D:\\Exports\\component.xlsx");
        data.blocks.add(block);
        data.componentBlocks.add(block);
        BotJobWorkspaceService service = new BotJobWorkspaceService(data, (job, name) -> {}, new Gson());

        BotJobWorkspaceService.GridSnapshot result = service.refresh(job("Web App"));

        assertTrue(result.botJobJson().contains("\"blockId\":77"));
        JsonObject componentPayload = JsonParser.parseString(result.componentJson()).getAsJsonObject();
        assertEquals(0, componentPayload.getAsJsonArray("instructions").size());
        assertEquals(77, componentPayload.getAsJsonArray("blocks")
                .get(0).getAsJsonObject().get("blockId").getAsInt());
        assertEquals("1# Login", componentPayload.getAsJsonArray("blocks")
                .get(0).getAsJsonObject().get("blockName").getAsString());
        assertEquals("D:\\Exports\\component.xlsx", componentPayload.getAsJsonArray("blocks")
                .get(0).getAsJsonObject().get("exportFile").getAsString());
        assertEquals(42, componentPayload.get("botJobId").getAsInt());
        assertEquals("Payments", componentPayload.get("botJobName").getAsString());
        assertEquals(7, componentPayload.get("homeBankingId").getAsInt());
    }

    @Test
    void componentSnapshotKeepsEmptyBlocksAlongsideInstructionBearingBlocks() {
        FakeDataPort data = new FakeDataPort();
        data.components.add(new BotJobLoadDTO());
        data.instructions = List.of(InstructionLoad.builder().id(19).name("Account").build());
        data.componentBlocks.add(block(77, 1, "Empty reusable block"));
        data.componentBlocks.add(block(88, 2, "Populated reusable block"));
        BotJobWorkspaceService service = new BotJobWorkspaceService(data, (job, name) -> {}, new Gson());

        BotJobWorkspaceService.GridSnapshot result = service.refresh(job("Web App"));

        JsonObject componentPayload = JsonParser.parseString(result.componentJson()).getAsJsonObject();
        assertEquals(1, componentPayload.getAsJsonArray("instructions").size());
        JsonArray blocks = componentPayload.getAsJsonArray("blocks");
        assertEquals(2, blocks.size());
        assertEquals(77, blocks.get(0).getAsJsonObject().get("blockId").getAsInt());
        assertEquals("Empty reusable block", blocks.get(0).getAsJsonObject().get("blockName").getAsString());
        assertEquals(88, blocks.get(1).getAsJsonObject().get("blockId").getAsInt());
        assertEquals(2, blocks.get(1).getAsJsonObject().get("blockOrderNumber").getAsInt());
    }

    @Test
    void scannerDispositionPreservesWebSwitchAndMobileCloseRules() {
        BotJobWorkspaceService service = new BotJobWorkspaceService(new FakeDataPort(), (job, name) -> {}, new Gson());

        assertEquals(BotJobWorkspaceService.ScannerDisposition.KEEP, service.scannerDisposition(job("Web App"), null));
        assertEquals(BotJobWorkspaceService.ScannerDisposition.KEEP, service.scannerDisposition(job("Web App"), 42));
        assertEquals(BotJobWorkspaceService.ScannerDisposition.OPEN, service.scannerDisposition(job("Web App"), 41));
        assertEquals(BotJobWorkspaceService.ScannerDisposition.CLOSE, service.scannerDisposition(job("Android"), 42));
        assertEquals(BotJobWorkspaceService.ScannerDisposition.CLOSE, service.scannerDisposition(job("iOS"), null));
        assertEquals(BotJobWorkspaceService.ScannerDisposition.KEEP, service.scannerDisposition(job("Rest Api"), 41));
    }

    private static BotJobLoadDTO job(String priority) {
        BotJobLoadDTO job = new BotJobLoadDTO();
        job.setId(42);
        job.setName("Payments");
        job.setHomeBankingId(7);
        job.setPriority(priority);
        return job;
    }

    private static BlockLoadDTO block(int id, int order, String name) {
        BlockLoadDTO block = new BlockLoadDTO();
        block.setId(id);
        block.setBlockOrderNumber(order);
        block.setName(name);
        block.setActive(true);
        block.setWait(0);
        return block;
    }

    private static final class FakeDataPort implements BotJobWorkspaceService.DataPort {
        private final List<String> calls = new ArrayList<>();
        private final List<BotJobLoadDTO> botJobs = new ArrayList<>();
        private final List<BlockLoadDTO> blocks = new ArrayList<>();
        private final List<BotJobLoadDTO> components = new ArrayList<>();
        private final List<BlockLoadDTO> componentBlocks = new ArrayList<>();
        private List<InstructionLoad> instructions = List.of();
        private ErrorMessage pageFieldsError;
        private ErrorMessage componentError;

        @Override
        public void clearAllCaches() {
            calls.add("clearAll");
            clearGridCaches();
        }

        @Override
        public void clearGridCaches() {
            calls.add("clearGrid");
        }

        @Override
        public ErrorMessage loadVariables(int botJobId) {
            calls.add("variables");
            return null;
        }

        @Override
        public ErrorMessage loadPageFields(int botJobId) {
            calls.add("fields");
            return pageFieldsError;
        }

        @Override
        public ErrorMessage loadCompleteJob(int botJobId) {
            calls.add("job");
            return null;
        }

        @Override
        public ErrorMessage loadBotJobBlocks(int botJobId, String botJobName) {
            calls.add("blocks");
            return null;
        }

        @Override
        public ErrorMessage loadComponents(int homeBankingId, int botJobId, String botJobName) {
            calls.add("components");
            return componentError;
        }

        @Override
        public ErrorMessage loadComponentBlocks(int homeBankingId, String botJobName) {
            calls.add("componentBlocks");
            return null;
        }

        @Override
        public List<BotJobLoadDTO> botJobs() {
            return botJobs;
        }

        @Override
        public List<BlockLoadDTO> botJobBlocks() {
            return blocks;
        }

        @Override
        public List<BotJobLoadDTO> components() {
            return components;
        }

        @Override
        public List<BlockLoadDTO> componentBlocks() {
            return componentBlocks;
        }

        @Override
        public List<InstructionLoad> buildJsonViewData(List<BotJobLoadDTO> jobs) {
            return instructions;
        }
    }
}
