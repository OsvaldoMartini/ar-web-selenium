package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.*;

import com.allinweb.ch.util.ARPropertyManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PerformDBEngineAccessTest {

    private static final String WEB_CONFIG_FILE_PATH = "C:\\ARWeb-Martini\\Config-4.7\\TESTS.config";
    private ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();

    @BeforeEach
    void setup() {
        File configFile = new File(WEB_CONFIG_FILE_PATH);

        // Create the config file if it does not exist
        if (!configFile.exists()) {
            arPropertyManager.setConfigurationFileName(WEB_CONFIG_FILE_PATH);
            arPropertyManager.createDefaultProperties(configFile);
        }

        // Set the configuration file path and load properties
        arPropertyManager.setConfigurationFileName(WEB_CONFIG_FILE_PATH);
        try (FileInputStream fis = new FileInputStream(configFile)) {
            arPropertyManager.loadProperties(fis);
        } catch (IOException e) {
            fail("Failed to load config file: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Test config file exists")
    void testConfigFileExists() {
        File configFile = new File(WEB_CONFIG_FILE_PATH);
        assertTrue(configFile.exists(), "Test configuration file must exist");
    }

    @Test
    @DisplayName("Test Access DB connection")
    void testAccessConnection() {
        // Example: your DB engine connection test
        try (Connection conn = PerformDBEngine.getInstance().getConnection()) {
            assertNotNull(conn, "Connection should not be null");
            assertFalse(conn.isReadOnly(), "Connection should be writable");
        } catch (SQLException e) {
            fail("Failed to connect to DB: " + e.getMessage());
        }
    }
}
