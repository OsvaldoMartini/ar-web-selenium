package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Convert {@code instruction.force_coordinates} from INTEGER/YESNO to VARCHAR(8)
 * (and the same column on {@code component_instruction}). Existing value {@code 1}
 * is transformed to string {@code "F"}; {@code 0} / NULL becomes empty string.
 *
 * <p>Also performs the I:E action split as part of the same migration: rows with
 * {@code actions = 'I:E'} become {@code actions = 'I'} with {@code 'E'} appended
 * to {@code force_coordinates} — so the flag bit takes over what the legacy action
 * code used to signal.</p>
 *
 * <p>Strategy per dialect:</p>
 * <ul>
 *   <li><b>Postgres</b> — single {@code ALTER TABLE … ALTER COLUMN … TYPE VARCHAR(8) USING CASE …}.</li>
 *   <li><b>SQLite</b>  — SQLite can't change a column type in place. Use the
 *       "add new col, backfill, drop old, rename" dance.</li>
 *   <li><b>SQLServer</b> — {@code ALTER COLUMN} with a preceding UPDATE to set the new
 *       string values, then an explicit cast. Mostly similar to Postgres.</li>
 *   <li><b>Access</b>   — backup the .accdb file into
 *       {@code {path_db}/backup/{stamp}__force_coordinates_varchar.accdb}, then read
 *       all rows into memory, DROP + CREATE the tables with the new schema, and
 *       INSERT the rows back with transformed flag values. No file deletion: the
 *       original .accdb is updated in place so the app's open JDBC connection
 *       continues to work.</li>
 * </ul>
 */
@Slf4j
public class M20260425_ForceCoordinatesVarchar implements Migration {

    private static final String NAME = "2026-04-25__force_coordinates_varchar";
    private static final String[] TABLES = {"instruction", "component_instruction"};

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        log.info("{} — applying on dialect={}", NAME, dialect);

        // Idempotency — if column is already VARCHAR/TEXT, skip the type change.
        boolean typeChangeNeeded = columnIsNumeric(conn, TABLES[0]);
        log.info("{} — typeChangeNeeded={}", NAME, typeChangeNeeded);

        if (typeChangeNeeded) {
            switch (dialect) {
                case "Postgres":
                    applyPostgres(conn);
                    break;
                case "TEXT":
                    applySqlite(conn);
                    break;
                case "SQLServer":
                    applySqlServer(conn);
                    break;
                default: // Access
                    applyAccess(conn);
                    break;
            }
        }

        // Second pass — same SQL for every dialect, idempotent.
        // UPDATE rows where actions = 'I:E'  →  actions = 'I', force_coordinates = force_coordinates || 'E'
        splitInputEnterAction(conn, dialect);
    }

    // ── Dialect handlers ────────────────────────────────────────────────────

    private void applyPostgres(Connection conn) throws SQLException {
        for (String table : TABLES) {
            exec(
                    conn,
                    "ALTER TABLE " + table
                            + " ALTER COLUMN force_coordinates TYPE VARCHAR(8)"
                            + " USING CASE WHEN force_coordinates = 1 THEN 'F' ELSE '' END");
        }
    }

    private void applySqlite(Connection conn) throws SQLException {
        for (String table : TABLES) {
            exec(conn, "ALTER TABLE " + table + " ADD COLUMN force_coordinates_new VARCHAR(8)");
            exec(
                    conn,
                    "UPDATE " + table
                            + " SET force_coordinates_new = CASE WHEN force_coordinates = 1 THEN 'F' ELSE '' END");
            // SQLite >= 3.35 supports DROP COLUMN; if older, the app's embedded SQLite build may not.
            exec(conn, "ALTER TABLE " + table + " DROP COLUMN force_coordinates");
            exec(conn, "ALTER TABLE " + table + " RENAME COLUMN force_coordinates_new TO force_coordinates");
        }
    }

    private void applySqlServer(Connection conn) throws SQLException {
        for (String table : TABLES) {
            exec(conn, "ALTER TABLE " + table + " ADD force_coordinates_new VARCHAR(8)");
            exec(
                    conn,
                    "UPDATE " + table
                            + " SET force_coordinates_new = CASE WHEN force_coordinates = 1 THEN 'F' ELSE '' END");
            exec(conn, "ALTER TABLE " + table + " DROP COLUMN force_coordinates");
            exec(conn, "EXEC sp_rename '" + table + ".force_coordinates_new', 'force_coordinates', 'COLUMN'");
        }
    }

    /**
     * Access in-memory migration. Takes a safety copy of the .accdb first
     * (under {@code {path_db}/backup/}), then rebuilds each affected table:
     * SELECT * → DROP TABLE → CREATE TABLE (new schema) → INSERT rows with transform.
     */
    private void applyAccess(Connection conn) throws SQLException {
        safetyBackupAccessFile();
        for (String table : TABLES) {
            rebuildTableAccess(conn, table);
        }
    }

    private void rebuildTableAccess(Connection conn, String table) throws SQLException {
        // 1. Snapshot current schema (column names + types) and row data.
        List<ColumnDef> cols = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                cols.add(new ColumnDef(md.getColumnName(i), md.getColumnTypeName(i)));
            }
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (ColumnDef c : cols) {
                    row.put(c.name, rs.getObject(c.name));
                }
                rows.add(row);
            }
        }
        log.info("{} — Access: cached {} row(s) from {}", NAME, rows.size(), table);

        // 2. DROP + CREATE with new schema (VARCHAR(8) for force_coordinates).
        //    To keep this migration self-contained we rebuild the column list from the snapshot,
        //    changing only force_coordinates' type. Types other than force_coordinates are kept
        //    verbatim using their DB-reported type name.
        StringBuilder createSql =
                new StringBuilder("CREATE TABLE ").append(table).append(" (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) createSql.append(", ");
            ColumnDef c = cols.get(i);
            String type = "force_coordinates".equalsIgnoreCase(c.name) ? "TEXT(8)" : c.typeName;
            createSql.append(c.name).append(' ').append(type);
        }
        createSql.append(')');
        exec(conn, "DROP TABLE " + table);
        exec(conn, createSql.toString());

        // 3. INSERT rows back, transforming force_coordinates on the way.
        if (rows.isEmpty()) return;
        StringBuilder insertSql =
                new StringBuilder("INSERT INTO ").append(table).append(" (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) insertSql.append(", ");
            insertSql.append(cols.get(i).name);
        }
        insertSql.append(") VALUES (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) insertSql.append(", ");
            insertSql.append('?');
        }
        insertSql.append(')');

        try (PreparedStatement ps = conn.prepareStatement(insertSql.toString())) {
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < cols.size(); i++) {
                    ColumnDef c = cols.get(i);
                    Object value = row.get(c.name);
                    if ("force_coordinates".equalsIgnoreCase(c.name)) {
                        value = transformLegacyForceCoord(value);
                    }
                    ps.setObject(i + 1, value);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
        log.info("{} — Access: reinserted {} row(s) into {}", NAME, rows.size(), table);
    }

    private String transformLegacyForceCoord(Object raw) {
        if (raw == null) return "";
        if (raw instanceof Boolean) return ((Boolean) raw) ? "F" : "";
        if (raw instanceof Number) return ((Number) raw).intValue() != 0 ? "F" : "";
        String s = raw.toString().trim();
        return s.equals("1") || s.equalsIgnoreCase("true") || s.equals("-1") ? "F" : "";
    }

    private void safetyBackupAccessFile() {
        try {
            String pathDb = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.PATH_DB);
            if (pathDb == null || pathDb.isBlank()) {
                log.warn("{} — PATH_DB is not set; skipping .accdb safety copy", NAME);
                return;
            }
            Path source = Paths.get(pathDb, ARConstants.FILE_NAME_ACCESS);
            if (!Files.exists(source)) {
                log.warn("{} — .accdb not found at {}; skipping safety copy", NAME, source);
                return;
            }
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path backupDir = Paths.get(pathDb, "backup");
            Files.createDirectories(backupDir);
            Path target = backupDir.resolve(stamp + "__" + NAME + "__" + ARConstants.FILE_NAME_ACCESS);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("{} — Access safety backup written to {}", NAME, target);
        } catch (IOException e) {
            // The connection may be holding a read lock on the file. We log and proceed —
            // the in-memory migration path below is still safe; the file copy is a belt-and-braces.
            log.warn(
                    "{} — Access safety backup failed ({}). Proceeding with in-memory migration.",
                    NAME,
                    e.getMessage());
        }
    }

    // ── Shared helpers ──────────────────────────────────────────────────────

    private void splitInputEnterAction(Connection conn, String dialect) throws SQLException {
        // Append 'E' to force_coordinates where actions='I:E', and set actions='I'.
        // Idempotent: second run finds zero rows with actions='I:E'.
        String concatSql;
        switch (dialect) {
            case "SQLServer":
                concatSql = "ISNULL(force_coordinates, '') + 'E'";
                break;
            case "Access":
            default:
                // Postgres, SQLite, Access all accept || for concat in their supported dialects.
                // Access also accepts & — we use || which is standards-SQL-ish and works on ucanaccess.
                if ("Access".equals(dialect)) concatSql = "(force_coordinates & 'E')";
                else concatSql = "(COALESCE(force_coordinates, '') || 'E')";
                break;
            case "Postgres":
            case "TEXT":
                concatSql = "(COALESCE(force_coordinates, '') || 'E')";
                break;
        }
        for (String table : TABLES) {
            try (Statement st = conn.createStatement()) {
                int rows = st.executeUpdate("UPDATE " + table
                        + " SET actions = 'I', force_coordinates = " + concatSql
                        + " WHERE actions = 'I:E'");
                if (rows > 0) {
                    log.info("{} — {}: split {} I:E row(s) into actions='I' with 'E' flag", NAME, table, rows);
                }
            }
        }
    }

    private boolean columnIsNumeric(Connection conn, String table) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(null, null, table, "force_coordinates")) {
            if (!rs.next()) return false;
            int dataType = rs.getInt("DATA_TYPE");
            return dataType == Types.INTEGER
                    || dataType == Types.SMALLINT
                    || dataType == Types.TINYINT
                    || dataType == Types.BIT
                    || dataType == Types.BOOLEAN
                    || dataType == Types.NUMERIC
                    || dataType == Types.DECIMAL;
        }
    }

    private void exec(Connection conn, String sql) throws SQLException {
        log.debug("{} — exec: {}", NAME, sql);
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static final class ColumnDef {
        final String name;
        final String typeName;

        ColumnDef(String name, String typeName) {
            this.name = name;
            this.typeName = typeName;
        }
    }
}
