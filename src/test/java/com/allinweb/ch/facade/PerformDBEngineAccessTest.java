package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARConstantsEngine;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class PerformDBEngineAccessTest {

    private PerformDBEngine dbEngine;

    @Mock
    private ARPropertyManager mockPropertyManager;

    @Mock
    private Connection mockConnection;

    @BeforeEach
    void setup() throws SQLException {
        dbEngine = PerformDBEngine.getInstance();

        // Inject mock ARPropertyManager
        PerformDBEngine.arPropertyManager = mockPropertyManager;

        // Mock database type and path
        when(mockPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE)).thenReturn("ACCESS");
        when(mockPropertyManager.getProperty(ARPropertyEnum.PATH_DB)).thenReturn("C:\\ARWeb-Martini\\ARWeb");
    }

    @Test
    @DisplayName("Test Access DB Connection fully mocked")
    void testAccessConnection() throws SQLException, ClassNotFoundException {
        // Mock DriverManager.getConnection() using try-with-resources for static mocking
        try (var driverManagerMock = mockStatic(DriverManager.class)) {
            driverManagerMock.when(() ->
                    DriverManager.getConnection(anyString())
            ).thenReturn(mockConnection);

            // Mock setReadOnly behavior
            doNothing().when(mockConnection).setReadOnly(false);

            // Call getConnection()
            Connection conn = dbEngine.getConnection();

            // Assertions
            assertNotNull(conn, "Connection should not be null");
            assertFalse(conn.isReadOnly(), "Connection should be writable");
            assertTrue(dbEngine.ACCESS_DB, "ACCESS_DB flag should be true");
            assertTrue(dbEngine.connDBWorks, "connDBWorks should be true");
            assertFalse(dbEngine.dbFailed, "dbFailed should be false");

            // Verify interactions
            verify(mockPropertyManager, atLeastOnce()).getProperty(ARPropertyEnum.DATABASE_TYPE);
            verify(mockPropertyManager, atLeastOnce()).getProperty(ARPropertyEnum.PATH_DB);
            verify(mockConnection, atLeastOnce()).setReadOnly(false);

            // Verify DriverManager was called with Access DB URL
            driverManagerMock.verify(() -> DriverManager.getConnection(
                    contains(ARConstantsEngine.FILE_NAME_ACCESS)
            ));
        }
    }
}
