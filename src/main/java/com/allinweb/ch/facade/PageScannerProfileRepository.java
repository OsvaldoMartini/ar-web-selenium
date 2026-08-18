package com.allinweb.ch.facade;

import com.allinweb.ch.model.ScannerSearchProfile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

/** JDBC repository for persisted Page Scanner focus profiles. */
public final class PageScannerProfileRepository {

    private static final String COLUMNS =
            "id, profile_key, label, search_terms, sort_order, is_protected";
    private static final PageScannerProfileRepository INSTANCE = new PageScannerProfileRepository(
            () -> PerformDBEngine.getInstance().getConnection());

    private final ConnectionProvider connections;

    public static PageScannerProfileRepository getInstance() {
        return INSTANCE;
    }

    public PageScannerProfileRepository(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    public List<ScannerSearchProfile> list() throws SQLException {
        String sql = "SELECT " + COLUMNS
                + " FROM page_scanner_profile ORDER BY sort_order ASC, label ASC, id ASC";
        List<ScannerSearchProfile> profiles = new ArrayList<>();
        try (Connection connection = connections.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                profiles.add(map(resultSet));
            }
        }
        return profiles;
    }

    public Optional<ScannerSearchProfile> findById(int id) throws SQLException {
        return find("SELECT " + COLUMNS + " FROM page_scanner_profile WHERE id = ?", id);
    }

    public Optional<ScannerSearchProfile> findByKey(String key) throws SQLException {
        return find("SELECT " + COLUMNS + " FROM page_scanner_profile WHERE profile_key = ?", key);
    }

    public int insert(ScannerSearchProfile profile) throws SQLException {
        String sql = "INSERT INTO page_scanner_profile"
                + " (profile_key, label, search_terms, sort_order, is_protected, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        Timestamp now = new Timestamp(System.currentTimeMillis());
        try (Connection connection = connections.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindMutable(statement, profile);
            statement.setBoolean(5, profile.protectedProfile());
            statement.setTimestamp(6, now);
            statement.setTimestamp(7, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        return findByKey(profile.key())
                .map(ScannerSearchProfile::id)
                .orElseThrow(() -> new SQLException("Could not retrieve inserted Page Scanner profile"));
    }

    public boolean update(ScannerSearchProfile profile) throws SQLException {
        String sql = "UPDATE page_scanner_profile SET profile_key = ?, label = ?, search_terms = ?,"
                + " sort_order = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = connections.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bindMutable(statement, profile);
            statement.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            statement.setInt(6, profile.id());
            return statement.executeUpdate() == 1;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection connection = connections.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM page_scanner_profile WHERE id = ?")) {
            statement.setInt(1, id);
            return statement.executeUpdate() == 1;
        }
    }

    private Optional<ScannerSearchProfile> find(String sql, Object value) throws SQLException {
        try (Connection connection = connections.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            if (value instanceof Number number) {
                statement.setInt(1, number.intValue());
            } else {
                statement.setString(1, String.valueOf(value));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    private static void bindMutable(PreparedStatement statement, ScannerSearchProfile profile) throws SQLException {
        statement.setString(1, profile.key());
        statement.setString(2, profile.label());
        statement.setString(3, profile.searchTerms());
        statement.setInt(4, profile.sortOrder());
    }

    private static ScannerSearchProfile map(ResultSet resultSet) throws SQLException {
        return new ScannerSearchProfile(
                resultSet.getInt("id"),
                resultSet.getString("profile_key"),
                resultSet.getString("label"),
                resultSet.getString("search_terms"),
                resultSet.getInt("sort_order"),
                readBoolean(resultSet, "is_protected"));
    }

    private static boolean readBoolean(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && ("true".equalsIgnoreCase(value.toString()) || "yes".equalsIgnoreCase(value.toString()));
    }

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }
}
