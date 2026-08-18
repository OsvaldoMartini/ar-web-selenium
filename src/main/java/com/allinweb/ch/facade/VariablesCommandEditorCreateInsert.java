package com.allinweb.ch.facade;

import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.Configuration;
import com.allinweb.ch.model.VariablesCommandEditorUpdateV1.ConfigurationKind;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/** SQL insert owned exclusively by ADD COMMAND. */
final class VariablesCommandEditorCreateInsert {
    private static final String INSERT_INSTRUCTION = """
            INSERT INTO instruction (
                   instruction_order_number,actions,name,xpath,coordinates,force_coordinates,
                   iframe_xpath,tag_name,shadow_host,shadow_root,css_selector,description,
                   operation,optional,block_marked,default_value,action_custom_max_wait_sec,
                   on_hold_seconds,codified,export_to_abr,active,block_id,
                   parent_block_id,parent_id,bot_job_id,client_named)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    int insert(
            Connection connection,
            int botJobId,
            int targetBlockId,
            int instructionOrderNumber,
            String targetAction,
            Configuration configuration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_INSTRUCTION, Statement.RETURN_GENERATED_KEYS)) {
            int parameter = 1;
            statement.setInt(parameter++, instructionOrderNumber);
            statement.setString(parameter++, targetAction);
            statement.setString(parameter++, CommandRegistry.transformedName(targetAction));
            for (int field = 0; field < 9; field++) {
                statement.setNull(parameter++, Types.VARCHAR);
            }
            statement.setObject(parameter++, configuredOperation(configuration));
            statement.setNull(parameter++, Types.BOOLEAN);
            statement.setNull(parameter++, Types.BOOLEAN);
            statement.setNull(parameter++, Types.VARCHAR);
            statement.setNull(parameter++, Types.INTEGER);
            Integer holdSeconds = configuredHold(configuration);
            if (holdSeconds == null) statement.setNull(parameter++, Types.INTEGER);
            else statement.setInt(parameter++, holdSeconds);
            statement.setNull(parameter++, Types.BOOLEAN);
            statement.setNull(parameter++, Types.BOOLEAN);
            statement.setBoolean(parameter++, true);
            statement.setInt(parameter++, targetBlockId);
            statement.setNull(parameter++, Types.INTEGER);
            statement.setNull(parameter++, Types.INTEGER);
            statement.setInt(parameter++, botJobId);
            statement.setNull(parameter, Types.VARCHAR);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("ADD COMMAND did not insert exactly one instruction.");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("ADD COMMAND returned no generated instruction ID.");
                }
                return keys.getInt(1);
            }
        }
    }

    private static String configuredOperation(Configuration configuration) {
        if (configuration.kind() == ConfigurationKind.WAIT
                || configuration.kind() == ConfigurationKind.NONE
                || isTypedVariableConfiguration(configuration.kind())) {
            return null;
        }
        return configuration.kind() == ConfigurationKind.GOTO
                        || configuration.kind() == ConfigurationKind.SWIPE
                ? String.valueOf(configuration.count())
                : configuration.intervalSeconds() + ":" + configuration.iterations();
    }

    private static Integer configuredHold(Configuration configuration) {
        return configuration.kind() == ConfigurationKind.WAIT
                ? configuration.waitSeconds()
                : null;
    }

    private static boolean isTypedVariableConfiguration(ConfigurationKind kind) {
        return kind == ConfigurationKind.CHECK_VALUE
                || kind == ConfigurationKind.EXTERNAL_CHECK
                || kind == ConfigurationKind.CONDITIONAL
                || kind == ConfigurationKind.EXCEL_WRITE;
    }
}
