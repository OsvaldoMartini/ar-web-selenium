package com.allinweb.ch.facade;

import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ScannerWorkspaceOperations;
import com.allinweb.ch.model.ScannerWorkspaceSessions;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.file.Files;
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
    private static final ARPropertyManager properties = ARPropertyManager.getInstance();

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

    /** Opens the native directory picker and returns a real path React can submit to save(). */
    public Map<String, Object> chooseDirectory(JsonObject body) {
        return chooseDirectory(body, NativePathChooser::chooseDirectory);
    }

    Map<String, Object> chooseDirectory(JsonObject body, DirectoryChooser chooser) {
        Map<String, Object> response = context(body);
        if (Boolean.FALSE.equals(response.get("ok"))) return response;

        String requested = str(body, "directory").trim();
        if (requested.isEmpty()) {
            Object parsedDirectory = parse(str(body, "exportFile")).get("directory");
            requested = parsedDirectory == null ? "" : parsedDirectory.toString().trim();
        }
        if (requested.isEmpty()) requested = configuredExportDirectory();

        File initialDirectory = null;
        try {
            if (!requested.isEmpty()) {
                File requestedFile = Paths.get(requested).toAbsolutePath().normalize().toFile();
                if (requestedFile.isDirectory()) initialDirectory = requestedFile;
            }
        } catch (RuntimeException ignored) {
            // The chooser can still open at the operating system's default location.
        }

        final File selected;
        try {
            selected = chooser.choose(initialDirectory);
        } catch (RuntimeException chooserFailure) {
            return fail(response, "Unable to open the Excel export folder selector.");
        }
        if (selected == null) {
            response.put("cancelled", true);
            response.put("directory", requested);
            response.put("message", "Excel export folder selection cancelled");
            return response;
        }
        try {
            Path selectedPath = selected.toPath().toRealPath();
            if (!Files.isDirectory(selectedPath) || !Files.isWritable(selectedPath)) {
                return fail(response, "Select a writable export directory.");
            }
            response.put("cancelled", false);
            response.put("directory", selectedPath.toString());
            response.put("message", "Excel export folder selected");
            return response;
        } catch (Exception error) {
            return fail(response, "Select a valid export directory.");
        }
    }

    public Map<String, Object> save(JsonObject body) {
        Map<String, Object> context = context(body);
        if (Boolean.FALSE.equals(context.get("ok"))) return context;
        boolean clear = bool(body, "clear");
        String delimiter = str(body, "delimiter");
        if (!List.of(",", "|").contains(delimiter)) return fail(context, "Select a valid delimiter.");

        String encoded = ":" + delimiter;
        if (!clear) {
            String directory = str(body, "directory").trim();
            String filename = str(body, "filename").trim();
            String fileType = str(body, "fileType").toLowerCase();
            if (directory.isEmpty()) return fail(context, "Export directory is required.");
            if (!List.of(".xlsx", ".csv").contains(fileType)) {
                return fail(context, "Select a valid file type.");
            }
            if (filename.isEmpty() || filename.length() > 150 || !filename.matches("[A-Za-z0-9 _@.-]+")) {
                return fail(context, "Enter a valid export filename.");
            }
            int extension = filename.lastIndexOf('.');
            if (extension > 0) filename = filename.substring(0, extension);
            if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)) {
                return fail(context, "Enter a valid export filename.");
            }
            try {
                encoded = buildEncodedTarget(directory, filename, fileType, delimiter);
            } catch (IllegalStateException invalidDirectory) {
                return fail(context, invalidDirectory.getMessage());
            } catch (Exception error) {
                return fail(context, "Enter a valid export directory.");
            }
        }

        String sessionId = str(body, "sessionId");
        boolean component = isComponentSession(sessionId);
        String table = component ? "component_block" : "block";
        int ownerId = component ? integer(body, "homeBankingId") : integer(body, "botJobId");
        int blockId = integer(body, "blockId");
        ErrorMessage error = database.updateBlockExportFile(table, ownerId, blockId, encoded);
        if (error != null) return fail(context, "Excel export configuration could not be saved.");

        lists.updateMemoryBlockExcelExport(table, ownerId, blockId, encoded);
        List<BotJobLoadDTO> source = component ? lists.getListBotJobComp() : lists.getListBotJob();
        List<InstructionLoad> instructions = source.isEmpty() ? List.of() : lists.buildJsonViewData(source);
        Map<String, Object> response = context;
        response.put("exportFile", encoded);
        response.put("instructions", instructions);
        response.put("updateOperation", component
                ? ScannerWorkspaceOperations.COMPONENTS_UPDATE
                : ScannerWorkspaceOperations.UPDATE_INSTRUCTIONS);
        response.put("message", clear ? "Excel export configuration cleared" : "Excel export configuration saved");
        return response;
    }

    static Map<String, Object> parse(String encoded) {
        String displayValue = encoded == null ? "" : encoded.trim().replace('\\', '/');
        if (displayValue.endsWith(":,") || displayValue.endsWith(":|")) {
            displayValue = displayValue.substring(0, displayValue.length() - 2);
        }
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("exportFile", encoded == null ? "" : encoded);
        parsed.put("delimiter", ",");
        parsed.put("directory", "");
        parsed.put("filename", "");
        parsed.put("fileType", ".xlsx");
        try {
            Path displayPath = displayValue.isBlank() ? null : Paths.get(displayValue);
            ExcelExportTarget.decode(encoded).ifPresent(target -> {
                Path path = displayPath == null ? target.path() : displayPath;
                Path file = path.getFileName();
                parsed.put("delimiter", target.delimiter());
                parsed.put("directory", path.getParent() == null
                        ? ""
                        : path.getParent().toString().replace('\\', '/'));
                parsed.put("filename", file == null ? "" : file.toString());
                parsed.put("fileType", target.fileType());
            });
        } catch (RuntimeException ignored) {
            parsed.put("warning", "Historical export path could not be parsed.");
        }
        return parsed;
    }

    static String buildEncodedTarget(String directory, String filename, String fileType, String delimiter)
            throws java.io.IOException {
        Path selectedDirectory = Paths.get(directory).toAbsolutePath().normalize();
        if (!Files.isDirectory(selectedDirectory) || !Files.isWritable(selectedDirectory)) {
            throw new IllegalStateException("Select a writable export directory.");
        }
        selectedDirectory = selectedDirectory.toRealPath();
        Path path = selectedDirectory.resolve(filename + fileType).normalize();
        if (!path.startsWith(selectedDirectory)) {
            throw new IllegalArgumentException("Export file must remain inside the selected directory.");
        }
        return ExcelExportTarget.encode(path, delimiter);
    }

    private String configuredExportDirectory() {
        String configured = properties.getProperty(ARPropertyEnum.PATH_EXPORT);
        if (configured == null || configured.isBlank()) return "";
        try {
            return Paths.get(configured.trim()).toAbsolutePath().normalize().toString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private Map<String, Object> context(JsonObject body) {
        int blockId = integer(body, "blockId");
        int botJobId = integer(body, "botJobId");
        int homeBankingId = integer(body, "homeBankingId");
        String sessionId = str(body, "sessionId");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("blockId", blockId);
        response.put("blockName", str(body, "blockName"));
        response.put("blockOrderNumber", integer(body, "blockOrderNumber"));
        response.put("botJobId", botJobId);
        response.put("homeBankingId", homeBankingId);
        response.put("sessionId", sessionId);
        response.put("requestId", str(body, "requestId"));
        if (blockId <= 0 || sessionId.isBlank()) {
            return fail(response, "Excel export block context is invalid.");
        }
        if (isComponentSession(sessionId) ? homeBankingId <= 0 : botJobId <= 0) {
            return fail(response, "Excel export owner context is invalid.");
        }
        return response;
    }

    private Map<String, Object> fail(Map<String, Object> response, String message) {
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

    @FunctionalInterface
    interface DirectoryChooser {
        File choose(File initialDirectory);
    }
}
