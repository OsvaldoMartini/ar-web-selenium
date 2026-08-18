package com.allinweb.ch.facade;

import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.PayloadJson;
import com.allinweb.ch.util.ErrorMessage;
import com.allinweb.ch.util.ExcelUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Presentation-neutral owner of Bot Job workspace cache activation and grid snapshot loading.
 *
 * <p>The desktop presentation applies the returned snapshots to React sessions, but it no longer owns the
 * ordering, failure cleanup, or scanner-selection policy for the shared backend caches.
 */
public final class BotJobWorkspaceService {

    private static final BotJobWorkspaceService INSTANCE = new BotJobWorkspaceService(
            new DefaultDataPort(), ExcelUtils::createExcelDataFile, new Gson());

    private final DataPort data;
    private final ExcelPort excel;
    private final Gson gson;

    BotJobWorkspaceService(DataPort data, ExcelPort excel, Gson gson) {
        this.data = data;
        this.excel = excel;
        this.gson = gson;
    }

    public static BotJobWorkspaceService getInstance() {
        return INSTANCE;
    }

    /** Replaces every job-scoped cache and returns the initial grid payloads. */
    public GridSnapshot activate(BotJobLoadDTO botJob) {
        requireJob(botJob);
        data.clearAllCaches();
        try {
            excel.create(botJob, null);
            requireSuccess(data.loadVariables(botJob.getId()));
            requireSuccess(data.loadPageFields(botJob.getId()));
            return loadGridSnapshot(botJob);
        } catch (RuntimeException failure) {
            data.clearAllCaches();
            throw failure;
        }
    }

    /** Reloads both grids as one fail-closed cache transition. */
    public GridSnapshot refresh(BotJobLoadDTO botJob) {
        requireJob(botJob);
        try {
            return loadGridSnapshot(botJob);
        } catch (RuntimeException failure) {
            data.clearGridCaches();
            throw failure;
        }
    }

    public void clearAllCaches() {
        data.clearAllCaches();
    }

    /** Preserves the existing desktop scanner transition without coupling it to a scene shell. */
    public ScannerDisposition scannerDisposition(BotJobLoadDTO botJob, Integer currentScannerBotJobId) {
        requireJob(botJob);
        if (isMobile(botJob.getPriority())) return ScannerDisposition.CLOSE;
        if (isWebApp(botJob.getPriority())
                && currentScannerBotJobId != null
                && !currentScannerBotJobId.equals(botJob.getId())) {
            return ScannerDisposition.OPEN;
        }
        return ScannerDisposition.KEEP;
    }

    private GridSnapshot loadGridSnapshot(BotJobLoadDTO botJob) {
        data.clearGridCaches();
        requireSuccess(data.loadCompleteJob(botJob.getId()));
        requireSuccess(data.loadBotJobBlocks(botJob.getId(), botJob.getName()));
        String botJobJson = payload(botJob, data.botJobs(), data.botJobBlocks());

        requireSuccess(data.loadComponents(botJob.getHomeBankingId(), botJob.getId(), botJob.getName()));
        requireSuccess(data.loadComponentBlocks(botJob.getHomeBankingId(), botJob.getName()));
        String componentJson = componentPayload(botJob, data.components(), data.componentBlocks());
        return new GridSnapshot(botJobJson, componentJson);
    }

    /**
     * Components always receive an envelope because component blocks can exist without instructions.
     * The separate block catalog is the authoritative source for block identity and presentation.
     */
    private String componentPayload(
            BotJobLoadDTO selectedBotJob,
            List<BotJobLoadDTO> jobs,
            List<BlockLoadDTO> blocks) {
        List<InstructionLoad> instructions = List.of();
        if (jobs != null && !jobs.isEmpty()) {
            List<InstructionLoad> loaded = data.buildJsonViewData(jobs);
            if (loaded != null) instructions = loaded;
        }

        JsonObject payload = new JsonObject();
        payload.add("instructions", gson.toJsonTree(instructions));
        payload.add("blocks", gson.toJsonTree(componentBlockCatalog(blocks)));
        payload.addProperty("botJobId", selectedBotJob.getId());
        payload.addProperty("botJobName", selectedBotJob.getName());
        payload.addProperty("homeBankingId", selectedBotJob.getHomeBankingId());
        return gson.toJson(payload);
    }

    private static List<Map<String, Object>> componentBlockCatalog(List<BlockLoadDTO> blocks) {
        if (blocks == null || blocks.isEmpty()) return List.of();

        return blocks.stream()
                .filter(block -> block != null && block.getId() != null)
                .sorted(Comparator
                        .comparingInt((BlockLoadDTO block) ->
                                block.getBlockOrderNumber() == null
                                        ? Integer.MAX_VALUE
                                        : block.getBlockOrderNumber())
                        .thenComparingInt(BlockLoadDTO::getId))
                .map(block -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("blockId", block.getId());
                    entry.put("blockOrderNumber", block.getBlockOrderNumber());
                    entry.put("blockName", block.getName());
                    entry.put("blockActive", block.getActive());
                    entry.put("blockWait", block.getWait());
                    entry.put("exportFile", block.getExportFile());
                    return entry;
                })
                .toList();
    }

    private String payload(
            BotJobLoadDTO selectedBotJob,
            List<BotJobLoadDTO> jobs,
            List<BlockLoadDTO> blocks) {
        if (jobs != null && !jobs.isEmpty()) {
            List<InstructionLoad> instructions = data.buildJsonViewData(jobs);
            if (instructions != null && !instructions.isEmpty()) return gson.toJson(instructions);
        }

        int blockId = -1;
        String blockName = "1# Default Block";
        if (selectedBotJob.getBlockId() == null && blocks != null && !blocks.isEmpty()) {
            BlockLoadDTO first = blocks.get(0);
            if (first != null) {
                blockId = first.getId() == null ? -1 : first.getId();
                blockName = first.getName() == null ? blockName : first.getName();
            }
        }
        return gson.toJson(new PayloadJson(selectedBotJob.getId(), blockId, blockName, 0));
    }

    private static void requireJob(BotJobLoadDTO botJob) {
        if (botJob == null || botJob.getId() == null || botJob.getId() <= 0) {
            throw new IllegalArgumentException("An active Bot Job is required");
        }
    }

    private static void requireSuccess(ErrorMessage error) {
        if (error != null) throw new WorkspaceLoadException(error);
    }

    private static boolean isWebApp(String priority) {
        return priority == null || priority.trim().isEmpty() || "Web App".equalsIgnoreCase(priority.trim());
    }

    private static boolean isMobile(String priority) {
        String normalized = priority == null ? "" : priority.trim();
        return "Android".equalsIgnoreCase(normalized) || "iOS".equalsIgnoreCase(normalized);
    }

    public record GridSnapshot(String botJobJson, String componentJson) {}

    public enum ScannerDisposition {
        OPEN,
        CLOSE,
        KEEP
    }

    public static final class WorkspaceLoadException extends IllegalStateException {
        private final ErrorMessage error;

        private WorkspaceLoadException(ErrorMessage error) {
            super(errorText(error));
            this.error = error;
        }

        public ErrorMessage error() {
            return error;
        }

        private static String errorText(ErrorMessage error) {
            if (error == null) return "Unable to load Bot Job workspace data";
            if (error.getErrorMessage() != null && !error.getErrorMessage().isBlank()) return error.getErrorMessage();
            if (error.getErrorHeader() != null && !error.getErrorHeader().isBlank()) return error.getErrorHeader();
            return error.getErrorTitle() == null || error.getErrorTitle().isBlank()
                    ? "Unable to load Bot Job workspace data"
                    : error.getErrorTitle();
        }
    }

    @FunctionalInterface
    interface ExcelPort {
        void create(BotJobLoadDTO botJob, String duplicateName);
    }

    interface DataPort {
        void clearAllCaches();

        void clearGridCaches();

        ErrorMessage loadVariables(int botJobId);

        ErrorMessage loadPageFields(int botJobId);

        ErrorMessage loadCompleteJob(int botJobId);

        ErrorMessage loadBotJobBlocks(int botJobId, String botJobName);

        ErrorMessage loadComponents(int homeBankingId, int botJobId, String botJobName);

        ErrorMessage loadComponentBlocks(int homeBankingId, String botJobName);

        List<BotJobLoadDTO> botJobs();

        List<BlockLoadDTO> botJobBlocks();

        List<BotJobLoadDTO> components();

        List<BlockLoadDTO> componentBlocks();

        List<InstructionLoad> buildJsonViewData(List<BotJobLoadDTO> jobs);
    }

    private static final class DefaultDataPort implements DataPort {
        private final PerformLists lists = PerformLists.getInstance();
        private final PerformDBEngine engine = PerformDBEngine.getInstance();
        private final PerformDataBase database = PerformDataBase.getInstance();

        @Override
        public void clearAllCaches() {
            lists.getListVariablesUser().clear();
            lists.getListWebPageItems().clear();
            clearGridCaches();
            lists.getAllActions().clear();
        }

        @Override
        public void clearGridCaches() {
            lists.getListBotJob().clear();
            lists.getListBlock().clear();
            lists.getListBotJobComp().clear();
            lists.getListBlockComp().clear();
        }

        @Override
        public ErrorMessage loadVariables(int botJobId) {
            return database.loadAllVariablesByCriteria("variable", botJobId, -1, "");
        }

        @Override
        public ErrorMessage loadPageFields(int botJobId) {
            return database.loadWebPageFields(botJobId, "bot_job");
        }

        @Override
        public ErrorMessage loadCompleteJob(int botJobId) {
            return engine.loadCompleteJobs(botJobId);
        }

        @Override
        public ErrorMessage loadBotJobBlocks(int botJobId, String botJobName) {
            return database.loadBlocks(botJobId, botJobName, "block");
        }

        @Override
        public ErrorMessage loadComponents(int homeBankingId, int botJobId, String botJobName) {
            return database.loadComponentsComplete(homeBankingId, botJobId, botJobName);
        }

        @Override
        public ErrorMessage loadComponentBlocks(int homeBankingId, String botJobName) {
            return database.loadBlocks(homeBankingId, botJobName, "component_block");
        }

        @Override
        public List<BotJobLoadDTO> botJobs() {
            return lists.getListBotJob();
        }

        @Override
        public List<BlockLoadDTO> botJobBlocks() {
            return lists.getListBlock();
        }

        @Override
        public List<BotJobLoadDTO> components() {
            return lists.getListBotJobComp();
        }

        @Override
        public List<BlockLoadDTO> componentBlocks() {
            return lists.getListBlockComp();
        }

        @Override
        public List<InstructionLoad> buildJsonViewData(List<BotJobLoadDTO> jobs) {
            return lists.buildJsonViewData(jobs);
        }
    }
}
