package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pane-free block export configuration boundary shared by bot-job and component grids. */
public final class ExcelExportService {
    private static final ExcelExportService INSTANCE = new ExcelExportService();
    private static final PerformDataBase database = PerformDataBase.getInstance();
    private static final PerformLists lists = PerformLists.getInstance();

    private ExcelExportService() {}

    public static ExcelExportService getInstance() {
        return INSTANCE;
    }

    public Map<String, Object> bootstrap(JsonObject body) {
        Map<String, Object> response = context(body);
        if (Boolean.FALSE.equals(response.get("ok"))) return response;
        response.putAll(parse(str(body, "exportFile")));
        response.put("fileTypes", List.of(".xlsx", ".csv"));
        response.put("delimiters", List.of(",", "|"));
        response.put("message", "Excel export configuration loaded");
        return response;
    }

    public Map<String, Object> save(JsonObject body) {
        Map<String, Object> context = context(body);
        if (Boolean.FALSE.equals(context.get("ok"))) return context;
        boolean clear = bool(body, "clear");
        String delimiter = str(body, "delimiter");
        if (!List.of(",", "|").contains(delimiter)) return failure("Select a valid delimiter.");

        String encoded = ":" + delimiter;
        if (!clear) {
            String directory = str(body, "directory").trim();
            String filename = str(body, "filename").trim();
            String fileType = str(body, "fileType").toLowerCase();
            if (directory.isEmpty()) return failure("Export directory is required.");
            if (!List.of(".xlsx", ".csv").contains(fileType)) return failure("Select a valid file type.");
            if (filename.isEmpty() || filename.length() > 150 || !filename.matches("[A-Za-z0-9 _@.-]+")) {
                return failure("Enter a valid export filename.");
            }
            int extension = filename.lastIndexOf('.');
            if (extension > 0) filename = filename.substring(0, extension);
            try {
                Path path = Paths.get(directory).toAbsolutePath().normalize().resolve(filename + fileType).normalize();
                if (!path.startsWith(Paths.get(directory).toAbsolutePath().normalize())) {
                    return failure("Export file must remain inside the selected directory.");
                }
                encoded = path.toString().replace('\\', '/') + ":" + delimiter;
            } catch (Exception error) {
                return failure("Enter a valid export directory.");
            }
        }

        String sessionId = str(body, "sessionId");
        boolean component = isComponentSession(sessionId);
        String table = component ? "component_block" : "block";
        int ownerId = component ? integer(body, "homeBankingId") : integer(body, "botJobId");
        int blockId = integer(body, "blockId");
        ErrorMessage error = database.updateBlockExportFile(table, ownerId, blockId, encoded);
        if (error != null) return failure("Excel export configuration could not be saved.");

        lists.updateMemoryBlockExcelExport(table, ownerId, blockId, encoded);
        List<BotJobLoadDTO> source = component ? lists.getListBotJobComp() : lists.getListBotJob();
        List<InstructionLoad> instructions = source.isEmpty() ? List.of() : lists.buildJsonViewData(source);
        Map<String, Object> response = context(body);
        response.put("exportFile", encoded);
        response.put("instructions", instructions);
        response.put("updateOperation", component
                ? ScannerWorkspaceOperations.COMPONENTS_UPDATE
                : ScannerWorkspaceOperations.UPDATE_INSTRUCTIONS);
        response.put("message", clear ? "Excel export configuration cleared" : "Excel export configuration saved");
        return response;
    }

    static Map<String, Object> parse(String encoded) {
        String value = encoded == null ? "" : encoded.trim().replace('\\', '/');
        String delimiter = ",";
        if (value.endsWith(":,") || value.endsWith(":|")) {
            delimiter = value.substring(value.length() - 1);
            value = value.substring(0, value.length() - 2);
        }
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("exportFile", encoded == null ? "" : encoded);
        parsed.put("delimiter", delimiter);
        parsed.put("directory", "");
        parsed.put("filename", "");
        parsed.put("fileType", ".xlsx");
        if (!value.isBlank() && !value.contains("No Excel Export File")) {
            try {
                Path path = Paths.get(value);
                Path file = path.getFileName();
                String filename = file == null ? "" : file.toString();
                String type = filename.toLowerCase().endsWith(".csv") ? ".csv" : ".xlsx";
                parsed.put("directory", path.getParent() == null ? "" : path.getParent().toString().replace('\\', '/'));
                parsed.put("filename", filename);
                parsed.put("fileType", type);
            } catch (Exception ignored) {
                parsed.put("warning", "Historical export path could not be parsed.");
            }
        }
        return parsed;
    }

    private Map<String, Object> context(JsonObject body) {
        int blockId = integer(body, "blockId");
        int botJobId = integer(body, "botJobId");
        int homeBankingId = integer(body, "homeBankingId");
        String sessionId = str(body, "sessionId");
        if (blockId <= 0 || sessionId.isBlank()) return failure("Excel export block context is invalid.");
        if (isComponentSession(sessionId) ? homeBankingId <= 0 : botJobId <= 0) {
            return failure("Excel export owner context is invalid.");
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("blockId", blockId);
        response.put("blockName", str(body, "blockName"));
        response.put("blockOrderNumber", integer(body, "blockOrderNumber"));
        response.put("botJobId", botJobId);
        response.put("homeBankingId", homeBankingId);
        response.put("sessionId", sessionId);
        response.put("requestId", str(body, "requestId"));
        return response;
    }

    private Map<String, Object> failure(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", false);
        response.put("error", message);
        return response;
    }

    private String str(JsonObject body, String key) {
        return body != null && body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : "";
    }

    private int integer(JsonObject body, String key) {
        try { return body != null && body.has(key) ? body.get(key).getAsInt() : -1; }
        catch (Exception ignored) { return -1; }
    }

    private boolean bool(JsonObject body, String key) {
        return body != null && body.has(key) && body.get(key).getAsBoolean();
    }

    private boolean isComponentSession(String sessionId) {
        return sessionId != null && sessionId.contains(ScannerWorkspaceSessions.COMPONENT_TASKS);
    }
}
