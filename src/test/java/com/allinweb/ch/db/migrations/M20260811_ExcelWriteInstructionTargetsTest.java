package com.allinweb.ch.db.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class M20260811_ExcelWriteInstructionTargetsTest {

    @Test
    void backfillsInstructionTargetsPreservesVariablesAndClearsOnlyElementParents() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE bot_job(id INTEGER PRIMARY KEY,home_banking_id INTEGER NOT NULL)");
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY,block_order_number INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,export_file TEXT)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY,instruction_order_number INTEGER NOT NULL,"
                    + "actions TEXT,name TEXT,operation TEXT,parent_id INTEGER,parent_block_id INTEGER,"
                    + "block_id INTEGER NOT NULL,bot_job_id INTEGER NOT NULL)");
            sql.execute("CREATE TABLE instruction_variable_slot(home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,instruction_id INTEGER NOT NULL,slot TEXT NOT NULL,"
                    + "variable_id INTEGER NOT NULL)");
            sql.execute("CREATE TABLE bot_job_variable_definition(home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,id INTEGER NOT NULL,producer_instruction_id INTEGER)");
            new M20260801_InstructionVariableCommandConfig().apply(connection, "TEXT");
            new M20260802_ConditionalCommandConfig().apply(connection, "TEXT");

            sql.execute("INSERT INTO bot_job VALUES(5,2)");
            sql.execute("INSERT INTO block VALUES(10,1,5,'C:/exports/legacy.xlsx:,')");
            sql.execute("INSERT INTO instruction VALUES"
                    + "(100,1,'input','Legacy Column',NULL,NULL,NULL,10,5),"
                    + "(101,2,'E','ExcelWrite','$amount',100,10,10,5),"
                    + "(102,3,'E','ExcelWrite','$balance',100,10,10,5),"
                    + "(103,4,'E','ExcelWrite','$cleared',100,10,10,5)");
            sql.execute("INSERT INTO instruction_variable_slot VALUES"
                    + "(2,5,101,'READ',700),(2,5,102,'READ',701),(2,5,103,'READ',702)");
            sql.execute("INSERT INTO bot_job_variable_definition VALUES"
                    + "(2,5,700,100),(2,5,701,102)");
            sql.execute("INSERT INTO instruction_variable_command_config("
                    + "home_banking_id,bot_job_id,instruction_id,command_type,condition_source,operand_kind,"
                    + "comparison_operator,operand_raw_value,operand_variable_id,output_key,output_column,"
                    + "output_file,external_source_key,format_policy,config_revision,created_at,updated_at) VALUES"
                    + "(2,5,102,'E',NULL,NULL,NULL,NULL,NULL,'Typed Key','Typed Column',"
                    + "'C:/exports/typed.csv:|',NULL,'EXACT_TEXT',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            sql.execute("INSERT INTO instruction_variable_command_config("
                    + "home_banking_id,bot_job_id,instruction_id,command_type,condition_source,operand_kind,"
                    + "comparison_operator,operand_raw_value,operand_variable_id,output_key,output_column,"
                    + "output_file,external_source_key,format_policy,config_revision,created_at,updated_at) VALUES"
                    + "(2,5,103,'E',NULL,NULL,NULL,NULL,NULL,'Cleared Key','Cleared Column',"
                    + "'',NULL,'EXACT_TEXT',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");

            new M20260811_ExcelWriteInstructionTargets().apply(connection, "TEXT");

            try (ResultSet migrated = sql.executeQuery("SELECT output_key,output_column,output_file"
                    + " FROM instruction_variable_command_config WHERE instruction_id=101")) {
                migrated.next();
                assertEquals("amount", migrated.getString("output_key"));
                assertEquals("Legacy Column", migrated.getString("output_column"));
                assertEquals("C:/exports/legacy.xlsx:,", migrated.getString("output_file"));
            }
            try (ResultSet preserved = sql.executeQuery("SELECT output_key,output_column,output_file"
                    + " FROM instruction_variable_command_config WHERE instruction_id=102")) {
                preserved.next();
                assertEquals("Typed Key", preserved.getString("output_key"));
                assertEquals("Typed Column", preserved.getString("output_column"));
                assertEquals("C:/exports/typed.csv:|", preserved.getString("output_file"));
            }
            try (ResultSet cleared = sql.executeQuery("SELECT output_key,output_column,output_file"
                    + " FROM instruction_variable_command_config WHERE instruction_id=103")) {
                cleared.next();
                assertEquals("Cleared Key", cleared.getString("output_key"));
                assertEquals("Cleared Column", cleared.getString("output_column"));
                assertEquals("", cleared.getString("output_file"));
            }
            try (ResultSet instruction = sql.executeQuery(
                    "SELECT parent_id,parent_block_id FROM instruction WHERE id=101")) {
                instruction.next();
                assertNull(instruction.getObject("parent_id"));
                assertNull(instruction.getObject("parent_block_id"));
            }
            try (ResultSet variables = sql.executeQuery(
                    "SELECT COUNT(*) AS total,SUM(variable_id) AS ids FROM instruction_variable_slot")) {
                variables.next();
                assertEquals(3, variables.getInt("total"));
                assertEquals(2103, variables.getInt("ids"));
            }
            try (ResultSet variables = sql.executeQuery(
                    "SELECT id,producer_instruction_id FROM bot_job_variable_definition ORDER BY id")) {
                variables.next();
                assertEquals(700, variables.getInt("id"));
                assertEquals(100, variables.getInt("producer_instruction_id"));
                variables.next();
                assertEquals(701, variables.getInt("id"));
                assertNull(variables.getObject("producer_instruction_id"));
            }
            try (ResultSet legacyBlock = sql.executeQuery("SELECT export_file FROM block WHERE id=10")) {
                legacyBlock.next();
                assertEquals("C:/exports/legacy.xlsx:,", legacyBlock.getString("export_file"));
            }
        }
    }
}
