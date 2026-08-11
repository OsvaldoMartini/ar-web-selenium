package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.allinweb.ch.model.UpdatedRow;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerformDataBaseInstructionDeletePreservationTest {

    private final ARPropertyManager properties = ARPropertyManager.getInstance();
    private final PerformDataBase database = PerformDataBase.getInstance();

    @TempDir
    Path temporaryDirectory;

    private String previousDatabaseType;
    private String previousDatabasePath;

    @BeforeEach
    void setUpDatabase() throws Exception {
        Properties configured = properties.getProperties();
        previousDatabaseType = configured.getProperty(ARPropertyEnum.DATABASE_TYPE.getValue());
        previousDatabasePath = configured.getProperty(ARPropertyEnum.PATH_DB.getValue());
        configured.setProperty(ARPropertyEnum.DATABASE_TYPE.getValue(), "TEXT");
        configured.setProperty(ARPropertyEnum.PATH_DB.getValue(), temporaryDirectory.toString());
        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE bot_job(id INTEGER PRIMARY KEY,home_banking_id INTEGER NOT NULL)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,bot_job_id INTEGER NOT NULL,"
                    + "block_id INTEGER NOT NULL,parent_id INTEGER,parent_block_id INTEGER)");
            sql.execute("CREATE TABLE reference(id INTEGER PRIMARY KEY,bot_job_id INTEGER NOT NULL,"
                    + "instruction_id INTEGER NOT NULL)");
            sql.execute("CREATE TABLE bot_job_variable_definition(id INTEGER PRIMARY KEY,"
                    + "home_banking_id INTEGER NOT NULL,bot_job_id INTEGER NOT NULL,"
                    + "producer_instruction_id INTEGER,updated_at TEXT)");
            sql.execute("INSERT INTO bot_job VALUES(5,2)");
            sql.execute("INSERT INTO instruction VALUES(100,5,10,NULL,NULL),(101,5,10,100,10)");
            sql.execute("INSERT INTO reference VALUES(1,5,100)");
            sql.execute("INSERT INTO bot_job_variable_definition VALUES(500,2,5,100,'before'),"
                    + "(501,2,5,101,'before')");
        }
    }

    @AfterEach
    void restoreDatabaseConfiguration() {
        restore(ARPropertyEnum.DATABASE_TYPE, previousDatabaseType);
        restore(ARPropertyEnum.PATH_DB, previousDatabasePath);
    }

    @Test
    void preservesVariablesAndDisconnectsSurvivingParents() throws Exception {
        UpdatedRow repair = new UpdatedRow();
        repair.setInstructionId(101);
        repair.setParentId(null);

        assertNull(database.deleteInstructionGraphAtomic(
                "instruction", 5, List.of(100), List.of(repair)));

        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            assertEquals(0, number(sql, "SELECT COUNT(*) FROM instruction WHERE id=100"));
            assertEquals(0, number(sql, "SELECT COUNT(*) FROM reference WHERE instruction_id=100"));
            assertEquals(2, number(sql, "SELECT COUNT(*) FROM bot_job_variable_definition"));
            assertNull(value(sql, "SELECT producer_instruction_id"
                    + " FROM bot_job_variable_definition WHERE id=500"));
            assertEquals(101, number(sql, "SELECT producer_instruction_id"
                    + " FROM bot_job_variable_definition WHERE id=501"));
            assertNull(value(sql, "SELECT parent_id FROM instruction WHERE id=101"));
            assertNull(value(sql, "SELECT parent_block_id FROM instruction WHERE id=101"));
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:"
                + temporaryDirectory.resolve("database.db"));
    }

    private int number(Statement sql, String query) throws Exception {
        return ((Number) value(sql, query)).intValue();
    }

    private Object value(Statement sql, String query) throws Exception {
        try (ResultSet rows = sql.executeQuery(query)) {
            return rows.next() ? rows.getObject(1) : null;
        }
    }

    private void restore(ARPropertyEnum property, String value) {
        Properties configured = properties.getProperties();
        if (value == null) configured.remove(property.getValue());
        else configured.setProperty(property.getValue(), value);
    }
}
