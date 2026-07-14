package com.allinweb.ch.component.pane;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ErrorMessage;
import java.util.List;

final class ScannerPreLaunchDefinitionLoad {
    private final Operations operations;

    ScannerPreLaunchDefinitionLoad(Operations operations) {
        this.operations = operations;
    }

    ErrorMessage loadDefinitions() {
        BotJobLoadDTO currentBotJob = operations.currentBotJob();
        ScannerPreLaunchPreparation.Result result = operations.loadDefinitions(currentBotJob);
        operations.setExcelDataGoto(result.excelDataGoto());
        operations.setBlocksLoaded(result.blocksLoaded());
        if (result.botJobMissing()) {
            operations.warn("I cannot find a Bot Job with this Organization ID: "
                    + currentBotJob.getHomeBankingId()
                    + " Environment ID: "
                    + currentBotJob.getId());
        }
        return result.errorMessage();
    }

    void reportLoadError(ErrorMessage errorMessage) {
        if (errorMessage == null) {
            return;
        }
        operations.error("Error: " + errorMessage.getErrorMessage());
        operations.showOperationFailed(errorMessage);
    }

    interface Operations {
        BotJobLoadDTO currentBotJob();

        ScannerPreLaunchPreparation.Result loadDefinitions(BotJobLoadDTO currentBotJob);

        void setExcelDataGoto(List<InstructionLoad> excelDataGoto);

        void setBlocksLoaded(List<BlockLoadDTO> blocksLoaded);

        void showOperationFailed(ErrorMessage errorMessage);

        void warn(String message);

        void error(String message);
    }
}
