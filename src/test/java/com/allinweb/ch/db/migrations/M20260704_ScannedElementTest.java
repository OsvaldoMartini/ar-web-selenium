package com.allinweb.ch.db.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Validates the {@code scanned_element} DDL against in-memory SQLite (the active TEXT dialect),
 * that the columns/uniqueness needed for the source-of-truth registry exist, and that the
 * migration is idempotent (its tableExists guard).
 */
class M20260704_ScannedElementTest {

    private static Connection sqlite() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @Test
    void createsTableWithRegistryColumns() throws Exception {
        try (Connection conn = sqlite()) {
            M20260704_ScannedElement migration = new M20260704_ScannedElement();
            migration.apply(conn, "TEXT");
            // Idempotent: second apply must be a no-op, not an error.
            migration.apply(conn, "TEXT");

            Set<String> cols = new HashSet<>();
            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery("PRAGMA table_info(scanned_element)")) {
                while (rs.next()) {
                    cols.add(rs.getString("name").toLowerCase());
                }
            }
            assertTrue(cols.contains("home_banking_id"), "must be scoped by organization");
            assertTrue(cols.contains("bot_job_id"), "must be scoped by bot job");
            assertTrue(cols.contains("element_hash"), "identity hash for disambiguation");
            assertTrue(cols.contains("last_scanned_at"), "last-scanned control date");
            assertTrue(cols.contains("ocr_text"), "OCR correction field");
            assertTrue(cols.contains("some_text") && cols.contains("defined_name"), "name fields");
            assertTrue(cols.contains("x_path") && cols.contains("css_selector"), "locator fields");
        }
    }

    @Test
    void enforcesUniquenessPerScopeAndHashButAllowsSameNameDifferentHash() throws Exception {
        try (Connection conn = sqlite()) {
            new M20260704_ScannedElement().apply(conn, "TEXT");

            // Two elements with the SAME defined_name but DIFFERENT element_hash must coexist.
            insert(conn, 2, 5, "hashA", "Rifiuta tutti");
            insert(conn, 2, 5, "hashB", "Rifiuta tutti");
            assertEquals(2, count(conn), "same name / different hash must be distinct rows");

            // Same scope + same hash must collide (upsert territory) — INSERT should throw.
            boolean threw = false;
            try {
                insert(conn, 2, 5, "hashA", "Rifiuta tutti");
            } catch (SQLException e) {
                threw = true;
            }
            assertTrue(threw, "duplicate (home_banking_id, bot_job_id, element_hash) must violate uniqueness");
        }
    }

    private static void insert(Connection conn, int hb, int bot, String hash, String name) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO scanned_element "
                    + "(home_banking_id, bot_job_id, element_hash, defined_name, scan_count) VALUES ("
                    + hb + "," + bot + ",'" + hash + "','" + name + "',1)");
        }
    }

    private static int count(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM scanned_element")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
