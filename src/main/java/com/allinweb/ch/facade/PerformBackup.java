package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ErrorMessage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import lombok.Getter;
import lombok.Setter;

/**
 * PerformActions.
 *
 * @author Osvaldo Martini
 * @version 1.0
 */
public class PerformBackup {
    protected static volatile PerformBackup instance;

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
    private PerformBackup() {
        // Initialize if necessary
    }

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
        return input == null ? "" : input.replace("'", "''");
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
                FileWriter writer = new FileWriter(sqlFile)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String url = escapeSql(rs.getString("url"));
                String name = escapeSql(rs.getString("name"));
                String priority = escapeSql(rs.getString("priority"));
                String searchConfig = escapeSql(rs.getString("search_config"));
                String optionsConfig = escapeSql(rs.getString("options_config"));
                String cookies = escapeSql(rs.getString("cookies"));
                String driverSession = escapeSql(rs.getString("driver_session"));
                String username = escapeSql(rs.getString("username"));
                String password = escapeSql(rs.getString("password"));

                // Compose SQL insert
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
            ARLogger.getInstance(PerformDataBase.class)
                    .info("HomeBanking backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe("Error during home_banking backup: " + error.getMessage());
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
                FileWriter writer = new FileWriter(sqlFile)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String url = escapeSql(rs.getString("url"));
                int homeBankingId = rs.getInt("home_banking_id");
                boolean homeBankingIdWasNull = rs.wasNull();

                // Compose SQL insert
                String insert = String.format(
                        "INSERT INTO home_url (id, url, home_banking_id) " + "VALUES (%d, '%s', %s);",
                        id, url, homeBankingIdWasNull ? "NULL" : homeBankingId);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            ARLogger.getInstance(PerformDataBase.class)
                    .info("Home URL backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            ARLogger.getInstance(PerformDataBase.class).severe("Error during home_url backup: " + error.getMessage());
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
                FileWriter writer = new FileWriter(sqlFile)) {

            while (rs.next()) {
                int botJobId = rs.getInt("id");
                String name = escapeSql(rs.getString("name"));
                String description = escapeSql(rs.getString("description"));
                String priority = escapeSql(rs.getString("priority"));
                int homeBankingId = rs.getInt("home_banking_id");
                boolean homeBankingIdWasNull = rs.wasNull();
                int homeUrlId = rs.getInt("home_url_id");
                boolean homeUrlIdWasNull = rs.wasNull();
                boolean active = rs.getBoolean("active");

                // Compose SQL insert
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
            ARLogger.getInstance(PerformDataBase.class).info("Backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            ARLogger.getInstance(PerformDataBase.class).severe("Error during bot_job backup: " + error.getMessage());
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
                       active, block_id, variable_id, parent_id, bot_job_id
                FROM instruction
                ORDER BY id ASC
                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery();
                FileWriter writer = new FileWriter(sqlFile)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                int order = rs.getInt("instruction_order_number");
                String actions = escapeSql(rs.getString("actions"));
                String name = escapeSql(rs.getString("name"));
                String xpath = escapeSql(rs.getString("xpath"));
                String coordinates = escapeSql(rs.getString("coordinates"));
                boolean forceCoordinates = rs.getBoolean("force_coordinates");
                String iframeXpath = escapeSql(rs.getString("iframe_xpath"));
                String tagName = escapeSql(rs.getString("tag_name"));
                String shadowHost = escapeSql(rs.getString("shadow_host"));
                String shadowRoot = escapeSql(rs.getString("shadow_root"));
                String cssSelector = escapeSql(rs.getString("css_selector"));
                String description = escapeSql(rs.getString("description"));
                String operation = escapeSql(rs.getString("operation"));
                boolean optional = rs.getBoolean("optional");
                boolean blockMarked = rs.getBoolean("block_marked");
                String defaultValue = escapeSql(rs.getString("default_value"));
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
                int parentId = rs.getInt("parent_id");
                boolean parentIdWasNull = rs.wasNull();
                int botJobId = rs.getInt("bot_job_id");
                boolean botJobIdWasNull = rs.wasNull();

                String insert = String.format(
                        "INSERT INTO instruction (id, instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, block_id, variable_id, parent_id, bot_job_id) "
                                + "VALUES (%d, %d, '%s', '%s', '%s', '%s', %d, '%s', '%s', '%s', '%s', '%s', '%s', '%s', %d, %d, '%s', %s, %s, %d, %d, %d, %s, %s, %s, %s);",
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
                        parentIdWasNull ? "NULL" : parentId,
                        botJobIdWasNull ? "NULL" : botJobId);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            ARLogger.getInstance(PerformDataBase.class)
                    .info("Instruction backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe("Error during instruction backup: " + error.getMessage());
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
                FileWriter writer = new FileWriter(sqlFile)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String type = escapeSql(rs.getString("type"));
                String name = escapeSql(rs.getString("name"));
                String value = escapeSql(rs.getString("value"));
                int instructionId = rs.getInt("instruction_id");
                boolean instructionIdWasNull = rs.wasNull();
                int botJobId = rs.getInt("bot_job_id");
                boolean botJobIdWasNull = rs.wasNull();
                String localFormat = escapeSql(rs.getString("local_format"));
                String delimiter = escapeSql(rs.getString("delimiter"));

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
            ARLogger.getInstance(PerformDataBase.class)
                    .info("Variable backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            ARLogger.getInstance(PerformDataBase.class).severe("Error during variable backup: " + error.getMessage());
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
                FileWriter writer = new FileWriter(sqlFile)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String referenceType = escapeSql(rs.getString("reference_type"));
                String value = escapeSql(rs.getString("value"));
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
            ARLogger.getInstance(PerformDataBase.class)
                    .info("Reference backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            ARLogger.getInstance(PerformDataBase.class).severe("Error during reference backup: " + error.getMessage());
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
                FileWriter writer = new FileWriter(sqlFile)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                int orderNumber = rs.getInt("block_order_number");
                String name = escapeSql(rs.getString("name"));
                String description = escapeSql(rs.getString("description"));
                int typeId = rs.getInt("type_id");
                boolean typeIdWasNull = rs.wasNull();
                String exportFile = escapeSql(rs.getString("export_file"));
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
            ARLogger.getInstance(PerformDataBase.class).info("Block backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            ARLogger.getInstance(PerformDataBase.class).severe("Error during block backup: " + error.getMessage());
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
                FileWriter writer = new FileWriter(sqlFile)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                int homeBankingId = rs.getInt("home_banking_id");
                boolean homeBankingIdWasNull = rs.wasNull();
                int orderNumber = rs.getInt("block_order_number");
                String name = escapeSql(rs.getString("name"));
                String description = escapeSql(rs.getString("description"));
                int typeId = rs.getInt("type_id");
                boolean typeIdWasNull = rs.wasNull();
                String exportFile = escapeSql(rs.getString("export_file"));
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
            ARLogger.getInstance(PerformDataBase.class)
                    .info("ComponentBlock backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe("Error during component_block backup: " + error.getMessage());
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
                       codified, export_to_abr, active, block_id, variable_id, parent_id,
                       home_banking_id
                FROM component_instruction
                ORDER BY id ASC
                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery();
                FileWriter writer = new FileWriter(sqlFile)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                int orderNumber = rs.getInt("instruction_order_number");
                String actions = escapeSql(rs.getString("actions"));
                String name = escapeSql(rs.getString("name"));
                String xpath = escapeSql(rs.getString("xpath"));
                String coordinates = escapeSql(rs.getString("coordinates"));
                Boolean forceCoordinates = rs.getBoolean("force_coordinates");
                boolean forceCoordinatesWasNull = rs.wasNull();
                String iframeXpath = escapeSql(rs.getString("iframe_xpath"));
                String tagName = escapeSql(rs.getString("tag_name"));
                String shadowHost = escapeSql(rs.getString("shadow_host"));
                String shadowRoot = escapeSql(rs.getString("shadow_root"));
                String cssSelector = escapeSql(rs.getString("css_selector"));
                String description = escapeSql(rs.getString("description"));
                String operation = escapeSql(rs.getString("operation"));
                Boolean optional = rs.getBoolean("optional");
                boolean optionalWasNull = rs.wasNull();
                Boolean blockMarked = rs.getBoolean("block_marked");
                boolean blockMarkedWasNull = rs.wasNull();
                String defaultValue = escapeSql(rs.getString("default_value"));
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
                int parentId = rs.getInt("parent_id");
                boolean parentIdWasNull = rs.wasNull();
                int homeBankingId = rs.getInt("home_banking_id");
                boolean homeBankingIdWasNull = rs.wasNull();

                String insert = String.format(
                        "INSERT INTO component_instruction ("
                                + "id, instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, "
                                + "tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, "
                                + "default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, "
                                + "block_id, variable_id, parent_id, home_banking_id"
                                + ") VALUES (%d, %d, '%s', '%s', '%s', '%s', %s, '%s', '%s', '%s', '%s', '%s', '%s', '%s', %s, %s, '%s', %s, %s, %s, %s, %d, %s, %s, %s, %s);",
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
                        parentIdWasNull ? "NULL" : parentId,
                        homeBankingIdWasNull ? "NULL" : homeBankingId);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            ARLogger.getInstance(PerformDataBase.class)
                    .info("ComponentInstruction backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe("Error during component_instruction backup: " + error.getMessage());
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
                FileWriter writer = new FileWriter(sqlFile)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String type = escapeSql(rs.getString("type"));
                String name = escapeSql(rs.getString("name"));
                String value = escapeSql(rs.getString("value"));
                int instructionId = rs.getInt("instruction_id");
                boolean instructionIdWasNull = rs.wasNull();
                int homeBankingId = rs.getInt("home_banking_id");
                boolean homeBankingIdWasNull = rs.wasNull();
                String localFormat = escapeSql(rs.getString("local_format"));
                String delimiter = escapeSql(rs.getString("delimiter"));

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
            ARLogger.getInstance(PerformDataBase.class)
                    .info("ComponentVariable backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe("Error during component_variable backup: " + error.getMessage());
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
                FileWriter writer = new FileWriter(sqlFile)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String referenceType = escapeSql(rs.getString("reference_type"));
                String value = escapeSql(rs.getString("value"));
                int instructionId = rs.getInt("instruction_id");
                int homeBankingId = rs.getInt("home_banking_id");
                boolean homeBankingIdWasNull = rs.wasNull();

                String insert = String.format(
                        "INSERT INTO component_reference (id, reference_type, value, instruction_id, home_banking_id) "
                                + "VALUES (%d, '%s', '%s', %d, %s);",
                        id, referenceType, value, instructionId, homeBankingIdWasNull ? "NULL" : homeBankingId);

                writer.write(insert + System.lineSeparator());
            }

            writer.flush();
            ARLogger.getInstance(PerformDataBase.class)
                    .info("ComponentReference backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            ARLogger.getInstance(PerformDataBase.class)
                    .severe("Error during component_reference backup: " + error.getMessage());
            return new ErrorMessage(
                    "Error in backup process", "Error during component_reference backup", error.getMessage());
        }
    }

    // RESTORE
    public ErrorMessage restoreHomeBanking(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
        INSERT INTO home_banking (
            id, url, name, priority, search_config, options_config,
            cookies, driver_session, username, password
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFilePath));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

            homeBankMap.clear();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    // Full insert statement found, now parse values
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 10) {
                        return new ErrorMessage("Parse Error", "Expected 10 values", currentInsert.toString());
                    }

                    for (int i = 0; i < values.size(); i++) {
                        String val = values.get(i);
                        if ("NULL".equalsIgnoreCase(val)) {
                            pstmt.setNull(i + 1, Types.VARCHAR);
                        } else {
                            pstmt.setString(i + 1, val);
                        }
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0); // reset
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
            }

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load home_banking data", e.getMessage());
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
                    id, url, home_banking_id
                ) VALUES (?, ?, ?);
                """;

        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFilePath));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    // Full insert statement found, parse values
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 3) {
                        return new ErrorMessage(
                                "Parse Error", "Expected 3 values for home_url", currentInsert.toString());
                    }

                    for (int i = 0; i < values.size(); i++) {
                        String val = values.get(i);
                        if ("NULL".equalsIgnoreCase(val)) {
                            pstmt.setNull(i + 1, Types.VARCHAR);
                        } else {
                            pstmt.setString(i + 1, val);
                        }
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0); // reset
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
            }

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load home_url data", e.getMessage());
        }
    }

    public ErrorMessage restoreBlock(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO block (
                    id, block_order_number, name, description, type_id,
                    export_file, active, wait, bot_job_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFilePath));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

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

                    for (int i = 0; i < values.size(); i++) {
                        String val = values.get(i);
                        // Columns with integer types: id, block_order_number, type_id, active, wait, bot_job_id
                        if (i == 0 || i == 1 || i == 4 || i == 6 || i == 7 || i == 8) {
                            if ("NULL".equalsIgnoreCase(val)) {
                                pstmt.setNull(i + 1, java.sql.Types.INTEGER);
                            } else {
                                try {
                                    pstmt.setInt(i + 1, Integer.parseInt(val));
                                } catch (NumberFormatException e) {
                                    pstmt.setString(i + 1, val); // fallback if parsing fails
                                }
                            }
                        } else {
                            // Columns with String types: name, description, export_file
                            if ("NULL".equalsIgnoreCase(val)) {
                                pstmt.setNull(i + 1, java.sql.Types.VARCHAR);
                            } else {
                                pstmt.setString(i + 1, val);
                            }
                        }
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0); // reset for next statement
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
            }

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load block data", e.getMessage());
        }
    }

    public ErrorMessage restoreBotJob(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO bot_job (
                    id, name, description, priority, home_banking_id, home_url_id, active
                ) VALUES (?, ?, ?, ?, ?, ?, ?);
                """;

        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFilePath));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

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

                    for (int i = 0; i < values.size(); i++) {
                        String val = values.get(i);

                        // Columns with integer types: id, home_banking_id, home_url_id, active
                        if (i == 0 || i == 4 || i == 5 || i == 6) {
                            if ("NULL".equalsIgnoreCase(val) || val.isBlank()) {
                                pstmt.setNull(i + 1, java.sql.Types.INTEGER);
                            } else {
                                try {
                                    pstmt.setInt(i + 1, Integer.parseInt(val));
                                } catch (NumberFormatException e) {
                                    pstmt.setString(i + 1, val); // fallback if parsing fails
                                }
                            }
                        } else {
                            // Columns with String types: name, description, priority
                            if ("NULL".equalsIgnoreCase(val)) {
                                pstmt.setNull(i + 1, java.sql.Types.VARCHAR);
                            } else {
                                pstmt.setString(i + 1, val);
                            }
                        }
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0);
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
            }

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load bot_job data", e.getMessage());
        }
    }

    public ErrorMessage restoreInstruction(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO instruction (
                    id, instruction_order_number, actions, name, xpath, coordinates,
                    force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root,
                    css_selector, description, operation, optional, block_marked,
                    default_value, action_custom_max_wait_sec, on_hold_seconds, codified,
                    export_to_abr, active, block_id, variable_id, parent_id, bot_job_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFilePath));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 26) {
                        return new ErrorMessage(
                                "Parse Error", "Expected 26 values for instruction", currentInsert.toString());
                    }

                    for (int i = 0; i < values.size(); i++) {
                        String val = values.get(i);

                        if ("NULL".equalsIgnoreCase(val) || val.isBlank()) {
                            pstmt.setNull(i + 1, java.sql.Types.NULL);
                        } else {
                            switch (i) {
                                case 0, 1, 6, 14, 15, 17, 18, 19, 20, 21, 22, 23, 24, 25 -> {
                                    // Integers or booleans
                                    try {
                                        pstmt.setInt(i + 1, Integer.parseInt(val));
                                    } catch (NumberFormatException e) {
                                        pstmt.setString(i + 1, val); // fallback if it’s not really a number
                                    }
                                }
                                default -> pstmt.setString(i + 1, val); // Strings
                            }
                        }
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0); // reset for next
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
            }

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load instruction data", e.getMessage());
        }
    }

    public ErrorMessage restoreVariable(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO variable (
                    id, type, name, value, instruction_id, bot_job_id, local_format, delimiter
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFilePath));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

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

                    for (int i = 0; i < values.size(); i++) {
                        String val = values.get(i);

                        // Integers: id, instruction_id, bot_job_id
                        if (i == 0 || i == 4 || i == 5) {
                            if ("NULL".equalsIgnoreCase(val) || val.isBlank()) {
                                pstmt.setNull(i + 1, java.sql.Types.INTEGER);
                            } else {
                                try {
                                    pstmt.setInt(i + 1, Integer.parseInt(val));
                                } catch (NumberFormatException e) {
                                    pstmt.setString(i + 1, val); // fallback
                                }
                            }
                        } else {
                            // Strings: type, name, value, local_format, delimiter
                            if ("NULL".equalsIgnoreCase(val)) {
                                pstmt.setNull(i + 1, java.sql.Types.VARCHAR);
                            } else {
                                pstmt.setString(i + 1, val);
                            }
                        }
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0); // reset for next statement
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
            }

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load variable data", e.getMessage());
        }
    }

    public ErrorMessage restoreReference(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO reference (
                    id, reference_type, value, instruction_id, bot_job_id
                ) VALUES (?, ?, ?, ?, ?);
                """;

        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFilePath));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

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

                    for (int i = 0; i < values.size(); i++) {
                        String val = values.get(i);

                        // Integer fields: id, instruction_id, bot_job_id
                        if (i == 0 || i == 3 || i == 4) {
                            if ("NULL".equalsIgnoreCase(val) || val.isBlank()) {
                                pstmt.setNull(i + 1, java.sql.Types.INTEGER);
                            } else {
                                try {
                                    pstmt.setInt(i + 1, Integer.parseInt(val));
                                } catch (NumberFormatException e) {
                                    pstmt.setString(i + 1, val); // fallback to string
                                }
                            }
                        } else {
                            // String fields: reference_type, value
                            if ("NULL".equalsIgnoreCase(val)) {
                                pstmt.setNull(i + 1, java.sql.Types.VARCHAR);
                            } else {
                                pstmt.setString(i + 1, val);
                            }
                        }
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0); // reset
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
            }

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load reference data", e.getMessage());
        }
    }

    public ErrorMessage restoreComponentBlock(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO component_block (
                    id, home_banking_id, block_order_number, name, description,
                    type_id, export_file, active, wait
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFilePath));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

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

                    for (int i = 0; i < values.size(); i++) {
                        String val = values.get(i);
                        // For numeric fields, parse as integer if possible; else set string
                        if (i == 0 || i == 1 || i == 2 || i == 5 || i == 7 || i == 8) {
                            // These are numeric or boolean flags
                            if ("NULL".equalsIgnoreCase(val)) {
                                pstmt.setNull(i + 1, java.sql.Types.INTEGER);
                            } else {
                                try {
                                    pstmt.setInt(i + 1, Integer.parseInt(val));
                                } catch (NumberFormatException e) {
                                    // fallback to string if parsing fails
                                    pstmt.setString(i + 1, val);
                                }
                            }
                        } else {
                            // For strings: name, description, export_file
                            if ("NULL".equalsIgnoreCase(val)) {
                                pstmt.setNull(i + 1, java.sql.Types.VARCHAR);
                            } else {
                                pstmt.setString(i + 1, val);
                            }
                        }
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0); // reset
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
            }

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load component_block data", e.getMessage());
        }
    }

    public ErrorMessage restoreComponentInstruction(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO component_instruction (
                    id, instruction_order_number, actions, name, xpath, coordinates,
                    force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root,
                    css_selector, description, operation, optional, block_marked,
                    default_value, action_custom_max_wait_sec, on_hold_seconds, codified,
                    export_to_abr, active, block_id, variable_id, parent_id, home_banking_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFilePath));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);
                if (line.endsWith(";")) {
                    List<String> values = extractValuesFromInsert(currentInsert.toString());

                    if (values.size() != 26) {
                        return new ErrorMessage(
                                "Parse Error",
                                "Expected 26 values for component_instruction",
                                currentInsert.toString());
                    }

                    for (int i = 0; i < values.size(); i++) {
                        String val = values.get(i);

                        if ("NULL".equalsIgnoreCase(val) || val.isBlank()) {
                            pstmt.setNull(i + 1, java.sql.Types.NULL);
                        } else {
                            switch (i) {
                                    // Integers or booleans (safe fallback to string for malformed int)
                                case 0, 1, 6, 14, 15, 17, 18, 19, 20, 21, 22, 23, 24, 25 -> {
                                    try {
                                        pstmt.setInt(i + 1, Integer.parseInt(val));
                                    } catch (NumberFormatException e) {
                                        pstmt.setString(i + 1, val);
                                    }
                                }
                                default -> pstmt.setString(i + 1, val); // String values
                            }
                        }
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0); // reset
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
            }

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load component_instruction data", e.getMessage());
        }
    }

    public ErrorMessage restoreComponentVariable(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO component_variable (
                    id, type, name, value, instruction_id, home_banking_id, local_format, delimiter
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFilePath));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

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

                    for (int i = 0; i < values.size(); i++) {
                        String val = values.get(i);

                        // Integer fields
                        if (i == 0 || i == 4 || i == 5) {
                            if ("NULL".equalsIgnoreCase(val) || val.isBlank()) {
                                pstmt.setNull(i + 1, java.sql.Types.INTEGER);
                            } else {
                                try {
                                    pstmt.setInt(i + 1, Integer.parseInt(val));
                                } catch (NumberFormatException e) {
                                    pstmt.setString(i + 1, val); // fallback
                                }
                            }
                        } else {
                            // String fields
                            if ("NULL".equalsIgnoreCase(val)) {
                                pstmt.setNull(i + 1, java.sql.Types.VARCHAR);
                            } else {
                                pstmt.setString(i + 1, val);
                            }
                        }
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0); // reset
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
            }

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load component_variable data", e.getMessage());
        }
    }

    public ErrorMessage restoreComponentReference(Connection conn, String sqlFilePath) {
        String insertQuery =
                """
                INSERT INTO component_reference (
                    id, reference_type, value, instruction_id, home_banking_id
                ) VALUES (?, ?, ?, ?, ?);
                """;

        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFilePath));
                PreparedStatement pstmt = conn.prepareStatement(insertQuery)) {

            StringBuilder currentInsert = new StringBuilder();
            boolean batchReady = false;

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

                    for (int i = 0; i < values.size(); i++) {
                        String val = values.get(i);

                        if (i == 0 || i == 3 || i == 4) { // Integer fields
                            if ("NULL".equalsIgnoreCase(val) || val.isBlank()) {
                                pstmt.setNull(i + 1, java.sql.Types.INTEGER);
                            } else {
                                pstmt.setInt(i + 1, Integer.parseInt(val));
                            }
                        } else { // String fields
                            if ("NULL".equalsIgnoreCase(val)) {
                                pstmt.setNull(i + 1, java.sql.Types.VARCHAR);
                            } else {
                                pstmt.setString(i + 1, val);
                            }
                        }
                    }

                    pstmt.addBatch();
                    currentInsert.setLength(0);
                    batchReady = true;
                }
            }

            if (batchReady) {
                pstmt.executeBatch();
            }

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load component_reference data", e.getMessage());
        }
    }
}
