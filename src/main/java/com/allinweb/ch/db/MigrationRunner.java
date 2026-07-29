package com.allinweb.ch.db;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Lightweight schema migration runner.
 *
 * <p>On startup, ensures the {@code schema_migrations} table exists, then runs any
 * migration in {@link #MIGRATIONS} whose {@code name()} is not already recorded as
 * applied. Each successful run inserts a row {@code (name, applied_at)}.</p>
 *
 * <p>Supports 4 dialects determined by {@link ARPropertyEnum#DATABASE_TYPE}:
 * {@code Postgres}, {@code SQLServer}, {@code TEXT} (SQLite), and {@code Access}
 * (default). Each migration knows its own per-dialect SQL or takes a bespoke
 * backup + rebuild + restore path for Access, which does not tolerate
 * in-place ALTER TABLE gracefully.</p>
 *
 * <p>Hook it in {@code PerformInitializer.initialize()} so every app boot runs
 * any pending migrations before the rest of the stack starts using the DB.</p>
 */
@Slf4j
public class MigrationRunner {

    /**
     * A migration is identified by a name (stable, NEVER renamed once released)
     * and knows how to apply itself given a connection + dialect string.
     */
    public interface Migration {
        /** Stable identifier. Convention: {@code YYYY-MM-DD__short_description}. */
        String name();

        /** Runs the migration. {@code dialect} is one of Postgres / SQLServer / TEXT / Access. */
        void apply(Connection conn, String dialect) throws SQLException;
    }

    /**
     * Registered migrations, in execution order. Never reorder; only APPEND new ones.
     */
    private static final List<Migration> MIGRATIONS = List.of(
            new com.allinweb.ch.db.migrations.M20260425_ForceCoordinatesVarchar(),
            new com.allinweb.ch.db.migrations.M20260426_ScrollFromActionsVarchar(),
            new com.allinweb.ch.db.migrations.M20260427_OcrConfig(),
            new com.allinweb.ch.db.migrations.M20260428_ElementLocator(),
            new com.allinweb.ch.db.migrations.M20260429_ClientNamed(),
            new com.allinweb.ch.db.migrations.M20260510_FieldMapping(),
            new com.allinweb.ch.db.migrations.M20260511_UseCaseTable(),
            new com.allinweb.ch.db.migrations.M20260512_FlowAuthoring(),
            new com.allinweb.ch.db.migrations.M20260513_Requirements(),
            new com.allinweb.ch.db.migrations.M20260702_AiPrompt(),
            new com.allinweb.ch.db.migrations.M20260704_ScannedElement(),
            new com.allinweb.ch.db.migrations.M20260710_HomeUrlName(),
            new com.allinweb.ch.db.migrations.M20260721_PageScannerProfile(),
            new com.allinweb.ch.db.migrations.M20260724_ScannedElementPageScope(),
            new com.allinweb.ch.db.migrations.M20260729_InstructionGraphState());

    private static volatile MigrationRunner instance;

    public static MigrationRunner getInstance() {
        if (instance == null) {
            synchronized (MigrationRunner.class) {
                if (instance == null) instance = new MigrationRunner();
            }
        }
        return instance;
    }

    private MigrationRunner() {}

    /**
     * Ensure the tracking table exists and apply every pending migration in order.
     * Safe to call on every app startup — fully idempotent.
     *
     * @throws RuntimeException on first migration failure (subsequent migrations are not attempted)
     */
    public void runPending(Connection conn) {
        String dialect = getDialect();
        try {
            ensureTable(conn, dialect);
            Set<String> applied = loadApplied(conn);
            int ran = 0;
            for (Migration m : MIGRATIONS) {
                if (applied.contains(m.name())) {
                    log.debug("MigrationRunner — already applied: {}", m.name());
                    continue;
                }
                log.info("MigrationRunner — applying: {} [{}]", m.name(), dialect);
                try {
                    m.apply(conn, dialect);
                    recordApplied(conn, m.name());
                    log.info("MigrationRunner — OK: {}", m.name());
                    ran++;
                } catch (SQLException e) {
                    log.error("MigrationRunner — FAILED: {} - {}", m.name(), e.getMessage());
                    throw new RuntimeException("Migration failed: " + m.name(), e);
                }
            }
            if (ran == 0) {
                log.info("MigrationRunner — nothing to apply (dialect={}, registered={})", dialect, MIGRATIONS.size());
            } else {
                log.info("MigrationRunner — {} migration(s) applied", ran);
            }
        } catch (SQLException e) {
            log.error("MigrationRunner — setup failed: {}", e.getMessage());
            throw new RuntimeException("MigrationRunner setup failed", e);
        }
    }

    private String getDialect() {
        String t = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.DATABASE_TYPE);
        return (t == null || t.isBlank()) ? "Access" : t;
    }

    private void ensureTable(Connection conn, String dialect) throws SQLException {
        String createSql = null;
        switch (dialect) {
            case "Postgres":
                createSql = "CREATE TABLE IF NOT EXISTS schema_migrations ("
                        + "name VARCHAR(255) PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
                break;
            case "SQLServer":
                // SQLServer needs metadata probe — IF NOT EXISTS on CREATE TABLE is awkward.
                if (!tableExists(conn, "schema_migrations")) {
                    createSql = "CREATE TABLE schema_migrations ("
                            + "name NVARCHAR(255) PRIMARY KEY, applied_at DATETIME DEFAULT CURRENT_TIMESTAMP)";
                }
                break;
            case "TEXT":
                createSql = "CREATE TABLE IF NOT EXISTS schema_migrations ("
                        + "name TEXT PRIMARY KEY, applied_at TEXT DEFAULT CURRENT_TIMESTAMP)";
                break;
            default: // Access
                if (!tableExists(conn, "schema_migrations")) {
                    createSql =
                            "CREATE TABLE schema_migrations (" + "name VARCHAR(255) PRIMARY KEY, applied_at DATETIME)";
                }
                break;
        }
        if (createSql != null) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(createSql);
            }
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getTables(null, null, tableName, new String[] {"TABLE"})) {
            return rs.next();
        }
    }

    private Set<String> loadApplied(Connection conn) throws SQLException {
        Set<String> applied = new HashSet<>();
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT name FROM schema_migrations")) {
            while (rs.next()) applied.add(rs.getString("name"));
        }
        return applied;
    }

    private void recordApplied(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("INSERT INTO schema_migrations (name, applied_at) VALUES (?, ?)")) {
            ps.setString(1, name);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }
}
