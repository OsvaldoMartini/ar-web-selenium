package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.*;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import net.ucanaccess.jdbc.UcanaccessConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Mutates the process-wide database type and PATH_DB")
class PerformDBEngineAccessTest {

    private static final Path WEB_CONFIG_FILE_PATH =
            Path.of(System.getProperty("user.dir"), "Config-4.2", "TESTS.config");
    private final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private Properties previousProperties;

    @TempDir
    Path temporaryDatabaseDirectory;

    @BeforeEach
    void setup() {
        previousProperties = new Properties();
        previousProperties.putAll(arPropertyManager.getProperties());
    }

    @AfterEach
    void restoreProperties() {
        Properties restored = new Properties();
        restored.putAll(previousProperties);
        arPropertyManager.setProperties(restored);
    }

    @Test
    @DisplayName("Test config file exists")
    void testConfigFileExists() {
        assertTrue(Files.isRegularFile(WEB_CONFIG_FILE_PATH), "Test configuration file must exist");
    }

    @Test
    @DisplayName("Test Access DB connection")
    void testAccessConnection() throws Exception {
        Properties isolated = new Properties();
        isolated.putAll(previousProperties);
        isolated.setProperty(ARPropertyEnum.DATABASE_TYPE.getValue(), "Access");
        isolated.setProperty(
                ARPropertyEnum.PATH_DB.getValue(), temporaryDatabaseDirectory.toAbsolutePath().toString());
        arPropertyManager.setProperties(isolated);

        Path accessDatabase = temporaryDatabaseDirectory.resolve("database.mdb");
        try (Connection conn = PerformDBEngine.getInstance().getConnection();
                Statement statement = conn.createStatement()) {
            assertNotNull(conn, "Connection should not be null");
            assertFalse(conn.isReadOnly(), "Connection should be writable");
            statement.execute("CREATE TABLE access_connection_probe (id INTEGER)");
            statement.executeUpdate("INSERT INTO access_connection_probe (id) VALUES (1)");
            try (ResultSet rows = statement.executeQuery("SELECT id FROM access_connection_probe")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
            assertInstanceOf(UcanaccessConnection.class, conn).unloadDB();
        }
        assertTrue(Files.isRegularFile(accessDatabase), "Production connection must create the Access database");
    }
}
