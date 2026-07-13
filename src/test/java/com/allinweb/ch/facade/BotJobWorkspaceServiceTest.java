package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.Gson;
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
        block.setName("1# Login");
        data.blocks.add(block);
        data.componentBlocks.add(block);
        BotJobWorkspaceService service = new BotJobWorkspaceService(data, (job, name) -> {}, new Gson());

        BotJobWorkspaceService.GridSnapshot result = service.refresh(job("Web App"));

        assertTrue(result.botJobJson().contains("\"blockId\":77"));
        assertTrue(result.componentJson().contains("\"name\":\"1# Login\""));
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
