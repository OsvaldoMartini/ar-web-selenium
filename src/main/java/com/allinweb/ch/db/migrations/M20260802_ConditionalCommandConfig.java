package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Adds CE-9 typed condition fields without changing legacy conditional execution. */
public final class M20260802_ConditionalCommandConfig implements Migration {
    private static final String NAME = "2026-08-02__conditional_command_config";
    private static final String TABLE = M20260801_InstructionVariableCommandConfig.TABLE;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection connection, String dialect) throws SQLException {
        addColumnIfMissing(connection, dialect, "condition_source", "VARCHAR(40)");
        String integerType = switch (dialect) {
            case "Postgres", "SQLServer" -> "BIGINT";
            case "TEXT" -> "INTEGER";
            default -> "LONG";
        };
        addColumnIfMissing(connection, dialect, "left_variable_id", integerType);
    }

    private static void addColumnIfMissing(
            Connection connection, String dialect, String column, String type) throws SQLException {
        if (columnExists(connection, column)) return;
        String addKeyword = "Access".equals(dialect) ? " ADD COLUMN " : " ADD ";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + TABLE + addKeyword + column + " " + type);
        }
    }

    private static boolean columnExists(Connection connection, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, null, null)) {
            while (columns.next()) {
                if (TABLE.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
