package com.allinweb.ch.facade;

import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.DefinitionDraft;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.MutationResult;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.OwnerKey;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableModels.ValueState;
import com.allinweb.ch.facade.variables.runtime.BotJobRuntimeVariableService;
import com.allinweb.ch.model.*;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

@Slf4j
public class PerformDataBase {

    private static final Logger logDB = LoggerFactory.getLogger("com.allinweb.database");

    // De-duplicated logging: getConnection() runs on every query and instruction loads are
    // triggered per WS request, so logging each occurrence floods the database log
    // (thousands of identical lines per minute). Log only when the message CHANGES.
    private static volatile String lastLoggedConnectionUrl = "";
    private static volatile String lastLoggedFetchSignature = "";

    private static void logConnectionUrlOnce(String label, String url) {
        String signature = label + url;
        if (!signature.equals(lastLoggedConnectionUrl)) {
            lastLoggedConnectionUrl = signature;
            logDB.info(label + " connection URL: " + url);
        }
    }

    public static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    public static final PerformMessage performMessage = PerformMessage.getInstance();
    public static final PerformInitializer performInitializer = PerformInitializer.getInstance();
    public static final PerformLists performLists = PerformLists.getInstance();
    private static final BotJobRuntimeVariableService botJobRuntimeVariables =
            new BotJobRuntimeVariableService();
    //    public static final MobileReturnServer mobileReturnServer = MobileReturnServer.getInstance();

    // Static final variable to hold the singleton instance
    protected static volatile PerformDataBase instance;
    public final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    public final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    public final String CONNECTION_TYPE_SQLITE = "jdbc:sqlite:"; // no parameters needed
    // Open connection counter
    public int openConnections = 0;

    @Getter
    @Setter
    private static ErrorMessage errorMessage = new ErrorMessage();

    @Getter
    @Setter
    public List<Integer> idsBlockAfter = new ArrayList<>();

    @Getter
    @Setter
    public List<Integer> idsInstrucAfter = new ArrayList<>();

    @Getter
    @Setter
    public List<Integer> idsBotJobAfter = new ArrayList<>();

    @Getter
    @Setter
    public List<Integer> idsHomeUrlAfter = new ArrayList<>();

    @Getter
    @Setter
    public List<Integer> idsHomeBankAfter = new ArrayList<>();

    @Getter
    @Setter
    public List<Integer> idsVariableAfter = new ArrayList<>();

    @Setter
    public boolean mobileDevices;

    // Postgres
    public boolean ACCESS_DB = false;
    public boolean POSTGRES_DB = false;
    public boolean SQLITE_DB = false;
    public boolean connDBWorks = false;
    public boolean dbFailed = false;
    private TreeMap<Integer, Integer> homeBankMap = new TreeMap<>();
    private TreeMap<Integer, Integer> botJobMap = new TreeMap<>();
    private TreeMap<Integer, Integer> blockMap = new TreeMap<>();
    private TreeMap<Integer, Integer> instructionMap = new TreeMap<>();
    private TreeMap<Integer, Integer> instrVariablesMap = new TreeMap<>();
    private TreeMap<Integer, Integer> instrNewInverted = new TreeMap<>();
    private TreeMap<Integer, Integer> variableMap = new TreeMap<>();
    private TreeMap<Integer, Integer> referenceMap = new TreeMap<>();
    // Private constructor to prevent instantiation
    private PerformDataBase() {}

    // Public method to access the singleton instance
    public static PerformDataBase getInstance() {
        if (instance == null) {
            synchronized (PerformDataBase.class) {
                if (instance == null) {
                    instance = new PerformDataBase();
                }
            }
        }
        return instance;
    }

    public void initialize(String databaseType) {}

    //    public void closeConnection() {
    //        if (conn != null) {
    //            try {
    //                conn.close();
    //                conn = null; // Reset the connection to null after closing
    //                decrementOpenConnections();
    //            } catch (SQLException e) {
    //                logDB.info(e.getMessage()); // Handle the exception, log it or rethrow it as needed
    //            }
    //        }
    //    }

    // Increment open connections counter
    public synchronized void incrementOpenConnections() {
        openConnections++;
        logDB.info("Open connections: " + openConnections);
    }

    // Decrement open connections counter
    public synchronized void decrementOpenConnections() {
        openConnections--;
        logDB.info("Open connections: " + openConnections);
    }

    // Get the current open connections count
    public int getOpenConnectionsCount() {
        return openConnections;
    }

    /**
     * Upsert scanned elements into the {@code scanned_element} source-of-truth registry for a
     * scope (organization + bot job). Best-effort: a registry failure must never break scanning.
     *
     * @return {@code [inserted, updated]} counts, or {@code [0,0]} on failure.
     */
    public int[] upsertScannedElements(
            Integer homeBankingId,
            Integer botJobId,
            Integer homeUrlId,
            String pageUrl,
            java.util.List<com.allinweb.ch.model.ElementDTO> elements) {
        try {
            return upsertScannedElementsStrict(
                    homeBankingId, botJobId, homeUrlId, pageUrl, elements);
        } catch (Exception e) {
            log.warn("upsertScannedElements failed (hb={}, bot={}): {}", homeBankingId, botJobId, e.getMessage());
            return new int[] {0, 0};
        }
    }

    /**
     * Persist a scanner batch and report any page-identity/schema failure to the interactive Page
     * Scanner caller.
     */
    public int[] upsertScannedElementsStrict(
            Integer homeBankingId,
            Integer botJobId,
            Integer homeUrlId,
            String pageUrl,
            java.util.List<com.allinweb.ch.model.ElementDTO> elements)
            throws SQLException {
        try (Connection conn = getConnection()) {
            com.allinweb.ch.db.ScannedElementRepository.UpsertResult result =
                    com.allinweb.ch.db.ScannedElementRepository.upsert(
                            conn, homeBankingId, botJobId, homeUrlId, pageUrl, elements);
            logDB.info(
                    "{} New Web Elements inserted to scanned_element and {} updated (homeBankingId={}, botJobId={})",
                    result.inserted(),
                    result.updated(),
                    homeBankingId,
                    botJobId);
            return new int[] {result.inserted(), result.updated()};
        }
    }

    /**
     * Strictly update one existing scanner row with a client-authored XPath. Database errors and a
     * stale/missing row are reported to the correlated Page Scanner caller instead of being hidden
     * by the scan-time best-effort path.
     */
    public int updateScannedElementCustomXPathStrict(
            Integer homeBankingId,
            Integer botJobId,
            String pageUrl,
            com.allinweb.ch.model.ElementDTO element)
            throws SQLException {
        try (Connection conn = getConnection()) {
            return com.allinweb.ch.db.ScannedElementRepository.updateCustomXPath(
                    conn, homeBankingId, botJobId, pageUrl, element);
        }
    }

    /**
     * Self-healing lookup: load the scanned_element registry for a scope and resolve the best match
     * for an instruction (validate/re-resolve when its stored xPath drifts or a name collides).
     * Best-effort: returns a NONE result on any failure so execution can fall back to its own path.
     */
    public com.allinweb.ch.facade.ScannedElementResolver.Result resolveScannedElement(
            Integer homeBankingId, Integer botJobId, com.allinweb.ch.model.InstructionLoad instruction) {
        try (Connection conn = getConnection()) {
            java.util.List<com.allinweb.ch.model.ScannedElement> registry =
                    com.allinweb.ch.db.ScannedElementRepository.load(conn, homeBankingId, botJobId);
            return com.allinweb.ch.facade.ScannedElementResolver.resolve(registry, instruction);
        } catch (Exception e) {
            log.warn("resolveScannedElement failed (hb={}, bot={}): {}", homeBankingId, botJobId, e.getMessage());
            return new com.allinweb.ch.facade.ScannedElementResolver.Result(
                    null, com.allinweb.ch.facade.ScannedElementResolver.Strategy.NONE, 0.0);
        }
    }

    /** Bot-job-scoped self-healing lookup (a bot job maps to one organization). */
    public com.allinweb.ch.facade.ScannedElementResolver.Result resolveScannedElementByBotJob(
            Integer botJobId, com.allinweb.ch.model.InstructionLoad instruction) {
        try (Connection conn = getConnection()) {
            java.util.List<com.allinweb.ch.model.ScannedElement> registry =
                    com.allinweb.ch.db.ScannedElementRepository.loadByBotJob(conn, botJobId);
            return com.allinweb.ch.facade.ScannedElementResolver.resolve(registry, instruction);
        } catch (Exception e) {
            log.warn("resolveScannedElementByBotJob failed (bot={}): {}", botJobId, e.getMessage());
            return new com.allinweb.ch.facade.ScannedElementResolver.Result(
                    null, com.allinweb.ch.facade.ScannedElementResolver.Strategy.NONE, 0.0);
        }
    }

    /** Current-page-only self-healing lookup for the active Playwright session. */
    public com.allinweb.ch.facade.ScannedElementResolver.Result resolveScannedElementByBotJobAndPage(
            Integer botJobId,
            String pageUrl,
            com.allinweb.ch.model.InstructionLoad instruction) {
        try (Connection conn = getConnection()) {
            java.util.List<com.allinweb.ch.model.ScannedElement> registry =
                    com.allinweb.ch.db.ScannedElementRepository.loadByBotJobAndPage(
                            conn, botJobId, pageUrl);
            return com.allinweb.ch.facade.ScannedElementResolver.resolve(registry, instruction);
        } catch (Exception e) {
            log.warn(
                    "resolveScannedElementByBotJobAndPage failed (bot={}, page={}): {}",
                    botJobId,
                    pageUrl,
                    e.getMessage());
            return new com.allinweb.ch.facade.ScannedElementResolver.Result(
                    null, com.allinweb.ch.facade.ScannedElementResolver.Strategy.NONE, 0.0);
        }
    }

    public Connection getConnection() throws SQLException {
        // Determine DB type from properties
        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);

        // Booleans to track DB type
        POSTGRES_DB = false;
        SQLITE_DB = false;
        ACCESS_DB = false;

        if (dataBaseType != null) {
            if ("Postgres".equalsIgnoreCase(dataBaseType)) {
                // Postgres-specific logic
                POSTGRES_DB = true;
            } else if ("TEXT".equalsIgnoreCase(dataBaseType)) {
                // SQLite-specific logic
                SQLITE_DB = true;
            } else if ("Access".equalsIgnoreCase(dataBaseType)) {
                // Access-specific logic
                ACCESS_DB = true;
            }
        }

        try {
            if (POSTGRES_DB) {
                // Postgres connection
                String dbUrl = arPropertyManager.getProperty(ARPropertyEnum.DB_URL);
                String userDB = arPropertyManager.getProperty(ARPropertyEnum.DB_USER);
                String userPwd = arPropertyManager.getProperty(ARPropertyEnum.DB_PWD);

                logConnectionUrlOnce("POSTGRES", dbUrl);
                // logDB.info("User Details: " + userDB + " - [PROTECTED]");

                Class.forName("org.postgresql.Driver");
                Connection conn = DriverManager.getConnection(dbUrl, userDB, userPwd);
                conn.setReadOnly(false);
                connDBWorks = true;
                return conn;

                //                // Reset open connections counter if too many
                //                if (getOpenConnectionsCount() > 10) {
                //                    this.openConnections = 0;
                //                }
                //                incrementOpenConnections();

            } else if (SQLITE_DB) {
                // SQLite connection
                String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
                String sqliteUrl = CONNECTION_TYPE_SQLITE
                        + dbPath
                        + ARConstants.FILE_NAME_SQLITE; // make sure you have FILE_NAME_SQLITE constant

                logConnectionUrlOnce("SQLITE", sqliteUrl);

                Class.forName("org.sqlite.JDBC");

                SQLiteConfig config = new SQLiteConfig();
                config.enforceForeignKeys(true);

                Connection conn = DriverManager.getConnection(sqliteUrl, config.toProperties());
                //                    conn = SQLiteHelper.getConnection(sqliteUrl);
                conn.setReadOnly(false);
                connDBWorks = true;
                return conn;

                //                // Reset open connections counter if too many
                //                if (getOpenConnectionsCount() > 10) {
                //                    this.openConnections = 0;
                //                }
                //                incrementOpenConnections();

            } else {
                // Default to Access connection
                String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
                String dbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;

                logConnectionUrlOnce("ACCESS", dbUrl);

                Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
                Connection conn = DriverManager.getConnection(dbUrl);
                conn.setReadOnly(false);
                connDBWorks = true;
                return conn;

                //                // Reset open connections counter if too many
                //                if (getOpenConnectionsCount() > 10) {
                //                    this.openConnections = 0;
                //                }
                //                incrementOpenConnections();
            }

        } catch (SQLException error) {
            logDB.error("getConnection Error: " + error.getMessage());

            String database = POSTGRES_DB ? "Postgres" : (SQLITE_DB ? "TEXT" : "Access");
            errorMessage.setErrorHeader(database);
            errorMessage.setErrorTitle("Connection Failed");
            errorMessage.setErrorMessage(error.getMessage());
            connDBWorks = false;
            dbFailed = true;
            throw error;
        } catch (ClassNotFoundException error) {
            logDB.error("Driver DB Class not Found Error: " + error.getMessage());
        }

        connDBWorks = false;
        dbFailed = false;
        return null;
    }

    public void changeDbConnection() throws SQLException {
        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        //        if (Strings.isNullOrEmpty(previousDB) || (previousDB != null && !previousDB.equals(dataBaseType))) {
        ErrorMessage errorMessage = null;

        // closeConnection();

        POSTGRES_DB = false;
        SQLITE_DB = false;
        ACCESS_DB = false;

        if (dataBaseType != null) {
            if ("Postgres".equalsIgnoreCase(dataBaseType)) {
                // Postgres-specific logic
                POSTGRES_DB = true;
            } else if ("TEXT".equalsIgnoreCase(dataBaseType)) {
                // SQLite-specific logic
                SQLITE_DB = true;
            } else if ("Access".equalsIgnoreCase(dataBaseType)) {
                // Access-specific logic
                ACCESS_DB = true;
            }
        } else {
            // Access-specific logic
            ACCESS_DB = true;
        }

        if (getConnection() != null) {
            performInitializer.initialize();
            performInitializer.initializeDBS();
        }
    }

    public ErrorMessage loadAllColumnsExcelWrite(String tableName, int whereId, int blockId) {

        performLists.getListExcelColumns().clear();

        String whereColumn = tableName.equals("instruction") ? "bot_job_id" : "home_banking_id";

        StringBuilder selectSQL = new StringBuilder("SELECT " + "    parent.id   AS parent_id, "
                + "    parent.name AS parent_name, "
                + "    child.actions, "
                + "    child.operation, "
                + "    child.tag_name, "
                + "    child.id     AS child_id, "
                + "    b.block_order_number "
                + "FROM "
                + tableName + " AS child " + "JOIN "
                + tableName + " AS parent " + "       ON child.parent_id = parent.id "
                + "      AND parent.active = 1 "
                + "LEFT JOIN block AS b ON child.block_id = b.id "
                + "WHERE child.name = ? "
                + "  AND child.active = 1 "
                + "  AND child."
                + whereColumn + " = ? ");

        if (blockId > 0) {
            selectSQL.append(" AND child.block_id = ? ");
        }

        selectSQL.append(" ORDER BY b.block_order_number, child.id;");
        try (PreparedStatement stmt = getConnection().prepareStatement(selectSQL.toString())) {

            stmt.setString(1, "ExcelWrite");
            stmt.setInt(2, whereId);

            if (blockId > 0) {
                stmt.setInt(3, blockId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ParentOperations parentOper = new ParentOperations();
                    parentOper.setId(rs.getInt("child_id"));
                    parentOper.setName("ExcelWrite");
                    parentOper.setParentName(rs.getString("parent_name")); // CSV column name
                    parentOper.setActions(rs.getString("actions"));
                    parentOper.setOperations(rs.getString("operation"));
                    parentOper.setTagName(rs.getString("tag_name")); // if available in class

                    performLists.getListExcelColumns().add(parentOper);
                }
            }

            return null;

        } catch (SQLException e) {
            logDB.error(String.format(
                    "Error loading ExcelWrite columns for %s=%d. Error: %s", whereColumn, whereId, e.getMessage()));

            return new ErrorMessage(
                    "Load ExcelWrite Columns Error",
                    String.format("Failed to load ExcelWrite columns for %s=%d", whereColumn, whereId),
                    e.getMessage());
        }
    }

    public ErrorMessage loadAllParents(
            String tableName,
            int whereId, // either bot_job_id or home_banking_id
            int instructionId) {

        performLists.getListParentOperations().clear();

        String whereColumn = tableName.equals("instruction") ? "bot_job_id" : "home_banking_id";

        String selectSQL = "SELECT " + "    parent.name AS parent_name, "
                + "    child.actions, "
                + "    child.operation, "
                + "    child.name AS child_name, "
                + "    child.id "
                + "FROM "
                + tableName + " AS child " + "LEFT JOIN "
                + tableName + " AS parent ON child.parent_id = parent.id " + "WHERE child.parent_id = ? "
                + "  AND child."
                + whereColumn + " = ? " + "ORDER BY child.id;";

        try (PreparedStatement stmt = getConnection().prepareStatement(selectSQL)) {
            stmt.setInt(1, instructionId); // child.parent_id = ?
            stmt.setInt(2, whereId); // bot_job_id or home_banking_id

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ParentOperations parentOper = new ParentOperations();
                    parentOper.setId(rs.getInt("id"));
                    parentOper.setName(rs.getString("child_name"));
                    parentOper.setParentName(rs.getString("parent_name"));
                    parentOper.setActions(rs.getString("actions"));
                    parentOper.setOperations(rs.getString("operation"));
                    parentOper.setInstructionId(instructionId);

                    performLists.getListParentOperations().add(parentOper);
                }
            }

            return null; // success

        } catch (SQLException e) {

            logDB.error(String.format(
                    "Error loading parents for instruction ID %d in %s=%d. Error: %s",
                    instructionId, whereColumn, whereId, e.getMessage()));

            return new ErrorMessage(
                    "LoadParents Error",
                    String.format("Failed to load parents for instruction ID %d", instructionId),
                    e.getMessage());
        }
    }

    /**
     * Loads only commands bound to one variable declaration.
     *
     * <p>Legacy data can contain more than one variable owned by the same Web Field. Scoping only
     * by {@code parent_id} would rewrite commands belonging to every declaration on that field.
     */
    List<ParentOperations> loadVariableDependents(
            String tableName, int whereId, int instructionId, int variableId)
            throws SQLException {
        try (Connection connection = getConnection()) {
            return loadVariableDependents(
                    connection, tableName, whereId, instructionId, variableId);
        }
    }

    List<ParentOperations> loadVariableDependents(
            Connection connection,
            String tableName,
            int whereId,
            int instructionId,
            int variableId)
            throws SQLException {
        if (!"instruction".equals(tableName)
                && !"component_instruction".equals(tableName)) {
            throw new SQLException("Unsupported variable instruction workspace.");
        }
        String whereColumn =
                "instruction".equals(tableName) ? "bot_job_id" : "home_banking_id";
        String selectSql = "SELECT parent.name AS parent_name,"
                + " child.actions, child.operation, child.name AS child_name, child.id"
                + " FROM " + tableName + " AS child"
                + " LEFT JOIN " + tableName + " AS parent ON child.parent_id = parent.id"
                + " WHERE child.parent_id = ? AND child.variable_id = ?"
                + " AND child." + whereColumn + " = ?"
                + " ORDER BY child.id";
        List<ParentOperations> dependents = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setInt(1, instructionId);
            statement.setInt(2, variableId);
            statement.setInt(3, whereId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ParentOperations dependent = new ParentOperations();
                    dependent.setId(result.getInt("id"));
                    dependent.setName(result.getString("child_name"));
                    dependent.setParentName(result.getString("parent_name"));
                    dependent.setActions(result.getString("actions"));
                    dependent.setOperations(result.getString("operation"));
                    dependent.setInstructionId(instructionId);
                    dependents.add(dependent);
                }
            }
        }
        return List.copyOf(dependents);
    }

    public ErrorMessage deleteVariablesBatch(
            String tableName, // e.g., "instruction_variable" or "component_instruction_variable"
            int whereId, // e.g., bot_job_id or home_banking_id
            List<InstructionLoad> dtos // contains instructionId(s) + variableId(s)
            ) {
        if (dtos == null || dtos.isEmpty()) {
            return new ErrorMessage(
                    "Invalid Input", "Variable deletion list is null or empty", "Cannot delete variables");
        }

        // Validate table name to avoid SQL injection
        List<String> allowedTables = Arrays.asList("variable", "component_variable");
        if (!allowedTables.contains(tableName)) {
            return new ErrorMessage(
                    "Invalid Table",
                    "Invalid table name: " + tableName,
                    "Only variable/component_variable are allowed");
        }

        // Determine foreign key column
        String foreignKeyColumn = "variable".equalsIgnoreCase(tableName) ? "bot_job_id" : "home_banking_id";

        final int BATCH_SIZE = 100;
        int count = 0;

        String deleteSQL =
                "DELETE FROM " + tableName + " WHERE instruction_id = ? AND id = ? AND " + foreignKeyColumn + " = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {

            conn.setAutoCommit(false); // transaction control

            for (InstructionLoad dto : dtos) {
                if (dto == null
                        || dto.getId() == null
                        || dto.getId() <= 0
                        || dto.getVariableId() == null
                        || dto.getVariableId() <= 0) {
                    continue; // skip invalid
                }

                pstmt.setInt(1, dto.getId());
                pstmt.setInt(2, dto.getVariableId());
                pstmt.setInt(3, whereId);
                pstmt.addBatch();

                if (++count % BATCH_SIZE == 0) {
                    pstmt.executeBatch();
                    pstmt.clearBatch();
                }
            }

            // Final flush
            if (count % BATCH_SIZE != 0) {
                pstmt.executeBatch();
                pstmt.clearBatch();
            }

            conn.commit();

            logDB.info(String.format(
                    "Batch delete completed for %d variable records in %s", count, tableName.toUpperCase()));

            return null; // success

        } catch (SQLException e) {

            logDB.error("Batch delete error for " + tableName + ": " + e.getMessage());
            return new ErrorMessage(
                    "Delete Variables Error",
                    "Failed batch deletion for variables in table: " + tableName,
                    e.getMessage());
        }
    }

    public ErrorMessage deleteInstructionsBatch(
            String tableName, // "instruction" or "component_instruction"
            int whereId, // bot_job_id or home_banking_id
            List<InstructionLoad> dtos // contains list of instructionId(s)
            ) {
        if (dtos == null || dtos.isEmpty()) {
            return new ErrorMessage("Invalid Input", "Instruction list is null or empty", "Cannot delete instructions");
        }

        // Validate table name to avoid SQL injection
        List<String> allowedTables = Arrays.asList("instruction", "component_instruction");
        if (!allowedTables.contains(tableName)) {
            return new ErrorMessage(
                    "Invalid Table",
                    "Invalid table name: " + tableName,
                    "Only instruction/component_instruction are allowed");
        }

        // Determine foreign key column
        String foreignKeyColumn =
                "component_instruction".equalsIgnoreCase(tableName) ? "home_banking_id" : "bot_job_id";

        final int BATCH_SIZE = 100;
        int count = 0;

        String deleteSQL = "DELETE FROM " + tableName + " WHERE id = ? AND " + foreignKeyColumn + " = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {

            conn.setAutoCommit(false); // transaction control

            for (InstructionLoad dto : dtos) {
                if (dto == null || dto.getId() == null || dto.getId() <= 0) {
                    continue; // skip invalid
                }

                pstmt.setInt(1, dto.getId()); // "id" in instruction table
                pstmt.setInt(2, whereId);
                pstmt.addBatch();

                if (++count % BATCH_SIZE == 0) {
                    pstmt.executeBatch();
                    pstmt.clearBatch();
                }
            }

            // final flush
            if (count % BATCH_SIZE != 0) {
                pstmt.executeBatch();
                pstmt.clearBatch();
            }

            conn.commit();

            logDB.info(String.format(
                    "Batch delete completed for %d instruction records in %s", count, tableName.toUpperCase()));

            return null; // success

        } catch (SQLException e) {

            logDB.error("Batch delete error for " + tableName + ": " + e.getMessage());
            return new ErrorMessage(
                    "Delete Instructions Error",
                    "Failed batch deletion for instructions in table: " + tableName,
                    e.getMessage());
        }
    }

    public ErrorMessage deleteReferencesBatch(
            String tableName, // "component_reference" or "reference"
            int whereId, // bot_job_id or home_banking_id
            List<InstructionLoad> dtos // contains list of instructionId(s)
            ) {
        if (dtos == null || dtos.isEmpty()) {
            return new ErrorMessage("Invalid Input", "Instruction list is null or empty", "Cannot delete references");
        }

        // Validate table name to avoid SQL injection
        List<String> allowedTables = Arrays.asList("reference", "component_reference");
        if (!allowedTables.contains(tableName)) {
            return new ErrorMessage(
                    "Invalid Table",
                    "Invalid table name: " + tableName,
                    "Only reference/component_reference are allowed");
        }

        // Determine foreign key column
        String foreignKeyColumn = "component_reference".equalsIgnoreCase(tableName) ? "home_banking_id" : "bot_job_id";

        final int BATCH_SIZE = 100;
        int count = 0;

        String deleteSQL = "DELETE FROM " + tableName + " WHERE instruction_id = ? AND " + foreignKeyColumn + " = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {

            conn.setAutoCommit(false); // transaction control

            for (InstructionLoad dto : dtos) {
                if (dto == null || dto.getId() == null || dto.getId() <= 0) {
                    continue; // skip invalid
                }

                pstmt.setInt(1, dto.getId());
                pstmt.setInt(2, whereId);
                pstmt.addBatch();

                if (++count % BATCH_SIZE == 0) {
                    pstmt.executeBatch();
                    pstmt.clearBatch();
                }
            }

            // final flush
            if (count % BATCH_SIZE != 0) {
                pstmt.executeBatch();
                pstmt.clearBatch();
            }

            conn.commit();

            logDB.info(String.format(
                    "Batch delete completed for %d reference records in %s", count, tableName.toUpperCase()));

            return null; // success

        } catch (SQLException e) {

            logDB.error("Batch delete error for " + tableName + ": " + e.getMessage());
            return new ErrorMessage(
                    "Delete References Error",
                    "Failed batch deletion for references in table: " + tableName,
                    e.getMessage());
        }
    }

    public ErrorMessage deleteRowParents(
            String tableName, // "instruction" or "component_instruction"
            int whereId, // bot_job_id or home_banking_id
            int parentId) {
        // Determine foreign key column
        String foreignKeyColumn = "instruction".equalsIgnoreCase(tableName) ? "bot_job_id" : "home_banking_id";

        String deleteSQL = "DELETE FROM " + tableName + " WHERE parent_id = ? AND " + foreignKeyColumn + " = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // disable auto-commit

            try (PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
                pstmt.setInt(1, parentId);
                pstmt.setInt(2, whereId);

                int rowsAffected = pstmt.executeUpdate();
                conn.commit();

                if (rowsAffected > 0) {

                    logDB.info(String.format(
                            "Deleted %d parents - parent ID %d in %s = %d",
                            rowsAffected, parentId, foreignKeyColumn, whereId));
                } else {

                    logDB.warn(String.format(
                            "No parents found to delete - parent ID %d in %s = %d",
                            parentId, foreignKeyColumn, whereId));
                }

                return null; // success
            } catch (SQLException e) {

                logDB.error(String.format(
                        "Error deleting parent ID %d in %s = %d. Error: %s",
                        parentId, foreignKeyColumn, whereId, e.getMessage()));

                return new ErrorMessage(
                        "Delete Parent Error", "Failed to delete parent ID: " + parentId, e.getMessage());
            }
        } catch (SQLException ex) {

            logDB.error(String.format(
                    "Connection error while deleting parent ID %d in %s = %d. Error: %s",
                    parentId, foreignKeyColumn, whereId, ex.getMessage()));

            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }
    }

    public ErrorMessage updateBlockOrderNumber(
            String tableName,
            int whereId, // either "bot_job_id" or "home_banking_id"
            boolean reorderAll) {
        final int BATCH_SIZE = 100;
        List<BlockLoadDTO> listBlocks =
                tableName.equals("block") ? performLists.getListBlock() : performLists.getListBlockComp();

        listBlocks.sort(Comparator.comparingInt(BlockLoadDTO::getBlockOrderNumber));

        String updateSQL = "UPDATE " + tableName + " SET block_order_number = ? WHERE id = ? AND " + whereId + " = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Disable auto-commit

            try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
                int newOrderNumber = 1;
                int count = 0;

                for (BlockLoadDTO blockOrder : listBlocks) {
                    int orderNumber = reorderAll ? newOrderNumber : blockOrder.getBlockOrderNumber();

                    // UPDATE MEMORY LIST ALSO
                    if (reorderAll) {
                        blockOrder.setBlockOrderNumber(newOrderNumber);
                    }

                    pstmt.setInt(1, orderNumber);
                    pstmt.setInt(2, blockOrder.getId());

                    // Choose the correct ID value based on the column
                    if ("block".equalsIgnoreCase(tableName)) {
                        pstmt.setInt(3, blockOrder.getBotJobId());
                    } else if ("component_block".equalsIgnoreCase(tableName)) {
                        pstmt.setInt(3, blockOrder.getHomeBankingId());
                    } else {
                        return new ErrorMessage("Invalid Table naem", "Invalid 'tableName' value", tableName);
                    }

                    pstmt.addBatch();
                    count++;
                    newOrderNumber++;

                    if (count % BATCH_SIZE == 0) {
                        pstmt.executeBatch();
                        conn.commit();

                        logDB.info("Executed batch of " + BATCH_SIZE + " block order updates for table: " + tableName);
                    }
                }

                // Execute remaining updates
                if (count % BATCH_SIZE != 0) {
                    pstmt.executeBatch();
                    conn.commit();

                    logDB.info("Executed final batch of " + (count % BATCH_SIZE) + " block order updates for table: "
                            + tableName);
                }

                //                loadBlocks(whereId, "", tableName);

            } catch (SQLException e) {

                logDB.error(String.format(
                        "Error updating block order numbers in table '%s'. Error: %s", tableName, e.getMessage()));
                return new ErrorMessage(
                        "Update Block Order Error", "Failed to update block order numbers", e.getMessage());
            }
        } catch (SQLException ex) {

            logDB.error(String.format(
                    "Connection error while updating block order numbers in table '%s'. Error: %s",
                    tableName, ex.getMessage()));
            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }
        return null; // Success
    }

    public ErrorMessage updateSubmittedBlockOrder(
            String tableName, int whereId, List<BlockLoadDTO> submittedBlocks) {
        try (Connection connection = getConnection()) {
            new BlockOrderTransaction().execute(
                    connection, tableName, whereId, submittedBlocks);
            return null;
        } catch (SQLException error) {
            logDB.error(
                    "Atomic block order update failed for table {} owner {}: {}",
                    tableName,
                    whereId,
                    error.getMessage());
            return new ErrorMessage(
                    "Move Block Refused",
                    "The block order was not saved",
                    error.getMessage());
        }
    }

    public ErrorMessage updateSwiftBlockOrderNumber(
            String tableName,
            int whereId, // either "bot_job_id" or "home_banking_id"
            List<BlockLoadDTO> listBlocks) {

        final int BATCH_SIZE = 100;

        String updateSQL = "UPDATE " + tableName
                + " SET block_order_number = ? WHERE id = ? AND "
                + (tableName.equalsIgnoreCase("block") ? "bot_job_id" : "home_banking_id")
                + " = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Disable auto-commit

            try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
                int count = 0;

                for (BlockLoadDTO blockOrder : listBlocks) {
                    pstmt.setInt(1, blockOrder.getBlockOrderNumber());
                    pstmt.setInt(2, blockOrder.getId());

                    // Choose correct id based on table type
                    if ("block".equalsIgnoreCase(tableName)) {
                        pstmt.setInt(3, blockOrder.getBotJobId());
                    } else if ("component_block".equalsIgnoreCase(tableName)) {
                        pstmt.setInt(3, blockOrder.getHomeBankingId());
                    } else {
                        return new ErrorMessage("Invalid Table Name", "Invalid 'tableName' value", tableName);
                    }

                    pstmt.addBatch();
                    count++;

                    if (count % BATCH_SIZE == 0) {
                        pstmt.executeBatch();
                        conn.commit();

                        logDB.info("Executed batch of " + BATCH_SIZE + " block order updates for table: " + tableName);
                    }
                }

                // Execute remaining updates
                if (count % BATCH_SIZE != 0) {
                    pstmt.executeBatch();
                    conn.commit();

                    logDB.info("Executed final batch of " + (count % BATCH_SIZE) + " block order updates for table: "
                            + tableName);
                }
            } catch (SQLException e) {

                logDB.error(String.format(
                        "Error updating block order numbers in table '%s'. Error: %s", tableName, e.getMessage()));
                return new ErrorMessage(
                        "Update Block Order Error", "Failed to update block order numbers", e.getMessage());
            }
        } catch (SQLException ex) {

            logDB.error(String.format(
                    "Connection error while updating block order numbers in table '%s'. Error: %s",
                    tableName, ex.getMessage()));
            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }
        return null; // Success
    }

    // Generic Update Block Name
    public ErrorMessage updateBlockName(
            int whereId,
            String tableName, // "block" or "component_block"
            int blockId,
            String blockName) {

        // Determine foreign key column
        String foreignKeyColumn = "block".equalsIgnoreCase(tableName) ? "bot_job_id" : "home_banking_id";

        String updateSQL = "UPDATE " + tableName + " SET name = ?" + " WHERE id = ? AND " + foreignKeyColumn + " = ?";

        blockName = !Strings.isNullOrEmpty(blockName) ? blockName.trim() : blockName;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // disable auto-commit

            try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
                pstmt.setString(1, blockName);
                pstmt.setInt(2, blockId);
                pstmt.setInt(3, whereId);

                int rowsAffected = pstmt.executeUpdate();
                conn.commit();

                if (rowsAffected > 0) {

                    logDB.info(String.format(
                            "Updated block name in table %s - blockId %d, whereId %d, name: %s",
                            tableName, blockId, whereId, blockName));
                    return null; // success
                } else {

                    logDB.warn(String.format(
                            "No record found to update in %s - blockId %d, whereId %d", tableName, blockId, whereId));
                    return new ErrorMessage(
                            "Update Block Name",
                            "No matching record found",
                            String.format("table: %s, blockId: %d, whereId: %d", tableName, blockId, whereId));
                }
            } catch (SQLException e) {

                logDB.error(String.format(
                        "Error updating block name in %s - blockId %d, whereId %d. Error: %s",
                        tableName, blockId, whereId, e.getMessage()));
                return new ErrorMessage("Update Block Name", "Failed to update block name", e.getMessage());
            }
        } catch (SQLException ex) {

            logDB.error(String.format(
                    "Connection error while updating block name in %s - blockId %d, whereId %d. Error: %s",
                    tableName, blockId, whereId, ex.getMessage()));
            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }
    }

    public ErrorMessage updateBlockExportFile(String blockTable, int whereId, int blockId, String exportFile) {

        // Determine the correct ID column
        String idColumn = blockTable.equalsIgnoreCase("block") ? "bot_job_id" : "home_banking_id";
        String updateSQL = "UPDATE " + blockTable + " SET export_file = ? WHERE id = ? AND " + idColumn + " = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

            conn.setAutoCommit(false); // start transaction

            pstmt.setString(1, exportFile);
            pstmt.setInt(2, blockId);
            pstmt.setInt(3, whereId);

            pstmt.addBatch();
            pstmt.executeBatch();
            conn.commit();

            logDB.info(String.format(
                    "Block export file updated. Table: %s, BlockId: %d, WhereId: %d", blockTable, blockId, whereId));

        } catch (SQLException e) {

            logDB.error(String.format(
                    "Error updating block export file. Table: %s, BlockId: %d, Error: %s",
                    blockTable, blockId, e.getMessage()));

            return new ErrorMessage(
                    "Update Block Export File Error", "Failed to update block export file", e.getMessage());
        }

        return null; // Success
    }

    public ErrorMessage insertNewBlock(String tableName, Integer whereId, BlockDetailsDTO blockDTO) {
        String selectIdsSQL = "SELECT id FROM " + tableName + " ORDER BY id";

        // Build insert SQL dynamically depending on table
        String insertSQL;
        if ("block".equalsIgnoreCase(tableName)) {
            insertSQL = "INSERT INTO block (block_order_number, description, name, type_id, active, wait, bot_job_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        } else if ("component_block".equalsIgnoreCase(tableName)) {
            insertSQL =
                    "INSERT INTO component_block (block_order_number, description, name, type_id, active, wait, home_banking_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        } else {
            return new ErrorMessage("Invalid table", "Unknown table: " + tableName, null);
        }

        try (Connection conn = getConnection();
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement();
                PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

            conn.setAutoCommit(false); // Begin transaction

            // Step 1: Get all block IDs before insertion
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            insertStmt.setInt(1, blockDTO.getBlockOrderNumber());
            insertStmt.setString(2, blockDTO.getBlockName() + " description");
            insertStmt.setString(3, blockDTO.getBlockName());
            insertStmt.setInt(4, 1); // type_id
            insertStmt.setInt(5, blockDTO.getActive() ? 1 : 0);
            insertStmt.setInt(6, 3); // wait
            insertStmt.setInt(7, whereId);

            insertStmt.addBatch();
            insertStmt.executeBatch();

            // Step 3: Get all block IDs after insertion
            idsBlockAfter.clear();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectIdsSQL)) {
                while (rsAfter.next()) {
                    idsBlockAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 4: Determine the new ID
            idsBlockAfter.removeAll(idsBefore);
            if (idsBlockAfter.size() == 1) {
                int newId = idsBlockAfter.get(0);

                logDB.info(String.format("Block data saved successfully in %s.\nBlockId: %d", tableName, newId));
            } else {

                logDB.warn("Block inserted, but new ID could not be uniquely identified.");
            }

            conn.commit(); // ✅ Commit transaction
            return null;

        } catch (SQLException error) {

            logDB.error(String.format("Error Initiate New Block in %s: %s", tableName, error.getMessage()));
            return new ErrorMessage("Error Initiate New Block", "Cannot create a new block", error.getMessage());
        }
    }

    // CREATE NEW BOT JOB
    public Integer getNewBotJobId() {
        if (idsBotJobAfter != null && idsBotJobAfter.size() == 1) {
            return idsBotJobAfter.get(0); // return the only new ID
        }
        return -1; // invalid or multiple IDs
    }

    public ErrorMessage createNewBotJob(BotJobLoadDTO createdBotJob) {
        String tableName = "bot_job";
        String insertSQL =
                "INSERT INTO bot_job (name, description, priority, home_banking_id, home_url_id, active) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement();
                PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            // Step 1: Get IDs before insertion
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery("SELECT id FROM " + tableName + " ORDER BY id")) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            // Step 2: Insert new bot job
            pstmt.setString(1, createdBotJob.getName());
            pstmt.setString(
                    2,
                    Strings.isNullOrEmpty(createdBotJob.getDescription())
                            ? createdBotJob.getName() + " description"
                            : createdBotJob.getDescription());
            pstmt.setString(3, createdBotJob.getPriority());
            pstmt.setInt(4, createdBotJob.getHomeBankingId());
            pstmt.setInt(5, createdBotJob.getHomeUrlId());
            pstmt.setInt(6, 1); // active = true

            pstmt.executeUpdate();

            // Step 3: Get IDs after insertion
            idsBotJobAfter.clear();
            try (ResultSet rsAfter = idStmtAfter.executeQuery("SELECT id FROM " + tableName + " ORDER BY id")) {
                while (rsAfter.next()) {
                    idsBotJobAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 4: Keep only the new IDs
            idsBotJobAfter.removeAll(idsBefore);

            logDB.info(String.format("BotJob inserted successfully. New IDs: %s", idsBotJobAfter));

            return null; // null means no error

        } catch (SQLException error) {

            logDB.error(String.format("createNewBotJob - Error: %s", error.getMessage()));

            return new ErrorMessage("Bot Job Insertion Error", "Error inserting a new bot job.", error.getMessage());
        }
    }

    public ErrorMessage updateInstructionsSplitter(List<UpdatedRow> instructions, int oldBlockId, int newBlockId) {
        final int BATCH_SIZE = 100;
        String updateSQL =
                "UPDATE instruction SET instruction_order_number = ?, block_id = ? WHERE id = ? AND block_id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Disable auto-commit

            try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
                int count = 0;

                for (UpdatedRow instruction : instructions) {
                    pstmt.setInt(1, instruction.getInstructionOrderNumber());
                    pstmt.setInt(2, newBlockId);
                    pstmt.setInt(3, instruction.getInstructionId());
                    pstmt.setInt(4, oldBlockId);
                    pstmt.addBatch();
                    count++;

                    if (count % BATCH_SIZE == 0) {
                        int[] rowsAffected = pstmt.executeBatch();
                        conn.commit();

                        logDB.info("Executed batch of " + BATCH_SIZE + " updates for oldBlockId " + oldBlockId
                                + " -> newBlockId " + newBlockId);
                    }
                }

                // Execute any remaining batch
                if (count % BATCH_SIZE != 0) {
                    int[] rowsAffected = pstmt.executeBatch();
                    conn.commit();

                    logDB.info("Executed final batch of " + (count % BATCH_SIZE) + " updates for oldBlockId "
                            + oldBlockId + " -> newBlockId " + newBlockId);
                }

                return null; // Success
            } catch (SQLException e) {
                // rollback changes on error

                logDB.error(String.format(
                        "updateInstructionsSplitter - Error updating instructions from blockId %d to %d. Error: %s",
                        oldBlockId, newBlockId, e.getMessage()));
                return new ErrorMessage("Update Error", "Failed to update instructions", e.getMessage());
            }
        } catch (SQLException ex) {

            logDB.error(String.format(
                    "Connection error while updating instructions from blockId %d to %d. Error: %s",
                    oldBlockId, newBlockId, ex.getMessage()));
            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }
    }

    public ErrorMessage splitBlockAtomic(
            int botJobId,
            BlockDetailsDTO newBlock,
            int originalBlockId,
            List<UpdatedRow> instructions,
            List<BlockOrderDetailDTO> updatedBlocks) {
        return splitBlockAtomic(
                "block",
                "instruction",
                botJobId,
                newBlock,
                originalBlockId,
                instructions,
                updatedBlocks);
    }

    public ErrorMessage splitBlockAtomic(
            String blockTable,
            String instructionTable,
            int ownerId,
            BlockDetailsDTO newBlock,
            int originalBlockId,
            List<UpdatedRow> instructions,
            List<BlockOrderDetailDTO> updatedBlocks) {
        try (Connection conn = getConnection()) {
            try {
                int newBlockId = splitBlockTransaction(
                        conn,
                        blockTable,
                        instructionTable,
                        ownerId,
                        newBlock,
                        originalBlockId,
                        instructions,
                        updatedBlocks);
                idsBlockAfter.clear();
                idsBlockAfter.add(newBlockId);
                newBlock.setBlockId(newBlockId);
                return null;
            } catch (SQLException error) {
                logDB.error(
                        "Atomic block split rolled back for {} owner {}",
                        blockTable,
                        ownerId,
                        error);
                return new ErrorMessage("Split Block Error", "The block split was rolled back", error.getMessage());
            }
        } catch (SQLException error) {
            logDB.error("Connection error during atomic block split", error);
            return new ErrorMessage("Database Connection Error", "Could not split the block", error.getMessage());
        }
    }

    static int splitBlockTransaction(
            Connection connection,
            String blockTable,
            String instructionTable,
            int ownerId,
            BlockDetailsDTO newBlock,
            int originalBlockId,
            List<UpdatedRow> instructions,
            List<BlockOrderDetailDTO> updatedBlocks)
            throws SQLException {
        boolean botJobTables =
                "block".equals(blockTable) && "instruction".equals(instructionTable);
        boolean componentTables =
                "component_block".equals(blockTable)
                        && "component_instruction".equals(instructionTable);
        if (!botJobTables && !componentTables) {
            throw new SQLException("Unsupported block split table pair");
        }
        if (ownerId <= 0 || originalBlockId <= 0 || newBlock == null
                || newBlock.getBlockOrderNumber() == null
                || newBlock.getBlockOrderNumber() <= 0
                || newBlock.getBlockName() == null
                || newBlock.getBlockName().isBlank()
                || instructions == null
                || instructions.isEmpty()) {
            throw new SQLException("Block split context is incomplete");
        }

        String ownerColumn = componentTables ? "home_banking_id" : "bot_job_id";
        String insertSql = "INSERT INTO " + blockTable
                + " (block_order_number, description, name, type_id, active, wait, "
                + ownerColumn + ") VALUES (?, ?, ?, ?, ?, ?, ?)";
        String moveSql = "UPDATE " + instructionTable
                + " SET instruction_order_number = ?, block_id = ?"
                + " WHERE id = ? AND block_id = ? AND " + ownerColumn + " = ?";
        String orderSql = "UPDATE " + blockTable
                + " SET block_order_number = ? WHERE id = ? AND " + ownerColumn + " = ?";

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int newBlockId;
            try (PreparedStatement insert =
                    connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                insert.setInt(1, newBlock.getBlockOrderNumber());
                insert.setString(
                        2,
                        newBlock.getBlockDescription() == null
                                || newBlock.getBlockDescription().isBlank()
                                ? newBlock.getBlockName() + " description"
                                : newBlock.getBlockDescription());
                insert.setString(3, newBlock.getBlockName());
                insert.setInt(4, newBlock.getTypeId() == null ? 1 : newBlock.getTypeId());
                insert.setInt(5, Boolean.FALSE.equals(newBlock.getActive()) ? 0 : 1);
                insert.setInt(6, newBlock.getWait() == null ? 3 : newBlock.getWait());
                insert.setInt(7, ownerId);
                if (insert.executeUpdate() != 1) {
                    throw new SQLException("Expected one inserted block row");
                }
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Database did not return the new block ID");
                    }
                    newBlockId = keys.getInt(1);
                }
            }

            try (PreparedStatement move = connection.prepareStatement(moveSql)) {
                for (UpdatedRow instruction : instructions) {
                    if (instruction == null
                            || instruction.getInstructionId() == null
                            || instruction.getInstructionOrderNumber() == null) {
                        throw new SQLException("Block split contains an incomplete instruction row");
                    }
                    move.setInt(1, instruction.getInstructionOrderNumber());
                    move.setInt(2, newBlockId);
                    move.setInt(3, instruction.getInstructionId());
                    move.setInt(4, originalBlockId);
                    move.setInt(5, ownerId);
                    if (move.executeUpdate() != 1) {
                        throw new SQLException("Instruction " + instruction.getInstructionId()
                                + " was not in source block " + originalBlockId
                                + " for the active owner");
                    }
                }
            }

            try (PreparedStatement order = connection.prepareStatement(orderSql)) {
                for (BlockOrderDetailDTO block :
                        updatedBlocks == null ? List.<BlockOrderDetailDTO>of() : updatedBlocks) {
                    if (block == null
                            || block.getBlockId() == null
                            || block.getBlockOrderNumber() == null) {
                        throw new SQLException("Block split contains an incomplete block order row");
                    }
                    order.setInt(1, block.getBlockOrderNumber());
                    order.setInt(2, block.getBlockId());
                    order.setInt(3, ownerId);
                    if (order.executeUpdate() != 1) {
                        throw new SQLException("Block " + block.getBlockId()
                                + " could not be reordered for the active owner");
                    }
                }
            }

            connection.commit();
            return newBlockId;
        } catch (SQLException | RuntimeException error) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                error.addSuppressed(rollbackFailure);
            }
            if (error instanceof SQLException sqlError) {
                throw sqlError;
            }
            throw new SQLException("Block split transaction failed", error);
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException restoreFailure) {
                logDB.warn("Could not restore block split connection auto-commit", restoreFailure);
            }
        }
    }

    public ErrorMessage rowsGetUpdateName(
            String tableName,
            int whereId, // either bot_job_id or home_banking_id
            List<ParentOperations> listParents) {

        if (listParents == null || listParents.isEmpty()) {
            return null; // nothing to do
        }

        String idColumn = "id";
        String parentIdColumn = "parent_id";
        String whereColumn = tableName.equals("instruction") ? "bot_job_id" : "home_banking_id";

        String instructionTable = tableName.equals("instruction") ? "instruction" : "component_instruction";
        String updateSQL = "UPDATE " + instructionTable + " SET "
                + "operation = ? "
                + "WHERE " + idColumn + " = ? AND " + parentIdColumn + " = ? AND " + whereColumn + " = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

            conn.setAutoCommit(false);

            for (ParentOperations parent : listParents) {
                if ("GET".equals(parent.getActions()) || "SET".equals(parent.getActions())) {
                    pstmt.setString(1, parent.getOperations());
                    pstmt.setInt(2, parent.getId());
                    pstmt.setInt(3, parent.getInstructionId());
                    pstmt.setInt(4, whereId);
                    pstmt.addBatch();
                }
            }

            pstmt.executeBatch();
            conn.commit();

            return null; // success
        } catch (SQLException e) {
            logDB.error("RowsGetUpdateName Error: " + e.getMessage());
            return new ErrorMessage("RowsGetUpdateName Error", "Failed to update parent operations", e.getMessage());
        }
    }

    public ErrorMessage rowsUpdateParentName(
            String tableName,
            int whereId, // either bot_job_id or home_banking_id
            List<ParentOperations> listParents) {

        if (listParents == null || listParents.isEmpty()) {
            return null; // nothing to do
        }

        String idColumn = "id";
        String parentIdColumn = "parent_id";
        String whereColumn = tableName.equals("instruction") ? "bot_job_id" : "home_banking_id";

        String instructionTable = tableName.equals("instruction") ? "instruction" : "component_instruction";
        String updateSQL = "UPDATE " + instructionTable + " SET "
                + "operation = ? "
                + "WHERE " + idColumn + " = ? AND " + parentIdColumn + " = ? AND " + whereColumn + " = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

            conn.setAutoCommit(false);

            for (ParentOperations parent : listParents) {
                if ("SET".equals(parent.getActions())
                        || "GET".equals(parent.getActions())
                        || "CK".equals(parent.getActions())
                        || "PDF CHECK".equals(parent.getActions())
                        || "CSV CHECK".equals(parent.getActions())
                        || "E".equals(parent.getActions())) {
                    pstmt.setString(1, parent.getOperations());
                    pstmt.setInt(2, parent.getId());
                    pstmt.setInt(3, parent.getInstructionId());
                    pstmt.setInt(4, whereId);
                    pstmt.addBatch();
                }
            }

            pstmt.executeBatch();
            conn.commit();

            return null; // success
        } catch (SQLException e) {
            logDB.error("RowsGetUpdateName Error: " + e.getMessage());
            return new ErrorMessage("RowsGetUpdateName Error", "Failed to update parent operations", e.getMessage());
        }
    }

    public ErrorMessage rowsUpdateName(
            String tableName,
            int whereId, // either bot_job_id or home_banking_id
            List<InstructionLoad> instructions) {

        if (instructions == null || instructions.isEmpty()) {
            return null; // nothing to do
        }

        String idColumn = "id";
        String blockIdColumn = "block_id";
        String whereColumn = tableName.equals("instruction") ? "bot_job_id" : "home_banking_id";

        String instructionTable = tableName.equals("instruction") ? "instruction" : "component_instruction";
        // Roadmap 3 Phase 3d: rename action ONLY writes client_named.
        // `name` and `actions` are set at INSERT time from definedName/someText and never
        // change again — they are the canonical keys used by ElementRecoveryService.findOrRecover
        // and by the I:<name> / SET:<name> action token lookup.
        String updateSQL = "UPDATE " + instructionTable + " SET "
                + "client_named = ? "
                + "WHERE " + idColumn + " = ? AND " + blockIdColumn + " = ? AND " + whereColumn + " = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

            conn.setAutoCommit(false);

            for (InstructionLoad instruction : instructions) {
                if (instruction.getClientNamed() == null) {
                    pstmt.setNull(1, java.sql.Types.VARCHAR);
                } else {
                    pstmt.setString(1, instruction.getClientNamed());
                }
                pstmt.setInt(2, instruction.getId());
                pstmt.setInt(3, instruction.getBlockId());
                pstmt.setInt(4, whereId);
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            conn.commit();

            return null; // success
        } catch (SQLException e) {
            logDB.error("Update Instruction Name Error: " + e.getMessage());
            return new ErrorMessage(
                    "Update Instruction Name Error", "Failed to update instruction names", e.getMessage());
        }
    }

    public ErrorMessage updateMoveRowsOrder(
            String tableName, int whereId, List<UpdatedRow> instructions) {
        return updateMoveRowsOrder(tableName, whereId, instructions, null, 2);
    }

    public ErrorMessage updateMoveRowsOrder(
            String tableName, int whereId, List<UpdatedRow> instructions,
            String expectedRevision, Integer layoutVersion) {
        if (instructions == null || instructions.isEmpty()) {
            return new ErrorMessage(
                    "Update Move Rows Order Error",
                    "Failed to update instruction order numbers",
                    "ROW_MOVE layout must include every instruction owned by the active owner.");
        }
        try (Connection connection = getConnection()) {
            new InstructionMoveTransaction().execute(
                    connection, tableName, whereId, instructions, expectedRevision, layoutVersion);
            return null;
        } catch (SQLException error) {
            logDB.error("Update Move Rows Order Error: " + error.getMessage());
            return new ErrorMessage(
                    "Update Move Rows Order Error", "Failed to update instruction order numbers", error.getMessage());
        }
    }

    public ErrorMessage rollBackBlocksRows(
            String targetTable, int ownerId, SplitDTO splitDTO) {
        if (!"instruction".equals(targetTable)
                && !"component_instruction".equals(targetTable)) {
            return new ErrorMessage(
                    "RollBack Error",
                    "Unsupported instruction table",
                    "Only instruction workspaces can roll back a block.");
        }
        if (ownerId <= 0
                || splitDTO == null
                || splitDTO.getBlockId() == null
                || splitDTO.getBlockId() <= 0
                || splitDTO.getGraphRevision() == null
                || splitDTO.getGraphRevision().isBlank()
                || splitDTO.getUpdatedBlocks() == null
                || splitDTO.getUpdatedBlocks().isEmpty()
                || splitDTO.getUpdatedRows() == null
                || splitDTO.getUpdatedRows().isEmpty()) {
            return new ErrorMessage(
                    "RollBack Error",
                    "Invalid rollback request",
                    "The owner, revision, complete block catalog, destination block, "
                            + "and instruction rows are required.");
        }
        try (Connection conn = getConnection()) {
            try {
                new BlockRollbackTransaction()
                        .execute(
                                conn,
                                targetTable,
                                ownerId,
                                splitDTO.getBlockId(),
                                splitDTO.getGraphRevision(),
                                splitDTO.getUpdatedBlocks(),
                                splitDTO.getUpdatedRows());
                return null;
            } catch (SQLException error) {
                logDB.error(
                        "RollBackBlocks - Error updating BlockId {}. Error: {}",
                        splitDTO.getBlockId(),
                        error.getMessage());
                return new ErrorMessage(
                        "RollBack Error",
                        "Failed to roll back instructions",
                        error.getMessage());
            }
        } catch (SQLException ex) {
            logDB.error(
                    "Connection error while rolling back BlockId {}. Error: {}",
                    splitDTO.getBlockId(),
                    ex.getMessage());
            return new ErrorMessage(
                    "Database Connection Error",
                    "Could not connect to database",
                    ex.getMessage());
        }
    }

    public void rollBackBlocksOrder(RollBackBlocksDTO rollBackBlocksDTO) {
        // Build the SQL update statement

        try (Statement stmt = getConnection().createStatement()) {

            String updateSQL = "UPDATE block SET  "
                    + " block_order_number = " + 1
                    + " WHERE id = " + rollBackBlocksDTO.getBlockId()
                    + " and bot_job_id = " + rollBackBlocksDTO.getBotJobId();

            int rowsAffected = stmt.executeUpdate(updateSQL);
            if (rowsAffected > 0) {

                logDB.warn(String.format(
                        "rollBackBlocksOrder - Block Order Reset for blockId: %d - Name: %s",
                        rollBackBlocksDTO.getBlockId(), rollBackBlocksDTO.getBlockName()));
            } else {

                logDB.warn(String.format(
                        "RollBackBlocks - No matching record found to update for blockId: %d - Name: %s",
                        rollBackBlocksDTO.getBlockId(), rollBackBlocksDTO.getBlockName()));
            }
        } catch (SQLException e) {

            logDB.error(String.format(
                    "This BlockId '%d' - Name: %s \n cannot be updated.\nError: %s",
                    rollBackBlocksDTO.getBlockId(), rollBackBlocksDTO.getBlockName(), e.getMessage()));
            return;
        }
    }

    public ErrorMessage deleteBlockDirect(
            String tableName,
            int whereId, // bot_job_id or home_banking_id
            int blockId) {
        // Determine the correct column based on the table
        String whereColumn;
        if ("block".equalsIgnoreCase(tableName)) {
            whereColumn = "bot_job_id";
        } else if ("component_block".equalsIgnoreCase(tableName)) {
            whereColumn = "home_banking_id";
        } else {
            return new ErrorMessage("Invalid Table", "Unknown table: " + tableName, null);
        }

        ErrorMessage errorMessage;
        String instructionTable = tableName.equals("block") ? "instruction" : "component_instruction";
        loadInstructions(whereId, blockId, -1, instructionTable);
        List<InstructionLoad> lstInstruc = instructionTable.equals("instruction")
                ? performLists.getListInstruction()
                : performLists.getListInstructionComp();

        if (!lstInstruc.isEmpty()) {
            String referenceTable = tableName.equals("block") ? "reference" : "component_reference";
            errorMessage = deleteReferencesBatch(referenceTable, whereId, lstInstruc);

            if (errorMessage == null) {
                errorMessage = deleteInstructionsBatch(instructionTable, whereId, lstInstruc);
            }

            if (errorMessage != null) {

                logDB.error("Error: " + errorMessage.getErrorTitle() + "-" + errorMessage.getErrorMessage());
            }
        }

        String deleteSQL = "DELETE FROM " + tableName + " WHERE id = ? AND " + whereColumn + " = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // disable auto-commit

            try (PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
                pstmt.setInt(1, blockId);
                pstmt.setInt(2, whereId);

                int rowsAffected = pstmt.executeUpdate();
                conn.commit();

                if (rowsAffected > 0) {

                    logDB.info(String.format(
                            "The Block id %d has been successfully deleted from %s = %d.",
                            blockId, whereColumn, whereId));
                } else {

                    logDB.warn(String.format(
                            "No matching record found for blockId %d in %s = %d.", blockId, whereColumn, whereId));
                }

                return null; // success
            } catch (SQLException e) {

                logDB.error(String.format(
                        "Error deleting blockId %d from %s = %d. Error: %s",
                        blockId, whereColumn, whereId, e.getMessage()));
                return new ErrorMessage("Delete Block Error", "Failed to delete block", e.getMessage());
            }
        } catch (SQLException ex) {

            logDB.error(String.format(
                    "Connection error while deleting blockId %d from %s = %d. Error: %s",
                    blockId, whereColumn, whereId, ex.getMessage()));
            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }
    }

    public ErrorMessage loadComponentsComplete(int homeBankingId, int botJobIdDest, String botJobNameDest) {
        String query = "\n" + "\n"
                + "SELECT \n"
                + "    hb.id AS home_banking_id, \n"
                + "\t-1 as bot_job_id,\n"
                + "\t'No Bot Job Name' as bot_job_name,\n"
                + "\tblk.id AS block_id, \n"
                + "    blk.block_order_number, \n"
                + "    blk.name AS block_name, \n"
                + "    blk.description AS block_description, \n"
                + "    blk.type_id, \n"
                + "    blk.export_file,\n"
                + "    blk.active AS block_active, \n"
                + "    blk.wait,\n"
                + "    bli.id AS instruction_id, \n"
                + "    bli.instruction_order_number, \n"
                + "    bli.actions, \n"
                + "    bli.name AS instruction_name, \n"
                + "    bli.client_named, \n"
                + "    bli.xpath, \n"
                + "    bli.coordinates, \n"
                + "    bli.iframe_xpath, \n"
                + "    bli.tag_name, \n"
                + "    bli.shadow_host, \n"
                + "    bli.shadow_root, \n"
                + "    bli.css_selector, \n"
                + "    bli.description AS instruction_description, \n"
                + "    bli.force_coordinates, \n"
                + "    bli.optional, \n"
                + "    bli.block_marked, \n"
                + "    bli.default_value, \n"
                + "    bli.action_custom_max_wait_sec, \n"
                + "    bli.on_hold_seconds, \n"
                + "    bli.codified, \n"
                + "    bli.export_to_abr, \n"
                + "    bli.operation, \n"
                + "    bli.parent_id, \n"
                + "    bli.active AS instruction_active,\n"
                + "    irl.reference_type, \n"
                + "    irl.value AS reference_value, \n"
                + "    bli.variable_id, \n"
                + "    bli.parent_block_id \n"
                + "FROM home_banking hb\n"
                + "LEFT JOIN component_block blk ON blk.home_banking_id = hb.id\n"
                + "JOIN component_instruction bli ON bli.block_id = blk.id\n"
                + "LEFT JOIN component_reference irl ON irl.instruction_id = bli.id\n"
                + "WHERE hb.id = "
                + homeBankingId + "\n"
                + "ORDER BY hb.id, blk.block_order_number, bli.instruction_order_number, bli.id ASC;";

        try (Connection connection = getConnection();
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            Map<Integer, BotJobLoadDTO> botJobMapDTO = new HashMap<>();
            Map<Integer, BlockLoadDTO> blockMapDTO = new HashMap<>();
            Map<Integer, InstructionLoad> instructionMapDTO = new HashMap<>();

            performLists.getListBotJobComp().clear();

            while (rs.next()) {
                //                int botJobId = rs.getInt("bot_job_id");
                BotJobLoadDTO botJobDTO = botJobMapDTO.get(botJobIdDest);

                if (botJobDTO == null) {
                    botJobDTO = new BotJobLoadDTO();
                    botJobDTO.setId(botJobIdDest);
                    botJobDTO.setHomeBankingId(rs.getInt("home_banking_id"));
                    botJobDTO.setName(botJobNameDest); // rs.getString("bot_job_name"));
                    botJobDTO.setBlockLoadDTOList(new ArrayList<>());
                    botJobMapDTO.put(botJobIdDest, botJobDTO);
                    performLists.getListBotJobComp().add(botJobDTO);
                }

                int blockId = rs.getInt("block_id");
                BlockLoadDTO blockDTO = blockMapDTO.get(blockId);

                if (blockDTO == null) {
                    blockDTO = new BlockLoadDTO();
                    blockDTO.setId(blockId);
                    blockDTO.setBlockOrderNumber(rs.getInt("block_order_number"));
                    blockDTO.setName(rs.getString("block_name"));
                    blockDTO.setDescription(rs.getString("block_description"));
                    blockDTO.setTypeId(rs.getInt("type_id"));
                    blockDTO.setActive(rs.getBoolean("block_active"));
                    blockDTO.setWait(rs.getInt("wait"));
                    blockDTO.setBotJobId(botJobDTO.getId());
                    blockDTO.setBotJobName(botJobDTO.getName());
                    blockDTO.setExportFile(rs.getString("export_file"));

                    blockDTO.setInstructionLoad(new ArrayList<>());
                    botJobDTO.getBlockLoadDTOList().add(blockDTO);
                    blockMapDTO.put(blockId, blockDTO);
                }

                int instructionId = rs.getInt("instruction_id");
                InstructionLoad instruction = instructionMapDTO.get(instructionId);

                if (instruction == null) {
                    instruction = new InstructionLoad();
                    instruction.setId(instructionId);
                    instruction.setInstructionOrderNumber(rs.getInt("instruction_order_number"));
                    instruction.setActions(rs.getString("actions"));
                    instruction.setName(rs.getString("instruction_name"));
                    instruction.setClientNamed(rs.getString("client_named"));
                    instruction.setXpath(rs.getString("xpath"));
                    instruction.setCoordinates(rs.getString("coordinates"));
                    instruction.setForceCoordinates(rs.getString("force_coordinates"));
                    instruction.setIFrameXPath(rs.getString("iframe_xpath"));

                    instruction.setTagName(rs.getString("tag_name"));
                    instruction.setShadowHost(rs.getString("shadow_host"));
                    instruction.setShadowRoot(rs.getString("shadow_root"));
                    instruction.setCssSelector(rs.getString("css_selector"));

                    instruction.setDescription(rs.getString("instruction_description"));
                    instruction.setOptional(rs.getBoolean("optional"));
                    instruction.setBlockMarked(rs.getBoolean("block_marked"));
                    instruction.setDefaultValue(rs.getString("default_value"));
                    instruction.setActionCustomMaxWaitSec(rs.getInt("action_custom_max_wait_sec"));
                    instruction.setOnHoldSeconds(rs.getInt("on_hold_seconds"));
                    instruction.setCodified(rs.getBoolean("codified"));
                    instruction.setExportToABR(rs.getBoolean("export_to_abr"));
                    instruction.setOperation(rs.getString("operation"));
                    instruction.setBlockId(rs.getInt("block_id"));
                    instruction.setParentBlockId(readNullableInteger(rs, "parent_block_id"));

                    if (instruction.getName().equals("EXCEL GOTO")
                            && ((instruction.getParentBlockId() != null && instruction.getParentBlockId() == 0)
                                    || instruction.getParentBlockId() == null)) {
                        instruction.setParentBlockId(instruction.getBlockId());
                    }

                    instruction.setParentId(readNullableInteger(rs, "parent_id"));
                    instruction.setVariableId(readNullableInteger(rs, "variable_id"));

                    instruction.setInstructionActive(rs.getBoolean("instruction_active"));

                    instruction.setReferenceLoadDTOList(new ArrayList<>());
                    blockDTO.getInstructionLoad().add(instruction);
                    instructionMapDTO.put(instructionId, instruction);
                }

                String referenceType = rs.getString("reference_type");
                if (referenceType != null) {
                    ReferenceLoadDTO reference = new ReferenceLoadDTO();
                    reference.setReferenceType(referenceType);
                    reference.setValue(rs.getString("reference_value"));
                    instruction.getReferenceLoadDTOList().add(reference);
                }
            }
        } catch (SQLException error) {

            logDB.error(String.format(
                    "Error loadComponentsComplete for Home Bank %d. Error: %s", homeBankingId, error.getMessage()));
            return new ErrorMessage(
                    "Error Loading Components Complete Job",
                    "Error loading component complete Job",
                    error.getMessage());
        }

        return null;
    }

    public ErrorMessage reorderInstructionsPerBlock(
            List<InstructionLoad> rowList, String tableName, boolean forceOrder) {
        final int BATCH_SIZE = 100;
        int orderNumber = 1;
        int count = 0;

        String updateSQL = "UPDATE " + tableName + " SET instruction_order_number = ? WHERE id = ? AND block_id = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

            conn.setAutoCommit(false); // Start transaction

            for (InstructionLoad instruction : rowList) {
                if (forceOrder) {
                    instruction.setInstructionOrderNumber(orderNumber);
                }

                Integer instrId = instruction.getId();
                Integer blockId = instruction.getBlockId();

                if (instrId == null || blockId == null) {

                    logDB.warn("Skipping reorder: instructionId or blockId is null.");
                    continue;
                }

                pstmt.setInt(1, instruction.getInstructionOrderNumber());
                pstmt.setInt(2, instrId);
                pstmt.setInt(3, blockId);

                pstmt.addBatch();
                orderNumber++;

                if (++count % BATCH_SIZE == 0) {
                    pstmt.executeBatch();
                    pstmt.clearBatch();
                }
            }

            // Execute remaining batch
            if (count % BATCH_SIZE != 0) {
                pstmt.executeBatch();
            }

            conn.commit(); // Commit transaction
            return null; // success

        } catch (SQLException e) {

            logDB.error("Error batch updating instruction order numbers: " + e.getMessage());
            return new ErrorMessage(
                    "Reorder Error", "Failed to reorder instructions in table " + tableName, e.getMessage());
        }
    }

    public ErrorMessage reorderInstructionsListBlock(
            List<BlockLoadDTO> blockLoad, String tableName, boolean forceOrder) {
        final int BATCH_SIZE = 100;
        int count = 0;

        String updateSQL = "UPDATE " + tableName + " SET instruction_order_number = ? WHERE id = ? AND block_id = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

            conn.setAutoCommit(false); // Start transaction

            // iterate over each block
            if (blockLoad != null) {
                for (BlockLoadDTO block : blockLoad) {
                    int orderNumber = 1; // restart order per block

                    if (block.getInstructionLoad() == null) {
                        continue; // no instructions in this block
                    }

                    for (InstructionLoad instruction : block.getInstructionLoad()) {
                        if (forceOrder) {
                            instruction.setInstructionOrderNumber(orderNumber);
                        }

                        Integer instrId = instruction.getId();
                        Integer blockId = block.getId(); // take blockId from block

                        if (instrId == null || blockId == null) {

                            logDB.warn("Skipping reorder: instructionId or blockId is null.");
                            continue;
                        }

                        pstmt.setInt(1, instruction.getInstructionOrderNumber());
                        pstmt.setInt(2, instrId);
                        pstmt.setInt(3, blockId);

                        pstmt.addBatch();
                        orderNumber++;
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            pstmt.executeBatch();
                            pstmt.clearBatch();
                        }
                    }
                }
            }

            // Execute any remaining batch
            if (count % BATCH_SIZE != 0) {
                pstmt.executeBatch();
            }

            conn.commit(); // Commit transaction
            return null; // success

        } catch (SQLException e) {

            logDB.error("Error batch updating instruction order numbers: " + e.getMessage());
            return new ErrorMessage(
                    "Reorder Error", "Failed to reorder instructions in table " + tableName, e.getMessage());
        }
    }

    public ErrorMessage deleteBotJobData(int botJobId) {
        String deleteSQL = "DELETE FROM bot_job WHERE id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(deleteSQL)) {
                ps.setInt(1, botJobId);
                ps.executeUpdate();
                conn.commit();
                return null; // success
            } catch (SQLException error) {
                return new ErrorMessage("Error deleting Bot Job", "I cannot delete the BotJob Now", error.getMessage());
            }

        } catch (SQLException error) {
            return new ErrorMessage("Error deleting Bot Job", "I cannot delete the BotJob Now", error.getMessage());
        }
    }

    public ErrorMessage updateBotJobDetails(int botJobId, int homeUrlId, String name, String description) {
        String updateSQL = "UPDATE bot_job SET name = ?, description = ?, home_url_id = ? WHERE id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Disable auto-commit

            try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
                pstmt.setString(1, name);
                pstmt.setString(2, description);
                pstmt.setInt(3, homeUrlId);
                pstmt.setInt(4, botJobId);

                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {

                    logDB.info(String.format("Bot Job id %d successfully updated!", botJobId));
                } else {
                    conn.commit(); // Commit even though nothing changed
                    return new ErrorMessage(
                            "No matching record found for Bot Job",
                            "Update Warning",
                            "No Bot Job with ID '" + botJobId + "' exists.");
                }

                conn.commit(); // Commit transaction
                return null; // Success, no error
            } catch (SQLException e) {

                logDB.error(String.format("Error updating BotJobId %d. Error: %s", botJobId, e.getMessage()));
                return new ErrorMessage("Bot Job Update Error", "Error updating Bot Job", e.getMessage());
            }
        } catch (SQLException ex) {

            logDB.error(
                    String.format("Connection error while updating BotJobId %d. Error: %s", botJobId, ex.getMessage()));
            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }
    }

    public boolean updateStatusBotJob(int botJobId, int status) {
        // Build the SQL delete statement
        try (Statement stmt = getConnection().createStatement()) {

            String updateSQL = "UPDATE bot_job set active = '" + status + "' WHERE id = " + botJobId;

            // Execute the update statement and check if any rows were affected
            int rowsAffected = stmt.executeUpdate(updateSQL);
            if (rowsAffected > 0) {

                logDB.info(String.format("The Status Bot Job  id %d has been successfully updated!", botJobId));
            } else {

                logDB.warn(String.format("No matching record found for botJobId %d.", botJobId));
            }
            return true;
        } catch (SQLException e) {

            logDB.error(String.format("Error updating Status for BotJobId ID %d. Error: %s", botJobId, e.getMessage()));
        }
        return false;
    }

    public BlockLoadDTO loadBlockByBotId(int botJobId, int blockId) {
        // List to store the fetched instructions
        BlockLoadDTO blockLoadDTO = new BlockLoadDTO();

        // Build the SQL query statement
        String querySQL = "SELECT * FROM block WHERE block_id = " + blockId + " and bot_job_id = " + botJobId;

        // Execute the query and process the result set
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(querySQL)) {

            while (rs.next()) {
                // Assuming you have an Instruction class, populate it with data from the ResultSet
                blockLoadDTO.setId(rs.getInt("id"));
                blockLoadDTO.setBotJobId(botJobId);
                blockLoadDTO.setActive(rs.getBoolean("active"));
                blockLoadDTO.setBlockOrderNumber(rs.getInt("block_order_number"));
                blockLoadDTO.setDescription(rs.getString("description"));
                blockLoadDTO.setExportFile(rs.getString("export_file"));
                blockLoadDTO.setTypeId(rs.getInt("type"));
                blockLoadDTO.setBlockOrderNumber(rs.getInt("wait"));
            }

            logDB.info(String.format("Fetched Block \"%s\"", blockLoadDTO.getName()));

        } catch (SQLException e) {

            logDB.error(String.format(
                    "Error fetching Block ID %d with BotJob Id %d. Error: %s: ", blockId, botJobId, e.getMessage()));
        }

        return blockLoadDTO;
    }

    public BlockLoadDTO loadAllBlockByBotId(int botJobId, int blockId) {
        // List to store the fetched instructions
        BlockLoadDTO blockLoadDTO = new BlockLoadDTO();

        // Build the SQL query statement
        String querySQL = "SELECT * FROM block WHERE block_id = " + blockId + " and bot_job_id = " + botJobId;

        // Execute the query and process the result set
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(querySQL)) {

            while (rs.next()) {
                // Assuming you have an Instruction class, populate it with data from the ResultSet
                blockLoadDTO.setId(rs.getInt("id"));
                blockLoadDTO.setBotJobId(botJobId);
                blockLoadDTO.setActive(rs.getBoolean("active"));
                blockLoadDTO.setBlockOrderNumber(rs.getInt("block_order_number"));
                blockLoadDTO.setDescription(rs.getString("description"));
                blockLoadDTO.setExportFile(rs.getString("export_file"));
                blockLoadDTO.setTypeId(rs.getInt("type"));
                blockLoadDTO.setBlockOrderNumber(rs.getInt("wait"));
            }

            logDB.info(String.format("Fetched Block \"%s\"", blockLoadDTO.getName()));

        } catch (SQLException e) {

            logDB.error(String.format(
                    "Error fetching Block ID %d with BotJob Id %d. Error: %s: ", blockId, botJobId, e.getMessage()));
        }

        return blockLoadDTO;
    }

    public ErrorMessage loadQuickBotJobs() {
        performLists.getQuickBotJobs().clear();

        // Build the query dynamically, adding a WHERE only when mobileDevices is true
        String selectPart = "SELECT bot.id AS bot_job_id, bot.name AS bot_job_name, "
                + "       bot.description AS bot_job_description, bot.priority AS bot_job_priority, "
                + "       bot.home_banking_id, bot.home_url_id, "
                + "       hu.url AS home_banking_url, "
                + "       hb.name AS home_banking_name, "
                + "       hb.priority AS home_banking_priority, hb.search_config, "
                + "       hb.options_config, hb.cookies, hb.driver_session, "
                + "       hb.username, hb.password, "
                + "       bot.active, "
                + "       b.id AS block_id, b.block_order_number, b.name AS block_name, "
                + "       b.description AS block_description, b.type_id, b.active AS block_active, b.wait ";

        String fromPart = "FROM bot_job bot "
                + "LEFT JOIN home_banking hb ON bot.home_banking_id = hb.id "
                + "LEFT JOIN home_url hu ON bot.home_url_id = hu.id AND hu.home_banking_id = hb.id "
                + "LEFT JOIN block b ON b.bot_job_id = bot.id ";

        String wherePart = "";
        if (this.mobileDevices) {
            // Case-insensitive prefix match for "Android:" or "iOS:"
            wherePart = "WHERE (UPPER(bot.priority) LIKE 'ANDROID%' OR UPPER(bot.priority) LIKE 'IOS%') ";
        }

        String orderPart = "ORDER BY bot.id ASC, b.block_order_number ASC;";

        String query = selectPart + fromPart + wherePart + orderPart;

        Map<Integer, BotJobLoadDTO> botJobMap = new HashMap<>();
        Map<Integer, BlockLoadDTO> blockMap = new HashMap<>();

        try (Connection connection = getConnection();
                PreparedStatement pstmt = connection.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int botJobId = rs.getInt("bot_job_id");

                // Get or create BotJobLoadDTO
                BotJobLoadDTO botJobDTO = botJobMap.get(botJobId);
                if (botJobDTO == null) {
                    botJobDTO = new BotJobLoadDTO();
                    botJobDTO.setId(botJobId);
                    botJobDTO.setName(rs.getString("bot_job_name"));
                    botJobDTO.setDescription(rs.getString("bot_job_description"));

                    String priority = rs.getString("bot_job_priority");
                    if (priority == null || priority.trim().isEmpty()) {
                        priority = "Web App";
                    }

                    botJobDTO.setPriority(priority);
                    botJobDTO.setHomeBankingId(rs.getInt("home_banking_id"));
                    botJobDTO.setHomeUrlId(rs.getInt("home_url_id"));
                    botJobDTO.setActive(rs.getBoolean("active"));

                    // Set HomeBankingLoadDTO
                    Integer homeBankingId = rs.getObject("home_banking_id", Integer.class);
                    if (homeBankingId != null) {
                        HomeBankingLoadDTO homeBankingDTO = new HomeBankingLoadDTO();
                        homeBankingDTO.setId(homeBankingId);
                        homeBankingDTO.setUrl(rs.getString("home_banking_url"));
                        homeBankingDTO.setName(rs.getString("home_banking_name"));
                        homeBankingDTO.setPriority(rs.getString("home_banking_priority"));
                        homeBankingDTO.setSearchConfig(rs.getString("search_config"));
                        homeBankingDTO.setOptionsConfig(rs.getString("options_config"));
                        homeBankingDTO.setCookies(rs.getString("cookies"));
                        homeBankingDTO.setDriverSession(rs.getString("driver_session"));
                        homeBankingDTO.setUsername(rs.getString("username"));
                        homeBankingDTO.setPassword(rs.getString("password"));

                        botJobDTO.setHomeBankingLoadDTO(homeBankingDTO);
                    }

                    botJobDTO.setBlockLoadDTOList(new ArrayList<>());
                    botJobMap.put(botJobId, botJobDTO);
                    performLists.getQuickBotJobs().add(botJobDTO);
                }

                // Add BlockLoadDTO if present
                int blockId = rs.getInt("block_id");
                if (!rs.wasNull()) {
                    BlockLoadDTO blockDTO = blockMap.get(blockId);
                    if (blockDTO == null) {
                        blockDTO = new BlockLoadDTO();
                        blockDTO.setId(blockId);
                        blockDTO.setBlockOrderNumber(rs.getInt("block_order_number"));
                        blockDTO.setName(rs.getString("block_name"));
                        blockDTO.setDescription(rs.getString("block_description"));
                        blockDTO.setTypeId(rs.getInt("type_id"));
                        blockDTO.setActive(rs.getBoolean("block_active"));
                        blockDTO.setWait(rs.getInt("wait"));

                        blockDTO.setBotJobId(botJobId);
                        blockDTO.setBotJobName(botJobDTO.getName());

                        blockMap.put(blockId, blockDTO);
                        botJobDTO.getBlockLoadDTOList().add(blockDTO);
                    }
                }
            }

        } catch (SQLException e) {
            logDB.error(String.format("Error loadQuickBotJobs: %s", e.getMessage()));
            return new ErrorMessage("Failed to load Quick Bot Jobs", "Database query error", e.getMessage());
        }

        return null;
    }

    public ErrorMessage updateInstructionStatus(
            String tableName, // "instruction" or "component_instruction"
            int whereId, // bot_job_id or home_banking_id
            int instructionId,
            int blockId,
            int parentId,
            String actions,
            boolean active) {

        final int BATCH_SIZE = 100;
        boolean isConditional =
                "IF".equals(actions) || "ELSEIF".equals(actions) || "ELSE".equals(actions) || "ENDIF".equals(actions);

        // Determine correct ID column based on table type
        String idColumn = tableName.equalsIgnoreCase("instruction") ? "bot_job_id" : "home_banking_id";

        String updateSQL;
        if (isConditional) {
            updateSQL = "UPDATE " + tableName + " SET active = ? WHERE block_id = ? AND parent_id = ? AND " + idColumn
                    + " = ?";
        } else {
            updateSQL =
                    "UPDATE " + tableName + " SET active = ? WHERE id = ? AND block_id = ? AND " + idColumn + " = ?";
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Disable auto-commit

            try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

                // Postgres compatibility: 1/0 instead of true/false
                if (POSTGRES_DB) {
                    pstmt.setInt(1, active ? 1 : 0);
                } else {
                    pstmt.setBoolean(1, active);
                }

                if (isConditional) {
                    pstmt.setInt(2, blockId);
                    pstmt.setInt(3, parentId);
                    pstmt.setInt(4, whereId);
                } else {
                    pstmt.setInt(2, instructionId);
                    pstmt.setInt(3, blockId);
                    pstmt.setInt(4, whereId);
                }

                pstmt.addBatch();
                int[] batchResults = pstmt.executeBatch();
                conn.commit();

                int rowsAffected = Arrays.stream(batchResults).sum();

                if (rowsAffected > 0) {

                    logDB.info(String.format(
                            "Instruction status updated. Rows affected: %d (InstructionId: %d, BlockId: %d, Actions: %s, WhereId: %d)",
                            rowsAffected, instructionId, blockId, actions, whereId));
                } else {

                    logDB.warn(String.format(
                            "No instruction updated (InstructionId: %d, BlockId: %d, Actions: %s, WhereId: %d)",
                            instructionId, blockId, actions, whereId));
                }

            } catch (SQLException e) {
                conn.rollback(); // Rollback if failure

                logDB.error(String.format(
                        "Error updating instruction status. InstructionId: %d, Error: %s",
                        instructionId, e.getMessage()));
                return new ErrorMessage(
                        "Update Instruction Status Error", "Failed to update instruction status", e.getMessage());
            }

        } catch (SQLException ex) {

            logDB.error(String.format(
                    "Connection error while updating instruction status. InstructionId: %d, Error: %s",
                    instructionId, ex.getMessage()));
            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }

        return null; // Success
    }

    public ErrorMessage updateInstructionActions(
            String tableName, // "instruction" or "component_instruction"
            int whereId, // bot_job_id or home_banking_id
            int instructionId,
            int blockId,
            String actions) {

        // Basic safety check
        if (!"instruction".equalsIgnoreCase(tableName) && !"component_instruction".equalsIgnoreCase(tableName)) {
            return new ErrorMessage("Invalid table", "Unsupported tableName", tableName);
        }

        String idColumn = tableName.equalsIgnoreCase("instruction") ? "bot_job_id" : "home_banking_id";

        String sql = "UPDATE " + tableName + " SET actions = ? WHERE id = ? AND block_id = ? AND " + idColumn + " = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, actions);
            pstmt.setInt(2, instructionId);
            pstmt.setInt(3, blockId);
            pstmt.setInt(4, whereId);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                logDB.warn("No instruction updated. InstructionId: " + instructionId);
            } else {
                logDB.info("Instruction actions updated. InstructionId: " + instructionId + ", Actions: " + actions);
            }

            return null;

        } catch (SQLException e) {
            logDB.error("Error updating instruction actions. InstructionId: " + instructionId + ", Error: "
                    + e.getMessage());

            return new ErrorMessage(
                    "Update Instruction Actions Error", "Failed to update instruction actions", e.getMessage());
        }
    }

    /**
     * Update the force_coordinates flag column for a single instruction. Accepts
     * any combination of {@code F}, {@code E}, {@code T}, {@code N} (order-insensitive).
     * Passed value is stored verbatim — callers are responsible for canonicalising.
     */
    public ErrorMessage updateInstructionForceCoordinates(
            String tableName, // "instruction" or "component_instruction"
            int whereId, // bot_job_id or home_banking_id
            int instructionId,
            int blockId,
            String forceCoordinates) {

        if (!"instruction".equalsIgnoreCase(tableName) && !"component_instruction".equalsIgnoreCase(tableName)) {
            return new ErrorMessage("Invalid table", "Unsupported tableName", tableName);
        }

        String idColumn = tableName.equalsIgnoreCase("instruction") ? "bot_job_id" : "home_banking_id";
        String sql = "UPDATE " + tableName + " SET force_coordinates = ? WHERE id = ? AND block_id = ? AND " + idColumn
                + " = ?";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, forceCoordinates == null ? "" : forceCoordinates);
            pstmt.setInt(2, instructionId);
            pstmt.setInt(3, blockId);
            pstmt.setInt(4, whereId);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                logDB.warn("No instruction force_coordinates updated. InstructionId: " + instructionId);
            } else {
                logDB.info("Instruction force_coordinates updated. InstructionId: " + instructionId + ", Flags: '"
                        + forceCoordinates + "'");
            }

            return null;

        } catch (SQLException e) {
            logDB.error("Error updating instruction force_coordinates. InstructionId: " + instructionId + ", Error: "
                    + e.getMessage());
            return new ErrorMessage(
                    "Update Instruction ForceCoordinates Error",
                    "Failed to update instruction force_coordinates",
                    e.getMessage());
        }
    }

    public ErrorMessage updateBlockStatus(
            String tableName, // "block" or "component_block"
            int whereId, // bot_job_id or home_banking_id
            int blockId,
            boolean blockActive) {

        String idColumn = tableName.equalsIgnoreCase("block") ? "bot_job_id" : "home_banking_id";

        String updateSQL = "UPDATE " + tableName + " SET active = ? WHERE id = ? AND " + idColumn + " = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Disable auto-commit

            try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

                // Postgres compatibility: 1/0 instead of true/false
                if (POSTGRES_DB) {
                    pstmt.setInt(1, blockActive ? 1 : 0);
                } else {
                    pstmt.setBoolean(1, blockActive);
                }

                pstmt.setInt(2, blockId);
                pstmt.setInt(3, whereId);

                pstmt.addBatch();
                int[] batchResults = pstmt.executeBatch();
                conn.commit();

                int rowsAffected = Arrays.stream(batchResults).sum();

                if (rowsAffected > 0) {

                    logDB.info(String.format(
                            "Block status updated. Table: %s, BlockId: %d, Active: %s",
                            tableName, blockId, blockActive));
                } else {

                    logDB.warn(String.format(
                            "No block status updated. Table: %s, WhereId: %d, BlockId: %d",
                            tableName, whereId, blockId));
                }

            } catch (SQLException e) {
                conn.rollback(); // Rollback if failure

                logDB.error(String.format(
                        "Error updating block status in table '%s'. Error: %s", tableName, e.getMessage()));
                return new ErrorMessage("Update Block Status Error", "Failed to update block status", e.getMessage());
            }

        } catch (SQLException ex) {

            logDB.error(String.format(
                    "Connection error while updating block status in table '%s'. Error: %s",
                    tableName, ex.getMessage()));
            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }

        return null; // Success
    }

    public ErrorMessage updateBlockAndInstructionStatus(
            String blockTable,
            String instructionTable,
            int whereId,
            int blockId,
            boolean blockActive) {
        try (Connection connection = getConnection()) {
            new BlockStatusTransaction().execute(
                    connection,
                    blockTable,
                    instructionTable,
                    whereId,
                    blockId,
                    blockActive);
            return null;
        } catch (SQLException error) {
            logDB.error(
                    "Atomic block status update failed for table {} owner {} block {}: {}",
                    blockTable,
                    whereId,
                    blockId,
                    error.getMessage());
            return new ErrorMessage(
                    "Update Block Status Error",
                    "The block and instruction statuses were not saved",
                    error.getMessage());
        }
    }

    public ErrorMessage updateInstructionStatusByBlock(
            String tableName, // "instruction" or "component_instruction"
            int whereId, // bot_job_id or home_banking_id
            int blockId,
            boolean blockActive) {

        final int BATCH_SIZE = 100;

        String updateSQL = "UPDATE " + tableName
                + " SET active = ? WHERE block_id = ? AND "
                + (tableName.equalsIgnoreCase("instruction") ? "bot_job_id" : "home_banking_id") + " = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Disable auto-commit

            try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

                // Postgres compatibility: use 1/0 instead of true/false
                if (POSTGRES_DB) {
                    pstmt.setInt(1, blockActive ? 1 : 0);
                } else {
                    pstmt.setBoolean(1, blockActive);
                }

                pstmt.setInt(2, blockId);
                pstmt.setInt(3, whereId);

                pstmt.addBatch();
                int[] batchResults = pstmt.executeBatch();
                conn.commit();

                int rowsAffected = Arrays.stream(batchResults).sum();

                if (rowsAffected > 0) {

                    logDB.info(String.format(
                            "Instruction status updated. Rows affected: %d (Table: %s, BlockId: %d, WhereId: %d)",
                            rowsAffected, tableName, blockId, whereId));
                } else {

                    logDB.warn(String.format(
                            "No instruction statuses were updated (Table: %s, BlockId: %d, WhereId: %d)",
                            tableName, blockId, whereId));
                }

            } catch (SQLException e) {
                conn.rollback(); // Roll back in case of error

                logDB.error(String.format(
                        "Error updating instruction status in table '%s'. Error: %s", tableName, e.getMessage()));
                return new ErrorMessage(
                        "Update Instruction Status Error", "Failed to update instruction status", e.getMessage());
            }

        } catch (SQLException ex) {

            logDB.error(String.format(
                    "Connection error while updating instruction status in table '%s'. Error: %s",
                    tableName, ex.getMessage()));
            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }

        return null; // Success
    }

    public ErrorMessage loadBlocks(
            Integer whereId, // botJobId or homeBankingId depending on tableName
            String botJobName,
            String tableName // "block" or "component_block"
            ) {
        // Validate tableName
        if (!"block".equals(tableName) && !"component_block".equals(tableName)) {
            return new ErrorMessage(
                    "Invalid Table Name", "tableName must be 'block' or 'component_block'", "Provided: " + tableName);
        }

        // Build SQL dynamically based on tableName
        StringBuilder query = new StringBuilder("SELECT ");
        if ("block".equals(tableName)) {
            query.append("b.id AS block_id, ")
                    .append("b.block_order_number, ")
                    .append("b.name AS block_name, ")
                    .append("b.description, ")
                    .append("b.type_id, ")
                    .append("b.export_file, ")
                    .append("b.wait, ")
                    .append("b.active, ")
                    .append("b.bot_job_id ")
                    //                    .append("bot.name AS bot_job_name ")
                    //                    append("FROM bot_job bot ")
                    .append("FROM block b ")
                    .append("WHERE b.bot_job_id = ? ")
                    .append("ORDER BY b.block_order_number ASC");
        } else { // component_block
            query.append("b.id AS block_id, ")
                    .append("b.block_order_number, ")
                    .append("b.name AS block_name, ")
                    .append("b.description, ")
                    .append("b.type_id, ")
                    .append("b.export_file, ")
                    .append("b.wait, ")
                    .append("b.active, ")
                    .append("b.home_banking_id ")
                    //                    .append("FROM home_banking hb ")
                    .append("FROM component_block b ")
                    //                    .append("JOIN component_instruction ci ON ci.home_banking_id = hb.id AND
                    // ci.block_id = b.id ")
                    .append("WHERE b.home_banking_id = ? ")
                    .append("ORDER BY b.block_order_number ASC");
        }

        // Choose target list depending on tableName
        List<BlockLoadDTO> targetList;
        if ("block".equals(tableName)) {
            performLists.getListBlock().clear();
            targetList = performLists.getListBlock();
        } else {
            performLists.getListBlockComp().clear();
            targetList = performLists.getListBlockComp();
        }

        Map<Integer, BlockLoadDTO> blockMapDTO = new HashMap<>();

        try (Connection connection = getConnection();
                PreparedStatement pstmt = connection.prepareStatement(query.toString())) {
            pstmt.setInt(1, whereId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int blockId = rs.getInt("block_id");
                    BlockLoadDTO blockDTO = blockMapDTO.get(blockId);

                    if (blockDTO == null) {
                        blockDTO = new BlockLoadDTO();
                        blockDTO.setId(blockId);
                        blockDTO.setBlockOrderNumber(rs.getInt("block_order_number"));
                        blockDTO.setName(rs.getString("block_name"));
                        blockDTO.setDescription(rs.getString("description"));
                        blockDTO.setTypeId(rs.getInt("type_id"));
                        blockDTO.setExportFile(rs.getString("export_file"));
                        blockDTO.setWait(rs.getInt("wait"));
                        blockDTO.setActive(rs.getBoolean("active"));

                        if ("block".equals(tableName)) {
                            blockDTO.setBotJobId(rs.getInt("bot_job_id"));
                            blockDTO.setBotJobName(botJobName);
                        } else {
                            blockDTO.setHomeBankingId(rs.getInt("home_banking_id"));
                            blockDTO.setBotJobId(whereId != null ? whereId : 0);
                            blockDTO.setBotJobName(botJobName);
                        }

                        blockMapDTO.put(blockId, blockDTO);
                        targetList.add(blockDTO);
                    }
                }
            }
        } catch (SQLException error) {

            logDB.error(String.format(
                    "Error loading blocks for %s id %d\nError: %s", tableName, whereId, error.getMessage()));

            return new ErrorMessage(
                    "Load Blocks Error", "Failed to load blocks from table: " + tableName, error.getMessage());
        }
        return null;
    }

    public synchronized ErrorMessage insertInstructionsBatch(
            String typeTask,
            List<InstructionLoad> instructions,
            Integer currentBotJobId,
            Integer currentBlockId,
            Integer homeBankingId) {

        String tableName = "instruction";
        if (isComponentTask(typeTask)) {
            tableName = "component_instruction";
        }

        final int BATCH_SIZE = 100;
        int count = 0;

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: Get all IDs before insertion
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery("SELECT id FROM " + tableName + " ORDER BY id")) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            // Step 2: Perform batch insert
            for (InstructionLoad instructionLoad : instructions) {
                StringBuilder columns = new StringBuilder();
                StringBuilder values = new StringBuilder();

                BiConsumer<String, Object> addColumnValue = (column, value) -> {
                    if (value != null) {
                        if (columns.length() > 0) {
                            columns.append(", ");
                            values.append(", ");
                        }
                        columns.append(column);
                        if (value instanceof String) {
                            values.append("'")
                                    .append(((String) value).replace("'", "''"))
                                    .append("'");
                        } else {
                            values.append(value);
                        }
                    }
                };

                // Add non-boolean fields
                addColumnValue.accept("coordinates", instructionLoad.getCoordinates());
                addColumnValue.accept("iframe_xpath", instructionLoad.getIFrameXPath());
                addColumnValue.accept("tag_name", instructionLoad.getTagName());
                addColumnValue.accept("shadow_host", instructionLoad.getShadowHost());
                addColumnValue.accept("shadow_root", instructionLoad.getShadowRoot());
                addColumnValue.accept("css_selector", instructionLoad.getCssSelector());
                addColumnValue.accept("xpath", instructionLoad.getXpath());
                addColumnValue.accept("action_custom_max_wait_sec", instructionLoad.getActionCustomMaxWaitSec());
                addColumnValue.accept("actions", instructionLoad.getActions());
                addColumnValue.accept("default_value", instructionLoad.getDefaultValue());
                addColumnValue.accept("description", instructionLoad.getDescription());
                addColumnValue.accept("instruction_order_number", instructionLoad.getInstructionOrderNumber());
                addColumnValue.accept("name", instructionLoad.getName());
                // Roadmap 3 Phase 3d: addColumnValue is null-skipping by design, so when the
                // user hasn't set a custom label the column is simply absent from the INSERT
                // (default NULL). ROW_UPDATE explicitly sets it (or null-clears it) afterwards.
                addColumnValue.accept("client_named", instructionLoad.getClientNamed());
                addColumnValue.accept(
                        "on_hold_seconds",
                        instructionLoad.getOnHoldSeconds() != null ? instructionLoad.getOnHoldSeconds() : 1);
                addColumnValue.accept("operation", instructionLoad.getOperation());
                addColumnValue.accept("parent_block_id", instructionLoad.getParentBlockId());
                addColumnValue.accept("parent_id", instructionLoad.getParentId());
                addColumnValue.accept("variable_id", instructionLoad.getVariableId());
                addColumnValue.accept("block_id", currentBlockId);

                if (isComponentTask(typeTask)) {
                    addColumnValue.accept("home_banking_id", homeBankingId);
                } else {
                    addColumnValue.accept("bot_job_id", currentBotJobId);
                }

                // Boolean fields
                addColumnValue.accept(
                        "block_marked",
                        instructionLoad.getBlockMarked() != null ? (instructionLoad.getBlockMarked() ? 1 : 0) : null);
                addColumnValue.accept(
                        "codified",
                        instructionLoad.getCodified() != null ? (instructionLoad.getCodified() ? 1 : 0) : null);
                addColumnValue.accept(
                        "export_to_abr",
                        instructionLoad.getExportToABR() != null ? (instructionLoad.getExportToABR() ? 1 : 0) : null);
                addColumnValue.accept(
                        "optional",
                        instructionLoad.getOptional() != null ? (instructionLoad.getOptional() ? 1 : 0) : null);
                addColumnValue.accept(
                        "active",
                        instructionLoad.getInstructionActive() != null
                                ? (instructionLoad.getInstructionActive() ? 1 : 0)
                                : null);
                addColumnValue.accept(
                        "force_coordinates",
                        instructionLoad.getForceCoordinates() != null ? instructionLoad.getForceCoordinates() : "");

                // Final insert
                String insertSQL = String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, values);
                stmt.addBatch(insertSQL);

                if (++count % BATCH_SIZE == 0) {
                    stmt.executeBatch();
                    stmt.clearBatch();
                }
            }

            // Final batch
            if (count % BATCH_SIZE != 0) {
                stmt.executeBatch();
                stmt.clearBatch();
            }

            // Step 3: Get all IDs after insertion
            idsInstrucAfter.clear();
            try (ResultSet rsAfter = idStmtAfter.executeQuery("SELECT id FROM " + tableName + " ORDER BY id")) {
                while (rsAfter.next()) {
                    idsInstrucAfter.add(rsAfter.getInt("id"));
                }
            }

            idsInstrucAfter.removeAll(idsBefore);

            logDB.info(String.format(
                    "Batch insert completed for %d %s records. New IDs: %s",
                    count, tableName.toUpperCase(), idsInstrucAfter));

            conn.commit();

            return null;

        } catch (SQLException error) {

            logDB.error("Batch insert error for " + tableName + ": " + error.getMessage());
            return new ErrorMessage(
                    "Instruction Insertion Error", "Error inserting batch instructions.", error.getMessage());
        }
    }

    public ErrorMessage updateInstructionsBatchByNameAndBlockId(
            String typeTask,
            List<InstructionLoad> instructions,
            Integer currentBotJobId,
            Integer currentBlockId,
            Integer homeBankingId) {

        String tableName = "instruction";
        if (isComponentTask(typeTask)) {
            tableName = "component_instruction";
        }

        int count = 0;

        // collect updated IDs (reuse your existing list name if you want)
        idsInstrucAfter.clear();

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            boolean isComponent = isComponentTask(typeTask);

            // Prepared once (faster + cleaner)
            StringBuilder countSql = new StringBuilder(
                    "SELECT COUNT(*) FROM " + tableName + " WHERE name = ? AND tag_name = ? AND block_id = ?");
            StringBuilder idSql = new StringBuilder(
                    "SELECT id FROM " + tableName + " WHERE name = ? AND tag_name = ? AND block_id = ?");

            if (isComponent) {
                countSql.append(" AND home_banking_id = ?");
                idSql.append(" AND home_banking_id = ?");
            } else {
                countSql.append(" AND bot_job_id = ?");
                idSql.append(" AND bot_job_id = ?");
            }

            try (PreparedStatement psCount = conn.prepareStatement(countSql.toString());
                    PreparedStatement psId = conn.prepareStatement(idSql.toString())) {

                for (InstructionLoad instructionLoad : instructions) {

                    String filterName = instructionLoad.getName();
                    String filterTagName = instructionLoad.getTagName();

                    if (filterName == null || filterTagName == null || currentBlockId == null) {
                        continue;
                    }

                    if (isComponent && homeBankingId == null) continue;
                    if (!isComponent && currentBotJobId == null) continue;

                    Object ownerValue = isComponent ? homeBankingId : currentBotJobId;

                    // -----------------------------
                    // 1) PRE-SELECT COUNT(*)
                    // -----------------------------
                    psCount.setString(1, filterName);
                    psCount.setString(2, filterTagName);
                    psCount.setInt(3, currentBlockId);
                    psCount.setObject(4, ownerValue);

                    int matchCount;
                    try (ResultSet rs = psCount.executeQuery()) {
                        rs.next();
                        matchCount = rs.getInt(1);
                    }

                    // Duplicate → ERROR
                    if (matchCount > 1) {
                        String errMsg = String.format("%s: %s", filterTagName, filterName);
                        logDB.error("Batch update error for " + tableName + ": " + errMsg);
                        return new ErrorMessage("Instruction Update Error", "Duplicate in block.", errMsg);
                    }

                    // No match → SKIP
                    if (matchCount == 0) {
                        continue;
                    }

                    // -----------------------------
                    // 2) Get ID (since matchCount == 1)
                    // -----------------------------
                    Integer instructionId = null;

                    psId.setString(1, filterName);
                    psId.setString(2, filterTagName);
                    psId.setInt(3, currentBlockId);
                    psId.setObject(4, ownerValue);

                    try (ResultSet rsId = psId.executeQuery()) {
                        if (rsId.next()) {
                            instructionId = rsId.getInt("id");
                        }
                    }

                    // Safety: if somehow no id returned, skip (or treat as error if you prefer)
                    if (instructionId == null) {
                        continue;
                    }

                    // ✅ attach DB id to the instruction object
                    instructionLoad.setId(instructionId);

                    // -----------------------------
                    // 3) UPDATE ONLY coordinates, xpath
                    // -----------------------------
                    Object coordinates = instructionLoad.getCoordinates();
                    Object xpath = instructionLoad.getXpath();

                    if (coordinates == null && xpath == null) {
                        continue; // nothing updated, so don't add id
                    }

                    StringBuilder sql =
                            new StringBuilder("UPDATE ").append(tableName).append(" SET ");
                    List<Object> params = new ArrayList<>();

                    if (coordinates != null) {
                        sql.append("coordinates = ?");
                        params.add(coordinates);
                    }

                    if (xpath != null) {
                        if (!params.isEmpty()) sql.append(", ");
                        sql.append("xpath = ?");
                        params.add(xpath);
                    }

                    sql.append(" WHERE id = ?");
                    params.add(instructionId);

                    try (PreparedStatement psUpdate = conn.prepareStatement(sql.toString())) {
                        for (int i = 0; i < params.size(); i++) {
                            psUpdate.setObject(i + 1, params.get(i));
                        }
                        psUpdate.executeUpdate();
                        count++;
                        idsInstrucAfter.add(instructionId); // collect updated id
                    }
                }
            }

            conn.commit();

            logDB.info(String.format(
                    "Batch update completed for %d %s records. Updated IDs: %s",
                    count, tableName.toUpperCase(), idsInstrucAfter));

            return null;

        } catch (SQLException error) {
            logDB.error("Batch update error for " + tableName + ": " + error.getMessage());
            return new ErrorMessage(
                    "Instruction Update Error", "Error updating batch instructions.", error.getMessage());
        }
    }

    public ErrorMessage insertInstruction(
            String typeTask,
            List<InstructionOperationDTO> instructions,
            Integer currentBotJobId,
            Integer currentBlockId,
            Integer homeBankingId) {

        String tableName = "instruction";
        if (isComponentTask(typeTask)) {
            tableName = "component_instruction";
        }

        final int BATCH_SIZE = 100;
        int count = 0;

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            // Step 1: Get all IDs before insertion
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery("SELECT id FROM " + tableName + " ORDER BY id")) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            // Step 2: Build and add batch statements
            for (InstructionOperationDTO instructionLoad : instructions) {
                StringBuilder columns = new StringBuilder();
                StringBuilder values = new StringBuilder();

                BiConsumer<String, Object> addColumnValue = (column, value) -> {
                    if (value != null) {
                        if (columns.length() > 0) {
                            columns.append(", ");
                            values.append(", ");
                        }
                        columns.append(column);
                        if (value instanceof String) {
                            values.append("'")
                                    .append(((String) value).replace("'", "''"))
                                    .append("'");
                        } else {
                            values.append(value);
                        }
                    }
                };

                // Non-boolean fields
                addColumnValue.accept("action_custom_max_wait_sec", instructionLoad.getActionCustomMaxWaitSec());
                addColumnValue.accept("actions", instructionLoad.getActions());
                addColumnValue.accept("description", instructionLoad.getDescription());
                addColumnValue.accept("instruction_order_number", instructionLoad.getInstructionOrderNumber());
                addColumnValue.accept("name", instructionLoad.getName());
                // Note: insertInstruction operates on InstructionOperationDTO (programmatic
                // operation inserts: IF / LOOP / parent ops), not on user-picker payloads.
                // client_named is only relevant on the picker INSERT path (insertInstructionsBatch
                // above) so it's intentionally absent here — operations are not user-renameable.
                addColumnValue.accept(
                        "on_hold_seconds",
                        instructionLoad.getOnHoldSeconds() != null ? instructionLoad.getOnHoldSeconds() : 1);
                addColumnValue.accept("operation", instructionLoad.getOperation());
                addColumnValue.accept("parent_block_id", instructionLoad.getParentBlockId());
                addColumnValue.accept("parent_id", instructionLoad.getParentId());
                addColumnValue.accept("variable_id", instructionLoad.getVariableId());
                addColumnValue.accept("block_id", currentBlockId);

                if (isComponentTask(typeTask)) {
                    addColumnValue.accept("home_banking_id", homeBankingId);
                } else {
                    addColumnValue.accept("bot_job_id", currentBotJobId);
                }

                // Boolean fields
                addColumnValue.accept(
                        "active",
                        instructionLoad.getInstructionActive() != null
                                ? (instructionLoad.getInstructionActive() ? 1 : 0)
                                : null);
                addColumnValue.accept(
                        "force_coordinates",
                        instructionLoad.getForceCoordinates() != null ? instructionLoad.getForceCoordinates() : "");
                // Final SQL
                String insertSQL = String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, values);
                stmt.addBatch(insertSQL);

                if (++count % BATCH_SIZE == 0) {
                    stmt.executeBatch();
                    stmt.clearBatch();
                }
            }

            // Final batch if not aligned with BATCH_SIZE
            if (count % BATCH_SIZE != 0) {
                stmt.executeBatch();
                stmt.clearBatch();
            }

            // Step 3: Get IDs after insertion
            idsInstrucAfter.clear();
            try (ResultSet rsAfter = idStmtAfter.executeQuery("SELECT id FROM " + tableName + " ORDER BY id")) {
                while (rsAfter.next()) {
                    idsInstrucAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 4: Compute new inserted IDs
            idsInstrucAfter.removeAll(idsBefore);

            logDB.info(String.format(
                    "Batch insert completed for %d %s records. New IDs: %s",
                    count, tableName.toUpperCase(), idsInstrucAfter));

            return null;

        } catch (SQLException error) {

            logDB.error("Batch insert error for " + tableName + ": " + error.getMessage());
            return new ErrorMessage("Instruction Insert Error", "Failed during batch insert", error.getMessage());
        }
    }

    public ErrorMessage updateInstructionParentIdOnly(String typeTask, List<InstructionOperationDTO> operations) {

        String tableName = "instruction";
        if (isComponentTask(typeTask)) {
            tableName = "component_instruction";
        }

        StringBuilder batchSQL = new StringBuilder();

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            for (InstructionOperationDTO operation : operations) {
                if (operation.getId() == null || operation.getParentId() == null) {

                    logDB.warn("Skipping: Instruction ID or Parent ID is null.");
                    continue;
                }

                String updateSQL = String.format(
                        "UPDATE %s SET parent_id = %d WHERE id = %d;",
                        tableName, operation.getParentId(), operation.getId());

                batchSQL.append(updateSQL).append("\n");
                stmt.addBatch(updateSQL);
            }

            stmt.executeBatch();
            logDB.info("Parent ID update batch executed:\n" + batchSQL);

            return null;

        } catch (SQLException e) {
            logDB.error("Batch parent_id update failed: " + e.getMessage());

            return new ErrorMessage("Update Error", "Failed to update parent_id in instructions.", e.getMessage());
        }
    }

    public ErrorMessage updateInstruction(
            String typeTask,
            InstructionOperationDTO InstructionOperation,
            Integer currentBotJobId,
            Integer currentBlockId,
            Integer homeBankingId) {

        String tableName = "instruction";
        if (isComponentTask(typeTask)) {
            tableName = "component_instruction";
        }

        try (Statement stmt = getConnection().createStatement()) {
            if (InstructionOperation.getId() == null) {
                logDB.warn("Instruction ID is null. Update failed.");
                return new ErrorMessage(
                        "Error Update Instruction",
                        "Error during updating instruction",
                        "Instruction ID is null. Update failed.");
            }

            StringBuilder setClause = new StringBuilder();

            // Helper method to add column assignments
            BiConsumer<String, Object> addColumnValue = (column, value) -> {
                if (value != null) {
                    if (setClause.length() > 0) {
                        setClause.append(", ");
                    }
                    if (value instanceof String) {
                        setClause
                                .append(column)
                                .append(" = '")
                                .append(((String) value).replace("'", "''"))
                                .append("'");
                    } else {
                        setClause.append(column).append(" = ").append(value);
                    }
                }
            };

            // Add fields to update
            addColumnValue.accept("action_custom_max_wait_sec", InstructionOperation.getActionCustomMaxWaitSec());
            addColumnValue.accept("actions", InstructionOperation.getActions());
            addColumnValue.accept("description", InstructionOperation.getDescription());
            addColumnValue.accept("instruction_order_number", InstructionOperation.getInstructionOrderNumber());
            addColumnValue.accept("name", InstructionOperation.getName());
            addColumnValue.accept("on_hold_seconds", InstructionOperation.getOnHoldSeconds());
            addColumnValue.accept("operation", InstructionOperation.getOperation());
            addColumnValue.accept("parent_block_id", InstructionOperation.getParentBlockId());
            addColumnValue.accept("parent_id", InstructionOperation.getParentId());
            addColumnValue.accept("variable_id", InstructionOperation.getVariableId());
            addColumnValue.accept("block_id", currentBlockId);

            if (isComponentTask(typeTask)) {
                addColumnValue.accept("home_banking_id", homeBankingId);
            } else {
                addColumnValue.accept("bot_job_id", currentBotJobId);
            }

            addColumnValue.accept(
                    "active",
                    InstructionOperation.getInstructionActive() != null
                            ? (InstructionOperation.getInstructionActive() ? 1 : 0)
                            : null);
            addColumnValue.accept(
                    "force_coordinates",
                    InstructionOperation.getForceCoordinates() != null
                            ? InstructionOperation.getForceCoordinates()
                            : "");

            if (setClause.isEmpty()) {

                logDB.warn("No fields to update for instruction ID: " + InstructionOperation.getId());
                return new ErrorMessage(
                        "Error Update Instruction",
                        "Error during updating instruction",
                        "No fields to update for instruction ID: " + InstructionOperation.getId());
            }

            // Construct final SQL query
            String updateSQL =
                    String.format("UPDATE %s SET %s WHERE id = %d", tableName, setClause, InstructionOperation.getId());

            int rowsAffected = stmt.executeUpdate(updateSQL);
            if (rowsAffected > 0) {

                logDB.info(String.format(
                        "%s UPDATED SUCCESSFULLY id: %d Name: %s Actions: %s Operation: %s",
                        tableName.toUpperCase(),
                        InstructionOperation.getId(),
                        InstructionOperation.getName(),
                        InstructionOperation.getActions(),
                        InstructionOperation.getOperation()));
            } else {

                logDB.warn(String.format(
                        "%s NOT UPDATED id: %d Name: %s Actions: %s Operations: %s",
                        tableName.toUpperCase(),
                        InstructionOperation.getId(),
                        InstructionOperation.getName(),
                        InstructionOperation.getActions(),
                        InstructionOperation.getOperation()));
            }
            return null;
        } catch (SQLException error) {
            logDB.warn(
                    "{} Update Failed id: {} Name: {} Actions: {} Operations: {}",
                    tableName.toUpperCase(),
                    InstructionOperation.getId(),
                    InstructionOperation.getName(),
                    InstructionOperation.getActions(),
                    InstructionOperation.getOperation());
            return new ErrorMessage(
                    "Error Update Instruction", "Error during updating instruction", error.getMessage());
        }
    }

    public boolean instructionIdExists(int instructionId) {
        String query = "SELECT COUNT(*) FROM instruction WHERE id = ?";
        try (Connection connection = getConnection(); // Assuming getConnection() provides a valid Connection
                PreparedStatement pstmt = connection.prepareStatement(query)) {

            pstmt.setInt(1, instructionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0; // If count is greater than 0, the ID exists
                }
            }
        } catch (SQLException error) {

            logDB.error(String.format(
                    "Error checking for instruction ID %d. Error: %s", instructionId, error.getMessage()));
        }
        return false; // Return false if an error occurs or the ID is not found
    }

    public List<InstructionLoad> preInsertStep(
            String operType, int targetOrderNumber, List<InstructionLoad> rowList, int shiftQty) {

        if ("INSERT_BEFORE".equals(operType)
                || "INSERT_AFTER".equals(operType)
                || "INSERT_AFTER_ELSEIF".equals(operType)) {

            boolean orderNumberExists = rowList.stream()
                    .anyMatch(instruction -> instruction.getInstructionOrderNumber() == targetOrderNumber);

            if (!orderNumberExists) {

                logDB.warn(String.format(
                        "preInsertStep - Target order number %d does not exist in the row list.", targetOrderNumber));
            }

            for (InstructionLoad instruction : rowList) {
                boolean shouldShift = "INSERT_BEFORE".equals(operType)
                        ? instruction.getInstructionOrderNumber() >= targetOrderNumber
                        : instruction.getInstructionOrderNumber() > targetOrderNumber;

                if (shouldShift) {
                    instruction.setInstructionOrderNumber(instruction.getInstructionOrderNumber() + shiftQty);
                }
            }
        }
        return rowList;
    }

    public ErrorMessage preFillNewInstruction(
            String name,
            String description,
            String actions,
            String operation,
            Integer onHold,
            SplitDTO splitDTO,
            boolean blockIdChanged) {

        //        this.botJobLoadDTO = loadBotJobById(splitDTO.getBotJobId());

        boolean updateRow = splitDTO.getType().equals("EDIT_OPERATION");
        boolean isIF = actions.equalsIgnoreCase(ARConstants.IF);
        boolean isELSEIF = actions.equalsIgnoreCase(ARConstants.ELSEIF);

        if (performLists.getQuickBotJobs().isEmpty()) {
            loadQuickBotJobs();
        }

        List<InstructionLoad> rowList = null;
        String instrName = "instruction";
        String blockTable = "block";
        int whereId = splitDTO.getBotJobId();
        if (isComponentTask(splitDTO.getSessionId())) {
            instrName = "component_instruction";
            blockTable = "component_block";
            whereId = splitDTO.getHomeBankingId();
        }

        ErrorMessage errorMessage = loadInstructions(whereId, splitDTO.getBlockId(), -1, instrName);

        if (errorMessage == null) {
            errorMessage = loadBlocks(whereId, "", blockTable);
        }

        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        BlockLoadDTO blockLoadFound = performLists.getBlockLoadByBankId(blockTable, whereId, splitDTO.getBlockId());

        List<BotJobLoadDTO> listBotJob =
                blockTable.equals("block") ? performLists.getListBotJob() : performLists.getListBotJobComp();

        List<BlockLoadDTO> listBlocks =
                blockTable.equals("block") ? performLists.getListBlock() : performLists.getListBlockComp();

        if (!listBotJob.isEmpty() && listBlocks.isEmpty()) {
            errorMessage = loadBlocks(whereId, splitDTO.getBotJobName(), blockTable);
        }

        if (errorMessage == null) {
            errorMessage = checkGapsBlockOrder(listBlocks, blockTable, whereId, splitDTO.getBotJobName());
        }

        rowList = instrName.equals("instruction")
                ? performLists.getListInstruction()
                : performLists.getListInstructionComp();

        Set<String> excluded = Set.of("GOTO", "EXCEL GOTO", "LOOP", "REFRESH_LOOP");
        boolean orderToFinal = excluded.contains(actions);
        String operType = splitDTO.getType();
        int targetOrderNumber = splitDTO.getInstructionOrderNumber();

        if (errorMessage == null) {
            if (!orderToFinal && !blockIdChanged) {
                if (isIF) {
                    rowList = preInsertStep(operType, targetOrderNumber, rowList, 3);
                } else {
                    rowList = preInsertStep(operType, targetOrderNumber, rowList, 1);
                }
                reorderInstructionsPerBlock(rowList, instrName, false);

            } else {
                splitDTO.setInstructionOrderNumber(rowList.size() + 1);
            }
        }

        InstructionOperationDTO instruction = new InstructionOperationDTO();
        // EDIT_OPERATION
        if (updateRow) {
            int idToUpdate = splitDTO.getInstructionId();
            instruction.setId(idToUpdate);
        }
        instruction.setName(name);
        instruction.setInstructionActive(true);

        if (splitDTO != null) {
            if ("INSERT_BEFORE".equals(splitDTO.getType()) || "EDIT_OPERATION".equals(splitDTO.getType())) {
                instruction.setInstructionOrderNumber(splitDTO.getInstructionOrderNumber());
            } else {
                instruction.setInstructionOrderNumber(splitDTO.getInstructionOrderNumber() + 1);
            }
        } else {
            instruction.setInstructionOrderNumber(
                    performLists.getListInstruction().size() + 1);
        }

        // PARENT BLOCK ID
        if (splitDTO.getParentBlockId() != null
                && (actions.equalsIgnoreCase("GOTO") || actions.equalsIgnoreCase("EXCEL GOTO"))) {
            instruction.setParentBlockId(splitDTO.getParentBlockId());
        }

        // PARENT ID
        if (splitDTO.getParentId() != null
                && (actions.equalsIgnoreCase(ARConstants.GET_VALUE)
                        || actions.equalsIgnoreCase(ARConstants.SET_VALUE)
                        || actions.equalsIgnoreCase(ARConstants.CHECK_VALUE)
                        || actions.equalsIgnoreCase(ARConstants.PDF_CHECK)
                        || actions.equalsIgnoreCase(ARConstants.CSV_CHECK)
                        || actions.equalsIgnoreCase(ARConstants.EXTRACT_FIELD)
                        || actions.equalsIgnoreCase(ARConstants.LOOP)
                        || actions.equalsIgnoreCase(ARConstants.REFRESH_LOOP))) {
            instruction.setParentId(splitDTO.getParentId());
        }

        if (splitDTO.getVariableId() != null) {
            instruction.setVariableId(splitDTO.getVariableId());
        }

        instruction.setOperation(operation);
        instruction.setActions(actions);
        instruction.setDescription(description);

        instruction.setActionCustomMaxWaitSec(30);
        instruction.setOnHoldSeconds(onHold);

        if (blockLoadFound != null) {
            instruction.setBlockId(blockLoadFound.getId());
        } else {

            errorMessage =
                    initiateNewBlock("block", splitDTO.getBotJobId(), "Default Block", "Default Block", 1, false);

            if (errorMessage == null) {
                int newBlockId = -9999;
                if (!getIdsBlockAfter().isEmpty() && getIdsBlockAfter().get(0) > 0) {
                    newBlockId = getIdsBlockAfter().get(0);
                }

                // IT SETS THE NEW TARGET IN CASE TO ADD MORE INSTRUCTIONS
                splitDTO.setBlockId(newBlockId);

                instruction.setBlockId(newBlockId);
            } else {
                return errorMessage;
            }
        }
        //        }
        instruction.setInstructionActive(true);
        // Wrap the persistence in a try-catch block
        errorMessage = null;

        try {
            targetOrderNumber = splitDTO.getInstructionOrderNumber();

            Integer currentBlockId = splitDTO.getBlockId();

            if (instruction.getBlockId() != null && !instruction.getBlockId().equals(currentBlockId)) {
                currentBlockId = instruction.getBlockId();
            }
            if (!updateRow) {

                if (isELSEIF) {
                    instruction.setParentId(splitDTO.getParentId());
                }

                performLists.getInstrucOperList().clear();
                performLists.getInstrucOperList().add(instruction);

                if (isIF) {
                    // Create ELSE
                    int orderNumber = instruction.getInstructionOrderNumber();
                    orderNumber++;
                    InstructionOperationDTO elseInstr = buildFromLoadDTO(instruction);
                    elseInstr.setName("ELSE");
                    elseInstr.setDescription("ELSE");
                    elseInstr.setActions(ARConstants.ELSE);
                    elseInstr.setOperation(ARConstants.ELSE);
                    elseInstr.setInstructionOrderNumber(orderNumber);
                    performLists.getInstrucOperList().add(elseInstr);

                    // Create ENDIF
                    orderNumber++;
                    InstructionOperationDTO endifInstr = buildFromLoadDTO(instruction);
                    endifInstr.setName("ENDIF");
                    endifInstr.setDescription("ENDIF");
                    endifInstr.setActions(ARConstants.ENDIF);
                    endifInstr.setOperation(ARConstants.ENDIF);
                    endifInstr.setInstructionOrderNumber(orderNumber);
                    performLists.getInstrucOperList().add(endifInstr);

                    // Last OrderNumber added
                    targetOrderNumber = orderNumber;
                }

                errorMessage = insertInstruction(
                        splitDTO.getSessionId(),
                        performLists.getInstrucOperList(),
                        splitDTO.getBotJobId(),
                        currentBlockId,
                        splitDTO.getHomeBankingId());

                if (isIF && errorMessage == null && idsInstrucAfter.size() == 3) {
                    int index = 0;
                    for (InstructionOperationDTO instruct : performLists.getInstrucOperList()) {
                        instruct.setId(idsInstrucAfter.get(index));
                        instruct.setParentId(idsInstrucAfter.get(0)); // PARENT ID FOR ALL
                        index++; // To get the NEWS Ids
                    }
                    errorMessage =
                            updateInstructionParentIdOnly(splitDTO.getSessionId(), performLists.getInstrucOperList());
                }
            } else {
                errorMessage = updateInstruction(
                        splitDTO.getSessionId(),
                        instruction,
                        splitDTO.getBotJobId(),
                        currentBlockId,
                        splitDTO.getHomeBankingId());
            }

            if (!updateRow || blockIdChanged) {
                splitDTO.setInstructionOrderNumber(targetOrderNumber);
            }

            if (errorMessage == null) {

                errorMessage = loadInstructions(whereId, splitDTO.getBlockId(), -1, instrName);

                if (!updateRow) {
                    rowList = instrName.equals("instruction")
                            ? performLists.getListInstruction()
                            : performLists.getListInstructionComp();

                    reorderInstructionsPerBlock(rowList, instrName, true);
                }

                logDB.info(String.format(
                        "\"Component\" Instruction: \"%s\" has been added successfully!", instruction.getName()));
            } else {

                logDB.error(String.format(
                        "Error Add New \"Component\" Instruction: \"%s\" Cannot be saved!", instruction.getName()));

                performMessage.errorMessageOperationFailed(errorMessage);
            }

        } catch (Exception e) {
            logDB.error("Cannot Insert Instruction\nError: " + e.getMessage());
            errorMessage = new ErrorMessage(
                    "Cannot Insert Instruction",
                    "The command could not be saved.",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            performLists.getInstrucOperList().clear();
        }
        return errorMessage;
    }

    public ErrorMessage initiateNewBlock(
            String tableName, int whereId, String blockName, String description, int blockOrder, boolean split) {
        // It Prevents Start without blocks
        BlockDetailsDTO newBlockDetails = new BlockDetailsDTO();
        newBlockDetails.setBlockName(blockName);
        newBlockDetails.setBlockDescription(description);
        newBlockDetails.setTypeId(1);
        newBlockDetails.setActive(true);
        newBlockDetails.setWait(3);

        if (split) {
            newBlockDetails.setBlockOrderNumber(blockOrder);
        } else {
            newBlockDetails.setBlockOrderNumber(1);
        }

        if ("block".equals(tableName)) {
            newBlockDetails.setBotJobId(whereId);
        } else {
            newBlockDetails.setHomeBankingId(whereId);
        }

        return insertNewBlock(tableName, whereId, newBlockDetails);
    }

    public ErrorMessage loadWebPageFields(int whereId, String tableName) {
        performLists.getListWebPageItems().clear();

        String sql;
        boolean useComponent = "home_banking".equalsIgnoreCase(tableName);

        if (useComponent) {
            sql = "SELECT hb.id AS home_banking_id, " + "b.id AS block_id, bli.id AS instruction_id, "
                    + "bli.instruction_order_number, bli.actions, bli.name AS instruction_name, "
                    + "bli.xpath, bli.operation, bli.tag_name "
                    + "FROM home_banking hb "
                    + "LEFT JOIN component_block b ON b.home_banking_id = hb.id "
                    + "JOIN component_instruction bli ON bli.block_id = b.id "
                    + "WHERE hb.id = ? AND operation IS NULL "
                    + "ORDER BY hb.id, b.block_order_number, bli.instruction_order_number ASC";
        } else {
            sql = "SELECT bot.id AS bot_job_id, " + "b.id AS block_id, bli.id AS instruction_id, "
                    + "bli.instruction_order_number, bli.actions, bli.name AS instruction_name, "
                    + "bli.xpath, bli.operation, bli.tag_name "
                    + "FROM bot_job bot "
                    + "LEFT JOIN block b ON b.bot_job_id = bot.id "
                    + "JOIN instruction bli ON bli.block_id = b.id "
                    + "WHERE bot.active = 1 AND bot.id = ? AND operation IS NULL "
                    + "ORDER BY bot.id, b.block_order_number, bli.instruction_order_number ASC";
        }

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, whereId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("instruction_id");
                    String name = rs.getString("instruction_name");
                    String actions = rs.getString("actions");

                    if (name != null) name = name.trim();
                    if (actions != null) actions = actions.trim();

                    // Filter out unwanted actions
                    if (actions == null
                            || actions.equalsIgnoreCase(ARConstants.SET_VALUE)
                            || actions.equalsIgnoreCase(ARConstants.GET_VALUE)
                            || actions.equalsIgnoreCase(ARConstants.CHECK_VALUE)
                            || actions.equalsIgnoreCase(ARConstants.PDF_CHECK)
                            || actions.equalsIgnoreCase(ARConstants.CSV_CHECK)
                            || actions.equalsIgnoreCase(ARConstants.HOLD)
                            || actions.equalsIgnoreCase(ARConstants.PAUSE)
                            || actions.equalsIgnoreCase(ARConstants.EXCEL_GOTO)
                            || actions.equalsIgnoreCase(ARConstants.SCREEN)
                            || actions.equalsIgnoreCase(ARConstants.QUIT)) {
                        continue;
                    }

                    int blockId = rs.getInt("block_id");
                    String tagName = rs.getString("tag_name");
                    if (tagName != null) tagName = tagName.trim();

                    int orderNumber = rs.getInt("instruction_order_number");

                    performLists
                            .getListWebPageItems()
                            .add(new ComboBoxVars(
                                    "(" + id + ")" + name, name, id, blockId, -1, -1, tagName, orderNumber, null));
                }
            }
        } catch (SQLException error) {

            logDB.error(String.format("loadWebPageFields - SQL Error: %s", error.getMessage()));
            return new ErrorMessage(
                    "Error loading Web Page Fields", "Error loading Web Page Fields", error.getMessage());

        } catch (Exception error) {

            logDB.error(String.format("loadWebPageFields - General Error: %s", error.getMessage()));
            return new ErrorMessage(
                    "Error loading Web Page Fields", "Error loading Web Page Fields", error.getMessage());
        }
        return null;
    }

    // Migration Scripts
    public int migrationScriptsv2_1f() {
        // Build the SQL update statement
        try (Statement stmt = getConnection().createStatement()) {
            int rowsAffected = 0;

            if (POSTGRES_DB) {

                // Update the bot_job_id in instruction using the bot_job_id from block
                String updateSQL = "UPDATE instruction\n" + "                SET bot_job_id = (\n"
                        + "                        SELECT b.bot_job_id\n"
                        + "                FROM block AS b\n"
                        + "                WHERE b.id = instruction.block_id\n"
                        + ");";

                rowsAffected = stmt.executeUpdate(updateSQL);

                // Update the bot_job_id in reference using the instruction_id from
                // instruction
                updateSQL = "UPDATE reference AS ref "
                        + "SET bot_job_id = (SELECT bli.bot_job_id FROM instruction AS bli WHERE bli.id = ref.instruction_id);";

                rowsAffected += stmt.executeUpdate(updateSQL);

                // Update the bot_job_id in complex_instruction using the instruction_id from
                // instruction
                updateSQL = "UPDATE complex_instruction AS com "
                        + "SET bot_job_id = (SELECT bli.bot_job_id FROM instruction AS bli WHERE bli.id = com.instruction_id);";

                rowsAffected += stmt.executeUpdate(updateSQL);

                // Update All Active on instruction
                updateSQL = "UPDATE instruction " + "SET active = true;";

                rowsAffected += stmt.executeUpdate(updateSQL);

                // Update All Active on block
                updateSQL = "UPDATE block " + "SET active = true;";

                rowsAffected += stmt.executeUpdate(updateSQL);
            } else {
                // Update the bot_job_id in instruction using the bot_job_id from block
                String updateSQL = "UPDATE instruction AS bli "
                        + "SET bli.bot_job_id = (SELECT b.bot_job_id FROM block AS b WHERE b.id = bli.block_id);";

                rowsAffected = stmt.executeUpdate(updateSQL);

                // Update the bot_job_id in reference using the instruction_id from
                // instruction
                updateSQL = "UPDATE reference AS ref "
                        + "SET ref.bot_job_id = (SELECT bli.bot_job_id FROM instruction AS bli WHERE bli.id = ref.instruction_id);";

                rowsAffected += stmt.executeUpdate(updateSQL);

                // Update the bot_job_id in complex_instruction using the instruction_id from
                // instruction
                updateSQL = "UPDATE complex_instruction AS com "
                        + "SET com.bot_job_id = (SELECT bli.bot_job_id FROM instruction AS bli WHERE bli.id = com.instruction_id);";

                rowsAffected += stmt.executeUpdate(updateSQL);

                // Update All Active on instruction
                updateSQL = "UPDATE instruction " + "SET active = true;";

                rowsAffected += stmt.executeUpdate(updateSQL);

                // Update All Active on block
                updateSQL = "UPDATE block " + "SET active = true;";

                rowsAffected += stmt.executeUpdate(updateSQL);
            }

            if (rowsAffected > 0) {

                logDB.warn(String.format("Migration DB Scripts - RowsUpdated - %s", rowsAffected));
            } else {
                logDB.info("Migration DB Scripts - No Rows were updated");
            }
            return rowsAffected;
        } catch (SQLException e) {
            logDB.warn("Migration DB Scripts - Error: " + e.getMessage());
        }
        return -1;
    }

    public List<InstructionLoad> filterInstructions(List<InstructionLoad> instructionList) {
        return instructionList.stream()
                .filter(instruction -> !ARConstants.EXTRACT_FIELD.equals(instruction.getActions())
                        && !ARConstants.SET_VALUE.equals(instruction.getActions())
                        && !ARConstants.GET_VALUE.equals(instruction.getActions())
                        && !ARConstants.CHECK_VALUE.equals(instruction.getActions())
                        && !ARConstants.PDF_CHECK.equals(instruction.getActions())
                        && !ARConstants.CSV_CHECK.equals(instruction.getActions())
                        && !ARConstants.GOTO.equals(instruction.getActions())
                        && !ARConstants.IF.equals(instruction.getActions())
                        && !ARConstants.ELSE.equals(instruction.getActions())
                        && !ARConstants.ENDIF.equals(instruction.getActions()))
                .collect(Collectors.toList());
    }

    public List<BlockLoadDTO> loadSavedBlocksForBotJob(int homeBankingId, Integer botJobId, String botJobName) {
        // SQL query to get the blocks for a specific bot job
        String query = "\n" + "SELECT \n"
                + "  hb.id as home_banking_id,\n"
                + "  hb.name as home_banking_name, \n"
                + "  bc.id AS block_id, \n"
                + "  bc.block_order_number, \n"
                + "  bc.name AS block_name, \n"
                + "  bc.description AS block_description, \n"
                + "  bc.type_id \n"
                //                + "  bot.id AS bot_job_id, \n"
                //                + "  bot.name AS bot_job_name \n"
                + "  FROM \n"
                + "  component_block bc \n"
                //                + "  JOIN bot_job bot on bot.active = 1 and bot.id = bc.bot_job_id \n"
                + "  JOIN home_banking hb ON hb.id = bc.home_banking_id \n"
                + "WHERE \n"
                + "  hb.id = "
                + homeBankingId;

        // Initialize the necessary data structures
        List<BlockLoadDTO> savedlistBlock = new ArrayList<>();

        Map<Integer, BlockLoadDTO> blockMapDTO = new HashMap<>();

        // Use Statement to execute the query
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                // Load the Block information
                int blockId = rs.getInt("block_id");
                BlockLoadDTO blockDTO = blockMapDTO.get(blockId);

                if (blockDTO == null) {
                    blockDTO = new BlockLoadDTO();
                    blockDTO.setHomeBankingName(rs.getString("home_banking_name"));
                    blockDTO.setHomeBankingId(rs.getInt("home_banking_id"));
                    blockDTO.setId(blockId);
                    blockDTO.setBlockOrderNumber(rs.getInt("block_order_number"));
                    blockDTO.setName(rs.getString("block_name"));
                    blockDTO.setDescription(rs.getString("block_description"));
                    blockDTO.setTypeId(rs.getInt("type_id"));
                    blockDTO.setBotJobId(botJobId);
                    blockDTO.setBotJobName(botJobName);

                    blockMapDTO.put(blockId, blockDTO);
                    savedlistBlock.add(blockDTO);
                }
            }
        } catch (SQLException e) {

            logDB.error(String.format(
                    "Error loadSavedBlocksForBotJob for Home Banking Id %d\nError: %s", homeBankingId, e.getMessage()));
        }

        return savedlistBlock;
    }

    public synchronized ErrorMessage insertReferencesBatch(List<InstructionLoad> instructionList) {
        String insertSQL =
                "INSERT INTO reference(reference_type, value, instruction_id, bot_job_id) VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            conn.setAutoCommit(false);

            final int BATCH_SIZE = 100;
            int count = 0;

            for (InstructionLoad instruction : instructionList) {
                Integer instructionId = instruction.getId();
                Integer botJobId = instruction.getBotJobId();

                if (instruction.getReferenceLoadDTOList() == null) continue;

                for (ReferenceLoadDTO reference : instruction.getReferenceLoadDTOList()) {
                    if ("customXPath".equalsIgnoreCase(reference.getReferenceType())) {
                        continue; // Skip this type
                    }

                    pstmt.setString(1, reference.getReferenceType());
                    pstmt.setString(2, reference.getValue());
                    pstmt.setInt(3, instructionId);
                    pstmt.setInt(4, botJobId);

                    pstmt.addBatch();

                    if (++count % BATCH_SIZE == 0) {
                        pstmt.executeBatch();
                        pstmt.clearBatch();
                    }
                }
            }

            if (count % BATCH_SIZE != 0) {
                pstmt.executeBatch();
                pstmt.clearBatch();
            }

            conn.commit();

            logDB.info("Reference batch insert completed successfully.");
            return null; // No error

        } catch (SQLException error) {

            logDB.error("Failed to insert references into database: " + error.getMessage());
            return new ErrorMessage(
                    "Reference Insertion Error", "An error occurred during reference insertion.", error.getMessage());
        } catch (Exception error) {

            logDB.error("Unexpected error inserting references: " + error.getMessage());
            return new ErrorMessage(
                    "Reference Insertion Error",
                    "An unexpected error occurred during reference insertion.",
                    error.getMessage());
        }
    }

    /**
     * Atomically inserts one Page Scanner instruction batch and all of its locator references.
     * Generated keys are read from the same connection; no global before/after ID diff is used.
     */
    public synchronized AtomicInstructionInsertResult insertInstructionsAndReferencesAtomic(
            List<InstructionLoad> instructionList, int botJobId, int blockId) {
        if (instructionList == null || instructionList.isEmpty()) {
            return new AtomicInstructionInsertResult(
                    new ErrorMessage("Instruction Insertion Error", "No instructions to insert.", null),
                    List.of());
        }
        try (Connection conn = getConnection()) {
            List<Integer> insertedIds = insertInstructionsAndReferencesTransaction(
                    conn, instructionList, botJobId, blockId);
            idsInstrucAfter.clear();
            idsInstrucAfter.addAll(insertedIds);
            return new AtomicInstructionInsertResult(null, List.copyOf(insertedIds));
        } catch (SQLException | RuntimeException failure) {
            logDB.error("Atomic Page Scanner insert failed: " + failure.getMessage());
            return new AtomicInstructionInsertResult(
                    new ErrorMessage(
                            "Instruction Insertion Error",
                            "Could not insert Page Scanner instructions and references.",
                            failure.getMessage()),
                    List.of());
        }
    }

    static List<Integer> insertInstructionsAndReferencesTransaction(
            Connection conn,
            List<InstructionLoad> instructionList,
            int botJobId,
            int blockId) throws SQLException {
        String instructionSql = "INSERT INTO instruction ("
                + "coordinates, iframe_xpath, tag_name, shadow_host, shadow_root, css_selector, xpath, "
                + "action_custom_max_wait_sec, actions, default_value, description, instruction_order_number, "
                + "name, client_named, on_hold_seconds, operation, parent_block_id, parent_id, variable_id, "
                + "block_id, bot_job_id, block_marked, codified, export_to_abr, optional, active, "
                + "force_coordinates) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String referenceSql =
                "INSERT INTO reference(reference_type, value, instruction_id, bot_job_id) VALUES (?, ?, ?, ?)";
        conn.setAutoCommit(false);
        try (PreparedStatement instructionStatement =
                        conn.prepareStatement(instructionSql, Statement.RETURN_GENERATED_KEYS);
                PreparedStatement referenceStatement = conn.prepareStatement(referenceSql)) {
            List<Integer> insertedIds = new ArrayList<>(instructionList.size());
            for (InstructionLoad instruction : instructionList) {
                bindAtomicInstruction(instructionStatement, instruction, botJobId, blockId);
                if (instructionStatement.executeUpdate() != 1) {
                    throw new SQLException("Instruction insert did not create exactly one row");
                }
                int instructionId;
                try (ResultSet keys = instructionStatement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Instruction insert returned no generated id");
                    instructionId = keys.getInt(1);
                }
                instruction.setId(instructionId);
                insertedIds.add(instructionId);

                if (instruction.getReferenceLoadDTOList() == null) continue;
                for (ReferenceLoadDTO reference : instruction.getReferenceLoadDTOList()) {
                    if (reference == null || "customXPath".equalsIgnoreCase(reference.getReferenceType())) continue;
                    referenceStatement.setString(1, reference.getReferenceType());
                    referenceStatement.setString(2, reference.getValue());
                    referenceStatement.setInt(3, instructionId);
                    referenceStatement.setInt(4, botJobId);
                    referenceStatement.addBatch();
                }
            }
            referenceStatement.executeBatch();
            conn.commit();
            return insertedIds;
        } catch (SQLException | RuntimeException failure) {
            try {
                conn.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    private static void bindAtomicInstruction(
            PreparedStatement statement,
            InstructionLoad instruction,
            int botJobId,
            int blockId) throws SQLException {
        int parameter = 1;
        statement.setObject(parameter++, instruction.getCoordinates());
        statement.setObject(parameter++, instruction.getIFrameXPath());
        statement.setObject(parameter++, instruction.getTagName());
        statement.setObject(parameter++, instruction.getShadowHost());
        statement.setObject(parameter++, instruction.getShadowRoot());
        statement.setObject(parameter++, instruction.getCssSelector());
        statement.setObject(parameter++, instruction.getXpath());
        statement.setObject(parameter++, instruction.getActionCustomMaxWaitSec());
        statement.setObject(parameter++, instruction.getActions());
        statement.setObject(parameter++, instruction.getDefaultValue());
        statement.setObject(parameter++, instruction.getDescription());
        statement.setObject(parameter++, instruction.getInstructionOrderNumber());
        statement.setObject(parameter++, instruction.getName());
        statement.setObject(parameter++, instruction.getClientNamed());
        statement.setObject(parameter++, instruction.getOnHoldSeconds() == null ? 1 : instruction.getOnHoldSeconds());
        statement.setObject(parameter++, instruction.getOperation());
        statement.setObject(parameter++, instruction.getParentBlockId());
        statement.setObject(parameter++, instruction.getParentId());
        statement.setObject(parameter++, instruction.getVariableId());
        statement.setInt(parameter++, blockId);
        statement.setInt(parameter++, botJobId);
        statement.setObject(parameter++, booleanValue(instruction.getBlockMarked()));
        statement.setObject(parameter++, booleanValue(instruction.getCodified()));
        statement.setObject(parameter++, booleanValue(instruction.getExportToABR()));
        statement.setObject(parameter++, booleanValue(instruction.getOptional()));
        statement.setObject(parameter++, booleanValue(instruction.getInstructionActive()));
        statement.setObject(
                parameter,
                instruction.getForceCoordinates() == null ? "" : instruction.getForceCoordinates());
    }

    private static Integer booleanValue(Boolean value) {
        return value == null ? null : value ? 1 : 0;
    }

    public record AtomicInstructionInsertResult(ErrorMessage error, List<Integer> instructionIds) {}

    public ErrorMessage upsertReferencesBatch(String typeTask, List<InstructionLoad> instructionList) {

        boolean isComponent = isComponentTask(typeTask);

        String tableName = isComponent ? "component_reference" : "reference";

        // Existence check
        String selectSql = "SELECT id FROM " + tableName + " WHERE reference_type = ? AND instruction_id = ? AND "
                + (isComponent ? "home_banking_id" : "bot_job_id") + " = ?";

        // Update if exists
        String updateSql = "UPDATE " + tableName + " SET value = ? WHERE id = ?";

        // Insert if not exists
        String insertSql = "INSERT INTO " + tableName + " (reference_type, value, instruction_id, "
                + (isComponent ? "home_banking_id" : "bot_job_id") + ") VALUES (?, ?, ?, ?)";

        final int BATCH_SIZE = 100;
        int updateCount = 0;
        int insertCount = 0;

        try (Connection conn = getConnection();
                PreparedStatement psSelect = conn.prepareStatement(selectSql);
                PreparedStatement psUpdate = conn.prepareStatement(updateSql);
                PreparedStatement psInsert = conn.prepareStatement(insertSql)) {

            conn.setAutoCommit(false);

            for (InstructionLoad instruction : instructionList) {

                Integer instructionId = instruction.getId();
                if (instructionId == null) {
                    // you said you already remove null ids, but this keeps it safe
                    continue;
                }

                // owner filter value depends on table
                Integer ownerId = isComponent ? instruction.getHomeBankingId() : instruction.getBotJobId();
                if (ownerId == null) {
                    continue;
                }

                if (instruction.getReferenceLoadDTOList() == null) continue;

                for (ReferenceLoadDTO ref : instruction.getReferenceLoadDTOList()) {

                    if (ref == null) continue;

                    // Skip this type
                    if (ref.getReferenceType() != null && "customXPath".equalsIgnoreCase(ref.getReferenceType())) {
                        continue;
                    }

                    String referenceType = ref.getReferenceType();
                    String value = ref.getValue();

                    if (referenceType == null) {
                        continue; // cannot filter without reference_type
                    }

                    // --- 1) Check if exists ---
                    Integer existingId = null;

                    psSelect.setString(1, referenceType);
                    psSelect.setInt(2, instructionId);
                    psSelect.setInt(3, ownerId);

                    try (ResultSet rs = psSelect.executeQuery()) {
                        if (rs.next()) {
                            existingId = rs.getInt("id");
                            // If your table is not unique and returns > 1 row, you can detect it:
                            if (rs.next()) {
                                String errMsg = String.format(
                                        "Duplicate reference row for reference_type='%s', instruction_id=%d, %s=%d",
                                        referenceType,
                                        instructionId,
                                        isComponent ? "home_banking_id" : "bot_job_id",
                                        ownerId);
                                logDB.error(errMsg);
                                conn.commit(); // no rollback per your preference
                                return new ErrorMessage("Reference Update Error", "Duplicate reference.", errMsg);
                            }
                        }
                    }

                    // --- 2) UPDATE if exists, else INSERT ---
                    if (existingId != null) {
                        psUpdate.setString(1, value);
                        psUpdate.setInt(2, existingId);
                        psUpdate.addBatch();
                        updateCount++;

                        if (updateCount % BATCH_SIZE == 0) {
                            psUpdate.executeBatch();
                            psUpdate.clearBatch();
                        }
                    } else {
                        psInsert.setString(1, referenceType);
                        psInsert.setString(2, value);
                        psInsert.setInt(3, instructionId);
                        psInsert.setInt(4, ownerId);
                        psInsert.addBatch();
                        insertCount++;

                        if (insertCount % BATCH_SIZE == 0) {
                            psInsert.executeBatch();
                            psInsert.clearBatch();
                        }
                    }
                }
            }

            // flush remaining
            psUpdate.executeBatch();
            psUpdate.clearBatch();

            psInsert.executeBatch();
            psInsert.clearBatch();

            conn.commit();

            logDB.info(String.format(
                    "Reference upsert completed. Inserted: %d, Updated: %d, Table: %s",
                    insertCount, updateCount, tableName.toUpperCase()));

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to upsert references into database: " + error.getMessage());
            return new ErrorMessage(
                    "Reference Update Error", "An error occurred during reference upsert.", error.getMessage());
        } catch (Exception error) {
            logDB.error("Unexpected error upserting references: " + error.getMessage());
            return new ErrorMessage(
                    "Reference Update Error",
                    "An unexpected error occurred during reference upsert.",
                    error.getMessage());
        }
    }

    public ErrorMessage insertReferences(List<ReferenceLoadDTO> queue, int instructionId) {
        String insertSQL =
                "INSERT INTO reference(reference_type, value, instruction_id, bot_job_id) VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            final int BATCH_SIZE = 100;
            int count = 0;

            for (ReferenceLoadDTO reference : queue) {
                if ("customXPath".equalsIgnoreCase(reference.getReferenceType())) {
                    continue; // Skip this type
                }

                pstmt.setString(1, reference.getReferenceType());
                pstmt.setString(2, reference.getValue());
                pstmt.setInt(3, instructionId);
                pstmt.setInt(4, reference.getBotJobId());

                pstmt.addBatch();

                if (++count % BATCH_SIZE == 0) {
                    pstmt.executeBatch();
                    pstmt.clearBatch();
                }
            }

            if (count % BATCH_SIZE != 0) {
                pstmt.executeBatch();
                pstmt.clearBatch();
            }

            logDB.info("Reference batch insert completed successfully.");
            return null; // No error
        } catch (SQLException error) {

            logDB.error("Failed to insert references into database: " + error.getMessage());
            return new ErrorMessage(
                    "Reference Insertion Error", "An error occurred during reference insertion.", error.getMessage());
        } catch (Exception error) {

            logDB.error("Failed to insert references into database: " + error.getMessage());
            return new ErrorMessage(
                    "Reference Insertion Error", "An error occurred during reference insertion.", error.getMessage());
        }
    }

    public ErrorMessage deleteInstructionGraphAtomic(
            String instructionTable,
            int whereId,
            List<Integer> ids,
            List<UpdatedRow> parentRepairs) {
        if (!"instruction".equals(instructionTable) && !"component_instruction".equals(instructionTable)) {
            return new ErrorMessage("Delete Instruction Error", "Invalid instruction table", instructionTable);
        }
        List<Integer> deleteIds = ids == null ? List.of() : ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (deleteIds.isEmpty()) {
            return new ErrorMessage("Delete Instruction Error", "No instructions selected", "Delete list is empty.");
        }
        List<UpdatedRow> repairs = parentRepairs == null ? List.of() : parentRepairs;
        Set<Integer> repairIds = new HashSet<>();
        for (UpdatedRow repair : repairs) {
            if (repair == null || repair.getInstructionId() == null
                    || repair.getInstructionId() <= 0
                    || repair.getParentId() != null
                    || !repairIds.add(repair.getInstructionId())) {
                return new ErrorMessage(
                        "Delete Instruction Error",
                        "Invalid parent repair",
                        "Parent repairs must contain unique surviving IDs and a null parentId.");
            }
        }
        boolean componentWorkspace = "component_instruction".equals(instructionTable);
        String ownerColumn = componentWorkspace ? "home_banking_id" : "bot_job_id";
        String referenceTable = "instruction".equals(instructionTable) ? "reference" : "component_reference";
        String placeholders = String.join(",", Collections.nCopies(deleteIds.size(), "?"));
        Connection connection = null;
        Boolean previousAutoCommit = null;
        try {
            connection = getConnection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            clearDeletedParentRelationships(
                    connection,
                    instructionTable,
                    ownerColumn,
                    whereId,
                    deleteIds,
                    repairs,
                    placeholders);
            deleteOwnedVariableRows(
                    connection, componentWorkspace, whereId, deleteIds, placeholders);
            deleteOwnedRows(connection, referenceTable, ownerColumn, whereId, deleteIds, placeholders);
            String sql = "DELETE FROM " + instructionTable + " WHERE " + ownerColumn + "=? AND id IN (" + placeholders + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindOwnerAndIds(statement, whereId, deleteIds);
                int deleted = statement.executeUpdate();
                if (deleted != deleteIds.size()) throw new SQLException(
                        "Expected to delete " + deleteIds.size() + " instructions but deleted " + deleted + ".");
            }
            connection.commit();
            return null;
        } catch (SQLException exception) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException ignored) { }
            }
            return new ErrorMessage("Delete Instruction Error", "Atomic instruction deletion failed", exception.getMessage());
        } finally {
            if (connection != null) {
                if (previousAutoCommit != null) {
                    try { connection.setAutoCommit(previousAutoCommit); } catch (SQLException ignored) { }
                }
                try { connection.close(); } catch (SQLException ignored) { }
            }
        }
    }

    private void clearDeletedParentRelationships(
            Connection connection,
            String instructionTable,
            String ownerColumn,
            int whereId,
            List<Integer> deleteIds,
            List<UpdatedRow> parentRepairs,
            String deletePlaceholders)
            throws SQLException {
        if (parentRepairs.isEmpty()) return;
        String sql = "UPDATE " + instructionTable
                + " SET parent_id=NULL WHERE " + ownerColumn
                + "=? AND id=? AND parent_id IN (" + deletePlaceholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (UpdatedRow repair : parentRepairs) {
                statement.setInt(1, whereId);
                statement.setInt(2, repair.getInstructionId());
                for (int index = 0; index < deleteIds.size(); index++) {
                    statement.setInt(index + 3, deleteIds.get(index));
                }
                int repaired = statement.executeUpdate();
                if (repaired != 1) {
                    throw new SQLException(
                            "Parent repair for instruction #"
                                    + repair.getInstructionId()
                                    + " no longer matches the confirmed graph.");
                }
            }
        }
    }

    public ErrorMessage deleteBlockGraphAtomic(String blockTable, int whereId, int blockId) {
        if (!"block".equals(blockTable) && !"component_block".equals(blockTable)) {
            return new ErrorMessage("Delete Block Error", "Invalid block table", blockTable);
        }
        boolean componentWorkspace = "component_block".equals(blockTable);
        String ownerColumn = componentWorkspace ? "home_banking_id" : "bot_job_id";
        String instructionTable = componentWorkspace ? "component_instruction" : "instruction";
        String referenceTable = "block".equals(blockTable) ? "reference" : "component_reference";
        Connection connection = null;
        Boolean previousAutoCommit = null;
        try {
            connection = getConnection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            int ownerBlockCount = 0;
            boolean targetBlockExists = false;
            try (PreparedStatement blocks = connection.prepareStatement(
                    "SELECT id FROM " + blockTable + " WHERE " + ownerColumn + "=?")) {
                blocks.setInt(1, whereId);
                try (ResultSet result = blocks.executeQuery()) {
                    while (result.next()) {
                        ownerBlockCount++;
                        if (result.getInt(1) == blockId) targetBlockExists = true;
                    }
                }
            }
            if (!targetBlockExists) {
                throw new SQLException("Block could not be deleted exactly once.");
            }
            boolean retainBlockRecord = ownerBlockCount == 1;

            try (PreparedStatement references = connection.prepareStatement(
                    "SELECT COUNT(*) FROM " + instructionTable + " WHERE " + ownerColumn
                            + "=? AND parent_block_id=? AND block_id<>?")) {
                references.setInt(1, whereId);
                references.setInt(2, blockId);
                references.setInt(3, blockId);
                try (ResultSet result = references.executeQuery()) {
                    if (result.next() && result.getInt(1) > 0) {
                        throw new SQLException("Commands in other blocks reference this block.");
                    }
                }
            }

            List<Integer> instructionIds = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT id FROM " + instructionTable + " WHERE " + ownerColumn + "=? AND block_id=?")) {
                select.setInt(1, whereId);
                select.setInt(2, blockId);
                try (ResultSet result = select.executeQuery()) {
                    while (result.next()) instructionIds.add(result.getInt(1));
                }
            }
            if (!instructionIds.isEmpty()) {
                String placeholders = String.join(",", Collections.nCopies(instructionIds.size(), "?"));
                deleteOwnedVariableRows(
                        connection, componentWorkspace, whereId, instructionIds, placeholders);
                deleteOwnedRows(connection, referenceTable, ownerColumn, whereId, instructionIds, placeholders);
                try (PreparedStatement deleteInstructions = connection.prepareStatement(
                        "DELETE FROM " + instructionTable + " WHERE " + ownerColumn + "=? AND id IN ("
                                + placeholders + ")")) {
                    bindOwnerAndIds(deleteInstructions, whereId, instructionIds);
                    if (deleteInstructions.executeUpdate() != instructionIds.size()) {
                        throw new SQLException("Not every instruction in the block was deleted.");
                    }
                }
            }
            if (!retainBlockRecord) {
                try (PreparedStatement deleteBlock = connection.prepareStatement(
                        "DELETE FROM " + blockTable + " WHERE " + ownerColumn + "=? AND id=?")) {
                    deleteBlock.setInt(1, whereId);
                    deleteBlock.setInt(2, blockId);
                    if (deleteBlock.executeUpdate() != 1) {
                        throw new SQLException("Block could not be deleted exactly once.");
                    }
                }

                List<Integer> remainingIds = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT id FROM " + blockTable + " WHERE " + ownerColumn
                                + "=? ORDER BY block_order_number,id")) {
                    select.setInt(1, whereId);
                    try (ResultSet result = select.executeQuery()) {
                        while (result.next()) remainingIds.add(result.getInt(1));
                    }
                }
                try (PreparedStatement reorder = connection.prepareStatement(
                        "UPDATE " + blockTable + " SET block_order_number=? WHERE " + ownerColumn + "=? AND id=?")) {
                    for (int index = 0; index < remainingIds.size(); index++) {
                        reorder.setInt(1, index + 1);
                        reorder.setInt(2, whereId);
                        reorder.setInt(3, remainingIds.get(index));
                        if (reorder.executeUpdate() != 1) {
                            throw new SQLException("Block order normalization failed.");
                        }
                    }
                }
            }
            connection.commit();
            return null;
        } catch (SQLException exception) {
            if (connection != null) try { connection.rollback(); } catch (SQLException ignored) { }
            return new ErrorMessage("Delete Block Error", "Atomic block deletion failed", exception.getMessage());
        } finally {
            if (connection != null) {
                if (previousAutoCommit != null) try { connection.setAutoCommit(previousAutoCommit); } catch (SQLException ignored) { }
                try { connection.close(); } catch (SQLException ignored) { }
            }
        }
    }

    public record BlockDeleteBatchResult(
            ErrorMessage error, List<Integer> deletedBlockIds, Integer retainedBlockId) {
        public BlockDeleteBatchResult {
            deletedBlockIds = deletedBlockIds == null ? List.of() : List.copyOf(deletedBlockIds);
        }
    }

    public BlockDeleteBatchResult deleteBlocksGraphAtomic(
            String blockTable,
            int whereId,
            List<Integer> deleteBlockIds,
            List<Integer> ignoredExpectedBlockIds,
            Integer ignoredRequestedRetainBlockId) {
        if (!"block".equals(blockTable) && !"component_block".equals(blockTable)) {
            return blockDeleteBatchFailure("Invalid block table: " + blockTable);
        }
        String deleteValidation = validateExactPositiveIds(deleteBlockIds, "deleteBlockIds");
        if (deleteValidation != null) return blockDeleteBatchFailure(deleteValidation);

        LinkedHashSet<Integer> selectedIds = new LinkedHashSet<>(deleteBlockIds);

        boolean componentWorkspace = "component_block".equals(blockTable);
        String ownerColumn = componentWorkspace ? "home_banking_id" : "bot_job_id";
        String instructionTable = componentWorkspace ? "component_instruction" : "instruction";
        String referenceTable = "block".equals(blockTable) ? "reference" : "component_reference";
        Connection connection = null;
        Boolean previousAutoCommit = null;
        try {
            connection = getConnection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            List<Integer> currentBlockIds = new ArrayList<>();
            try (PreparedStatement currentBlocks = connection.prepareStatement(
                    "SELECT id FROM " + blockTable + " WHERE " + ownerColumn
                            + "=? ORDER BY block_order_number,id")) {
                currentBlocks.setInt(1, whereId);
                try (ResultSet result = currentBlocks.executeQuery()) {
                    while (result.next()) currentBlockIds.add(result.getInt(1));
                }
            }
            LinkedHashSet<Integer> currentIds = new LinkedHashSet<>(currentBlockIds);
            if (!currentIds.containsAll(selectedIds)) {
                throw new SQLException("One or more selected Blocks do not belong to the active owner.");
            }

            boolean deletesCompleteOwnerSet = selectedIds.equals(currentIds);
            Integer retainedBlockId =
                    deletesCompleteOwnerSet ? currentBlockIds.get(0) : null;

            List<Integer> deletedBlockIds = new ArrayList<>();
            for (Integer selectedId : selectedIds) {
                if (!Objects.equals(selectedId, retainedBlockId)) {
                    deletedBlockIds.add(selectedId);
                }
            }
            if (!deletedBlockIds.isEmpty()) {
                String deletedPlaceholders =
                        String.join(",", Collections.nCopies(deletedBlockIds.size(), "?"));
                String selectedPlaceholders =
                        String.join(",", Collections.nCopies(selectedIds.size(), "?"));
                String externalReferenceSql = "SELECT COUNT(*) FROM " + instructionTable
                        + " WHERE " + ownerColumn + "=? AND parent_block_id IN (" + deletedPlaceholders + ")"
                        + " AND (block_id IS NULL OR block_id NOT IN (" + selectedPlaceholders + "))";
                try (PreparedStatement references = connection.prepareStatement(externalReferenceSql)) {
                    int parameter = 1;
                    references.setInt(parameter++, whereId);
                    for (Integer id : deletedBlockIds) references.setInt(parameter++, id);
                    for (Integer id : selectedIds) references.setInt(parameter++, id);
                    try (ResultSet result = references.executeQuery()) {
                        if (result.next() && result.getInt(1) > 0) {
                            throw new SQLException(
                                    "Commands in surviving Blocks reference a Block selected for deletion.");
                        }
                    }
                }
            }

            List<Integer> instructionIds = new ArrayList<>();
            String selectedPlaceholders =
                    String.join(",", Collections.nCopies(selectedIds.size(), "?"));
            try (PreparedStatement instructions = connection.prepareStatement(
                    "SELECT id FROM " + instructionTable + " WHERE " + ownerColumn
                            + "=? AND block_id IN (" + selectedPlaceholders + ")")) {
                instructions.setInt(1, whereId);
                int parameter = 2;
                for (Integer id : selectedIds) instructions.setInt(parameter++, id);
                try (ResultSet result = instructions.executeQuery()) {
                    while (result.next()) instructionIds.add(result.getInt(1));
                }
            }
            if (!instructionIds.isEmpty()) {
                String instructionPlaceholders =
                        String.join(",", Collections.nCopies(instructionIds.size(), "?"));
                deleteOwnedVariableRows(
                        connection,
                        componentWorkspace,
                        whereId,
                        instructionIds,
                        instructionPlaceholders);
                deleteOwnedRows(
                        connection,
                        referenceTable,
                        ownerColumn,
                        whereId,
                        instructionIds,
                        instructionPlaceholders);
                try (PreparedStatement deleteInstructions = connection.prepareStatement(
                        "DELETE FROM " + instructionTable + " WHERE " + ownerColumn
                                + "=? AND id IN (" + instructionPlaceholders + ")")) {
                    bindOwnerAndIds(deleteInstructions, whereId, instructionIds);
                    if (deleteInstructions.executeUpdate() != instructionIds.size()) {
                        throw new SQLException("Not every selected Block instruction was deleted.");
                    }
                }
            }

            if (!deletedBlockIds.isEmpty()) {
                String deletedPlaceholders =
                        String.join(",", Collections.nCopies(deletedBlockIds.size(), "?"));
                try (PreparedStatement deleteBlocks = connection.prepareStatement(
                        "DELETE FROM " + blockTable + " WHERE " + ownerColumn
                                + "=? AND id IN (" + deletedPlaceholders + ")")) {
                    bindOwnerAndIds(deleteBlocks, whereId, deletedBlockIds);
                    if (deleteBlocks.executeUpdate() != deletedBlockIds.size()) {
                        throw new SQLException("Not every selected Block was deleted.");
                    }
                }
            }

            List<Integer> remainingIds = new ArrayList<>();
            try (PreparedStatement remaining = connection.prepareStatement(
                    "SELECT id FROM " + blockTable + " WHERE " + ownerColumn
                            + "=? ORDER BY block_order_number,id")) {
                remaining.setInt(1, whereId);
                try (ResultSet result = remaining.executeQuery()) {
                    while (result.next()) remainingIds.add(result.getInt(1));
                }
            }
            try (PreparedStatement reorder = connection.prepareStatement(
                    "UPDATE " + blockTable + " SET block_order_number=? WHERE " + ownerColumn + "=? AND id=?")) {
                for (int index = 0; index < remainingIds.size(); index++) {
                    reorder.setInt(1, index + 1);
                    reorder.setInt(2, whereId);
                    reorder.setInt(3, remainingIds.get(index));
                    if (reorder.executeUpdate() != 1) {
                        throw new SQLException("Block order normalization failed.");
                    }
                }
            }

            connection.commit();
            return new BlockDeleteBatchResult(null, deletedBlockIds, retainedBlockId);
        } catch (SQLException exception) {
            if (connection != null) try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            return blockDeleteBatchFailure(exception.getMessage());
        } finally {
            if (connection != null) {
                if (previousAutoCommit != null) try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                }
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    private BlockDeleteBatchResult blockDeleteBatchFailure(String detail) {
        return new BlockDeleteBatchResult(
                new ErrorMessage("Delete Blocks Error", "Atomic block deletion failed", detail),
                List.of(),
                null);
    }

    private String validateExactPositiveIds(List<Integer> ids, String fieldName) {
        if (ids == null || ids.isEmpty()) return fieldName + " must contain at least one Block ID.";
        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        for (Integer id : ids) {
            if (id == null || id <= 0) return fieldName + " must contain only positive Block IDs.";
            if (!unique.add(id)) return fieldName + " must not contain duplicate Block IDs.";
        }
        return null;
    }

    private void deleteOwnedRows(Connection connection, String table, String ownerColumn, int whereId,
            List<Integer> ids, String placeholders) throws SQLException {
        String sql = "DELETE FROM " + table + " WHERE " + ownerColumn + "=? AND instruction_id IN (" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindOwnerAndIds(statement, whereId, ids);
            statement.executeUpdate();
        }
    }

    private void deleteOwnedVariableRows(
            Connection connection,
            boolean componentWorkspace,
            int whereId,
            List<Integer> instructionIds,
            String placeholders)
            throws SQLException {
        if (componentWorkspace) {
            deleteOwnedRows(
                    connection,
                    "component_variable",
                    "home_banking_id",
                    whereId,
                    instructionIds,
                    placeholders);
            return;
        }

        OwnerKey owner = botJobVariableOwner(connection, whereId);
        String selectSql = "SELECT id FROM bot_job_variable_definition"
                + " WHERE home_banking_id=? AND bot_job_id=?"
                + " AND producer_instruction_id IN (" + placeholders + ")";
        List<Long> definitionIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setInt(1, owner.homeBankingId());
            statement.setInt(2, owner.botJobId());
            for (int index = 0; index < instructionIds.size(); index++) {
                statement.setInt(index + 3, instructionIds.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    definitionIds.add(result.getLong(1));
                }
            }
        }
        if (definitionIds.isEmpty()) {
            return;
        }
        MutationResult deleted =
                botJobRuntimeVariables.deleteDefinitions(connection, owner, definitionIds, null);
        if (!deleted.applied()) {
            throw new SQLException(deleted.message());
        }
    }

    private void bindOwnerAndIds(PreparedStatement statement, int whereId, List<Integer> ids) throws SQLException {
        statement.setInt(1, whereId);
        for (int index = 0; index < ids.size(); index++) statement.setInt(index + 2, ids.get(index));
    }

    public void updateDatabaseSchema(String dbUrl, File dbFile) {

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (Statement stmt = conn.createStatement()) {

                DatabaseMetaData dbMeta = conn.getMetaData();

                // 1. Check if the foreign key constraint already exists
                boolean fkExists = false;
                ResultSet rsFK = null;
                try {
                    // getImportedKeys(catalog, schema, table)
                    // For Access, catalog and schema are usually null or empty string.
                    // "bot_job" is the table that *has* the foreign key.
                    rsFK = dbMeta.getImportedKeys(null, null, "bot_job");

                    while (rsFK.next()) {
                        String fkColumnName = rsFK.getString("FKCOLUMN_NAME");
                        String pkTableName = rsFK.getString("PKTABLE_NAME");
                        String pkColumnName = rsFK.getString("PKCOLUMN_NAME");

                        // Check if it's the specific foreign key we want
                        // Matching by foreign key column, referenced table, and referenced primary key column
                        if ("home_url_id".equalsIgnoreCase(fkColumnName)
                                && "home_url".equalsIgnoreCase(pkTableName)
                                && "id".equalsIgnoreCase(pkColumnName)) {
                            fkExists = true;
                            // Optional: You can print the FK_NAME if you want to know what Access called it
                            // String fkName = rsFK.getString("FK_NAME");
                            // logDB.info(String.format("Foreign key '%s' (home_url_id -> home_url.id) already
                            // exists.", fkName));
                            break;
                        }
                    }
                } finally {
                    if (rsFK != null) {
                        try {
                            rsFK.close();
                        } catch (SQLException e) {
                            logDB.error("Error closing ResultSet for FK check: " + e.getMessage());
                        }
                    }
                }

                // 2. Add the foreign key constraint only if it doesn't exist
                if (!fkExists) {
                    logDB.info(String.format(
                            "Foreign key 'FK_NewHomeURL' (home_url_id -> home_url.id) not found. Adding it..."));
                    String addHomrURLForeignKeySQL = "ALTER TABLE bot_job "
                            + "ADD CONSTRAINT FK_NewHomeURL FOREIGN KEY (home_url_id) "
                            + "REFERENCES home_url(id) ";
                    stmt.executeUpdate(addHomrURLForeignKeySQL);
                    logDB.info(String.format("Foreign key 'FK_NewHomeURL' added to 'bot_job' table."));
                    logDB.info(String.format("Database %s has been updated with the foreign key!", dbFile.getName()));
                } else {
                    logDB.info(String.format(
                            "Database %s no need for foreign key 'FK_NewHomeURL' updates (constraint exists).",
                            dbFile.getName()));
                }

                // Ensure the statement is closed
                if (stmt != null) {
                    try {
                        stmt.close();
                    } catch (SQLException e) {
                        logDB.error("Error closing Statement: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException error) {
            logDB.info("initializeDatabase\nError: " + error.getMessage());
        }
    }

    public void disableForeignKeyConstraints(String dbUrl) {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            DatabaseMetaData meta = conn.getMetaData();
            Statement stmt = conn.createStatement();

            // Loop through all tables
            ResultSet tables = meta.getTables(null, null, null, new String[] {"TABLE"});
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");

                // Get foreign keys for the table
                ResultSet fks = meta.getImportedKeys(null, null, tableName);
                while (fks.next()) {
                    String fkName = fks.getString("FK_NAME");

                    if (fkName != null && !fkName.trim().isEmpty()) {
                        String dropSQL = String.format("ALTER TABLE [%s] DROP CONSTRAINT [%s]", tableName, fkName);
                        logDB.info("Dropping FK: " + dropSQL);
                        try {
                            stmt.executeUpdate(dropSQL);
                        } catch (SQLException ex) {
                            logDB.error("Failed to drop constraint " + fkName + ": " + ex.getMessage());
                        }
                    }
                }
                fks.close();
            }

            tables.close();
            stmt.close();
            logDB.info("All foreign key constraints removed.");

        } catch (SQLException e) {
            logDB.error("Error disable Foreign Key Constraints");
        }
    }

    private void updateBotJobHomeUrlId(List<HomeUrlDTO> listHomeUrl) {
        try (Connection conn = getConnection()) {

            ErrorMessage errorMessage = updateBotJobHomeUrlIds(conn, listHomeUrl);

            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }

        } catch (SQLException ex) {
            logDB.info(ex.getMessage());
        }
    }

    private ErrorMessage updateBotJobHomeUrlIds(Connection conn, List<HomeUrlDTO> listHomeUrl) {
        // Update bot_job only if the home_url_id to be set exists in the home_url table
        String blockInsertQuery = "UPDATE bot_job AS bj " + "SET bj.home_url_id = ? "
                + "WHERE bj.home_banking_id = ? "
                + "AND EXISTS (SELECT 1 FROM home_url WHERE id = ?);"; // Parameter for home_url.id check

        try (PreparedStatement blockStmt = conn.prepareStatement(blockInsertQuery)) {
            boolean batchModeEnabled = false;
            for (HomeUrlDTO homeUrl : listHomeUrl) {
                blockStmt.setInt(1, homeUrl.getId()); // 1st ?: Sets home_url_id in bot_job
                blockStmt.setInt(2, homeUrl.getHomeBankingId()); // 2nd ?: Sets home_banking_id in bot_job
                blockStmt.setInt(3, homeUrl.getId()); // 4th ?: Used in AND EXISTS (SELECT 1 FROM home_url WHERE id = ?)

                blockStmt.addBatch(); // Add the current block to the batch
                batchModeEnabled = true;
            }
            if (batchModeEnabled) {
                blockStmt.executeBatch(); // Execute the batch update
            }
            return null;
        } catch (SQLException error) {
            logDB.info(error.getMessage());
            return new ErrorMessage("Error Duplicating Blocks", "Block Insertion Failure", error.getMessage());
        }
    }

    // CREATE NEW HOME BANK
    public Integer getNewHomeBankId() {
        if (idsHomeBankAfter != null && idsHomeBankAfter.size() == 1) {
            return idsHomeBankAfter.get(0); // return the only new ID
        }
        return -1; // invalid or multiple IDs
    }

    public ErrorMessage createNewHomeBanking(DatabaseUserDTO user) {
        String tableName = "home_banking";
        String insertSQL = "INSERT INTO " + tableName
                + " (Name, Url, priority, search_config, options_config, username, password) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement();
                PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            // Step 1: Get IDs before insertion
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery("SELECT id FROM " + tableName + " ORDER BY id")) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            // Step 2: Prepare values
            String priority = Strings.isNullOrEmpty(user.getPriority()) ? "" : user.getPriority();
            String searchConfig = Strings.isNullOrEmpty(user.getSearchConfig()) ? "" : user.getSearchConfig();
            String optionsConfig = Strings.isNullOrEmpty(user.getOptionsConfig()) ? "" : user.getOptionsConfig();

            // Step 3: Insert new home_banking record
            pstmt.setString(1, user.getName());
            pstmt.setString(2, user.getUrl());
            pstmt.setString(3, priority);
            pstmt.setString(4, searchConfig);
            pstmt.setString(5, optionsConfig);
            pstmt.setString(6, user.getUsername());
            pstmt.setString(7, user.getPassword());
            pstmt.addBatch();
            pstmt.executeBatch();

            // Step 4: Get IDs after insertion
            idsHomeBankAfter.clear();
            try (ResultSet rsAfter = idStmtAfter.executeQuery("SELECT id FROM " + tableName + " ORDER BY id")) {
                while (rsAfter.next()) {
                    idsHomeBankAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 5: Keep only the new IDs
            idsHomeBankAfter.removeAll(idsBefore);

            logDB.info(String.format("HomeBanking inserted successfully. New IDs: %s", idsHomeBankAfter));

            return null; // null means no error

        } catch (SQLException error) {

            logDB.error(String.format("saveUserData - Error: %s", error.getMessage()));

            return new ErrorMessage(
                    "HomeBanking Insertion Error", "Error inserting a new HomeBanking record.", error.getMessage());
        }
    }

    // CREATE NEW HOME URL CHILD
    public Integer getNewHomeUrlId() {
        if (idsHomeUrlAfter != null && idsHomeUrlAfter.size() == 1) {
            return idsHomeUrlAfter.get(0); // return the only new ID
        }
        return -1; // invalid or multiple IDs
    }

    public ErrorMessage createHomeUrlChild(int homeBankId, String newUrl) {
        return createHomeUrlChild(homeBankId, newUrl, "TEST");
    }

    public ErrorMessage createHomeUrlChild(int homeBankId, String newUrl, String environmentName) {
        String tableName = "home_url";
        String insertSQL = "INSERT INTO " + tableName + " (name, url, home_banking_id) VALUES (?, ?, ?)";
        String safeEnvironmentName = defaultEnvironmentName(environmentName);

        try (Connection conn = getConnection();
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement();
                PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            conn.setAutoCommit(false); // Disable auto-commit

            // Step 1: Get IDs before insertion
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery("SELECT id FROM " + tableName + " ORDER BY id")) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            // Step 2: Insert new home_url record
            pstmt.setString(1, safeEnvironmentName);
            pstmt.setString(2, newUrl);
            pstmt.setInt(3, homeBankId);
            pstmt.addBatch();
            pstmt.executeBatch();

            // Step 3: Get IDs after insertion
            idsHomeUrlAfter.clear();
            try (ResultSet rsAfter = idStmtAfter.executeQuery("SELECT id FROM " + tableName + " ORDER BY id")) {
                while (rsAfter.next()) {
                    idsHomeUrlAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 4: Keep only the new IDs
            idsHomeUrlAfter.removeAll(idsBefore);

            logDB.info(String.format("HomeUrl inserted successfully. New IDs: %s", idsHomeUrlAfter));

            conn.commit(); // Commit transaction
            return null; // Success

        } catch (SQLException error) {

            logDB.error(String.format("createHomeUrlChild - Error: %s", error.getMessage()));

            return new ErrorMessage("Home URL Insertion Error", "Error inserting a new home URL.", error.getMessage());
        }
    }

    public ErrorMessage createNewHomeUrl(int homeBankId, String newUrl) {
        return createNewHomeUrl(homeBankId, newUrl, "TEST");
    }

    public ErrorMessage createNewHomeUrl(int homeBankId, String newUrl, String environmentName) {
        String tableName = "home_url";
        String checkQuery = "SELECT COUNT(*) FROM " + tableName + " WHERE url = ? AND home_banking_id = ?";
        String insertSQL = "INSERT INTO " + tableName + " (name, url, home_banking_id) VALUES (?, ?, ?)";
        String safeEnvironmentName = defaultEnvironmentName(environmentName);

        try (Connection conn = getConnection();
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false); // Disable auto-commit

            // Step 1: Check if the URL already exists for the given home_banking_id
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setString(1, newUrl);
                checkStmt.setInt(2, homeBankId);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        conn.commit(); // Commit even though nothing was inserted
                        return new ErrorMessage(
                                "Error: URL already exists for this organization.",
                                "Duplicate URL Entry",
                                "The URL '" + newUrl + "' is already assigned to this home_banking_id: " + homeBankId);
                    }
                }
            }

            // Step 2: Get IDs before insertion
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery("SELECT id FROM " + tableName + " ORDER BY id")) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            // Step 3: Prepare and execute insert
            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
                pstmt.setString(1, safeEnvironmentName);
                pstmt.setString(2, newUrl);
                pstmt.setInt(3, homeBankId);

                pstmt.addBatch();
                pstmt.executeBatch();
            }

            // Step 4: Get IDs after insertion
            idsHomeUrlAfter.clear();
            try (ResultSet rsAfter = idStmtAfter.executeQuery("SELECT id FROM " + tableName + " ORDER BY id")) {
                while (rsAfter.next()) {
                    idsHomeUrlAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 5: Keep only the new IDs
            idsHomeUrlAfter.removeAll(idsBefore);

            logDB.info(String.format("HomeUrl inserted successfully. New IDs: %s", idsHomeUrlAfter));

            conn.commit(); // Commit transaction
            return null; // Success, no error

        } catch (SQLException error) {

            logDB.error(String.format("insertNewHomeUrl - Error: %s", error.getMessage()));

            return new ErrorMessage("Home URL Insertion Error", "Error inserting a new home URL.", error.getMessage());
        }
    }

    public ErrorMessage updateHomeUrl(int homeUrlId, int homeBankId, String newUrl) throws SQLException {
        return updateHomeUrl(homeUrlId, homeBankId, newUrl, "TEST");
    }

    public ErrorMessage updateHomeUrl(int homeUrlId, int homeBankId, String newUrl, String environmentName)
            throws SQLException {
        String checkQuery = "SELECT COUNT(*) FROM home_url WHERE url = ? AND home_banking_id = ? AND id != ?";
        String updateQuery = "UPDATE home_url SET name = ?, url = ? WHERE id = ? AND home_banking_id = ?";
        String safeEnvironmentName = defaultEnvironmentName(environmentName);

        try (Connection conn = getConnection()) {
            // Check if the URL already exists for this org but with a different ID
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setString(1, newUrl);
                checkStmt.setInt(2, homeBankId);
                checkStmt.setInt(3, homeUrlId);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return new ErrorMessage(
                                "Error: URL already exists for this organization.",
                                "Duplicate URL Entry",
                                "The URL '" + newUrl + "' is already assigned to this home_banking_id: " + homeBankId);
                    }
                }
            }

            // Proceed with update
            try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                updateStmt.setString(1, safeEnvironmentName);
                updateStmt.setString(2, newUrl);
                updateStmt.setInt(3, homeUrlId);
                updateStmt.setInt(4, homeBankId);

                int updated = updateStmt.executeUpdate();
                if (updated == 0) {
                    return new ErrorMessage(
                            "No URL updated",
                            "Update Failed",
                            "No record was found with ID " + homeUrlId + " for home_banking_id " + homeBankId);
                }

                return null;
            }

        } catch (SQLException error) {
            logDB.info(error.getMessage());
            return new ErrorMessage("Error updating URL", "Org URL Update Failure", error.getMessage());
        }
    }

    private String defaultEnvironmentName(String environmentName) {
        return environmentName == null || environmentName.trim().isEmpty() ? "TEST" : environmentName.trim();
    }

    public ErrorMessage deleteHomeUrl(int homeUrlId) throws SQLException {
        String usageCheckQuery = "SELECT COUNT(*) AS usage_count FROM bot_job WHERE home_url_id = ?";
        String deleteQuery = "DELETE FROM home_url WHERE id = ?";

        try (Connection conn = getConnection()) {
            // Step 1: Check usage in bot_job
            try (PreparedStatement checkStmt = conn.prepareStatement(usageCheckQuery)) {
                checkStmt.setInt(1, homeUrlId);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        int usageCount = rs.getInt("usage_count");
                        if (usageCount > 0) {
                            return new ErrorMessage(
                                    "Cannot delete URL",
                                    "URL in Use",
                                    "This URL is used in " + usageCount
                                            + " bot_job(s). Please detach it before deletion.");
                        }
                    }
                }
            }

            // Step 2: Proceed with deletion
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery)) {
                deleteStmt.setInt(1, homeUrlId);
                int rows = deleteStmt.executeUpdate();

                if (rows == 0) {
                    return new ErrorMessage("URL not found", "Deletion Failure", "No matching URL ID found.");
                }

                return null; // Success
            }

        } catch (SQLException e) {
            return new ErrorMessage("Error deleting URL", "Database Error", e.getMessage());
        }
    }

    public int countUsageOfHomeUrlId(int homeUrlId) {
        String countQuery = "SELECT COUNT(*) AS usage_count FROM bot_job WHERE home_url_id = ?";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(countQuery)) {

            stmt.setInt(1, homeUrlId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("usage_count");
                }
            }

        } catch (SQLException e) {
            logDB.info("Error counting usage of Home URL ID " + homeUrlId + ": " + e.getMessage());
        }

        return 0; // Return 0 if query fails or no result
    }

    public ErrorMessage loadAllDataUsers() {
        performLists.getListDatabaseUsers().clear();

        String selectSQL = "SELECT " + "  bank.ID, "
                + "  bank.Name, "
                + "  hu.url, "
                + "  bank.priority, "
                + "  COUNT(bot.ID) AS Jobs, "
                + "  bank.search_config AS searchConfig, "
                + "  bank.options_config AS optionsConfig, "
                + "  bank.username, "
                + "  bank.password "
                + "FROM home_banking bank "
                + "LEFT JOIN bot_job bot ON bot.home_banking_id = bank.id "
                + "LEFT JOIN home_url hu ON hu.home_banking_id = bank.id "
                + "GROUP BY "
                + "  bank.ID, bank.Name, hu.url, bank.priority, "
                + "  bank.search_config, bank.options_config, bank.username, bank.password "
                + "ORDER BY bank.ID;";

        try (PreparedStatement pstmt = getConnection().prepareStatement(selectSQL);
                ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("ID");
                String jobs = rs.getString("Jobs");
                String name = rs.getString("Name");
                String url = rs.getString("Url");
                String priority = rs.getString("Priority");
                String searchConfig = rs.getString("searchConfig");
                String optionsConfig = rs.getString("optionsConfig");
                String username = rs.getString("username");
                String password = rs.getString("password");

                // Convert "£" delimiters back to newlines
                String priorityStr = convertDelimitedString(priority);
                String searchConfigStr = convertDelimitedString(searchConfig);
                String optionsConfigStr = convertDelimitedString(optionsConfig);

                performLists
                        .getListDatabaseUsers()
                        .add(new DatabaseUserDTO(
                                id,
                                jobs,
                                name,
                                url,
                                priorityStr,
                                searchConfigStr,
                                optionsConfigStr,
                                username,
                                password));
            }

        } catch (SQLException e) {

            logDB.error(String.format("Error loadAllDataUsers: %s", e.getMessage()));

            return new ErrorMessage("Failed to load Database Users", "Database query error", e.getMessage());
        }

        return null;
    }

    /**
     * Helper to replace '£' delimiters with newlines and trim the trailing newline.
     */
    private String convertDelimitedString(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String part : input.split("£")) {
            sb.append(part).append("\n");
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1); // remove last newline
        }
        return sb.toString();
    }

    public void selectHomeBankinOneRow() {
        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        String accessDbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;
        logDB.info("ACCESS connection URL: " + accessDbUrl);

        String postgresDbUrl = arPropertyManager.getProperty(ARPropertyEnum.DB_URL);
        String userDB = arPropertyManager.getProperty(ARPropertyEnum.DB_USER);
        String userPwd = arPropertyManager.getProperty(ARPropertyEnum.DB_PWD);

        // String userData = userDB + " - " + userPwd;

        logDB.info("POSTGRES connection URL: " + postgresDbUrl);
        // logDB.info("User Details: " + userData);

        final int BATCH_SIZE = 100;

        try (Connection accessConn = DriverManager.getConnection(accessDbUrl);
                Connection postgresConn = DriverManager.getConnection(postgresDbUrl, userDB, userPwd);
                Statement postgreStmt = postgresConn.createStatement(); ) {
            accessConn.setAutoCommit(false); // Use manual commit for batch performance

            String selectPostgresSQL =
                    "SELECT ID, url, name, priority, search_config, options_config, cookies, driver_session, username, password FROM home_banking order by id";
            ResultSet rsHomeBank = postgreStmt.executeQuery(selectPostgresSQL);

            String checkSQL = "SELECT id FROM home_banking WHERE url = ?";
            String insertSQL =
                    "INSERT INTO home_banking (url, name, priority, search_config, options_config, cookies, driver_session, username, password, id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            homeBankMap.clear();
            try (PreparedStatement checkStmt = accessConn.prepareStatement(checkSQL);
                    PreparedStatement insertStmt = accessConn.prepareStatement(insertSQL)) {
                int count = 0;

                while (rsHomeBank.next()) {
                    int id = rsHomeBank.getInt("id");
                    String url = rsHomeBank.getString("url");

                    if (url != null && !url.trim().isEmpty()) {
                        homeBankMap.put(id, -1);
                    }

                    // Check for existence
                    checkStmt.setString(1, url);
                    ResultSet checkResult = checkStmt.executeQuery();

                    if (!checkResult.next()) {
                        // Add to batch
                        insertStmt.setInt(10, id);

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
                            accessConn.commit();
                            logDB.info("Inserted batch of " + BATCH_SIZE);
                        }
                    } else {
                        logDB.info("Skipped (exists): " + url);
                    }
                }

                // Final batch
                if (count % BATCH_SIZE != 0) {
                    insertStmt.executeBatch();
                    accessConn.commit();
                    logDB.info("Inserted final batch of " + (count % BATCH_SIZE));
                }
            }

            logDB.info("Sync completed.");
        } catch (SQLException error) {
            logDB.error("Error export HomeBanking");
        }
    }

    public ErrorMessage dropPostGresSequences() {
        // Build the SQL update statement

        String postgresDbUrl = arPropertyManager.getProperty(ARPropertyEnum.DB_URL);
        String userDB = arPropertyManager.getProperty(ARPropertyEnum.DB_USER);
        String userPwd = arPropertyManager.getProperty(ARPropertyEnum.DB_PWD);

        try (Connection postgresConn = DriverManager.getConnection(postgresDbUrl, userDB, userPwd)) {

            try (Statement stmt = postgresConn.createStatement()) {
                int rowsAffected = 0;

                rowsAffected += stmt.executeUpdate("DELETE  FROM \"home_url\";");
                rowsAffected += stmt.executeUpdate("DELETE FROM \"home_banking\";");

                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"home_url_id_seq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"home_banking_id_seq\";");

                // Recreate sequences
                rowsAffected += stmt.executeUpdate("CREATE SEQUENCE \"home_url_id_seq\" START WITH 1 INCREMENT BY 1;");
                rowsAffected +=
                        stmt.executeUpdate("CREATE SEQUENCE \"home_banking_id_seq\" START WITH 1 INCREMENT BY 1;");

                // Uncomment if needed
                //            rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"instruction_id_seq\";");
                //            rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"block_id_seq\";");
                //            rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"bot_job_id_seq\";");
                //            rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"component_block_id_seq\";");
                //            rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS
                // \"component_instruction_id_seq\";");
                //            rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS
                // \"component_reference_id_seq\";");
                //            rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS
                // \"component_variable_id_seq\";");
                //            rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"reference_id_seq\";");
                //            rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"variable_id_seq\";");

                if (rowsAffected > 0) {

                    logDB.warn(String.format("Migration DB Scripts - RowsUpdated - %s", rowsAffected));
                } else {
                    logDB.info("Migration DB Scripts - No Rows were updated");
                }
                return null;

            } catch (SQLException error) {

                logDB.warn("Migration DB Scripts - Error: " + error.getMessage());
                return new ErrorMessage(
                        "Error Drop Tables Migration 2.7f", "Error dropping OLD objects", error.getMessage());
            }

        } catch (SQLException error) {
            logDB.error("Failed to dropPostGresSequences.");
            return new ErrorMessage("Connection Error", "Could not connect to Postgres DB", error.getMessage());
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

    // CREATE SAVED COMPONENTS
    public ErrorMessage createCompBlock(BlockDetailsDTO blockDetailsDTO) {

        final int BATCH_SIZE = 100;
        String checkExistsSQL =
                "SELECT id, block_order_number, name, description, type_id, export_file, active, wait, bot_job_id "
                        + "FROM block WHERE bot_job_id = ? AND id = ? ORDER BY id";

        String selectComponentIdsSQL = "SELECT id FROM component_block WHERE home_banking_id = "
                + blockDetailsDTO.getHomeBankingId() + " ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement selectStmt = conn.prepareStatement(checkExistsSQL);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current component_block ids before insert
            List<Integer> blockIdsBefore = new ArrayList<>();
            try (ResultSet rsIdsBefore = idStmtBefore.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsBefore.next()) {
                    blockIdsBefore.add(rsIdsBefore.getInt("id"));
                }
            }

            // Step 2: prepare and execute insert
            selectStmt.setInt(1, blockDetailsDTO.getBotJobId());
            selectStmt.setInt(2, blockDetailsDTO.getBlockId());

            try (ResultSet rs = selectStmt.executeQuery()) {

                String insertSQL = "INSERT INTO component_block "
                        + "(block_order_number, name, description, type_id, export_file, active, wait, home_banking_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

                blockMap.clear();

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                    int count = 0;

                    while (rs.next()) {
                        int id = rs.getInt("id");
                        //                        int blockOrderNumber = rs.getInt("block_order_number");
                        //                        String name = rs.getString("name");
                        //                        String description = rs.getString("description");
                        Integer typeId = rs.getObject("type_id") != null ? rs.getInt("type_id") : null;
                        String exportFile = rs.getString("export_file");
                        int active = rs.getInt("active");
                        Integer wait = rs.getObject("wait") != null ? rs.getInt("wait") : null;

                        Integer newHomeBankId = blockDetailsDTO.getHomeBankingId();
                        if (newHomeBankId == null) {
                            logDB.info("Skipped component_block with unknown home_banking_id");
                            continue;
                        }

                        blockMap.put(id, -1);

                        insertStmt.setInt(1, blockDetailsDTO.getBlockOrderNumber());
                        insertStmt.setString(2, blockDetailsDTO.getBlockName());
                        insertStmt.setString(3, blockDetailsDTO.getBlockDescription());
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
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            conn.commit();
                            logDB.info("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        conn.commit();
                        logDB.info("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get component_block ids after insert
            List<Integer> blockIdsAfter = new ArrayList<>();
            try (ResultSet rsIdsAfter = idStmtAfter.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsAfter.next()) {
                    blockIdsAfter.add(rsIdsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs
            List<Integer> newComponentIds = new ArrayList<>(blockIdsAfter);
            newComponentIds.removeAll(blockIdsBefore);

            // You can now use `newComponentIds` as needed
            logDB.info("Newly inserted component_block IDs: " + newComponentIds);

            // Step 5: update blockMap with new IDs
            List<Integer> keys = new ArrayList<>(blockMap.keySet());

            if (keys.size() != newComponentIds.size()) {
                logDB.error(
                        "Mismatch in size: expected " + keys.size() + " new IDs, but got " + newComponentIds.size());
            } else {
                for (int i = 0; i < keys.size(); i++) {
                    Integer oldId = keys.get(i);
                    Integer newId = newComponentIds.get(i);
                    blockMap.put(oldId, newId);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to create saved component_block");
            return new ErrorMessage(
                    "Failed to create saved component_block", "component_block Insertion Failure", error.getMessage());
        }
    }

    public ErrorMessage createCompInstructions(BlockDetailsDTO blockDetailsDTO) {

        final int BATCH_SIZE = 100;

        String checkExistsSQL = "SELECT * FROM instruction WHERE bot_job_id = ? AND block_id = ? order by id";

        String selectComponentIdsSQL = "SELECT id FROM component_instruction WHERE home_banking_id = "
                + blockDetailsDTO.getHomeBankingId() + " ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement selectStmt = conn.prepareStatement(checkExistsSQL);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current component_block ids before insert
            List<Integer> blockIdsBefore = new ArrayList<>();
            try (ResultSet rsIdsBefore = idStmtBefore.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsBefore.next()) {
                    blockIdsBefore.add(rsIdsBefore.getInt("id"));
                }
            }

            // Step 2: prepare and execute insert
            selectStmt.setInt(1, blockDetailsDTO.getBotJobId());
            selectStmt.setInt(2, blockDetailsDTO.getBlockId());

            try (ResultSet rsInstruction = selectStmt.executeQuery()) {

                String insertSQL = "INSERT INTO component_instruction ("
                        + "instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, block_id, variable_id, parent_id, home_banking_id, client_named) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                instructionMap.clear();
                instrVariablesMap.clear();

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                    int count = 0;

                    while (rsInstruction.next()) {
                        int id = rsInstruction.getInt("id");
                        Integer newHomeBankId = blockDetailsDTO.getHomeBankingId();
                        if (newHomeBankId == null) {
                            logDB.info("Skipped component_instruction with unknown home_banking_id");
                            continue;
                        }

                        int oldBlockId = rsInstruction.getInt("block_id");
                        Integer newBlockId = blockMap.get(oldBlockId);
                        if (newBlockId == null) {
                            logDB.info("Skipped component_instruction with unknown block_id: " + oldBlockId);
                            continue;
                        }

                        instructionMap.put(id, -1);

                        insertStmt.setInt(1, rsInstruction.getInt("instruction_order_number"));
                        insertStmt.setString(2, rsInstruction.getString("actions"));
                        insertStmt.setString(3, rsInstruction.getString("name"));
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

                        // variable_id + tracking
                        Integer variableId = rsInstruction.getInt("variable_id");
                        if (rsInstruction.wasNull()) {
                            variableId = null;
                        }

                        if (variableId != null) {
                            instrVariablesMap.put(id, variableId);
                        }
                        insertStmt.setNull(23, Types.INTEGER); // TO BE SOLVED LATER

                        insertOrNull(insertStmt, 24, rsInstruction, "parent_id");

                        insertStmt.setInt(25, newHomeBankId);
                        insertStmt.setString(26, rsInstruction.getString("client_named"));

                        insertStmt.addBatch();
                        count++;
                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            conn.commit();
                            logDB.info("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        conn.commit();
                        logDB.info("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get component_block ids after insert
            List<Integer> blockIdsAfter = new ArrayList<>();
            try (ResultSet rsIdsAfter = idStmtAfter.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsAfter.next()) {
                    blockIdsAfter.add(rsIdsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs
            List<Integer> newComponentIds = new ArrayList<>(blockIdsAfter);
            newComponentIds.removeAll(blockIdsBefore);

            // You can now use `newComponentIds` as needed
            logDB.info("Newly inserted component_instruction IDs: " + newComponentIds);

            // Step 5: update blockMap with new IDs
            List<Integer> keys = new ArrayList<>(instructionMap.keySet());

            if (keys.size() != newComponentIds.size()) {
                logDB.error(
                        "Mismatch in size: expected " + keys.size() + " new IDs, but got " + newComponentIds.size());
            } else {
                for (int i = 0; i < keys.size(); i++) {
                    Integer oldId = keys.get(i);
                    Integer newId = newComponentIds.get(i);
                    instructionMap.put(oldId, newId);
                }
            }

            return null;

        } catch (SQLException error) {

            logDB.error("Failed to create saved component_instruction: " + error.getMessage());
            return new ErrorMessage(
                    "Failed to create saved component_instruction",
                    "component_instruction Insertion Failure",
                    error.getMessage());
        }
    }

    public ErrorMessage createCompVariables(BlockDetailsDTO blockDetailsDTO) {

        final int BATCH_SIZE = 100;

        String idsInstruction =
                instructionMap.keySet().stream().map(String::valueOf).collect(Collectors.joining(","));

        String checkExistsSQL = "SELECT id,variable_type AS type,name,"
                + "configured_value AS value,local_format,delimiter,"
                + "producer_instruction_id AS instruction_id"
                + " FROM bot_job_variable_definition"
                + " WHERE bot_job_id = ? AND producer_instruction_id IN ("
                + idsInstruction + ") ORDER BY id";

        String selectComponentIdsSQL = "SELECT id FROM component_variable WHERE home_banking_id = "
                + blockDetailsDTO.getHomeBankingId() + " ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement selectStmt = conn.prepareStatement(checkExistsSQL);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current component_block ids before insert
            List<Integer> blockIdsBefore = new ArrayList<>();
            try (ResultSet rsIdsBefore = idStmtBefore.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsBefore.next()) {
                    blockIdsBefore.add(rsIdsBefore.getInt("id"));
                }
            }

            // Step 2: prepare and execute insert
            selectStmt.setInt(1, blockDetailsDTO.getBotJobId());

            try (ResultSet rsVariable = selectStmt.executeQuery()) {

                String insertSQL =
                        "INSERT INTO component_variable (type, name, value, local_format, delimiter, instruction_id, home_banking_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

                variableMap.clear();

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                    int count = 0;

                    while (rsVariable.next()) {
                        int id = rsVariable.getInt("id");

                        Integer newHomeBankId = blockDetailsDTO.getHomeBankingId();
                        if (newHomeBankId == null) {
                            logDB.info("Skipped component_variable with unknown home_banking_id");
                            continue;
                        }

                        Integer instructionId = rsVariable.getObject("instruction_id") != null
                                ? rsVariable.getInt("instruction_id")
                                : null;

                        Integer newInstructionId = null;

                        if (instructionId != null) {
                            newInstructionId = instructionMap.get(instructionId);
                            if (newInstructionId == null) {
                                logDB.info("Skipped component_variable with unknown instruction_id: " + instructionId);
                                continue;
                            }
                        }

                        variableMap.put(id, -1);

                        insertStmt.setString(1, rsVariable.getString("type"));
                        insertStmt.setString(2, rsVariable.getString("name"));
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
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            conn.commit();
                            logDB.info("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        conn.commit();
                        logDB.info("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get component_block ids after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsIdsAfter = idStmtAfter.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsAfter.next()) {
                    idsAfter.add(rsIdsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs
            List<Integer> newComponentIds = new ArrayList<>(idsAfter);
            newComponentIds.removeAll(blockIdsBefore);

            // You can now use `newComponentIds` as needed
            logDB.info("Newly inserted component_variable IDs: " + newComponentIds);

            // Step 5: update blockMap with new IDs
            List<Integer> keys = new ArrayList<>(variableMap.keySet());

            if (keys.size() != newComponentIds.size()) {
                logDB.error(
                        "Mismatch in size: expected " + keys.size() + " new IDs, but got " + newComponentIds.size());
            } else {
                for (int i = 0; i < keys.size(); i++) {
                    Integer oldId = keys.get(i);
                    Integer newId = newComponentIds.get(i);
                    variableMap.put(oldId, newId);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to create saved component_variable");
            return new ErrorMessage(
                    "Failed to create saved component_variable",
                    "component_variable Insertion Failure",
                    error.getMessage());
        }
    }

    public ErrorMessage createUpdateCompInstruction(BlockDetailsDTO blockDetailsDTO) {
        final int BATCH_SIZE = 100;

        instrNewInverted.clear();

        for (Map.Entry<Integer, Integer> entry : instructionMap.entrySet()) {
            instrNewInverted.put(entry.getValue(), entry.getKey());
        }

        try (Connection conn = getConnection();
                Statement postgresStmt = conn.createStatement()) {
            conn.setAutoCommit(false);

            String idsInstruction =
                    instructionMap.values().stream().map(String::valueOf).collect(Collectors.joining(","));

            String idsBlock = blockMap.values().stream().map(String::valueOf).collect(Collectors.joining(","));

            String selectAccessSQL =
                    "SELECT id, name, parent_id, parent_block_id, variable_id " + "FROM component_instruction "
                            + "WHERE (parent_id IS NOT NULL OR variable_id IS NOT NULL) "
                            + "AND home_banking_id = "
                            + blockDetailsDTO.getHomeBankingId() + " AND id IN ("
                            + idsInstruction + ") " + "AND block_id IN ("
                            + idsBlock + ") " + "ORDER BY id";

            try (ResultSet rsInstruction = postgresStmt.executeQuery(selectAccessSQL)) {

                String updateSQL =
                        "UPDATE component_instruction SET variable_id = ?, parent_block_id = ?, parent_id = ? WHERE id = ?";

                try (PreparedStatement updateStmt = conn.prepareStatement(updateSQL)) {
                    int count = 0;

                    while (rsInstruction.next()) {
                        int id = rsInstruction.getInt("id");
                        String name = rsInstruction.getString("name");

                        // ---- variable_id ----
                        Integer originalOldID = instrNewInverted.get(id);
                        Integer originalVarId = null;

                        if (originalOldID != null) {
                            originalVarId = instrVariablesMap.get(originalOldID);
                        }

                        Integer newVariableId = null;
                        if (originalVarId != null) {
                            newVariableId = variableMap.get(originalVarId);
                        }

                        if (newVariableId == null) {
                            logDB.info("Skipped variable_id column with unknown variable_id: " + newVariableId);
                            updateStmt.setNull(1, Types.INTEGER);
                        } else {
                            updateStmt.setInt(1, newVariableId);
                        }

                        // Handle parent_id or parent_block_id based on name
                        if ("GOTO".equalsIgnoreCase(name) || "EXCEL GOTO".equalsIgnoreCase(name)) {
                            int parentBlockId = rsInstruction.getInt("parent_block_id");
                            if (rsInstruction.wasNull() || !blockMap.containsKey(parentBlockId)) {
                                updateStmt.setNull(2, Types.INTEGER);
                            } else {
                                updateStmt.setInt(2, blockMap.get(parentBlockId));
                            }
                            updateStmt.setNull(3, Types.INTEGER); // Skip parent_id
                        } else {
                            int parentInstructionId = rsInstruction.getInt("parent_id");
                            if (rsInstruction.wasNull() || !instructionMap.containsKey(parentInstructionId)) {
                                updateStmt.setNull(3, Types.INTEGER);
                            } else {
                                updateStmt.setInt(3, instructionMap.get(parentInstructionId));
                            }
                            updateStmt.setNull(2, Types.INTEGER); // Skip parent_block_id
                        }

                        updateStmt.setInt(4, id); // WHERE clause: name = ?

                        updateStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            updateStmt.executeBatch();
                            conn.commit();
                            logDB.info("Updated batch of " + BATCH_SIZE);
                        }
                    }

                    if (count % BATCH_SIZE != 0) {
                        updateStmt.executeBatch();
                        conn.commit();
                        logDB.info("Updated final batch of " + (count % BATCH_SIZE));
                    }

                    logDB.info("Updated component_instruction records: " + count);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to update component_instruction");
            return new ErrorMessage(
                    "Failed to update component_instruction",
                    "component_instruction Update Failure",
                    error.getMessage());
        }
    }

    public ErrorMessage createCompReferences(BlockDetailsDTO blockDetailsDTO) {

        final int BATCH_SIZE = 100;

        String idsInstruction =
                instructionMap.keySet().stream().map(String::valueOf).collect(Collectors.joining(","));

        String checkExistsSQL = "SELECT * FROM reference " + "WHERE  bot_job_id = ? AND instruction_id IN ("
                + idsInstruction + ") ORDER BY id";

        String selectComponentIdsSQL = "SELECT id FROM component_reference WHERE home_banking_id = "
                + blockDetailsDTO.getHomeBankingId() + " ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement selectStmt = conn.prepareStatement(checkExistsSQL);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current component_block ids before insert
            List<Integer> blockIdsBefore = new ArrayList<>();
            try (ResultSet rsIdsBefore = idStmtBefore.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsBefore.next()) {
                    blockIdsBefore.add(rsIdsBefore.getInt("id"));
                }
            }

            // Step 2: prepare and execute insert
            selectStmt.setInt(1, blockDetailsDTO.getBotJobId());

            try (ResultSet rsReference = selectStmt.executeQuery()) {

                String insertSQL =
                        "INSERT INTO component_reference ( reference_type, value, instruction_id, home_banking_id) VALUES (?, ?, ?, ?)";

                referenceMap.clear();

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                    int count = 0;

                    while (rsReference.next()) {
                        int id = rsReference.getInt("id");

                        int oldInstructionId = rsReference.getInt("instruction_id");
                        Integer newInstructionId = instructionMap.get(oldInstructionId);
                        if (newInstructionId == null) {
                            logDB.info("Skipped component_reference with unknown instruction_id: " + oldInstructionId);
                            continue;
                        }

                        Integer newHomeBankId = blockDetailsDTO.getHomeBankingId();
                        if (newHomeBankId == null) {
                            logDB.info("Skipped component_reference with unknown home_banking_id: " + newHomeBankId);
                            continue;
                        }

                        referenceMap.put(id, -1);

                        insertStmt.setString(1, rsReference.getString("reference_type"));
                        insertStmt.setString(2, rsReference.getString("value"));
                        insertStmt.setInt(3, newInstructionId);

                        if (newHomeBankId != null) {
                            insertStmt.setInt(4, newHomeBankId);
                        } else {
                            insertStmt.setNull(4, Types.INTEGER);
                        }

                        insertStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            conn.commit();
                            logDB.info("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        conn.commit();
                        logDB.info("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get component_block ids after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsIdsAfter = idStmtAfter.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsAfter.next()) {
                    idsAfter.add(rsIdsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs
            List<Integer> newComponentIds = new ArrayList<>(idsAfter);
            newComponentIds.removeAll(blockIdsBefore);

            // You can now use `newComponentIds` as needed
            logDB.info("Newly inserted component_reference IDs: " + newComponentIds);

            // Step 5: update blockMap with new IDs
            List<Integer> keys = new ArrayList<>(referenceMap.keySet());

            if (keys.size() != newComponentIds.size()) {
                logDB.error(
                        "Mismatch in size: expected " + keys.size() + " new IDs, but got " + newComponentIds.size());
            } else {
                for (int i = 0; i < keys.size(); i++) {
                    Integer oldId = keys.get(i);
                    Integer newId = newComponentIds.get(i);
                    referenceMap.put(oldId, newId);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to create saved component_reference");
            return new ErrorMessage(
                    "Failed to create saved component_reference",
                    "component_reference Insertion Failure",
                    error.getMessage());
        }
    }

    // INJECT BACK SAVED COMPONENTS
    public ErrorMessage createInjectBlock(BlockDetailsDTO blockDetailsDTO) {

        final int BATCH_SIZE = 100;
        String checkExistsSQL =
                "SELECT id, block_order_number, name, description, type_id, export_file, active, wait, home_banking_id "
                        + "FROM component_block WHERE home_banking_id = ? AND id = ? ORDER BY id";

        String selectComponentIdsSQL =
                "SELECT id FROM block WHERE bot_job_id = " + blockDetailsDTO.getBotJobId() + " ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement selectStmt = conn.prepareStatement(checkExistsSQL);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current block ids before insert
            List<Integer> blockIdsBefore = new ArrayList<>();
            try (ResultSet rsIdsBefore = idStmtBefore.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsBefore.next()) {
                    blockIdsBefore.add(rsIdsBefore.getInt("id"));
                }
            }

            // Step 2: prepare and execute insert
            selectStmt.setInt(1, blockDetailsDTO.getHomeBankingId());
            selectStmt.setInt(2, blockDetailsDTO.getBlockId());

            try (ResultSet rs = selectStmt.executeQuery()) {

                String insertSQL = "INSERT INTO block "
                        + "(block_order_number, name, description, type_id, export_file, active, wait, bot_job_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

                blockMap.clear();

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                    int count = 0;

                    while (rs.next()) {
                        int id = rs.getInt("id");
                        //                        int blockOrderNumber = rs.getInt("block_order_number");
                        Integer typeId = rs.getObject("type_id") != null ? rs.getInt("type_id") : null;
                        String exportFile = rs.getString("export_file");
                        int active = rs.getInt("active");
                        Integer wait = rs.getObject("wait") != null ? rs.getInt("wait") : null;

                        Integer newBotJobId = blockDetailsDTO.getBotJobId();
                        if (newBotJobId == null) {
                            logDB.info("Skipped block with unknown bot_job_id");
                            continue;
                        }

                        blockMap.put(id, -1);

                        insertStmt.setInt(1, blockDetailsDTO.getBlockOrderNumber());
                        insertStmt.setString(2, blockDetailsDTO.getBlockName());
                        insertStmt.setString(3, blockDetailsDTO.getBlockDescription());
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

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            conn.commit();
                            logDB.info("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        conn.commit();
                        logDB.info("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get block ids after insert
            idsBlockAfter.clear();
            try (ResultSet rsIdsAfter = idStmtAfter.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsAfter.next()) {
                    idsBlockAfter.add(rsIdsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs
            List<Integer> newComponentIds = new ArrayList<>(idsBlockAfter);
            newComponentIds.removeAll(blockIdsBefore);

            // You can now use `newComponentIds` as needed
            logDB.info("Newly inserted block IDs: " + newComponentIds);

            // Step 5: update blockMap with new IDs
            List<Integer> keys = new ArrayList<>(blockMap.keySet());

            if (keys.size() != newComponentIds.size()) {
                logDB.error(
                        "Mismatch in size: expected " + keys.size() + " new IDs, but got " + newComponentIds.size());
            } else {
                for (int i = 0; i < keys.size(); i++) {
                    Integer oldId = keys.get(i);
                    Integer newId = newComponentIds.get(i);
                    blockMap.put(oldId, newId);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to create saved block");
            return new ErrorMessage("Failed to create saved block", "block Insertion Failure", error.getMessage());
        }
    }

    public ErrorMessage createInjectInstructions(BlockDetailsDTO blockDetailsDTO) {

        final int BATCH_SIZE = 100;

        String checkExistsSQL =
                "SELECT * FROM component_instruction WHERE home_banking_id = ? AND block_id = ? order by id";

        String selectComponentIdsSQL =
                "SELECT id FROM instruction WHERE bot_job_id = " + blockDetailsDTO.getBotJobId() + " ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement selectStmt = conn.prepareStatement(checkExistsSQL);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current component_block ids before insert
            List<Integer> blockIdsBefore = new ArrayList<>();
            try (ResultSet rsIdsBefore = idStmtBefore.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsBefore.next()) {
                    blockIdsBefore.add(rsIdsBefore.getInt("id"));
                }
            }

            // Step 2: prepare and execute insert
            selectStmt.setInt(1, blockDetailsDTO.getHomeBankingId());
            selectStmt.setInt(2, blockDetailsDTO.getBlockId());

            try (ResultSet rsInstruction = selectStmt.executeQuery()) {

                String insertSQL = "INSERT INTO instruction ("
                        + "instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, block_id, variable_id, parent_id, bot_job_id, client_named) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                instructionMap.clear();
                instrVariablesMap.clear();

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                    int count = 0;

                    while (rsInstruction.next()) {
                        int id = rsInstruction.getInt("id");
                        Integer newBotJobId = blockDetailsDTO.getBotJobId();
                        if (newBotJobId == null) {
                            logDB.info("Skipped instruction with unknown bot_job_id");
                            continue;
                        }

                        int oldBlockId = rsInstruction.getInt("block_id");
                        Integer newBlockId = blockMap.get(oldBlockId);
                        if (newBlockId == null) {
                            logDB.info("Skipped instruction with unknown block_id: " + oldBlockId);
                            continue;
                        }

                        instructionMap.put(id, -1);

                        insertStmt.setInt(1, rsInstruction.getInt("instruction_order_number"));
                        insertStmt.setString(2, rsInstruction.getString("actions"));
                        insertStmt.setString(3, rsInstruction.getString("name"));
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

                        // variable_id + tracking
                        Integer variableId = rsInstruction.getInt("variable_id");
                        if (rsInstruction.wasNull()) {
                            variableId = null;
                        }

                        if (variableId != null) {
                            instrVariablesMap.put(id, variableId);
                        }
                        insertStmt.setNull(23, Types.INTEGER); // TO BE SOLVED LATER

                        insertOrNull(insertStmt, 24, rsInstruction, "parent_id");

                        insertStmt.setInt(25, newBotJobId);
                        insertStmt.setString(26, rsInstruction.getString("client_named"));

                        insertStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            conn.commit();
                            logDB.info("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        conn.commit();
                        logDB.info("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get component_block ids after insert
            List<Integer> blockIdsAfter = new ArrayList<>();
            try (ResultSet rsIdsAfter = idStmtAfter.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsAfter.next()) {
                    blockIdsAfter.add(rsIdsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs
            List<Integer> newComponentIds = new ArrayList<>(blockIdsAfter);
            newComponentIds.removeAll(blockIdsBefore);

            // You can now use `newComponentIds` as needed
            logDB.info("Newly inserted instruction IDs: " + newComponentIds);

            // Step 5: update blockMap with new IDs
            List<Integer> keys = new ArrayList<>(instructionMap.keySet());

            if (keys.size() != newComponentIds.size()) {
                logDB.error(
                        "Mismatch in size: expected " + keys.size() + " new IDs, but got " + newComponentIds.size());
            } else {
                for (int i = 0; i < keys.size(); i++) {
                    Integer oldId = keys.get(i);
                    Integer newId = newComponentIds.get(i);
                    instructionMap.put(oldId, newId);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to create saved instruction");
            return new ErrorMessage(
                    "Failed to create saved instruction", "instruction Insertion Failure", error.getMessage());
        }
    }

    public ErrorMessage createInjectVariables(BlockDetailsDTO blockDetailsDTO) {
        if (usesDurableBotJobVariables()) {
            return createInjectVariablesDurable(blockDetailsDTO);
        }

        final int BATCH_SIZE = 100;

        String idsInstruction =
                instructionMap.keySet().stream().map(String::valueOf).collect(Collectors.joining(","));

        String checkExistsSQL = "SELECT * FROM component_variable "
                + "WHERE  home_banking_id = ? AND instruction_id IN (" + idsInstruction + ") ORDER BY id";

        String selectComponentIdsSQL =
                "SELECT id FROM variable WHERE bot_job_id = " + blockDetailsDTO.getBotJobId() + " ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement selectStmt = conn.prepareStatement(checkExistsSQL);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current component_block ids before insert
            List<Integer> blockIdsBefore = new ArrayList<>();
            try (ResultSet rsIdsBefore = idStmtBefore.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsBefore.next()) {
                    blockIdsBefore.add(rsIdsBefore.getInt("id"));
                }
            }

            // Step 2: prepare and execute insert
            selectStmt.setInt(1, blockDetailsDTO.getHomeBankingId());

            try (ResultSet rsVariable = selectStmt.executeQuery()) {

                String insertSQL =
                        "INSERT INTO variable (type, name, value, local_format, delimiter, instruction_id, bot_job_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

                variableMap.clear();

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                    int count = 0;

                    while (rsVariable.next()) {
                        int id = rsVariable.getInt("id");

                        Integer newBotJobId = blockDetailsDTO.getBotJobId();
                        if (newBotJobId == null) {
                            logDB.info("Skipped variable with unknown bot_job_id");
                            continue;
                        }

                        Integer instructionId = rsVariable.getObject("instruction_id") != null
                                ? rsVariable.getInt("instruction_id")
                                : null;

                        Integer newInstructionId = null;

                        if (instructionId != null) {
                            newInstructionId = instructionMap.get(instructionId);
                            if (newInstructionId == null) {
                                logDB.info("Skipped variable with unknown instruction_id: " + instructionId);
                                continue;
                            }
                        }

                        variableMap.put(id, -1);

                        insertStmt.setString(1, rsVariable.getString("type"));
                        insertStmt.setString(2, rsVariable.getString("name"));
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

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            conn.commit();
                            logDB.info("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        conn.commit();
                        logDB.info("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get component_block ids after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsIdsAfter = idStmtAfter.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsAfter.next()) {
                    idsAfter.add(rsIdsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs
            List<Integer> newComponentIds = new ArrayList<>(idsAfter);
            newComponentIds.removeAll(blockIdsBefore);

            // You can now use `newComponentIds` as needed
            logDB.info("Newly inserted variable IDs: " + newComponentIds);

            // Step 5: update blockMap with new IDs
            List<Integer> keys = new ArrayList<>(variableMap.keySet());

            if (keys.size() != newComponentIds.size()) {
                logDB.error(
                        "Mismatch in size: expected " + keys.size() + " new IDs, but got " + newComponentIds.size());
            } else {
                for (int i = 0; i < keys.size(); i++) {
                    Integer oldId = keys.get(i);
                    Integer newId = newComponentIds.get(i);
                    variableMap.put(oldId, newId);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to create saved variable");
            return new ErrorMessage(
                    "Failed to create saved variable", "variable Insertion Failure", error.getMessage());
        }
    }

    public ErrorMessage createUpdateInjectInstruction(BlockDetailsDTO blockDetailsDTO) {
        final int BATCH_SIZE = 100;

        instrNewInverted.clear();

        for (Map.Entry<Integer, Integer> entry : instructionMap.entrySet()) {
            instrNewInverted.put(entry.getValue(), entry.getKey());
        }

        try (Connection conn = getConnection();
                Statement postgresStmt = conn.createStatement()) {
            conn.setAutoCommit(false);

            String idsInstruction =
                    instructionMap.values().stream().map(String::valueOf).collect(Collectors.joining(","));

            String idsBlock = blockMap.values().stream().map(String::valueOf).collect(Collectors.joining(","));

            String selectAccessSQL = "SELECT id, name, parent_id, parent_block_id, variable_id " + "FROM instruction "
                    + "WHERE (parent_block_id IS NOT NULL OR parent_id IS NOT NULL OR variable_id IS NOT NULL) "
                    + "AND bot_job_id = "
                    + blockDetailsDTO.getBotJobId() + " AND id IN ("
                    + idsInstruction + ") " + "AND block_id IN ("
                    + idsBlock + ") " + "ORDER BY id";

            try (ResultSet rsInstruction = postgresStmt.executeQuery(selectAccessSQL)) {

                String updateSQL =
                        "UPDATE instruction SET variable_id = ?, parent_block_id = ?, parent_id = ? WHERE id = ?";

                try (PreparedStatement updateStmt = conn.prepareStatement(updateSQL)) {
                    int count = 0;

                    while (rsInstruction.next()) {
                        int id = rsInstruction.getInt("id");
                        String name = rsInstruction.getString("name");

                        // ---- variable_id ----
                        Integer originalOldID = instrNewInverted.get(id);
                        Integer originalVarId = null;

                        if (originalOldID != null) {
                            originalVarId = instrVariablesMap.get(originalOldID);
                        }

                        Integer newVariableId = null;
                        if (originalVarId != null) {
                            newVariableId = variableMap.get(originalVarId);
                        }

                        if (newVariableId == null) {
                            logDB.info("Skipped variable_id column with unknown variable_id: " + newVariableId);
                            updateStmt.setNull(1, Types.INTEGER);
                        } else {
                            updateStmt.setInt(1, newVariableId);
                        }

                        // Handle parent_id or parent_block_id based on name
                        if ("GOTO".equalsIgnoreCase(name) || "EXCEL GOTO".equalsIgnoreCase(name)) {
                            int parentBlockId = rsInstruction.getInt("parent_block_id");
                            if (rsInstruction.wasNull() || !blockMap.containsKey(parentBlockId)) {
                                updateStmt.setNull(2, Types.INTEGER);
                            } else {
                                updateStmt.setInt(2, blockMap.get(parentBlockId));
                            }
                            updateStmt.setNull(3, Types.INTEGER); // Skip parent_id
                        } else {
                            int parentInstructionId = rsInstruction.getInt("parent_id");
                            if (rsInstruction.wasNull() || !instructionMap.containsKey(parentInstructionId)) {
                                updateStmt.setNull(3, Types.INTEGER);
                            } else {
                                updateStmt.setInt(3, instructionMap.get(parentInstructionId));
                            }
                            updateStmt.setNull(2, Types.INTEGER); // Skip parent_block_id
                        }

                        updateStmt.setInt(4, id); // WHERE clause: name = ?

                        updateStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            updateStmt.executeBatch();
                            conn.commit();
                            logDB.info("Updated batch of " + BATCH_SIZE);
                        }
                    }

                    if (count % BATCH_SIZE != 0) {
                        updateStmt.executeBatch();
                        conn.commit();
                        logDB.info("Updated final batch of " + (count % BATCH_SIZE));
                    }

                    logDB.info("Updated instruction records: " + count);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to update instruction");
            return new ErrorMessage("Failed to update instruction", "instruction Update Failure", error.getMessage());
        }
    }

    private ErrorMessage createInjectVariablesDurable(BlockDetailsDTO blockDetailsDTO) {
        if (blockDetailsDTO == null
                || blockDetailsDTO.getHomeBankingId() == null
                || blockDetailsDTO.getBotJobId() == null) {
            return new ErrorMessage(
                    "Variable Insertion Error",
                    "The durable Bot Job variable owner is required.",
                    null);
        }
        String ids = instructionMap.keySet().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        if (ids.isBlank()) {
            variableMap.clear();
            return null;
        }
        String select = "SELECT id,type,name,value,local_format,delimiter,instruction_id"
                + " FROM component_variable WHERE home_banking_id=?"
                + " AND instruction_id IN (" + ids + ") ORDER BY id";
        try (Connection connection = getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(select)) {
                statement.setInt(1, blockDetailsDTO.getHomeBankingId());
                variableMap.clear();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        Integer sourceInstructionId =
                                nullableInt(rows, "instruction_id");
                        Integer generatedInstructionId =
                                sourceInstructionId == null
                                        ? null
                                        : instructionMap.get(sourceInstructionId);
                        if (sourceInstructionId != null
                                && generatedInstructionId == null) {
                            continue;
                        }
                        MutationResult created = botJobRuntimeVariables.createDefinition(
                                connection,
                                new OwnerKey(
                                        blockDetailsDTO.getHomeBankingId(),
                                        blockDetailsDTO.getBotJobId()),
                                new DefinitionDraft(
                                        rows.getString("type"),
                                        rows.getString("name"),
                                        rows.getString("value"),
                                        rows.getString("local_format"),
                                        rows.getString("delimiter"),
                                        generatedInstructionId == null
                                                ? null
                                                : generatedInstructionId.longValue(),
                                        ValueState.VOID,
                                        null),
                                null);
                        if (!created.applied() || created.definition() == null) {
                            throw new SQLException(created.message());
                        }
                        variableMap.put(
                                rows.getInt("id"),
                                Math.toIntExact(created.definition().id()));
                    }
                }
                connection.commit();
                connection.setAutoCommit(previousAutoCommit);
                return null;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                connection.setAutoCommit(previousAutoCommit);
                throw failure;
            }
        } catch (SQLException | RuntimeException failure) {
            return new ErrorMessage(
                    "Failed to create saved variable",
                    "Durable variable insertion failure",
                    failure.getMessage());
        }
    }

    private static Integer nullableInt(ResultSet rows, String column)
            throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    private static boolean usesDurableBotJobVariables() {
        return true;
    }

    public ErrorMessage createInjectReferences(BlockDetailsDTO blockDetailsDTO) {

        final int BATCH_SIZE = 100;

        String idsInstruction =
                instructionMap.keySet().stream().map(String::valueOf).collect(Collectors.joining(","));

        String checkExistsSQL = "SELECT * FROM component_reference "
                + "WHERE  home_banking_id = ? AND instruction_id IN (" + idsInstruction + ") ORDER BY id";

        String selectComponentIdsSQL =
                "SELECT id FROM reference WHERE bot_job_id = " + blockDetailsDTO.getBotJobId() + " ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement selectStmt = conn.prepareStatement(checkExistsSQL);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current component_block ids before insert
            List<Integer> blockIdsBefore = new ArrayList<>();
            try (ResultSet rsIdsBefore = idStmtBefore.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsBefore.next()) {
                    blockIdsBefore.add(rsIdsBefore.getInt("id"));
                }
            }

            // Step 2: prepare and execute insert
            selectStmt.setInt(1, blockDetailsDTO.getHomeBankingId());

            try (ResultSet rsReference = selectStmt.executeQuery()) {

                String insertSQL =
                        "INSERT INTO reference (reference_type, value, instruction_id, bot_job_id) VALUES (?, ?, ?, ?)";

                referenceMap.clear();

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                    int count = 0;

                    while (rsReference.next()) {
                        int id = rsReference.getInt("id");

                        int oldInstructionId = rsReference.getInt("instruction_id");
                        Integer newInstructionId = instructionMap.get(oldInstructionId);
                        if (newInstructionId == null) {
                            logDB.info("Skipped reference with unknown instruction_id: " + oldInstructionId);
                            continue;
                        }

                        Integer newBotJobId = blockDetailsDTO.getBotJobId();
                        if (newBotJobId == null) {
                            logDB.info("Skipped reference with unknown bot_job_id: " + newBotJobId);
                            continue;
                        }

                        referenceMap.put(id, -1);

                        insertStmt.setString(1, rsReference.getString("reference_type"));
                        insertStmt.setString(2, rsReference.getString("value"));
                        insertStmt.setInt(3, newInstructionId);

                        if (newBotJobId != null) {
                            insertStmt.setInt(4, newBotJobId);
                        } else {
                            insertStmt.setNull(4, Types.INTEGER);
                        }

                        insertStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            conn.commit();
                            logDB.info("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        conn.commit();
                        logDB.info("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get component_block ids after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsIdsAfter = idStmtAfter.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsAfter.next()) {
                    idsAfter.add(rsIdsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs
            List<Integer> newComponentIds = new ArrayList<>(idsAfter);
            newComponentIds.removeAll(blockIdsBefore);

            // You can now use `newComponentIds` as needed
            logDB.info("Newly inserted reference IDs: " + newComponentIds);

            // Step 5: update blockMap with new IDs
            List<Integer> keys = new ArrayList<>(referenceMap.keySet());

            if (keys.size() != newComponentIds.size()) {
                logDB.error(
                        "Mismatch in size: expected " + keys.size() + " new IDs, but got " + newComponentIds.size());
            } else {
                for (int i = 0; i < keys.size(); i++) {
                    Integer oldId = keys.get(i);
                    Integer newId = newComponentIds.get(i);
                    referenceMap.put(oldId, newId);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to create saved reference");
            return new ErrorMessage(
                    "Failed to create saved reference", "reference Insertion Failure", error.getMessage());
        }
    }

    // CLONE BOT JOB
    public Integer getNewBotBojId(int previousBotJob) {
        return botJobMap.get(previousBotJob);
    }

    public ErrorMessage cloneBotJob(
            HomeUrlDTO homeUrlDTO,
            int previousBotJob,
            String newBotJobName,
            String newDescription,
            String priority) {

        String selectComponentIdsSQL =
                "SELECT id FROM bot_job WHERE home_banking_id = " + homeUrlDTO.getHomeBankingId() + " ORDER BY id";

        try (Connection conn = getConnection();
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current component_block ids before insert
            List<Integer> blockIdsBefore = new ArrayList<>();
            try (ResultSet rsIdsBefore = idStmtBefore.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsBefore.next()) {
                    blockIdsBefore.add(rsIdsBefore.getInt("id"));
                }
            }

            String insertSQL =
                    "INSERT INTO bot_job (name, description, priority, home_banking_id, home_url_id, active) "
                            + "SELECT ?, ?, ?, ?, ?, ? FROM bot_job WHERE id = ?";

            botJobMap.clear();
            botJobMap.put(previousBotJob, -1);

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                insertStmt.setString(1, newBotJobName);
                insertStmt.setString(2, newDescription);
                insertStmt.setString(3, priority);
                insertStmt.setInt(4, homeUrlDTO.getHomeBankingId());
                insertStmt.setInt(5, homeUrlDTO.getId());
                insertStmt.setInt(6, 1);
                insertStmt.setInt(7, previousBotJob);

                insertStmt.addBatch();
                insertStmt.executeBatch();
                conn.commit();
                logDB.info("Inserted bot job record: 1");
            }

            // Step 3: get component_block ids after insert
            List<Integer> blockIdsAfter = new ArrayList<>();
            try (ResultSet rsIdsAfter = idStmtAfter.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsAfter.next()) {
                    blockIdsAfter.add(rsIdsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs
            List<Integer> newComponentIds = new ArrayList<>(blockIdsAfter);
            newComponentIds.removeAll(blockIdsBefore);

            // You can now use `newComponentIds` as needed
            logDB.info("Newly inserted bot_job IDs: " + newComponentIds);

            // Step 5: update blockMap with new IDs
            List<Integer> keys = new ArrayList<>(botJobMap.keySet());

            if (keys.size() != newComponentIds.size()) {
                logDB.error(
                        "Mismatch in size: expected " + keys.size() + " new IDs, but got " + newComponentIds.size());
            } else {
                for (int i = 0; i < keys.size(); i++) {
                    Integer oldId = keys.get(i);
                    Integer newId = newComponentIds.get(i);
                    botJobMap.put(oldId, newId);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to clone bot_job");
            return new ErrorMessage("Failed to clone bot_job", "bot_job Insertion Failure", error.getMessage());
        }
    }

    public ErrorMessage cloneBlock(int previousBotJob) {

        final int BATCH_SIZE = 100;
        String checkExistsSQL =
                "SELECT id, block_order_number, name, description, type_id, export_file, active, wait, bot_job_id "
                        + "FROM block WHERE bot_job_id = ? ORDER BY id";

        String selectComponentIdsSQL = "SELECT id FROM block ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement selectStmt = conn.prepareStatement(checkExistsSQL);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current component_block ids before insert
            List<Integer> blockIdsBefore = new ArrayList<>();
            try (ResultSet rsIdsBefore = idStmtBefore.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsBefore.next()) {
                    blockIdsBefore.add(rsIdsBefore.getInt("id"));
                }
            }

            // Step 2: prepare and execute insert
            selectStmt.setInt(1, previousBotJob);
            try (ResultSet rs = selectStmt.executeQuery()) {

                String insertSQL = "INSERT INTO block "
                        + "( block_order_number, name, description, type_id, export_file, active, wait, bot_job_id) "
                        + "VALUES ( ?, ?, ?, ?, ?, ?, ?, ?)";

                blockMap.clear();

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                    int count = 0;

                    while (rs.next()) {
                        int id = rs.getInt("id");
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
                            logDB.info("Skipped block with unknown bot_job_id: " + newBotJobId);
                            continue;
                        }

                        blockMap.put(id, -1);

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

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            conn.commit();
                            logDB.info("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        conn.commit();
                        logDB.info("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get component_block ids after insert
            List<Integer> blockIdsAfter = new ArrayList<>();
            try (ResultSet rsIdsAfter = idStmtAfter.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsAfter.next()) {
                    blockIdsAfter.add(rsIdsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs
            List<Integer> newComponentIds = new ArrayList<>(blockIdsAfter);
            newComponentIds.removeAll(blockIdsBefore);

            // You can now use `newComponentIds` as needed
            logDB.info("Newly inserted block IDs: " + newComponentIds);

            // Step 5: update blockMap with new IDs
            List<Integer> keys = new ArrayList<>(blockMap.keySet());

            if (keys.size() != newComponentIds.size()) {
                logDB.error(
                        "Mismatch in size: expected " + keys.size() + " new IDs, but got " + newComponentIds.size());
            } else {
                for (int i = 0; i < keys.size(); i++) {
                    Integer oldId = keys.get(i);
                    Integer newId = newComponentIds.get(i);
                    blockMap.put(oldId, newId);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to clone block");
            return new ErrorMessage("Failed to clone block", "block Insertion Failure", error.getMessage());
        }
    }

    public ErrorMessage cloneInstructions(int previousBotJob) {

        final int BATCH_SIZE = 100;

        String checkExistsSQL = "SELECT * FROM instruction WHERE bot_job_id = ? order by id";

        String selectComponentIdsSQL = "SELECT id FROM instruction ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement selectStmt = conn.prepareStatement(checkExistsSQL);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current component_block ids before insert
            List<Integer> blockIdsBefore = new ArrayList<>();
            try (ResultSet rsIdsBefore = idStmtBefore.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsBefore.next()) {
                    blockIdsBefore.add(rsIdsBefore.getInt("id"));
                }
            }

            // Step 2: prepare and execute insert
            selectStmt.setInt(1, previousBotJob);

            try (ResultSet rsInstruction = selectStmt.executeQuery()) {

                String insertSQL = "INSERT INTO instruction ("
                        + " instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, block_id, variable_id, parent_id, bot_job_id, client_named) "
                        + "VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                instructionMap.clear();
                instrVariablesMap.clear();

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                    int count = 0;

                    while (rsInstruction.next()) {
                        int id = rsInstruction.getInt("id");

                        int oldBlockId = rsInstruction.getInt("block_id");
                        Integer newBlockId = blockMap.get(oldBlockId);
                        if (newBlockId == null) {
                            logDB.info("Skipped instruction with unknown block_id: " + oldBlockId);
                            continue;
                        }

                        int oldBotJobId = rsInstruction.getInt("bot_job_id");
                        Integer newBotJobId = botJobMap.get(oldBotJobId);
                        if (newBotJobId == null) {
                            logDB.info("Skipped instruction with unknown bot_job_id: " + newBotJobId);
                            continue;
                        }

                        instructionMap.put(id, -1);

                        insertStmt.setInt(1, rsInstruction.getInt("instruction_order_number"));
                        insertStmt.setString(2, rsInstruction.getString("actions"));
                        insertStmt.setString(3, rsInstruction.getString("name"));
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

                        // variable_id + tracking
                        Integer variableId = rsInstruction.getInt("variable_id");
                        if (rsInstruction.wasNull()) {
                            variableId = null;
                        }

                        if (variableId != null) {
                            instrVariablesMap.put(id, variableId);
                        }
                        insertStmt.setNull(23, Types.INTEGER); // TO BE SOLVED LATER

                        insertOrNull(insertStmt, 24, rsInstruction, "parent_id");

                        insertStmt.setInt(25, newBotJobId);
                        insertStmt.setString(26, rsInstruction.getString("client_named"));

                        insertStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            conn.commit();
                            logDB.info("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        conn.commit();
                        logDB.info("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get component_block ids after insert
            List<Integer> blockIdsAfter = new ArrayList<>();
            try (ResultSet rsIdsAfter = idStmtAfter.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsAfter.next()) {
                    blockIdsAfter.add(rsIdsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs
            List<Integer> newComponentIds = new ArrayList<>(blockIdsAfter);
            newComponentIds.removeAll(blockIdsBefore);

            // You can now use `newComponentIds` as needed
            logDB.info("Newly inserted instruction IDs: " + newComponentIds);

            // Step 5: update blockMap with new IDs
            List<Integer> keys = new ArrayList<>(instructionMap.keySet());

            if (keys.size() != newComponentIds.size()) {
                logDB.error(
                        "Mismatch in size: expected " + keys.size() + " new IDs, but got " + newComponentIds.size());
            } else {
                for (int i = 0; i < keys.size(); i++) {
                    Integer oldId = keys.get(i);
                    Integer newId = newComponentIds.get(i);
                    instructionMap.put(oldId, newId);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to clono instruction");
            return new ErrorMessage("Failed to clone instruction", "instruction Insertion Failure", error.getMessage());
        }
    }

    public ErrorMessage cloneVariables(int previousBotJob) {
        if (usesDurableBotJobVariables()) {
            return cloneVariablesDurable(previousBotJob);
        }

        final int BATCH_SIZE = 100;

        String checkExistsSQL = "SELECT * FROM variable WHERE bot_job_id = ? order by id";

        String selectComponentIdsSQL = "SELECT id FROM variable ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement selectStmt = conn.prepareStatement(checkExistsSQL);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current component_block ids before insert
            List<Integer> blockIdsBefore = new ArrayList<>();
            try (ResultSet rsIdsBefore = idStmtBefore.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsBefore.next()) {
                    blockIdsBefore.add(rsIdsBefore.getInt("id"));
                }
            }

            // Step 2: prepare and execute insert
            selectStmt.setInt(1, previousBotJob);

            try (ResultSet rsVariable = selectStmt.executeQuery()) {

                String insertSQL =
                        "INSERT INTO variable ( type, name, value, local_format, delimiter, instruction_id, bot_job_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

                variableMap.clear();

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                    int count = 0;

                    while (rsVariable.next()) {
                        int id = rsVariable.getInt("id");

                        int oldBotJobId = rsVariable.getInt("bot_job_id");
                        Integer newBotJobId = botJobMap.get(oldBotJobId);
                        if (newBotJobId == null) {
                            logDB.info("Skipped variable with unknown bot_job_id: " + newBotJobId);
                            continue;
                        }

                        Integer instructionId = rsVariable.getObject("instruction_id") != null
                                ? rsVariable.getInt("instruction_id")
                                : null;

                        Integer newInstructionId = null;

                        if (instructionId != null) {
                            newInstructionId = instructionMap.get(instructionId);
                            if (newInstructionId == null) {
                                logDB.info("Skipped variable with unknown instruction_id: " + instructionId);
                                continue;
                            }
                        }

                        variableMap.put(id, -1);

                        insertStmt.setString(1, rsVariable.getString("type"));
                        insertStmt.setString(2, rsVariable.getString("name"));
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

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            conn.commit();
                            logDB.info("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        conn.commit();
                        logDB.info("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get component_block ids after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsIdsAfter = idStmtAfter.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsAfter.next()) {
                    idsAfter.add(rsIdsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs
            List<Integer> newComponentIds = new ArrayList<>(idsAfter);
            newComponentIds.removeAll(blockIdsBefore);

            // You can now use `newComponentIds` as needed
            logDB.info("Newly inserted variable IDs: " + newComponentIds);

            // Step 5: update blockMap with new IDs
            List<Integer> keys = new ArrayList<>(variableMap.keySet());

            if (keys.size() != newComponentIds.size()) {
                logDB.error(
                        "Mismatch in size: expected " + keys.size() + " new IDs, but got " + newComponentIds.size());
            } else {
                for (int i = 0; i < keys.size(); i++) {
                    Integer oldId = keys.get(i);
                    Integer newId = newComponentIds.get(i);
                    variableMap.put(oldId, newId);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to clone variable");
            return new ErrorMessage("Failed to clone variable", "variable Insertion Failure", error.getMessage());
        }
    }

    public ErrorMessage cloneUpdateInstruction(int previosBotJob) {
        final int BATCH_SIZE = 100;

        instrNewInverted.clear();

        for (Map.Entry<Integer, Integer> entry : instructionMap.entrySet()) {
            instrNewInverted.put(entry.getValue(), entry.getKey());
        }

        try (Connection conn = getConnection();
                Statement postgresStmt = conn.createStatement()) {
            conn.setAutoCommit(false);

            String idsInstruction =
                    instructionMap.values().stream().map(String::valueOf).collect(Collectors.joining(","));

            String idsBlock = blockMap.values().stream().map(String::valueOf).collect(Collectors.joining(","));

            int newBotJobId = botJobMap.get(previosBotJob);

            String selectAccessSQL = "SELECT id, name, parent_id, parent_block_id, variable_id " + "FROM instruction "
                    + "WHERE (parent_block_id IS NOT NULL OR parent_id IS NOT NULL OR variable_id IS NOT NULL) "
                    + "AND bot_job_id = "
                    + newBotJobId + " AND id IN ("
                    + idsInstruction + ") " + "AND block_id IN ("
                    + idsBlock + ") " + "ORDER BY id";

            try (ResultSet rsInstruction = postgresStmt.executeQuery(selectAccessSQL)) {

                String updateSQL =
                        "UPDATE instruction SET variable_id = ?, parent_block_id = ?, parent_id = ? WHERE id = ?";

                try (PreparedStatement updateStmt = conn.prepareStatement(updateSQL)) {
                    int count = 0;

                    while (rsInstruction.next()) {
                        int id = rsInstruction.getInt("id");
                        String name = rsInstruction.getString("name");

                        // ---- variable_id ----
                        Integer originalOldID = instrNewInverted.get(id);
                        Integer originalVarId = null;

                        if (originalOldID != null) {
                            originalVarId = instrVariablesMap.get(originalOldID);
                        }

                        Integer newVariableId = null;
                        if (originalVarId != null) {
                            newVariableId = variableMap.get(originalVarId);
                        }

                        if (newVariableId == null) {
                            logDB.info("Skipped variable_id column with unknown variable_id: " + newVariableId);
                            updateStmt.setNull(1, Types.INTEGER);
                        } else {
                            updateStmt.setInt(1, newVariableId);
                        }

                        // Handle parent_id or parent_block_id based on name
                        if ("GOTO".equalsIgnoreCase(name) || "EXCEL GOTO".equalsIgnoreCase(name)) {
                            int parentBlockId = rsInstruction.getInt("parent_block_id");
                            if (rsInstruction.wasNull() || !blockMap.containsKey(parentBlockId)) {
                                updateStmt.setNull(2, Types.INTEGER);
                            } else {
                                updateStmt.setInt(2, blockMap.get(parentBlockId));
                            }
                            updateStmt.setNull(3, Types.INTEGER); // Skip parent_id
                        } else {
                            int parentInstructionId = rsInstruction.getInt("parent_id");
                            if (rsInstruction.wasNull() || !instructionMap.containsKey(parentInstructionId)) {
                                updateStmt.setNull(3, Types.INTEGER);
                            } else {
                                updateStmt.setInt(3, instructionMap.get(parentInstructionId));
                            }
                            updateStmt.setNull(2, Types.INTEGER); // Skip parent_block_id
                        }

                        updateStmt.setInt(4, id); // WHERE clause: name = ?

                        updateStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            updateStmt.executeBatch();
                            conn.commit();
                            logDB.info("Updated batch of " + BATCH_SIZE);
                        }
                    }

                    if (count % BATCH_SIZE != 0) {
                        updateStmt.executeBatch();
                        conn.commit();
                        logDB.info("Updated final batch of " + (count % BATCH_SIZE));
                    }

                    logDB.info("Updated instruction records: " + count);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to update cloned instruction");
            return new ErrorMessage(
                    "Failed to update cloned instruction", "cloned instruction Update Failure", error.getMessage());
        }
    }

    private ErrorMessage cloneVariablesDurable(int previousBotJob) {
        Integer newBotJobId = botJobMap.get(previousBotJob);
        if (previousBotJob <= 0 || newBotJobId == null || newBotJobId <= 0) {
            return new ErrorMessage(
                    "Failed to clone variable",
                    "Durable variable owner mapping is unavailable",
                    null);
        }
        String select = "SELECT id,variable_type,name,configured_value,local_format,"
                + "delimiter,producer_instruction_id"
                + " FROM bot_job_variable_definition"
                + " WHERE home_banking_id=? AND bot_job_id=? ORDER BY id";
        try (Connection connection = getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                OwnerKey sourceOwner =
                        botJobVariableOwner(connection, previousBotJob);
                OwnerKey destinationOwner =
                        botJobVariableOwner(connection, newBotJobId);
                variableMap.clear();
                try (PreparedStatement statement = connection.prepareStatement(select)) {
                    statement.setInt(1, sourceOwner.homeBankingId());
                    statement.setInt(2, sourceOwner.botJobId());
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            Integer sourceProducer =
                                    nullableInt(rows, "producer_instruction_id");
                            Integer destinationProducer =
                                    sourceProducer == null
                                            ? null
                                            : instructionMap.get(sourceProducer);
                            if (sourceProducer != null
                                    && destinationProducer == null) {
                                throw new SQLException(
                                        "A cloned variable producer was not cloned.");
                            }
                            MutationResult created =
                                    botJobRuntimeVariables.createDefinition(
                                            connection,
                                            destinationOwner,
                                            new DefinitionDraft(
                                                    rows.getString("variable_type"),
                                                    rows.getString("name"),
                                                    rows.getString("configured_value"),
                                                    rows.getString("local_format"),
                                                    rows.getString("delimiter"),
                                                    destinationProducer == null
                                                            ? null
                                                            : destinationProducer.longValue(),
                                                    ValueState.VOID,
                                                    null),
                                            null);
                            if (!created.applied()
                                    || created.definition() == null) {
                                throw new SQLException(created.message());
                            }
                            variableMap.put(
                                    rows.getInt("id"),
                                    Math.toIntExact(created.definition().id()));
                        }
                    }
                }
                connection.commit();
                connection.setAutoCommit(previousAutoCommit);
                return null;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                connection.setAutoCommit(previousAutoCommit);
                throw failure;
            }
        } catch (SQLException | RuntimeException failure) {
            return new ErrorMessage(
                    "Failed to clone variable",
                    "Durable variable insertion failure",
                    failure.getMessage());
        }
    }

    public ErrorMessage cloneReferences(int previousBotJob) {

        final int BATCH_SIZE = 100;

        String idsInstruction =
                instructionMap.keySet().stream().map(String::valueOf).collect(Collectors.joining(","));

        String checkExistsSQL = "SELECT * FROM reference " + "WHERE  bot_job_id = ? AND instruction_id IN ("
                + idsInstruction + ") ORDER BY id";

        String selectComponentIdsSQL = "SELECT id FROM reference ORDER BY id";

        try (Connection conn = getConnection();
                PreparedStatement selectStmt = conn.prepareStatement(checkExistsSQL);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current component_block ids before insert
            List<Integer> blockIdsBefore = new ArrayList<>();
            try (ResultSet rsIdsBefore = idStmtBefore.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsBefore.next()) {
                    blockIdsBefore.add(rsIdsBefore.getInt("id"));
                }
            }

            // Step 2: prepare and execute insert
            selectStmt.setInt(1, previousBotJob);

            try (ResultSet rsReference = selectStmt.executeQuery()) {

                String insertSQL =
                        "INSERT INTO reference (reference_type, value, instruction_id, bot_job_id) VALUES (?, ?, ?, ?)";

                referenceMap.clear();

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

                    int count = 0;

                    while (rsReference.next()) {
                        int id = rsReference.getInt("id");

                        int oldInstructionId = rsReference.getInt("instruction_id");
                        Integer newInstructionId = instructionMap.get(oldInstructionId);
                        if (newInstructionId == null) {
                            logDB.info("Skipped reference with unknown instruction_id: " + oldInstructionId);
                            continue;
                        }

                        int oldBotJobId = rsReference.getInt("bot_job_id");
                        Integer newBotJobId = botJobMap.get(oldBotJobId);
                        if (newBotJobId == null) {
                            logDB.info("Skipped reference with unknown bot_job_id: " + newBotJobId);
                            continue;
                        }

                        referenceMap.put(id, -1);

                        insertStmt.setString(1, rsReference.getString("reference_type"));
                        insertStmt.setString(2, rsReference.getString("value"));
                        insertStmt.setInt(3, newInstructionId);

                        if (newBotJobId != null) {
                            insertStmt.setInt(4, newBotJobId);
                        } else {
                            insertStmt.setNull(4, Types.INTEGER);
                        }

                        insertStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            insertStmt.executeBatch();
                            conn.commit();
                            logDB.info("Inserted batch of " + BATCH_SIZE);
                        }
                    }

                    // Final batch
                    if (count % BATCH_SIZE != 0) {
                        insertStmt.executeBatch();
                        conn.commit();
                        logDB.info("Inserted final batch of " + (count % BATCH_SIZE));
                    }
                }
            }

            // Step 3: get component_block ids after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsIdsAfter = idStmtAfter.executeQuery(selectComponentIdsSQL)) {
                while (rsIdsAfter.next()) {
                    idsAfter.add(rsIdsAfter.getInt("id"));
                }
            }

            // Step 4: compute new IDs
            List<Integer> newComponentIds = new ArrayList<>(idsAfter);
            newComponentIds.removeAll(blockIdsBefore);

            // You can now use `newComponentIds` as needed
            logDB.info("Newly inserted reference IDs: " + newComponentIds);

            // Step 5: update blockMap with new IDs
            List<Integer> keys = new ArrayList<>(referenceMap.keySet());

            if (keys.size() != newComponentIds.size()) {
                logDB.error(
                        "Mismatch in size: expected " + keys.size() + " new IDs, but got " + newComponentIds.size());
            } else {
                for (int i = 0; i < keys.size(); i++) {
                    Integer oldId = keys.get(i);
                    Integer newId = newComponentIds.get(i);
                    referenceMap.put(oldId, newId);
                }
            }

            return null;

        } catch (SQLException error) {
            logDB.error("Failed to clone reference");
            return new ErrorMessage("Failed to clone reference", "reference Insertion Failure", error.getMessage());
        }
    }

    public void createTableLLama2AIVector() {

        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement()) {

                String createTableVectorOpenAI =
                        """
                                CREATE TABLE web_elements_llama2 (
                                  id SERIAL PRIMARY KEY,
                                  element_name TEXT,
                                  element_type TEXT,
                                  embedding VECTOR(4096) -- size of OpenAI embedding vector
                                );
                                """;
                stmt.executeUpdate(createTableVectorOpenAI);
            }
            logDB.info("Database %s has been created!");
        } catch (SQLException error) {
            logDB.info("initializeDatabase\nError: " + error.getMessage());
        }
    }

    public void createTableOpenAIVector() {

        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement()) {

                String createTableVectorOpenAI =
                        """
                                CREATE TABLE web_elements_openai (
                                  id SERIAL PRIMARY KEY,
                                  element_name TEXT,
                                  element_type TEXT,
                                  embedding vector(1536) -- size of OpenAI embedding vector
                                );
                                """;
                stmt.executeUpdate(createTableVectorOpenAI);
            }
            logDB.info("Database %s has been created!");
        } catch (SQLException error) {
            logDB.info("initializeDatabase\nError: " + error.getMessage());
        }
    }

    public boolean deleteAllJobDetails(String dataBaseType) {
        // Build the SQL delete statement
        try (Statement stmt = getConnection().createStatement()) {

            // Execute each statement individually
            stmt.executeUpdate("DELETE FROM bot_job_runtime_variable_value;");
            stmt.executeUpdate("DELETE FROM bot_job_variable_definition;");
            // 2026-08-03 consolidation: bot_job_runtime_memory and
            // bot_job_variable_migration_note no longer exist; runtime counters live on
            // instruction_graph_state, and the new connection/config tables join the purge.
            stmt.executeUpdate("DELETE FROM instruction_variable_slot;");
            stmt.executeUpdate("DELETE FROM instruction_variable_command_config;");
            stmt.executeUpdate("DELETE FROM instruction_graph_state;");
            // Retained only as the backward-import source for installations that have not
            // completed the durable definition migration.
            stmt.executeUpdate("DELETE FROM variable;");
            stmt.executeUpdate("DELETE FROM reference;");
            stmt.executeUpdate("DELETE FROM instruction;");
            stmt.executeUpdate("DELETE FROM block;");
            stmt.executeUpdate("DELETE FROM bot_job;");

            stmt.executeUpdate("DELETE FROM component_variable;");
            stmt.executeUpdate("DELETE FROM component_reference;");
            stmt.executeUpdate("DELETE FROM component_instruction;");
            stmt.executeUpdate("DELETE FROM component_block;");

            // Drop sequences if they exist
            //            if (!dataBaseType.equalsIgnoreCase("ACCESS")) {
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"blockLoopInstructionSeq\";");
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"blockSeq\";");
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"botJobSeq\";");
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"variableSeq\";");
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"instructionReferenceSeq\";");
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedInstructionReferenceSeq\";");
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"excelReportSeq\";");
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedInstructionReferenceSeq\";");
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedBlockLoopInstructionSeq\";");
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"complexInstructionSeq\";");
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"configurationSeq\";");
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"homeBankingSeq\";");
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedBlockSeq\";");
            //                stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"idgen\";");
            //            }

            logDB.info("All Rows DELETED for:\n"
                    + "Variables;\n"
                    + "Instructions References;\n"
                    + "Instructions;\n"
                    + "Blocks;\n"
                    + "Bot Jobs;\n"
                    + "Saved Components;");

            return true;

        } catch (SQLException e) {

            logDB.error(dataBaseType + " Problems:\n"
                    + "Not Possible delete the  Rows was for these tables:\n"
                    + "ExcelReportDTO;\n"
                    + "Variables;\n"
                    + "Instructions References;\n"
                    + "Instructions;\n"
                    + "Blocks;\n"
                    + "Bot Jobs;\n"
                    + "Saved Components;\n"
                    + "Sequences Not dropped\n"
                    + e.getMessage());
        }
        return false;
    }

    public boolean deleteHomeUrl(String dataBaseType) {
        // Build the SQL delete statement
        try (Statement stmt = getConnection().createStatement()) {

            // Execute each statement individually
            stmt.executeUpdate("DELETE FROM home_url;");

            logDB.info("All Rows DELETED for:\n" + "HomeUrl;");

            return true;

        } catch (SQLException e) {

            logDB.error(dataBaseType + " Problems:\n"
                    + "Not Possible delete the  Rows was for these tables:\n"
                    + "HomeUrl;\n"
                    + e.getMessage());
        }
        return false;
    }

    public ErrorMessage loadAllVariablesByCriteria(String tableName, int whereId, int parentId, String parentName) {
        performLists.getListVariablesUser().clear();

        // Determine related table and columns based on tableName
        String joinTable;
        String joinTableVarId;
        String filterColumn;

        boolean componentVariables =
                "component_variable".equalsIgnoreCase(tableName);
        if (componentVariables) {
            joinTable = "component_instruction";
            joinTableVarId = "variable_id"; // assuming the join column name in component_instruction is variable_id
            filterColumn = "home_banking_id";
        } else {
            // default to "variable"
            joinTable = "instruction";
            joinTableVarId = "variable_id";
            filterColumn = "bot_job_id";
        }

        String physicalTable =
                componentVariables ? "component_variable" : "bot_job_variable_definition";
        String typeColumn = componentVariables ? "type" : "variable_type";
        String valueColumn = componentVariables ? "value" : "configured_value";
        String producerColumn =
                componentVariables ? "instruction_id" : "producer_instruction_id";
        StringBuilder selectSQL = new StringBuilder(
                "SELECT vars.id, vars." + typeColumn + " AS type, vars.name, vars."
                        + valueColumn + " AS value, vars.local_format, vars.delimiter, COUNT(blk."
                        + joinTableVarId + ") AS UsedVars "
                        + "FROM " + physicalTable + " vars "
                        + "LEFT JOIN " + joinTable + " blk ON blk." + joinTableVarId + " = vars.id "
                        + "WHERE vars." + filterColumn + " = ? ");

        if (parentId != -1) {
            selectSQL.append(" AND vars.").append(producerColumn).append(" = ? ");
        }

        selectSQL.append(" GROUP BY vars.id, vars.")
                .append(typeColumn)
                .append(", vars.name, vars.")
                .append(valueColumn)
                .append(", vars.local_format, vars.delimiter ");
        selectSQL.append(" ORDER BY vars.id ");

        try (PreparedStatement pstmt = getConnection().prepareStatement(selectSQL.toString())) {
            pstmt.setInt(1, whereId);
            if (parentId != -1) {
                pstmt.setInt(2, parentId);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String type = rs.getString("type");
                    String name = rs.getString("name");
                    String value = rs.getString("value");
                    String localFormat = rs.getString("local_format");
                    String delimiter = rs.getString("delimiter");
                    String usedVars = rs.getString("UsedVars");

                    performLists
                            .getListVariablesUser()
                            .add(new VariableUserDTO(
                                    id,
                                    type,
                                    name,
                                    value,
                                    whereId,
                                    parentId,
                                    parentName,
                                    localFormat,
                                    delimiter,
                                    usedVars));
                }
            }
        } catch (SQLException error) {

            logDB.error("loadAllVariablesByCriteria. Error: " + error.getMessage());
            return new ErrorMessage("Error loading Variables", "Error loading Variables", error.getMessage());
        }
        return null;
    }

    public ErrorMessage createVariable(VariableUserDTO user) {
        return createVariable("variable", user == null ? -1 : user.getBotJobId(), user);
    }

    /**
     * Creates a variable in the instruction workspace that owns it.
     *
     * <p>The Components command editor is scoped by {@code home_banking_id}; routing its create
     * operation through the legacy overload wrote the row into {@code variable} using a Bot Job
     * owner. Keep the table choice whitelisted and verify the parent instruction on the same
     * transaction before inserting.
     */
    public ErrorMessage createVariable(
            String targetTable, int ownerId, VariableUserDTO user) {
        if (user == null
                || ownerId <= 0
                || user.getParentId() == null
                || user.getParentId() <= 0) {
            return new ErrorMessage(
                    "Variable Insertion Error",
                    "A variable, parent instruction, and positive workspace owner are required.",
                    null);
        }
        String tableName;
        String instructionTable;
        String ownerColumn;
        if ("variable".equals(targetTable)
                || "bot_job_variable_definition".equals(targetTable)) {
            tableName = "bot_job_variable_definition";
            instructionTable = "instruction";
            ownerColumn = "bot_job_id";
        } else if ("component_variable".equals(targetTable)) {
            tableName = "component_variable";
            instructionTable = "component_instruction";
            ownerColumn = "home_banking_id";
        } else {
            return new ErrorMessage(
                    "Variable Insertion Error",
                    "Unsupported variable workspace.",
                    targetTable);
        }

        try (Connection conn = getConnection()) {
            if ("bot_job_variable_definition".equals(tableName)) {
                OwnerKey owner = botJobVariableOwner(conn, ownerId);
                MutationResult created = botJobRuntimeVariables.createDefinition(
                        conn,
                        owner,
                        new DefinitionDraft(
                                user.getType(),
                                user.getName(),
                                user.getValue(),
                                user.getLocalFormat(),
                                user.getDelimiter(),
                                user.getParentId().longValue(),
                                ValueState.VOID,
                                null),
                        null);
                if (!created.applied() || created.definition() == null) {
                    return new ErrorMessage(
                            "Variable Insertion Error",
                            "Error inserting a new variable.",
                            created.message());
                }
                idsVariableAfter.clear();
                idsVariableAfter.add(Math.toIntExact(created.definition().id()));
                return null;
            }
            return createVariableTransaction(
                    conn,
                    tableName,
                    instructionTable,
                    ownerColumn,
                    ownerId,
                    user);
        } catch (SQLException error) {
            logDB.error("createVariable - Error: {}", error.getMessage());
            return new ErrorMessage(
                    "Variable Insertion Error",
                    "Error inserting a new variable.",
                    error.getMessage());
        }
    }

    synchronized ErrorMessage createVariableTransaction(
            Connection conn,
            String tableName,
            String instructionTable,
            String ownerColumn,
            int ownerId,
            VariableUserDTO user)
            throws SQLException {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement parent = conn.prepareStatement(
                    "SELECT COUNT(*) FROM " + instructionTable
                            + " WHERE id = ? AND " + ownerColumn + " = ?")) {
                parent.setInt(1, user.getParentId());
                parent.setInt(2, ownerId);
                try (ResultSet result = parent.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        throw new SQLException(
                                "The variable parent instruction does not belong to the active workspace");
                    }
                }
            }

            try (PreparedStatement existing = conn.prepareStatement(
                    "SELECT id FROM " + tableName
                            + " WHERE instruction_id = ? AND " + ownerColumn + " = ?")) {
                existing.setInt(1, user.getParentId());
                existing.setInt(2, ownerId);
                try (ResultSet result = existing.executeQuery()) {
                    if (result.next()) {
                        throw new SQLException(
                                "The instruction already owns a variable. Edit the existing variable instead.");
                    }
                }
            }

            String insertSQL = "INSERT INTO " + tableName
                    + " (type, Name, Value, " + ownerColumn
                    + ", instruction_id, local_format, delimiter) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement insert =
                    conn.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, user.getType());
                insert.setString(2, user.getName());
                insert.setString(3, user.getValue());
                insert.setInt(4, ownerId);
                insert.setInt(5, user.getParentId());
                insert.setString(6, user.getLocalFormat());
                insert.setString(7, user.getDelimiter());
                if (insert.executeUpdate() != 1) {
                    throw new SQLException("Variable insert did not create exactly one row");
                }
                idsVariableAfter.clear();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (keys.next()) idsVariableAfter.add(keys.getInt(1));
                }
            }
            conn.commit();
            logDB.info(
                    "Variable inserted successfully into {}. New IDs: {}",
                    tableName,
                    idsVariableAfter);
            return null;
        } catch (SQLException | RuntimeException failure) {
            try {
                conn.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            try {
                conn.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {
                // The transaction outcome is already known and the caller closes the connection.
            }
        }
    }

    public ErrorMessage updateUserData(String id, DatabaseUserDTO user) {
        String updateSQL =
                "UPDATE home_banking SET Name = ?, Url = ?, Priority = ?, search_config = ?, options_config = ? WHERE ID = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // start transaction

            try (PreparedStatement stmt = conn.prepareStatement(updateSQL)) {
                int userId = Integer.parseInt(id);

                // Replace newlines with "£" and handle null values
                String priority = Strings.isNullOrEmpty(user.getPriority())
                        ? ""
                        : user.getPriority().replace("\n", "£");
                String searchConfig = Strings.isNullOrEmpty(user.getSearchConfig())
                        ? ""
                        : user.getSearchConfig().replace("\n", "£");
                String optionsConfig = Strings.isNullOrEmpty(user.getOptionsConfig())
                        ? ""
                        : user.getOptionsConfig().replace("\n", "£");

                // Set parameters
                stmt.setString(1, user.getName());
                stmt.setString(2, user.getUrl());
                stmt.setString(3, priority);
                stmt.setString(4, searchConfig);
                stmt.setString(5, optionsConfig);
                stmt.setInt(6, userId);

                stmt.addBatch();
                int[] rowsAffectedBatch = stmt.executeBatch();
                conn.commit();

                int rowsAffected = rowsAffectedBatch.length > 0 ? rowsAffectedBatch[0] : 0;

                if (rowsAffected > 0) {
                    logDB.info(String.format("Updated %d row(s) in home_banking where ID = %d", rowsAffected, userId));
                } else {
                    logDB.warn(
                            String.format("No matching record found to update in home_banking where ID = %d", userId));
                    return new ErrorMessage(
                            "Update Warning",
                            "Id Not Found",
                            String.format("No matching record found to update Id: %d", userId));
                }

                return null; // success
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rbEx) {
                    logDB.error("Rollback failed: " + rbEx.getMessage());
                }

                logDB.error(String.format(
                        "Error updating row in home_banking where ID = %s. Error: %s", id, e.getMessage()));
                return new ErrorMessage(
                        "Update Error", "Failed to update home_banking record for ID = " + id, e.getMessage());
            }
        } catch (SQLException ex) {
            logDB.error("Connection error while updating home_banking for ID = {}. Error: {}", id, ex.getMessage());
            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        } catch (NumberFormatException nfEx) {
            logDB.error("Invalid ID format: {}", id);
            return new ErrorMessage("Invalid ID", "Provided ID is not a valid number", nfEx.getMessage());
        }
    }

    public ErrorMessage updateUserData(String tableName, int whereId, VariableUserDTO user) {
        // Determine foreign key column
        boolean botJobVariable = "variable".equalsIgnoreCase(tableName)
                || "bot_job_variable_definition".equalsIgnoreCase(tableName);
        String foreignKeyColumn = botJobVariable ? "bot_job_id" : "home_banking_id";
        String physicalTable =
                botJobVariable ? "bot_job_variable_definition" : tableName;

        String updateSQL = botJobVariable
                ? "UPDATE bot_job_variable_definition SET "
                        + "name=?,variable_type=?,configured_value=?,local_format=?,delimiter=?,"
                        + "updated_at=CURRENT_TIMESTAMP WHERE id=? AND bot_job_id=?"
                : "UPDATE " + physicalTable + " SET "
                        + "name=?,type=?,value=?,local_format=?,delimiter=? "
                        + "WHERE id=? AND " + foreignKeyColumn + "=?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // start transaction

            try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
                pstmt.setString(1, user.getName());
                pstmt.setString(2, user.getType());
                pstmt.setString(3, user.getValue());
                pstmt.setString(4, user.getLocalFormat());
                pstmt.setString(5, user.getDelimiter());
                pstmt.setInt(6, user.getId());
                pstmt.setInt(7, whereId);

                int rowsAffected = pstmt.executeUpdate();
                conn.commit();

                if (rowsAffected > 0) {

                    logDB.info(String.format(
                            "Updated %d row(s) in %s where id = %d and %s = %d",
                            rowsAffected, physicalTable, user.getId(), foreignKeyColumn, whereId));
                } else {

                    logDB.warn(String.format(
                            "No matching row found in %s where id = %d and %s = %d",
                            physicalTable, user.getId(), foreignKeyColumn, whereId));
                }

                return null; // success
            } catch (SQLException e) {

                logDB.error(String.format(
                        "Error updating row in %s where id = %d and %s = %d. Error: %s",
                        physicalTable, user.getId(), foreignKeyColumn, whereId, e.getMessage()));

                return new ErrorMessage(
                        "Update Error",
                        "Failed to update row in " + physicalTable + " where id = " + user.getId() + " and "
                                + foreignKeyColumn + " = " + whereId,
                        e.getMessage());
            }
        } catch (SQLException ex) {

            logDB.error(String.format(
                    "Connection error while updating row in %s where id = %d and %s = %d. Error: %s",
                    physicalTable, user.getId(), foreignKeyColumn, whereId, ex.getMessage()));

            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }
    }

    public ErrorMessage updateVariableAndOperationsAtomic(
            String variableTable,
            String instructionTable,
            int whereId,
            VariableUserDTO variable,
            List<ParentOperations> dependents) {
        try (Connection connection = getConnection()) {
            new VariableUpdateTransaction()
                    .execute(connection, variableTable, instructionTable, whereId, variable, dependents);
            return null;
        } catch (SQLException exception) {
            return new ErrorMessage("Update Variable Error", "Atomic variable update failed", exception.getMessage());
        }
    }

    public ErrorMessage deleteUserData(String orgId) {
        String deleteHomeUrlSQL = "DELETE FROM home_url WHERE home_banking_id = ?";
        String deleteHomeBankingSQL = "DELETE FROM home_banking WHERE id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // start transaction

            int homeBankId = Integer.parseInt(orgId);

            try (PreparedStatement deleteHomeUrlStmt = conn.prepareStatement(deleteHomeUrlSQL);
                    PreparedStatement deleteHomeBankingStmt = conn.prepareStatement(deleteHomeBankingSQL)) {

                // batch for home_url
                deleteHomeUrlStmt.setInt(1, homeBankId);
                deleteHomeUrlStmt.addBatch();

                // batch for home_banking
                deleteHomeBankingStmt.setInt(1, homeBankId);
                deleteHomeBankingStmt.addBatch();

                int[] urlRowsBatch = deleteHomeUrlStmt.executeBatch();
                int[] bankRowsBatch = deleteHomeBankingStmt.executeBatch();

                conn.commit();

                int urlRows = urlRowsBatch.length > 0 ? urlRowsBatch[0] : 0;
                int bankRows = bankRowsBatch.length > 0 ? bankRowsBatch[0] : 0;

                if (urlRows > 0 || bankRows > 0) {
                    logDB.info(String.format(
                            "Deleted %d row(s) from home_url and %d row(s) from home_banking for orgId = %d",
                            urlRows, bankRows, homeBankId));
                } else {
                    logDB.warn(String.format(
                            "No rows found to delete in home_url/home_banking for orgId = %d", homeBankId));
                }

                return null; // success
            } catch (SQLException e) {
                logDB.error(String.format("Error deleting rows for orgId = %d. Error: %s", homeBankId, e.getMessage()));

                return new ErrorMessage(
                        "Delete Error",
                        "Failed to delete rows from home_url/home_banking for orgId = " + homeBankId,
                        e.getMessage());
            }
        } catch (SQLException ex) {
            logDB.error(String.format(
                    "Connection error while deleting rows for orgId = %d. Error: %s", orgId, ex.getMessage()));

            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }
    }

    public ErrorMessage deleteUserData(String tableName, int whereId, int variableId) {
        // Determine foreign key column
        boolean botJobVariable = "variable".equalsIgnoreCase(tableName)
                || "bot_job_variable_definition".equalsIgnoreCase(tableName);
        String foreignKeyColumn = botJobVariable ? "bot_job_id" : "home_banking_id";
        String physicalTable =
                botJobVariable ? "bot_job_variable_definition" : tableName;

        if (botJobVariable) {
            try (Connection connection = getConnection()) {
                MutationResult deleted = botJobRuntimeVariables.deleteDefinitions(
                        connection,
                        botJobVariableOwner(connection, whereId),
                        Set.of((long) variableId),
                        null);
                if (deleted.applied()) {
                    return null;
                }
                return new ErrorMessage(
                        "Delete Error",
                        "Failed to delete the Bot Job variable definition.",
                        deleted.message());
            } catch (SQLException exception) {
                return new ErrorMessage(
                        "Database Connection Error",
                        "Could not delete the Bot Job variable definition",
                        exception.getMessage());
            }
        }

        // Build SQL
        String deleteSQL =
                "DELETE FROM " + physicalTable + " WHERE id = ? AND " + foreignKeyColumn + " = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // start transaction

            try (PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
                pstmt.setInt(1, variableId);
                pstmt.setInt(2, whereId);

                pstmt.addBatch();
                int[] rowsAffectedBatch = pstmt.executeBatch();
                conn.commit();

                int rowsAffected = rowsAffectedBatch.length > 0 ? rowsAffectedBatch[0] : 0;

                if (rowsAffected > 0) {

                    logDB.info(String.format(
                            "Deleted %d row(s) from %s where id = %d and %s = %d",
                            rowsAffected, physicalTable, variableId, foreignKeyColumn, whereId));
                } else {

                    logDB.warn(String.format(
                            "No rows found to delete in %s where id = %d and %s = %d",
                            physicalTable, variableId, foreignKeyColumn, whereId));
                }

                return null; // success
            } catch (SQLException e) {

                logDB.error(String.format(
                        "Error deleting row in %s where id = %d and %s = %d. Error: %s",
                        physicalTable, variableId, foreignKeyColumn, whereId, e.getMessage()));

                return new ErrorMessage(
                        "Delete Error",
                        "Failed to delete from " + physicalTable + " where id = " + variableId + " and " + foreignKeyColumn
                                + " = " + whereId,
                        e.getMessage());
            }
        } catch (SQLException ex) {

            logDB.error(String.format(
                    "Connection error while deleting row in %s where id = %d and %s = %d. Error: %s",
                    physicalTable, variableId, foreignKeyColumn, whereId, ex.getMessage()));

            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }
    }

    private static OwnerKey botJobVariableOwner(Connection connection, int botJobId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT home_banking_id FROM bot_job WHERE id=?")) {
            statement.setInt(1, botJobId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("The Bot Job variable owner was not found.");
                }
                int homeBankingId = rows.getInt("home_banking_id");
                if (homeBankingId <= 0 || rows.next()) {
                    throw new SQLException("The Bot Job variable owner is invalid.");
                }
                return new OwnerKey(homeBankingId, botJobId);
            }
        }
    }

    private InstructionOperationDTO buildFromLoadDTO(InstructionOperationDTO dto) {
        return InstructionOperationDTO.builder()
                .id(dto.getId())
                .blockId(dto.getBlockId())
                .botJobId(dto.getBotJobId())
                .homeBankingId(dto.getHomeBankingId())
                .name(dto.getName())
                .description(dto.getDescription())
                .actions(dto.getActions())
                .operation(dto.getOperation())
                .instructionOrderNumber(dto.getInstructionOrderNumber())
                .actionCustomMaxWaitSec(dto.getActionCustomMaxWaitSec())
                .onHoldSeconds(dto.getOnHoldSeconds())
                .variableId(dto.getVariableId())
                .parentBlockId(dto.getParentBlockId())
                .parentId(dto.getParentId())
                .instructionActive(dto.getInstructionActive())
                .blockActive(dto.getBlockActive())
                .refreshLoop(dto.getRefreshLoop())
                .loopOnly(dto.getLoopOnly())
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Requirement CRUD + traceability links (Requirements tab)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Load all requirements for a bot job, with rolled-up coverage counts
     * (linked Functional Cases + linked Flow Tests). Single query with two
     * count subqueries — no N+1.
     */
    public List<RequirementDTO> loadRequirements(int botJobId) {
        List<RequirementDTO> rows = new ArrayList<>();
        if (botJobId <= 0) return rows;
        String sql = "SELECT r.id, r.bot_job_id, r.external_ref, r.title, r.description, "
                + "r.priority, r.status, r.created_at, r.updated_at, "
                + "(SELECT COUNT(*) FROM requirement_use_case x WHERE x.requirement_id = r.id) AS uc_count, "
                + "(SELECT COUNT(*) FROM requirement_flow x WHERE x.requirement_id = r.id) AS flow_count "
                + "FROM requirement r WHERE r.bot_job_id = ? ORDER BY r.id ASC";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, botJobId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RequirementDTO d = new RequirementDTO();
                    d.setId(rs.getInt("id"));
                    d.setBotJobId(rs.getInt("bot_job_id"));
                    d.setExternalRef(rs.getString("external_ref"));
                    d.setTitle(rs.getString("title"));
                    d.setDescription(rs.getString("description"));
                    d.setPriority(rs.getString("priority"));
                    d.setStatus(rs.getString("status"));
                    d.setCreatedAt(rs.getString("created_at"));
                    d.setUpdatedAt(rs.getString("updated_at"));
                    d.setLinkedUseCaseCount(rs.getInt("uc_count"));
                    d.setLinkedFlowCount(rs.getInt("flow_count"));
                    rows.add(d);
                }
            }
        } catch (SQLException e) {
            log.error("loadRequirements({}) failed: {}", botJobId, e.getMessage());
        }
        return rows;
    }

    /** Insert (id == null) or update an existing requirement. Returns id, or null on error. */
    public Integer saveRequirement(RequirementDTO dto) {
        if (dto == null
                || dto.getBotJobId() == null
                || dto.getBotJobId() <= 0
                || dto.getTitle() == null
                || dto.getTitle().isBlank()) {
            return null;
        }
        String now = new java.sql.Timestamp(System.currentTimeMillis()).toString();
        String status = dto.getStatus() != null && !dto.getStatus().isBlank() ? dto.getStatus() : "ACTIVE";
        try {
            Connection conn = getConnection();
            if (dto.getId() == null) {
                String insertSql = "INSERT INTO requirement "
                        + "(bot_job_id, external_ref, title, description, priority, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, dto.getBotJobId());
                    ps.setString(2, dto.getExternalRef());
                    ps.setString(3, dto.getTitle());
                    ps.setString(4, dto.getDescription());
                    ps.setString(5, dto.getPriority());
                    ps.setString(6, status);
                    ps.setString(7, now);
                    ps.setString(8, now);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) return keys.getInt(1);
                    }
                }
                try (PreparedStatement ps =
                        conn.prepareStatement("SELECT id FROM requirement WHERE bot_job_id = ? AND title = ?")) {
                    ps.setInt(1, dto.getBotJobId());
                    ps.setString(2, dto.getTitle());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getInt(1);
                    }
                }
            } else {
                String updateSql = "UPDATE requirement SET external_ref = ?, title = ?, description = ?, "
                        + "priority = ?, status = ?, updated_at = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, dto.getExternalRef());
                    ps.setString(2, dto.getTitle());
                    ps.setString(3, dto.getDescription());
                    ps.setString(4, dto.getPriority());
                    ps.setString(5, status);
                    ps.setString(6, now);
                    ps.setInt(7, dto.getId());
                    ps.executeUpdate();
                    return dto.getId();
                }
            }
        } catch (SQLException e) {
            log.error("saveRequirement({}) failed: {}", dto, e.getMessage());
        }
        return null;
    }

    /** Delete a requirement (FK CASCADE removes its links). Belt-and-suspenders explicit DELETE for SQLite-without-pragma. */
    public boolean deleteRequirement(int requirementId) {
        if (requirementId <= 0) return false;
        Connection conn = null;
        Boolean prevAuto = null;
        try {
            conn = getConnection();
            prevAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps =
                    conn.prepareStatement("DELETE FROM requirement_use_case WHERE requirement_id = ?")) {
                ps.setInt(1, requirementId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps =
                    conn.prepareStatement("DELETE FROM requirement_flow WHERE requirement_id = ?")) {
                ps.setInt(1, requirementId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM requirement WHERE id = ?")) {
                ps.setInt(1, requirementId);
                ps.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error("deleteRequirement({}) failed: {}", requirementId, e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            return false;
        } finally {
            if (conn != null && prevAuto != null) {
                try {
                    conn.setAutoCommit(prevAuto);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    /** Load both link sets (use cases + flows) for one requirement. */
    public RequirementLinksDTO loadRequirementLinks(int requirementId) {
        RequirementLinksDTO out = new RequirementLinksDTO();
        out.setRequirementId(requirementId);
        if (requirementId <= 0) return out;
        try (PreparedStatement ps = getConnection()
                .prepareStatement("SELECT use_case_id FROM requirement_use_case WHERE requirement_id = ?")) {
            ps.setInt(1, requirementId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.getUseCaseIds().add(rs.getInt(1));
            }
        } catch (SQLException e) {
            log.error("loadRequirementLinks(uc {}) failed: {}", requirementId, e.getMessage());
        }
        try (PreparedStatement ps =
                getConnection().prepareStatement("SELECT flow_id FROM requirement_flow WHERE requirement_id = ?")) {
            ps.setInt(1, requirementId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.getFlowIds().add(rs.getInt(1));
            }
        } catch (SQLException e) {
            log.error("loadRequirementLinks(flow {}) failed: {}", requirementId, e.getMessage());
        }
        return out;
    }

    /** Replace BOTH link sets for one requirement in a single transaction. */
    public boolean saveRequirementLinks(int requirementId, List<Integer> useCaseIds, List<Integer> flowIds) {
        if (requirementId <= 0) return false;
        Connection conn = null;
        Boolean prevAuto = null;
        try {
            conn = getConnection();
            prevAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement del =
                    conn.prepareStatement("DELETE FROM requirement_use_case WHERE requirement_id = ?")) {
                del.setInt(1, requirementId);
                del.executeUpdate();
            }
            if (useCaseIds != null && !useCaseIds.isEmpty()) {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO requirement_use_case (requirement_id, use_case_id) VALUES (?, ?)")) {
                    for (Integer ucId : useCaseIds) {
                        if (ucId == null || ucId <= 0) continue;
                        ins.setInt(1, requirementId);
                        ins.setInt(2, ucId);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }
            try (PreparedStatement del =
                    conn.prepareStatement("DELETE FROM requirement_flow WHERE requirement_id = ?")) {
                del.setInt(1, requirementId);
                del.executeUpdate();
            }
            if (flowIds != null && !flowIds.isEmpty()) {
                try (PreparedStatement ins =
                        conn.prepareStatement("INSERT INTO requirement_flow (requirement_id, flow_id) VALUES (?, ?)")) {
                    for (Integer fId : flowIds) {
                        if (fId == null || fId <= 0) continue;
                        ins.setInt(1, requirementId);
                        ins.setInt(2, fId);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error("saveRequirementLinks({}) failed: {}", requirementId, e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            return false;
        } finally {
            if (conn != null && prevAuto != null) {
                try {
                    conn.setAutoCommit(prevAuto);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Flow CRUD (Phase 1b of ROADMAP_9 — named ordered execution sequences)
    // ──────────────────────────────────────────────────────────────────────

    /** Load all flows for a bot job (steps NOT included; ordered by id ASC). */
    public List<FlowDTO> loadFlows(int botJobId) {
        List<FlowDTO> rows = new ArrayList<>();
        if (botJobId <= 0) return rows;
        String sql = "SELECT id, bot_job_id, name, description, created_at, updated_at "
                + "FROM flow WHERE bot_job_id = ? ORDER BY id ASC";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, botJobId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FlowDTO d = new FlowDTO();
                    d.setId(rs.getInt("id"));
                    d.setBotJobId(rs.getInt("bot_job_id"));
                    d.setName(rs.getString("name"));
                    d.setDescription(rs.getString("description"));
                    d.setCreatedAt(rs.getString("created_at"));
                    d.setUpdatedAt(rs.getString("updated_at"));
                    rows.add(d);
                }
            }
        } catch (SQLException e) {
            log.error("loadFlows({}) failed: {}", botJobId, e.getMessage());
        }
        return rows;
    }

    /** Insert (id == null) or update (id != null) a flow. Returns new id, or null on error. */
    public Integer saveFlow(FlowDTO dto) {
        if (dto == null
                || dto.getBotJobId() == null
                || dto.getBotJobId() <= 0
                || dto.getName() == null
                || dto.getName().isBlank()) {
            return null;
        }
        String now = new java.sql.Timestamp(System.currentTimeMillis()).toString();
        try {
            Connection conn = getConnection();
            if (dto.getId() == null) {
                String insertSql = "INSERT INTO flow (bot_job_id, name, description, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, dto.getBotJobId());
                    ps.setString(2, dto.getName());
                    ps.setString(3, dto.getDescription());
                    ps.setString(4, now);
                    ps.setString(5, now);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) return keys.getInt(1);
                    }
                }
                try (PreparedStatement ps =
                        conn.prepareStatement("SELECT id FROM flow WHERE bot_job_id = ? AND name = ?")) {
                    ps.setInt(1, dto.getBotJobId());
                    ps.setString(2, dto.getName());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getInt(1);
                    }
                }
            } else {
                String updateSql = "UPDATE flow SET name = ?, description = ?, updated_at = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, dto.getName());
                    ps.setString(2, dto.getDescription());
                    ps.setString(3, now);
                    ps.setInt(4, dto.getId());
                    ps.executeUpdate();
                    return dto.getId();
                }
            }
        } catch (SQLException e) {
            log.error("saveFlow({}) failed: {}", dto, e.getMessage());
        }
        return null;
    }

    /** Delete a flow (cascades to flow_step rows via FK CASCADE — schema-enforced). */
    public boolean deleteFlow(int flowId) {
        if (flowId <= 0) return false;
        Connection conn = null;
        Boolean prevAuto = null;
        try {
            conn = getConnection();
            prevAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            // Belt-and-suspenders: delete steps explicitly in case the FK cascade
            // is not enforced by the dialect (SQLite without pragma foreign_keys=ON, etc.)
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM flow_step WHERE flow_id = ?")) {
                ps.setInt(1, flowId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM flow WHERE id = ?")) {
                ps.setInt(1, flowId);
                ps.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error("deleteFlow({}) failed: {}", flowId, e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            return false;
        } finally {
            if (conn != null && prevAuto != null) {
                try {
                    conn.setAutoCommit(prevAuto);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    /** Load all steps for a flow, ordered by step_order ASC. */
    public List<FlowStepDTO> loadFlowSteps(int flowId) {
        List<FlowStepDTO> rows = new ArrayList<>();
        if (flowId <= 0) return rows;
        String sql = "SELECT id, flow_id, step_order, name, step_type, payload_json, created_at, updated_at "
                + "FROM flow_step WHERE flow_id = ? ORDER BY step_order ASC, id ASC";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, flowId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FlowStepDTO d = new FlowStepDTO();
                    d.setId(rs.getLong("id"));
                    d.setFlowId(rs.getInt("flow_id"));
                    d.setStepOrder(rs.getInt("step_order"));
                    d.setName(rs.getString("name"));
                    d.setStepType(rs.getString("step_type"));
                    d.setPayloadJson(rs.getString("payload_json"));
                    d.setCreatedAt(rs.getString("created_at"));
                    d.setUpdatedAt(rs.getString("updated_at"));
                    rows.add(d);
                }
            }
        } catch (SQLException e) {
            log.error("loadFlowSteps({}) failed: {}", flowId, e.getMessage());
        }
        return rows;
    }

    /**
     * Replace every step of a flow with the given list. Single transaction:
     * DELETE all existing steps for the flow, then INSERT each. Same wipe-and-
     * insert semantics as {@link #saveFieldMappingsForUseCase(int, int, List)}.
     */
    public boolean saveFlowSteps(int flowId, List<FlowStepDTO> steps) {
        if (flowId <= 0) return false;
        String deleteSql = "DELETE FROM flow_step WHERE flow_id = ?";
        String insertSql = "INSERT INTO flow_step "
                + "(flow_id, step_order, name, step_type, payload_json, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        Connection conn = null;
        Boolean prevAutoCommit = null;
        try {
            conn = getConnection();
            prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                del.setInt(1, flowId);
                del.executeUpdate();
            }
            String now = new java.sql.Timestamp(System.currentTimeMillis()).toString();
            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                int idx = 0;
                for (FlowStepDTO s : steps) {
                    if (s.getStepType() == null || s.getStepType().isBlank()) continue;
                    ins.setInt(1, flowId);
                    ins.setInt(2, s.getStepOrder() != null ? s.getStepOrder() : idx);
                    ins.setString(3, s.getName());
                    ins.setString(4, s.getStepType());
                    ins.setString(5, s.getPayloadJson());
                    ins.setString(6, s.getCreatedAt() != null ? s.getCreatedAt() : now);
                    ins.setString(7, now);
                    ins.addBatch();
                    idx++;
                }
                ins.executeBatch();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error("saveFlowSteps(flow={}, n={}) failed: {}", flowId, steps.size(), e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            return false;
        } finally {
            if (conn != null && prevAutoCommit != null) {
                try {
                    conn.setAutoCommit(prevAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Use Case CRUD (Phase 1a of ROADMAP_9 — named groups of mappings)
    // ──────────────────────────────────────────────────────────────────────

    /** Load all use cases for a bot job, ordered by id ASC ("Default" first). */
    public List<UseCaseDTO> loadUseCases(int botJobId) {
        List<UseCaseDTO> rows = new ArrayList<>();
        if (botJobId <= 0) return rows;
        String sql = "SELECT id, bot_job_id, name, description, created_at, updated_at "
                + "FROM use_case WHERE bot_job_id = ? ORDER BY id ASC";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, botJobId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UseCaseDTO d = new UseCaseDTO();
                    d.setId(rs.getInt("id"));
                    d.setBotJobId(rs.getInt("bot_job_id"));
                    d.setName(rs.getString("name"));
                    d.setDescription(rs.getString("description"));
                    d.setCreatedAt(rs.getString("created_at"));
                    d.setUpdatedAt(rs.getString("updated_at"));
                    rows.add(d);
                }
            }
        } catch (SQLException e) {
            log.error("loadUseCases({}) failed: {}", botJobId, e.getMessage());
        }
        return rows;
    }

    /**
     * Insert (id == null) or update (id != null) a use case. Returns the id
     * of the saved row, or null on error. Caller is responsible for unique
     * (bot_job_id, name) violation handling — we surface the SQL error.
     */
    public Integer saveUseCase(UseCaseDTO dto) {
        if (dto == null
                || dto.getBotJobId() == null
                || dto.getBotJobId() <= 0
                || dto.getName() == null
                || dto.getName().isBlank()) {
            return null;
        }
        String now = new java.sql.Timestamp(System.currentTimeMillis()).toString();
        try {
            Connection conn = getConnection();
            if (dto.getId() == null) {
                String insertSql = "INSERT INTO use_case (bot_job_id, name, description, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, dto.getBotJobId());
                    ps.setString(2, dto.getName());
                    ps.setString(3, dto.getDescription());
                    ps.setString(4, now);
                    ps.setString(5, now);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) return keys.getInt(1);
                    }
                }
                // Fallback: re-query
                try (PreparedStatement ps =
                        conn.prepareStatement("SELECT id FROM use_case WHERE bot_job_id = ? AND name = ?")) {
                    ps.setInt(1, dto.getBotJobId());
                    ps.setString(2, dto.getName());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getInt(1);
                    }
                }
            } else {
                String updateSql = "UPDATE use_case SET name = ?, description = ?, updated_at = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, dto.getName());
                    ps.setString(2, dto.getDescription());
                    ps.setString(3, now);
                    ps.setInt(4, dto.getId());
                    ps.executeUpdate();
                    return dto.getId();
                }
            }
        } catch (SQLException e) {
            log.error("saveUseCase({}) failed: {}", dto, e.getMessage());
        }
        return null;
    }

    /**
     * Delete a use case AND its mappings. We do the mappings DELETE
     * explicitly because the use_case_id FK is defined in application code,
     * not in the schema (SQLite/Access ALTER TABLE limitations).
     */
    public boolean deleteUseCase(int useCaseId) {
        if (useCaseId <= 0) return false;
        Connection conn = null;
        Boolean prevAuto = null;
        try {
            conn = getConnection();
            prevAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps =
                    conn.prepareStatement("DELETE FROM use_case_field_mapping WHERE use_case_id = ?")) {
                ps.setInt(1, useCaseId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM use_case WHERE id = ?")) {
                ps.setInt(1, useCaseId);
                ps.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error("deleteUseCase({}) failed: {}", useCaseId, e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            return false;
        } finally {
            if (conn != null && prevAuto != null) {
                try {
                    conn.setAutoCommit(prevAuto);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    /** Returns the id of the "Default" use case for a bot job, creating it if missing. */
    public int ensureDefaultUseCase(int botJobId) {
        if (botJobId <= 0) return -1;
        try {
            Connection conn = getConnection();
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT id FROM use_case WHERE bot_job_id = ? AND name = ?")) {
                ps.setInt(1, botJobId);
                ps.setString(2, "Default");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
            UseCaseDTO def = new UseCaseDTO();
            def.setBotJobId(botJobId);
            def.setName("Default");
            def.setDescription("Auto-created");
            Integer id = saveUseCase(def);
            return id != null ? id : -1;
        } catch (SQLException e) {
            log.error("ensureDefaultUseCase({}) failed: {}", botJobId, e.getMessage());
            return -1;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Field mapping CRUD — now scoped to a use case
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Load all field mappings for one use case. Skips rows whose referenced
     * instruction was deleted (defensive — should not happen with FK cascade,
     * but legacy DBs may have orphans).
     */
    public List<FieldMappingDTO> loadFieldMappingsForUseCase(int useCaseId) {
        List<FieldMappingDTO> rows = new ArrayList<>();
        if (useCaseId <= 0) return rows;
        String sql = "SELECT m.id, m.bot_job_id, m.use_case_id, m.api_key, m.api_spec_file, "
                + "m.api_field_name, m.bot_instruction_id, m.created_at, m.updated_at "
                + "FROM use_case_field_mapping m "
                + "INNER JOIN instruction i ON i.id = m.bot_instruction_id "
                + "WHERE m.use_case_id = ? "
                + "ORDER BY m.id ASC";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, useCaseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FieldMappingDTO d = new FieldMappingDTO();
                    d.setId(rs.getLong("id"));
                    d.setBotJobId(rs.getInt("bot_job_id"));
                    d.setUseCaseId(rs.getInt("use_case_id"));
                    d.setApiKey(rs.getString("api_key"));
                    d.setApiSpecFile(rs.getString("api_spec_file"));
                    d.setApiFieldName(rs.getString("api_field_name"));
                    d.setBotInstructionId(rs.getInt("bot_instruction_id"));
                    d.setCreatedAt(rs.getString("created_at"));
                    d.setUpdatedAt(rs.getString("updated_at"));
                    rows.add(d);
                }
            }
        } catch (SQLException e) {
            log.error("loadFieldMappingsForUseCase({}) failed: {}", useCaseId, e.getMessage());
        }
        return rows;
    }

    /**
     * Replace every field mapping for one use case with the given list.
     * Single transaction: DELETE all existing rows for the use case, then
     * INSERT each supplied mapping. Caller must supply the resolved bot_job_id
     * (we cannot infer it from useCaseId without an extra query).
     */
    public boolean saveFieldMappingsForUseCase(int botJobId, int useCaseId, List<FieldMappingDTO> mappings) {
        if (botJobId <= 0 || useCaseId <= 0) return false;
        String deleteSql = "DELETE FROM use_case_field_mapping WHERE use_case_id = ?";
        String insertSql = "INSERT INTO use_case_field_mapping "
                + "(bot_job_id, use_case_id, api_key, api_spec_file, api_field_name, "
                + "bot_instruction_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = null;
        Boolean prevAutoCommit = null;
        try {
            conn = getConnection();
            prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                del.setInt(1, useCaseId);
                del.executeUpdate();
            }
            String now = new java.sql.Timestamp(System.currentTimeMillis()).toString();
            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                for (FieldMappingDTO m : mappings) {
                    if (m.getApiKey() == null || m.getBotInstructionId() == null) continue;
                    ins.setInt(1, botJobId);
                    ins.setInt(2, useCaseId);
                    ins.setString(3, m.getApiKey());
                    ins.setString(4, m.getApiSpecFile());
                    ins.setString(5, m.getApiFieldName());
                    ins.setInt(6, m.getBotInstructionId());
                    ins.setString(7, m.getCreatedAt() != null ? m.getCreatedAt() : now);
                    ins.setString(8, now);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error(
                    "saveFieldMappingsForUseCase(job={}, uc={}, n={}) failed: {}",
                    botJobId,
                    useCaseId,
                    mappings.size(),
                    e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            return false;
        } finally {
            if (conn != null && prevAutoCommit != null) {
                try {
                    conn.setAutoCommit(prevAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Backwards-compatible wrappers — Default use case for the bot job.
    // The WebSocket layer keeps these for now; phase out once React always
    // sends an explicit useCaseId.
    // ──────────────────────────────────────────────────────────────────────

    /** @deprecated Use {@link #loadFieldMappingsForUseCase(int)}. */
    @Deprecated
    public List<FieldMappingDTO> loadFieldMappings(int botJobId) {
        int defId = ensureDefaultUseCase(botJobId);
        if (defId <= 0) return new ArrayList<>();
        return loadFieldMappingsForUseCase(defId);
    }

    /** @deprecated Use {@link #saveFieldMappingsForUseCase(int, int, List)}. */
    @Deprecated
    public boolean saveFieldMappings(int botJobId, List<FieldMappingDTO> mappings) {
        int defId = ensureDefaultUseCase(botJobId);
        if (defId <= 0) return false;
        return saveFieldMappingsForUseCase(botJobId, defId, mappings);
    }

    /**
     * Read-only fetch of all blocks for a bot job. Used by the Flow tab's
     * UI step inspector for the block picker (Phase 2c of ROADMAP_9).
     * Pure query — no singleton mutation.
     */
    public List<Map<String, Object>> loadBlocksForBotJob(int botJobId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (botJobId <= 0) return rows;
        String sql = "SELECT id, block_order_number, name, description, active "
                + "FROM block WHERE bot_job_id = ? "
                + "ORDER BY block_order_number ASC, id ASC";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, botJobId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", rs.getInt("id"));
                    r.put("blockOrderNumber", rs.getInt("block_order_number"));
                    r.put("name", rs.getString("name"));
                    r.put("description", rs.getString("description"));
                    r.put("active", rs.getInt("active"));
                    rows.add(r);
                }
            }
        } catch (SQLException e) {
            log.error("loadBlocksForBotJob({}) failed: {}", botJobId, e.getMessage());
        }
        return rows;
    }

    /**
     * Read-only fetch of INPUT-text instructions for a single bot job, joined with their
     * parent block. Used by the Functional Test mapping tab in MultiTest. Bypasses the
     * heavy `loadInstructions` / `performLists` machinery — this is a pure query that
     * does not mutate any singleton state.
     *
     * Filter: actions LIKE 'I:%' AND active = 1.
     */
    public List<Map<String, Object>> loadInputTextInstructionsForBotJob(int botJobId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (botJobId <= 0) {
            return rows;
        }
        String sql = "SELECT i.id, i.name, i.client_named, i.actions, i.xpath, i.css_selector, "
                + "i.block_id, i.instruction_order_number, "
                + "b.name AS block_name, b.block_order_number "
                + "FROM instruction i "
                + "LEFT JOIN block b ON b.id = i.block_id "
                + "WHERE i.bot_job_id = ? AND i.actions LIKE 'I:%' AND i.active = 1 "
                + "ORDER BY b.block_order_number ASC, i.instruction_order_number ASC";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, botJobId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", rs.getInt("id"));
                    r.put("name", rs.getString("name"));
                    r.put("clientNamed", rs.getString("client_named"));
                    r.put("actions", rs.getString("actions"));
                    r.put("xpath", rs.getString("xpath"));
                    r.put("cssSelector", rs.getString("css_selector"));
                    r.put("blockId", rs.getInt("block_id"));
                    r.put("instructionOrderNumber", rs.getInt("instruction_order_number"));
                    r.put("blockName", rs.getString("block_name"));
                    r.put("blockOrderNumber", rs.getInt("block_order_number"));
                    rows.add(r);
                }
            }
        } catch (SQLException e) {
            log.error("loadInputTextInstructionsForBotJob({}) failed: {}", botJobId, e.getMessage());
        }
        return rows;
    }

    /**
     * Read-only fetch of the active AI prompt template by name from {@code ai_prompt}
     * (seeded by migration M20260702_AiPrompt). Pure query — no singleton mutation.
     * Returns null when the prompt is missing or inactive.
     */
    public String loadAiPrompt(String name) {
        String sql = "SELECT content FROM ai_prompt WHERE name = ? AND active = 1";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("content");
                }
            }
        } catch (SQLException e) {
            logDB.error("loadAiPrompt({}) failed: {}", name, e.getMessage());
        }
        return null;
    }

    /** Updates an ai_prompt row's content (UI prompt editor). Returns null on success. */
    public ErrorMessage updateAiPrompt(String name, String content) {
        String sql = "UPDATE ai_prompt SET content = ?, updated_at = ? WHERE name = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, content);
            ps.setString(2, new java.sql.Timestamp(System.currentTimeMillis()).toString());
            ps.setString(3, name);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                return new ErrorMessage("Prompt Not Found", "No ai_prompt row named '" + name + "' to update.", name);
            }
            logDB.info("updateAiPrompt({}) — {} chars saved", name, content == null ? 0 : content.length());
            return null;
        } catch (SQLException e) {
            logDB.error("updateAiPrompt({}) failed: {}", name, e.getMessage());
            return new ErrorMessage(
                    "Prompt Update Failed", "Could not update ai_prompt '" + name + "'", e.getMessage());
        }
    }

    /**
     * Read-only fetch of one block's instructions WITH their reference rows, returned as a
     * local list. Same join/mapping as {@link #loadInstructions} but never touches
     * {@code performLists.getListInstruction()} — safe to call from background threads
     * (GEN FLOW) while the UI owns the shared list.
     */
    public List<InstructionLoad> loadBlockInstructionsReadOnly(int botJobId, int blockId) {
        List<InstructionLoad> result = new ArrayList<>();
        String sql = "SELECT i.id AS instruction_id, i.bot_job_id, "
                + "i.block_id, i.instruction_order_number, i.actions, i.name, i.client_named, i.operation, "
                + "i.xpath, i.coordinates, i.force_coordinates, i.iframe_xpath, "
                + "i.tag_name, i.shadow_host, i.shadow_root, i.css_selector, "
                + "i.description AS instruction_description, i.default_value, "
                + "r.id AS reference_id, r.value AS reference_value, r.reference_type "
                + "FROM instruction i "
                + "LEFT JOIN reference r ON r.instruction_id = i.id AND r.bot_job_id = i.bot_job_id "
                + "WHERE i.bot_job_id = ? AND i.block_id = ? "
                + "ORDER BY i.instruction_order_number ASC, r.id ASC";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, botJobId);
            ps.setInt(2, blockId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<Integer, InstructionLoad> byId = new LinkedHashMap<>();
                while (rs.next()) {
                    int instrId = rs.getInt("instruction_id");
                    InstructionLoad instruction = byId.get(instrId);
                    if (instruction == null) {
                        instruction = new InstructionLoad();
                        instruction.setId(instrId);
                        instruction.setBotJobId(rs.getInt("bot_job_id"));
                        instruction.setBlockId(rs.getInt("block_id"));
                        instruction.setInstructionOrderNumber(rs.getInt("instruction_order_number"));
                        instruction.setActions(rs.getString("actions"));
                        instruction.setName(rs.getString("name"));
                        instruction.setClientNamed(rs.getString("client_named"));
                        instruction.setOperation(rs.getString("operation"));
                        instruction.setXpath(rs.getString("xpath"));
                        instruction.setCoordinates(rs.getString("coordinates"));
                        instruction.setForceCoordinates(rs.getString("force_coordinates"));
                        instruction.setIFrameXPath(rs.getString("iframe_xpath"));
                        instruction.setTagName(rs.getString("tag_name"));
                        instruction.setShadowHost(rs.getString("shadow_host"));
                        instruction.setShadowRoot(rs.getString("shadow_root"));
                        instruction.setCssSelector(rs.getString("css_selector"));
                        instruction.setDescription(rs.getString("instruction_description"));
                        instruction.setDefaultValue(rs.getString("default_value"));
                        instruction.setReferenceLoadDTOList(new ArrayList<>());
                        byId.put(instrId, instruction);
                    }
                    int refId = rs.getInt("reference_id");
                    if (refId > 0) {
                        ReferenceLoadDTO ref = new ReferenceLoadDTO();
                        ref.setId(refId);
                        ref.setInstructionId(instrId);
                        ref.setValue(rs.getString("reference_value"));
                        ref.setReferenceType(rs.getString("reference_type"));
                        ref.setBotJobId(instruction.getBotJobId());
                        instruction.getReferenceLoadDTOList().add(ref);
                    }
                }
                result.addAll(byId.values());
            }
        } catch (SQLException e) {
            logDB.error("loadBlockInstructionsReadOnly({}, {}) failed: {}", botJobId, blockId, e.getMessage());
        }
        return result;
    }

    static Integer readNullableInteger(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }

    public ErrorMessage loadInstructions(int whereID, int blockId, int instrucId, String tableName) {
        List<InstructionLoad> instructions = new ArrayList<>();

        // Validate table name to avoid SQL injection
        List<String> allowedTables = Arrays.asList("instruction", "component_instruction");
        if (!allowedTables.contains(tableName)) {
            return new ErrorMessage(
                    "Invalid Table Name", "tableName must be 'instruction' or 'component_instruction'", tableName);
        }

        // Determine filtering column and reference table
        String whereColumn = tableName.equals("component_instruction") ? "home_banking_id" : "bot_job_id";
        String referenceTable = tableName.equals("component_instruction") ? "component_reference" : "reference";

        // Build SQL with JOIN
        StringBuilder querySQL = new StringBuilder()
                .append("SELECT i.id AS instruction_id, ")
                .append("i.block_id, i.instruction_order_number, i.actions, i.name, i.client_named, i.operation, ")
                .append("i.xpath, i.coordinates, i.force_coordinates, i.iframe_xpath, ")
                .append("i.tag_name, i.shadow_host, i.shadow_root, i.css_selector, ")
                .append("i.description AS instruction_description, i.default_value, ")
                .append("i.optional, i.action_custom_max_wait_sec, ")
                .append("i.on_hold_seconds, i.codified, i.export_to_abr, i.active AS instruction_active, ")
                .append("i.variable_id, i.parent_id, i.parent_block_id, ")
                .append("r.id AS reference_id, r.instruction_id AS ref_instruction_id, r.value AS reference_value, ")
                .append("r.reference_type, r.")
                .append(whereColumn)
                .append(" AS ref_where_id, ")
                .append("i.")
                .append(whereColumn)
                .append(" AS instr_where_id ")
                .append("FROM ")
                .append(tableName)
                .append(" i ")
                .append("LEFT JOIN ")
                .append(referenceTable)
                .append(" r ON r.instruction_id = i.id AND r.")
                .append(whereColumn)
                .append(" = i.")
                .append(whereColumn)
                .append(" WHERE i.")
                .append(whereColumn)
                .append(" = ?");

        if (blockId > 0) {
            querySQL.append(" AND i.block_id = ?");
        }
        if (instrucId > -1) {
            querySQL.append(" AND i.id = ?");
        }

        querySQL.append(" ORDER BY i.instruction_order_number ASC, r.id ASC");

        // Target list
        List<InstructionLoad> targetList;
        if ("instruction".equals(tableName)) {
            performLists.getListInstruction().clear();
            targetList = performLists.getListInstruction();
        } else {
            performLists.getListInstructionComp().clear();
            targetList = performLists.getListInstructionComp();
        }

        try (Connection connection = getConnection();
                PreparedStatement pstmt = connection.prepareStatement(querySQL.toString())) {
            int paramIndex = 1;
            pstmt.setInt(paramIndex++, whereID);
            if (blockId > 0) {
                pstmt.setInt(paramIndex++, blockId);
            }
            if (instrucId > -1) {
                pstmt.setInt(paramIndex++, instrucId);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                Map<Integer, InstructionLoad> instructionMap = new LinkedHashMap<>();

                while (rs.next()) {
                    int instrId = rs.getInt("instruction_id");

                    // Avoid duplicating instructions if multiple references exist
                    InstructionLoad instruction = instructionMap.get(instrId);
                    if (instruction == null) {
                        instruction = new InstructionLoad();
                        instruction.setId(instrId);
                        if (tableName.equals("component_instruction")) {
                            instruction.setHomeBankingId(rs.getInt("instr_where_id"));
                        } else {
                            instruction.setBotJobId(rs.getInt("instr_where_id"));
                        }

                        instruction.setName(rs.getString("name"));
                        instruction.setClientNamed(rs.getString("client_named"));
                        instruction.setActions(rs.getString("actions"));
                        instruction.setOperation(rs.getString("operation"));

                        instruction.setBlockId(rs.getInt("block_id"));
                        instruction.setInstructionOrderNumber(rs.getInt("instruction_order_number"));
                        instruction.setXpath(rs.getString("xpath"));
                        instruction.setCoordinates(rs.getString("coordinates"));
                        instruction.setForceCoordinates(rs.getString("force_coordinates"));
                        instruction.setIFrameXPath(rs.getString("iframe_xpath"));
                        instruction.setTagName(rs.getString("tag_name"));
                        instruction.setShadowHost(rs.getString("shadow_host"));
                        instruction.setShadowRoot(rs.getString("shadow_root"));
                        instruction.setCssSelector(rs.getString("css_selector"));
                        instruction.setDescription(rs.getString("instruction_description"));
                        instruction.setDefaultValue(rs.getString("default_value"));
                        instruction.setOptional(rs.getBoolean("optional"));
                        instruction.setActionCustomMaxWaitSec(rs.getInt("action_custom_max_wait_sec"));
                        instruction.setOnHoldSeconds(rs.getInt("on_hold_seconds"));
                        instruction.setCodified(rs.getBoolean("codified"));
                        instruction.setExportToABR(rs.getBoolean("export_to_abr"));
                        instruction.setInstructionActive(rs.getBoolean("instruction_active"));
                        instruction.setVariableId(readNullableInteger(rs, "variable_id"));
                        instruction.setParentId(readNullableInteger(rs, "parent_id"));
                        instruction.setParentBlockId(readNullableInteger(rs, "parent_block_id"));

                        instruction.setReferenceLoadDTOList(new ArrayList<>());
                        instructionMap.put(instrId, instruction);
                    }

                    // Add reference if exists
                    int refId = rs.getInt("reference_id");
                    if (refId > 0) {
                        ReferenceLoadDTO ref = new ReferenceLoadDTO();
                        ref.setId(refId);
                        ref.setInstructionId(rs.getInt("ref_instruction_id"));
                        ref.setValue(rs.getString("reference_value"));
                        ref.setReferenceType(rs.getString("reference_type"));
                        if (tableName.equals("component_instruction")) {
                            ref.setHomeBankingId(rs.getInt("ref_where_id"));
                        } else {
                            ref.setBotJobId(rs.getInt("ref_where_id"));
                        }
                        instruction.getReferenceLoadDTOList().add(ref);
                    }
                }

                instructions.addAll(instructionMap.values());
                targetList.addAll(instructions);

                String fetchMessage = String.format(
                        "Fetched %d instructions (with references) from table %s%s%s",
                        instructions.size(),
                        tableName,
                        blockId > 0 ? " for Block ID " + blockId : "",
                        instrucId > -1 ? " and Instruction ID " + instrucId : "");
                // Identical reloads repeat many times per second (per-request reloads);
                // only log when the result actually changes.
                if (!fetchMessage.equals(lastLoggedFetchSignature)) {
                    lastLoggedFetchSignature = fetchMessage;
                    logDB.info(fetchMessage);
                }
            }
            return null;
        } catch (SQLException error) {

            logDB.error(String.format(
                    "Error fetching instructions from table %s%s%s. Error: %s",
                    tableName,
                    blockId > 0 ? " for Block ID " + blockId : "",
                    instrucId > -1 ? " and Instruction ID " + instrucId : "",
                    error.getMessage()));

            return new ErrorMessage(
                    "Load Instructions Error",
                    "Failed to load instructions from table: " + tableName,
                    error.getMessage());
        }
    }

    public List<ReferenceLoadDTO> getReferencesList(int whereID, List<InstructionLoad> lstInstruc, String tableName) {
        List<ReferenceLoadDTO> references = new ArrayList<>();

        // Validate table name to avoid SQL injection
        List<String> allowedTables = Arrays.asList("reference", "component_reference");
        if (!allowedTables.contains(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }

        // Determine filter column
        String whereColumn = tableName.equals("component_reference") ? "home_banking_id" : "bot_job_id";

        // Collect instruction IDs
        List<Integer> instrucIds = lstInstruc.stream()
                .map(InstructionLoad::getId) // adjust if your DTO uses another method name for ID
                .filter(id -> id != null && id > -1)
                .toList();

        // Build query dynamically
        StringBuilder querySQL = new StringBuilder("SELECT * FROM ")
                .append(tableName)
                .append(" WHERE ")
                .append(whereColumn)
                .append(" = ?");

        if (!instrucIds.isEmpty()) {
            querySQL.append(" AND instruction_id IN (")
                    .append(instrucIds.stream().map(i -> "?").collect(Collectors.joining(",")))
                    .append(")");
        }

        querySQL.append(" ORDER BY id ASC");

        try (PreparedStatement pstmt = getConnection().prepareStatement(querySQL.toString())) {
            int paramIndex = 1;
            pstmt.setInt(paramIndex++, whereID);

            // Bind instruction IDs if present
            for (Integer id : instrucIds) {
                pstmt.setInt(paramIndex++, id);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ReferenceLoadDTO reference = new ReferenceLoadDTO();

                    reference.setId(rs.getInt("id"));

                    if (tableName.equals("component_reference")) {
                        reference.setHomeBankingId(rs.getInt("home_banking_id"));
                    } else {
                        reference.setBotJobId(rs.getInt("bot_job_id"));
                    }

                    reference.setReferenceType(rs.getString("reference_type"));
                    reference.setValue(rs.getString("value"));
                    reference.setInstructionId(rs.getInt("instruction_id"));

                    references.add(reference);
                }

                logDB.info(String.format(
                        "Fetched %d references from table %s where %s=%d and instrucIds=%s",
                        references.size(), tableName, whereColumn, whereID, instrucIds));
            }
        } catch (SQLException e) {

            logDB.error(String.format(
                    "Error fetching references from table %s where %s=%d. Error: %s",
                    tableName, whereColumn, whereID, e.getMessage()));
        }

        return references;
    }

    /**
     * Utility to check if a column exists in ResultSet
     */
    private boolean hasColumn(ResultSet rs, String columnName) throws SQLException {
        ResultSetMetaData rsMeta = rs.getMetaData();
        for (int i = 1; i <= rsMeta.getColumnCount(); i++) {
            if (columnName.equalsIgnoreCase(rsMeta.getColumnName(i))) {
                return true;
            }
        }
        return false;
    }

    public ErrorMessage updateColumns() {
        try (Connection conn = getConnection()) {

            // Check and add parent_block_id column if missing in both tables
            addColumnIfNotExists(conn, "instruction", "parent_block_id", "INTEGER");
            addColumnIfNotExists(conn, "component_instruction", "parent_block_id", "INTEGER");

            return null;

        } catch (SQLException e) {

            logDB.error("Error ensuring parent_block_id column: " + e.getMessage());
            return new ErrorMessage(
                    "Database Column Error", "Error ensuring parent_block_id column exists in tables", e.getMessage());
        }
    }

    /**
     * Helper method to add column if it does not exist
     */
    private void addColumnIfNotExists(Connection conn, String tableName, String columnName, String columnType)
            throws SQLException {
        boolean columnExists = false;
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (rs.next()) {
                columnExists = true;
            }
        }
        if (!columnExists) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
            }
        }
    }

    public ErrorMessage preDeleteNullBlocks(String blockTable, int whereId, String instTable) {
        ErrorMessage errorMessage = loadBlocks(whereId, "", blockTable);

        if (errorMessage == null) {
            errorMessage = loadInstructions(whereId, -1, -1, instTable);
        }
        if (errorMessage == null) {
            // Snapshot of previous IDs
            List<Integer> previousIds = (blockTable.equals("block")
                            ? performLists.getListBlock()
                            : performLists.getListBlockComp())
                    .stream().map(BlockLoadDTO::getId).filter(Objects::nonNull).toList();

            // Snapshot of previous IDs
            List<Integer> currentIds = (instTable.equals("instruction")
                            ? performLists.getListInstruction()
                            : performLists.getListInstructionComp())
                    .stream()
                            .map(InstructionLoad::getBlockId)
                            .filter(Objects::nonNull)
                            .toList();

            List<Integer> restToDeleteIds =
                    previousIds.stream().filter(id -> !currentIds.contains(id)).collect(Collectors.toList());

            logDB.info("Blocks To Delete: " + restToDeleteIds.size());
            // Keep at least One for BLOCK TABLE
            List<BlockLoadDTO> listBlocks =
                    instTable.equals("instruction") ? performLists.getListBlock() : performLists.getListBlockComp();
            errorMessage = null;
            if (!restToDeleteIds.isEmpty() && (blockTable.equals("block") && listBlocks.size() > 1)) {
                errorMessage = deleteNullBlocks(blockTable, whereId, restToDeleteIds);
            } else if (errorMessage == null && !restToDeleteIds.isEmpty() && (blockTable.equals("component_block"))) {
                errorMessage = deleteNullBlocks(blockTable, whereId, restToDeleteIds);
            }
        }
        return errorMessage;
    }

    public ErrorMessage deleteNullBlocks(String tableName, int whereId, List<Integer> restIds) {
        if (restIds == null || restIds.isEmpty()) {
            return null; // Nothing to delete
        }

        // Determine foreign key column
        String foreignKeyColumn = "block".equalsIgnoreCase(tableName) ? "bot_job_id" : "home_banking_id";

        // Build dynamic delete SQL
        String deleteSQL = "DELETE FROM " + tableName + " WHERE id = ? AND " + foreignKeyColumn + " = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
                for (Integer id : restIds) {
                    pstmt.setInt(1, id);
                    pstmt.setInt(2, whereId);
                    pstmt.addBatch();
                }

                int[] results = pstmt.executeBatch();
                conn.commit();

                int totalDeleted = Arrays.stream(results).sum();

                logDB.info(String.format(
                        "Deleted %d blocks from table %s where %s = %d (IDs: %s)",
                        totalDeleted, tableName, foreignKeyColumn, whereId, restIds));

                return null; // success
            } catch (SQLException e) {

                logDB.error(String.format(
                        "Error deleting blocks from table %s where %s = %d. IDs: %s. Error: %s",
                        tableName, foreignKeyColumn, whereId, restIds, e.getMessage()));

                return new ErrorMessage(
                        "Delete Blocks Error",
                        "Failed to delete blocks from table " + tableName + " where " + foreignKeyColumn + " = "
                                + whereId,
                        e.getMessage());
            }
        } catch (SQLException ex) {

            logDB.error(String.format(
                    "Connection error while deleting blocks from table %s where %s = %d. IDs: %s. Error: %s",
                    tableName, foreignKeyColumn, whereId, restIds, ex.getMessage()));

            return new ErrorMessage("Database Connection Error", "Could not connect to database", ex.getMessage());
        }
    }

    public boolean isConnDBWorks() {
        return connDBWorks;
    }

    private boolean isComponentTask(String typeTask) {
        return ScannerWorkspaceSessions.COMPONENT_TASKS.equals(typeTask);
    }

    public ErrorMessage checkGapsBlockOrder(
            List<BlockLoadDTO> listBlock, String blockTable, int whereId, String botJobName) {
        ErrorMessage errorMessage = null;
        boolean mustReload = false;

        // Optional: check for duplicates or gaps
        Set<Integer> seenNumbers = new HashSet<>();
        for (BlockLoadDTO block : listBlock) {
            if (!seenNumbers.add(block.getBlockOrderNumber())) {
                logDB.info("Duplicate blockOrderNumber found: " + block.getBlockOrderNumber() + " (Block ID: "
                        + block.getId() + ")");
                // updateBlockOrderNumber  ALREADY UPDATE MEMORY LIST
                if (errorMessage == null) {
                    errorMessage = updateBlockOrderNumber(blockTable, whereId, true);
                    mustReload = true;
                }
                break;
            }
        }

        // Collect all block order numbers
        List<Integer> orderNumbers = listBlock.stream()
                .map(BlockLoadDTO::getBlockOrderNumber)
                .sorted()
                .toList();

        // Check for gaps
        for (int i = 1; i < orderNumbers.size(); i++) {
            int expected = orderNumbers.get(i - 1) + 1;
            int actual = orderNumbers.get(i);
            if (actual != expected) {
                logDB.info("Gap found: expected " + expected + " but found " + actual);
                if (errorMessage == null) {
                    errorMessage = updateBlockOrderNumber(blockTable, whereId, true);
                    mustReload = true;
                }
                break; // stop after first gap, or remove if you want to list all gaps
            }
        }

        if (mustReload) {
            errorMessage = loadBlocks(whereId, botJobName, blockTable);
        }
        return errorMessage;
    }

    public void callSocketLists(String sessionLists, String sessionMobile) {
        performLists.initialize(sessionLists);
        // mobileReturnServer.initialize(sessionMobile);
    }
}
