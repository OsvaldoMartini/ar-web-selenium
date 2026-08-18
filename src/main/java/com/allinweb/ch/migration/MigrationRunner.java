package com.allinweb.ch.migration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ordered, checksummed, transactional database migration runner.
 *
 * <p>Replaces: ARControlPanel.databaseControl() existence-check + initializeMainDatabase* dispatch,
 * plus all PerformDataBase schema-mutation methods (migrationScriptsv2_1f, updateDatabaseSchema,
 * updateColumns, addColumnIfNotExists, disableForeignKeyConstraints, dropPostGresSequences).
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>Discovers classpath resources under db/migration/&lt;dialect&gt;/V*.sql sorted by version.
 *   <li>Creates schema_migrations table if missing (V000 bootstrap).
 *   <li>Each migration runs in its own transaction; failure rolls back that migration only.
 *   <li>Checksum of an applied migration is verified on every startup; drift fails fast.
 * </ul>
 *
 * <p>Not thread-safe — call at startup before any concurrent DB work.
 */
public final class MigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    public enum Dialect {
        POSTGRES("postgres"),
        SQLITE("sqlite"),
        ACCESS("access"),
        SQLSERVER("sqlserver");

        final String folder;

        Dialect(String folder) {
            this.folder = folder;
        }
    }

    public record Migration(String version, String name, String sql, String checksum) {}

    private final Connection conn;
    private final Dialect dialect;
    private final Clock clock;

    public MigrationRunner(Connection conn, Dialect dialect) {
        this(conn, dialect, Clock.systemUTC());
    }

    public MigrationRunner(Connection conn, Dialect dialect, Clock clock) {
        this.conn = Objects.requireNonNull(conn);
        this.dialect = Objects.requireNonNull(dialect);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Main entry point. Runs all pending migrations for the current dialect.
     *
     * @throws SQLException if any migration fails; the database state is guaranteed to be the last
     *     successful version (previous migrations commit independently).
     */
    public void run() throws SQLException, IOException {
        ensureSchemaMigrationsTable();
        Set<String> applied = loadAppliedVersions();
        List<Migration> all = discoverMigrations();
        all.sort(Comparator.comparing(Migration::version));

        for (Migration m : all) {
            if (applied.contains(m.version())) {
                verifyChecksum(m);
                continue;
            }
            apply(m);
        }
        log.info("Migrations complete. Applied={}, total={}", applied.size(), all.size());
    }

    // ---------------------------------------------------------------------
    // Discovery (classpath)
    // ---------------------------------------------------------------------

    private List<Migration> discoverMigrations() throws IOException {
        String root = "db/migration/" + dialect.folder;
        List<Migration> result = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        // Case 1: running from a filesystem (IDE / mvn exec)
        URL dirUrl = cl.getResource(root);
        if (dirUrl == null) {
            throw new IOException("Migration folder not found on classpath: " + root);
        }

        if ("file".equals(dirUrl.getProtocol())) {
            try {
                Path dir = Paths.get(dirUrl.toURI());
                try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "V*.sql")) {
                    for (Path p : files) {
                        result.add(loadMigration(p.getFileName().toString(), Files.readString(p)));
                    }
                }
            } catch (Exception e) {
                throw new IOException("Failed to list migrations from " + dirUrl, e);
            }
        } else {
            // Case 2: running from inside a jar — use ModuleLayer / JarFile walk.
            // Keep a manifest file (MANIFEST.txt) listing migration filenames for deterministic jar discovery.
            try (InputStream manifest = cl.getResourceAsStream(root + "/MANIFEST.txt")) {
                if (manifest == null) {
                    throw new IOException("Missing " + root + "/MANIFEST.txt inside jar");
                }
                String list = new String(manifest.readAllBytes(), StandardCharsets.UTF_8);
                for (String line : list.split("\\R")) {
                    String name = line.trim();
                    if (name.isEmpty() || !name.startsWith("V")) continue;
                    try (InputStream is = cl.getResourceAsStream(root + "/" + name)) {
                        if (is == null) throw new IOException("Missing migration resource: " + name);
                        result.add(loadMigration(name, new String(is.readAllBytes(), StandardCharsets.UTF_8)));
                    }
                }
            }
        }
        return result;
    }

    private Migration loadMigration(String fileName, String sql) {
        // Expected "V<version>__<name>.sql"
        if (!fileName.startsWith("V") || !fileName.endsWith(".sql") || !fileName.contains("__")) {
            throw new IllegalStateException("Invalid migration file name: " + fileName);
        }
        String base = fileName.substring(1, fileName.length() - 4); // strip V and .sql
        int sep = base.indexOf("__");
        String version = base.substring(0, sep);
        String name = base.substring(sep + 2).replace('_', ' ');
        return new Migration(version, name, sql, sha256(sql));
    }

    // ---------------------------------------------------------------------
    // schema_migrations bookkeeping
    // ---------------------------------------------------------------------

    private void ensureSchemaMigrationsTable() throws SQLException {
        String ddl;
        switch (dialect) {
            case POSTGRES -> ddl = "CREATE TABLE IF NOT EXISTS schema_migrations ("
                    + "version VARCHAR(20) PRIMARY KEY, "
                    + "name VARCHAR(200) NOT NULL, "
                    + "checksum CHAR(64) NOT NULL, "
                    + "applied_at TIMESTAMP NOT NULL, "
                    + "success BOOLEAN NOT NULL)";
            case SQLITE -> ddl = "CREATE TABLE IF NOT EXISTS schema_migrations ("
                    + "version TEXT PRIMARY KEY, name TEXT NOT NULL, "
                    + "checksum TEXT NOT NULL, applied_at TEXT NOT NULL, "
                    + "success INTEGER NOT NULL)";
            case ACCESS -> ddl =
                    // UCanAccess does not support IF NOT EXISTS — use defensive check
                    null;
            default -> ddl = "CREATE TABLE schema_migrations ("
                    + "version VARCHAR(20) PRIMARY KEY, name VARCHAR(200) NOT NULL, "
                    + "checksum CHAR(64) NOT NULL, applied_at DATETIME NOT NULL, "
                    + "success BIT NOT NULL)";
        }
        if (dialect == Dialect.ACCESS && !tableExists("schema_migrations")) {
            ddl = "CREATE TABLE schema_migrations ("
                    + "version TEXT(20) PRIMARY KEY, "
                    + "name TEXT(200) NOT NULL, "
                    + "checksum TEXT(64) NOT NULL, "
                    + "applied_at DATETIME NOT NULL, "
                    + "success YESNO NOT NULL)";
        }
        if (ddl != null && (dialect != Dialect.ACCESS || !tableExists("schema_migrations"))) {
            try (Statement s = conn.createStatement()) {
                s.execute(ddl);
            }
        }
    }

    private boolean tableExists(String name) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, name, null)) {
            return rs.next();
        }
    }

    private Set<String> loadAppliedVersions() throws SQLException {
        Set<String> out = new HashSet<>();
        try (Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery("SELECT version FROM schema_migrations WHERE success = "
                        + (dialect == Dialect.SQLITE ? "1" : "TRUE"))) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    private void verifyChecksum(Migration m) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT checksum FROM schema_migrations WHERE version = ?")) {
            ps.setString(1, m.version());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;
                String stored = rs.getString(1);
                if (!stored.equals(m.checksum())) {
                    throw new SQLException("Checksum drift on migration "
                            + m.version()
                            + " ("
                            + m.name()
                            + "). stored="
                            + stored
                            + " computed="
                            + m.checksum()
                            + ". Refusing to start.");
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Apply
    // ---------------------------------------------------------------------

    private void apply(Migration m) throws SQLException {
        log.info("Applying migration {} — {}", m.version(), m.name());
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement s = conn.createStatement()) {
            for (String stmt : splitStatements(m.sql())) {
                if (stmt.isBlank()) continue;
                s.execute(stmt);
            }
            recordApplied(m, true);
            conn.commit();
            log.info("Migration {} applied successfully.", m.version());
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignore) {
            }
            // Best-effort record failure in a separate transaction
            try {
                recordApplied(m, false);
                conn.commit();
            } catch (SQLException ignore) {
            }
            throw new SQLException("Migration " + m.version() + " (" + m.name() + ") failed: " + e.getMessage(), e);
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    private void recordApplied(Migration m, boolean success) throws SQLException {
        String sql =
                "INSERT INTO schema_migrations (version, name, checksum, applied_at, success) VALUES (?, ?, ?, ?, ?)";
        // UPSERT semantics — replace on failure-then-retry
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM schema_migrations WHERE version = ?")) {
            del.setString(1, m.version());
            del.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, m.version());
            ps.setString(2, m.name());
            ps.setString(3, m.checksum());
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now(clock)));
            if (dialect == Dialect.SQLITE) {
                ps.setInt(5, success ? 1 : 0);
            } else {
                ps.setBoolean(5, success);
            }
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------

    /** Split a SQL script on semicolons that live OUTSIDE string literals and comments. */
    static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false, inDouble = false, inLineCmt = false, inBlockCmt = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char n = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (inLineCmt) {
                if (c == '\n') inLineCmt = false;
                cur.append(c);
                continue;
            }
            if (inBlockCmt) {
                if (c == '*' && n == '/') {
                    inBlockCmt = false;
                    cur.append(c).append(n);
                    i++;
                } else {
                    cur.append(c);
                }
                continue;
            }
            if (inSingle) {
                cur.append(c);
                if (c == '\'') {
                    if (n == '\'') {
                        cur.append(n);
                        i++;
                    } else {
                        inSingle = false;
                    }
                }
                continue;
            }
            if (inDouble) {
                cur.append(c);
                if (c == '"') inDouble = false;
                continue;
            }
            if (c == '-' && n == '-') {
                inLineCmt = true;
                cur.append(c);
                continue;
            }
            if (c == '/' && n == '*') {
                inBlockCmt = true;
                cur.append(c);
                continue;
            }
            if (c == '\'') {
                inSingle = true;
                cur.append(c);
                continue;
            }
            if (c == '"') {
                inDouble = true;
                cur.append(c);
                continue;
            }
            if (c == ';') {
                out.add(cur.toString().trim());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        if (!cur.toString().isBlank()) out.add(cur.toString().trim());
        return out;
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
