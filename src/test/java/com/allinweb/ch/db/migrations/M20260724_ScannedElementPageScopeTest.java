package com.allinweb.ch.db.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.db.ScannedElementRepository;
import com.allinweb.ch.db.ScannedPageIdentity;
import com.allinweb.ch.model.ElementDTO;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SQLite coverage for the append-only migration from the legacy scanner repository schema. */
class M20260724_ScannedElementPageScopeTest {

    private static final String ACCOUNTS_PAGE = "https://bank.example/accounts";
    private static final String PAYMENTS_PAGE = "https://bank.example/payments";
    private static final String LOCATOR_XPATH = "//button[@test-id='continue']";
    private static final String LOCATOR_CSS = "button[test-id='continue']";
    private static final String LOCATOR_ID = "continue";

    private static Connection legacyDatabase() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        new M20260704_ScannedElement().apply(connection, "TEXT");
        return connection;
    }

    @Test
    void backfillsPageIdentityPreservesRowsAndIsIdempotent() throws Exception {
        try (Connection connection = legacyDatabase()) {
            insertLegacyRow(connection, 41, ACCOUNTS_PAGE, "legacy-accounts", "//custom/accounts");
            insertLegacyRow(connection, 42, PAYMENTS_PAGE, "legacy-payments", "//custom/payments");

            M20260724_ScannedElementPageScope migration =
                    new M20260724_ScannedElementPageScope();
            migration.apply(connection, "TEXT");
            migration.apply(connection, "TEXT");

            assertEquals(2, count(connection));

            ElementDTO locator = locator();
            ScannedPageIdentity accounts = ScannedPageIdentity.fromStoredUrl(ACCOUNTS_PAGE);
            ScannedPageIdentity payments = ScannedPageIdentity.fromStoredUrl(PAYMENTS_PAGE);
            assertNotEquals(accounts.pageKey(), payments.pageKey());

            assertMigratedRow(
                    connection,
                    41,
                    ACCOUNTS_PAGE,
                    accounts.pageKey(),
                    ScannedElementRepository.pageScopedHash(accounts.pageKey(), locator),
                    "//custom/accounts");
            assertMigratedRow(
                    connection,
                    42,
                    PAYMENTS_PAGE,
                    payments.pageKey(),
                    ScannedElementRepository.pageScopedHash(payments.pageKey(), locator),
                    "//custom/payments");

            assertTrue(accounts.pageKey().matches("url-v1:[0-9a-f]{64}"));
            assertTrue(payments.pageKey().matches("url-v1:[0-9a-f]{64}"));
        }
    }

    @Test
    void createsThePageScopedUniqueAndNameIndexes() throws Exception {
        try (Connection connection = legacyDatabase()) {
            insertLegacyRow(connection, 1, ACCOUNTS_PAGE, "legacy-accounts", null);
            new M20260724_ScannedElementPageScope().apply(connection, "TEXT");

            assertEquals(
                    List.of("home_banking_id", "bot_job_id", "page_key", "element_hash"),
                    indexColumns(connection, "uq_scanned_scope_page_hash"));
            assertTrue(indexIsUnique(connection, "uq_scanned_scope_page_hash"));

            assertEquals(
                    List.of("home_banking_id", "bot_job_id", "page_key", "defined_name"),
                    indexColumns(connection, "idx_scanned_page_name"));
        }
    }

    private static ElementDTO locator() {
        ElementDTO element = new ElementDTO();
        element.setXPath(LOCATOR_XPATH);
        element.setCssSelector(LOCATOR_CSS);
        element.setAttribId(LOCATOR_ID);
        return element;
    }

    private static void insertLegacyRow(
            Connection connection, long id, String pageUrl, String legacyHash, String customXPath)
            throws SQLException {
        String sql = "INSERT INTO scanned_element ("
                + "id, home_banking_id, bot_job_id, home_url_id, page_url, element_hash,"
                + "tag_name, type_element, defined_name, client_named, some_text, x_path,"
                + "custom_x_path, css_selector, attrib_id, attrib_name, coordinates, iframe_xpath,"
                + "shadow_host, shadow_root, attribute_data, ocr_text, ocr_match_quality,"
                + "ocr_confidence, scan_count, first_scanned_at, last_scanned_at"
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            statement.setLong(parameter++, id);
            statement.setInt(parameter++, 2);
            statement.setInt(parameter++, 5);
            statement.setInt(parameter++, 3);
            statement.setString(parameter++, pageUrl);
            statement.setString(parameter++, legacyHash);
            statement.setString(parameter++, "button");
            statement.setString(parameter++, "button");
            statement.setString(parameter++, "continue_button");
            statement.setString(parameter++, "Continue");
            statement.setString(parameter++, "Continue");
            statement.setString(parameter++, LOCATOR_XPATH);
            statement.setString(parameter++, customXPath);
            statement.setString(parameter++, LOCATOR_CSS);
            statement.setString(parameter++, LOCATOR_ID);
            statement.setString(parameter++, "continue");
            statement.setString(parameter++, "100,200");
            statement.setString(parameter++, null);
            statement.setString(parameter++, null);
            statement.setString(parameter++, null);
            statement.setString(parameter++, "{\"test-id\":\"continue\"}");
            statement.setString(parameter++, "Continue");
            statement.setString(parameter++, "EXACT");
            statement.setDouble(parameter++, 0.98d);
            statement.setInt(parameter++, 7);
            statement.setString(parameter++, "2026-07-20 10:00:00");
            statement.setString(parameter, "2026-07-23 11:30:00");
            statement.executeUpdate();
        }
    }

    private static void assertMigratedRow(
            Connection connection,
            long id,
            String expectedPageUrl,
            String expectedPageKey,
            String expectedElementHash,
            String expectedCustomXPath)
            throws SQLException {
        String sql = "SELECT * FROM scanned_element WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                assertEquals(expectedPageUrl, row.getString("page_url"));
                assertEquals(expectedPageKey, row.getString("page_key"));
                assertEquals(expectedElementHash, row.getString("element_hash"));
                assertEquals(expectedCustomXPath, row.getString("custom_x_path"));
                assertEquals("continue_button", row.getString("defined_name"));
                assertEquals("Continue", row.getString("client_named"));
                assertEquals("Continue", row.getString("ocr_text"));
                assertEquals("EXACT", row.getString("ocr_match_quality"));
                assertEquals(0.98d, row.getDouble("ocr_confidence"), 0.0001d);
                assertEquals(7, row.getInt("scan_count"));
                assertEquals("2026-07-20 10:00:00", row.getString("first_scanned_at"));
                assertEquals("2026-07-23 11:30:00", row.getString("last_scanned_at"));
            }
        }
    }

    private static int count(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("SELECT COUNT(*) FROM scanned_element")) {
            row.next();
            return row.getInt(1);
        }
    }

    private static boolean indexIsUnique(Connection connection, String indexName)
            throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet indexes = statement.executeQuery("PRAGMA index_list(scanned_element)")) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("name"))) {
                    return indexes.getInt("unique") == 1;
                }
            }
        }
        return false;
    }

    private static List<String> indexColumns(Connection connection, String indexName)
            throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet index = statement.executeQuery("PRAGMA index_info('" + indexName + "')")) {
            while (index.next()) {
                columns.add(index.getString("name"));
            }
        }
        return columns;
    }
}
