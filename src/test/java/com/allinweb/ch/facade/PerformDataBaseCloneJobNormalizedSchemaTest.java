package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.HomeUrlDTO;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerformDataBaseCloneJobNormalizedSchemaTest {

    private static final int SOURCE_JOB_ID = 29;
    private static final int HOME_BANKING_ID = 13;

    private final ARPropertyManager properties = ARPropertyManager.getInstance();
    private final PerformDataBase database = PerformDataBase.getInstance();

    @TempDir
    Path temporaryDirectory;

    private String previousDatabaseType;
    private String previousDatabasePath;

    @BeforeEach
    void createNormalizedSchema() throws Exception {
        Properties configured = properties.getProperties();
        previousDatabaseType = configured.getProperty(ARPropertyEnum.DATABASE_TYPE.getValue());
        previousDatabasePath = configured.getProperty(ARPropertyEnum.PATH_DB.getValue());
        configured.setProperty(ARPropertyEnum.DATABASE_TYPE.getValue(), "TEXT");
        configured.setProperty(ARPropertyEnum.PATH_DB.getValue(), temporaryDirectory.toString());

        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            sql.execute("PRAGMA foreign_keys=ON");
            sql.execute("CREATE TABLE bot_job(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,"
                    + "description TEXT,priority TEXT,home_banking_id INTEGER NOT NULL,"
                    + "home_url_id INTEGER NOT NULL,active INTEGER NOT NULL)");
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "block_order_number INTEGER NOT NULL,name TEXT,description TEXT,type_id INTEGER,"
                    + "export_file TEXT,active INTEGER NOT NULL,wait INTEGER,bot_job_id INTEGER NOT NULL)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "instruction_order_number INTEGER NOT NULL,actions TEXT,name TEXT,xpath TEXT,"
                    + "coordinates TEXT,force_coordinates INTEGER,iframe_xpath TEXT,tag_name TEXT,"
                    + "shadow_host TEXT,shadow_root TEXT,css_selector TEXT,description TEXT,"
                    + "operation TEXT,optional INTEGER,block_marked INTEGER,default_value TEXT,"
                    + "action_custom_max_wait_sec INTEGER,on_hold_seconds INTEGER,codified INTEGER,"
                    + "export_to_abr INTEGER,active INTEGER NOT NULL,block_id INTEGER NOT NULL,"
                    + "parent_block_id INTEGER,parent_id INTEGER,bot_job_id INTEGER NOT NULL,"
                    + "client_named TEXT)");
            sql.execute("CREATE TABLE reference(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "reference_type TEXT,value TEXT,instruction_id INTEGER,bot_job_id INTEGER NOT NULL)");
            sql.execute("CREATE TABLE instruction_graph_state(workspace_kind TEXT NOT NULL,"
                    + "home_banking_id INTEGER NOT NULL,owner_id INTEGER NOT NULL,graph_version INTEGER NOT NULL,"
                    + "created_at TEXT NOT NULL,updated_at TEXT NOT NULL,runtime_revision INTEGER,"
                    + "reset_generation INTEGER,next_variable_id INTEGER,"
                    + "PRIMARY KEY(workspace_kind,home_banking_id,owner_id))");
            sql.execute("CREATE TABLE bot_job_variable_definition(home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,id INTEGER NOT NULL,variable_type TEXT,name TEXT NOT NULL,"
                    + "configured_value TEXT,local_format TEXT,delimiter TEXT,producer_instruction_id INTEGER,"
                    + "created_at TEXT NOT NULL,updated_at TEXT NOT NULL,"
                    + "PRIMARY KEY(home_banking_id,bot_job_id,id))");
            sql.execute("CREATE TABLE bot_job_runtime_variable_value(home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,variable_id INTEGER NOT NULL,value_state TEXT NOT NULL,"
                    + "raw_value TEXT,void_reason TEXT,value_source TEXT NOT NULL,entry_revision INTEGER NOT NULL,"
                    + "last_execution_id INTEGER,updated_at TEXT NOT NULL,"
                    + "PRIMARY KEY(home_banking_id,bot_job_id,variable_id))");
            sql.execute("CREATE TABLE instruction_variable_slot(home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,instruction_id INTEGER NOT NULL,slot TEXT NOT NULL,"
                    + "variable_id INTEGER NOT NULL,slot_revision INTEGER NOT NULL,created_at TEXT NOT NULL,"
                    + "updated_at TEXT NOT NULL,PRIMARY KEY(home_banking_id,bot_job_id,instruction_id,slot))");
            sql.execute("CREATE TABLE instruction_variable_command_config(home_banking_id INTEGER NOT NULL,"
                    + "bot_job_id INTEGER NOT NULL,instruction_id INTEGER NOT NULL,command_type TEXT NOT NULL,"
                    + "operand_kind TEXT,comparison_operator TEXT,operand_raw_value TEXT,operand_variable_id INTEGER,"
                    + "output_key TEXT,output_column TEXT,output_file TEXT,external_source_key TEXT,format_policy TEXT,"
                    + "config_revision INTEGER NOT NULL,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,"
                    + "PRIMARY KEY(home_banking_id,bot_job_id,instruction_id))");

            sql.execute("INSERT INTO bot_job VALUES(29,'Lloyds','source','Web App',13,15,1)");
            sql.execute("INSERT INTO block VALUES(10,1,'Login','',NULL,NULL,1,NULL,29),"
                    + "(20,2,'Next','',NULL,NULL,1,NULL,29)");
            sql.execute("INSERT INTO instruction(id,instruction_order_number,actions,name,active,block_id,"
                    + "parent_block_id,parent_id,bot_job_id,client_named) VALUES"
                    + "(100,1,'INPUT','username',1,10,NULL,NULL,29,'User Name'),"
                    + "(101,2,'GET','account_value',1,10,NULL,NULL,29,NULL),"
                    + "(102,3,'CK','check_account',1,10,NULL,101,29,NULL),"
                    + "(103,1,'GOTO','GOTO',1,20,10,NULL,29,NULL)");
            sql.execute("INSERT INTO bot_job_variable_definition VALUES"
                    + "(13,29,7,'STRING','account','configured',NULL,NULL,101,'before','before')");
            sql.execute("INSERT INTO instruction_variable_slot VALUES"
                    + "(13,29,102,'LEFT',7,4,'before','before')");
            sql.execute("INSERT INTO instruction_variable_command_config VALUES"
                    + "(13,29,102,'CK','VARIABLE','EQUALS',NULL,7,NULL,NULL,NULL,NULL,NULL,3,'before','before')");
            sql.execute("INSERT INTO reference(reference_type,value,instruction_id,bot_job_id)"
                    + " VALUES('DATA','account',101,29)");
        }
    }

    @AfterEach
    void restoreConfiguration() {
        Properties configured = properties.getProperties();
        restore(configured, ARPropertyEnum.DATABASE_TYPE, previousDatabaseType);
        restore(configured, ARPropertyEnum.PATH_DB, previousDatabasePath);
    }

    @Test
    void clonesNormalizedVariableGraphWithoutLegacyInstructionVariableId() throws Exception {
        HomeUrlDTO destination = new HomeUrlDTO();
        destination.setId(16);
        destination.setHomeBankingId(HOME_BANKING_ID);

        assertNull(database.cloneBotJob(destination, SOURCE_JOB_ID, "Lloyds Copy", "copy", "Web App"));
        int clonedJobId = database.getNewBotBojId(SOURCE_JOB_ID);
        assertTrue(clonedJobId > 0);
        assertNull(database.cloneBlock(SOURCE_JOB_ID));
        assertNull(database.cloneInstructions(SOURCE_JOB_ID));
        assertNull(database.cloneVariables(SOURCE_JOB_ID));
        assertNull(database.cloneUpdateInstruction(SOURCE_JOB_ID));
        assertNull(database.cloneReferences(SOURCE_JOB_ID));

        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            assertEquals(4, number(sql, "SELECT COUNT(*) FROM instruction WHERE bot_job_id=" + clonedJobId));
            assertEquals(1, number(sql, "SELECT COUNT(*) FROM bot_job_variable_definition"
                    + " WHERE home_banking_id=13 AND bot_job_id=" + clonedJobId));
            assertEquals(1, number(sql, "SELECT COUNT(*) FROM instruction_variable_slot"
                    + " WHERE home_banking_id=13 AND bot_job_id=" + clonedJobId));
            assertEquals(1, number(sql, "SELECT COUNT(*) FROM instruction_variable_command_config"
                    + " WHERE home_banking_id=13 AND bot_job_id=" + clonedJobId));
            assertEquals(1, number(sql, "SELECT COUNT(*) FROM reference WHERE bot_job_id=" + clonedJobId));

            int destinationProducer = number(sql, "SELECT producer_instruction_id"
                    + " FROM bot_job_variable_definition WHERE bot_job_id=" + clonedJobId);
            assertEquals("account_value", text(sql, "SELECT name FROM instruction WHERE id=" + destinationProducer));
            int destinationVariable = number(sql, "SELECT id FROM bot_job_variable_definition"
                    + " WHERE bot_job_id=" + clonedJobId);
            assertEquals(destinationVariable, number(sql, "SELECT variable_id FROM instruction_variable_slot"
                    + " WHERE bot_job_id=" + clonedJobId));
            assertEquals(destinationVariable, number(sql, "SELECT operand_variable_id"
                    + " FROM instruction_variable_command_config WHERE bot_job_id=" + clonedJobId));

            int clonedParent = number(sql, "SELECT parent_id FROM instruction"
                    + " WHERE bot_job_id=" + clonedJobId + " AND name='check_account'");
            assertEquals("account_value", text(sql, "SELECT name FROM instruction WHERE id=" + clonedParent));
            int clonedParentBlock = number(sql, "SELECT parent_block_id FROM instruction"
                    + " WHERE bot_job_id=" + clonedJobId + " AND name='GOTO'");
            assertEquals("Login", text(sql, "SELECT name FROM block WHERE id=" + clonedParentBlock));
            assertEquals(0, number(sql, "SELECT COUNT(*) FROM pragma_table_info('instruction')"
                    + " WHERE name='variable_id'"));
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:"
                + temporaryDirectory.resolve("database.db"));
    }

    private int number(Statement sql, String query) throws Exception {
        try (ResultSet rows = sql.executeQuery(query)) {
            return rows.next() ? rows.getInt(1) : -1;
        }
    }

    private String text(Statement sql, String query) throws Exception {
        try (ResultSet rows = sql.executeQuery(query)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    private void restore(Properties configured, ARPropertyEnum property, String value) {
        if (value == null) configured.remove(property.getValue());
        else configured.setProperty(property.getValue(), value);
    }
}
