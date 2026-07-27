package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.ParentOperations;
import com.allinweb.ch.model.VariableUserDTO;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariableUpdateTransactionTest {
    @Test
    void rollsBackVariableAndEarlierDependentWhenALaterRewriteFails() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE variable(id INTEGER PRIMARY KEY,bot_job_id INTEGER,name TEXT,type TEXT,"
                    + "value TEXT,local_format TEXT,delimiter TEXT,instruction_id INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,parent_id INTEGER,"
                    + "variable_id INTEGER,operation TEXT)");
            sql.execute("INSERT INTO variable VALUES(1,19,'old_name','$String','old_value','','',100)");
            sql.execute("INSERT INTO instruction VALUES(10,19,100,1,'Field:old_value')");

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

    @Test
    void rollsBackComponentVariableAndDependentOperationsWithHomeBankingOwnership() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE component_variable(id INTEGER PRIMARY KEY,home_banking_id INTEGER,name TEXT,"
                    + "type TEXT,value TEXT,local_format TEXT,delimiter TEXT,instruction_id INTEGER)");
            sql.execute("CREATE TABLE component_instruction(id INTEGER PRIMARY KEY,home_banking_id INTEGER,"
                    + "parent_id INTEGER,variable_id INTEGER,operation TEXT)");
            sql.execute("INSERT INTO component_variable VALUES(1,2,'old_name','$String','old_value','','',100)");
            sql.execute("INSERT INTO component_instruction VALUES(10,2,100,1,'Field:old_value')");

            VariableUserDTO variable =
                    new VariableUserDTO(1, "$String", "new_name", "new_value", 19, 100, "Field", "", "", "");
            assertThrows(SQLException.class, () -> new VariableUpdateTransaction().execute(
                    connection,
                    "component_variable",
                    "component_instruction",
                    2,
                    variable,
                    List.of(dependent(10, 100, "Field:new_value"), dependent(999, 100, "Field:new_value"))));

            try (ResultSet row = sql.executeQuery("SELECT name,value FROM component_variable WHERE id=1")) {
                row.next();
                assertEquals("old_name", row.getString("name"));
                assertEquals("old_value", row.getString("value"));
            }
            try (ResultSet row = sql.executeQuery("SELECT operation FROM component_instruction WHERE id=10")) {
                row.next();
                assertEquals("Field:old_value", row.getString("operation"));
            }
        }
    }

    @Test
    void rejectsAForgeAttemptAgainstAnotherWebFieldsVariable() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE variable(id INTEGER PRIMARY KEY,bot_job_id INTEGER,name TEXT,type TEXT,"
                    + "value TEXT,local_format TEXT,delimiter TEXT,instruction_id INTEGER)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER,parent_id INTEGER,"
                    + "variable_id INTEGER,operation TEXT)");
            sql.execute("INSERT INTO variable VALUES(1,19,'protected','$String','old','','',100)");

            VariableUserDTO forged =
                    new VariableUserDTO(1, "$String", "forged", "new", 19, 101, "Other Field", "", "", "");

            assertThrows(
                    SQLException.class,
                    () -> new VariableUpdateTransaction().execute(
                            connection,
                            "variable",
                            "instruction",
                            19,
                            forged,
                            List.of()));

            try (ResultSet row = sql.executeQuery(
                    "SELECT name,value,instruction_id FROM variable WHERE id=1")) {
                row.next();
                assertEquals("protected", row.getString("name"));
                assertEquals("old", row.getString("value"));
                assertEquals(100, row.getInt("instruction_id"));
            }
        }
    }

    @Test
    void updatesOnlyCommandsBoundToTheSelectedLegacyDuplicateVariable() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE component_variable(id INTEGER PRIMARY KEY,home_banking_id INTEGER,name TEXT,"
                    + "type TEXT,value TEXT,local_format TEXT,delimiter TEXT,instruction_id INTEGER)");
            sql.execute("CREATE TABLE component_instruction(id INTEGER PRIMARY KEY,home_banking_id INTEGER,"
                    + "parent_id INTEGER,variable_id INTEGER,name TEXT,actions TEXT,operation TEXT)");
            sql.execute("INSERT INTO component_variable VALUES"
                    + "(1,2,'first','$String','one','','',100),"
                    + "(2,2,'second','$String','two','','',100)");
            sql.execute("INSERT INTO component_instruction VALUES"
                    + "(100,2,NULL,NULL,'Field','C',NULL),"
                    + "(10,2,100,1,'GET first','GET','Field:$first'),"
                    + "(11,2,100,2,'GET second','GET','Field:$second')");

            List<ParentOperations> dependents = PerformDataBase.getInstance()
                    .loadVariableDependents(
                            connection, "component_instruction", 2, 100, 2);
            assertEquals(1, dependents.size());
            assertEquals(11, dependents.get(0).getId());

            VariableUserDTO selected =
                    new VariableUserDTO(2, "$String", "renamed", "updated", 2, 100, "Field", "", "", "");
            new VariableOperationRewriteService().rewrite(dependents, selected);
            new VariableUpdateTransaction().execute(
                    connection,
                    "component_variable",
                    "component_instruction",
                    2,
                    selected,
                    dependents);

            try (ResultSet rows = sql.executeQuery(
                    "SELECT id,name,value FROM component_variable ORDER BY id")) {
                rows.next();
                assertEquals(1, rows.getInt("id"));
                assertEquals("first", rows.getString("name"));
                assertEquals("one", rows.getString("value"));
                rows.next();
                assertEquals(2, rows.getInt("id"));
                assertEquals("renamed", rows.getString("name"));
                assertEquals("updated", rows.getString("value"));
            }
            try (ResultSet rows = sql.executeQuery(
                    "SELECT id,operation FROM component_instruction WHERE id IN (10,11) ORDER BY id")) {
                rows.next();
                assertEquals(10, rows.getInt("id"));
                assertEquals("Field:$first", rows.getString("operation"));
                rows.next();
                assertEquals(11, rows.getInt("id"));
                assertEquals("Field:$renamed", rows.getString("operation"));
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
