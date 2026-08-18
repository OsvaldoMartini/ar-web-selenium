package com.allinweb.ch.facade;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Instruction-owned ExcelWrite file workspace for the detached React Command Editor.
 *
 * <p>This service never persists command configuration. It loads reusable targets, opens the
 * native directory chooser, and validates one target. The existing atomic Command Editor mutation
 * remains the only write boundary for {@code instruction_variable_command_config}.
 */
@Slf4j
public final class ExcelWriteWorkspaceService {
    private static final ExcelWriteWorkspaceService INSTANCE = new ExcelWriteWorkspaceService();
    private static final PerformDataBase DATABASE = PerformDataBase.getInstance();

    private ExcelWriteWorkspaceService() {}

    public static ExcelWriteWorkspaceService getInstance() {
        return INSTANCE;
    }

    public JsonObject bootstrap(JsonObject body) {
        JsonObject response = response(body);
        try {
            int homeBankingId = positive(body, "homeBankingId");
            int botJobId = positive(body, "botJobId");
            int instructionId = integer(body, "instructionId", 0);
            LinkedHashMap<String, TargetUsage> targets = new LinkedHashMap<>();
            try (Connection connection = DATABASE.getConnection()) {
                loadTypedTargets(connection, homeBankingId, botJobId, targets);
                loadLegacyTargets(connection, botJobId, targets);
                String currentEncoded = string(body, "outputFile").trim();
                String suggestedOutputKey = string(body, "outputKey").trim();
                String suggestedOutputColumn = string(body, "outputColumn").trim();
                boolean legacyCurrent = false;
                if (instructionId > 0) {
                    InstructionSuggestion suggestion = loadInstructionSuggestion(
                            connection, homeBankingId, botJobId, instructionId);
                    if (suggestion != null) {
                        if (currentEncoded.isEmpty()) currentEncoded = suggestion.outputFile();
                        if (suggestedOutputKey.isEmpty()) {
                            suggestedOutputKey = suggestion.outputKey();
                        }
                        if (suggestedOutputColumn.isEmpty()) {
                            suggestedOutputColumn = suggestion.outputColumn();
                        }
                        legacyCurrent = suggestion.legacy();
                    }
                }
                if (!currentEncoded.isEmpty()) {
                    TargetUsage current = targets.getOrDefault(
                            normalizedTargetKey(currentEncoded),
                            new TargetUsage(currentEncoded, 0, legacyCurrent));
                    response.add("current", targetJson(current));
                }
                response.addProperty("suggestedOutputKey", suggestedOutputKey);
                response.addProperty("suggestedOutputColumn", suggestedOutputColumn);
            }
            JsonArray result = new JsonArray();
            targets.values().forEach(target -> result.add(targetJson(target)));
            response.add("targets", result);
            response.addProperty("ok", true);
            response.addProperty(
                    "message",
                    targets.isEmpty()
                            ? "No instruction-owned ExcelWrite files are configured for this Bot Job."
                            : "ExcelWrite files loaded for the active Bot Job.");
        } catch (Exception error) {
            log.error(
                    "ExcelWrite target bootstrap failed homeBankingId={} botJobId={} instructionId={}",
                    integer(body, "homeBankingId", -1),
                    integer(body, "botJobId", -1),
                    integer(body, "instructionId", 0),
                    error);
            return fail(response, "ExcelWrite file configurations could not be loaded.");
        }
        return response;
    }

    public JsonObject chooseDirectory(JsonObject body) {
        JsonObject response = response(body);
        try {
            positive(body, "homeBankingId");
            positive(body, "botJobId");
            String requested = string(body, "directory").trim();
            File initial = null;
            if (!requested.isEmpty()) {
                try {
                    File candidate = Paths.get(requested).toAbsolutePath().normalize().toFile();
                    if (candidate.isDirectory()) initial = candidate;
                } catch (RuntimeException ignored) {
                    // The native chooser can still open at its operating-system default.
                }
            }
            File selected = NativePathChooser.chooseDirectory(initial);
            if (selected == null) {
                response.addProperty("ok", true);
                response.addProperty("cancelled", true);
                response.addProperty("directory", requested);
                response.addProperty("message", "ExcelWrite directory selection cancelled.");
                return response;
            }
            Path selectedPath = selected.toPath().toRealPath();
            if (!Files.isDirectory(selectedPath) || !Files.isWritable(selectedPath)) {
                return fail(response, "Select a writable ExcelWrite directory.");
            }
            response.addProperty("ok", true);
            response.addProperty("cancelled", false);
            response.addProperty("directory", selectedPath.toString());
            response.addProperty("message", "ExcelWrite directory selected.");
            return response;
        } catch (Exception error) {
            log.warn(
                    "ExcelWrite directory chooser failed botJobId={} instructionId={}: {}",
                    integer(body, "botJobId", -1),
                    integer(body, "instructionId", 0),
                    error.getClass().getSimpleName());
            return fail(response, "Unable to open the ExcelWrite directory selector.");
        }
    }

    public JsonObject validateTarget(JsonObject body) {
        JsonObject response = response(body);
        try {
            positive(body, "homeBankingId");
            positive(body, "botJobId");
            String directory = string(body, "directory").trim();
            String filename = string(body, "filename").trim();
            String fileType = string(body, "fileType").trim().toLowerCase(Locale.ROOT);
            String delimiter = string(body, "delimiter");
            if (directory.isEmpty()) return fail(response, "ExcelWrite directory is required.");
            if (!".xlsx".equals(fileType) && !".csv".equals(fileType)) {
                return fail(response, "Select .xlsx or .csv for ExcelWrite.");
            }
            if (!",".equals(delimiter) && !"|".equals(delimiter)) {
                return fail(response, "Select a valid ExcelWrite delimiter.");
            }
            if (filename.isEmpty()
                    || filename.length() > 150
                    || !filename.matches("[A-Za-z0-9 _@.-]+")) {
                return fail(response, "Enter a valid ExcelWrite filename.");
            }
            int extension = filename.lastIndexOf('.');
            if (extension > 0) filename = filename.substring(0, extension);
            if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)) {
                return fail(response, "Enter a valid ExcelWrite filename.");
            }
            String encoded = ExcelExportService.buildEncodedTarget(
                    directory, filename, fileType, delimiter);
            response.addProperty("ok", true);
            response.addProperty("outputFile", encoded);
            response.add("target", targetJson(new TargetUsage(encoded, 0, false)));
            response.addProperty(
                    "message",
                    "ExcelWrite target validated. Use UPDATE to persist this instruction configuration.");
            return response;
        } catch (IllegalStateException invalidDirectory) {
            return fail(response, invalidDirectory.getMessage());
        } catch (Exception error) {
            log.warn(
                    "ExcelWrite target validation failed botJobId={} instructionId={}: {}",
                    integer(body, "botJobId", -1),
                    integer(body, "instructionId", 0),
                    error.getClass().getSimpleName());
            return fail(response, "Enter a valid ExcelWrite file destination.");
        }
    }

    private static void loadTypedTargets(
            Connection connection,
            int homeBankingId,
            int botJobId,
            Map<String, TargetUsage> targets) throws Exception {
        String sql = "SELECT c.output_file,COUNT(*) AS usage_count"
                + " FROM instruction_variable_command_config c"
                + " JOIN instruction i ON i.id=c.instruction_id AND i.bot_job_id=c.bot_job_id"
                + " WHERE c.home_banking_id=? AND c.bot_job_id=?"
                + " AND UPPER(TRIM(i.actions)) IN ('E','EXCELWRITE')"
                + " AND c.output_file IS NOT NULL AND TRIM(c.output_file)<>''"
                + " GROUP BY c.output_file ORDER BY c.output_file";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String encoded = rows.getString("output_file");
                    TargetUsage usage = new TargetUsage(encoded, rows.getInt("usage_count"), false);
                    targets.put(normalizedTargetKey(encoded), usage);
                }
            }
        }
    }

    private static void loadLegacyTargets(
            Connection connection, int botJobId, Map<String, TargetUsage> targets) throws Exception {
        String sql = "SELECT b.export_file,COUNT(*) AS usage_count FROM block b"
                + " JOIN instruction i ON i.block_id=b.id AND i.bot_job_id=b.bot_job_id"
                + " WHERE b.bot_job_id=? AND UPPER(TRIM(i.actions)) IN ('E','EXCELWRITE')"
                + " AND b.export_file IS NOT NULL AND TRIM(b.export_file)<>''"
                + " AND b.export_file<>'No Excel Export File'"
                + " GROUP BY b.export_file ORDER BY b.export_file";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String encoded = rows.getString("export_file");
                    String key = normalizedTargetKey(encoded);
                    targets.putIfAbsent(
                            key,
                            new TargetUsage(encoded, rows.getInt("usage_count"), true));
                }
            }
        }
    }

    private static InstructionSuggestion loadInstructionSuggestion(
            Connection connection, int homeBankingId, int botJobId, int instructionId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT c.instruction_id AS typed_id,c.output_file,c.output_key,c.output_column,"
                        + "b.export_file,p.name AS parent_name,i.operation"
                        + " FROM instruction i"
                        + " JOIN bot_job bot ON bot.id=i.bot_job_id AND bot.home_banking_id=?"
                        + " JOIN block b ON b.id=i.block_id AND b.bot_job_id=i.bot_job_id"
                        + " LEFT JOIN instruction p ON p.id=i.parent_id AND p.bot_job_id=i.bot_job_id"
                        + " LEFT JOIN instruction_variable_command_config c"
                        + " ON c.home_banking_id=bot.home_banking_id"
                        + " AND c.bot_job_id=i.bot_job_id AND c.instruction_id=i.id"
                        + " WHERE i.bot_job_id=? AND i.id=?"
                        + " AND UPPER(TRIM(i.actions)) IN ('E','EXCELWRITE')")) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            statement.setInt(3, instructionId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                boolean typed = rows.getObject("typed_id") != null;
                String outputFile = typed
                        ? safe(rows.getString("output_file"))
                        : normalizeLegacyFile(rows.getString("export_file"));
                String outputKey = typed
                        ? safe(rows.getString("output_key"))
                        : legacyOutputKey(rows.getString("operation"));
                String outputColumn = typed
                        ? safe(rows.getString("output_column"))
                        : safe(rows.getString("parent_name"));
                return new InstructionSuggestion(
                        outputFile, outputKey, outputColumn, !typed);
            }
        }
    }

    private static String normalizeLegacyFile(String value) {
        String normalized = safe(value).trim();
        return "No Excel Export File".equals(normalized) ? "" : normalized;
    }

    private static String legacyOutputKey(String operation) {
        String[] parts = safe(operation).split(":");
        String value = parts.length == 0 ? "" : parts[parts.length - 1].trim();
        if (value.startsWith("$") || value.startsWith("#")) value = value.substring(1).trim();
        return value.isEmpty() ? "ExcelWrite" : value;
    }

    private static JsonObject targetJson(TargetUsage usage) {
        JsonObject target = new JsonObject();
        target.addProperty("outputFile", usage.encoded());
        target.addProperty("usageCount", usage.usageCount());
        target.addProperty("legacy", usage.legacy());
        try {
            ExcelExportTarget decoded = ExcelExportTarget.decode(usage.encoded()).orElseThrow();
            Path path = decoded.path();
            target.addProperty("directory", path.getParent() == null ? "" : path.getParent().toString());
            target.addProperty("filename", path.getFileName() == null ? "" : path.getFileName().toString());
            target.addProperty("fileType", decoded.fileType());
            target.addProperty("delimiter", decoded.delimiter());
        } catch (RuntimeException invalid) {
            target.addProperty("directory", "");
            target.addProperty("filename", usage.encoded());
            target.addProperty("fileType", ".xlsx");
            target.addProperty("delimiter", ",");
            target.addProperty("invalid", true);
        }
        return target;
    }

    private static String normalizedTargetKey(String encoded) {
        try {
            ExcelExportTarget target = ExcelExportTarget.decode(encoded).orElseThrow();
            return target.path().toString().toLowerCase(Locale.ROOT) + ":" + target.delimiter();
        } catch (RuntimeException invalid) {
            return safe(encoded).toLowerCase(Locale.ROOT);
        }
    }

    private static JsonObject response(JsonObject body) {
        JsonObject response = new JsonObject();
        response.addProperty("requestId", string(body, "requestId"));
        response.addProperty("bindingEpoch", string(body, "bindingEpoch"));
        response.addProperty("homeBankingId", integer(body, "homeBankingId", -1));
        response.addProperty("botJobId", integer(body, "botJobId", -1));
        response.addProperty("instructionId", integer(body, "instructionId", 0));
        return response;
    }

    private static JsonObject fail(JsonObject response, String message) {
        response.addProperty("ok", false);
        response.addProperty("error", message == null || message.isBlank()
                ? "ExcelWrite file operation failed."
                : message);
        return response;
    }

    private static int positive(JsonObject body, String field) {
        int value = integer(body, field, -1);
        if (value <= 0) throw new IllegalArgumentException(field + " must be positive.");
        return value;
    }

    private static int integer(JsonObject body, String field, int fallback) {
        try {
            return body != null && body.has(field) && !body.get(field).isJsonNull()
                    ? body.get(field).getAsInt()
                    : fallback;
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }

    private static String string(JsonObject body, String field) {
        try {
            return body != null && body.has(field) && !body.get(field).isJsonNull()
                    ? safe(body.get(field).getAsString())
                    : "";
        } catch (RuntimeException invalid) {
            return "";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record TargetUsage(String encoded, int usageCount, boolean legacy) {}

    private record InstructionSuggestion(
            String outputFile,
            String outputKey,
            String outputColumn,
            boolean legacy) {}
}
