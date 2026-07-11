package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.ParentOperations;
import com.allinweb.ch.model.VariableUserDTO;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariableUpdateTransactionTest {
    @Test
    void rollsBackVariableAndEarlierDependentWhenALaterRewriteFails() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE variable(id INTEGER PRIMARY KEY,bot_job_id INTEGER,name TEXT,type TEXT,"
                    + "value TEXT,local_format TEXT,delimiter TEXT)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,parent_id INTEGER,"
                    + "operation TEXT)");
            sql.execute("INSERT INTO variable VALUES(1,19,'old_name','$String','old_value','','')");
            sql.execute("INSERT INTO instruction VALUES(10,19,100,'Field:old_value')");

            VariableUserDTO variable =
                    new VariableUserDTO(1, "$String", "new_name", "new_value", 19, 100, "Field", "", "", "");
            assertThrows(SQLException.class, () -> new VariableUpdateTransaction().execute(
                    connection,
                    "variable",
                    "instruction",
                    19,
                    variable,
                    List.of(dependent(10, 100, "Field:new_value"), dependent(999, 100, "Field:new_value"))));

            try (ResultSet row = sql.executeQuery("SELECT name,value FROM variable WHERE id=1")) {
                row.next();
                assertEquals("old_name", row.getString("name"));
                assertEquals("old_value", row.getString("value"));
            }
            try (ResultSet row = sql.executeQuery("SELECT operation FROM instruction WHERE id=10")) {
                row.next();
                assertEquals("Field:old_value", row.getString("operation"));
            }
        }
    }

    private ParentOperations dependent(int id, int parentId, String operation) {
        ParentOperations row = new ParentOperations();
        row.setId(id);
        row.setInstructionId(parentId);
        row.setActions("SET");
        row.setOperations(operation);
        return row;
    }
}
