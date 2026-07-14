package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
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

    private record RecordingLists(List<BotJobLoadDTO> botJobs) implements ScannerPreLaunchPreparation.ListsPort {}
}
