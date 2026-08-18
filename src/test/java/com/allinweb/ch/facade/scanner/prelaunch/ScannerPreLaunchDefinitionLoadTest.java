package com.allinweb.ch.facade.scanner.prelaunch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ErrorMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerPreLaunchDefinitionLoadTest {

    @Test
    void loadDefinitionsAppliesLoadedData() {
        RecordingOperations operations = new RecordingOperations();
        List<InstructionLoad> excelGoto = List.of(new InstructionLoad());
        List<BlockLoadDTO> blocks = List.of(new BlockLoadDTO());
        operations.result = new ScannerPreLaunchPreparation.Result(null, excelGoto, blocks, false);
        ScannerPreLaunchDefinitionLoad loader = new ScannerPreLaunchDefinitionLoad(operations);

        ErrorMessage error = loader.loadDefinitions();

        assertEquals(null, error);
        assertSame(excelGoto, operations.excelDataGoto);
        assertSame(blocks, operations.blocksLoaded);
        assertEquals(List.of("currentBotJob", "loadDefinitions", "setExcelDataGoto", "setBlocksLoaded"),
                operations.calls);
    }

    @Test
    void loadDefinitionsWarnsWhenBotJobMissing() {
        RecordingOperations operations = new RecordingOperations();
        operations.result = new ScannerPreLaunchPreparation.Result(null, List.of(), List.of(), true);
        ScannerPreLaunchDefinitionLoad loader = new ScannerPreLaunchDefinitionLoad(operations);

        loader.loadDefinitions();

        assertEquals(1, operations.warnCalls);
    }

    @Test
    void reportLoadErrorIgnoresNullError() {
        RecordingOperations operations = new RecordingOperations();
        ScannerPreLaunchDefinitionLoad loader = new ScannerPreLaunchDefinitionLoad(operations);

        loader.reportLoadError(null);

        assertEquals(0, operations.errorCalls);
        assertEquals(0, operations.operationFailedCalls);
    }

    @Test
    void reportLoadErrorLogsAndShowsFailure() {
        RecordingOperations operations = new RecordingOperations();
        ScannerPreLaunchDefinitionLoad loader = new ScannerPreLaunchDefinitionLoad(operations);
        ErrorMessage error = new ErrorMessage("Title", "Short", "Cannot load");

        loader.reportLoadError(error);

        assertEquals(1, operations.errorCalls);
        assertEquals(1, operations.operationFailedCalls);
        assertSame(error, operations.operationFailedError);
    }

    private static final class RecordingOperations implements ScannerPreLaunchDefinitionLoad.Operations {
        private final List<String> calls = new ArrayList<>();
        private final BotJobLoadDTO currentBotJob = createCurrentBotJob();
        private ScannerPreLaunchPreparation.Result result =
                new ScannerPreLaunchPreparation.Result(null, List.of(), List.of(), false);
        private List<InstructionLoad> excelDataGoto;
        private List<BlockLoadDTO> blocksLoaded;
        private int warnCalls;
        private int errorCalls;
        private int operationFailedCalls;
        private ErrorMessage operationFailedError;

        @Override
        public BotJobLoadDTO currentBotJob() {
            calls.add("currentBotJob");
            return currentBotJob;
        }

        @Override
        public ScannerPreLaunchPreparation.Result loadDefinitions(BotJobLoadDTO currentBotJob) {
            calls.add("loadDefinitions");
            return result;
        }

        @Override
        public void setExcelDataGoto(List<InstructionLoad> excelDataGoto) {
            calls.add("setExcelDataGoto");
            this.excelDataGoto = excelDataGoto;
        }

        @Override
        public void setBlocksLoaded(List<BlockLoadDTO> blocksLoaded) {
            calls.add("setBlocksLoaded");
            this.blocksLoaded = blocksLoaded;
        }

        @Override
        public void showOperationFailed(ErrorMessage errorMessage) {
            operationFailedCalls++;
            operationFailedError = errorMessage;
        }

        @Override
        public void warn(String message) {
            warnCalls++;
        }

        @Override
        public void error(String message) {
            errorCalls++;
        }

        private static BotJobLoadDTO createCurrentBotJob() {
            BotJobLoadDTO botJob = new BotJobLoadDTO();
            botJob.setId(42);
            botJob.setHomeBankingId(7);
            return botJob;
        }
    }
}
