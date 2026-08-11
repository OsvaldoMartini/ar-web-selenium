package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.InstructionVariableCommandConfigRepository;
import com.allinweb.ch.db.InstructionVariableCommandConfigRepository.StoredConfiguration;
import com.allinweb.ch.db.MigrationRunner.Migration;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Configuration;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.ConfigurationKind;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Backfills legacy Block-owned ExcelWrite destinations into instruction-owned typed configuration.
 * Variables are untouched; only the obsolete Web Element parent relationship is cleared after its
 * old column name has been captured.
 */
public final class M20260811_ExcelWriteInstructionTargets implements Migration {
    private static final String NAME = "2026-08-11__excelwrite_instruction_targets";
    private static final InstructionVariableCommandConfigRepository CONFIGURATIONS =
            new InstructionVariableCommandConfigRepository();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection connection, String dialect) throws SQLException {
        List<LegacyRow> rows = loadRows(connection);
        for (LegacyRow row : rows) {
            StoredConfiguration stored = CONFIGURATIONS.load(
                    connection, row.homeBankingId(), row.botJobId(), row.instructionId());
            String outputKey = stored == null
                    ? legacyOutputKey(row.operation())
                    : safe(stored.outputKey()).trim();
            String outputColumn = stored == null
                    ? safe(row.parentName()).trim()
                    : safe(stored.outputColumn()).trim();
            String outputFile = stored == null
                    ? normalizeFile(row.blockExportFile())
                    : normalizeFile(stored.outputFile());
            String formatPolicy = stored == null
                    ? "EXACT_TEXT"
                    : safe(stored.formatPolicy()).trim();
            Configuration configuration = new Configuration(
                    ConfigurationKind.EXCEL_WRITE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    outputKey,
                    outputColumn,
                    outputFile,
                    null,
                    formatPolicy,
                    null);
            CONFIGURATIONS.upsert(
                    connection,
                    row.homeBankingId(),
                    row.botJobId(),
                    row.instructionId(),
                    "E",
                    configuration);
        }

        try (PreparedStatement clear = connection.prepareStatement(
                "UPDATE instruction SET parent_id=NULL,parent_block_id=NULL"
                        + " WHERE UPPER(TRIM(actions)) IN ('E','EXCELWRITE')")) {
            clear.executeUpdate();
        }
        try (PreparedStatement release = connection.prepareStatement(
                "UPDATE bot_job_variable_definition SET producer_instruction_id=NULL"
                        + " WHERE producer_instruction_id IN (SELECT i.id FROM instruction i"
                        + " WHERE UPPER(TRIM(i.actions)) IN ('E','EXCELWRITE'))")) {
            release.executeUpdate();
        }
    }

    private static List<LegacyRow> loadRows(Connection connection) throws SQLException {
        ArrayList<LegacyRow> rows = new ArrayList<>();
        String sql = "SELECT bot.home_banking_id,i.bot_job_id,i.id AS instruction_id,"
                + "i.operation,p.name AS parent_name,b.export_file"
                + " FROM instruction i"
                + " JOIN bot_job bot ON bot.id=i.bot_job_id"
                + " JOIN block b ON b.id=i.block_id AND b.bot_job_id=i.bot_job_id"
                + " LEFT JOIN instruction p ON p.id=i.parent_id AND p.bot_job_id=i.bot_job_id"
                + " WHERE UPPER(TRIM(i.actions)) IN ('E','EXCELWRITE')"
                + " ORDER BY i.bot_job_id,b.block_order_number,i.instruction_order_number,i.id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                rows.add(new LegacyRow(
                        result.getInt("home_banking_id"),
                        result.getInt("bot_job_id"),
                        result.getInt("instruction_id"),
                        result.getString("operation"),
                        result.getString("parent_name"),
                        result.getString("export_file")));
            }
        }
        return rows;
    }

    private static String normalizeFile(String value) {
        String normalized = safe(value).trim();
        return "No Excel Export File".equals(normalized) ? "" : normalized;
    }

    private static String legacyOutputKey(String operation) {
        String[] parts = safe(operation).split(":");
        String value = parts.length == 0 ? "" : parts[parts.length - 1].trim();
        if (value.startsWith("$") || value.startsWith("#")) value = value.substring(1).trim();
        return value.isEmpty() ? "ExcelWrite" : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record LegacyRow(
            int homeBankingId,
            int botJobId,
            int instructionId,
            String operation,
            String parentName,
            String blockExportFile) {}
}
