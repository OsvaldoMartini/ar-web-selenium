package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Getter;
import lombok.Setter;

/**
 * PerformActions.
 *
 * @author Osvaldo Martini
 * @version 1.0
 */
public class ExportAccessToSQLite {
    protected static volatile ExportAccessToSQLite instance;

    @Getter
    @Setter
    public Connection conn = null;

    private TreeMap<Integer, Integer> homeBankMap = new TreeMap<>();
    private TreeMap<Integer, Integer> homeUrlMap = new TreeMap<>();
    private TreeMap<Integer, Integer> botJobMap = new TreeMap<>();
    private TreeMap<Integer, Integer> blockMap = new TreeMap<>();
    private TreeMap<Integer, Integer> instructionMap = new TreeMap<>();
    private TreeMap<Integer, Integer> variableMap = new TreeMap<>();
    private TreeMap<Integer, Integer> referenceMap = new TreeMap<>();

    // Private constructor to prevent instantiation
    private ExportAccessToSQLite() {
        // Initialize if necessary
    }

    public static ExportAccessToSQLite getInstance() {
        if (instance == null) {
            synchronized (ExportAccessToSQLite.class) {
                if (instance == null) {
                    instance = new ExportAccessToSQLite();
                }
            }
        }
        return instance;
    }

    public final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    public final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    public final String CONNECTION_TYPE_SQLITE = "jdbc:sqlite:"; // no parameters needed

    private static final ARPropertyManager arPropertyManager;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
    }

    public void initialize(Connection conn) {
        this.conn = conn;
    }

    public void exportHomeBanking() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String accessDbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;
        ARLogger.getInstance(PerformDataBase.class).info("ACCESS connection URL: " + accessDbUrl);

        String sqliteDbUrl = CONNECTION_TYPE_SQLITE + dbPath + ARConstants.FILE_NAME_SQLITE;

        ARLogger.getInstance(PerformDataBase.class).info("SQLite connection URL: " + sqliteDbUrl);

        final int BATCH_SIZE = 100;

        String selectIdsSQL = "SELECT id FROM home_banking ORDER BY id";

        try (Connection accessConn = DriverManager.getConnection(accessDbUrl);
                Connection sqliteConn = DriverManager.getConnection(sqliteDbUrl);
                Statement accessStmt = accessConn.createStatement();
                Statement idStmtBefore = sqliteConn.createStatement();
                Statement idStmtAfter = sqliteConn.createStatement()) {

            sqliteConn.setAutoCommit(false);

            // Step 1: get existing IDs from PostgreSQL before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            // Step 2: get data from Access
            String selectAccessSQL =
                    "SELECT ID, url, name, priority, search_config, options_config, cookies, driver_session, username, password FROM home_banking ORDER BY id";
            ResultSet rsHomeBank = accessStmt.executeQuery(selectAccessSQL);

            String checkSQL = "SELECT id FROM home_banking WHERE url = ?";
            String insertSQL =
                    "INSERT INTO home_banking (url, name, priority, search_config, options_config, cookies, driver_session, username, password) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            homeBankMap.clear();

            try (PreparedStatement checkStmt = sqliteConn.prepareStatement(checkSQL);
                    PreparedStatement insertStmt = sqliteConn.prepareStatement(insertSQL)) {

                int count = 0;

                while (rsHomeBank.next()) {
                    int oldId = rsHomeBank.getInt("id");
                    String url = rsHomeBank.getString("url");

                    if (url != null && !url.trim().isEmpty()) {
                        homeBankMap.put(oldId, -1); // initialize with -1
                    }

                    // Check existence in Postgres by url
                    checkStmt.setString(1, url);
                    try (ResultSet checkResult = checkStmt.executeQuery()) {
                        if (!checkResult.next()) {
                            // Insert new row
                            insertStmt.setString(1, url);
                            insertStmt.setString(2, rsHomeBank.getString("name"));
                            insertStmt.setString(3, rsHomeBank.getString("priority"));
                            insertStmt.setString(4, rsHomeBank.getString("search_config"));
                            insertStmt.setString(5, rsHomeBank.getString("options_config"));
                            insertStmt.setString(6, rsHomeBank.getString("cookies"));
                            insertStmt.setString(7, rsHomeBank.getString("driver_session"));
                            insertStmt.setString(8, rsHomeBank.getString("username"));
                            insertStmt.setString(9, rsHomeBank.getString("password"));

                            insertStmt.addBatch();
                            count++;

                            if (count % BATCH_SIZE == 0) {
                                insertStmt.executeBatch();
                                sqliteConn.commit();
                                System.out.println("Inserted batch of " + BATCH_SIZE);
                            }
                        } else {
                            System.out.println("Skipped (exists): " + url);
                        }
                    }
                }

                // Final batch insert
                if (count % BATCH_SIZE != 0) {
                    insertStmt.executeBatch();
                    sqliteConn.commit();
                    System.out.println("Inserted final batch of " + (count % BATCH_SIZE));
                }
            }

            // Step 3: get all IDs after insertion
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs (those in idsAfter but not in idsBefore)
            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            // Step 5: Now you want to map old Access IDs to new Postgres IDs.
            // But you only have old IDs from Access, and new IDs from Postgres.
            // The challenge is you inserted rows without specifying IDs (autoincrement).
            // So how to match oldId to newId? If order matches, you can pair them in order.

            // Let's create a list of old IDs that were inserted (those with -1 in homeBankMap)
            List<Integer> insertedOldIds = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : homeBankMap.entrySet()) {
                if (entry.getValue() == -1) {
                    insertedOldIds.add(entry.getKey());
                }
            }

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println("Mismatch: inserted old IDs count = " + insertedOldIds.size()
                        + " but new IDs count = " + newIds.size());
            } else {
                for (int i = 0; i < insertedOldIds.size(); i++) {
                    homeBankMap.put(insertedOldIds.get(i), newIds.get(i));
                }
            }

            System.out.println("Mapping of old to new home_banking IDs: " + homeBankMap);

            System.out.println("Sync completed.");

        } catch (SQLException error) {
            ARLogger.getInstance(PerformDataBase.class).severe("Error export HomeBanking: " + error.getMessage());
        }
    }

    public void exportHomeUrl() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String accessDbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;
        ARLogger.getInstance(PerformDataBase.class).info("ACCESS connection URL: " + accessDbUrl);

        String sqliteDbUrl = CONNECTION_TYPE_SQLITE + dbPath + ARConstants.FILE_NAME_SQLITE;

        ARLogger.getInstance(PerformDataBase.class).info("SQLite connection URL: " + sqliteDbUrl);

        final int BATCH_SIZE = 100;
        String selectIdsSQL = "SELECT id FROM home_url ORDER BY id";

        try (Connection accessConn = DriverManager.getConnection(accessDbUrl);
                Connection sqliteConn = DriverManager.getConnection(sqliteDbUrl);
                Statement accessStmt = accessConn.createStatement();
                Statement idStmtBefore = sqliteConn.createStatement();
                Statement idStmtAfter = sqliteConn.createStatement()) {

            sqliteConn.setAutoCommit(false);

            // Step 1: Get existing IDs from PostgreSQL before inserts
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            // Step 2: Select data from Access
            String selectAccessSQL = "SELECT id, home_banking_id, url FROM home_url ORDER BY id";
            try (ResultSet rsHomeUrl = accessStmt.executeQuery(selectAccessSQL)) {

                String insertHomeUrlSQL = "INSERT INTO home_url (url, home_banking_id) VALUES (?, ?)";
                try (PreparedStatement insertStmt = sqliteConn.prepareStatement(insertHomeUrlSQL)) {

                    int count = 0;
                    homeUrlMap.clear();

                    while (rsHomeUrl.next()) {
                        int oldId = rsHomeUrl.getInt("id");
                        String url = rsHomeUrl.getString("url");

                        int oldHomeBankId = rsHomeUrl.getInt("home_banking_id");
                        Integer newHomeBankId = homeBankMap.get(oldHomeBankId);
                        if (newHomeBankId == null) {
                            System.out.println("Skipped home_url with unknown home_banking_id: " + oldHomeBankId);
                            continue;
                        }

                        if (url != null && !url.trim().isEmpty()) {
                            homeUrlMap.put(oldId, -1); // Initialize mapping with -1
                        }

                        insertStmt.setString(1, url);
                        insertStmt.setInt(2, newHomeBankId);
                        insertStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            sqliteConn.commit();
                            System.out.println("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch insert
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        sqliteConn.commit();
                        System.out.println("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: Get IDs from PostgreSQL after inserts
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 4: Determine new IDs inserted
            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            // Step 5: Map old Access IDs to new Postgres IDs by insert order
            List<Integer> insertedOldIds = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : homeUrlMap.entrySet()) {
                if (entry.getValue() == -1) {
                    insertedOldIds.add(entry.getKey());
                }
            }

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println("Mismatch: inserted old IDs count = " + insertedOldIds.size()
                        + " but new IDs count = " + newIds.size());
            } else {
                for (int i = 0; i < insertedOldIds.size(); i++) {
                    homeUrlMap.put(insertedOldIds.get(i), newIds.get(i));
                }
            }

            System.out.println("Mapping of old to new home_url IDs: " + homeUrlMap);

            System.out.println("Sync completed.");
        } catch (SQLException error) {
            ARLogger.getInstance(PerformDataBase.class).severe("Failed to export home_url: " + error.getMessage());
        }
    }

    public void exportBotJob() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String accessDbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;
        ARLogger.getInstance(PerformDataBase.class).info("ACCESS connection URL: " + accessDbUrl);

        String sqliteDbUrl = CONNECTION_TYPE_SQLITE + dbPath + ARConstants.FILE_NAME_SQLITE;

        final int BATCH_SIZE = 100;
        String selectIdsSQL = "SELECT id FROM bot_job ORDER BY id";

        try (Connection accessConn = DriverManager.getConnection(accessDbUrl);
                Connection sqliteConn = DriverManager.getConnection(sqliteDbUrl);
                Statement accessStmt = accessConn.createStatement();
                Statement idStmtBefore = sqliteConn.createStatement();
                Statement idStmtAfter = sqliteConn.createStatement()) {

            sqliteConn.setAutoCommit(false);

            // Step 1: get existing IDs from PostgreSQL before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            String selectAccessFabrizio =
                    "SELECT bot.id, bot.name, bot.description, bot.priority, bot.active, bot.home_banking_id, bot.home_url_id FROM bot_job bot ORDER BY bot.id";
            try (ResultSet rsBotJob = accessStmt.executeQuery(selectAccessFabrizio)) {

                String insertSQL =
                        "INSERT INTO bot_job (name, description, priority, active, home_url_id, home_banking_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?)";

                try (PreparedStatement insertStmt = sqliteConn.prepareStatement(insertSQL)) {

                    int count = 0;
                    botJobMap.clear();

                    while (rsBotJob.next()) {
                        int oldId = rsBotJob.getInt("id");
                        String name = rsBotJob.getString("name");
                        String description = rsBotJob.getString("description");
                        String priority = rsBotJob.getString("priority");
                        int active = rsBotJob.getInt("active");

                        int oldHomeBankId = rsBotJob.getInt("home_banking_id");
                        Integer newHomeBankId = homeBankMap.get(oldHomeBankId);
                        if (newHomeBankId == null) {
                            System.out.println("Skipped bot_job with unknown home_banking_id: " + oldHomeBankId);
                            continue;
                        }

                        // If you have home_url_id mapping (not shown in your code), you can do similar mapping:
                        int oldHomeUrlId = rsBotJob.getInt("home_url_id");
                        Integer newHomeUrlId = homeUrlMap.get(oldHomeUrlId);
                        if (oldHomeUrlId != 0 && newHomeUrlId == null) {
                            System.out.println("Skipped bot_job with unknown home_url_id: " + oldHomeUrlId);
                            continue;
                        }

                        botJobMap.put(oldId, -1); // initialize mapping

                        insertStmt.setString(1, name);
                        insertStmt.setString(2, description);
                        insertStmt.setString(3, priority);
                        insertStmt.setInt(4, active);

                        if (newHomeUrlId != null) {
                            insertStmt.setInt(5, newHomeUrlId);
                        } else {
                            insertStmt.setNull(5, Types.INTEGER);
                        }

                        insertStmt.setInt(6, newHomeBankId);

                        insertStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            sqliteConn.commit();
                            System.out.println("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        sqliteConn.commit();
                        System.out.println("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get IDs from PostgreSQL after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs inserted
            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            // Step 5: map old Access IDs to new Postgres IDs
            List<Integer> insertedOldIds = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : botJobMap.entrySet()) {
                if (entry.getValue() == -1) {
                    insertedOldIds.add(entry.getKey());
                }
            }

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println("Mismatch: inserted old IDs count = " + insertedOldIds.size()
                        + " but new IDs count = " + newIds.size());
            } else {
                for (int i = 0; i < insertedOldIds.size(); i++) {
                    botJobMap.put(insertedOldIds.get(i), newIds.get(i));
                }
            }

            System.out.println("Mapping of old to new bot_job IDs: " + botJobMap);
            System.out.println("Sync completed.");

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class).severe("Failed to export bot_job: " + e.getMessage());
        }
    }

    public void exportBlock() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String accessDbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;
        ARLogger.getInstance(PerformDataBase.class).info("ACCESS connection URL: " + accessDbUrl);

        String sqliteDbUrl = CONNECTION_TYPE_SQLITE + dbPath + ARConstants.FILE_NAME_SQLITE;

        final int BATCH_SIZE = 100;
        String selectIdsSQL = "SELECT id FROM block ORDER BY id";

        try (Connection accessConn = DriverManager.getConnection(accessDbUrl);
                Connection sqliteConn = DriverManager.getConnection(sqliteDbUrl);
                Statement accessStmt = accessConn.createStatement();
                Statement idStmtBefore = sqliteConn.createStatement();
                Statement idStmtAfter = sqliteConn.createStatement()) {

            sqliteConn.setAutoCommit(false);

            // Step 1: get existing block IDs before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            String selectAccessSQL =
                    "SELECT id, block_order_number, name, description, type_id, export_file, active, wait, bot_job_id FROM block ORDER BY id";

            try (ResultSet rs = accessStmt.executeQuery(selectAccessSQL)) {

                String checkExistsSQL =
                        "SELECT id FROM block WHERE block_order_number = ? AND name = ? AND bot_job_id = ?";

                String insertSQL =
                        "INSERT INTO block (block_order_number, name, description, type_id, export_file, active, wait, bot_job_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement insertStmt = sqliteConn.prepareStatement(insertSQL);
                        PreparedStatement checkStmt = sqliteConn.prepareStatement(checkExistsSQL)) {

                    int count = 0;
                    blockMap.clear();

                    while (rs.next()) {
                        int oldId = rs.getInt("id");
                        int blockOrderNumber = rs.getInt("block_order_number");
                        String name = rs.getString("name");
                        String description = rs.getString("description");
                        Integer typeId = rs.getObject("type_id") != null ? rs.getInt("type_id") : null;
                        String exportFile = rs.getString("export_file");
                        int active = rs.getInt("active");
                        Integer wait = rs.getObject("wait") != null ? rs.getInt("wait") : null;
                        int oldBotJobId = rs.getInt("bot_job_id");

                        // Map old bot_job_id to new
                        Integer newBotJobId = botJobMap.get(oldBotJobId);
                        if (newBotJobId == null) {
                            System.out.println("Skipped block with unknown bot_job_id: " + oldBotJobId);
                            continue;
                        }

                        blockMap.put(oldId, -1); // initialize with -1 for mapping later

                        // Check existence
                        checkStmt.setInt(1, blockOrderNumber);
                        checkStmt.setString(2, name);
                        checkStmt.setInt(3, newBotJobId);

                        try (ResultSet checkRs = checkStmt.executeQuery()) {
                            if (!checkRs.next()) {
                                insertStmt.setInt(1, blockOrderNumber);
                                insertStmt.setString(2, name);
                                insertStmt.setString(3, description);

                                if (typeId != null) {
                                    insertStmt.setInt(4, typeId);
                                } else {
                                    insertStmt.setNull(4, Types.INTEGER);
                                }

                                insertStmt.setString(5, exportFile);
                                insertStmt.setInt(6, active);

                                if (wait != null) {
                                    insertStmt.setInt(7, wait);
                                } else {
                                    insertStmt.setNull(7, Types.INTEGER);
                                }

                                insertStmt.setInt(8, newBotJobId);

                                insertStmt.addBatch();
                                count++;
                            } else {
                                System.out.println("Skipped existing block: " + name);
                            }
                        }

                        if (count % BATCH_SIZE == 0 && count > 0) {
                            insertStmt.executeBatch();
                            sqliteConn.commit();
                            System.out.println("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        sqliteConn.commit();
                        System.out.println("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get IDs from PostgreSQL after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs inserted
            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            // Step 5: map old Access IDs to new Postgres IDs
            List<Integer> insertedOldIds = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : blockMap.entrySet()) {
                if (entry.getValue() == -1) {
                    insertedOldIds.add(entry.getKey());
                }
            }

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println("Mismatch: inserted old IDs count = " + insertedOldIds.size()
                        + " but new IDs count = " + newIds.size());
            } else {
                for (int i = 0; i < insertedOldIds.size(); i++) {
                    blockMap.put(insertedOldIds.get(i), newIds.get(i));
                }
            }

            System.out.println("Mapping of old to new block IDs: " + blockMap);
            System.out.println("Sync completed.");

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class).severe("Failed to export block: " + e.getMessage());
        }
    }

    public void exportInstructions() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String accessDbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;
        ARLogger.getInstance(PerformDataBase.class).info("ACCESS connection URL: " + accessDbUrl);

        String sqliteDbUrl = CONNECTION_TYPE_SQLITE + dbPath + ARConstants.FILE_NAME_SQLITE;

        final int BATCH_SIZE = 100;
        String selectIdsSQL = "SELECT id FROM instruction ORDER BY id";

        try (Connection accessConn = DriverManager.getConnection(accessDbUrl);
                Connection sqliteConn = DriverManager.getConnection(sqliteDbUrl);
                Statement accessStmt = accessConn.createStatement();
                Statement idStmtBefore = sqliteConn.createStatement();
                Statement idStmtAfter = sqliteConn.createStatement()) {

            sqliteConn.setAutoCommit(false);

            // Step 1: get existing instruction IDs before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            String selectAccessSQL = "SELECT * FROM instruction ORDER BY id";

            try (ResultSet rsInstruction = accessStmt.executeQuery(selectAccessSQL)) {

                String checkExistsSQL =
                        "SELECT id FROM instruction WHERE instruction_order_number = ? AND name = ? AND bot_job_id = ? AND block_id = ?";
                String insertSQL = "INSERT INTO instruction ("
                        + "instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, block_id, variable_id, parent_id, bot_job_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement insertStmt = sqliteConn.prepareStatement(insertSQL);
                        PreparedStatement checkStmt = sqliteConn.prepareStatement(checkExistsSQL)) {

                    int count = 0;
                    instructionMap.clear();

                    while (rsInstruction.next()) {
                        int oldId = rsInstruction.getInt("id");

                        int oldBotJobId = rsInstruction.getInt("bot_job_id");
                        Integer newBotJobId = botJobMap.get(oldBotJobId);
                        if (newBotJobId == null) {
                            System.out.println("Skipped instruction with unknown bot_job_id: " + oldBotJobId);
                            continue;
                        }

                        int oldBlockId = rsInstruction.getInt("block_id");
                        Integer newBlockId = blockMap.get(oldBlockId);
                        if (newBlockId == null) {
                            System.out.println("Skipped instruction with unknown block_id: " + oldBlockId);
                            continue;
                        }

                        int instructionOrderNumber = rsInstruction.getInt("instruction_order_number");
                        String name = rsInstruction.getString("name");

                        instructionMap.put(oldId, -1); // initialize with -1 for later mapping

                        checkStmt.setInt(1, instructionOrderNumber);
                        checkStmt.setString(2, name);
                        checkStmt.setInt(3, newBotJobId);
                        checkStmt.setInt(4, newBlockId);

                        try (ResultSet checkRs = checkStmt.executeQuery()) {
                            if (!checkRs.next()) {
                                insertStmt.setInt(1, instructionOrderNumber);
                                insertStmt.setString(2, rsInstruction.getString("actions"));
                                insertStmt.setString(3, name);
                                insertStmt.setString(4, rsInstruction.getString("xpath"));
                                insertStmt.setString(5, rsInstruction.getString("coordinates"));

                                insertOrNull(insertStmt, 6, rsInstruction, "force_coordinates");
                                insertStmt.setString(7, rsInstruction.getString("iframe_xpath"));
                                insertStmt.setString(8, rsInstruction.getString("tag_name"));
                                insertStmt.setString(9, rsInstruction.getString("shadow_host"));
                                insertStmt.setString(10, rsInstruction.getString("shadow_root"));
                                insertStmt.setString(11, rsInstruction.getString("css_selector"));
                                insertStmt.setString(12, rsInstruction.getString("description"));
                                insertStmt.setString(13, rsInstruction.getString("operation"));

                                insertOrNull(insertStmt, 14, rsInstruction, "optional");
                                insertOrNull(insertStmt, 15, rsInstruction, "block_marked");
                                insertStmt.setString(16, rsInstruction.getString("default_value"));
                                insertOrNull(insertStmt, 17, rsInstruction, "action_custom_max_wait_sec");
                                insertOrNull(insertStmt, 18, rsInstruction, "on_hold_seconds");
                                insertOrNull(insertStmt, 19, rsInstruction, "codified");
                                insertOrNull(insertStmt, 20, rsInstruction, "export_to_abr");

                                insertStmt.setInt(21, rsInstruction.getInt("active"));
                                insertStmt.setInt(22, newBlockId);

                                insertOrNull(insertStmt, 23, rsInstruction, "variable_id");
                                insertOrNull(insertStmt, 24, rsInstruction, "parent_id");

                                insertStmt.setInt(25, newBotJobId);

                                insertStmt.addBatch();
                                count++;
                            } else {
                                System.out.println("Skipped existing instruction: " + name);
                            }
                        }

                        if (count % BATCH_SIZE == 0 && count > 0) {
                            insertStmt.executeBatch();
                            sqliteConn.commit();
                            System.out.println("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        sqliteConn.commit();
                        System.out.println("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get IDs after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 4: find new IDs inserted
            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            // Step 5: map old IDs to new IDs
            List<Integer> insertedOldIds = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : instructionMap.entrySet()) {
                if (entry.getValue() == -1) {
                    insertedOldIds.add(entry.getKey());
                }
            }

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println("Mismatch: inserted old IDs count = " + insertedOldIds.size()
                        + " but new IDs count = " + newIds.size());
            } else {
                for (int i = 0; i < insertedOldIds.size(); i++) {
                    instructionMap.put(insertedOldIds.get(i), newIds.get(i));
                }
            }

            System.out.println("Mapping of old to new instruction IDs: " + instructionMap);
            System.out.println("Sync completed.");

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class).severe("Failed to export instructions: " + e.getMessage());
        }
    }

    public void exportVariables() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String accessDbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;
        ARLogger.getInstance(PerformDataBase.class).info("ACCESS connection URL: " + accessDbUrl);

        String sqliteDbUrl = CONNECTION_TYPE_SQLITE + dbPath + ARConstants.FILE_NAME_SQLITE;

        final int BATCH_SIZE = 100;
        String selectIdsSQL = "SELECT id FROM variable ORDER BY id";

        try (Connection accessConn = DriverManager.getConnection(accessDbUrl);
                Connection sqliteConn = DriverManager.getConnection(sqliteDbUrl);
                Statement accessStmt = accessConn.createStatement();
                Statement idStmtBefore = sqliteConn.createStatement();
                Statement idStmtAfter = sqliteConn.createStatement()) {

            sqliteConn.setAutoCommit(false);

            // Step 1: get existing variable IDs before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            String selectAccessSQL = "SELECT * FROM variable ORDER BY id";

            try (ResultSet rsVariable = accessStmt.executeQuery(selectAccessSQL)) {

                String checkExistsSQL =
                        "SELECT id FROM variable WHERE type = ? AND name = ? AND instruction_id = ? AND bot_job_id = ?";
                String insertSQL =
                        "INSERT INTO variable (type, name, value, local_format, delimiter, instruction_id, bot_job_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement insertStmt = sqliteConn.prepareStatement(insertSQL);
                        PreparedStatement checkStmt = sqliteConn.prepareStatement(checkExistsSQL)) {

                    int count = 0;
                    variableMap.clear();

                    while (rsVariable.next()) {
                        int oldId = rsVariable.getInt("id");
                        String type = rsVariable.getString("type");
                        String name = rsVariable.getString("name");

                        int oldBotJobId = rsVariable.getInt("bot_job_id");
                        Integer newBotJobId = botJobMap.get(oldBotJobId);
                        if (newBotJobId == null) {
                            System.out.println("Skipped variable with unknown bot_job_id: " + oldBotJobId);
                            continue;
                        }

                        Integer instructionId = rsVariable.getObject("instruction_id") != null
                                ? rsVariable.getInt("instruction_id")
                                : null;

                        Integer newInstructionId = null;
                        if (instructionId != null) {
                            newInstructionId = instructionMap.get(instructionId);
                            if (newInstructionId == null) {
                                System.out.println("Skipped variable with unknown instruction_id: " + instructionId);
                                continue;
                            }
                        }

                        variableMap.put(oldId, -1); // initialize with -1

                        checkStmt.setString(1, type);
                        checkStmt.setString(2, name);

                        if (newInstructionId != null) {
                            checkStmt.setInt(3, newInstructionId);
                        } else {
                            checkStmt.setNull(3, Types.INTEGER);
                        }

                        checkStmt.setInt(4, newBotJobId);

                        try (ResultSet checkRs = checkStmt.executeQuery()) {
                            if (!checkRs.next()) {
                                insertStmt.setString(1, type);
                                insertStmt.setString(2, name);
                                insertStmt.setString(3, rsVariable.getString("value"));
                                insertStmt.setString(4, rsVariable.getString("local_format"));
                                insertStmt.setString(5, rsVariable.getString("delimiter"));

                                if (newInstructionId != null) {
                                    insertStmt.setInt(6, newInstructionId);
                                } else {
                                    insertStmt.setNull(6, Types.INTEGER);
                                }

                                insertStmt.setInt(7, newBotJobId);

                                insertStmt.addBatch();
                                count++;
                            } else {
                                System.out.println("Skipped existing variable: " + name);
                            }
                        }

                        if (count % BATCH_SIZE == 0 && count > 0) {
                            insertStmt.executeBatch();
                            sqliteConn.commit();
                            System.out.println("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        sqliteConn.commit();
                        System.out.println("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get IDs after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 4: find new IDs inserted
            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            // Step 5: map old IDs to new IDs
            List<Integer> insertedOldIds = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : variableMap.entrySet()) {
                if (entry.getValue() == -1) {
                    insertedOldIds.add(entry.getKey());
                }
            }

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println("Mismatch: inserted old IDs count = " + insertedOldIds.size()
                        + " but new IDs count = " + newIds.size());
            } else {
                for (int i = 0; i < insertedOldIds.size(); i++) {
                    variableMap.put(insertedOldIds.get(i), newIds.get(i));
                }
            }

            System.out.println("Mapping of old to new variable IDs: " + variableMap);
            System.out.println("Sync completed.");

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class).severe("Failed to export variables: " + e.getMessage());
        }
    }

    public void exportUpdateInstruction() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String sqliteDbUrl = CONNECTION_TYPE_SQLITE + dbPath + ARConstants.FILE_NAME_SQLITE;

        final int BATCH_SIZE = 100;

        try (Connection sqliteConn = DriverManager.getConnection(sqliteDbUrl);
                Statement postgresStmt = sqliteConn.createStatement()) {
            sqliteConn.setAutoCommit(false);

            String selectAccessSQL =
                    "SELECT id, name, parent_id, variable_id FROM instruction WHERE parent_id IS NOT NULL OR variable_id IS NOT NULL ORDER BY id";
            try (ResultSet rsInstruction = postgresStmt.executeQuery(selectAccessSQL)) {

                String updateSQL = "UPDATE instruction SET variable_id = ?, parent_id = ? WHERE id = ? ";

                try (PreparedStatement updateStmt = sqliteConn.prepareStatement(updateSQL)) {
                    int count = 0;

                    while (rsInstruction.next()) {
                        int id = rsInstruction.getInt("id");
                        String name = rsInstruction.getString("name");

                        // Set variable_id directly from Access
                        int originalVariableId = rsInstruction.getInt("variable_id");
                        // Map old bot_job_id to new
                        Integer newVariableId = variableMap.get(originalVariableId);
                        if (newVariableId == null) {
                            System.out.println(
                                    "Skipped update variable column with unknown variable_id: " + newVariableId);
                            updateStmt.setNull(1, Types.INTEGER);
                        } else {
                            updateStmt.setInt(1, newVariableId);
                        }

                        // Handle parent_id based on name
                        if ("GOTO".equalsIgnoreCase(name) || "EXCEL GOTO".equalsIgnoreCase(name)) {
                            int parentBlockId = rsInstruction.getInt("parent_id");
                            Integer newParentBlockId = blockMap.get(parentBlockId);
                            if (newParentBlockId != null) {
                                updateStmt.setInt(2, newParentBlockId);
                            } else {
                                updateStmt.setNull(2, Types.INTEGER);
                            }
                        } else {
                            int parentInstructionId = rsInstruction.getInt("parent_id");
                            Integer newParentInstructionId = instructionMap.get(parentInstructionId);
                            if (newParentInstructionId != null) {
                                updateStmt.setInt(2, newParentInstructionId);
                            } else {
                                updateStmt.setNull(2, Types.INTEGER);
                            }
                        }

                        updateStmt.setInt(3, id); // WHERE clause: name = ?

                        updateStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            updateStmt.executeBatch();
                            sqliteConn.commit();
                            System.out.println("Updated batch of " + BATCH_SIZE);
                        }
                    }

                    if (count % BATCH_SIZE != 0) {
                        updateStmt.executeBatch();
                        sqliteConn.commit();
                        System.out.println("Updated final batch of " + (count % BATCH_SIZE));
                    }

                    ARLogger.getInstance(PerformDataBase.class).info("Updated instruction records: " + count);
                }
            }

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class).severe("Failed to update instructions");
        }
    }

    public void exportReferences() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String accessDbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;
        ARLogger.getInstance(PerformDataBase.class).info("ACCESS connection URL: " + accessDbUrl);

        String sqliteDbUrl = CONNECTION_TYPE_SQLITE + dbPath + ARConstants.FILE_NAME_SQLITE;

        final int BATCH_SIZE = 100;
        String selectIdsSQL = "SELECT id FROM reference ORDER BY id";

        try (Connection accessConn = DriverManager.getConnection(accessDbUrl);
                Connection sqliteConn = DriverManager.getConnection(sqliteDbUrl);
                Statement accessStmt = accessConn.createStatement();
                Statement idStmtBefore = sqliteConn.createStatement();
                Statement idStmtAfter = sqliteConn.createStatement()) {

            sqliteConn.setAutoCommit(false);

            // Step 1: get IDs before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            String selectAccessSQL = "SELECT * FROM reference ORDER BY id";
            try (ResultSet rs = accessStmt.executeQuery(selectAccessSQL)) {

                String checkExistsSQL =
                        "SELECT id FROM reference WHERE reference_type = ? AND value = ? AND instruction_id = ? AND bot_job_id = ?";
                String insertSQL =
                        "INSERT INTO reference (reference_type, value, instruction_id, bot_job_id) VALUES (?, ?, ?, ?)";

                try (PreparedStatement insertStmt = sqliteConn.prepareStatement(insertSQL);
                        PreparedStatement checkStmt = sqliteConn.prepareStatement(checkExistsSQL)) {
                    int count = 0;
                    referenceMap.clear();

                    while (rs.next()) {
                        int oldId = rs.getInt("id");
                        String referenceType = rs.getString("reference_type");
                        String value = rs.getString("value");

                        int oldInstructionId = rs.getInt("instruction_id");
                        Integer newInstructionId = instructionMap.get(oldInstructionId);
                        if (newInstructionId == null) {
                            System.out.println("Skipped reference with unknown instruction_id: " + oldInstructionId);
                            continue;
                        }

                        int oldBotJobId = rs.getInt("bot_job_id");
                        Integer newBotJobId = botJobMap.get(oldBotJobId);

                        referenceMap.put(oldId, -1); // initialize with -1

                        checkStmt.setString(1, referenceType);
                        checkStmt.setString(2, value);
                        checkStmt.setInt(3, newInstructionId);
                        if (newBotJobId != null) {
                            checkStmt.setInt(4, newBotJobId);
                        } else {
                            checkStmt.setNull(4, Types.INTEGER);
                        }

                        try (ResultSet checkRs = checkStmt.executeQuery()) {
                            if (!checkRs.next()) {
                                insertStmt.setString(1, referenceType);
                                insertStmt.setString(2, value);
                                insertStmt.setInt(3, newInstructionId);
                                if (newBotJobId != null) {
                                    insertStmt.setInt(4, newBotJobId);
                                } else {
                                    insertStmt.setNull(4, Types.INTEGER);
                                }
                                insertStmt.addBatch();
                                count++;
                            } else {
                                System.out.println("Skipped existing reference: " + referenceType);
                            }
                        }

                        if (count % BATCH_SIZE == 0 && count > 0) {
                            insertStmt.executeBatch();
                            sqliteConn.commit();
                            System.out.println("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        sqliteConn.commit();
                        System.out.println("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get IDs after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 4: find new IDs inserted
            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            // Step 5: map old IDs to new IDs
            List<Integer> insertedOldIds = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : referenceMap.entrySet()) {
                if (entry.getValue() == -1) {
                    insertedOldIds.add(entry.getKey());
                }
            }

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println("Mismatch: inserted old IDs count = " + insertedOldIds.size()
                        + " but new IDs count = " + newIds.size());
            } else {
                for (int i = 0; i < insertedOldIds.size(); i++) {
                    referenceMap.put(insertedOldIds.get(i), newIds.get(i));
                }
            }

            System.out.println("Mapping of old to new reference IDs: " + referenceMap);
            System.out.println("Sync completed.");

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class).severe("Failed to export reference table: " + e.getMessage());
        }
    }

    public void exportCompBlock() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String accessDbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;
        ARLogger.getInstance(PerformDataBase.class).info("ACCESS connection URL: " + accessDbUrl);

        String sqliteDbUrl = CONNECTION_TYPE_SQLITE + dbPath + ARConstants.FILE_NAME_SQLITE;

        final int BATCH_SIZE = 100;
        String selectIdsSQL = "SELECT id FROM component_block ORDER BY id";

        try (Connection accessConn = DriverManager.getConnection(accessDbUrl);
                Connection sqliteConn = DriverManager.getConnection(sqliteDbUrl);
                Statement accessStmt = accessConn.createStatement();
                Statement idStmtBefore = sqliteConn.createStatement();
                Statement idStmtAfter = sqliteConn.createStatement()) {

            sqliteConn.setAutoCommit(false);

            // Step 1: Get IDs before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            String selectAccessSQL =
                    "SELECT id, block_order_number, name, description, type_id, export_file, active, wait, home_banking_id FROM component_block ORDER BY id";

            try (ResultSet rs = accessStmt.executeQuery(selectAccessSQL)) {

                String checkExistsSQL =
                        "SELECT id FROM component_block WHERE block_order_number = ? AND name = ? AND home_banking_id = ?";
                String insertSQL =
                        "INSERT INTO component_block (block_order_number, name, description, type_id, export_file, active, wait, home_banking_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement insertStmt = sqliteConn.prepareStatement(insertSQL);
                        PreparedStatement checkStmt = sqliteConn.prepareStatement(checkExistsSQL)) {

                    int count = 0;
                    List<Integer> insertedOldIds = new ArrayList<>();
                    blockMap.clear();

                    while (rs.next()) {
                        int id = rs.getInt("id");
                        int blockOrderNumber = rs.getInt("block_order_number");
                        String name = rs.getString("name");
                        String description = rs.getString("description");
                        Integer typeId = rs.getObject("type_id") != null ? rs.getInt("type_id") : null;
                        String exportFile = rs.getString("export_file");
                        int active = rs.getInt("active");
                        Integer wait = rs.getObject("wait") != null ? rs.getInt("wait") : null;
                        int oldHomeBankId = rs.getInt("home_banking_id");

                        blockMap.put(id, -1); // initialize with -1

                        Integer newHomeBankId = homeBankMap.get(oldHomeBankId);
                        if (newHomeBankId == null) {
                            System.out.println(
                                    "Skipped component_block with unknown home_banking_id: " + oldHomeBankId);
                            continue;
                        }

                        checkStmt.setInt(1, blockOrderNumber);
                        checkStmt.setString(2, name);
                        checkStmt.setInt(3, newHomeBankId);

                        try (ResultSet checkRs = checkStmt.executeQuery()) {
                            if (!checkRs.next()) {
                                insertStmt.setInt(1, blockOrderNumber);
                                insertStmt.setString(2, name);
                                insertStmt.setString(3, description);

                                if (typeId != null) {
                                    insertStmt.setInt(4, typeId);
                                } else {
                                    insertStmt.setNull(4, Types.INTEGER);
                                }

                                insertStmt.setString(5, exportFile);
                                insertStmt.setInt(6, active);

                                if (wait != null) {
                                    insertStmt.setInt(7, wait);
                                } else {
                                    insertStmt.setNull(7, Types.INTEGER);
                                }

                                insertStmt.setInt(8, newHomeBankId);

                                insertStmt.addBatch();
                                insertedOldIds.add(id); // Track only inserted IDs
                                count++;
                            } else {
                                System.out.println("Skipped existing component_block: " + name);
                            }
                        }

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            sqliteConn.commit();
                            System.out.println("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        sqliteConn.commit();
                        System.out.println("Inserted final batch of " + (count % BATCH_SIZE));
                    }

                    // Step 3: get IDs after insert
                    List<Integer> idsAfter = new ArrayList<>();
                    try (ResultSet rsAfter = idStmtAfter.executeQuery(selectIdsSQL)) {
                        while (rsAfter.next()) {
                            idsAfter.add(rsAfter.getInt("id"));
                        }
                    }

                    // Step 4: get new IDs by removing old ones
                    List<Integer> newIds = new ArrayList<>(idsAfter);
                    newIds.removeAll(idsBefore);

                    // Step 5: match and map
                    if (insertedOldIds.size() != newIds.size()) {
                        System.err.println("Mismatch: inserted old IDs = " + insertedOldIds.size() + " but new IDs = "
                                + newIds.size());
                    } else {
                        for (int i = 0; i < insertedOldIds.size(); i++) {
                            blockMap.put(insertedOldIds.get(i), newIds.get(i));
                        }
                    }

                    System.out.println("Mapping of old to new component_block IDs: " + blockMap);
                    System.out.println("Component Block export completed.");
                }
            }

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class).severe("Failed to export component_block: " + e.getMessage());
        }
    }

    public void exportCompInstructions() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String accessDbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;
        ARLogger.getInstance(PerformDataBase.class).info("ACCESS connection URL: " + accessDbUrl);

        String sqliteDbUrl = CONNECTION_TYPE_SQLITE + dbPath + ARConstants.FILE_NAME_SQLITE;

        final int BATCH_SIZE = 100;
        String selectIdsSQL = "SELECT id FROM component_instruction ORDER BY id";

        try (Connection accessConn = DriverManager.getConnection(accessDbUrl);
                Connection sqliteConn = DriverManager.getConnection(sqliteDbUrl);
                Statement accessStmt = accessConn.createStatement();
                Statement idStmtBefore = sqliteConn.createStatement();
                Statement idStmtAfter = sqliteConn.createStatement()) {

            sqliteConn.setAutoCommit(false);

            // Step 1: Get IDs before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            String selectAccessSQL = "SELECT * FROM component_instruction ORDER BY id";
            try (ResultSet rsInstruction = accessStmt.executeQuery(selectAccessSQL)) {

                String checkExistsSQL =
                        "SELECT id FROM component_instruction WHERE instruction_order_number = ? AND name = ? AND home_banking_id = ? AND block_id = ?";
                String insertSQL = "INSERT INTO component_instruction ("
                        + "instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, block_id, variable_id, parent_id, home_banking_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement insertStmt = sqliteConn.prepareStatement(insertSQL);
                        PreparedStatement checkStmt = sqliteConn.prepareStatement(checkExistsSQL)) {

                    int count = 0;
                    List<Integer> insertedOldIds = new ArrayList<>();
                    instructionMap.clear();

                    while (rsInstruction.next()) {
                        int id = rsInstruction.getInt("id");
                        int oldHomeBankId = rsInstruction.getInt("home_banking_id");
                        Integer newHomeBankId = homeBankMap.get(oldHomeBankId);
                        if (newHomeBankId == null) {
                            System.out.println(
                                    "Skipped component_instruction with unknown home_banking_id: " + oldHomeBankId);
                            continue;
                        }

                        int oldBlockId = rsInstruction.getInt("block_id");
                        Integer newBlockId = blockMap.get(oldBlockId);
                        if (newBlockId == null) {
                            System.out.println("Skipped component_instruction with unknown block_id: " + oldBlockId);
                            continue;
                        }

                        int instructionOrderNumber = rsInstruction.getInt("instruction_order_number");
                        String name = rsInstruction.getString("name");

                        instructionMap.put(id, -1); // initialize with -1

                        checkStmt.setInt(1, instructionOrderNumber);
                        checkStmt.setString(2, name);
                        checkStmt.setInt(3, newHomeBankId);
                        checkStmt.setInt(4, newBlockId);

                        try (ResultSet checkRs = checkStmt.executeQuery()) {
                            if (!checkRs.next()) {
                                insertStmt.setInt(1, instructionOrderNumber);
                                insertStmt.setString(2, rsInstruction.getString("actions"));
                                insertStmt.setString(3, name);
                                insertStmt.setString(4, rsInstruction.getString("xpath"));
                                insertStmt.setString(5, rsInstruction.getString("coordinates"));

                                insertOrNull(insertStmt, 6, rsInstruction, "force_coordinates");
                                insertStmt.setString(7, rsInstruction.getString("iframe_xpath"));
                                insertStmt.setString(8, rsInstruction.getString("tag_name"));
                                insertStmt.setString(9, rsInstruction.getString("shadow_host"));
                                insertStmt.setString(10, rsInstruction.getString("shadow_root"));
                                insertStmt.setString(11, rsInstruction.getString("css_selector"));
                                insertStmt.setString(12, rsInstruction.getString("description"));
                                insertStmt.setString(13, rsInstruction.getString("operation"));

                                insertOrNull(insertStmt, 14, rsInstruction, "optional");
                                insertOrNull(insertStmt, 15, rsInstruction, "block_marked");
                                insertStmt.setString(16, rsInstruction.getString("default_value"));
                                insertOrNull(insertStmt, 17, rsInstruction, "action_custom_max_wait_sec");
                                insertOrNull(insertStmt, 18, rsInstruction, "on_hold_seconds");
                                insertOrNull(insertStmt, 19, rsInstruction, "codified");
                                insertOrNull(insertStmt, 20, rsInstruction, "export_to_abr");

                                insertStmt.setInt(21, rsInstruction.getInt("active"));
                                insertStmt.setInt(22, newBlockId);
                                insertOrNull(insertStmt, 23, rsInstruction, "variable_id");
                                insertOrNull(insertStmt, 24, rsInstruction, "parent_id");
                                insertStmt.setInt(25, newHomeBankId);

                                insertStmt.addBatch();
                                insertedOldIds.add(id);
                                count++;
                            } else {
                                System.out.println("Skipped existing component_instruction: " + name);
                            }
                        }

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            sqliteConn.commit();
                            System.out.println("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        sqliteConn.commit();
                        System.out.println("Inserted final batch of " + (count % BATCH_SIZE));
                    }

                    // Step 3: Get new IDs
                    List<Integer> idsAfter = new ArrayList<>();
                    try (ResultSet rsAfter = idStmtAfter.executeQuery(selectIdsSQL)) {
                        while (rsAfter.next()) {
                            idsAfter.add(rsAfter.getInt("id"));
                        }
                    }

                    List<Integer> newIds = new ArrayList<>(idsAfter);
                    newIds.removeAll(idsBefore);

                    // Step 4: Map old → new
                    if (insertedOldIds.size() != newIds.size()) {
                        System.err.println("Mismatch: inserted old IDs = " + insertedOldIds.size() + " but new IDs = "
                                + newIds.size());
                    } else {
                        for (int i = 0; i < insertedOldIds.size(); i++) {
                            instructionMap.put(insertedOldIds.get(i), newIds.get(i));
                        }
                    }

                    System.out.println("Mapping of old to new component_instruction IDs: " + instructionMap);
                    System.out.println("Component Instruction export completed.");
                }
            }

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe("Failed to export component_instruction: " + e.getMessage());
        }
    }

    public void exportCompVariables() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String accessDbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;
        ARLogger.getInstance(PerformDataBase.class).info("ACCESS connection URL: " + accessDbUrl);

        String sqliteDbUrl = CONNECTION_TYPE_SQLITE + dbPath + ARConstants.FILE_NAME_SQLITE;

        final int BATCH_SIZE = 100;
        String selectIdsSQL = "SELECT id FROM component_variable ORDER BY id";

        try (Connection accessConn = DriverManager.getConnection(accessDbUrl);
                Connection sqliteConn = DriverManager.getConnection(sqliteDbUrl);
                Statement accessStmt = accessConn.createStatement();
                Statement idStmtBefore = sqliteConn.createStatement();
                Statement idStmtAfter = sqliteConn.createStatement()) {

            sqliteConn.setAutoCommit(false);

            // Step 1: Get existing IDs
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            String selectAccessSQL = "SELECT * FROM component_variable ORDER BY id";
            try (ResultSet rsVariable = accessStmt.executeQuery(selectAccessSQL)) {

                String checkExistsSQL =
                        "SELECT id FROM component_variable WHERE type = ? AND name = ? AND instruction_id = ? AND home_banking_id = ?";
                String insertSQL =
                        "INSERT INTO component_variable (type, name, value, local_format, delimiter, instruction_id, home_banking_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement insertStmt = sqliteConn.prepareStatement(insertSQL);
                        PreparedStatement checkStmt = sqliteConn.prepareStatement(checkExistsSQL)) {

                    int count = 0;
                    List<Integer> insertedOldIds = new ArrayList<>();
                    variableMap.clear();

                    while (rsVariable.next()) {
                        int id = rsVariable.getInt("id");
                        String type = rsVariable.getString("type");
                        String name = rsVariable.getString("name");

                        int oldHomeBankId = rsVariable.getInt("home_banking_id");
                        Integer newHomeBankId = homeBankMap.get(oldHomeBankId);
                        if (newHomeBankId == null) {
                            System.out.println(
                                    "Skipped component_variable with unknown home_banking_id: " + oldHomeBankId);
                            continue;
                        }

                        Integer instructionId = rsVariable.getObject("instruction_id") != null
                                ? rsVariable.getInt("instruction_id")
                                : null;

                        Integer newInstructionId = null;
                        if (instructionId != null) {
                            newInstructionId = instructionMap.get(instructionId);
                            if (newInstructionId == null) {
                                System.out.println(
                                        "Skipped component_variable with unknown instruction_id: " + instructionId);
                                continue;
                            }
                        }

                        variableMap.put(id, -1); // init with -1

                        checkStmt.setString(1, type);
                        checkStmt.setString(2, name);
                        checkStmt.setInt(3, newInstructionId);
                        checkStmt.setInt(4, newHomeBankId);

                        try (ResultSet checkRs = checkStmt.executeQuery()) {
                            if (!checkRs.next()) {
                                insertStmt.setString(1, type);
                                insertStmt.setString(2, name);
                                insertStmt.setString(3, rsVariable.getString("value"));
                                insertStmt.setString(4, rsVariable.getString("local_format"));
                                insertStmt.setString(5, rsVariable.getString("delimiter"));

                                if (newInstructionId != null) {
                                    insertStmt.setInt(6, newInstructionId);
                                } else {
                                    insertStmt.setNull(6, Types.INTEGER);
                                }

                                insertStmt.setInt(7, newHomeBankId);

                                insertStmt.addBatch();
                                insertedOldIds.add(id);
                                count++;
                            } else {
                                System.out.println("Skipped existing component_variable: " + name);
                            }
                        }

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            sqliteConn.commit();
                            System.out.println("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        sqliteConn.commit();
                        System.out.println("Inserted final batch of " + (count % BATCH_SIZE));
                    }

                    // Step 3: Get new IDs
                    List<Integer> idsAfter = new ArrayList<>();
                    try (ResultSet rsAfter = idStmtAfter.executeQuery(selectIdsSQL)) {
                        while (rsAfter.next()) {
                            idsAfter.add(rsAfter.getInt("id"));
                        }
                    }

                    List<Integer> newIds = new ArrayList<>(idsAfter);
                    newIds.removeAll(idsBefore);

                    // Step 4: Build variableMap
                    if (insertedOldIds.size() != newIds.size()) {
                        System.err.println("Mismatch: inserted old IDs = " + insertedOldIds.size() + " but new IDs = "
                                + newIds.size());
                    } else {
                        for (int i = 0; i < insertedOldIds.size(); i++) {
                            variableMap.put(insertedOldIds.get(i), newIds.get(i));
                        }
                    }

                    System.out.println("Mapping of old to new component_variable IDs: " + variableMap);
                    System.out.println("Component Variable export completed.");
                }
            }

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe("Failed to export component_variable: " + e.getMessage());
        }
    }

    public void exportUpdateCompInstruction() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String sqliteDbUrl = CONNECTION_TYPE_SQLITE + dbPath + ARConstants.FILE_NAME_SQLITE;

        final int BATCH_SIZE = 100;

        try (Connection sqliteConn = DriverManager.getConnection(sqliteDbUrl);
                Statement postgresStmt = sqliteConn.createStatement()) {

            sqliteConn.setAutoCommit(false);

            String selectPostgresSQL =
                    "SELECT id, name, parent_id, variable_id FROM component_instruction WHERE parent_id IS NOT NULL OR variable_id IS NOT NULL ORDER BY id";
            try (ResultSet rsInstruction = postgresStmt.executeQuery(selectPostgresSQL)) {

                String updateSQL = "UPDATE component_instruction SET variable_id = ?, parent_id = ? WHERE id = ? ";

                try (PreparedStatement updateStmt = sqliteConn.prepareStatement(updateSQL)) {
                    int count = 0;

                    while (rsInstruction.next()) {
                        int id = rsInstruction.getInt("id");
                        String name = rsInstruction.getString("name");

                        // Set variable_id directly from Access
                        int originalVariableId = rsInstruction.getInt("variable_id");
                        // Map old bot_job_id to new
                        Integer newVariableId = variableMap.get(originalVariableId);
                        if (newVariableId == null) {
                            System.out.println("Skipped variable_id column with unknown variable_id: " + newVariableId);
                            updateStmt.setNull(1, Types.INTEGER);
                        } else {
                            updateStmt.setInt(1, newVariableId);
                        }

                        // Handle parent_id based on name
                        if ("GOTO".equalsIgnoreCase(name) || "EXCEL GOTO".equalsIgnoreCase(name)) {
                            int parentBlockId = rsInstruction.getInt("parent_id");
                            Integer newParentBlockId = blockMap.get(parentBlockId);
                            if (newParentBlockId != null) {
                                updateStmt.setInt(2, newParentBlockId);
                            } else {
                                updateStmt.setNull(2, Types.INTEGER);
                            }
                        } else {
                            int parentInstructionId = rsInstruction.getInt("parent_id");
                            Integer newParentInstructionId = instructionMap.get(parentInstructionId);
                            if (newParentInstructionId != null) {
                                updateStmt.setInt(2, newParentInstructionId);
                            } else {
                                updateStmt.setNull(2, Types.INTEGER);
                            }
                        }

                        updateStmt.setInt(3, id); // WHERE clause: name = ?

                        updateStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            updateStmt.executeBatch();
                            sqliteConn.commit();
                            System.out.println("Updated batch of " + BATCH_SIZE);
                        }
                    }

                    if (count % BATCH_SIZE != 0) {
                        updateStmt.executeBatch();
                        sqliteConn.commit();
                        System.out.println("Updated final batch of " + (count % BATCH_SIZE));
                    }

                    ARLogger.getInstance(PerformDataBase.class).info("Updated component_instruction records: " + count);
                }
            }

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class).severe("Failed to update component_instruction");
        }
    }

    public void exportCompReferences() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String accessDbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;
        ARLogger.getInstance(PerformDataBase.class).info("ACCESS connection URL: " + accessDbUrl);

        String sqliteDbUrl = CONNECTION_TYPE_SQLITE + dbPath + ARConstants.FILE_NAME_SQLITE;

        final int BATCH_SIZE = 100;
        String selectIdsSQL = "SELECT id FROM component_reference ORDER BY id";

        try (Connection accessConn = DriverManager.getConnection(accessDbUrl);
                Connection sqliteConn = DriverManager.getConnection(sqliteDbUrl);
                Statement accessStmt = accessConn.createStatement();
                Statement idStmtBefore = sqliteConn.createStatement();
                Statement idStmtAfter = sqliteConn.createStatement()) {

            sqliteConn.setAutoCommit(false);

            // Step 1: Get existing IDs before insertion
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            String selectAccessSQL = "SELECT * FROM component_reference ORDER BY id";
            try (ResultSet rs = accessStmt.executeQuery(selectAccessSQL)) {

                String checkExistsSQL =
                        "SELECT id FROM component_reference WHERE reference_type = ? AND value = ? AND instruction_id = ? AND home_banking_id = ?";
                String insertSQL =
                        "INSERT INTO component_reference (reference_type, value, instruction_id, home_banking_id) VALUES (?, ?, ?, ?)";

                try (PreparedStatement insertStmt = sqliteConn.prepareStatement(insertSQL);
                        PreparedStatement checkStmt = sqliteConn.prepareStatement(checkExistsSQL)) {

                    int count = 0;
                    List<Integer> insertedOldIds = new ArrayList<>();
                    referenceMap.clear();

                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String referenceType = rs.getString("reference_type");
                        String value = rs.getString("value");

                        int oldInstructionId = rs.getInt("instruction_id");
                        Integer newInstructionId = instructionMap.get(oldInstructionId);
                        if (newInstructionId == null) {
                            System.out.println(
                                    "Skipped component_reference with unknown instruction_id: " + oldInstructionId);
                            continue;
                        }

                        int oldHomeBankId = rs.getInt("home_banking_id");
                        Integer newHomeBankId = homeBankMap.get(oldHomeBankId);
                        if (newHomeBankId == null) {
                            System.out.println(
                                    "Skipped component_reference with unknown home_banking_id: " + oldHomeBankId);
                            continue;
                        }

                        referenceMap.put(id, -1); // initialize with -1

                        checkStmt.setString(1, referenceType);
                        checkStmt.setString(2, value);
                        checkStmt.setInt(3, newInstructionId);
                        checkStmt.setInt(4, newHomeBankId);

                        try (ResultSet checkRs = checkStmt.executeQuery()) {
                            if (!checkRs.next()) {
                                insertStmt.setString(1, referenceType);
                                insertStmt.setString(2, value);
                                insertStmt.setInt(3, newInstructionId);
                                insertStmt.setInt(4, newHomeBankId);

                                insertStmt.addBatch();
                                insertedOldIds.add(id);
                                count++;
                            } else {
                                System.out.println("Skipped existing component_reference: " + referenceType);
                            }
                        }

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            sqliteConn.commit();
                            System.out.println("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        sqliteConn.commit();
                        System.out.println("Inserted final batch of " + (count % BATCH_SIZE));
                    }

                    // Step 3: Get all new IDs
                    List<Integer> idsAfter = new ArrayList<>();
                    try (ResultSet rsAfter = idStmtAfter.executeQuery(selectIdsSQL)) {
                        while (rsAfter.next()) {
                            idsAfter.add(rsAfter.getInt("id"));
                        }
                    }

                    // Step 4: Map old inserted IDs to new ones
                    List<Integer> newIds = new ArrayList<>(idsAfter);
                    newIds.removeAll(idsBefore);

                    if (insertedOldIds.size() != newIds.size()) {
                        System.err.println("Mismatch: inserted old IDs = " + insertedOldIds.size() + " but new IDs = "
                                + newIds.size());
                    } else {
                        for (int i = 0; i < insertedOldIds.size(); i++) {
                            referenceMap.put(insertedOldIds.get(i), newIds.get(i));
                        }
                    }

                    System.out.println("Mapping of old to new component_reference IDs: " + referenceMap);
                    System.out.println("Component Reference export completed.");
                }
            }

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe("Failed to export component_reference table: " + e.getMessage());
        }
    }

    private void insertOrNull(PreparedStatement stmt, int parameterIndex, ResultSet rs, String columnName)
            throws SQLException {
        Object value = rs.getObject(columnName);
        if (value == null) {
            stmt.setNull(parameterIndex, Types.INTEGER);
        } else {
            stmt.setInt(parameterIndex, rs.getInt(columnName));
        }
    }
}
