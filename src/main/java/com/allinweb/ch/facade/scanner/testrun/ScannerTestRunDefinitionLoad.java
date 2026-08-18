package com.allinweb.ch.facade.scanner.testrun;

import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchPreparation;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import java.util.List;

public final class ScannerTestRunDefinitionLoad {
    private final Operations operations;

    public ScannerTestRunDefinitionLoad(Operations operations) {
        this.operations = operations;
    }

    public ScannerPreLaunchPreparation.Result loadAndApply(BotJobLoadDTO currentBotJob) {
        ScannerPreLaunchPreparation.Result definitions = operations.loadDefinitions(currentBotJob);
        operations.setExcelDataGoto(definitions.excelDataGoto());
        operations.setBlocksLoaded(definitions.blocksLoaded());
        return definitions;
    }

    public interface Operations {
        ScannerPreLaunchPreparation.Result loadDefinitions(BotJobLoadDTO currentBotJob);

        void setExcelDataGoto(List<InstructionLoad> excelDataGoto);

        void setBlocksLoaded(List<BlockLoadDTO> blocksLoaded);
    }
}
