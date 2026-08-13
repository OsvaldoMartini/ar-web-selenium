package com.allinweb.ch.facade.execution;

import com.allinweb.ch.facade.ExcelSyntheticDatasetStore;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.readersAndWriters.ExcelReader;
import com.allinweb.ch.socket.ExcelDataWorkspaceService.IntegrationDataset;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ExtractedData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Loads one owner-scoped execution dataset without mutating the singleton Excel Data workspace. */
public final class MultiExecutionDatasetLoader {
    private static final AtomicLong EPOCHS = new AtomicLong();
    private final ExcelSyntheticDatasetStore synthetic = new ExcelSyntheticDatasetStore();

    public PreparedDataset load(Plan plan, String requestedMode) throws Exception {
        String mode = requestedMode == null ? "" : requestedMode.trim().toUpperCase(Locale.ROOT);
        if (!"REAL".equals(mode) && !"SYNTHETIC".equals(mode)) {
            throw new IllegalArgumentException("Excel mode must be REAL or SYNTHETIC.");
        }
        ExtractedData data = "SYNTHETIC".equals(mode)
                ? synthetic.load(plan.owner().homeBankingId(), plan.owner().botJobId())
                : loadReal(plan);
        if (data == null) {
            throw new IllegalStateException(mode + " execution data is not available for this Bot Job.");
        }
        if (data.getErrorMessage() != null && !data.getErrorMessage().isBlank()) {
            throw new IllegalStateException(data.getErrorMessage());
        }
        ExtractedData frozen = data.deepCopy();
        long epoch = EPOCHS.incrementAndGet();
        String revision = revision(plan, mode, frozen);
        IntegrationDataset integration = new IntegrationDataset(
                plan.owner().botJobId(),
                plan.owner().homeBankingId(),
                mode,
                epoch,
                1L,
                revision,
                Instant.now(),
                frozen);
        return new PreparedDataset(integration, json(integration));
    }

    private static ExtractedData loadReal(Plan plan) throws Exception {
        Path workbook = Path.of(ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_EXCEL))
                .resolve(plan.environment().botJobName().trim() + ARConstants.FILE_FORMAT_EXCEL)
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(workbook)) {
            throw new IllegalStateException("REAL Excel data file is not available for this Bot Job.");
        }
        List<String> actions = plan.instructions().stream().map(InstructionSnapshot::action).toList();
        Map<String, String> aliases = new LinkedHashMap<>();
        for (InstructionSnapshot instruction : plan.instructions()) {
            if (instruction.clientNamed() != null && !instruction.clientNamed().isBlank()) {
                aliases.put(instruction.name(), instruction.clientNamed());
            }
        }
        return new ExcelReader().extractData(workbook.toString(), actions, aliases);
    }

    private static JsonObject json(IntegrationDataset dataset) {
        JsonObject result = new JsonObject();
        result.addProperty("mode", dataset.mode());
        result.addProperty("datasetEpoch", dataset.datasetEpoch());
        result.addProperty("datasetRevision", dataset.datasetRevision());
        result.addProperty("contentRevision", dataset.contentRevision());
        result.addProperty("rowCount", dataset.data().getNumberOfDataRows());
        JsonArray blocks = new JsonArray();
        for (String blockName : dataset.data().getBlocks()) {
            JsonObject block = new JsonObject();
            block.addProperty("name", blockName);
            JsonArray columns = new JsonArray();
            dataset.data().getExtractedFields(blockName).forEach(columns::add);
            block.add("columns", columns);
            JsonArray rows = new JsonArray();
            for (int rowIndex = 0; rowIndex < dataset.data().getNumberOfDataRows(); rowIndex++) {
                JsonObject row = new JsonObject();
                row.addProperty("index", rowIndex);
                JsonObject values = new JsonObject();
                for (Map.Entry<String, String> value
                        : dataset.data().getRowFieldValues(blockName, rowIndex).entrySet()) {
                    if (value.getValue() == null) values.add(value.getKey(), null);
                    else values.addProperty(value.getKey(), value.getValue());
                }
                row.add("values", values);
                rows.add(row);
            }
            block.add("rows", rows);
            blocks.add(block);
        }
        result.add("blocks", blocks);
        return result;
    }

    private static String revision(Plan plan, String mode, ExtractedData data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            part(digest, Integer.toString(plan.owner().homeBankingId()));
            part(digest, Integer.toString(plan.owner().botJobId()));
            part(digest, mode);
            List<String> blocks = new ArrayList<>(data.getBlocks());
            blocks.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo));
            for (String block : blocks) {
                part(digest, block);
                List<String> columns = new ArrayList<>(data.getExtractedFields(block));
                columns.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo));
                for (String column : columns) {
                    part(digest, column);
                    for (int row = 0; row < data.getNumberOfDataRows(); row++) {
                        part(digest, Integer.toString(row));
                        part(digest, data.getFieldValue(block, column, row));
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void part(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    public record PreparedDataset(IntegrationDataset integration, JsonObject clientSnapshot) {}
}
