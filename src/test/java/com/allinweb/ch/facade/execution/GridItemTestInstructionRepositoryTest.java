package com.allinweb.ch.facade.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.model.GridItemTestActionContracts.Action;
import com.allinweb.ch.model.InstructionLoad;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GridItemTestInstructionRepositoryTest {
    @TempDir
    Path tempDirectory;

    @Test
    void loadsOnlyTheOwnedInstructionAndStoredLocatorFacts() throws Exception {
        String url = database("grid-test-instruction.db");
        bootstrap(url);
        GridItemTestInstructionRepository repository = repository(url);

        GridItemTestInstructionRepository.InstructionSnapshot snapshot =
                repository.load(2, 29, 101);

        assertEquals(101, snapshot.id());
        assertEquals("Login", snapshot.blockName());
        assertEquals("Customer number", snapshot.displayKey());
        assertEquals("//input[@id='customer']", snapshot.xpath());
        assertEquals(1, snapshot.references().size());
        assertEquals("placeholder", snapshot.references().get(0).type());

        InstructionLoad click = snapshot.toInstructionLoad(Action.CLICK);
        InstructionLoad input = snapshot.toInstructionLoad(Action.INPUT);
        assertEquals("C", click.getActions());
        assertEquals("I", input.getActions());
        assertEquals(2, input.getHomeBankingId());
        assertEquals(29, input.getBotJobId());
        assertEquals("placeholder", input.getReferenceLoadDTOList().get(0).getReferenceType());
    }

    @Test
    void rejectsAnotherOrganizationOrBotJob() throws Exception {
        String url = database("grid-test-owner.db");
        bootstrap(url);
        GridItemTestInstructionRepository repository = repository(url);

        SQLException organization = assertThrows(
                SQLException.class, () -> repository.load(3, 29, 101));
        SQLException botJob = assertThrows(
                SQLException.class, () -> repository.load(2, 30, 101));

        assertTrue(organization.getMessage().contains("does not belong"));
        assertTrue(botJob.getMessage().contains("does not belong"));
    }

    private GridItemTestInstructionRepository repository(String url) {
        return new GridItemTestInstructionRepository(() -> DriverManager.getConnection(url));
    }

    private String database(String name) {
        return "jdbc:sqlite:" + tempDirectory.resolve(name);
    }

    private void bootstrap(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE bot_job (id INTEGER PRIMARY KEY,name TEXT,priority TEXT,"
                            + "home_banking_id INTEGER NOT NULL)");
            statement.execute(
                    "CREATE TABLE block (id INTEGER PRIMARY KEY,block_order_number INTEGER NOT NULL,"
                            + "name TEXT NOT NULL,active INTEGER NOT NULL,wait INTEGER,export_file TEXT,"
                            + "bot_job_id INTEGER NOT NULL)");
            statement.execute(
                    "CREATE TABLE instruction (id INTEGER PRIMARY KEY,bot_job_id INTEGER NOT NULL,"
                            + "block_id INTEGER NOT NULL,instruction_order_number INTEGER NOT NULL,"
                            + "actions TEXT,name TEXT,client_named TEXT,operation TEXT,xpath TEXT,"
                            + "coordinates TEXT,force_coordinates TEXT,iframe_xpath TEXT,tag_name TEXT,"
                            + "shadow_host TEXT,shadow_root TEXT,css_selector TEXT,description TEXT,"
                            + "default_value TEXT,optional INTEGER,block_marked INTEGER,"
                            + "action_custom_max_wait_sec INTEGER,on_hold_seconds INTEGER,codified INTEGER,"
                            + "export_to_abr INTEGER,active INTEGER,parent_id INTEGER,parent_block_id INTEGER)");
            statement.execute(
                    "CREATE TABLE reference (id INTEGER PRIMARY KEY,reference_type TEXT,value TEXT,"
                            + "instruction_id INTEGER NOT NULL,bot_job_id INTEGER NOT NULL)");
            statement.execute("INSERT INTO bot_job VALUES (29,'Lloyds','Web App',2)");
            statement.execute("INSERT INTO block VALUES (10,1,'Login',1,0,'',29)");
            statement.execute(
                    "INSERT INTO instruction VALUES (101,29,10,1,'I','Customer','Customer number','',"
                            + "'//input[@id=''customer'']','','','','input','','','#customer','',NULL,"
                            + "0,0,6,0,0,0,1,NULL,NULL)");
            statement.execute(
                    "INSERT INTO reference VALUES (501,'placeholder','Customer number',101,29)");
        }
    }
}
