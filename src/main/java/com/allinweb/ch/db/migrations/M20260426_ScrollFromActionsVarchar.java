package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Move the legacy {@code S} (scroll) and {@code E} (enter) flag tokens out of the
 * {@code actions} column and into {@code force_coordinates}.
 *
 * <p>Before this migration, actions strings for input rows were formatted like
 * {@code "I:S:Password"} or {@code "I:E:username"} — with the {@code :S:} /
 * {@code :E:} token acting as a flag mixed into the action payload. The
 * {@code force_coordinates} column now (since migration 2026-04-25) owns all
 * F/E/T/N/S post-input flags, so the legacy tokens are redundant.</p>
 *
 * <p>This migration rewrites existing rows so that:
 * <pre>
 *   "I:S:X"    -> actions "I:X",   force_coordinates += "S"
 *   "I:E:X"    -> actions "I:X",   force_coordinates += "E"
 *   "I:S:E:X"  -> actions "I:X",   force_coordinates += "SE"
 *   "I:E:S:X"  -> actions "I:X",   force_coordinates += "ES"
 *   "I:S"      -> actions "I",     force_coordinates += "S"
 *   (plain "I:X" and "I" are left alone)
 * </pre></p>
 *
 * <p>Runs autonomously: writes are committed row by row (no batch so any driver
 * that silently drops a batch on a single-row failure can't mask the problem),
 * autoCommit is forced on before the updates, and a post-check verifies no
 * legacy {@code :S:} or {@code :E:} tokens remain — if any do, the migration
 * fails loudly rather than recording itself as applied.</p>
 */
@Slf4j
public class M20260426_ScrollFromActionsVarchar implements Migration {

    private static final String NAME = "2026-04-26__scroll_from_actions";
    private static final String[] TABLES = {"instruction", "component_instruction"};

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection conn, String dialect) throws SQLException {
        log.info("{} — applying on dialect={}", NAME, dialect);
        runRewriteAndVerify(conn);
    }

    /**
     * Idempotent safety net: callable from {@code PerformInitializer.initialize()}
     * on every startup, regardless of whether this migration is recorded as
     * applied in {@code schema_migrations}. Protects against the "migration row
     * written but data rewrite was silently dropped" failure mode (which was
     * possible under the prior {@code executeBatch} implementation on some JDBC
     * drivers). No-op when the tables are already clean.
     */
    public static void selfHealIfLegacyRemains(Connection conn) {
        try {
            new M20260426_ScrollFromActionsVarchar().runRewriteAndVerify(conn);
        } catch (SQLException e) {
            log.error("{} — self-heal pass failed: {}", NAME, e.getMessage(), e);
        }
    }

    private void runRewriteAndVerify(Connection conn) throws SQLException {
        // Force autoCommit so each executeUpdate is durable the moment it returns.
        // A few JDBC drivers (and in-process pools) leave autoCommit=false behind
        // from a prior transactional section — that can make UPDATEs look like no-ops.
        boolean originalAutoCommit = conn.getAutoCommit();
        if (!originalAutoCommit) {
            conn.setAutoCommit(true);
        }

        try {
            for (String table : TABLES) {
                rewriteTable(conn, table);
                assertNoLegacyRemains(conn, table);
            }
        } finally {
            if (!originalAutoCommit) {
                conn.setAutoCommit(false);
            }
        }
    }

    private void rewriteTable(Connection conn, String table) throws SQLException {
        // Fetch rows whose actions need rewriting. LIKE covers "I:S:X"/"I:E:X"
        // (including combined I:S:E:X and I:E:S:X — the rewrite() helper splits
        // all tokens), and the exact-match arms cover plain "I:S"/"I:E".
        List<Row> rows = new ArrayList<>();
        String select = "SELECT id, actions, force_coordinates FROM " + table
                + " WHERE actions LIKE 'I:S:%' OR actions LIKE 'I:E:%'"
                + " OR actions = 'I:S' OR actions = 'I:E'";
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(select)) {
            while (rs.next()) {
                rows.add(new Row(rs.getInt("id"), rs.getString("actions"), rs.getString("force_coordinates")));
            }
        }

        log.info("{} — {}: found {} candidate row(s)", NAME, table, rows.size());
        if (rows.isEmpty()) return;

        String update = "UPDATE " + table + " SET actions = ?, force_coordinates = ? WHERE id = ?";
        int written = 0;
        try (PreparedStatement ps = conn.prepareStatement(update)) {
            for (Row r : rows) {
                Rewritten rw = rewrite(r.actions, r.forceCoordinates);
                if (rw == null) {
                    // Matched the WHERE but rewrite() saw no S/E tokens — should not happen,
                    // but don't crash on it. Log and move on.
                    log.warn(
                            "{} — {}: id={} actions='{}' matched WHERE but rewrite produced no change",
                            NAME,
                            table,
                            r.id,
                            r.actions);
                    continue;
                }
                ps.setString(1, rw.actions);
                ps.setString(2, rw.forceCoordinates);
                ps.setInt(3, r.id);
                int n = ps.executeUpdate();
                if (n != 1) {
                    throw new SQLException(
                            NAME + " — " + table + ": id=" + r.id + " UPDATE affected " + n + " row(s), expected 1");
                }
                log.info(
                        "{} — {}: id={} actions '{}' -> '{}', force_coordinates '{}' -> '{}'",
                        NAME,
                        table,
                        r.id,
                        r.actions,
                        rw.actions,
                        r.forceCoordinates == null ? "" : r.forceCoordinates,
                        rw.forceCoordinates);
                written++;
            }
        }
        log.info("{} — {}: rewrote {} row(s)", NAME, table, written);
    }

    /**
     * Fails loudly if any legacy-shaped actions value still exists after the
     * rewrite pass. This ensures the migration can only record itself as applied
     * once the target state has actually been reached.
     */
    private void assertNoLegacyRemains(Connection conn, String table) throws SQLException {
        String check = "SELECT COUNT(*) FROM " + table
                + " WHERE actions LIKE 'I:S:%' OR actions LIKE 'I:E:%'"
                + " OR actions = 'I:S' OR actions = 'I:E'";
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(check)) {
            if (rs.next()) {
                int remaining = rs.getInt(1);
                if (remaining > 0) {
                    throw new SQLException(NAME + " — " + table + ": " + remaining
                            + " row(s) still contain legacy ':S:' / ':E:' tokens after rewrite");
                }
            }
        }
    }

    /**
     * Split {@code actions} on {@code ':'}, strip any {@code S}/{@code E} tokens
     * (anywhere past the first), and return the cleaned actions string together
     * with the flags merged into the supplied {@code force_coordinates} value.
     * Returns {@code null} if the input had no S/E flag tokens — caller can skip.
     */
    static Rewritten rewrite(String actions, String forceCoordinates) {
        if (actions == null || actions.isEmpty()) return null;
        String[] parts = actions.split(":");
        if (parts.length < 2) return null;

        StringBuilder cleaned = new StringBuilder(parts[0]);
        StringBuilder addFlags = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            String tok = parts[i];
            if ("S".equalsIgnoreCase(tok)) {
                if (addFlags.indexOf("S") < 0) addFlags.append('S');
            } else if ("E".equalsIgnoreCase(tok)) {
                if (addFlags.indexOf("E") < 0) addFlags.append('E');
            } else {
                cleaned.append(':').append(tok);
            }
        }
        if (addFlags.length() == 0) return null; // nothing to move

        String newFc = forceCoordinates == null ? "" : forceCoordinates;
        StringBuilder merged = new StringBuilder(newFc);
        for (int i = 0; i < addFlags.length(); i++) {
            char c = addFlags.charAt(i);
            if (merged.indexOf(String.valueOf(c)) < 0) merged.append(c);
        }

        return new Rewritten(cleaned.toString(), merged.toString());
    }

    private static final class Row {
        final int id;
        final String actions;
        final String forceCoordinates;

        Row(int id, String actions, String forceCoordinates) {
            this.id = id;
            this.actions = actions;
            this.forceCoordinates = forceCoordinates;
        }
    }

    static final class Rewritten {
        final String actions;
        final String forceCoordinates;

        Rewritten(String actions, String forceCoordinates) {
            this.actions = actions;
            this.forceCoordinates = forceCoordinates;
        }
    }
}
