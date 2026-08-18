package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates the database-owned optimistic version row for each instruction workspace owner.
 *
 * <p>The compound primary key deliberately includes both the workspace kind and normalized owner
 * ID. A Component workspace and a Bot Job may have the same numeric ID without sharing a graph
 * version.
 */
@Slf4j
public final class M20260729_InstructionGraphState implements Migration {

    public static final String TABLE = "instruction_graph_state";
    private static final String NAME = "2026-07-29__instruction_graph_state";
    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "workspace_kind",
            "home_banking_id",
            "owner_id",
            "graph_version",
            "created_at",
            "updated_at");

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
            log.info("{} - created {} for dialect {}", NAME, TABLE, dialect);
        }
        verifyShape(connection);
    }

    static String createTableSql(String dialect) {
        return switch (dialect) {
            case "Postgres" -> "CREATE TABLE " + TABLE + " ("
                    + "workspace_kind VARCHAR(16) NOT NULL,"
                    + "home_banking_id BIGINT NOT NULL,"
                    + "owner_id BIGINT NOT NULL,"
                    + "graph_version BIGINT NOT NULL,"
                    + "created_at TIMESTAMP NOT NULL,"
                    + "updated_at TIMESTAMP NOT NULL,"
                    + "CONSTRAINT pk_instruction_graph_state PRIMARY KEY "
                    + "(workspace_kind, home_banking_id, owner_id)"
                    + ")";
            case "SQLServer" -> "CREATE TABLE " + TABLE + " ("
                    + "workspace_kind NVARCHAR(16) NOT NULL,"
                    + "home_banking_id BIGINT NOT NULL,"
                    + "owner_id BIGINT NOT NULL,"
                    + "graph_version BIGINT NOT NULL,"
                    + "created_at DATETIME NOT NULL,"
                    + "updated_at DATETIME NOT NULL,"
                    + "CONSTRAINT pk_instruction_graph_state PRIMARY KEY "
                    + "(workspace_kind, home_banking_id, owner_id)"
                    + ")";
            case "TEXT" -> "CREATE TABLE " + TABLE + " ("
                    + "workspace_kind TEXT NOT NULL,"
                    + "home_banking_id INTEGER NOT NULL,"
                    + "owner_id INTEGER NOT NULL,"
                    + "graph_version INTEGER NOT NULL,"
                    + "created_at TEXT NOT NULL,"
                    + "updated_at TEXT NOT NULL,"
                    + "PRIMARY KEY (workspace_kind, home_banking_id, owner_id)"
                    + ")";
            default -> "CREATE TABLE " + TABLE + " ("
                    + "workspace_kind VARCHAR(16) NOT NULL,"
                    + "home_banking_id LONG NOT NULL,"
                    + "owner_id LONG NOT NULL,"
                    + "graph_version LONG NOT NULL,"
                    + "created_at DATETIME NOT NULL,"
                    + "updated_at DATETIME NOT NULL,"
                    + "CONSTRAINT pk_instruction_graph_state PRIMARY KEY "
                    + "(workspace_kind, home_banking_id, owner_id)"
                    + ")";
        };
    }

    private static void verifyShape(Connection connection) throws SQLException {
        Set<String> columns = new HashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getColumns(null, null, null, null)) {
            while (result.next()) {
                String table = result.getString("TABLE_NAME");
                if (TABLE.equalsIgnoreCase(table)) {
                    columns.add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        if (!columns.containsAll(REQUIRED_COLUMNS)) {
            Set<String> missing = new HashSet<>(REQUIRED_COLUMNS);
            missing.removeAll(columns);
            throw new SQLException(NAME + " found an incompatible " + TABLE
                    + " table; missing columns " + missing);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(null, null, null, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
