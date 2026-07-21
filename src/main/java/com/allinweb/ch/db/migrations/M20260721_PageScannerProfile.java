package com.allinweb.ch.db.migrations;

import com.allinweb.ch.db.MigrationRunner.Migration;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** Creates and seeds the persisted Page Scanner focus-profile catalog. */
@Slf4j
public final class M20260721_PageScannerProfile implements Migration {

    private static final String NAME = "2026-07-21__page_scanner_profile";
    private static final String TABLE = "page_scanner_profile";

    private static final List<SeedProfile> SEEDS = List.of(
            new SeedProfile("factory-default", "All page scanner controls", "", 10, true),
            new SeedProfile(
                    "all-interactive",
                    "All interactive controls",
                    "button, a, select, option, input, textarea, role, aria-haspopup, data-testid",
                    20,
                    false),
            new SeedProfile(
                    "select-options", "Select options", "select, option, combobox, listbox", 30, false),
            new SeedProfile(
                    "inputs", "Inputs and textareas", "input, textarea, textbox, contenteditable", 40, false),
            new SeedProfile(
                    "clickables",
                    "Buttons and clickables",
                    "button, link, menuitem, tab, treeitem, svg",
                    50,
                    false),
            new SeedProfile(
                    "outputs",
                    "Labels and outputs",
                    "label, span, p, div, h1, h2, h3, output",
                    60,
                    false),
            new SeedProfile(
                    "data-ids",
                    "Data/test id attributes",
                    "attr:data-testid, attr:data-test-id, attr:test-id, attr:data-cy, attr:data-qa, attr:id, attr:name",
                    70,
                    false));

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void apply(Connection connection, String dialect) throws SQLException {
        if (!tableExists(connection, TABLE)) {
            createTable(connection, dialect);
        }
        seedMissingProfiles(connection, dialect);
    }

    private void createTable(Connection connection, String dialect) throws SQLException {
        String ddl;
        switch (dialect) {
            case "Postgres":
                ddl = "CREATE TABLE " + TABLE + " ("
                        + "id SERIAL PRIMARY KEY,"
                        + "profile_key VARCHAR(64) NOT NULL,"
                        + "label VARCHAR(128) NOT NULL,"
                        + "search_terms TEXT NOT NULL,"
                        + "sort_order INTEGER NOT NULL DEFAULT 0,"
                        + "is_protected BOOLEAN NOT NULL DEFAULT FALSE,"
                        + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "CONSTRAINT uq_page_scanner_profile_key UNIQUE (profile_key),"
                        + "CONSTRAINT uq_page_scanner_profile_label UNIQUE (label)"
                        + ")";
                break;
            case "SQLServer":
                ddl = "CREATE TABLE " + TABLE + " ("
                        + "id INT IDENTITY(1,1) PRIMARY KEY,"
                        + "profile_key NVARCHAR(64) NOT NULL,"
                        + "label NVARCHAR(128) NOT NULL,"
                        + "search_terms NVARCHAR(MAX) NOT NULL,"
                        + "sort_order INT NOT NULL DEFAULT 0,"
                        + "is_protected BIT NOT NULL DEFAULT 0,"
                        + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "CONSTRAINT uq_page_scanner_profile_key UNIQUE (profile_key),"
                        + "CONSTRAINT uq_page_scanner_profile_label UNIQUE (label)"
                        + ")";
                break;
            case "TEXT":
                ddl = "CREATE TABLE " + TABLE + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "profile_key TEXT NOT NULL UNIQUE,"
                        + "label TEXT NOT NULL UNIQUE,"
                        + "search_terms TEXT NOT NULL,"
                        + "sort_order INTEGER NOT NULL DEFAULT 0,"
                        + "is_protected INTEGER NOT NULL DEFAULT 0,"
                        + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")";
                break;
            default: // Access / UCanAccess
                ddl = "CREATE TABLE " + TABLE + " ("
                        + "id AUTOINCREMENT PRIMARY KEY,"
                        + "profile_key VARCHAR(64) NOT NULL,"
                        + "label VARCHAR(128) NOT NULL,"
                        + "search_terms MEMO NOT NULL,"
                        + "sort_order LONG NOT NULL,"
                        + "is_protected YESNO NOT NULL,"
                        + "created_at DATETIME NOT NULL,"
                        + "updated_at DATETIME NOT NULL,"
                        + "CONSTRAINT uq_page_scanner_profile_key UNIQUE (profile_key),"
                        + "CONSTRAINT uq_page_scanner_profile_label UNIQUE (label)"
                        + ")";
                break;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        }
        log.info("{} - created {} for dialect {}", NAME, TABLE, dialect);
    }

    private void seedMissingProfiles(Connection connection, String dialect) throws SQLException {
        String select = "SELECT id FROM " + TABLE + " WHERE profile_key = ?";
        String insert = "INSERT INTO " + TABLE
                + " (profile_key, label, search_terms, sort_order, is_protected, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        Timestamp now = new Timestamp(System.currentTimeMillis());
        for (SeedProfile seed : SEEDS) {
            if (exists(connection, select, seed.key())) {
                continue;
            }
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setString(1, seed.key());
                statement.setString(2, seed.label());
                statement.setString(3, seed.searchTerms());
                statement.setInt(4, seed.sortOrder());
                if ("TEXT".equals(dialect)) {
                    statement.setInt(5, seed.protectedProfile() ? 1 : 0);
                } else {
                    statement.setBoolean(5, seed.protectedProfile());
                }
                statement.setTimestamp(6, now);
                statement.setTimestamp(7, now);
                statement.executeUpdate();
            }
        }
    }

    private boolean exists(Connection connection, String sql, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String candidate : new String[] {tableName, tableName.toLowerCase(), tableName.toUpperCase()}) {
            try (ResultSet tables = metadata.getTables(null, null, candidate, new String[] {"TABLE"})) {
                if (tables.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    private record SeedProfile(
            String key, String label, String searchTerms, int sortOrder, boolean protectedProfile) {}
}
