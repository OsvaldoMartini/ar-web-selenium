package com.allinweb.ch.component.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScannerTestRunDefinitionLoadTest {

    @Test
    void loadAndApplyStoresDefinitionListsAndReturnsResult() {
        FakeOperations operations = new FakeOperations();
        BotJobLoadDTO botJob = new BotJobLoadDTO();
        InstructionLoad excelGoto = new InstructionLoad();
        BlockLoadDTO block = new BlockLoadDTO();
        operations.result = new ScannerPreLaunchPreparation.Result(null, List.of(excelGoto), List.of(block), false);
        ScannerTestRunDefinitionLoad loader = new ScannerTestRunDefinitionLoad(operations);

        ScannerPreLaunchPreparation.Result result = loader.loadAndApply(botJob);

        assertSame(operations.result, result);
        assertSame(botJob, operations.currentBotJob);
        assertEquals(List.of(excelGoto), operations.excelDataGoto);
        assertEquals(List.of(block), operations.blocksLoaded);
    }

    private static final class FakeOperations implements ScannerTestRunDefinitionLoad.Operations {
        private ScannerPreLaunchPreparation.Result result;
        private BotJobLoadDTO currentBotJob;
        private List<InstructionLoad> excelDataGoto;
        private List<BlockLoadDTO> blocksLoaded;

        @Override
        public ScannerPreLaunchPreparation.Result loadDefinitions(BotJobLoadDTO currentBotJob) {
            this.currentBotJob = currentBotJob;
            return result;
        }

        @Override
        public void setExcelDataGoto(List<InstructionLoad> excelDataGoto) {
            this.excelDataGoto = excelDataGoto;
        }

        @Override
        public void setBlocksLoaded(List<BlockLoadDTO> blocksLoaded) {
            this.blocksLoaded = blocksLoaded;
        }
    }
}
