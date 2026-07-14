package com.allinweb.ch.component.pane;

import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.util.ErrorMessage;
import java.util.List;

final class ScannerPreLaunchPreparation {
    private static final String INSTRUCTION_TABLE = "instruction";
    private static final String VARIABLE_TABLE = "variable";

    private final EnginePort engine;
    private final ListsPort lists;

    ScannerPreLaunchPreparation(EnginePort engine, ListsPort lists) {
        this.engine = engine;
        this.lists = lists;
    }

    static ScannerPreLaunchPreparation from(PerformDBEngine engine, PerformLists lists) {
        return new ScannerPreLaunchPreparation(new PerformDBEnginePort(engine), new PerformListsPort(lists));
    }

    Result loadDefinitions(BotJobLoadDTO currentBotJob) {
        ErrorMessage errorMessage = engine.loadHomeBanking(null);
        if (errorMessage == null) {
            errorMessage = engine.loadHomeUrls(currentBotJob.getHomeBankingId());
        }

        List<InstructionLoad> excelDataGoto = List.of();
        if (errorMessage == null) {
            excelDataGoto = loadAndFixExcelGoto(currentBotJob);
        }
        if (errorMessage == null) {
            errorMessage = engine.loadCompleteJobs(currentBotJob.getId());
        }
        if (errorMessage == null) {
            errorMessage = engine.loadAllVariables(VARIABLE_TABLE, currentBotJob.getId());
        }

        List<BlockLoadDTO> blocksLoaded = List.of();
        if (errorMessage == null && !lists.botJobs().isEmpty()) {
            blocksLoaded = lists.botJobs().get(0).getBlockLoadDTOList();
            errorMessage = engine.loadAllActionsPerBlock(blocksLoaded);
        }
        return new Result(errorMessage, excelDataGoto, blocksLoaded, lists.botJobs().isEmpty());
    }

    BotJobSelection loadCurrentBotJob(BotJobLoadDTO currentBotJob, String excelBasePath) {
        if (lists.botJobs().isEmpty()) {
            return BotJobSelection.missingBotJob();
        }
        HomeBankingLoadDTO homeBanking = lists.homeBankingById(currentBotJob.getHomeBankingId());
        if (homeBanking == null || isBlank(homeBanking.getUrl())) {
            return BotJobSelection.missingHomeBanking();
        }

        BotJobLoadDTO loadedBotJob = lists.botJobs().get(0);
        loadedBotJob.setHomeBankingLoadDTO(homeBanking);
        HomeUrlDTO homeUrl = lists.homeUrlByBankId(loadedBotJob.getHomeBankingId(), loadedBotJob.getHomeUrlId());
        if (homeUrl != null) {
            loadedBotJob.setHomeUrlId(homeUrl.getId());
            homeBanking.setUrl(homeUrl.getUrl());
        }

        return BotJobSelection.loaded(
                loadedBotJob, loadedBotJob.getName(), excelBasePath + "\\" + loadedBotJob.getName() + ".xlsx");
    }

    boolean resetInstructionExecutionFlags() {
        if (lists.botJobs().isEmpty()) {
            return false;
        }
        lists.botJobs().get(0).getBlockLoadDTOList().stream()
                .flatMap(block -> block.getInstructionLoad().stream())
                .forEach(instruction -> instruction.setExecuted(false));
        return true;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<InstructionLoad> loadAndFixExcelGoto(BotJobLoadDTO currentBotJob) {
        List<InstructionLoad> excelDataGoto = engine.loadExcelGotoBlock(currentBotJob.getId(), INSTRUCTION_TABLE);
        if (hasMissingParentBlock(excelDataGoto)) {
            InstructionLoad firstGoto = excelDataGoto.get(0);
            engine.fixExcelGoto(
                    INSTRUCTION_TABLE,
                    currentBotJob.getId(),
                    firstGoto.getId(),
                    firstGoto.getBlockId());
            excelDataGoto = engine.loadExcelGotoBlock(currentBotJob.getId(), INSTRUCTION_TABLE);
        }
        return excelDataGoto;
    }

    private boolean hasMissingParentBlock(List<InstructionLoad> excelDataGoto) {
        return !excelDataGoto.isEmpty()
                && (excelDataGoto.get(0).getParentBlockId() == null
                        || excelDataGoto.get(0).getParentBlockId() <= 0);
    }

    record Result(
            ErrorMessage errorMessage,
            List<InstructionLoad> excelDataGoto,
            List<BlockLoadDTO> blocksLoaded,
            boolean botJobMissing) {}

    record BotJobSelection(
            boolean loaded,
            boolean botJobMissing,
            boolean homeBankingMissing,
            BotJobLoadDTO botJob,
            String botJobName,
            String excelPath) {
        private static BotJobSelection loaded(BotJobLoadDTO botJob, String botJobName, String excelPath) {
            return new BotJobSelection(true, false, false, botJob, botJobName, excelPath);
        }

        private static BotJobSelection missingBotJob() {
            return new BotJobSelection(false, true, false, null, null, null);
        }

        private static BotJobSelection missingHomeBanking() {
            return new BotJobSelection(false, false, true, null, null, null);
        }
    }

    interface EnginePort {
        ErrorMessage loadHomeBanking(Integer homeBankingId);

        ErrorMessage loadHomeUrls(Integer homeBankingId);

        List<InstructionLoad> loadExcelGotoBlock(int whereId, String tableName);

        ErrorMessage fixExcelGoto(String tableName, int whereId, int instructionId, int newParentBlockId);

        ErrorMessage loadCompleteJobs(int botJobId);

        ErrorMessage loadAllVariables(String varTable, int whereId);

        ErrorMessage loadAllActionsPerBlock(List<BlockLoadDTO> blockLoadDTOList);
    }

    interface ListsPort {
        List<BotJobLoadDTO> botJobs();

        HomeBankingLoadDTO homeBankingById(int homeBankingId);

        HomeUrlDTO homeUrlByBankId(int homeBankingId, int homeUrlId);
    }

    private record PerformDBEnginePort(PerformDBEngine engine) implements EnginePort {
        @Override
        public ErrorMessage loadHomeBanking(Integer homeBankingId) {
            return engine.loadHomeBanking(homeBankingId);
        }

        @Override
        public ErrorMessage loadHomeUrls(Integer homeBankingId) {
            return engine.loadHomeUrls(homeBankingId);
        }

        @Override
        public List<InstructionLoad> loadExcelGotoBlock(int whereId, String tableName) {
            return engine.loadExcelGotoBlock(whereId, tableName);
        }

        @Override
        public ErrorMessage fixExcelGoto(String tableName, int whereId, int instructionId, int newParentBlockId) {
            return engine.fixExcelGoto(tableName, whereId, instructionId, newParentBlockId);
        }

        @Override
        public ErrorMessage loadCompleteJobs(int botJobId) {
            return engine.loadCompleteJobs(botJobId);
        }

        @Override
        public ErrorMessage loadAllVariables(String varTable, int whereId) {
            return engine.loadAllVariables(varTable, whereId);
        }

        @Override
        public ErrorMessage loadAllActionsPerBlock(List<BlockLoadDTO> blockLoadDTOList) {
            return engine.loadAllActionsPerBlock(blockLoadDTOList);
        }
    }

    private record PerformListsPort(PerformLists lists) implements ListsPort {
        @Override
        public List<BotJobLoadDTO> botJobs() {
            return lists.getListBotJob();
        }

        @Override
        public HomeBankingLoadDTO homeBankingById(int homeBankingId) {
            return lists.getHomeBankingById(homeBankingId);
        }

        @Override
        public HomeUrlDTO homeUrlByBankId(int homeBankingId, int homeUrlId) {
            return lists.getHomeUrlByBankId(homeBankingId, homeUrlId);
        }
    }
}
