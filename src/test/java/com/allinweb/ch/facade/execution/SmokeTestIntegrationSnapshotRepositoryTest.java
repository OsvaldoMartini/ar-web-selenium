package com.allinweb.ch.facade.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.InstructionSnapshot;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Owner;
import com.allinweb.ch.facade.execution.SmokeTestIntegrationSnapshotRepository.Plan;
import com.allinweb.ch.model.InstructionLoad;
import com.allinweb.ch.model.SmokeTestIntegrationContracts.Scope;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SmokeTestIntegrationSnapshotRepositoryTest {
    @TempDir
    Path tempDirectory;

    @Test
    void loadsAnImmutableOwnerScopedPlanFromCurrentTablesOnly() throws Exception {
        String url = database("authoritative-plan.db");
        bootstrap(url);
        SmokeTestIntegrationSnapshotRepository repository = repository(url);

        Plan first = repository.load(new Owner(2, 5), Scope.all());
        Plan repeated = repository.load(new Owner(2, 5), Scope.all());

        assertEquals(new Owner(2, 5), first.owner());
        assertEquals("Bank One", first.environment().organizationName());
        assertEquals("https://bank.example/login", first.environment().url());
        assertEquals("--start-maximized", first.environment().optionsConfig());
        assertEquals("Chrome", first.environment().browserType());
        assertEquals(List.of(10, 20), first.blocks().stream().map(block -> block.id()).toList());
        assertEquals(List.of(101, 102, 201), first.instructions().stream()
                .map(InstructionSnapshot::id)
                .toList());
        assertNull(first.block(30), "inactive Blocks must not enter an ALL execution plan");
        assertNull(first.instruction(103), "inactive instructions must not enter the plan");
        assertNull(first.instruction(301), "instructions in inactive Blocks must not enter the plan");
        assertNull(first.instruction(999), "another Bot Job must never enter the plan");

        InstructionSnapshot element = first.instruction(101);
        assertEquals("//button[@id='login']", element.xpath());
        assertEquals(1, element.references().size());
        assertEquals("role", element.references().get(0).type());
        assertEquals("button", element.references().get(0).value());

        assertEquals(501, first.variableId(102, "get_write"));
        assertEquals(502, first.variableId(201, "READ_SET"));
        assertNull(first.variableId(101, "LEFT"));

        InstructionLoad adapter = element.toInstructionLoad();
        assertEquals(2, adapter.getHomeBankingId());
        assertEquals(5, adapter.getBotJobId());
        assertEquals(10, adapter.getBlockId());
        assertEquals("//button[@id='login']", adapter.getXpath());
        assertEquals("role", adapter.getReferenceLoadDTOList().get(0).getReferenceType());

        assertTrue(first.planRevision().matches("[0-9a-f]{64}"));
        assertEquals(first.planRevision(), repeated.planRevision());
        assertThrows(UnsupportedOperationException.class, () -> first.blocks().clear());
        assertThrows(UnsupportedOperationException.class, () -> element.variableSlots().clear());
    }

    @Test
    void validatesExactOwnerAndRequestedActiveBlockScope() throws Exception {
        String url = database("scope-validation.db");
        bootstrap(url);
        SmokeTestIntegrationSnapshotRepository repository = repository(url);

        Plan blockTwo = repository.load(new Owner(2, 5), Scope.blocks(List.of(20)));
        assertEquals(List.of(20), blockTwo.scope().blockIds());
        assertEquals(List.of(20), blockTwo.blocks().stream().map(block -> block.id()).toList());
        assertEquals(List.of(201), blockTwo.instructions().stream()
                .map(InstructionSnapshot::id)
                .toList());

        SQLException ownerMismatch = assertThrows(
                SQLException.class,
                () -> repository.load(new Owner(3, 5), Scope.all()));
        assertTrue(ownerMismatch.getMessage().contains("not owned"));

        SQLException inactive = assertThrows(
                SQLException.class,
                () -> repository.load(new Owner(2, 5), Scope.blocks(List.of(30))));
        assertTrue(inactive.getMessage().contains("inactive Block"));

        SQLException foreign = assertThrows(
                SQLException.class,
                () -> repository.load(new Owner(2, 5), Scope.blocks(List.of(90))));
        assertTrue(foreign.getMessage().contains("outside"));
    }

    @Test
    void planRevisionIncludesReferencesLocatorsAndVariableSlots() throws Exception {
        String url = database("plan-revision.db");
        bootstrap(url);
        SmokeTestIntegrationSnapshotRepository repository = repository(url);
        String initial = repository.load(new Owner(2, 5), Scope.all()).planRevision();

        execute(url, "UPDATE reference SET value='submit' WHERE id=1001");
        String referenceChanged = repository.load(new Owner(2, 5), Scope.all()).planRevision();
        assertNotEquals(initial, referenceChanged);

        execute(url, "UPDATE instruction SET xpath='//button[@id=''continue'']' WHERE id=101");
        String locatorChanged = repository.load(new Owner(2, 5), Scope.all()).planRevision();
        assertNotEquals(referenceChanged, locatorChanged);

        execute(
                url,
                "UPDATE instruction_variable_slot SET variable_id=502"
                        + " WHERE home_banking_id=2 AND bot_job_id=5"
                        + " AND instruction_id=102 AND slot='GET_WRITE'");
        Plan slotChanged = repository.load(new Owner(2, 5), Scope.all());
        assertNotEquals(locatorChanged, slotChanged.planRevision());
        assertEquals(502, slotChanged.variableId(102, "GET_WRITE"));
    }

    @Test
    void rejectsASlotWhoseVariableDefinitionIsNotOwnedByTheBotJob() throws Exception {
        String url = database("slot-owner.db");
        bootstrap(url);
        execute(
                url,
                "UPDATE instruction_variable_slot SET variable_id=999"
                        + " WHERE home_banking_id=2 AND bot_job_id=5"
                        + " AND instruction_id=102 AND slot='GET_WRITE'");

        SQLException error = assertThrows(
                SQLException.class,
                () -> repository(url).load(new Owner(2, 5), Scope.all()));

        assertTrue(error.getMessage().contains("is not owned"));
    }

    private SmokeTestIntegrationSnapshotRepository repository(String url) {
        return new SmokeTestIntegrationSnapshotRepository(
                () -> DriverManager.getConnection(url),
                () -> "Chrome");
    }

    private String database(String name) {
        return "jdbc:sqlite:" + tempDirectory.resolve(name);
    }

    private void execute(String url, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void bootstrap(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE home_banking (id INTEGER PRIMARY KEY,name TEXT,options_config TEXT)");
            statement.execute(
                    "CREATE TABLE home_url (id INTEGER PRIMARY KEY,name TEXT,url TEXT,"
                            + "home_banking_id INTEGER NOT NULL)");
            statement.execute(
                    "CREATE TABLE bot_job (id INTEGER PRIMARY KEY,name TEXT,priority TEXT,"
                            + "active INTEGER NOT NULL,home_banking_id INTEGER NOT NULL,"
                            + "home_url_id INTEGER NOT NULL)");
            statement.execute(
                    "CREATE TABLE block (id INTEGER PRIMARY KEY,block_order_number INTEGER NOT NULL,"
                            + "name TEXT NOT NULL,description TEXT,type_id INTEGER,export_file TEXT,"
                            + "active INTEGER NOT NULL,wait INTEGER,bot_job_id INTEGER NOT NULL)");
            statement.execute(
                    "CREATE TABLE instruction (id INTEGER PRIMARY KEY,bot_job_id INTEGER NOT NULL,"
                            + "block_id INTEGER NOT NULL,instruction_order_number INTEGER NOT NULL,"
                            + "actions TEXT,name TEXT,client_named TEXT,operation TEXT,xpath TEXT,"
                            + "coordinates TEXT,force_coordinates TEXT,iframe_xpath TEXT,tag_name TEXT,"
                            + "shadow_host TEXT,shadow_root TEXT,css_selector TEXT,description TEXT,"
                            + "default_value TEXT,optional INTEGER,block_marked INTEGER,"
                            + "action_custom_max_wait_sec INTEGER,on_hold_seconds INTEGER,codified INTEGER,"
                            + "export_to_abr INTEGER,active INTEGER NOT NULL,parent_id INTEGER,"
                            + "parent_block_id INTEGER)");
            statement.execute(
                    "CREATE TABLE reference (id INTEGER PRIMARY KEY,reference_type TEXT,value TEXT,"
                            + "instruction_id INTEGER NOT NULL,bot_job_id INTEGER NOT NULL)");
            statement.execute(
                    "CREATE TABLE bot_job_variable_definition (home_banking_id INTEGER NOT NULL,"
                            + "bot_job_id INTEGER NOT NULL,id INTEGER NOT NULL,variable_type TEXT,"
                            + "name TEXT NOT NULL,configured_value TEXT,local_format TEXT,delimiter TEXT,"
                            + "producer_instruction_id INTEGER,created_at TEXT,updated_at TEXT,"
                            + "PRIMARY KEY(home_banking_id,bot_job_id,id))");
            statement.execute(
                    "CREATE TABLE instruction_variable_slot (home_banking_id INTEGER NOT NULL,"
                            + "bot_job_id INTEGER NOT NULL,instruction_id INTEGER NOT NULL,slot TEXT NOT NULL,"
                            + "variable_id INTEGER NOT NULL,slot_revision INTEGER NOT NULL,"
                            + "created_at TEXT,updated_at TEXT,"
                            + "PRIMARY KEY(home_banking_id,bot_job_id,instruction_id,slot))");

            statement.execute(
                    "INSERT INTO home_banking VALUES"
                            + " (2,'Bank One','--start-maximized'),"
                            + " (3,'Other Bank','--headless')");
            statement.execute(
                    "INSERT INTO home_url VALUES"
                            + " (7,'Login','https://bank.example/login',2),"
                            + " (8,'Other','https://other.example',3)");
            statement.execute(
                    "INSERT INTO bot_job VALUES"
                            + " (5,'Payment Flow','Web App',1,2,7),"
                            + " (6,'Other Flow','Web App',1,3,8)");
            statement.execute(
                    "INSERT INTO block VALUES"
                            + " (10,1,'Login','','1','',1,0,5),"
                            + " (20,2,'Payment','','1','',1,0,5),"
                            + " (30,3,'Disabled','','1','',0,0,5),"
                            + " (90,1,'Other','','1','',1,0,6)");

            statement.execute(
                    "INSERT INTO instruction"
                            + " (id,bot_job_id,block_id,instruction_order_number,actions,name,client_named,"
                            + " operation,xpath,coordinates,force_coordinates,iframe_xpath,tag_name,"
                            + " shadow_host,shadow_root,css_selector,description,default_value,optional,"
                            + " block_marked,action_custom_max_wait_sec,on_hold_seconds,codified,"
                            + " export_to_abr,active,parent_id,parent_block_id) VALUES"
                            + " (101,5,10,1,'C','Login button',NULL,'',"
                            + " '//button[@id=''login'']','','','','button','','','button.login','',"
                            + " NULL,0,0,6,0,0,0,1,NULL,NULL),"
                            + " (102,5,10,2,'GET','Read message',NULL,'','', '', '', '', '', '', '', '', '',"
                            + " NULL,0,0,6,0,0,0,1,101,NULL),"
                            + " (103,5,10,3,'REFRESH','Disabled instruction',NULL,'','', '', '', '', '', '', '', '', '',"
                            + " NULL,0,0,6,0,0,0,0,NULL,NULL),"
                            + " (201,5,20,1,'SET','Write value','Client value','','#target', '', '', '', 'input', '', '', '', '',"
                            + " '  ',0,0,6,0,1,0,1,101,NULL),"
                            + " (301,5,30,1,'C','Inactive Block row',NULL,'','', '', '', '', 'button', '', '', '', '',"
                            + " NULL,0,0,6,0,0,0,1,NULL,NULL),"
                            + " (999,6,90,1,'C','Other owner row',NULL,'','//other', '', '', '', 'button', '', '', '', '',"
                            + " NULL,0,0,6,0,0,0,1,NULL,NULL)");
            statement.execute(
                    "INSERT INTO reference VALUES"
                            + " (1001,'role','button',101,5),"
                            + " (9001,'role','other',999,6)");
            statement.execute(
                    "INSERT INTO bot_job_variable_definition"
                            + " (home_banking_id,bot_job_id,id,variable_type,name,created_at,updated_at) VALUES"
                            + " (2,5,501,'$String','Message','2026-08-06','2026-08-06'),"
                            + " (2,5,502,'$String','Input','2026-08-06','2026-08-06'),"
                            + " (3,6,999,'$String','Other','2026-08-06','2026-08-06')");
            statement.execute(
                    "INSERT INTO instruction_variable_slot VALUES"
                            + " (2,5,102,'GET_WRITE',501,1,'2026-08-06','2026-08-06'),"
                            + " (2,5,201,'READ_SET',502,1,'2026-08-06','2026-08-06'),"
                            + " (3,6,999,'GET_WRITE',999,1,'2026-08-06','2026-08-06')");
        }
    }
}
