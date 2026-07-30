package com.allinweb.ch.facade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerformDataBaseBlockDeleteBatchTest {

    private static final int OWNER_ID = 77;
    private static final int OTHER_OWNER_ID = 88;

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
        createSchemaAndSeed();
    }

    @AfterEach
    void restoreDatabaseConfiguration() {
        restore(ARPropertyEnum.DATABASE_TYPE, previousDatabaseType);
        restore(ARPropertyEnum.PATH_DB, previousDatabasePath);
    }

    @Test
    void selectAllRetainsDatabaseFirstBlockAndClearsItsGraph() throws Exception {
        PerformDataBase.BlockDeleteBatchResult result = database.deleteBlocksGraphAtomic(
                "block", OWNER_ID, List.of(30, 10, 20), null, null);

        assertNull(result.error());
        assertEquals(List.of(30, 20), result.deletedBlockIds());
        assertEquals(10, result.retainedBlockId());
        try (Connection connection = connection()) {
            assertEquals(List.of("10:1"), blocks(connection, OWNER_ID));
            assertEquals(0, count(connection, "instruction", OWNER_ID));
            assertEquals(0, count(connection, "variable", OWNER_ID));
            assertEquals(0, count(connection, "reference", OWNER_ID));
            assertEquals(List.of("90:1"), blocks(connection, OTHER_OWNER_ID));
            assertEquals(1, count(connection, "instruction", OTHER_OWNER_ID));
            assertEquals(1, count(connection, "variable", OTHER_OWNER_ID));
            assertEquals(1, count(connection, "reference", OTHER_OWNER_ID));
        }
    }

    @Test
    void deletesOnlyTheRequestedOwnedSubsetAndNormalizesRemainingOrder() throws Exception {
        PerformDataBase.BlockDeleteBatchResult result = database.deleteBlocksGraphAtomic(
                "block", OWNER_ID, List.of(20), null, null);

        assertNull(result.error());
        assertEquals(List.of(20), result.deletedBlockIds());
        assertNull(result.retainedBlockId());
        try (Connection connection = connection()) {
            assertEquals(List.of("10:1", "30:2"), blocks(connection, OWNER_ID));
            assertEquals(2, count(connection, "instruction", OWNER_ID));
            assertEquals(2, count(connection, "variable", OWNER_ID));
            assertEquals(2, count(connection, "reference", OWNER_ID));
        }
    }

    @Test
    void refusesBlocksOwnedByAnotherBotJobWithoutChangingTheOwnerGraph() throws Exception {
        PerformDataBase.BlockDeleteBatchResult result = database.deleteBlocksGraphAtomic(
                "block", OWNER_ID, List.of(90), null, null);

        assertNotNull(result.error());
        try (Connection connection = connection()) {
            assertEquals(List.of("10:1", "20:2", "30:3"), blocks(connection, OWNER_ID));
            assertEquals(3, count(connection, "instruction", OWNER_ID));
            assertEquals(List.of("90:1"), blocks(connection, OTHER_OWNER_ID));
        }
    }

    private void createSchemaAndSeed() throws SQLException {
        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE block(id INTEGER PRIMARY KEY, bot_job_id INTEGER NOT NULL, "
                    + "block_order_number INTEGER NOT NULL)");
            sql.execute("CREATE TABLE instruction(id INTEGER PRIMARY KEY, bot_job_id INTEGER NOT NULL, "
                    + "block_id INTEGER NOT NULL, parent_block_id INTEGER)");
            sql.execute("CREATE TABLE variable(id INTEGER PRIMARY KEY, bot_job_id INTEGER NOT NULL, "
                    + "instruction_id INTEGER NOT NULL)");
            sql.execute("CREATE TABLE reference(id INTEGER PRIMARY KEY, bot_job_id INTEGER NOT NULL, "
                    + "instruction_id INTEGER NOT NULL)");
            sql.execute("INSERT INTO block VALUES(10,77,1),(20,77,2),(30,77,3),(90,88,1)");
            sql.execute("INSERT INTO instruction VALUES(101,77,10,NULL),(201,77,20,NULL),"
                    + "(301,77,30,NULL),(901,88,90,NULL)");
            sql.execute("INSERT INTO variable VALUES(1001,77,101),(2001,77,201),"
                    + "(3001,77,301),(9001,88,901)");
            sql.execute("INSERT INTO reference VALUES(1002,77,101),(2002,77,201),"
                    + "(3002,77,301),(9002,88,901)");
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + temporaryDirectory.resolve("database.db"));
    }

    private List<String> blocks(Connection connection, int ownerId) throws SQLException {
        try (Statement sql = connection.createStatement();
                ResultSet rows = sql.executeQuery("SELECT id,block_order_number FROM block WHERE bot_job_id="
                        + ownerId + " ORDER BY block_order_number,id")) {
            java.util.ArrayList<String> values = new java.util.ArrayList<>();
            while (rows.next()) values.add(rows.getInt("id") + ":" + rows.getInt("block_order_number"));
            return List.copyOf(values);
        }
    }

    private int count(Connection connection, String table, int ownerId) throws SQLException {
        try (Statement sql = connection.createStatement();
                ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE bot_job_id=" + ownerId)) {
            return rows.next() ? rows.getInt(1) : -1;
        }
    }

    private void restore(ARPropertyEnum property, String value) {
        Properties configured = properties.getProperties();
        if (value == null) configured.remove(property.getValue());
        else configured.setProperty(property.getValue(), value);
    }
}
