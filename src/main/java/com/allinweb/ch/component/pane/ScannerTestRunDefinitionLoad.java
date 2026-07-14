package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import java.util.List;

final class ScannerTestRunDefinitionLoad {
    private final Operations operations;

    ScannerTestRunDefinitionLoad(Operations operations) {
        this.operations = operations;
    }

    ScannerPreLaunchPreparation.Result loadAndApply(BotJobLoadDTO currentBotJob) {
        ScannerPreLaunchPreparation.Result definitions = operations.loadDefinitions(currentBotJob);
        operations.setExcelDataGoto(definitions.excelDataGoto());
        operations.setBlocksLoaded(definitions.blocksLoaded());
        return definitions;
    }

    interface Operations {
        ScannerPreLaunchPreparation.Result loadDefinitions(BotJobLoadDTO currentBotJob);

        void setExcelDataGoto(List<InstructionLoad> excelDataGoto);

        void setBlocksLoaded(List<BlockLoadDTO> blocksLoaded);
    }
}
