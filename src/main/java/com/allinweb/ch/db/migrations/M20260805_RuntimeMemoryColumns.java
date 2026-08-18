package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * Variable-domain consolidation, user decision 2026-08-03 ("fewer tables, no new tables").
 *
 * <p>The dropped {@code bot_job_runtime_memory} table held three per-Bot-Job counters
 * (runtime_revision, reset_generation, next_variable_id). They now live as COLUMNS on the
 * existing {@code instruction_graph_state} row, so one row per Bot Job carries both the
 * authoring counter ({@code graph_version}) and the runtime counters. NULL columns mean
 * "runtime memory not initialized yet" — {@code BotJobRuntimeMemoryRepository} initializes
 * them on first use exactly like the old row-insert did.
 *
 * <p>Also adds the unique variable-name index: names such as Left_Operand / Right_Operand
 * are first-class and can never duplicate inside one Bot Job. No table is created.
 */
public final class M20260805_RuntimeMemoryColumns implements Migration {
    private static final String NAME = "2026-08-05__runtime_memory_columns";
    private static final String STATE_TABLE = "instruction_graph_state";
    private static final String[] COLUMNS = {
        "runtime_revision", "reset_generation", "next_variable_id",
    };

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection connection, String dialect) throws SQLException {
        String integerType = switch (dialect) {
            case "Postgres", "SQLServer" -> "BIGINT";
            case "TEXT" -> "INTEGER";
            default -> "LONG";
        };
        for (String column : COLUMNS) {
            if (columnExists(connection, STATE_TABLE, column)) continue;
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + STATE_TABLE
                        + " ADD COLUMN " + column + " " + integerType);
            }
        }
        if (!indexExists(connection, "bot_job_variable_definition", "ux_bot_job_variable_name")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE UNIQUE INDEX ux_bot_job_variable_name"
                        + " ON bot_job_variable_definition (home_banking_id, bot_job_id, name)");
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, null, null)) {
            while (columns.next()) {
                if (table.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean indexExists(Connection connection, String table, String name)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(null, null, table, false, false)) {
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                if (indexName != null && name.toLowerCase(Locale.ROOT)
                        .equals(indexName.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }
}
