package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/** Adds the typed CE-7 command configuration shadow table without replacing legacy execution. */
public final class M20260801_InstructionVariableCommandConfig implements Migration {
    public static final String TABLE = "instruction_variable_command_config";
    private static final String NAME = "2026-08-01__instruction_variable_command_config";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection connection, String dialect) throws SQLException {
        if (!tableExists(connection, TABLE)) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createTableSql(dialect));
            }
        }
        if (!indexExists(connection, "ix_instruction_variable_command_config_job")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE INDEX ix_instruction_variable_command_config_job ON "
                        + TABLE + " (home_banking_id, bot_job_id)");
            }
        }
    }

    static String createTableSql(String dialect) {
        String integerType = switch (dialect) {
            case "Postgres", "SQLServer" -> "BIGINT";
            case "TEXT" -> "INTEGER";
            default -> "LONG";
        };
        String textType = switch (dialect) {
            case "SQLServer" -> "NVARCHAR(2048)";
            case "Access" -> "LONGCHAR";
            default -> "TEXT";
        };
        String timestampType = switch (dialect) {
            case "Postgres" -> "TIMESTAMP";
            case "SQLServer" -> "DATETIME2";
            case "TEXT" -> "TEXT";
            default -> "DATETIME";
        };
        return "CREATE TABLE " + TABLE + " ("
                + "home_banking_id " + integerType + " NOT NULL,"
                + "bot_job_id " + integerType + " NOT NULL,"
                + "instruction_id " + integerType + " NOT NULL,"
                + "command_type VARCHAR(40) NOT NULL,"
                + "operand_kind VARCHAR(20),"
                + "comparison_operator VARCHAR(40),"
                + "operand_raw_value " + textType + ","
                + "operand_variable_id " + integerType + ","
                + "output_key " + textType + ","
                + "output_column " + textType + ","
                + "output_file " + textType + ","
                + "external_source_key " + textType + ","
                + "format_policy VARCHAR(60),"
                + "config_revision " + integerType + " NOT NULL,"
                + "created_at " + timestampType + " NOT NULL,"
                + "updated_at " + timestampType + " NOT NULL,"
                + "CONSTRAINT pk_instruction_variable_command_config PRIMARY KEY"
                + " (home_banking_id, bot_job_id, instruction_id)"
                + ")";
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, null, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (name.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }

    private static boolean indexExists(Connection connection, String name) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(null, null, TABLE, false, false)) {
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                if (indexName != null && name.toLowerCase(Locale.ROOT)
                        .equals(indexName.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }
}
