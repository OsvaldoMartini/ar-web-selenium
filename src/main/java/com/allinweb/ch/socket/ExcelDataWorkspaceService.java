package com.allinweb.ch.socket;

import com.allinweb.ch.facade.PerformDBEngine;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformLists;
import com.allinweb.ch.facade.BotJobNativeOperationService;
import com.allinweb.ch.facade.BotJobToolbarConcurrencyGuard;
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
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import javax.websocket.Session;
import lombok.extern.slf4j.Slf4j;

/** Owns the fixed detached Excel Data page and its retained execution dataset. */
@Slf4j
public final class ExcelDataWorkspaceService {
    public static final String SESSION_ID = DetachedWorkspaceSessions.EXCEL_DATA_MANAGER;
    private static final ExcelDataWorkspaceService INSTANCE = new ExcelDataWorkspaceService();

    private final PerformLists lists = PerformLists.getInstance();
    private final PerformDataBase database = PerformDataBase.getInstance();
    private final PerformDBEngine engine = PerformDBEngine.getInstance();
    private final ScannerPreLaunchExcelLoader loader = new ScannerPreLaunchExcelLoader();
    private final BotJobNativeOperationService nativeOperations =
            BotJobNativeOperationService.createDefault(
                    ARPropertyManager.getInstance(), new BotJobToolbarConcurrencyGuard());
    private Binding binding;

    private ExcelDataWorkspaceService() {}

    public static ExcelDataWorkspaceService getInstance() {
        return INSTANCE;
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
            binding = new Binding(job.getId(), job.getHomeBankingId(), job.getName(), workbook, data, Instant.now(), false);
            boolean opened = PagesOpenWorkspaceService.getInstance()
                    .openOrFocusDetachedWorkspace(SESSION_ID, botJobId, "Excel Data workspace requested.");
            return opened ? snapshot("Excel Data workspace opened.") : failure("Excel Data workspace could not be opened.");
        } catch (Exception error) {
            log.error("Unable to open Excel Data workspace for Bot Job {}", botJobId, error);
            return failure(error.getMessage());
        }
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

    public synchronized JsonObject close(JsonObject request, String sessionId, Session transport) {
        if (!SESSION_ID.equals(sessionId)
                || transport == null
                || WebSocketSessionManager.getSession(SESSION_ID) != transport) {
            return failure("Only the active Excel Data workspace can close this dataset.");
        }
        boolean released = binding != null && loader.close(binding.workbook().toString());
        binding = null;
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
                ExtractedData syntheticRows = syntheticData(binding.data(), 3);
                loader.replaceInMemory(binding.workbook().toString(), syntheticRows);
                binding = new Binding(
                        binding.botJobId(),
                        binding.homeBankingId(),
                        binding.botJobName(),
                        binding.workbook(),
                        syntheticRows,
                        Instant.now(),
                        true);
                return snapshot("Synthetic rows generated in memory. Save to Excel to update the workbook.");
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
            binding = new Binding(
                    binding.botJobId(),
                    binding.homeBankingId(),
                    binding.botJobName(),
                    binding.workbook(),
                    reloaded,
                    Instant.now(),
                    false);
            nativeOperations.openFile(generated);
            return snapshot("Excel data file generated and loaded into memory.");
        } catch (Exception error) {
            log.error(
                    "Unable to generate {} Excel data for Bot Job {}",
                    synthetic ? "synthetic" : "standard",
                    binding.botJobId(),
                    error);
            return failure(error.getMessage());
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
        binding = new Binding(
                binding.botJobId(), binding.homeBankingId(), binding.botJobName(), binding.workbook(),
                binding.data(), Instant.now(), true);
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
            File saved = new ExcelUtils()
                    .generateExcelFiles(binding.data(), binding.botJobName().trim(), null, false);
            if (!saved.toPath().toAbsolutePath().normalize().equals(binding.workbook())) {
                return failure("Excel save produced an unexpected file.");
            }
            binding = new Binding(
                    binding.botJobId(), binding.homeBankingId(), binding.botJobName(), binding.workbook(),
                    binding.data(), Instant.now(), false);
            return snapshot("In-memory Excel dataset saved atomically to the original workbook.");
        } catch (Exception error) {
            log.error("Unable to save in-memory Excel data for Bot Job {}", binding.botJobId(), error);
            return failure(error.getMessage());
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

    private ExtractedData syntheticData(ExtractedData template, int rowCount) {
        ExtractedData synthetic = new ExtractedData();
        for (String blockName : template.getBlocks()) {
            for (String column : template.getExtractedFields(blockName)) {
                for (int row = 0; row < rowCount; row++) {
                    synthetic.addFieldValue(
                            blockName,
                            column,
                            "SYNTHETIC_" + normalizeSyntheticName(column) + "_" + (row + 1),
                            row);
                }
            }
        }
        return synthetic;
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
        response.addProperty("rowCount", data.getNumberOfDataRows());
        response.addProperty("dirty", binding.dirty());

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

    private record Binding(
            int botJobId,
            int homeBankingId,
            String botJobName,
            Path workbook,
            ExtractedData data,
            Instant loadedAt,
            boolean dirty) {}
}
