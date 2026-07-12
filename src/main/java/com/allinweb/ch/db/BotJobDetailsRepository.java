package com.allinweb.ch.db;

import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.model.BotJobDetailsPersistedState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Read-only, allowlisted Bot Job Details projection. It never mutates PerformLists caches. */
public final class BotJobDetailsRepository {

    private final PerformDataBase database;

    public BotJobDetailsRepository(PerformDataBase database) {
        this.database = database;
    }

    public BotJobDetailsPersistedState load(int botJobId) throws SQLException {
        if (botJobId <= 0) {
            throw new IllegalArgumentException("Bot Job ID must be positive");
        }
        try (Connection connection = database.getConnection()) {
            JobRow job = loadJob(connection, botJobId);
            String organizationName = loadOrganizationName(connection, job.homeBankingId());
            EnvironmentRow selectedEnvironment =
                    loadEnvironment(connection, job.homeUrlId(), job.homeBankingId());
            List<BotJobDetailsPersistedState.Environment> environments =
                    loadEnvironments(connection, job.homeBankingId());
            List<BotJobDetailsPersistedState.Block> blocks = loadBlocks(connection, botJobId);
            return new BotJobDetailsPersistedState(
                    job.id(),
                    safe(job.name()),
                    safe(job.description()),
                    normalizeProjectType(job.projectType()),
                    job.active(),
                    job.homeBankingId(),
                    organizationName,
                    job.homeUrlId(),
                    selectedEnvironment == null ? "" : defaultEnvironmentName(selectedEnvironment.name()),
                    selectedEnvironment == null ? "" : safe(selectedEnvironment.url()),
                    environments,
                    blocks);
        }
    }

    private JobRow loadJob(Connection connection, int botJobId) throws SQLException {
        String sql = "SELECT id, name, description, priority, active, home_banking_id, home_url_id "
                + "FROM bot_job WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, botJobId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Bot Job was not found");
                }
                return new JobRow(
                        result.getInt("id"),
                        result.getString("name"),
                        result.getString("description"),
                        result.getString("priority"),
                        result.getBoolean("active"),
                        result.getInt("home_banking_id"),
                        result.getInt("home_url_id"));
            }
        }
    }

    private String loadOrganizationName(Connection connection, int homeBankingId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT name FROM home_banking WHERE id = ?")) {
            statement.setInt(1, homeBankingId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? safe(result.getString("name")) : "";
            }
        }
    }

    private EnvironmentRow loadEnvironment(Connection connection, int homeUrlId, int homeBankingId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, name, url, home_banking_id FROM home_url WHERE id = ? AND home_banking_id = ?")) {
            statement.setInt(1, homeUrlId);
            statement.setInt(2, homeBankingId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return new EnvironmentRow(
                        result.getInt("id"),
                        result.getString("name"),
                        result.getString("url"),
                        result.getInt("home_banking_id"));
            }
        }
    }

    private List<BotJobDetailsPersistedState.Environment> loadEnvironments(
            Connection connection, int homeBankingId) throws SQLException {
        String sql = "SELECT id, name, url, home_banking_id FROM home_url "
                + "WHERE home_banking_id = ? ORDER BY id";
        List<BotJobDetailsPersistedState.Environment> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, homeBankingId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new BotJobDetailsPersistedState.Environment(
                            result.getInt("id"),
                            defaultEnvironmentName(result.getString("name")),
                            safe(result.getString("url")),
                            result.getInt("home_banking_id")));
                }
            }
        }
        return rows;
    }

    private List<BotJobDetailsPersistedState.Block> loadBlocks(Connection connection, int botJobId)
            throws SQLException {
        String sql = "SELECT id, block_order_number, name, description, type_id, active, wait "
                + "FROM block WHERE bot_job_id = ? ORDER BY block_order_number, id";
        List<BotJobDetailsPersistedState.Block> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, botJobId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new BotJobDetailsPersistedState.Block(
                            result.getInt("id"),
                            result.getInt("block_order_number"),
                            safe(result.getString("name")),
                            safe(result.getString("description")),
                            result.getInt("type_id"),
                            result.getBoolean("active"),
                            result.getInt("wait")));
                }
            }
        }
        return rows;
    }

    private static String normalizeProjectType(String value) {
        return value == null || value.isBlank() ? "Web App" : value;
    }

    private static String defaultEnvironmentName(String value) {
        return value == null || value.isBlank() ? "TEST" : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record JobRow(
            int id,
            String name,
            String description,
            String projectType,
            boolean active,
            int homeBankingId,
            int homeUrlId) {}

    private record EnvironmentRow(int id, String name, String url, int homeBankingId) {}
}
