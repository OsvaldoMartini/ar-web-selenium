package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.ReferenceLoadDTO;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

class PerformDataBasePageScannerTransactionTest {

    @Test
    void insertsInstructionsAndReferencesWithGeneratedKeysInOneTransaction() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createSchema(connection, false);
            InstructionLoad instruction = instruction("Submit", "//button[@id='submit']");

            List<Integer> ids = PerformDataBase.insertInstructionsAndReferencesTransaction(
                    connection, List.of(instruction), 42, 91);

            assertEquals(1, ids.size());
            assertEquals(ids.get(0), instruction.getId());
            assertEquals(1, count(connection, "instruction"));
            assertEquals(1, count(connection, "reference"));
        }
    }

    @Test
    void referenceFailureRollsBackTheInstructionToo() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createSchema(connection, true);
            InstructionLoad instruction = instruction("Submit", null);

            assertThrows(
                    SQLException.class,
                    () -> PerformDataBase.insertInstructionsAndReferencesTransaction(
                            connection, List.of(instruction), 42, 91));

            assertEquals(0, count(connection, "instruction"));
            assertEquals(0, count(connection, "reference"));
        }
    }

    private static InstructionLoad instruction(String name, String referenceValue) {
        InstructionLoad instruction = new InstructionLoad();
        instruction.setName(name);
        instruction.setTagName("button");
        instruction.setXpath("//button[@id='submit']");
        instruction.setActions("C");
        instruction.setInstructionOrderNumber(1);
        ReferenceLoadDTO reference = new ReferenceLoadDTO();
        reference.setReferenceType("currentXPath");
        reference.setValue(referenceValue);
        instruction.setReferenceLoadDTOList(List.of(reference));
        return instruction;
    }

    private static void createSchema(Connection connection, boolean requireReferenceValue)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE instruction (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      coordinates TEXT, iframe_xpath TEXT, tag_name TEXT, shadow_host TEXT,
                      shadow_root TEXT, css_selector TEXT, xpath TEXT,
                      action_custom_max_wait_sec INTEGER, actions TEXT, default_value TEXT,
                      description TEXT, instruction_order_number INTEGER, name TEXT,
                      client_named TEXT, on_hold_seconds INTEGER, operation TEXT,
                      parent_block_id INTEGER, parent_id INTEGER,
                      block_id INTEGER, bot_job_id INTEGER, block_marked INTEGER,
                      codified INTEGER, export_to_abr INTEGER, optional INTEGER, active INTEGER,
                      force_coordinates TEXT
                    )
                    """);
            statement.execute("CREATE TABLE reference ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, reference_type TEXT, value TEXT"
                    + (requireReferenceValue ? " NOT NULL" : "")
                    + ", instruction_id INTEGER, bot_job_id INTEGER)");
        }
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rows.next() ? rows.getInt(1) : -1;
        }
    }
}
