package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import java.io.File;
import java.sql.*;
import lombok.Getter;
import lombok.Setter;

/**
 * PerformActions.
 *
 * @author Osvaldo Martini
 * @version 1.0
 */
public class PerformInitializer {
    protected static volatile PerformInitializer instance;

    @Getter
    @Setter
    public Connection conn = null;

    // Private constructor to prevent instantiation
    private PerformInitializer() {
        // Initialize if necessary
    }

    public static PerformInitializer getInstance() {
        if (instance == null) {
            synchronized (PerformInitializer.class) {
                if (instance == null) {
                    instance = new PerformInitializer();
                }
            }
        }
        return instance;
    }

    public final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    public final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    public final String CONNECTION_TYPE_SQLITE = "jdbc:sqlite:"; // no parameters needed

    private static final PerformDataBase performDataBase;

    static {
        performDataBase = PerformDataBase.getInstance();
    }

    public void initialize(Connection conn) {
        this.conn = conn;
    }

    public void initializeMainDatabasePostgres() {
        try (Connection conn = performDataBase.getConnection()) {
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
                String createURLTableSQL = "CREATE TABLE home_url ("
                        + "ID SERIAL PRIMARY KEY, "
                        + "url TEXT, "
                        + "home_banking_id INTEGER REFERENCES home_banking(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createURLTableSQL);

                // Create bot_job table with a foreign key reference to home_banking
                String createBotJobTableSQL = "CREATE TABLE bot_job ("
                        + "id SERIAL PRIMARY KEY, "
                        + "name TEXT UNIQUE, "
                        + "description TEXT, "
                        + "priority TEXT, "
                        + "active INTEGER NOT NULL, "
                        + "home_banking_id INTEGER REFERENCES home_banking(id) ON DELETE CASCADE, "
                        + "home_url_id INTEGER REFERENCES home_url(id) ON DELETE CASCADE)";
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
                        + "local_format TEXT,"
                        + "delimiter TEXT,"
                        + "instruction_id INTEGER REFERENCES instruction(id) ON DELETE CASCADE, "
                        + "bot_job_id INTEGER REFERENCES bot_job(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createVariableTableSQL);

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
                        + "local_format TEXT,"
                        + "delimiter TEXT,"
                        + "instruction_id INTEGER REFERENCES component_instruction(id) ON DELETE CASCADE, "
                        + "home_banking_id INTEGER REFERENCES home_banking(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createComponentVariableTableSQL);
            }
            System.out.println("Database %s has been created!");
        } catch (SQLException error) {
            System.out.println("initializeDatabase\nError: " + error.getMessage());
        }
    }

    public void initializeMainDatabaseAccess(File dbFile) {

        try (Connection conn = performDataBase.getConnection()) {
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
                String createURLTableSQL = "CREATE TABLE home_url ("
                        + "ID INTEGER PRIMARY KEY, "
                        + "url MEMO, "
                        + "home_banking_id INTEGER);";
                stmt.executeUpdate(createURLTableSQL);

                String addURLForeignKeySQL = "ALTER TABLE home_url "
                        + "ADD CONSTRAINT FK_URL FOREIGN KEY (home_banking_id) "
                        + "REFERENCES home_banking(id) ";
                stmt.executeUpdate(addURLForeignKeySQL);

                // Create bot_job table with a foreign key reference to home_banking
                String createBotJobTableSQL = "CREATE TABLE bot_job ("
                        + "id INTEGER PRIMARY KEY, "
                        + "name TEXT UNIQUE, "
                        + "description TEXT, "
                        + "priority MEMO, "
                        + "active YESNO NOT NULL, "
                        + "home_banking_id INTEGER, "
                        + "home_url_id INTEGER);";
                stmt.executeUpdate(createBotJobTableSQL);

                String addBotJobForeignKeySQL = "ALTER TABLE bot_job "
                        + "ADD CONSTRAINT FK_BotJob FOREIGN KEY (home_banking_id) "
                        + "REFERENCES home_banking(id) ";
                stmt.executeUpdate(addBotJobForeignKeySQL);

                String addHomrURLForeignKeySQL = "ALTER TABLE bot_job "
                        + "ADD CONSTRAINT FK_HomeUrl FOREIGN KEY (home_url_id) "
                        + "REFERENCES home_url(id) ";
                stmt.executeUpdate(addHomrURLForeignKeySQL);

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
                        + "REFERENCES bot_job(id) ";
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
                        + "REFERENCES block(id) ";
                stmt.executeUpdate(addForeignKeySQL3);

                String addForeignKeySQL4 = "ALTER TABLE instruction "
                        + "ADD CONSTRAINT FK_4 FOREIGN KEY (bot_job_id) "
                        + "REFERENCES bot_job(id) ";
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
                        + "REFERENCES instruction(id) ";
                stmt.executeUpdate(addForeignKeySQL5);

                String addForeignKeySQL6 = "ALTER TABLE reference "
                        + "ADD CONSTRAINT FK_6 FOREIGN KEY (bot_job_id) "
                        + "REFERENCES bot_job(id) ";
                stmt.executeUpdate(addForeignKeySQL6);

                String createVariableTableSQL = "CREATE TABLE variable ("
                        + "id INTEGER PRIMARY KEY, "
                        + "type TEXT, "
                        + "name TEXT, "
                        + "value MEMO, "
                        + "instruction_id INTEGER, "
                        + "bot_job_id INTEGER,"
                        + "local_format TEXT,"
                        + "delimiter TEXT);";
                stmt.executeUpdate(createVariableTableSQL);

                String addForeignKeySQL7 = "ALTER TABLE variable "
                        + "ADD CONSTRAINT FK_7 FOREIGN KEY (instruction_id) "
                        + "REFERENCES instruction(id) ";
                stmt.executeUpdate(addForeignKeySQL7);

                String addForeignKeySQL8 = "ALTER TABLE variable "
                        + "ADD CONSTRAINT FK_8 FOREIGN KEY (bot_job_id) "
                        + "REFERENCES bot_job(id) ";
                stmt.executeUpdate(addForeignKeySQL8);

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
                        + "REFERENCES home_banking(id) ";
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
                        + "REFERENCES component_block(id) ";
                stmt.executeUpdate(addForeignKeySQL10);

                String addCompBlkHomeForeignKeySQL = "ALTER TABLE component_instruction "
                        + "ADD CONSTRAINT FK_BLKHomeBank FOREIGN KEY (home_banking_id) "
                        + "REFERENCES home_banking(id) ";
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
                        + "REFERENCES component_instruction(id) ";
                stmt.executeUpdate(addForeignKeySQL11);

                String addCompReferForeignKeySQL = "ALTER TABLE component_reference "
                        + "ADD CONSTRAINT FK_CompRefer FOREIGN KEY (home_banking_id) "
                        + "REFERENCES home_banking(id) ";
                stmt.executeUpdate(addCompReferForeignKeySQL);

                String createComponentVariableTableSQL = "CREATE TABLE component_variable ("
                        + "id INTEGER PRIMARY KEY, "
                        + "type TEXT, "
                        + "name TEXT, "
                        + "value MEMO, "
                        + "instruction_id INTEGER, "
                        + "home_banking_id INTEGER,"
                        + "local_format TEXT,"
                        + "delimiter TEXT);";
                stmt.executeUpdate(createComponentVariableTableSQL);

                String addForeignKeySQL12 = "ALTER TABLE component_variable "
                        + "ADD CONSTRAINT FK_12 FOREIGN KEY (instruction_id) "
                        + "REFERENCES component_instruction(id) ";
                stmt.executeUpdate(addForeignKeySQL12);

                String addCompVarForeignKeySQL = "ALTER TABLE component_variable "
                        + "ADD CONSTRAINT FK_CompVar FOREIGN KEY (home_banking_id) "
                        + "REFERENCES home_banking(id) ";
                stmt.executeUpdate(addCompVarForeignKeySQL);
            }
            System.out.printf("Database %s has been created!%n", dbFile.getName());
        } catch (SQLException error) {
            System.out.println("initializeDatabase\nError: " + error.getMessage());
        }
    }

    public void initializeMainDatabaseSQLite(File dbFile) {
        try (Connection conn = performDataBase.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                // Enable foreign keys in SQLite
                stmt.execute("PRAGMA foreign_keys = ON;");

                String createHomeBankingTableSQL = "CREATE TABLE home_banking ("
                        + "ID INTEGER PRIMARY KEY AUTOINCREMENT, "
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

                String createURLTableSQL = "CREATE TABLE home_url ("
                        + "ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "url TEXT, "
                        + "home_banking_id INTEGER, "
                        + "FOREIGN KEY(home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createURLTableSQL);

                String createBotJobTableSQL = "CREATE TABLE bot_job ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "name TEXT UNIQUE, "
                        + "description TEXT, "
                        + "priority TEXT, "
                        + "active INTEGER NOT NULL, "
                        + "home_banking_id INTEGER, "
                        + "home_url_id INTEGER, "
                        + "FOREIGN KEY(home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY(home_url_id) REFERENCES home_url(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createBotJobTableSQL);

                String createBlockTableSQL = "CREATE TABLE block ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "block_order_number INTEGER NOT NULL, "
                        + "name TEXT NOT NULL, "
                        + "description TEXT, "
                        + "type_id INTEGER, "
                        + "export_file TEXT, "
                        + "active INTEGER NOT NULL, "
                        + "wait INTEGER, "
                        + "bot_job_id INTEGER, "
                        + "FOREIGN KEY(bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createBlockTableSQL);

                String createInstructionTableSQL = "CREATE TABLE instruction ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
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
                        + "block_id INTEGER, "
                        + "variable_id INTEGER, "
                        + "parent_id INTEGER, "
                        + "bot_job_id INTEGER, "
                        + "FOREIGN KEY(block_id) REFERENCES block(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY(bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createInstructionTableSQL);

                String createReferenceTableSQL = "CREATE TABLE reference ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "reference_type TEXT, "
                        + "value TEXT, "
                        + "instruction_id INTEGER NOT NULL, "
                        + "bot_job_id INTEGER, "
                        + "FOREIGN KEY(instruction_id) REFERENCES instruction(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY(bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createReferenceTableSQL);

                String createVariableTableSQL = "CREATE TABLE variable ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "type TEXT, "
                        + "name TEXT, "
                        + "value TEXT, "
                        + "local_format TEXT, "
                        + "delimiter TEXT, "
                        + "instruction_id INTEGER, "
                        + "bot_job_id INTEGER, "
                        + "FOREIGN KEY(instruction_id) REFERENCES instruction(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY(bot_job_id) REFERENCES bot_job(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createVariableTableSQL);

                String createComponentBlockTableSQL = "CREATE TABLE component_block ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "home_banking_id INTEGER, "
                        + "block_order_number INTEGER NOT NULL, "
                        + "name TEXT NOT NULL, "
                        + "description TEXT, "
                        + "type_id INTEGER, "
                        + "export_file TEXT, "
                        + "active INTEGER, "
                        + "wait INTEGER, "
                        + "FOREIGN KEY(home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createComponentBlockTableSQL);

                String createComponentInstructionTableSQL = "CREATE TABLE component_instruction ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
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
                        + "block_id INTEGER, "
                        + "variable_id INTEGER, "
                        + "parent_id INTEGER, "
                        + "home_banking_id INTEGER, "
                        + "FOREIGN KEY(block_id) REFERENCES component_block(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY(home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createComponentInstructionTableSQL);

                String createComponentReferenceTableSQL = "CREATE TABLE component_reference ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "reference_type TEXT, "
                        + "value TEXT, "
                        + "instruction_id INTEGER NOT NULL, "
                        + "home_banking_id INTEGER, "
                        + "FOREIGN KEY(instruction_id) REFERENCES component_instruction(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY(home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createComponentReferenceTableSQL);

                String createComponentVariableTableSQL = "CREATE TABLE component_variable ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "type TEXT, "
                        + "name TEXT, "
                        + "value TEXT, "
                        + "local_format TEXT, "
                        + "delimiter TEXT, "
                        + "instruction_id INTEGER, "
                        + "home_banking_id INTEGER, "
                        + "FOREIGN KEY(instruction_id) REFERENCES component_instruction(id) ON DELETE CASCADE, "
                        + "FOREIGN KEY(home_banking_id) REFERENCES home_banking(id) ON DELETE CASCADE)";
                stmt.executeUpdate(createComponentVariableTableSQL);
            }

            System.out.printf("SQLite Database %s has been created!%n", dbFile.getName());
        } catch (SQLException error) {
            System.out.println("initializeDatabase\nError: " + error.getMessage());
        }
    }

    public boolean doesNotInstructionTableExistAccess(Connection conn) throws SQLException {
        try {
            if (conn != null && conn.getMetaData() != null) {
                setConn(conn);
                try (ResultSet rs = conn.getMetaData().getTables(null, null, "instruction", null)) {
                    return !rs.next(); // true if the table does NOT exist
                }
            }
        } catch (SQLException error) {
            System.out.println("Error checking table existence: " + error.getMessage());
            throw error; // rethrow so caller handles it
        } catch (Exception error) {
            System.out.println("Unexpected error while checking table existence: " + error.getMessage());
            throw new SQLException("Unexpected error while checking table existence", error);
        }

        return true; // Assume table does not exist if no valid connection or metadata
    }

    public boolean doesNotInstructionTableExist(Connection conn) throws SQLException {
        try {
            if (conn != null && conn.getMetaData() != null) {
                setConn(conn);
                try (ResultSet rs = conn.getMetaData().getTables(null, null, "instruction", null)) {
                    return !rs.next(); // true if the table does NOT exist
                }
            }
        } catch (SQLException error) {
            System.out.println("SQL error while checking table existence: " + error.getMessage());
            throw error;
        } catch (Exception error) {
            System.out.println("Unexpected error while checking table existence: " + error.getMessage());
            throw new SQLException("Unexpected error while checking table existence", error);
        }

        return true; // assume table does not exist if no connection or metadata
    }

    public boolean doesNotInstructionTableExistSQLITE(Connection conn) throws SQLException {
        try {
            if (conn != null && conn.getMetaData() != null) {
                try (ResultSet rs = conn.getMetaData().getTables(null, null, "instruction", null)) {
                    return !rs.next(); // true if the table does NOT exist
                }
            }
        } catch (SQLException error) {
            System.out.println("SQL error while checking SQLite table existence: " + error.getMessage());
            throw error;
        } catch (Exception error) {
            System.out.println("Unexpected error while checking SQLite table existence: " + error.getMessage());
            throw new SQLException("Unexpected error while checking SQLite table existence", error);
        }

        return true;
    }

    public void testConnection(String dataBaseType, String dbUrlPath, String dbUrl, String userDB, String userPwd)
            throws SQLException, ClassNotFoundException {

        if (dataBaseType == null) {
            throw new IllegalArgumentException("Database type is required.");
        }

        if ("Postgres".equalsIgnoreCase(dataBaseType)) {
            // PostgreSQL
            Class.forName("org.postgresql.Driver");
            DriverManager.getConnection(dbUrl, userDB, userPwd);

        } else if ("SQLite".equalsIgnoreCase(dataBaseType)) {
            // SQLite
            String dbSQLiteUrl = CONNECTION_TYPE_SQLITE + dbUrlPath + ARConstants.FILE_NAME_SQLITE;
            ARLogger.getInstance(PerformDataBase.class).info("SQLite connection URL: " + dbSQLiteUrl);
            Class.forName("org.sqlite.JDBC");
            DriverManager.getConnection(dbUrl);

        } else {
            // Default to Access
            String dbAccessUrl = CONNECTION_TYPE + dbUrlPath + ARConstants.FILE_NAME_ACCESS + CONNECTION_PARAMETERS;
            ARLogger.getInstance(PerformDataBase.class).info("Access connection URL: " + dbAccessUrl);
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            DriverManager.getConnection(dbAccessUrl);
        }
    }
}
