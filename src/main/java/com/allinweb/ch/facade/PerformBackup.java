package com.allinweb.ch.facade;


import com.allinweb.ch.util.ErrorMessage;
import java.io.*;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * PerformBackup.
 *
 * @author Osvaldo Martini
 * @version 1.0
 */
import lombok.extern.slf4j.Slf4j;

  @Slf4j public class PerformBackup {
    protected static volatile PerformBackup instance;

    @Getter
    @Setter
    public Connection conn = null;

    private TreeMap<Integer, Integer> homeBankMap = new TreeMap<>();
    private TreeMap<Integer, Integer> homeUrlMap = new TreeMap<>();
    private TreeMap<Integer, Integer> botJobMap = new TreeMap<>();
    private TreeMap<Integer, Integer> blockMap = new TreeMap<>();
    private TreeMap<Integer, Integer> instructionMap = new TreeMap<>();
    private TreeMap<Integer, Integer> instrVariablesMap = new TreeMap<>();
    private TreeMap<Integer, Integer> instrParentBlockMap = new TreeMap<>();
    private TreeMap<Integer, Integer> instrParentIdMap = new TreeMap<>();
    private TreeMap<Integer, Integer> instrNewInverted = new TreeMap<>();
    private TreeMap<Integer, Integer> variableMap = new TreeMap<>();
    private TreeMap<Integer, Integer> referenceMap = new TreeMap<>();

    // Private constructor to prevent instantiation
    private PerformBackup() {}

    public static PerformBackup getInstance() {
        if (instance == null) {
            synchronized (PerformBackup.class) {
                if (instance == null) {
                    instance = new PerformBackup();
                }
            }
        }
        return instance;
    }

    public void initialize(Connection conn) {
        this.conn = conn;
    }

    private String escapeSql(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("'", "''");
    }

    public ErrorMessage backupHomeBanking(Connection conn, String backupFilePath) {
        String query =
                """
                SELECT id, url, name, priority, search_config, options_config,
                       cookies, driver_session, username, password
                FROM home_banking
                ORDER BY id ASC
                """;

        File sqlFile = new File(backupFilePath);

        try (PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String url = toSqlValue(rs.getString("url"));
                String name = toSqlValue(rs.getString("name"));
                String priority = toSqlValue(rs.getString("priority"));
                String searchConfig = toSqlValue(rs.getString("search_config"));
                String optionsConfig = toSqlValue(rs.getString("options_config"));
                String cookies = toSqlValue(rs.getString("cookies"));
                String driverSession = toSqlValue(rs.getString("driver_session"));
                String username = toSqlValue(rs.getString("username"));
                String password = toSqlValue(rs.getString("password"));

                // Compose SQL insert using '[null]' as string literal when applicable
                String insert = String.format(
                        "INSERT INTO home_banking (id, url, name, priority, search_config, options_config, cookies, driver_session, username, password) "
                                + "VALUES (%d, '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s');",
                        id,
                        url,
                        name,
                        priority,
                        searchConfig,
                        optionsConfig,
                        cookies,
                        driverSession,
                        username,
                        password);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            
                    log.info("HomeBanking backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            
                    log.error("Error during home_banking backup: " + error.getMessage());
            return new ErrorMessage("Error in backup process", "Error during home_banking backup", error.getMessage());
        }
    }

    public ErrorMessage backupHomeUrl(Connection conn, String backupFilePath) {
        String query =
                """
                SELECT id, url, home_banking_id
                FROM home_url
                ORDER BY id ASC
                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String url = toSqlValue(rs.getString("url"));

                int homeBankingId = rs.getInt("home_banking_id");
                boolean homeBankingIdWasNull = rs.wasNull();

                // Compose SQL insert
                String insert = String.format(
                        "INSERT INTO home_url (id, url, home_banking_id) VALUES (%d, '%s', %s);",
                        id, url, homeBankingIdWasNull ? "NULL" : homeBankingId);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            
                    log.info("Home URL backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            log.error("Error during home_url backup: " + error.getMessage());
            return new ErrorMessage("Error in backup process", "Error during home_url backup", error.getMessage());
        }
    }

    public ErrorMessage backupBotJob(Connection conn, String backupFilePath) {
        String query =
                """
                SELECT id, name, description, priority,
                       home_banking_id, home_url_id, active
                FROM bot_job
                ORDER BY id ASC
                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            while (rs.next()) {
                int botJobId = rs.getInt("id");
                String name = toSqlValue(rs.getString("name"));
                String description = toSqlValue(rs.getString("description"));
                String priority = toSqlValue(rs.getString("priority"));

                int homeBankingId = rs.getInt("home_banking_id");
                boolean homeBankingIdWasNull = rs.wasNull();

                int homeUrlId = rs.getInt("home_url_id");
                boolean homeUrlIdWasNull = rs.wasNull();

                boolean active = rs.getBoolean("active");

                // Compose SQL insert with '[null]' for strings and SQL NULL for nullable ints
                String insert = String.format(
                        "INSERT INTO bot_job (id, name, description, priority, home_banking_id, home_url_id, active) "
                                + "VALUES (%d, '%s', '%s', '%s', %s, %s, %d);",
                        botJobId,
                        name,
                        description,
                        priority,
                        homeBankingIdWasNull ? "NULL" : homeBankingId,
                        homeUrlIdWasNull ? "NULL" : homeUrlId,
                        active ? 1 : 0);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            log.info("Backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            log.error("Error during bot_job backup: " + error.getMessage());
            return new ErrorMessage("Error in backup process", "Error during bot_job backup", error.getMessage());
        }
    }

    public ErrorMessage backupInstruction(Connection conn, String backupFilePath) {
        String query =
                """
                SELECT id, instruction_order_number, actions, name, xpath, coordinates,
                       force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root,
                       css_selector, description, operation, optional, block_marked, default_value,
                       action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr,
                       active, block_id, variable_id, parent_block_id, parent_id, bot_job_id
                FROM instruction
                ORDER BY id ASC
                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            while (rs.next()) {
                int id = rs.getInt("id");
                int order = rs.getInt("instruction_order_number");
                String actions = toSqlValue(rs.getString("actions"));
                String name = toSqlValue(rs.getString("name"));
                String xpath = toSqlValue(rs.getString("xpath"));
                String coordinates = toSqlValue(rs.getString("coordinates"));
                boolean forceCoordinates = rs.getBoolean("force_coordinates");
                String iframeXpath = toSqlValue(rs.getString("iframe_xpath"));
                String tagName = toSqlValue(rs.getString("tag_name"));
                String shadowHost = toSqlValue(rs.getString("shadow_host"));
                String shadowRoot = toSqlValue(rs.getString("shadow_root"));
                String cssSelector = toSqlValue(rs.getString("css_selector"));
                String description = toSqlValue(rs.getString("description"));
                String operation = toSqlValue(rs.getString("operation"));
                boolean optional = rs.getBoolean("optional");
                boolean blockMarked = rs.getBoolean("block_marked");
                String defaultValue = toSqlValue(rs.getString("default_value"));
                int maxWait = rs.getInt("action_custom_max_wait_sec");
                boolean maxWaitWasNull = rs.wasNull();
                int holdSec = rs.getInt("on_hold_seconds");
                boolean holdSecWasNull = rs.wasNull();
                boolean codified = rs.getBoolean("codified");
                boolean exportToAbr = rs.getBoolean("export_to_abr");
                boolean active = rs.getBoolean("active");
                int blockId = rs.getInt("block_id");
                boolean blockIdWasNull = rs.wasNull();
                int variableId = rs.getInt("variable_id");
                boolean variableIdWasNull = rs.wasNull();
                int parentBlockId = rs.getInt("parent_block_id");
                boolean parentBlockIdWasNull = rs.wasNull();
                int parentId = rs.getInt("parent_id");
                boolean parentIdWasNull = rs.wasNull();
                int botJobId = rs.getInt("bot_job_id");
                boolean botJobIdWasNull = rs.wasNull();

                String insert = String.format(
                        "INSERT INTO instruction (id, instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, block_id, variable_id, parent_block_id, parent_id, bot_job_id) "
                                + "VALUES (%d, %d, '%s', '%s', '%s', '%s', %d, '%s', '%s', '%s', '%s', '%s', '%s', '%s', %d, %d, '%s', %s, %s, %d, %d, %d, %s, %s, %s, %s, %s);",
                        id,
                        order,
                        actions,
                        name,
                        xpath,
                        coordinates,
                        forceCoordinates ? 1 : 0,
                        iframeXpath,
                        tagName,
                        shadowHost,
                        shadowRoot,
                        cssSelector,
                        description,
                        operation,
                        optional ? 1 : 0,
                        blockMarked ? 1 : 0,
                        defaultValue,
                        maxWaitWasNull ? "NULL" : maxWait,
                        holdSecWasNull ? "NULL" : holdSec,
                        codified ? 1 : 0,
                        exportToAbr ? 1 : 0,
                        active ? 1 : 0,
                        blockIdWasNull ? "NULL" : blockId,
                        variableIdWasNull ? "NULL" : variableId,
                        parentBlockIdWasNull ? "NULL" : parentBlockId,
                        parentIdWasNull ? "NULL" : parentId,
                        botJobIdWasNull ? "NULL" : botJobId);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            
                    log.info("Instruction backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            
                    log.error("Error during instruction backup: " + error.getMessage());
            return new ErrorMessage("Error in backup process", "Error during instruction backup", error.getMessage());
        }
    }

    public ErrorMessage backupVariable(Connection conn, String backupFilePath) {
        String query =
                """
                SELECT id, type, name, value, instruction_id, bot_job_id, local_format, delimiter
                FROM variable
                ORDER BY id ASC
                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String type = toSqlValue(rs.getString("type"));
                String name = toSqlValue(rs.getString("name"));
                String value = toSqlValue(rs.getString("value"));
                int instructionId = rs.getInt("instruction_id");
                boolean instructionIdWasNull = rs.wasNull();
                int botJobId = rs.getInt("bot_job_id");
                boolean botJobIdWasNull = rs.wasNull();
                String localFormat = toSqlValue(rs.getString("local_format"));
                String delimiter = toSqlValue(rs.getString("delimiter"));

                String insert = String.format(
                        "INSERT INTO variable (id, type, name, value, instruction_id, bot_job_id, local_format, delimiter) "
                                + "VALUES (%d, '%s', '%s', '%s', %s, %s, '%s', '%s');",
                        id,
                        type,
                        name,
                        value,
                        instructionIdWasNull ? "NULL" : instructionId,
                        botJobIdWasNull ? "NULL" : botJobId,
                        localFormat,
                        delimiter);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            
                    log.info("Variable backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            log.error("Error during variable backup: " + error.getMessage());
            return new ErrorMessage("Error in backup process", "Error during variable backup", error.getMessage());
        }
    }

    public ErrorMessage backupReference(Connection conn, String backupFilePath) {
        String query =
                """
                SELECT id, reference_type, value, instruction_id, bot_job_id
                FROM reference
                ORDER BY id ASC
                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String referenceType = toSqlValue(rs.getString("reference_type"));
                String value = toSqlValue(rs.getString("value"));
                int instructionId = rs.getInt("instruction_id"); // NOT NULL, no check needed
                int botJobId = rs.getInt("bot_job_id");
                boolean botJobIdWasNull = rs.wasNull();

                String insert = String.format(
                        "INSERT INTO reference (id, reference_type, value, instruction_id, bot_job_id) "
                                + "VALUES (%d, '%s', '%s', %d, %s);",
                        id, referenceType, value, instructionId, botJobIdWasNull ? "NULL" : botJobId);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            
                    log.info("Reference backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            log.error("Error during reference backup: " + error.getMessage());
            return new ErrorMessage("Error in backup process", "Error during reference backup", error.getMessage());
        }
    }

    public ErrorMessage backupBlock(Connection conn, String backupFilePath) {
        String query =
                """
                SELECT id, block_order_number, name, description, type_id, export_file, active, wait, bot_job_id
                FROM block
                ORDER BY id ASC
                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            while (rs.next()) {
                int id = rs.getInt("id");
                int orderNumber = rs.getInt("block_order_number");

                String name = toSqlValue(rs.getString("name"));
                String description = toSqlValue(rs.getString("description"));

                int typeId = rs.getInt("type_id");
                boolean typeIdWasNull = rs.wasNull();

                String exportFile = toSqlValue(rs.getString("export_file"));

                boolean active = rs.getBoolean("active");

                int wait = rs.getInt("wait");
                boolean waitWasNull = rs.wasNull();

                int botJobId = rs.getInt("bot_job_id");
                boolean botJobIdWasNull = rs.wasNull();

                String insert = String.format(
                        "INSERT INTO block (id, block_order_number, name, description, type_id, export_file, active, wait, bot_job_id) "
                                + "VALUES (%d, %d, '%s', '%s', %s, '%s', %d, %s, %s);",
                        id,
                        orderNumber,
                        name,
                        description,
                        typeIdWasNull ? "NULL" : typeId,
                        exportFile,
                        active ? 1 : 0,
                        waitWasNull ? "NULL" : wait,
                        botJobIdWasNull ? "NULL" : botJobId);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            log.info("Block backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            log.error("Error during block backup: " + error.getMessage());
            return new ErrorMessage("Error in backup process", "Error during block backup", error.getMessage());
        }
    }

    public ErrorMessage backupComponentBlock(Connection conn, String backupFilePath) {
        String query =
                """
                SELECT id, home_banking_id, block_order_number, name, description, type_id,
                       export_file, active, wait
                FROM component_block
                ORDER BY id ASC
                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            while (rs.next()) {
                int id = rs.getInt("id");

                int homeBankingId = rs.getInt("home_banking_id");
                boolean homeBankingIdWasNull = rs.wasNull();

                int orderNumber = rs.getInt("block_order_number");

                String name = toSqlValue(rs.getString("name"));
                String description = toSqlValue(rs.getString("description"));

                int typeId = rs.getInt("type_id");
                boolean typeIdWasNull = rs.wasNull();

                String exportFile = toSqlValue(rs.getString("export_file"));

                boolean active = rs.getBoolean("active");
                boolean activeWasNull = rs.wasNull();

                int wait = rs.getInt("wait");
                boolean waitWasNull = rs.wasNull();

                String insert = String.format(
                        "INSERT INTO component_block (id, home_banking_id, block_order_number, name, description, type_id, export_file, active, wait) "
                                + "VALUES (%d, %s, %d, '%s', '%s', %s, '%s', %s, %s);",
                        id,
                        homeBankingIdWasNull ? "NULL" : homeBankingId,
                        orderNumber,
                        name,
                        description,
                        typeIdWasNull ? "NULL" : typeId,
                        exportFile,
                        activeWasNull ? "NULL" : (active ? "1" : "0"),
                        waitWasNull ? "NULL" : wait);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            
                    log.info("ComponentBlock backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            
                    log.error("Error during component_block backup: " + error.getMessage());
            return new ErrorMessage(
                    "Error in backup process", "Error during component_block backup", error.getMessage());
        }
    }

    public ErrorMessage backupComponentInstruction(Connection conn, String backupFilePath) {
        String query =
                """
                SELECT id, instruction_order_number, actions, name, xpath, coordinates,
                       force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root,
                       css_selector, description, operation, optional, block_marked,
                       default_value, action_custom_max_wait_sec, on_hold_seconds,
                       codified, export_to_abr, active, block_id, variable_id, parent_block_id, parent_id,
                       home_banking_id
                FROM component_instruction
                ORDER BY id ASC
                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            while (rs.next()) {
                int id = rs.getInt("id");
                int orderNumber = rs.getInt("instruction_order_number");

                String actions = toSqlValue(rs.getString("actions"));
                String name = toSqlValue(rs.getString("name"));
                String xpath = toSqlValue(rs.getString("xpath"));
                String coordinates = toSqlValue(rs.getString("coordinates"));

                Boolean forceCoordinates = rs.getBoolean("force_coordinates");
                boolean forceCoordinatesWasNull = rs.wasNull();

                String iframeXpath = toSqlValue(rs.getString("iframe_xpath"));
                String tagName = toSqlValue(rs.getString("tag_name"));
                String shadowHost = toSqlValue(rs.getString("shadow_host"));
                String shadowRoot = toSqlValue(rs.getString("shadow_root"));
                String cssSelector = toSqlValue(rs.getString("css_selector"));
                String description = toSqlValue(rs.getString("description"));
                String operation = toSqlValue(rs.getString("operation"));

                Boolean optional = rs.getBoolean("optional");
                boolean optionalWasNull = rs.wasNull();

                Boolean blockMarked = rs.getBoolean("block_marked");
                boolean blockMarkedWasNull = rs.wasNull();

                String defaultValue = toSqlValue(rs.getString("default_value"));

                int waitSec = rs.getInt("action_custom_max_wait_sec");
                boolean waitSecWasNull = rs.wasNull();

                int onHoldSec = rs.getInt("on_hold_seconds");
                boolean onHoldWasNull = rs.wasNull();

                Boolean codified = rs.getBoolean("codified");
                boolean codifiedWasNull = rs.wasNull();

                Boolean exportToAbr = rs.getBoolean("export_to_abr");
                boolean exportToAbrWasNull = rs.wasNull();

                boolean active = rs.getBoolean("active");

                int blockId = rs.getInt("block_id");
                boolean blockIdWasNull = rs.wasNull();

                int variableId = rs.getInt("variable_id");
                boolean variableIdWasNull = rs.wasNull();

                int parentBlockId = rs.getInt("parent_block_id");
                boolean parentBlockIdWasNull = rs.wasNull();

                int parentId = rs.getInt("parent_id");
                boolean parentIdWasNull = rs.wasNull();

                int homeBankingId = rs.getInt("home_banking_id");
                boolean homeBankingIdWasNull = rs.wasNull();

                String insert = String.format(
                        "INSERT INTO component_instruction ("
                                + "id, instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, "
                                + "tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, "
                                + "default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, "
                                + "block_id, variable_id, parent_block_id, parent_id, home_banking_id"
                                + ") VALUES (%d, %d, '%s', '%s', '%s', '%s', %s, '%s', '%s', '%s', '%s', '%s', '%s', '%s', %s, %s, '%s', %s, %s, %s, %s, %d, %s, %s, %s, %s, %s);",
                        id,
                        orderNumber,
                        actions,
                        name,
                        xpath,
                        coordinates,
                        forceCoordinatesWasNull ? "NULL" : (forceCoordinates ? "1" : "0"),
                        iframeXpath,
                        tagName,
                        shadowHost,
                        shadowRoot,
                        cssSelector,
                        description,
                        operation,
                        optionalWasNull ? "NULL" : (optional ? "1" : "0"),
                        blockMarkedWasNull ? "NULL" : (blockMarked ? "1" : "0"),
                        defaultValue,
                        waitSecWasNull ? "NULL" : waitSec,
                        onHoldWasNull ? "NULL" : onHoldSec,
                        codifiedWasNull ? "NULL" : (codified ? "1" : "0"),
                        exportToAbrWasNull ? "NULL" : (exportToAbr ? "1" : "0"),
                        active ? 1 : 0,
                        blockIdWasNull ? "NULL" : blockId,
                        variableIdWasNull ? "NULL" : variableId,
                        parentBlockIdWasNull ? "NULL" : parentBlockId,
                        parentIdWasNull ? "NULL" : parentId,
                        homeBankingIdWasNull ? "NULL" : homeBankingId);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            
                    log.info("ComponentInstruction backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            
                    log.error("Error during component_instruction backup: " + error.getMessage());
            return new ErrorMessage(
                    "Error in backup process", "Error during component_instruction backup", error.getMessage());
        }
    }

    public ErrorMessage backupComponentVariable(Connection conn, String backupFilePath) {
        String query =
                """
                SELECT id, type, name, value, instruction_id, home_banking_id, local_format, delimiter
                FROM component_variable
                ORDER BY id ASC
                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            while (rs.next()) {
                int id = rs.getInt("id");

                String type = toSqlValue(rs.getString("type"));
                String name = toSqlValue(rs.getString("name"));
                String value = toSqlValue(rs.getString("value"));

                int instructionId = rs.getInt("instruction_id");
                boolean instructionIdWasNull = rs.wasNull();

                int homeBankingId = rs.getInt("home_banking_id");
                boolean homeBankingIdWasNull = rs.wasNull();

                String localFormat = toSqlValue(rs.getString("local_format"));
                String delimiter = toSqlValue(rs.getString("delimiter"));

                String insert = String.format(
                        "INSERT INTO component_variable (id, type, name, value, instruction_id, home_banking_id, local_format, delimiter) "
                                + "VALUES (%d, '%s', '%s', '%s', %s, %s, '%s', '%s');",
                        id,
                        type,
                        name,
                        value,
                        instructionIdWasNull ? "NULL" : instructionId,
                        homeBankingIdWasNull ? "NULL" : homeBankingId,
                        localFormat,
                        delimiter);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            
                    log.info("ComponentVariable backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            
                    log.error("Error during component_variable backup: " + error.getMessage());
            return new ErrorMessage(
                    "Error in backup process", "Error during component_variable backup", error.getMessage());
        }
    }

    public ErrorMessage backupComponentReference(Connection conn, String backupFilePath) {
        String query =
                """
                SELECT id, reference_type, value, instruction_id, home_banking_id
                FROM component_reference
                ORDER BY id ASC
                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            while (rs.next()) {
                int id = rs.getInt("id");

                String referenceType = toSqlValue(rs.getString("reference_type"));
                String value = toSqlValue(rs.getString("value"));

                int instructionId = rs.getInt("instruction_id"); // NOT NULL, so no null check needed

                int homeBankingId = rs.getInt("home_banking_id");
                boolean homeBankingIdWasNull = rs.wasNull();

                String insert = String.format(
                        "INSERT INTO component_reference (id, reference_type, value, instruction_id, home_banking_id) "
                                + "VALUES (%d, '%s', '%s', %d, %s);",
                        id, referenceType, value, instructionId, homeBankingIdWasNull ? "NULL" : homeBankingId);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            
                    log.info("ComponentReference backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            
                    log.error("Error during component_reference backup: " + error.getMessage());
            return new ErrorMessage(
                    "Error in backup process", "Error during component_reference backup", error.getMessage());
        }
    }

    // RESTORE
    public ErrorMessage restoreHomeBanking(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
        INSERT INTO home_banking (
            url, name, priority, search_config, options_config,
            cookies, driver_session, username, password
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        String selectHomeBankingIdsSQL = "SELECT id FROM home_banking ORDER BY id";

        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(sqlFilePath), Charset.forName("windows-1252")));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement();
                Statement deleteStmt = conn.createStatement()) {
            conn.setAutoCommit(false);

            // 🔹 Step 0: Delete in correct order
            //            deleteStmt.execute("PRAGMA foreign_keys = ON");
            deleteStmt.executeUpdate("DELETE FROM component_reference");
            deleteStmt.executeUpdate("DELETE FROM component_variable");
            deleteStmt.executeUpdate("DELETE FROM component_instruction");
            deleteStmt.executeUpdate("DELETE FROM component_block");

            deleteStmt.executeUpdate("DELETE FROM reference");
            deleteStmt.executeUpdate("DELETE FROM variable");
            deleteStmt.executeUpdate("DELETE FROM instruction");
            deleteStmt.executeUpdate("DELETE FROM block");
            deleteStmt.executeUpdate("DELETE FROM bot_job");
            deleteStmt.executeUpdate("DELETE FROM home_url");
            deleteStmt.executeUpdate("DELETE FROM home_banking");
            conn.commit();

            // Step 1: Get current home_banking IDs before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectHomeBankingIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            // Step 2: Read SQL file and prepare batch
            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

            homeBankMap.clear();
            List<Integer> insertedOldIds = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 10) {
                        return new ErrorMessage("Parse Error", "Expected 10 values", currentInsert.toString());
                    }

                    for (int i = 1; i < values.size(); i++) { // start from 1 to skip old ID
                        String val = values.get(i);
                        switch (i) {
                            case 1 -> setSafeParam(pstmt, 1, val, Types.VARCHAR); // url
                            case 2 -> setSafeParam(pstmt, 2, val, Types.VARCHAR); // name
                            case 3 -> setSafeParam(pstmt, 3, val, Types.VARCHAR); // priority
                            case 4 -> setSafeParam(pstmt, 4, val, Types.VARCHAR); // search_config
                            case 5 -> setSafeParam(pstmt, 5, val, Types.VARCHAR); // options_config
                            case 6 -> setSafeParam(pstmt, 6, val, Types.VARCHAR); // cookies
                            case 7 -> setSafeParam(pstmt, 7, val, Types.VARCHAR); // driver_session
                            case 8 -> setSafeParam(pstmt, 8, val, Types.VARCHAR); // username
                            case 9 -> setSafeParam(pstmt, 9, val, Types.VARCHAR); // password
                            default -> System.err.println("Unexpected value index: " + i);
                        }
                    }

                    // Track old ID for mapping later
                    try {
                        int oldId = Integer.parseInt(values.get(0));
                        insertedOldIds.add(oldId);
                        homeBankMap.put(oldId, -1); // initially set to -1
                    } catch (Exception ex) {
                        System.out.println("Error parsing homeBankMap entry: " + ex.getMessage());
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0); // reset buffer
                    batchReady = true;
                }
            }

            // Step 3: Execute batch
            if (batchReady) {
                pstmt.executeBatch();
                conn.commit();
            }

            // Step 4: Get home_banking IDs after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectHomeBankingIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            // Step 5: Compute new IDs
            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println(
                        "Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                homeBankMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            System.out.println("HomeBankMap populated: " + homeBankMap);

            return null;

        } catch (Exception error) {
            return new ErrorMessage("Restore Failed", "Failed to load home_banking data", error.getMessage());
        }
    }

    private List<String> extractValuesFromInsert(String insert) {
        int start = insert.indexOf("VALUES (") + 8;
        int end = insert.lastIndexOf(");");
        String valuesString = insert.substring(start, end);

        List<String> values = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < valuesString.length(); i++) {
            char c = valuesString.charAt(i);
            if (c == '\'') {
                insideQuotes = !insideQuotes;
            } else if (c == ',' && !insideQuotes) {
                values.add(sb.toString().trim().replace("''", "'"));
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString().trim().replace("''", "'")); // add last value

        return values;
    }

    public ErrorMessage restoreHomeUrl(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO home_url (
                    url, home_banking_id
                ) VALUES (?, ?);
                """;

        String selectHomeUrlIdsSQL = "SELECT id FROM home_url ORDER BY id";

        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(sqlFilePath), Charset.forName("windows-1252")));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {
            conn.setAutoCommit(false);

            // Step 1: get current home_url IDs before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectHomeUrlIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

            homeUrlMap.clear();
            List<Integer> insertedOldIds = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 3) {
                        return new ErrorMessage(
                                "Parse Error", "Expected 3 values for home_url", currentInsert.toString());
                    }

                    // Extract old home_banking_id (index 2)
                    Integer oldHomeBankId = null;
                    try {
                        String oldHomeBankIdStr = values.get(2);
                        oldHomeBankId = Integer.parseInt(oldHomeBankIdStr);
                    } catch (NumberFormatException ex) {
                        System.out.println("Invalid home_banking_id format: " + values.get(2));
                    }

                    // Lookup newHomeBankId from homeBankMap
                    Integer newHomeBankId = null;
                    if (oldHomeBankId != null) {
                        newHomeBankId = homeBankMap.get(oldHomeBankId);
                    }

                    if (newHomeBankId == null) {
                        System.out.println("Skipped home_url with unknown home_banking_id: " + oldHomeBankId);
                        currentInsert.setLength(0); // reset buffer
                        continue; // skip this row
                    }

                    for (int i = 1; i < values.size(); i++) {
                        String val = values.get(i);
                        switch (i) {
                                //                            case 0 -> setSafeParam(pstmt, 1, val, Types.INTEGER); //
                                // id
                            case 1 -> setSafeParam(pstmt, 1, val, Types.VARCHAR); // url
                            case 2 -> pstmt.setInt(2, newHomeBankId); // home_banking_id (mapped)
                        }
                    }

                    // Track old ID for mapping later (id is values.get(0))
                    try {
                        int oldId = Integer.parseInt(values.get(0));
                        insertedOldIds.add(oldId);
                        homeUrlMap.put(oldId, -1); // initialize mapping to -1
                    } catch (Exception ex) {
                        System.out.println("Error parsing homeUrlMap entry: " + ex.getMessage());
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0);
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
                conn.commit();
            }

            // Get IDs after insert and update map like before...

            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectHomeUrlIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println(
                        "Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                homeUrlMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            System.out.println("homeUrlMap populated: " + homeUrlMap);

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load home_url data", e.getMessage());
        }
    }

    public ErrorMessage restoreBlock(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO block (
                    block_order_number, name, description, type_id,
                    export_file, active, wait, bot_job_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                """;

        String selectBlockIdsSQL = "SELECT id FROM block ORDER BY id";

        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(sqlFilePath), Charset.forName("windows-1252")));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {
            conn.setAutoCommit(false);

            // Step 1: get current block IDs before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectBlockIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

            List<Integer> insertedOldIds = new ArrayList<>();
            blockMap.clear();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 9) {
                        return new ErrorMessage("Parse Error", "Expected 9 values for block", currentInsert.toString());
                    }

                    // Extract old bot_job_id (index 8)
                    Integer oldBotJobId = null;
                    try {
                        String oldBotJobIdStr = values.get(8);
                        oldBotJobId = Integer.parseInt(oldBotJobIdStr);
                    } catch (NumberFormatException ex) {
                        System.out.println("Invalid bot_job_id format: " + values.get(8));
                    }

                    // Lookup newBotJobId from botJobMap
                    Integer newBotJobId = null;
                    if (oldBotJobId != null) {
                        newBotJobId = botJobMap.get(oldBotJobId);
                    }

                    if (newBotJobId == null) {
                        System.out.println("Skipped block with unknown bot_job_id: " + oldBotJobId);
                        currentInsert.setLength(0);
                        continue; // skip this row
                    }

                    for (int i = 1; i < values.size(); i++) {
                        String val = values.get(i);

                        switch (i) {
                                //                            case 0 -> setSafeParam(pstmt, 1, val, Types.INTEGER); //
                                // id
                            case 1 -> setSafeParam(pstmt, 1, val, Types.INTEGER); // block_order_number
                            case 2 -> setSafeParam(pstmt, 2, val, Types.VARCHAR); // name
                            case 3 -> setSafeParam(pstmt, 3, val, Types.VARCHAR); // description
                            case 4 -> setSafeParam(pstmt, 4, val, Types.INTEGER); // type_id
                            case 5 -> setSafeParam(pstmt, 5, val, Types.VARCHAR); // export_file
                            case 6 -> setSafeParam(pstmt, 6, val, Types.INTEGER); // active
                            case 7 -> setSafeParam(pstmt, 7, val, Types.INTEGER); // wait
                            case 8 -> pstmt.setInt(8, newBotJobId); // bot_job_id (already mapped)
                        }
                    }

                    // Track old ID for mapping later
                    try {
                        int oldId = Integer.parseInt(values.get(0));
                        insertedOldIds.add(oldId);
                        blockMap.put(oldId, -1); // initialize mapping
                    } catch (Exception ex) {
                        System.out.println("Error parsing blockMap entry: " + ex.getMessage());
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0);
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
                conn.commit();
            }

            // Step 3: get block IDs after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectBlockIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println(
                        "Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                blockMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            System.out.println("blockMap populated: " + blockMap);

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load block data", e.getMessage());
        }
    }

    public ErrorMessage restoreBotJob(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO bot_job (
                    name, description, priority, home_banking_id, home_url_id, active
                ) VALUES (?, ?, ?, ?, ?, ?);
                """;

        String selectBotJobIdsSQL = "SELECT id FROM bot_job ORDER BY id";

        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(sqlFilePath), Charset.forName("windows-1252")));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {
            conn.setAutoCommit(false);

            // Step 1: get current bot_job IDs before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectBotJobIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

            List<Integer> insertedOldIds = new ArrayList<>();
            botJobMap.clear();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 7) {
                        return new ErrorMessage(
                                "Parse Error", "Expected 7 values for bot_job", currentInsert.toString());
                    }

                    // Extract old home_banking_id (index 4)
                    Integer oldHomeBankId = null;
                    try {
                        String oldHomeBankIdStr = values.get(4);
                        oldHomeBankId = Integer.parseInt(oldHomeBankIdStr);
                    } catch (NumberFormatException ex) {
                        System.out.println("Invalid home_banking_id format: " + values.get(4));
                    }

                    // Extract old home_url_id (index 5)
                    Integer oldHomeUrlId = null;
                    try {
                        String oldHomeUrlIdStr = values.get(5);
                        oldHomeUrlId = Integer.parseInt(oldHomeUrlIdStr);
                    } catch (NumberFormatException ex) {
                        System.out.println("Invalid home_url_id format: " + values.get(5));
                    }

                    // Lookup newHomeBankId and newHomeUrlId from maps
                    Integer newHomeBankId = null;
                    Integer newHomeUrlId = null;
                    if (oldHomeBankId != null) {
                        newHomeBankId = homeBankMap.get(oldHomeBankId);
                    }
                    if (oldHomeUrlId != null) {
                        newHomeUrlId = homeUrlMap.get(oldHomeUrlId);
                    }

                    if (newHomeBankId == null) {
                        System.out.println("Skipped bot_job with unknown home_banking_id: " + oldHomeBankId);
                        currentInsert.setLength(0);
                        continue; // skip this row
                    }

                    if (newHomeUrlId == null) {
                        System.out.println("Skipped bot_job with unknown home_url_id: " + oldHomeUrlId);
                        currentInsert.setLength(0);
                        continue; // skip this row
                    }

                    // Now set parameters
                    for (int i = 1; i < values.size(); i++) {
                        String val = values.get(i);

                        switch (i) {
                                //                            case 0 -> setSafeParam(pstmt, 1, val, Types.INTEGER); //
                                // id
                            case 1 -> setSafeParam(pstmt, 1, val, Types.VARCHAR); // name
                            case 2 -> setSafeParam(pstmt, 2, val, Types.VARCHAR); // description
                            case 3 -> setSafeParam(pstmt, 3, val, Types.VARCHAR); // priority
                            case 4 -> pstmt.setInt(4, newHomeBankId); // home_banking_id (mapped)
                            case 5 -> pstmt.setInt(5, newHomeUrlId); // home_url_id (mapped)
                            case 6 -> setSafeParam(pstmt, 6, val, Types.INTEGER); // active
                        }
                    }

                    // Track old ID for mapping later
                    try {
                        int oldId = Integer.parseInt(values.get(0));
                        insertedOldIds.add(oldId);
                        botJobMap.put(oldId, -1); // initialize mapping
                    } catch (Exception ex) {
                        System.out.println("Error parsing botJobMap entry: " + ex.getMessage());
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0);
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
                conn.commit();
            }

            // Step 3: get bot_job IDs after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectBotJobIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println(
                        "Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                botJobMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            System.out.println("botJobMap populated: " + botJobMap);

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load bot_job data", e.getMessage());
        }
    }

    public ErrorMessage restoreInstruction(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO instruction (
                    instruction_order_number, actions, name, xpath, coordinates,
                    force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root,
                    css_selector, description, operation, optional, block_marked,
                    default_value, action_custom_max_wait_sec, on_hold_seconds, codified,
                    export_to_abr, active, block_id, variable_id, parent_block_id, parent_id, bot_job_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        String selectInstructionIdsSQL = "SELECT id FROM instruction ORDER BY id";

        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(sqlFilePath), Charset.forName("windows-1252")));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectInstructionIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;
            List<Integer> insertedOldIds = new ArrayList<>();
            instructionMap.clear();
            instrVariablesMap.clear();
            instrParentBlockMap.clear();
            instrParentIdMap.clear();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 26 && values.size() != 27) {
                        return new ErrorMessage(
                                "Parse Error",
                                "Expected 26 or 27 values for instruction, but got " + values.size(),
                                currentInsert.toString());
                    }

                    // Extract old block_id (index 22) and bot_job_id (index 25 / index 26)
                    Integer oldBlockId = parseIntSafe(values.get(22));
                    Integer oldBotJobId =
                            parseIntSafe(values.get(values.size() == 26 ? 25 : 26)); // index depends on 26/27 cols

                    Integer newBlockId = oldBlockId != null ? blockMap.get(oldBlockId) : null;
                    Integer newBotJobId = oldBotJobId != null ? botJobMap.get(oldBotJobId) : null;

                    if (newBlockId == null) {
                        System.out.println("Skipped instruction with unknown block_id: " + oldBlockId);
                        currentInsert.setLength(0);
                        continue;
                    }

                    if (newBotJobId == null) {
                        System.out.println("Skipped instruction with unknown bot_job_id: " + oldBotJobId);
                        currentInsert.setLength(0);
                        continue;
                    }

                    Integer instructionId = values.get(0) != null ? Integer.valueOf(values.get(0)) : null;
                    String action = values.get(2);
                    boolean isGoto = "GOTO".equalsIgnoreCase(action) || "EXCEL GOTO".equalsIgnoreCase(action);

                    Integer parentBlockId = null;
                    Integer parentId = null;

                    boolean hasParentBlockId = values.size() == 27; // has parent_block_id columns 27 cols

                    if (hasParentBlockId) {
                        // values.size() == 27: parent_block_id = 24, parent_id = 25
                        String parBlockValue = values.get(24);
                        String parIdValue = values.get(25);

                        if (parBlockValue != null
                                && !parBlockValue.isBlank()
                                && !parBlockValue.equalsIgnoreCase("null")
                                && !parBlockValue.equalsIgnoreCase("[null]")) {
                            try {
                                parentBlockId = Integer.valueOf(parBlockValue.trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }

                        if (parIdValue != null
                                && !parIdValue.isBlank()
                                && !parIdValue.equalsIgnoreCase("null")
                                && !parIdValue.equalsIgnoreCase("[null]")) {
                            try {
                                parentId = Integer.valueOf(parIdValue.trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    } else {
                        // values.size() == 26: parent_block_id may not exist
                        String parValue = values.get(24);

                        if (parValue != null
                                && !parValue.isBlank()
                                && !parValue.equalsIgnoreCase("null")
                                && !parValue.equalsIgnoreCase("[null]")) {
                            try {
                                if (isGoto) {
                                    parentBlockId = Integer.valueOf(parValue.trim());
                                } else {
                                    parentId = Integer.valueOf(parValue.trim());
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }

                    // Map the values if instructionId exists
                    if (instructionId != null) {
                        if (parentBlockId != null) instrParentBlockMap.put(instructionId, parentBlockId);
                        if (parentId != null) instrParentIdMap.put(instructionId, parentId);
                    }

                    // Set prepared statement params to null for insert (new table always has parent_block_id)
                    setSafeParam(pstmt, 24, null, Types.INTEGER); // parent_block_id
                    setSafeParam(pstmt, 25, null, Types.INTEGER); // parent_id
                    setSafeParam(pstmt, 26, String.valueOf(newBotJobId), Types.INTEGER);

                    for (int i = 1; i < values.size(); i++) {
                        switch (i) {
                                //                            case 0 -> setSafeParam(pstmt, 1, values.get(0),
                                // Types.INTEGER); // id
                            case 1 -> setSafeParam(pstmt, 1, values.get(1), Types.INTEGER); // instruction_order_number
                            case 2 -> setSafeParam(pstmt, 2, values.get(2), Types.VARCHAR); // actions
                            case 3 -> {
                                String val = values.get(3);
                                setSafeParam(pstmt, 3, val, Types.VARCHAR);
                            } // name
                            case 4 -> setSafeParam(pstmt, 4, values.get(4), Types.VARCHAR); // xpath
                            case 5 -> setSafeParam(pstmt, 5, values.get(5), Types.VARCHAR); // coordinates
                            case 6 -> setSafeParam(pstmt, 6, values.get(6), Types.INTEGER); // force_coordinates
                            case 7 -> setSafeParam(pstmt, 7, values.get(7), Types.VARCHAR); // iframe_xpath
                            case 8 -> setSafeParam(pstmt, 8, values.get(8), Types.VARCHAR); // tag_name
                            case 9 -> setSafeParam(pstmt, 9, values.get(9), Types.VARCHAR); // shadow_host
                            case 10 -> setSafeParam(pstmt, 10, values.get(10), Types.VARCHAR); // shadow_root
                            case 11 -> setSafeParam(pstmt, 11, values.get(11), Types.VARCHAR); // css_selector
                            case 12 -> setSafeParam(pstmt, 12, values.get(12), Types.VARCHAR); // description
                            case 13 -> setSafeParam(pstmt, 13, values.get(13), Types.VARCHAR); // operation
                            case 14 -> setSafeParam(pstmt, 14, values.get(14), Types.INTEGER); // optional
                            case 15 -> setSafeParam(pstmt, 15, values.get(15), Types.INTEGER); // block_marked
                            case 16 -> setSafeParam(pstmt, 16, values.get(16), Types.VARCHAR); // default_value
                            case 17 -> setSafeParam(
                                    pstmt, 17, values.get(17), Types.INTEGER); // action_custom_max_wait_sec
                            case 18 -> setSafeParam(pstmt, 18, values.get(18), Types.INTEGER); // on_hold_seconds
                            case 19 -> setSafeParam(pstmt, 19, values.get(19), Types.INTEGER); // codified
                            case 20 -> setSafeParam(pstmt, 20, values.get(20), Types.INTEGER); // export_to_abr
                            case 21 -> setSafeParam(pstmt, 21, values.get(21), Types.INTEGER); // active

                            case 22 -> {
                                // block_id replaced with newBlockId
                                setSafeParam(
                                        pstmt,
                                        22,
                                        String.valueOf(newBlockId),
                                        Types.INTEGER); // block_id already mapped
                            }
                            case 23 -> {
                                // variable_id + tracking
                                String varValue = values.get(23);
                                Integer variableId = null;
                                if (varValue != null
                                        && !varValue.isBlank()
                                        && !varValue.equalsIgnoreCase("null")
                                        && !varValue.equalsIgnoreCase("[null]")) {
                                    try {
                                        variableId = Integer.valueOf(varValue.trim());
                                    } catch (NumberFormatException e) {
                                        // Ignore parsing errors and keep variableId as null
                                    }
                                }

                                if (instructionId != null && variableId != null) {
                                    instrVariablesMap.put(instructionId, variableId);
                                }

                                // Firts to Be mapped after INSERTS into Variable TABLE
                                setSafeParam(pstmt, 23, "NULL", Types.INTEGER);
                            }
                            case 24, 25, 26 -> {}
                            default -> throw new IllegalArgumentException("Unexpected column index: " + i);
                        }
                    }

                    try {
                        int oldId = Integer.parseInt(values.get(0));
                        insertedOldIds.add(oldId);
                        instructionMap.put(oldId, -1);
                    } catch (Exception ex) {
                        System.out.println("Error parsing instructionMap entry: " + ex.getMessage());
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0);
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
                conn.commit();
            }

            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectInstructionIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println(
                        "Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                instructionMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            System.out.println("instructionMap populated: " + instructionMap);
            return null;

        } catch (Exception error) {
            return new ErrorMessage("Restore Failed", "Failed to load instruction data", error.getMessage());
        }
    }

    public ErrorMessage restoreVariable(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO variable (
                    type, name, value, instruction_id, bot_job_id, local_format, delimiter
                ) VALUES (?, ?, ?, ?, ?, ?, ?);
                """;

        String selectVariableIdsSQL = "SELECT id FROM variable ORDER BY id";

        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(sqlFilePath), Charset.forName("windows-1252")));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {
            conn.setAutoCommit(false);

            // Step 1: get current variable IDs before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectVariableIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

            List<Integer> insertedOldIds = new ArrayList<>();
            variableMap.clear();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 8) {
                        return new ErrorMessage(
                                "Parse Error", "Expected 8 values for variable", currentInsert.toString());
                    }

                    // Extract old instruction_id (index 4) and bot_job_id (index 5)
                    Integer oldInstructionId = null;
                    Integer oldBotJobId = null;
                    try {
                        oldInstructionId = Integer.parseInt(values.get(4));
                    } catch (NumberFormatException ex) {
                        System.out.println("Invalid instruction_id format: " + values.get(4));
                    }
                    try {
                        oldBotJobId = Integer.parseInt(values.get(5));
                    } catch (NumberFormatException ex) {
                        System.out.println("Invalid bot_job_id format: " + values.get(5));
                    }

                    // Lookup new IDs
                    Integer newInstructionId = oldInstructionId != null ? instructionMap.get(oldInstructionId) : null;
                    Integer newBotJobId = oldBotJobId != null ? botJobMap.get(oldBotJobId) : null;

                    if (newInstructionId == null) {
                        System.out.println("Skipped variable with unknown instruction_id: " + oldInstructionId);
                        currentInsert.setLength(0);
                        continue;
                    }
                    if (newBotJobId == null) {
                        System.out.println("Skipped variable with unknown bot_job_id: " + oldBotJobId);
                        currentInsert.setLength(0);
                        continue;
                    }

                    for (int i = 1; i < values.size(); i++) {
                        String val = values.get(i);

                        switch (i) {
                                //                            case 0 -> setSafeParam(pstmt, 1, val, Types.INTEGER);
                            case 1 -> setSafeParam(pstmt, 1, val, Types.VARCHAR); // type
                            case 2 -> setSafeParam(pstmt, 2, val, Types.VARCHAR); // name
                            case 3 -> setSafeParam(pstmt, 3, val, Types.VARCHAR); // value
                            case 4 -> {
                                // instruction_id replaced with newInstructionId
                                setSafeParam(pstmt, 4, String.valueOf(newInstructionId), Types.INTEGER);
                            }
                            case 5 -> {
                                // bot_job_id replaced with newBotJobId
                                setSafeParam(pstmt, 5, String.valueOf(newBotJobId), Types.INTEGER);
                            }
                            case 6 -> setSafeParam(pstmt, 6, val, Types.VARCHAR); // local_format
                            case 7 -> setSafeParam(pstmt, 7, val, Types.VARCHAR); // delimiter
                            default -> throw new IllegalArgumentException("Unexpected column index: " + i);
                        }
                    }

                    try {
                        int oldId = Integer.parseInt(values.get(0));
                        insertedOldIds.add(oldId);
                        variableMap.put(oldId, -1);
                    } catch (Exception ex) {
                        System.out.println("Error parsing variableMap entry: " + ex.getMessage());
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0);
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
                conn.commit();
            }

            // Step 3: get variable IDs after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectVariableIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println(
                        "Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                variableMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            System.out.println("variableMap populated: " + variableMap);

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load variable data", e.getMessage());
        }
    }

    public ErrorMessage restoreUpdateInstruction(Connection conn) {
        final int BATCH_SIZE = 100;

        instrNewInverted.clear();

        for (Map.Entry<Integer, Integer> entry : instructionMap.entrySet()) {
            instrNewInverted.put(entry.getValue(), entry.getKey());
        }

        try (Statement connStmt = conn.createStatement()) {
            conn.setAutoCommit(false);

            String selectAccessSQL = "SELECT id, name, parent_id, variable_id, parent_block_id "
                    + "FROM instruction WHERE parent_id IS NOT NULL OR variable_id IS NULL OR parent_block_id IS NOT NULL ORDER BY id";

            try (ResultSet rsInstruction = connStmt.executeQuery(selectAccessSQL)) {

                String updateSQL =
                        "UPDATE instruction SET variable_id = ?, parent_id = ?, parent_block_id = ? WHERE id = ?";

                try (PreparedStatement updateStmt = conn.prepareStatement(updateSQL)) {
                    int count = 0;

                    while (rsInstruction.next()) {
                        int id = rsInstruction.getInt("id");

                        // ---- variable_id ----
                        Integer originalOldID = instrNewInverted.get(id);

                        Integer originalVarId = null;
                        Integer originalParBlockId = null;

                        if (originalOldID != null) {
                            originalVarId = instrVariablesMap.get(originalOldID);
                        }

                        if (originalOldID != null) {
                            originalParBlockId = instrParentBlockMap.get(originalOldID);
                        }

                        Integer originalOldParentID = null;
                        if (originalOldID != null) {
                            originalOldParentID = instrParentIdMap.get(originalOldID);
                        }

                        Integer newVariableId = null;
                        if (originalVarId != null) {
                            newVariableId = variableMap.get(originalVarId);
                        }
                        setSafeParam(
                                updateStmt,
                                1,
                                newVariableId != null ? String.valueOf(newVariableId) : "NULL",
                                Types.INTEGER);

                        // ---- parent_id ----
                        Integer newParentId = null;
                        if (originalOldParentID != null) {
                            newParentId = instructionMap.get(originalOldParentID);
                        }

                        setSafeParam(
                                updateStmt,
                                2,
                                newParentId != null ? String.valueOf(newParentId) : "NULL",
                                Types.INTEGER);

                        // ---- parent_block_id ----
                        Integer newParentBlockId = null;
                        if (originalParBlockId != null) {
                            newParentBlockId = blockMap.get(originalParBlockId);
                        }
                        setSafeParam(
                                updateStmt,
                                3,
                                newParentBlockId != null ? String.valueOf(newParentBlockId) : "NULL",
                                Types.INTEGER);

                        // ---- WHERE id = ? ----
                        updateStmt.setInt(4, id);

                        updateStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            updateStmt.executeBatch();
                            conn.commit();
                            System.out.println("Updated batch of " + BATCH_SIZE);
                        }
                    }

                    if (count % BATCH_SIZE != 0) {
                        updateStmt.executeBatch();
                        conn.commit();
                        System.out.println("Updated final batch of " + (count % BATCH_SIZE));
                    }

                    log.info("Updated instruction records: " + count);
                }
            }

            return null;

        } catch (SQLException e) {
            log.error("Failed to update instructions: " + e.getMessage());
            return new ErrorMessage("Restore Failed", "Failed to update instruction data", e.getMessage());
        }
    }

    public ErrorMessage restoreReference(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO reference (
                    reference_type, value, instruction_id, bot_job_id
                ) VALUES (?, ?, ?, ?);
                """;

        String selectReferenceIdsSQL = "SELECT id FROM reference ORDER BY id";

        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(sqlFilePath), Charset.forName("windows-1252")));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectReferenceIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;
            List<Integer> insertedOldIds = new ArrayList<>();
            referenceMap.clear();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 5) {
                        return new ErrorMessage(
                                "Parse Error", "Expected 5 values for reference", currentInsert.toString());
                    }

                    // Parse old instruction ID and bot job ID
                    Integer oldInstructionId = parseIntSafe(values.get(3));
                    Integer oldBotJobId = parseIntSafe(values.get(4));

                    Integer newInstructionId = oldInstructionId != null ? instructionMap.get(oldInstructionId) : null;
                    Integer newBotJobId = oldBotJobId != null ? botJobMap.get(oldBotJobId) : null;

                    if (newInstructionId == null || newBotJobId == null) {
                        System.out.println("Skipped reference due to unknown instruction or bot_job ID: " + "instr="
                                + oldInstructionId + ", botJob=" + oldBotJobId);
                        currentInsert.setLength(0);
                        continue;
                    }

                    for (int i = 1; i < values.size(); i++) {
                        String val = values.get(i);
                        switch (i) {
                                //                            case 0 -> setSafeParam(pstmt, 1, val, Types.INTEGER); //
                                // id
                            case 1 -> setSafeParam(pstmt, 1, val, Types.VARCHAR); // reference_type
                            case 2 -> setSafeParam(pstmt, 2, val, Types.VARCHAR); // value
                            case 3 -> setSafeParam(
                                    pstmt, 3, String.valueOf(newInstructionId), Types.INTEGER); // instruction_id
                            case 4 -> setSafeParam(pstmt, 4, String.valueOf(newBotJobId), Types.INTEGER); // bot_job_id
                            default -> throw new IllegalArgumentException("Unexpected column index: " + i);
                        }
                    }

                    try {
                        int oldId = Integer.parseInt(values.get(0));
                        insertedOldIds.add(oldId);
                        referenceMap.put(oldId, -1); // mark for replacement
                    } catch (Exception ex) {
                        System.out.println("Error parsing referenceMap entry: " + ex.getMessage());
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0);
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
                conn.commit();
            }

            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectReferenceIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println(
                        "Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                referenceMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            System.out.println("referenceMap populated: " + referenceMap);

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load reference data", e.getMessage());
        }
    }

    public ErrorMessage restoreComponentBlock(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO component_block (
                    home_banking_id, block_order_number, name, description,
                    type_id, export_file, active, wait
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                """;

        String selectCompBlockIdsSQL = "SELECT id FROM component_block ORDER BY id";

        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(sqlFilePath), Charset.forName("windows-1252")));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current component_block IDs before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectCompBlockIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

            List<Integer> insertedOldIds = new ArrayList<>();
            // Assuming you have a componentBlockMap similar to blockMap to track IDs
            blockMap.clear();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 9) {
                        return new ErrorMessage(
                                "Parse Error", "Expected 9 values for component_block", currentInsert.toString());
                    }

                    // Extract old bot_job_id (index 8)
                    Integer oldHomeBankId = null;
                    try {
                        String oldHomeBankIdStr = values.get(1);
                        oldHomeBankId = Integer.parseInt(oldHomeBankIdStr);
                    } catch (NumberFormatException ex) {
                        System.out.println("Invalid home_banking_id format: " + values.get(1));
                    }

                    // Lookup newBotJobId from botJobMap
                    Integer newHomeBankId = null;
                    if (oldHomeBankId != null) {
                        newHomeBankId = homeBankMap.get(oldHomeBankId);
                    }

                    if (newHomeBankId == null) {
                        System.out.println("Skipped block with unknown home_banking_id: " + oldHomeBankId);
                        currentInsert.setLength(0);
                        continue; // skip this row
                    }

                    for (int i = 1; i < values.size(); i++) {
                        String val = values.get(i);

                        switch (i) {
                                //                            case 0 -> setSafeParam(pstmt, 1, val, Types.INTEGER); //
                                // id
                            case 1 -> pstmt.setInt(1, newHomeBankId); // home_banking_id (already mapped)
                            case 2 -> setSafeParam(pstmt, 2, val, Types.INTEGER); // block_order_number
                            case 3 -> setSafeParam(pstmt, 3, val, Types.VARCHAR); // name
                            case 4 -> setSafeParam(pstmt, 4, val, Types.VARCHAR); // description
                            case 5 -> setSafeParam(pstmt, 5, val, Types.INTEGER); // type_id
                            case 6 -> setSafeParam(pstmt, 6, val, Types.VARCHAR); // export_file
                            case 7 -> setSafeParam(pstmt, 7, val, Types.INTEGER); // active
                            case 8 -> setSafeParam(pstmt, 8, val, Types.INTEGER); // wait
                        }
                    }

                    // Track old ID for mapping later
                    try {
                        int oldId = Integer.parseInt(values.get(0));
                        insertedOldIds.add(oldId);
                        blockMap.put(oldId, -1); // initialize mapping
                    } catch (Exception ex) {
                        System.out.println("Error parsing componentBlockMap entry: " + ex.getMessage());
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0);
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
                conn.commit();
            }

            // Step 3: get component_block IDs after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectCompBlockIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println(
                        "Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                blockMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            System.out.println("componentBlockMap populated: " + blockMap);

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load component_block data", e.getMessage());
        }
    }

    public ErrorMessage restoreComponentInstruction(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO component_instruction (
                    instruction_order_number, actions, name, xpath, coordinates,
                    force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root,
                    css_selector, description, operation, optional, block_marked,
                    default_value, action_custom_max_wait_sec, on_hold_seconds, codified,
                    export_to_abr, active, block_id, variable_id, parent_block_id, parent_id, home_banking_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        String selectInstructionIdsSQL = "SELECT id FROM component_instruction ORDER BY id";

        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(sqlFilePath), Charset.forName("windows-1252")));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectInstructionIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;
            List<Integer> insertedOldIds = new ArrayList<>();
            instructionMap.clear();
            instrVariablesMap.clear();
            instrParentBlockMap.clear();
            instrParentIdMap.clear();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 26 && values.size() != 27) {
                        return new ErrorMessage(
                                "Parse Error",
                                "Expected 26 or 27 values for instruction, but got " + values.size(),
                                currentInsert.toString());
                    }

                    // Extract old block_id (index 22) and bot_job_id (index 25 / index 26)
                    Integer oldBlockId = parseIntSafe(values.get(22));
                    Integer oldHomeBankId =
                            parseIntSafe(values.get(values.size() == 26 ? 25 : 26)); // index depends on 26/27 cols

                    Integer newBlockId = oldBlockId != null ? blockMap.get(oldBlockId) : null;
                    Integer newHomeBankId = oldHomeBankId != null ? homeBankMap.get(oldHomeBankId) : null;

                    if (newBlockId == null) {
                        System.out.println("Skipped component_instruction with unknown block_id: " + oldBlockId);
                        currentInsert.setLength(0);
                        continue;
                    }
                    if (newHomeBankId == null) {
                        System.out.println("Skipped instruction with unknown home_banking_id: " + oldHomeBankId);
                        currentInsert.setLength(0);
                        continue;
                    }

                    Integer instructionId = values.get(0) != null ? Integer.valueOf(values.get(0)) : null;
                    String action = values.get(2);
                    boolean isGoto = "GOTO".equalsIgnoreCase(action) || "EXCEL GOTO".equalsIgnoreCase(action);

                    Integer parentBlockId = null;
                    Integer parentId = null;

                    boolean hasParentBlockId = values.size() == 27; // has parent_block_id columns 27 cols

                    if (hasParentBlockId) {
                        // values.size() == 27: parent_block_id = 24, parent_id = 25
                        String parBlockValue = values.get(24);
                        String parIdValue = values.get(25);

                        if (parBlockValue != null
                                && !parBlockValue.isBlank()
                                && !parBlockValue.equalsIgnoreCase("null")
                                && !parBlockValue.equalsIgnoreCase("[null]")) {
                            try {
                                parentBlockId = Integer.valueOf(parBlockValue.trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }

                        if (parIdValue != null
                                && !parIdValue.isBlank()
                                && !parIdValue.equalsIgnoreCase("null")
                                && !parIdValue.equalsIgnoreCase("[null]")) {
                            try {
                                parentId = Integer.valueOf(parIdValue.trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    } else {
                        // values.size() == 26: parent_block_id may not exist
                        String parValue = values.get(24);

                        if (parValue != null
                                && !parValue.isBlank()
                                && !parValue.equalsIgnoreCase("null")
                                && !parValue.equalsIgnoreCase("[null]")) {
                            try {
                                if (isGoto) {
                                    parentBlockId = Integer.valueOf(parValue.trim());
                                } else {
                                    parentId = Integer.valueOf(parValue.trim());
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }

                    // Map the values if instructionId exists
                    if (instructionId != null) {
                        if (parentBlockId != null) instrParentBlockMap.put(instructionId, parentBlockId);
                        if (parentId != null) instrParentIdMap.put(instructionId, parentId);
                    }

                    // Set prepared statement params to null for insert (new table always has parent_block_id)
                    setSafeParam(pstmt, 24, null, Types.INTEGER); // parent_block_id
                    setSafeParam(pstmt, 25, null, Types.INTEGER); // parent_id
                    setSafeParam(pstmt, 26, String.valueOf(newHomeBankId), Types.INTEGER);

                    for (int i = 1; i < values.size(); i++) {
                        switch (i) {
                                //                            case 0 -> setSafeParam(pstmt, 1, values.get(0),
                                // Types.INTEGER); // id
                            case 1 -> setSafeParam(pstmt, 1, values.get(1), Types.INTEGER); // instruction_order_number
                            case 2 -> setSafeParam(pstmt, 2, values.get(2), Types.VARCHAR); // actions
                            case 3 -> {
                                String val = values.get(3);
                                setSafeParam(pstmt, 3, val, Types.VARCHAR);
                            } // name
                            case 4 -> setSafeParam(pstmt, 4, values.get(4), Types.VARCHAR); // xpath
                            case 5 -> setSafeParam(pstmt, 5, values.get(5), Types.VARCHAR); // coordinates
                            case 6 -> setSafeParam(pstmt, 6, values.get(6), Types.INTEGER); // force_coordinates
                            case 7 -> setSafeParam(pstmt, 7, values.get(7), Types.VARCHAR); // iframe_xpath
                            case 8 -> setSafeParam(pstmt, 8, values.get(8), Types.VARCHAR); // tag_name
                            case 9 -> setSafeParam(pstmt, 9, values.get(9), Types.VARCHAR); // shadow_host
                            case 10 -> setSafeParam(pstmt, 10, values.get(10), Types.VARCHAR); // shadow_root
                            case 11 -> setSafeParam(pstmt, 11, values.get(11), Types.VARCHAR); // css_selector
                            case 12 -> setSafeParam(pstmt, 12, values.get(12), Types.VARCHAR); // description
                            case 13 -> setSafeParam(pstmt, 13, values.get(13), Types.VARCHAR); // operation
                            case 14 -> setSafeParam(pstmt, 14, values.get(14), Types.INTEGER); // optional
                            case 15 -> setSafeParam(pstmt, 15, values.get(15), Types.INTEGER); // block_marked
                            case 16 -> setSafeParam(pstmt, 16, values.get(16), Types.VARCHAR); // default_value
                            case 17 -> setSafeParam(
                                    pstmt, 17, values.get(17), Types.INTEGER); // action_custom_max_wait_sec
                            case 18 -> setSafeParam(pstmt, 18, values.get(18), Types.INTEGER); // on_hold_seconds
                            case 19 -> setSafeParam(pstmt, 19, values.get(19), Types.INTEGER); // codified
                            case 20 -> setSafeParam(pstmt, 20, values.get(20), Types.INTEGER); // export_to_abr
                            case 21 -> setSafeParam(pstmt, 21, values.get(21), Types.INTEGER); // active

                            case 22 -> {
                                // block_id replaced with newBlockId
                                setSafeParam(
                                        pstmt,
                                        22,
                                        String.valueOf(newBlockId),
                                        Types.INTEGER); // block_id already mapped
                            }
                            case 23 -> {
                                // variable_id + tracking
                                String varValue = values.get(23);
                                Integer variableId = null;
                                if (varValue != null
                                        && !varValue.isBlank()
                                        && !varValue.equalsIgnoreCase("null")
                                        && !varValue.equalsIgnoreCase("[null]")) {
                                    try {
                                        variableId = Integer.valueOf(varValue.trim());
                                    } catch (NumberFormatException e) {
                                        // Ignore parsing errors and keep variableId as null
                                    }
                                }

                                if (instructionId != null && variableId != null) {
                                    instrVariablesMap.put(instructionId, variableId);
                                }

                                // Firts to Be mapped after INSERTS into Variable TABLE
                                setSafeParam(pstmt, 23, "NULL", Types.INTEGER);
                            }
                            case 24, 25, 26 -> {}
                            default -> throw new IllegalArgumentException("Unexpected column index: " + i);
                        }
                    }

                    try {
                        int oldId = Integer.parseInt(values.get(0));
                        insertedOldIds.add(oldId);
                        instructionMap.put(oldId, -1);
                    } catch (Exception ex) {
                        System.out.println("Error parsing instructionMap entry: " + ex.getMessage());
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0);
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
                conn.commit();
            }

            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectInstructionIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println(
                        "Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                instructionMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            System.out.println("instructionMap populated: " + instructionMap);
            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load component_instruction data", e.getMessage());
        }
    }

    public ErrorMessage restoreComponentVariable(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO component_variable (
                    type, name, value, instruction_id, home_banking_id, local_format, delimiter
                ) VALUES (?, ?, ?, ?, ?, ?, ?);
                """;

        String selectVariableIdsSQL = "SELECT id FROM component_variable ORDER BY id";

        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(sqlFilePath), Charset.forName("windows-1252")));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            // Step 1: get current variable IDs before insert
            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectVariableIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

            List<Integer> insertedOldIds = new ArrayList<>();
            variableMap.clear(); // Map<Integer,Integer>

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 8) {
                        return new ErrorMessage(
                                "Parse Error", "Expected 8 values for component_variable", currentInsert.toString());
                    }

                    // Extract old instruction_id (index 4) and home_banking_id (index 5)
                    Integer oldInstructionId = parseIntSafe(values.get(4));
                    Integer oldHomeBankingId = parseIntSafe(values.get(5));

                    // Lookup new IDs
                    Integer newInstructionId = oldInstructionId != null ? instructionMap.get(oldInstructionId) : null;
                    Integer newHomeBankingId = oldHomeBankingId != null ? homeBankMap.get(oldHomeBankingId) : null;

                    if (newInstructionId == null) {
                        System.out.println("Skipped variable with unknown instruction_id: " + oldInstructionId);
                        currentInsert.setLength(0);
                        continue;
                    }
                    if (newHomeBankingId == null) {
                        System.out.println("Skipped variable with unknown home_banking_id: " + oldHomeBankingId);
                        currentInsert.setLength(0);
                        continue;
                    }

                    for (int i = 1; i < values.size(); i++) {
                        String val = values.get(i);

                        switch (i) {
                                //                            case 0 -> setSafeParam(pstmt, 1, val, Types.INTEGER);
                            case 1 -> setSafeParam(pstmt, 1, val, Types.VARCHAR); // type
                            case 2 -> setSafeParam(pstmt, 2, val, Types.VARCHAR); // name
                            case 3 -> setSafeParam(pstmt, 3, val, Types.VARCHAR); // value
                            case 4 -> setSafeParam(
                                    pstmt,
                                    4,
                                    String.valueOf(newInstructionId),
                                    Types.INTEGER); // instruction_id replaced
                            case 5 -> setSafeParam(
                                    pstmt,
                                    5,
                                    String.valueOf(newHomeBankingId),
                                    Types.INTEGER); // home_banking_id replaced
                            case 6 -> setSafeParam(pstmt, 6, val, Types.VARCHAR); // local_format
                            case 7 -> setSafeParam(pstmt, 7, val, Types.VARCHAR); // delimiter
                            default -> throw new IllegalArgumentException("Unexpected column index: " + i);
                        }
                    }

                    try {
                        int oldId = Integer.parseInt(values.get(0));
                        insertedOldIds.add(oldId);
                        variableMap.put(oldId, -1);
                    } catch (Exception ex) {
                        System.out.println("Error parsing variableMap entry: " + ex.getMessage());
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0);
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
                conn.commit();
            }

            // Step 3: get variable IDs after insert
            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectVariableIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println(
                        "Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                variableMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            System.out.println("variableMap populated: " + variableMap);

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load component_variable data", e.getMessage());
        }
    }

    public ErrorMessage restoreComponentUpdateInstruction(Connection conn) {
        final int BATCH_SIZE = 100;

        instrNewInverted.clear();
        for (Map.Entry<Integer, Integer> entry : instructionMap.entrySet()) {
            instrNewInverted.put(entry.getValue(), entry.getKey());
        }

        try (Statement connStmt = conn.createStatement()) {
            conn.setAutoCommit(false);

            String selectAccessSQL = "SELECT id, name, parent_id, variable_id, parent_block_id "
                    + "FROM component_instruction WHERE parent_id IS NOT NULL OR variable_id IS NULL OR parent_block_id IS NOT NULL ORDER BY id";

            try (ResultSet rsInstruction = connStmt.executeQuery(selectAccessSQL)) {

                String updateSQL =
                        "UPDATE component_instruction SET variable_id = ?, parent_id = ?, parent_block_id = ? WHERE id = ?";

                try (PreparedStatement updateStmt = conn.prepareStatement(updateSQL)) {
                    int count = 0;

                    while (rsInstruction.next()) {
                        int id = rsInstruction.getInt("id");

                        // ---- variable_id ----
                        Integer originalOldID = instrNewInverted.get(id);

                        Integer originalVarId = null;
                        Integer originalParBlockId = null;

                        if (originalOldID != null) {
                            originalVarId = instrVariablesMap.get(originalOldID);
                        }

                        if (originalOldID != null) {
                            originalParBlockId = instrParentBlockMap.get(originalOldID);
                        }

                        Integer originalOldParentID = null;
                        if (originalOldID != null) {
                            originalOldParentID = instrParentIdMap.get(originalOldID);
                        }

                        Integer newVariableId = null;
                        if (originalVarId != null) {
                            newVariableId = variableMap.get(originalVarId);
                        }
                        setSafeParam(
                                updateStmt,
                                1,
                                newVariableId != null ? String.valueOf(newVariableId) : "NULL",
                                Types.INTEGER);

                        // ---- parent_id ----
                        Integer newParentId = null;
                        if (originalOldParentID != null) {
                            newParentId = instructionMap.get(originalOldParentID);
                        }

                        setSafeParam(
                                updateStmt,
                                2,
                                newParentId != null ? String.valueOf(newParentId) : "NULL",
                                Types.INTEGER);

                        // ---- parent_block_id ----
                        Integer newParentBlockId = null;
                        if (originalParBlockId != null) {
                            newParentBlockId = blockMap.get(originalParBlockId);
                        }
                        setSafeParam(
                                updateStmt,
                                3,
                                newParentBlockId != null ? String.valueOf(newParentBlockId) : "NULL",
                                Types.INTEGER);

                        // ---- WHERE id = ? ----
                        updateStmt.setInt(4, id);

                        updateStmt.addBatch();
                        count++;

                        if (count % BATCH_SIZE == 0) {
                            updateStmt.executeBatch();
                            conn.commit();
                            System.out.println("Updated batch of " + BATCH_SIZE);
                        }
                    }

                    if (count % BATCH_SIZE != 0) {
                        updateStmt.executeBatch();
                        conn.commit();
                        System.out.println("Updated final batch of " + (count % BATCH_SIZE));
                    }

                    log.info("Updated component_instruction records: " + count);
                }
            }

            return null;

        } catch (SQLException error) {
            
                    log.error("Failed to update component_instruction: " + error.getMessage());
            return new ErrorMessage(
                    "Restore Failed", "Failed to update component_instruction data", error.getMessage());
        }
    }

    public ErrorMessage restoreComponentReference(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO component_reference (
                    reference_type, value, instruction_id, home_banking_id
                ) VALUES (?, ?, ?, ?);
                """;

        String selectReferenceIdsSQL = "SELECT id FROM component_reference ORDER BY id";

        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(sqlFilePath), Charset.forName("windows-1252")));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery);
                Statement idStmtBefore = conn.createStatement();
                Statement idStmtAfter = conn.createStatement()) {

            conn.setAutoCommit(false);

            List<Integer> idsBefore = new ArrayList<>();
            try (ResultSet rsBefore = idStmtBefore.executeQuery(selectReferenceIdsSQL)) {
                while (rsBefore.next()) {
                    idsBefore.add(rsBefore.getInt("id"));
                }
            }

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;
            List<Integer> insertedOldIds = new ArrayList<>();
            referenceMap.clear();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 5) {
                        return new ErrorMessage(
                                "Parse Error", "Expected 5 values for component_reference", currentInsert.toString());
                    }

                    // Parse old instruction ID and bot job ID
                    Integer oldInstructionId = parseIntSafe(values.get(3));
                    Integer oldHomeBankId = parseIntSafe(values.get(4));

                    Integer newInstructionId = oldInstructionId != null ? instructionMap.get(oldInstructionId) : null;
                    Integer newHomeBankId = oldHomeBankId != null ? homeBankMap.get(oldHomeBankId) : null;

                    if (newInstructionId == null || newHomeBankId == null) {
                        System.out.println("Skipped component_reference due to unknown instruction or home_bank ID: "
                                + "instr=" + oldInstructionId + ", homeBankId=" + oldHomeBankId);
                        currentInsert.setLength(0);
                        continue;
                    }

                    for (int i = 1; i < values.size(); i++) {
                        String val = values.get(i);
                        switch (i) {
                                //                            case 0 -> setSafeParam(pstmt, 1, val, Types.INTEGER); //
                                // id
                            case 1 -> setSafeParam(pstmt, 1, val, Types.VARCHAR); // reference_type
                            case 2 -> setSafeParam(pstmt, 2, val, Types.VARCHAR); // value
                            case 3 -> setSafeParam(
                                    pstmt, 3, String.valueOf(newInstructionId), Types.INTEGER); // instruction_id
                            case 4 -> setSafeParam(
                                    pstmt, 4, String.valueOf(newHomeBankId), Types.INTEGER); // home_banking_id
                            default -> throw new IllegalArgumentException("Unexpected column index: " + i);
                        }
                    }

                    try {
                        int oldId = Integer.parseInt(values.get(0));
                        insertedOldIds.add(oldId);
                        referenceMap.put(oldId, -1); // mark for replacement
                    } catch (Exception ex) {
                        System.out.println("Error parsing referenceMap entry: " + ex.getMessage());
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0);
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
                conn.commit();
            }

            List<Integer> idsAfter = new ArrayList<>();
            try (ResultSet rsAfter = idStmtAfter.executeQuery(selectReferenceIdsSQL)) {
                while (rsAfter.next()) {
                    idsAfter.add(rsAfter.getInt("id"));
                }
            }

            List<Integer> newIds = new ArrayList<>(idsAfter);
            newIds.removeAll(idsBefore);

            if (insertedOldIds.size() != newIds.size()) {
                System.err.println(
                        "Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                referenceMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            System.out.println("referenceMap populated: " + referenceMap);

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load component_reference data", e.getMessage());
        }
    }

    private void setSafeParam(PreparedStatement pstmt, int index, String val, int sqlType) throws SQLException {
        if (val == null || val.isBlank() || val.equalsIgnoreCase("null") || val.equalsIgnoreCase("[null]")) {
            pstmt.setNull(index, sqlType);
            return;
        }
        if (sqlType == Types.INTEGER) {
            try {
                pstmt.setInt(index, Integer.parseInt(val.trim()));
            } catch (NumberFormatException e) {
                // Fallback: if expected integer is malformed, save as string but log warning
                System.err.printf(
                        "Warning: Expected integer but got '%s' at index %d. Saving as string.%n", val, index);
                pstmt.setString(index, val);
            }
        } else if (sqlType == Types.VARCHAR) {
            pstmt.setString(index, val);
        } else {
            // Optionally handle other types here (e.g., DATE, BOOLEAN, etc.)
            pstmt.setObject(index, val);
        }
    }

    private Integer parseIntSafe(String val) {
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            return null;
        }
    }

    private String toSqlValue(String val) {
        if (val == null || val.isBlank()) {
            return "[null]";
        }
        return escapeSql(val);
    }
}
