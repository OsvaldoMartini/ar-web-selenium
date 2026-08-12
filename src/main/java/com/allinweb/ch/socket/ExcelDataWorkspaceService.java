package com.allinweb.ch.socket;

import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.BotJobNativeOperationService;
import com.allinweb.ch.facade.BotJobToolbarConcurrencyGuard;
import com.allinweb.ch.facade.ExcelSyntheticDatasetStore;
import com.allinweb.ch.facade.VariablesWorkspacePreferenceStore;
import com.allinweb.ch.facade.scanner.prelaunch.ScannerPreLaunchExcelLoader;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.DetachedWorkspaceSessions;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.allinweb.ch.util.ExcelUtils;
import com.allinweb.ch.util.ExtractedData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

/** Owns the fixed detached Excel Data page and its retained execution dataset. */
@Slf4j
public final class ExcelDataWorkspaceService {
    public static final String SESSION_ID = DetachedWorkspaceSessions.EXCEL_DATA_MANAGER;
    private static final ExcelDataWorkspaceService INSTANCE = new ExcelDataWorkspaceService();
    private static final AtomicLong DATASET_EPOCHS = new AtomicLong();

    private final PerformLists lists = PerformLists.getInstance();
    private final PerformDataBase database = PerformDataBase.getInstance();
    private final PerformDBEngine engine = PerformDBEngine.getInstance();
    private final ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();
    private final ExcelSyntheticDatasetStore syntheticStore = new ExcelSyntheticDatasetStore();
    private final VariablesWorkspacePreferenceStore preferences = new VariablesWorkspacePreferenceStore();
    private final BotJobNativeOperationService nativeOperations =
            BotJobNativeOperationService.createDefault(
                    ARPropertyManager.getInstance(), new BotJobToolbarConcurrencyGuard());
    private Binding binding;
    private Session readyTransport;
    private long readyDatasetEpoch = -1L;

    private ExcelDataWorkspaceService() {}

    public static ExcelDataWorkspaceService getInstance() {
        return INSTANCE;
    }

    public synchronized boolean isOpenForBotJob(int botJobId) {
        return binding != null
                && binding.botJobId() == botJobId
                && WebSocketSessionManager.isSessionOpen(SESSION_ID);
    }

    public synchronized JsonObject openForBotJob(int botJobId) {
        try {
            ErrorMessage error = lists.getQuickBotJobs().isEmpty() ? database.loadQuickBotJobs() : null;
            if (error != null) return failure(errorText(error));
            BotJobLoadDTO job = lists.getQuickBotJobById(botJobId);
            if (job == null) return failure("Bot Job " + botJobId + " was not found.");
            if (binding != null && binding.botJobId() == botJobId) {
                boolean opened = PagesOpenWorkspaceService.getInstance()
                        .openOrFocusDetachedWorkspace(
                                SESSION_ID, botJobId, "Excel Data workspace requested for execution.");
                if (opened) publishRetargetSnapshot();
                return opened
                        ? snapshot("Retained Excel memory opened for execution.")
                        : failure("Excel Data workspace could not be opened.");
            }
            error = database.loadBlocks(job.getId(), job.getName(), "block");
            if (error == null) error = engine.loadAllActionsPerBlock(lists.getListBlock());
            if (error != null) return failure(errorText(error));

            Path workbook = Path.of(ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_EXCEL))
                    .resolve(job.getName().trim() + ARConstants.FILE_FORMAT_EXCEL)
                    .toAbsolutePath()
                    .normalize();
            ExtractedData data = loader.load(workbook.toString(), lists);
            ExtractedData synthetic = syntheticStore.load(job.getHomeBankingId(), job.getId());
            String syntheticContext = preferences.loadSyntheticContext(job.getHomeBankingId(), job.getId());
            binding = new Binding(job.getId(), job.getHomeBankingId(), job.getName(), workbook, data, synthetic,
                    syntheticContext);
            readyTransport = null;
            readyDatasetEpoch = -1L;
            selectExecutionData();
            boolean opened = PagesOpenWorkspaceService.getInstance()
                    .openOrFocusDetachedWorkspace(SESSION_ID, botJobId, "Excel Data workspace requested.");
            if (opened) publishRetargetSnapshot();
            return opened ? snapshot("Excel Data workspace opened.") : failure("Excel Data workspace could not be opened.");
        } catch (Exception error) {
            log.error("Unable to open Excel Data workspace for Bot Job {}", botJobId, error);
            return failure(error.getMessage());
        }
    }

    private void publishRetargetSnapshot() {
        if (binding == null || !WebSocketSessionManager.isSessionOpen(SESSION_ID)) return;
        JsonObject current = snapshot("Excel Data reloaded for the selected Bot Job.");
        WebSocketSessionManager.getInstance().sendMessageJson(
                binding.homeBankingId(), SESSION_ID, current.toString(), "excelData.retarget");
    }

    public synchronized JsonObject bootstrap(JsonObject request, String sessionId, Session transport) {
        if (!SESSION_ID.equals(sessionId)
                || transport == null
                || !transport.isOpen()
                || WebSocketSessionManager.getSession(SESSION_ID) != transport) {
            return failure("Only the active Excel Data workspace can load this dataset.");
        }
        if (binding == null) return failure("No Bot Job Excel dataset is open.");
        return snapshot("Excel dataset loaded from memory.");
    }

    /** Records that React rendered the exact current Excel Data owner/dataset generation. */
    public synchronized JsonObject ready(JsonObject request, String sessionId, Session transport) {
        JsonObject refusal = requireActiveTransport(sessionId, transport);
        if (refusal != null) return refusal;
        if (binding == null
                || request == null
                || !request.has("botJobId")
                || request.get("botJobId").getAsInt() != binding.botJobId()
                || !request.has("homeBankingId")
                || request.get("homeBankingId").getAsInt() != binding.homeBankingId()
                || !request.has("datasetEpoch")
                || request.get("datasetEpoch").getAsLong() != binding.datasetEpoch) {
            return failure("The Excel Data page does not match the active execution dataset.");
        }
        readyTransport = transport;
        readyDatasetEpoch = binding.datasetEpoch;
        notifyAll();
        return snapshot("Excel Data is visible and loaded.");
    }

    /** Waits for the independently connected React Excel Data page to render this owner. */
    public synchronized void requireReady(int homeBankingId, int botJobId, long timeoutMillis) {
        long deadline = System.nanoTime()
                + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!isReady(homeBankingId, botJobId)) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                throw new IllegalStateException(
                        "Excel Data did not become visible and ready before execution.");
            }
            try {
                java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(this, remaining);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Excel Data readiness was interrupted before execution.", interrupted);
            }
        }
    }

    public synchronized boolean isReadyForOwner(int homeBankingId, int botJobId) {
        return isReady(homeBankingId, botJobId);
    }

    private boolean isReady(int homeBankingId, int botJobId) {
        return binding != null
                && binding.homeBankingId() == homeBankingId
                && binding.botJobId() == botJobId
                && readyDatasetEpoch == binding.datasetEpoch
                && readyTransport != null
                && readyTransport.isOpen()
                && WebSocketSessionManager.getSession(SESSION_ID) == readyTransport;
    }

    public synchronized JsonObject close(JsonObject request, String sessionId, Session transport) {
        if (!SESSION_ID.equals(sessionId)
                || transport == null
                || WebSocketSessionManager.getSession(SESSION_ID) != transport) {
            return failure("Only the active Excel Data workspace can close this dataset.");
        }
        boolean released = binding != null && loader.close(binding.workbook().toString());
        binding = null;
        readyTransport = null;
        readyDatasetEpoch = -1L;
        notifyAll();
        JsonObject response = success(released ? "Excel dataset released from memory." : "Excel dataset closed.");
        response.addProperty("released", released);
        return response;
    }

    public synchronized JsonObject generate(
            JsonObject request, String sessionId, Session transport, boolean synthetic) {
        JsonObject authorization = requireActiveTransport(sessionId, transport);
        if (authorization != null) return authorization;
        if (binding == null) return failure("No Bot Job Excel dataset is open.");
        if (request == null || !request.has("confirmed") || !request.get("confirmed").getAsBoolean()) {
            return failure("Confirm replacement of the active Bot Job Excel file.");
        }
        try {
            ErrorMessage error = database.loadBlocks(binding.botJobId(), binding.botJobName(), "block");
            if (error == null) error = engine.loadAllActionsPerBlock(lists.getListBlock());
            if (error != null) return failure(errorText(error));

            if (synthetic) {
                int rowCount = request.has("rowCount") ? request.get("rowCount").getAsInt() : 1;
                if (rowCount < 1 || rowCount > 1000) {
                    return failure("Synthetic row count must be between 1 and 1000.");
                }
                String context = request.has("context")
                        ? request.get("context").getAsString().trim()
                        : binding.syntheticContext;
                ExtractedData syntheticRows = request.has("blocks")
                        ? syntheticDataFromRequest(binding.realData, request.getAsJsonArray("blocks"), rowCount)
                        : syntheticData(binding.data(), rowCount, context);
                binding.syntheticData = syntheticRows;
                binding.syntheticContext = context.isBlank() ? "Bank Account" : context;
                binding.mode = Mode.SYNTHETIC;
                binding.syntheticDirty = true;
                binding.loadedAt = Instant.now();
                binding.changed();
                binding.resetSelectedRow();
                selectExecutionData();
                return snapshot("Synthetic rows generated in memory. Save Synthetic Data to keep them after restart.");
            }

            ExtractedData source = ExcelUtils.isFileExists(binding.botJobName(), lists.getAllActions());
            if (source != null && source.getErrorMessage() != null) return failure(source.getErrorMessage());
            File generated = new ExcelUtils()
                    .generateExcelFiles(source, binding.botJobName().trim(), null, false);
            Path generatedPath = generated.toPath().toAbsolutePath().normalize();
            if (!generatedPath.equals(binding.workbook())) {
                return failure("Excel generation produced an unexpected file.");
            }
            loader.close(binding.workbook().toString());
            ExtractedData reloaded = loader.load(binding.workbook().toString(), lists);
            binding.realData = reloaded;
            binding.mode = Mode.REAL;
            binding.realDirty = false;
            binding.loadedAt = Instant.now();
            binding.changed();
            binding.resetSelectedRow();
            selectExecutionData();
            nativeOperations.openFile(generated);
            return snapshot("Excel data file generated and loaded into memory.");
        } catch (Exception error) {
            log.error(
                    "Unable to generate {} Excel data for Bot Job {}",
                    synthetic ? "synthetic" : "standard",
                    binding.botJobId(),
                    error);
            return excelFileFailure(error, "Excel data could not be generated.");
        }
    }

    public synchronized JsonObject addRow(JsonObject request, String sessionId, Session transport) {
        JsonObject authorization = requireActiveTransport(sessionId, transport);
        if (authorization != null) return authorization;
        if (binding == null) return failure("No Bot Job Excel dataset is open.");
        int rowCount = binding.data().getNumberOfDataRows();
        if (rowCount < 1) return failure("Create or load the first Excel row before copying a previous row.");
        int sourceRow = rowCount - 1;
        int targetRow = rowCount;
        for (String blockName : binding.data().getBlocks()) {
            for (String column : binding.data().getExtractedFields(blockName)) {
                binding.data().addFieldValue(
                        blockName,
                        column,
                        binding.data().getFieldValue(blockName, column, sourceRow),
                        targetRow);
            }
        }
        binding.setDirty(true);
        binding.loadedAt = Instant.now();
        binding.changed();
        binding.reconcileSelectedRow();
        return snapshot("Row " + (targetRow + 1) + " copied from row " + (sourceRow + 1) + " in memory.");
    }

    public synchronized JsonObject save(JsonObject request, String sessionId, Session transport) {
        JsonObject authorization = requireActiveTransport(sessionId, transport);
        if (authorization != null) return authorization;
        if (binding == null) return failure("No Bot Job Excel dataset is open.");
        if (request == null || !request.has("confirmed") || !request.get("confirmed").getAsBoolean()) {
            return failure("Confirm saving the in-memory dataset to the active Bot Job Excel file.");
        }
        try {
            ErrorMessage error = database.loadBlocks(binding.botJobId(), binding.botJobName(), "block");
            if (error == null) error = engine.loadAllActionsPerBlock(lists.getListBlock());
            if (error != null) return failure(errorText(error));
            if (binding.mode == Mode.SYNTHETIC) {
                syntheticStore.save(binding.homeBankingId(), binding.botJobId(), binding.data());
                binding.syntheticDirty = false;
                return snapshot("Synthetic memory data saved to the database.");
            }
            File saved = new ExcelUtils()
                    .generateExcelFiles(binding.data(), binding.botJobName().trim(), null, false);
            if (!saved.toPath().toAbsolutePath().normalize().equals(binding.workbook())) {
                return failure("Excel save produced an unexpected file.");
            }
            binding.realDirty = false;
            return snapshot("In-memory Excel dataset saved atomically to the original workbook.");
        } catch (Exception error) {
            log.error("Unable to save in-memory Excel data for Bot Job {}", binding.botJobId(), error);
            return excelFileFailure(error, "Excel data could not be saved.");
        }
    }

    public synchronized void publishActiveCell(
            int botJobId, String blockName, String column, int rowIndex, Integer instructionId) {
        if (binding == null || binding.botJobId() != botJobId) return;
        JsonObject event = new JsonObject();
        event.addProperty("botJobId", botJobId);
        event.addProperty("blockName", blockName);
        event.addProperty("column", column);
        event.addProperty("rowIndex", rowIndex);
        if (instructionId != null) event.addProperty("instructionId", instructionId);
        WebSocketSessionManager.getInstance()
                .sendMessageJson(binding.homeBankingId(), SESSION_ID, event.toString(), "excelData.activeCell");
    }

    public synchronized JsonObject applyExecutionMode(int botJobId) {
        if (binding == null || binding.botJobId() != botJobId) {
            return failure("Excel Data memory is not open for Bot Job " + botJobId + ".");
        }
        selectExecutionData();
        return snapshot(binding.mode + " in-memory data selected for TEST RUN.");
    }

    /**
     * Keeps durable Batch/Launch execution on an explicitly saved REAL workbook.
     *
     * <p>Smoke, Integration, and TEST RUN intentionally consume current in-memory data so users
     * can validate edits before choosing whether to save them.
     */
    public synchronized JsonObject validateLaunchDataSaved(int botJobId) {
        if (binding == null || binding.botJobId() != botJobId) {
            return success("No retained Excel memory changes are pending for this Bot Job.");
        }
        if (blocksLaunchForUnsavedRealData(binding.mode.name(), binding.realDirty)) {
            return failure("REAL Excel memory has unsaved changes. Save to Excel before Launch.");
        }
        return success("Excel data is ready for Launch.");
    }

    /**
     * Freezes the exact retained REAL/SYNTHETIC dataset used by one Integration run.
     *
     * <p>The returned data is a deep copy, including unsaved in-memory edits. Later cell edits,
     * row deletes, mode changes, saves, or reloads in the detached Excel Data page therefore
     * cannot alter an already-started browser run.
     */
    public synchronized IntegrationDataset freezeIntegrationData(
            int botJobId, String expectedMode) {
        if (binding == null || binding.botJobId() != botJobId) {
            throw new IllegalStateException(
                    "Excel Data memory is not open for Bot Job " + botJobId + ".");
        }
        Mode requested;
        try {
            requested = Mode.valueOf(expectedMode == null
                    ? "" : expectedMode.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalidMode) {
            throw new IllegalArgumentException("Excel mode must be REAL or SYNTHETIC.");
        }
        if (binding.mode != requested) {
            throw new IllegalStateException(
                    "Excel Data mode changed. Reopen Smoke Test before Integration.");
        }
        ExtractedData frozen = binding.data().deepCopy();
        return new IntegrationDataset(
                binding.botJobId(),
                binding.homeBankingId(),
                binding.mode.name(),
                binding.datasetEpoch,
                binding.datasetRevision,
                datasetContentRevision(binding, frozen),
                binding.loadedAt,
                frozen);
    }

    /**
     * Freezes the currently selected REAL or SYNTHETIC memory for one manual GridItem action.
     *
     * <p>The selected mode is backend-owned; callers cannot select it through the test request.
     * Unsaved in-memory edits are intentionally included because this is a manual one-row test,
     * not an Integration run. A mismatched owner is reported as unavailable and never exposes the
     * dataset retained for another Bot Job or organization.
     */
    public synchronized Optional<GridItemDataset> freezeSelectedGridItemData(
            int homeBankingId, int botJobId) {
        if (binding == null
                || binding.homeBankingId() != homeBankingId
                || binding.botJobId() != botJobId) {
            return Optional.empty();
        }
        ExtractedData frozen = binding.data().deepCopy();
        return Optional.of(new GridItemDataset(
                binding.botJobId(),
                binding.homeBankingId(),
                binding.mode.name(),
                binding.datasetEpoch,
                binding.datasetRevision,
                binding.selectedRowIndex(),
                binding.loadedAt,
                frozen));
    }

    public synchronized JsonObject setMode(JsonObject request, String sessionId, Session transport) {
        JsonObject authorization = requireActiveTransport(sessionId, transport);
        if (authorization != null) return authorization;
        String requested = request != null && request.has("mode") ? request.get("mode").getAsString() : "REAL";
        binding.mode = "SYNTHETIC".equalsIgnoreCase(requested) ? Mode.SYNTHETIC : Mode.REAL;
        JsonObject reloadFailure = reloadSyntheticSelection();
        if (reloadFailure != null) return reloadFailure;
        binding.loadedAt = Instant.now();
        binding.changed();
        selectExecutionData();
        JsonObject response = snapshot(binding.mode + " data selected.");
        publishModeChanged();
        return response;
    }

    public synchronized JsonObject modeForBotJob(int botJobId) {
        JsonObject response = success("Excel execution data mode loaded.");
        response.addProperty("botJobId", botJobId);
        response.addProperty("mode", binding != null && binding.botJobId() == botJobId
                ? binding.mode.name() : Mode.REAL.name());
        return response;
    }

    public synchronized JsonObject setModeForBotJob(int botJobId, String requested) {
        if (binding == null || binding.botJobId() != botJobId) {
            JsonObject opened = openForBotJob(botJobId);
            if (!opened.has("ok") || !opened.get("ok").getAsBoolean()) return opened;
        }
        binding.mode = "SYNTHETIC".equalsIgnoreCase(requested) ? Mode.SYNTHETIC : Mode.REAL;
        JsonObject reloadFailure = reloadSyntheticSelection();
        if (reloadFailure != null) return reloadFailure;
        binding.loadedAt = Instant.now();
        binding.changed();
        selectExecutionData();
        JsonObject response = modeForBotJob(botJobId);
        response.addProperty("message", binding.mode + " data selected.");
        publishModeChanged();
        return response;
    }

    private JsonObject reloadSyntheticSelection() {
        if (binding.mode != Mode.SYNTHETIC) return null;
        try {
            ExtractedData saved = syntheticStore.load(binding.homeBankingId(), binding.botJobId());
            binding.syntheticData = saved == null ? emptyLike(binding.realData) : saved;
            binding.syntheticDirty = false;
            return null;
        } catch (Exception error) {
            log.error("Unable to reload synthetic Excel data for Bot Job {}", binding.botJobId(), error);
            return failure("Synthetic data could not be loaded from the database.");
        }
    }

    public synchronized JsonObject clearRows(
            JsonObject request, String sessionId, Session transport) {
        JsonObject authorization = requireActiveTransport(sessionId, transport);
        if (authorization != null) return authorization;
        if (binding == null) return failure("No Bot Job Excel dataset is open.");
        if (request == null || !request.has("confirmed") || !request.get("confirmed").getAsBoolean()) {
            return failure("Confirm clearing all in-memory Excel rows.");
        }
        if (binding.mode == Mode.REAL) {
            binding.realData = emptyLike(binding.realData);
        } else {
            ExtractedData syntheticSource = binding.syntheticData == null
                    ? binding.realData : binding.syntheticData;
            binding.syntheticData = emptyLike(syntheticSource);
        }
        binding.setDirty(true);
        binding.loadedAt = Instant.now();
        binding.changed();
        selectExecutionData();
        return snapshot(binding.mode + " rows cleared from memory.");
    }

    public synchronized JsonObject deleteRow(
            JsonObject request, String sessionId, Session transport) {
        JsonObject authorization = requireActiveTransport(sessionId, transport);
        if (authorization != null) return authorization;
        if (binding == null) return failure("No Bot Job Excel dataset is open.");
        try {
            int rowIndex = request.get("rowIndex").getAsInt();
            if (!binding.data().removeRow(rowIndex)) {
                return failure("The Excel memory row no longer exists.");
            }
            binding.rowDeleted(rowIndex);
            binding.setDirty(true);
            binding.loadedAt = Instant.now();
            binding.changed();
            selectExecutionData();
            return snapshot("Row " + (rowIndex + 1) + " deleted from " + binding.mode + " memory.");
        } catch (RuntimeException error) {
            return failure("The Excel memory row deletion is invalid.");
        }
    }

    public synchronized JsonObject selectRow(
            JsonObject request, String sessionId, Session transport) {
        JsonObject authorization = requireActiveTransport(sessionId, transport);
        if (authorization != null) return authorization;
        if (binding == null) return failure("No Bot Job Excel dataset is open.");
        try {
            int rowIndex = request.get("rowIndex").getAsInt();
            int rowCount = binding.data().getNumberOfDataRows();
            if (rowIndex < 0 || rowIndex >= rowCount) {
                return failure("The Excel memory row no longer exists.");
            }
            binding.selectRow(rowIndex);
            return snapshot("Row " + (rowIndex + 1) + " selected for GridItem input tests.");
        } catch (RuntimeException invalid) {
            return failure("Select a valid Excel memory row.");
        }
    }

    public synchronized JsonObject moveRow(
            JsonObject request, String sessionId, Session transport) {
        JsonObject authorization = requireActiveTransport(sessionId, transport);
        if (authorization != null) return authorization;
        if (binding == null) return failure("No Bot Job Excel dataset is open.");
        try {
            int fromIndex = request.get("fromIndex").getAsInt();
            int toIndex = request.get("toIndex").getAsInt();
            int rowCount = binding.data().getNumberOfDataRows();
            if (fromIndex < 0
                    || toIndex < 0
                    || fromIndex >= rowCount
                    || toIndex >= rowCount) {
                return failure("The Excel memory row move is outside the active dataset.");
            }
            if (fromIndex == toIndex) {
                return snapshot("The Excel memory row is already in that position.");
            }
            if (!binding.data().moveRow(fromIndex, toIndex)) {
                return failure("The Excel memory row could not be moved.");
            }
            binding.rowMoved(fromIndex, toIndex);
            binding.setDirty(true);
            binding.loadedAt = Instant.now();
            binding.changed();
            selectExecutionData();
            return snapshot(
                    "Row " + (fromIndex + 1) + " moved to " + (toIndex + 1)
                            + " in " + binding.mode + " memory.");
        } catch (RuntimeException invalid) {
            return failure("The Excel memory row move is invalid.");
        }
    }

    public synchronized JsonObject updateSyntheticContext(
            JsonObject request, String sessionId, Session transport) {
        JsonObject authorization = requireActiveTransport(sessionId, transport);
        if (authorization != null) return authorization;
        if (binding == null) return failure("No Bot Job Excel dataset is open.");
        try {
            String context = request.get("context").getAsString().trim();
            if (context.isBlank() || context.length() > 80) return failure("Select a valid synthetic context.");
            JsonObject metadata = new JsonObject();
            metadata.addProperty("source", "EXCEL_DATA_WORKSPACE");
            metadata.addProperty("contractVersion", 1);
            preferences.saveSyntheticContext(
                    binding.homeBankingId(), binding.botJobId(), context, metadata.toString());
            binding.syntheticContext = context;
            return snapshot("Synthetic context saved.");
        } catch (Exception error) {
            log.error("Unable to save synthetic context for Bot Job {}", binding.botJobId(), error);
            return failure("The synthetic context preference could not be saved.");
        }
    }

    private void publishModeChanged() {
        if (binding == null) return;
        JsonObject event = new JsonObject();
        event.addProperty("botJobId", binding.botJobId());
        event.addProperty("mode", binding.mode.name());
        WebSocketSessionManager.getInstance().sendMessageJson(
                binding.homeBankingId(),
                DetachedWorkspaceSessions.SMOKE_TEST_MANAGER,
                event.toString(),
                "excelData.mode.changed");
    }

    public synchronized JsonObject refresh(JsonObject request, String sessionId, Session transport) {
        JsonObject authorization = requireActiveTransport(sessionId, transport);
        if (authorization != null) return authorization;
        try {
            if (binding.mode == Mode.REAL) {
                loader.close(binding.workbook().toString());
                binding.realData = loader.load(binding.workbook().toString(), lists);
                binding.realDirty = false;
            } else {
                ExtractedData saved = syntheticStore.load(binding.homeBankingId(), binding.botJobId());
                binding.syntheticData = saved == null ? emptyLike(binding.realData) : saved;
                binding.syntheticDirty = false;
            }
            binding.loadedAt = Instant.now();
            binding.changed();
            selectExecutionData();
            return snapshot(binding.mode + " data reloaded.");
        } catch (Exception error) {
            log.error("Unable to refresh {} Excel memory for Bot Job {}", binding.mode, binding.botJobId(), error);
            return excelFileFailure(error, binding.mode + " data could not be reloaded.");
        }
    }

    public synchronized JsonObject updateCell(JsonObject request, String sessionId, Session transport) {
        JsonObject authorization = requireActiveTransport(sessionId, transport);
        if (authorization != null) return authorization;
        try {
            String block = request.get("blockName").getAsString();
            String column = request.get("column").getAsString();
            int row = request.get("rowIndex").getAsInt();
            if (row < 0 || !binding.data().getBlocks().contains(block)
                    || !binding.data().getExtractedFields(block).contains(column)) {
                return failure("The Excel memory cell no longer exists.");
            }
            String value = request.has("value") && !request.get("value").isJsonNull()
                    ? request.get("value").getAsString() : null;
            binding.data().addFieldValue(block, column, value, row);
            binding.setDirty(true);
            binding.loadedAt = Instant.now();
            binding.changed();
            selectExecutionData();
            return snapshot("Cell updated in " + binding.mode + " memory.");
        } catch (RuntimeException error) {
            return failure("The Excel memory cell update is invalid.");
        }
    }

    private void selectExecutionData() {
        binding.reconcileSelectedRow();
        loader.replaceInMemory(binding.workbook().toString(), binding.data());
        loader.setExecutionEnabled(binding.workbook().toString(), true);
    }

    private ExtractedData emptyLike(ExtractedData template) {
        ExtractedData empty = new ExtractedData();
        for (String block : template.getBlocks()) {
            for (String column : template.getExtractedFields(block)) empty.addField(block, column);
        }
        return empty;
    }

    private ExtractedData syntheticData(ExtractedData template, int rowCount, String context) {
        ExtractedData synthetic = new ExtractedData();
        String contextName = normalizeSyntheticName(context == null || context.isBlank()
                ? "Financial" : context);
        for (String blockName : template.getBlocks()) {
            for (String column : template.getExtractedFields(blockName)) {
                for (int row = 0; row < rowCount; row++) {
                    synthetic.addFieldValue(
                            blockName,
                            column,
                            contextName + "_" + normalizeSyntheticName(column) + "_" + (row + 1),
                            row);
                }
            }
        }
        return synthetic;
    }

    private ExtractedData syntheticDataFromRequest(
            ExtractedData template, JsonArray blocks, int rowCount) {
        ExtractedData synthetic = emptyLike(template);
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            JsonObject block = blocks.get(blockIndex).getAsJsonObject();
            String blockName = block.get("name").getAsString();
            if (!template.getBlocks().contains(blockName)) continue;
            JsonArray rows = block.getAsJsonArray("rows");
            for (int rowIndex = 0; rowIndex < Math.min(rowCount, rows.size()); rowIndex++) {
                JsonObject values = rows.get(rowIndex).getAsJsonObject();
                for (String column : template.getExtractedFields(blockName)) {
                    if (!values.has(column)) continue;
                    synthetic.addFieldValue(blockName, column,
                            values.get(column).isJsonNull() ? null : values.get(column).getAsString(), rowIndex);
                }
            }
        }
        return synthetic;
    }

    private JsonObject excelFileFailure(Exception error, String fallback) {
        String detail = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        String type = error.getClass().getSimpleName().toLowerCase();
        JsonObject response;
        if (detail.contains("being used") || detail.contains("another process")
                || detail.contains("access is denied") || type.contains("accessdenied")) {
            response = failure("Close the Excel file and try again.");
            response.addProperty("errorCode", "EXCEL_FILE_IN_USE");
        } else if (detail.contains("corrupt") || detail.contains("invalid")
                || detail.contains("zip") || type.contains("format")) {
            response = failure("The Excel file is damaged or has an invalid format.");
            response.addProperty("errorCode", "EXCEL_FILE_CORRUPTED");
        } else {
            response = failure(fallback);
            response.addProperty("errorCode", "EXCEL_FILE_OPERATION_FAILED");
        }
        return response;
    }

    private String normalizeSyntheticName(String column) {
        if (column == null || column.isBlank()) return "VALUE";
        return column.trim().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "").toUpperCase();
    }

    private JsonObject requireActiveTransport(String sessionId, Session transport) {
        if (!SESSION_ID.equals(sessionId)
                || transport == null
                || !transport.isOpen()
                || WebSocketSessionManager.getSession(SESSION_ID) != transport) {
            return failure("Only the active Excel Data workspace can use this operation.");
        }
        return null;
    }

    private JsonObject snapshot(String message) {
        if (binding == null) return failure("No Bot Job Excel dataset is open.");
        ExtractedData data = binding.data();
        JsonObject response = success(message);
        response.addProperty("botJobId", binding.botJobId());
        response.addProperty("homeBankingId", binding.homeBankingId());
        response.addProperty("botJobName", binding.botJobName());
        response.addProperty("fileName", binding.workbook().getFileName().toString());
        response.addProperty("filePath", binding.workbook().toString());
        response.addProperty("loadedAt", binding.loadedAt().toString());
        response.addProperty("datasetEpoch", binding.datasetEpoch);
        response.addProperty("datasetRevision", binding.datasetRevision);
        response.addProperty("rowCount", data.getNumberOfDataRows());
        if (binding.selectedRowIndex() == null) response.add("selectedRowIndex", null);
        else response.addProperty("selectedRowIndex", binding.selectedRowIndex());
        response.addProperty("dirty", binding.dirty());
        response.addProperty("mode", binding.mode.name());
        response.addProperty("syntheticContext", binding.syntheticContext);

        JsonArray blocks = new JsonArray();
        for (String blockName : data.getBlocks()) {
            JsonObject block = new JsonObject();
            block.addProperty("name", blockName);
            JsonArray columns = new JsonArray();
            data.getExtractedFields(blockName).forEach(columns::add);
            block.add("columns", columns);
            JsonArray rows = new JsonArray();
            for (int rowIndex = 0; rowIndex < data.getNumberOfDataRows(); rowIndex++) {
                JsonObject row = new JsonObject();
                row.addProperty("index", rowIndex);
                JsonObject values = new JsonObject();
                for (Map.Entry<String, String> value : data.getRowFieldValues(blockName, rowIndex).entrySet()) {
                    if (value.getValue() == null) values.add(value.getKey(), null);
                    else values.addProperty(value.getKey(), value.getValue());
                }
                row.add("values", values);
                rows.add(row);
            }
            block.add("rows", rows);
            blocks.add(block);
        }
        response.add("blocks", blocks);
        return response;
    }

    private JsonObject success(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.addProperty("message", message);
        return response;
    }

    private JsonObject failure(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("error", message == null || message.isBlank() ? "Excel dataset is unavailable." : message);
        return response;
    }

    private String errorText(ErrorMessage error) {
        if (error == null) return "Excel dataset is unavailable.";
        if (error.getErrorMessage() != null && !error.getErrorMessage().isBlank()) return error.getErrorMessage();
        if (error.getErrorHeader() != null && !error.getErrorHeader().isBlank()) return error.getErrorHeader();
        return error.getErrorTitle();
    }

    private enum Mode { REAL, SYNTHETIC }

    public record IntegrationDataset(
            int botJobId,
            int homeBankingId,
            String mode,
            long datasetEpoch,
            long datasetRevision,
            String contentRevision,
            Instant loadedAt,
            ExtractedData data) {}

    /** Immutable current-mode snapshot consumed by one GridItem CLICK/INPUT request. */
    public record GridItemDataset(
            int botJobId,
            int homeBankingId,
            String mode,
            long datasetEpoch,
            long datasetRevision,
            Integer selectedRowIndex,
            Instant loadedAt,
            ExtractedData data) {}

    static Integer clampSelectedRow(Integer selectedRowIndex, int rowCount) {
        if (rowCount <= 0) return null;
        if (selectedRowIndex == null) return 0;
        return Math.max(0, Math.min(selectedRowIndex, rowCount - 1));
    }

    static Integer selectedRowAfterDelete(
            Integer selectedRowIndex, int deletedRowIndex, int remainingRowCount) {
        if (remainingRowCount <= 0) return null;
        if (selectedRowIndex == null) return 0;
        int adjusted = selectedRowIndex > deletedRowIndex
                ? selectedRowIndex - 1 : selectedRowIndex;
        return clampSelectedRow(adjusted, remainingRowCount);
    }

    static Integer selectedRowAfterMove(
            Integer selectedRowIndex, int fromIndex, int toIndex, int rowCount) {
        Integer selected = clampSelectedRow(selectedRowIndex, rowCount);
        if (selected == null || fromIndex == toIndex) return selected;
        if (selected == fromIndex) return toIndex;
        if (fromIndex < toIndex && selected > fromIndex && selected <= toIndex) {
            return selected - 1;
        }
        if (fromIndex > toIndex && selected >= toIndex && selected < fromIndex) {
            return selected + 1;
        }
        return selected;
    }

    static boolean blocksLaunchForUnsavedRealData(String mode, boolean dirty) {
        return dirty && "REAL".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    private static String datasetContentRevision(Binding owner, ExtractedData data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digestPart(digest, Integer.toString(owner.botJobId));
            digestPart(digest, Integer.toString(owner.homeBankingId));
            digestPart(digest, owner.mode.name());
            int rowCount = data.getNumberOfDataRows();
            digestPart(digest, Integer.toString(rowCount));
            List<String> blocks = new ArrayList<>(data.getBlocks());
            blocks.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo));
            for (String block : blocks) {
                digestPart(digest, block);
                List<String> columns = new ArrayList<>(data.getExtractedFields(block));
                columns.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo));
                for (String column : columns) {
                    digestPart(digest, column);
                    for (int row = 0; row < rowCount; row++) {
                        digestPart(digest, Integer.toString(row));
                        digestPart(digest, data.getFieldValue(block, column, row));
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void digestPart(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static final class Binding {
        private final int botJobId;
        private final int homeBankingId;
        private final String botJobName;
        private final Path workbook;
        private ExtractedData realData;
        private ExtractedData syntheticData;
        private Instant loadedAt = Instant.now();
        private Mode mode = Mode.REAL;
        private boolean realDirty;
        private boolean syntheticDirty;
        private String syntheticContext;
        private Integer selectedRowIndex;
        private final long datasetEpoch = DATASET_EPOCHS.incrementAndGet();
        private long datasetRevision = 1L;

        private Binding(int botJobId, int homeBankingId, String botJobName, Path workbook,
                ExtractedData realData, ExtractedData syntheticData, String syntheticContext) {
            this.botJobId = botJobId;
            this.homeBankingId = homeBankingId;
            this.botJobName = botJobName;
            this.workbook = workbook;
            this.realData = realData;
            this.syntheticData = syntheticData;
            this.syntheticContext = syntheticContext == null || syntheticContext.isBlank()
                    ? "Bank Account" : syntheticContext;
            resetSelectedRow();
        }
        int botJobId() { return botJobId; }
        int homeBankingId() { return homeBankingId; }
        String botJobName() { return botJobName; }
        Path workbook() { return workbook; }
        ExtractedData data() { return mode == Mode.REAL ? realData : syntheticData; }
        Instant loadedAt() { return loadedAt; }
        boolean dirty() { return mode == Mode.REAL ? realDirty : syntheticDirty; }
        void setDirty(boolean dirty) { if (mode == Mode.REAL) realDirty = dirty; else syntheticDirty = dirty; }
        Integer selectedRowIndex() { return selectedRowIndex; }
        void resetSelectedRow() {
            selectedRowIndex = clampSelectedRow(null, data().getNumberOfDataRows());
        }
        void reconcileSelectedRow() {
            selectedRowIndex = clampSelectedRow(selectedRowIndex, data().getNumberOfDataRows());
        }
        void selectRow(int rowIndex) {
            selectedRowIndex = clampSelectedRow(rowIndex, data().getNumberOfDataRows());
        }
        void rowDeleted(int rowIndex) {
            selectedRowIndex = selectedRowAfterDelete(
                    selectedRowIndex, rowIndex, data().getNumberOfDataRows());
        }
        void rowMoved(int fromIndex, int toIndex) {
            selectedRowIndex = selectedRowAfterMove(
                    selectedRowIndex, fromIndex, toIndex, data().getNumberOfDataRows());
        }
        void changed() { datasetRevision++; }
    }
}
