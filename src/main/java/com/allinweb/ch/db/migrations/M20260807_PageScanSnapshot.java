package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the immutable, owner-scoped Page Scanner history registry. */
public final class M20260807_PageScanSnapshot implements Migration {

    private static final String NAME = "2026-08-07__page_scan_snapshot";
    private static final String TABLE = "page_scan_snapshot";
    private static final String OWNER_INDEX = "idx_page_scan_snapshot_owner";
    private static final String PAGE_INDEX = "idx_page_scan_snapshot_page";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        if (!tableExists(conn, TABLE)) {
            try (Statement statement = conn.createStatement()) {
                statement.executeUpdate(createTableSql(dialect));
            }
        }
        if ("SQLServer".equalsIgnoreCase(dialect)) {
            repairOversizedSqlServerKeys(conn);
        }
        ensureIndex(
                conn,
                OWNER_INDEX,
                "CREATE INDEX " + OWNER_INDEX
                        + " ON " + TABLE + " (home_banking_id, bot_job_id, captured_at)");
        ensureIndex(
                conn,
                PAGE_INDEX,
                "CREATE INDEX " + PAGE_INDEX
                        + " ON " + TABLE + " (home_banking_id, bot_job_id, page_key)");
    }

    static String createTableSql(String dialect) {
        String selected = dialect == null ? "Access" : dialect;
        return switch (selected) {
            case "Postgres" -> "CREATE TABLE " + TABLE + " ("
                    + "scan_id VARCHAR(36) PRIMARY KEY,"
                    + "home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,"
                    + "home_url_id INTEGER,"
                    + "page_key VARCHAR(128) NOT NULL,"
                    + "page_url TEXT,"
                    + "captured_at VARCHAR(40) NOT NULL,"
                    + "element_count INTEGER NOT NULL,"
                    + "artifact_path TEXT NOT NULL,"
                    + "manifest_sha256 VARCHAR(64),"
                    + "status VARCHAR(16) NOT NULL,"
                    + "pinned INTEGER NOT NULL DEFAULT 0)";
            case "SQLServer" -> "CREATE TABLE " + TABLE + " ("
                    + "scan_id NVARCHAR(36) NOT NULL,"
                    + "home_banking_id INT NOT NULL,"
                    + "bot_job_id INT NOT NULL,"
                    + "home_url_id INT,"
                    + "page_key NVARCHAR(128) NOT NULL,"
                    + "page_url NVARCHAR(MAX),"
                    + "captured_at NVARCHAR(40) NOT NULL,"
                    + "element_count INT NOT NULL,"
                    + "artifact_path NVARCHAR(MAX) NOT NULL,"
                    + "manifest_sha256 NVARCHAR(64),"
                    + "status NVARCHAR(16) NOT NULL,"
                    + "pinned INT NOT NULL DEFAULT 0,"
                    + "CONSTRAINT pk_page_scan_snapshot PRIMARY KEY (scan_id))";
            case "TEXT" -> "CREATE TABLE " + TABLE + " ("
                    + "scan_id TEXT PRIMARY KEY,"
                    + "home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,"
                    + "home_url_id INTEGER,"
                    + "page_key TEXT NOT NULL,"
                    + "page_url TEXT,"
                    + "captured_at TEXT NOT NULL,"
                    + "element_count INTEGER NOT NULL,"
                    + "artifact_path TEXT NOT NULL,"
                    + "manifest_sha256 TEXT,"
                    + "status TEXT NOT NULL,"
                    + "pinned INTEGER NOT NULL DEFAULT 0)";
            default -> "CREATE TABLE " + TABLE + " ("
                    + "scan_id VARCHAR(36) PRIMARY KEY,"
                    + "home_banking_id LONG NOT NULL,"
                    + "bot_job_id LONG NOT NULL,"
                    + "home_url_id LONG,"
                    + "page_key VARCHAR(128) NOT NULL,"
                    + "page_url MEMO,"
                    + "captured_at VARCHAR(40) NOT NULL,"
                    + "element_count LONG NOT NULL,"
                    + "artifact_path MEMO NOT NULL,"
                    + "manifest_sha256 VARCHAR(64),"
                    + "status VARCHAR(16) NOT NULL,"
                    + "pinned LONG NOT NULL DEFAULT 0)";
        };
    }

    /**
     * Repairs the original P1 draft if a SQL Server installation created its 4000-character key
     * columns before the bounded schema was deployed. Known secondary indexes and the primary key
     * are removed only when a narrowing operation is actually required, then recreated by
     * {@link #apply(Connection, String)}.
     */
    private static void repairOversizedSqlServerKeys(Connection conn) throws SQLException {
        if (!columnExceeds(conn, "scan_id", 36)
                && !columnExceeds(conn, "page_key", 128)
                && !columnExceeds(conn, "captured_at", 40)
                && !columnExceeds(conn, "manifest_sha256", 64)
                && !columnExceeds(conn, "status", 16)) {
            return;
        }
        assertMaximumLength(conn, "scan_id", 36);
        assertMaximumLength(conn, "page_key", 128);
        assertMaximumLength(conn, "captured_at", 40);
        assertMaximumLength(conn, "manifest_sha256", 64);
        assertMaximumLength(conn, "status", 16);

        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement statement = conn.createStatement()) {
            dropIndexIfPresent(conn, statement, OWNER_INDEX);
            dropIndexIfPresent(conn, statement, PAGE_INDEX);
            String primaryKey = primaryKeyName(conn);
            if (primaryKey != null && !primaryKey.isBlank()) {
                statement.executeUpdate("ALTER TABLE " + TABLE + " DROP CONSTRAINT "
                        + sqlServerIdentifier(primaryKey));
            }
            statement.executeUpdate("ALTER TABLE " + TABLE
                    + " ALTER COLUMN scan_id NVARCHAR(36) NOT NULL");
            statement.executeUpdate("ALTER TABLE " + TABLE
                    + " ALTER COLUMN page_key NVARCHAR(128) NOT NULL");
            statement.executeUpdate("ALTER TABLE " + TABLE
                    + " ALTER COLUMN captured_at NVARCHAR(40) NOT NULL");
            statement.executeUpdate("ALTER TABLE " + TABLE
                    + " ALTER COLUMN manifest_sha256 NVARCHAR(64) NULL");
            statement.executeUpdate("ALTER TABLE " + TABLE
                    + " ALTER COLUMN status NVARCHAR(16) NOT NULL");
            statement.executeUpdate("ALTER TABLE " + TABLE
                    + " ADD CONSTRAINT pk_page_scan_snapshot PRIMARY KEY (scan_id)");
            conn.commit();
        } catch (SQLException failure) {
            conn.rollback();
            throw failure;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    private static boolean columnExceeds(Connection conn, String column, int maximum)
            throws SQLException {
        DatabaseMetaData metadata = conn.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, null, column)) {
            while (columns.next()) {
                if (TABLE.equalsIgnoreCase(columns.getString("TABLE_NAME"))) {
                    return columns.getInt("COLUMN_SIZE") > maximum;
                }
            }
        }
        throw new SQLException("Missing " + TABLE + "." + column + " during SQL Server repair");
    }

    private static void assertMaximumLength(Connection conn, String column, int maximum)
            throws SQLException {
        String sql = "SELECT MAX(LEN(" + column + ")) FROM " + TABLE;
        try (Statement statement = conn.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            if (rows.next() && rows.getInt(1) > maximum) {
                throw new SQLException("Cannot narrow " + TABLE + "." + column
                        + ": existing data exceeds " + maximum + " characters");
            }
        }
    }

    private static String primaryKeyName(Connection conn) throws SQLException {
        try (ResultSet keys = conn.getMetaData().getPrimaryKeys(null, null, TABLE)) {
            return keys.next() ? keys.getString("PK_NAME") : null;
        }
    }

    private static void dropIndexIfPresent(Connection conn, Statement statement, String name)
            throws SQLException {
        if (indexExists(conn, name)) {
            statement.executeUpdate("DROP INDEX " + sqlServerIdentifier(name) + " ON " + TABLE);
        }
    }

    private static String sqlServerIdentifier(String value) {
        return "[" + value.replace("]", "]]") + "]";
    }

    private static void ensureIndex(Connection conn, String name, String createSql)
            throws SQLException {
        if (indexExists(conn, name)) return;
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate(createSql);
        }
    }

    private static boolean indexExists(Connection conn, String name) throws SQLException {
        try (ResultSet indexes = conn.getMetaData().getIndexInfo(null, null, TABLE, false, false)) {
            while (indexes.next()) {
                if (name.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return true;
            }
        }
        return false;
    }

    private static boolean tableExists(Connection conn, String name) throws SQLException {
        DatabaseMetaData metadata = conn.getMetaData();
        try (var tables = metadata.getTables(null, null, null, new String[] {"TABLE"})) {
            while (tables.next()) {
                if (name.equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }
}
