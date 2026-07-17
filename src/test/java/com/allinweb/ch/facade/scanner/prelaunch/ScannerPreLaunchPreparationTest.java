package com.allinweb.ch.facade.scanner.prelaunch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ErrorMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchPreparationTest {

    @Test
    void loadDefinitionsLoadsBotJobBlocksAndActions() {
        RecordingEngine engine = new RecordingEngine();
        BlockLoadDTO block = new BlockLoadDTO();
        BotJobLoadDTO loadedBotJob = botJob(42, 2, List.of(block));
        RecordingLists lists = new RecordingLists(List.of(loadedBotJob));
        ScannerPreLaunchPreparation preparation = new ScannerPreLaunchPreparation(engine, lists);

        ScannerPreLaunchPreparation.Result result = preparation.loadDefinitions(botJob(42, 2, List.of()));

        assertNull(result.errorMessage());
        assertFalse(result.botJobMissing());
        assertEquals(List.of(block), result.blocksLoaded());
        assertEquals(List.of("loadHomeBanking", "loadHomeUrls:2", "loadExcelGotoBlock:42:instruction",
                "loadCompleteJobs:42", "loadAllVariables:variable:42", "loadAllActionsPerBlock:1"), engine.calls);
    }

    @Test
    void loadDefinitionsFixesExcelGotoWithoutParentBlock() {
        RecordingEngine engine = new RecordingEngine();
        InstructionLoad missingParent = excelGoto(100, 20, null);
        InstructionLoad fixedParent = excelGoto(100, 20, 20);
        engine.excelGotoLoads.add(List.of(missingParent));
        engine.excelGotoLoads.add(List.of(fixedParent));
        RecordingLists lists = new RecordingLists(List.of(botJob(42, 2, List.of())));
        ScannerPreLaunchPreparation preparation = new ScannerPreLaunchPreparation(engine, lists);

        ScannerPreLaunchPreparation.Result result = preparation.loadDefinitions(botJob(42, 2, List.of()));

        assertEquals(List.of(fixedParent), result.excelDataGoto());
        assertEquals("instruction:42:100:20", engine.fixExcelGotoCall);
    }

    @Test
    void loadDefinitionsStopsAfterFirstError() {
        RecordingEngine engine = new RecordingEngine();
        ErrorMessage error = new ErrorMessage("Home Banking", "Load failed", "Cannot load home banking");
        engine.homeBankingError = error;
        ScannerPreLaunchPreparation preparation =
                new ScannerPreLaunchPreparation(engine, new RecordingLists(List.of(botJob(42, 2, List.of()))));

        ScannerPreLaunchPreparation.Result result = preparation.loadDefinitions(botJob(42, 2, List.of()));

        assertEquals(error, result.errorMessage());
        assertEquals(List.of("loadHomeBanking"), engine.calls);
        assertTrue(result.excelDataGoto().isEmpty());
        assertTrue(result.blocksLoaded().isEmpty());
    }

    @Test
    void loadCurrentBotJobAttachesHomeBankingAndHomeUrl() {
        BotJobLoadDTO loadedBotJob = botJob(42, 2, List.of());
        loadedBotJob.setName("Login Job");
        loadedBotJob.setHomeUrlId(7);
        HomeBankingLoadDTO homeBanking = homeBanking("https://base.example");
        HomeUrlDTO homeUrl = homeUrl(8, "https://override.example");
        RecordingLists lists = new RecordingLists(List.of(loadedBotJob));
        lists.homeBanking = homeBanking;
        lists.homeUrl = homeUrl;
        ScannerPreLaunchPreparation preparation = new ScannerPreLaunchPreparation(new RecordingEngine(), lists);

        ScannerPreLaunchPreparation.BotJobSelection selection =
                preparation.loadCurrentBotJob(botJob(42, 2, List.of()), "D:\\Excel");

        assertTrue(selection.loaded());
        assertEquals(loadedBotJob, selection.botJob());
        assertEquals("Login Job", selection.botJobName());
        assertEquals("D:\\Excel\\Login Job.xlsx", selection.excelPath());
        assertEquals(8, loadedBotJob.getHomeUrlId());
        assertEquals("https://override.example", homeBanking.getUrl());
        assertEquals(homeBanking, loadedBotJob.getHomeBankingLoadDTO());
    }

    @Test
    void loadCurrentBotJobReportsMissingBotJob() {
        ScannerPreLaunchPreparation preparation =
                new ScannerPreLaunchPreparation(new RecordingEngine(), new RecordingLists(List.of()));

        ScannerPreLaunchPreparation.BotJobSelection selection =
                preparation.loadCurrentBotJob(botJob(42, 2, List.of()), "D:\\Excel");

        assertFalse(selection.loaded());
        assertTrue(selection.botJobMissing());
    }

    @Test
    void loadCurrentBotJobReportsMissingHomeBanking() {
        RecordingLists lists = new RecordingLists(List.of(botJob(42, 2, List.of())));
        ScannerPreLaunchPreparation preparation = new ScannerPreLaunchPreparation(new RecordingEngine(), lists);

        ScannerPreLaunchPreparation.BotJobSelection selection =
                preparation.loadCurrentBotJob(botJob(42, 2, List.of()), "D:\\Excel");

        assertFalse(selection.loaded());
        assertTrue(selection.homeBankingMissing());
    }

    @Test
    void resetInstructionExecutionFlagsMarksAllLoadedInstructionsUnexecuted() {
        InstructionLoad first = instruction(true);
        InstructionLoad second = instruction(true);
        BlockLoadDTO block = new BlockLoadDTO();
        block.setInstructionLoad(List.of(first, second));
        RecordingLists lists = new RecordingLists(List.of(botJob(42, 2, List.of(block))));
        ScannerPreLaunchPreparation preparation = new ScannerPreLaunchPreparation(new RecordingEngine(), lists);

        assertTrue(preparation.resetInstructionExecutionFlags());

        assertFalse(first.getExecuted());
        assertFalse(second.getExecuted());
    }

    @Test
    void resetInstructionExecutionFlagsReturnsFalseWhenBotJobMissing() {
        ScannerPreLaunchPreparation preparation =
                new ScannerPreLaunchPreparation(new RecordingEngine(), new RecordingLists(List.of()));

        assertFalse(preparation.resetInstructionExecutionFlags());
    }

    private BotJobLoadDTO botJob(int id, int homeBankingId, List<BlockLoadDTO> blocks) {
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        botJob.setId(id);
        botJob.setHomeBankingId(homeBankingId);
        botJob.setBlockLoadDTOList(blocks);
        return botJob;
    }

    private InstructionLoad excelGoto(int id, int blockId, Integer parentBlockId) {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setId(id);
        instruction.setBlockId(blockId);
        instruction.setParentBlockId(parentBlockId);
        return instruction;
    }

    private InstructionLoad instruction(boolean executed) {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setExecuted(executed);
        return instruction;
    }

    private HomeBankingLoadDTO homeBanking(String url) {
        HomeBankingLoadDTO homeBanking = new HomeBankingLoadDTO();
        homeBanking.setUrl(url);
        return homeBanking;
    }

    private HomeUrlDTO homeUrl(int id, String url) {
        HomeUrlDTO homeUrl = new HomeUrlDTO();
        homeUrl.setId(id);
        homeUrl.setUrl(url);
        return homeUrl;
    }

    private static final class RecordingEngine implements ScannerPreLaunchPreparation.EnginePort {
        private final List<String> calls = new ArrayList<>();
        private final List<List<InstructionLoad>> excelGotoLoads = new ArrayList<>();
        private ErrorMessage homeBankingError;
        private String fixExcelGotoCall;

        @Override
        public ErrorMessage loadHomeBanking(Integer homeBankingId) {
            calls.add("loadHomeBanking");
            return homeBankingError;
        }

        @Override
        public ErrorMessage loadHomeUrls(Integer homeBankingId) {
            calls.add("loadHomeUrls:" + homeBankingId);
            return null;
        }

        @Override
        public List<InstructionLoad> loadExcelGotoBlock(int whereId, String tableName) {
            calls.add("loadExcelGotoBlock:" + whereId + ":" + tableName);
            if (excelGotoLoads.isEmpty()) {
                return List.of();
            }
            return excelGotoLoads.remove(0);
        }

        @Override
        public ErrorMessage fixExcelGoto(String tableName, int whereId, int instructionId, int newParentBlockId) {
            fixExcelGotoCall = tableName + ":" + whereId + ":" + instructionId + ":" + newParentBlockId;
            return null;
        }

        @Override
        public ErrorMessage loadCompleteJobs(int botJobId) {
            calls.add("loadCompleteJobs:" + botJobId);
            return null;
        }

        @Override
        public ErrorMessage loadAllVariables(String varTable, int whereId) {
            calls.add("loadAllVariables:" + varTable + ":" + whereId);
            return null;
        }

        @Override
        public ErrorMessage loadAllActionsPerBlock(List<BlockLoadDTO> blockLoadDTOList) {
            calls.add("loadAllActionsPerBlock:" + blockLoadDTOList.size());
            return null;
        }
    }

    private static final class RecordingLists implements ScannerPreLaunchPreparation.ListsPort {
        private final List<BotJobLoadDTO> botJobs;
        private HomeBankingLoadDTO homeBanking;
        private HomeUrlDTO homeUrl;

        private RecordingLists(List<BotJobLoadDTO> botJobs) {
            this.botJobs = botJobs;
        }

        @Override
        public List<BotJobLoadDTO> botJobs() {
            return botJobs;
        }

        @Override
        public HomeBankingLoadDTO homeBankingById(int homeBankingId) {
            return homeBanking;
        }

        @Override
        public HomeUrlDTO homeUrlByBankId(int homeBankingId, int homeUrlId) {
            return homeUrl;
        }
    }
}
