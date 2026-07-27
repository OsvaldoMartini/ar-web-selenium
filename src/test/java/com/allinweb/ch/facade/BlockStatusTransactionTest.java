package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class BlockStatusTransactionTest {

    @Test
    void rollsBackBlockWhenAChildInstructionUpdateFails() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE component_block("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER,active INTEGER)");
            sql.execute("CREATE TABLE component_instruction("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER,block_id INTEGER,active INTEGER)");
            sql.execute("INSERT INTO component_block VALUES(10,7,1)");
            sql.execute("INSERT INTO component_instruction VALUES(1,7,10,1)");
            sql.execute("CREATE TRIGGER refuse_child_status BEFORE UPDATE OF active "
                    + "ON component_instruction BEGIN "
                    + "SELECT RAISE(ABORT,'child status failure'); END");

            assertThrows(
                    SQLException.class,
                    () -> new BlockStatusTransaction().execute(
                            connection,
                            "component_block",
                            "component_instruction",
                            7,
                            10,
                            false));

            try (ResultSet block = sql.executeQuery(
                    "SELECT active FROM component_block WHERE id=10")) {
                block.next();
                assertEquals(1, block.getInt("active"));
            }
        }
    }

    @Test
    void supportsAnEmptyOwnedBlock() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE component_block("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER,active INTEGER)");
            sql.execute("CREATE TABLE component_instruction("
                    + "id INTEGER PRIMARY KEY,home_banking_id INTEGER,block_id INTEGER,active INTEGER)");
            sql.execute("INSERT INTO component_block VALUES(10,7,1)");

            new BlockStatusTransaction().execute(
                    connection,
                    "component_block",
                    "component_instruction",
                    7,
                    10,
                    false);

            try (ResultSet block = sql.executeQuery(
                    "SELECT active FROM component_block WHERE id=10")) {
                block.next();
                assertEquals(0, block.getInt("active"));
            }
        }
    }
}
