package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class PerformDataBaseNullableIntegerTest {
    @Test
    void preservesSqlNullInsteadOfCreatingMissingParentZero() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement();
                ResultSet row = sql.executeQuery(
                        "SELECT NULL AS parent_id, 37 AS variable_id, NULL AS parent_block_id")) {
            row.next();

            assertNull(PerformDataBase.readNullableInteger(row, "parent_id"));
            assertEquals(37, PerformDataBase.readNullableInteger(row, "variable_id"));
            assertNull(PerformDataBase.readNullableInteger(row, "parent_block_id"));
        }
    }
}
