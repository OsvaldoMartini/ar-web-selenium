package com.allinweb.ch.facade;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Bot Job-scoped durable preferences for the Variables workspace. */
public final class VariablesWorkspacePreferenceStore {
    public static final String VARIABLE_MODE = "variables.resolve.variableMode";

    public String loadVariableMode(int homeBankingId, int botJobId) throws SQLException {
        try (Connection connection = PerformDataBase.getInstance().getConnection()) {
            ensureTable(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT preference_value FROM bot_job_workspace_preference"
                            + " WHERE home_banking_id=? AND bot_job_id=? AND preference_key=?")) {
                statement.setInt(1, homeBankingId);
                statement.setInt(2, botJobId);
                statement.setString(3, VARIABLE_MODE);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next() && "DISTINCT".equalsIgnoreCase(rows.getString(1))) {
                        return "DISTINCT";
                    }
                }
            }
        }
        return "SAME";
    }

    public void saveVariableMode(int homeBankingId, int botJobId, String mode)
            throws SQLException {
        String normalized = "DISTINCT".equalsIgnoreCase(mode) ? "DISTINCT" : "SAME";
        try (Connection connection = PerformDataBase.getInstance().getConnection()) {
            ensureTable(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO bot_job_workspace_preference"
                            + " (home_banking_id,bot_job_id,preference_key,preference_value,updated_at)"
                            + " VALUES (?,?,?,?,CURRENT_TIMESTAMP)"
                            + " ON CONFLICT(home_banking_id,bot_job_id,preference_key) DO UPDATE SET"
                            + " preference_value=excluded.preference_value,updated_at=CURRENT_TIMESTAMP")) {
                statement.setInt(1, homeBankingId);
                statement.setInt(2, botJobId);
                statement.setString(3, VARIABLE_MODE);
                statement.setString(4, normalized);
                statement.executeUpdate();
            }
        }
    }

    private static void ensureTable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS bot_job_workspace_preference ("
                        + "home_banking_id INTEGER NOT NULL,bot_job_id INTEGER NOT NULL,"
                        + "preference_key TEXT NOT NULL,preference_value TEXT NOT NULL,"
                        + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "PRIMARY KEY(home_banking_id,bot_job_id,preference_key))")) {
            statement.executeUpdate();
        }
    }
}
