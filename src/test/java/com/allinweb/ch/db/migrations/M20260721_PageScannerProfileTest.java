package com.allinweb.ch.db.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class M20260721_PageScannerProfileTest {

    @Test
    void createsAndIdempotentlySeedsTheSevenCanonicalProfiles() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            M20260721_PageScannerProfile migration = new M20260721_PageScannerProfile();
            migration.apply(connection, "TEXT");
            migration.apply(connection, "TEXT");

            List<String> keys = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "SELECT profile_key FROM page_scanner_profile ORDER BY sort_order, id")) {
                while (rows.next()) {
                    keys.add(rows.getString(1));
                }
            }
            assertEquals(
                    List.of(
                            "factory-default",
                            "all-interactive",
                            "select-options",
                            "inputs",
                            "clickables",
                            "outputs",
                            "data-ids"),
                    keys);

            try (Statement statement = connection.createStatement();
                    ResultSet factory = statement.executeQuery(
                            "SELECT label, search_terms, sort_order, is_protected"
                                    + " FROM page_scanner_profile WHERE profile_key = 'factory-default'")) {
                assertTrue(factory.next());
                assertEquals("All page scanner controls", factory.getString("label"));
                assertEquals("", factory.getString("search_terms"));
                assertEquals(10, factory.getInt("sort_order"));
                assertEquals(1, factory.getInt("is_protected"));
            }

            try (Statement statement = connection.createStatement();
                    ResultSet dataIds = statement.executeQuery(
                            "SELECT search_terms, sort_order FROM page_scanner_profile"
                                    + " WHERE profile_key = 'data-ids'")) {
                assertTrue(dataIds.next());
                assertEquals(
                        "attr:data-testid, attr:data-test-id, attr:test-id, attr:data-cy, attr:data-qa, attr:id, attr:name",
                        dataIds.getString("search_terms"));
                assertEquals(70, dataIds.getInt("sort_order"));
            }
        }
    }
}
