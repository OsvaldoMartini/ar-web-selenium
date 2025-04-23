package com.allinweb.ch.facade;

import com.allinweb.ch.component.model.BlockLoadDTO;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.ComplexInstructionLoadDTO;
import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.component.model.InstructionReferenceLoadDTO;
import com.allinweb.ch.component.model.RollBackBlocksDTO;
import com.allinweb.ch.component.model.RowMoveDTO;
import com.allinweb.ch.component.model.VariableLoadDTO;
import com.allinweb.ch.component.model.VariableUserDTO;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ComboBoxVars;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class PerformDataBase {

    // Static final variable to hold the singleton instance
    protected static volatile PerformDataBase instance;

    private final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    // Private constructor to prevent instantiation
    private PerformDataBase() {
        // Initialize if necessary
    }

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

    private static final ARPropertyManager arPropertyManager;
    private static final PerformMessage performMessage;

    static {
        performMessage = PerformMessage.getInstance();
        arPropertyManager = ARPropertyManager.getInstance();
    }

    private String previousDB;

    private Connection conn = null;

    private final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    private final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";

    // Postgres
    private boolean POSTGRES_DB = false;
    private final String CONNECTION_POSTGRES = "jdbc:postgresql://";
    private final String DB_HOST = "localhost"; // or your PostgreSQL server address
    private final String DB_PORT = "5432"; // default PostgreSQL port
    private final String DB_NAME = "abr_web"; // your database name
    private final String USERNAME = "postgres"; // your database username
    private final String PASSWORD = "martini"; // your database password

    private SessionFactory sessionFactory = null;
    private Session session = null;

    private ObservableList<VariableUserDTO> variablesList = FXCollections.observableArrayList();
    private ObservableList<ComboBoxVars> webPageItems = FXCollections.observableArrayList();

    private BotJobLoadDTO botJobLoadDTO;

    private List<BotJobLoadDTO> botJobLoadList = new ArrayList<>();
    private List<BlockLoadDTO> blockLoadList = new ArrayList<>();

    private Gson gson = new Gson();

    public void initialize(String databaseType) {
        this.previousDB = databaseType;
    }

    public Connection getConn() {
        return conn;
    }

    public void setConn(Connection conn) {
        this.conn = conn;
    }

    public void closeConnection() {
        if (conn != null) {
            try {
                conn.close();
                conn = null; // Reset the connection to null after closing
            } catch (SQLException e) {
                System.out.println(e.getMessage()); // Handle the exception, log it or rethrow it as needed
            }
        }
    }

    public void changeDbConnection() {
        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        //        if (Strings.isNullOrEmpty(previousDB) || (previousDB != null && !previousDB.equals(dataBaseType))) {
        closeConnection();
        previousDB = dataBaseType;

        if (dataBaseType != null && dataBaseType.equalsIgnoreCase("POSTGRES")) {
            POSTGRES_DB = true;

            createTableOpenAIVector();
            if (!doesInstructionTableExist()) {
                initializeMainDatabasePostgres();
            }

        } else {
            POSTGRES_DB = false;

            String dbPath = arPropertyManager.getProperty(ARPropertyEnum.FOLDER_PATH_DB);
            String dbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;

            File dbFile = new File(dbPath + ARConstants.FILE_NAME_DB);
            if (!dbFile.exists()) {
                initializeMainDatabaseAccess(dbUrl, dbFile);
            } else {
                ARLogger.getInstance(PerformDataBase.class)
                        .info(String.format("Database '%s' detected!", dbFile.getName()));
            }
        }
        //        }
    }

    public void createTableOpenAIVector() {

        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement()) {

                String createTableVectorOpenAI =
                        """
                        CREATE TABLE web_elements (
                          id SERIAL PRIMARY KEY,
                          element_name TEXT,
                          element_type TEXT,
                          embedding VECTOR(1536) -- size of OpenAI embedding vector
                        );
                        """;
                stmt.executeUpdate(createTableVectorOpenAI);
            }
            System.out.println("Database %s has been created!");
        } catch (SQLException error) {
            System.out.println("initializeDatabase\nError: " + error.getMessage());
        }
    }

    public boolean doesInstructionTableExist() {
        try (Connection conn = getConnection()) {
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "instruction", null)) {
                return rs.next(); // Returns true if the table exists
            }
        } catch (SQLException error) {
            System.out.println("Error checking table existence: " + error.getMessage());
        }
        return false; // Default return if an exception occurs or the table does not exist
    }

    public void initializeMainDatabasePostgres() {

        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement()) {

                // Create home_banking table
                String createHomeBankingTableSQL = "CREATE TABLE home_banking ("
                        + "ID SERIAL PRIMARY KEY, "
                        + "url TEXT, "
                        + "name TEXT, "
                        + "priority TEXT, "
                        + "search_config TEXT, "
                        + "options_config TEXT, "
                        + "cookies TEXT, "
                        + "driver_session TEXT, "
                        + "username TEXT, "
                        + "password TEXT)";
                stmt.executeUpdate(createHomeBankingTableSQL);

                // Create bot_job table with a foreign key reference to home_banking
                String createBotJobTableSQL = "CREATE TABLE bot_job ("
                        + "id SERIAL PRIMARY KEY, "
                        + "name TEXT UNIQUE, "
                        + "description TEXT, "
                        + "priority TEXT, "
                        + "active INTEGER NOT NULL, "
                        + "home_banking_id INTEGER REFERENCES home_banking(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createBotJobTableSQL);

                // Create block table with a foreign key reference to bot_job
                String createBlockTableSQL = "CREATE TABLE block ("
                        + "id SERIAL PRIMARY KEY, "
                        + "block_order_number INTEGER NOT NULL, "
                        + "name TEXT NOT NULL, "
                        + "description TEXT, "
                        + "type_id INTEGER, "
                        + "export_file TEXT, "
                        + "active INTEGER NOT NULL, "
                        + "wait INTEGER, "
                        + "bot_job_id INTEGER REFERENCES bot_job(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createBlockTableSQL);

                // Create instruction table with foreign key references to block and bot_job
                String createInstructionTableSQL = "CREATE TABLE instruction ("
                        + "id SERIAL PRIMARY KEY, "
                        + "instruction_order_number INTEGER NOT NULL, "
                        + "actions TEXT, "
                        + "name TEXT, "
                        + "xpath TEXT, "
                        + "coordinates TEXT, "
                        + "force_coordinates INTEGER, "
                        + "iframe_xpath TEXT, "
                        + "tag_name TEXT, "
                        + "shadow_host TEXT, "
                        + "shadow_root TEXT, "
                        + "css_selector TEXT, "
                        + "description TEXT, "
                        + "operation TEXT, "
                        + "optional INTEGER, "
                        + "block_marked INTEGER, "
                        + "default_value TEXT, "
                        + "action_custom_max_wait_sec INTEGER, "
                        + "on_hold_seconds INTEGER, "
                        + "codified INTEGER, "
                        + "export_to_abr INTEGER, "
                        + "active INTEGER NOT NULL, "
                        + "block_id INTEGER REFERENCES block(id) ON DELETE CASCADE, "
                        + "variable_id INTEGER, "
                        + "parent_id INTEGER, "
                        + "bot_job_id INTEGER REFERENCES bot_job(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createInstructionTableSQL);

                String createReferenceTableSQL = "CREATE TABLE reference ("
                        + "id SERIAL PRIMARY KEY, "
                        + "reference_type TEXT, "
                        + "value TEXT, "
                        + "instruction_id INTEGER NOT NULL REFERENCES instruction(id) ON DELETE CASCADE, "
                        + "bot_job_id INTEGER REFERENCES bot_job(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createReferenceTableSQL);

                String createVariableTableSQL = "CREATE TABLE variable ("
                        + "id SERIAL PRIMARY KEY, "
                        + "type TEXT, "
                        + "name TEXT, "
                        + "value TEXT, "
                        + "instruction_id INTEGER REFERENCES instruction(id) ON DELETE CASCADE, "
                        + "bot_job_id INTEGER REFERENCES bot_job(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createVariableTableSQL);

                //                String createConfigurationTableSQL = "CREATE TABLE configuration ("
                //                        + "id SERIAL PRIMARY KEY, "
                //                        + "pathJava TEXT, "
                //                        + "logLevel TEXT, "
                //                        + "pathDB TEXT, "
                //                        + "interactionTimeoutSec TEXT, "
                //                        + "pathLog TEXT, "
                //                        + "defaultInstructionStopSeconds TEXT, "
                //                        + "pathReport TEXT, "
                //                        + "browser TEXT, "
                //                        + "dataBaseType TEXT, "
                //                        + "pageUpdateTimeoutSec TEXT, "
                //                        + "pathPriority TEXT, "
                //                        + "pathEngine TEXT, "
                //                        + "pathExcel TEXT, "
                //                        + "pathExport TEXT, "
                //                        + "socketPort TEXT, "
                //                        + "blockLimit TEXT, "
                //                        + "pathJavaFx TEXT)";
                //                stmt.executeUpdate(createConfigurationTableSQL);

                String createComponentBlockTableSQL = "CREATE TABLE component_block ("
                        + "id SERIAL PRIMARY KEY, "
                        + "home_banking_id INTEGER REFERENCES home_banking(id) ON DELETE CASCADE, "
                        + "block_order_number INTEGER NOT NULL, "
                        + "name TEXT NOT NULL, "
                        + "description TEXT, "
                        + "type_id INTEGER, "
                        + "export_file TEXT, "
                        + "active INTEGER, "
                        + "wait INTEGER)";
                stmt.executeUpdate(createComponentBlockTableSQL);

                String createComponentInstructionTableSQL = "CREATE TABLE component_instruction ("
                        + "id SERIAL PRIMARY KEY, "
                        + "instruction_order_number INTEGER NOT NULL, "
                        + "actions TEXT, "
                        + "name TEXT, "
                        + "xpath TEXT, "
                        + "coordinates TEXT, "
                        + "force_coordinates INTEGER, "
                        + "iframe_xpath TEXT, "
                        + "tag_name TEXT, "
                        + "shadow_host TEXT, "
                        + "shadow_root TEXT, "
                        + "css_selector TEXT, "
                        + "description TEXT, "
                        + "operation TEXT, "
                        + "optional INTEGER, "
                        + "block_marked INTEGER, "
                        + "default_value TEXT, "
                        + "action_custom_max_wait_sec INTEGER, "
                        + "on_hold_seconds INTEGER, "
                        + "codified INTEGER, "
                        + "export_to_abr INTEGER, "
                        + "active INTEGER NOT NULL, "
                        + "block_id INTEGER REFERENCES component_block(id) ON DELETE CASCADE, "
                        + "variable_id INTEGER, "
                        + "parent_id INTEGER, "
                        + "home_banking_id INTEGER REFERENCES home_banking(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createComponentInstructionTableSQL);

                String createComponentReferenceTableSQL = "CREATE TABLE component_reference ("
                        + "id SERIAL PRIMARY KEY, "
                        + "reference_type TEXT, "
                        + "value TEXT, "
                        + "instruction_id INTEGER NOT NULL REFERENCES component_instruction(id) ON DELETE CASCADE, "
                        + "home_banking_id INTEGER REFERENCES home_banking(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createComponentReferenceTableSQL);

                String createComponentVariableTableSQL = "CREATE TABLE component_variable ("
                        + "id SERIAL PRIMARY KEY, "
                        + "type TEXT, "
                        + "name TEXT, "
                        + "value TEXT, "
                        + "instruction_id INTEGER REFERENCES component_instruction(id) ON DELETE CASCADE, "
                        + "home_banking_id INTEGER REFERENCES home_banking(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createComponentVariableTableSQL);
            }
            System.out.println("Database %s has been created!");
        } catch (SQLException error) {
            System.out.println("initializeDatabase\nError: " + error.getMessage());
        }
    }

    public void initializeMainDatabaseAccess(String dbUrl, File dbFile) {

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (Statement stmt = conn.createStatement()) {

                // Create home_banking table
                String createHomeBankingTableSQL = "CREATE TABLE home_banking ("
                        + "ID INTEGER PRIMARY KEY, "
                        + "url MEMO, "
                        + "name TEXT, "
                        + "priority MEMO, "
                        + "search_config MEMO, "
                        + "options_config MEMO, "
                        + "cookies MEMO, "
                        + "driver_session MEMO, "
                        + "username TEXT, "
                        + "password TEXT)";
                stmt.executeUpdate(createHomeBankingTableSQL);

                // Create bot_job table with a foreign key reference to home_banking
                String createBotJobTableSQL = "CREATE TABLE bot_job ("
                        + "id INTEGER PRIMARY KEY, "
                        + "name TEXT UNIQUE, "
                        + "description TEXT, "
                        + "priority MEMO, "
                        + "active YESNO NOT NULL, "
                        + "home_banking_id INTEGER);";
                stmt.executeUpdate(createBotJobTableSQL);

                String addBotJobForeignKeySQL = "ALTER TABLE bot_job "
                        + "ADD CONSTRAINT FK_BotJob FOREIGN KEY (home_banking_id) "
                        + "REFERENCES home_banking(id) ON DELETE CASCADE";
                stmt.executeUpdate(addBotJobForeignKeySQL);

                // Create block table with a foreign key reference to bot_job
                String createBlockTableSQL = "CREATE TABLE block ("
                        + "id INTEGER PRIMARY KEY, "
                        + "block_order_number INTEGER NOT NULL, "
                        + "name TEXT NOT NULL, "
                        + "description TEXT, "
                        + "type_id INTEGER, "
                        + "export_file TEXT, "
                        + "active YESNO NOT NULL, "
                        + "wait INTEGER, "
                        + "bot_job_id INTEGER);";
                stmt.executeUpdate(createBlockTableSQL);

                String addForeignKeySQL2 = "ALTER TABLE block "
                        + "ADD CONSTRAINT FK_2 FOREIGN KEY (bot_job_id) "
                        + "REFERENCES bot_job(id) ON DELETE CASCADE";
                stmt.executeUpdate(addForeignKeySQL2);

                // Create instruction table with foreign key references to block and bot_job
                String createInstructionTableSQL = "CREATE TABLE instruction ("
                        + "id INTEGER PRIMARY KEY, "
                        + "instruction_order_number INTEGER NOT NULL, "
                        + "actions MEMO, "
                        + "name TEXT, "
                        + "xpath MEMO, "
                        + "coordinates TEXT, "
                        + "force_coordinates YESNO, "
                        + "iframe_xpath MEMO, "
                        + "tag_name TEXT, "
                        + "shadow_host MEMO, "
                        + "shadow_root MEMO, "
                        + "css_selector MEMO, "
                        + "description TEXT, "
                        + "operation TEXT, "
                        + "optional YESNO, "
                        + "block_marked YESNO, "
                        + "default_value TEXT, "
                        + "action_custom_max_wait_sec INTEGER, "
                        + "on_hold_seconds INTEGER, "
                        + "codified YESNO, "
                        + "export_to_abr YESNO, "
                        + "active YESNO NOT NULL, "
                        + "block_id INTEGER, "
                        + "variable_id INTEGER, "
                        + "parent_id INTEGER, "
                        + "bot_job_id INTEGER);";
                stmt.executeUpdate(createInstructionTableSQL);

                String addForeignKeySQL3 = "ALTER TABLE instruction "
                        + "ADD CONSTRAINT FK_3 FOREIGN KEY (block_id) "
                        + "REFERENCES block(id) ON DELETE CASCADE";
                stmt.executeUpdate(addForeignKeySQL3);

                String addForeignKeySQL4 = "ALTER TABLE instruction "
                        + "ADD CONSTRAINT FK_4 FOREIGN KEY (bot_job_id) "
                        + "REFERENCES bot_job(id) ON DELETE CASCADE";
                stmt.executeUpdate(addForeignKeySQL4);

                String createReferenceTableSQL = "CREATE TABLE reference ("
                        + "id INTEGER PRIMARY KEY, "
                        + "reference_type TEXT, "
                        + "value MEMO, "
                        + "instruction_id INTEGER NOT NULL, "
                        + "bot_job_id INTEGER);";
                stmt.executeUpdate(createReferenceTableSQL);

                String addForeignKeySQL5 = "ALTER TABLE reference "
                        + "ADD CONSTRAINT FK_5 FOREIGN KEY (instruction_id) "
                        + "REFERENCES instruction(id) ON DELETE CASCADE";
                stmt.executeUpdate(addForeignKeySQL5);

                String addForeignKeySQL6 = "ALTER TABLE reference "
                        + "ADD CONSTRAINT FK_6 FOREIGN KEY (bot_job_id) "
                        + "REFERENCES bot_job(id) ON DELETE CASCADE";
                stmt.executeUpdate(addForeignKeySQL6);

                String createVariableTableSQL = "CREATE TABLE variable ("
                        + "id INTEGER PRIMARY KEY, "
                        + "type TEXT, "
                        + "name TEXT, "
                        + "value MEMO, "
                        + "instruction_id INTEGER, "
                        + "bot_job_id INTEGER);";
                stmt.executeUpdate(createVariableTableSQL);

                String addForeignKeySQL7 = "ALTER TABLE variable "
                        + "ADD CONSTRAINT FK_7 FOREIGN KEY (instruction_id) "
                        + "REFERENCES instruction(id) ON DELETE CASCADE";
                stmt.executeUpdate(addForeignKeySQL7);

                String addForeignKeySQL8 = "ALTER TABLE variable "
                        + "ADD CONSTRAINT FK_8 FOREIGN KEY (bot_job_id) "
                        + "REFERENCES bot_job(id) ON DELETE CASCADE";
                stmt.executeUpdate(addForeignKeySQL8);

                //                String createConfigurationTableSQL = "CREATE TABLE configuration ("
                //                        + "id INTEGER PRIMARY KEY, "
                //                        + "pathJava MEMO, "
                //                        + "logLevel TEXT, "
                //                        + "pathDB TEXT, "
                //                        + "interactionTimeoutSec TEXT, "
                //                        + "pathLog MEMO, "
                //                        + "defaultInstructionStopSeconds TEXT, "
                //                        + "pathReport TEXT, "
                //                        + "browser MEMO, "
                //                        + "dataBaseType TEXT, "
                //                        + "pageUpdateTimeoutSec TEXT, "
                //                        + "pathPriority TEXT, "
                //                        + "pathEngine TEXT, "
                //                        + "pathExcel TEXT, "
                //                        + "pathExport TEXT, "
                //                        + "socketPort TEXT, "
                //                        + "blockLimit TEXT, "
                //                        + "pathJavaFx TEXT)";
                //                stmt.executeUpdate(createConfigurationTableSQL);

                String createComponentBlockTableSQL = "CREATE TABLE component_block ("
                        + "id INTEGER PRIMARY KEY, "
                        + "home_banking_id INTEGER, "
                        + "block_order_number INTEGER NOT NULL, "
                        + "name TEXT NOT NULL, "
                        + "description TEXT, "
                        + "type_id INTEGER, "
                        + "export_file TEXT, "
                        + "active YESNO, "
                        + "wait INTEGER);";
                stmt.executeUpdate(createComponentBlockTableSQL);

                String addForeignKeySQL9 = "ALTER TABLE component_block "
                        + "ADD CONSTRAINT FK_9 FOREIGN KEY (home_banking_id) "
                        + "REFERENCES home_banking(id) ON DELETE CASCADE";
                stmt.executeUpdate(addForeignKeySQL9);

                String createComponentInstructionTableSQL = "CREATE TABLE component_instruction ("
                        + "id INTEGER PRIMARY KEY, "
                        + "instruction_order_number INTEGER NOT NULL, "
                        + "actions MEMO, "
                        + "name TEXT, "
                        + "xpath MEMO, "
                        + "coordinates TEXT, "
                        + "force_coordinates YESNO, "
                        + "iframe_xpath MEMO, "
                        + "tag_name TEXT, "
                        + "shadow_host MEMO, "
                        + "shadow_root MEMO, "
                        + "css_selector MEMO, "
                        + "description TEXT, "
                        + "operation TEXT, "
                        + "optional YESNO, "
                        + "block_marked YESNO, "
                        + "default_value TEXT, "
                        + "action_custom_max_wait_sec INTEGER, "
                        + "on_hold_seconds INTEGER, "
                        + "codified YESNO, "
                        + "export_to_abr YESNO, "
                        + "active YESNO NOT NULL, "
                        + "block_id INTEGER, "
                        + "variable_id INTEGER, "
                        + "parent_id INTEGER, "
                        + "home_banking_id INTEGER);";
                stmt.executeUpdate(createComponentInstructionTableSQL);

                String addForeignKeySQL10 = "ALTER TABLE component_instruction "
                        + "ADD CONSTRAINT FK_10 FOREIGN KEY (block_id) "
                        + "REFERENCES component_block(id) ON DELETE CASCADE";
                stmt.executeUpdate(addForeignKeySQL10);

                String addCompBlkHomeForeignKeySQL = "ALTER TABLE component_instruction "
                        + "ADD CONSTRAINT FK_BLKHomeBank FOREIGN KEY (home_banking_id) "
                        + "REFERENCES home_banking(id) ON DELETE CASCADE";
                stmt.executeUpdate(addCompBlkHomeForeignKeySQL);

                String createComponentReferenceTableSQL = "CREATE TABLE component_reference ("
                        + "id INTEGER PRIMARY KEY, "
                        + "reference_type TEXT, "
                        + "value MEMO, "
                        + "instruction_id INTEGER NOT NULL, "
                        + "home_banking_id INTEGER);";
                stmt.executeUpdate(createComponentReferenceTableSQL);

                String addForeignKeySQL11 = "ALTER TABLE component_reference "
                        + "ADD CONSTRAINT FK_11 FOREIGN KEY (instruction_id) "
                        + "REFERENCES component_instruction(id) ON DELETE CASCADE";
                stmt.executeUpdate(addForeignKeySQL11);

                String addCompReferForeignKeySQL = "ALTER TABLE component_reference "
                        + "ADD CONSTRAINT FK_CompRefer FOREIGN KEY (home_banking_id) "
                        + "REFERENCES home_banking(id) ON DELETE CASCADE";
                stmt.executeUpdate(addCompReferForeignKeySQL);

                String createComponentVariableTableSQL = "CREATE TABLE component_variable ("
                        + "id INTEGER PRIMARY KEY, "
                        + "type TEXT, "
                        + "name TEXT, "
                        + "value MEMO, "
                        + "instruction_id INTEGER, "
                        + "home_banking_id INTEGER);";
                stmt.executeUpdate(createComponentVariableTableSQL);

                String addForeignKeySQL12 = "ALTER TABLE component_variable "
                        + "ADD CONSTRAINT FK_12 FOREIGN KEY (instruction_id) "
                        + "REFERENCES component_instruction(id) ON DELETE CASCADE";
                stmt.executeUpdate(addForeignKeySQL12);

                String addCompVarForeignKeySQL = "ALTER TABLE component_variable "
                        + "ADD CONSTRAINT FK_CompVar FOREIGN KEY (home_banking_id) "
                        + "REFERENCES home_banking(id) ON DELETE CASCADE";
                stmt.executeUpdate(addCompVarForeignKeySQL);
            }
            System.out.println(String.format("Database %s has been created!", dbFile.getName()));
        } catch (SQLException error) {
            System.out.println("initializeDatabase\nError: " + error.getMessage());
        }
    }

    public void changeDbConnectionHibernate() {
        String priorityPath = arPropertyManager.getProperty(ARPropertyEnum.FOLDER_PATH_PRIORITY);
        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);

        if (Strings.isNullOrEmpty(previousDB) || (previousDB != null && !previousDB.equals(dataBaseType))) {
            closeConnection();
            previousDB = dataBaseType;

            if (dataBaseType != null && dataBaseType.equalsIgnoreCase("POSTGRES")) {
                POSTGRES_DB = true;
            } else {
                POSTGRES_DB = false;
            }

            if (priorityPath != null) {

                //            if (priorityPath != null && !priorityPath.isBlank()) {
                //                arPriorities.loadPriorities();
                //            }

                if (POSTGRES_DB) {
                    String dbUrl = CONNECTION_POSTGRES + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
                    sessionFactory = new Configuration()
                            .configure()
                            .setProperty("hibernate.connection.url", dbUrl)
                            .setProperty("hibernate.connection.username", USERNAME)
                            .setProperty("hibernate.connection.password", PASSWORD)
                            .setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                            .setProperty("hibernate.connection.driver_class", "org.postgresql.Driver")
                            .buildSessionFactory();
                    session = sessionFactory.openSession();
                    //                cacheEntitiesFromDB();
                } else {

                    try {
                        String dbPath = arPropertyManager.getProperty(ARPropertyEnum.FOLDER_PATH_DB);
                        if (!dbPath.isBlank()) {
                            File dbFolder = new File(dbPath);
                            dbFolder.mkdirs();
                            String dbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;
                            sessionFactory = new Configuration()
                                    .configure()
                                    .setProperty("hibernate.connection.url", dbUrl)
                                    .buildSessionFactory();
                            session = sessionFactory.openSession();
                            //                    cacheEntitiesFromDB();
                        }
                    } catch (Exception error) {

                    }
                }
            }
        }
    }

    public Connection getConnection() {
        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);

        if (dataBaseType != null && dataBaseType.equalsIgnoreCase("POSTGRES")) {
            POSTGRES_DB = true;
        } else {
            POSTGRES_DB = false;
        }

        try {
            if (conn == null || conn.isClosed()) {
                if (!POSTGRES_DB) {
                    String dbPath = arPropertyManager.getProperty(ARPropertyEnum.FOLDER_PATH_DB);
                    String dbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;
                    ARLogger.getInstance(PerformDataBase.class).info("ACCESS connection URL: " + dbUrl);
                    conn = DriverManager.getConnection(dbUrl);
                } else {
                    String dbUrl = CONNECTION_POSTGRES + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
                    String userDB = USERNAME + " - " + PASSWORD;
                    ARLogger.getInstance(PerformDataBase.class).info("POSTGRES connection URL: " + dbUrl);
                    ARLogger.getInstance(PerformDataBase.class).info("User Details: " + userDB);
                    conn = DriverManager.getConnection(dbUrl, USERNAME, PASSWORD);
                }
            }
        } catch (SQLException error) {
            ARLogger.getInstance(PerformDataBase.class).severe("getConnection Error: " + error.getMessage());
        }

        //        changeDbConnection(previousDB);

        return conn;
    }

    // Handle BLOCK_UPDATE message
    public boolean updateExportAR(InstructionLoadDTO instruction) {
        try (Statement stmt = getConnection().createStatement()) {

            // Update each export_to_abr
            String updateSQL = "UPDATE instruction SET export_to_abr = " + instruction.getExportToABR()
                    + " WHERE id = " + instruction.getBlockId()
                    + " and bot_job_id = " + instruction.getBotJobId();

            int rowsAffected = stmt.executeUpdate(updateSQL);

            if (rowsAffected > 0) {
                ARLogger.getInstance(PerformDataBase.class)
                        .info(String.format(
                                "Instruction updated blockId: %s, Export to AR: %s",
                                instruction.getBlockId(), instruction.getExportToABR()));
            } else {
                ARLogger.getInstance(PerformDataBase.class)
                        .warning(String.format(
                                "updateExportAR - No matching record found to update botJobId: %d blockId: %d",
                                instruction.getBotJobId(), instruction.getBlockId()));

                return false;
            }

            return true;

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe(String.format("Error updateBlockExportFile. Error: %s", e.getMessage()));
        }
        return false;
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
                ARLogger.getInstance(PerformDataBase.class)
                        .warning(String.format(
                                "rollBackBlocksOrder - Block Order Reset for blockId: %d - Name: %s",
                                rollBackBlocksDTO.getBlockId(), rollBackBlocksDTO.getBlockName()));
            } else {
                ARLogger.getInstance(PerformDataBase.class)
                        .warning(String.format(
                                "RollBackBlocks - No matching record found to update for blockId: %d - Name: %s",
                                rollBackBlocksDTO.getBlockId(), rollBackBlocksDTO.getBlockName()));
            }
        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "This BlockId '%d' - Name: %s \n cannot be updated.\nError: %s",
                            rollBackBlocksDTO.getBlockId(), rollBackBlocksDTO.getBlockName(), e.getMessage()));
            return;
        }
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

            ARLogger.getInstance(PerformDataBase.class)
                    .info(String.format("Fetched Block \"%s\"", blockLoadDTO.getName()));

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "Error fetching Block ID %d with BotJob Id %d. Error: %s: ",
                            blockId, botJobId, e.getMessage()));
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

            ARLogger.getInstance(PerformDataBase.class)
                    .info(String.format("Fetched Block \"%s\"", blockLoadDTO.getName()));

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "Error fetching Block ID %d with BotJob Id %d. Error: %s: ",
                            blockId, botJobId, e.getMessage()));
        }

        return blockLoadDTO;
    }

    public boolean updateBotStatus() {
        // SQL query to get the blocks for a specific bot job
        String query = "update bot_job set active = 1";

        // Initialize the necessary data structures

        // Use Statement to execute the query
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {
            return true;

        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe(String.format("Error updating Active = 1 all botjobs\nError: %s", e.getMessage()));
        }

        return false;
    }

    //    private void loadBotJobComplex(BotJobDTO botJob) {
    //        String selectSQL =
    //                " SELECT bot.ID botId, bot.Name botName, blk.ID blockId, blk.Name blockName,
    // blk.block_order_number, "
    //                        + " blockInstr.id blockInstrId, blockInstr.instruction_order_number
    // instructionOrderNumber, blockInstr.actions, "
    //                        + " instr.id instId, instr.reference_type, instr.value"
    //                        + " FROM reference instr "
    //                        + " join instruction blockInstr on blockInstr.id = instr.instruction_id"
    //                        + " join bot_job bot on active = 1 and bot.id = " + botJob.getId()
    //                        + " join block blk on blk.bot_job_id = bot.id "
    //                        + " order by blockInstr.id, blockInstr.instruction_order_number, instr.id";
    //        try (Statement stmt = getConnection().createStatement();
    //                ResultSet rs = stmt.executeQuery(selectSQL)) {
    //
    //            List<ReferenceDTO> instructions = new ArrayList<>();
    //
    //            while (rs.next()) {
    //                String botId = rs.getString("botId");
    //                String botName = rs.getString("botName");
    //                String blockId = rs.getString("blockId");
    //                String blockName = rs.getString("blockName");
    //                String blockOrderNumber = rs.getString("block_order_number");
    //
    //                String blockInstrId = rs.getString("blockInstrId");
    //                String instructionOrderNumber = rs.getString("instructionOrderNumber");
    //                String actions = rs.getString("actions");
    //
    //                String instId = rs.getString("instId");
    //                String referenceType = rs.getString("reference_type");
    //                String value = rs.getString("value");
    //
    //                if (botJob.getId() == Integer.parseInt(botId)) {
    //                    for (BlockDTO block : botJob.getBlocks()) {
    //                        if (block.getId() == Integer.parseInt(blockId)) {
    //                            boolean exist = false;
    //                            for (InstructionLoadDTO blockInstruction : block.getBlockLoopInstructionLoadDTOS()) {
    //                                if (blockInstruction.getId() == Integer.parseInt(blockInstrId)) {
    //                                    for (ReferenceDTO instructionReference :
    //                                            blockInstruction.getInstructionReferenceDTOList()) {
    //                                        if (instructionReference.getId() == Integer.parseInt(instId)
    //                                                && instructionReference
    //                                                        .getReferenceType()
    //                                                        .equalsIgnoreCase(referenceType)
    //                                                && instructionReference
    //                                                        .getValue()
    //                                                        .equalsIgnoreCase(value)) {
    //                                            exist = true;
    //                                            break;
    //                                        }
    //                                    }
    //                                    if (!exist) {
    //                                        ReferenceDTO inst = new ReferenceDTO();
    //                                        inst.setId(Integer.parseInt(instId));
    //                                        inst.setReferenceType(referenceType);
    //                                        inst.setValue(value);
    //                                        instructions.add(inst);
    //                                        break;
    //                                    }
    //                                }
    //                                if (exist) {
    //                                    break;
    //                                }
    //                            }
    //                        }
    //                    }
    //                }
    //
    //                //                System.out.println(String.format(
    //                //                        "%s  %s  %s  %s  %s   %s   %s   %s",
    //                //                        botId, botName, blockId, blockName, blockOrderNumber, referenceType,
    // value));
    //
    //                //               databaseUserDto = new DatabaseUserDTO(
    //                //                        id, jobs, name, url, priority, searchConfig, optionsConfig, username,
    //                // password);
    //            }
    //        } catch (SQLException e) {
    //            System.out.println(e.getMessage());
    //        }
    //        //        jobUserList.clear();
    //        //        loadBotJobData();
    //    }

    public ObservableList<ComboBoxVars> loadWebPageFields(int botJobId) {
        webPageItems.clear();
        String selectSQL = " SELECT  "
                + "  bot.id AS bot_job_id,  "
                + "  b.id AS block_id,  "
                + "  bli.id AS instruction_id,  "
                + "  bli.instruction_order_number,  "
                + "  bli.actions,  "
                + "  bli.name AS instruction_name,  "
                + "  bli.xpath,  "
                + "  bli.operation,      "
                + "  bli.tag_name      "
                + " FROM bot_job bot  "
                + " LEFT JOIN block b ON b.bot_job_id = bot.id  "
                + " JOIN instruction bli ON bli.block_id = b.id  "
                + " where bot.active = 1 and bot.id = " + botJobId
                + "   and operation is null  "
                + "  ORDER BY bot.id, b.block_order_number, bli.instruction_order_number ASC;";

        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                int id = rs.getInt("instruction_id");
                String name = rs.getString("instruction_name").trim();
                String actions = rs.getString("actions").trim();
                Integer blockId = rs.getInt("block_id");
                String tagName = rs.getString("tag_name").trim();
                Integer orderNumber = rs.getInt("instruction_order_number");

                // Filter out "SET", "GET", "CK", adn "H"
                if (actions != null
                        && !actions.equalsIgnoreCase(ARConstants.SET_VALUE)
                        && !actions.equalsIgnoreCase(ARConstants.GET_VALUE)
                        && !actions.equalsIgnoreCase(ARConstants.CHECK_VALUE)
                        && !actions.equalsIgnoreCase(ARConstants.HOLD)) {
                    webPageItems.add(
                            new ComboBoxVars("(" + id + ")" + name, name, id, blockId, -1, -1, tagName, orderNumber));
                }
            }
        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe(String.format(
                            "loadWebPageFields - Error selecting Web Page Fields. Error: %s", e.getMessage()));
        }
        return webPageItems;
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
                ARLogger.getInstance(PerformDataBase.class)
                        .warning(String.format("Migration DB Scripts - RowsUpdated - %s", rowsAffected));
            } else {
                ARLogger.getInstance(PerformDataBase.class).info("Migration DB Scripts - No Rows were updated");
            }
            return rowsAffected;
        } catch (SQLException e) {
            ARLogger.getInstance(PerformDataBase.class).warning("Migration DB Scripts - Error: " + e.getMessage());
        }
        return -1;
    }

    public ErrorMessage dropTablesMigrationScriptsv2_7f() {
        // Build the SQL update statement
        try (Statement stmt = getConnection().createStatement()) {
            int rowsAffected = 0;

            if (POSTGRES_DB) {

                stmt.executeUpdate("DROP TABLE job_run_report;");
                stmt.executeUpdate("DROP TABLE  variable;");
                stmt.executeUpdate("DROP TABLE  instruction_reference;");
                stmt.executeUpdate("DROP TABLE  block_loop_instruction;");

                stmt.executeUpdate("DROP TABLE  saved_instruction_reference;");
                stmt.executeUpdate("DROP TABLE  saved_block_loop_instruction;");
                stmt.executeUpdate("DROP TABLE  saved_blocks;");

                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"blockLoopInstructionSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"blockSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"botJobSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"variableSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"instructionReferenceSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedInstructionReferenceSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"excelReportSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedInstructionReferenceSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedBlockLoopInstructionSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"complexInstructionSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"configurationSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"homeBankingSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedBlockSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"idgen\";");

                // Update the bot_job_id in reference using the instruction_id from

            } else {

                stmt.executeUpdate("DROP TABLE job_run_report;");
                stmt.executeUpdate("DROP TABLE  variable;");
                stmt.executeUpdate("DROP TABLE  instruction_reference;");
                stmt.executeUpdate("DROP TABLE  complex_instruction;");
                stmt.executeUpdate("DROP TABLE  block_loop_instruction;");

                stmt.executeUpdate("DROP TABLE  saved_instruction_reference;");
                stmt.executeUpdate("DROP TABLE  saved_block_loop_instruction;");
                stmt.executeUpdate("DROP TABLE  saved_blocks;");

                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"blockLoopInstructionSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"blockSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"botJobSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"variableSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"instructionReferenceSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedInstructionReferenceSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"excelReportSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedInstructionReferenceSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedBlockLoopInstructionSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"complexInstructionSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"configurationSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"homeBankingSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"savedBlockSeq\";");
                rowsAffected += stmt.executeUpdate("DROP SEQUENCE IF EXISTS \"idgen\";");
            }

            if (rowsAffected > 0) {
                ARLogger.getInstance(PerformDataBase.class)
                        .warning(String.format("Migration DB Scripts - RowsUpdated - %s", rowsAffected));
            } else {
                ARLogger.getInstance(PerformDataBase.class).info("Migration DB Scripts - No Rows were updated");
            }
            return null;
        } catch (SQLException error) {
            ARLogger.getInstance(PerformDataBase.class).warning("Migration DB Scripts - Error: " + error.getMessage());
            return new ErrorMessage(
                    "Error Drop Tables Migration 2.7f", "Error dropping OLD objects", error.getMessage());
        }
    }

    public List<InstructionReferenceLoadDTO> instReferenceToDuplicateOld(
            Connection conn, int oldBotJobId, String targetTable) throws SQLException {
        String query = "SELECT id, reference_type, value, block_loop_instruction_id, bot_job_id "
                + "  FROM " + targetTable
                + "  WHERE bot_job_id = ? "
                + "  ORDER BY id";

        List<InstructionReferenceLoadDTO> referenceDTOList = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, oldBotJobId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                InstructionReferenceLoadDTO referenceDTO = new InstructionReferenceLoadDTO();
                referenceDTO.setId(rs.getInt("id"));
                referenceDTO.setReferenceType(rs.getString("reference_type"));
                referenceDTO.setValue(rs.getString("value"));
                referenceDTO.setBlockLoopInstructionId(rs.getInt("block_loop_instruction_id"));
                referenceDTO.setBotJobId(rs.getInt("bot_job_id"));

                referenceDTOList.add(referenceDTO);
            }
        }

        return referenceDTOList;
    }

    public List<ComplexInstructionLoadDTO> instComplexToDuplicate(
            Connection conn, int oldBotJobId, int oldBlockId, String table1, String table2) throws SQLException {
        String query = " SELECT \n" + "  cp.id, \n"
                + "  cp.instruction, \n"
                + "  cp.order_number, \n"
                + "  cp.way, \n"
                + "  cp.instruction_id, \n"
                + "  cp.bot_job_id \n"
                + "FROM \n"
                + table1 + " cp";

        if (oldBlockId > -1) {
            query += " JOIN " + table2
                    + " bli ON bli.id = cp.instruction_id and cp.bot_job_id = ? and bli.block_id = ?  ";
        } else {
            query += " WHERE cp.bot_job_id = ? ";
        }

        query += " ORDER BY cp.id";

        List<ComplexInstructionLoadDTO> referenceDTOList = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(query)) {

            if (oldBlockId > -1) {
                stmt.setInt(1, oldBotJobId);
                stmt.setInt(2, oldBlockId);
            } else {
                stmt.setInt(1, oldBotJobId);
            }

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ComplexInstructionLoadDTO complexInstructionLoadDTO = new ComplexInstructionLoadDTO();
                complexInstructionLoadDTO.setId(rs.getInt("id")); // Set the ID from complex_instruction
                complexInstructionLoadDTO.setInstructionId(
                        rs.getInt("instruction_id")); // Set the instruction_id as instructionId
                complexInstructionLoadDTO.setBotJobId(rs.getInt("bot_job_id")); // Set bot_job_id
                complexInstructionLoadDTO.setOrderNumber(rs.getInt("order_number"));
                complexInstructionLoadDTO.setInstruction(rs.getString("instruction"));
                complexInstructionLoadDTO.setWay(rs.getString("way"));

                referenceDTOList.add(complexInstructionLoadDTO);
            }
        }

        return referenceDTOList;
    }

    //    public ErrorMessage migration2_6f(Connection conn, int oldBotJobId, int newBotJobId, String[] arrayTables)
    //            throws SQLException {
    //
    //        Map<Integer, Integer> blocksOlderAndNewId = new HashMap<>();
    //
    //        List<BlockLoadDTO> blockList = blocksToDuplicate(conn, oldBotJobId);
    //        if (blockList.size() > 0) {
    //            // tablesMigration = {"block", "block_loop_instruction", "instruction",
    // "instruction_reference","reference",
    //            // "variable"};
    //            // Assuming instList is a List<InstructionLoadDTO> and refersList is a
    // List<InstructionReferenceLoadDTO>
    //            for (BlockLoadDTO block : blockList) {
    //                blocksOlderAndNewId.put(block.getId(), block.getId());
    //                block.setId(block.getId());
    //                block.setBotJobId(newBotJobId);
    //            }
    //        }
    //
    //        Map<Integer, Integer> parentOlderAndNewId = new HashMap<>();
    //        Map<Integer, Integer> variableOlderAndNewId = new HashMap<>();
    //
    //        //  "block", "block_loop_instruction", "instruction", "instruction_reference", "reference", "variable"
    //        List<InstructionLoadDTO> instList = instructionsToDuplicate(
    //                conn, oldBotJobId, -1, arrayTables[1], arrayTables[0]); // "block_loop_instruction", "instruction"
    //
    //        List<VariableLoadDTO> varsList = instVariablesToDuplicateOLD(conn, oldBotJobId, arrayTables[5]);
    //
    //        // tablesMigration = {"block", "block_loop_instruction", "instruction",
    // "instruction_reference","reference",
    //        // "variable"};
    //        if (varsList.size() > 0) {
    //            for (VariableLoadDTO variable : varsList) {
    //                if (!variableOlderAndNewId.containsKey(variable.getId())) {
    //                    variableOlderAndNewId.put(variable.getId(), variable.getId());
    //                    variable.setId(variable.getId());
    //                }
    //            }
    //        }
    //
    //        if (instList.size() > 0) {
    //
    //            // Prepare the Ids
    //            // tablesMigration = {"block", "block_loop_instruction", "instruction",
    // "instruction_reference","reference",
    //            // "variable"};
    //            //            int currentId = getMaxId(conn, arrayTables[2]) + 1;
    //            for (InstructionLoadDTO instruction : instList) {
    //                instruction.setInstructionId(instruction.getId()); // Holds the News Ids
    //                instruction.setBotJobId(newBotJobId); // Holds the News Ids
    //
    //                if (!parentOlderAndNewId.containsKey(instruction.getParentId())) {
    //                    parentOlderAndNewId.put(instruction.getId(), instruction.getId());
    //                }
    //
    //                // Loop through the instList and find a matching InstructionLoadDTO
    //                for (BlockLoadDTO block : blockList) {
    //                    if (instruction.getBlockOrderNumber().equals(block.getBlockOrderNumber())) {
    //                        // Once found, update the blockLoopInstructionId with the new instructionId
    //                        instruction.setBlockId(block.getId());
    //                        break; // Exit the inner loop since we've found a match
    //                    }
    //                }
    //                //                currentId++;
    //            }
    //            // Duplicate instruction
    //            // tablesMigration = {"block", "block_loop_instruction", "instruction",
    // "instruction_reference","reference",
    //            // "variable"};
    //            ErrorMessage errorMessage = duplicateBlockLoopInstructions(
    //                    conn, instList, parentOlderAndNewId, variableOlderAndNewId, blocksOlderAndNewId,
    // arrayTables[2]);
    //            if (errorMessage != null) {
    //                return errorMessage;
    //            }
    //
    //            if (varsList.size() > 0) {
    //
    //                // Assuming instList is a List<InstructionLoadDTO> and refersList is a
    // List<InstructionReferenceLoadDTO>
    //                for (VariableLoadDTO variable : varsList) {
    //                    //                    variable.setId(currentVarId);
    //
    //                    // Loop through the instList and find a matching InstructionLoadDTO
    //                    for (InstructionLoadDTO instruction : instList) {
    //                        if (variable.getInstructionId().equals(instruction.getId())) {
    //                            // Once found, update the blockLoopInstructionId with the new instructionId
    //                            variable.setInstructionId(instruction.getInstructionId());
    //                            variable.setBotJobId(newBotJobId);
    //                            break; // Exit the inner loop since we've found a match
    //                        }
    //                    }
    //                    //                    currentVarId++;
    //                }
    //
    //                // Update variables
    //                // tablesMigration = {"block", "block_loop_instruction", "instruction", "instruction_reference",
    //                // "reference", "variable"};
    //                errorMessage = updateVariables(conn, varsList, arrayTables[5]);
    //                if (errorMessage != null) {
    //                    return errorMessage;
    //                }
    //            }
    //
    //            // tablesMigration = {"block", "block_loop_instruction", "instruction", "instruction_reference",
    //            // "reference", "variable"};
    //            List<InstructionReferenceLoadDTO> refersList =
    //                    instReferenceToDuplicateOld(conn, oldBotJobId, arrayTables[3]);
    //            if (refersList.size() > 0) {
    //
    //                //                currentId = getMaxId(conn, arrayTables[2]) + 1;
    //
    //                // Assuming instList is a List<InstructionLoadDTO> and refersList is a
    // List<InstructionReferenceLoadDTO>
    //                for (InstructionReferenceLoadDTO reference : refersList) {
    //                    //                    reference.setId(currentId++);
    //
    //                    // Loop through the instList and find a matching InstructionLoadDTO
    //                    for (InstructionLoadDTO instruction : instList) {
    //                        if (reference.getBlockLoopInstructionId().equals(instruction.getId())) {
    //                            // Once found, update the blockLoopInstructionId with the new instructionId
    //                            reference.setBlockLoopInstructionId(instruction.getInstructionId());
    //                            reference.setBotJobId(newBotJobId);
    //                            break; // Exit the inner loop since we've found a match
    //                        }
    //                    }
    //                }
    //
    //                // Duplicate reference
    //                // tablesMigration = {"block", "block_loop_instruction", "instruction", "instruction_reference",
    //                // "reference", "variable"};
    //                errorMessage = duplicateInstructionReferences(conn, refersList, arrayTables[4]);
    //                if (errorMessage != null) {
    //                    return errorMessage;
    //                }
    //            }
    //        }
    //        return null;
    //    }

    private ErrorMessage duplicateComplexInstructions(
            Connection conn, List<ComplexInstructionLoadDTO> complexList, String targetTable) throws SQLException {
        // Prepare the insert statement for complex instructions
        String complexInstructionInsertQuery = "INSERT INTO " + targetTable
                + " (instruction, order_number, way, instruction_id, bot_job_id) " + "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement complexStmt = conn.prepareStatement(complexInstructionInsertQuery)) {
            // Loop through each ComplexInstructionLoadDTO in the complexList
            for (ComplexInstructionLoadDTO complexInstruction : complexList) {
                // Set the parameters for the INSERT statement
                complexStmt.setString(1, complexInstruction.getInstruction());
                complexStmt.setInt(2, complexInstruction.getOrderNumber());
                complexStmt.setString(3, complexInstruction.getWay());
                complexStmt.setInt(4, complexInstruction.getInstructionId()); // Assuming you have the updated ID
                complexStmt.setInt(5, complexInstruction.getBotJobId()); // Set the new bot job ID

                // Execute the insert statement for each complex instruction
                // complexStmt.executeUpdate();

                complexStmt.addBatch(); // Add to batch
            }

            complexStmt.executeBatch(); // Execute the batch insert
            return null;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return new ErrorMessage(
                    "Error Duplicating Complex Instructions", "Block Insertion Failure", e.getMessage());
        }
    }

    private ErrorMessage updateVariables(Connection conn, List<VariableLoadDTO> varsList, String targetTable)
            throws SQLException {
        String variableInsertQuery = "update " + targetTable + " set instruction_id = ?  where id = ?";

        try (PreparedStatement varStmt = conn.prepareStatement(variableInsertQuery)) {
            for (VariableLoadDTO variableDTO : varsList) {
                varStmt.setInt(1, variableDTO.getInstructionId());
                varStmt.setInt(2, variableDTO.getId());

                varStmt.addBatch(); // Add to batch
            }

            varStmt.executeBatch(); // Execute the batch insert
            return null;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return new ErrorMessage("Error Duplicating Variables", "Block Insertion Failure", e.getMessage());
        }
    }

    public List<InstructionLoadDTO> filterInstructions(List<InstructionLoadDTO> instructionList) {
        return instructionList.stream()
                .filter(instruction -> !ARConstants.EXTRACT_FIELD.equals(instruction.getActions())
                        && !ARConstants.SET_VALUE.equals(instruction.getActions())
                        && !ARConstants.GET_VALUE.equals(instruction.getActions())
                        && !ARConstants.CHECK_VALUE.equals(instruction.getActions())
                        && !ARConstants.GOTO.equals(instruction.getActions())
                        && !ARConstants.IF.equals(instruction.getActions())
                        && !ARConstants.ELSE.equals(instruction.getActions())
                        && !ARConstants.ENDIF.equals(instruction.getActions()))
                .collect(Collectors.toList());
    }

    private void deleteBlockInstruction(int instructionId) throws SQLException {
        String deleteBlockInstruction = "delete FROM instruction " + " where id = " + instructionId;

        try (Statement stmt = getConnection().createStatement()) {
            int rowsAffected = stmt.executeUpdate(deleteBlockInstruction);
            if (rowsAffected > 0) {
                ARLogger.getInstance(PerformDataBase.class).finer("Data deleted successfully for: " + instructionId);
            } else {
                ARLogger.getInstance(PerformDataBase.class)
                        .finer("No matching record found to delete for: " + instructionId);
            }
        }
    }

    private void deleteInstrReference(int instructionId) throws SQLException {
        String deleteSQL = "delete FROM reference " + " where instruction_id =  " + instructionId;

        try (Statement stmt = getConnection().createStatement()) {
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ARLogger.getInstance(PerformDataBase.class).finer("Data deleted successfully for: " + instructionId);
            } else {
                ARLogger.getInstance(PerformDataBase.class)
                        .finer("No matching record found to delete for: " + instructionId);
            }
        }
    }

    private boolean existVariables(int instructionId) throws SQLException {
        String query = "select id FROM variable " + " where instruction_id =  " + instructionId;
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                return true;
            }
        }

        return false;
    }

    private void forceDeleteOrphan(int instructionId) throws SQLException {
        String deleteSQL = "delete FROM reference " + " where instruction_id is null ";

        try (Statement stmt = getConnection().createStatement()) {
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ARLogger.getInstance(PerformDataBase.class).finer("Data deleted successfully for: " + instructionId);
            } else {
                ARLogger.getInstance(PerformDataBase.class)
                        .finer("No matching record found to delete for: " + instructionId);
            }
        }
    }

    private void forceDeleteFatherNoChild(int instructionId) throws SQLException {
        String deleteSQL = "DELETE FROM instruction " + "WHERE id IN ( "
                + "    SELECT bli.id "
                + "    FROM instruction bli "
                + "    LEFT JOIN reference irl ON irl.instruction_id = bli.id "
                + "    WHERE irl.id IS NULL "
                + "    AND bli.name NOT IN ('Check', 'GetValue', 'SetValue')"
                + ") ";

        try (Statement stmt = getConnection().createStatement()) {
            int rowsAffected = stmt.executeUpdate(deleteSQL);
            if (rowsAffected > 0) {
                ARLogger.getInstance(PerformDataBase.class).finer("Data deleted successfully for: " + instructionId);
            } else {
                ARLogger.getInstance(PerformDataBase.class)
                        .finer("No matching record found to delete for: " + instructionId);
            }
        }
    }

    public ObservableList<VariableUserDTO> loadAllVariablesByCriteria(int botJobId, int parentId) {
        variablesList.clear();
        String selectSQL = "SELECT vars.id, vars.type, vars.name, vars.value, COUNT(blk.variable_id) UsedVars "
                + "FROM variable vars "
                + "LEFT JOIN instruction blk ON blk.variable_id = vars.id "
                + "WHERE vars.bot_job_id = " + botJobId;

        if (parentId != -1) { // Check if instructionId is provided (not -1)
            selectSQL += " AND instruction_id = " + parentId;
        }

        selectSQL += " GROUP BY vars.id, vars.type, vars.Name, vars.value";

        try (Statement stmt = getConnection().createStatement(); // Assuming you have getConnection() method
                ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                int id = rs.getInt("ID");
                String type = rs.getString("type");
                String name = rs.getString("name");
                String value = rs.getString("value");
                String usedVars = rs.getString("UsedVars");
                variablesList.add(new VariableUserDTO(id, type, name, value, botJobId, parentId, usedVars));
            }
            return variablesList;
        } catch (SQLException e) {
            // Handle the exception properly (log, throw, etc.)
            ARLogger.getInstance(PerformDataBase.class).severe("loadAllVariblesByCriteria  \nError: " + e.getMessage());
        }
        return null;
    }

    public List<VariableLoadDTO> loadAllVariables(int botJobId) {
        List<VariableLoadDTO> variablesLoadList = new ArrayList<>();
        String selectSQL =
                "SELECT vars.id, instruction_id, vars.type, vars.name, vars.value, COUNT(blk.variable_id) UsedVars "
                        + "FROM variable vars "
                        + "LEFT JOIN instruction blk ON blk.variable_id = vars.id "
                        + "WHERE vars.bot_job_id = " + botJobId;

        selectSQL += " GROUP BY vars.id, vars.type, vars.Name, vars.value";

        try (Statement stmt = getConnection().createStatement(); // Assuming you have getConnection() method
                ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                Integer instructionId = rs.getInt("instruction_id");
                String type = rs.getString("type");
                String name = rs.getString("name");
                String value = rs.getString("value");
                Integer usedVars = rs.getInt("UsedVars");
                variablesLoadList.add(
                        new VariableLoadDTO(id, -1, botJobId, instructionId, type, name, value, usedVars));
            }
            return variablesLoadList;
        } catch (SQLException error) {
            ARLogger.getInstance(PerformDataBase.class).severe("loadAllVariables  \nError: " + error.getMessage());
        }
        return null;
    }

    public void updateUserData(Integer userId, VariableUserDTO user) {
        //        try {
        String updateSQL = "UPDATE variable SET Name = '" + user.getName() + "', "
                + " type = '" + user.getType() + "', "
                + " value = '" + user.getValue() + "' "
                + " WHERE ID = " + userId;
        try (Statement stmt = getConnection().createStatement()) {
            int rowsAffected = stmt.executeUpdate(updateSQL);
            if (rowsAffected > 0) {
                System.out.println("Data updated successfully.");
            } else {
                System.out.println("No matching record found to update.");
            }
        } catch (SQLException e) {
            //            performMessage.errorMessage(
            //                    "MAX CHARACTERS LIMIT FOR ACCESS",
            //                    String.format("The '%s' cannot be updated.", user.getName()),
            //                    e.getMessage(),
            //                    null,
            //                    null,
            //                    0);

            return;
        }
        //        } catch (NumberFormatException e) {
        //            System.out.println("Invalid ID format.");
        //        }
    }

    public void deleteUserData(String Id) {
        try {
            int variableId = Integer.parseInt(Id);
            String deleteSQL = "DELETE FROM variable WHERE ID = " + variableId;
            try (Statement stmt = getConnection().createStatement()) {
                int rowsAffected = stmt.executeUpdate(deleteSQL);
                if (rowsAffected > 0) {
                    System.out.println("Data deleted successfully.");
                } else {
                    System.out.println("No matching record found to delete.");
                }
            } catch (SQLException e) {
                //                performMessage.errorMessage(
                //                        "Error Deleting",
                //                        String.format("Cannot be deleted id: '%s'", Id),
                //                        e.getMessage(),
                //                        null,
                //                        null,
                //                        0);
            }
        } catch (NumberFormatException e) {
            //            performMessage.errorMessage(
            //                    "Invalid ID format.", String.format("The id: '%s' is in invalid format!", Id), null,
            // null, null, 0);
        }
    }

    private void loadJobVariables(RowMoveDTO rowMoveDTO, int instructionId) {
        variablesList.clear();
        String selectSQL = " SELECT vars.id, vars.type, vars.name, vars.value, COUNT(blk.variable_id) UsedVars "
                + " FROM variable vars "
                + " left join instruction blk on blk.variable_id = vars.id "
                + " where vars.bot_job_id = " + rowMoveDTO.getBotJobId()
                + " and  instruction_id = " + instructionId
                + " group by vars.id, vars.type, vars.Name, vars.value ";
        try (Statement stmt = getConnection().createStatement();
                ResultSet rs = stmt.executeQuery(selectSQL)) {
            while (rs.next()) {
                Integer id = rs.getInt("ID");
                String type = rs.getString("type");
                String name = rs.getString("name");
                String value = rs.getString("value");
                String usedVars = rs.getString("UsedVars");
                variablesList.add(
                        new VariableUserDTO(id, type, name, value, rowMoveDTO.getBotJobId(), instructionId, usedVars));
            }
        } catch (SQLException e) {
            //            performMessage.errorMessage(
            //                    "Error loading Variables", "Could Not Load the Variables", e.getMessage(), null, null,
            // 0);
        }
    }

    public void dataBaseInUse(String errorMessage) {
        if (errorMessage.contains("UCAExc:::5.0.1") && errorMessage.contains("The table data is read only")) {

            performMessage.errorMessage(
                    "Database In Use", "The database is currently in use by another application", null, null, null, 0);
        }
    }
}
