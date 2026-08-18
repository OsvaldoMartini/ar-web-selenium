package com.allinweb.ch.facade;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/** Bot Job-scoped durable preferences for the Variables workspace. */
public final class VariablesWorkspacePreferenceStore {
    public static final String VARIABLE_MODE = "variables.resolve.variableMode";
    public static final String EXCEL_SYNTHETIC_CONTEXT = "excel.synthetic.context";
    private static final Object SCHEMA_LOCK = new Object();

    public String loadVariableMode(int homeBankingId, int botJobId) throws SQLException {
        try (Connection connection = PerformDataBase.getInstance().getConnection()) {
            ensureTable(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT preference_value FROM bot_job_workspace_preference"
                            + " WHERE organization_id=? AND home_banking_id=?"
                            + " AND bot_job_id=? AND preference_key=?")) {
                statement.setInt(1, homeBankingId);
                statement.setInt(2, homeBankingId);
                statement.setInt(3, botJobId);
                statement.setString(4, VARIABLE_MODE);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        return "SAME".equalsIgnoreCase(rows.getString(1))
                                ? "SAME"
                                : "DISTINCT";
                    }
                }
            }
        }
        return "DISTINCT";
    }

    public String loadSyntheticContext(int homeBankingId, int botJobId) throws SQLException {
        try (Connection connection = PerformDataBase.getInstance().getConnection()) {
            ensureTable(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT preference_value FROM bot_job_workspace_preference"
                            + " WHERE organization_id=? AND home_banking_id=?"
                            + " AND bot_job_id=? AND preference_key=?")) {
                statement.setInt(1, homeBankingId);
                statement.setInt(2, homeBankingId);
                statement.setInt(3, botJobId);
                statement.setString(4, EXCEL_SYNTHETIC_CONTEXT);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next() && !rows.getString(1).isBlank()) return rows.getString(1);
                }
            }
        }
        return "Bank Account";
    }

    public void saveSyntheticContext(
            int homeBankingId, int botJobId, String context, String metadataJson) throws SQLException {
        String normalized = context == null || context.isBlank() ? "Bank Account" : context.trim();
        String metadata = metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson;
        try (Connection connection = PerformDataBase.getInstance().getConnection()) {
            ensureTable(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO bot_job_workspace_preference"
                            + " (organization_id,home_banking_id,bot_job_id,preference_key,"
                            + "preference_value,metadata_json,created_at,updated_at)"
                            + " VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"
                            + " ON CONFLICT(organization_id,home_banking_id,bot_job_id,preference_key)"
                            + " DO UPDATE SET preference_value=excluded.preference_value,"
                            + "metadata_json=excluded.metadata_json,updated_at=CURRENT_TIMESTAMP")) {
                statement.setInt(1, homeBankingId);
                statement.setInt(2, homeBankingId);
                statement.setInt(3, botJobId);
                statement.setString(4, EXCEL_SYNTHETIC_CONTEXT);
                statement.setString(5, normalized);
                statement.setString(6, metadata);
                statement.executeUpdate();
            }
        }
    }

    public void saveVariableMode(
            int homeBankingId,
            int botJobId,
            String mode,
            String metadataJson)
            throws SQLException {
        String normalized = "DISTINCT".equalsIgnoreCase(mode) ? "DISTINCT" : "SAME";
        String metadata = metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson;
        try (Connection connection = PerformDataBase.getInstance().getConnection()) {
            ensureTable(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO bot_job_workspace_preference"
                            + " (organization_id,home_banking_id,bot_job_id,preference_key,"
                            + "preference_value,metadata_json,created_at,updated_at)"
                            + " VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"
                            + " ON CONFLICT(organization_id,home_banking_id,bot_job_id,preference_key)"
                            + " DO UPDATE SET preference_value=excluded.preference_value,"
                            + "metadata_json=excluded.metadata_json,updated_at=CURRENT_TIMESTAMP")) {
                statement.setInt(1, homeBankingId);
                statement.setInt(2, homeBankingId);
                statement.setInt(3, botJobId);
                statement.setString(4, VARIABLE_MODE);
                statement.setString(5, normalized);
                statement.setString(6, metadata);
                statement.executeUpdate();
            }
        }
    }

    static void ensureTable(Connection connection) throws SQLException {
        synchronized (SCHEMA_LOCK) {
            createCurrentTable(connection, "bot_job_workspace_preference");
            Set<String> columns = tableColumns(connection, "bot_job_workspace_preference");
            if (columns.contains("id")
                    && columns.contains("organization_id")
                    && columns.contains("metadata_json")
                    && columns.contains("created_at")) {
                return;
            }
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP TABLE IF EXISTS bot_job_workspace_preference_next");
                createCurrentTable(connection, "bot_job_workspace_preference_next");
                statement.executeUpdate(
                        "INSERT INTO bot_job_workspace_preference_next"
                                + " (organization_id,home_banking_id,bot_job_id,preference_key,"
                                + "preference_value,metadata_json,created_at,updated_at)"
                                + " SELECT home_banking_id,home_banking_id,bot_job_id,preference_key,"
                                + "preference_value,'{}',COALESCE(updated_at,CURRENT_TIMESTAMP),"
                                + "COALESCE(updated_at,CURRENT_TIMESTAMP)"
                                + " FROM bot_job_workspace_preference");
                statement.executeUpdate("DROP TABLE bot_job_workspace_preference");
                statement.executeUpdate(
                        "ALTER TABLE bot_job_workspace_preference_next"
                                + " RENAME TO bot_job_workspace_preference");
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private static void createCurrentTable(Connection connection, String tableName)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "organization_id INTEGER NOT NULL,"
                            + "home_banking_id INTEGER NOT NULL,"
                            + "bot_job_id INTEGER NOT NULL,"
                            + "preference_key TEXT NOT NULL,"
                            + "preference_value TEXT NOT NULL,"
                            + "metadata_json TEXT NOT NULL DEFAULT '{}',"
                            + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                            + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                            + "UNIQUE(organization_id,home_banking_id,bot_job_id,preference_key),"
                            + "FOREIGN KEY(organization_id) REFERENCES home_banking(id) ON DELETE CASCADE,"
                            + "FOREIGN KEY(home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE,"
                            + "FOREIGN KEY(bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE)"
            );
        }
    }

    private static Set<String> tableColumns(Connection connection, String tableName)
            throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rows.next()) columns.add(rows.getString("name"));
        }
        return columns;
    }
}
