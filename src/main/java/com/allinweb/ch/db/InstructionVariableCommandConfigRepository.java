package com.allinweb.ch.db;

import com.allinweb.ch.db.migrations.M20260801_InstructionVariableCommandConfig;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Configuration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistence boundary for typed Variables Command Editor configuration. */
public final class InstructionVariableCommandConfigRepository {
    private static final String TABLE = M20260801_InstructionVariableCommandConfig.TABLE;

    public Map<Integer, StoredConfiguration> loadForBotJob(Connection connection, int botJobId)
            throws SQLException {
        LinkedHashMap<Integer, StoredConfiguration> result = new LinkedHashMap<>();
        String sql = "SELECT instruction_id,command_type,operand_kind,comparison_operator,"
                + "operand_raw_value,operand_variable_id,output_key,output_column,output_file,"
                + "external_source_key,format_policy,config_revision FROM " + TABLE
                + " WHERE bot_job_id=? ORDER BY instruction_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    StoredConfiguration stored = from(rows);
                    result.put(stored.instructionId(), stored);
                }
            }
        }
        return Map.copyOf(result);
    }

    public StoredConfiguration load(
            Connection connection, int homeBankingId, int botJobId, int instructionId)
            throws SQLException {
        String sql = "SELECT instruction_id,command_type,operand_kind,comparison_operator,"
                + "operand_raw_value,operand_variable_id,output_key,output_column,output_file,"
                + "external_source_key,format_policy,config_revision FROM " + TABLE
                + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            statement.setInt(3, instructionId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? from(rows) : null;
            }
        }
    }

    public void upsert(
            Connection connection,
            int homeBankingId,
            int botJobId,
            int instructionId,
            String commandType,
            Configuration configuration)
            throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        String update = "UPDATE " + TABLE + " SET command_type=?,operand_kind=?,"
                + "comparison_operator=?,operand_raw_value=?,operand_variable_id=?,output_key=?,"
                + "output_column=?,output_file=?,external_source_key=?,format_policy=?,"
                + "config_revision=config_revision+1,updated_at=?"
                + " WHERE home_banking_id=? AND bot_job_id=? AND instruction_id=?";
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            bindConfiguration(statement, commandType, configuration, now, 1);
            statement.setInt(12, homeBankingId);
            statement.setInt(13, botJobId);
            statement.setInt(14, instructionId);
            if (statement.executeUpdate() == 1) return;
        }
        String insert = "INSERT INTO " + TABLE + " (home_banking_id,bot_job_id,instruction_id,"
                + "command_type,operand_kind,comparison_operator,operand_raw_value,"
                + "operand_variable_id,output_key,output_column,output_file,external_source_key,"
                + "format_policy,config_revision,created_at,updated_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setInt(1, homeBankingId);
            statement.setInt(2, botJobId);
            statement.setInt(3, instructionId);
            bindConfiguration(statement, commandType, configuration, now, 4);
            statement.setInt(14, 1);
            statement.setTimestamp(15, now);
            statement.setTimestamp(16, now);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Typed command configuration was not stored.");
            }
        }
    }

    private static void bindConfiguration(
            PreparedStatement statement,
            String commandType,
            Configuration configuration,
            Timestamp now,
            int offset)
            throws SQLException {
        statement.setString(offset, commandType);
        statement.setString(offset + 1, configuration.operandKind());
        statement.setString(offset + 2, configuration.comparisonOperator());
        statement.setString(offset + 3, configuration.operandRawValue());
        statement.setObject(offset + 4, configuration.operandVariableId());
        statement.setString(offset + 5, configuration.outputKey());
        statement.setString(offset + 6, configuration.outputColumn());
        statement.setString(offset + 7, configuration.outputFile());
        statement.setString(offset + 8, configuration.externalSourceKey());
        statement.setString(offset + 9, configuration.formatPolicy());
        if (offset == 1) statement.setTimestamp(offset + 10, now);
    }

    private static StoredConfiguration from(ResultSet rows) throws SQLException {
        return new StoredConfiguration(
                rows.getInt("instruction_id"),
                rows.getString("command_type"),
                rows.getString("operand_kind"),
                rows.getString("comparison_operator"),
                rows.getString("operand_raw_value"),
                nullableInteger(rows, "operand_variable_id"),
                rows.getString("output_key"),
                rows.getString("output_column"),
                rows.getString("output_file"),
                rows.getString("external_source_key"),
                rows.getString("format_policy"),
                rows.getLong("config_revision"));
    }

    private static Integer nullableInteger(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    public record StoredConfiguration(
            int instructionId,
            String commandType,
            String operandKind,
            String comparisonOperator,
            String operandRawValue,
            Integer operandVariableId,
            String outputKey,
            String outputColumn,
            String outputFile,
            String externalSourceKey,
            String formatPolicy,
            long configRevision) {}
}
