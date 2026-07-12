package com.allinweb.ch.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Mutates ARPropertyManager and related system properties")
class BancaStatoIsolatedFixtureTest {

    private static final String SOURCE_CONFIG_PROPERTY = "bancastato.source.config";
    private static final String SOURCE_DATABASE_PROPERTY = "bancastato.source.database";
    private static final String ACTIVE_CONFIG_PROPERTY = "ARWebConfig";

    private final ARPropertyManager manager = ARPropertyManager.getInstance();
    private Properties managerPropertiesBefore;
    private String managerConfigBefore;
    private String activeConfigBefore;
    private String sourceConfigBefore;
    private String sourceDatabaseBefore;

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void captureGlobalState() {
        managerPropertiesBefore = new Properties();
        managerPropertiesBefore.putAll(manager.getProperties());
        managerConfigBefore = manager.getConfigurationFileName();
        activeConfigBefore = System.getProperty(ACTIVE_CONFIG_PROPERTY);
        sourceConfigBefore = System.getProperty(SOURCE_CONFIG_PROPERTY);
        sourceDatabaseBefore = System.getProperty(SOURCE_DATABASE_PROPERTY);
    }

    @AfterEach
    void restoreGlobalState() {
        manager.setProperties(managerPropertiesBefore);
        manager.setConfigurationFileName(managerConfigBefore);
        restoreSystemProperty(ACTIVE_CONFIG_PROPERTY, activeConfigBefore);
        restoreSystemProperty(SOURCE_CONFIG_PROPERTY, sourceConfigBefore);
        restoreSystemProperty(SOURCE_DATABASE_PROPERTY, sourceDatabaseBefore);
    }

    @Test
    void onlineBackupIncludesCommittedWalAndSanitizesConfig() throws Exception {
        Path sourceConfig = writeSourceConfig();
        Path sourceDatabase = tempDirectory.resolve("source-database.db");
        System.setProperty(SOURCE_CONFIG_PROPERTY, sourceConfig.toString());
        System.setProperty(SOURCE_DATABASE_PROPERTY, sourceDatabase.toString());

        Class.forName("org.sqlite.JDBC");
        try (Connection writer = DriverManager.getConnection("jdbc:sqlite:" + sourceDatabase);
                Statement statement = writer.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("CREATE TABLE fixture_value (value TEXT NOT NULL)");
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            statement.execute("INSERT INTO fixture_value(value) VALUES ('committed-in-wal')");

            try (BancaStatoIsolatedFixture fixture = BancaStatoIsolatedFixture.create(tempDirectory)) {
                String snapshotPath = fixture.databaseFile().toString().replace('\\', '/');
                try (Connection snapshot =
                                DriverManager.getConnection("jdbc:sqlite:file:" + snapshotPath + "?mode=ro");
                        Statement snapshotStatement = snapshot.createStatement();
                        ResultSet result = snapshotStatement.executeQuery("SELECT value FROM fixture_value")) {
                    assertTrue(result.next());
                    assertEquals("committed-in-wal", result.getString(1));
                }

                Properties isolated = new Properties();
                try (var input = Files.newInputStream(fixture.configFile())) {
                    isolated.load(input);
                }
                assertEquals("", isolated.getProperty(ARPropertyEnum.DB_PWD.getValue()));
                assertEquals("", isolated.getProperty(ARPropertyEnum.AI_API_KEY.getValue()));
                assertFalse(Files.readString(fixture.configFile()).contains("production-secret"));
                assertTrue(Path.of(isolated.getProperty(ARPropertyEnum.PATH_DB.getValue()))
                        .startsWith(fixture.fixtureRoot()));
            }
        }
    }

    @Test
    void activationAndOutputValidationRestoreGlobalState() throws Exception {
        Path sourceConfig = writeSourceConfig();
        Path sourceDatabase = writeRollbackDatabase();
        System.setProperty(SOURCE_CONFIG_PROPERTY, sourceConfig.toString());
        System.setProperty(SOURCE_DATABASE_PROPERTY, sourceDatabase.toString());

        try (BancaStatoIsolatedFixture fixture = BancaStatoIsolatedFixture.create(tempDirectory)) {
            fixture.activate(manager);
            assertEquals(fixture.configFile().toString(), manager.getConfigurationFileName());
            assertTrue(Path.of(manager.getProperty(ARPropertyEnum.PATH_DB)).startsWith(fixture.fixtureRoot()));
            fixture.requireIsolatedOutput(fixture.fixtureRoot().resolve("reports").resolve("safe.txt"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> fixture.requireIsolatedOutput(fixture.fixtureRoot().resolve("..").resolve("escape.txt")));
        }

        assertEquals(managerPropertiesBefore, manager.getProperties());
        assertEquals(managerConfigBefore, manager.getConfigurationFileName());
        assertEquals(activeConfigBefore, System.getProperty(ACTIVE_CONFIG_PROPERTY));
    }

    private Path writeSourceConfig() throws IOException {
        Path config = tempDirectory.resolve("source-ARWeb.config");
        Properties properties = new Properties();
        properties.setProperty(ARPropertyEnum.DATABASE_TYPE.getValue(), "TEXT");
        properties.setProperty(ARPropertyEnum.BROWSER.getValue(), "edge");
        properties.setProperty(ARPropertyEnum.DB_PWD.getValue(), "production-secret");
        properties.setProperty(ARPropertyEnum.AI_API_KEY.getValue(), "production-secret");
        try (var output = Files.newOutputStream(config)) {
            properties.store(output, "synthetic fixture source");
        }
        return config;
    }

    private Path writeRollbackDatabase() throws Exception {
        Path database = tempDirectory.resolve("rollback-source.db");
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE fixture_value (value TEXT NOT NULL)");
            statement.execute("INSERT INTO fixture_value(value) VALUES ('rollback')");
        }
        return database;
    }

    private static void restoreSystemProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
