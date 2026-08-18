package com.allinweb.ch.facade.execution;

import com.allinweb.ch.db.InstructionVariableCommandConfigRepository;
import com.allinweb.ch.db.InstructionVariableCommandConfigRepository.StoredConfiguration;
import com.allinweb.ch.facade.ExcelExportTarget;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.model.BlockLoadDTO;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.InstructionLoad;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, execution-owned ExcelWrite destination plan.
 *
 * <p>The typed instruction configuration is authoritative whenever it exists, including an
 * intentionally cleared file. The historical Block file and parent-name column are consulted only
 * for instructions that have not been migrated yet. Runtime variables remain independent and are
 * resolved by the instruction's READ slot already loaded into {@link InstructionLoad}.
 */
public final class ExcelWriteExecutionPlan {
    private static final PerformDataBase DATABASE = PerformDataBase.getInstance();
    private static final InstructionVariableCommandConfigRepository CONFIGURATIONS =
            new InstructionVariableCommandConfigRepository();

    private final Map<Integer, Target> targets;

    private ExcelWriteExecutionPlan(Map<Integer, Target> targets) {
        this.targets = Collections.unmodifiableMap(new LinkedHashMap<>(targets));
    }

    public static ExcelWriteExecutionPlan load(BotJobLoadDTO botJob) throws SQLException {
        if (botJob == null || botJob.getId() == null || botJob.getId() <= 0) {
            throw new SQLException("ExcelWrite execution requires an active Bot Job.");
        }
        List<BlockLoadDTO> blocks = botJob.getBlockLoadDTOList() == null
                ? List.of()
                : botJob.getBlockLoadDTOList();
        boolean hasExcelWrite = blocks.stream()
                .filter(block -> block != null && block.getInstructionLoad() != null)
                .flatMap(block -> block.getInstructionLoad().stream())
                .anyMatch(row -> row != null && isExcelWrite(row.getActions()));
        if (!hasExcelWrite) return new ExcelWriteExecutionPlan(Map.of());

        Map<Integer, StoredConfiguration> typed = Map.of();
        try (Connection connection = DATABASE.getConnection()) {
            if (tableExists(connection, "instruction_variable_command_config")) {
                typed = CONFIGURATIONS.loadForBotJob(connection, botJob.getId());
            }
        }

        LinkedHashMap<Integer, Target> plan = new LinkedHashMap<>();
        for (BlockLoadDTO block : blocks) {
            if (block == null || block.getInstructionLoad() == null) continue;
            Map<Integer, InstructionLoad> rows = new LinkedHashMap<>();
            for (InstructionLoad row : block.getInstructionLoad()) {
                if (row != null && row.getId() != null) rows.put(row.getId(), row);
            }
            for (InstructionLoad instruction : block.getInstructionLoad()) {
                if (instruction == null
                        || instruction.getId() == null
                        || !isExcelWrite(instruction.getActions())) {
                    continue;
                }
                StoredConfiguration stored = typed.get(instruction.getId());
                if (stored != null) {
                    plan.put(instruction.getId(), target(
                            instruction.getId(),
                            stored.outputKey(),
                            stored.outputColumn(),
                            stored.outputFile(),
                            stored.formatPolicy(),
                            false));
                    continue;
                }
                InstructionLoad parent = instruction.getParentId() == null
                        ? null
                        : rows.get(instruction.getParentId());
                plan.put(instruction.getId(), target(
                        instruction.getId(),
                        legacyOutputKey(instruction.getOperation()),
                        parent == null ? "" : safe(parent.getName()).trim(),
                        block.getExportFile(),
                        "EXACT_TEXT",
                        true));
            }
        }
        validateSharedFiles(plan.values());
        return new ExcelWriteExecutionPlan(plan);
    }

    public Target targetFor(Integer instructionId) {
        return instructionId == null ? null : targets.get(instructionId);
    }

    public List<Target> configuredTargets() {
        return targets.values().stream().filter(Target::configured).toList();
    }

    private static Target target(
            int instructionId,
            String outputKey,
            String outputColumn,
            String encoded,
            String formatPolicy,
            boolean legacy) {
        Optional<ExcelExportTarget> decoded;
        try {
            decoded = ExcelExportTarget.decode(encoded);
        } catch (RuntimeException invalid) {
            decoded = Optional.empty();
        }
        return new Target(
                instructionId,
                safe(outputKey).trim(),
                safe(outputColumn).trim(),
                safe(encoded).trim(),
                safe(formatPolicy).trim(),
                legacy,
                decoded.orElse(null));
    }

    private static boolean isExcelWrite(String action) {
        String normalized = safe(action).trim().toUpperCase(Locale.ROOT);
        return "E".equals(normalized) || "EXCELWRITE".equals(normalized);
    }

    private static void validateSharedFiles(Iterable<Target> targets) throws SQLException {
        LinkedHashMap<String, String> delimiters = new LinkedHashMap<>();
        for (Target target : targets) {
            if (!target.configured()) continue;
            String previous = delimiters.putIfAbsent(target.fileKey(), target.delimiter());
            if (previous != null && !previous.equals(target.delimiter())) {
                throw new SQLException(
                        "ExcelWrite instructions targeting the same file must use one delimiter.");
            }
        }
    }

    private static String legacyOutputKey(String operation) {
        String[] parts = safe(operation).split(":");
        String value = parts.length == 0 ? "" : parts[parts.length - 1].trim();
        if (value.startsWith("$") || value.startsWith("#")) value = value.substring(1).trim();
        return value.isEmpty() ? "ExcelWrite" : value;
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String candidate : List.of(table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT))) {
            try (ResultSet rows = metadata.getTables(null, null, candidate, new String[] {"TABLE"})) {
                if (rows.next()) return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record Target(
            int instructionId,
            String outputKey,
            String outputColumn,
            String encodedFile,
            String formatPolicy,
            boolean legacy,
            ExcelExportTarget fileTarget) {
        public boolean configured() {
            return fileTarget != null
                    && !outputKey.isBlank()
                    && !outputColumn.isBlank();
        }

        public String fullPath() {
            return fileTarget == null ? "" : fileTarget.path().toString();
        }

        public String fileKey() {
            String normalized = fileTarget == null
                    ? ""
                    : fileTarget.path().toAbsolutePath().normalize().toString();
            return isWindows() ? normalized.toLowerCase(Locale.ROOT) : normalized;
        }

        public String delimiter() {
            return fileTarget == null ? "," : fileTarget.delimiter();
        }

        private static boolean isWindows() {
            return System.getProperty("os.name", "")
                    .toLowerCase(Locale.ROOT)
                    .contains("win");
        }
    }
}
